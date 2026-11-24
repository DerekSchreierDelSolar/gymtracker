package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

// ---------- Der Morgen danach (Roadmap Schritt 5) ----------
// Startseite: Rückblick aufs letzte Training (an gute Erfahrung anknüpfen) + Neuanfang-Effekt (Montag, Monat, Jahr).
// Vorfreude statt Pflicht: „Letztes Mal 75 kg Kniebeugen – schaffst du heute 77,5?“ (Schnellstart-Karte, Erinnerung).
// Web: morning.js (gleich).

/** Ganze Kalendertage zwischen dem Tag von `t` und heute (0 = heute). */
fun daysAgo(t: Long, now: Long = System.currentTimeMillis()): Int {
    fun midnight(x: Long) = Calendar.getInstance().apply {
        timeInMillis = x; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return Math.round((midnight(now) - midnight(t)) / 86_400_000.0).toInt()
}

/** „Heute“, „Gestern“, „Vorgestern“, sonst Wochentag („Am Donnerstag“). */
fun dayLabel(t: Long): String = when (daysAgo(t)) {
    0 -> tr("Heute", "Today")
    1 -> tr("Gestern", "Yesterday")
    2 -> tr("Vorgestern", "2 days ago")
    else -> tr("Am ", "On ") + weekdayLong(weekdayOf(t))
}

/**
 * Neuanfang-Effekt: Neujahr (1.–7. Januar), Monatsanfang (1.–2.), Montag – jeweils nur, solange in diesem Zeitraum
 * noch nicht trainiert wurde. null = kein besonderer Tag.
 */
fun freshStart(store: GymStore, now: Long = System.currentTimeMillis()): String? {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val sessions = allSessions(store)
    fun noneSince(t: Long) = sessions.none { it.start >= t }
    val dayStart = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val monthStart = (dayStart.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    val goal = store.weeklyGoal
    return when {
        c.get(Calendar.MONTH) == Calendar.JANUARY && c.get(Calendar.DAY_OF_MONTH) <= 7 && noneSince(monthStart) ->
            tr("🎆 Neues Jahr, neuer Anfang – dein erstes Training ${c.get(Calendar.YEAR)} wartet", "🎆 New year, fresh start – your first workout of ${c.get(Calendar.YEAR)} is waiting")
        c.get(Calendar.DAY_OF_MONTH) <= 2 && noneSince(monthStart) ->
            tr("📅 Neuer Monat – leg einen starken Start hin", "📅 New month – make it a strong start")
        weekdayOf(now) == 1 && noneSince(startOfWeek(now)) ->
            if (goal == 1) tr("🌱 Neue Woche – 1 Training wartet auf dich", "🌱 New week – 1 workout is waiting for you")
            else tr("🌱 Neue Woche – $goal Trainings warten auf dich", "🌱 New week – $goal workouts are waiting for you")
        else -> null
    }
}

/**
 * Vorfreude-Satz für ein Workout: die erste passende Übung (Ziel-Übungen zuerst) mit dem Vorschlag aus Progression.kt.
 * null = keine Übung mit Verlauf.
 */
fun anticipation(store: GymStore, w: Workout, today: Boolean = true): String? {
    val whenText = if (today) tr("heute", "today") else tr("nächstes Mal", "next time")
    val goalEx = store.goals.filter { it.type.needsExercise && goalProgress(store, it).reachedAt == null }.mapNotNull { it.exercise }.toSet()
    val dayStart = startOfToday()
    for (ex in w.exercises.sortedBy { if (it in goalEx) 0 else 1 }) {
        if (store.groupOf(ex).isCardio) continue
        val s = progressionSuggestion(store, ex, dayStart) ?: continue
        val last = store.setsFor(ex).filter { it.time < dayStart && it.isTop }
        val day = dayKey(last.first().time)
        val lastDay = last.filter { dayKey(it.time) == day }
        val name = exName(ex)
        if (isTimed(ex)) {
            val best = lastDay.maxOf { it.reps }
            return tr("Letztes Mal ${fmtSeconds(best)} $name – schaffst du $whenText ${fmtSeconds(best + 10)}?",
                "Last time ${fmtSeconds(best)} $name – can you do ${fmtSeconds(best + 10)} $whenText?")
        }
        val top = lastDay.maxOf { it.weight }
        val reps = lastDay.filter { it.weight == top }.maxOf { it.reps }
        return when {
            s.weight != null && s.weight > top -> tr("Letztes Mal ${fmtKg(top)} $name – schaffst du $whenText ${fmtWU(s.weight)}?",
                "Last time ${fmtKg(top)} $name – can you do ${fmtWU(s.weight)} $whenText?")
            top <= 0 -> tr("Letztes Mal $reps × $name – $whenText ${reps + 1}?", "Last time $reps × $name – ${reps + 1} $whenText?")
            else -> tr("Letztes Mal ${fmtKg(top)} × $reps $name – $whenText ${reps + 1} Wdh.?",
                "Last time ${fmtKg(top)} × $reps $name – ${reps + 1} reps $whenText?")
        }
    }
    return null
}

/** Das letzte Training in einem Satz: wichtigster Erfolg (ohne die allgemeinen) + Umfang. */
private fun lastSessionLine(store: GymStore, t: TrainingSession): Pair<String, String?> {
    val wins = achievements(store, t)
    val top = wins.firstOrNull { it.kind != AchievementKind.SESSION && it.kind != AchievementKind.WEEK_COUNT }
    val session = wins.first { it.kind == AchievementKind.SESSION }
    return if (top != null) "${top.emoji} ${top.title}" to listOfNotNull(top.detail, session.detail).joinToString(" · ").ifEmpty { null }
    else "${session.emoji} ${tr("Training geschafft", "Workout done")}" to session.detail
}

/** Karte ganz oben auf der Startseite: Neuanfang + letztes Training (höchstens 3 Tage her). Nichts davon → keine Karte. */
@Composable
fun MorningCard(store: GymStore) {
    val fresh = freshStart(store)
    val last = allSessions(store).maxByOrNull { it.start }?.takeIf { daysAgo(it.start) <= 3 }
    if (fresh == null && last == null) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fresh?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            last?.let { t ->
                val (title, detail) = lastSessionLine(store, t)
                Column {
                    Text("${dayLabel(t.start)} · ${t.workoutName}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
