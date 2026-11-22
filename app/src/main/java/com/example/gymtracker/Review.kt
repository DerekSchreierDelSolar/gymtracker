package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.roundToInt

// ---------- Wochen-/Monatsrückblick (Karte auf der Startseite) ----------
//
// Zeigt den letzten ABGESCHLOSSENEN Zeitraum (letzte Woche Mo–So bzw. letzten Kalendermonat)
// im Vergleich zum Zeitraum davor. Web: review.js (gleiche Logik).

enum class ReviewPeriod { WEEK, MONTH }

/** Beginn des Zeitraums, der `back` Zeiträume vor dem laufenden liegt (back = 1 → letzte Woche bzw. letzter Monat). */
fun reviewStart(period: ReviewPeriod, back: Int, now: Long = System.currentTimeMillis()): Long =
    when (period) {
        ReviewPeriod.WEEK -> Calendar.getInstance().apply { timeInMillis = startOfWeek(now); add(Calendar.DAY_OF_MONTH, -7 * back) }.timeInMillis
        ReviewPeriod.MONTH -> Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -back)
        }.timeInMillis
    }

/** Kennzahlen eines Zeitraums [start, end). */
data class PeriodSummary(
    val sessions: Int,
    val sets: Int,
    val volume: Double,
    /** Trainingszeit in ms (nur gestartete Workouts – freie Trainings haben keine echte Dauer) */
    val duration: Long,
    /** null = kein Training mit bekanntem Körpergewicht */
    val kcal: Int?
)

fun periodSummary(store: GymStore, start: Long, end: Long): PeriodSummary {
    val list = allSessions(store).filter { it.start in start until end }
    val stats = list.map { sessionStats(store, it) }
    return PeriodSummary(
        sessions = list.size,
        sets = stats.sumOf { it.sets },
        volume = stats.sumOf { it.volume },
        duration = list.filter { it.id >= 0 }.sumOf { (it.end - it.start).coerceAtLeast(0) },
        kcal = stats.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum()
    )
}

data class Review(
    val start: Long,
    val end: Long,
    val now: PeriodSummary,
    val before: PeriodSummary,
    val records: List<RecordEvent>,
    /** Muskel-IDs, am meisten trainiert zuerst (höchstens 3) */
    val topMuscles: List<String>,
    /** Muskeln, die in den 3 Zeiträumen davor trainiert wurden, jetzt aber kaum (< 20 % des Spitzenmuskels) */
    val neglected: List<String>
)

fun review(store: GymStore, period: ReviewPeriod, now: Long = System.currentTimeMillis()): Review {
    val start = reviewStart(period, 1, now)
    val end = reviewStart(period, 0, now)
    val prev = reviewStart(period, 2, now)
    val earlier = reviewStart(period, 4, now) // 3 Zeiträume vor dem Rückblick = „normalerweise trainiert“
    val load = relativeLoad(muscleScoreOf(regionScore(store.sets.filter { it.time in start until end })))
    val usual = muscleScoreOf(regionScore(store.sets.filter { it.time in earlier until start })).filterValues { it > 0f }.keys
    return Review(
        start = start,
        end = end,
        now = periodSummary(store, start, end),
        before = periodSummary(store, prev, start),
        records = recordEvents(store).filter { it.set.time in start until end },
        topMuscles = load.entries.filter { it.value > 0f }.sortedByDescending { it.value }.take(3).map { it.key },
        neglected = MUSCLES.keys.filter { it in usual && (load[it] ?: 0f) < 0.2f }
    )
}

/** Veränderung als Text + Farbe: „+12 %“ (grün) / „−5 %“ (rot) bzw. bei Anzahlen „+1“. null = kein Vergleich möglich. */
@Composable
private fun deltaOf(now: Double, before: Double, percent: Boolean): Pair<String, Color>? {
    if (before <= 0.0 && now <= 0.0) return null
    val diff = now - before
    val text = when {
        percent && before <= 0.0 -> return tr("neu", "new") to MaterialTheme.colorScheme.primary
        percent -> (diff / before * 100).roundToInt().let { if (it > 0) "+$it %" else if (it < 0) "−${-it} %" else "±0 %" }
        else -> diff.roundToInt().let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "±0" }
    }
    val color = when {
        diff > 0 -> Color(0xFF43A047)
        diff < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    return text to color
}

