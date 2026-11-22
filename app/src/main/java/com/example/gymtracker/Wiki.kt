package com.example.gymtracker

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

// ---------- Übungs-Wiki: Ausführung + worauf man achten muss ----------
//
// Inhalte liegen als JSON in app/src/main/assets/wiki/*.json (eine Datei pro Muskelgruppe),
// Schlüssel = Grundübung (CatalogExercise.base). Die Web-Version liest dieselben Dateien
// (web/tools/gen-data.mjs). Format pro Eintrag:
//   "Bankdrücken": { "muscles": [de, en], "how": [[de, en], …], "tips": [[de, en], …],
//                    "variants": { "Kurzhantel": [de, en], … } }   // Schlüssel = Text in der Klammer

/** Ein Text in beiden Sprachen; `text` liefert die aktuelle. */
data class WikiText(val de: String, val en: String) {
    val text: String get() = tr(de, en)
}

data class WikiEntry(
    val muscles: WikiText,
    val how: List<WikiText>,
    val tips: List<WikiText>,
    /** Hinweise zu einzelnen Geräte-Varianten, Schlüssel = Klammertext im Katalog-Namen. */
    val variants: Map<String, WikiText>,
    /** Muskel-IDs (siehe MUSCLES in BodyMap.kt): Haupt- und Hilfsmuskeln fürs Körperdiagramm. */
    val primary: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    /** Genauer: Region (REGION_NAMES in BodyMap.kt) → Stufe 1 / 0,6 / 0,3 – überschreibt primary/secondary für diese Muskeln. */
    val regions: Map<String, Float> = emptyMap()
)

object Wiki {
    /** Grundübung → Eintrag. Wird einmal beim App-Start aus den Assets geladen. */
    var entries: Map<String, WikiEntry> = emptyMap()
        private set

    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        val result = LinkedHashMap<String, WikiEntry>()
        val files = context.assets.list("wiki")?.sorted().orEmpty()
        for (f in files) {
            val json = JSONObject(context.assets.open("wiki/$f").bufferedReader().use { it.readText() })
            for (base in json.keys()) result[base] = parse(json.getJSONObject(base))
        }
        entries = result
    }

    private fun pair(a: JSONArray) = WikiText(a.getString(0), a.getString(1))
    private fun pairs(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { pair(a.getJSONArray(it)) }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    private fun parse(o: JSONObject): WikiEntry {
        val v = o.optJSONObject("variants")
        return WikiEntry(
            muscles = pair(o.getJSONArray("muscles")),
            how = pairs(o.optJSONArray("how")),
            tips = pairs(o.optJSONArray("tips")),
            variants = v?.keys()?.asSequence()?.associateWith { pair(v.getJSONArray(it)) }.orEmpty(),
            primary = strings(o.optJSONArray("primary")),
            secondary = strings(o.optJSONArray("secondary")),
            regions = o.optJSONObject("regions")?.let { r -> r.keys().asSequence().associateWith { r.getDouble(it).toFloat() } }.orEmpty()
        )
    }
}

/** Klammertext eines Katalog-Namens: „Bankdrücken (Kurzhantel)“ → „Kurzhantel“. */
private fun variantKey(entry: CatalogExercise): String =
    entry.name.removePrefix(entry.base).trim().removeSurrounding("(", ")")

/** Wiki-Eintrag zu einer Übung (Katalog-Name oder Grundübung), null bei eigenen Übungen. */
fun wikiFor(name: String): WikiEntry? {
    val base = CATALOG.firstOrNull { it.name == name }?.base ?: name
    return Wiki.entries[base]
}

/** Übersetzter Anzeigename einer Grundübung. Englisch: kürzester EN-Name der Varianten (z. B. „Bench Press“). */
fun baseName(base: String): String {
    val variants = CATALOG.filter { it.base == base }
    return tr(base, variants.minByOrNull { it.en.length }?.en ?: base)
}

/** Kleiner ⓘ-Button, der das Wiki zu einer Übung öffnet (nur, wenn es einen Eintrag gibt). */
@Composable
fun WikiButton(name: String) {
    if (wikiFor(name) == null) return
    var open by remember { mutableStateOf(false) }
    IconAction(Ico.INFO, onClick = { open = true }, tint = MaterialTheme.colorScheme.primary)
    if (open) WikiSheet(name, onDismiss = { open = false })
}

/**
 * Wiki-Blatt, das von unten hereinfährt (ModalBottomSheet).
 * `name` = Katalog-Name (zeigt nur den Hinweis zu dieser Variante)
 * oder Grundübung (zeigt die Hinweise zu allen Varianten).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiSheet(name: String, onDismiss: () -> Unit) {
    if (wikiFor(name) == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { WikiContent(name, showHeader = true, showMuscles = true) }
    }
}

/**
 * Inhalt des Wikis (für das Blatt und den Reiter „Info“ der Übungsdetails).
 * showHeader = Symbol + Name, showMuscles = Körperdiagramm.
 */
