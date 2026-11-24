package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ---------- Freies Training als gestartetes Training ----------
// Startet leer (trainingWorkoutId = FREE_WORKOUT_ID), Übungen kommen im Training über die Seite „＋ Übung“ dazu
// (FreeAddPage = letzte Seite im Pager). Gespeichert wird es beim Beenden wie jedes Training, mit workoutId = -1.
// Web: free.js

/** Workout des laufenden freien Trainings – nur im Speicher, nie in store.workouts. */
fun freeWorkout(exercises: List<String>) = Workout(FREE_WORKOUT_ID, tr("Freies Training", "Free workout"), exercises)

/** Freie Trainings (gestartet und gespeichert oder aus losen Sätzen eines Tages), neueste zuerst. */
fun freeSessions(store: GymStore): List<TrainingSession> =
    allSessions(store).filter { it.workoutId == FREE_WORKOUT_ID }.sortedByDescending { it.start }

/** Letztes freies Training vor `before` (für „Wie letztes Mal“). */
fun lastFreeSession(store: GymStore, before: Long = Long.MAX_VALUE): TrainingSession? =
    freeSessions(store).firstOrNull { it.start < before }

/** Alle Übungen, die man hinzufügen kann: Katalog + eigene. */
private fun allExerciseNames(store: GymStore): List<String> =
    CATALOG.map { it.name } + store.customNames.filter { n -> CATALOG.none { it.name.equals(n, ignoreCase = true) } }

/**
 * Letzte Seite im freien Training: Übungen hinzufügen. Oben das letzte freie Training (einzeln oder alle übernehmen),
 * dann Favoriten und zum Schluss Suche bzw. Kategorien. Schon hinzugefügte tragen ein ✓ (antippen = hinspringen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FreeAddPage(
    store: GymStore,
    start: Long,
    exercises: List<String>,
    /** Übungen hinzufügen und zur ersten davon springen */
    onAdd: (List<String>) -> Unit,
    onOpen: (String) -> Unit
) {
    val last = remember(start, store.revision) { lastFreeSession(store, start) }
    var query by rememberSaveable { mutableStateOf("") }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val inTraining = { n: String -> n in exercises }

    @Composable
    fun Header(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

    @Composable
    fun Chips(names: List<String>) = FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { n ->
            if (inTraining(n)) FilterChip(selected = true, onClick = { onOpen(n) }, label = { Text("✓ " + exName(n)) })
            else SuggestionChip(onClick = { onAdd(listOf(n)) }, label = { Text("＋ " + exName(n)) })
        }
    }

    @Composable
    fun ExerciseLine(n: String) {
        val g = store.groupOf(n)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { if (inTraining(n)) onOpen(n) else onAdd(listOf(n)) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExerciseIcon(n, g, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { NameWithStar(store, n) }
            Text(
                if (inTraining(n)) "✓" else "+",
                style = MaterialTheme.typography.titleLarge,
                color = if (inTraining(n)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(tr("Übung hinzufügen", "Add exercise"), style = MaterialTheme.typography.headlineSmall)
            if (exercises.isEmpty()) Text(
                tr("Freies Training: Wähle die erste Übung – weitere kommen jederzeit hier dazu.",
                    "Free workout: pick your first exercise – add more here any time."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Wie letztes Mal
        if (last != null) item {
            val names = last.exercises.distinct()
            Header(tr("Letztes freies Training · ", "Last free workout · ") + fmtDate(last.start))
            Chips(names)
            val missing = names.filterNot(inTraining)
            if (missing.size > 1) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = { onAdd(missing) }) {
                    AppIcon(Ico.ADD, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    val n = missing.size
                    Text(tr("Alle $n übernehmen", "Add all $n"))
                }
            }
        }
        val lastNames = last?.exercises.orEmpty().toSet()
        val favs = allExerciseNames(store).filter { store.isFavorite(it) && it !in lastNames }.sortedByName { exName(it) }
        if (favs.isNotEmpty()) item {
            Header("★ " + tr("Favoriten", "Favorites"))
            Chips(favs)
        }
        // Alle übrigen: Suche oder Kategorien
        item {
            Header(tr("Alle Übungen", "All exercises"))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Übung suchen …", "Search exercises …")) },
                leadingIcon = { AppIcon(Ico.SEARCH) },
                trailingIcon = if (query.isNotEmpty()) { { IconAction(Ico.CLOSE, onClick = { query = "" }) } } else null,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        val all = allExerciseNames(store)
        if (query.isNotBlank()) {
            val f = ExerciseFilter(query = query)
            val hits = all.filter { f.matches(it, store.groupOf(it)) }.sortedByName { exName(it) }.favoritesFirst(store) { it }.take(40)
            if (hits.isEmpty()) item { Text(tr("Keine Übung gefunden.", "No exercise found."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(hits, key = { "q_$it" }) { ExerciseLine(it) }
        } else {
            MuscleGroup.entries.forEach { g ->
                val names = all.filter { store.groupOf(it) == g }
                if (names.isEmpty()) return@forEach
                item(key = "g_${g.name}") {
                    CategoryRow(g, names.size, names.count(inTraining), onClick = { openGroup = if (openGroup == g.name) null else g.name })
                }
                if (openGroup == g.name) items(names.sortedByName { exName(it) }.favoritesFirst(store) { it }, key = { "e_$it" }) { ExerciseLine(it) }
            }
        }
    }
}

/**
 * „Als Workout speichern“ unter einem freien Training (SummaryScreen): legt ein Workout mit denselben Übungen an,
 * Ziel-Sätze = gemachte Arbeitssätze. Web: saveAsWorkoutButton in free.js.
 */
@Composable
fun SaveAsWorkoutButton(store: GymStore, t: TrainingSession, onOpenWorkout: (Long) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    // nur Übungen mit Satz, in der Reihenfolge des Trainings
    val sets = t.exercises.associateWith { e -> store.setsBetween(e, t.start, t.end).count { it.isWork } }
    val exercises = t.exercises.distinct().filter { (sets[it] ?: 0) > 0 }
    if (exercises.isEmpty()) return
    OutlinedButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) {
        AppIcon(Ico.LIST, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(tr("Als Workout speichern", "Save as workout"))
    }
    Text(
        tr("Gleiche Übungen, Ziel-Sätze wie heute – zum Wiederholen.", "Same exercises, target sets as today – to repeat it."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (asking) {
        var name by remember { mutableStateOf(tr("Freies Training", "Free workout") + " " + fmtDate(t.start)) }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(tr("Als Workout speichern", "Save as workout")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Name", "Name")) }, singleLine = true)
                    Text(
                        exercises.joinToString(" · ") { exName(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    asking = false
                    store.addWorkoutFrom(name, exercises, exercises.associateWith { sets[it] ?: 0 })?.let { onOpenWorkout(it.id) }
                }) { Text(tr("Speichern", "Save")) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(tr("Abbrechen", "Cancel")) } }
        )
    }
}
