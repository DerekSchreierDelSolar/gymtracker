package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ---------- Workout-Assistent im Bot („Erstelle mir ein Workout“) ----------
//
// Ablauf im Chat: 1. Muskeln in der Figur antippen (1× rot = Hauptfokus, 2× orange = mittel, 3× gelb = leicht,
// 4× aus), 2. Geräte wählen, 3. die App sucht passende Katalog-Übungen und legt das Workout an.
// Web: web/js/wizard.js (gleiche Logik).

/** Stufen beim Antippen: Hauptfokus → mittel → leicht → aus (Werte wie in der Heatmap: 1 / 0,6 / 0,3) */
val WIZARD_STAGES = listOf(1f, 0.6f, 0.3f)

fun nextStage(current: Float?): Float? = when (current) {
    null -> WIZARD_STAGES[0]
    WIZARD_STAGES[0] -> WIZARD_STAGES[1]
    WIZARD_STAGES[1] -> WIZARD_STAGES[2]
    else -> null
}

object WorkoutWizard {
    /** Muskel-ID → Stufe (nur gewählte Muskeln) */
    val muscles = mutableStateMapOf<String, Float>()
    /** Erlaubte Geräte; leer = alle */
    val equipment = mutableStateListOf<Equipment>()

    fun reset() {
        muscles.clear()
        equipment.clear()
    }

    fun tap(id: String) {
        val n = nextStage(muscles[id])
        if (n == null) muscles.remove(id) else muscles[id] = n
    }

    /** Geräte, die im Assistenten angeboten werden (Cardiogeräte passen nicht zu Muskelzielen) */
    val equipmentChoices: List<Equipment> get() = Equipment.entries.filter { it != Equipment.CARDIO_GERAET }

    /** Beitrag einer Übung je Muskel: Hauptmuskel 1, Hilfsmuskel 0,3 (stärkste Region zählt) */
    private fun contribution(name: String): Map<String, Float> {
        val levels = musclesOf(name)?.levels() ?: return emptyMap()
        val out = HashMap<String, Float>()
        for ((region, v) in levels) {
            val w = if (v >= 0.8f) 1f else 0.3f
            val m = muscleOfRegion(region)
            out[m] = maxOf(out[m] ?: 0f, w)
        }
        return out
    }

    /**
     * Übungen auswählen (gierig): Bedarf = Übungen mit dem Muskel als Hauptmuskel – Hauptfokus 3, mittel 1,5,
     * leicht 0,6 (Hilfsmuskel-Arbeit zählt 0,3). Jede Runde gewinnt die Übung, die den offenen Bedarf am meisten
     * deckt (gewichtet nach Stufe); Abzug für Hauptarbeit an nicht gewählten Muskeln, kleiner Bonus für schon
     * genutzte Übungen. Pro Grundübung eine Variante; mindestens 4 (wenn es passende gibt), höchstens 8 Übungen.
     * Gleiche Werte wie pickExercises in web/js/wizard.js.
     */
    fun pick(store: GymStore): List<String> {
        if (muscles.isEmpty()) return emptyList()
        val need = muscles.mapValues { (_, s) -> when (s) { 1f -> 3f; 0.6f -> 1.5f; else -> 0.6f } }.toMutableMap()
        val allowed = equipment.toSet()
        val candidates = CATALOG.filter { c ->
            !c.group.isCardio && (allowed.isEmpty() || c.equipment in allowed)
        }.mapNotNull { c -> contribution(c.name).takeIf { it.isNotEmpty() }?.let { c to it } }
        val chosen = mutableListOf<CatalogExercise>()
        while (chosen.size < 8 && (need.values.any { it > 0.01f } || chosen.size < 4)) {
            val filling = need.values.none { it > 0.01f } // Bedarf gedeckt, aber noch keine 4 Übungen
            var best: CatalogExercise? = null
            var bestScore = 0.15f
            for ((c, contrib) in candidates) {
                if (chosen.any { it.base == c.base }) continue
                var score = 0f
                for ((m, w) in contrib) {
                    val open = need[m]
                    if (open != null) {
                        if (open > 0f) score += minOf(open, w) * muscles.getValue(m)
                        else if (filling) score += w * muscles.getValue(m) * 0.5f
                    } else if (w >= 1f) score -= 0.3f // Hauptarbeit für einen nicht gewählten Muskel
                }
                if (store.hasExercise(c.name) || store.setsFor(c.name).isNotEmpty()) score += 0.1f
                if (score > bestScore) { best = c; bestScore = score }
            }
            val b = best ?: break
            chosen += b
            for ((m, w) in contribution(b.name)) need[m]?.let { need[m] = it - w }
        }
        return chosen.map { it.name }
    }

