package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ---------- Tab „Übersicht“ (Dashboard) ----------

/** Ein neuer Rekord: Übung, Satz, an dem er aufgestellt wurde. */
data class RecordEvent(val exercise: String, val set: GymSet)

/**
 * Alle Rekorde chronologisch: Ein Trainingstag ist ein Rekord, wenn sein bester Wert
 * (Gewicht bzw. Dauer) alles davor übertrifft. Der allererste Tag zählt nicht.
 */
fun recordEvents(store: GymStore): List<RecordEvent> {
    val events = mutableListOf<RecordEvent>()
    store.sets.top().groupBy { it.exercise }.forEach { (name, list) -> // ohne Aufwärm-/Drop-Sätze
        val byTime = store.groupOf(name).isCardio || isTimed(name)
        val metric: (GymSet) -> Double = if (byTime) { s -> s.reps.toDouble() } else { s -> s.weight }
        var best: Double? = null
        list.sortedBy { it.time }.groupBy { dayKey(it.time) }.values.forEach { day ->
            val top = day.maxBy(metric)
            val prev = best
            if (prev != null && metric(top) > prev) events.add(RecordEvent(name, top))
            if (prev == null || metric(top) > prev) best = metric(top)
        }
    }
    return events.sortedByDescending { it.set.time }
}

/**
 * Nächstes Workout nach dem zuletzt trainierten: das folgende in der Liste mit mindestens einer Übung
 * (nach dem letzten wieder das erste). Noch nie trainiert oder Workout gelöscht → das erste.
 */
fun nextWorkoutAfter(workouts: List<Workout>, lastId: Long?): Workout? {
    val i = workouts.indexOfFirst { it.id == lastId }
    if (i < 0) return workouts.firstOrNull()
    return (1..workouts.size).map { workouts[(i + it) % workouts.size] }.firstOrNull { it.exercises.isNotEmpty() }
        ?: workouts[i]
}

/** Wochen in Folge mit mindestens einem Training (die laufende Woche zählt, wenn schon trainiert). */
fun weekStreak(store: GymStore): Int {
    val weeks = allSessions(store).map { startOfWeek(it.start) }.toSet()
    var w = startOfWeek()
    if (w !in weeks) w = previousWeek(w) // diese Woche noch nichts → Serie endet nicht sofort
    var n = 0
    while (w in weeks) { n++; w = previousWeek(w) }
    return n
}

fun previousWeek(weekStart: Long): Long =
    Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis

/** Volumen (kg × Wdh.) pro Woche für die letzten `count` Wochen, älteste zuerst. */
fun weeklyVolumes(store: GymStore, count: Int): List<Pair<Long, Double>> {
    val starts = generateSequence(startOfWeek()) { previousWeek(it) }.take(count).toList().reversed()
    val strength = store.sets.filter { it.isWork && !store.groupOf(it.exercise).isCardio && !isTimed(it.exercise) }
    return starts.map { ws ->
        val we = Calendar.getInstance().apply { timeInMillis = ws; add(Calendar.DAY_OF_MONTH, 7) }.timeInMillis
        ws to strength.filter { it.time in ws until we }.sumOf { it.weight * it.reps }
    }
}

/**
 * Geschätzter Verbrauch (kcal) pro Woche für die letzten `count` Wochen, älteste zuerst (alle Trainings inkl. freier).
 * null, wenn es noch kein Körpergewicht gibt (dann ist keine Schätzung möglich).
 */
fun weeklyKcal(store: GymStore, count: Int): List<Pair<Long, Double>>? {
    if (store.weights.isEmpty()) return null
    val starts = generateSequence(startOfWeek()) { previousWeek(it) }.take(count).toList().reversed()
    val sessions = allSessions(store)
    return starts.map { ws ->
        val we = Calendar.getInstance().apply { timeInMillis = ws; add(Calendar.DAY_OF_MONTH, 7) }.timeInMillis
        ws to sessions.filter { it.start in ws until we }.sumOf { sessionStats(store, it).kcal ?: 0 }.toDouble()
    }
}

