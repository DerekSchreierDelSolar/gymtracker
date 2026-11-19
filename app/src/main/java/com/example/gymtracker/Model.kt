package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

// ---------- Daten ----------

data class GymSet(
    val id: Long,
    val exercise: String,
    val weight: Double,
    val reps: Int,
    val time: Long
)

/** Muskelgruppe: bestimmt Farbe und Symbol einer Übung. */
enum class MuscleGroup(private val de: String, private val en: String, val color: Color) {
    BRUST("Brust", "Chest", Color(0xFFE57373)),
    RUECKEN("Rücken", "Back", Color(0xFF64B5F6)),
    BEINE("Beine", "Legs", Color(0xFF81C784)),
    SCHULTERN("Schultern", "Shoulders", Color(0xFFFFB74D)),
    ARME("Arme", "Arms", Color(0xFFBA68C8)),
    BAUCH("Bauch", "Abs", Color(0xFF4DB6AC)),
    GANZKOERPER("Ganzkörper", "Full body", Color(0xFF7986CB)),
    CARDIO("Cardio", "Cardio", Color(0xFFF06292)),
    SONSTIGE("Sonstige", "Other", Color(0xFF90A4AE));

    val label: String get() = tr(de, en)
}

/*
 * Cardio-Übungen verwenden dasselbe Datenmodell wie Kraftübungen:
 * Die Minuten stehen im Feld `reps`, `weight` ist immer 0.
 * So bleibt die JSON-Datei kompatibel und GymSet unverändert.
 */
val MuscleGroup.isCardio: Boolean get() = this == MuscleGroup.CARDIO

/*
 * Zeitübungen (Plank, Wandsitzen …): Die Sekunden stehen ebenfalls im Feld `reps`,
 * `weight` ist optional (z. B. Farmer's Walk mit 2 × 24 kg).
 */
val TIMED_CATALOG = setOf("Unterarmstütz / Plank (Körpergewicht)", "Seitstütz (Körpergewicht)", "Wandsitzen (Körpergewicht)", "Farmer's Walk (Kurzhantel)", "Farmer's Walk (Kettlebell)")

/**
 * Zusatzangaben zu eigenen Übungen (nicht im Katalog): Gerät und ob sie zeitbasiert sind.
 * Global, weil Formatierung und Filter sie an vielen Stellen brauchen; befüllt vom GymStore.
 * Compose-States, damit Änderungen sofort sichtbar werden.
 */
object CustomMeta {
    val equipment = mutableStateMapOf<String, Equipment>()
    val timed = mutableStateListOf<String>()
}

/** Wird die Übung nach Zeit (Sekunden) statt nach Wiederholungen gemessen? */
fun isTimed(name: String): Boolean = name in TIMED_CATALOG || name in CustomMeta.timed

/** Sekunden als „45 s“ bzw. „1:30 min“. */
fun fmtSeconds(sec: Int): String =
    if (sec < 60) "$sec s" else "${sec / 60}:${(sec % 60).toString().padStart(2, '0')} min"

/** Text für einen einzelnen Satz, eine Cardio-Einheit oder eine Zeitübung. */
fun fmtSet(s: GymSet, cardio: Boolean): String = when {
    cardio -> "${s.reps} min"
    isTimed(s.exercise) -> (if (s.weight > 0) "${fmtW(s.weight)} kg · " else "") + fmtSeconds(s.reps)
    else -> "${fmtW(s.weight)} kg × ${s.reps}"
}

/**
 * Geschätztes Maximalgewicht für eine Wiederholung (1RM) nach Epley: Gewicht × (1 + Wdh./30).
 * Macht Sätze mit unterschiedlichen Wiederholungszahlen vergleichbar (60 × 10 ≈ 65 × 6).
 * Über ~12 Wiederholungen wird die Formel ungenau.
 */
fun oneRepMax(weight: Double, reps: Int): Double =
    if (reps <= 1) weight else weight * (1 + reps / 30.0)

/**
 * `name` ist der interne Schlüssel (deutsch, so steht er in der JSON-Datei),
 * `en` nur der englische Anzeigename. Dadurch bleiben gespeicherte Daten beim Sprachwechsel gültig.
 */
data class CatalogExercise(
    val name: String,
    /** Grundübung ohne Gerät, z. B. „Bankdrücken“ für „Bankdrücken (Kurzhantel)“. */
    val base: String,
    val group: MuscleGroup,
    val equipment: Equipment,
    val en: String
)

/** Benötigtes Gerät einer Übung (für den Filter). */
enum class Equipment(private val de: String, private val en: String) {
    LANGHANTEL("Langhantel", "Barbell"),
    KURZHANTEL("Kurzhantel", "Dumbbell"),
    MASCHINE("Maschine", "Machine"),
    KABEL("Kabelzug", "Cable"),
    KETTLEBELL("Kettlebell", "Kettlebell"),
    BAND("Widerstandsband", "Resistance band"),
    KOERPER("Körpergewicht", "Bodyweight"),
    CARDIO_GERAET("Cardiogerät", "Cardio machine");

    val label: String get() = tr(de, en)
}

