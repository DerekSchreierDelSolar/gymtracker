package com.example.gymtracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.random.Random

// ---------- Meerschweinchen-Sprüche im Summary (Roadmap Schritt 9, variable Belohnung) ----------
// Passend zum Training ausgewählt; pro Training fest (Zufall mit der Trainings-ID als Startwert).
// Selten (~1 von 20) ein „besonderer“ Spruch – Spezialbilder dafür stehen im Backlog (docs/roadmap.md).
// Web: quotes.js (gleich).

enum class QuoteMood { RARE, COMEBACK, BIG, STREAK, EARLY, LATE, SHORT, NORMAL }

/** Spruch-Pool je Stimmung als [de, en]; `{n}` = Wochen in Folge. */
private val QUOTES: Map<QuoteMood, List<Pair<String, String>>> = mapOf(
    QuoteMood.BIG to listOf(
        "Quiek! Das war stark – ich hab vor Aufregung mein Heu fallen lassen." to "Squeak! That was strong – I dropped my hay in excitement.",
        "Neuer Rekord? Ich erzähl's allen Meerschweinchen im Käfig." to "A new record? I'm telling every guinea pig in the cage.",
        "Wer bist du und was hast du mit meinem Trainingspartner gemacht? 💪" to "Who are you and what have you done with my training buddy? 💪",
        "So viel Kraft – davon träume ich heute Nacht." to "So much power – I'll dream about it tonight.",
        "Ich würde dir applaudieren, aber meine Pfoten sind zu kurz." to "I'd applaud, but my paws are too short."
    ),
    QuoteMood.NORMAL to listOf(
        "Training erledigt. Jetzt hast du dir eine Gurke verdient. 🥒" to "Workout done. You've earned a cucumber. 🥒",
        "Gut gemacht! Ich hab in der Zeit … geschlafen. Aber ich bin stolz auf dich." to "Well done! I spent the time … sleeping. But I'm proud of you.",
        "Jeder Satz zählt – sagt auch meine Oma, und die ist sehr weise." to "Every set counts – my grandma says so too, and she's very wise.",
        "Muskeln wachsen beim Ausruhen. Ich trainiere das gerade sehr intensiv." to "Muscles grow while resting. I'm training that very hard right now.",
        "Du warst da. Das ist schon mehr als die meisten Meerschweinchen schaffen." to "You showed up. That's more than most guinea pigs manage.",
        "Stark wie ein Hamster. Nur besser." to "Strong like a hamster. Only better."
    ),
    QuoteMood.SHORT to listOf(
        "Kurz, aber gemacht – das zählt doppelt." to "Short, but done – that counts double.",
        "Besser ein kleines Training als ein großes Sofa." to "Better a small workout than a big sofa.",
        "Auch Meerschweinchen machen kleine Schritte. Ganz viele davon." to "Guinea pigs take small steps too. Lots of them."
    ),
    QuoteMood.COMEBACK to listOf(
        "Da bist du ja wieder! Ich hab dein Wasserschälchen frisch gemacht." to "There you are! I freshened up your water bowl.",
        "Willkommen zurück – die Hanteln haben dich vermisst. Ich auch." to "Welcome back – the dumbbells missed you. So did I."
    ),
    QuoteMood.STREAK to listOf(
        "{n} Wochen am Stück – du bist zuverlässiger als meine Futterzeiten." to "{n} weeks in a row – you're more reliable than my feeding times.",
        "Wochenziel geschafft! Den Rest der Woche darfst du Heu kauen." to "Weekly goal done! You may chew hay for the rest of the week."
    ),
    QuoteMood.EARLY to listOf("So früh schon wach? Ich schlaf noch ein bisschen weiter … 😴" to "Up this early? I'll sleep a little longer … 😴"),
    QuoteMood.LATE to listOf("Training um diese Uhrzeit? Respekt, Nachteule. 🦉" to "Working out at this hour? Respect, night owl. 🦉"),
    QuoteMood.RARE to listOf(
        "🌟 Goldene Gurke! Dieses Meerschweinchen zeigt sich nur echten Champions." to "🌟 Golden cucumber! This guinea pig only shows itself to true champions.",
        "🎩 Das vornehme Meerschweinchen verneigt sich vor deiner Leistung." to "🎩 The distinguished guinea pig bows to your performance."
    )
)

data class Quote(val mood: QuoteMood, val text: String)

/** Spruch fürs Training: selten besonders, sonst Comeback > großer Erfolg > Serie/Wochenziel > Uhrzeit > kurz > normal. */
fun quoteFor(store: GymStore, t: TrainingSession, wins: List<Achievement>, stats: SessionStats): Quote {
    val rnd = Random(t.id * 31 + t.start)
    val earlier = allSessions(store).filter { it.start < t.start }.maxByOrNull { it.start }
    val hour = Calendar.getInstance().apply { timeInMillis = t.start }.get(Calendar.HOUR_OF_DAY)
    val minutes = if (t.id >= 0) (t.end - t.start) / 60_000 else Long.MAX_VALUE
    val streak = wins.firstOrNull { it.kind == AchievementKind.STREAK }
    val mood = when {
        rnd.nextInt(20) == 0 -> QuoteMood.RARE
        earlier != null && t.start - earlier.start >= 14L * 86_400_000 -> QuoteMood.COMEBACK
        wins.any { it.big && it.kind != AchievementKind.WEEK_GOAL } -> QuoteMood.BIG
        streak != null || wins.any { it.kind == AchievementKind.WEEK_GOAL } -> QuoteMood.STREAK
        hour < 7 -> QuoteMood.EARLY
        hour >= 21 -> QuoteMood.LATE
        stats.sets <= 6 || minutes < 20 -> QuoteMood.SHORT
        else -> QuoteMood.NORMAL
    }
    val n = streakFrom(allSessions(store).filter { it.start <= t.end }.map { startOfWeek(it.start) }.toSet(), startOfWeek(t.start)).weeks
    // „{n} Wochen am Stück“ nur, wenn es wirklich eine Serie gibt
    val hasRecord = wins.any { it.kind == AchievementKind.RECORD }
    val pool = QUOTES.getValue(mood).filter {
        (mood != QuoteMood.STREAK || n >= 2 || "{n}" !in it.first) && (hasRecord || !it.first.startsWith("Neuer Rekord")) // Rekord-Spruch nur bei echtem Rekord
    }
    val (de, en) = pool[rnd.nextInt(pool.size)]
    return Quote(mood, tr(de, en).replace("{n}", n.toString()))
}

/** Meerschweinchen mit Sprechblase (Summary, unter den Erfolgen). */
@Composable
fun QuoteBubble(q: Quote) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Image(
                painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .graphicsLayer { scaleX = 1.45f; scaleY = 1.45f } // Rand des Adaptive-Icon-Vordergrunds abschneiden
            )
            if (q.mood == QuoteMood.RARE) Text("✨", modifier = Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f)
        ) {
            Text(q.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}
