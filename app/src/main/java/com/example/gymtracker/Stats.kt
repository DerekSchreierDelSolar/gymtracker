package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

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
    val kcal: Int?
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
 * Geschätzter Kalorienverbrauch eines Trainings (MET-Methode, siehe CARDIO_MET).
 * Cardio: eingetragene Minuten × MET des Geräts.
 * Kraft: restliche Trainingszeit × 5 MET; beim freien Training (keine echte Dauer)
 * 2,5 Minuten pro Satz inkl. Pause.
 */
fun estimateKcal(store: GymStore, t: TrainingSession, results: List<ExerciseResult>): Int? {
    val kg = store.weightAt(t.start) ?: return null
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
        kcal = estimateKcal(store, t, results)
    )
}

/** Alle Trainings inkl. freier Trainings (eines pro Tag mit Sätzen ohne Workout). */
fun allSessions(store: GymStore): List<TrainingSession> =
    store.sessions + store.freeSetsByDay().keys.mapNotNull { store.freeSession(it) }


