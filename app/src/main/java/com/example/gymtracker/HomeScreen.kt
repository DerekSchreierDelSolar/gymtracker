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
    onBot: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onCreatedWorkout: (Long) -> Unit,
    calMonth: Int,
    onCalMonth: (Int) -> Unit,
    calDay: Int,
    onCalDay: (Int) -> Unit,
    onOpenSession: (Long) -> Unit,
    listFilter: ExerciseFilter,
    onListFilter: (ExerciseFilter) -> Unit,
    onStartWorkout: (Long) -> Unit,
    /** Leiste „Training läuft“ (minimiertes Training) – sitzt über der Navigationsleiste */
    trainingBar: (@Composable () -> Unit)? = null
) {
    var showNewWorkout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val listUi = rememberFilterUi(listFilter)

    // Pager für die fünf Hauptbereiche, synchron mit `tab` („Fragen“ ist jetzt ein eigener Bildschirm im ⋮-Menü)
    val pagerState = rememberPagerState(initialPage = tab.coerceAtMost(4)) { 5 }
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
        // Höchstens 20 MB lesen (echte Sicherungen sind weit kleiner): eine riesige Datei würde sonst
        // den Arbeitsspeicher sprengen und die App abstürzen lassen
        val maxBytes = 20 * 1024 * 1024
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(maxBytes + 1)
                if (bytes.size > maxBytes) null else String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
        if (text != null && store.inspectBackup(text) != null) pendingImport = text
        else toast(tr("Das ist keine gültige GymTracker-Sicherung.", "This is not a valid GymTracker backup."))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Nur der Bereichsname (der App-Name stand vorher darunter und kostete Platz)
                    Text(
                        when (tab) {
                            0 -> tr("Übersicht", "Overview")
                            1 -> tr("Übungen", "Exercises")
                            2 -> "Workouts"
                            3 -> tr("Kalender", "Calendar")
                            else -> tr("Körper", "Body")
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    // Suche + Filter nur im Übungen-Bereich (und nur, wenn es Übungen gibt)
                    if (tab == 1 && store.exercises.isNotEmpty()) FilterActions(listUi, listFilter, onListFilter)
                    // Wiki und Fragen immer direkt erreichbar (auf allen Tabs)
                    IconAction(Ico.BOOK, onClick = onWiki)
                    IconAction(Ico.CHAT, onClick = onBot)
                    // ⋮-Menü: Box ist der Anker, an dem sich das DropdownMenu ausrichtet
                    Box {
                        IconAction(Ico.MORE, onClick = { menuOpen = true })
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Einstellungen", "Settings")) },
                                leadingIcon = { AppIcon(Ico.SETTINGS) },
                                onClick = { menuOpen = false; showSettings = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(tr("Daten exportieren", "Export data")) },
                                leadingIcon = { AppIcon(Ico.UPLOAD) },
                                onClick = {
                                    menuOpen = false
                                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
                                    exportLauncher.launch("gymtracker-backup-$date.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Daten importieren", "Import data")) },
                                leadingIcon = { AppIcon(Ico.DOWNLOAD) },
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
            Column {
                trainingBar?.invoke()
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    listOf(
                        Triple(Ico.HOME, tr("Start", "Home"), 0),
                        Triple(Ico.DUMBBELL, tr("Übungen", "Exercises"), 1),
                        Triple(Ico.LIST, "Workouts", 2),
                        Triple(Ico.CALENDAR, tr("Kalender", "Calendar"), 3),
                        Triple(Ico.BODY, tr("Körper", "Body"), 4)
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
                0 -> DashboardTab(store, modifier, onStartWorkout, onOpenWorkout, onOpenSession, onGoTo = onTab, onNewWorkout = { showNewWorkout = true })
                1 -> ExerciseGrid(store, modifier, listFilter, onListFilter, listUi, onOpen, onCatalog)
                2 -> WorkoutList(store, modifier, onOpenWorkout, onNew = { showNewWorkout = true }, onStart = onStartWorkout)
                3 -> CalendarTab(store, modifier, calMonth, onCalMonth, calDay, onCalDay, onOpenSession)
                else -> BodyTab(store, modifier)
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
                        "• ${plural(info.sessions, tr("Trainingseinheit", "session"), tr("Trainingseinheiten", "sessions"))}\n\n" +
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
    Text(tr("Profil für Kalorien (optional)", "Profile for calories (optional)"), style = MaterialTheme.typography.titleSmall)
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

/** Einstellungen: Design, Sprache, Profil, Backup, Erinnerung und „Fortschritt zurücksetzen“. */
@Composable
fun SettingsDialog(store: GymStore, onDismiss: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Einstellungen", "Settings")) },
        text = {
            // scrollbar, damit auf kleinen Displays alles erreichbar bleibt
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ProfileSettings(store)
                Spacer(Modifier.height(8.dp))
                AutoBackupSettings(store)
                Spacer(Modifier.height(8.dp))
                ReminderSettings()
                Spacer(Modifier.height(8.dp))
                HealthSettings(store)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                // Fortschritt zurücksetzen (rot, mit Nachfrage und danach „Rückgängig“)
                Text(tr("Fortschritt", "Progress"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("Löscht alle Sätze, Trainingseinheiten, Notizen und Gewichtseinträge. Übungen und Workouts bleiben.",
                        "Deletes all sets, workouts done, notes and weight entries. Exercises and workouts stay."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { confirmReset = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    AppIcon(Ico.DELETE, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Fortschritt zurücksetzen", "Reset progress"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Fortschritt zurücksetzen?", "Reset progress?")) },
            text = {
                Text(
                    tr(
                        "${plural(store.sets.size, "Satz", "Sätze")}, ${plural(store.sessions.size, "Trainingseinheit", "Trainingseinheiten")} und ${plural(store.weights.size, "Gewichtseintrag", "Gewichtseinträge")} werden gelöscht. Tipp: vorher über ⋮ → „Daten exportieren“ sichern.",
                        "${plural(store.sets.size, "set", "sets")}, ${plural(store.sessions.size, "workout", "workouts")} and ${plural(store.weights.size, "weight entry", "weight entries")} will be deleted. Tip: back up first via ⋮ → \"Export data\"."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val snap = store.resetProgress()
                        confirmReset = false
                        onDismiss()
                        Undo.show(tr("Fortschritt zurückgesetzt", "Progress reset")) { store.restoreProgress(snap) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(tr("Zurücksetzen", "Reset")) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Abbrechen", "Cancel")) } }
        )
    }
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
                        onLongClick = { Hints.used(Hints.LIST); menuFor = name }
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
    }
}



/** Liest höchstens [limit] Bytes (InputStream.readNBytes gibt es erst ab Android 13). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
