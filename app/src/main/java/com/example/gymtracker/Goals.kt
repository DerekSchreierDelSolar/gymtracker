package com.example.gymtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.abs

// ---------- Langfristige Ziele (Roadmap Schritt 1) ----------
// Web: goals.js (gleiche Logik). Im JSON als "goals": [{ id, type, exercise?, target, start, created }].

/** Art des Ziels. Bei STRENGTH/ENDURANCE gehört eine Übung dazu. */
enum class GoalType(val emoji: String) {
    /** Gewicht bei einer Übung schaffen (bester Satz, ohne Aufwärm-/Drop-Sätze), z. B. 100 kg Bankdrücken */
    STRENGTH("🏋️"),
    /** Zeit am Stück: Cardio in Minuten bzw. Zeitübung in Sekunden (längster Satz), z. B. 30 min laufen */
    ENDURANCE("⏱️"),
    /** Körpergewicht erreichen (ab- oder zunehmen, Richtung aus Start → Ziel) */
    BODYWEIGHT("⚖️"),
    /** Ausdauer steigern: Cardio-Minuten pro Woche (wiederkehrend, wird nie „fertig“) */
    CARDIO_WEEK("🫀"),
    /** Gesund bleiben: in so vielen Wochen das Wochenziel schaffen (jede geschaffte Woche zählt, keine Serie) */
    REGULAR("💚");

    val label: String get() = when (this) {
        STRENGTH -> tr("Gewicht schaffen", "Lift a weight")
        ENDURANCE -> tr("Zeit am Stück", "Go the distance")
        BODYWEIGHT -> tr("Körpergewicht", "Body weight")
        CARDIO_WEEK -> tr("Ausdauer steigern", "Build endurance")
        REGULAR -> tr("Gesund bleiben", "Stay healthy")
    }

    val hint: String get() = when (this) {
        STRENGTH -> if (isLb) tr("z. B. 225 lbs Bankdrücken", "e.g. 225 lbs bench press") else tr("z. B. 100 kg Bankdrücken", "e.g. 100 kg bench press")
        ENDURANCE -> tr("z. B. 30 min laufen oder 2 min Unterarmstütz", "e.g. run 30 min or hold a plank for 2 min")
        BODYWEIGHT -> if (isLb) tr("z. B. 20 lbs abnehmen", "e.g. lose 20 lbs") else tr("z. B. 10 kg abnehmen", "e.g. lose 10 kg")
        CARDIO_WEEK -> tr("Cardio-Minuten pro Woche", "Cardio minutes per week")
        REGULAR -> tr("regelmäßig dein Wochenziel schaffen", "hit your weekly goal regularly")
    }

    val needsExercise: Boolean get() = this == STRENGTH || this == ENDURANCE

    companion object {
        fun parse(s: String?): GoalType? = entries.firstOrNull { it.name == s }
    }
}

/**
 * Ein Ziel. `target` in der Einheit des Typs (kg, Minuten/Sekunden wie GymSet.reps, Minuten pro Woche, Wochen).
 * `start` = Stand beim Anlegen (bester Satz bzw. Körpergewicht), damit der Fortschritt ab dort zählt.
 */
data class Goal(
    val id: Long,
    val type: GoalType,
    val exercise: String? = null,
    val target: Double,
    val start: Double = 0.0,
    val created: Long
)

/** Fortschritt zur Anzeige: fraction 0..1, reachedAt = wann geschafft (null = noch nicht / wiederkehrend). */
data class GoalProgress(val fraction: Float, val value: String, val detail: String, val reachedAt: Long?, val short: String = "${Math.round(fraction * 100)} %")

/** Menge einer Ausdauer-Übung: Cardio in Minuten, Zeitübung in Sekunden (wie GymSet.reps). */
fun fmtAmount(exercise: String, v: Double, store: GymStore): String =
    if (store.groupOf(exercise).isCardio) "${fmtW(v)} min" else "${fmtW(v)} s"

