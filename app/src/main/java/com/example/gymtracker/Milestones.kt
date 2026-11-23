package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.floor

// ---------- Nächste Meilensteine (Roadmap Schritt 4, Zielgradient-Effekt) ----------
// Kleine, bald erreichbare Schritte auf der Startseite: je näher, desto motivierender.
// Immer positiv formuliert („noch 2,5 kg bis …“), nie „du hast verpasst“. Web: milestones.js (gleich).

/** Ein nächster Schritt: fraction = Anteil des Wegs vom letzten zum nächsten Meilenstein (0..1). */
data class Milestone(val emoji: String, val title: String, val detail: String?, val fraction: Float, val exercise: String? = null)

/** Gesamtvolumen-Meilensteine in Tonnen. */
private val VOLUME_STEPS_T = listOf(1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)

/** Nächster Trainings-Meilenstein nach n (wie isMilestone in Achievements.kt). */
private fun nextCountMilestone(n: Int): Int =
    listOf(5, 10, 25, 50, 75, 100).firstOrNull { it > n } ?: ((n / 50) + 1) * 50

/** Schrittweite für „runde“ Gewichte: leicht 5 kg, mittel 10 kg, schwer 20 kg. */
private fun weightStep(best: Double) = when {
    best < 30 -> 5.0
    best < 150 -> 10.0
    else -> 20.0
}

private fun frac(prev: Double, cur: Double, next: Double): Float =
    if (next <= prev) 1f else ((cur - prev) / (next - prev)).toFloat().coerceIn(0f, 1f)

/**
 * Die nächsten erreichbaren Meilensteine, nächster zuerst (höchstens `limit`, höchstens 2 Übungen).
 */