@Composable
private fun ReviewStat(label: String, value: String, delta: Pair<String, Color>?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(delta?.first ?: " ", style = MaterialTheme.typography.labelMedium, color = delta?.second ?: Color.Unspecified)
    }
}

/** Trainingszeit kurz: „3 h 20 min“ bzw. „45 min“ */
fun fmtHoursMinutes(ms: Long): String {
    val min = (ms / 60000).toInt()
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

/** Karte „Rückblick“ mit Umschalter Woche/Monat. Nichts anzeigen, solange es in beiden Zeiträumen kein Training gibt. */
@Composable
fun ReviewCard(store: GymStore) {
    var periodName by rememberSaveable { mutableStateOf(ReviewPeriod.WEEK.name) }
    val period = ReviewPeriod.valueOf(periodName)
    val r = review(store, period)
    // Ganz ohne Trainings (auch im Monat) bleibt die Karte weg
    if (r.now.sessions == 0 && r.before.sessions == 0 && period == ReviewPeriod.WEEK &&
        review(store, ReviewPeriod.MONTH).let { it.now.sessions == 0 && it.before.sessions == 0 }) return
    val vs = if (period == ReviewPeriod.WEEK) tr("ggü. Vorwoche", "vs. week before") else tr("ggü. Vormonat", "vs. month before")

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Rückblick", "Review"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentChip(tr("Letzte Woche", "Last week"), period == ReviewPeriod.WEEK, { periodName = ReviewPeriod.WEEK.name }, Modifier.weight(1f))
                SegmentChip(tr("Letzter Monat", "Last month"), period == ReviewPeriod.MONTH, { periodName = ReviewPeriod.MONTH.name }, Modifier.weight(1f))
            }
            Text(
                "${fmtDate(r.start)} – ${fmtDate(r.end - 1)} · $vs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (r.now.sessions == 0) {
                Text(
                    tr("Keine Trainingseinheiten in diesem Zeitraum.", "No workouts in this period."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Row {
                ReviewStat(tr("Einheiten", "Workouts"), r.now.sessions.toString(),
                    deltaOf(r.now.sessions.toDouble(), r.before.sessions.toDouble(), false), Modifier.weight(1f))
                ReviewStat(tr("Sätze", "Sets"), r.now.sets.toString(),
                    deltaOf(r.now.sets.toDouble(), r.before.sets.toDouble(), false), Modifier.weight(1f))
                ReviewStat(tr("Volumen", "Volume"), "${fmtVolume(r.now.volume)} kg",
                    deltaOf(r.now.volume, r.before.volume, true), Modifier.weight(1f))
            }
            Row {
                ReviewStat(tr("Trainingszeit", "Training time"), if (r.now.duration > 0) fmtHoursMinutes(r.now.duration) else "–",
                    deltaOf(r.now.duration.toDouble(), r.before.duration.toDouble(), true).takeIf { r.now.duration > 0 }, Modifier.weight(1f))
                ReviewStat("🔥 kcal", r.now.kcal?.let { "~$it" } ?: "–",
                    r.now.kcal?.let { deltaOf(it.toDouble(), (r.before.kcal ?: 0).toDouble(), true) }, Modifier.weight(1f))
                ReviewStat(tr("Rekorde", "Records"), "🏆 ${r.records.size}", null, Modifier.weight(1f))
            }
            if (r.records.isNotEmpty()) ReviewLine("🏆",
                r.records.map { it.exercise }.distinct().take(3).joinToString(", ") { exName(it) } +
                    if (r.records.map { it.exercise }.distinct().size > 3) " …" else "")
            if (r.topMuscles.isNotEmpty()) ReviewLine("💪", tr("Am meisten: ", "Most: ") + r.topMuscles.joinToString(", ") { muscleLabel(it) })
            if (r.neglected.isNotEmpty()) ReviewLine("💤", tr("Kaum trainiert: ", "Barely trained: ") + r.neglected.joinToString(", ") { muscleLabel(it) })
        }
    }
}

@Composable
private fun ReviewLine(icon: String, text: String) {
    Row {
        Text(icon, modifier = Modifier.width(28.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
