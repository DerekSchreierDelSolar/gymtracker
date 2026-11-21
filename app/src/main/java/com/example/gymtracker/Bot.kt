package com.example.gymtracker

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

// ---------- Fragen-Bot (ohne KI) ----------
//
// Beantwortet Fragen nur aus dem Übungs-Wiki (assets/wiki/*.json) und einer festen Liste zu den
// App-Funktionen (assets/bot.json). Kein Sprachmodell: Die Frage wird in Wörter zerlegt und mit
// Stichwörtern verglichen (mit etwas Toleranz für Tippfehler). Web-Version: web/js/bot.js (gleiche Logik).

/** Eine Nachricht im Verlauf. groups = (Überschrift, Grundübungen) – als antippbare Chips (öffnen das Wiki). */
data class BotMessage(
    val fromUser: Boolean,
    val text: String,
    val groups: List<Pair<String, List<String>>> = emptyList(),
    val suggestions: List<String> = emptyList()
)

object Bot {
    private class Faq(val keys: List<List<String>>, val answer: WikiText)

    private var faq: List<Faq> = emptyList()
    /** Region/Muskel → Wortfolgen, die ihn bezeichnen */
    private var targets: Map<String, List<List<String>>> = emptyMap()
    private var exerciseWords: List<List<String>> = emptyList()
    private var muscleWords: List<List<String>> = emptyList()
    private var howWords: List<List<String>> = emptyList()
    private var tipWords: List<List<String>> = emptyList()

    /** Verlauf (bleibt erhalten, solange die App läuft) */
    val history = mutableStateListOf<BotMessage>()

    fun load(context: Context) {
        if (faq.isNotEmpty()) return
        val o = JSONObject(context.assets.open("bot.json").bufferedReader().use { it.readText() })
        fun phrases(a: JSONArray) = List(a.length()) { tokens(a.getString(it)) }
        val f = o.getJSONArray("faq")
        faq = List(f.length()) { i ->
            val e = f.getJSONObject(i)
            val a = e.getJSONArray("a")
            Faq(phrases(e.getJSONArray("keys")), WikiText(a.getString(0), a.getString(1)))
        }
        val t = o.getJSONObject("targets")
        targets = t.keys().asSequence().associateWith { phrases(t.getJSONArray(it)) }
        exerciseWords = phrases(o.getJSONArray("exerciseWords"))
        muscleWords = phrases(o.getJSONArray("muscleWords"))
        howWords = phrases(o.getJSONArray("howWords"))
        tipWords = phrases(o.getJSONArray("tipWords"))
    }

    // ----- Text vergleichen -----

    /** Kleinbuchstaben, Umlaute ausgeschrieben, nur Buchstaben/Ziffern, Wörter durch ein Leerzeichen getrennt */
    fun norm(s: String): String = s.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun tokens(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }

