package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// ---------- Aufwärm-Rampe (Roadmap Schritt 11) ----------
// Knopf „🔥 Aufwärmen“ unter der Satz-Eingabe (Kraftübungen mit Gewicht). Der Scheibenrechner wurde auf Wunsch
// wieder entfernt; bei Langhantel-Übungen rechnet die Rampe mit einer 20-kg-Stange. Web: warmup.js (gleich).

/** Standard-Langhantelstange (kg) für die Stufe „leere Stange“. */
const val BAR_KG = 20.0
/** Stange in der gewählten Einheit: 20 kg bzw. 45 lbs (in kg). */
val barKg: Double get() = if (isLb) 45 / LB_PER_KG else BAR_KG

/**
 * Aufwärm-Rampe zum Arbeitsgewicht: leere Stange (bei Langhantel) × 10, dann 40 % × 10, 60 % × 5, 80 % × 2 –
 * auf 2,5 kg gerundet, doppelte/zu leichte Stufen fallen weg. Unter 20 kg Arbeitsgewicht nur 50 % × 10.
 */
fun warmupRamp(work: Double, bar: Double?): List<Pair<Double, Int>> {
    if (work <= 0) return emptyList()
    fun round(v: Double) = roundPlate(v) // 2,5 kg bzw. 5 lbs
    val steps = if (work < 20) listOf(0.5 to 10) else listOf(0.4 to 10, 0.6 to 5, 0.8 to 2)
    val out = mutableListOf<Pair<Double, Int>>()
    if (bar != null && work > bar * 1.5) out += bar to 10
    for ((f, reps) in steps) {
        val w = maxOf(round(work * f), bar ?: 0.0)
        if (w < work && out.none { it.first == w }) out += w to reps
    }
    return out
}

/** Knopf „🔥 Aufwärmen“ unter der Satz-Eingabe. onWarmup: Gewicht + Wdh. als Aufwärmsatz übernehmen. */
@Composable
fun SetHelpers(exercise: String, weightText: String, onWarmup: (Double, Int) -> Unit) {
    val weight = parseW(weightText)
    if (weight == null || weight <= 0) return
    var ramp by remember { mutableStateOf(false) }
    TextButton(onClick = { ramp = true }) { Text(tr("🔥 Aufwärmen", "🔥 Warm-up")) }
    val bar = if (equipmentOf(exercise) == Equipment.LANGHANTEL) barKg else null
    if (ramp) WarmupDialog(weight, bar, onPick = { w, r -> onWarmup(w, r); ramp = false }) { ramp = false }
}

@Composable
private fun WarmupDialog(work: Double, bar: Double?, onPick: (Double, Int) -> Unit, onDismiss: () -> Unit) {
    val ramp = warmupRamp(work, bar)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Aufwärmen bis ${fmtKg(work)}", "Warm up to ${fmtKg(work)}")) },
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
                        Text("${fmtKg(w)} × $r", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (bar != null && w == bar) Text(tr("leere Stange", "empty bar"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) {
                    Text("1.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
                    Text(tr("${fmtKg(work)} – Arbeitssätze", "${fmtKg(work)} – work sets"), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen", "Close")) } }
    )
}
