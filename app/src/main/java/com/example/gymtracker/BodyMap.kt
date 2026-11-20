package com.example.gymtracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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

/**
 * Füllung einer Fläche: Fell/Details des Meerschweinchens, Muskel (Farbe je nach Belastung),
 * TISSUE = Rumpf/Arme/Beine als Muskelgewebe (Untergrund zwischen den Muskeln),
 * DECO = zusätzlicher Muskel nur für die Optik (immer neutral, nie eingefärbt).
 */
enum class BodyFill { FUR, PATCH, PINK, DARK, LIGHT, MUSCLE, TISSUE, DECO }

/**
 * Eine Fläche der Grafik. id = Muskel-ID (dann fill = MUSCLE) oder null für Körper/Gesicht/DECO.
 * fiber = Faserrichtung in Grad gegen die Senkrechte (linke Hälfte; die gespiegelte Hälfte bekommt −fiber).
 * detail = anatomische Fugen innerhalb des Muskels, durch Komma getrennt je „u0 v0 uc vc u1 v1“:
 *   Kurve von (u0,v0) über den Kontrollpunkt (uc,vc) nach (u1,v1), in 0..1 des Begrenzungsrahmens
 *   (u = 0 außen, 1 Körpermitte; v = 0 oben).
 */
class BodyShape(
    val id: String?,
    val view: BodyView,
    val d: String,
    val mirror: Boolean = true,
    val fill: BodyFill = if (id != null) BodyFill.MUSCLE else BodyFill.FUR,
    val fiber: Float = 0f,
    val detail: String = ""
) {
    /** Punkte des Pfads (inkl. Kontrollpunkte) – reicht für Begrenzungsrahmen und Lage-Tests. */
    val points: List<Offset> by lazy {
        val n = Regex("-?\\d+(\\.\\d+)?").findAll(d).map { it.value.toFloat() }.toList()
        (0 until n.size / 2).map { Offset(n[2 * it], n[2 * it + 1]) }
    }
    /** Liegt die Form ganz im Kopf (Kreis um 50/30)? Flecken dort übernehmen die Schattierung des Kopfes. */
    val inHead: Boolean by lazy { points.all { (it - Offset(50f, 30f)).getDistance() <= 24.5f } }
    val isMuscle: Boolean get() = fill == BodyFill.MUSCLE || fill == BodyFill.DECO
    /** Körperteil mit brauner Außenkontur (alles außer Muskeln) */
    val isBody: Boolean get() = !isMuscle
}


const val BODY_WIDTH = 100f
const val BODY_HEIGHT = 196f
const val BACK_OFFSET = 108f
/** Rand links/rechts um jede Figur (für Formen, die über 0..100 hinausragen). */
const val BODY_PAD = 0f

/*
 * Muskulöses Meerschweinchen (passend zum App-Icon): Meerschweinchen-Kopf mit Hängeohren,
 * menschenähnlicher Körper in V-Form. Ein Muskel darf aus mehreren Flächen mit derselben ID bestehen
 * (z. B. Sixpack). Reihenfolge = Zeichenreihenfolge (später liegt oben).
 * Alle Formen ohne id bekommen zusätzlich eine braune Außenkontur.
 */
