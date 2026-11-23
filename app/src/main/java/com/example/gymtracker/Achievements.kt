package com.example.gymtracker

// ---------- Erfolge eines Trainings (Roadmap Schritt 2) ----------
// Gemeinsame Grundlage für die Feier im Summary, die Morgen-Zeile, Abzeichen und den Rückblick.
// Liefert IMMER mindestens einen Erfolg (Peak-End: jedes Training endet mit etwas Positivem).
// Nur Vergleiche mit der eigenen Vergangenheit, nie Kritik („weniger als letztes Mal“ kommt nicht vor).
// Web: achievements.js (gleiche Logik).

/** Art des Erfolgs – Reihenfolge = Wichtigkeit (die wichtigsten zuerst). */
enum class AchievementKind(val emoji: String, val big: Boolean) {
    GOAL_REACHED("🏆", true),
    LEVEL_UP("⭐", true),
    BADGE("🏅", true),
    RECORD("🥇", true),
    REP_RECORD("🔁", false),
    GOAL_PROGRESS("🎯", false),
    WEEK_GOAL("✅", true),
    MILESTONE("🎉", true),
    VOLUME_UP("📈", false),
    EXERCISE_UP("💪", false),
    MUSCLE_BACK("👋", false),
    STREAK("🔥", false),
    WEEK_COUNT("📅", false),
    SESSION("👍", false)
}

/**
 * Ein Erfolg. `big` = Grund zum Feiern (Konfetti in Schritt 3). `exercise` = zugehörige Übung (für Symbol/Antippen).
 */
data class Achievement(
    val kind: AchievementKind,
    val title: String,
    val detail: String? = null,
    val exercise: String? = null
) {
    val emoji: String get() = kind.emoji
    val big: Boolean get() = kind.big
}

/** Meilensteine nach Anzahl der Trainings. */
private fun isMilestone(n: Int) = n in setOf(1, 5, 10, 25, 50, 75, 100) || (n > 100 && n % 50 == 0)

private fun ordinal(n: Int) = tr("$n.", if (n % 100 in 11..13) "${n}th" else when (n % 10) { 1 -> "${n}st"; 2 -> "${n}nd"; 3 -> "${n}rd"; else -> "${n}th" })

private fun daysBetween(a: Long, b: Long) = ((b - a) / 86_400_000L).toInt()

/** Menge eines Satzes je nach Übung: Gewicht in kg, Cardio in min, Zeitübung in s. */
private fun fmtMetric(store: GymStore, ex: String, v: Double): String = when {
    store.groupOf(ex).isCardio -> "${fmtW(v)}\u00A0min"
    isTimed(ex) -> "${fmtW(v)} s"
    else -> "${fmtKg(v)}"
}

/**
 * Alle Erfolge eines Trainings, wichtigste zuerst. Vergleicht nur mit Daten VOR dem Training –
 * funktioniert also auch für ältere Trainings (Summary aus dem Kalender).
 */
