package com.example.gymtracker

import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Bildschirm 1: Startseite mit Tabs „Übungen“ und „Workouts“ ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: GymStore,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpen: (String) -> Unit,
    onCatalog: () -> Unit,
    onWiki: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onCreatedWorkout: (Long) -> Unit,
    calMonth: Int,
    onCalMonth: (Int) -> Unit,
    calDay: Int,
    onCalDay: (Int) -> Unit,
    onOpenSession: (Long) -> Unit,
    listFilter: ExerciseFilter,
    onListFilter: (ExerciseFilter) -> Unit,
    onStartWorkout: (Long) -> Unit
) {
    var showNewWorkout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val listUi = rememberFilterUi(listFilter)

    // Pager für die fünf Hauptbereiche, synchron mit `tab`
    val pagerState = rememberPagerState(initialPage = tab) { 6 }
    val currentOnTab by rememberUpdatedState(onTab)
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab) pagerState.animateScrollToPage(tab)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnTab(it) }
    }
    var menuOpen by remember { mutableStateOf(false) }
    // Inhalt einer gewählten Backup-Datei, die noch bestätigt werden muss
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // Android-Dateidialog „Speichern unter…“. Liefert eine Uri (oder null bei Abbruch).
    // Die App braucht dafür keine Speicher-Berechtigung: der Nutzer wählt den Ort selbst.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error(tr("Datei nicht beschreibbar", "File not writable"))
            out.use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) }
        }.onSuccess {
            store.markExported()
            toast(tr("Backup gespeichert", "Backup saved"))
        }.onFailure { toast(tr("Export fehlgeschlagen: ", "Export failed: ") + it.message) }
    }

    // Android-Dateidialog „Öffnen…“
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
        if (text != null && store.inspectBackup(text) != null) pendingImport = text
        else toast(tr("Das ist keine gültige GymTracker-Sicherung.", "This is not a valid GymTracker backup."))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (tab) {
                                0 -> tr("Übersicht", "Overview")
                                1 -> tr("Übungen", "Exercises")
                                2 -> "Workouts"
                                3 -> tr("Kalender", "Calendar")
                                4 -> tr("Körper", "Body")
                                else -> tr("Fragen", "Ask")
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "GymTracker",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Suche + Filter nur im Übungen-Bereich (und nur, wenn es Übungen gibt)
                    if (tab == 1 && store.exercises.isNotEmpty()) FilterActions(listUi, listFilter, onListFilter)
                    // Übungs-Wiki direkt aus dem Übungen-Bereich erreichbar (sonst über ⋮)
                    if (tab == 1) IconAction(Ico.BOOK, onClick = onWiki)
                    // ⋮-Menü: Box ist der Anker, an dem sich das DropdownMenu ausrichtet
                    Box {
                        IconAction(Ico.MORE, onClick = { menuOpen = true })
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Einstellungen", "Settings")) },
                                leadingIcon = { AppIcon(Ico.SETTINGS) },
                                onClick = { menuOpen = false; showSettings = true }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Übungs-Wiki", "Exercise wiki")) },
                                leadingIcon = { AppIcon(Ico.BOOK) },
                                onClick = { menuOpen = false; onWiki() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(tr("Daten exportieren", "Export data")) },
                                onClick = {
                                    menuOpen = false
                                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
                                    exportLauncher.launch("gymtracker-backup-$date.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Daten importieren", "Import data")) },
                                onClick = {
                                    menuOpen = false
                                    // */*: manche Dateimanager kennzeichnen .json nicht als application/json
                                    importLauncher.launch(arrayOf("*/*"))
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf(
                    Triple(Ico.HOME, tr("Start", "Home"), 0),
                    Triple(Ico.DUMBBELL, tr("Übungen", "Exercises"), 1),
                    Triple(Ico.LIST, "Workouts", 2),
                    Triple(Ico.CALENDAR, tr("Kalender", "Calendar"), 3),
                    Triple(Ico.BODY, tr("Körper", "Body"), 4),
                    Triple(Ico.CHAT, tr("Fragen", "Ask"), 5)
                ).forEach { (icon, label, i) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
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
        },
        // Schwebender Haupt-Button je nach Bereich
        floatingActionButton = {
            when (tab) {
                1 -> ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onCatalog,
                    icon = { AppIcon(Ico.ADD) },
                    text = { Text(tr("Übung", "Exercise")) }
                )
                2 -> ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { showNewWorkout = true },
                    icon = { AppIcon(Ico.ADD) },
                    text = { Text("Workout") }
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
                0 -> DashboardTab(store, modifier, onStartWorkout, onOpenWorkout, onOpenSession, onGoTo = onTab)
                1 -> ExerciseGrid(store, modifier, listFilter, onListFilter, listUi, onOpen, onCatalog)
                2 -> WorkoutList(store, modifier, onOpenWorkout, onNew = { showNewWorkout = true })
                3 -> CalendarTab(store, modifier, calMonth, onCalMonth, calDay, onCalDay, onOpenSession)
                4 -> BodyTab(store, modifier)
                else -> BotTab(modifier)
            }
        }
    }

    val importText = pendingImport
    val info = importText?.let { store.inspectBackup(it) }
    if (importText != null && info != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(tr("Sicherung importieren?", "Import backup?")) },
            text = {
                Text(
                    tr("Die Sicherung enthält:", "The backup contains:") + "\n" +
                        "• ${plural(info.exercises, tr("Übung", "exercise"), tr("Übungen", "exercises"))}\n" +
                        "• ${plural(info.sets, tr("Satz", "set"), tr("Sätze", "sets"))}\n" +
                        "• ${plural(info.workouts, "Workout", "Workouts")}\n" +
                        "• ${plural(info.sessions, tr("Training", "session"), tr("Trainings", "sessions"))}\n\n" +
                        tr(
                            "Deine aktuellen Daten werden dadurch ersetzt. Eine Kopie davon wird vorher in der App abgelegt.",
                            "Your current data will be replaced. A copy of it is kept in the app first."
                        )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    runCatching { store.importJson(importText) }
                        .onSuccess { toast(tr("Sicherung importiert", "Backup imported")) }
                        .onFailure { toast(tr("Import fehlgeschlagen: ", "Import failed: ") + it.message) }
                }) { Text(tr("Importieren", "Import")) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(tr("Abbrechen", "Cancel")) } }
        )
    }

    if (showSettings) SettingsDialog(store, onDismiss = { showSettings = false })

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

