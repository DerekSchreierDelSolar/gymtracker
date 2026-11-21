package com.example.gymtracker

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

// ---------- Muskel-Wiki: Anatomie + medizinische Infos je Muskel ----------
//
// Inhalte in app/src/main/assets/muscles.json, Schlüssel = Muskel-ID aus MUSCLES (BodyMap.kt).
// Die Web-Version liest dieselbe Datei (gen-data.mjs → wiki-data.js, MUSCLE_INFO). Format pro Eintrag:
//   "chest": { "latin": "M. pectoralis major", "parts": [de, en], "origin": [de, en], "insertion": [de, en],
//              "nerve": [de, en], "everyday": [de, en], "function"/"problems"/"stretch"/"facts": [[de, en], …] }

data class MuscleInfo(
    val latin: String,
    val parts: WikiText,
    val origin: WikiText,
    val insertion: WikiText,
    val nerve: WikiText,
    val everyday: WikiText?,
    val function: List<WikiText>,
    val problems: List<WikiText>,
    val stretch: List<WikiText>,
    val facts: List<WikiText>
)

object MuscleWiki {
    /** Muskel-ID → Eintrag (Reihenfolge wie in der Datei). */
    var entries: Map<String, MuscleInfo> = emptyMap()
        private set

    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        val json = JSONObject(context.assets.open("muscles.json").bufferedReader().use { it.readText() })
        fun pair(a: JSONArray) = WikiText(a.getString(0), a.getString(1))
        fun pairs(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { pair(a.getJSONArray(it)) }
        entries = json.keys().asSequence().filter { it in MUSCLES }.associateWith { id ->
            val o = json.getJSONObject(id)
            MuscleInfo(
                latin = o.getString("latin"),
                parts = pair(o.getJSONArray("parts")),
                origin = pair(o.getJSONArray("origin")),
                insertion = pair(o.getJSONArray("insertion")),
                nerve = pair(o.getJSONArray("nerve")),
                everyday = o.optJSONArray("everyday")?.let(::pair),
                function = pairs(o.optJSONArray("function")),
                problems = pairs(o.optJSONArray("problems")),
                stretch = pairs(o.optJSONArray("stretch")),
                facts = pairs(o.optJSONArray("facts"))
            )
        }
    }
}

/** Grundübungen (ohne Cardio), bei denen der Muskel Hauptarbeit leistet – alphabetisch. */
fun mainExercisesFor(muscle: String): List<String> =
    Wiki.entries.keys
        .filter { b -> CATALOG.any { it.base == b && !it.group.isCardio } }
        .filter { b -> musclesOf(b)?.levels()?.any { (k, v) -> muscleOfRegion(k) == muscle && v >= 0.8f } == true }
        .sortedByName { baseName(it) }

/** Muskel-Blatt (von unten), z. B. aus dem Wiki oder durch Antippen der Figur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleSheet(id: String, onDismiss: () -> Unit) {
    if (id !in MuscleWiki.entries) return
    var exercise by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { MuscleContent(id, onExercise = { exercise = it }) }
    }
    exercise?.let { WikiSheet(it, onDismiss = { exercise = null }) }
}

@Composable
fun ColumnScope.MuscleContent(id: String, onExercise: (String) -> Unit) {
    val m = MuscleWiki.entries[id] ?: return
    Text(muscleLabel(id), style = MaterialTheme.typography.titleLarge)
    Text(m.latin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    // Figur: der ganze Muskel (alle Regionen) rot
    BodyFigure({ key -> if (muscleOfRegion(key) == id) PRIMARY_COLOR else null }, Modifier.padding(vertical = 8.dp))
    Text(m.parts.text, style = MaterialTheme.typography.bodyMedium)

    MuscleHeading(tr("Anatomie", "Anatomy"))
    MuscleFact(tr("Ursprung", "Origin"), m.origin.text)
    MuscleFact(tr("Ansatz", "Insertion"), m.insertion.text)
    MuscleFact(tr("Nerv", "Nerve"), m.nerve.text)

    if (m.function.isNotEmpty()) {
        MuscleHeading(tr("Funktion", "Function"))
        m.function.forEach { MuscleBullet("•", it.text) }
    }
    m.everyday?.let {
        MuscleHeading(tr("Im Alltag", "In everyday life"))
        Text(it.text, style = MaterialTheme.typography.bodyMedium)
    }
    if (m.problems.isNotEmpty()) {
        MuscleHeading(tr("Häufige Beschwerden & Verletzungen", "Common problems & injuries"))
        m.problems.forEach { MuscleBullet("⚠", it.text, MaterialTheme.colorScheme.error) }
    }
    if (m.stretch.isNotEmpty()) {
        MuscleHeading(tr("Dehnen", "Stretching"))
        m.stretch.forEach { MuscleBullet("↔", it.text) }
    }
    if (m.facts.isNotEmpty()) {
        MuscleHeading(tr("Wissenswertes", "Good to know"))
        m.facts.forEach { MuscleBullet("💡", it.text) }
    }
    val exercises = mainExercisesFor(id)
    if (exercises.isNotEmpty()) {
        MuscleHeading(tr("Übungen (Hauptarbeit)", "Exercises (main work)"))
        exercises.forEach { base ->
            val first = CATALOG.first { it.base == base }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onExercise(base) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExerciseIcon(first.name, first.group, Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(baseName(base), Modifier.weight(1f))
                AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(
        tr(
            "Allgemeine Informationen, keine Diagnose und kein Ersatz für ärztlichen Rat. Bei starken oder anhaltenden Schmerzen, Taubheit oder Lähmungen ärztlich abklären.",
            "General information, not a diagnosis or a substitute for medical advice. See a doctor for severe or persistent pain, numbness or weakness."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun MuscleHeading(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

/** „Ursprung: …“ – Titel fett über dem Text */
@Composable
private fun MuscleFact(label: String, text: String) {
    Column(Modifier.padding(bottom = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MuscleBullet(bullet: String, text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row {
        Text(bullet, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Muskeln, die zur Suche passen (Name DE/EN, Lateinname, Teile). */
fun muscleSearch(query: String): List<String> {
    val q = query.trim()
    return MuscleWiki.entries.filter { (id, m) ->
        q.isEmpty() || listOf(MUSCLES.getValue(id).de, MUSCLES.getValue(id).en, m.latin, m.parts.de, m.parts.en).any { it.contains(q, true) }
    }.keys.toList().sortedByName { muscleLabel(it) }
}

/** Reiter „Muskeln“ im Wiki: antippbare Figur (ohne Suche) + Liste. */
fun LazyListScope.muscleWikiItems(query: String, onOpen: (String) -> Unit) {
    val list = muscleSearch(query)
    if (query.isBlank()) item(key = "figure") {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BodyFigure({ null }, onTapMuscle = { if (it in MuscleWiki.entries) onOpen(it) })
            Text(
                tr("Muskel antippen oder aus der Liste wählen", "Tap a muscle or pick one from the list"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    if (list.isEmpty()) item {
        Text(
            tr("Kein Muskel gefunden.", "No muscle found."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
    items(list, key = { "m-$it" }) { id ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(id) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(muscleLabel(id))
                Text(
                    MuscleWiki.entries.getValue(id).latin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppIcon(Ico.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
