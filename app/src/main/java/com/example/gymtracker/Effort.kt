package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------- Anstrengung (RIR) + %-Tabelle zum 1RM (Roadmap Schritt 12) ----------
// RIR = „Reps in Reserve“: wie viele Wiederholungen wären noch gegangen? 0 = bis zum Versagen, 4 = „4+“.
// Freiwillig nach jedem Arbeitssatz abgefragt (Chips unter der Eingabe), änderbar im Satz-Dialog.
// Wirkt auf den 💡-Vorschlag (Progression.kt): ≥ 3 in Reserve → mehr Gewicht. Web: effort.js.

/** Kurztext für die Satz-Zeile: „RIR 2“ bzw. „RIR 4+“. */
fun rirLabel(rir: Int): String = "RIR " + if (rir >= 4) "4+" else rir.toString()

/** Chips 0 / 1 / 2 / 3 / 4+; `selected` hervorgehoben, erneut antippen = abwählen (null). */
@Composable
fun RirChips(selected: Int?, onPick: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..4).forEach { n ->
            SegmentChip(if (n == 4) "4+" else n.toString(), selected == n, { onPick(if (selected == n) null else n) }, Modifier.weight(1f))
        }
    }
}

/** Abfrage direkt nach dem Satz (unter der Eingabe); verschwindet nach Antippen oder ✕. */
@Composable
fun RirPrompt(onPick: (Int) -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Wie viele Wdh. wären noch gegangen?", "How many more reps could you have done?"),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onSkip) { Text("✕") }
            }
            Row(Modifier.padding(end = 8.dp)) { RirChips(null) { it?.let(onPick) } }
        }
    }
}

/** Übliche Wdh.-Zahl je Prozent vom 1RM (Faustregel). */
private val PERCENT_REPS = listOf(100 to "1", 95 to "2", 90 to "3–4", 85 to "5–6", 80 to "7–8", 75 to "9–10", 70 to "11–12", 65 to "13–15", 60 to "16–20")

/** Karte „Prozente vom 1RM“ im Reiter Fortschritt (Kraftübungen). `orm` = geschätztes 1RM. */
@Composable
fun OneRmTable(orm: Double) {
    if (orm <= 0) return
    Card(Modifier.fillMaxWidth(), colors = glassCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Prozente vom 1RM (${fmtKg(orm)})", "Percent of 1RM (${fmtKg(orm)})"), style = MaterialTheme.typography.titleSmall)
            Text(tr("Geschätzt aus deinem besten Satz – Wdh. als Faustregel.", "Estimated from your best set – reps as a rule of thumb."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            PERCENT_REPS.forEach { (p, reps) ->
                Row {
                    Text("$p\u00A0%", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${fmtKg(if (p == 100) orm else roundPlate(orm * p / 100))}", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(tr("$reps Wdh.", "$reps reps"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