val BODY_SHAPES: List<BodyShape> = listOf(
    // ----- Körper (vorne und hinten gleich) -----
    BodyShape(null, BodyView.BOTH, "M30 18 Q16 12 15 22 Q17 30 28 28 Z", fill = BodyFill.PATCH),             // Ohr (hängend, seitlich)
    BodyShape(null, BodyView.BOTH, "M13 70 Q8 78 9 90 L10 100 Q7 110 8 122 L15 124 Q17 112 19 102 Q23 92 23 82 Q22 74 18 70 Z", fill = BodyFill.TISSUE), // Arm
    BodyShape(null, BodyView.BOTH, "M7 120 Q4 129 9 133 Q15 135 16 124 Z", fill = BodyFill.PINK),            // Pfote
    BodyShape(null, BodyView.BOTH, "M33 128 Q27 140 28 152 Q29 158 31 162 Q28 170 30 178 L34 186 L45 186 Q46 176 46 166 Q47 160 47 154 Q49 144 49 136 L50 132 Z", fill = BodyFill.TISSUE), // Bein
    BodyShape(null, BodyView.BOTH, "M33 184 Q28 192 34 194 L47 194 Q49 189 46 184 Z", fill = BodyFill.PINK),  // Fuß
    BodyShape(null, BodyView.BOTH, "M50 46 L42 48 Q34 55 24 58 Q14 60 11 68 Q10 76 15 80 L22 82 Q25 96 31 108 Q33 116 31 124 Q30 130 34 134 L50 136 Z", fill = BodyFill.TISSUE), // Rumpf (V-Form)
    BodyShape(null, BodyView.BOTH, "M50 8 C63 8 71 18 71 30 C71 43 62 52 50 52 C38 52 29 43 29 30 C29 18 37 8 50 8 Z", mirror = false), // Kopf
    BodyShape(null, BodyView.FRONT, "M28 25 Q19 25 18 21 Q20 16 28 20 Z", fill = BodyFill.PINK),             // Ohr innen

    // ----- Gesicht (vorne) -----
    BodyShape(null, BodyView.FRONT, "M47.4 8.8 C38.8 8.8 30.9 15.2 29.2 25.6 Q29.9 35.2 34.8 38.4 Q40.1 36 43.4 29.6 Q46 21.6 47.4 17.6 Z", fill = BodyFill.PATCH), // Fellzeichnung (heller Streifen über der Nase bleibt frei)
    BodyShape(null, BodyView.FRONT, "M40.1 23.2 C42 23.2 43.1 25.3 43.1 27.6 C43.1 29.9 42 32 40.1 32 C38.2 32 37.1 29.9 37.1 27.6 C37.1 25.3 38.2 23.2 40.1 23.2 Z", fill = BodyFill.DARK), // Auge
    BodyShape(null, BodyView.FRONT, "M41 24.6 A0.9 0.9 0 1 1 41 26.4 A0.9 0.9 0 1 1 41 24.6 Z", fill = BodyFill.LIGHT),          // Glanzpunkt
    BodyShape(null, BodyView.FRONT, "M34.8 41.6 Q37.1 38.4 40.1 40.8 Q38.4 44.4 34.8 41.6 Z", fill = BodyFill.PINK),           // Wange
    BodyShape(null, BodyView.FRONT, "M46.4 35.2 Q50 32 53.6 35.2 Q50 39.2 46.4 35.2 Z", fill = BodyFill.PINK, mirror = false), // Nase
    BodyShape(null, BodyView.FRONT, "M46.4 41.2 Q48.3 43.6 50 40.8 Q51.7 43.6 53.6 41.2 Q51.7 42.4 50 41.6 Q48.3 42.4 46.4 41.2 Z", fill = BodyFill.DARK, mirror = false), // Mund

    // ----- Hinterkopf (hinten) -----
    BodyShape(null, BodyView.BACK, "M50 9 C40 9 30 17 29.5 29 Q31 42 38 48 Q44 51 50 51 Z", fill = BodyFill.PATCH),

    // ----- Zusätzliche Muskeln nur für die Optik (DECO: nie eingefärbt, füllen die Flächen zwischen den Muskeln) -----
    BodyShape(null, BodyView.FRONT, "M39 47 Q43 52 47.5 58.5 L50 58.5 L50 48 Q44 48.5 39 47 Z", fill = BodyFill.DECO, fiber = 25f), // Halsmuskel
    BodyShape(null, BodyView.FRONT, "M49 82.5 Q45.5 83.5 43 85 L43 87 L49 86.5 Z", fill = BodyFill.DECO, fiber = 0f), // oberer Bauchmuskel
    BodyShape(null, BodyView.FRONT, "M23.5 79 Q22.5 88 27 98 Q29.5 92 30 86 Q28.5 82 25 79.5 Z", fill = BodyFill.DECO, fiber = -55f), // Sägezahnmuskel
    BodyShape(null, BodyView.FRONT, "M32.5 116 Q30.5 124 32 131.5 L37.5 133 Q40.5 126 41 119.5 Q36.5 120 32.5 116 Z", fill = BodyFill.DECO, fiber = 30f), // Hüftmuskel
    BodyShape(null, BodyView.FRONT, "M22.8 84 Q23.2 92 19.5 102 L16 101 Q20.5 95 22.8 84 Z", fill = BodyFill.DECO, fiber = -10f), // Armbeuger
    BodyShape(null, BodyView.FRONT, "M18.5 103.5 Q15.5 112 13.5 121.5 L15.3 124 Q17.3 112 19.3 102.5 Z", fill = BodyFill.DECO, fiber = 15f), // Unterarm-Beuger
    BodyShape(null, BodyView.FRONT, "M36.5 160 Q40 157.5 43.5 160 Q43.5 164 40 164.5 Q36.5 164 36.5 160 Z", fill = BodyFill.DECO, fiber = 0f), // Kniescheibe
    BodyShape(null, BodyView.FRONT, "M34.5 165 Q36.5 174 35.5 184.5 L40.5 184.5 Q41.5 174 39.5 164.5 Z", fill = BodyFill.DECO, fiber = 4f), // Schienbeinmuskel
    BodyShape(null, BodyView.FRONT, "M40.5 164.5 Q42.5 174 41.5 184.5 L45 185 Q46 176 46 166 Q44 163.5 40.5 164.5 Z", fill = BodyFill.DECO, fiber = -4f), // Schienbein innen
    BodyShape(null, BodyView.FRONT, "M45.5 148 Q48.5 152 48 158.5 L45.5 158.5 Q44.5 153 45.5 148 Z", fill = BodyFill.DECO, fiber = -8f), // Oberschenkel innen
    BodyShape(null, BodyView.BACK, "M29 60 Q25.5 63 26 66.5 Q35 64 43 72 Q41 63 35 59 Z", fill = BodyFill.DECO, fiber = -60f), // Untergrätenmuskel
    BodyShape(null, BodyView.BACK, "M37 107 Q41 112 43 116 L43.5 121.5 Q38 120.5 33.5 119 Q37 114 37 107 Z", fill = BodyFill.DECO, fiber = 20f), // seitlicher Rückenmuskel
    BodyShape(null, BodyView.BACK, "M33 177 Q36 181 37.5 186 L41.5 186 Q42 181 44.5 176.5 Q38.5 179 33 177 Z", fill = BodyFill.DECO, fiber = 0f), // Achillessehne

    // ----- Muskeln vorne -----
    BodyShape("traps", BodyView.FRONT, "M43 49 Q36 54 28 57 L38 59 Q42 56 45 53 Z", fiber = 50f, detail = "0.1 0.9 0.5 0.45 1 0.3"),
    BodyShape("delts", BodyView.FRONT, "M25 58 Q14 60 11.5 68 Q11 75 15 79 Q19 76 22 70 Q25 64 30 60 Z", fiber = 10f, detail = "0.78 0.08 0.6 0.5 0.55 0.98, 0.38 0.05 0.25 0.5 0.42 0.95"),
    BodyShape("chest", BodyView.FRONT, "M49 60 L36 60 Q28 62 24 70 Q24 79 31 83 Q40 86 49 82 Z", fiber = 70f, detail = "0.12 0.22 0.55 0.38 1 0.3, 0.2 0.62 0.6 0.78 1 0.9"),
    BodyShape("biceps", BodyView.FRONT, "M21 79.5 Q13.5 80.5 11 88 Q10.5 95 13 100 Q17.5 100.5 20 94 Q22.5 87 21 79.5 Z", fiber = 0f, detail = "0.55 0.05 0.4 0.5 0.55 0.92, 0.02 0.72 0.3 0.8 0.45 1"),
    BodyShape("forearms", BodyView.FRONT, "M10 103 Q8 110 8.5 119 L13.5 121.5 Q15.5 112 18.5 103.5 Q14 101.5 10 103 Z", fiber = 8f, detail = "0.62 0 0.45 0.5 0.3 1, 0.88 0.1 0.75 0.5 0.65 1"),
    BodyShape("abs", BodyView.FRONT, "M49 87 L43 88 Q42 93 43 97 L49 97 Z", fiber = 0f),                       // Sixpack oben
    BodyShape("abs", BodyView.FRONT, "M49 99 L43 99 Q42 104 43 108 L49 108 Z", fiber = 0f),                    // Mitte
    BodyShape("abs", BodyView.FRONT, "M49 110 L43 110 Q42 116 44 120 Q46 124 49 125 Z", fiber = 0f),           // unten
    BodyShape("obliques", BodyView.FRONT, "M41 88 Q33 90 29 98 Q30 108 33 116 Q37 120 41 119 Q40 104 41 88 Z", fiber = -40f, detail = "0.05 0.12 0.3 0.05 0.5 0.06, 0.05 0.26 0.3 0.18 0.55 0.2, 0.08 0.4 0.3 0.32 0.6 0.34"),
    BodyShape("quads", BodyView.FRONT, "M33 132 Q28 142 29 152 Q31 158 35 159 Q37 148 39 136 Z", fiber = 4f, detail = "0.55 0.05 0.6 0.5 0.5 0.95"),  // äußerer Teil
    BodyShape("quads", BodyView.FRONT, "M40 134 Q37 146 37 156 Q40 159 43 157 Q45 146 44 136 Z", fiber = 4f, detail = "0.5 0.1 0.55 0.55 0.45 0.95"),  // Mitte
    BodyShape("quads", BodyView.FRONT, "M45 146 Q43 152 44 158 Q47 159 47.5 154 Q47.5 149 45 146 Z", fiber = 4f), // Träne innen
    BodyShape("adductors", BodyView.FRONT, "M46 134 Q49 137 49 142 Q48 146 46 148 Q45 141 45 136 Z", fiber = -12f),
    BodyShape("calves", BodyView.FRONT, "M31 165 Q29 172 31 179 L34 183 Q35 174 34 166 Z", fiber = 0f, detail = "0.5 0.05 0.6 0.5 0.45 0.9"),

    // ----- Muskeln hinten -----
    BodyShape("traps", BodyView.BACK, "M50 46 L43 49 Q35 55 27 58 Q36 62 42 70 L50 92 Z", fiber = 50f, detail = "0 0.17 0.5 0.3 1 0.28, 0.3 0.55 0.65 0.6 1 0.62"),
    BodyShape("delts", BodyView.BACK, "M25 58 Q14 60 11.5 68 Q11 75 15 79 Q19 76 22 70 Q25 64 30 60 Z", fiber = 10f, detail = "0.62 0.05 0.45 0.5 0.5 0.98, 0.3 0.08 0.2 0.5 0.38 0.95"),
    BodyShape("lats", BodyView.BACK, "M43 72 Q35 64 26 66 Q23 76 25 86 Q29 98 37 108 Q42 102 45 92 Z", fiber = 35f, detail = "0.05 0.18 0.4 0.08 0.65 0.22, 0.2 0.45 0.5 0.55 0.75 0.9"),
    BodyShape("lowerback", BodyView.BACK, "M49 94 L46 96 Q43 106 43.5 116 Q46 122 49 123 Z", fiber = 0f, detail = "0.5 0 0.45 0.5 0.5 1"),
    BodyShape("obliques", BodyView.BACK, "M29 100 Q28.5 110 32 118 L38 116 Q34 109 33.5 104 Q31 101 29 100 Z", fiber = -40f),
    BodyShape("triceps", BodyView.BACK, "M22 79.5 Q14.5 80.5 11.5 88 Q10.5 95 12.5 100 L19 99.5 Q22.5 91 22 79.5 Z", fiber = 0f, detail = "0.5 0.05 0.42 0.4 0.5 0.68, 0.12 0.72 0.5 0.55 0.88 0.72"),
    BodyShape("forearms", BodyView.BACK, "M10 103 Q8 110 8.5 119 L13.5 121.5 Q15.5 112 18.5 103.5 Q14 101.5 10 103 Z", fiber = 8f, detail = "0.62 0 0.45 0.5 0.3 1"),
    BodyShape("glutes", BodyView.BACK, "M49 122 Q40 120 33 125 Q29 132 32 140 Q40 146 49 143 Z", fiber = 55f, detail = "0 0.32 0.4 0.12 0.8 0.04, 0.15 0.65 0.5 0.5 0.95 0.75"),
    BodyShape("hamstrings", BodyView.BACK, "M32 142 Q29 148 30 154 Q32 159 36 160 Q38 152 39 145 Z", fiber = 0f, detail = "0.5 0.05 0.45 0.5 0.55 0.95"), // äußerer Kopf
    BodyShape("hamstrings", BodyView.BACK, "M41 146 Q39 152 40 159 Q44 161 46 157 Q47 150 46 145 Z", fiber = 0f, detail = "0.5 0.05 0.55 0.5 0.45 0.95"), // innerer Kopf
    BodyShape("calves", BodyView.BACK, "M31 164 Q28 170 31 177 Q34 179 37 176 Q38 170 37 164 Z", fiber = 0f, detail = "0.15 0.72 0.5 0.95 0.9 0.78"),     // Wade außen
    BodyShape("calves", BodyView.BACK, "M39 164 Q38 170 40 176 Q43 178 45 175 Q47 169 45 164 Z", fiber = 0f, detail = "0.1 0.78 0.5 0.95 0.85 0.72")      // Wade innen
)

