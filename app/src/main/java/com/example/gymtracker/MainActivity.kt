package com.example.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.gymtracker.ui.theme.GymTrackerTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    private lateinit var store: GymStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GymStore(applicationContext)
        AutoBackup.load(applicationContext)
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
    }

    /** App geht in den Hintergrund (auch beim Wechsel zu einer anderen App): ggf. automatisch sichern. */
    override fun onStop() {
        super.onStop()
        RestTimer.appVisible = false // Pausen-Ende dann per Benachrichtigung melden
        AutoBackup.runIfDue(applicationContext, store)
    }
}

@Composable
fun GymApp(store: GymStore) {
    // Navigation über einfachen State. Reihenfolge im `when` = was „oben“ liegt:
    // Übungsdetail > Workout-Übungsauswahl > Workout > Katalog > Startseite.
    // Dadurch führt „Zurück“ aus einer Übung wieder ins Workout, aus dem sie geöffnet wurde.
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showWiki by rememberSaveable { mutableStateOf(false) }
    var showBot by rememberSaveable { mutableStateOf(false) }   // „Fragen“ (früher ein Tab, jetzt über ⋮ / Wiki)
    var workoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickForWorkout by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Laufendes Training: Startzeit (null = kein Training) und aktuelle Übung
    var trainingStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var trainingIndex by rememberSaveable { mutableIntStateOf(0) }
    // Tausch nur für das laufende Training: Original → Alternative (HashMap ist Serializable → rememberSaveable)
    var swaps by rememberSaveable { mutableStateOf<Map<String, String>>(HashMap()) }
    // Während des Trainings: Workout bearbeiten offen? Wert = Übung, die im Training gerade angezeigt war
    var editInTraining by rememberSaveable { mutableStateOf<String?>(null) }
    // Geöffnete Trainings-Übersicht
    var summaryId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Kalender: angezeigter Monat und gewählter Tag. Hier oben gespeichert, damit sie erhalten
    // bleiben, wenn man aus dem Kalender eine Trainingsübersicht öffnet und zurückkommt.
    var calMonth by rememberSaveable { mutableIntStateOf(monthIndex(System.currentTimeMillis())) }
    var calDay by rememberSaveable { mutableIntStateOf(dayKey(System.currentTimeMillis())) }
    // Filter für Übungsliste und Katalog (getrennt). Hier oben gehalten, damit sie erhalten
    // bleiben, wenn man eine Übung öffnet und zurückkommt.
    var listFilter by remember { mutableStateOf(ExerciseFilter()) }
    var catalogFilter by remember { mutableStateOf(ExerciseFilter()) }

    val ex = current
    val workout = workoutId?.let { store.workout(it) }
    // Negative ID = freies Training (wird aus den Sätzen des Tages gebaut)
    val summary = summaryId?.let { if (it < 0) store.freeSession((-it).toInt()) else store.session(it) }
    val start = trainingStart
    when {
        summary != null -> {
            BackHandler { summaryId = null }
            SummaryScreen(store, summary, onDone = { summaryId = null })
        }
        // Workout während des laufenden Trainings bearbeiten (Training läuft weiter, Zurück = zurück ins Training)
        workout != null && start != null && editInTraining != null -> {
            val backToTraining = {
                // wieder auf die Übung, die vorher offen war (Index kann sich durch Hinzufügen/Entfernen verschoben haben)
                val i = sessionWorkout(workout, swaps).exercises.indexOf(editInTraining)
                if (i >= 0) trainingIndex = i
                editInTraining = null
            }
            BackHandler { backToTraining() }
            WorkoutPickerScreen(store, workout, onBack = backToTraining)
        }
        workout != null && start != null -> {
            // Nur für dieses Training getauschte Übungen (Original → Alternative); das Workout selbst bleibt unverändert
            val session = sessionWorkout(workout, swaps)
            TrainingScreen(
                store, session, start,
                index = trainingIndex.coerceIn(0, (session.exercises.size - 1).coerceAtLeast(0)),
                onIndex = { trainingIndex = it },
                onSwap = { from, to -> swaps = swapTo(swaps, workout, from, to) },
                originalOf = { e -> swaps.entries.firstOrNull { it.value == e }?.key },
                onEdit = { editInTraining = session.exercises.getOrNull(trainingIndex) ?: "" },
                onFinish = {
                    // Übungen, die vor dem Tausch schon Sätze hatten, gehören trotzdem zum Training
                    val withSets = session.copy(exercises = session.exercises + swaps.keys.filter { o ->
                        o !in session.exercises && store.setsBetween(o, start, Long.MAX_VALUE).isNotEmpty()
                    })
                    val t = store.finishTraining(withSets, start)
                    trainingStart = null
                    trainingIndex = 0
                    swaps = HashMap()
                    summaryId = t?.id
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
                onStart = { trainingStart = System.currentTimeMillis(); trainingIndex = 0; swaps = HashMap() },
                onOpenSession = { summaryId = it },
                onDuplicate = { store.duplicateWorkout(workout.id)?.let { workoutId = it.id } }
            )
        }
        showCatalog -> {
            BackHandler { showCatalog = false }
            CatalogScreen(store, catalogFilter, onFilter = { catalogFilter = it }, onBack = { showCatalog = false })
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
            onOpenWorkout = { workoutId = it },
            onCreatedWorkout = { workoutId = it; pickForWorkout = true },
            calMonth = calMonth,
            onCalMonth = { calMonth = it },
            calDay = calDay,
            onCalDay = { calDay = it },
            onOpenSession = { summaryId = it },
            listFilter = listFilter,
            onListFilter = { listFilter = it },
            // Schnellstart vom Dashboard: Workout öffnen und Training direkt starten
            onStartWorkout = { id ->
                workoutId = id
                trainingStart = System.currentTimeMillis()
                swaps = HashMap()
                trainingIndex = 0
            }
        )
    }
}



/** Workout für das laufende Training: getauschte Übungen ersetzt, Ziel-Sätze von der Original-Übung übernommen. */
fun sessionWorkout(w: Workout, swaps: Map<String, String>): Workout {
    if (swaps.isEmpty()) return w
    return w.copy(
        exercises = w.exercises.map { swaps[it] ?: it },
        targets = w.targets.mapKeys { (k, _) -> swaps[k] ?: k }
    )
}

/** Tausch eintragen: `from` ist die angezeigte Übung (Original oder schon eine Alternative); to == Original = zurück */
fun swapTo(swaps: Map<String, String>, w: Workout, from: String, to: String): Map<String, String> {
    val original = swaps.entries.firstOrNull { it.value == from }?.key ?: from
    val out = HashMap(swaps)
    if (to == original) out.remove(original) else if (original in w.exercises) out[original] = to
    return out
}
