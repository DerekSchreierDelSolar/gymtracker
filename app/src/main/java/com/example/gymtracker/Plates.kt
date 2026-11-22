package com.example.gymtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------- Scheibenrechner + Aufwärm-Rampe (Roadmap Schritt 11) ----------
// Unter der Satz-Eingabe: „🏋 Scheiben“ (nur Langhantel) und „🔥 Aufwärmen“ (Kraftübungen mit Gewicht).
// Stangengewicht in GymStore.barWeight (JSON settings.barWeight, Standard 20). Web: plates.js (gleich).

/** Übliche Scheiben (kg), schwerste zuerst. */
val PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

/** Farben wie bei Wettkampfscheiben (zur Orientierung). */
private fun plateColor(kg: Double): Color = when (kg) {
    25.0 -> Color(0xFFD32F2F); 20.0 -> Color(0xFF1976D2); 15.0 -> Color(0xFFFBC02D); 10.0 -> Color(0xFF388E3C)
    5.0 -> Color(0xFFECEFF1); else -> Color(0xFF9E9E9E)
}

/** Scheibengewicht mit bis zu 2 Nachkommastellen (1,25 statt 1,3). */
fun fmtPlate(kg: Double): String = if (kg * 10 % 1.0 == 0.0) fmtW(kg) else String.format(appLocale(), "%.2f", kg)

/** Scheiben pro Seite (schwerste zuerst) + Rest, der sich mit den Scheiben nicht darstellen lässt. null = leichter als die Stange. */
data class PlateLoad(val perSide: List<Double>, val rest: Double)

fun platesFor(total: Double, bar: Double): PlateLoad? {
    if (total < bar) return null
    var side = (total - bar) / 2
    val out = mutableListOf<Double>()
    for (p in PLATES) while (side >= p - 1e-6) { out += p; side -= p }
    return PlateLoad(out, (side * 2 * 100).roundToInt() / 100.0)
}

/**
 * Aufwärm-Rampe zum Arbeitsgewicht: leere Stange (bei Langhantel) × 10, dann 40 % × 10, 60 % × 5, 80 % × 2 –
 * auf 2,5 kg gerundet, doppelte/zu leichte Stufen fallen weg. Unter 20 kg Arbeitsgewicht nur eine Stufe.
 */
fun warmupRamp(work: Double, bar: Double?): List<Pair<Double, Int>> {
    if (work <= 0) return emptyList()
    fun round(v: Double) = floor(v / 2.5 + 0.5) * 2.5
    val steps = if (work < 20) listOf(0.5 to 10) else listOf(0.4 to 10, 0.6 to 5, 0.8 to 2)
    val out = mutableListOf<Pair<Double, Int>>()
    if (bar != null && work > bar * 1.5) out += bar to 10
    for ((f, reps) in steps) {
        val w = maxOf(round(work * f), bar ?: 0.0)
        if (w < work && out.none { it.first == w }) out += w to reps
    }
    return out
}

/** Hilfs-Knöpfe unter der Satz-Eingabe. onWarmup: Gewicht + Wdh. als Aufwärmsatz übernehmen. */
@Composable
fun SetHelpers(store: GymStore, exercise: String, weightText: String, onWarmup: (Double, Int) -> Unit) {
    val barbell = equipmentOf(exercise) == Equipment.LANGHANTEL
    val weight = weightText.replace(',', '.').toDoubleOrNull()
    var plates by remember { mutableStateOf(false) }
    var ramp by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (barbell) TextButton(onClick = { plates = true }) { Text(tr("🏋 Scheiben", "🏋 Plates")) }
        if (weight != null && weight > 0) TextButton(onClick = { ramp = true }) { Text(tr("🔥 Aufwärmen", "🔥 Warm-up")) }
    }
    if (plates) PlateDialog(store, weight ?: store.barWeight) { plates = false }
    if (ramp && weight != null) WarmupDialog(store, weight, if (barbell) store.barWeight else null, onPick = { w, r -> onWarmup(w, r); ramp = false }) { ramp = false }
}

@Composable
private fun PlateDialog(store: GymStore, total: Double, onDismiss: () -> Unit) {
    val bar = store.barWeight
    val load = platesFor(total, bar)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("${fmtW(total)} kg auflegen", "Load ${fmtW(total)} kg")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Stange", "Bar"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20.0, 15.0, 10.0, 7.5).forEach { b ->
                        SegmentChip("${fmtW(b)} kg", bar == b, { store.changeBarWeight(b) }, Modifier.weight(1f))
                    }
                }
                when {
                    load == null -> Text(tr("Leichter als die Stange.", "Lighter than the bar."), color = MaterialTheme.colorScheme.error)
                    load.perSide.isEmpty() && load.rest == 0.0 -> Text(tr("Nur die leere Stange.", "Just the empty bar."))
                    else -> {
                        Text(tr("Pro Seite:", "Per side:"), style = MaterialTheme.typography.labelLarge)
                        // Scheiben als Balken: Höhe nach Gewicht, wie sie auf der Stange stecken
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(width = 28.dp, height = 8.dp).background(MaterialTheme.colorScheme.outline))
                            load.perSide.forEach { p ->
                                Box(
                                    Modifier
                                        .size(width = 14.dp, height = (24 + p * 2.4).dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(plateColor(p))
                                )
                            }
                        }
                        Text(
                            load.perSide.groupingBy { it }.eachCount().entries.joinToString(" + ") { (p, n) -> if (n > 1) "$n × ${fmtPlate(p)}" else fmtPlate(p) } + " kg",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        if (load.rest > 0) Text(
                            tr("Rest ${fmtPlate(load.rest)} kg lässt sich nicht genau auflegen.", "${fmtPlate(load.rest)} kg can't be loaded exactly."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun WarmupDialog(store: GymStore, work: Double, bar: Double?, onPick: (Double, Int) -> Unit, onDismiss: () -> Unit) {
    val ramp = warmupRamp(work, bar)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Aufwärmen bis ${fmtW(work)} kg", "Warm up to ${fmtW(work)} kg")) },
        text = {
            Column {
                Text(tr("Antippen übernimmt den Satz als Aufwärmsatz.", "Tap to use a set as warm-up."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ramp.forEachIndexed { i, (w, r) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPick(w, r) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("A${i + 1}", style = MaterialTheme.typography.labelLarge, color = SetType.WARMUP.color, modifier = Modifier.width(40.dp))
                        Text("${fmtW(w)} kg × $r", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (bar != null && w == bar) Text(tr("leere Stange", "empty bar"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) {
                    Text("1.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
                    Text(tr("${fmtW(work)} kg – Arbeitssätze", "${fmtW(work)} kg – work sets"), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen", "Close")) } }
    )
}
