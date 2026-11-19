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
 * Muskulöses Meerschweinchen (passend zum App-Icon): Meerschweinchen-Kopf mit Hängeohren,
 * menschenähnlicher Körper in V-Form. Ein Muskel darf aus mehreren Flächen mit derselben ID bestehen
 * (z. B. Sixpack). Reihenfolge = Zeichenreihenfolge (später liegt oben).
 * Alle Formen ohne id bekommen zusätzlich eine braune Außenkontur.
 */
val BODY_SHAPES: List<BodyShape> = listOf(
    // ----- Körper (vorne und hinten gleich) -----
    BodyShape(null, BodyView.BOTH, "M30 18 Q16 12 15 22 Q17 30 28 28 Z", fill = BodyFill.PATCH),             // Ohr (hängend, seitlich)
    BodyShape(null, BodyView.BOTH, "M13 70 Q8 78 9 90 L10 100 Q7 110 8 122 L15 124 Q17 112 19 102 Q23 92 23 82 Q22 74 18 70 Z"), // Arm
    BodyShape(null, BodyView.BOTH, "M7 120 Q4 129 9 133 Q15 135 16 124 Z", fill = BodyFill.PINK),            // Pfote
    BodyShape(null, BodyView.BOTH, "M33 128 Q27 140 28 152 Q29 158 31 162 Q28 170 30 178 L34 186 L45 186 Q46 176 46 166 Q47 160 47 154 Q49 144 49 136 L50 132 Z"), // Bein
    BodyShape(null, BodyView.BOTH, "M33 184 Q28 192 34 194 L47 194 Q49 189 46 184 Z", fill = BodyFill.PINK),  // Fuß
    BodyShape(null, BodyView.BOTH, "M50 46 L42 48 Q34 55 24 58 Q14 60 11 68 Q10 76 15 80 L22 82 Q25 96 31 108 Q33 116 31 124 Q30 130 34 134 L50 136 Z"), // Rumpf (V-Form)
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

    // ----- Muskeln vorne -----
    BodyShape("traps", BodyView.FRONT, "M43 49 Q36 54 28 57 L38 59 Q42 56 45 53 Z"),
    BodyShape("delts", BodyView.FRONT, "M25 58 Q14 60 11.5 68 Q11 75 15 79 Q19 76 22 70 Q25 64 30 60 Z"),
    BodyShape("chest", BodyView.FRONT, "M49 60 L36 60 Q28 62 24 70 Q24 79 31 83 Q40 86 49 82 Z"),
    BodyShape("biceps", BodyView.FRONT, "M21 80 Q15 82 13 90 Q13 96 15 99 Q19 98 21 92 Q23 86 21 80 Z"),
    BodyShape("forearms", BodyView.FRONT, "M10 103 Q8 110 9 118 L13 121 Q15 112 18 104 Q14 101 10 103 Z"),
    BodyShape("abs", BodyView.FRONT, "M49 87 L43 88 Q42 93 43 97 L49 97 Z"),                       // Sixpack oben
    BodyShape("abs", BodyView.FRONT, "M49 99 L43 99 Q42 104 43 108 L49 108 Z"),                    // Mitte
    BodyShape("abs", BodyView.FRONT, "M49 110 L43 110 Q42 116 44 120 Q46 124 49 125 Z"),           // unten
    BodyShape("obliques", BodyView.FRONT, "M41 88 Q33 90 29 98 Q30 108 33 116 Q37 120 41 119 Q40 104 41 88 Z"),
    BodyShape("quads", BodyView.FRONT, "M33 132 Q28 142 29 152 Q31 158 35 159 Q37 148 39 136 Z"),  // äußerer Teil
    BodyShape("quads", BodyView.FRONT, "M40 134 Q37 146 37 156 Q40 159 43 157 Q45 146 44 136 Z"),  // Mitte
    BodyShape("quads", BodyView.FRONT, "M45 146 Q43 152 44 158 Q47 159 47.5 154 Q47.5 149 45 146 Z"), // Träne innen
    BodyShape("adductors", BodyView.FRONT, "M46 134 Q49 137 49 142 Q48 146 46 148 Q45 141 45 136 Z"),
    BodyShape("calves", BodyView.FRONT, "M31 165 Q29 172 31 179 L34 183 Q35 174 34 166 Z"),

    // ----- Muskeln hinten -----
    BodyShape("traps", BodyView.BACK, "M50 46 L43 49 Q35 55 27 58 Q36 62 42 70 L50 92 Z"),
    BodyShape("delts", BodyView.BACK, "M25 58 Q14 60 11.5 68 Q11 75 15 79 Q19 76 22 70 Q25 64 30 60 Z"),
    BodyShape("lats", BodyView.BACK, "M43 72 Q35 64 26 66 Q23 76 25 86 Q29 98 37 108 Q42 102 45 92 Z"),
    BodyShape("lowerback", BodyView.BACK, "M49 94 L46 96 Q43 106 43.5 116 Q46 122 49 123 Z"),
    BodyShape("obliques", BodyView.BACK, "M27 92 Q26 102 30 110 L33 108 Q30 100 30 92 Z"),
    BodyShape("triceps", BodyView.BACK, "M22 79 Q16 81 13 88 Q12 95 15 99 L19 98 Q22 90 22 79 Z"),
    BodyShape("forearms", BodyView.BACK, "M10 103 Q8 110 9 118 L13 121 Q15 112 18 104 Q14 101 10 103 Z"),
    BodyShape("glutes", BodyView.BACK, "M49 122 Q40 120 33 125 Q29 132 32 140 Q40 146 49 143 Z"),
    BodyShape("hamstrings", BodyView.BACK, "M32 142 Q29 148 30 154 Q32 159 36 160 Q38 152 39 145 Z"), // äußerer Kopf
    BodyShape("hamstrings", BodyView.BACK, "M41 146 Q39 152 40 159 Q44 161 46 157 Q47 150 46 145 Z"), // innerer Kopf
    BodyShape("calves", BodyView.BACK, "M31 164 Q28 170 31 177 Q34 179 37 176 Q38 170 37 164 Z"),     // Wade außen
    BodyShape("calves", BodyView.BACK, "M39 164 Q38 170 40 176 Q43 178 45 175 Q47 169 45 164 Z")      // Wade innen
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
fun MuscleMap(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier, showLegend: Boolean = true) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BodyFigure({ id -> if (id in primary) PRIMARY_COLOR else if (id in secondary) SECONDARY_COLOR else null })
        if (showLegend) MuscleLegend(primary, secondary, Modifier.padding(top = 8.dp))
    }
}

