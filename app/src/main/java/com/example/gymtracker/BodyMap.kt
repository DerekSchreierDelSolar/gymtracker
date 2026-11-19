package com.example.gymtracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// ---------- Körperdiagramm: welche Muskeln eine Übung belastet ----------
//
// Die Grafik (ein Meerschweinchen, passend zum App-Icon) besteht aus SVG-Pfaden (Pfad-Syntax wie im <path d="…">-Attribut).
// Koordinaten: eine Figur ist 100 breit × 196 hoch, Mitte bei x = 50, Blick zum Betrachter.
// Die Rückansicht wird um BACK_OFFSET nach rechts verschoben gezeichnet.
// Fast alle Formen sind nur für die linke Bildhälfte (x < 50) angegeben und werden an der
// Mittellinie gespiegelt (mirror = true) – das spart die Hälfte der Daten und bleibt symmetrisch.
//
// GRAFIK AUSTAUSCHEN: nur BODY_SHAPES ersetzen. Jede Muskelfläche braucht eine der IDs aus
// MUSCLES; Körper-/Gesichtsteile haben id = null und eine BodyFill-Farbe. Die Web-Version (web/js/bodymap.js) übernimmt die
// Pfade automatisch per gen-data.mjs.

/** Muskel-IDs (fest, auch in den Wiki-Daten) mit Anzeigenamen. */
val MUSCLES: Map<String, WikiText> = linkedMapOf(
    "chest" to WikiText("Brust", "Chest"),
    "delts" to WikiText("Schultern", "Shoulders"),
    "traps" to WikiText("Trapez", "Traps"),
    "lats" to WikiText("Latissimus", "Lats"),
    "lowerback" to WikiText("Unterer Rücken", "Lower back"),
    "biceps" to WikiText("Bizeps", "Biceps"),
    "triceps" to WikiText("Trizeps", "Triceps"),
    "forearms" to WikiText("Unterarme", "Forearms"),
    "abs" to WikiText("Bauch", "Abs"),
    "obliques" to WikiText("Seitliche Bauchmuskeln", "Obliques"),
    "glutes" to WikiText("Gesäß", "Glutes"),
    "quads" to WikiText("Oberschenkel vorne", "Quads"),
    "hamstrings" to WikiText("Beinbeuger", "Hamstrings"),
    "adductors" to WikiText("Adduktoren", "Adductors"),
    "calves" to WikiText("Waden", "Calves")
)

fun muscleLabel(id: String): String = MUSCLES[id]?.text ?: id

/** Ansicht: vorne, hinten oder beide. */
enum class BodyView { FRONT, BACK, BOTH }

/** Füllung einer Fläche: Fell/Details des Meerschweinchens oder Muskel (Farbe je nach Belastung). */
enum class BodyFill { FUR, PATCH, PINK, DARK, LIGHT, MUSCLE }

/** Eine Fläche der Grafik. id = Muskel-ID (dann fill = MUSCLE) oder null für Körper/Gesicht. */
class BodyShape(
    val id: String?,
    val view: BodyView,
    val d: String,
    val mirror: Boolean = true,
    val fill: BodyFill = if (id != null) BodyFill.MUSCLE else BodyFill.FUR
)

const val BODY_WIDTH = 100f
const val BODY_HEIGHT = 196f
const val BACK_OFFSET = 108f

/*
 * Meerschweinchen im Fitness-Look (passend zum App-Icon): großer runder Kopf, Ohren,
 * runder Körper, kurze Arme und Füße. Reihenfolge = Zeichenreihenfolge (später liegt oben).
 * Alle Formen ohne id bekommen zusätzlich eine braune Außenkontur.
 */
