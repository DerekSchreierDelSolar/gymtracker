package com.example.gymtracker

import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Bildschirm 1: Startseite mit Tabs Start, Workouts, Kalender, Körper (+ „Fragen“ unten als Knopf) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: GymStore,
    tab: Int,
    onTab: (Int) -> Unit,
    onFree: () -> Unit,
    onWiki: () -> Unit,
    onBot: () -> Unit,
    onSettings: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onCreatedWorkout: (Long) -> Unit,
    calMonth: Int,
    onCalMonth: (Int) -> Unit,
    calDay: Int,
    onCalDay: (Int) -> Unit,
    onOpenSession: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    /** Leiste „Training läuft“ (minimiertes Training) – sitzt über der Navigationsleiste */
    trainingBar: (@Composable () -> Unit)? = null
) {
    var showNewWorkout by remember { mutableStateOf(false) }

    // Pager für die vier Hauptbereiche, synchron mit `tab`. „Fragen“ ist ein eigener Bildschirm (Knopf in der Navigation),
    // die frühere Übungsliste steckt im Workout „Freies Training“ (FreeTrainingScreen).
    val pagerState = rememberPagerState(initialPage = tab.coerceIn(0, 3)) { 4 }
    val currentOnTab by rememberUpdatedState(onTab)
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab) pagerState.animateScrollToPage(tab)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnTab(it) }
    }
    var menuOpen by remember { mutableStateOf(false) }
    // Export/Import (SettingsScreen.kt) – auch auf der Einstellungsseite
    val backup = rememberBackupActions(store)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Nur der Bereichsname (der App-Name stand vorher darunter und kostete Platz)
                    Text(
                        when (tab) {
                            0 -> tr("Übersicht", "Overview")
                            1 -> "Workouts"
                            2 -> tr("Kalender", "Calendar")
                            else -> tr("Körper", "Body")
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    // Wiki immer direkt erreichbar (auf allen Tabs); „Fragen“ sitzt unten in der Navigation
                    IconAction(Ico.BOOK, onClick = onWiki)
                    // ⋮-Menü: Box ist der Anker, an dem sich das DropdownMenu ausrichtet
                    Box {
                        IconAction(Ico.MORE, onClick = { menuOpen = true })
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Einstellungen", "Settings")) },
                                leadingIcon = { AppIcon(Ico.SETTINGS) },
                                onClick = { menuOpen = false; onSettings() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(tr("Daten exportieren", "Export data")) },
                                leadingIcon = { AppIcon(Ico.UPLOAD) },
                                onClick = {
                                    menuOpen = false
                                    backup.export()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Daten importieren", "Import data")) },
                                leadingIcon = { AppIcon(Ico.DOWNLOAD) },
                                onClick = {
                                    menuOpen = false
                                    backup.import()
                                }
                            )
                            DropdownMenuItem(
                                enabled = false,
                                onClick = {},
                                text = {
                                    Text(
                                        if (store.lastExport == 0L) tr("Noch nie exportiert", "Never exported")
                                        else tr("Letzter Export: ", "Last export: ") + fmtDate(store.lastExport),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        // Navigation unten mit Icons (Material-3-Standard für Hauptbereiche einer App)
        bottomBar = {
            Column {
                trainingBar?.invoke()
                MainNavBar(selected = tab, onSelect = { i -> if (i < 0) onBot() else onTab(i) })
            }
        },
        // Schwebender Haupt-Button je nach Bereich
        floatingActionButton = {
            when (tab) {
                1 -> ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { showNewWorkout = true },
                    icon = { AppIcon(Ico.ADD) },
                    text = { Text(tr("Workout", "Workout")) }
                )
            }
        }
    ) { padding ->
        // Wischbare Bereiche: eine Seite pro Tab. Abgleich in beide Richtungen wie im Trainingsmodus:
        //  - Tipp auf die Navigation ändert `tab` → Pager scrollt animiert dorthin
        //  - Wischen → sobald die Seite eingerastet ist, wird `tab` aktualisiert
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) { page ->
            val modifier = Modifier.fillMaxSize()
            when (page) {
                0 -> DashboardTab(store, modifier, onStartWorkout, onOpenWorkout, onOpenSession, onGoTo = onTab, onNewWorkout = { showNewWorkout = true })
                1 -> WorkoutList(store, modifier, onOpenWorkout, onNew = { showNewWorkout = true }, onStart = onStartWorkout, onFree = onFree)
                2 -> CalendarTab(store, modifier, calMonth, onCalMonth, calDay, onCalDay, onOpenSession)
                else -> BodyTab(store, modifier)
            }
        }
    }

    if (showNewWorkout) {
        NewWorkoutDialog(
            onDismiss = { showNewWorkout = false },
            onCreate = { name ->
                showNewWorkout = false
                store.addWorkout(name)?.let { onCreatedWorkout(it.id) }
            },
            // Vorlage: Workout ist sofort fertig → direkt öffnen (nicht erst die Auswahl)
            onTemplate = { t ->
                showNewWorkout = false
                onOpenWorkout(store.addWorkoutFromTemplate(t).id)
            }
        )
    }
}

/**
 * Navigationsleiste unten: 4 Tabs (0–3) + Knopf „Fragen“ (-1, kein Tab, eigener Bildschirm).
 * Wird auf der Startseite und im Bildschirm „Fragen“ gezeigt; `selected` = markierter Eintrag.
 */
@Composable
fun MainNavBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        listOf(
            Triple(Ico.HOME, tr("Start", "Home"), 0),
            Triple(Ico.LIST, tr("Workouts", "Workouts"), 1),
            Triple(Ico.CALENDAR, tr("Kalender", "Calendar"), 2),
            Triple(Ico.BODY, tr("Körper", "Body"), 3),
            Triple(Ico.CHAT, tr("Fragen", "Ask"), -1)
        ).forEach { (icon, label, i) ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { AppIcon(icon) },
                label = { Text(label, maxLines = 1, softWrap = false) },
                // Akzentfarbe statt der Standard-Zweitfarbe
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Profil für genauere Kalorien (optional): Geschlecht, Geburtsjahr, Größe. Erst wenn alles ausgefüllt ist,
 * rechnet estimateKcal mit dem Grundumsatz (Mifflin-St-Jeor), sonst mit der einfachen MET-Formel.
 */
@Composable
fun ProfileSettings(store: GymStore) {
    val p = store.profile
    // Eingabetext lokal halten (auch halb getippte Werte), gespeichert wird nur Gültiges
    var year by remember { mutableStateOf(p.birthYear?.toString() ?: "") }
    var height by remember { mutableStateOf(p.heightCm?.toString() ?: "") }
    Text(
        tr("Mit Geschlecht, Geburtsjahr und Größe (plus Körpergewicht) wird der Verbrauch über deinen Grundumsatz genauer geschätzt.",
            "With sex, birth year and height (plus body weight) calories are estimated more precisely from your resting metabolic rate."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("m" to tr("Männlich", "Male"), "f" to tr("Weiblich", "Female")).forEach { (key, label) ->
            FilterChip(
                selected = p.sex == key,
                onClick = { store.updateProfile(p.copy(sex = if (p.sex == key) null else key)) }, // erneut tippen = abwählen
                label = { Text(label) }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = year,
            onValueChange = { v ->
                year = v.filter(Char::isDigit).take(4)
                val y = year.toIntOrNull()
                store.updateProfile(store.profile.copy(birthYear = y?.takeIf { Profile.validYear(it) }))
            },
            label = { Text(tr("Geburtsjahr", "Birth year")) },
            isError = year.isNotEmpty() && !Profile.validYear(year.toIntOrNull()),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = height,
            onValueChange = { v ->
                height = v.filter(Char::isDigit).take(3)
                val h = height.toIntOrNull()
                store.updateProfile(store.profile.copy(heightCm = h?.takeIf { Profile.validHeight(it) }))
            },
            label = { Text(tr("Größe (cm)", "Height (cm)")) },
            isError = height.isNotEmpty() && !Profile.validHeight(height.toIntOrNull()),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
    if (store.profile.isComplete && store.weights.isEmpty()) Text(
        tr("Trag im Tab „Körper“ noch dein Gewicht ein.", "Also log your weight in the \"Body\" tab."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * „Freies Training“: fest oben in der Workout-Liste, nicht löschbar (kein echtes Workout). Zeigt „Meine Übungen“
 * als Kacheln (früher Tab „Übungen“); Sätze ohne Workout zählen als freies Training des Tages. Web: freeTrainingScreen in home.js.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeTrainingScreen(
    store: GymStore,
    filter: ExerciseFilter,
    onFilter: (ExerciseFilter) -> Unit,
    onOpen: (String) -> Unit,
    onCatalog: () -> Unit,
    onBack: () -> Unit,
    /** läuft gerade ein freies Training? (dann „Fortsetzen“, Kachel = ins Training) */
    running: Boolean = false,
    onStart: () -> Unit = {}
) {
    val ui = rememberFilterUi(filter)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Freies Training", "Free workout")) },
                navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
                actions = {
                    // ＋ = Übungskatalog (eigene Liste erweitern)
                    IconAction(Ico.ADD, onClick = onCatalog)
                    if (store.exercises.isNotEmpty()) FilterActions(ui, filter, onFilter)
                }
            )
        },
        // ▶ startet das freie Training (Dauer, Pausen-Timer, Übersicht) – Sätze ohne Start gehen weiterhin
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onStart,
                icon = { AppIcon(Ico.PLAY) },
                text = { Text(if (running) tr("Fortsetzen", "Resume") else tr("Training starten", "Start workout")) }
            )
        }
    ) { padding -> ExerciseGrid(store, Modifier.padding(padding).fillMaxSize(), filter, onFilter, ui, onOpen, onCatalog) }
}

/** Alle eigenen Übungen als Kacheln, gruppiert nach Kategorie. */
@Composable
fun ExerciseGrid(
    store: GymStore,
    modifier: Modifier,
    filter: ExerciseFilter,
    onFilter: (ExerciseFilter) -> Unit,
    ui: FilterUiState,
    onOpen: (String) -> Unit,
    onCatalog: () -> Unit
) {
    if (store.exercises.isEmpty()) {
        Column(modifier.padding(16.dp)) {
            Text(tr("Noch keine Übungen. Wähle welche aus dem Katalog aus.", "No exercises yet. Pick some from the catalog."))
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCatalog) { Text(tr("Übungskatalog öffnen", "Open exercise catalog")) }
        }
        return
    }
    // Nur Kategorien/Geräte anbieten, die in den eigenen Übungen vorkommen
    val groups = store.exercises.map { store.groupOf(it) }.distinct().sortedBy { it.ordinal }
    val equipment = store.exercises.mapNotNull { equipmentOf(it) }.distinct().sortedBy { it.ordinal }
    val visible = store.exercises.filter { filter.matches(it, store.groupOf(it)) }
    // Aus „Meine Übungen“ entfernen (Workouts und Verlauf bleiben), mit „Rückgängig“
    fun remove(name: String) {
        val index = store.removeFromList(name)
        Undo.show(tr("„${exName(name)}“ entfernt – bleibt in Workouts und Verlauf", "\"${exName(name)}\" removed – stays in workouts and history")) {
            store.restoreToList(name, index)
        }
    }
    // Gedrückt halten → Menü (Öffnen / Zu Workout hinzufügen / Aus Liste entfernen) statt sofort zu entfernen
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    menuFor?.let { name ->
        ExerciseMenuSheet(
            store, name,
            onOpen = { menuFor = null; onOpen(name) },
            onRemove = { menuFor = null; remove(name) },
            onDismiss = { menuFor = null }
        )
    }

    Column(modifier) {
        FilterBar(filter, onFilter, groups, equipment, ui)
        if (visible.isEmpty()) {
            NoFilterResults(onReset = { onFilter(ExerciseFilter()) })
            return@Column
        }
        TileGrid(Modifier.weight(1f).fillMaxWidth()) {
            // Hinweis auf die sonst unsichtbare Geste – nur bis zum ersten Gedrückthalten
            if (Hints.show(Hints.LIST)) item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    tr("Tippen = öffnen · gedrückt halten = weitere Aktionen", "Tap = open · long-press = more actions"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Eine Überschrift pro Kategorie, in der Reihenfolge des enums
            MuscleGroup.entries.forEach { group ->
                val names = visible.filter { store.groupOf(it) == group }.sortedByName { exName(it) } // alphabetisch
                if (names.isEmpty()) return@forEach

                // Überschrift über die ganze Zeile (alle Spalten)
                item(key = "header_${group.name}", span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeader(group)
                }
                gridItems(names, key = { it }) { name ->
                    ExerciseTile(
                        name, group, store.setsFor(name).firstOrNull(),
                        onClick = { onOpen(name) },
                        onLongClick = { Hints.used(Hints.LIST); menuFor = name },
                        favorite = store.isFavorite(name) // nur kleiner Stern, Position bleibt alphabetisch
                    )
                }
            }
        }
    }
}

/**
 * Menü einer Übungskachel (gedrückt halten): Öffnen, zu einem Workout hinzufügen (✓ = schon drin, antippen = an/aus),
 * aus „Meine Übungen“ entfernen. Web: exerciseMenu in home.js.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseMenuSheet(store: GymStore, name: String, onOpen: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var pickWorkout by rememberSaveable { mutableStateOf(false) }
    val group = store.groupOf(name)
    @Composable
    fun Action(icon: Ico, text: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon, tint = color)
            Spacer(Modifier.width(16.dp))
            Text(text, color = color, modifier = Modifier.weight(1f))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                ExerciseIcon(name, group, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(exName(name), style = MaterialTheme.typography.titleMedium)
            }
            if (!pickWorkout) {
                Action(Ico.CHEVRON, tr("Öffnen", "Open"), onClick = onOpen)
                Action(Ico.ADD, tr("Zu Workout hinzufügen", "Add to workout"), onClick = { pickWorkout = true })
                if (store.isFavorite(name)) Action(Ico.STAR_FILLED, tr("Aus Favoriten entfernen", "Remove from favorites")) {
                    store.toggleFavorite(name); onDismiss()
                } else Action(Ico.STAR, tr("Als Favorit markieren", "Mark as favorite")) { store.toggleFavorite(name); onDismiss() }
                Action(Ico.DELETE, tr("Aus der Liste entfernen", "Remove from list"), MaterialTheme.colorScheme.error, onClick = onRemove)
                Text(
                    tr("Workouts und Verlauf bleiben erhalten.", "Workouts and history are kept."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp)
                )
            } else if (store.workouts.isEmpty()) {
                Text(
                    tr("Noch keine Workouts – leg eins im Tab „Workouts“ an.", "No workouts yet – create one in the \"Workouts\" tab."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Text(tr("Zu welchem Workout?", "Which workout?"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                store.workouts.forEach { w ->
                    val inside = name in w.exercises
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                store.toggleInWorkout(w.id, name)
                                // wie in WorkoutExercisePage: Standard-Ziel 3 Sätze (Cardio 1×)
                                if (!inside) store.setTarget(w.id, name, if (group.isCardio) 1 else 3)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(w.name, modifier = Modifier.weight(1f))
                        if (inside) Text("✓ " + tr("drin", "added"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        else AppIcon(Ico.ADD, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Raster für Übungskacheln: so viele Spalten, wie mit mind. 100 dp Breite passen
 * (Handy hochkant: 3). `content` ist ein Lambda mit LazyGridScope als Empfänger –
 * darin stehen die item()/gridItems()-Aufrufe.
 */
@Composable
fun TileGrid(
    modifier: Modifier,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        state = state,
        modifier = modifier,
        // unten mehr Platz, damit der schwebende Button die letzten Kacheln nicht verdeckt
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** „Bankdrücken (Langhantel)“ → („Bankdrücken“, „Langhantel“); ohne Klammer → (Name, null). Web: splitExerciseName in exercise.js. */
fun splitExerciseName(display: String): Pair<String, String?> {
    val i = display.lastIndexOf(" (")
    if (i <= 0 || !display.endsWith(")")) return display to null
    return display.substring(0, i) to display.substring(i + 2, display.length - 1)
}

/** Kompakte Kachel einer Übung: Symbol, Name, letzter Eintrag. */
@Composable
fun ExerciseTile(
    name: String,
    group: MuscleGroup,
    last: GymSet?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gedrückt halten (null = nicht belegt, z. B. im Workout, wo langes Drücken das Ziehen startet) */
    onLongClick: (() -> Unit)? = null,
    favorite: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    // Kachel leicht in der Kategorie-Farbe getönt
    Card(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(
                // combinedClickable = Tippen + langes Drücken (wie onclick + contextmenu im Web)
                if (onLongClick == null) Modifier.clickable(onClick = onClick)
                else Modifier.combinedClickable(onClick = onClick, onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                })
            ),
        colors = CardDefaults.cardColors(
            containerColor = group.color.copy(alpha = 0.14f)
                .compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    ) {
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExerciseIcon(name, group, Modifier.size(44.dp))
            Spacer(Modifier.height(6.dp))
            // Name zweigeteilt: Grundübung + darunter klein das Gerät („Schrägbankdrücken“ / „Langhantel“).
            // Feste Höhe des Namensblocks (2 Zeilen + Gerät) → alle Kacheln einer Reihe gleich hoch.
            val (base, detail) = splitExerciseName(exName(name))
            val nameStyle = MaterialTheme.typography.labelLarge
            val detailStyle = MaterialTheme.typography.bodySmall
            val blockHeight = with(LocalDensity.current) { (nameStyle.lineHeight * 2).toDp() + detailStyle.lineHeight.toDp() }
            Column(
                Modifier.height(blockHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Ein einzelnes langes Wort bleibt in einer Zeile und wird dafür etwas kleiner (statt „Schrägbankdrü-cken“)
                BasicText(
                    base,
                    style = nameStyle.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                    maxLines = if (' ' in base) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = nameStyle.fontSize)
                )
                if (detail != null) Text(
                    detail,
                    style = detailStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (last == null) "–" else fmtSet(last, group.isCardio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        // Favorit: kleiner Stern oben rechts
        if (favorite) Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.TopEnd) { FavoriteMark() }
    }
    }
}



/** Liest höchstens [limit] Bytes (InputStream.readNBytes gibt es erst ab Android 13). */
internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
