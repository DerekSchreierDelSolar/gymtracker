package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

// ---------- Daten ----------

data class GymSet(
    val id: Long,
    val exercise: String,
    val weight: Double,
    val reps: Int,
    val time: Long,
    val type: SetType = SetType.NORMAL,
    /** Wdh. in Reserve (0–4, 4 = „4+“), optional nach dem Satz abgefragt (Schritt 12); null = nicht angegeben. JSON "rir" nur wenn gesetzt. */
    val rir: Int? = null
) {
    /** Arbeitssatz: zählt für Volumen, kcal, Ziel-Sätze und Heatmap (alles außer Aufwärmen). */
    val isWork: Boolean get() = type != SetType.WARMUP
    /** Zählt für Rekorde, 1RM, Fortschritt und Gewichts-Vorschlag (Normal + bis zum Versagen). */
    val isTop: Boolean get() = type == SetType.NORMAL || type == SetType.FAILURE
}

/** Satz-Typ. `short` = Kürzel (A/D/V bzw. W/D/F): Aufwärmsätze statt Nummer, außerdem im Typ-Umschalter. */
enum class SetType(private val shortDe: String, private val shortEn: String, private val de: String, private val en: String) {
    NORMAL("", "", "Normal", "Normal"),
    WARMUP("A", "W", "Aufwärmen", "Warm-up"),
    DROP("D", "D", "Drop", "Drop"),
    FAILURE("V", "F", "Versagen", "Failure");

    val label: String get() = tr(de, en)
    val short: String get() = tr(shortDe, shortEn)
    /** Farbe des Kürzels/Hinweises (Normal: keine eigene Farbe). */
    val color: Color get() = when (this) {
        NORMAL -> Color.Unspecified
        WARMUP -> Color(0xFF7A8AA0)
        DROP -> Color(0xFFE8703A)
        FAILURE -> Color(0xFFD64545)
    }

    companion object {
        /** Aus dem JSON lesen; unbekannt/fehlend = NORMAL (alte Dateien bleiben gültig). */
        fun parse(s: String?): SetType = entries.firstOrNull { it.name == s } ?: NORMAL
    }
}

/** Nur Arbeitssätze (ohne Aufwärmen). */
fun List<GymSet>.work(): List<GymSet> = filter { it.isWork }
/** Nur Sätze, die für Rekorde zählen (ohne Aufwärmen und Drop). */
fun List<GymSet>.top(): List<GymSet> = filter { it.isTop }

/**
 * Beschriftung je Satz (Liste älteste zuerst): Arbeitssätze werden 1, 2, 3 … nummeriert,
 * Aufwärmsätze bekommen ihr Kürzel („A“) und zählen nicht mit.
 */
fun setLabels(sortedAsc: List<GymSet>): List<String> {
    var n = 0
    return sortedAsc.map { if (it.isWork) "${++n}." else it.type.short }
}

