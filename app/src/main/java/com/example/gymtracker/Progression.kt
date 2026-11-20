package com.example.gymtracker

import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ---------- Progressions-Vorschlag ----------

/** Vorschlag fürs nächste Training: `weight` kann übernommen werden (null = kein Gewicht). */
data class Suggestion(val title: String, val reason: String, val weight: Double?)

/** Mitternacht heute (Sätze von heute zählen nicht als „letztes Mal“). */
fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * „Doppelte Progression“: Erst die Wiederholungen steigern, dann das Gewicht.
 * Grundlage ist der letzte Trainingstag dieser Übung vor `before`.
 */
fun progressionSuggestion(store: GymStore, name: String, before: Long): Suggestion? {
    if (store.groupOf(name).isCardio) return null
    val prev = store.setsFor(name).filter { it.time < before && it.isTop } // ohne Aufwärm-/Drop-Sätze
    val lastDay = prev.firstOrNull()?.let { dayKey(it.time) } ?: return null
    val last = prev.filter { dayKey(it.time) == lastDay }

    if (isTimed(name)) {
        val best = last.maxOf { it.reps }
        return Suggestion(
            tr("Ziel: ${fmtSeconds(best + 10)}", "Goal: ${fmtSeconds(best + 10)}"),
            tr("Letztes Mal max. ${fmtSeconds(best)} – 10 s länger versuchen", "Last time max ${fmtSeconds(best)} – try 10 s longer"),
            null
        )
    }
    val top = last.maxOf { it.weight }
    val atTop = last.filter { it.weight == top }
    val minReps = atTop.minOf { it.reps }
    val maxReps = atTop.maxOf { it.reps }

    if (top <= 0.0) { // Körpergewicht: Wiederholungen steigern
        return Suggestion(
            tr("Ziel: ${maxReps + 1} Wdh.", "Goal: ${maxReps + 1} reps"),
            tr("Letztes Mal max. $maxReps Wdh.", "Last time max $maxReps reps"),
            null
        )
    }
    val step = when (equipmentOf(name)) {
        Equipment.KURZHANTEL, Equipment.KETTLEBELL -> 2.0
        else -> 2.5
    }
    return if (atTop.size >= 2 && minReps >= 8) {
        Suggestion(
            tr("Heute ${fmtW(top + step)} kg?", "Try ${fmtW(top + step)} kg today?"),
            tr(
                "Letztes Mal ${atTop.size} Sätze mit ${fmtW(top)} kg und mind. $minReps Wdh. – Zeit für mehr Gewicht",
                "Last time ${atTop.size} sets at ${fmtW(top)} kg with at least $minReps reps – time to go heavier"
            ),
            top + step
        )
    } else {
        Suggestion(
            tr("${fmtW(top)} kg, Ziel: ${maxReps + 1}+ Wdh.", "${fmtW(top)} kg, goal: ${maxReps + 1}+ reps"),
            tr(
                "Erst bei 2 Sätzen mit 8+ Wdh. lohnt sich mehr Gewicht",
                "Go heavier once you hit 8+ reps on 2 sets"
            ),
            top
        )
    }
}

/** Karte mit dem Vorschlag und „Übernehmen“-Button (füllt das kg-Feld). */
@Composable
fun ProgressionHint(store: GymStore, name: String, before: Long = startOfToday(), onApply: (Double) -> Unit) {
    val s = progressionSuggestion(store, name, before) ?: return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💡", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(s.reason, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (s.weight != null) {
                TextButton(onClick = { onApply(s.weight) }) { Text(tr("Übernehmen", "Apply")) }
            }
        }
    }
}


