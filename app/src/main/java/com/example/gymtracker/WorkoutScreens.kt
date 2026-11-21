package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

// ---------- Workouts ----------

/** Tab „Workouts“: Liste aller Workouts. */
@Composable
fun WorkoutList(store: GymStore, modifier: Modifier, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    if (store.workouts.isEmpty()) {
        Column(modifier.padding(16.dp)) {
            Text(
                tr(
                    "Noch keine Workouts. Ein Workout fasst mehrere Übungen zusammen, z. B. „Push-Tag“ oder „Beine“.",
                    "No workouts yet. A workout groups several exercises, e.g. \"Push day\" or \"Legs\"."
                )
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNew) { Text(tr("Workout anlegen", "Create workout")) }
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(store.workouts, key = { it.id }) { w ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpen(w.id) }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.titleLarge)
                        // Trainingstage laut Plan, z. B. „📅 Mo · Do“
                        if (w.days.isNotEmpty()) Text(
                            "📅 " + daysLabel(w.days),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val lastTraining = store.sessionsFor(w.id).firstOrNull()
                        Text(
                            plural(w.exercises.size, tr("Übung", "exercise"), tr("Übungen", "exercises")) +
                                (lastTraining?.let { tr(" · zuletzt ", " · last ") + fmtDate(it.start) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Symbole der enthaltenen Kategorien als kleine Vorschau
                        val groups = w.exercises.map { store.groupOf(it) }.distinct().sortedBy { it.ordinal }
                        if (groups.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                groups.forEach { MuscleIcon(it, Modifier.size(28.dp)) }
                            }
                        }
                    }
                    AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewWorkoutDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit, onTemplate: (WorkoutTemplate) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Neues Workout", "New workout")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Name, z. B. Push-Tag", "Name, e.g. Push day")) },
                    singleLine = true
                )
                Text(tr("… oder Vorlage übernehmen:", "… or start from a template:"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TEMPLATES.forEach { t ->
                        // Gefüllte Chips: auf dem Dialog-Hintergrund sonst kaum sichtbar
                        AssistChip(
                            onClick = { onTemplate(t) },
                            label = { Text(t.label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = null
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(tr("Anlegen", "Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

/** Ein Workout: seine Übungen als Liste in der festgelegten Reihenfolge (ziehen zum Umsortieren). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    store: GymStore,
    workout: Workout,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onDuplicate: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val history = store.sessionsFor(workout.id)

    // ----- Drag & Drop zum Umsortieren (Liste) -----
    val listState = rememberLazyListState()
    var dragKey by remember { mutableStateOf<String?>(null) }  // gerade gezogene Übung
    var dragY by remember { mutableFloatStateOf(0f) }          // senkrechte Verschiebung gegenüber ihrem Listenplatz
    val haptic = LocalHapticFeedback.current
    // Die Gesten-Coroutine läuft länger als eine Recomposition – mit rememberUpdatedState
    // sieht sie trotzdem immer die aktuelle Reihenfolge.
    val currentExercises by rememberUpdatedState(workout.exercises)

    fun startDrag(name: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        dragKey = name
        dragY = 0f
    }

    /** Finger bewegt: Zeile mitziehen; überdeckt sie den Nachbarn zur Hälfte → Plätze tauschen. */
    fun dragBy(name: String, dy: Float) {
        dragY += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val cur = items.firstOrNull { it.key == name } ?: return
        val idx = currentExercises.indexOf(name)
        val neighbor = when {
            dragY > 0 -> currentExercises.getOrNull(idx + 1)
            dragY < 0 -> currentExercises.getOrNull(idx - 1)
            else -> null
        } ?: return
        val t = items.firstOrNull { it.key == neighbor } ?: return
        // Vorderkante der gezogenen Zeile (unten beim Ziehen nach unten, oben nach oben) über der Mitte des Nachbarn?
        val edge = if (dragY > 0) cur.offset + cur.size + dragY else cur.offset + dragY
        val passed = if (dragY > 0) edge > t.offset + t.size / 2f else edge < t.offset + t.size / 2f
        if (!passed) return
        // LazyColumn hält ihre Position am obersten sichtbaren Eintrag (per key) fest. Wird der getauscht,
        // würde die Liste ihm hinterherscrollen – daher die aktuelle Position ausdrücklich beibehalten.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        val firstKey = items.firstOrNull()?.key
        store.moveInWorkout(workout.id, idx, currentExercises.indexOf(neighbor))
        if (firstKey == name || firstKey == neighbor) listState.requestScrollToItem(firstIndex, firstOffset)
        // Die Zeile springt im Layout an den neuen Platz – Verschiebung ausgleichen, damit sie unter dem Finger bleibt
        val newOffset = if (dragY > 0) t.offset + t.size - cur.size else t.offset
        dragY -= newOffset - cur.offset
    }

    fun endDrag() {
        dragKey = null
        dragY = 0f
        store.saveNow()
    }

    // Auto-Scroll: liegt die gezogene Zeile am oberen/unteren Rand, schiebt eine Schleife (einmal pro Bild)
    // die Liste weiter – je tiefer im Randbereich, desto schneller. LaunchedEffect startet die Schleife,
    // sobald gezogen wird, und bricht sie ab, wenn dragKey wieder null ist.
    val density = LocalDensity.current
    LaunchedEffect(dragKey) {
        val name = dragKey ?: return@LaunchedEffect
        val zone = with(density) { 72.dp.toPx() }
        val fab = with(density) { 72.dp.toPx() }   // der schwebende Start-Button verdeckt den unteren Rand
        val maxSpeed = with(density) { 6.dp.toPx() } // Pixel pro Bild
        while (true) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val cur = info.visibleItemsInfo.firstOrNull { it.key == name } ?: continue
            val top = cur.offset + dragY
            val bottom = top + cur.size
            val start = info.viewportStartOffset.toFloat()
            val end = info.viewportEndOffset - fab
            val speed = when {
                top < start + zone -> -maxSpeed * ((start + zone - top) / zone).coerceAtMost(1f)
                bottom > end - zone -> maxSpeed * ((bottom - end + zone) / zone).coerceAtMost(1f)
                else -> 0f
            }
            if (speed == 0f) continue
            val scrolled = listState.scrollBy(speed)
            if (scrolled != 0f) {
                // Inhalt ist um `scrolled` gewandert – Zeile bleibt unter dem Finger, ggf. mit Nachbarn tauschen
                dragBy(name, scrolled)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout.name, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
                actions = {
                    IconAction(Ico.EDIT, onClick = onEdit)
                    IconAction(Ico.COPY, onClick = onDuplicate)
                    IconAction(Ico.DELETE, onClick = { confirmDelete = true })
                }
            )
        },
        floatingActionButton = {
            if (workout.exercises.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onStart,
                    icon = { AppIcon(Ico.PLAY) },
                    text = { Text(tr("Training starten", "Start workout")) }
                )
            }
        }
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        if (workout.exercises.isEmpty()) {
            Column(modifier.padding(16.dp)) {
                Text(tr("Dieses Workout hat noch keine Übungen.", "This workout has no exercises yet."))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onEdit) { Text(tr("Übungen auswählen", "Choose exercises")) }
                Spacer(Modifier.height(16.dp))
                WorkoutDaysRow(store, workout)
            }
        } else {
            LazyColumn(
                modifier,
                state = listState,
                // unten Platz für den schwebenden Start-Button
                contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { WorkoutDaysRow(store, workout, Modifier.padding(bottom = 8.dp)) }
                item {
                    Text(
                        tr(
                            "Tipp: Am Griff rechts ziehen (oder Zeile gedrückt halten), um die Reihenfolge zu ändern.",
                            "Tip: drag the handle on the right (or long-press a row) to change the order."
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(workout.exercises, key = { it }) { name ->
                    val dragging = name == dragKey
                    // Wichtig: die Modifier-Kette hat immer dieselbe Struktur (nur Werte ändern sich),
                    // sonst würde Compose den pointerInput-Knoten neu anlegen und die Geste abbrechen.
                    WorkoutExerciseRow(
                        store, workout, name,
                        onClick = { onOpen(name) },
                        modifier = Modifier
                            // Andere Zeilen gleiten animiert an ihren neuen Platz; die gezogene folgt dem Finger
                            .animateItem(
                                placementSpec = if (dragging) null
                                else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                            )
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                if (dragging) {
                                    translationY = dragY
                                    scaleX = 1.02f; scaleY = 1.02f
                                    shadowElevation = 24f
                                }
                            }
                            // ganze Zeile: erst gedrückt halten, dann ziehen
                            .pointerInput(name) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startDrag(name) },
                                    onDrag = { change, amount -> change.consume(); dragBy(name, amount.y) },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() }
                                )
                            },
                        // Griff: sofort ziehen
                        handleModifier = Modifier.pointerInput(name) {
                            detectDragGestures(
                                onDragStart = { startDrag(name) },
                                onDrag = { change, amount -> change.consume(); dragBy(name, amount.y) },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() }
                            )
                        }
                    )
                }
                // Bisherige Trainings dieses Workouts
                if (history.isNotEmpty()) {
                    item {
                        Text(
                            tr("Letzte Trainings", "Recent sessions"),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                        )
                    }
                    items(history, key = { "s_${it.id}" }) { t ->
                        val stats = sessionStats(store, t)
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSession(t.id) }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(fmtDateTime(t.start), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${stats.percent} % · ${fmtDuration(t.end - t.start)} · " +
                                            plural(stats.sets, tr("Satz", "set"), tr("Sätze", "sets")) +
                                            (if (stats.volume > 0) " · ${fmtW(stats.volume)} kg" else "") +
                                            (stats.kcal?.let { " · 🔥 $it kcal" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (stats.records > 0) Text("🏆 ${stats.records}")
                                AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = { Text(tr("Workout löschen?", "Delete workout?")) },
            text = {
                Text(
                    tr(
                        "„${workout.name}“ wird gelöscht. Die Übungen und ihre Sätze bleiben erhalten.",
                        "\"${workout.name}\" will be deleted. The exercises and their sets are kept."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.deleteWorkout(workout.id)
                    onBack()
                }) { Text(tr("Löschen", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen", "Cancel")) }
            }
        )
    }
}

/** Eine Übung im Workout als Listenzeile: Bild, Name, letzter Satz + Ziel-Sätze, rechts der Griff zum Ziehen. */
@Composable
fun WorkoutExerciseRow(
    store: GymStore,
    workout: Workout,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier
) {
    val group = store.groupOf(name)
    val last = store.setsFor(name).firstOrNull()
    val target = store.targetFor(workout, name)
    Card(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        // leicht in der Kategorie-Farbe getönt (wie die Kacheln)
        colors = CardDefaults.cardColors(
            containerColor = group.color.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseIcon(name, group, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exName(name), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        last?.let { fmtSet(it, group.isCardio) } ?: "–",
                        if (target > 0) tr("Ziel ", "Goal ") + plural(target, tr("Satz", "set"), tr("Sätze", "sets")) else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            // Griff: große Tippfläche (48 dp), sofort ziehbar
            Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                AppIcon(Ico.DRAG, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Übungen für ein Workout an- und abhaken.
 * Zur Auswahl stehen alle Katalog-Übungen plus eigene Übungen, die nicht im Katalog sind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPickerScreen(store: GymStore, workout: Workout, onBack: () -> Unit) {
    val all = CATALOG.map { it.name } +
        store.customNames.filter { name -> CATALOG.none { it.name.equals(name, ignoreCase = true) } }
    var filter by remember { mutableStateOf(ExerciseFilter()) }
    val ui = rememberFilterUi(filter)
    val visible = all.filter { filter.matches(it, store.groupOf(it)) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Übungen: ", "Exercises: ") + workout.name) },
            navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
            actions = { FilterActions(ui, filter, { filter = it }) }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            FilterBar(
                filter, { filter = it },
                groups = all.map { store.groupOf(it) }.distinct().sortedBy { it.ordinal },
                equipment = Equipment.entries,
                ui = ui
            )
            if (visible.isEmpty()) NoFilterResults(onReset = { filter = ExerciseFilter() })
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        plural(workout.exercises.size, tr("Übung", "exercise"), tr("Übungen", "exercises")) +
                            tr(" ausgewählt", " selected")
                    )
                }
                MuscleGroup.entries.forEach { group ->
                    val names = visible.filter { store.groupOf(it) == group }
                    if (names.isEmpty()) return@forEach
                    item(key = "h_${group.name}") { GroupHeader(group) }
                    items(names, key = { it }) { name ->
                        val checked = name in workout.exercises
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { store.toggleInWorkout(workout.id, name) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseIcon(name, group, Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(exName(name), modifier = Modifier.weight(1f))
                            // ⓘ = Anleitung/Muskeln aus dem Wiki (eigener Klick, schaltet die Zeile nicht um)
                            WikiButton(name)
                            // Ziel-Sätze nur für angehakte Übungen. Die Buttons haben eigene Klicks,
                            // deshalb schalten sie die Zeile nicht um.
                            if (checked) {
                                val target = store.targetFor(workout, name)
                                TextButton(
                                    onClick = { store.setTarget(workout.id, name, target - 1) },
                                    enabled = target > 0,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp)
                                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                Text(
                                    when {
                                        target == 0 -> "optional"
                                        group.isCardio -> "${target}×"
                                        else -> plural(target, tr("Satz", "set"), tr("Sätze", "sets"))
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(56.dp)
                                )
                                TextButton(
                                    onClick = { store.setTarget(workout.id, name, target + 1) },
                                    enabled = target < 20,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp)
                                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            }
                            // onCheckedChange = null: Klick wird von der ganzen Zeile behandelt
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}


