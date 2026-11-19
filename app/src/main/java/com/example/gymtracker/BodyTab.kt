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

@Composable
fun BodyTab(store: GymStore, modifier: Modifier) {
    val focus = LocalFocusManager.current
    val last = store.weights.lastOrNull()
    var input by remember { mutableStateOf(last?.let { fmtW(it.kg) } ?: "") }

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

        // Eingabe
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(tr("Gewicht in kg", "Weight in kg")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val kg = input.replace(',', '.').toDoubleOrNull()
                    // Plausibilitätsprüfung gegen Tippfehler (z. B. 824 statt 82,4)
                    if (kg != null && kg in 20.0..400.0) {
                        store.addWeight(kg)
                        focus.clearFocus()
                    }
                }) { Text(tr("Eintragen", "Log")) }
            }
        }

        // Kalorien diese Woche
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
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

        // Gewichtsverlauf (letzte 30 Einträge)
        if (store.weights.size >= 2) {
            item {
                val shown = store.weights.takeLast(30)
                ChartCard(
                    tr("Gewichtsverlauf", "Weight trend"),
                    "${fmtW(shown.minOf { it.kg })} – ${fmtW(shown.maxOf { it.kg })} kg"
                ) {
                    LineChart(shown.map { it.kg }, "kg")
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtShortDate(shown.first().time), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(fmtShortDate(shown.last().time), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Liste der Einträge (neueste zuerst)
        if (store.weights.isNotEmpty()) {
            item { Text(tr("Einträge", "Entries"), style = MaterialTheme.typography.titleMedium) }
            items(store.weights.reversed().take(20), key = { it.time }) { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDate(w.time), modifier = Modifier.weight(1f))
                    Text("${fmtW(w.kg)} kg", style = MaterialTheme.typography.titleSmall)
                    IconAction(Ico.DELETE, onClick = { store.deleteWeight(w) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
}


