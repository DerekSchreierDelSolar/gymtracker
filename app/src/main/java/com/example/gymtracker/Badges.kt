package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar

// ---------- Abzeichen (Roadmap Schritt 7) ----------
// Rückwirkend aus den vorhandenen Daten berechnet (nichts gespeichert). Sichtbare Abzeichen = Meilensteine,
// geheime = Überraschungen (variable Belohnung), werden erst gezeigt, wenn man sie hat. Web: badges.js (gleich).

data class Badge(val id: String, val emoji: String, val name: String, val desc: String, val secret: Boolean = false)

val BADGES: List<Badge> get() = listOf(
    Badge("first", "👣", tr("Erster Schritt", "First step"), tr("Dein erstes Training", "Your first workout")),
    Badge("s10", "🔟", tr("Dranbleiber", "Sticking with it"), tr("10 Trainings", "10 workouts")),
    Badge("s50", "🏅", tr("Stammgast", "Regular"), tr("50 Trainings", "50 workouts")),
    Badge("s100", "💯", tr("Hundert!", "Hundred!"), tr("100 Trainings", "100 workouts")),
    Badge("s250", "🏛️", tr("Eisenfest", "Iron will"), tr("250 Trainings", "250 workouts")),
    Badge("streak4", "🔥", tr("Vier Wochen am Stück", "Four weeks straight"), tr("4 Wochen in Folge trainiert", "Trained 4 weeks in a row")),
    Badge("streak12", "🌋", tr("Ein Vierteljahr", "A whole quarter"), tr("12 Wochen in Folge trainiert", "Trained 12 weeks in a row")),
    Badge("week10", "✅", tr("Zuverlässig", "Reliable"), tr("10 Wochen mit erreichtem Wochenziel", "10 weeks with your weekly goal reached")),
    Badge("rec1", "🥇", tr("Erster Rekord", "First record"), tr("Deine erste Bestleistung", "Your first personal record")),
    Badge("rec25", "🏆", tr("Rekordjäger", "Record hunter"), tr("25 Rekorde", "25 records")),
    Badge("day10t", "🏋️", tr("Schwerlast", "Heavy duty"), tr("10 t an einem Tag bewegt", "10 t lifted in one day")),
    Badge("total100t", "🏗️", tr("Tonnenweise", "By the ton"), tr("100 t insgesamt bewegt", "100 t lifted in total")),
    Badge("goal1", "🎯", tr("Zielstrebig", "Determined"), tr("Ein langfristiges Ziel erreicht", "Reached a long-term goal")),
    Badge("allround", "🧩", tr("Rundum", "All-rounder"), tr("Brust, Rücken, Beine, Schultern, Bizeps und Trizeps in einer Woche", "Chest, back, legs, shoulders, biceps and triceps in one week")),
    Badge("cardio30", "🫁", tr("Langer Atem", "Staying power"), tr("30 min Cardio am Stück", "30 min of cardio in one go")),
    Badge("hold2", "🪨", tr("Felsenfest", "Rock solid"), tr("2 min bei einer Zeitübung (z. B. Unterarmstütz)", "2 min on a timed exercise (e.g. plank)")),
    Badge("explorer", "🧭", tr("Entdecker", "Explorer"), tr("25 verschiedene Übungen gemacht", "Did 25 different exercises")),
    // Geheime Überraschungen
    Badge("early", "🌅", tr("Frühaufsteher", "Early bird"), tr("Training vor 7 Uhr", "Workout before 7 am"), secret = true),
    Badge("night", "🦉", tr("Nachteule", "Night owl"), tr("Training nach 21 Uhr", "Workout after 9 pm"), secret = true),
    Badge("weekend", "⚔️", tr("Wochenend-Krieger", "Weekend warrior"), tr("Samstag und Sonntag trainiert", "Trained on Saturday and Sunday"), secret = true),
    Badge("newyear", "🎆", tr("Guter Vorsatz", "New Year's resolution"), tr("Training am 1. Januar", "Workout on 1 January"), secret = true),
    Badge("comeback", "🔄", tr("Comeback", "Comeback"), tr("Nach über 30 Tagen Pause wieder trainiert", "Back after more than 30 days off"), secret = true),
    Badge("double", "✌️", tr("Doppelschicht", "Double shift"), tr("Zwei Trainings an einem Tag", "Two workouts in one day"), secret = true),
    Badge("marathon", "⏳", tr("Marathon", "Marathon"), tr("Training über 90 Minuten", "Workout over 90 minutes"), secret = true)
)

private fun hourOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)

