package com.example.gymtracker

import android.content.Context
import org.json.JSONObject

/**
 * Laufendes Training dauerhaft merken (SharedPreferences `training`, gerätegebunden, nicht im Export/Backup).
 * `rememberSaveable` allein übersteht nur Drehen und ein Beenden durch das System – nicht Wegwischen aus den
 * letzten Apps, „Beenden erzwingen“ oder einen Neustart. Web: `gym_training` im localStorage (nav.js).
 */
data class TrainingState(
    val start: Long,
    val workoutId: Long,
    val index: Int = 0,
    val open: Boolean = true,
    val swaps: Map<String, String> = emptyMap(),
    val restEnd: Long? = null,
    val restTotal: Long = 0L
)

object TrainingPrefs {
    private const val PREFS = "training"

    fun load(context: Context): TrainingState? = runCatching {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("state", null) ?: return null
        val o = JSONObject(text)
        val sw = o.optJSONObject("swaps")
        TrainingState(
            start = o.getLong("start"),
            workoutId = o.getLong("workoutId"),
            index = o.optInt("index", 0),
            open = o.optBoolean("open", true),
            swaps = sw?.keys()?.asSequence()?.associateWith { sw.getString(it) }.orEmpty(),
            // Abgelaufene Pause nicht wiederbeleben
            restEnd = o.optLong("restEnd", 0L).takeIf { it > System.currentTimeMillis() },
            restTotal = o.optLong("restTotal", 0L)
        )
    }.getOrNull()

    /** null = kein Training (Eintrag löschen). */
    fun save(context: Context, s: TrainingState?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (s == null) prefs.remove("state") else prefs.putString("state", JSONObject().apply {
            put("start", s.start)
            put("workoutId", s.workoutId)
            put("index", s.index)
            put("open", s.open)
            put("swaps", JSONObject(s.swaps))
            s.restEnd?.let { put("restEnd", it) }
            put("restTotal", s.restTotal)
        }.toString())
        prefs.apply()
    }
}

/** Ab so langer Zeit ohne neuen Satz bietet „Beenden“ an, das Training beim letzten Satz enden zu lassen. */
const val FORGOTTEN_END_MS = 30 * 60_000L

/**
 * Zeitpunkt des letzten Satzes, wenn er mehr als 30 min zurückliegt („Beenden vergessen“) – sonst null.
 * Ohne diese Korrektur würden Dauer, kcal, Puls und das Abzeichen „> 90 min“ verfälscht. Web: forgottenEnd in training.js.
 */
fun forgottenEnd(store: GymStore, exercises: List<String>, start: Long, now: Long = System.currentTimeMillis()): Long? =
    exercises.flatMap { store.setsBetween(it, start, now) }.maxOfOrNull { it.time }
        ?.takeIf { now - it > FORGOTTEN_END_MS }
