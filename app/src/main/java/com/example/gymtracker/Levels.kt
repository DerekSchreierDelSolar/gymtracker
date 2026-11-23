package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------- Level-System (Roadmap Schritt 6) ----------
// Punkte (XP) rückwirkend aus den vorhandenen Daten – nichts wird gespeichert, Import/Löschen rechnet automatisch neu.
// Level-Kurve: Level n → n+1 braucht 100 + 50·(n−1) XP (Level 2 bei 100, 3 bei 250, 4 bei 450 …).
// Web: levels.js (gleich).

object Xp {
    const val SESSION = 50
    const val SET = 5
    const val RECORD = 25
    const val WEEK_GOAL = 50
    const val GOAL = 200
}

/** Punkte bis einschließlich `until`, aufgeschlüsselt (für die Erklärung). */
data class XpBreakdown(val sessions: Int, val sets: Int, val records: Int, val weeks: Int, val goals: Int) {
    val total: Int get() = sessions * Xp.SESSION + sets * Xp.SET + records * Xp.RECORD + weeks * Xp.WEEK_GOAL + goals * Xp.GOAL
}

fun xpBreakdown(store: GymStore, until: Long = Long.MAX_VALUE): XpBreakdown = store.memo("xp" to until) {
    val sessions = allSessions(store).filter { it.start <= until }
    // Wochen mit erreichtem Wochenziel: das Training, das die Woche vollmacht, muss bis `until` stattgefunden haben
    val weeks = sessions.groupBy { startOfWeek(it.start) }.values.count { l -> l.size >= store.weeklyGoal }
    val goals = store.goals.count { g -> goalProgress(store, g).reachedAt?.let { it <= until } == true }
    XpBreakdown(
        sessions = sessions.size,
        sets = store.sets.count { it.isWork && it.time <= until },
        records = recordEvents(store).count { it.set.time <= until },
        weeks = weeks,
        goals = goals
    )
}

/** XP, die man für Level n → n+1 braucht. */
fun xpForNext(level: Int) = 100 + 50 * (level - 1)

/** Level zu einer Punktzahl: (Level, XP im aktuellen Level, XP bis zum nächsten). */
data class LevelInfo(val level: Int, val into: Int, val needed: Int) {
    val fraction: Float get() = into.toFloat() / needed
}

fun levelOf(xp: Int): LevelInfo {
    var level = 1
    var rest = xp
    while (rest >= xpForNext(level)) { rest -= xpForNext(level); level++ }
    return LevelInfo(level, rest, xpForNext(level))
}

/** Titel je Level-Stufe (Meerschweinchen-Thema). */
fun levelTitle(level: Int): String = when {
    level >= 50 -> tr("Gym-Legende", "Gym legend")
    level >= 40 -> tr("Muskel-Meerschwein", "Muscle guinea pig")
    level >= 30 -> tr("Eiserner Nager", "Iron rodent")
    level >= 20 -> tr("Kraftpaket", "Powerhouse")
    level >= 10 -> tr("Hantel-Held", "Dumbbell hero")
    level >= 5 -> tr("Trainingsschweinchen", "Gym piggy")
    else -> tr("Frischling", "Rookie")
}

/** Nächstes Level mit neuem Titel (null = schon der höchste). */
fun nextTitleLevel(level: Int): Int? = listOf(5, 10, 20, 30, 40, 50).firstOrNull { it > level }

/** Level-Aufstieg durch ein Training (für Achievements.kt): neues Level oder null. */
fun levelUpIn(store: GymStore, t: TrainingSession): Int? {
    val before = levelOf(xpBreakdown(store, t.start - 1).total).level
    val after = levelOf(xpBreakdown(store, t.end).total).level
    return after.takeIf { it > before }
}

/** Schmale Karte auf der Startseite: Level, Titel, Balken bis zum nächsten Level. Antippen = wofür gibt es Punkte? */
@Composable
fun LevelCard(store: GymStore) {
    val xp = xpBreakdown(store)
    if (xp.total == 0) return
    val info = levelOf(xp.total)
    var explain by remember { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { explain = true }
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Level ${info.level} · ${levelTitle(info.level)}", "Level ${info.level} · ${levelTitle(info.level)}"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tr("Noch ${info.needed - info.into} Punkte bis Level ${info.level + 1}", "${info.needed - info.into} points to level ${info.level + 1}"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${fmtKcal(xp.total)} XP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(info.fraction)
        }
    }
    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text(tr("Wofür gibt es Punkte?", "How do I earn points?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple(tr("Training", "Workout"), Xp.SESSION, xp.sessions),
                    Triple(tr("Satz (ohne Aufwärmen)", "Set (no warm-ups)"), Xp.SET, xp.sets),
                    Triple(tr("Rekord", "Record"), Xp.RECORD, xp.records),
                    Triple(tr("Woche mit Wochenziel", "Week with weekly goal"), Xp.WEEK_GOAL, xp.weeks),
                    Triple(tr("Erreichtes Ziel", "Goal reached"), Xp.GOAL, xp.goals)
                ).forEach { (label, per, n) ->
                    Row {
                        Text("$label · +$per", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("$n× = ${fmtKcal(per * n)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Text(tr("Jedes Level braucht 50 Punkte mehr als das vorige. Titel gibt es ab Level 5, 10, 20, 30, 40 und 50.",
                    "Each level needs 50 points more than the one before. New titles at levels 5, 10, 20, 30, 40 and 50."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { explain = false }) { Text("OK") } }
    )
}
