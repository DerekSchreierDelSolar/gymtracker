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
    val suggestions: List<String> = emptyList(),
    /** Schritt des Workout-Assistenten unter der Blase (null = keiner) */
    val widget: BotWidget? = null,
    /** Angelegtes Workout → Knopf „Workout öffnen“ */
    val workoutId: Long? = null
)

enum class BotWidget { MUSCLES, EQUIPMENT }

object Bot {
    private class Faq(val keys: List<List<String>>, val answer: WikiText)

    private var faq: List<Faq> = emptyList()
    /** Region/Muskel → Wortfolgen, die ihn bezeichnen */
    private var targets: Map<String, List<List<String>>> = emptyMap()
    private var exerciseWords: List<List<String>> = emptyList()
    private var muscleWords: List<List<String>> = emptyList()
    private var howWords: List<List<String>> = emptyList()
    private var tipWords: List<List<String>> = emptyList()
    /** Fragen zum Muskel selbst (Muskel-Wiki): Abschnitt → Wortfolgen (anatomy, function, problems, stretch, general) */
    private var anatomyWords: Map<String, List<List<String>>> = emptyMap()
    /** Muskelgruppen für den Workout-Assistenten („Beine“, „Push“ …): Wortfolgen → Muskel-ID → Stufe */
    private class WorkoutGroup(val words: List<List<String>>, val muscles: Map<String, Float>)
    private var workoutGroups: List<WorkoutGroup> = emptyList()

    /** Verlauf (bleibt erhalten, solange die App läuft) */
    val history = mutableStateListOf<BotMessage>()

    /** Für persönliche Antworten („Wie war mein Puls?“): setzt BotTab. Kontext = Application-Context (kein Leak). */
    var store: GymStore? = null
    var appContext: Context? = null

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
        anatomyWords = o.optJSONObject("anatomyWords")?.let { a -> a.keys().asSequence().associateWith { phrases(a.getJSONArray(it)) } }.orEmpty()
        val g = o.optJSONArray("workoutGroups") ?: JSONArray()
        // Ungültige Einträge überspringen statt den ganzen Bot scheitern zu lassen (Prüfung in gen-data.mjs läuft nur fürs Web)
        workoutGroups = (0 until g.length()).mapNotNull { i ->
            val e = g.optJSONObject(i) ?: return@mapNotNull null
            val w = e.optJSONArray("words") ?: return@mapNotNull null
            val m = e.optJSONObject("muscles") ?: return@mapNotNull null
            val muscles = m.keys().asSequence().mapNotNull { k -> m.optDouble(k).takeIf { !it.isNaN() }?.let { k to it.toFloat() } }.toMap()
            WorkoutGroup(List(w.length()) { tokens(w.optString(it)) }.filter { it.isNotEmpty() }, muscles)
        }
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
    /** Supplement-ID → Wortfolgen (Suchbegriffe aus supplements.json) */
    private val supplementPhrases: Map<String, List<List<String>>> by lazy {
        SupplementWiki.entries.associate { s -> s.id to s.keys.map(::tokens).filter { it.isNotEmpty() } }
    }

