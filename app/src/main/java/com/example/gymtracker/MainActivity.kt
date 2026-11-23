package com.example.gymtracker

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.gymtracker.ui.theme.GymTrackerTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    private lateinit var store: GymStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GymStore(applicationContext)
        AutoBackup.load(applicationContext)
        HealthSync.load(applicationContext)
        Recovery.load(applicationContext)
        store.onWeightChange = { old, new -> HealthSync.onWeightChange(applicationContext, old, new) }
        store.onTrainingFinished = { if (HealthSync.enabled) HealthSync.sync(applicationContext, store) }
        Wiki.load(applicationContext)
        MuscleWiki.load(applicationContext)
        SupplementWiki.load(applicationContext)
        Bot.load(applicationContext)
        Reminder.schedule(applicationContext) // Erinnerung (falls an) nach Update/Neuinstallation sicher planen
        ExerciseImages.load(applicationContext)
        Hints.load(applicationContext)
        setContent {
            // Gewähltes Design; "system" folgt der Einstellung des Handys
            val dark = when (store.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // Status- und Navigationsleiste passend einfärben (helle Symbole auf dunklem
            // Hintergrund und umgekehrt). Läuft erneut, sobald sich `dark` ändert.
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                    navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { dark }
                )
                onDispose { }
            }
            // Theme aus ui/theme/Theme.kt (vom Projekt-Template). dynamicColor = false:
            // feste App-Farben statt Farben aus dem Hintergrundbild (Android 12+).
            GymTrackerTheme(darkTheme = dark, dynamicColor = false) {
                // Box: „Rückgängig“-Leiste liegt über allen Bildschirmen
                Box(Modifier.fillMaxSize()) {
                    GymApp(store)
                    UndoBar()
                }
            }
        }
    }

    /** App sichtbar? (für den Pausen-Timer, siehe RestTimer) */
    override fun onStart() {
        super.onStart()
        RestTimer.appVisible = true
        HealthSync.syncIfDue(applicationContext, store) // Gewicht von anderen Apps (z. B. Waage) holen
    }

    /** App geht in den Hintergrund (auch beim Wechsel zu einer anderen App): ggf. automatisch sichern. */
    override fun onStop() {
        super.onStop()
        RestTimer.appVisible = false // Pausen-Ende dann per Benachrichtigung melden
        AutoBackup.runIfDue(applicationContext, store)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GymApp(store: GymStore) {
    // Navigation über einfachen State. Reihenfolge im `when` = was „oben“ liegt:
    // Übungsdetail > Workout-Übungsauswahl > Workout > Katalog > Startseite.
    // Dadurch führt „Zurück“ aus einer Übung wieder ins Workout, aus dem sie geöffnet wurde.
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showWiki by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) } // Einstellungen (SettingsScreen.kt)
    var showBot by rememberSaveable { mutableStateOf(false) }   // „Fragen“ (früher ein Tab, jetzt über ⋮ / Wiki)
    var workoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Reiter im Workout (0 = Übungen, 1 = Überblick); liegt hier, damit er beim Zurückkehren aus Übung/Training erhalten bleibt
    var workoutTab by rememberSaveable(workoutId) { mutableIntStateOf(0) }
    var pickForWorkout by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Laufendes Training: Startzeit (null = kein Training), Workout und aktuelle Übung.
    // Unabhängig von `workoutId` (= angezeigtes Workout), damit man das Training minimieren und
    // in der App herumgehen kann, während es weiterläuft.
    // Startwerte aus TrainingPrefs (TrainingState.kt): ein laufendes Training übersteht so auch das Wegwischen der App
    val context = LocalContext.current
    val restored = remember { TrainingPrefs.load(context) }
    var trainingStart by rememberSaveable { mutableStateOf(restored?.start) }
    var trainingWorkoutId by rememberSaveable { mutableStateOf(restored?.workoutId) }
    var trainingIndex by rememberSaveable { mutableIntStateOf(restored?.index ?: 0) }
    // false = minimiert (Leiste „Training läuft“ statt Trainingsbildschirm)
    var trainingOpen by rememberSaveable { mutableStateOf(restored?.open ?: true) }
    // Pause: Endzeitpunkt (null = keine) und Gesamtlänge – hier oben, damit sie beim Minimieren weiterläuft
    var restEnd by rememberSaveable { mutableStateOf(restored?.restEnd) }
    var restTotal by rememberSaveable { mutableLongStateOf(restored?.restTotal ?: 0L) }
    // Training starten, während schon eins läuft → Hinweis
    var startBlocked by remember { mutableStateOf(false) }
    // Tausch nur für das laufende Training: Original → Alternative (HashMap ist Serializable → rememberSaveable)
    var swaps by rememberSaveable { mutableStateOf<Map<String, String>>(HashMap(restored?.swaps.orEmpty())) }
    // Während des Trainings: Workout bearbeiten offen? Wert = Übung, die im Training gerade angezeigt war
    var editInTraining by rememberSaveable { mutableStateOf<String?>(null) }
    // Geöffnete Trainings-Übersicht
    var summaryId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Übersicht direkt nach dem Training (nicht aus dem Kalender) → feiern (Celebration.kt)
    var celebrateId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Kalender: angezeigter Monat und gewählter Tag. Hier oben gespeichert, damit sie erhalten
    // bleiben, wenn man aus dem Kalender eine Trainingsübersicht öffnet und zurückkommt.
    var calMonth by rememberSaveable { mutableIntStateOf(monthIndex(System.currentTimeMillis())) }
    var calDay by rememberSaveable { mutableIntStateOf(dayKey(System.currentTimeMillis())) }
    // Filter für Übungsliste und Katalog (getrennt). Hier oben gehalten, damit sie erhalten
    // bleiben, wenn man eine Übung öffnet und zurückkommt.
    var listFilter by remember { mutableStateOf(ExerciseFilter()) }
    var catalogFilter by remember { mutableStateOf(ExerciseFilter()) }

    // Laufendes Training bei jeder Änderung dauerhaft merken (bzw. löschen, sobald es beendet ist)
    LaunchedEffect(trainingStart, trainingWorkoutId, trainingIndex, trainingOpen, restEnd, restTotal, swaps) {
        val s = trainingStart
        val id = trainingWorkoutId
        TrainingPrefs.save(context, if (s != null && id != null) TrainingState(s, id, trainingIndex, trainingOpen, swaps, restEnd, restTotal) else null)
    }

    val ex = current
    val workout = workoutId?.let { store.workout(it) }
    // Negative ID = freies Training (wird aus den Sätzen des Tages gebaut)
    val summary = summaryId?.let { if (it < 0) store.freeSession((-it).toInt()) else store.session(it) }
    val start = trainingStart
    val trainingWorkout = trainingWorkoutId?.let { store.workout(it) }
    // Nur für dieses Training getauschte Übungen (Original → Alternative); das Workout selbst bleibt unverändert
    val session = trainingWorkout?.let { sessionWorkout(it, swaps, isDeload(store)) }

    fun resetTraining() {
        trainingStart = null
        trainingWorkoutId = null
        trainingIndex = 0
        trainingOpen = true
        swaps = HashMap()
        editInTraining = null
        restEnd = null
        RestTimer.cancel(context)
    }
    // Workout gelöscht, während das Training minimiert war → Training aufgeben (Sätze bleiben als freies Training)
    if (start != null && trainingWorkout == null) SideEffect { resetTraining() }

    fun startTraining(id: Long) {
        when {
            trainingStart == null -> {
                trainingStart = System.currentTimeMillis()
                trainingWorkoutId = id
                trainingIndex = 0
                trainingOpen = true
                swaps = HashMap()
                restEnd = null
            }
            trainingWorkoutId == id -> trainingOpen = true // dasselbe Workout: einfach fortsetzen
            else -> startBlocked = true
        }
    }

    // Pause läuft auch weiter, wenn das Training minimiert ist
    RestTimerEffect(restEnd, session?.exercises?.getOrNull(trainingIndex) ?: "", onDone = { restEnd = null })

    val trainingVisible = start != null && session != null && trainingOpen
    val homeVisible = !trainingVisible && summary == null && ex == null && workout == null &&
        !showCatalog && !showBot && !showWiki && !showSettings
    val showBar = start != null && trainingWorkout != null && !trainingVisible
    val bar: @Composable () -> Unit = {
        TrainingBar(trainingWorkout?.name ?: "", start ?: 0L, restEnd, onOpen = { trainingOpen = true })
    }
    // Bei minimiertem Training auf der Startseite: Zurück schickt die App in den Hintergrund, statt sie zu
    // beenden (sonst ginge das laufende Training auf älteren Android-Versionen verloren).
    // Steht vor den Bildschirmen → deren eigene BackHandler haben Vorrang.
    BackHandler(enabled = showBar && homeVisible) { (context as? Activity)?.moveTaskToBack(true) }

    // Leiste unten auf allen anderen Bildschirmen (Startseite: über der Navigationsleiste).
    // consumeWindowInsets: der Bildschirm darüber lässt unten keinen Platz mehr für die Systemleiste frei,
    // das übernimmt die Leiste. Bei offener Tastatur ausgeblendet.
    val globalBar = showBar && !homeVisible && !WindowInsets.isImeVisible
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .then(if (globalBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
        ) {
            when {
                summary != null -> {
                    BackHandler { summaryId = null; celebrateId = null }
                    SummaryScreen(store, summary, celebrate = summary.id == celebrateId, onDone = { summaryId = null; celebrateId = null })
                }
                // Workout während des laufenden Trainings bearbeiten (Training läuft weiter, Zurück = zurück ins Training)
                trainingVisible && editInTraining != null -> {
                    val backToTraining = {
                        // wieder auf die Übung, die vorher offen war (Index kann sich durch Hinzufügen/Entfernen verschoben haben)
                        val i = sessionWorkout(trainingWorkout, swaps).exercises.indexOf(editInTraining)
                        if (i >= 0) trainingIndex = i
                        editInTraining = null
                    }
                    BackHandler { backToTraining() }
                    WorkoutPickerScreen(store, trainingWorkout, onBack = backToTraining)
                }
                trainingVisible -> {
                    TrainingScreen(
                        store, session, start,
                        index = trainingIndex.coerceIn(0, (session.exercises.size - 1).coerceAtLeast(0)),
                        onIndex = { trainingIndex = it },
                        onSwap = { from, to -> swaps = swapTo(swaps, trainingWorkout, from, to) },
                        originalOf = { e -> swaps.entries.firstOrNull { it.value == e }?.key },
                        onEdit = { editInTraining = session.exercises.getOrNull(trainingIndex) ?: "" },
                        onMinimize = { trainingOpen = false },
                        restEnd = restEnd,
                        restTotal = restTotal,
                        onRest = { end, total -> restEnd = end; restTotal = total },
                        onFinish = { end ->
                            // Übungen, die vor dem Tausch schon Sätze hatten, gehören trotzdem zum Training
                            val withSets = session.copy(exercises = session.exercises + swaps.keys.filter { o ->
                                o !in session.exercises && store.setsBetween(o, start, Long.MAX_VALUE).isNotEmpty()
                            })
                            val t = store.finishTraining(withSets, start, end)
                            resetTraining()
                            summaryId = t?.id
                            celebrateId = t?.id
                        }
                    )
                }
                ex != null -> {
                    BackHandler { current = null }
                    ExerciseDetailScreen(store, ex, onBack = { current = null })
                }
                workout != null && pickForWorkout -> {
                    BackHandler { pickForWorkout = false }
                    WorkoutPickerScreen(store, workout, onBack = { pickForWorkout = false })
                }
                workout != null -> {
                    BackHandler { workoutId = null }
                    WorkoutScreen(
                        store, workout,
                        onBack = { workoutId = null },
                        onOpen = { current = it },
                        onEdit = { pickForWorkout = true },
                        onStart = { startTraining(workout.id) },
                        onOpenSession = { summaryId = it },
                        onDuplicate = { store.duplicateWorkout(workout.id)?.let { workoutId = it.id } },
                        running = start != null && trainingWorkoutId == workout.id,
                        tab = workoutTab,
                        onTab = { workoutTab = it }
                    )
                }
                showCatalog -> {
                    BackHandler { showCatalog = false }
                    CatalogScreen(store, catalogFilter, onFilter = { catalogFilter = it }, onBack = { showCatalog = false })
                }
                showSettings -> {
                    BackHandler { showSettings = false }
                    SettingsScreen(store, onBack = { showSettings = false })
                }
                showBot -> {
                    BackHandler { showBot = false }
                    BotScreen(store, onBack = { showBot = false }, onOpenWorkout = { showBot = false; workoutId = it })
                }
                showWiki -> {
                    BackHandler { showWiki = false }
                    WikiScreen(onBack = { showWiki = false }, onAsk = { showBot = true })
                }
                else -> HomeScreen(
                    store,
                    tab = tab,
                    onTab = { tab = it },
                    onOpen = { current = it },
                    onCatalog = { showCatalog = true },
                    onWiki = { showWiki = true },
                    onBot = { showBot = true },
                    onSettings = { showSettings = true },
                    onOpenWorkout = { workoutId = it },
                    onCreatedWorkout = { workoutId = it; pickForWorkout = true },
                    calMonth = calMonth,
                    onCalMonth = { calMonth = it },
                    calDay = calDay,
                    onCalDay = { calDay = it },
                    onOpenSession = { summaryId = it },
                    listFilter = listFilter,
                    onListFilter = { listFilter = it },
                    // Schnellstart (Dashboard, ▶ in der Workout-Liste): Training direkt starten
                    onStartWorkout = { id -> startTraining(id) },
                    trainingBar = if (showBar) bar else null
                )
            }
        }
        // Hintergrund in Leistenfarbe auch unter der Systemleiste (Gesten-Bereich)
        if (globalBar) Box(
            Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) { bar() }
    }

    if (startBlocked) {
        AlertDialog(
            onDismissRequest = { startBlocked = false },
            title = { Text(tr("Training läuft bereits", "Workout in progress")) },
            text = {
                val name = trainingWorkout?.name ?: ""
                Text(tr(
                    "„$name“ läuft noch. Beende es, bevor du ein neues Training startest.",
                    "“$name” is still running. Finish it before starting a new workout."
                ))
            },
            confirmButton = {
                TextButton(onClick = { startBlocked = false; trainingOpen = true }) { Text(tr("Zum Training", "Go to workout")) }
            },
            dismissButton = { TextButton(onClick = { startBlocked = false }) { Text(tr("Abbrechen", "Cancel")) } }
        )
    }
}


