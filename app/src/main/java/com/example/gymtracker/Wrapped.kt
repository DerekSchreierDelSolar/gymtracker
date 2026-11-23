package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar

// ---------- Jahresrückblick „Dein Jahr“ (Roadmap Schritt 17) ----------
// Knopf unten in der Rückblick-Karte → Blatt mit Zahlen und Höhepunkten eines Kalenderjahres.
// Alles rückwirkend aus den Daten (nichts gespeichert). Im Januar wird standardmäßig das Vorjahr gezeigt. Web: wrapped.js.

data class YearReview(
    val year: Int,
    val sessions: Int,
    val durationMs: Long,
    val sets: Int,
    val volume: Double,
    val kcal: Int?,
    val favorite: Pair<String, Int>?,          // Übung, Sätze
    val topGroup: MuscleGroup?,
    val bestMonth: Pair<Int, Int>?,            // Monat 0–11, Trainings
    val recordJump: Triple<String, Double, Double>?, // Übung, vorher, nachher (kg)
    val records: Int,
    val longestStreak: Int,
    val goals: List<Goal>,
    val badges: List<Badge>,
    val levelFrom: Int,
    val levelTo: Int
)

private fun yearOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)
private fun monthOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.MONTH)
private fun yearStart(y: Int) = Calendar.getInstance().apply { clear(); set(y, 0, 1) }.timeInMillis

/** Abgeschlossene Jahre mit Trainings, neuestes zuerst – das laufende Jahr gibt es erst, wenn es um ist. */
fun reviewYears(store: GymStore, now: Long = System.currentTimeMillis()): List<Int> =
    allSessions(store).map { yearOf(it.start) }.filter { it < yearOf(now) }.distinct().sortedDescending()

/** Standardjahr: das zuletzt abgeschlossene Jahr mit Trainings; null = noch keins (Knopf ausgeblendet). */
fun defaultReviewYear(store: GymStore, now: Long = System.currentTimeMillis()): Int? = reviewYears(store, now).firstOrNull()

fun yearReview(store: GymStore, year: Int): YearReview {
    val from = yearStart(year)
    val to = yearStart(year + 1)
    val sessions = allSessions(store).filter { it.start in from until to }
    val stats = sessions.map { sessionStats(store, it) }
    val work = store.sets.filter { it.isWork && it.time in from until to }
    val strengthSets = work.filter { !store.groupOf(it.exercise).isCardio && !isTimed(it.exercise) }

    // Größter Sprung: bestes Gewicht im Jahr ggü. Bestwert davor (bzw. erstem Trainingstag im Jahr)
    var jump: Triple<String, Double, Double>? = null
    store.sets.top().filter { it.time in from until to && it.weight > 0 && !store.groupOf(it.exercise).isCardio && !isTimed(it.exercise) }
        .groupBy { it.exercise }.forEach { (ex, list) ->
            val before = store.sets.top().filter { it.exercise == ex && it.time < from }.maxOfOrNull { it.weight }
                ?: list.minBy { it.time }.let { first -> list.filter { dayKey(it.time) == dayKey(first.time) }.maxOf { it.weight } }
            val best = list.maxOf { it.weight }
            if (best - before > (jump?.let { it.third - it.second } ?: 0.0)) jump = Triple(ex, before, best)
        }

    // Längste Serie innerhalb des Jahres (mit Joker)
    val weeks = sessions.map { startOfWeek(it.start) }.toSet()
    val longest = weeks.maxOfOrNull { streakFrom(weeks, it).weeks } ?: 0

    val earned = earnedBadges(store)
    return YearReview(
        year = year,
        sessions = sessions.size,
        durationMs = sessions.filter { it.id >= 0 }.sumOf { it.end - it.start },
        sets = work.size,
        volume = strengthSets.sumOf { it.weight * it.reps },
        kcal = stats.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum(),
        favorite = work.groupingBy { it.exercise }.eachCount().maxByOrNull { it.value }?.toPair(),
        topGroup = work.groupingBy { store.groupOf(it.exercise) }.eachCount()
            .filterKeys { it != MuscleGroup.CARDIO && it != MuscleGroup.SONSTIGE }.maxByOrNull { it.value }?.key,
        bestMonth = sessions.groupingBy { monthOf(it.start) }.eachCount().maxByOrNull { it.value }?.toPair(),
        recordJump = jump,
        records = recordEvents(store).count { it.set.time in from until to },
        longestStreak = longest,
        goals = store.goals.filter { g -> goalProgress(store, g).reachedAt?.let { it in from until to } == true },
        badges = BADGES.filter { b -> earned[b.id]?.let { it in from until to } == true },
        levelFrom = levelOf(xpBreakdown(store, from - 1).total).level,
        levelTo = levelOf(xpBreakdown(store, to - 1).total).level
    )
}

