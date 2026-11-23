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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------- Tab „Übersicht“ (Dashboard) ----------

/** Ein neuer Rekord: Übung, Satz, an dem er aufgestellt wurde. */
data class RecordEvent(val exercise: String, val set: GymSet)

/**
 * Alle Rekorde chronologisch: Ein Trainingstag ist ein Rekord, wenn sein bester Wert
 * (Gewicht bzw. Dauer) alles davor übertrifft. Der allererste Tag zählt nicht.
 */
fun recordEvents(store: GymStore): List<RecordEvent> = store.memo("recordEvents") {
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
    events.sortedByDescending { it.set.time }
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

/** Serie: Wochen in Folge (inkl. Joker-Wochen); `jokers` = davon per Joker überbrückt, `lastJoker` = Woche des jüngsten Jokers. */
data class Streak(val weeks: Int, val jokers: Int, val lastJoker: Long?)

private fun monthOf(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

/**
 * Serie ab Woche `from` rückwärts (Roadmap Schritt 8, „Serie ohne Angst“). Joker: pro Kalendermonat (des Wochen-Montags)
 * überbrückt eine Woche ohne Training die Serie, wenn die Woche davor trainiert wurde – also nur einzelne Lücken.
 * Joker-Wochen zählen mit. Web: streakFrom in stats.js.
 */
fun streakFrom(weeks: Set<Long>, from: Long): Streak {
    var w = from
    var n = 0
    var jokers = 0
    var last: Long? = null
    val used = mutableSetOf<Int>()
    while (true) {
        if (w in weeks) n++
        else if (monthOf(w) !in used && previousWeek(w) in weeks) { used += monthOf(w); jokers++; n++; if (last == null) last = w }
        else break
        w = previousWeek(w)
    }
    return Streak(n, jokers, last)
}

/** Aktuelle Serie (die laufende Woche zählt, wenn schon trainiert; sonst endet sie nicht sofort). */
fun weekStreakInfo(store: GymStore): Streak {
    val weeks = allSessions(store).map { startOfWeek(it.start) }.toSet()
    var w = startOfWeek()
    if (w !in weeks) w = previousWeek(w)
    return streakFrom(weeks, w)
}

fun weekStreak(store: GymStore): Int = weekStreakInfo(store).weeks

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
    val streakInfo = weekStreakInfo(store)
    val streak = streakInfo.weeks
    // Joker in den letzten 2 Wochen eingesetzt → in der Karte erwähnen
    val jokerRecent = streakInfo.lastJoker?.let { it >= previousWeek(previousWeek(weekStart)) } == true
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
        // Der Morgen danach: Neuanfang + letztes Training (Morning.kt)
        item { MorningCard(store) }

        // Wochenziel + Serie
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { editGoal = true; Hints.used(Hints.WEEK_GOAL) }
            ) {
                Row(Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PercentRing((weekSessions.size.toFloat() / goal).coerceAtMost(1f), Modifier.size(84.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Wochenziel", "Weekly goal"), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            // Ziel erreicht/übertroffen → nicht „5 von 3“, sondern „5 Trainings ✓“ (Ziel steht darunter)
                            if (weekSessions.size >= goal) plural(weekSessions.size, tr("Training", "workout"), tr("Trainings", "workouts")) + " ✓"
                            else tr("${weekSessions.size} von $goal Trainings", "${weekSessions.size} of $goal workouts"),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (streak > 0) "🔥 " + tr(
                                "${plural(streak, "Woche", "Wochen")} in Folge",
                                "${plural(streak, "week", "weeks")} in a row"
                            ) + (if (jokerRecent) tr(" · 🃏 Joker hat sie gerettet", " · 🃏 saved by a joker") else "") else tr("Starte deine Serie!", "Start your streak!"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Trainingsblock (Cycle.kt)
                        cycleLine(store)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        // „tippen zum Ändern“ nur, bis das Ziel einmal geöffnet wurde (Hints)
                        val hint = Hints.show(Hints.WEEK_GOAL)
                        if (hint || weekSessions.size >= goal) Text(
                            if (weekSessions.size >= goal) tr("Ziel $goal/Woche", "Goal $goal/week") + (if (hint) tr(" · tippen zum Ändern", " · tap to change") else "")
                            else tr("Tippen, um das Ziel zu ändern", "Tap to change goal"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Kennzahlen dieser Woche (früher eigene Kachel „Diese Woche“)
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val kcal = weekStats.mapNotNull { it.kcal }.sum()
                    WeekStat(tr("Sätze", "Sets"), weekStats.sumOf { it.sets }.toString(), Modifier.weight(1f))
                    WeekStat(tr("Volumen", "Volume"), "${fmtVol(weekStats.sumOf { it.volume })}", Modifier.weight(1f))
                    WeekStat("🔥 kcal", if (kcal > 0) "~${fmtKcal(kcal)}" else "–", Modifier.weight(1f))
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
                            // Vorfreude statt Pflicht: Vorschlag fürs heutige Training (Morning.kt)
                            anticipation(store, nextWorkout, today = pick == null || dueToday(store)?.id == nextWorkout.id)?.let {
                                Text("💡 $it", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
                            }
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

        // Level (Levels.kt) mit Abzeichen-Zeile (Badges.kt) – unter dem Schnellstart, damit der ohne Scrollen sichtbar ist
        item { LevelCard(store) }

        // Langfristige Ziele (Goals.kt) mit den nächsten Meilensteinen (Milestones.kt)
        item { GoalsCard(store) }

        // Rückblick: letzte Woche bzw. letzter Monat im Vergleich (Review.kt)
        if (store.sets.isNotEmpty()) item { ReviewCard(store) }

        // Muskel-Heatmap (Meerschweinchen), darunter aufklappbar „Sätze pro Muskelgruppe“
        if (store.sets.isNotEmpty()) item { MuscleHeatCard(store, period, onPeriod = { period = it }) }

        // Verlauf: Volumen bzw. kcal pro Woche (8 Wochen) in einer Karte mit Umschalter
        val volumes = weeklyVolumes(store, 8)
        val kcalWeeks = weeklyKcal(store, 8)?.takeIf { w -> w.any { it.second > 0 } }
        if (volumes.any { it.second > 0 } || kcalWeeks != null) item { TrendCard(volumes, kcalWeeks) }

        // Neueste Rekorde (kompakt in einer Karte)
        val records = recordEvents(store).take(10)
        if (records.isNotEmpty()) item { RecordsCard(store, records) }
    }

    if (editGoal) {
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text(tr("Wochenziel", "Weekly goal")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text(
                        tr("🔥 Serie = Wochen in Folge mit mindestens einem Training. 🃏 Ein Joker pro Monat überbrückt eine Woche Pause (Urlaub, krank) – die Serie bleibt.",
                            "🔥 Streak = weeks in a row with at least one workout. 🃏 One joker per month bridges a week off (holiday, sick) – the streak stays."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editGoal = false }) { Text("OK") } }
        )
    }
}



/** Kleine Kennzahl in der Wochenziel-Karte (Wert fett, Beschriftung darunter). */
@Composable
private fun WeekStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Karte „Verlauf“: Volumen bzw. geschätzte kcal pro Woche (8 Wochen), Umschalter nur, wenn es kcal gibt. Web: trendCard (home.js). */
@Composable
private fun TrendCard(volumes: List<Pair<Long, Double>>, kcalWeeks: List<Pair<Long, Double>>?) {
    var showKcal by rememberSaveable { mutableStateOf(false) }
    val kcal = showKcal && kcalWeeks != null || volumes.none { it.second > 0 }
    ChartCard(tr("Verlauf pro Woche", "Weekly trend"),
        if (kcal) tr("Letzte 8 Wochen · geschätzt", "Last 8 weeks · estimated")
        else tr("Letzte 8 Wochen · aktuelle Woche hervorgehoben", "Last 8 weeks · current week highlighted")) {
        if (kcalWeeks != null && volumes.any { it.second > 0 }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentChip(tr("Volumen", "Volume"), !kcal, { showKcal = false }, Modifier.weight(1f))
                SegmentChip("🔥 kcal", kcal, { showKcal = true }, Modifier.weight(1f))
            }
        }
        if (kcal && kcalWeeks != null) HistoryBars(kcalWeeks.map { it.second }, kcalWeeks.map { fmtWeek(it.first) }) { "~${fmtKcal(it)}\u00A0kcal" }
        else HistoryBars(volumes.map { it.second }, volumes.map { fmtWeek(it.first) }) { "${fmtVol(it)}" }
    }
}

/** Karte „Neueste Rekorde“: 3 Zeilen, „Alle anzeigen“ klappt bis 10 auf. Web: recordsCard (home.js). */
@Composable
private fun RecordsCard(store: GymStore, records: List<RecordEvent>) {
    var all by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp)) {
            Text(tr("🏆 Neueste Rekorde", "🏆 Latest records"), style = MaterialTheme.typography.titleMedium)
            (if (all) records else records.take(3)).forEach { r ->
                val g = store.groupOf(r.exercise)
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExerciseIcon(r.exercise, g, Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exName(r.exercise), style = MaterialTheme.typography.titleSmall)
                        Text(fmtDate(r.set.time), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(fmtSet(r.set, g.isCardio), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (records.size > 3) TextButton(onClick = { all = !all }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(if (all) tr("Weniger anzeigen", "Show less") else tr("Mehr anzeigen (${records.size})", "Show more (${records.size})"))
            }
            if (records.size <= 3) Spacer(Modifier.height(8.dp))
        }
    }
}