fun achievements(store: GymStore, t: TrainingSession, stats: SessionStats = sessionStats(store, t)): List<Achievement> {
    val out = mutableListOf<Achievement>()
    val results = stats.results.filter { it.sets.isNotEmpty() }
    val all = allSessions(store).sortedBy { it.start }

    // 1. Ziele, die in diesem Training erreicht wurden
    store.goals.filter { it.created <= t.end }.forEach { g ->
        val reached = goalProgress(store, g).reachedAt
        if (reached != null && reached in t.start..t.end)
            out += Achievement(AchievementKind.GOAL_REACHED, tr("Ziel erreicht: ", "Goal reached: ") + goalTitle(store, g),
                tr("Seit ${fmtDate(g.created)} drangeblieben – stark!", "Kept at it since ${fmtDate(g.created)} – awesome!"), g.exercise)
    }

    // Level-Aufstieg (Levels.kt)
    levelUpIn(store, t)?.let { lv ->
        out += Achievement(AchievementKind.LEVEL_UP, tr("Level $lv erreicht!", "Level $lv reached!"),
            if (levelTitle(lv) != levelTitle(lv - 1)) tr("Neuer Titel: ${levelTitle(lv)}", "New title: ${levelTitle(lv)}")
            else nextTitleLevel(lv)?.let { n -> tr("Noch ${plural(n - lv, "Level", "Level")} bis „${levelTitle(n)}“", "${plural(n - lv, "level", "levels")} to \"${levelTitle(n)}\"") })
    }

    // Neue Abzeichen (Badges.kt)
    badgesIn(store, t).forEach { b ->
        out += Achievement(AchievementKind.BADGE, tr("Neues Abzeichen: ${b.name} ${b.emoji}", "New badge: ${b.name} ${b.emoji}"), b.desc)
    }

    // 2./3./8. Je Übung: Rekord > Wiederholungsrekord > besser als letztes Mal (höchstens einer pro Übung)
    for (r in results) {
        val ex = r.name
        val byTime = r.group.isCardio || isTimed(ex)
        val metric: (GymSet) -> Double = if (byTime) { s -> s.reps.toDouble() } else { s -> s.weight }
        val now = r.sets.top()
        if (now.isEmpty()) continue
        val before = store.sets.filter { it.exercise == ex && it.time < t.start && it.isTop }
        val best = now.maxOf(metric)
        val prevBest = before.maxOfOrNull(metric)
        if (r.record && prevBest != null) {
            out += Achievement(AchievementKind.RECORD, tr("Neuer Rekord: ", "New record: ") + exName(ex),
                "${fmtMetric(store, ex, best)} (+${fmtMetric(store, ex, best - prevBest)})", ex)
            continue
        }
        // Wiederholungsrekord: bei diesem (oder höherem) Gewicht nie so viele Wiederholungen geschafft
        if (!byTime) {
            val repRecord = now.sortedByDescending { it.weight }.firstOrNull { s ->
                val prior = before.filter { it.weight >= s.weight }
                prior.isNotEmpty() && prior.maxOf { it.reps } < s.reps
            }
            if (repRecord != null) {
                out += Achievement(AchievementKind.REP_RECORD, tr("Wiederholungsrekord: ", "Rep record: ") + exName(ex),
                    (if (repRecord.weight > 0) "${fmtKg(repRecord.weight)} × ${repRecord.reps}" else tr("${repRecord.reps} Wdh.", "${repRecord.reps} reps")) +
                        tr(" – so viele wie nie zuvor", " – more than ever before"), ex)
                continue
            }
        }
        // Besser als letztes Mal (letzter Trainingstag davor)
        val prev = r.previous
        if (prev.isEmpty()) continue
        val prevTop = prev.maxOf(metric)
        val detail: String? = when {
            best > prevTop -> "+${fmtMetric(store, ex, best - prevTop)}"
            !byTime && best == prevTop -> {
                val repsNow = now.filter { it.weight == best }.maxOf { it.reps }
                val repsPrev = prev.filter { it.weight == best }.maxOfOrNull { it.reps } ?: 0
                val volNow = r.sets.work().sumOf { it.weight * it.reps }
                val volPrev = prev.sumOf { it.weight * it.reps }
                when {
                    repsNow > repsPrev -> tr("+${repsNow - repsPrev} Wdh. bei ${fmtKg(best)}", "+${repsNow - repsPrev} reps at ${fmtKg(best)}")
                    volPrev > 0 && volNow > volPrev -> tr("+${Math.round((volNow - volPrev) / volPrev * 100)}\u00A0% Volumen", "+${Math.round((volNow - volPrev) / volPrev * 100)}\u00A0% volume")
                    else -> null
                }
            }
            else -> null
        }
        if (detail != null) out += Achievement(AchievementKind.EXERCISE_UP, exName(ex), detail + tr(" ggü. letztem Mal", " vs. last time"), ex)
    }

    // 4. Näher am Ziel (Übungsziele besser geworden, Cardio-Wochenziel in diesem Training vollgemacht)
    store.goals.filter { it.created <= t.end }.forEach { g ->
        val p = goalProgress(store, g)
        if (p.reachedAt != null && p.reachedAt <= t.end) return@forEach // schon erreicht (bzw. gerade – siehe oben)
        when (g.type) {
            GoalType.STRENGTH, GoalType.ENDURANCE -> {
                val ex = g.exercise ?: return@forEach
                val metric: (GymSet) -> Double = if (g.type == GoalType.STRENGTH) { s -> s.weight } else { s -> s.reps.toDouble() }
                val now = results.firstOrNull { it.name == ex }?.sets?.top()?.filter { it.reps > 0 }.orEmpty()
                if (now.isEmpty()) return@forEach
                val before = store.sets.filter { it.exercise == ex && it.time < t.start && it.isTop && it.reps > 0 }.maxOfOrNull(metric) ?: 0.0
                val best = now.maxOf(metric)
                if (best > before) {
                    val fmt: (Double) -> String = if (g.type == GoalType.STRENGTH) { v -> "${fmtKg(v)}" } else { v -> fmtAmount(ex, v, store) }
                    out += Achievement(AchievementKind.GOAL_PROGRESS, tr("Näher dran: ", "Getting closer: ") + goalTitle(store, g),
                        tr("+${fmt(best - before)} · noch ${fmt(g.target - best)}", "+${fmt(best - before)} · ${fmt(g.target - best)} to go"), ex)
                }
            }
            GoalType.CARDIO_WEEK -> {
                val ws = startOfWeek(t.start)
                fun minutes(until: Long) = store.sets.filter { it.isWork && it.time in ws until until && store.groupOf(it.exercise).isCardio }.sumOf { it.reps }
                if (minutes(t.start) < g.target && minutes(t.end + 1) >= g.target)
                    out += Achievement(AchievementKind.GOAL_PROGRESS, tr("Cardio-Ziel dieser Woche geschafft", "Cardio goal done this week"),
                        tr("${fmtW(g.target)}\u00A0min Ausdauer – dein Herz sagt danke", "${fmtW(g.target)}\u00A0min of cardio – your heart says thanks"))
            }
            else -> {}
        }
    }

    // 5./11. Training Nr. x in dieser Woche, Wochenziel
    val weekStart = startOfWeek(t.start)
    val inWeek = all.filter { startOfWeek(it.start) == weekStart }
    val pos = inWeek.indexOfFirst { it.id == t.id } + 1
    val goal = store.weeklyGoal
    when {
        pos == goal -> out += Achievement(AchievementKind.WEEK_GOAL, tr("Wochenziel geschafft!", "Weekly goal reached!"),
            tr("$goal von $goal Trainings diese Woche", "$goal of $goal workouts this week"))
        pos > goal -> out += Achievement(AchievementKind.WEEK_COUNT, tr("${ordinal(pos)} Training diese Woche", "${ordinal(pos)} workout this week"),
            tr("Wochenziel übertroffen", "Weekly goal beaten"))
        pos > 0 -> out += Achievement(AchievementKind.WEEK_COUNT, tr("${ordinal(pos)} Training diese Woche", "${ordinal(pos)} workout this week"),
            tr("Noch ${goal - pos} bis zum Wochenziel", "${goal - pos} more to your weekly goal"))
    }

    // 6. Meilenstein: das wievielte Training insgesamt
    val number = all.indexOfFirst { it.id == t.id } + 1
    if (number > 0 && isMilestone(number)) out += Achievement(AchievementKind.MILESTONE,
        if (number == 1) tr("Dein erstes Training!", "Your first workout!") else tr("${number}. Training!", "Workout #$number!"),
        if (number == 1) tr("Der schwerste Schritt ist geschafft.", "The hardest step is done.") else tr("$number Trainings – das ist Routine.", "$number workouts – that's a habit."))

    // 7. Mehr Volumen als beim letzten Mal dieses Workouts
    if (t.workoutId >= 0 && stats.volume > 0) {
        val last = store.sessions.filter { it.workoutId == t.workoutId && it.start < t.start }.maxByOrNull { it.start }
        val prevVol = last?.let { sessionStats(store, it).volume } ?: 0.0
        if (prevVol > 0 && stats.volume > prevVol) out += Achievement(AchievementKind.VOLUME_UP,
            tr("+${Math.round((stats.volume - prevVol) / prevVol * 100)}\u00A0% Volumen", "+${Math.round((stats.volume - prevVol) / prevVol * 100)}\u00A0% volume"),
            tr("${fmtVol(stats.volume)} statt ${fmtVol(prevVol)} beim letzten Mal", "${fmtVol(stats.volume)} vs. ${fmtVol(prevVol)} last time"))
    }

    // 9. Muskelgruppe nach mindestens 14 Tagen Pause wieder trainiert
    val skip = setOf(MuscleGroup.CARDIO, MuscleGroup.SONSTIGE, MuscleGroup.GANZKOERPER)
    results.map { it.group }.distinct().filter { it !in skip }.forEach { g ->
        val last = store.sets.filter { it.time < t.start && it.isWork && store.groupOf(it.exercise) == g }.maxOfOrNull { it.time }
        if (last != null && daysBetween(last, t.start) >= 14) out += Achievement(AchievementKind.MUSCLE_BACK,
            tr("${g.label} wieder dabei", "${g.label} is back"),
            tr("Nach ${daysBetween(last, t.start)} Tagen Pause", "After a ${daysBetween(last, t.start)}-day break"))
    }

    // 10. Serie: erstes Training der Woche verlängert die Serie (Wochen in Folge mit Training)
    if (pos == 1) {
        val s = streakFrom(all.filter { it.start <= t.end }.map { startOfWeek(it.start) }.toSet(), weekStart)
        val n = s.weeks
        if (n >= 2) out += Achievement(AchievementKind.STREAK, tr("$n Wochen in Folge", "$n weeks in a row"),
            if (s.jokers > 0) tr("Deine Serie läuft weiter – der Joker hat sie gerettet 🃏", "Your streak continues – saved by a joker 🃏")
            else tr("Deine Serie läuft weiter", "Your streak continues"))
    }

    // 12. Immer: das Training selbst
    val parts = buildList {
        if (stats.sets > 0) add(plural(stats.sets, tr("Satz", "set"), tr("Sätze", "sets")))
        if (stats.volume > 0) add(tr("${fmtVol(stats.volume)} bewegt", "${fmtVol(stats.volume)} moved"))
        if (stats.cardioMinutes > 0) add("${stats.cardioMinutes}\u00A0min Cardio")
        if (t.id >= 0 && t.end - t.start >= 60_000) add("${Math.round((t.end - t.start) / 60000.0)}\u00A0min")
    }
    out += Achievement(AchievementKind.SESSION, tr("Training geschafft", "Workout done"), parts.joinToString(" · ").ifEmpty { null })

    return out.sortedBy { it.kind.ordinal } // stabil: innerhalb einer Art bleibt die Reihenfolge der Übungen
}

