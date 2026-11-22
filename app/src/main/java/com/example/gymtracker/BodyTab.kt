package com.example.gymtracker

import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// ---------- Tab „Körper“: Gewicht & Kalorienverbrauch ----------

/** Montag 00:00 der Woche, in der `t` liegt (Standard: jetzt). */
fun startOfWeek(t: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    // Tage seit Montag (Calendar: Sonntag = 1, Montag = 2 …)
    val sinceMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    c.add(Calendar.DAY_OF_MONTH, -sinceMonday)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyTab(store: GymStore, modifier: Modifier) {
    val focus = LocalFocusManager.current
    val last = store.weights.lastOrNull()
    var input by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<Long?>(null) }   // gewählter Tag fürs Nachtragen (null = heute)
    var pickDay by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<WeightEntry?>(null) } // im Diagramm angetippter Eintrag

    // Verbrauch dieser Woche über alle Trainings (inkl. freier Trainings)
    val weekSessions = allSessions(store).filter { it.start >= startOfWeek() }
    val weekKcal = weekSessions.mapNotNull { sessionStats(store, it).kcal }.sum()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Aktuelles Gewicht groß
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        tr("Aktuelles Gewicht", "Current weight"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        last?.let { "${fmtW(it.kg)} kg" } ?: "–",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    val first = store.weights.firstOrNull()
                    if (first != null && last != null && store.weights.size >= 2) {
                        val diff = last.kg - first.kg
                        Text(
                            (if (diff >= 0) "+" else "−") + "${fmtW(kotlin.math.abs(diff))} kg " +
                                tr("seit ", "since ") + fmtDate(first.time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Eingabe: Feld leer (letzter Wert nur als Platzhalter → kein versehentlicher Doppel-Eintrag),
        // darunter das Datum (Standard heute, antippen = vergessenen Tag nachtragen)
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(tr("Gewicht in kg", "Weight in kg")) },
                        placeholder = { last?.let { Text(fmtW(it.kg)) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val kg = input.replace(',', '.').toDoubleOrNull()
                        // Plausibilitätsprüfung gegen Tippfehler (z. B. 824 statt 82,4)
                        if (kg != null && kg in 20.0..400.0) {
                            store.addWeight(kg, day ?: System.currentTimeMillis())
                            input = ""
                            day = null
                            focus.clearFocus()
                        }
                    }) { Text(tr("Eintragen", "Log")) }
                }
                TextButton(onClick = { pickDay = true }) {
                    AppIcon(Ico.CALENDAR, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(day?.let { tr("Für ", "For ") + fmtDate(it) } ?: tr("Für heute · anderes Datum wählen", "For today · pick another date"))
                }
            }
        }

        // Kalorien diese Woche (dezent, damit das Gewicht im Vordergrund bleibt)
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tr("Diese Woche", "This week"), style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (last == null) tr(
                                "Trag dein Gewicht ein, dann schätzt die App deinen Kalorienverbrauch.",
                                "Log your weight and the app will estimate calories burned."
                            ) else tr(
                                "~$weekKcal kcal verbrannt · ${plural(weekSessions.size, "Training", "Trainings")}",
                                "~$weekKcal kcal burned · ${plural(weekSessions.size, "workout", "workouts")}"
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Gewichtsverlauf (letzte 30 Einträge); Punkt antippen = Eintrag anpassen oder löschen (statt einer Liste)
        if (store.weights.isNotEmpty()) {
            item {
                val shown = store.weights.takeLast(30)
                ChartCard(
                    tr("Gewichtsverlauf", "Weight trend"),
                    "${fmtW(shown.minOf { it.kg })} – ${fmtW(shown.maxOf { it.kg })} kg"
                ) {
                    LineChart(shown.map { it.kg }, "kg", onTapPoint = { editEntry = shown[it] })
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtShortDate(shown.first().time), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (shown.size > 1) Text(fmtShortDate(shown.last().time), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        tr("Punkt antippen zum Anpassen oder Löschen", "Tap a point to edit or delete"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item {
            Text(
                tr(
                    "Der Verbrauch ist eine Schätzung nach der MET-Methode (Körpergewicht × Dauer × Intensität der Übung). Cardio wird über die eingetragenen Minuten gerechnet, Krafttraining über die Trainingsdauer.",
                    "Calories are estimated with the MET method (body weight × duration × exercise intensity). Cardio uses the logged minutes, strength training the workout duration."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    editEntry?.let { e -> WeightEditDialog(store, e, onDismiss = { editEntry = null }) }

    // Datum wählen (Material-Kalender). Er liefert Mitternacht in UTC → als 12:00 Ortszeit speichern,
    // damit der Tag in jeder Zeitzone stimmt. Zukunft ist nicht wählbar.
    if (pickDay) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        day = Calendar.getInstance().apply {
                            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis.takeIf { dayKey(it) != dayKey(System.currentTimeMillis()) } // heute = null
                    }
                    pickDay = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text(tr("Abbrechen", "Cancel")) } }
        ) { DatePicker(state) }
    }
}



/** Gewichtseintrag aus dem Diagramm: Gewicht anpassen oder löschen (mit „Rückgängig“). */
@Composable
private fun WeightEditDialog(store: GymStore, entry: WeightEntry, onDismiss: () -> Unit) {
    var text by remember(entry) { mutableStateOf(fmtW(entry.kg)) }
    val kg = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 } // wie beim Eintragen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fmtDate(entry.time)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(tr("Gewicht in kg", "Weight in kg")) },
                isError = kg == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = kg != null, onClick = {
                kg?.let { store.updateWeight(entry, it) }
                onDismiss()
            }) { Text(tr("Speichern", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = {
                store.deleteWeight(entry)
                Undo.show(tr("Eintrag gelöscht", "Entry deleted")) { store.restoreWeight(entry) }
                onDismiss()
            }) { Text(tr("Löschen", "Delete"), color = MaterialTheme.colorScheme.error) }
        }
    )
}
