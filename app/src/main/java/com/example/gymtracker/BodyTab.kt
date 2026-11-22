package com.example.gymtracker

import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
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

    var showMet by remember { mutableStateOf(false) } // Erklärung der kcal-Schätzung (ⓘ)

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

        // Wozu das Gewicht dient (kcal-Schätzung). Der Wochen-Verbrauch selbst steht auf der Startseite;
        // die Erklärung der MET-Methode liegt hinter ⓘ statt als Dauertext unten.
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (last == null) tr(
                        "Trag dein Gewicht ein, dann schätzt die App deinen Kalorienverbrauch.",
                        "Log your weight and the app will estimate calories burned."
                    ) else tr(
                        "Dein Gewicht fließt in die kcal-Schätzung ein (Startseite: „Diese Woche“).",
                        "Your weight is used for the calorie estimate (home: \"This week\")."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconAction(Ico.INFO, onClick = { showMet = true }, tint = MaterialTheme.colorScheme.primary)
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
                    LineChart(shown.map { it.kg }, "kg", onTapPoint = { editEntry = shown[it]; Hints.used(Hints.WEIGHT) })
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtShortDate(shown.first().time), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (shown.size > 1) Text(fmtShortDate(shown.last().time), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (Hints.show(Hints.WEIGHT)) Text(
                        tr("Punkt antippen zum Anpassen oder Löschen", "Tap a point to edit or delete"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

    }

    if (showMet) AlertDialog(
        onDismissRequest = { showMet = false },
        title = { Text(tr("Wie wird der Verbrauch geschätzt?", "How are calories estimated?")) },
        text = {
            Text(tr(
                "Nach der MET-Methode: Körpergewicht × Dauer × Intensität der Übung. Cardio wird über die eingetragenen Minuten gerechnet, Krafttraining über die Trainingsdauer. Mit vollständigem Profil (Einstellungen: Geschlecht, Geburtsjahr, Größe) nimmt die App statt des Körpergewichts deinen Grundumsatz – das ist genauer. Es bleibt eine Schätzung.",
                "With the MET method: body weight × duration × exercise intensity. Cardio uses the logged minutes, strength training the workout duration. With a complete profile (settings: sex, birth year, height) the app uses your basal metabolic rate instead of body weight, which is more accurate. It is still an estimate."
            ))
        },
        confirmButton = { TextButton(onClick = { showMet = false }) { Text("OK") } }
    )

    editEntry?.let { e -> WeightEditDialog(store, e, onDismiss = { editEntry = null }) }

    // Datum fürs Nachtragen; heute = null
    if (pickDay) DayPickerDialog(
        initial = day ?: System.currentTimeMillis(),
        onPicked = { t -> day = t.takeIf { dayKey(it) != dayKey(System.currentTimeMillis()) } },
        onDismiss = { pickDay = false }
    )
}

/**
 * Tag wählen (Material-Kalender) in Sprache und Datumsformat der App (TT.MM.JJJJ bzw. TT/MM/JJJJ),
 * unabhängig von der Systemsprache. Zukunft ist nicht wählbar.
 * Der Kalender rechnet in UTC-Mitternacht → onPicked bekommt 12:00 Ortszeit des Tages (stimmt in jeder Zeitzone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(initial: Long, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    // Ortsdatum von initial als UTC-Mitternacht (so erwartet es der DatePicker)
    val local = Calendar.getInstance().apply { timeInMillis = initial }
    val initialUtc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    // Der DatePicker liest Sprache und Datumsformat aus LocalConfiguration → auf die App-Sprache setzen
    val base = LocalConfiguration.current
    val config = remember(base, appLanguage) { Configuration(base).apply { setLocale(appLocale()) } }
    CompositionLocalProvider(LocalConfiguration provides config) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialUtc,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        onPicked(Calendar.getInstance().apply {
                            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis)
                    }
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
        ) {
            // Titel und Kopfzeile selbst setzen: der Standard nimmt die Systemsprache („Nov 22, 2026“).
            // Ohne Stift-Umschalter, weil auch die Texteingabe das System-Datumsformat erwartet.
            val selectedLocal = state.selectedDateMillis?.let { utc ->
                val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                Calendar.getInstance().apply { set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0) }.timeInMillis
            }
            DatePicker(
                state,
                title = {
                    Text(tr("Datum wählen", "Select date"), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
                },
                headline = {
                    Text(selectedLocal?.let { fmtDate(it) } ?: "–", style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp))
                },
                showModeToggle = false
            )
        }
    }
}

/** Gewichtseintrag aus dem Diagramm: Gewicht und Datum anpassen oder löschen (mit „Rückgängig“). */
@Composable
private fun WeightEditDialog(store: GymStore, entry: WeightEntry, onDismiss: () -> Unit) {
    var text by remember(entry) { mutableStateOf(fmtW(entry.kg)) }
    var time by remember(entry) { mutableLongStateOf(entry.time) }
    var pickDay by remember { mutableStateOf(false) }
    val kg = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 } // wie beim Eintragen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Eintrag bearbeiten", "Edit entry")) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(tr("Gewicht in kg", "Weight in kg")) },
                    isError = kg == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                // Datum ändern (gleicher Kalender wie beim Nachtragen)
                TextButton(onClick = { pickDay = true }) {
                    AppIcon(Ico.CALENDAR, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(fmtDate(time))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = kg != null, onClick = {
                kg?.let { store.updateWeight(entry, it, time) }
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
    if (pickDay) DayPickerDialog(time, onPicked = { time = it }, onDismiss = { pickDay = false })
}
