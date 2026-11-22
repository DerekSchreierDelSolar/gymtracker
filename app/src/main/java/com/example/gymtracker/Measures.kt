package com.example.gymtracker

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// ---------- Körpermaße + Körperfett (Roadmap Schritt 15) ----------
// Karte im Tab „Körper“ unter dem Gewicht. JSON "measures" = [{ time, type, value }] (nur wenn vorhanden).
// Körperfett kommt zusätzlich aus Health Connect (HealthConnect.kt, readBodyFat). Web: measures.js (gleich).

enum class MeasureType(val unit: String, val min: Double, val max: Double, private val de: String, private val en: String) {
    WAIST("cm", 40.0, 250.0, "Taille", "Waist"),
    CHEST("cm", 40.0, 250.0, "Brust", "Chest"),
    ARM("cm", 10.0, 80.0, "Arm", "Arm"),
    HIP("cm", 40.0, 250.0, "Hüfte", "Hips"),
    THIGH("cm", 20.0, 120.0, "Oberschenkel", "Thigh"),
    BODYFAT("%", 2.0, 70.0, "Körperfett", "Body fat");

    val label: String get() = tr(de, en)

    companion object {
        fun parse(s: String?): MeasureType? = entries.firstOrNull { it.name == s }
    }
}

data class Measure(val time: Long, val type: MeasureType, val value: Double)

/** Karte „Körpermaße“: Maß wählen, Wert eintragen (heute), Verlauf als Kurve (Punkt antippen = ändern/löschen). */
@Composable
fun MeasuresCard(store: GymStore) {
    val focus = LocalFocusManager.current
    var typeName by rememberSaveable { mutableStateOf(MeasureType.WAIST.name) }
    val type = MeasureType.valueOf(typeName)
    var input by remember(type) { mutableStateOf("") }
    var edit by remember { mutableStateOf<Measure?>(null) }
    val list = store.measures.filter { it.type == type }
    val last = list.lastOrNull()
    val value = input.replace(',', '.').toDoubleOrNull()?.takeIf { it in type.min..type.max }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("📏 Körpermaße", "📏 Body measurements"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MeasureType.entries.forEach { t ->
                    FilterChip(selected = t == type, onClick = { typeName = t.name }, label = { Text(t.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    label = { Text("${type.label} (${type.unit})") },
                    placeholder = { last?.let { Text(fmtW(it.value)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(enabled = value != null, onClick = {
                    value?.let { store.addMeasure(type, it) }
                    input = ""
                    focus.clearFocus()
                }) { Text(tr("Eintragen", "Log")) }
            }
            if (list.isEmpty()) {
                Text(
                    if (type == MeasureType.BODYFAT) tr("Noch kein Wert. Mit Health Connect kommt das Körperfett auch automatisch von deiner Waage.",
                        "No value yet. With Health Connect body fat also comes in automatically from your scale.")
                    else tr("Noch kein Wert – am besten morgens, immer an derselben Stelle messen.", "No value yet – measure in the morning, always at the same spot."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val first = list.first()
                val diff = list.last().value - first.value
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${fmtW(list.last().value)} ${type.unit}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    if (list.size >= 2) Text(
                        (if (diff >= 0) "+" else "−") + "${fmtW(Math.round(abs(diff) * 10) / 10.0)} ${type.unit} " + tr("seit ", "since ") + fmtDate(first.time),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                val shown = list.takeLast(30)
                LineChart(shown.map { it.value }, type.unit, onTapPoint = { edit = shown[it] })
                Row(Modifier.fillMaxWidth()) {
                    Text(fmtShortDate(shown.first().time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (shown.size > 1) Text(fmtShortDate(shown.last().time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    edit?.let { m -> MeasureEditDialog(store, m) { edit = null } }
}

@Composable
private fun MeasureEditDialog(store: GymStore, m: Measure, onDismiss: () -> Unit) {
    var text by remember(m) { mutableStateOf(fmtW(m.value)) }
    var time by remember(m) { mutableLongStateOf(m.time) }
    var pickDay by remember { mutableStateOf(false) }
    val v = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in m.type.min..m.type.max }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("${m.type.label} bearbeiten", "Edit ${m.type.label}")) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, isError = v == null,
                    label = { Text("${m.type.label} (${m.type.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { pickDay = true }) {
                    AppIcon(Ico.CALENDAR, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(fmtDate(time))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = v != null, onClick = { v?.let { store.updateMeasure(m, it, time) }; onDismiss() }) { Text(tr("Speichern", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = {
                store.deleteMeasure(m)
                Undo.show(tr("Eintrag gelöscht", "Entry deleted")) { store.restoreMeasure(m) }
                onDismiss()
            }) { Text(tr("Löschen", "Delete"), color = MaterialTheme.colorScheme.error) }
        }
    )
    if (pickDay) DayPickerDialog(time, onPicked = { time = it }, onDismiss = { pickDay = false })
}
