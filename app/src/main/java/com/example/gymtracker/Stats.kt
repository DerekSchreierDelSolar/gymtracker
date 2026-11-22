package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Calendar

// ---------- Trainingsmodus ----------

/** Ergebnis einer Übung innerhalb eines Trainings. */
data class ExerciseResult(
    val name: String,
    val group: MuscleGroup,
    val sets: List<GymSet>,
    /** true = besser als alles vor diesem Training (Gewicht bzw. Minuten bei Cardio) */
    val record: Boolean,
    /** Ziel-Sätze für diese Übung */
    val target: Int,
    /** Sätze vom letzten Mal (letzter Trainingstag davor), leer = erstes Mal */
    val previous: List<GymSet>
) {
    /** Sätze, die aufs Ziel angerechnet werden (ohne Aufwärmen; mehr als das Ziel zählt nicht extra) */
    val counted: Int get() = minOf(sets.count { it.isWork }, target)
}

data class SessionStats(
    val results: List<ExerciseResult>,
    val sets: Int,
    val volume: Double,
    val cardioMinutes: Int,
    val records: Int,
    /** Geschätzter Verbrauch; null, wenn noch kein Körpergewicht eingetragen ist */
    val kcal: Int?,
    /** true = kcal aus dem Puls berechnet (heartRateKcal), sonst MET-Schätzung */
    val kcalFromHr: Boolean = false
) {
    /** Erledigte Ziel-Sätze (pro Übung höchstens das Ziel) */
    val done: Int get() = results.sumOf { it.counted }
    /** Summe aller Ziel-Sätze (optionale Übungen mit Ziel 0 tragen nichts bei) */
    val total: Int get() = results.sumOf { it.target }
    /** Abschluss 0.0 … 1.0 – ohne jedes Ziel zählt: irgendetwas gemacht = 100 % */
    val completion: Float get() = goalCompletion(done, total, sets)
    val percent: Int get() = Math.round(completion * 100)
}

fun exerciseResult(
    store: GymStore,
    name: String,
    start: Long,
    end: Long,
    target: Int,
    freeOnly: Boolean = false
): ExerciseResult {
    val group = store.groupOf(name)
    // Bestleistung: Cardio/Zeitübung = längste Dauer, Kraft = schwerstes Gewicht
    val metric: (GymSet) -> Double =
        if (group.isCardio || isTimed(name)) { s -> s.reps.toDouble() } else { s -> s.weight }
    // Beim freien Training nur Sätze, die zu keinem gespeicherten Training gehören –
    // sonst würden Sätze eines Workouts, das zeitlich dazwischen lag, doppelt gezählt.
    val sets = store.setsBetween(name, start, end).let { list ->
        if (!freeOnly) list
        else list.filter { s -> store.sessions.none { t -> s.time in t.start..t.end && s.exercise in t.exercises } }
    }
    // Aufwärm- und Drop-Sätze zählen nicht für Rekorde
    val best = sets.top().maxOfOrNull(metric)
    val prevBest = store.sets.filter { it.exercise == name && it.time < start && it.isTop }.maxOfOrNull(metric)
    // Bestleistung nur, wenn es vorher schon Sätze gab – sonst wäre jedes erste Training ein Rekord
    val record = best != null && prevBest != null && best > prevBest
    // Sätze vom letzten Trainingstag vor diesem Training (für „▲ +2,5 kg vs. letztes Mal“)
    val before = store.setsFor(name).filter { it.time < start && it.isTop }
    val lastDay = before.firstOrNull()?.let { dayKey(it.time) }
    val previous = before.filter { dayKey(it.time) == lastDay }
    return ExerciseResult(name, group, sets, record, target, previous)
}

/**
 * Optionale Angaben für genauere Kalorien (im JSON als "profile", nur wenn etwas gesetzt ist).
 * sex = "m" / "f", birthYear z. B. 1994, heightCm z. B. 180. Fehlt etwas, gilt die einfache MET-Formel.
 */
data class Profile(val sex: String? = null, val birthYear: Int? = null, val heightCm: Int? = null) {
    val isComplete: Boolean get() = sex != null && birthYear != null && heightCm != null
    val isEmpty: Boolean get() = sex == null && birthYear == null && heightCm == null

    companion object {
        fun validYear(y: Int?) = y != null && y in 1900..(Calendar.getInstance().get(Calendar.YEAR) - 10)
        fun validHeight(h: Int?) = h != null && h in 100..250
    }
}

/** Grundumsatz in kcal pro Tag nach Mifflin-St-Jeor; null, wenn das Profil unvollständig ist. */
fun bmrKcalPerDay(p: Profile, kg: Double, time: Long): Double? {
    if (!p.isComplete) return null
    val year = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)
    val age = (year - p.birthYear!!).coerceAtLeast(10)
    return 10 * kg + 6.25 * p.heightCm!! - 5 * age + if (p.sex == "m") 5 else -161
}

