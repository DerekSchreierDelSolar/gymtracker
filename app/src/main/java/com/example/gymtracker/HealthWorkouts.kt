package com.example.gymtracker

import android.content.Context
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.RecordIdFilter
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import android.health.connect.datatypes.ExerciseSegment
import android.health.connect.datatypes.ExerciseSegmentType as Seg
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType as Ses
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.units.Energy
import android.health.connect.datatypes.units.Mass
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.ZoneId

// ---------- Trainings → Health Connect ----------
//
// Jedes Training (gespeicherte TrainingSession und „freies Training“ pro Tag) wird als ExerciseSessionRecord
// geschrieben: Typ Krafttraining (reine Cardio-Einheiten mit passendem Typ, z. B. Laufband), Titel = Workout-Name,
// Notiz = Sätze als Text, Abschnitte (ExerciseSegment) je Satz mit Übungstyp und Wiederholungen. Dazu ein
// ActiveCaloriesBurnedRecord (geschätzte kcal ohne Grundumsatz).
//
// Abgleich wie ein Spiegel: In den SharedPreferences steht pro Training ein Fingerabdruck (Hash der Inhalte).
// Geschrieben wird nur, was neu ist oder sich geändert hat (z. B. Satz nachträglich bearbeitet); Trainings, die es
// in der App nicht mehr gibt (Fortschritt zurückgesetzt, Satz gelöscht → freies Training leer), werden gelöscht.

object HealthWorkouts {
    private const val PREFS = "health_connect"
    private const val SESSION_PREFIX = "gymtracker-session-"
    private const val KCAL_PREFIX = "gymtracker-kcal-"