/**
 * Fertige Compose-Pfade (einmal berechnet), in Gesamtkoordinaten.
 * mirrored = gespiegelte (rechte) Hälfte; box = Begrenzungsrahmen auf dem Bildschirm (für Verläufe und Fugen).
 */
private class BodyPath(
    val shape: BodyShape,
    val path: Path,
    /** 0 = Vorderansicht, BACK_OFFSET = Rückansicht */ val dx: Float,
    val mirrored: Boolean
) {
    val box: Rect = shape.points.let { p ->
        val xs = p.map { if (mirrored) BODY_WIDTH - it.x else it.x }
        Rect(xs.min() + dx, p.minOf { it.y }, xs.max() + dx, p.maxOf { it.y })
    }
    /** Bildschirm-x zu u (0 = außen, 1 = Körpermitte) – bei der gespiegelten Hälfte von rechts gemessen */
    fun x(u: Float) = if (mirrored) box.right - u * box.width else box.left + u * box.width
    fun y(v: Float) = box.top + v * box.height
}

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
            for (dx in offsets) for ((i, p) in variants.withIndex()) {
                add(BodyPath(s, Path().apply { addPath(p, Offset(dx, 0f)) }, dx, mirrored = i == 1))
            }
        }
    }
}

/** Umriss einer Figur (alle Körperteile vereint) – Muskeln dürfen nicht darüber hinauswachsen. */
private val silhouettes = HashMap<Float, Path>()
private fun silhouette(dx: Float): Path = silhouettes.getOrPut(dx) {
    bodyPaths.filter { it.dx == dx && it.shape.isBody }
        .fold(Path()) { acc, b -> Path().apply { op(acc, b.path, PathOperation.Union) } }
}