    /** Name aus den Hauptfokus-Muskeln, z. B. „Brust & Trizeps“; eindeutig gemacht (… 2) */
    fun workoutName(store: GymStore): String {
        val main = muscles.entries.sortedByDescending { it.value }.take(3).map { muscleLabel(it.key) }
        val base = main.joinToString(" & ").ifEmpty { tr("Mein Workout", "My workout") }
        if (store.workouts.none { it.name == base }) return base
        var i = 2
        while (store.workouts.any { it.name == "$base $i" }) i++
        return "$base $i"
    }

    /** Workout anlegen: Übungen in „Meine Übungen“ aufnehmen, Ziel 3 Sätze je Übung */
    fun create(store: GymStore, exercises: List<String>): Workout? {
        val w = store.addWorkout(workoutName(store)) ?: return null
        for (e in exercises) {
            store.toggleInWorkout(w.id, e)
            store.setTarget(w.id, e, 3)
        }
        return store.workout(w.id)
    }
}

/** Schritt 1 im Chat: Figur zum Antippen + alle Muskeln als Knöpfe (kleine Muskeln sind schwer zu treffen) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardMuscleStep(active: Boolean, onNext: () -> Unit) {
    val sel = WorkoutWizard.muscles
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyFigure(
            colorOf = { region -> levelColor(sel[muscleOfRegion(region)]) },
            widthFraction = 1f,
            onTapMuscle = if (active) { id -> WorkoutWizard.tap(id) } else null
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MUSCLES.keys.forEach { id ->
                val stage = sel[id]
                FilterChip(
                    selected = stage != null,
                    enabled = active,
                    onClick = { WorkoutWizard.tap(id) },
                    label = { Text(muscleLabel(id)) },
                    leadingIcon = levelColor(stage)?.let { c -> { LegendDot(c) } }
                )
            }
        }
        if (active) Button(onClick = onNext, enabled = sel.isNotEmpty(), modifier = Modifier.align(Alignment.End)) {
            Text(tr("Weiter", "Next"))
        }
    }
}

/** Schritt 2 im Chat: Geräte (Mehrfachauswahl, keine Auswahl = alle) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardEquipmentStep(active: Boolean, onCreate: () -> Unit) {
    val sel = WorkoutWizard.equipment
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FilterChip(
                selected = sel.isEmpty(), enabled = active,
                onClick = { sel.clear() },
                label = { Text(tr("Egal (alle)", "Any (all)")) }
            )
            WorkoutWizard.equipmentChoices.forEach { e ->
                FilterChip(
                    selected = e in sel, enabled = active,
                    onClick = { if (e in sel) sel.remove(e) else sel.add(e) },
                    label = { Text(e.label) }
                )
            }
        }
        if (active) Button(onClick = onCreate, modifier = Modifier.align(Alignment.End)) { Text(tr("Workout erstellen", "Create workout")) }
    }
}

/** Stufen-Namen im Assistenten (Hauptfokus statt „Hauptarbeit“ der Übungs-Info) */
fun wizardStageTitle(stage: Float): String = when (stage) {
    1f -> tr("Hauptfokus", "Main focus")
    0.6f -> tr("Mittel", "Moderate")
    else -> tr("Leicht", "Light")
}