/** Erreichte Abzeichen: ID → Zeitpunkt, an dem es verdient wurde. */
fun earnedBadges(store: GymStore): Map<String, Long> = store.memo("earnedBadges") {
    val out = mutableMapOf<String, Long>()
    fun earn(id: String, t: Long?) { if (t != null) out[id] = t }
    val sessions = allSessions(store).sortedBy { it.start }
    val work = store.sets.filter { it.isWork }.sortedBy { it.time }
    val strength = work.filter { !store.groupOf(it.exercise).isCardio && !isTimed(it.exercise) }

    // Anzahl Trainings
    mapOf("first" to 1, "s10" to 10, "s50" to 50, "s100" to 100, "s250" to 250).forEach { (id, n) -> earn(id, sessions.getOrNull(n - 1)?.start) }

    // Wochen in Folge / Wochenziel
    val byWeek = sessions.groupBy { startOfWeek(it.start) }.toSortedMap()
    for ((w, l) in byWeek) {
        val run = streakFrom(byWeek.keys, w).weeks // mit Joker (DashboardTab.kt)
        if (run >= 4 && "streak4" !in out) earn("streak4", l.first().start)
        if (run >= 12 && "streak12" !in out) earn("streak12", l.first().start)
    }
    earn("week10", byWeek.values.mapNotNull { it.getOrNull(store.weeklyGoal - 1)?.start }.getOrNull(9))

    // Rekorde
    val records = recordEvents(store).map { it.set.time }.sorted()
    earn("rec1", records.getOrNull(0))
    earn("rec25", records.getOrNull(24))

    // Volumen: 10 t an einem Tag, 100 t insgesamt
    var total = 0.0
    val perDay = mutableMapOf<Int, Double>()
    for (s in strength) {
        val v = s.weight * s.reps
        total += v
        if (total >= 100_000 && "total100t" !in out) earn("total100t", s.time)
        val d = (perDay[dayKey(s.time)] ?: 0.0) + v
        perDay[dayKey(s.time)] = d
        if (d >= 10_000 && "day10t" !in out) earn("day10t", s.time)
    }

    earn("goal1", store.goals.mapNotNull { goalProgress(store, it).reachedAt }.minOrNull())

    // Alle großen Muskelgruppen in einer Woche
    val main = setOf(MuscleGroup.BRUST, MuscleGroup.RUECKEN, MuscleGroup.BEINE, MuscleGroup.SCHULTERN, MuscleGroup.BIZEPS, MuscleGroup.TRIZEPS)
    val seen = mutableMapOf<Long, MutableSet<MuscleGroup>>()
    for (s in work) {
        val g = store.groupOf(s.exercise)
        if (g !in main) continue
        val set = seen.getOrPut(startOfWeek(s.time)) { mutableSetOf() }
        set += g
        if (set.size == main.size && "allround" !in out) earn("allround", s.time)
    }

    earn("cardio30", work.firstOrNull { store.groupOf(it.exercise).isCardio && it.reps >= 30 }?.time)
    earn("hold2", work.firstOrNull { isTimed(it.exercise) && !store.groupOf(it.exercise).isCardio && it.reps >= 120 }?.time)

    // 25 verschiedene Übungen
    val distinct = mutableSetOf<String>()
    for (s in work) if (distinct.add(s.exercise) && distinct.size == 25) { earn("explorer", s.time); break }

    // Geheime
    earn("early", sessions.firstOrNull { hourOf(it.start) < 7 }?.start)
    earn("night", sessions.firstOrNull { hourOf(it.start) >= 21 }?.start)
    val days = sessions.groupBy { dayKey(it.start) }
    earn("weekend", sessions.firstOrNull { t ->
        weekdayOf(t.start) == 7 && dayKey(t.start - 86_400_000L) in days.keys
    }?.start)
    earn("newyear", sessions.firstOrNull { Calendar.getInstance().apply { timeInMillis = it.start }.let { c -> c.get(Calendar.MONTH) == 0 && c.get(Calendar.DAY_OF_MONTH) == 1 } }?.start)
    earn("comeback", sessions.zipWithNext().firstOrNull { (a, b) -> b.start - a.start > 30L * 86_400_000 }?.second?.start)
    earn("double", days.values.filter { it.size >= 2 }.minOfOrNull { it.sortedBy { s -> s.start }[1].start })
    earn("marathon", sessions.firstOrNull { it.id >= 0 && it.end - it.start >= 90L * 60_000 }?.start)
    out
}

/** Abzeichen, die in diesem Training (zwischen Start und Ende) verdient wurden – für Achievements.kt. */
fun badgesIn(store: GymStore, t: TrainingSession, earned: Map<String, Long> = earnedBadges(store)): List<Badge> =
    BADGES.filter { b -> earned[b.id]?.let { it in t.start..t.end } == true }

/** Zeile unten in der Level-Karte (Levels.kt): Anzahl + die neuesten; antippen = Übersicht. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesRow(store: GymStore) {
    val earned = earnedBadges(store)
    if (earned.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val all = BADGES
    val latest = all.filter { it.id in earned }.sortedByDescending { earned[it.id] }.take(5)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🏅", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Abzeichen", "Badges"), style = MaterialTheme.typography.titleSmall)
            Text(tr("${earned.size} von ${all.size} · antippen für alle", "${earned.size} of ${all.size} · tap for all"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(latest.joinToString(" ") { it.emoji }, style = MaterialTheme.typography.titleLarge)
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(tr("🏅 Abzeichen · ${earned.size} von ${all.size}", "🏅 Badges · ${earned.size} of ${all.size}"), style = MaterialTheme.typography.titleLarge)
            Text(tr("❓ = geheim – wird verraten, wenn du es schaffst", "❓ = secret – revealed once you earn it"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // erreichte zuerst (neueste oben), dann offene, geheime zuletzt
            val sorted = all.sortedWith(compareBy<Badge>({ if (it.id in earned) 0 else if (!it.secret) 1 else 2 }, { -(earned[it.id] ?: 0L) }))
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(sorted, key = { it.id }) { b ->
                    val got = earned[b.id]
                    val hidden = got == null && b.secret
                    Column(
                        Modifier.padding(6.dp).alpha(if (got != null) 1f else 0.45f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (hidden) "❓" else b.emoji, style = MaterialTheme.typography.headlineMedium)
                        Text(if (hidden) tr("Geheim", "Secret") else b.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        Text(
                            if (hidden) tr("Überraschung", "Surprise") else if (got != null) b.desc + " · " + fmtDate(got) else b.desc,
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