// ---------- Plastische Optik (Licht von oben links) ----------

private val SHADE = Color(0xFF4A2E18)
private fun white(a: Float) = Color.White.copy(alpha = a)
private fun shade(a: Float) = SHADE.copy(alpha = a)
private fun black(a: Float) = Color.Black.copy(alpha = a)
private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)

private fun isHead(s: BodyShape) = s.fill == BodyFill.FUR && !s.mirror && s.id == null

/** Kreisverlauf in 0..1 des Rahmens (wie SVG objectBoundingBox – bei länglichen Formen also eine Ellipse). */
private fun boxRadial(box: Rect, cx: Float, cy: Float, r: Float, vararg stops: Pair<Float, Color>): Brush {
    val shader = RadialGradientShader(Offset(cx, cy), r, stops.map { it.second }, stops.map { it.first })
    shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(box.width, box.height); postTranslate(box.left, box.top) })
    return ShaderBrush(shader)
}

/** Waagerechter Verlauf über den Rahmen; reversed = von rechts (gespiegelte Hälfte: außen ist rechts). */
private fun boxHorizontal(box: Rect, reversed: Boolean, vararg stops: Pair<Float, Color>): Brush =
    Brush.horizontalGradient(*stops, startX = if (reversed) box.right else box.left, endX = if (reversed) box.left else box.right)