fun nextMilestones(store: GymStore, limit: Int = 3): List<Milestone> {
    val out = mutableListOf<Milestone>()
    val now = System.currentTimeMillis()
    val sessions = allSessions(store)

    // Übungen: Ziel-Übungen + die 4 häufigsten Kraftübungen der letzten 60 Tage
    val goals = store.goals.filter { goalProgress(store, it).reachedAt == null }
    val goalEx = goals.filter { it.type.needsExercise }.associateBy { it.exercise }
    val recent = store.sets.filter { it.isWork && it.time > now - 60L * 86_400_000 }
        .filter { !store.groupOf(it.exercise).isCardio && !isTimed(it.exercise) }
        .groupingBy { it.exercise }.eachCount().entries.sortedByDescending { it.value }.take(4).map { it.key }
    val exercises = (goalEx.keys.filterNotNull() + recent).distinct()
    val exMilestones = mutableListOf<Milestone>()
    for (ex in exercises) {
        val g = goalEx[ex]
        val byTime = store.groupOf(ex).isCardio || isTimed(ex)
        val top = store.sets.top().filter { it.exercise == ex && it.reps > 0 }
        if (top.isEmpty()) continue
        val best = if (byTime) top.maxOf { it.reps.toDouble() } else top.maxOf { it.weight }
        if (best <= 0) continue
        val step = when {
            !byTime -> weightStep(best)
            store.groupOf(ex).isCardio -> 5.0   // Minuten
            else -> 30.0                        // Sekunden
        }
        var next = (floor(best / step) + 1) * step
        if (g != null && g.target in (best + 0.001)..next) next = g.target // nie über das eigene Ziel hinaus
        val prev = maxOf(0.0, next - step)
        val unit: (Double) -> String = if (!byTime) { v -> "${fmtW(v)} kg" } else { v -> fmtAmount(ex, v, store) }
        exMilestones += Milestone(
            if (g != null) "🎯" else "🏋️",
            tr("Noch ${unit(next - best)} bis ${unit(next)} ", "${unit(next - best)} to ${unit(next)} ") + exName(ex),
            tr("Bisher bestes: ${unit(best)}", "Best so far: ${unit(best)}") + if (g != null && next == g.target) tr(" · dein Ziel!", " · your goal!") else "",
            frac(prev, best, next), ex
        )
    }
    out += exMilestones.sortedByDescending { it.fraction }.take(2)

    // Anzahl Trainings
    val count = sessions.size
    if (count > 0) {
        val next = nextCountMilestone(count)
        val prev = if (count >= 100) (count / 50) * 50 else listOf(1, 5, 10, 25, 50, 75).lastOrNull { it <= count } ?: 0
        out += Milestone("🎉", tr("Noch ${plural(next - count, "Training", "Trainings")} bis zum $next. Training",
            "${plural(next - count, "workout", "workouts")} to workout #$next"),
            tr("$count Trainings bisher", "$count workouts so far"), frac(prev.toDouble(), count.toDouble(), next.toDouble()))
    }

    // Gesamtvolumen (Tonnen)
    val volume = store.sets.filter { it.isWork && !store.groupOf(it.exercise).isCardio && !isTimed(it.exercise) }.sumOf { it.weight * it.reps } / 1000.0
    if (volume > 0) {
        val next = VOLUME_STEPS_T.firstOrNull { it > volume } ?: (((volume / 10000).toInt() + 1) * 10000)
        val prev = VOLUME_STEPS_T.lastOrNull { it <= volume } ?: 0
        out += Milestone("🏗️", tr("Noch ${fmtW(Math.round((next - volume) * 10) / 10.0)} t bis $next t bewegt",
            "${fmtW(Math.round((next - volume) * 10) / 10.0)} t to $next t lifted"),
            tr("Gesamtvolumen seit Beginn: ${fmtW(Math.round(volume * 10) / 10.0)} t", "Total volume so far: ${fmtW(Math.round(volume * 10) / 10.0)} t"),
            frac(prev.toDouble(), volume, next.toDouble()))
    }

    // Wochenziel: nur wenn genau noch eins fehlt
    val week = startOfWeek()
    val thisWeek = sessions.count { it.start >= week }
    if (store.weeklyGoal - thisWeek == 1) out += Milestone("✅", tr("Noch 1 Training bis zum Wochenziel", "1 workout to your weekly goal"),
        null, (thisWeek.toFloat() / store.weeklyGoal))

    // Serie: diese Woche noch nicht trainiert, aber letzte Woche → mit einem Training wächst die Serie
    if (thisWeek == 0) {
        val n = streakFrom(sessions.map { startOfWeek(it.start) }.toSet(), previousWeek(week)).weeks // mit Joker
        if (n >= 1) out += Milestone("🔥", tr("Ein Training diese Woche → ${n + 1} Wochen in Folge", "One workout this week → ${n + 1} weeks in a row"),
            null, 0.9f)
    }

    // Cardio-Wochenziel
    goals.filter { it.type == GoalType.CARDIO_WEEK }.forEach { g ->
        val min = store.sets.filter { it.isWork && it.time >= week && store.groupOf(it.exercise).isCardio }.sumOf { it.reps }.toDouble()
        if (min < g.target) out += Milestone("🫀", tr("Noch ${fmtW(g.target - min)} min Cardio diese Woche", "${fmtW(g.target - min)} min of cardio left this week"),
            tr("Dein Ziel: ${fmtW(g.target)} min pro Woche", "Your goal: ${fmtW(g.target)} min per week"), (min / g.target).toFloat())
    }

    return out.sortedByDescending { it.fraction }.take(limit)
}

/** „🏁 Als Nächstes“ als Zeilen unten in der Ziele-Karte (GoalsCard in Goals.kt). */
@Composable
fun MilestoneRows(list: List<Milestone>) {
    list.forEach { m ->
        Column(Modifier.padding(top = 8.dp, bottom = 8.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.emoji, modifier = Modifier.width(32.dp))
                Text(m.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(m.fraction, Modifier.padding(start = 32.dp))
            m.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, top = 4.dp))
            }
        }
    }
}