val BODY_SHAPES: List<BodyShape> = listOf(
    // ----- Körper (vorne und hinten gleich) -----
    BodyShape(null, BodyView.BOTH, "M27 30 Q11 27 12 17 Q20 11 34 22 Z", fill = BodyFill.PATCH),            // Ohr (hängend, seitlich)
    BodyShape(null, BodyView.BOTH, "M30 172 L29 182 L46 182 L46 170 Z"),                                     // Unterschenkel
    BodyShape(null, BodyView.BOTH, "M25 178 Q22 188 31 190 L46 190 Q49 184 46 178 Z", fill = BodyFill.PINK), // Fuß
    BodyShape(null, BodyView.BOTH, "M27 66 Q17 68 14 80 L9 104 Q8 110 12 114 L19 114 Q21 106 23 98 L31 78 Z"), // Arm
    BodyShape(null, BodyView.BOTH, "M8 110 Q5 119 11 122 Q18 124 20 113 Z", fill = BodyFill.PINK),          // Pfote
    BodyShape(null, BodyView.BOTH, "M50 56 L38 58 Q27 62 24 76 Q19 98 19 122 Q19 152 27 166 Q36 176 50 176 Z"), // Körper
    BodyShape(null, BodyView.BOTH, "M50 16 C70 16 82 28 82 44 C82 60 68 71 50 71 C32 71 18 60 18 44 C18 28 30 16 50 16 Z", mirror = false), // Kopf
    BodyShape(null, BodyView.FRONT, "M25 27 Q15 25 15.5 19 Q21 15.5 30 22.5 Z", fill = BodyFill.PINK),       // Ohr innen

    // ----- Gesicht (vorne) -----
    BodyShape(null, BodyView.FRONT, "M46 17 C33 17 21 25 18.5 38 Q19.5 50 27 54 Q35 51 40 43 Q44 33 46 28 Z", fill = BodyFill.PATCH), // Fellzeichnung (heller Streifen über der Nase bleibt frei)
    BodyShape(null, BodyView.FRONT, "M35 35 C37.8 35 39.5 37.7 39.5 40.5 C39.5 43.3 37.8 46 35 46 C32.2 46 30.5 43.3 30.5 40.5 C30.5 37.7 32.2 35 35 35 Z", fill = BodyFill.DARK), // Auge
    BodyShape(null, BodyView.FRONT, "M36.4 36.8 A1.3 1.3 0 1 1 36.4 39.4 A1.3 1.3 0 1 1 36.4 36.8 Z", fill = BodyFill.LIGHT),   // Glanzpunkt
    BodyShape(null, BodyView.FRONT, "M27 58 Q30.5 54 35 57 Q32.5 61.5 27 58 Z", fill = BodyFill.PINK),            // Wange
    BodyShape(null, BodyView.FRONT, "M44.5 50 Q50 46 55.5 50 Q50 55 44.5 50 Z", fill = BodyFill.PINK, mirror = false), // Nase
    BodyShape(null, BodyView.FRONT, "M50 53 L50 57 M44.5 57.5 Q47.5 60.5 50 57 Q52.5 60.5 55.5 57.5 Q52.5 59 50 58 Q47.5 59 44.5 57.5 Z", fill = BodyFill.DARK, mirror = false),          // Mund

    // ----- Hinterkopf (hinten) -----
    BodyShape(null, BodyView.BACK, "M50 18 C34 18 21 28 20 43 Q22 56 32 62 Q42 66 50 65 Z", fill = BodyFill.PATCH),

    // ----- Muskeln vorne -----
    BodyShape("traps", BodyView.FRONT, "M38 63 Q31 64 28 67 L36 69 Q40 67 43 67 Z"),
    BodyShape("delts", BodyView.FRONT, "M27 67 Q18 69 15.5 80 Q20.5 82 25.5 80 Q28.5 74 31 70 Z"),
    BodyShape("chest", BodyView.FRONT, "M49 70 L36 70 Q29.5 73 29 80 Q32 88 40 89 Q46 89 49 86 Z"),
    BodyShape("biceps", BodyView.FRONT, "M25 82 Q19 83 16 90 L14 96 Q17.5 98 20.5 96 Q23.5 89 26 83 Z"),
    BodyShape("forearms", BodyView.FRONT, "M13.5 99 L10.5 107 Q11.5 111 15 111 L18.5 104 L19.5 99 Q16.5 97.5 13.5 99 Z"),
    BodyShape("abs", BodyView.FRONT, "M49 92 L42 92 Q40 112 41 132 Q44 142 49 144 Z"),
    BodyShape("obliques", BodyView.FRONT, "M40 93 Q32 96 28 106 Q26.5 120 30 132 Q35 136 39.5 134 Q38.5 112 40 93 Z"),
    BodyShape("quads", BodyView.FRONT, "M30 140 Q27 150 30 160 Q34 168 40 169 Q45 162 45 152 Q44 144 40 141 Z"),
    BodyShape("adductors", BodyView.FRONT, "M46 146 Q48.5 152 48.5 158 Q47.5 164 46 167 Q45 156 44 148 Z"),
    BodyShape("calves", BodyView.FRONT, "M31 172 L31 179 L35.5 179 L35.5 172 Z"),

    // ----- Muskeln hinten -----
    BodyShape("traps", BodyView.BACK, "M50 58 L43 59 Q34 61 29 66 Q38 69 44 77 L50 92 Z"),
    BodyShape("delts", BodyView.BACK, "M27 67 Q18 69 15.5 80 Q20.5 82 25.5 80 Q28.5 74 31 70 Z"),
    BodyShape("lats", BodyView.BACK, "M44 80 Q37 72 29.5 73 Q26 86 29 100 Q34 112 42 116 Q46 106 47 96 Z"),
    BodyShape("lowerback", BodyView.BACK, "M49 98 L46.5 100 Q43.5 110 43.5 120 Q46 126 49 127 Z"),
    BodyShape("obliques", BodyView.BACK, "M27.5 104 Q25.5 116 27.5 126 L31.5 126 Q30.5 116 30.5 106 Z"),
    BodyShape("triceps", BodyView.BACK, "M25 82 Q19 83 16 90 L14 96 Q17.5 98 20.5 96 Q23.5 89 26 83 Z"),
    BodyShape("forearms", BodyView.BACK, "M13.5 99 L10.5 107 Q11.5 111 15 111 L18.5 104 L19.5 99 Q16.5 97.5 13.5 99 Z"),
    BodyShape("glutes", BodyView.BACK, "M49 129 Q38 127 30 133 Q25 143 29 153 Q38 161 49 157 Z"),
    BodyShape("hamstrings", BodyView.BACK, "M29.5 156 Q30 163 33 168 L44 168 Q46.5 163 46.5 159 Q40 162 31 157 Z"),
    BodyShape("calves", BodyView.BACK, "M30.5 171 Q30 175 31.5 179 L44 179 Q45.5 175 45 171 Z")
)