/** Workout für das laufende Training: getauschte Übungen ersetzt, Ziel-Sätze von der Original-Übung übernommen. */
fun sessionWorkout(w: Workout, swaps: Map<String, String>, deload: Boolean = false): Workout {
    if (swaps.isEmpty() && !deload) return w
    return w.copy(
        // distinct: Alternative, die danach (Workout im Training bearbeiten) auch selbst ins Workout kam, nur einmal –
        // sonst doppelter Pager-Schlüssel (Absturz) und doppelt gezählte Sätze
        exercises = w.exercises.map { swaps[it] ?: it }.distinct(),
        // Deload-Woche (Cycle.kt): halbe Ziel-Sätze; fehlende Einträge = Standard 0 (optional) bleiben so
        targets = w.targets.mapKeys { (k, _) -> swaps[k] ?: k }.mapValues { (_, t) -> if (deload) deloadTarget(t) else t },
        links = w.links.map { swaps[it] ?: it }.toSet()
    )
}

/** Tausch eintragen: `from` ist die angezeigte Übung (Original oder schon eine Alternative); to == Original = zurück */
fun swapTo(swaps: Map<String, String>, w: Workout, from: String, to: String): Map<String, String> {
    val original = swaps.entries.firstOrNull { it.value == from }?.key ?: from
    val out = HashMap(swaps)
    if (to == original) out.remove(original) else if (original in w.exercises) out[original] = to
    return out
}