@Composable
fun DashboardTab(
    store: GymStore,
    modifier: Modifier,
    onStartWorkout: (Long) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onGoTo: (Int) -> Unit,
    onNewWorkout: () -> Unit = { onGoTo(2) }
) {
    val sessions = allSessions(store)
    val weekStart = startOfWeek()
    val weekSessions = sessions.filter { it.start >= weekStart }
    val weekStats = weekSessions.map { sessionStats(store, it) }
    val streak = weekStreak(store)
    val goal = store.weeklyGoal
    var editGoal by remember { mutableStateOf(false) }
    // Zeitraum für „Trainierte Muskeln“ und „Sätze pro Muskelgruppe“
    var period by rememberSaveable { mutableStateOf(StatPeriod.WEEK) }

    // Schnellstart: mit Trainingsplan „Heute dran“ bzw. das nächste geplante Workout, sonst „Weiter mit“
    // = das Workout NACH dem zuletzt trainierten (Reihenfolge der Workout-Liste, am Ende wieder vorn)
    val lastSession = store.sessions.maxByOrNull { it.start }
    val pick = planPick(store)
    val nextWorkout = pick?.workout ?: nextWorkoutAfter(store.workouts, lastSession?.workoutId)
    val nextTitle = pick?.title ?: tr("Weiter mit", "Continue with")

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Wochenziel + Serie
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { editGoal = true; Hints.used(Hints.WEEK_GOAL) }
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    PercentRing((weekSessions.size.toFloat() / goal).coerceAtMost(1f), Modifier.size(84.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Wochenziel", "Weekly goal"), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            // Ziel erreicht/übertroffen → nicht „5 von 3“, sondern „5 Trainings ✓“ (Ziel steht darunter)
                            if (weekSessions.size >= goal) plural(weekSessions.size, tr("Trainingseinheit", "workout"), tr("Trainingseinheiten", "workouts")) + " ✓"
                            else tr("${weekSessions.size} von $goal Trainingseinheiten", "${weekSessions.size} of $goal workouts"),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (streak > 0) "🔥 " + tr(
                                "${plural(streak, "Woche", "Wochen")} in Folge",
                                "${plural(streak, "week", "weeks")} in a row"
                            ) else tr("Starte deine Serie!", "Start your streak!"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // „tippen zum Ändern“ nur, bis das Ziel einmal geöffnet wurde (Hints)
                        val hint = Hints.show(Hints.WEEK_GOAL)
                        if (hint || weekSessions.size >= goal) Text(
                            if (weekSessions.size >= goal) tr("Ziel $goal/Woche", "Goal $goal/week") + (if (hint) tr(" · tippen zum Ändern", " · tap to change") else "")
                            else tr("Tippen, um das Ziel zu ändern", "Tap to change goal"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Erholungs-Check (Recovery.kt, nur mit Health Connect und Daten von heute)
        if (Recovery.rating() != null) item { RecoveryCard() }

        // Weiter mit … (Schnellstart)
        if (nextWorkout != null) {
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onOpenWorkout(nextWorkout.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nextTitle, style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(nextWorkout.name, style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            val last = store.sessionsFor(nextWorkout.id).firstOrNull()
                            Text(
                                plural(nextWorkout.exercises.size, tr("Übung", "exercise"), tr("Übungen", "exercises")) +
                                    (last?.let { tr(" · zuletzt ", " · last ") + fmtDate(it.start) } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (nextWorkout.exercises.isNotEmpty()) {
                            FilledIconButton(onClick = { onStartWorkout(nextWorkout.id) }, modifier = Modifier.size(56.dp)) {
                                AppIcon(Ico.PLAY)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("Willkommen! 👋", "Welcome! 👋"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("So geht's: 1. Workout aus einer Vorlage anlegen · 2. Training starten · 3. Sätze eintragen.", "How it works: 1. Create a workout from a template · 2. Start training · 3. Log your sets."),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onNewWorkout) { Text(tr("Vorlage wählen", "Choose a template")) }
                    }
                }
            }
        }

        // Kennzahlen dieser Woche (mit Überschrift, damit der Zeitraum klar ist)
        item { Column {
            Text(
                tr("Diese Woche", "This week"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(tr("Sätze", "Sets"), weekStats.sumOf { it.sets }.toString(), Modifier.weight(1f))
                StatBox(tr("Volumen", "Volume"), "${fmtVolume(weekStats.sumOf { it.volume })} kg", Modifier.weight(1f))
                val kcal = weekStats.mapNotNull { it.kcal }.sum()
                StatBox("🔥 kcal", if (kcal > 0) "~${fmtKcal(kcal)}" else "–", Modifier.weight(1f))
            }
        } }

        // Rückblick: letzte Woche bzw. letzter Monat im Vergleich (Review.kt)
        if (store.sets.isNotEmpty()) item { ReviewCard(store) }

        // Muskel-Heatmap (Meerschweinchen)
        if (store.sets.isNotEmpty()) item { MuscleHeatCard(store, period, onPeriod = { period = it }) }

        // Diagramm: Volumen pro Woche (8 Wochen)
        val volumes = weeklyVolumes(store, 8)
        if (volumes.any { it.second > 0 }) {
            item {
                ChartCard(tr("Volumen pro Woche", "Weekly volume"), tr("Letzte 8 Wochen · aktuelle Woche hervorgehoben", "Last 8 weeks · current week highlighted")) {
                    HistoryBars(volumes.map { it.second }, volumes.map { fmtWeek(it.first) }) { "${fmtVolume(it)} kg" }
                }
            }
        }

        // Diagramm: geschätzter Kalorienverbrauch pro Woche (8 Wochen), nur mit Körpergewicht
        val kcalWeeks = weeklyKcal(store, 8)
        if (kcalWeeks != null && kcalWeeks.any { it.second > 0 }) {
            item {
                ChartCard(tr("🔥 Kalorien pro Woche", "🔥 Calories per week"), tr("Letzte 8 Wochen · geschätzt", "Last 8 weeks · estimated")) {
                    HistoryBars(kcalWeeks.map { it.second }, kcalWeeks.map { fmtWeek(it.first) }) { "~${fmtKcal(it)} kcal" }
                }
            }
        }

        // Diagramm: Sätze pro Muskelgruppe (gleicher Zeitraum wie die Heatmap)
        val perGroup = period.filter(store.sets.work()) // ohne Aufwärmsätze
            .groupBy { store.groupOf(it.exercise) }
            .map { (g, l) -> g.label to l.size.toDouble() }
            .sortedByDescending { it.second }
        if (perGroup.isNotEmpty()) {
            item {
                ChartCard(tr("Sätze pro Muskelgruppe", "Sets per muscle group"), period.label) {
                    HorizontalBars(perGroup) { it.toInt().toString() }
                }
            }
        }

        // Neueste Rekorde
        val records = recordEvents(store).take(5)
        if (records.isNotEmpty()) {
            item { Text(tr("Neueste Rekorde", "Latest records"), style = MaterialTheme.typography.titleMedium) }
            items(records, key = { "rec_${it.exercise}_${it.set.id}" }) { r ->
                val g = store.groupOf(r.exercise)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseIcon(r.exercise, g, Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(exName(r.exercise), style = MaterialTheme.typography.titleSmall)
                            Text(fmtDate(r.set.time), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("🏆 " + fmtSet(r.set, g.isCardio), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }

    if (editGoal) {
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text(tr("Wochenziel", "Weekly goal")) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { store.changeWeeklyGoal(goal - 1) }, enabled = goal > 1) {
                        Text("−", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        tr("$goal × pro Woche", "$goal × per week"),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { store.changeWeeklyGoal(goal + 1) }, enabled = goal < 14) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editGoal = false }) { Text("OK") } }
        )
    }
}