/** Kugel (Kopf, Ohren): Glanz oben links, dunkler Rand */
private fun sphere(box: Rect, mirrored: Boolean) = boxRadial(
    box, if (mirrored) 0.6f else 0.4f, 0.32f, 0.72f,
    0f to white(0.45f), 0.45f to white(0f), 0.75f to shade(0f), 1f to shade(0.42f)
)

/** Licht-Ebene über einer Fläche: Muskel = Polster, Rumpf = halber Zylinder, Arm/Bein = Zylinder, Pfote = Kugel. */
private fun lightOf(b: BodyPath): Brush? {
    val s = b.shape
    return when {
        s.isMuscle -> boxRadial(b.box, 0.32f, 0.28f, 0.8f, 0f to white(0.55f), 0.35f to white(0f), 0.7f to black(0f), 1f to black(0.32f))
        isHead(s) -> null // Kopf-Kugel kommt zuletzt über Kopf, Flecken und Gesicht
        s.fill == BodyFill.PATCH -> if (s.inHead) null else sphere(b.box, b.mirrored)
        s.fill == BodyFill.FUR || s.fill == BodyFill.TISSUE -> when {
            !s.mirror -> sphere(b.box, b.mirrored)
            s.points.maxOf { it.x } >= 49f -> boxHorizontal(b.box, b.mirrored,
                0f to shade(0.5f), 0.28f to shade(0.12f), 0.55f to shade(0f), 0.85f to white(0.22f), 1f to white(0.18f))
            else -> boxHorizontal(b.box, b.mirrored,
                0f to shade(0.45f), 0.25f to shade(0f), 0.45f to white(0.3f), 0.65f to white(0f), 1f to shade(0.38f))
        }
        s.fill == BodyFill.PINK && !s.inHead -> boxRadial(b.box, if (b.mirrored) 0.6f else 0.4f, 0.3f, 0.75f,
            0f to white(0.5f), 0.5f to white(0f), 1f to Color(0x598A3A35))
        else -> null
    }
}

/** Muskelfasern: feine Linien in Faserrichtung, auf die Form beschnitten. */
private fun DrawScope.fibers(b: BodyPath, angle: Float) {
    val c = b.box.center
    val r = (b.box.width + b.box.height) / 2 + 2f
    val color = Color(0xFF3A1A0A).copy(alpha = 0.16f)
    clipPath(b.path) {
        rotate(if (b.mirrored) -angle else angle, c) {
            var x = c.x - r
            while (x <= c.x + r) {
                drawLine(color, Offset(x, c.y - r), Offset(x, c.y + r), strokeWidth = 0.38f)
                x += 1.5f
            }
        }
    }
}

/** Anatomische Fugen innerhalb eines Muskels (BodyShape.detail): dunkle Linie mit heller Kante. */
private fun DrawScope.details(b: BodyPath) {
    if (b.shape.detail.isEmpty()) return
    val p = Path()
    for (c in b.shape.detail.split(',')) {
        val n = c.trim().split(' ').map { it.toFloat() }
        p.moveTo(b.x(n[0]), b.y(n[1]))
        p.quadraticTo(b.x(n[2]), b.y(n[3]), b.x(n[4]), b.y(n[5]))
    }
    clipPath(b.path) {
        translate(if (b.mirrored) -0.45f else 0.45f, 0.35f) { drawPath(p, white(0.45f), style = Stroke(0.55f)) }
        drawPath(p, Color(0xFF3A1A0A).copy(alpha = 0.55f), style = Stroke(0.6f, cap = StrokeCap.Round))
    }
}

val PRIMARY_COLOR = Color(0xFFE24B4A)
val SECONDARY_COLOR = Color(0xFFEF9F27)

/**
 * Körperdiagramm (Vorder- und Rückansicht) mit eingefärbten Muskeln.
 * primary = Hauptmuskeln (rot), secondary = Hilfsmuskeln (orange), Rest grau.
 */
@Composable
fun MuscleMap(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier, showLegend: Boolean = true) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BodyFigure({ id -> if (id in primary) PRIMARY_COLOR else if (id in secondary) SECONDARY_COLOR else null })
        if (showLegend) MuscleLegend(primary, secondary, Modifier.padding(top = 8.dp))
    }
}

/**
 * Die Meerschweinchen-Figur (vorne + hinten) mit „Vorne/Hinten“-Beschriftung.
 * colorOf(Muskel-ID) = Farbe des Muskels oder null für neutral grau.
 * view = nur eine Seite (FRONT/BACK) zeichnen, ohne Beschriftung; widthFraction = Breite im Container.
 */
