package com.example.gymtracker

import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date

// ---------- Eingabezeile für einen Satz (Detailansicht und Trainingsmodus) ----------

/**
 * kg + Wdh. (bzw. Minuten bei Cardio, Sekunden bei Zeitübungen) mit Button.
 * Vorbelegt mit dem letzten Satz der Übung.
 * `remember(exercise)` setzt die Felder zurück, sobald eine andere Übung angezeigt wird.
 * `weightOverride`: wird gesetzt, wenn der Nutzer einen Gewichts-Vorschlag übernimmt.
 */
@Composable
fun SetInput(
    store: GymStore,
    exercise: String,
    onAdded: () -> Unit = {},
    weightOverride: Double? = null
) {
    val cardio = store.groupOf(exercise).isCardio
    val timed = isTimed(exercise)
    val lastSet = store.setsFor(exercise).firstOrNull()
    var weightText by remember(exercise) { mutableStateOf(lastSet?.let { fmtW(it.weight) } ?: "") }
    var repsText by remember(exercise) { mutableStateOf(lastSet?.reps?.toString() ?: "") }
    // Übernommener Vorschlag überschreibt das Gewichtsfeld
    LaunchedEffect(weightOverride) { if (weightOverride != null) weightText = fmtW(weightOverride) }

    // Stoppuhr für Zeitübungen: Startzeitpunkt (null = steht)
    var watchStart by remember(exercise) { mutableStateOf<Long?>(null) }
    var watchNow by remember { mutableLongStateOf(0L) }
    LaunchedEffect(watchStart) {
        while (watchStart != null) {
            watchNow = System.currentTimeMillis()
            delay(100)
        }
    }

    if (timed) {
        val running = watchStart != null
        val elapsedSec = watchStart?.let { ((watchNow - it) / 1000).toInt().coerceAtLeast(0) }
            ?: (repsText.toIntOrNull() ?: 0)
        Card(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Stoppuhr", "Stopwatch"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${elapsedSec / 60}:${(elapsedSec % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        if (running) {
                            // Stopp: gemessene Sekunden ins Feld übernehmen
                            val sec = ((System.currentTimeMillis() - watchStart!!) / 1000).toInt()
                            repsText = sec.coerceAtLeast(1).toString()
                            watchStart = null
                        } else {
                            watchNow = System.currentTimeMillis()
                            watchStart = watchNow
                        }
                    },
                    colors = if (running) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.buttonColors()
                ) {
                    if (!running) AppIcon(Ico.PLAY, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) tr("Stopp", "Stop") else tr("Start", "Start"))
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Gewicht: bei Kraftübungen immer, bei Zeitübungen nur, wenn ein Gerät im Spiel ist
        if (!cardio && (!timed || equipmentOf(exercise) != Equipment.KOERPER)) {
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
        }
        OutlinedTextField(
            value = repsText,
            onValueChange = { repsText = it },
            label = {
                Text(
                    when {
                        cardio -> tr("Minuten", "Minutes")
                        timed -> tr("Sekunden", "Seconds")
                        else -> tr("Wdh.", "Reps")
                    }
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            // Komma als Dezimaltrenner erlauben; bei Cardio (und Zeitübungen ohne Gerät) ist das Gewicht 0
            val w = if (cardio) 0.0 else weightText.replace(',', '.').toDoubleOrNull() ?: if (timed) 0.0 else null
            val r = repsText.trim().toIntOrNull()
            if (w != null && r != null && r > 0) {
                store.addSet(exercise, w, r)
                watchStart = null
                onAdded()
            }
        }, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

// ---------- Einzelner Satz: Zeile + Bearbeiten-Dialog ----------

/**
 * Eine Zeile „1. 60 kg × 8“ mit Stift-Icon. Der Stift öffnet einen Dialog zum Ändern oder Löschen.
 * Wird im Übungs-Verlauf und in der „Heute“-Liste des Trainingsmodus verwendet.
 */
@Composable
fun SetRow(store: GymStore, set: GymSet, number: Int, cardio: Boolean) {
    var editing by remember { mutableStateOf(false) }
    // Ganze Zeile antippen = bearbeiten (kein Stift-Symbol)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { editing = true }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$number. ${fmtSet(set, cardio)}", modifier = Modifier.weight(1f))
        Text(
            SimpleDateFormat("HH:mm", appLocale()).format(Date(set.time)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (editing) {
        EditSetDialog(
            set = set,
            cardio = cardio,
            onDismiss = { editing = false },
            onSave = { w, r -> store.updateSet(set, w, r); editing = false },
            onDelete = { store.deleteSet(set); editing = false }
        )
    }
}

/**
 * Dialog zum Ändern eines Satzes. „Löschen“ fragt beim ersten Tippen nach
 * („Wirklich löschen?“) – erst der zweite Tipp löscht.
 */
@Composable
fun EditSetDialog(
    set: GymSet,
    cardio: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit,
    onDelete: () -> Unit
) {
    var weightText by remember { mutableStateOf(fmtW(set.weight)) }
    var repsText by remember { mutableStateOf(set.reps.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val w = if (cardio) 0.0 else weightText.replace(',', '.').toDoubleOrNull()
    val r = repsText.trim().toIntOrNull()
    val valid = w != null && w >= 0 && r != null && r > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Eintrag bearbeiten", "Edit entry")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    fmtDateTime(set.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!cardio) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text("kg") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it },
                        label = {
                            Text(
                                when {
                                    cardio -> tr("Minuten", "Minutes")
                                    isTimed(set.exercise) -> tr("Sekunden", "Seconds")
                                    else -> tr("Wdh.", "Reps")
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                // Löschen: rot und zweistufig
                TextButton(
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    AppIcon(Ico.DELETE, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (confirmDelete) tr("Wirklich löschen? Nochmal tippen", "Really delete? Tap again")
                        else tr("Eintrag löschen", "Delete entry")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(w!!, r!!) }, enabled = valid) { Text(tr("Speichern", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

// ---------- Bildschirm 2: Übung mit Sätzen & Fortschritt ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(store: GymStore, exercise: String, onBack: () -> Unit) {
    val group = store.groupOf(exercise)
    val cardio = group.isCardio
    val timed = isTimed(exercise)
    val history = store.setsFor(exercise)
    var confirmDelete by remember { mutableStateOf(false) }
    var editMuscles by remember { mutableStateOf(false) }
    // Vom Nutzer übernommener Gewichts-Vorschlag (füllt das kg-Feld)
    var appliedWeight by remember { mutableStateOf<Double?>(null) }

    // Reiter: Training (Eingabe + Verlauf), Fortschritt, Info (Muskeln + Wiki)
    val tabs = listOf(tr("Training", "Training"), tr("Fortschritt", "Progress"), tr("Info", "Info"))
    var tab by rememberSaveable(exercise) { mutableIntStateOf(0) }

    // Nach Tag gruppiert, neuester Tag zuerst
    val days = history.groupBy { fmtDate(it.time) }

    // Übungsbild als durchsichtiger Hintergrund; Kopfzeile und Reiter dann ebenfalls durchsichtig
    val img = hasExerciseImage(exercise)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    ExerciseBackdrop(exercise)
    Scaffold(containerColor = if (img) Color.Transparent else MaterialTheme.colorScheme.background, topBar = {
        Column {
            TopAppBar(
                colors = if (img) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
                else TopAppBarDefaults.topAppBarColors(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseIcon(exercise, group, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(exName(exercise))
                    }
                },
                navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
                actions = { IconAction(Ico.DELETE, onClick = { confirmDelete = true }) }
            )
            PrimaryTabRow(
                selectedTabIndex = tab.coerceAtMost(tabs.size - 1),
                containerColor = if (img) Color.Transparent else TabRowDefaults.primaryContainerColor
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        text = { Text(label, maxLines = 1) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (tab) {
                // ----- Training: Rekord-Zeile, Vorschlag, Eingabe, Verlauf pro Tag -----
                0 -> {
                    item { RecordLine(history, cardio, timed) }
                    item { ProgressionHint(store, exercise, onApply = { appliedWeight = it }) }
                    item { SetInput(store, exercise, weightOverride = appliedWeight) }
                    if (days.isNotEmpty()) item {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(tr("Verlauf", "History"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("Satz antippen zum Bearbeiten", "Tap a set to edit"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    days.forEach { (day, daySets) ->
                        item(key = day) {
                            Card(Modifier.fillMaxWidth(), colors = glassCardColors()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        if (cardio)
                                            "$day · " + plural(daySets.size, tr("Einheit", "session"), tr("Einheiten", "sessions")) +
                                                " · ${daySets.sumOf { it.reps }} " + tr("Min gesamt", "min total")
                                        else if (timed)
                                            "$day · " + plural(daySets.size, tr("Satz", "set"), tr("Sätze", "sets")) +
                                                " · " + tr("gesamt ", "total ") + fmtSeconds(daySets.sumOf { it.reps })
                                        else
                                            "$day · " + plural(daySets.size, tr("Satz", "set"), tr("Sätze", "sets")) +
                                                " · ${fmtW(daySets.sumOf { it.weight * it.reps })} " + tr("kg Volumen", "kg volume"),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    // Neuester Satz oben; Nummer bleibt die Reihenfolge im Training
                                    val chrono = daySets.sortedBy { it.time }
                                    chrono.withIndex().reversed().forEach { (i, s) -> SetRow(store, s, i + 1, cardio) }
                                }
                            }
                        }
                    }
                }

                // ----- Fortschritt: Rekord, Zuwachs, Diagramm -----
                1 -> item { ExerciseProgress(history, cardio, timed) }

                // ----- Info: Muskeln (Vorder-/Rückseite) + Wiki (Ausführung, Hinweise) -----
                else -> {
                    val muscles = musclesOf(exercise)
                    val custom = catalogGroup(exercise) == null
                    if (muscles != null) {
                        item { MuscleSideCard(BodyView.FRONT, muscles.primary, muscles.secondary, glassCardColors()) }
                        item { MuscleSideCard(BodyView.BACK, muscles.primary, muscles.secondary, glassCardColors()) }
                    } else item {
                        Text(tr("Noch keine Muskeln festgelegt.", "No muscles set yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (custom) item {
                        TextButton(onClick = { editMuscles = true }) {
                            Text(if (muscles != null) tr("Muskeln bearbeiten", "Edit muscles") else tr("Muskeln festlegen", "Set muscles"))
                        }
                    }
                    if (wikiFor(exercise) != null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            WikiContent(exercise, showHeader = false, showMuscles = false)
                        }
                    }
                }
            }
        }
    }

    }

    if (editMuscles) MuscleEditDialog(store, exercise, onDismiss = { editMuscles = false })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Übung löschen?", "Delete exercise?")) },
            text = {
                Text(
                    tr(
                        "„${exName(exercise)}“ und alle zugehörigen Sätze werden gelöscht.",
                        "\"${exName(exercise)}\" and all of its sets will be deleted."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.deleteExercise(exercise)
                    onBack()
                }) { Text(tr("Löschen", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen", "Cancel")) }
            }
        )
    }
}



// ---------- Reiter „Fortschritt“ ----------

/** Gruppierung der Fortschritts-Werte. */
private enum class ProgressBucket(val de: String, val en: String) { DAY("Tage", "Days"), WEEK("Wochen", "Weeks"), MONTH("Monate", "Months") }

/** Kennzahl im Diagramm (Kraftübungen). */
private enum class ProgressMetric(val de: String, val en: String) { MAX("Max. Gewicht", "Max weight"), ORM("Geschätztes 1RM", "Estimated 1RM"), VOLUME("Volumen", "Volume") }

/**
 * Rekord, Zuwachs seit dem ersten Training und Verlaufsdiagramm.
 * Kraft: Max. Gewicht / geschätztes 1RM / Volumen; Cardio: Minuten; Zeitübungen: längste Zeit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseProgress(history: List<GymSet>, cardio: Boolean, timed: Boolean) {
    var bucket by rememberSaveable { mutableStateOf(ProgressBucket.DAY) }
    var metric by rememberSaveable { mutableStateOf(ProgressMetric.MAX) }
    if (history.isEmpty()) {
        Text(tr("Noch keine Sätze eingetragen.", "No sets logged yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val strength = !cardio && !timed
    val unit = when { cardio -> "min"; timed -> "s"; else -> "kg" }

    // Wert eines Zeitabschnitts
    fun value(l: List<GymSet>): Double = when {
        cardio -> l.sumOf { it.reps }.toDouble()
        timed -> l.maxOf { it.reps }.toDouble()
        metric == ProgressMetric.ORM -> Math.round(l.maxOf { oneRepMax(it.weight, it.reps) } * 2) / 2.0
        metric == ProgressMetric.VOLUME -> l.sumOf { it.weight * it.reps }
        else -> l.maxOf { it.weight }
    }
    // Zeitabschnitte, älteste zuerst
    val key: (Long) -> Int = when (bucket) {
        ProgressBucket.DAY -> { t -> dayKey(t) }
        ProgressBucket.WEEK -> { t -> dayKey(startOfWeek(t)) }
        ProgressBucket.MONTH -> { t -> monthIndex(t) }
    }
    val groups = history.sortedBy { it.time }.groupBy { key(it.time) }.values.toList()
    val values = groups.map(::value)

    // Rekord und Zuwachs (Tageswerte, unabhängig von der Gruppierung)
    val perDay = history.sortedBy { it.time }.groupBy { dayKey(it.time) }.values.map(::value)
    val record = perDay.max()
    val gain = record - perDay.first()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), colors = glassCardColors()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Rekord", "Record"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("🏆 ${fmtW(record)} $unit", style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(tr("seit dem ersten Training", "since first session"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (if (gain > 0) "+" else "") + "${fmtW(gain)} $unit" + if (gain > 0) " ↑" else "",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (gain > 0) completionColor(1f) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (strength) {
            val best = history.maxBy { it.weight }
            Text(
                tr("Bester Satz: ", "Best set: ") + fmtSet(best, false) + " · " + tr("geschätztes 1RM ", "estimated 1RM ") +
                    "${fmtW(Math.round(history.maxOf { oneRepMax(it.weight, it.reps) } * 2) / 2.0)} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressMetric.entries.forEach { m ->
                    FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(tr(m.de, m.en)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressBucket.entries.forEach { b ->
                FilterChip(selected = bucket == b, onClick = { bucket = b }, label = { Text(tr(b.de, b.en)) })
            }
        }
        if (values.size >= 2) {
            val title = when {
                cardio -> tr("Minuten", "Minutes")
                timed -> tr("Längste Zeit", "Longest hold")
                else -> tr(metric.de, metric.en)
            } + " " + when (bucket) {
                ProgressBucket.DAY -> tr("pro Trainingstag", "per training day")
                ProgressBucket.WEEK -> tr("pro Woche", "per week")
                ProgressBucket.MONTH -> tr("pro Monat", "per month")
            }
            ProgressChart(values, title = title, unit = if (metric == ProgressMetric.VOLUME && strength) "kg" else unit, colors = glassCardColors())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtShortDate(groups.first().first().time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtShortDate(groups.last().first().time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                tr("Ab zwei Einträgen in dieser Einteilung erscheint hier ein Diagramm.", "A chart appears once there are two entries for this grouping."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Leicht durchsichtige Karten (Übungsdetails mit Hintergrundbild) – überall gleich. */
@Composable
fun glassCardColors(): CardColors =
    CardDefaults.cardColors(containerColor = CardDefaults.cardColors().containerColor.copy(alpha = 0.82f))

/**
 * Motivierende Zeile mit dem Rekord (oben im Reiter „Training“):
 * erstes Mal / neuer Rekord heute / bisheriger Rekord mit Ansporn.
 * Kraft: schwerster Satz (bei Gleichstand mehr Wdh.); Cardio und Zeitübungen: längste Einheit.
 */
@Composable
fun RecordLine(history: List<GymSet>, cardio: Boolean, timed: Boolean) {
    val today = dayKey(System.currentTimeMillis())
    val best = if (cardio || timed) history.maxByOrNull { it.reps }
    else history.maxWithOrNull(compareBy<GymSet>({ it.weight }, { it.reps }))
    val before = history.filter { dayKey(it.time) != today }
    val beatsOld = best != null && dayKey(best.time) == today && before.isNotEmpty() &&
        before.none { if (cardio || timed) it.reps >= best.reps else it.weight > best.weight || (it.weight == best.weight && it.reps >= best.reps) }

    val (emoji, text) = when {
        best == null -> "💪" to tr("Erstes Mal – heute setzt du deinen ersten Rekord!", "First time – today you set your first record!")
        beatsOld -> "🔥" to tr("Neuer Rekord heute: ", "New record today: ") + fmtSet(best, cardio) + tr(" – stark!", " – great job!")
        else -> "🏆" to tr("Rekord: ", "Record: ") + fmtSet(best, cardio) + " (${fmtDate(best.time)})" +
            tr(" – schaffst du heute mehr?", " – can you beat it today?")
    }
    Card(Modifier.fillMaxWidth(), colors = glassCardColors()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}
