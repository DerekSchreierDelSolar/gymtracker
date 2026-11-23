package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------- Supersätze (Roadmap Schritt 13) ----------
// Workout.links = Übungen, die mit der NÄCHSTEN in der Liste verbunden sind; aufeinanderfolgende Verbindungen
// bilden eine Gruppe (2 = Supersatz, 3+ = Riesensatz). Im Training: nach einem Arbeitssatz direkt zur nächsten
// Übung der Gruppe, Pause erst nach der letzten, dann zurück zur ersten (solange noch Ziel-Sätze offen sind).
// Web: supersets.js (gleich).

/** Gruppen des Workouts (nur echte Supersätze, mind. 2 Übungen), in Listenreihenfolge. */
fun supersetGroups(w: Workout): List<List<String>> {
    val out = mutableListOf<List<String>>()
    var cur = mutableListOf<String>()
    for ((i, ex) in w.exercises.withIndex()) {
        cur += ex
        if (ex !in w.links || i == w.exercises.lastIndex) {
            if (cur.size >= 2) out += cur
            cur = mutableListOf()
        }
    }
    return out
}

/** Supersatz-Gruppe, zu der die Übung gehört (null = keine). */
fun supersetOf(w: Workout, name: String): List<String>? = supersetGroups(w).firstOrNull { name in it }

/** Verbindungen nach dem Verschieben einer Übung: die verschobene löst sich aus ihrer Gruppe, Nachbarn bleiben. */
fun movedLinks(w: Workout, from: Int, to: Int): Set<String> {
    val ex = w.exercises
    val moved = ex.getOrNull(from) ?: return w.links
    val out = w.links.toMutableSet()
    out -= moved
    // die Übung davor war mit der verschobenen verbunden → Verbindung lösen
    ex.getOrNull(from - 1)?.let { out -= it }
    // am Ziel: die Übung, die jetzt direkt davor steht, verbindet nicht automatisch mit der neuen
    val after = ex.toMutableList().apply { add(to, removeAt(from)) }
    after.getOrNull(to - 1)?.let { out -= it }
    return out
}

/**
 * Wohin nach einem Satz? Rückgabe: (nächste Übung oder null = bleiben, Pause starten?).
 * Nur für Arbeitssätze in einer Gruppe; sonst (null, true) wie bisher.
 */
fun supersetNext(store: GymStore, w: Workout, name: String, start: Long, warmup: Boolean): Pair<String?, Boolean> {
    val group = supersetOf(w, name) ?: return null to true
    if (warmup) return null to true
    val i = group.indexOf(name)
    if (i < group.lastIndex) return group[i + 1] to false
    // letzte der Gruppe: Pause; zurück zur ersten, solange irgendeine Übung ihr Ziel noch nicht hat (ohne Ziele: immer)
    val open = group.any { ex ->
        val target = store.targetFor(w, ex)
        target == 0 || store.setsBetween(ex, start, Long.MAX_VALUE).count { it.isWork } < target
    }
    return (if (open) group.first() else null) to true
}

/**
 * Gewählte Übungen (mind. 2) zu einer Gruppe verbinden: Sie rücken an den Platz der obersten gewählten zusammen
 * (Reihenfolge untereinander bleibt) und lösen sich aus bisherigen Gruppen. Andere Verbindungen bleiben,
 * solange die Übung danach dieselbe ist.
 */
fun groupedWorkout(w: Workout, selected: Set<String>): Workout {
    val sel = w.exercises.filter { it in selected }
    if (sel.size < 2) return w
    val rest = w.exercises.filter { it !in selected }
    val insertAt = w.exercises.take(w.exercises.indexOf(sel.first())).count { it !in selected }
    val order = rest.take(insertAt) + sel + rest.drop(insertAt)
    val oldNext = w.exercises.withIndex().associate { (i, e) -> e to w.exercises.getOrNull(i + 1) }
    val links = mutableSetOf<String>()
    for ((i, e) in order.withIndex()) {
        val next = order.getOrNull(i + 1) ?: continue
        if (e in w.links && e !in selected && next !in selected && oldNext[e] == next) links += e
    }
    links += sel.dropLast(1)
    return w.copy(exercises = order, links = links)
}

/** Farbe der Supersatz-Markierung. */
val SUPERSET_COLOR = Color(0xFFFF9800)

/** Kopfzeile über einer Gruppe in der Workout-Liste: „🔗 Supersatz“ bzw. „Riesensatz“ + Auflösen. */
@Composable
fun SupersetHeader(size: Int, onUnlink: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "🔗 " + (if (size >= 3) tr("Riesensatz", "Giant set") else tr("Supersatz", "Superset")) +
                tr(" · abwechselnd, Pause erst danach", " · alternate, rest afterwards"),
            style = MaterialTheme.typography.labelLarge,
            color = SUPERSET_COLOR,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onUnlink, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr("Auflösen", "Unlink")) }
    }
}

/** Hinweis auf der Trainingsseite: mit welchen Übungen diese im Supersatz ist. */
@Composable
fun SupersetHint(group: List<String>, name: String) {
    val others = group.filter { it != name }.joinToString(" + ") { exName(it) }
    Surface(shape = RoundedCornerShape(12.dp), color = SUPERSET_COLOR.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Text(
            tr("🔗 Supersatz mit $others – nach dem Satz geht's direkt weiter, Pause erst nach ",
                "🔗 Superset with $others – after the set you go straight on, rest only after ") + exName(group.last()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
