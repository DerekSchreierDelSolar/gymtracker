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



/**
 * Alphabetisch nach dem angezeigten Namen sortieren (Sprache der App; Umlaute wie Grundbuchstaben,
 * Groß/klein egal) – für alle Übungslisten. `name` liefert den Anzeigenamen.
 */
fun <T> List<T>.sortedByName(name: (T) -> String): List<T> {
    val c = java.text.Collator.getInstance(appLocale()).apply { strength = java.text.Collator.PRIMARY }
    // Einseitige Variante direkt unter der normalen: „Bizepscurls (Kurzhantel)“ vor „… (Kurzhantel, einarmig)“
    val side = Regex(""", (einarmig|einbeinig)\)""", RegexOption.IGNORE_CASE)
    fun key(t: T) = name(t).replace(side, ")")
    fun one(t: T) = if (key(t) == name(t)) 0 else 1 // 1 = einseitige Variante
    return sortedWith { a, b -> c.compare(key(a), key(b)).takeIf { it != 0 } ?: (one(a) - one(b)) }
}
