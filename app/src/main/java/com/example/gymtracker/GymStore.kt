package com.example.gymtracker

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GymStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(context.filesDir, "gym_data.json")
    val exercises = mutableStateListOf<String>()
    val sets = mutableStateListOf<GymSet>()
    /** Muskelgruppe pro Übung (Übungsname → Gruppe). */
    private val groups = mutableStateMapOf<String, MuscleGroup>()
    val workouts = mutableStateListOf<Workout>()
    val sessions = mutableStateListOf<TrainingSession>()
    /** Gewichtsverlauf, älteste zuerst */
    val weights = mutableStateListOf<WeightEntry>()
    /** Notizen pro Übung und Tag: "Übung|Tag" (Tag = dayKey) → Text. Vor init { load() }! */
    private val notes = mutableStateMapOf<String, String>()
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

    /** Optionales Profil für genauere Kalorien (Stats.kt). Vor init { load() }! */
    var profile by mutableStateOf(Profile())
        private set

    fun updateProfile(p: Profile) {
        profile = p
        save()
    }

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
                // Nur die 3 neuesten Kopien behalten (sonst wächst der Speicher bei wiederholten Fehlern)
                file.parentFile?.listFiles { f -> f.name.startsWith("gym_data_corrupt_") }
                    ?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
            }
        }
    }

    /**
     * Speichert atomar: erst in eine temporäre Datei schreiben, dann umbenennen.
     * Stürzt die App mitten im Schreiben ab, bleibt die alte Datei vollständig erhalten.
     */
    private fun save() {
        try {
            val tmp = File(file.parentFile, "gym_data.json.tmp")
            tmp.writeText(toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            // z. B. Speicher voll: nicht abstürzen, aber deutlich melden (die Daten sind nur im Arbeitsspeicher)
            e.printStackTrace()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    appContext,
                    tr("Speichern fehlgeschlagen – ist der Speicher voll?", "Saving failed – is the storage full?"),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
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
        clearAll()
        try {
            readFrom(root)
        } catch (e: Exception) {
            // Fehlerhafte Sicherung (z. B. Satz ohne Zeit): halb eingelesene Daten verwerfen und den alten
            // Stand aus der Datei zurückholen – sonst würde der nächste save() ihn überschreiben.
            clearAll()
            load()
            throw e
        }
        save()
    }

    private fun clearAll() {
        exercises.clear(); sets.clear(); groups.clear(); workouts.clear(); sessions.clear(); weights.clear(); notes.clear()
        CustomMeta.equipment.clear(); CustomMeta.timed.clear(); CustomMeta.muscles.clear()
        profile = Profile()
    }

    private fun readFrom(root: JSONObject) {
        run {
            val ex = root.optJSONArray("exercises") ?: JSONArray()
            for (i in 0 until ex.length()) migrateName(ex.getString(i)).let { if (it !in exercises) exercises.add(it) }
            val g = root.optJSONObject("groups") ?: JSONObject()
            val oldArms = mutableListOf<String>() // frühere Kategorie „ARME“ → nach den Muskeln aufteilen (unten)
            g.keys().forEach { key ->
                val n = migrateName(key)
                // Katalog-Übungen: Kategorie kommt immer aus dem Katalog (nicht speichern, sonst wirken alte Zuordnungen weiter)
                if (catalogGroup(n) != null) return@forEach
                if (g.getString(key) == "ARME") { oldArms += n; return@forEach }
                runCatching { MuscleGroup.valueOf(g.getString(key)) }
                    .onSuccess { groups[n] = it }
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
            // Eigene Übungen der früheren Kategorie „Arme“: nach dem Hauptmuskel aufteilen (sonst Bizeps)
            for (n in oldArms) groups[n] = migrateArmGroup(CustomMeta.muscles[n])
            root.optJSONObject("settings")?.let { weeklyGoal = it.optInt("weeklyGoal", 3) }
            // Profil (optional): ungültige Werte werden ignoriert
            profile = root.optJSONObject("profile")?.let { p ->
                Profile(
                    sex = p.optString("sex").takeIf { it == "m" || it == "f" },
                    birthYear = p.optInt("birthYear", 0).takeIf { Profile.validYear(it) },
                    heightCm = p.optInt("height", 0).takeIf { Profile.validHeight(it) }
                )
            } ?: Profile()
            val wo = root.optJSONArray("workouts") ?: JSONArray()
            for (i in 0 until wo.length()) {
                val o = wo.getJSONObject(i)
                val list = o.optJSONArray("exercises") ?: JSONArray()
                workouts.add(
                    Workout(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        exercises = (0 until list.length()).map { migrateName(list.getString(it)) },
                        targets = o.optJSONObject("targets").toIntMap().mapKeys { migrateName(it.key) },
                        days = o.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.getInt(it) }.filter { it in 1..7 }.toSet() }.orEmpty()
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
            // Notizen: { "Übung": { "20261119": "Text" } }
            val no = root.optJSONObject("notes") ?: JSONObject()
            no.keys().forEach { ex ->
                val days = no.optJSONObject(ex) ?: return@forEach
                days.keys().forEach { d -> d.toIntOrNull()?.let { notes["${migrateName(ex)}|$it"] = days.getString(d) } }
            }
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
                        targets = o.optJSONObject("targets").toIntMap().mapKeys { migrateName(it.key) },
                        hr = HeartRate.fromJson(o.optJSONObject("hr"))
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
                        time = o.getLong("time"),
                        type = SetType.parse(o.optString("type").ifEmpty { null })
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
            if (w.days.isNotEmpty()) o.put("days", JSONArray(w.days.sorted()))
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
            t.hr?.let { o.put("hr", it.toJson()) }
            se.put(o)
        }
        root.put("sessions", se)
        root.put("weights", JSONArray().also { arr ->
            weights.forEach { arr.put(JSONObject().put("time", it.time).put("kg", it.kg)) }
        })
        root.put("notes", JSONObject().also { o ->
            notes.forEach { (key, text) ->
                val ex = key.substringBeforeLast('|')
                val day = key.substringAfterLast('|')
                (o.optJSONObject(ex) ?: JSONObject().also { o.put(ex, it) }).put(day, text)
            }
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
        if (!profile.isEmpty) root.put("profile", JSONObject().apply {
            profile.sex?.let { put("sex", it) }
            profile.birthYear?.let { put("birthYear", it) }
            profile.heightCm?.let { put("height", it) }
        })
        val s = JSONArray()
        sets.forEach { set ->
            val o = JSONObject()
            o.put("id", set.id)
            o.put("exercise", set.exercise)
            o.put("weight", set.weight)
            o.put("reps", set.reps)
            o.put("time", set.time)
            if (set.type != SetType.NORMAL) o.put("type", set.type.name) // nur abweichende Typen speichern
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
    /** Katalog-Übungen: Kategorie aus dem Katalog; eigene Übungen: gespeicherte Kategorie. */
    fun groupOf(name: String): MuscleGroup =
        catalogGroup(name) ?: groups[name] ?: MuscleGroup.SONSTIGE

    /** Notiz einer Übung an einem Tag (dayKey), null = keine. */
    fun noteFor(exercise: String, day: Int): String? = notes["$exercise|$day"]

    /** Notiz setzen; leerer Text löscht sie. */
    fun setNote(exercise: String, day: Int, text: String) {
        val t = text.trim()
        if (t.isEmpty()) notes.remove("$exercise|$day") else notes["$exercise|$day"] = t
        save()
    }

    /**
     * Übung nur aus „Meine Übungen“ (Tab Übungen) entfernen. Verlauf, Notizen, Kategorie und alle Workouts
     * behalten sie – über den Katalog lässt sie sich wieder hinzufügen. (deleteExercise löscht dagegen alles.)
     */
    fun removeFromList(name: String): Int {
        val index = exercises.indexOf(name)
        exercises.remove(name)
        save()
        return index
    }

    /** Aus der Liste entfernte Übung an ihren alten Platz zurück („Rückgängig“). */
    fun restoreToList(name: String, index: Int) {
        if (hasExercise(name)) return
        exercises.add(index.coerceIn(0, exercises.size), name)
        save()
    }

    /** Eigene Übungen (nicht im Katalog): in der Liste, in einem Workout oder mit Verlauf – auch aus der Liste entfernte. */
    val customNames: List<String>
        get() = (exercises + workouts.flatMap { it.exercises } + sets.map { it.exercise })
            .distinct().filter { catalogGroup(it) == null }

    /** Übung komplett löschen: auch Verlauf, Notizen und aus allen Workouts. */
    fun deleteExercise(name: String) {
        exercises.remove(name)
        notes.keys.filter { it.substringBeforeLast('|') == name }.forEach { notes.remove(it) }
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

    /** Trainingstag (1 = Mo … 7 = So) im Plan des Workouts an-/abwählen. */
    fun toggleDay(workoutId: Long, day: Int) {
        val i = workouts.indexOfFirst { it.id == workoutId }
        if (i < 0) return
        val w = workouts[i]
        workouts[i] = w.copy(days = if (day in w.days) w.days - day else w.days + day)
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

    /** Gesicherter Stand vor dem Zurücksetzen (für „Rückgängig“) */
    class ProgressSnapshot(
        val sets: List<GymSet>, val sessions: List<TrainingSession>,
        val weights: List<WeightEntry>, val notes: Map<String, String>
    )

    /**
     * Fortschritt zurücksetzen: alle Sätze, Trainings, Notizen und Gewichtseinträge löschen.
     * Übungen, Workouts (Ziele, Plan) und Einstellungen bleiben. Vorher wird die Datei nach
     * gym_data_before_reset.json kopiert (wie beim Import). Gibt den alten Stand zurück.
     */
    fun resetProgress(): ProgressSnapshot {
        val snap = ProgressSnapshot(sets.toList(), sessions.toList(), weights.toList(), notes.toMap())
        if (file.exists()) runCatching { file.copyTo(File(file.parentFile, "gym_data_before_reset.json"), overwrite = true) }
        sets.clear(); sessions.clear(); weights.clear(); notes.clear()
        save()
        return snap
    }

    /** „Rückgängig“ nach resetProgress() */
    fun restoreProgress(s: ProgressSnapshot) {
        sets.clear(); sets.addAll(s.sets)
        sessions.clear(); sessions.addAll(s.sessions)
        weights.clear(); weights.addAll(s.weights)
        notes.clear(); notes.putAll(s.notes)
        save()
    }

    /** Kopie eines Workouts (gleiche Übungen, Reihenfolge und Ziele) mit Namenszusatz. */
    fun duplicateWorkout(id: Long): Workout? {
        val w = workout(id) ?: return null
        val copy = w.copy(id = System.currentTimeMillis(), name = w.name + tr(" (Kopie)", " (copy)"), days = emptySet()) // Plan-Tage nicht doppelt belegen
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

    fun addSet(exercise: String, weight: Double, reps: Int, type: SetType = SetType.NORMAL) {
        val now = System.currentTimeMillis()
        sets.add(GymSet(now, exercise, weight, reps, now, type))
        save()
    }

    /** Gewicht/Wiederholungen/Typ eines bestehenden Satzes ändern (Zeitpunkt bleibt gleich). */
    fun updateSet(set: GymSet, weight: Double, reps: Int, type: SetType = set.type) {
        val i = sets.indexOf(set)
        if (i < 0) return
        sets[i] = set.copy(weight = weight, reps = reps, type = type)
        save()
    }

    /** Gelöschten Satz zurückholen („Rückgängig“). */
    fun restoreSet(set: GymSet) {
        if (sets.none { it.id == set.id }) sets.add(set)
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
    /** Nach jedem abgeschlossenen Training (MainActivity: Health-Connect-Abgleich). */
    var onTrainingFinished: (() -> Unit)? = null

    fun finishTraining(workout: Workout, start: Long): TrainingSession? {
        val end = System.currentTimeMillis()
        if (workout.exercises.all { setsBetween(it, start, end).isEmpty() }) return null
        val t = TrainingSession(
            start, workout.id, workout.name, workout.exercises, start, end,
            targets = workout.exercises.associateWith { targetFor(workout, it) }
        )
        sessions.add(t)
        save()
        onTrainingFinished?.invoke()
        return t
    }

    fun session(id: Long): TrainingSession? = sessions.firstOrNull { it.id == id }

    /** Puls zu einem Training speichern (aus Health Connect). */
    fun setHeartRate(id: Long, hr: HeartRate) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || sessions[i].hr == hr) return
        sessions[i] = sessions[i].copy(hr = hr)
        save()
    }

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
            targets = exs.associateWith { e -> list.count { it.exercise == e && it.isWork } }
        )
    }

    // ----- Körpergewicht -----

    /**
     * Wird bei jeder Gewichtsänderung durch den Nutzer aufgerufen (alt, neu; null = nicht vorhanden).
     * Setzt MainActivity für den Health-Connect-Abgleich – GymStore selbst kennt Health Connect nicht.
     */
    var onWeightChange: ((WeightEntry?, WeightEntry?) -> Unit)? = null

    /** Gewicht eintragen; `time` = gewählter Tag (Nachtragen), Liste bleibt nach Zeit sortiert. */
    fun addWeight(kg: Double, time: Long = System.currentTimeMillis()) {
        val e = WeightEntry(time, kg)
        weights.add(e)
        weights.sortBy { it.time }
        save()
        onWeightChange?.invoke(null, e)
    }

    /** Gelöschten Eintrag zurückholen („Rückgängig“). */
    fun restoreWeight(entry: WeightEntry) {
        weights.add(entry)
        weights.sortBy { it.time }
        save()
        onWeightChange?.invoke(null, entry)
    }

    fun deleteWeight(entry: WeightEntry) {
        weights.remove(entry)
        save()
        onWeightChange?.invoke(entry, null)
    }

    /** Gewicht und ggf. Datum eines Eintrags ändern; Liste bleibt nach Zeit sortiert. */
    fun updateWeight(entry: WeightEntry, kg: Double, time: Long = entry.time) {
        val i = weights.indexOf(entry)
        if (i < 0) return
        val e = WeightEntry(time, kg)
        weights[i] = e
        weights.sortBy { it.time }
        save()
        onWeightChange?.invoke(entry, e)
    }

    /** Einträge aus Health Connect übernehmen (ohne onWeightChange – sie kommen ja von dort). */
    fun importWeights(list: List<WeightEntry>) {
        if (list.isEmpty()) return
        weights.addAll(list)
        weights.sortBy { it.time }
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


