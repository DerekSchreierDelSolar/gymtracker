package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Übersicht nach dem Training (auch später aus „Letzte Trainings“ aufrufbar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(store: GymStore, t: TrainingSession, celebrate: Boolean = false, onDone: () -> Unit) {
    val stats = sessionStats(store, t)
    val wins = remember(t.id, stats.sets) { achievements(store, t, stats) }
    // Konfetti + Vibration nur einmal direkt nach dem Training und nur bei großen Erfolgen (Rekord, Ziel, Wochenziel, Meilenstein)
    var confetti by rememberSaveable(t.id) { mutableStateOf(celebrate && wins.any { it.big }) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(t.id) { if (confetti) celebrateVibration(context) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Trainingsübersicht", "Workout summary")) },
            actions = { TextButton(onClick = onDone) { Text(tr("Fertig", "Done")) } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Erfolge (Achievements.kt) – das Erste, was man nach dem Training sieht
            item { AchievementsCard(wins, fresh = celebrate) }

            // Direkt nach dem Training: Fortschritt der Ziele, zu denen es beigetragen hat
            val touched = if (celebrate) goalsTouched(store, stats) else emptyList()
            if (touched.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp)) {
                        Text(tr("🎯 Deine Ziele", "🎯 Your goals"), style = MaterialTheme.typography.titleMedium)
                        touched.forEach { GoalRow(store, it) }
                    }
                }
            }

            item {
                Text(t.workoutName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    fmtDateTime(t.start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PercentRing(stats.completion, Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            t.id < 0 -> tr("Freies Training – zählt als 100 %", "Free workout – counts as 100 %")
                            stats.total == 0 -> tr("Ohne Ziel-Sätze – zählt als 100 %", "No target sets – counts as 100 %")
                            else -> tr(
                                "${stats.done} von ${stats.total} Ziel-Sätzen erledigt",
                                "${stats.done} of ${stats.total} target sets done"
                            )
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            // Kennzahlen: max. 3 pro Zeile
            item {
                val tiles = buildList {
                    if (t.id >= 0) add(tr("Dauer", "Duration") to fmtDuration(t.end - t.start))
                    add(tr("Sätze", "Sets") to stats.sets.toString())
                    stats.kcal?.let {
                        add((if (stats.kcalFromHr) tr("❤ Verbrauch", "❤ Burned") else tr("🔥 Verbrauch", "🔥 Burned")) to "~${fmtKcal(it)} kcal")
                    }
                    if (stats.volume > 0) add(tr("Volumen", "Volume") to "${fmtVolume(stats.volume)} kg")
                    if (stats.cardioMinutes > 0) add("Cardio" to "${stats.cardioMinutes} min")
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, value) -> StatBox(label, value, Modifier.weight(1f)) }
                        }
                    }
                    if (stats.kcal == null) {
                        Text(
                            tr(
                                "Tipp: Trag unter „Körper“ dein Gewicht ein, dann schätzt die App den Kalorienverbrauch.",
                                "Tip: enter your weight under \"Body\" and the app will estimate calories burned."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Puls (aus Health Connect, z. B. von einer Smartwatch)
            t.hr?.let { hr -> item { HeartRateCard(store, t, hr, stats.kcalFromHr) } }
            // Puls pro Satz: Spitze, Erholung nach 1 min, vor dem nächsten Satz (braucht feine Pulsdaten)
            val setHearts = setHeartRates(store, t, stats.results)
            if (setHearts.isNotEmpty()) item { SetHeartCard(setHearts) }

            // Heatmap: welche Muskeln dieses Training belastet hat (Summe der Sätze)
            val sessionSets = stats.results.flatMap { it.sets }
            if (sessionSets.isNotEmpty()) item {
                MuscleHeatCard(
                    title = tr("Trainierte Muskeln", "Trained muscles"),
                    score = regionScore(sessionSets),
                    showSets = true,
                    empty = tr("Für diese Übungen sind keine Muskeln hinterlegt.", "No muscles known for these exercises.")
                )
            }

            // Diagramm 1: Volumen pro Übung (nur Kraftübungen mit Sätzen)
            val volumeItems = stats.results
                .filter { !it.group.isCardio && !isTimed(it.name) && it.sets.isNotEmpty() }
                .map { exName(it.name) to it.sets.work().sumOf { s -> s.weight * s.reps } }
                .filter { it.second > 0 }   // Körpergewichtsübungen (0 kg) hätten nur einen leeren Balken
                .sortedByDescending { it.second }
            if (volumeItems.size >= 2) {
                item {
                    ChartCard(tr("Volumen pro Übung", "Volume per exercise"), tr("Gewicht × Wiederholungen", "Weight × reps")) {
                        HorizontalBars(volumeItems, wide = true) { "${fmtVolume(it)} kg" }
                    }
                }
            }

            // Diagramm 2: Verlauf dieses Workouts (letzte bis zu 8 Trainings inkl. diesem)
            if (t.workoutId >= 0) {
                val history = store.sessionsFor(t.workoutId)
                    .filter { it.start <= t.start }
                    .take(8)
                    .reversed()
                if (history.size >= 2) {
                    item {
                        val volumes = history.map { sessionStats(store, it).volume }
                        val prev = volumes[volumes.size - 2]
                        val change = if (prev > 0) Math.round((volumes.last() - prev) / prev * 100).toInt() else null
                        ChartCard(
                            tr("Verlauf: ${t.workoutName}", "Trend: ${t.workoutName}"),
                            when {
                                change == null -> tr("Volumen der letzten Trainingseinheiten", "Volume of recent sessions")
                                change >= 0 -> tr("+$change % Volumen gegenüber letztem Mal", "+$change % volume vs. last time")
                                else -> tr("$change % Volumen gegenüber letztem Mal", "$change % volume vs. last time")
                            }
                        ) {
                            HistoryBars(volumes, history.map { fmtShortDate(it.start) }) { "${fmtVolume(it)} kg" }
                        }
                    }
                }
            }

            items(stats.results, key = { it.name }) { r ->
                val cardio = r.group.isCardio
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseIcon(r.name, r.group, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (r.target == 0) "${exName(r.name)}  ·  optional"
                                else "${exName(r.name)}  ·  ${r.sets.count { it.isWork }}/${r.target}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (r.target > 0 && r.sets.count { it.isWork } >= r.target) completionColor(1f)
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (r.sets.isEmpty()) {
                                Text(
                                    if (r.target == 0) tr("nicht gemacht", "not done") else tr("übersprungen", "skipped"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    r.sets.joinToString(", ") { fmtSet(it, cardio) + if (it.type != SetType.NORMAL) " (${it.type.short})" else "" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                val volume = r.sets.work().sumOf { it.weight * it.reps }
                                if (!cardio && !isTimed(r.name) && volume > 0) {  // Körpergewicht (0 kg): keine „Volumen: 0 kg“-Zeile
                                    Text(
                                        tr("Volumen: ", "Volume: ") + "${fmtVolume(volume)} kg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ComparisonLine(r)
                            }
                        }
                        if (r.record) Text("🏆", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(tr("Fertig", "Done")) }
            }
        }
    }
    if (confetti) ConfettiOverlay { confetti = false }
    }
}

/**
 * Vergleich mit dem letzten Mal: Kraft = schwerster Satz, Cardio = Minuten gesamt.
 * Pfeil + Text (nicht nur Farbe), damit es auch ohne Farbwahrnehmung lesbar ist.
 */
@Composable
fun ComparisonLine(r: ExerciseResult) {
    val sets = r.sets.top() // ohne Aufwärm-/Drop-Sätze (wie r.previous)
    if (r.previous.isEmpty() || sets.isEmpty()) return
    val cardio = r.group.isCardio
    val timed = isTimed(r.name)
    val now = when {
        cardio -> sets.sumOf { it.reps }.toDouble()
        timed -> sets.maxOf { it.reps }.toDouble()
        else -> sets.maxOf { it.weight }
    }
    val before = when {
        cardio -> r.previous.sumOf { it.reps }.toDouble()
        timed -> r.previous.maxOf { it.reps }.toDouble()
        else -> r.previous.maxOf { it.weight }
    }
    val diff = now - before
    fun fmtDiff(d: Double) = when {
        cardio -> "${fmtW(d)} min"
        timed -> "${d.toInt()} s"
        else -> "${fmtW(d)} kg"
    }
    val (text, color) = when {
        diff > 0.001 -> "▲ +${fmtDiff(diff)}" to completionColor(1f)
        diff < -0.001 -> "▼ −${fmtDiff(-diff)}" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "= ${tr("gleich", "same")}" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        "$text " + tr("ggü. letztem Mal", "vs. last time"),
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



/**
 * Karte „Puls“: Ø / max / min, Verlauf je Minute und – mit Geburtsjahr im Profil – Zeit in den Pulszonen
 * (Anteil an der geschätzten Maximalfrequenz 220 − Alter).
 */
@Composable
fun HeartRateCard(store: GymStore, t: TrainingSession, hr: HeartRate, kcalFromHr: Boolean) {
    val curve = hr.curve.filter { it > 0 }
    ChartCard(
        tr("❤ Puls", "❤ Heart rate"),
        tr("Ø ${hr.avg} · max ${hr.max} · min ${hr.min} bpm", "avg ${hr.avg} · max ${hr.max} · min ${hr.min} bpm")
    ) {
        if (curve.size >= 2) LineChart(curve.map { it.toDouble() }, "bpm", headline = "Ø ${hr.avg} bpm", dots = false)
        val maxHr = maxHeartRate(store, t.start)
        if (maxHr != null && curve.isNotEmpty()) {
            val zones = heartZoneNames()
            val minutes = heartZoneMinutes(hr, maxHr)
            Spacer(Modifier.height(8.dp))
            Text(tr("Pulszonen", "Heart rate zones"), style = MaterialTheme.typography.labelLarge)
            Text(
                tr("< 60 · 60–70 · 70–80 · 80–90 · > 90 % der max. Herzfrequenz (~$maxHr bpm = 220 − Alter)",
                    "< 60 · 60–70 · 70–80 · 80–90 · > 90 % of max heart rate (~$maxHr bpm = 220 − age)"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            HorizontalBars(zones.mapIndexed { i, z -> z to minutes[i].toDouble() }) { "${it.toInt()} min" }
        }
        val p = store.profile
        if (!kcalFromHr && (p.sex == null || p.birthYear == null)) Text(
            tr("Tipp: Mit Geschlecht und Geburtsjahr im Profil (Einstellungen) berechnet die App die kcal aus dem Puls.",
                "Tip: with sex and birth year in your profile (settings) the app calculates calories from heart rate."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Karte „Puls pro Satz“: je Übung und Satz Spitze → 1 min später (Erholung) und Puls vor dem nächsten Satz;
 * ⚠ = vor dem nächsten Satz nicht einmal halb zurück zur Ausgangslage (Pause evtl. zu kurz).
 */
@Composable
fun SetHeartCard(items: List<SetHeart>) {
    val warn = MaterialTheme.colorScheme.error
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val drops = items.mapNotNull { it.drop60 }
    ChartCard(
        tr("❤ Puls pro Satz", "❤ Heart rate per set"),
        tr("Spitze → 1 min später · vor dem nächsten Satz", "Peak → 1 min later · before the next set")
    ) {
        // Nach Übung gruppiert, in der Reihenfolge des Trainings
        items.groupBy { it.set.exercise }.forEach { (exercise, list) ->
            Text(exName(exercise), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            list.forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.label, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.width(28.dp))
                    Text(
                        "${h.peak}" + (h.after60?.let { " → $it (−${h.peak - it})" } ?: "") + " bpm",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                    )
                    h.beforeNext?.let {
                        Text(
                            (if (h.recovered == false) "⚠ " else "") + tr("vor nächstem: $it", "before next: $it"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (h.recovered == false) warn else muted
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (drops.isNotEmpty()) {
            val avg = Math.round(drops.average()).toInt()
            Text(
                tr("Ø Erholung 1 min nach dem Satz: −$avg bpm", "Avg recovery 1 min after the set: −$avg bpm") +
                    tr(" (über 12 normal, ab 20 gut trainiert)", " (above 12 normal, 20+ well trained)"),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (items.any { it.recovered == false }) Text(
            tr("⚠ = Puls vor dem nächsten Satz noch nicht einmal halb gesunken – die Pause war evtl. zu kurz.",
                "⚠ = heart rate hadn't even dropped halfway before the next set – the rest may have been too short."),
            style = MaterialTheme.typography.bodySmall, color = muted
        )
    }
}