/** Bester Wert einer Übung (Gewicht bzw. Dauer), null = noch nie gemacht. */
fun bestValue(store: GymStore, type: GoalType, exercise: String): Double? {
    val sets = store.sets.top().filter { it.exercise == exercise && it.reps > 0 }
    return if (type == GoalType.ENDURANCE) sets.maxOfOrNull { it.reps.toDouble() } else sets.maxOfOrNull { it.weight }
}

fun goalTitle(store: GymStore, g: Goal): String = when (g.type) {
    GoalType.STRENGTH -> "${fmtKg(g.target)} " + exName(g.exercise ?: "")
    GoalType.ENDURANCE -> fmtAmount(g.exercise ?: "", g.target, store) + " " + exName(g.exercise ?: "")
    GoalType.BODYWEIGHT -> if (g.target < g.start) tr("Auf ${fmtKg(g.target)} abnehmen", "Get down to ${fmtKg(g.target)}")
        else tr("Auf ${fmtKg(g.target)} zunehmen", "Get up to ${fmtKg(g.target)}")
    GoalType.CARDIO_WEEK -> tr("${fmtW(g.target)} min Cardio pro Woche", "${fmtW(g.target)} min cardio per week")
    GoalType.REGULAR -> tr("In ${g.target.toInt()} Wochen das Wochenziel schaffen", "Hit the weekly goal in ${g.target.toInt()} weeks")
}

/** Anteil des Wegs von start nach target (beide Richtungen), 0..1. */
private fun way(start: Double, current: Double, target: Double): Float =
    if (target == start) 1f else ((current - start) / (target - start)).toFloat().coerceIn(0f, 1f)

private fun weekEnd(ws: Long): Long = Calendar.getInstance().apply { timeInMillis = ws; add(Calendar.DAY_OF_MONTH, 7) }.timeInMillis

/** Cardio-Minuten (Arbeitssätze) im Zeitraum. */
private fun cardioMinutes(store: GymStore, from: Long, to: Long): Double =
    store.sets.filter { it.isWork && it.time in from until to && store.groupOf(it.exercise).isCardio }.sumOf { it.reps.toDouble() }