@Composable
fun ColumnScope.WikiContent(name: String, showHeader: Boolean, showMuscles: Boolean) {
    val entry = wikiFor(name) ?: return
    val catalogEntry = CATALOG.firstOrNull { it.name == name }
    val base = catalogEntry?.base ?: name
    val group = catalogEntry?.group ?: CATALOG.first { it.base == base }.group
    // Welche Varianten-Hinweise zeigen? Bei einer konkreten Übung nur deren, sonst alle (mit Gerät davor)
    val notes: List<Pair<String, WikiText>> =
        if (catalogEntry != null) listOfNotNull(entry.variants[variantKey(catalogEntry)]?.let { catalogEntry.equipment.label to it })
        else CATALOG.filter { it.base == base }.mapNotNull { c ->
            entry.variants[variantKey(c)]?.let { exName(c.name) to it }
        }

    if (showHeader) Row(verticalAlignment = Alignment.CenterVertically) {
        ExerciseIcon(catalogEntry?.name ?: CATALOG.first { it.base == base }.name, group, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (catalogEntry != null) exName(name) else baseName(base),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                entry.muscles.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    if (showMuscles) musclesOf(name)?.let { MuscleMap(it.levels(), Modifier.padding(top = 8.dp)) }

    if (entry.how.isNotEmpty()) {
        WikiHeading(tr("Ausführung", "How to"))
        entry.how.forEachIndexed { i, t -> WikiLine("${i + 1}.", t.text) }
    }
    if (entry.tips.isNotEmpty()) {
        WikiHeading(tr("Darauf achten", "Watch out"))
        entry.tips.forEach { WikiLine("⚠", it.text, MaterialTheme.colorScheme.error) }
    }
    if (notes.isNotEmpty()) {
        WikiHeading(if (catalogEntry != null) tr("Speziell mit diesem Gerät", "With this equipment") else tr("Varianten", "Variations"))
        notes.forEach { (label, t) ->
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(t.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
    Text(
        tr(
            "Allgemeine Hinweise – ersetzen keine Einweisung. Bei Schmerzen abbrechen.",
            "General guidance – not a substitute for coaching. Stop if it hurts."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun WikiHeading(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

/** Zeile mit Aufzählungszeichen (Nummer oder ⚠) und hängendem Einzug. */
@Composable
private fun WikiLine(bullet: String, text: String, bulletColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row {
        Text(
            bullet,
            style = MaterialTheme.typography.bodyMedium,
            color = bulletColor,
            modifier = Modifier.width(24.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Wiki-Übersicht mit Reitern Übungen (Grundübungen nach Muskelgruppe) / Muskeln / Supplements, jeweils mit Suche. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiScreen(onBack: () -> Unit, onAsk: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 Übungen, 1 Muskeln, 2 Supplements
    var openMuscle by remember { mutableStateOf<String?>(null) }
    var openSupplement by remember { mutableStateOf<String?>(null) }
    // Grundübungen in Katalog-Reihenfolge, gruppiert
    val bases = CATALOG.distinctBy { it.base }.filter { Wiki.entries.containsKey(it.base) }
    val q = query.trim()
    val visible = bases.filter { c ->
        q.isEmpty() || CATALOG.any { it.base == c.base && (it.name.contains(q, true) || it.en.contains(q, true)) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Wiki", "Wiki")) },
            actions = { IconAction(Ico.CHAT, onClick = onAsk) }, // Frage an den Bot stellen
            navigationIcon = { IconAction(Ico.BACK, onClick = onBack) }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(tr("Übungen", "Exercises"), tr("Muskeln", "Muscles"), "Supplements").forEachIndexed { i, label ->
                        SegmentChip(label, tab == i, { tab = i; query = "" }, Modifier.weight(1f))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(when (tab) { 1 -> tr("Muskel suchen", "Search muscle"); 2 -> tr("Supplement suchen", "Search supplement"); else -> tr("Übung suchen", "Search exercise") }) },
                    leadingIcon = { AppIcon(Ico.SEARCH) },
                    trailingIcon = { if (query.isNotEmpty()) IconAction(Ico.CLOSE, onClick = { query = "" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (tab == 1) {
                muscleWikiItems(query) { openMuscle = it }
                return@LazyColumn
            }
            if (tab == 2) {
                supplementWikiItems(query) { openSupplement = it }
                return@LazyColumn
            }
            if (visible.isEmpty()) item {
                Text(
                    tr("Keine Übung gefunden.", "No exercise found."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            visible.sortedByName { baseName(it.base) }.groupBy { it.group }.toSortedMap().forEach { (group, list) -> // alphabetisch je Kategorie
                item(key = group.name) { GroupHeader(group) }
                items(list, key = { it.base }) { c ->
                    val entry = Wiki.entries.getValue(c.base)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open = c.base }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExerciseIcon(c.name, group, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(baseName(c.base))
                            Text(
                                entry.muscles.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    open?.let { WikiSheet(it, onDismiss = { open = null }) }
    openMuscle?.let { MuscleSheet(it, onDismiss = { openMuscle = null }) }
    openSupplement?.let { SupplementSheet(it, onDismiss = { openSupplement = null }) }
}
