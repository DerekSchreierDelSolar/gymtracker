package com.example.gymtracker

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------- Filter ----------

/**
 * Filter für Übungslisten. Leere Menge = kein Filter in dieser Dimension.
 * Innerhalb einer Dimension gilt ODER (Brust oder Rücken), zwischen den Dimensionen UND
 * (… und Kurzhantel).
 */
data class ExerciseFilter(
    val groups: Set<MuscleGroup> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
    /** Suchtext; leer = keine Suche */
    val query: String = ""
) {
    val isActive: Boolean get() = groups.isNotEmpty() || equipment.isNotEmpty() || query.isNotBlank()

    fun matches(name: String, group: MuscleGroup): Boolean =
        (groups.isEmpty() || group in groups) &&
            (equipment.isEmpty() || equipmentOf(name) in equipment) &&
            matchesQuery(name)

    /** Sucht im gespeicherten (deutschen) und im englischen Namen, Groß-/Kleinschreibung egal. */
    private fun matchesQuery(name: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val en = CATALOG.firstOrNull { it.name.equals(name, ignoreCase = true) }?.en ?: ""
        return name.contains(q, ignoreCase = true) || en.contains(q, ignoreCase = true)
    }
}

/** Element hinzufügen, falls nicht enthalten – sonst entfernen. */
fun <T> Set<T>.toggle(x: T): Set<T> = if (x in this) this - x else this + x

/** Speichert alles lokal als JSON-Datei im privaten App-Speicher. */

/**
 * Ob Suchleiste und Filter-Chips gerade aufgeklappt sind (pro Bildschirm).
 * Eine kleine Zustands-Klasse statt zwei loser Variablen, damit Kopfzeile (Buttons)
 * und Inhalt (Leisten) denselben Zustand teilen.
 */
class FilterUiState(searchOpen: Boolean) {
    var searchOpen by mutableStateOf(searchOpen)
    var filtersOpen by mutableStateOf(false)
}

/** Läuft schon eine Suche (z. B. nach Rückkehr aus einer Übung), startet die Suchleiste offen. */
@Composable
fun rememberFilterUi(filter: ExerciseFilter): FilterUiState =
    remember { FilterUiState(searchOpen = filter.query.isNotEmpty()) }

/**
 * Die zwei Buttons für die Kopfzeile: Lupe und Filter-Trichter.
 * Aufgeklappt = farbig hinterlegt. Am Trichter zeigt ein Badge die Zahl aktiver Filter.
 */
@Composable
fun FilterActions(ui: FilterUiState, filter: ExerciseFilter, onChange: (ExerciseFilter) -> Unit) {
    val tonal = IconButtonDefaults.filledTonalIconButtonColors()
    val plain = IconButtonDefaults.iconButtonColors()
    IconButton(
        onClick = {
            // Zuklappen leert die Suche – sonst würde unsichtbar weiter gefiltert
            if (ui.searchOpen) onChange(filter.copy(query = ""))
            ui.searchOpen = !ui.searchOpen
        },
        colors = if (ui.searchOpen) tonal else plain
    ) { AppIcon(Ico.SEARCH) }

    val count = filter.groups.size + filter.equipment.size
    IconButton(
        onClick = { ui.filtersOpen = !ui.filtersOpen },
        colors = if (ui.filtersOpen) tonal else plain
    ) {
        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
            AppIcon(Ico.FILTER)
        }
    }
}

/**
 * Aufklappbare Suchleiste und zwei waagerecht scrollbare Chip-Leisten (Kategorien, Geräte).
 * Es werden nur Werte angeboten, die in der jeweiligen Liste auch vorkommen.
 */
@Composable
fun FilterBar(
    filter: ExerciseFilter,
    onChange: (ExerciseFilter) -> Unit,
    groups: List<MuscleGroup>,
    equipment: List<Equipment>,
    ui: FilterUiState
) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth()) {
        // AnimatedVisibility klappt den Inhalt beim Ein-/Ausblenden weich auf bzw. zu
        AnimatedVisibility(visible = ui.searchOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            // Beim Aufklappen direkt ins Feld springen, damit die Tastatur erscheint
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { if (filter.query.isEmpty()) focusRequester.requestFocus() }
            // Suchleiste: rundes, gefülltes Textfeld ohne Unterstrich
            TextField(
                value = filter.query,
                onValueChange = { onChange(filter.copy(query = it)) },
                placeholder = { Text(tr("Übung suchen …", "Search exercises …")) },
                leadingIcon = { AppIcon(Ico.SEARCH, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    // ✕ leert die Suche und klappt die Leiste wieder zu
                    IconAction(Ico.CLOSE, onClick = {
                        onChange(filter.copy(query = ""))
                        ui.searchOpen = false
                        focus.clearFocus()
                    })
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                // Lupe auf der Tastatur schließt die Tastatur
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                    .focusRequester(focusRequester)
            )
        }
        AnimatedVisibility(visible = ui.filtersOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filter.groups.isNotEmpty() || filter.equipment.isNotEmpty()) {
                        item {
                            AssistChip(
                                // Setzt nur die Chips zurück, eine laufende Suche bleibt
                                onClick = { onChange(filter.copy(groups = emptySet(), equipment = emptySet())) },
                                label = { Text("✕ " + tr("Zurücksetzen", "Reset")) }
                            )
                        }
                    }
                    items(groups) { g ->
                        FilterChip(
                            selected = g in filter.groups,
                            onClick = { onChange(filter.copy(groups = filter.groups.toggle(g))) },
                            label = { Text(g.label) },
                            leadingIcon = { MuscleIcon(g, Modifier.size(18.dp)) }
                        )
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(equipment) { e ->
                        FilterChip(
                            selected = e in filter.equipment,
                            onClick = { onChange(filter.copy(equipment = filter.equipment.toggle(e))) },
                            label = { Text(e.label) }
                        )
                    }
                }
            }
        }
    }
}

/** Hinweis, wenn der Filter nichts übrig lässt. */
@Composable
fun NoFilterResults(onReset: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(tr("Keine Übungen für diesen Filter.", "No exercises match this filter."))
        TextButton(onClick = onReset) { Text(tr("Filter zurücksetzen", "Reset filter")) }
    }
}