/** Knopf in der Rückblick-Karte + Blatt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearReviewButton(store: GymStore) {
    val initial = defaultReviewYear(store) ?: return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(tr("🎉 Dein Jahr $initial ansehen", "🎉 See your year $initial")) }
    if (!open) return
    var year by remember { mutableIntStateOf(initial) }
    val r = remember(year, store.sets.size) { yearReview(store, year) }
    val years = reviewYears(store)
    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tr("🎉 Dein Jahr ${r.year}", "🎉 Your year ${r.year}"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (years.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                years.take(4).forEach { y -> SegmentChip(y.toString(), y == year, { year = y }, Modifier.weight(1f)) }
            }
            if (r.sessions == 0) {
                Text(tr("In diesem Jahr gibt es noch keine Trainings.", "No workouts this year yet."))
                return@Column
            }
            val tiles = buildList {
                add(tr("Trainings", "Workouts") to r.sessions.toString())
                if (r.durationMs > 0) add(tr("Trainingszeit", "Training time") to fmtHoursMinutes(r.durationMs))
                add(tr("Sätze", "Sets") to fmtKcal(r.sets))
                if (r.volume > 0) add(tr("Bewegt", "Lifted") to "${fmtW(Math.round(toBig(r.volume) * 10) / 10.0)} $bigUnit")
                r.kcal?.let { add("🔥 kcal" to "~${fmtKcal(it)}") }
                add(tr("Rekorde", "Records") to "🏆 ${r.records}")
            }
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (l, v) -> StatBox(l, v, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            HorizontalDivider()
            r.favorite?.let { (ex, n) -> WrappedLine("❤️", tr("Lieblingsübung", "Favourite exercise"), "${exName(ex)} · " + plural(n, tr("Satz", "set"), tr("Sätze", "sets"))) }
            r.recordJump?.let { (ex, a, b) -> WrappedLine("🚀", tr("Größter Sprung", "Biggest jump"), "${exName(ex)}: ${fmtWU(a)} → ${fmtKg(b)} (+${fmtWU(b - a)})") }
            r.topGroup?.let { WrappedLine("💪", tr("Am meisten trainiert", "Most trained"), it.label) }
            r.bestMonth?.let { (m, n) ->
                val name = SimpleDateFormat("LLLL", appLocale()).format(Calendar.getInstance().apply { clear(); set(r.year, m, 1) }.time)
                WrappedLine("📅", tr("Stärkster Monat", "Best month"), "$name · " + plural(n, "Training", tr("Trainings", "workouts")))
            }
            if (r.longestStreak >= 2) WrappedLine("🔥", tr("Längste Serie", "Longest streak"), tr("${r.longestStreak} Wochen in Folge", "${r.longestStreak} weeks in a row"))
            if (r.levelTo > r.levelFrom) WrappedLine("⭐", tr("Level", "Level"), tr("von ${r.levelFrom} auf ${r.levelTo} · ${levelTitle(r.levelTo)}", "from ${r.levelFrom} to ${r.levelTo} · ${levelTitle(r.levelTo)}"))
            if (r.goals.isNotEmpty()) WrappedLine("🎯", tr("Erreichte Ziele", "Goals reached"), r.goals.joinToString(", ") { goalTitle(store, it) })
            if (r.badges.isNotEmpty()) WrappedLine("🏅", tr("Neue Abzeichen", "New badges"), r.badges.joinToString(" ") { it.emoji } + " (${r.badges.size})")
            HorizontalDivider()
            Text(
                tr("Jeder Satz davon war eine Entscheidung für dich. Auf ein starkes ${r.year + 1}! 💪", "Every one of those sets was a choice for yourself. Here's to a strong ${r.year + 1}! 💪"),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun WrappedLine(emoji: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