fun goalProgress(store: GymStore, g: Goal): GoalProgress = when (g.type) {
    GoalType.STRENGTH, GoalType.ENDURANCE -> {
        val ex = g.exercise ?: ""
        val fmt: (Double) -> String = if (g.type == GoalType.STRENGTH) { v -> "${fmtKg(v)}" } else { v -> fmtAmount(ex, v, store) }
        val metric: (GymSet) -> Double = if (g.type == GoalType.STRENGTH) { s -> s.weight } else { s -> s.reps.toDouble() }
        val sets = store.sets.top().filter { it.exercise == ex && it.reps > 0 }
        val cur = sets.maxOfOrNull(metric) ?: 0.0
        val reached = sets.filter { it.time >= g.created && metric(it) >= g.target }.minOfOrNull { it.time }
        GoalProgress(
            // Anteil am Ziel (nicht ab Start): bereits Geschafftes zählt mit – motiviert mehr als ein leerer Balken
            fraction = if (reached != null) 1f else (cur / g.target).toFloat().coerceIn(0f, 1f),
            value = "${fmt(cur)} / ${fmt(g.target)}",
            detail = when {
                reached != null -> tr("Geschafft am ${fmtDate(reached)}", "Achieved on ${fmtDate(reached)}")
                cur > g.start && g.start > 0 -> tr("+${fmt(cur - g.start)} seit dem Start · noch ${fmt(g.target - cur)}",
                    "+${fmt(cur - g.start)} since you started · ${fmt(g.target - cur)} to go")
                else -> tr("Noch ${fmt(g.target - cur)}", "${fmt(g.target - cur)} to go")
            },
            reachedAt = reached
        )
    }
    GoalType.BODYWEIGHT -> {
        val lose = g.target < g.start
        val after = store.weights.filter { it.time >= g.created }
        val cur = store.weights.lastOrNull()?.kg ?: g.start
        val reached = after.firstOrNull { if (lose) it.kg <= g.target else it.kg >= g.target }?.time
        val done = if (lose) g.start - cur else cur - g.start
        GoalProgress(
            fraction = if (reached != null) 1f else way(g.start, cur, g.target),
            value = "${fmtKg(cur)}",
            detail = when {
                reached != null -> tr("Geschafft am ${fmtDate(reached)}", "Achieved on ${fmtDate(reached)}")
                done > 0 -> tr("${fmtKg(done)} geschafft · noch ${fmtKg(abs(cur - g.target))}",
                    "${fmtKg(done)} done · ${fmtKg(abs(cur - g.target))} to go")
                // noch kein Fortschritt: nur den Weg nennen, nicht „falsche Richtung“ vorhalten
                else -> tr("Start ${fmtKg(g.start)} · noch ${fmtKg(abs(cur - g.target))}", "Started at ${fmtKg(g.start)} · ${fmtKg(abs(cur - g.target))} to go")
            },
            reachedAt = reached
        )
    }
    GoalType.CARDIO_WEEK -> {
        val ws = startOfWeek()
        val now = cardioMinutes(store, ws, weekEnd(ws))
        // die letzten 4 abgeschlossenen Wochen (nur seit dem Anlegen) – zählt die geschafften, nicht die verpassten
        val weeks = generateSequence(previousWeek(ws)) { previousWeek(it) }.take(4).filter { weekEnd(it) > g.created }.toList()
        val hit = weeks.count { cardioMinutes(store, it, weekEnd(it)) >= g.target }
        GoalProgress(
            fraction = (now / g.target).toFloat().coerceIn(0f, 1f),
            value = tr("${fmtW(now)} / ${fmtW(g.target)} min diese Woche", "${fmtW(now)} / ${fmtW(g.target)} min this week"),
            detail = when {
                now >= g.target -> tr("Diese Woche geschafft 💪", "Done this week 💪")
                hit > 0 -> tr("In ${hit} der letzten ${weeks.size} Wochen geschafft", "Done in $hit of the last ${weeks.size} weeks")
                else -> tr("Noch ${fmtW(g.target - now)} min diese Woche", "${fmtW(g.target - now)} min to go this week")
            },
            reachedAt = null
        )
    }
    GoalType.REGULAR -> {
        // Wochen seit dem Anlegen, in denen das Wochenziel erreicht wurde; Zeitpunkt = Training, das die Woche vollgemacht hat
        val from = startOfWeek(g.created)
        val byWeek = allSessions(store).filter { it.start >= from }.groupBy { startOfWeek(it.start) }.toSortedMap()
        val doneAt = byWeek.values.mapNotNull { l -> l.sortedBy { it.start }.getOrNull(store.weeklyGoal - 1)?.start }
        val n = g.target.toInt()
        val reached = doneAt.getOrNull(n - 1)
        GoalProgress(
            fraction = (doneAt.size.toFloat() / n).coerceIn(0f, 1f),
            value = tr("${minOf(doneAt.size, n)} von $n Wochen", "${minOf(doneAt.size, n)} of $n weeks"),
            short = "${minOf(doneAt.size, n)}/$n",
            detail = if (reached != null) tr("Geschafft am ${fmtDate(reached)}", "Achieved on ${fmtDate(reached)}")
                else tr("Jede Woche mit ${store.weeklyGoal}× Training zählt", "Every week with ${store.weeklyGoal}× training counts"),
            reachedAt = reached
        )
    }
}

/** Schmaler Fortschrittsbalken (ohne Bibliothek). */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, done: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(if (done) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
        )
    }
}

