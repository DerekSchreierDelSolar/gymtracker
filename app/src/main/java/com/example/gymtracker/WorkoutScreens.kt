package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.animation.core.Animatable
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
import kotlinx.coroutines.launch

// ---------- Workouts ----------

/** Tab „Workouts“: Liste aller Workouts. */
@Composable
fun WorkoutList(store: GymStore, modifier: Modifier, onOpen: (Long) -> Unit, onNew: () -> Unit, onStart: (Long) -> Unit, onFree: () -> Unit) {
    if (store.workouts.isEmpty()) {
        Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            FreeTrainingCard(store, onFree, onStart = { onStart(FREE_WORKOUT_ID) })
            Spacer(Modifier.height(16.dp))
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
        item(key = "free") { FreeTrainingCard(store, onFree, onStart = { onStart(FREE_WORKOUT_ID) }) }
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

/**
 * Fester Eintrag „Freies Training“ oben in der Workout-Liste (kein echtes Workout, daher nicht löschbar):
 * öffnet FreeTrainingScreen mit allen eigenen Übungen. Web: freeTrainingCard in workout.js.
 */
@Composable
private fun FreeTrainingCard(store: GymStore, onClick: () -> Unit, onStart: () -> Unit) {
    val lastSession = lastFreeSession(store)
    val last = lastSession?.start
    Card(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) { AppIcon(Ico.DUMBBELL, tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Freies Training", "Free workout"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    tr("Einzelne Übungen ohne festen Plan", "Single exercises without a fixed plan"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    plural(store.exercises.size, tr("Übung", "exercise"), tr("Übungen", "exercises")) +
                        (last?.let { tr(" · zuletzt ", " · last ") + fmtDate(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
                // Vorschau: was letztes Mal dran war
                lastSession?.exercises?.distinct()?.takeIf { it.isNotEmpty() }?.let { ex ->
                    Text(
                        tr("Zuletzt: ", "Last: ") + ex.take(4).joinToString(", ") { exName(it) } + if (ex.size > 4) " …" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 2
                    )
                }
            }
            // ▶ startet das freie Training (leer, Übungen kommen im Training dazu)
            FilledIconButton(onClick = onStart, modifier = Modifier.size(52.dp)) { AppIcon(Ico.PLAY) }
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
    running: Boolean = false,
    /** Reiter: 0 = Übungen, 1 = Überblick (Trainingstage, Muskeln, letzte Trainings); Zustand liegt in GymApp */
    tab: Int = 0,
    onTab: (Int) -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf(false) }


    // ----- Supersatz bilden: Auswahlmodus (Kästchen statt Griff, unten „Verbinden“) -----
    var selecting by rememberSaveable(workout.id) { mutableStateOf(false) }
    var selected by remember(workout.id) { mutableStateOf(setOf<String>()) }
    BackHandler(enabled = selecting) { selecting = false }
    val groups = supersetGroups(workout)

    // ----- Drag & Drop zum Umsortieren (Liste) -----
    val listState = rememberLazyListState()
    val overviewState = rememberLazyListState()
    // Prinzip: Gemerkt wird nur, wo die Zeile beim Greifen stand (dragStart, Bildschirm-Koordinaten der Liste)
    // und wie weit der Finger seitdem gewandert ist (dragDelta). Die Verschiebung der Zeile wird beim Zeichnen
    // aus dem AKTUELLEN Layout berechnet (dragShift) – so bleibt sie unter dem Finger, egal wann Tausch und
    // Neuaufbau passieren (kein Nachrechnen mit veralteten Positionen mehr).
    var dragKey by remember { mutableStateOf<String?>(null) }  // gerade gezogene Übung
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    // Stand beim Greifen: Supersatz-Verbindungen werden immer gegenüber diesem Stand berechnet (movedLinks),
    // sonst würde schon das Vorbeiziehen an einem Supersatz ihn auflösen
    var dragOrigin by remember { mutableStateOf<Workout?>(null) }
    // Loslassen: Zeile gleitet von der Finger-Position an ihren Platz
    var settleKey by remember { mutableStateOf<String?>(null) }
    val settle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    /** Aktuelle Übungsliste direkt aus dem Store (die Gesten-Coroutine überdauert Recompositions). */
    fun liveExercises(): List<String> = store.workouts.firstOrNull { it.id == workout.id }?.exercises ?: emptyList()

    /** Verschiebung der Zeile gegenüber ihrem Platz im aktuellen Layout. */
    fun dragShift(name: String): Float {
        val cur = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == name } ?: return 0f
        return dragStart + dragDelta - cur.offset
    }

    fun startDrag(name: String) {
        if (selecting) return // im Auswahlmodus wird nicht umsortiert
        val cur = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == name } ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        settleKey = null
        dragOrigin = store.workouts.firstOrNull { it.id == workout.id }
        dragStart = cur.offset.toFloat()
        dragDelta = 0f
        dragKey = name
    }

    /** Überdeckt die gezogene Zeile den Nachbarn zur Hälfte → Plätze tauschen. */
    fun checkSwap(name: String) {
        val origin = dragOrigin ?: return
        val exercises = liveExercises()
        val items = listState.layoutInfo.visibleItemsInfo
        // Erst wieder tauschen, wenn das Layout den letzten Tausch schon zeigt (sonst doppelte/falsche Tausche)
        val shown = items.mapNotNull { it.key as? String }.filter { it in exercises }
        val first = exercises.indexOf(shown.firstOrNull() ?: return)
        if (first < 0 || first + shown.size > exercises.size || exercises.subList(first, first + shown.size) != shown) return
        val cur = items.firstOrNull { it.key == name } ?: return
        val idx = exercises.indexOf(name)
        val top = dragStart + dragDelta
        val below = exercises.getOrNull(idx + 1)?.let { n -> items.firstOrNull { it.key == n } }
        val above = exercises.getOrNull(idx - 1)?.let { n -> items.firstOrNull { it.key == n } }
        val target = when {
            below != null && top + cur.size > below.offset + below.size / 2f -> idx + 1
            above != null && top < above.offset + above.size / 2f -> idx - 1
            else -> return
        }
        val neighbor = exercises[target]
        // LazyColumn hält ihre Position am obersten sichtbaren Eintrag (per key) fest. Wird der getauscht,
        // würde die Liste ihm hinterherscrollen – daher die aktuelle Position ausdrücklich beibehalten.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        val firstKey = items.firstOrNull()?.key
        val order = exercises.toMutableList().apply { add(target, removeAt(idx)) }
        val from = origin.exercises.indexOf(name)
        val links = if (from < 0) origin.links else movedLinks(origin, from, target)
        store.reorderWorkout(workout.id, order, links)
        if (firstKey == name || firstKey == neighbor) listState.requestScrollToItem(firstIndex, firstOffset)
    }

    fun dragBy(name: String, dy: Float) {
        if (dragKey != name) return
        dragDelta += dy
        checkSwap(name)
    }

    fun endDrag() {
        // onDragCancel kommt auch, wenn Compose die Gesten-Coroutine abbricht (z. B. Bildschirm verlassen),
        // ohne dass gezogen wurde → nur nach echtem Ziehen als „benutzt“ merken
        val name = dragKey ?: return
        Hints.used(Hints.DRAG)
        val shift = dragShift(name)
        dragKey = null
        dragOrigin = null
        settleKey = name
        scope.launch {
            settle.snapTo(shift)
            settle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            settleKey = null
        }
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
            val top = dragStart + dragDelta   // die Zeile bleibt unter dem Finger, auch wenn die Liste scrollt
            val bottom = top + cur.size
            val start = info.viewportStartOffset.toFloat()
            val end = info.viewportEndOffset - fab
            val speed = when {
                top < start + zone -> -maxSpeed * ((start + zone - top) / zone).coerceAtMost(1f)
                bottom > end - zone -> maxSpeed * ((bottom - end + zone) / zone).coerceAtMost(1f)
                else -> 0f
            }
            if (speed == 0f) continue
            // Inhalt wandert unter der Zeile durch → ggf. mit dem Nachbarn tauschen
            if (listState.scrollBy(speed) != 0f) checkSwap(name)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(workout.name, style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
                    actions = {
                        IconAction(Ico.COPY, onClick = onDuplicate)
                        IconAction(Ico.DELETE, onClick = { confirmDelete = true })
                    }
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf(tr("Übungen", "Exercises"), tr("Überblick", "Overview")).forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i, onClick = { onTab(i) },
                            text = { Text(label, maxLines = 1) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Auswahlmodus: Abbrechen / Verbinden fest unten
            if (selecting && tab == 0) Surface(tonalElevation = 3.dp) {
              Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    tr(
                        "Tippe die Übungen an, die du abwechselnd machen willst (mind. 2). Sie rücken in der Liste zusammen.",
                        "Tap the exercises you want to alternate (at least 2). They will be moved next to each other."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SUPERSET_COLOR
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { selecting = false }, modifier = Modifier.weight(1f)) { Text(tr("Abbrechen", "Cancel")) }
                    Button(
                        onClick = {
                            store.linkSuperset(workout.id, selected)
                            selecting = false
                        },
                        enabled = selected.size >= 2,
                        modifier = Modifier.weight(1f)
                    ) { Text(tr("Verbinden", "Link") + if (selected.isNotEmpty()) " (${selected.size})" else "") }
                }
              }
            }
        },
        floatingActionButton = {
            if (workout.exercises.isNotEmpty() && !(selecting && tab == 0)) {
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onStart,
                    icon = { AppIcon(Ico.PLAY) },
                    text = { Text(if (running) tr("Training fortsetzen", "Resume workout") else tr("Training starten", "Start workout")) },
                    // beim Scrollen nur noch das Symbol → verdeckt keinen Text
                    expanded = (if (tab == 0) listState else overviewState).firstVisibleItemIndex == 0
                )
            }
        }
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        if (tab == 1) {
            WorkoutOverview(store, workout, store.sessionsFor(workout.id), overviewState, modifier, onOpenSession)
        } else if (workout.exercises.isEmpty()) {
            Column(modifier.padding(16.dp)) {
                Text(tr("Dieses Workout hat noch keine Übungen.", "This workout has no exercises yet."))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onEdit) { Text(tr("Übungen auswählen", "Choose exercises")) }
            }
        } else {
            LazyColumn(
                modifier,
                state = listState,
                // unten Platz für den schwebenden Start-Button
                contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bedienhinweis nur, bis einmal umsortiert wurde
                if (!selecting && Hints.show(Hints.DRAG)) item {
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
                workout.exercises.forEach { name ->
                // Über einer Gruppe: „🔗 Supersatz · Auflösen“ (beim Ziehen/Auswählen ausgeblendet)
                val group = groups.firstOrNull { it.first() == name }
                if (group != null) item(key = "grp_$name") {
                    if (dragKey == null && !selecting) SupersetHeader(group.size) {
                        val old = workout.links
                        store.setLinks(workout.id, old - group.toSet())
                        Undo.show(tr("Supersatz aufgelöst", "Superset unlinked")) { store.setLinks(workout.id, old) }
                    }
                }
                item(key = name) {
                    val dragging = name == dragKey
                    val lifted = dragging || name == settleKey
                    // Wichtig: die Modifier-Kette hat immer dieselbe Struktur (nur Werte ändern sich),
                    // sonst würde Compose den pointerInput-Knoten neu anlegen und die Geste abbrechen.
                    WorkoutExerciseRow(
                        store, workout, name,
                        onClick = { if (selecting) selected = if (name in selected) selected - name else selected + name else onOpen(name) },
                        inSuperset = groups.any { name in it },
                        selection = if (selecting) name in selected else null,
                        modifier = Modifier
                            // Andere Zeilen gleiten animiert an ihren neuen Platz; die gezogene folgt dem Finger
                            .animateItem(
                                placementSpec = if (lifted) null
                                else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                            )
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer {
                                // nur in der Zeichenphase gelesen → Fingerbewegung baut nichts neu auf
                                translationY = when {
                                    dragging -> dragShift(name)
                                    name == settleKey -> settle.value
                                    else -> 0f
                                }
                                val s = if (dragging) 1.02f else 1f
                                scaleX = s; scaleY = s
                                shadowElevation = if (lifted) 24f else 0f
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
                }
                // Beschrifteter Knopf statt nur Stift oben: öffnet WorkoutPickerScreen
                if (!selecting) item {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        AppIcon(Ico.ADD)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Übungen hinzufügen / bearbeiten", "Add / edit exercises"))
                    }
                }
                // Supersatz bilden → Auswahlmodus (Supersets.kt)
                if (!selecting && workout.exercises.size >= 2) item {
                    OutlinedButton(
                        onClick = { selected = emptySet(); selecting = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SUPERSET_COLOR)
                    ) { Text(tr("🔗 Supersatz bilden", "🔗 Create superset")) }
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

/** Reiter „Überblick“ eines Workouts: Trainingstage, beanspruchte Muskeln (Heatmap) und letzte Trainingseinheiten. */
@Composable
private fun WorkoutOverview(
    store: GymStore,
    workout: Workout,
    history: List<TrainingSession>,
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
    onOpenSession: (Long) -> Unit
) {
    LazyColumn(
        modifier,
        state = state,
        // unten Platz für den schwebenden Start-Button
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) { WorkoutDaysRow(store, workout, Modifier.padding(16.dp)) }
        }
        // Heatmap: welche Muskeln die Übungen dieses Workouts beanspruchen (gewichtet nach Ziel-Sätzen)
        if (workout.exercises.isNotEmpty()) item {
            MuscleHeatCard(
                title = tr("Beanspruchte Muskeln", "Muscles worked"),
                score = workoutRegionScore(workout),
                showSets = false,
                empty = tr("Für diese Übungen sind keine Muskeln hinterlegt.", "No muscles known for these exercises."),
                header = {
                    Text(
                        tr(
                            "Aus den Übungen dieses Workouts, gewichtet nach Ziel-Sätzen (Hilfsmuskel zählt ½).",
                            "From this workout's exercises, weighted by target sets (secondary counts ½)."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        // Bisherige Trainings dieses Workouts
        item {
            Text(
                tr("Letzte Trainingseinheiten", "Recent sessions"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp)
            )
        }
        if (history.isEmpty()) item {
            Text(
                tr("Mit diesem Workout wurde noch nicht trainiert.", "No sessions with this workout yet."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
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
                            "${stats.percent}\u00A0% · ${fmtDuration(t.end - t.start)} · " +
                                plural(stats.sets, tr("Satz", "set"), tr("Sätze", "sets")) +
                                (if (stats.volume > 0) " · ${fmtVol(stats.volume)}" else "") +
                                (stats.kcal?.let { " · 🔥 ${fmtKcal(it)}\u00A0kcal" } ?: ""),
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

/** Eine Übung im Workout als Listenzeile: Bild, Name, letzter Satz + Ziel-Sätze, rechts der Griff zum Ziehen. */
@Composable
fun WorkoutExerciseRow(
    store: GymStore,
    workout: Workout,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    /** Teil eines Supersatzes → orangefarbener Rahmen */
    inSuperset: Boolean = false,
    /** Auswahlmodus „Supersatz bilden“: angehakt ja/nein, null = kein Auswahlmodus (Griff) */
    selection: Boolean? = null
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
        ),
        border = when {
            selection == true -> androidx.compose.foundation.BorderStroke(2.dp, SUPERSET_COLOR)
            inSuperset && selection == null -> androidx.compose.foundation.BorderStroke(2.dp, SUPERSET_COLOR.copy(alpha = 0.7f))
            else -> null
        }
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
            if (selection != null) {
                // Auswahlmodus: Kästchen statt Griff (die ganze Zeile ist antippbar)
                Checkbox(
                    checked = selection, onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = SUPERSET_COLOR),
                    modifier = Modifier.padding(end = 4.dp)
                )
            } else {
                // Griff: große Tippfläche (48 dp), sofort ziehbar
                Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                    AppIcon(Ico.DRAG, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    // Kategorie antippen = Filter-Chip dieser Kategorie setzen (sichtbar, abwählbar); Zurück wählt ihn wieder ab.
    // Ohne Suche/Filter: Übersicht, sonst direkt alle Treffer.
    var fromCategory by rememberSaveable { mutableStateOf(false) }
    val group = filter.groups.singleOrNull()
    val overview = !filter.isActive
    val inCategory = fromCategory && filter.groups.isNotEmpty()
    val closeCategory = { fromCategory = false; filter = filter.copy(groups = emptySet()) }
    val visible = all.filter { filter.matches(it, store.groupOf(it)) }
    val listState = rememberLazyListState() // außerhalb des if → Scroll-Position bleibt beim Zurückkehren erhalten

    detail?.let { name ->
        BackHandler { detail = null }
        WorkoutExercisePage(store, workout, name, onBack = { detail = null })
        return
    }
    if (inCategory) BackHandler { closeCategory() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(group?.label ?: (tr("Übungen: ", "Exercises: ") + workout.name)) },
            navigationIcon = { IconAction(Ico.BACK, onClick = { if (inCategory) closeCategory() else onBack() }) },
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
                            CategoryRow(g, names.size, names.count { it in workout.exercises }, onClick = { fromCategory = true; filter = filter.copy(groups = setOf(g)) })
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


