package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
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
fun WorkoutList(store: GymStore, modifier: Modifier, onOpen: (Long) -> Unit, onNew: () -> Unit, onStart: (Long) -> Unit) {
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
                    // ▶ startet das Training direkt (Karte antippen = Workout öffnen/bearbeiten)
                    if (w.exercises.isNotEmpty()) {
                        FilledTonalIconButton(onClick = { onStart(w.id) }, modifier = Modifier.size(52.dp)) {
                            AppIcon(Ico.PLAY)
                        }
                    } else AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onDuplicate: () -> Unit,
    /** Läuft gerade (minimiert) ein Training mit diesem Workout? → „Training fortsetzen“ */
    running: Boolean = false
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
        // onDragCancel kommt auch, wenn Compose die Gesten-Coroutine abbricht (z. B. Bildschirm verlassen),
        // ohne dass gezogen wurde → nur nach echtem Ziehen als „benutzt“ merken
        if (dragKey == null) return
        Hints.used(Hints.DRAG)
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
                    text = { Text(if (running) tr("Training fortsetzen", "Resume workout") else tr("Training starten", "Start workout")) },
                    // beim Scrollen nur noch das Symbol → verdeckt keinen Text
                    expanded = listState.firstVisibleItemIndex == 0
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
                // Bedienhinweis nur, bis einmal umsortiert wurde
                if (Hints.show(Hints.DRAG)) item {
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
                workout.exercises.forEachIndexed { pos, name -> item(key = name) {
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
                // Verbinder zur nächsten Übung: antippen = Supersatz an/aus (Supersets.kt); beim Ziehen ausgeblendet
                if (pos < workout.exercises.lastIndex) item(key = "link_$name") {
                    if (dragKey == null) SupersetLink(name in workout.links) { store.toggleLink(workout.id, name) }
                }
                }
                // Beschrifteter Knopf statt nur Stift oben: öffnet WorkoutPickerScreen
                item {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        AppIcon(Ico.ADD)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Übungen hinzufügen / bearbeiten", "Add / edit exercises"))
                    }
                }
                // Bisherige Trainings dieses Workouts
                if (history.isNotEmpty()) {
                    item {
                        Text(
                            tr("Letzte Trainingseinheiten", "Recent sessions"),
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
                                            (if (stats.volume > 0) " · ${fmtVol(stats.volume)}" else "") +
                                            (stats.kcal?.let { " · 🔥 ${fmtKcal(it)} kcal" } ?: ""),
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
    var editTarget by remember { mutableStateOf(false) }
    if (editTarget) AlertDialog(
        onDismissRequest = { editTarget = false },
        title = { Text(exName(name)) },
        text = { TargetStepper(target, group, onChange = { store.setTarget(workout.id, name, it) }) },
        confirmButton = { TextButton(onClick = { editTarget = false }) { Text("OK") } }
    )
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
                    last?.let { tr("Zuletzt ", "Last ") + fmtSet(it, group.isCardio) } ?: "–",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                // Ziel-Sätze immer sichtbar („Ziel: –“ = optional) und direkt antippbar
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = { editTarget = true },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        tr("Ziel: ", "Goal: ") + (if (target == 0) "–" else targetLabel(target, group)) + " ✎",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            // Griff: große Tippfläche (48 dp), sofort ziehbar
            Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                AppIcon(Ico.DRAG, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Workout bearbeiten: oben die Übungen des Workouts, darunter die Kategorien; Kategorie antippen → ihre Übungen
 * (Katalog + eigene). Antippen öffnet [WorkoutExercisePage] mit Info, Ziel-Sätzen und Hinzufügen/Entfernen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPickerScreen(store: GymStore, workout: Workout, onBack: () -> Unit) {
    val all = CATALOG.map { it.name } +
        store.customNames.filter { name -> CATALOG.none { it.name.equals(name, ignoreCase = true) } }
    var filter by remember { mutableStateOf(ExerciseFilter()) }
    val ui = rememberFilterUi(filter)
    // Geöffnete Übung (null = Liste). rememberSaveable: übersteht Drehen; Zurück schließt erst die Seite.
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    // Geöffnete Kategorie (null = Übersicht). Mit Suche/Filter: direkt alle Treffer.
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val group = openGroup?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }
    val overview = group == null && !filter.isActive
    val visible = all.filter { (group == null || store.groupOf(it) == group) && filter.matches(it, store.groupOf(it)) }
    val listState = rememberLazyListState() // außerhalb des if → Scroll-Position bleibt beim Zurückkehren erhalten

    detail?.let { name ->
        BackHandler { detail = null }
        WorkoutExercisePage(store, workout, name, onBack = { detail = null })
        return
    }
    if (group != null) BackHandler { openGroup = null }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(group?.label ?: (tr("Übungen: ", "Exercises: ") + workout.name)) },
            navigationIcon = { IconAction(Ico.BACK, onClick = { if (group != null) openGroup = null else onBack() }) },
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
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (overview) {
                    // Übersicht: oben die Übungen des Workouts, darunter die Kategorien zum Hinzufügen
                    if (workout.exercises.isNotEmpty()) {
                        item(key = "h_in") {
                            Text(
                                tr("In diesem Workout", "In this workout") + " (${workout.exercises.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(workout.exercises, key = { "in_$it" }) { name -> PickerRow(store, workout, name, onClick = { detail = name }) }
                    }
                    item(key = "h_add") {
                        Column(Modifier.padding(top = if (workout.exercises.isEmpty()) 0.dp else 16.dp, bottom = 8.dp)) {
                            Text(tr("Übung hinzufügen", "Add exercise"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("Wähle eine Kategorie – oder suche oben direkt nach einer Übung.", "Pick a category – or search for an exercise at the top."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Favoriten (★) direkt zum Antippen, vor den Kategorien
                    val favs = visible.filter { store.isFavorite(it) }.sortedByName { exName(it) }
                    if (favs.isNotEmpty()) {
                        item(key = "h_fav") { FavoritesHeader() }
                        items(favs, key = { "fav_$it" }) { name -> PickerRow(store, workout, name, onClick = { detail = name }) }
                        item(key = "h_groups") {
                            Text(tr("Kategorien", "Categories"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        }
                    }
                    items(MuscleGroup.entries.filter { g -> visible.any { store.groupOf(it) == g } }, key = { "g_${it.name}" }) { g ->
                        val names = visible.filter { store.groupOf(it) == g }
                        Box(Modifier.padding(bottom = 8.dp)) {
                            CategoryRow(g, names.size, names.count { it in workout.exercises }, onClick = { openGroup = g.name })
                        }
                    }
                } else {
                    item {
                        Text(
                            tr("Tippe auf eine Übung für Infos, Ziel-Sätze und zum Hinzufügen.", "Tap an exercise for info, target sets and to add it."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Kategorie bzw. Suchtreffer: alle passenden Übungen (die des Workouts mit ✓ und Ziel)
                    MuscleGroup.entries.forEach { g ->
                        val names = visible.filter { store.groupOf(it) == g }.sortedByName { exName(it) }.favoritesFirst(store) { it } // alphabetisch, Favoriten zuerst
                        if (names.isEmpty()) return@forEach
                        if (group == null) item(key = "h_${g.name}") { GroupHeader(g) }
                        items(names, key = { it }) { name -> PickerRow(store, workout, name, onClick = { detail = name }) }
                    }
                }
            }
        }
    }
}

/** Zeile der Auswahlliste: Bild, Name, bei Übungen im Workout das Ziel („✓ 3 Sätze“), Pfeil. */
@Composable
private fun PickerRow(store: GymStore, workout: Workout, name: String, onClick: () -> Unit) {
    val group = store.groupOf(name)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExerciseIcon(name, group, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NameWithStar(store, name)
            if (name in workout.exercises) Text(
                "✓ " + targetLabel(store.targetFor(workout, name), group),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** „optional“ / „3 Sätze“ / bei Cardio „2×“ */
private fun targetLabel(target: Int, group: MuscleGroup): String = when {
    target == 0 -> tr("optional", "optional")
    group.isCardio -> "${target}×"
    else -> plural(target, tr("Satz", "set"), tr("Sätze", "sets"))
}

/** „Ziel-Sätze  − 3 Sätze +“ (0 = optional, max. 20) – Workout bearbeiten und Ziel-Dialog in der Workout-Liste. */
@Composable
fun TargetStepper(target: Int, group: MuscleGroup, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Ziel-Sätze", "Target sets"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("0 = optional", "0 = optional"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = { onChange(target - 1) },
            enabled = target > 0,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp)
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            targetLabel(target, group),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(88.dp)
        )
        FilledTonalButton(
            onClick = { onChange(target + 1) },
            enabled = target < 20,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp)
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * Übung beim Bearbeiten eines Workouts: wie der Reiter „Info“ der Übungsdetails (Bild im Hintergrund,
 * Muskeln vorne/hinten, Anleitung), unten eine feste Leiste: „Zum Workout hinzufügen“ bzw. Ziel-Sätze + Entfernen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExercisePage(store: GymStore, workout: Workout, name: String, onBack: () -> Unit) {
    val group = store.groupOf(name)
    val added = name in workout.exercises
    val target = store.targetFor(workout, name)
    val img = hasExerciseImage(name)
    val muscles = musclesOf(name)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ExerciseBackdrop(name)
        Scaffold(
            containerColor = if (img) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    colors = if (img) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
                    else TopAppBarDefaults.topAppBarColors(),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseIcon(name, group, Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(exName(name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
                    actions = { FavoriteButton(store, name) }
                )
            },
            bottomBar = {
                // Feste Leiste: Ziel-Sätze bzw. Hinzufügen
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        if (added) {
                            TargetStepper(target, group, onChange = { store.setTarget(workout.id, name, it) })
                            TextButton(
                                onClick = { store.toggleInWorkout(workout.id, name) },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text(tr("Aus dem Workout entfernen", "Remove from workout"), color = MaterialTheme.colorScheme.error) }
                        } else {
                            Button(onClick = {
                                store.toggleInWorkout(workout.id, name)
                                // Standard-Ziel beim Hinzufügen: 3 Sätze (Cardio 1×) – lässt sich direkt danach ändern
                                store.setTarget(workout.id, name, if (group.isCardio) 1 else 3)
                            }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                AppIcon(Ico.ADD)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Zum Workout hinzufügen", "Add to workout"))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // wie der Reiter „Info“ in ExerciseDetailScreen
                if (muscles != null) {
                    // Vorder- und Rückseite nebeneinander, darunter die Muskelliste
                    item { MuscleSideCard(BodyView.BOTH, muscles.levels(), showList = true, colors = glassCardColors()) }
                } else item {
                    Text(tr("Noch keine Muskeln festgelegt.", "No muscles set yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (wikiFor(name) != null) item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WikiContent(name, showHeader = false, showMuscles = false)
                    }
                }
            }
        }
    }
}