/**
 * Die Meerschweinchen-Figur (vorne + hinten) mit „Vorne/Hinten“-Beschriftung.
 * colorOf(Muskel-ID) = Farbe des Muskels oder null für neutral grau.
 */
@Composable
fun BodyFigure(colorOf: (String) -> Color?, modifier: Modifier = Modifier) {
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
                        BodyFill.MUSCLE -> b.shape.id?.let(colorOf) ?: neutral
                    }
                    drawPath(b.path, color)
                    // Fell: gleichfarbiger Rand schließt die Naht zwischen den gespiegelten Hälften
                    if (b.shape.fill == BodyFill.FUR || b.shape.fill == BodyFill.PATCH) drawPath(b.path, color, style = Stroke(width = 0.8f))
                    // dünne Fuge zwischen benachbarten Muskeln
                    if (b.shape.fill == BodyFill.MUSCLE) drawPath(b.path, fur, style = Stroke(width = 0.7f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(0.72f).padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
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
 * Belastung je Muskel, relativ zum am stärksten trainierten (= 1.0).
 * Jeder Satz zählt 1 für die Hauptmuskeln und 0,5 für die Hilfsmuskeln.
 * Übungen ohne bekannte Muskeln werden übersprungen.
 */
fun muscleLoad(sets: List<GymSet>): Map<String, Float> {
    val score = HashMap<String, Float>()
    val cache = HashMap<String, MuscleSet?>()
    for (s in sets) {
        val m = cache.getOrPut(s.exercise) { musclesOf(s.exercise) } ?: continue
        m.primary.forEach { score[it] = (score[it] ?: 0f) + 1f }
        m.secondary.forEach { score[it] = (score[it] ?: 0f) + 0.5f }
    }
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

/** Karte „Trainierte Muskeln“: Heatmap + Farblegende + Muskeln mit Prozent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleHeatCard(store: GymStore, period: StatPeriod, onPeriod: (StatPeriod) -> Unit) {
    val load = muscleLoad(period.filter(store.sets))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Trainierte Muskeln", "Trained muscles"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatPeriod.entries.forEach { p ->
                    FilterChip(selected = p == period, onClick = { onPeriod(p) }, label = { Text(p.label) })
                }
            }
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
                Text(
                    tr("Keine Trainings in diesem Zeitraum.", "No workouts in this period."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Muskeln mit Prozent, stärkste zuerst
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    load.entries.sortedByDescending { it.value }.forEach { (id, p) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            heatColor(p)?.let { LegendDot(it) }
                            Text("${muscleLabel(id)} ${Math.round(p * 100)} %", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                // Nie trainierte Muskeln als Hinweis
                val missing = MUSCLES.keys.filter { it !in load }
                if (missing.isNotEmpty()) Text(
                    tr("Nicht trainiert: ", "Not trained: ") + missing.joinToString(", ") { muscleLabel(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
