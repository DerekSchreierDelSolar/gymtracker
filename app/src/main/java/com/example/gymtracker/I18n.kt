package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale

// ---------- Sprache ----------

/**
 * Aktuelle App-Sprache ("de" oder "en"). Ein Compose-State auf oberster Ebene:
 * Jede Stelle, die tr() während des Zeichnens aufruft, liest diesen State –
 * ändert er sich, zeichnet Compose diese Stellen automatisch neu.
 */
var appLanguage by mutableStateOf("de")

val isEnglish: Boolean get() = appLanguage == "en"

/** Text in der aktuellen Sprache. Beide Varianten stehen direkt nebeneinander im Code. */
fun tr(de: String, en: String): String = if (isEnglish) en else de

/** Locale für Datums-/Zahlenformate (Monats- und Wochentagsnamen, Dezimaltrenner). */
fun appLocale(): Locale = if (isEnglish) Locale.US else Locale.GERMANY


