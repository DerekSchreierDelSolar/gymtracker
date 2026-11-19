package com.example.gymtracker

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GymStore(context: Context) {
    private val file = File(context.filesDir, "gym_data.json")
    val exercises = mutableStateListOf<String>()
    val sets = mutableStateListOf<GymSet>()
    /** Muskelgruppe pro Übung (Übungsname → Gruppe). */
    private val groups = mutableStateMapOf<String, MuscleGroup>()
    val workouts = mutableStateListOf<Workout>()
    val sessions = mutableStateListOf<TrainingSession>()
    /** Gewichtsverlauf, älteste zuerst */
    val weights = mutableStateListOf<WeightEntry>()
    /** Standard-Pausenlänge in Sekunden (0 = kein Pausen-Timer). `private set`: nur über setRest() änderbar. */
    var restSeconds by mutableIntStateOf(90)
        private set

    fun setRest(seconds: Int) {
        restSeconds = seconds
        save()
    }

    /** Zeitpunkt des letzten Exports (0 = noch nie). */
    var lastExport by mutableLongStateOf(0L)
        private set

    fun markExported() {
        lastExport = System.currentTimeMillis()
        save()
    }

    /**
     * Wochenziel: so viele Trainings pro Woche (für das Dashboard).
     * Muss VOR `init { load() }` stehen – Kotlin initialisiert Properties in Reihenfolge.
     */
    var weeklyGoal by mutableIntStateOf(3)
        private set

    /** Design: "system" (folgt dem Handy), "light" oder "dark". */
    var themeMode by mutableStateOf("system")
        private set

    fun setTheme(mode: String) {
        themeMode = mode
        save()
    }

    /** Sprache: "de" oder "en". Setzt auch den globalen State appLanguage. */
    fun setLanguage(lang: String) {
        appLanguage = lang
        save()
    }

    init { load() }

    private fun load() {
        if (!file.exists()) return
        try {
            val text = file.readText()
            readFrom(JSONObject(text))
            // Alte Übungsnamen wurden beim Lesen umbenannt → gleich in neuer Form speichern
            if (RENAMED_EXERCISES.keys.any { "\"$it\"" in text }) save()
        } catch (e: Exception) {
            e.printStackTrace()
            // Datei ist beschädigt: Kopie aufheben, bevor der nächste save() sie überschreibt
            runCatching {
                file.copyTo(File(file.parentFile, "gym_data_corrupt_${System.currentTimeMillis()}.json"))
            }
        }
    }

    /**
     * Speichert atomar: erst in eine temporäre Datei schreiben, dann umbenennen.
     * Stürzt die App mitten im Schreiben ab, bleibt die alte Datei vollständig erhalten.
     */
    private fun save() {
        val tmp = File(file.parentFile, "gym_data.json.tmp")
        tmp.writeText(toJson().toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    // ----- Export / Import -----

    /** Alle Daten als formatiertes JSON (für die Backup-Datei). */
    fun exportJson(): String = toJson().toString(2)

    /** Was in einer Backup-Datei steckt – null, wenn es keine gültige GymTracker-Sicherung ist. */
    data class BackupInfo(val exercises: Int, val sets: Int, val workouts: Int, val sessions: Int)

    fun inspectBackup(text: String): BackupInfo? = runCatching {
        val root = JSONObject(text)
        // Mindestens die beiden Kernlisten müssen vorhanden sein
        val ex = root.getJSONArray("exercises")
        val s = root.getJSONArray("sets")
        BackupInfo(
            exercises = ex.length(),
            sets = s.length(),
            workouts = root.optJSONArray("workouts")?.length() ?: 0,
            sessions = root.optJSONArray("sessions")?.length() ?: 0
        )
    }.getOrNull()

    /**
     * Ersetzt alle Daten durch die Sicherung. Die bisherigen Daten werden vorher
     * nach gym_data_before_import.json kopiert. Wirft eine Exception bei ungültigem JSON –
     * in dem Fall bleibt alles unverändert.
     */
    fun importJson(text: String) {
        val root = JSONObject(text)           // zuerst parsen: bei Fehler wird nichts verändert
        if (file.exists()) {
            file.copyTo(File(file.parentFile, "gym_data_before_import.json"), overwrite = true)
        }
        exercises.clear(); sets.clear(); groups.clear(); workouts.clear(); sessions.clear(); weights.clear()
        CustomMeta.equipment.clear(); CustomMeta.timed.clear(); CustomMeta.muscles.clear()
        readFrom(root)
        save()
    }

    private fun readFrom(root: JSONObject) {
        run {
            val ex = root.optJSONArray("exercises") ?: JSONArray()
            for (i in 0 until ex.length()) migrateName(ex.getString(i)).let { if (it !in exercises) exercises.add(it) }
            val g = root.optJSONObject("groups") ?: JSONObject()
            g.keys().forEach { key ->
                runCatching { MuscleGroup.valueOf(g.getString(key)) }
                    .onSuccess { groups[migrateName(key)] = it }
            }
            // Eigene Übungen: Gerät und Zeit-Messung
            val eq = root.optJSONObject("customEquipment") ?: JSONObject()
            eq.keys().forEach { key ->
                runCatching { Equipment.valueOf(eq.getString(key)) }
                    .onSuccess { CustomMeta.equipment[migrateName(key)] = it }
            }
            val tm = root.optJSONArray("customTimed") ?: JSONArray()
            for (i in 0 until tm.length()) CustomMeta.timed.add(migrateName(tm.getString(i)))
            // Eigene Übungen: Muskeln fürs Körperdiagramm { "Name": { "primary": [...], "secondary": [...] } }
            val mu = root.optJSONObject("customMuscles") ?: JSONObject()
            mu.keys().forEach { key ->
                val o = mu.optJSONObject(key) ?: return@forEach
                fun ids(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }.filter { it in MUSCLES }
                CustomMeta.muscles[migrateName(key)] = MuscleSet(ids(o.optJSONArray("primary")), ids(o.optJSONArray("secondary")))
            }
            root.optJSONObject("settings")?.let { weeklyGoal = it.optInt("weeklyGoal", 3) }
            val wo = root.optJSONArray("workouts") ?: JSONArray()
            for (i in 0 until wo.length()) {
                val o = wo.getJSONObject(i)
                val list = o.optJSONArray("exercises") ?: JSONArray()
                workouts.add(
                    Workout(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        exercises = (0 until list.length()).map { migrateName(list.getString(it)) },
                        targets = o.optJSONObject("targets").toIntMap().mapKeys { migrateName(it.key) }
                    )
                )
            }
            root.optJSONObject("settings")?.let {
                restSeconds = it.optInt("restSeconds", 90)
                lastExport = it.optLong("lastExport", 0L)
                themeMode = it.optString("theme", "system")
                appLanguage = it.optString("language", "de")
            }
            val we = root.optJSONArray("weights") ?: JSONArray()
            weights.addAll(
                (0 until we.length()).map {
                    val o = we.getJSONObject(it)
                    WeightEntry(o.getLong("time"), o.getDouble("kg"))
                }.sortedBy { it.time }
            )
            val se = root.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until se.length()) {
                val o = se.getJSONObject(i)
                val list = o.optJSONArray("exercises") ?: JSONArray()
                sessions.add(
                    TrainingSession(
                        id = o.getLong("id"),
                        workoutId = o.getLong("workoutId"),
                        workoutName = o.getString("workoutName"),
                        exercises = (0 until list.length()).map { migrateName(list.getString(it)) },
                        start = o.getLong("start"),
                        end = o.getLong("end"),
                        targets = o.optJSONObject("targets").toIntMap().mapKeys { migrateName(it.key) }
                    )
                )
            }
            val s = root.optJSONArray("sets") ?: JSONArray()
            for (i in 0 until s.length()) {
                val o = s.getJSONObject(i)
                sets.add(
                    GymSet(
                        id = o.getLong("id"),
                        exercise = migrateName(o.getString("exercise")),
                        weight = o.getDouble("weight"),
                        reps = o.getInt("reps"),
                        time = o.getLong("time")
                    )
                )
            }
        }
    }

    private fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "GymTracker")
        root.put("version", 1)
        root.put("exercises", JSONArray(exercises.toList()))
        val g = JSONObject()
        groups.forEach { (name, group) -> g.put(name, group.name) }
        root.put("groups", g)
        root.put("customEquipment", JSONObject().also { o -> CustomMeta.equipment.forEach { (k, v) -> o.put(k, v.name) } })
        root.put("customTimed", JSONArray(CustomMeta.timed.toList()))
        root.put("customMuscles", JSONObject().also { o ->
            CustomMeta.muscles.forEach { (k, m) ->
                o.put(k, JSONObject().put("primary", JSONArray(m.primary)).put("secondary", JSONArray(m.secondary)))
            }
        })
        val wo = JSONArray()
        workouts.forEach { w ->
            val o = JSONObject()
            o.put("id", w.id)
            o.put("name", w.name)
            o.put("exercises", JSONArray(w.exercises))
            o.put("targets", w.targets.toJson())
            wo.put(o)
        }
        root.put("workouts", wo)
        val se = JSONArray()
        sessions.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("workoutId", t.workoutId)
            o.put("workoutName", t.workoutName)
            o.put("exercises", JSONArray(t.exercises))
            o.put("start", t.start)
            o.put("end", t.end)
            o.put("targets", t.targets.toJson())
            se.put(o)
        }
        root.put("sessions", se)
        root.put("weights", JSONArray().also { arr ->
            weights.forEach { arr.put(JSONObject().put("time", it.time).put("kg", it.kg)) }
        })
        root.put(
            "settings",
            JSONObject()
                .put("restSeconds", restSeconds)
                .put("lastExport", lastExport)
                .put("theme", themeMode)
                .put("language", appLanguage)
                .put("weeklyGoal", weeklyGoal)
        )
        val s = JSONArray()
        sets.forEach { set ->
            val o = JSONObject()
            o.put("id", set.id)
            o.put("exercise", set.exercise)
            o.put("weight", set.weight)
            o.put("reps", set.reps)
            o.put("time", set.time)
            s.put(o)
        }
        root.put("sets", s)
        return root
    }

    fun hasExercise(name: String): Boolean =
        exercises.any { it.equals(name.trim(), ignoreCase = true) }

    fun addExercise(name: String, group: MuscleGroup? = null) {
        val n = name.trim()
        if (n.isEmpty() || hasExercise(n)) return
        exercises.add(n)
        (group ?: catalogGroup(n))?.let { groups[n] = it }
        save()
    }

    /**
     * Eigene Übung anlegen. Gibt false zurück, wenn der Name leer ist oder schon existiert
     * (als eigene Übung oder im Katalog).
     */
    fun addCustomExercise(name: String, group: MuscleGroup, equipment: Equipment?, timed: Boolean, muscles: MuscleSet? = null): Boolean {
        val n = name.trim()
        if (n.isEmpty() || hasExercise(n) || catalogGroup(n) != null) return false
        exercises.add(n)
        groups[n] = group
        if (equipment != null) CustomMeta.equipment[n] = equipment
        if (timed && !group.isCardio) CustomMeta.timed.add(n)
        if (muscles != null && !muscles.isEmpty) CustomMeta.muscles[n] = muscles
        save()
        return true
    }

    /** Muskeln einer eigenen Übung festlegen (leere Auswahl = entfernen). */
    fun setCustomMuscles(name: String, muscles: MuscleSet) {
        if (muscles.isEmpty) CustomMeta.muscles.remove(name) else CustomMeta.muscles[name] = muscles
        save()
    }

    fun changeWeeklyGoal(n: Int) {
        weeklyGoal = n.coerceIn(1, 14)
        save()
    }

    /** Gespeicherte Gruppe, sonst aus dem Katalog, sonst „Sonstige“. */
    fun groupOf(name: String): MuscleGroup =
        groups[name] ?: catalogGroup(name) ?: MuscleGroup.SONSTIGE

    fun deleteExercise(name: String) {
        exercises.remove(name)
        groups.remove(name)
        CustomMeta.equipment.remove(name)
        CustomMeta.muscles.remove(name)
        CustomMeta.timed.remove(name)
        sets.removeAll { it.exercise == name }
        // Übung auch aus allen Workouts entfernen
        for (i in workouts.indices) {
            workouts[i] = workouts[i].copy(
                exercises = workouts[i].exercises - name,
                targets = workouts[i].targets - name
            )
        }
        save()
    }

    // ----- Workouts -----

    fun workout(id: Long): Workout? = workouts.firstOrNull { it.id == id }

    fun addWorkout(name: String): Workout? {
        val n = name.trim()
        if (n.isEmpty()) return null
        val w = Workout(System.currentTimeMillis(), n, emptyList())
        workouts.add(w)
        save()
        return w
    }

    /** Ziel-Sätze einer Übung im Workout (gespeichert oder Standard). */
    fun targetFor(w: Workout, exercise: String): Int =
        w.targets[exercise] ?: defaultTarget(groupOf(exercise))

    fun setTarget(workoutId: Long, exercise: String, target: Int) {
        val i = workouts.indexOfFirst { it.id == workoutId }
        if (i < 0) return
        val w = workouts[i]
        // 0 = optional: zählt nicht in den Prozentsatz
        workouts[i] = w.copy(targets = w.targets + (exercise to target.coerceIn(0, 20)))
        save()
    }

    /** Übung im Workout von Position `from` nach `to` verschieben (für Drag & Drop). */
    fun moveInWorkout(workoutId: Long, from: Int, to: Int) {
        val i = workouts.indexOfFirst { it.id == workoutId }
        if (i < 0) return
        val list = workouts[i].exercises.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        // Nur im Speicher ändern; gespeichert wird beim Loslassen (saveNow), nicht bei jedem Schritt
        workouts[i] = workouts[i].copy(exercises = list)
    }

    /** Aktuellen Stand sofort in die Datei schreiben. */
    fun saveNow() = save()

    /** Kopie eines Workouts (gleiche Übungen, Reihenfolge und Ziele) mit Namenszusatz. */
    fun duplicateWorkout(id: Long): Workout? {
        val w = workout(id) ?: return null
        val copy = w.copy(id = System.currentTimeMillis(), name = w.name + tr(" (Kopie)", " (copy)"))
        workouts.add(copy)
        save()
        return copy
    }

    /**
     * Workout aus einer Vorlage anlegen. Fehlende Übungen kommen automatisch in „Meine Übungen“.
     * Ziele: 3 Sätze (Kraft/Zeit) bzw. 1 Einheit (Cardio) – so zeigt der Prozentsatz echten Fortschritt.
     */
    fun addWorkoutFromTemplate(t: WorkoutTemplate): Workout {
        t.exercises.forEach { addExercise(it) }
        val w = Workout(
            id = System.currentTimeMillis(),
            name = t.label,
            exercises = t.exercises,
            targets = t.exercises.associateWith { if (groupOf(it).isCardio) 1 else 3 }
        )
        workouts.add(w)
        save()
        return w
    }

    fun deleteWorkout(id: Long) {
        workouts.removeAll { it.id == id }
        save()
    }

    /**
     * Übung zum Workout hinzufügen bzw. daraus entfernen.
     * Ist die Übung noch nicht in „Meine Übungen“, wird sie dort ebenfalls angelegt.
     */
    fun toggleInWorkout(id: Long, exercise: String) {
        val i = workouts.indexOfFirst { it.id == id }
        if (i < 0) return
        val w = workouts[i]
        if (exercise in w.exercises) {
            workouts[i] = w.copy(exercises = w.exercises - exercise)
        } else {
            addExercise(exercise)
            workouts[i] = w.copy(exercises = w.exercises + exercise)
        }
        save()
    }

    fun addSet(exercise: String, weight: Double, reps: Int) {
        val now = System.currentTimeMillis()
        sets.add(GymSet(now, exercise, weight, reps, now))
        save()
    }

    /** Gewicht/Wiederholungen eines bestehenden Satzes ändern (Zeitpunkt bleibt gleich). */
    fun updateSet(set: GymSet, weight: Double, reps: Int) {
        val i = sets.indexOf(set)
        if (i < 0) return
        sets[i] = set.copy(weight = weight, reps = reps)
        save()
    }

    fun deleteSet(set: GymSet) {
        sets.remove(set)
        save()
    }

    // ----- Trainings -----

    /** Sätze einer Übung in einem Zeitraum, älteste zuerst. */
    fun setsBetween(exercise: String, start: Long, end: Long): List<GymSet> =
        sets.filter { it.exercise == exercise && it.time in start..end }.sortedBy { it.time }

    /**
     * Training abschließen. Gibt die gespeicherte Session zurück,
     * oder null, wenn kein einziger Satz eingetragen wurde (dann wird nichts gespeichert).
     */
    fun finishTraining(workout: Workout, start: Long): TrainingSession? {
        val end = System.currentTimeMillis()
        if (workout.exercises.all { setsBetween(it, start, end).isEmpty() }) return null
        val t = TrainingSession(
            start, workout.id, workout.name, workout.exercises, start, end,
            targets = workout.exercises.associateWith { targetFor(workout, it) }
        )
        sessions.add(t)
        save()
        return t
    }

    fun session(id: Long): TrainingSession? = sessions.firstOrNull { it.id == id }

    // ----- Freies Training (Sätze ohne gestartetes Workout) -----

    /** Sätze, die zu keinem gespeicherten Training gehören, gruppiert nach Tag (JJJJMMTT). */
    fun freeSetsByDay(): Map<Int, List<GymSet>> =
        sets.filter { s -> sessions.none { t -> s.time in t.start..t.end && s.exercise in t.exercises } }
            .groupBy { dayKey(it.time) }

    /**
     * Baut aus den freien Sätzen eines Tages ein „virtuelles“ Training.
     * Die negative ID (-JJJJMMTT) unterscheidet es von gespeicherten Trainings.
     * Ziel = tatsächliche Satzzahl → immer 100 %.
     */
    fun freeSession(day: Int): TrainingSession? {
        val list = freeSetsByDay()[day]?.sortedBy { it.time } ?: return null
        val exs = list.map { it.exercise }.distinct()
        return TrainingSession(
            id = -day.toLong(),
            workoutId = -1,
            workoutName = tr("Freies Training", "Free workout"),
            exercises = exs,
            start = list.first().time,
            end = list.last().time,
            targets = exs.associateWith { e -> list.count { it.exercise == e } }
        )
    }

    // ----- Körpergewicht -----

    fun addWeight(kg: Double) {
        weights.add(WeightEntry(System.currentTimeMillis(), kg))
        save()
    }

    fun deleteWeight(entry: WeightEntry) {
        weights.remove(entry)
        save()
    }

    /**
     * Gewicht zum Zeitpunkt t: der letzte Eintrag davor. Gibt es keinen,
     * nehmen wir den ersten danach (besser als gar keine Schätzung). null = nie eingetragen.
     */
    fun weightAt(t: Long): Double? =
        weights.lastOrNull { it.time <= t }?.kg ?: weights.firstOrNull()?.kg

    /** Trainings eines Workouts, neuestes zuerst. */
    fun sessionsFor(workoutId: Long): List<TrainingSession> =
        sessions.filter { it.workoutId == workoutId }.sortedByDescending { it.start }

    /** Alle Sätze einer Übung, neueste zuerst. */
    fun setsFor(exercise: String): List<GymSet> =
        sets.filter { it.exercise == exercise }.sortedByDescending { it.time }
}