/** Gerät laut Katalog bzw. eigener Übung; null, wenn unbekannt. */
fun equipmentOf(name: String): Equipment? =
    CATALOG.firstOrNull { it.name.equals(name, ignoreCase = true) }?.equipment ?: CustomMeta.equipment[name]

/** Anzeigename einer Übung: im Englischen der Katalog-Name, sonst der gespeicherte Name. */
fun exName(name: String): String =
    if (isEnglish) CATALOG.firstOrNull { it.name.equals(name, ignoreCase = true) }?.en ?: name
    else name

/** Ein Workout ist eine benannte, geordnete Liste von Übungsnamen. */
data class Workout(
    val id: Long,
    val name: String,
    val exercises: List<String>,
    /** Ziel-Sätze pro Übung; fehlt ein Eintrag, gilt der Standard (siehe defaultTarget) */
    val targets: Map<String, Int> = emptyMap()
)

/**
 * Abschluss 0.0 … 1.0 aus erledigten und geforderten Ziel-Sätzen.
 * Sind alle Übungen optional (Ziel-Summe 0), gilt: mindestens ein Satz = 100 %, sonst 0 %.
 */
fun goalCompletion(done: Int, total: Int, setCount: Int): Float =
    if (total == 0) (if (setCount > 0) 1f else 0f) else done.toFloat() / total

/** Fertiger Trainingsplan zum Übernehmen. Übungsnamen = Katalog-Schlüssel. */
data class WorkoutTemplate(private val de: String, private val en: String, val exercises: List<String>) {
    val label: String get() = tr(de, en)
}

val TEMPLATES = listOf(
    WorkoutTemplate("Push (Drücken)", "Push", listOf(
        "Bankdrücken (Langhantel)", "Schrägbankdrücken (Kurzhantel)", "Schulterdrücken (Kurzhantel)", "Seitheben (Kurzhantel)", "Trizepsdrücken (Kabelzug)", "Dips (Körpergewicht)"
    )),
    WorkoutTemplate("Pull (Ziehen)", "Pull", listOf(
        "Klimmzüge (Körpergewicht)", "Rudern vorgebeugt (Langhantel)", "Latziehen (Kabelzug)", "Face Pulls (Kabelzug)", "Bizepscurls (Kurzhantel)", "Hammercurls (Kurzhantel)"
    )),
    WorkoutTemplate("Beine", "Legs", listOf(
        "Kniebeugen (Langhantel)", "Rumänisches Kreuzheben (Langhantel)", "Beinpresse (Maschine)", "Beinbeuger (Maschine)", "Wadenheben stehend (Maschine)"
    )),
    WorkoutTemplate("Oberkörper", "Upper body", listOf(
        "Bankdrücken (Langhantel)", "Rudern vorgebeugt (Langhantel)", "Schulterdrücken (Kurzhantel)", "Latziehen (Kabelzug)", "Bizepscurls (Kurzhantel)", "Trizepsdrücken (Kabelzug)"
    )),
    WorkoutTemplate("Unterkörper", "Lower body", listOf(
        "Kniebeugen (Langhantel)", "Rumänisches Kreuzheben (Langhantel)", "Ausfallschritte (Kurzhantel)", "Beinstrecker (Maschine)", "Wadenheben stehend (Maschine)", "Unterarmstütz / Plank (Körpergewicht)"
    )),
    WorkoutTemplate("Ganzkörper", "Full body", listOf(
        "Kniebeugen (Langhantel)", "Bankdrücken (Langhantel)", "Rudern vorgebeugt (Langhantel)", "Schulterdrücken (Kurzhantel)", "Hip Thrust (Langhantel)", "Unterarmstütz / Plank (Körpergewicht)"
    )),
    WorkoutTemplate("Cardio & Core", "Cardio & core", listOf(
        "Rudergerät", "Laufband", "Hängendes Beinheben (Körpergewicht)", "Russian Twists (Körpergewicht)", "Unterarmstütz / Plank (Körpergewicht)"
    ))
)

/** Standard-Ziel: 0 = optional (zählt nicht in den Prozentsatz). */
@Suppress("UNUSED_PARAMETER")
fun defaultTarget(group: MuscleGroup): Int = 0

/**
 * Ein abgeschlossenes Training. Die Sätze selbst stehen weiterhin in `sets` –
 * zum Training gehören alle Sätze seiner Übungen zwischen `start` und `end`.
 * Name und Übungsliste werden kopiert, damit die Übersicht auch dann noch stimmt,
 * wenn das Workout später geändert oder gelöscht wird.
 */
data class TrainingSession(
    val id: Long,
    val workoutId: Long,
    val workoutName: String,
    val exercises: List<String>,
    val start: Long,
    val end: Long,
    /** Ziel-Sätze zum Zeitpunkt des Trainings (Kopie aus dem Workout) */
    val targets: Map<String, Int> = emptyMap()
)

/** Map<String, Int> ↔ JSONObject (für targets) */
fun JSONObject?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optInt(it, 1) }
}

fun Map<String, Int>.toJson(): JSONObject = JSONObject().also { o -> forEach { (k, v) -> o.put(k, v) } }


