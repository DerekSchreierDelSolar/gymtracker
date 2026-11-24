package com.example.gymtracker

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

// ---------- Supplement-Wiki: Nahrungsergänzungsmittel mit Evidenz, Dosierung und Risiken ----------
//
// Inhalte in app/src/main/assets/supplements.json, Schlüssel = feste ID (z. B. "kreatin").
// Web: gen-data.mjs → web/js/supplement-data.js (SUPPLEMENTS), Logik in wiki.js. Format pro Eintrag:
//   { "name"/"category"/"summary"/"what"/"dose"/"timing": [de, en], "effects"/"risks"/"tips": [[de, en], …],
//     "keys": [Suchbegriffe ohne Umlaute, für Suche und Bot], "evidence": 0–3, "deficiencyOnly": true (optional) }

data class Supplement(
    val id: String,
    val name: WikiText,
    val keys: List<String>,
    val category: WikiText,
    /** 3 = gut belegt, 2 = teilweise belegt, 1 = schwach belegt, 0 = kein Nutzen belegt */
    val evidence: Int,
    /** nur bei nachgewiesenem Mangel sinnvoll */
    val deficiencyOnly: Boolean,
    val summary: WikiText,
    val what: WikiText,
    val effects: List<WikiText>,
    val dose: WikiText,
    val timing: WikiText,
    val risks: List<WikiText>,
    val tips: List<WikiText>
)

object SupplementWiki {
    var entries: List<Supplement> = emptyList()
        private set

    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        val json = JSONObject(context.assets.open("supplements.json").bufferedReader().use { it.readText() })
        fun pair(a: JSONArray) = WikiText(a.getString(0), a.getString(1))
        fun pairs(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { pair(a.getJSONArray(it)) }
        entries = json.keys().asSequence().map { id ->
            val o = json.getJSONObject(id)
            val k = o.optJSONArray("keys") ?: JSONArray()
            Supplement(
                id = id,
                name = pair(o.getJSONArray("name")),
                keys = List(k.length()) { k.getString(it) },
                category = pair(o.getJSONArray("category")),
                evidence = o.optInt("evidence", 0).coerceIn(0, 3),
                deficiencyOnly = o.optBoolean("deficiencyOnly", false),
                summary = pair(o.getJSONArray("summary")),
                what = pair(o.getJSONArray("what")),
                effects = pairs(o.optJSONArray("effects")),
                dose = pair(o.getJSONArray("dose")),
                timing = pair(o.getJSONArray("timing")),
                risks = pairs(o.optJSONArray("risks")),
                tips = pairs(o.optJSONArray("tips"))
            )
        }.toList()
    }

    fun byId(id: String) = entries.firstOrNull { it.id == id }
}

/** Evidenz-Stufe: Beschriftung + Farbe (grün → grau) */
fun evidenceLabel(level: Int): String = when (level) {
    3 -> tr("Gut belegt", "Well proven")
    2 -> tr("Teilweise belegt", "Some evidence")
    1 -> tr("Schwach belegt", "Weak evidence")
    else -> tr("Kein Nutzen belegt", "No proven benefit")
}

fun evidenceColor(level: Int): Color = when (level) {
    3 -> Color(0xFF43A047)
    2 -> Color(0xFFF9A825)
    1 -> Color(0xFFEF6C00)
    else -> Color(0xFF9E9E9E)
}

/** Kleines farbiges Etikett „Gut belegt“ */
@Composable
fun EvidenceBadge(level: Int) {
    val c = evidenceColor(level)
    Row(
        Modifier
            .background(c.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(evidenceLabel(level), style = MaterialTheme.typography.labelMedium)
    }
}

/** Suche über Name, Kategorie und Suchbegriffe (Umlaute egal, wie im Bot). */
fun supplementSearch(query: String): List<Supplement> {
    val q = Bot.norm(query)
    return SupplementWiki.entries.filter { s ->
        q.isEmpty() || listOf(s.name.de, s.name.en, s.name.text, s.category.de, s.category.en, s.category.text).any { Bot.norm(it).contains(q) } ||
            s.keys.any { Bot.norm(it).contains(q) }
    }
}

/** Reiter „Supplements“ im Wiki: nach Evidenz gruppiert (gut belegt zuerst), innerhalb alphabetisch. */
fun LazyListScope.supplementWikiItems(query: String, onOpen: (String) -> Unit) {
    val list = supplementSearch(query)
    if (query.isBlank()) item(key = "supp-intro") {
        Text(
            tr(
                "Was bringt was? Einstufung nach aktueller Studienlage – das meiste erreichst du über Training, Schlaf und Ernährung.",
                "What actually works? Rated by current evidence – most results come from training, sleep and diet."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
    if (list.isEmpty()) item {
        Text(
            tr("Kein Supplement gefunden.", "No supplement found."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
    for (level in 3 downTo 0) {
        val group = list.filter { it.evidence == level }.sortedByName { it.name.text }
        if (group.isEmpty()) continue
        item(key = "supp-h$level") {
            Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(evidenceColor(level), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(evidenceLabel(level), style = MaterialTheme.typography.titleMedium)
            }
        }
        items(group, key = { "s-" + it.id }) { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(s.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.name.text)
                    Text(
                        (if (s.deficiencyOnly) tr("Nur bei Mangel · ", "Only if deficient · ") else "") + s.summary.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Supplement-Blatt (von unten) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementSheet(id: String, onDismiss: () -> Unit) {
    val s = SupplementWiki.byId(id) ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(s.name.text, style = MaterialTheme.typography.titleLarge)
            Text(s.category.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                EvidenceBadge(s.evidence)
                if (s.deficiencyOnly) Text(
                    tr("Nur bei Mangel", "Only if deficient"),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            Text(s.summary.text, style = MaterialTheme.typography.bodyLarge)

            SuppHeading(tr("Was ist das?", "What is it?"))
            Text(s.what.text, style = MaterialTheme.typography.bodyMedium)
            if (s.effects.isNotEmpty()) {
                SuppHeading(tr("Wirkung", "Effect"))
                s.effects.forEach { SuppBullet("•", it.text) }
            }
            SuppHeading(tr("Dosierung", "Dosage"))
            Text(s.dose.text, style = MaterialTheme.typography.bodyMedium)
            if (s.timing.de != "–") {
                SuppHeading(tr("Einnahme", "When to take"))
                Text(s.timing.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (s.risks.isNotEmpty()) {
                SuppHeading(tr("Nebenwirkungen & Wechselwirkungen", "Side effects & interactions"))
                s.risks.forEach { SuppBullet("⚠", it.text, MaterialTheme.colorScheme.error) }
            }
            if (s.tips.isNotEmpty()) {
                SuppHeading(tr("Gut zu wissen", "Good to know"))
                s.tips.forEach { SuppBullet("💡", it.text) }
            }
            Text(
                tr(
                    "Allgemeine Informationen, keine Beratung. Bei Erkrankungen, Medikamenten, Schwangerschaft oder Stillzeit vorher ärztlich abklären. Wettkampfsportler: nur geprüfte Produkte (z. B. Kölner Liste) wegen Dopinggefahr.",
                    "General information, not advice. With medical conditions, medication, pregnancy or breastfeeding ask a doctor first. Competitive athletes: only tested products (e.g. Kölner Liste) because of doping risk."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SuppHeading(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun SuppBullet(bullet: String, text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Row {
        Text(bullet, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
