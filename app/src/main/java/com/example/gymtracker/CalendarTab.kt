package com.example.gymtracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat

// ---------- Kalender ----------

/** Tag als Zahl JJJJMMTT (z. B. 20261118) – praktisch zum Gruppieren und Vergleichen. */
fun dayKey(t: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

/** Monat als fortlaufende Zahl (Jahr * 12 + Monat 0–11), damit ‹ › einfach ±1 rechnen kann. */
fun monthIndex(t: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
}

/** Grün = komplett, Gelb = mindestens die Hälfte, Rot = weniger. */
fun completionColor(p: Float): Color = when {
    p >= 0.999f -> Color(0xFF43A047)
    p >= 0.5f -> Color(0xFFFFA000)
    else -> Color(0xFFE53935)
}

/** Fortschrittsring (Kreisbogen ab 12 Uhr im Uhrzeigersinn) mit optionalem Inhalt in der Mitte. */
@Composable
fun CompletionRing(progress: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val color = completionColor(progress)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.11f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(
                color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** Ring mit Prozentzahl in der Mitte. */
@Composable
fun PercentRing(progress: Float, modifier: Modifier = Modifier) {
    CompletionRing(progress, modifier) {
        Text(
            "${Math.round(progress * 100)}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Tab „Kalender“: Monatsansicht mit Trainingstagen und Details zum gewählten Tag. */
@Composable
fun CalendarTab(
    store: GymStore,
    modifier: Modifier,
    month: Int,
    onMonth: (Int) -> Unit,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val year = month / 12
    val m = month % 12 // 0 = Januar
    val first = Calendar.getInstance().apply { clear(); set(year, m, 1) }
    // Wochentag des 1. als Spalte 0–6 mit Montag = 0 (Calendar: Sonntag = 1, Montag = 2 …)
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val todayKey = dayKey(System.currentTimeMillis())

    // Daten vorbereiten: gespeicherte Trainings + freie Trainings (Sätze ohne Workout, 1 pro Tag)
    val allSessions = allSessions(store)
    val statsBySession = allSessions.associate { it.id to sessionStats(store, it) }
    val sessionsByDay = allSessions.groupBy { dayKey(it.start) }
    // Trainingsplan: Wochentage (1 = Mo … 7 = So) mit geplantem Workout → Punkt im Kalender
    val plannedDays = store.workouts.filter { it.exercises.isNotEmpty() }.flatMap { it.days }.toSet()
    val weekStartKey = dayKey(startOfWeek())
    fun planMark(key: Int, day: Int): PlanMark {
        val weekday = (offset + day - 1) % 7 + 1 // Spalte im Raster, 1 = Montag
        if (weekday !in plannedDays) return PlanMark.NONE
        return when {
            key >= todayKey -> PlanMark.PLANNED
            key >= weekStartKey -> PlanMark.MISSED // verpasst nur in der laufenden Woche (der Plan kann neu sein)
            else -> PlanMark.NONE
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Kopf: ‹ November 2026 ›
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month - 1) }) { AppIcon(Ico.CHEVRON, Modifier.size(24.dp).graphicsLayer { scaleX = -1f }) }
                Text(
                    // "LLLL" = Monatsname in Nominativ („November“), in der App-Sprache
                    SimpleDateFormat("LLLL yyyy", appLocale()).format(first.time),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onMonth(month + 1) }) { AppIcon(Ico.CHEVRON) }
            }
        }

        // Wochentage
        item {
            Row {
                (if (isEnglish) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                else listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")).forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tage als Raster: 7 Spalten, so viele Zeilen wie nötig
        item {
            val rows = (offset + daysInMonth + 6) / 7
            Column {
                for (r in 0 until rows) {
                    Row {
                        for (c in 0 until 7) {
                            val day = r * 7 + c - offset + 1
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                if (day in 1..daysInMonth) {
                                    val key = year * 10000 + (m + 1) * 100 + day
                                    // Bei mehreren Trainings an einem Tag zählt das beste
                                    val best = sessionsByDay[key]?.maxOfOrNull { statsBySession.getValue(it.id).completion }
                                    DayCell(
                                        day = day,
                                        completion = best,
                                        isToday = key == todayKey,
                                        isSelected = key == selectedDay,
                                        onClick = { onSelectDay(key) },
                                        plan = planMark(key, day)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Monatsstatistik
        item {
            val monthSessions = allSessions.filter { dayKey(it.start) / 100 == year * 100 + m + 1 }
            val text = if (monthSessions.isEmpty()) {
                tr("Keine Trainingseinheiten in diesem Monat", "No workouts this month")
            } else {
                val avg = Math.round(monthSessions.map { statsBySession.getValue(it.id).completion }.average() * 100)
                val days = monthSessions.map { dayKey(it.start) }.distinct().size
                val n = monthSessions.size
                val kcal = monthSessions.mapNotNull { statsBySession.getValue(it.id).kcal }.sum()
                tr(
                    "${plural(n, "Trainingseinheit", "Trainingseinheiten")} an ${plural(days, "Tag", "Tagen")} · Ø $avg % abgeschlossen",
                    "${plural(n, "workout", "workouts")} on ${plural(days, "day", "days")} · avg. $avg % completed"
                ) + (if (kcal > 0) " · 🔥 ~${fmtKcal(kcal)} kcal" else "")
            }
            Card(Modifier.fillMaxWidth()) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }

        // Details zum gewählten Tag
        val daySessions = (sessionsByDay[selectedDay] ?: emptyList()).sortedBy { it.start }

        item {
            val c = Calendar.getInstance().apply {
                clear(); set(selectedDay / 10000, selectedDay / 100 % 100 - 1, selectedDay % 100)
            }
            Text(
                SimpleDateFormat(tr("EEEE, dd.MM.yyyy", "EEEE, dd/MM/yyyy"), appLocale()).format(c.time),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (daySessions.isEmpty()) {
                Text(
                    tr("Kein Training an diesem Tag.", "No workout on this day."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(daySessions, key = { it.id }) { t ->
            val stats = statsBySession.getValue(t.id)
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSession(t.id) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PercentRing(stats.completion, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.workoutName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            // Freies Training hat keine echte Dauer (nur Abstand erster–letzter Satz)
                            when {
                                t.id < 0 -> plural(stats.results.size, tr("Übung", "exercise"), tr("Übungen", "exercises")) + " · "
                                stats.total == 0 -> fmtDuration(t.end - t.start) + " · "
                                else -> "${stats.done}/${stats.total} " + tr("Ziel-Sätze", "target sets") +
                                    " · ${fmtDuration(t.end - t.start)} · "
                            } + plural(stats.sets, tr("Satz", "set"), tr("Sätze", "sets")) +
                                (stats.kcal?.let { " · 🔥 ${fmtKcal(it)} kcal" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Legende (Erklärung zum Prozentwert hinter ⓘ)
        item {
            var info by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                listOf(1f to "100 %", 0.5f to "≥ 50 %", 0.25f to "< 50 %").forEach { (p, label) ->
                    CompletionRing(p, Modifier.size(16.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(2.dp))
                }
                if (plannedDays.isNotEmpty()) {
                    PlanDot(PlanMark.PLANNED); Text(tr("geplant", "planned"), style = MaterialTheme.typography.labelSmall)
                    PlanDot(PlanMark.MISSED); Text(tr("verpasst", "missed"), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                IconAction(Ico.INFO, onClick = { info = !info }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (info) Text(
                tr(
                    "Prozent = erledigte Ziel-Sätze. Sätze ohne gestartetes Workout zählen als freies Training mit 100 %. Punkte = Trainingstage laut Plan (verpasst nur in der laufenden Woche).",
                    "Percent = target sets done. Sets logged without starting a workout count as a free workout with 100 %. Dots = planned training days (missed only in the current week)."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Markierung eines Kalendertags laut Trainingsplan */
enum class PlanMark { NONE, PLANNED, MISSED }

@Composable
fun PlanDot(mark: PlanMark, modifier: Modifier = Modifier) {
    if (mark == PlanMark.NONE) return
    // geplant = gefüllter Punkt, verpasst = roter Ring (unterscheidbar auch ohne Farbsehen)
    val base = modifier.size(7.dp).clip(CircleShape)
    Box(
        if (mark == PlanMark.PLANNED) base.background(MaterialTheme.colorScheme.primary)
        else base.border(1.5.dp, MaterialTheme.colorScheme.error, CircleShape)
    )
}

@Composable
fun DayCell(
    day: Int,
    completion: Float?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    plan: PlanMark = PlanMark.NONE
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (completion != null) CompletionRing(completion, Modifier.fillMaxSize().padding(3.dp))
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || completion != null) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        // Plan-Punkt unter der Zahl (nur ohne Training an dem Tag)
        if (completion == null) PlanDot(plan, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}