/** Einstellungen: Design (System/Hell/Dunkel) und Sprache (Deutsch/English). */
@Composable
fun SettingsDialog(store: GymStore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Einstellungen", "Settings")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Design", "Theme"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "system" to tr("System", "System"),
                        "light" to tr("☀ Hell", "☀ Light"),
                        "dark" to tr("☾ Dunkel", "☾ Dark")
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = store.themeMode == mode,
                            onClick = { store.setTheme(mode) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(tr("Sprache", "Language"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("de" to "Deutsch", "en" to "English").forEach { (lang, label) ->
                        FilterChip(
                            selected = appLanguage == lang,
                            onClick = { store.setLanguage(lang) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                AutoBackupSettings(store)
                Spacer(Modifier.height(8.dp))
                ReminderSettings()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
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
    var removing by remember { mutableStateOf<String?>(null) } // Übung, deren Entfernen gerade gefragt wird

    Column(modifier) {
        FilterBar(filter, onFilter, groups, equipment, ui)
        if (visible.isEmpty()) {
            NoFilterResults(onReset = { onFilter(ExerciseFilter()) })
            return@Column
        }
        TileGrid(Modifier.weight(1f).fillMaxWidth()) {
            // Eine Überschrift pro Kategorie, in der Reihenfolge des enums
            MuscleGroup.entries.forEach { group ->
                val names = visible.filter { store.groupOf(it) == group }
                if (names.isEmpty()) return@forEach

                // Überschrift über die ganze Zeile (alle Spalten)
                item(key = "header_${group.name}", span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeader(group)
                }
                gridItems(names, key = { it }) { name ->
                    ExerciseTile(
                        name, group, store.setsFor(name).firstOrNull(),
                        onClick = { onOpen(name) },
                        onLongClick = { removing = name }
                    )
                }
            }
        }
    }

    // Gedrückt halten → aus „Meine Übungen“ entfernen (Workouts und Verlauf bleiben)
    removing?.let { name ->
        val inWorkouts = store.workouts.filter { name in it.exercises }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(tr("„${exName(name)}“ entfernen?", "Remove \"${exName(name)}\"?")) },
            text = {
                Text(
                    tr("Die Übung verschwindet aus deinen Übungen. Dein Verlauf bleibt erhalten", "The exercise disappears from your exercises. Your history is kept") +
                        (if (inWorkouts.isEmpty()) "" else
                            (if (inWorkouts.size == 1) tr(", ebenso im Workout ", ", and it stays in the workout ")
                            else tr(", ebenso in den Workouts ", ", and it stays in the workouts ")) +
                                inWorkouts.joinToString(", ") { tr("„${it.name}“", "\"${it.name}\"") }) +
                        tr(". Über den Katalog kannst du sie jederzeit wieder hinzufügen.", ". You can add it again from the catalog at any time.")
                )
            },
            confirmButton = {
                TextButton(onClick = { store.removeFromList(name); removing = null }) { Text(tr("Entfernen", "Remove")) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tr("Abbrechen", "Cancel")) } }
        )
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

/** Kompakte Kachel einer Übung: Symbol, Name, letzter Eintrag. */
@Composable
fun ExerciseTile(
    name: String,
    group: MuscleGroup,
    last: GymSet?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gedrückt halten (null = nicht belegt, z. B. im Workout, wo langes Drücken das Ziehen startet) */
    onLongClick: (() -> Unit)? = null
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExerciseIcon(name, group, Modifier.size(44.dp))
            Spacer(Modifier.height(6.dp))
            // minLines = maxLines = 2 → alle Kacheln gleich hoch, egal wie lang der Name ist
            // Silbentrennung in der App-Sprache, damit lange Wörter sauber umbrechen („Schrägbank-drücken“)
            Text(
                exName(name),
                style = MaterialTheme.typography.labelLarge.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                    localeList = LocaleList(tr("de-DE", "en-US"))
                ),
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (last == null) "–" else fmtSet(last, group.isCardio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}


