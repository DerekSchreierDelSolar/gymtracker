package com.example.gymtracker

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ---------- Feier im Summary (Roadmap Schritt 3, Peak-End-Regel) ----------
// Erfolge aus Achievements.kt groß oben, Konfetti + Vibration bei großen Erfolgen (nur direkt nach dem Training).
// Web: celebration.js (gleich).

/**
 * Karte „Erfolge“: der wichtigste Erfolg groß, die nächsten darunter, Rest aufklappbar.
 * `fresh` = direkt nach dem Training (Überschrift „Stark!“ statt neutral).
 */
@Composable
fun AchievementsCard(list: List<Achievement>, fresh: Boolean) {
    if (list.isEmpty()) return
    var all by remember { mutableStateOf(false) }
    val top = list.first()
    val head = remember { headline(list) }
    val rest = list.drop(1)
    val shown = if (all) rest else rest.take(5)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (top.big) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            if (fresh) Text(
                head, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(top.emoji, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(top.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    top.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            shown.forEach { a ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Text(a.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        a.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (rest.size > 5) TextButton(onClick = { all = !all }) {
                Text(if (all) tr("Weniger anzeigen", "Show less") else tr("+${rest.size - 5} weitere", "+${rest.size - 5} more"))
            }
        }
    }
}

/** Kurze Überschrift je nach Anzahl großer Erfolge (zufällig aus wenigen Varianten, damit es nicht gleich klingt). */
private fun headline(list: List<Achievement>): String {
    val big = list.count { it.big }
    val pool = when {
        big >= 2 -> listOf(tr("Was für ein Training! 🔥", "What a workout! 🔥"), tr("Rekordverdächtig!", "Record-breaking!"))
        big == 1 -> listOf(tr("Stark gemacht!", "Well done!"), tr("Das hat sich gelohnt!", "That paid off!"))
        else -> listOf(tr("Gut gemacht!", "Nice work!"), tr("Wieder ein Schritt weiter", "Another step forward"), tr("Jedes Training zählt", "Every workout counts"))
    }
    return pool[Random.nextInt(pool.size)]
}

private data class Particle(
    val x: Float, val delay: Float, val speed: Float, val sway: Float, val phase: Float,
    val spin: Float, val w: Float, val h: Float, val color: Color
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFFFF5722), Color(0xFF7C83FD)
)

/**
 * Konfetti, das einmal von oben durch den Bildschirm fällt (ca. 3 s), ohne Bibliothek: Canvas + Frame-Uhr.
 * Fängt keine Berührungen ab. `onEnd` wird nach dem Durchlauf aufgerufen.
 */
@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier, onEnd: () -> Unit) {
    val particles = remember {
        List(110) {
            Particle(
                x = Random.nextFloat(), delay = Random.nextFloat() * 0.6f, speed = 0.35f + Random.nextFloat() * 0.35f,
                sway = 0.01f + Random.nextFloat() * 0.03f, phase = Random.nextFloat() * 2 * PI.toFloat(),
                spin = (Random.nextFloat() - 0.5f) * 720f, w = 6f + Random.nextFloat() * 6f, h = 10f + Random.nextFloat() * 8f,
                color = CONFETTI_COLORS[Random.nextInt(CONFETTI_COLORS.size)]
            )
        }
    }
    var time by remember { mutableFloatStateOf(0f) } // Sekunden seit Start
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < 3.2f) time = (withFrameNanos { it } - start) / 1e9f
        onEnd()
    }
    Canvas(modifier.fillMaxSize()) {
        val density = this.density
        particles.forEach { p ->
            val t = time - p.delay
            if (t <= 0f) return@forEach
            // fällt von knapp über dem oberen Rand nach unten, pendelt seitlich, dreht sich; am Ende ausblenden
            val y = (-0.05f + t * p.speed) * size.height
            val x = (p.x + sin(p.phase + t * 4f) * p.sway) * size.width
            val alpha = (1f - ((time - 2.4f) / 0.8f)).coerceIn(0f, 1f)
            if (y > size.height || alpha <= 0f) return@forEach
            rotate(p.spin * t, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - p.w * density / 2, y - p.h * density / 2), Size(p.w * density, p.h * density))
            }
        }
    }
}

/** Kurzes Jubel-Vibrieren (dreimal kurz, einmal länger). */
fun celebrateVibration(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator == null || !vibrator.hasVibrator()) return
    val pattern = longArrayOf(0, 40, 70, 40, 70, 40, 90, 160)
    if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }
}