/**
 * kcal je „MET-Stunde“: Standard 1 kcal × kg; mit vollständigem Profil der persönliche Grundumsatz pro Stunde
 * (korrigierte MET-Methode – berücksichtigt Alter, Größe und Geschlecht).
 */
fun kcalPerMetHour(store: GymStore, kg: Double, time: Long): Double =
    bmrKcalPerDay(store.profile, kg, time)?.let { it / 24.0 } ?: kg

/**
 * Geschätzter Kalorienverbrauch eines Trainings (MET-Methode, siehe CARDIO_MET).
 * Cardio: eingetragene Minuten × MET des Geräts.
 * Kraft: restliche Trainingszeit × 5 MET; beim freien Training (keine echte Dauer)
 * 2,5 Minuten pro Satz inkl. Pause. Mit Profil: × Grundumsatz/24 statt × kg (kcalPerMetHour).
 */
/**
 * Verbrauch aus dem Puls (Formel nach Keytel et al. 2005, kcal/min aus Ø-Puls, Gewicht, Alter, Geschlecht) über die
 * Trainingsdauer. Genauer als MET, weil die tatsächliche Anstrengung zählt. Braucht Puls, Körpergewicht, Geschlecht
 * und Geburtsjahr; sonst null (→ MET-Schätzung).
 */
fun heartRateKcal(store: GymStore, t: TrainingSession): Int? {
    val hr = t.hr ?: return null
    val p = store.profile
    val kg = store.weightAt(t.start) ?: return null
    val sex = p.sex ?: return null
    val year = p.birthYear ?: return null
    val age = (Calendar.getInstance().apply { timeInMillis = t.start }.get(Calendar.YEAR) - year).coerceAtLeast(10)
    val perMin = if (sex == "m") (-55.0969 + 0.6309 * hr.avg + 0.1988 * kg + 0.2017 * age) / 4.184
    else (-20.4022 + 0.4472 * hr.avg - 0.1263 * kg + 0.074 * age) / 4.184
    val minutes = (t.end - t.start) / 60000.0
    return Math.round(perMin * minutes).toInt().takeIf { it > 0 }
}

fun estimateKcal(store: GymStore, t: TrainingSession, results: List<ExerciseResult>): Int? {
    val kg = store.weightAt(t.start)?.let { kcalPerMetHour(store, it, t.start) } ?: return null
    val cardio = results.filter { it.group.isCardio }.map { it.copy(sets = it.sets.work()) }
    val cardioMinutes = cardio.sumOf { r -> r.sets.sumOf { it.reps } }
    val cardioKcal = cardio.sumOf { r -> r.sets.sumOf { it.reps } * cardioMet(r.name) } * kg / 60.0

    val strengthSets = results.filter { !it.group.isCardio }.sumOf { it.sets.count { s -> s.isWork } }
    val strengthMinutes = when {
        strengthSets == 0 -> 0.0
        // echte Dauer abzüglich Cardio – aber mindestens 1 Minute pro Satz
        t.id >= 0 -> ((t.end - t.start) / 60000.0 - cardioMinutes).coerceAtLeast(strengthSets * 1.0)
        else -> strengthSets * MINUTES_PER_SET
    }
    val strengthKcal = strengthMinutes * STRENGTH_MET * kg / 60.0
    return Math.round(cardioKcal + strengthKcal).toInt()
}

fun sessionStats(store: GymStore, t: TrainingSession): SessionStats {
    // Ältere Trainings ohne gespeicherte Ziele bekommen den Standard
    val results = t.exercises.map {
        exerciseResult(
            store, it, t.start, t.end, t.targets[it] ?: defaultTarget(store.groupOf(it)),
            freeOnly = t.id < 0 // negative ID = freies Training
        )
    }
    // Volumen nur aus Kraftübungen mit Wiederholungen (bei Zeitübungen wäre kg × Sekunden sinnlos)
    // Aufwärmsätze zählen weder fürs Volumen noch für die Satzzahl
    val strength = results.filter { !it.group.isCardio && !isTimed(it.name) }.flatMap { it.sets.work() }
    val cardio = results.filter { it.group.isCardio }.flatMap { it.sets.work() }
    return SessionStats(
        results = results,
        sets = results.sumOf { it.sets.count { s -> s.isWork } },
        volume = strength.sumOf { it.weight * it.reps },
        cardioMinutes = cardio.sumOf { it.reps },
        records = results.count { it.record },
        kcal = heartRateKcal(store, t) ?: estimateKcal(store, t, results),
        kcalFromHr = heartRateKcal(store, t) != null
    )
}

/** Alle Trainings inkl. freier Trainings (eines pro Tag mit Sätzen ohne Workout). */
fun allSessions(store: GymStore): List<TrainingSession> =
    store.sessions + store.freeSetsByDay().keys.mapNotNull { store.freeSession(it) }