/** Muskelgruppe: bestimmt Farbe und Symbol einer Übung. */
enum class MuscleGroup(private val de: String, private val en: String, val color: Color) {
    BRUST("Brust", "Chest", Color(0xFFE57373)),
    RUECKEN("Rücken", "Back", Color(0xFF64B5F6)),
    BEINE("Beine", "Legs", Color(0xFF81C784)),
    GLUTEUS("Gluteus", "Glutes", Color(0xFFFF8A65)),
    SCHULTERN("Schultern", "Shoulders", Color(0xFFFFB74D)),
    // früher eine Kategorie „Arme“ (ARME) – beim Laden alter Daten migriert, siehe migrateGroup()
    BIZEPS("Bizeps", "Biceps", Color(0xFFBA68C8)),
    TRIZEPS("Trizeps", "Triceps", Color(0xFF9575CD)),
    UNTERARM("Unterarm", "Forearms", Color(0xFFA1887F)),
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
val TIMED_CATALOG = setOf("Wandsitzen (Körpergewicht)", "Unterarmstütz / Plank (Körpergewicht)", "Seitstütz (Körpergewicht)", "Farmer's Walk (Kurzhantel)", "Farmer's Walk (Kettlebell)")

/**
 * Zusatzangaben zu eigenen Übungen (nicht im Katalog): Gerät, ob sie zeitbasiert sind, Muskeln.
 * Global, weil Formatierung und Filter sie an vielen Stellen brauchen; befüllt vom GymStore.
 * Compose-States, damit Änderungen sofort sichtbar werden.
 */
object CustomMeta {
    val equipment = mutableStateMapOf<String, Equipment>()
    val timed = mutableStateListOf<String>()
    /** Selbst gewählte Haupt-/Hilfsmuskeln (Körperdiagramm). */
    val muscles = mutableStateMapOf<String, MuscleSet>()
}

/** Wird die Übung nach Zeit (Sekunden) statt nach Wiederholungen gemessen? */
fun isTimed(name: String): Boolean = name in TIMED_CATALOG || name in CustomMeta.timed

/** Sekunden als „45 s“ bzw. „1:30 min“. */
fun fmtSeconds(sec: Int): String =
    if (sec < 60) "$sec\u00A0s" else "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}\u00A0min"

/** Text für einen einzelnen Satz, eine Cardio-Einheit oder eine Zeitübung. */
fun fmtSet(s: GymSet, cardio: Boolean): String = when {
    cardio -> "${s.reps}\u00A0min"
    isTimed(s.exercise) -> (if (s.weight > 0) "${fmtKg(s.weight)} · " else "") + fmtSeconds(s.reps)
    else -> "${fmtKg(s.weight)} × ${s.reps}"
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
    val targets: Map<String, Int> = emptyMap(),
    /** Trainingstage laut Plan: 1 = Montag … 7 = Sonntag (leer = ohne festen Tag) */
    val days: Set<Int> = emptySet(),
    /** Supersätze: Übungen, die mit der NÄCHSTEN in der Liste verbunden sind (Supersets.kt). JSON "links" nur wenn nicht leer. */
    val links: Set<String> = emptySet()
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
        "Klimmzüge (Körpergewicht)", "Rudern vorgebeugt (Langhantel)", "Latziehen (Kabelzug)", "Face Pulls (Kabelzug)", "Bizeps-Curls (Kurzhantel)", "Hammer Curls (Kurzhantel)"
    )),
    WorkoutTemplate("Beine", "Legs", listOf(
        "Kniebeugen (Langhantel)", "Rumänisches Kreuzheben (Langhantel)", "Beinpresse (Maschine)", "Beincurls (Maschine)", "Wadenheben stehend (Maschine)"
    )),
    WorkoutTemplate("Oberkörper", "Upper body", listOf(
        "Bankdrücken (Langhantel)", "Rudern vorgebeugt (Langhantel)", "Schulterdrücken (Kurzhantel)", "Latziehen (Kabelzug)", "Bizeps-Curls (Kurzhantel)", "Trizepsdrücken (Kabelzug)"
    )),
    WorkoutTemplate("Unterkörper", "Lower body", listOf(
        "Kniebeugen (Langhantel)", "Rumänisches Kreuzheben (Langhantel)", "Ausfallschritte (Kurzhantel)", "Beinstrecken (Maschine)", "Wadenheben stehend (Maschine)", "Unterarmstütz / Plank (Körpergewicht)"
    )),
    WorkoutTemplate("Ganzkörper", "Full body", listOf(
        "Kniebeugen (Langhantel)", "Bankdrücken (Langhantel)", "Rudern vorgebeugt (Langhantel)", "Schulterdrücken (Kurzhantel)", "Hip Thrust (Langhantel)", "Unterarmstütz / Plank (Körpergewicht)"
    )),
    WorkoutTemplate("Cardio & Core", "Cardio & core", listOf(
        "Rudergerät", "Laufband", "Beinheben hängend (Körpergewicht)", "Russian Twist (Körpergewicht)", "Unterarmstütz / Plank (Körpergewicht)"
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
    val targets: Map<String, Int> = emptyMap(),
    /** Puls während des Trainings (aus Health Connect, z. B. von einer Smartwatch), null = unbekannt */
    val hr: HeartRate? = null
)

/**
 * Puls eines Trainings: Durchschnitt, Maximum, Minimum und Verlauf (Ø je Minute ab Trainingsstart, 0 = keine Messung).
 * Im JSON als `hr` = { avg, max, min, curve } – nur wenn vorhanden.
 */
data class HeartRate(
    val avg: Int, val max: Int, val min: Int, val curve: List<Int>,
    /** Fein: Ø je 10 Sekunden ab Trainingsstart (0 = keine Messung) – für den Puls pro Satz. Leer bei älteren Daten. */
    val fine: List<Int> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().put("avg", avg).put("max", max).put("min", min).put("curve", JSONArray(curve))
        .also { if (fine.isNotEmpty()) it.put("fine", JSONArray(fine)) }

    /** Puls zum Zeitpunkt `time` (aus `fine`, nächster Messwert im Umkreis von 10 s), null = keine Messung */
    fun at(start: Long, time: Long): Int? {
        val i = ((time - start) / FINE_STEP).toInt()
        return listOf(i, i - 1, i + 1).firstNotNullOfOrNull { fine.getOrNull(it)?.takeIf { v -> v > 0 } }
    }

    /** Höchster Puls im Zeitraum from..to (aus `fine`) samt Zeitpunkt, null = keine Messung */
    fun peak(start: Long, from: Long, to: Long): Pair<Int, Long>? = indicesIn(start, from, to)
        .filter { fine[it] > 0 }.maxByOrNull { fine[it] }?.let { fine[it] to start + it * FINE_STEP }

    /** Niedrigster Puls im Zeitraum from..to (aus `fine`), null = keine Messung */
    fun low(start: Long, from: Long, to: Long): Int? = indicesIn(start, from, to).map { fine[it] }.filter { it > 0 }.minOrNull()

    private fun indicesIn(start: Long, from: Long, to: Long): IntRange =
        ((from - start) / FINE_STEP).toInt().coerceAtLeast(0)..((to - start) / FINE_STEP).toInt().coerceAtMost(fine.lastIndex)

    companion object {
        /** Auflösung von `fine` in ms */
        const val FINE_STEP = 10_000L

        private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it, 0).coerceIn(0, 250) }

        /** Aus dem JSON lesen; Ungültiges → null (alte oder fremde Dateien bleiben lesbar) */
        fun fromJson(o: JSONObject?): HeartRate? {
            if (o == null) return null
            val avg = o.optInt("avg", 0)
            val max = o.optInt("max", 0)
            if (avg !in 20..250 || max !in avg..250) return null
            return HeartRate(avg, max, o.optInt("min", avg).coerceIn(0, avg), o.optJSONArray("curve").ints(), o.optJSONArray("fine").ints())
        }

        /** Ø je Abschnitt der Länge `step` ab `start` (0 = keine Messung) */
        private fun buckets(s: List<Pair<Long, Int>>, start: Long, end: Long, step: Long): List<Int> {
            val sums = LongArray(((end - start) / step).toInt() + 1)
            val counts = IntArray(sums.size)
            s.forEach { (t, bpm) -> val i = ((t - start) / step).toInt(); sums[i] += bpm.toLong(); counts[i]++ }
            return sums.indices.map { if (counts[it] > 0) Math.round(sums[it].toDouble() / counts[it]).toInt() else 0 }
        }

        /** Aus Messwerten (Zeit in ms, Schläge/min) im Zeitraum start..end */
        fun of(samples: List<Pair<Long, Int>>, start: Long, end: Long): HeartRate? {
            val s = samples.filter { it.first in start..end && it.second in 25..250 }
            if (s.size < 5) return null
            return HeartRate(
                Math.round(s.map { it.second }.average()).toInt(), s.maxOf { it.second }, s.minOf { it.second },
                buckets(s, start, end, 60_000L), buckets(s, start, end, FINE_STEP)
            )
        }
    }
}

/** Map<String, Int> ↔ JSONObject (für targets) */
fun JSONObject?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optInt(it, 1) }
}

fun Map<String, Int>.toJson(): JSONObject = JSONObject().also { o -> forEach { (k, v) -> o.put(k, v) } }



/**
 * Frühere Kategorie „Arme“ (gespeichert als "ARME") → Bizeps, Trizeps oder Unterarm, je nach erstem
 * Hauptmuskel einer eigenen Übung; ohne Muskelangabe Bizeps. Gilt auch beim Import alter Sicherungen.
 */
fun migrateArmGroup(muscles: MuscleSet?): MuscleGroup = when (muscles?.primary?.firstOrNull { it in setOf("biceps", "triceps", "forearms") }) {
    "triceps" -> MuscleGroup.TRIZEPS
    "forearms" -> MuscleGroup.UNTERARM
    else -> MuscleGroup.BIZEPS
}