/** Fertige Compose-Pfade (einmal berechnet), in Gesamtkoordinaten. */
private class BodyPath(val shape: BodyShape, val path: Path)

private val bodyPaths: List<BodyPath> by lazy {
    val mirror = Matrix().apply {
        this[0, 0] = -1f            // x → −x
        this[3, 0] = BODY_WIDTH     // … + 100  ⇒ Spiegelung an x = 50
    }
    buildList {
        for (s in BODY_SHAPES) {
            val base = PathParser().parsePathString(s.d).toPath()
            val variants = if (s.mirror) listOf(base, Path().apply { addPath(base); transform(mirror) }) else listOf(base)
            val offsets = when (s.view) {
                BodyView.FRONT -> listOf(0f)
                BodyView.BACK -> listOf(BACK_OFFSET)
                BodyView.BOTH -> listOf(0f, BACK_OFFSET)
            }
            for (dx in offsets) for (p in variants) {
                add(BodyPath(s, Path().apply { addPath(p, Offset(dx, 0f)) }))
            }
        }
    }
}

val PRIMARY_COLOR = Color(0xFFE24B4A)
val SECONDARY_COLOR = Color(0xFFEF9F27)

/**
 * Körperdiagramm (Vorder- und Rückansicht) mit eingefärbten Muskeln.
 * primary = Hauptmuskeln (rot), secondary = Hilfsmuskeln (orange), Rest grau.
 */
@Composable
fun MuscleMap(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier) {
    // Hell/dunkel am tatsächlichen Hintergrund ablesen (App-Einstellung kann vom System abweichen)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fur = if (dark) Color(0xFF5C4D41) else Color(0xFFF3E6D6)
    val patch = if (dark) Color(0xFF3B2F27) else Color(0xFF9C6B45)
    val outline = if (dark) Color(0xFF8F7A69) else Color(0xFF7A5A43)
    val pink = if (dark) Color(0xFFB98585) else Color(0xFFF2B8B5)
    val neutral = if (dark) Color(0xFF7A7068) else Color(0xFFCFC7BE)
    val totalWidth = BACK_OFFSET + BODY_WIDTH

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(totalWidth / BODY_HEIGHT)
        ) {
            val s = size.width / totalWidth
            scale(s, s, pivot = Offset.Zero) {
                // 1. Außenkontur: alle Körperteile dick umranden; die Füllungen danach überdecken
                //    die Linien im Inneren, übrig bleibt nur der Umriss.
                for (b in bodyPaths) if (b.shape.id == null) drawPath(b.path, outline, style = Stroke(width = 2.4f))
                // 2. Flächen
                for (b in bodyPaths) {
                    val color = when (b.shape.fill) {
                        BodyFill.FUR -> fur
                        BodyFill.PATCH -> patch
                        BodyFill.PINK -> pink
                        BodyFill.DARK -> Color(0xFF2B211B)
                        BodyFill.LIGHT -> Color.White
                        BodyFill.MUSCLE -> when (b.shape.id) {
                            in primary -> PRIMARY_COLOR
                            in secondary -> SECONDARY_COLOR
                            else -> neutral
                        }
                    }
                    drawPath(b.path, color)
                    // dünne Fuge zwischen benachbarten Muskeln
                    if (b.shape.fill == BodyFill.MUSCLE) drawPath(b.path, fur, style = Stroke(width = 0.7f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(0.72f).padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Text(tr("Vorne", "Front"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Hinten", "Back"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MuscleLegend(primary, secondary, Modifier.padding(top = 8.dp))
    }
}

/** „Haupt: Brust · Hilfs: Trizeps, Schultern“ mit Farbpunkten. */
@Composable
fun MuscleLegend(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (primary.isNotEmpty()) {
            LegendDot(PRIMARY_COLOR)
            Text(tr("Haupt: ", "Primary: ") + primary.joinToString(", ") { muscleLabel(it) }, style = MaterialTheme.typography.bodyMedium)
        }
        if (primary.isNotEmpty() && secondary.isNotEmpty()) Text("  ·  ", style = MaterialTheme.typography.bodyMedium)
        if (secondary.isNotEmpty()) {
            LegendDot(SECONDARY_COLOR)
            Text(tr("Hilfs: ", "Secondary: ") + secondary.joinToString(", ") { muscleLabel(it) }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LegendDot(color: Color) =
    Box(Modifier.padding(end = 6.dp).size(10.dp).background(color, CircleShape))