    /** Grundübung (CatalogExercise.base) → Abschnitt-Typ. Nicht aufgeführte Kraftübungen: WEIGHTLIFTING. */
    private val SEGMENT_OF = mapOf(
        "Bankdrücken" to Seg.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
        "Schrägbankdrücken" to Seg.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
        "Negativbankdrücken" to Seg.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
        "Floor Press" to Seg.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
        "Enges Bankdrücken" to Seg.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
        "Kreuzheben" to Seg.EXERCISE_SEGMENT_TYPE_DEADLIFT,
        "Rumänisches Kreuzheben" to Seg.EXERCISE_SEGMENT_TYPE_DEADLIFT,
        "Sumo-Kreuzheben" to Seg.EXERCISE_SEGMENT_TYPE_DEADLIFT,
        "Rack Pulls" to Seg.EXERCISE_SEGMENT_TYPE_DEADLIFT,
        "Kniebeugen" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Frontkniebeugen" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Goblet Squat" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Hackenschmidt-Kniebeugen" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Bulgarian Split Squat" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Ausfallschritte" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Ausfallschritte gehend" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Step-Ups" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Beinpresse" to Seg.EXERCISE_SEGMENT_TYPE_LEG_PRESS,
        "Beincurls" to Seg.EXERCISE_SEGMENT_TYPE_LEG_CURL,
        "Nordic Hamstring Curls" to Seg.EXERCISE_SEGMENT_TYPE_LEG_CURL,
        "Beinstrecken" to Seg.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION,
        "Latziehen" to Seg.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN,
        "Klimmzüge" to Seg.EXERCISE_SEGMENT_TYPE_PULL_UP,
        "Australische Klimmzüge" to Seg.EXERCISE_SEGMENT_TYPE_PULL_UP,
        "Einarmiges Rudern" to Seg.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW,
        "Renegade Row" to Seg.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW,
        "Hyperextensions" to Seg.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
        "Good Mornings" to Seg.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
        "Schulterdrücken" to Seg.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
        "Arnold-Press" to Seg.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
        "Seitheben" to Seg.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
        "Frontheben" to Seg.EXERCISE_SEGMENT_TYPE_FRONT_RAISE,
        "Bizeps-Curls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Hammer Curls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Konzentrationscurls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Schrägbank-Curls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Scott-Curls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Reverse Curls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Trizepsdrücken" to Seg.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
        "Trizepsdrücken über Kopf" to Seg.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
        "French Press" to Seg.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
        "Trizeps-Kickbacks" to Seg.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_TWO_ARM,
        "Hip Thrust" to Seg.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
        "Beckenheben" to Seg.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
        "Burpees" to Seg.EXERCISE_SEGMENT_TYPE_BURPEE,
        "Jump Squat" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Hampelmann" to Seg.EXERCISE_SEGMENT_TYPE_JUMPING_JACK,
        "Swing" to Seg.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING,
        "Mountain Climbers" to Seg.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER,
        "Crunches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "McGill Curl-up" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "Bicycle Crunches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "Sit-Ups" to Seg.EXERCISE_SEGMENT_TYPE_SIT_UP,
        "V-Ups" to Seg.EXERCISE_SEGMENT_TYPE_SIT_UP,
        "Beinheben liegend" to Seg.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        "Beinheben hängend" to Seg.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        "Knieheben am Gerät" to Seg.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        "Unterarmstütz / Plank" to Seg.EXERCISE_SEGMENT_TYPE_PLANK,
        "Seitstütz" to Seg.EXERCISE_SEGMENT_TYPE_PLANK,
        "Russian Twist" to Seg.EXERCISE_SEGMENT_TYPE_UPPER_TWIST,
        "Rumpfrotation" to Seg.EXERCISE_SEGMENT_TYPE_UPPER_TWIST,
        "Brustpresse" to Seg.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
        "Gestrecktes Kreuzheben" to Seg.EXERCISE_SEGMENT_TYPE_DEADLIFT,
        "Box Squat" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Zercher Squat" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Sumo Squat" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Einbeinige Kniebeuge" to Seg.EXERCISE_SEGMENT_TYPE_SQUAT,
        "Cossack Squat" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Reverse Lunge" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Seitliche Ausfallschritte" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Cross Lunge" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Split Squat" to Seg.EXERCISE_SEGMENT_TYPE_LUNGE,
        "Glute Ham Raises" to Seg.EXERCISE_SEGMENT_TYPE_LEG_CURL,
        "Military Press" to Seg.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
        "Vorgebeugtes Seitheben" to Seg.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
        "Reverse Unterarm-Curls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Unterarm-Curls" to Seg.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        "Pull-Through" to Seg.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
        "Reverse Hyperextensions" to Seg.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
        "Superman" to Seg.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
        "Butterfly-Crunches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "Reverse Crunches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "Side Crunches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "V-Crunches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "Heel-Touches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "Toe-Touches" to Seg.EXERCISE_SEGMENT_TYPE_CRUNCH,
        "Klappmesser" to Seg.EXERCISE_SEGMENT_TYPE_SIT_UP,
        "Hüftheben" to Seg.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        "Knieheben im Liegen" to Seg.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        "Flutter Kicks" to Seg.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        "Beinschere" to Seg.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        "Spiderman Plank" to Seg.EXERCISE_SEGMENT_TYPE_PLANK,
        "Side Plank Crunches" to Seg.EXERCISE_SEGMENT_TYPE_PLANK,
        "Seitliches Hüftheben" to Seg.EXERCISE_SEGMENT_TYPE_PLANK,
        "Bauchroller" to Seg.EXERCISE_SEGMENT_TYPE_PLANK,
        "Hüftrollen" to Seg.EXERCISE_SEGMENT_TYPE_UPPER_TWIST,
        "Seitliches Oberkörperbeugen" to Seg.EXERCISE_SEGMENT_TYPE_UPPER_TWIST,
        "Trizeps-Dips" to Seg.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
        "Frog Pumps" to Seg.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
    )

    /** Cardio-Grundübung → (Session-Typ, wenn das Training nur daraus besteht; Abschnitt-Typ). */
    private val CARDIO_OF = mapOf(
        "Laufband" to (Ses.EXERCISE_SESSION_TYPE_RUNNING_TREADMILL to Seg.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL),
        "Laufband Gehen mit Steigung" to (Ses.EXERCISE_SESSION_TYPE_WALKING to Seg.EXERCISE_SEGMENT_TYPE_WALKING),
        "Laufen (draußen)" to (Ses.EXERCISE_SESSION_TYPE_RUNNING to Seg.EXERCISE_SEGMENT_TYPE_RUNNING),
        "Gehen" to (Ses.EXERCISE_SESSION_TYPE_WALKING to Seg.EXERCISE_SEGMENT_TYPE_WALKING),
        "Wandern" to (Ses.EXERCISE_SESSION_TYPE_HIKING to Seg.EXERCISE_SEGMENT_TYPE_WALKING),
        "Fahrradergometer" to (Ses.EXERCISE_SESSION_TYPE_BIKING_STATIONARY to Seg.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY),
        "Spinning" to (Ses.EXERCISE_SESSION_TYPE_BIKING_STATIONARY to Seg.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY),
        "Air Bike" to (Ses.EXERCISE_SESSION_TYPE_BIKING_STATIONARY to Seg.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY),
        "Radfahren (draußen)" to (Ses.EXERCISE_SESSION_TYPE_BIKING to Seg.EXERCISE_SEGMENT_TYPE_BIKING),
        "Crosstrainer" to (Ses.EXERCISE_SESSION_TYPE_ELLIPTICAL to Seg.EXERCISE_SEGMENT_TYPE_ELLIPTICAL),
        "Rudergerät" to (Ses.EXERCISE_SESSION_TYPE_ROWING_MACHINE to Seg.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE),
        "Stepper" to (Ses.EXERCISE_SESSION_TYPE_STAIR_CLIMBING_MACHINE to Seg.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE),
        "Schwimmen" to (Ses.EXERCISE_SESSION_TYPE_SWIMMING_POOL to Seg.EXERCISE_SEGMENT_TYPE_SWIMMING_POOL),
        "Seilspringen" to (Ses.EXERCISE_SESSION_TYPE_OTHER_WORKOUT to Seg.EXERCISE_SEGMENT_TYPE_JUMP_ROPE),
        "HIIT" to (Ses.EXERCISE_SESSION_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING to Seg.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING),
        "Boxen (Sandsack)" to (Ses.EXERCISE_SESSION_TYPE_BOXING to Seg.EXERCISE_SEGMENT_TYPE_PUNCH),
    )