@Composable
fun BodyFigure(
    colorOf: (String) -> Color?,
    modifier: Modifier = Modifier,
    view: BodyView = BodyView.BOTH,
    widthFraction: Float = 0.72f
) {
    // Hell/dunkel am tatsächlichen Hintergrund ablesen (App-Einstellung kann vom System abweichen)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Im dunklen Design bleibt das Fell hell (leicht gedämpft) – dunkles Fell verschluckt Gesicht und Augen
    val fur = if (dark) Color(0xFFE2D3C1) else Color(0xFFF3E6D6)
    val patch = if (dark) Color(0xFF946642) else Color(0xFF9C6B45)
    val outline = if (dark) Color(0xFFB39579) else Color(0xFF7A5A43)
    val pink = if (dark) Color(0xFFE8AEAB) else Color(0xFFF2B8B5)
    val neutral = if (dark) Color(0xFFBDB3A8) else Color(0xFFCFC7BE)
    // Muskelgewebe zwischen den Muskeln (Rumpf, Arme, Beine) – etwas dunkler als untrainierte Muskeln
    val tissue = if (dark) Color(0xFFB0A599) else Color(0xFFC8BDB1)
    val groundShadow = Color(0xFF3A2415)
    val totalWidth = (if (view == BodyView.BOTH) BACK_OFFSET + BODY_WIDTH else BODY_WIDTH) + 2 * BODY_PAD
    // Bei einer einzelnen Seite: nur deren Pfade, die Rückansicht an x = 0 verschoben
    val shift = if (view == BodyView.BACK) -BACK_OFFSET else 0f
    val paths = when (view) {
        BodyView.BOTH -> bodyPaths
        BodyView.FRONT -> bodyPaths.filter { it.dx == 0f }
        BodyView.BACK -> bodyPaths.filter { it.dx == BACK_OFFSET }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth(widthFraction)
                .aspectRatio(totalWidth / (BODY_HEIGHT + 4f)) // + Platz für den Bodenschatten
        ) {
            val s = size.width / totalWidth
            scale(s, s, pivot = Offset.Zero) { translate(left = shift + BODY_PAD) {
                // 0. Bodenschatten und weicher Schatten der ganzen Figur (unten rechts)
                for (dx in paths.map { it.dx }.distinct()) {
                    drawOval(groundShadow.copy(alpha = 0.13f), Offset(dx + BODY_WIDTH / 2 - 26f, BODY_HEIGHT - 4.2f), Size(52f, 6.4f))
                    translate(1.5f, 3f) {
                        val sil = silhouette(dx)
                        for (w in listOf(5f, 3f, 1.5f)) drawPath(sil, groundShadow.copy(alpha = 0.05f), style = Stroke(w, join = StrokeJoin.Round))
                        drawPath(sil, groundShadow.copy(alpha = 0.08f))
                    }
                }
                // 1. Außenkontur: alle Körperteile dick umranden; die Füllungen danach überdecken
                //    die Linien im Inneren, übrig bleibt nur der Umriss.
                for (b in paths) if (b.shape.isBody) drawPath(b.path, outline, style = Stroke(width = 2.4f, join = StrokeJoin.Round))
                // 2. Flächen in Reihenfolge (später liegt oben), jeweils mit Licht-Ebene
                for (b in paths) {
                    val shape = b.shape
                    val color = when (shape.fill) {
                        BodyFill.FUR -> fur
                        BodyFill.PATCH -> patch
                        BodyFill.PINK -> pink
                        BodyFill.DARK -> Color(0xFF2B211B)
                        BodyFill.LIGHT -> Color.White
                        BodyFill.TISSUE -> tissue
                        BodyFill.DECO -> neutral
                        BodyFill.MUSCLE -> shape.id?.let(colorOf) ?: neutral
                    }
                    if (shape.isMuscle) {
                        // Muskel wächst bis an seine Nachbarn heran (Rand etwas dunkler = gewölbte Kante),
                        // aber nicht über den Körperumriss hinaus; dazwischen bleibt nur eine feine Fuge
                        clipPath(silhouette(b.dx)) {
                            drawPath(b.path, color.darken(0.82f), style = Stroke(2.6f, join = StrokeJoin.Round))
                        }
                        drawPath(b.path, color)
                        drawPath(b.path, Color(0x804A2A18), style = Stroke(0.45f))
                        fibers(b, shape.fiber)
                    } else {
                        drawPath(b.path, color)
                        // gleichfarbiger Rand schließt die Naht zwischen den gespiegelten Hälften
                        if (shape.fill == BodyFill.FUR || shape.fill == BodyFill.PATCH || shape.fill == BodyFill.TISSUE)
                            drawPath(b.path, color, style = Stroke(width = 0.8f))
                        if (shape.fill == BodyFill.TISSUE) fibers(b, 0f)
                    }
                    lightOf(b)?.let { drawPath(b.path, it) }
                    if (shape.fill == BodyFill.MUSCLE) details(b)
                }
                // 3. Kopf als Kugel – über Kopf, Fellflecken, Augen und Nase
                for (b in paths) if (isHead(b.shape)) drawPath(b.path, sphere(b.box, false))
            } }
        }
        if (view == BodyView.BOTH) Row(Modifier.fillMaxWidth(0.72f).padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Text(tr("Vorne", "Front"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Hinten", "Back"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** „● Haupt: Brust“ und darunter „● Hilfs: Trizeps, Schultern“. */
@Composable
fun MuscleLegend(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (primary.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(PRIMARY_COLOR)
            Text(tr("Haupt: ", "Primary: ") + primary.joinToString(", ") { muscleLabel(it) }, style = MaterialTheme.typography.bodyMedium)
        }
        if (secondary.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(SECONDARY_COLOR)
            Text(tr("Hilfs: ", "Secondary: ") + secondary.joinToString(", ") { muscleLabel(it) }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun LegendDot(color: Color) =
    Box(Modifier.padding(end = 6.dp).size(10.dp).background(color, CircleShape))

// ---------- Muskeln einer Übung ----------

/** Haupt- und Hilfsmuskeln (Muskel-IDs aus MUSCLES). */
data class MuscleSet(val primary: List<String>, val secondary: List<String>) {
    val isEmpty: Boolean get() = primary.isEmpty() && secondary.isEmpty()
}

/** Muskeln einer Übung: selbst gewählte (eigene Übung) oder aus dem Wiki (Katalog); null = unbekannt. */
fun musclesOf(name: String): MuscleSet? =
    CustomMeta.muscles[name]
        ?: wikiFor(name)?.let { MuscleSet(it.primary, it.secondary) }?.takeIf { !it.isEmpty }

/**
 * Auswahl der Muskeln mit Vorschau: zwei Chip-Reihen (Haupt/Hilfs). Ein Muskel kann nur in einer
 * der beiden Listen stehen – Auswahl in der einen entfernt ihn aus der anderen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusclePicker(value: MuscleSet, onChange: (MuscleSet) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MuscleMap(value.primary, value.secondary, showLegend = false)
        Text(tr("Hauptmuskeln", "Primary muscles"), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MUSCLES.keys.forEach { id ->
                val on = id in value.primary
                FilterChip(
                    selected = on,
                    onClick = {
                        onChange(if (on) value.copy(primary = value.primary - id)
                        else MuscleSet(value.primary + id, value.secondary - id))
                    },
                    label = { Text(muscleLabel(id)) },
                    leadingIcon = if (on) { { LegendDot(PRIMARY_COLOR) } } else null
                )
            }
        }
        Text(tr("Hilfsmuskeln", "Secondary muscles"), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MUSCLES.keys.forEach { id ->
                val on = id in value.secondary
                FilterChip(
                    selected = on,
                    onClick = {
                        onChange(if (on) value.copy(secondary = value.secondary - id)
                        else MuscleSet(value.primary - id, value.secondary + id))
                    },
                    label = { Text(muscleLabel(id)) },
                    leadingIcon = if (on) { { LegendDot(SECONDARY_COLOR) } } else null
                )
            }
        }
    }
}

/** Dialog „Muskeln bearbeiten“ für eigene Übungen. */
@Composable
fun MuscleEditDialog(store: GymStore, name: String, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(CustomMeta.muscles[name] ?: MuscleSet(emptyList(), emptyList())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Muskeln", "Muscles")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { MusclePicker(value) { value = it } }
        },
        confirmButton = {
            TextButton(onClick = { store.setCustomMuscles(name, value); onDismiss() }) { Text(tr("Speichern", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

// ---------- Heatmap: wie stark wurde jeder Muskel in einem Zeitraum trainiert? ----------

/** Farbstufen der Heatmap: (untere Grenze in %, Farbe, Beschriftung). */
val HEAT_LEVELS = listOf(
    Triple(0.01f, Color(0xFFF2C94C), "1–19 %"),
    Triple(0.20f, SECONDARY_COLOR, "20–49 %"),
    Triple(0.50f, Color(0xFFE8703A), "50–79 %"),
    Triple(0.80f, PRIMARY_COLOR, "80–100 %")
)

fun heatColor(p: Float): Color? = HEAT_LEVELS.lastOrNull { p >= it.first }?.second

/**
 * Sätze je Muskel (ohne Aufwärmsätze): jeder Satz zählt 1 für die Hauptmuskeln und 0,5 für die Hilfsmuskeln.
 * Übungen ohne bekannte Muskeln werden übersprungen.
 */
fun muscleScore(sets: List<GymSet>): Map<String, Float> {
    val score = HashMap<String, Float>()
    val cache = HashMap<String, MuscleSet?>()
    for (s in sets) {
        if (!s.isWork) continue // Aufwärmsätze zählen nicht
        val m = cache.getOrPut(s.exercise) { musclesOf(s.exercise) } ?: continue
        m.primary.forEach { score[it] = (score[it] ?: 0f) + 1f }
        m.secondary.forEach { score[it] = (score[it] ?: 0f) + 0.5f }
    }
    return score
}

/** Belastung je Muskel relativ zum am stärksten trainierten (= 1.0). */
fun relativeLoad(score: Map<String, Float>): Map<String, Float> {
    val max = score.values.maxOrNull() ?: return emptyMap()
    return score.mapValues { it.value / max }
}

/** Zeiträume der Heatmap/Muskelgruppen-Statistik auf der Startseite. */
enum class StatPeriod(private val de: String, private val en: String) {
    LAST("Letztes Training", "Last workout"),
    WEEK("7 Tage", "7 days"),
    MONTH("30 Tage", "30 days"),
    ALL("Alle", "All");

    val label: String get() = tr(de, en)

    /** Sätze in diesem Zeitraum. „Letztes Training“ = alle Sätze des letzten Trainingstags. */
    fun filter(sets: List<GymSet>): List<GymSet> {
        val now = System.currentTimeMillis()
        val day = 24L * 3600 * 1000
        return when (this) {
            LAST -> sets.maxByOrNull { it.time }?.let { last -> sets.filter { dayKey(it.time) == dayKey(last.time) } }.orEmpty()
            WEEK -> sets.filter { it.time >= now - 7 * day }
            MONTH -> sets.filter { it.time >= now - 30 * day }
            ALL -> sets
        }
    }
}

/** Startseite: Karte „Trainierte Muskeln“ mit Zeitraum-Umschalter, Werte in Prozent. */
@Composable
fun MuscleHeatCard(store: GymStore, period: StatPeriod, onPeriod: (StatPeriod) -> Unit) {
    MuscleHeatCard(
        title = tr("Trainierte Muskeln", "Trained muscles"),
        score = muscleScore(period.filter(store.sets)),
        showSets = false,
        empty = tr("Keine Trainings in diesem Zeitraum.", "No workouts in this period.")
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatPeriod.entries.forEach { p ->
                FilterChip(selected = p == period, onClick = { onPeriod(p) }, label = { Text(p.label) })
            }
        }
    }
}

/** „6,5“ bzw. „6“ – halbe Sätze kommen von Hilfsmuskeln. */
fun fmtSets(v: Float): String = fmtW(Math.round(v * 2) / 2.0)

/**
 * Karte mit Heatmap: Figur, Farblegende und Muskeln (stärkste zuerst).
 * showSets = true: Summe der Sätze je Muskel (Trainingsübersicht), sonst Prozent vom Maximum.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleHeatCard(
    title: String,
    score: Map<String, Float>,
    showSets: Boolean,
    empty: String,
    header: (@Composable () -> Unit)? = null
) {
    val load = relativeLoad(score)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            header?.invoke()
            BodyFigure({ id -> load[id]?.let(::heatColor) })
            // Farblegende
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HEAT_LEVELS.forEach { (_, c, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(c)
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (load.isEmpty()) {
                Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (showSets) {
                    // Trainingsübersicht: Anteil jedes Muskels an der Summe (zusammen 100 %) + Sätze
                    val total = score.values.sum()
                    Text(
                        tr("Anteil am Training · Sätze", "Share of workout · sets"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalBars(
                        score.entries.sortedByDescending { it.value }.map { muscleLabel(it.key) to it.value.toDouble() }
                    ) { v -> "${Math.round(v / total * 100)} % · ${fmtSets(v.toFloat())}" }
                } else
                // Muskeln mit Prozent (relativ zum stärksten), stärkste zuerst
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    score.entries.sortedByDescending { it.value }.forEach { (id, _) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            heatColor(load.getValue(id))?.let { LegendDot(it) }
                            Text(
                                muscleLabel(id) + " ${Math.round(load.getValue(id) * 100)} %",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                if (showSets) Text(
                    tr("Summe: ", "Total: ") + fmtSets(score.values.sum()) + " " + tr("Muskel-Sätze", "muscle sets") +
                        " · " + plural(score.size, tr("Muskel", "muscle"), tr("Muskeln", "muscles")) +
                        tr(" (Hilfsmuskel zählt ½)", " (secondary counts ½)"),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

// ---------- Muskel-Karten pro Seite (Reiter „Info“ der Übungsdetails) ----------

/** Muskeln, die auf einer Seite der Figur zu sehen sind (aus BODY_SHAPES abgeleitet). */
fun musclesOnSide(view: BodyView): List<String> =
    MUSCLES.keys.filter { id -> BODY_SHAPES.any { it.id == id && (it.view == view || it.view == BodyView.BOTH) } }

/**
 * Karte für eine Seite (Vorder- oder Rückseite): Titel oben, darunter das Meerschweinchen
 * mittig und groß. showList = unten die Muskeln der ganzen Übung (Haupt-/Hilfsmuskeln), für die letzte Karte.
 */
@Composable
fun MuscleSideCard(
    view: BodyView,
    primary: List<String>,
    secondary: List<String>,
    showList: Boolean = false,
    colors: CardColors = CardDefaults.cardColors()
) {
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (view == BodyView.FRONT) tr("Vorderseite", "Front") else tr("Rückseite", "Back"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            BodyFigure(
                { id -> if (id in primary) PRIMARY_COLOR else if (id in secondary) SECONDARY_COLOR else null },
                view = view, widthFraction = 0.62f
            )
            if (showList) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MuscleGroupList(tr("Hauptmuskeln", "Primary"), primary, PRIMARY_COLOR)
                    MuscleGroupList(tr("Hilfsmuskeln", "Secondary"), secondary.filter { it !in primary }, SECONDARY_COLOR)
                }
            }
        }
    }
}

/** Überschrift („Hauptmuskeln“) mit Farbpunkt, darunter die Muskeln. */
@Composable
private fun MuscleGroupList(title: String, ids: List<String>, color: Color) {
    if (ids.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(ids.joinToString(", ") { muscleLabel(it) }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