/** Karte „Meine Ziele“ auf der Startseite: offene Ziele zuerst, geschaffte danach. Antippen = bearbeiten. */
@Composable
fun GoalsCard(store: GymStore) {
    var edit by remember { mutableStateOf<Goal?>(null) }
    var create by remember { mutableStateOf(false) }
    val items = store.goals.map { it to goalProgress(store, it) }.sortedBy { if (it.second.reachedAt != null) 1 else 0 }
    // Nächste Meilensteine (Milestones.kt) unten in derselben Karte – mit eigenen Zielen nur die 2 nächsten
    val miles = nextMilestones(store, if (items.isEmpty()) 3 else 2)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("🎯 Meine Ziele", "🎯 My goals"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { create = true }) { Text(tr("+ Ziel", "+ Goal")) }
            }
            if (items.isEmpty() && miles.isNotEmpty()) {
                // kurz halten – die Meilensteine darunter zeigen schon, was als Nächstes kommt
                Text(tr("Noch kein eigenes Ziel – z. B. $exBench Bankdrücken oder $exLose abnehmen.", "No goal of your own yet – e.g. $exBench bench press or lose $exLose."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp))
            } else if (items.isEmpty()) {
                Text(
                    tr("Setz dir ein langfristiges Ziel – z. B. $exBench Bankdrücken, 30 min laufen oder $exLose abnehmen. Hier siehst du dann, wie nah du dran bist.",
                        "Set yourself a long-term goal – e.g. $exBench bench press, run 30 min or lose $exLose. You'll see here how close you are."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            items.forEach { (g, p) -> GoalRow(store, g, p) { edit = g } }
            if (miles.isNotEmpty()) {
                Text(tr("🏁 Als Nächstes", "🏁 Up next"), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                MilestoneRows(miles)
            }
        }
    }

    if (create) GoalDialog(store, null) { create = false }
    edit?.let { g -> GoalDialog(store, g) { edit = null } }
}

/** Ein Ziel mit Balken (Startseite, Summary). onClick = null: nicht antippbar. */
@Composable
fun GoalRow(store: GymStore, g: Goal, p: GoalProgress = goalProgress(store, g), onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.reachedAt != null) "🏆" else g.type.emoji, modifier = Modifier.width(32.dp))
            Text(goalTitle(store, g), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(p.short,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(p.fraction, Modifier.padding(start = 32.dp), done = p.reachedAt != null)
        Spacer(Modifier.height(4.dp))
        Text(
            (if (p.reachedAt == null) p.value + " · " else "") + p.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp)
        )
    }
}

/** Übungen, die zum Zieltyp passen: Kraft = mit Gewicht, Ausdauer = Cardio oder Zeitübung. */
private fun fitsType(store: GymStore, type: GoalType, name: String): Boolean {
    val timeBased = store.groupOf(name).isCardio || isTimed(name)
    return if (type == GoalType.ENDURANCE) timeBased else !timeBased
}