    private fun baseOf(name: String) = CATALOG.firstOrNull { it.name == name }?.base ?: name

    /** Ein Training, fertig zum Schreiben. key = stabile ID („s<id>“ bzw. „f<Tag>“ beim freien Training). */
    private class Item(val key: String, val hash: String, val records: List<Record>, val plain: List<Record>)

    /**
     * Trainings übertragen. onDone bekommt die Anzahl geschriebener (neuer/geänderter) Trainings.
     * Lehnt Health Connect ein Training ab (z. B. weil ein Abschnitt-Typ nicht zum Session-Typ passt),
     * wird es ohne Abschnitte erneut geschrieben.
     */
    @RequiresApi(34)
    fun sync(
        context: Context, m: HealthConnectManager, store: GymStore,
        onDone: (Int) -> Unit, onError: (HealthConnectException) -> Unit
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Geschriebene Trainings: „key=hash“
        val written = prefs.getStringSet("sessions", emptySet())!!
            .associate { it.substringBefore('=') to it.substringAfter('=') }.toMutableMap()
        val sessions = HealthSync.canWriteWorkouts(context)
        val kcal = HealthSync.canWriteKcal(context)
        val items = allSessions(store).mapNotNull { build(store, it, sessions, kcal) }
        val changed = items.filter { written[it.key] != it.hash }
        val gone = written.keys - items.map { it.key }.toSet()

        fun save() = prefs.edit().putStringSet("sessions", written.map { "${it.key}=${it.value}" }.toSet()).apply()

        fun next(i: Int) {
            if (i >= changed.size) { save(); onDone(changed.size); return }
            val item = changed[i]
            HealthSync.insert(context, m, item.records, {
                written[item.key] = item.hash
                next(i + 1)
            }, { e ->
                Log.w("HealthWorkouts", "Training ${item.key} abgelehnt, ohne Abschnitte: ${e.message}")
                HealthSync.insert(context, m, item.plain, {
                    written[item.key] = item.hash
                    next(i + 1)
                }, { e2 -> save(); onError(e2) })
            })
        }

        val removeIds = gone.flatMap {
            listOf(
                RecordIdFilter.fromClientRecordId(ExerciseSessionRecord::class.java, SESSION_PREFIX + it),
                RecordIdFilter.fromClientRecordId(ActiveCaloriesBurnedRecord::class.java, KCAL_PREFIX + it)
            )
        }
        HealthSync.delete(context, m, removeIds) {
            gone.forEach { written.remove(it) }
            next(0)
        }
    }

