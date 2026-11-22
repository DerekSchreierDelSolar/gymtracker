package com.example.gymtracker

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf

/**
 * Bedienhinweise („Tippen = …“, „Tipp: Am Griff ziehen …“) nur zeigen, bis die Geste einmal benutzt wurde.
 * Gemerkt in SharedPreferences (gerätegebunden, nicht im Export) – wie localStorage `gym_hints` im Web (ui.js).
 * `done` ist eine Compose-State-Liste → Hinweise verschwinden sofort nach der ersten Nutzung.
 */
object Hints {
    const val LIST = "list"          // Übungen-Tab: Tippen / gedrückt halten
    const val DRAG = "drag"          // Workout: am Griff ziehen
    const val REST = "rest"          // Training: Pause-Kreis antippen
    const val WEEK_GOAL = "weekGoal" // Startseite: Wochenziel antippen
    const val WEIGHT = "weight"      // Körper: Punkt im Gewichtsverlauf antippen
    const val SET = "set"            // Übung: Satz antippen zum Bearbeiten

    private var prefs: SharedPreferences? = null
    private val done = mutableStateListOf<String>()

    fun load(context: Context) {
        val p = context.getSharedPreferences("hints", Context.MODE_PRIVATE)
        prefs = p
        done.clear()
        done.addAll(p.all.keys)
    }

    /** true = Hinweis noch zeigen */
    fun show(key: String): Boolean = key !in done

    /** Geste wurde benutzt → Hinweis künftig ausblenden */
    fun used(key: String) {
        if (key in done) return
        done.add(key)
        prefs?.edit()?.putBoolean(key, true)?.apply()
    }
}
