package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

// ---------- Trainingsblock mit Deload (Roadmap Schritt 14) ----------
// `weeks` Aufbauwochen + 1 Deload-Woche, ab dem Montag `start`, wiederholt sich. Im JSON "cycle" = { start, weeks }.
// Deload-Woche: im Training halbe Ziel-Sätze (aufgerundet, sessionWorkout) und 💡 ≈ 60 % Gewicht (Progression.kt).
// Das Workout selbst bleibt unverändert. Web: cycle.js (gleich).

data class TrainingCycle(val start: Long, val weeks: Int) {
    val length: Int get() = weeks + 1
}

/** Position im Block: Woche 1..length (length = Deload); null = kein Block oder Start in der Zukunft. */
fun cycleWeek(c: TrainingCycle?, now: Long = System.currentTimeMillis()): Int? {
    if (c == null) return null
    val diff = startOfWeek(now) - c.start
    if (diff < 0) return null
    val n = Math.round(diff / (7.0 * 86_400_000)).toInt() // Sommer-/Winterzeit: runden statt abschneiden
    return n % c.length + 1
}

fun isDeload(store: GymStore, now: Long = System.currentTimeMillis()): Boolean =
    store.cycle?.let { cycleWeek(it, now) == it.length } == true

/** Ziel-Sätze in der Deload-Woche: halbiert, aufgerundet (optional bleibt optional). */
fun deloadTarget(t: Int): Int = if (t <= 0) 0 else ceil(t / 2.0).toInt()

/** Deload-Gewicht: ≈ 60 %, auf 2,5 kg gerundet. */
fun deloadWeight(kg: Double): Double = floor(kg * 0.6 / 2.5 + 0.5) * 2.5

/** Zeile für die Wochenziel-Karte (null = kein Block). */
fun cycleLine(store: GymStore): String? {
    val c = store.cycle ?: return null
    val w = cycleWeek(c) ?: return null
    return if (w == c.length) tr("🛌 Deload-Woche – leichter trainieren, der Körper baut auf", "🛌 Deload week – train lighter, your body rebuilds")
    else tr("📆 Block: Woche $w von ${c.length} · Deload in Woche ${c.length}", "📆 Block: week $w of ${c.length} · deload in week ${c.length}")
}

/** Abschnitt „Trainingsblock“ im Einstellungen-Dialog. */
@Composable
fun CycleSettings(store: GymStore) {
    val c = store.cycle
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Trainingsblock mit Deload", "Training block with deload"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("Ein paar Wochen Aufbau, dann eine leichte Woche: halbe Ziel-Sätze, ca. 60 % Gewicht. Beugt Übertraining vor.",
                        "A few weeks of building, then a light week: half the target sets, about 60 % weight. Helps prevent overtraining."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = c != null, onCheckedChange = { on -> store.changeCycle(if (on) TrainingCycle(startOfWeek(), 4) else null) })
        }
        if (c != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { store.changeCycle(c.copy(weeks = c.weeks - 1)) }, enabled = c.weeks > 2) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(tr("${c.weeks} Wochen Aufbau + 1 Deload", "${c.weeks} weeks build + 1 deload"),
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(onClick = { store.changeCycle(c.copy(weeks = c.weeks + 1)) }, enabled = c.weeks < 8) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            cycleLine(store)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