    /** Training in Health-Connect-Einträge umwandeln (null = nichts zu schreiben). */
    @RequiresApi(34)
    private fun build(store: GymStore, t: TrainingSession, withSession: Boolean, withKcal: Boolean): Item? {
        val stats = sessionStats(store, t)
        val sets = stats.results.flatMap { it.sets }.sortedBy { it.time }
        if (sets.isEmpty()) return null
        val key = if (t.id >= 0) "s${t.id}" else "f${-t.id}"

        // Dauer eines Satzes (geschätzt; erfasst wird nur der Zeitpunkt, an dem er eingetragen wurde)
        fun duration(s: GymSet): Long = when {
            store.groupOf(s.exercise).isCardio -> s.reps * 60_000L
            isTimed(s.exercise) -> s.reps * 1000L
            else -> ((s.reps * 3 + 10) * 1000L).coerceIn(20_000L, 120_000L)
        }.coerceAtLeast(5_000L)

        // Freies Training hat keine echte Start-/Endzeit: vom Beginn des ersten bis zum letzten Satz
        val start = if (t.id >= 0) t.start else sets.minOf { it.time - duration(it) }
        val end = (if (t.id >= 0) t.end else sets.last().time).coerceAtLeast(start + 60_000L)

        val cardio = stats.results.filter { it.group.isCardio }
        val onlyCardio = cardio.size == stats.results.count { it.sets.isNotEmpty() } && cardio.isNotEmpty()
        val type = if (onlyCardio) CARDIO_OF[baseOf(cardio.first().name)]?.first ?: Ses.EXERCISE_SESSION_TYPE_OTHER_WORKOUT
        else Ses.EXERCISE_SESSION_TYPE_STRENGTH_TRAINING

        // Abschnitte je Satz: endet beim Eintragen, ohne Überlappung, innerhalb des Trainings
        val weights = Build.VERSION.SDK_INT >= 34 &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 19   // Gewicht pro Abschnitt
        val segments = mutableListOf<ExerciseSegment>()
        var prevEnd = start
        for (s in sets) {
            val segEnd = s.time.coerceIn(start, end)
            val segStart = maxOf(segEnd - duration(s), prevEnd)
            if (segStart >= segEnd) continue
            val isCardio = store.groupOf(s.exercise).isCardio
            val segType = if (isCardio) {
                if (onlyCardio) CARDIO_OF[baseOf(s.exercise)]?.second ?: Seg.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
                else Seg.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
            } else SEGMENT_OF[baseOf(s.exercise)] ?: Seg.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
            val b = ExerciseSegment.Builder(Instant.ofEpochMilli(segStart), Instant.ofEpochMilli(segEnd), segType)
            if (!isCardio && !isTimed(s.exercise) && s.reps > 0) b.setRepetitionsCount(s.reps)
            if (weights && s.weight > 0) b.setWeight(Mass.fromGrams(s.weight * 1000))
            segments += b.build()
            prevEnd = segEnd
        }

        // Notiz: Übungen mit ihren Sätzen, z. B. „Bankdrücken: 60 kg × 10, 80 kg × 8“
        val notes = stats.results.filter { it.sets.isNotEmpty() }.joinToString("\n") { r ->
            exName(r.name) + ": " + r.sets.sortedBy { it.time }.joinToString(", ") { fmtSet(it, r.group.isCardio) }
        }

        val zone = ZoneId.systemDefault().rules
        val s0 = Instant.ofEpochMilli(start)
        val e0 = Instant.ofEpochMilli(end)
        fun session(withSegments: Boolean): Record =
            ExerciseSessionRecord.Builder(HealthSync.metadata(SESSION_PREFIX + key), s0, e0, type)
                .setTitle(t.workoutName)
                .setNotes(notes)
                .setStartZoneOffset(zone.getOffset(s0)).setEndZoneOffset(zone.getOffset(e0))
                .apply { if (withSegments) setSegments(segments) }
                .build()

        // Aktiver Verbrauch = Schätzung minus Grundumsatz in dieser Zeit (1 MET)
        val active = stats.kcal?.let { total ->
            val kg = store.weightAt(t.start) ?: return@let null
            val rest = kcalPerMetHour(store, kg, t.start) * (end - start) / 3_600_000.0
            (total - rest).coerceAtLeast(0.0)
        }
        val kcalRecord = if (withKcal && active != null && active > 0)
            ActiveCaloriesBurnedRecord.Builder(HealthSync.metadata(KCAL_PREFIX + key), s0, e0, Energy.fromCalories(active * 1000))
                .setStartZoneOffset(zone.getOffset(s0)).setEndZoneOffset(zone.getOffset(e0))
                .build()
        else null

        val records = listOfNotNull(if (withSession) session(true) else null, kcalRecord)
        if (records.isEmpty()) return null
        val plain = listOfNotNull(if (withSession) session(false) else null, kcalRecord)
        // Fingerabdruck: alles, was in den Einträgen landet (auch welche Berechtigungen galten)
        val hash = listOf(start, end, type, t.workoutName, notes, active?.let { Math.round(it) }, withSession, withKcal,
            sets.joinToString(";") { "${it.time},${it.weight},${it.reps},${it.type}" }).hashCode().toString()
        return Item(key, hash, records, plain)
    }
}