    private fun findSupplement(words: List<String>): Pair<String, List<String>>? =
        supplementPhrases.flatMap { (id, ps) -> ps.filter { has(words, it) }.map { id to it } }
            .maxWithOrNull(compareBy({ it.second.size }, { it.second.sumOf { w -> w.length } }))

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
            tr("Übungen für den unteren Rücken", "Exercises for the lower back"),
            tr("Was macht der Latissimus?", "What does the lat do?"),
            tr("Wie viel Kreatin soll ich nehmen?", "How much creatine should I take?"),
            tr("Bringen BCAA etwas?", "Do BCAAs work?"),
            tr("Wie dehne ich die Waden?", "How do I stretch my calves?"),
            tr("Verletzungen am Beinbeuger", "Hamstring injuries"),
            tr("Wie war mein Puls beim letzten Training?", "What was my heart rate last workout?"),
            tr("Was ist Health Connect?", "What is Health Connect?"),
            tr("Wie erholt bin ich heute?", "How recovered am I today?")
        )

    /** „Erstelle mir ein Workout“ immer zuerst, danach eine zufällige Auswahl */
    val suggestions: List<String> get() = listOf(tr("Erstelle mir ein Workout", "Create a workout for me")) + pool.shuffled().take(4)

    /** Begrüßung; `recent` = Name der zuletzt trainierten Übung → passender erster Vorschlag. */
    fun greeting(recent: String? = null) = BotMessage(
        false,
        tr(
            "Hallo! 👋 Ich kenne alle Übungen, Muskeln und Supplements aus dem Wiki und die Funktionen der App. Frag mich z. B. nach der Ausführung einer Übung, nach Übungen für einen Muskel, nach einem Supplement oder wie etwas in der App funktioniert – oder sag „Erstelle mir ein Workout“.",
            "Hi! 👋 I know every exercise, muscle and supplement in the wiki and the app's features. Ask me how to do an exercise, which exercises train a muscle, about a supplement, or how something in the app works – or say \"Create a workout for me\"."
        ),
        // „Erstelle mir ein Workout“ zuerst, dann die zuletzt trainierte Übung, dann Zufall
        suggestions = suggestions.let { s -> s.take(1) + listOfNotNull(recent?.let { tr("Wie mache ich $it?", "How do I do $it?") }) + s.drop(1).take(if (recent != null) 3 else 4) }
    )

    /** Frage stellen: Frage und Antwort landen im Verlauf. */
    fun ask(question: String) {
        if (question.isBlank()) return
        history.add(BotMessage(true, question.trim()))
        val words = splitCompounds(tokens(question))
        if (wantsWorkout(words)) startWizard(presetMuscles(words)) else history.add(answer(question))
    }

    // ----- Workout-Assistent (WorkoutWizard.kt) -----

    /** Einzelwörter, die eine Muskelgruppe/einen Muskel bezeichnen („brust“, „bein“ …) – für splitCompounds */
    private val muscleWordSet: Set<String> by lazy {
        (workoutGroups.flatMap { it.words } + targets.values.flatten()).filter { it.size == 1 }.map { it[0] }.toSet()
    }

    /**
     * „Brustworkout“ → „brust workout“, „Ganzkörpertrainingsplan“ → „ganzkoerper trainingsplan“ – nur wenn der
     * vordere Teil ein Muskel-/Gruppenwort ist („Krafttraining“ bleibt ganz, sonst würde jede Frage dazu den Assistenten starten).
     */
    private fun splitCompounds(words: List<String>): List<String> = words.flatMap { w ->
        val i = listOf("workout", "training", "routine").map { w.indexOf(it) }.filter { it >= 3 }.minOrNull()
        if (i == null || w.substring(0, i) !in muscleWordSet) listOf(w) else listOf(w.substring(0, i), w.substring(i))
    }

    /** Wörter, nach denen ein Muskel ausdrücklich nicht gemeint ist („ohne Beine“) */
    private val NEGATIONS = setOf("ohne", "kein", "keine", "keinen", "nicht", "no", "without", "not", "except")

    /**
     * Wortvergleich für die Vorauswahl: 0 = gleich, 1 = Tippfehler, null = anders. Tippfehler erst ab 7 Buchstaben,
     * damit Alltagswörter nicht treffen („beide“ ≠ „beine“, „brauch“ ≠ „bauch“).
     */
    private fun presetDistance(a: String, b: String): Int? = when {
        a == b -> 0
        minOf(a.length, b.length) >= 7 && close(a, b) -> 1
        else -> null
    }

    /** „Erstelle mir ein Workout“, „Trainingsplan machen“, „create a workout“ … */
    private fun wantsWorkout(words: List<String>): Boolean {
        val verb = words.any { w -> listOf("erstell", "generier", "bau", "mach", "zusammenstell", "plan", "create", "build", "make", "generate").any { w.startsWith(it) } }
        val noun = words.any { w -> listOf("workout", "training", "trainingsplan", "plan", "routine").any { w.startsWith(it) } }
        return verb && noun && words.none { it == "wie" || it == "how" } // „Wie erstelle ich ein Workout?“ = App-Frage
    }

    /**
     * Muskeln aus der Frage vorauswählen („Brust-Workout“ → Brust als Hauptfokus, „Beintraining“ → Beinmuskeln).
     * Gruppen (bot.json „workoutGroups“) und Muskel-/Regionsbegriffe („targets“, Region → ganzer Muskel, Hauptfokus);
     * längere Wortfolgen gewinnen und verbrauchen ihre Wörter („unterer Rücken“ ≠ „Rücken“), dann exakte vor Tippfehler-Treffern
     * („ruecken“ ≠ „druecken“), dann längere Wörter, bei Gleichstand die Gruppe. Verneinte Treffer („ohne Beine“) verbrauchen
     * ihre Wörter, wählen aber nichts aus.
     */
    private fun presetMuscles(words: List<String>): Map<String, Float> {
        class Hit(val start: Int, val phrase: List<String>, val typos: Int, val muscles: Map<String, Float>, val group: Boolean)
        val hits = mutableListOf<Hit>()
        fun scan(phrases: List<List<String>>, muscles: Map<String, Float>, group: Boolean) {
            for (p in phrases) for (i in 0..words.size - p.size) {
                if (p.isEmpty()) continue
                val d = p.indices.map { k -> presetDistance(words[i + k], p[k]) }
                if (d.all { it != null }) hits += Hit(i, p, d.sumOf { it!! }, muscles, group)
            }
        }
        workoutGroups.forEach { scan(it.words, it.muscles, true) }
        targets.forEach { (key, ps) -> scan(ps, mapOf(muscleOfRegion(key) to 1f), false) }
        val used = BooleanArray(words.size)
        val accepted = mutableListOf<Hit>()
        val order = compareByDescending<Hit> { it.phrase.size }.thenBy { it.typos }
            .thenByDescending { it.phrase.sumOf { w -> w.length } }.thenByDescending { it.group }
        for (h in hits.sortedWith(order)) {
            val range = h.start until h.start + h.phrase.size
            if (range.any { used[it] }) continue
            range.forEach { used[it] = true }
            if (h.start == 0 || words[h.start - 1] !in NEGATIONS) accepted += h
        }
        // Reihenfolge wie in der Frage
        val out = LinkedHashMap<String, Float>()
        for (h in accepted.sortedBy { it.start }) for ((m, s) in h.muscles) if (m in MUSCLES) out[m] = maxOf(out[m] ?: 0f, s)
        return out
    }

    private fun startWizard(preset: Map<String, Float> = emptyMap()) {
        WorkoutWizard.reset()
        WorkoutWizard.muscles.putAll(preset)
        val names = preset.entries.sortedByDescending { it.value }.joinToString(", ") { muscleLabel(it.key) }
        history.add(BotMessage(
            false,
            if (preset.isEmpty()) tr(
                "Gern! Welche Muskeln willst du trainieren? Tippe in der Figur oder unten: 1× = Hauptfokus (rot), 2× = mittel (orange), 3× = leicht (gelb), 4× = wieder aus.",
                "Sure! Which muscles do you want to train? Tap the figure or the buttons: 1× = main focus (red), 2× = moderate (orange), 3× = light (yellow), 4× = off."
            ) else tr(
                "Gern! Ich habe schon markiert: $names. Passe die Auswahl bei Bedarf an – tippe in der Figur oder unten: 1× = Hauptfokus (rot), 2× = mittel (orange), 3× = leicht (gelb), 4× = wieder aus.",
                "Sure! I've already selected: $names. Adjust if you like – tap the figure or the buttons: 1× = main focus (red), 2× = moderate (orange), 3× = light (yellow), 4× = off."
            ),
            widget = BotWidget.MUSCLES
        ))
    }

    /** Schritt-Nachricht „einfrieren“ (Knöpfe weg) und die Auswahl als eigene Antwort festhalten */
    private fun closeStep(summary: String) {
        val i = history.indexOfLast { it.widget != null }
        if (i >= 0) history[i] = history[i].copy(widget = null)
        history.add(BotMessage(true, summary))
    }

    fun wizardMusclesDone() {
        val sel = WorkoutWizard.muscles
        val summary = WIZARD_STAGES.mapNotNull { st ->
            val names = sel.filterValues { it == st }.keys.map(::muscleLabel)
            if (names.isEmpty()) null else wizardStageTitle(st) + ": " + names.joinToString(", ")
        }.joinToString("\n")
        closeStep(summary)
        history.add(BotMessage(
            false,
            tr("Mit welchen Geräten? Wähle eins oder mehrere – ohne Auswahl nehme ich alle.", "Which equipment? Pick one or more – with none selected I'll use everything."),
            widget = BotWidget.EQUIPMENT
        ))
    }

    fun wizardCreate(store: GymStore) {
        val eq = WorkoutWizard.equipment
        closeStep(if (eq.isEmpty()) tr("Geräte: alle", "Equipment: any") else tr("Geräte: ", "Equipment: ") + eq.joinToString(", ") { it.label })
        val picked = WorkoutWizard.pick(store)
        if (picked.isEmpty()) {
            history.add(BotMessage(
                false,
                tr("Dazu habe ich mit diesen Geräten keine passenden Übungen gefunden. Wähle bitte andere oder mehr Geräte.",
                    "I couldn't find matching exercises with that equipment. Please pick other or more equipment."),
                widget = BotWidget.EQUIPMENT
            ))
            return
        }
        val w = WorkoutWizard.create(store, picked) ?: return
        history.add(BotMessage(
            false,
            tr("Fertig! Ich habe das Workout „${w.name}“ mit ${picked.size} Übungen angelegt (je 3 Ziel-Sätze):",
                "Done! I created the workout \"${w.name}\" with ${picked.size} exercises (3 target sets each):") +
                "\n" + picked.mapIndexed { i, e -> "${i + 1}. ${exName(e)}" }.joinToString("\n") +
                "\n\n" + tr("Du kannst es jederzeit bearbeiten – Übungen tauschen, Ziel-Sätze ändern, Reihenfolge ziehen.",
                    "You can edit it anytime – swap exercises, change target sets, drag to reorder."),
            workoutId = w.id,
            suggestions = listOf(tr("Erstelle mir ein Workout", "Create a workout for me"))
        ))
    }

    fun answer(question: String): BotMessage {
        val words = tokens(question)
        val ex = findExercise(words)
        val target = findTarget(words)
        val faqHit = faq.map { f -> f to f.keys.filter { has(words, it) }.sumOf { it.size } }.maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }?.first
        val asksHow = hasAny(words, howWords) || hasAny(words, tipWords) || hasAny(words, muscleWords)
        // Frage zum Muskel selbst („Was macht der Latissimus?“, „Wie dehne ich die Waden?“) → Muskel-Wiki
        val anatomy = anatomyWords.filterValues { hasAny(words, it) }.keys
        // Supplement genannt („Wie viel Kreatin?“) – außer eine Übung passt mit einer längeren Wortfolge
        // Persönliche Fragen zu den eigenen Daten (Puls, Health-Connect-Status) vor allem anderen
        personalAnswer(words)?.let { return it }
        val supp = findSupplement(words)
        return when {
            supp != null && (ex == null || supp.second.size >= ex.second.size) -> supplementAnswer(supp.first)
            target != null && anatomy.isNotEmpty() && !hasAny(words, exerciseWords) && muscleOfRegion(target.first) in MuscleWiki.entries &&
                (ex == null || ex.second.all { w -> target.second.any { close(it, w) } }) ->
                muscleAnswer(muscleOfRegion(target.first), anatomy)
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

    // ----- Persönliche Antworten aus den eigenen Daten -----

    private val mineWords = listOf("mein", "meine", "meinen", "meinem", "meiner", "letzten", "letztes", "letzte", "my", "last").map { listOf(it) }
    private val heartWords = listOf(listOf("puls"), listOf("herzfrequenz"), listOf("heart", "rate"), listOf("pulse"))
    /** Dann ist eine Erklärung gemeint, keine persönliche Auswertung („Wie berechnet die App kcal aus meinem Puls?“) */
    private val explainWords = listOf("kcal", "kalorien", "calories", "berechnet", "berechnen", "calculated", "zonen", "zones", "warum", "why")
        .map { listOf(it) }
    private val statusWords = listOf("verbunden", "status", "aktiv", "eingeschaltet", "connected", "active", "laeuft", "funktioniert", "working")
        .map { listOf(it) }

    private fun personalAnswer(words: List<String>): BotMessage? {
        val s = store ?: return null
        if (hasAny(words, mineWords) && hasAny(words, heartWords) && !hasAny(words, explainWords)) return heartAnswer(s)
        if (has(words, listOf("health", "connect")) && hasAny(words, statusWords)) return statusAnswer(s)
        if (hasAny(words, recoveryWords) && hasAny(words, todayWords) && !hasAny(words, explainWords) &&
            !hasAny(words, listOf(listOf("saetzen"), listOf("satz"), listOf("sets"), listOf("set")))) return recoveryAnswer()
        return null
    }

    private val recoveryWords = listOf("erholt", "erholung", "ausgeruht", "fit", "recovered", "recovery", "rested").map { listOf(it) }
    private val todayWords = listOf(listOf("bin", "ich"), listOf("heute"), listOf("mein"), listOf("meine"), listOf("today"), listOf("am", "i"), listOf("my"))

    /** „Wie erholt bin ich heute?“ → Ampel aus dem Erholungs-Check (Recovery.kt) */
    private fun recoveryAnswer(): BotMessage {
        val c = appContext
        val r = Recovery.rating()
        if (r == null) return BotMessage(false, when {
            c == null || !HealthSync.available(c) -> tr(
                "Den Erholungs-Check gibt es mit Health Connect ab Android 14 – auf diesem Gerät leider nicht.",
                "The recovery check needs Health Connect (Android 14+) – not available on this device.")
            !HealthSync.enabled || !(HealthSync.canReadResting(c) || HealthSync.canReadHrv(c) || HealthSync.canReadSleep(c)) -> tr(
                "Dafür brauche ich Ruhepuls, HRV oder Schlaf von deiner Smartwatch: Einstellungen → „Health Connect“ einschalten und „Ruhepuls“, „HRV“ und „Schlaf lesen“ erlauben.",
                "For that I need resting heart rate, HRV or sleep from your smartwatch: Settings → turn on \"Health Connect\" and allow \"Read resting HR\", \"Read HRV\" and \"Read sleep\".")
            else -> tr(
                "Von heute liegen noch keine Werte vor. Trägt deine Uhr nachts und liefert ihre App an Health Connect? Nach dem nächsten Abgleich (App öffnen) schaue ich wieder nach.",
                "There's no data from today yet. Do you wear your watch at night and does its app send data to Health Connect? I'll check again at the next sync (open the app).")
        })
        val text = buildString {
            append(Recovery.message(r.level))
            r.parts.forEach { p -> append("\n${p.icon} ${p.label}: ${p.value}" + (p.compare?.let { " – $it" } ?: "")) }
            append(tr("\nNur eine Orientierung – hör auch auf dein Gefühl.", "\nJust a guide – listen to your body too."))
        }
        return BotMessage(false, text)
    }

    /** „Wie war mein Puls beim letzten Training?“ → letztes Training mit Puls, Zonen, kcal, Vergleich */
    private fun heartAnswer(s: GymStore): BotMessage {
        val withHr = s.sessions.filter { it.hr != null }.sortedByDescending { it.start }
        val last = withHr.firstOrNull() ?: return BotMessage(false, noHeartReason())
        val hr = last.hr!!
        val stats = sessionStats(s, last)
        val text = buildString {
            append(tr("Dein letztes Training mit Puls: ${last.workoutName} am ${fmtDate(last.start)}\n",
                "Your last workout with heart rate: ${last.workoutName} on ${fmtDate(last.start)}\n"))
            append(tr("❤ Ø ${hr.avg} · max ${hr.max} · min ${hr.min} bpm", "❤ avg ${hr.avg} · max ${hr.max} · min ${hr.min} bpm"))
            maxHeartRate(s, last.start)?.let { maxHr ->
                val minutes = heartZoneMinutes(hr, maxHr)
                val top = minutes.indices.maxByOrNull { minutes[it] }
                if (top != null && minutes[top] > 0) append(tr("\nMeiste Zeit in Zone ${heartZoneNames()[top]} (${minutes[top]} min)",
                    "\nMost time in zone ${heartZoneNames()[top]} (${minutes[top]} min)"))
            }
            stats.kcal?.let {
                append(if (stats.kcalFromHr) tr("\n🔥 ~${fmtKcal(it)} kcal (aus dem Puls berechnet)", "\n🔥 ~${fmtKcal(it)} kcal (calculated from heart rate)")
                else tr("\n🔥 ~${fmtKcal(it)} kcal (MET-Schätzung – mit Geschlecht und Geburtsjahr im Profil rechne ich mit dem Puls)",
                    "\n🔥 ~${fmtKcal(it)} kcal (MET estimate – with sex and birth year in your profile I use heart rate)"))
            }
            if (withHr.size >= 2) {
                val recent = withHr.take(5)
                val avg = Math.round(recent.map { it.hr!!.avg }.average())
                append(tr("\nØ deiner letzten ${recent.size} Trainings mit Puls: $avg bpm (davor: ${withHr[1].hr!!.avg} bpm)",
                    "\nAverage of your last ${recent.size} workouts with heart rate: $avg bpm (previous: ${withHr[1].hr!!.avg} bpm)"))
            }
            append(tr("\nMehr in der Trainingsübersicht (Kalender → Tag antippen).", "\nMore in the workout summary (calendar → tap the day)."))
        }
        return BotMessage(false, text)
    }

    /** Warum (noch) kein Puls da ist – je nach Gerät, Einstellung und Berechtigung */
    private fun noHeartReason(): String {
        val c = appContext
        return when {
            c == null || !HealthSync.available(c) -> tr(
                "Ich habe noch zu keinem Training Pulswerte. Den Puls holt die App über Health Connect – das gibt es ab Android 14.",
                "I don't have heart rate for any workout yet. The app gets heart rate via Health Connect – available from Android 14.")
            !HealthSync.enabled -> tr(
                "Ich habe noch keine Pulswerte. Schalte unter ⋮ → Einstellungen „Health Connect“ ein und erlaube „Puls lesen“ – dann hole ich den Puls deiner Smartwatch zu deinen Trainings.",
                "I don't have heart rate data yet. Turn on \"Health Connect\" under ⋮ → Settings and allow \"Read heart rate\" – then I'll fetch your smartwatch heart rate for your workouts.")
            !HealthSync.canReadHeartRate(c) -> tr(
                "Health Connect ist an, aber „Puls lesen“ ist nicht erlaubt. Einstellungen → „Health Connect“ → „Fehlende erlauben“.",
                "Health Connect is on, but \"Read heart rate\" isn't allowed. Settings → \"Health Connect\" → \"Allow missing\".")
            else -> tr(
                "Zu deinen Trainings der letzten 7 Tage habe ich noch keinen Puls gefunden. Prüfe, ob deine Uhr während des Trainings misst und ihre App (z. B. Samsung Health) Daten an Health Connect liefert. Uhren übertragen oft verzögert – ich schaue bei jedem Abgleich wieder nach.",
                "I haven't found heart rate for your workouts of the last 7 days yet. Check that your watch measures during the workout and its app (e.g. Samsung Health) sends data to Health Connect. Watches often sync late – I check again at every sync.")
        }
    }

    /** „Ist Health Connect verbunden?“ → Status, Berechtigungen, letzter Abgleich */
    private fun statusAnswer(s: GymStore): BotMessage {
        val c = appContext
        if (c == null || !HealthSync.available(c)) return BotMessage(false, tr(
            "Health Connect ist auf diesem Gerät nicht verfügbar – es gibt das erst ab Android 14.",
            "Health Connect isn't available on this device – it requires Android 14."))
        if (!HealthSync.enabled) return BotMessage(false, tr(
            "Health Connect ist ausgeschaltet. Einschalten: ⋮ → Einstellungen → „Health Connect“.",
            "Health Connect is turned off. Turn it on: ⋮ → Settings → \"Health Connect\"."))
        val perms = listOf(
            tr("Gewicht lesen", "read weight") to HealthSync.canRead(c),
            tr("Gewicht eintragen", "write weight") to HealthSync.canWrite(c),
            tr("Trainings eintragen", "write workouts") to HealthSync.canWriteWorkouts(c),
            tr("kcal eintragen", "write calories") to HealthSync.canWriteKcal(c),
            tr("Puls lesen", "read heart rate") to HealthSync.canReadHeartRate(c)
        )
        val text = buildString {
            append(tr("Health Connect ist eingeschaltet.\n", "Health Connect is on.\n"))
            append(perms.joinToString("\n") { (l, ok) -> (if (ok) "✓ " else "– ") + l })
            if (HealthSync.lastSync > 0) append(tr("\nZuletzt abgeglichen: ", "\nLast sync: ") + fmtDateTime(HealthSync.lastSync))
            HealthSync.error?.let { append(tr("\nLetzter Fehler: ", "\nLast error: ") + it) }
            append(tr("\n${s.sessions.count { it.hr != null }} von ${s.sessions.size} Trainings haben Pulswerte.",
                "\n${s.sessions.count { it.hr != null }} of ${s.sessions.size} workouts have heart rate."))
            if (perms.any { !it.second }) append(tr("\nFehlendes kannst du in den Einstellungen mit „Fehlende erlauben“ freigeben.",
                "\nYou can allow missing ones in Settings via \"Allow missing\"."))
        }
        return BotMessage(false, text)
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

    /** Kurzinfo aus dem Supplement-Wiki: Einstufung, Wirkung, Dosierung, Einnahme, Risiken */
    private fun supplementAnswer(id: String): BotMessage {
        val s = SupplementWiki.byId(id) ?: return BotMessage(false, "")
        val dot = when (s.evidence) { 3 -> "🟢"; 2 -> "🟡"; 1 -> "🟠"; else -> "⚪" }
        val sb = StringBuilder(s.name.text).append("\n").append(dot).append(" ").append(evidenceLabel(s.evidence))
        if (s.deficiencyOnly) sb.append(tr(" · nur bei Mangel", " · only if deficient"))
        sb.append("\n\n").append(s.summary.text)
        if (s.effects.isNotEmpty()) {
            sb.append("\n\n").append(tr("Wirkung:", "Effect:"))
            s.effects.forEach { sb.append("\n• ").append(it.text) }
        }
        sb.append("\n\n").append(tr("Dosierung: ", "Dosage: ")).append(s.dose.text)
        if (s.timing.de != "–") sb.append("\n").append(tr("Einnahme: ", "When: ")).append(s.timing.text)
        if (s.risks.isNotEmpty()) {
            sb.append("\n\n").append(tr("Nebenwirkungen:", "Side effects:"))
            s.risks.forEach { sb.append("\n⚠ ").append(it.text) }
        }
        sb.append("\n\n").append(tr("Mehr im Wiki unter „Supplements“. Keine Beratung – bei Erkrankungen oder Medikamenten ärztlich abklären.",
            "More in the wiki under \"Supplements\". Not advice – ask a doctor if you have conditions or take medication."))
        return BotMessage(false, sb.toString())
    }

    /**
     * Infos aus dem Muskel-Wiki. sections = erkannte Fragearten; nur „general“ → alles,
     * sonst nur die gefragten Teile (z. B. nur Dehnen).
     */
    private fun muscleAnswer(id: String, sections: Set<String>): BotMessage {
        val m = MuscleWiki.entries.getValue(id)
        val specific = sections - "general"
        fun show(s: String) = specific.isEmpty() || s in specific
        val sb = StringBuilder(muscleLabel(id)).append(" · ").append(m.latin)
        if (specific.isEmpty()) sb.append("\n\n").append(m.parts.text)
        if (show("anatomy")) sb.append("\n\n")
            .append(tr("Ursprung: ", "Origin: ")).append(m.origin.text)
            .append("\n").append(tr("Ansatz: ", "Insertion: ")).append(m.insertion.text)
            .append("\n").append(tr("Nerv: ", "Nerve: ")).append(m.nerve.text)
        if (show("function") && m.function.isNotEmpty()) {
            sb.append("\n\n").append(tr("Funktion:", "Function:"))
            m.function.forEach { sb.append("\n• ").append(it.text) }
        }
        if (show("problems") && m.problems.isNotEmpty()) {
            sb.append("\n\n").append(tr("Häufige Beschwerden:", "Common problems:"))
            m.problems.forEach { sb.append("\n⚠ ").append(it.text) }
        }
        if (show("stretch") && m.stretch.isNotEmpty()) {
            sb.append("\n\n").append(tr("Dehnen:", "Stretching:"))
            m.stretch.forEach { sb.append("\n• ").append(it.text) }
        }
        sb.append("\n\n").append(tr("Mehr im Wiki unter „Muskeln“. Keine Diagnose – bei starken oder anhaltenden Beschwerden ärztlich abklären.",
            "More in the wiki under \"Muscles\". Not a diagnosis – see a doctor for severe or persistent problems."))
        val main = mainExercisesFor(id)
        return BotMessage(false, sb.toString(),
            groups = listOfNotNull((tr("🔴 Übungen dafür", "🔴 Exercises for it") to main.take(15)).takeIf { main.isNotEmpty() }))
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
