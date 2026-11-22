package com.example.gymtracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date

/** Überschrift einer Kategorie: farbiger Punkt + Name (Übungsliste und Katalog). */
@Composable
fun GroupHeader(group: MuscleGroup) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(group.color)
        )
        Spacer(Modifier.width(8.dp))
        Text(group.label, style = MaterialTheme.typography.titleMedium)
    }
}


// ---------- Diagramme (Trainingsübersicht & Körper) ----------

/** Überschrift + Inhalt in einer Karte – einheitlicher Rahmen für alle Diagramme. */
@Composable
fun ChartCard(title: String, subtitle: String? = null, colors: CardColors = CardDefaults.cardColors(), content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Waagerechte Balken (z. B. Volumen pro Übung). Eine Datenreihe → eine Farbe;
 * jede Zeile ist direkt beschriftet (Name links, Wert rechts), daher keine Legende nötig.
 * Gebaut aus normalen Layout-Bausteinen statt Canvas: Balkenlänge = Anteil am größten Wert.
 */
@Composable
fun HorizontalBars(items: List<Pair<String, Double>>, format: (Double) -> String) {
    val max = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
    val barColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(104.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(track)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((value / max).toFloat().coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
                Text(
                    format(value),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(72.dp)
                )
            }
        }
    }
}

/**
 * Senkrechte Säulen über die Zeit (z. B. Volumen der letzten Trainings).
 * Die letzte Säule (= aktuelles Training) ist in voller Akzentfarbe, die älteren gedämpft.
 * Beschriftet werden nur der aktuelle Wert und das Datum unter jeder Säule.
 */
@Composable
fun HistoryBars(values: List<Double>, labels: List<String>, format: (Double) -> String) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: return
    val strong = MaterialTheme.colorScheme.primary
    // Gedämpfte Farbe deckend mischen (statt halbtransparent), damit nichts durchscheint
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest)
    val grid = MaterialTheme.colorScheme.outlineVariant
    // Nur oben abgerundet: die Säule steht auf der Grundlinie
    val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    Column {
        Text(
            format(values.last()),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(Modifier.height(4.dp))
        // Jede Säule bekommt eine gleich breite Spalte (weight 1f) – die Datumszeile darunter
        // verwendet dieselben Spalten, dadurch steht jedes Datum genau unter seiner Säule.
        Row(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            values.forEachIndexed { i, v ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .fillMaxHeight((v / max).toFloat().coerceIn(0.02f, 1f))
                            .clip(barShape)
                            .background(if (i == values.lastIndex) strong else muted)
                    )
                }
            }
        }
        // Dezente Grundlinie
        HorizontalDivider(color = grid)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Kalenderwoche nach ISO 8601 (Woche beginnt Montag, KW 1 = Woche mit dem ersten Donnerstag): „KW 47“ bzw. „W47“. */
fun fmtWeek(t: Long): String {
    val c = java.util.Calendar.getInstance().apply {
        firstDayOfWeek = java.util.Calendar.MONDAY
        minimalDaysInFirstWeek = 4
        timeInMillis = t
    }
    val w = c.get(java.util.Calendar.WEEK_OF_YEAR)
    return tr("KW $w", "W$w")
}

/** Kurzes Datum für Achsen-Beschriftungen („18.11.“ bzw. „18/11“). */
fun fmtShortDate(t: Long): String =
    SimpleDateFormat(tr("dd.MM.", "dd/MM"), appLocale()).format(Date(t))

// ---------- Diagramm ----------

@Composable
fun ProgressChart(values: List<Double>, title: String, unit: String, colors: CardColors = CardDefaults.cardColors()) {
    ChartCard(title, subtitle = "${fmtW(values.min())} – ${fmtW(values.max())} $unit", colors = colors) {
        LineChart(values, unit)
    }
}

/**
 * Liniendiagramm: 2 dp Linie, kleine Punkte mit Hintergrund-Ring, dezente Hilfslinien.
 * Links die Werte der drei Hilfslinien (Achse), oben rechts der letzte (aktuelle) Wert.
 * onTapPoint: Antippen wählt den nächstgelegenen Punkt (Index); dann reicht auch ein einzelner Punkt.
 */
@Composable
fun LineChart(values: List<Double>, unit: String, onTapPoint: ((Int) -> Unit)? = null, headline: String? = null, dots: Boolean = true) {
    if (values.size < (if (onTapPoint != null) 1 else 2)) return
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val min = values.min()
    val max = values.max()
    // Etwas Luft oben/unten, damit eine fast flache Linie nicht am Rand klebt
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    val lo = min - span * 0.1
    val hi = max + span * 0.1
    Column {
        Text(
            headline ?: "${fmtW(values.last())} $unit",   // Standard: letzter Wert (z. B. aktuelles Gewicht)
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.End)
        )
        Row(Modifier.height(140.dp).padding(vertical = 8.dp)) {
            // Achse: Werte auf Höhe der Hilfslinien (oben, Mitte, unten)
            Column(
                Modifier.fillMaxHeight().padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf(hi, (hi + lo) / 2, lo).forEach {
                    Text(fmtW(Math.round(it * 10) / 10.0), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val tap = rememberUpdatedState(onTapPoint)
            val n = values.size
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (onTapPoint == null) Modifier else Modifier.pointerInput(n) {
                        // nächster Punkt in x-Richtung (gleiche Abstände wie beim Zeichnen)
                        detectTapGestures { pos ->
                            val pad = 6.dp.toPx()
                            val i = if (n == 1) 0 else Math.round((pos.x - pad) / ((size.width - 2 * pad) / (n - 1))).coerceIn(0, n - 1)
                            tap.value?.invoke(i)
                        }
                    })
            ) {
                val pad = 6.dp.toPx()
                val usableH = size.height - 2 * pad
                // Drei dezente horizontale Hilfslinien
                for (k in 0..2) {
                    val y = pad + usableH * k / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val stepX = if (values.size > 1) (size.width - 2 * pad) / (values.size - 1) else 0f
                val points = values.mapIndexed { i, v ->
                    Offset(if (values.size > 1) pad + i * stepX else size.width / 2, (pad + usableH - (v - lo) / (hi - lo) * usableH).toFloat())
                }
                points.zipWithNext { a, b -> drawLine(color, a, b, 2.dp.toPx(), StrokeCap.Round) }
                if (dots) points.forEachIndexed { i, pt ->   // dots = false: nur Linie (viele Werte, z. B. Puls)
                    val r = if (i == points.lastIndex) 5.dp.toPx() else 3.5.dp.toPx()
                    drawCircle(surface, r + 2.dp.toPx(), pt) // Ring in Hintergrundfarbe trennt Punkt von Linie
                    drawCircle(color, r, pt)
                }
            }
        }
    }
}


/**
 * Schlanker Auswahl-Button für Reihen gleich breiter Optionen (Trainingstage, Zeitraum):
 * in einer Row mit Modifier.weight(1f) aufrufen → alle zusammen nutzen die volle Breite.
 */
@Composable
fun SegmentChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Kategorie als große Zeile (beim Hinzufügen von Übungen: erst Kategorie wählen, dann Übung).
 * total = Übungen der Kategorie, added = davon schon in der Liste bzw. im Workout.
 */
@Composable
fun CategoryRow(group: MuscleGroup, total: Int, added: Int, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = group.color.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MuscleIcon(group, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    plural(total, tr("Übung", "exercise"), tr("Übungen", "exercises")) +
                        (if (added > 0) tr(" · $added ausgewählt", " · $added selected") else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (added > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
