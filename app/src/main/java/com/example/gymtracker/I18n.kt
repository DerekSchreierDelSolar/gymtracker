package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale

// ---------- Sprache ----------

/** Sprachen der App: Code → Name in der Sprache selbst (Reihenfolge = Auswahl in den Einstellungen). Web: LANGUAGES in core.js */
val LANGUAGES = listOf("de" to "Deutsch", "en" to "English", "fr" to "Français", "it" to "Italiano", "es" to "Español", "tr" to "Türkçe")

/**
 * Aktuelle App-Sprache (Code aus LANGUAGES). Ein Compose-State auf oberster Ebene:
 * Jede Stelle, die tr() während des Zeichnens aufruft, liest diesen State –
 * ändert er sich, zeichnet Compose diese Stellen automatisch neu.
 * Setter lädt bei Bedarf die Übersetzungstabelle (Translations).
 */
private var languageState by mutableStateOf("de")
var appLanguage: String
    get() = languageState
    set(v) {
        val l = v.takeIf { c -> LANGUAGES.any { it.first == c } } ?: "de"
        Translations.prepare(l)
        languageState = l
    }

val isEnglish: Boolean get() = appLanguage == "en"
val isGerman: Boolean get() = appLanguage == "de"

/**
 * Text in der aktuellen Sprache. Deutsch und Englisch stehen direkt nebeneinander im Code;
 * die anderen Sprachen kommen aus assets/i18n/<lang>.json (Schlüssel = deutscher Text), sonst Englisch.
 */
fun tr(de: String, en: String): String = when (appLanguage) {
    "de" -> de
    "en" -> en
    else -> Translations.lookup(de, en) ?: en
}

/** Locale für Datums-/Zahlenformate (Monats- und Wochentagsnamen, Dezimaltrenner). Europäisches Datumsformat auch auf Englisch. */
fun appLocale(): Locale = when (appLanguage) {
    "en" -> Locale.UK
    "fr" -> Locale.FRANCE
    "it" -> Locale.ITALY
    "es" -> Locale("es", "ES")
    "tr" -> Locale("tr", "TR")
    else -> Locale.GERMANY
}

/**
 * Übersetzungstabellen für weitere Sprachen (wie gettext: deutscher Text → Übersetzung), erzeugt/geprüft mit
 * `node web/tools/i18n.mjs`. Texte mit eingesetzten Werten stehen als Vorlage drin („{0} Sätze“ → „{0} séries“):
 * Der fertige deutsche Text wird gegen die Vorlage gematcht (wie ein LIKE mit Platzhaltern) und die Werte
 * an die Stellen der Übersetzung gesetzt. Ergebnisse werden zwischengespeichert. Web: core.js.
 */
object Translations {
    /** Zugriff auf assets/ – gesetzt von GymStore bzw. HealthPrivacyActivity, bevor die Sprache gesetzt wird. */
    var assets: android.content.res.AssetManager? = null

    private class Template(val regex: Regex, val holes: List<Int>, val text: String)

    private var lang = ""
    private var exact: Map<String, String> = emptyMap()
    private var templates: List<Template> = emptyList()
    private val cache = HashMap<String, String>()
    private const val MISS = "\u0000"

    fun prepare(l: String) {
        if (l == lang || l == "de" || l == "en") return
        val json = runCatching { assets?.open("i18n/$l.json")?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return
        val o = org.json.JSONObject(json)
        val ex = HashMap<String, String>()
        val tp = ArrayList<Pair<Int, Template>>()
        val hole = Regex("""\{(\d+)\}""")
        for (k in o.keys()) {
            val v = o.getString(k)
            if (!hole.containsMatchIn(k)) { ex[k] = v; continue }
            val holes = hole.findAll(k).map { it.groupValues[1].toInt() }.toList()
            val pattern = k.split(hole).joinToString("(.*?)") { if (it.isEmpty()) "" else Regex.escape(it) }
            tp += k.replace(hole, "").length to Template(Regex(pattern, RegexOption.DOT_MATCHES_ALL), holes, v)
        }
        synchronized(cache) {
            exact = ex
            templates = tp.sortedByDescending { it.first }.map { it.second } // genauere Vorlagen zuerst
            cache.clear()
            lang = l
        }
    }

    /** Gleicher deutscher Text mit anderer Bedeutung (z. B. „Start“ = Home bzw. Start): Eintrag „deutsch␟englisch“ hat Vorrang. */
    fun lookup(de: String, en: String): String? {
        exact["$de␟$en"]?.let { return it }
        exact[de]?.let { return it }
        synchronized(cache) {
            cache[de]?.let { return it.takeIf { v -> v != MISS } }
            if (cache.size > 3000) cache.clear()
            var out: String? = null
            for (t in templates) {
                val m = t.regex.matchEntire(de) ?: continue
                var s = t.text
                t.holes.forEachIndexed { i, n -> s = s.replace("{$n}", m.groupValues[i + 1]) }
                out = s
                break
            }
            cache[de] = out ?: MISS
            return out
        }
    }
}



/**
 * Alphabetisch nach dem angezeigten Namen sortieren (Sprache der App; Umlaute wie Grundbuchstaben,
 * Groß/klein egal) – für alle Übungslisten. `name` liefert den Anzeigenamen.
 */
fun <T> List<T>.sortedByName(name: (T) -> String): List<T> {
    val c = java.text.Collator.getInstance(appLocale()).apply { strength = java.text.Collator.PRIMARY }
    // Einseitige Variante direkt unter der normalen: „Bizeps-Curls (Kurzhantel)“ vor „… (Kurzhantel, einarmig)“
    val side = Regex(""", (einarmig|einbeinig)\)""", RegexOption.IGNORE_CASE)
    fun key(t: T) = name(t).replace(side, ")")
    fun one(t: T) = if (key(t) == name(t)) 0 else 1 // 1 = einseitige Variante
    return sortedWith { a, b -> c.compare(key(a), key(b)).takeIf { it != 0 } ?: (one(a) - one(b)) }
}