    /** Gleiches Wort – bei längeren Wörtern auch mit 1 (ab 9 Buchstaben 2) Tippfehlern */
    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        val n = minOf(a.length, b.length)
        if (n < 5) return false
        val max = if (n >= 9) 2 else 1
        if (kotlin.math.abs(a.length - b.length) > max) return false
        // Levenshtein-Abstand (Einfügen, Löschen, Ersetzen)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length] <= max
    }

    /** Kommt die Wortfolge `phrase` in `words` vor? */
    private fun has(words: List<String>, phrase: List<String>): Boolean {
        if (phrase.isEmpty() || phrase.size > words.size) return false
        return (0..words.size - phrase.size).any { i -> phrase.indices.all { k -> close(words[i + k], phrase[k]) } }
    }

    private fun hasAny(words: List<String>, list: List<List<String>>) = list.any { has(words, it) }

    // ----- Übungen erkennen -----

    /** Grundübung → Wortfolgen (deutscher Name, Teile bei „/“, englische Namen ohne Gerät) */
    private val exercisePhrases: Map<String, List<List<String>>> by lazy {
        Wiki.entries.keys.associateWith { base ->
            val names = base.split('/').map { it.trim() } + base +
                CATALOG.filter { it.base == base }.map { it.en.substringBefore(" (") }
            names.map(::tokens).filter { it.isNotEmpty() }.distinct()
        }
    }

    /** Am besten passende Übung: die mit der längsten erkannten Wortfolge („Rumänisches Kreuzheben“ vor „Kreuzheben“) */
    private fun findExercise(words: List<String>): Pair<String, List<String>>? =
        exercisePhrases.flatMap { (base, ps) -> ps.filter { has(words, it) }.map { base to it } }
            .maxWithOrNull(compareBy({ it.second.size }, { it.second.sumOf { w -> w.length } }))

    private fun findTarget(words: List<String>): Pair<String, List<String>>? =
        targets.flatMap { (key, ps) -> ps.filter { has(words, it) }.map { key to it } }
            .maxWithOrNull(compareBy({ it.second.size }, { it.second.sumOf { w -> w.length } }))

    private fun levelsOf(base: String): Map<String, Float> =
        Wiki.entries[base]?.let { MuscleSet(it.primary, it.secondary, it.regions).levels() }.orEmpty()

    // ----- Antworten -----

    /** Vorschlags-Pool; angezeigt wird jeweils eine zufällige Auswahl (wechselt bei jedem Öffnen). */
    private val pool: List<String>
        get() = listOf(
            tr("Wie mache ich Kreuzheben?", "How do I deadlift?"),
            tr("Übungen für obere Brust", "Exercises for upper chest"),
            tr("Was ist ein Drop-Satz?", "What is a drop set?"),
            tr("Wie mache ich ein Backup?", "How do I back up?"),
            tr("Welche Muskeln trainiert Rudern sitzend?", "Which muscles does seated row work?"),
            tr("Übungen für hintere Schulter", "Exercises for rear delts"),
            tr("Wie plane ich Trainingstage?", "How do I plan training days?"),
            tr("Übungen für Waden", "Exercises for calves"),
            tr("Wie funktioniert der Gewichts-Vorschlag?", "How does the weight suggestion work?"),
            tr("Was bedeutet optional?", "What does optional mean?"),
            tr("Übungen für den unteren Rücken", "Exercises for the lower back")
        )

    val suggestions: List<String> get() = pool.shuffled().take(5)

    /** Begrüßung; `recent` = Name der zuletzt trainierten Übung → passender erster Vorschlag. */
    fun greeting(recent: String? = null) = BotMessage(
        false,
        tr(
            "Hallo! 👋 Ich kenne alle Übungen aus dem Wiki und die Funktionen der App. Frag mich z. B. nach der Ausführung einer Übung, nach Übungen für einen Muskel oder wie etwas in der App funktioniert.",
            "Hi! 👋 I know every exercise in the wiki and the app's features. Ask me how to do an exercise, which exercises train a muscle, or how something in the app works."
        ),
        suggestions = listOfNotNull(recent?.let { tr("Wie mache ich $it?", "How do I do $it?") }) + suggestions.take(if (recent != null) 4 else 5)
    )

    /** Frage stellen: Frage und Antwort landen im Verlauf. */
    fun ask(question: String) {
        if (question.isBlank()) return
        history.add(BotMessage(true, question.trim()))
        history.add(answer(question))
    }

    fun answer(question: String): BotMessage {
        val words = tokens(question)
        val ex = findExercise(words)
        val target = findTarget(words)
        val faqHit = faq.map { f -> f to f.keys.filter { has(words, it) }.sumOf { it.size } }.maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }?.first
        val asksHow = hasAny(words, howWords) || hasAny(words, tipWords) || hasAny(words, muscleWords)
        return when {
            // „Übungen für Beinbeuger“: Muskel gemeint, obwohl es auch eine Übung „Beinbeuger“ gibt
            target != null && ex != null && hasAny(words, exerciseWords) && ex.second.all { w -> target.second.any { close(it, w) } } ->
                targetAnswer(target.first)
            // „Bankdrücken eintragen“ → App-Funktion, „Wie mache ich Bankdrücken?“ → Übung
            ex != null && faqHit != null && !asksHow -> BotMessage(false, faqHit.answer.text)
            ex != null -> exerciseAnswer(ex.first, words)
            target != null -> targetAnswer(target.first)
            faqHit != null -> BotMessage(false, faqHit.answer.text)
            else -> BotMessage(
                false,
                tr(
                    "Dazu weiß ich leider nichts. Ich kenne die Übungen aus dem Wiki und die Funktionen der App – probier z. B.:",
                    "Sorry, I don't know about that. I know the exercises from the wiki and the app's features – try for example:"
                ),
                suggestions = suggestions
            )
        }
    }

    /** Ausführung, Tipps und Muskeln einer Übung (je nach Frage nur ein Teil) */
    private fun exerciseAnswer(base: String, words: List<String>): BotMessage {
        val e = Wiki.entries.getValue(base)
        val onlyMuscles = hasAny(words, muscleWords) && !hasAny(words, howWords) && !hasAny(words, tipWords)
        val onlyTips = hasAny(words, tipWords) && !hasAny(words, howWords)
        val muscles = levelLabels(levelsOf(base)).joinToString("\n") { (stage, labels) ->
            (if (stage >= 0.8f) "🔴 " else if (stage >= 0.45f) "🟠 " else "🟡 ") + levelTitle(stage) + ": " + labels.joinToString(", ")
        }
        val sb = StringBuilder(baseName(base))
        if (muscles.isNotEmpty()) sb.append("\n\n").append(muscles)
        if (!onlyMuscles && !onlyTips && e.how.isNotEmpty()) {
            sb.append("\n\n").append(tr("Ausführung:", "How to:"))
            e.how.forEachIndexed { i, t -> sb.append("\n${i + 1}. ").append(t.text) }
        }
        if (!onlyMuscles && e.tips.isNotEmpty()) {
            sb.append("\n\n").append(tr("Darauf achten:", "Watch out:"))
            e.tips.forEach { sb.append("\n• ").append(it.text) }
        }
        // Gerät genannt (z. B. „Kurzhantel“)? Dann den Hinweis zu dieser Variante dazu
        e.variants.entries.firstOrNull { (k, _) -> has(words, tokens(k.substringBefore('/'))) }?.let { (k, v) ->
            sb.append("\n\n").append(tr("Hinweis ($k): ", "Note ($k): ")).append(v.text)
        }
        return BotMessage(false, sb.toString(), groups = listOf(tr("Im Wiki öffnen", "Open in wiki") to listOf(base)))
    }

    /** Übungen, bei denen eine Region bzw. ein Muskel Hauptarbeit leistet (und unterstützend mitarbeitet) */
    private fun targetAnswer(key: String): BotMessage {
        val isRegion = key.contains('.')
        val label = if (isRegion) regionLabel(key) else muscleLabel(key)
        fun levelFor(base: String): Float {
            val l = levelsOf(base)
            return if (isRegion) l[key] ?: 0f else l.filterKeys { muscleOfRegion(it) == key }.values.maxOrNull() ?: 0f
        }
        val bases = Wiki.entries.keys.filter { b -> CATALOG.any { it.base == b && !it.group.isCardio } }
        // Bei einer Region zählen zuerst Übungen mit eigenen Regionswerten für diesen Muskel; Übungen, die nur
        // „ganzer Muskel“ kennen (z. B. Burpees → Brust), kommen dann unter „arbeitet mit“
        val muscle = muscleOfRegion(key)
        fun explicit(b: String) = !isRegion || Wiki.entries.getValue(b).regions.keys.any { muscleOfRegion(it) == muscle }
        val mainAll = bases.filter { levelFor(it) >= 0.8f }
        val mainExplicit = mainAll.filter(::explicit)
        val main = mainExplicit.ifEmpty { mainAll }
        // gibt es Übungen mit eigenen Regionswerten, bleiben die nur „ganzer Muskel“-Übungen ganz weg (z. B. Plank bei hinterer Schulter)
        val moderate = (bases.filter { levelFor(it) in 0.45f..0.8f } + mainAll).distinct().filter { it !in main }
            .filter { mainExplicit.isEmpty() || explicit(it) }
        if (main.isEmpty() && moderate.isEmpty()) return BotMessage(false, tr("Zu $label kenne ich keine Übungen.", "I don't know any exercises for $label."))
        return BotMessage(
            false,
            tr("Übungen für $label – antippen öffnet das Wiki:", "Exercises for $label – tap to open the wiki:"),
            groups = listOfNotNull(
                (tr("🔴 Hauptarbeit", "🔴 Main work") to main.take(15)).takeIf { main.isNotEmpty() },
                (tr("🟠 Arbeitet mit", "🟠 Assists") to moderate.take(10)).takeIf { moderate.isNotEmpty() }
            )
        )
    }
}
