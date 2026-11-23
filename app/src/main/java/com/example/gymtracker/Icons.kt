package com.example.gymtracker

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp

// ---------- App-Icons (selbst gezeichnet, ohne Icon-Bibliothek) ----------

enum class Ico { BACK, SEARCH, CLOSE, MORE, DELETE, EDIT, SETTINGS, PLAY, ADD, CALENDAR, DUMBBELL, LIST, CHECK, CHEVRON, FILTER, BODY, COPY, HOME, INFO, BOOK, CHAT, SEND, DRAG, UPLOAD, DOWNLOAD, SWAP, STAR, STAR_FILLED }

/**
 * Einfache Linien-Icons im 24×24-Raster (wie Material Symbols „outlined“).
 * Die Farbe kommt standardmäßig aus LocalContentColor – innerhalb von IconButton,
 * NavigationBar usw. passt sie sich dadurch automatisch an.
 */
@Composable
fun AppIcon(icon: Ico, modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val sw = 2f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun l(vararg xy: Float) {
            // Polylinie aus Koordinatenpaaren: x1, y1, x2, y2, …
            for (i in 0 until xy.size - 2 step 2) {
                drawLine(tint, p(xy[i], xy[i + 1]), p(xy[i + 2], xy[i + 3]), sw, StrokeCap.Round)
            }
        }
        fun dot(x: Float, y: Float, r: Float) = drawCircle(tint, r * s, p(x, y))
        fun ring(x: Float, y: Float, r: Float) = drawCircle(tint, r * s, p(x, y), style = Stroke(sw))

        when (icon) {
            Ico.BACK -> { l(20f, 12f, 4f, 12f); l(11f, 5f, 4f, 12f, 11f, 19f) }
            Ico.SEARCH -> { ring(10.5f, 10.5f, 6.5f); l(15.5f, 15.5f, 20f, 20f) }
            Ico.CLOSE -> { l(6f, 6f, 18f, 18f); l(18f, 6f, 6f, 18f) }
            Ico.MORE -> { dot(12f, 5f, 1.8f); dot(12f, 12f, 1.8f); dot(12f, 19f, 1.8f) }
            Ico.DELETE -> {
                l(4f, 7f, 20f, 7f); l(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                l(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f); l(10f, 11f, 10f, 16f); l(14f, 11f, 14f, 16f)
            }
            Ico.EDIT -> { l(4f, 20f, 5f, 15f, 15f, 5f, 19f, 9f, 9f, 19f, 4f, 20f); l(13f, 7f, 17f, 11f) }
            Ico.SETTINGS -> { // Schieberegler
                l(4f, 7f, 20f, 7f); l(4f, 17f, 20f, 17f)
                dot(9f, 7f, 2.6f); dot(15f, 17f, 2.6f)
            }
            Ico.PLAY -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(8f * s, 5f * s); lineTo(19f * s, 12f * s); lineTo(8f * s, 19f * s); close()
                }
                drawPath(path, tint)
            }
            Ico.ADD -> { l(12f, 5f, 12f, 19f); l(5f, 12f, 19f, 12f) }
            Ico.CALENDAR -> {
                drawRoundRect(
                    tint, p(4f, 5.5f), Size(16f * s, 14.5f * s), CornerRadius(2.5f * s), style = Stroke(sw)
                )
                l(4f, 10f, 20f, 10f); l(8f, 3f, 8f, 7f); l(16f, 3f, 16f, 7f)
            }
            Ico.DUMBBELL -> {
                l(8f, 12f, 16f, 12f)
                drawLine(tint, p(6.5f, 7.5f), p(6.5f, 16.5f), 3f * s, StrokeCap.Round)
                drawLine(tint, p(17.5f, 7.5f), p(17.5f, 16.5f), 3f * s, StrokeCap.Round)
                l(3f, 10f, 3f, 14f); l(21f, 10f, 21f, 14f)
            }
            Ico.LIST -> {
                dot(5f, 6f, 1.5f); dot(5f, 12f, 1.5f); dot(5f, 18f, 1.5f)
                l(9f, 6f, 20f, 6f); l(9f, 12f, 20f, 12f); l(9f, 18f, 20f, 18f)
            }
            Ico.CHECK -> l(5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Ico.CHEVRON -> l(9f, 5f, 16f, 12f, 9f, 19f)
            Ico.FILTER -> l(4f, 5f, 20f, 5f, 14f, 12.5f, 14f, 19f, 10f, 21f, 10f, 12.5f, 4f, 5f) // Trichter
            Ico.COPY -> { // zwei versetzte Blätter
                drawRoundRect(tint, p(8f, 8f), Size(12f * s, 12f * s), CornerRadius(2f * s), style = Stroke(sw))
                l(4f, 15f, 4f, 6f, 6f, 4f, 15f, 4f)
            }
            Ico.HOME -> { // Haus: Dach + Körper
                l(3f, 11f, 12f, 3.5f, 21f, 11f)
                l(5.5f, 9.5f, 5.5f, 20f, 18.5f, 20f, 18.5f, 9.5f)
                l(10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f)
            }
            Ico.INFO -> { // ⓘ: Kreis, Punkt, Strich
                ring(12f, 12f, 9f); dot(12f, 7.8f, 1.4f); l(12f, 11f, 12f, 16.5f)
            }
            Ico.BOOK -> { // aufgeschlagenes Buch
                l(12f, 6.5f, 12f, 19.5f)
                l(12f, 6.5f, 8f, 5f, 3f, 5f, 3f, 18f, 8f, 18f, 12f, 19.5f)
                l(12f, 6.5f, 16f, 5f, 21f, 5f, 21f, 18f, 16f, 18f, 12f, 19.5f)
            }
            Ico.CHAT -> { // Sprechblase mit Spitze unten links und drei Punkten
                l(5f, 5f, 19f, 5f, 21f, 7f, 21f, 15f, 19f, 17f, 10f, 17f, 6f, 20.5f, 6.5f, 17f, 5f, 17f, 3f, 15f, 3f, 7f, 5f, 5f)
                dot(8f, 11f, 1.2f); dot(12f, 11f, 1.2f); dot(16f, 11f, 1.2f)
            }
            Ico.SWAP -> { l(4f, 8f, 18f, 8f); l(14f, 4f, 18f, 8f, 14f, 12f); l(20f, 16f, 6f, 16f); l(10f, 12f, 6f, 16f, 10f, 20f) } // Tauschen: Pfeil rechts oben, links unten
            Ico.UPLOAD -> { l(4f, 15f, 4f, 20f, 20f, 20f, 20f, 15f); l(12f, 15f, 12f, 4f); l(7f, 9f, 12f, 4f, 17f, 9f) } // Export: Pfeil aus der Ablage
            Ico.DOWNLOAD -> { l(4f, 15f, 4f, 20f, 20f, 20f, 20f, 15f); l(12f, 4f, 12f, 15f); l(7f, 10f, 12f, 15f, 17f, 10f) } // Import: Pfeil in die Ablage
            Ico.DRAG -> { // Griff zum Ziehen: 2 × 3 Punkte
                for (y in listOf(6f, 12f, 18f)) { dot(9f, y, 1.7f); dot(15f, y, 1.7f) }
            }
            Ico.SEND -> { // Papierflieger
                l(3f, 11f, 21f, 3f, 14f, 21f, 11f, 13f, 3f, 11f)
                l(11f, 13f, 21f, 3f)
            }
            Ico.STAR, Ico.STAR_FILLED -> { // Fünfzack (Favorit), ausgefüllt = markiert
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 9.5f else 4.1f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = 12f + r * kotlin.math.cos(a).toFloat()
                    val y = 12.8f + r * kotlin.math.sin(a).toFloat()
                    if (i == 0) path.moveTo(x * s, y * s) else path.lineTo(x * s, y * s)
                }
                path.close()
                if (icon == Ico.STAR_FILLED) drawPath(path, tint)
                drawPath(path, tint, style = Stroke(sw, join = StrokeJoin.Round))
            }
            Ico.BODY -> { // Person: Kopf + Schultern (oberer Halbkreis)
                ring(12f, 7.5f, 3.5f)
                drawArc(tint, 180f, 180f, false, p(5f, 14f), Size(14f * s, 14f * s), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
    }
}

/** Runder Icon-Button mit einem App-Icon (für Top-Leisten). */
@Composable
fun IconAction(icon: Ico, onClick: () -> Unit, tint: Color = LocalContentColor.current) {
    IconButton(onClick = onClick) { AppIcon(icon, tint = tint) }
}

