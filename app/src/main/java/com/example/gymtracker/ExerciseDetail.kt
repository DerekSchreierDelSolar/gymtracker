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
        }) { Text(if (cardio) tr("Eintragen", "Log") else tr("Satz +", "Set +")) }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$number. ${fmtSet(set, cardio)}", modifier = Modifier.weight(1f))
        Text(
            SimpleDateFormat("HH:mm", appLocale()).format(Date(set.time)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconAction(Ico.EDIT, onClick = { editing = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    // Diagramm-Ansicht bei Kraftübungen: false = Maximalgewicht, true = geschätztes 1RM
    var show1RM by rememberSaveable { mutableStateOf(false) }
    // Vom Nutzer übernommener Gewichts-Vorschlag (füllt das kg-Feld)
    var appliedWeight by remember { mutableStateOf<Double?>(null) }

    // Nach Tag gruppiert, neuester Tag zuerst
    val days = history.groupBy { fmtDate(it.time) }
    val best = if (cardio || timed) history.maxByOrNull { it.reps } else history.maxByOrNull { it.weight }
    val best1RM = if (cardio || timed) null else history.filter { it.weight > 0 }.maxOfOrNull { oneRepMax(it.weight, it.reps) }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseIcon(exercise, group, Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(exName(exercise))
                }
            },
            navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
            actions = {
                WikiButton(exercise)
                IconAction(Ico.DELETE, onClick = { confirmDelete = true })
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Fortschritts-Diagramm (ab 2 Trainingstagen)
            if (days.size >= 2) {
                item {
                    when {
                        cardio -> ProgressChart(
                            days.values.map { d -> d.sumOf { it.reps }.toDouble() }.reversed(),
                            title = tr("Minuten pro Trainingstag", "Minutes per training day"), unit = "min"
                        )
                        timed -> ProgressChart(
                            days.values.map { d -> d.maxOf { it.reps }.toDouble() }.reversed(),
                            title = tr("Längste Zeit pro Training", "Longest hold per session"), unit = "s"
                        )
                        else -> Column {
                            // Umschalter: Maximalgewicht oder geschätztes 1RM
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                FilterChip(selected = !show1RM, onClick = { show1RM = false },
                                    label = { Text(tr("Max. Gewicht", "Max weight")) })
                                FilterChip(selected = show1RM, onClick = { show1RM = true },
                                    label = { Text(tr("Geschätztes 1RM", "Estimated 1RM")) })
                            }
                            if (show1RM) ProgressChart(
                                days.values.map { d -> d.maxOf { oneRepMax(it.weight, it.reps) } }.reversed()
                                    .map { Math.round(it * 2) / 2.0 },
                                title = tr("Geschätztes 1RM pro Training", "Estimated 1RM per session"), unit = "kg"
                            ) else ProgressChart(
                                days.values.map { d -> d.maxOf { it.weight } }.reversed(),
                                title = tr("Maximalgewicht pro Training", "Max weight per session"), unit = "kg"
                            )
                        }
                    }
                }
            }

            // Bestleistung (+ geschätztes 1RM bei Kraftübungen)
            if (best != null) {
                item {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(
                            (if (cardio || timed) tr("🏆 Längste Einheit: ", "🏆 Longest: ")
                            else tr("🏆 Bestleistung: ", "🏆 Personal record: ")) + fmtSet(best, cardio),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (best1RM != null) {
                            Text(
                                tr("Geschätztes 1RM: ", "Estimated 1RM: ") + "${fmtW(Math.round(best1RM * 2) / 2.0)} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Progressions-Vorschlag
            item { ProgressionHint(store, exercise, onApply = { appliedWeight = it }) }

            // 2. Eingabe
            item { SetInput(store, exercise, weightOverride = appliedWeight) }

            // 3. Verlauf pro Tag
            if (days.isNotEmpty()) {
                item {
                    Text(
                        tr("Verlauf", "History"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            days.forEach { (day, daySets) ->
                item(key = day) {
                    Card(Modifier.fillMaxWidth()) {
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
                            daySets.sortedBy { it.time }.forEachIndexed { i, s ->
                                SetRow(store, s, i + 1, cardio)
                            }
                        }
                    }
                }
            }
        }
    }

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