/** Neues Ziel anlegen (goal = null: erst Typ wählen) bzw. bestehendes bearbeiten/löschen. */
@Composable
fun GoalDialog(store: GymStore, goal: Goal?, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(goal?.type) }
    var exercise by remember { mutableStateOf(goal?.exercise) }
    var query by remember { mutableStateOf("") }
    // Kraft- und Gewichtsziele werden in kg gespeichert, im Feld aber in der gewählten Einheit gezeigt
    val inWeight = { tt: GoalType? -> tt == GoalType.STRENGTH || tt == GoalType.BODYWEIGHT }
    var text by remember { mutableStateOf(goal?.target?.let { if (inWeight(goal.type)) fmtWU(it) else fmtW(it) } ?: "") }
    var steps by remember { mutableIntStateOf(goal?.target?.toInt() ?: 0) }
    val currentWeight = store.weights.lastOrNull()?.kg

    fun pickType(t: GoalType) {
        type = t; exercise = null; text = ""
        steps = when (t) { GoalType.CARDIO_WEEK -> 90; GoalType.REGULAR -> 12; else -> 0 }
    }

    val t = type
    val best = if (t != null && t.needsExercise) exercise?.let { bestValue(store, t, it) } else null
    val number = text.replace(',', '.').toDoubleOrNull()?.let { if (inWeight(t)) fromUnit(it) else it }
    // Fehlermeldung bzw. null = gültig
    val error: String? = when (t) {
        null -> ""
        GoalType.STRENGTH, GoalType.ENDURANCE -> when {
            exercise == null -> ""
            number == null || number <= 0 -> ""
            best != null && number <= best && (goal == null || exercise != goal.exercise) ->
                tr("Hast du schon geschafft – nimm ein höheres Ziel", "Already done – pick a higher goal")
            else -> null
        }
        GoalType.BODYWEIGHT -> when {
            currentWeight == null -> ""
            number == null || number !in 20.0..400.0 -> ""
            abs(number - currentWeight) < 0.05 -> tr("Das ist dein aktuelles Gewicht", "That's your current weight")
            else -> null
        }
        GoalType.CARDIO_WEEK, GoalType.REGULAR -> null
    }

    fun save() {
        val tt = type ?: return
        val target = if (tt == GoalType.CARDIO_WEEK || tt == GoalType.REGULAR) steps.toDouble() else number ?: return
        // Start nur beim Anlegen (bzw. bei geänderter Übung) festhalten – sonst wäre der bisherige Fortschritt weg
        val keepStart = goal != null && goal.type == tt && goal.exercise == exercise
        val start = when {
            keepStart -> goal!!.start
            tt.needsExercise -> best ?: 0.0
            tt == GoalType.BODYWEIGHT -> currentWeight ?: 0.0
            else -> 0.0
        }
        if (goal == null) store.addGoal(Goal(System.currentTimeMillis(), tt, exercise, target, start, System.currentTimeMillis()))
        else store.updateGoal(goal.copy(type = tt, exercise = exercise, target = target, start = start,
            created = if (keepStart) goal.created else System.currentTimeMillis()))
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) tr("Neues Ziel", "New goal") else tr("Ziel bearbeiten", "Edit goal")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    // Schritt 1: Art des Ziels
                    t == null -> GoalType.entries.forEach { gt ->
                        Surface(
                            onClick = { pickType(gt) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(gt.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp))
                                Column {
                                    Text(gt.label, style = MaterialTheme.typography.titleSmall)
                                    Text(gt.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    // Schritt 2a: Übung wählen
                    t.needsExercise && exercise == null -> {
                        Text("${t.emoji} ${t.label}", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            label = { Text(tr("Übung suchen", "Search exercise")) }, modifier = Modifier.fillMaxWidth()
                        )
                        val q = query.trim().lowercase()
                        val trained = store.sets.groupingBy { it.exercise }.eachCount()
                        val list = if (q.isEmpty() && trained.keys.any { fitsType(store, t, it) })
                            trained.entries.filter { fitsType(store, t, it.key) }.sortedByDescending { it.value }.map { it.key }.take(30)
                        else (CATALOG.map { it.name } + store.customNames).distinct()
                            .filter { fitsType(store, t, it) && (q.isEmpty() || q in exName(it).lowercase() || q in it.lowercase()) }
                            .sortedByName { exName(it) }
                        if (q.isEmpty() && trained.keys.any { fitsType(store, t, it) })
                            Text(tr("Deine häufigsten Übungen – oder oben suchen", "Your most frequent exercises – or search above"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(list, key = { it }) { name ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { exercise = name; text = "" }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ExerciseIcon(name, store.groupOf(name), Modifier.size(32.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(exName(name), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    // Schritt 2b: Zielwert
                    else -> {
                        Text("${t.emoji} ${t.label}", style = MaterialTheme.typography.titleSmall)
                        if (t.needsExercise) Row(verticalAlignment = Alignment.CenterVertically) {
                            val ex = exercise ?: ""
                            ExerciseIcon(ex, store.groupOf(ex), Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exName(ex), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    best?.let { tr("Bisher bestes: ", "Best so far: ") + if (t == GoalType.STRENGTH) "${fmtKg(it)}" else fmtAmount(ex, it, store) }
                                        ?: tr("Noch nicht gemacht", "Not done yet"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { exercise = null; query = "" }) { Text(tr("Ändern", "Change")) }
                        }
                        when (t) {
                            GoalType.STRENGTH, GoalType.ENDURANCE, GoalType.BODYWEIGHT -> {
                                if (t == GoalType.BODYWEIGHT) Text(
                                    currentWeight?.let { tr("Aktuell: ${fmtKg(it)}", "Current: ${fmtKg(it)}") }
                                        ?: tr("Trag zuerst im Tab „Körper“ dein Gewicht ein.", "First log your weight in the \"Body\" tab."),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (t != GoalType.BODYWEIGHT || currentWeight != null) {
                                    val unit = when {
                                        t == GoalType.ENDURANCE && store.groupOf(exercise ?: "").isCardio -> tr("Minuten", "minutes")
                                        t == GoalType.ENDURANCE -> tr("Sekunden", "seconds")
                                        else -> wUnit
                                    }
                                    OutlinedTextField(
                                        value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                                        singleLine = true, label = { Text(tr("Ziel in $unit", "Goal in $unit")) },
                                        isError = error != null && error.isNotEmpty(),
                                        supportingText = error?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            GoalType.CARDIO_WEEK -> {
                                GoalStepper(tr("$steps min pro Woche", "$steps min per week"), steps > 15, steps < 600,
                                    { steps -= 15 }, { steps += 15 })
                                Text(tr("Alle Cardio-Übungen zählen zusammen (Laufen, Rad, Rudern …).", "All cardio exercises count together (running, cycling, rowing …)."),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GoalType.REGULAR -> {
                                GoalStepper(tr("$steps Wochen", "$steps weeks"), steps > 4, steps < 52, { steps -= 4 }, { steps += 4 })
                                Text(tr("Zählt jede Woche, in der du dein Wochenziel (${store.weeklyGoal}× Training) schaffst – eine verpasste Woche macht nichts kaputt.",
                                    "Every week in which you hit your weekly goal (${store.weeklyGoal}× training) counts – a missed week doesn't break anything."),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (t != null && !(t.needsExercise && exercise == null))
                TextButton(onClick = { save() }, enabled = error == null) { Text(tr("Speichern", "Save")) }
        },
        dismissButton = {
            if (goal != null) TextButton(onClick = {
                val index = store.deleteGoal(goal)
                onDismiss()
                Undo.show(tr("Ziel gelöscht", "Goal deleted")) { store.restoreGoal(goal, index) }
            }) { Text(tr("Löschen", "Delete"), color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = {
                // „Zurück“ zum vorherigen Schritt, im ersten Schritt schließen
                when {
                    t == null -> onDismiss()
                    t.needsExercise && exercise != null -> { exercise = null; query = "" }
                    else -> type = null
                }
            }) { Text(if (t == null) tr("Abbrechen", "Cancel") else tr("Zurück", "Back")) }
        }
    )
}

@Composable
private fun GoalStepper(label: String, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDown, enabled = canDown) { Text("−", style = MaterialTheme.typography.headlineMedium) }
        Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        TextButton(onClick = onUp, enabled = canUp) { Text("+", style = MaterialTheme.typography.headlineMedium) }
    }
}

/** Beispielwerte in den Ziel-Texten passend zur Einheit (runde Werte statt umgerechneter). */
private val exBench: String get() = if (isLb) "225 lbs" else "100 kg"
private val exLose: String get() = if (isLb) "20 lbs" else "10 kg"
