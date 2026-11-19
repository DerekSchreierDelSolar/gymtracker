package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ---------- Eigene Übung anlegen ----------

/**
 * Dialog: Name, Kategorie, Gerät und Messart (Wiederholungen oder Zeit).
 * FlowRow bricht die Chips automatisch in mehrere Zeilen um.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseDialog(store: GymStore, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(MuscleGroup.BRUST) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }
    var timed by remember { mutableStateOf(false) }
    val exists = name.isNotBlank() && (store.hasExercise(name) || catalogGroup(name.trim()) != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Eigene Übung", "Custom exercise")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Name", "Name")) },
                    singleLine = true,
                    isError = exists,
                    supportingText = if (exists) {
                        { Text(tr("Gibt es schon", "Already exists")) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(tr("Kategorie", "Category"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleGroup.entries.forEach { g ->
                        FilterChip(
                            selected = group == g,
                            onClick = { group = g },
                            label = { Text(g.label) },
                            leadingIcon = { MuscleIcon(g, Modifier.size(18.dp)) }
                        )
                    }
                }
                Text(tr("Gerät", "Equipment"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.entries.forEach { e ->
                        FilterChip(
                            selected = equipment == e,
                            // erneutes Tippen hebt die Auswahl auf (Gerät ist optional)
                            onClick = { equipment = if (equipment == e) null else e },
                            label = { Text(e.label) }
                        )
                    }
                }
                if (!group.isCardio) {
                    Text(tr("Messung", "Measured by"), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !timed, onClick = { timed = false },
                            label = { Text(tr("Wiederholungen", "Reps")) })
                        FilterChip(selected = timed, onClick = { timed = true },
                            label = { Text(tr("Zeit (Sekunden)", "Time (seconds)")) })
                    }
                } else {
                    Text(
                        tr("Cardio wird in Minuten erfasst.", "Cardio is logged in minutes."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (store.addCustomExercise(name, group, equipment, timed)) onDismiss() },
                enabled = name.isNotBlank() && !exists
            ) { Text(tr("Anlegen", "Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}


// ---------- Bildschirm 3: Übungskatalog ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    store: GymStore,
    filter: ExerciseFilter,
    onFilter: (ExerciseFilter) -> Unit,
    onBack: () -> Unit
) {
    val visible = CATALOG.filter { filter.matches(it.name, it.group) }
    // Eigene Übungen = gespeicherte Übungen, die nicht im Katalog stehen
    val customVisible = store.exercises
        .filter { catalogGroup(it) == null && filter.matches(it, store.groupOf(it)) }
    val ui = rememberFilterUi(filter)
    var showCustom by remember { mutableStateOf(false) }
    if (showCustom) CustomExerciseDialog(store, onDismiss = { showCustom = false })

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Übungskatalog", "Exercise catalog")) },
            navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
            actions = {
                IconAction(Ico.ADD, onClick = { showCustom = true })
                FilterActions(ui, filter, onFilter)
            }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            FilterBar(
                filter, onFilter,
                groups = MuscleGroup.entries.filter { g -> CATALOG.any { it.group == g } },
                equipment = Equipment.entries,
                ui = ui
            )
            if (visible.isEmpty() && customVisible.isEmpty()) {
                NoFilterResults(onReset = { onFilter(ExerciseFilter()) })
                return@Column
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        tr(
                            "Tippe auf eine Übung, um sie zu deiner Liste hinzuzufügen. Fehlt etwas? Oben mit + eine eigene Übung anlegen.",
                            "Tap an exercise to add it to your list. Missing one? Create your own with + at the top."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Eigene Übungen (nicht im Katalog) zuerst
                if (customVisible.isNotEmpty()) {
                    item(key = "custom_header") {
                        Text(
                            tr("Eigene Übungen", "Custom exercises"),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(customVisible, key = { "custom_$it" }) { name ->
                        val g = store.groupOf(name)
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ExerciseIcon(name, g, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name)
                                Text(
                                    listOfNotNull(
                                        g.label,
                                        equipmentOf(name)?.label,
                                        if (isTimed(name)) tr("Zeit", "Time") else null
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AppIcon(Ico.CHECK, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                visible.groupBy { it.group }.forEach { (group, list) ->
                    item(key = group.name) { GroupHeader(group) }
                    items(list, key = { it.name }) { entry ->
                        val added = store.hasExercise(entry.name)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !added) { store.addExercise(entry.name, entry.group) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseIcon(entry.name, group, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exName(entry.name))
                                Text(
                                    entry.equipment.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (added) "✓" else "+",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (added) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


