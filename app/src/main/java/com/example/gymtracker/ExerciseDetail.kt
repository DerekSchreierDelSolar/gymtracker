package com.example.gymtracker

import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

// ---------- Eingabezeile für einen Satz (Detailansicht und Trainingsmodus) ----------

/**
 * kg + Wdh. (bzw. Minuten bei Cardio, Sekunden bei Zeitübungen) mit Button.
 * Vorbelegt mit dem letzten Satz der Übung.
 * `remember(exercise)` setzt die Felder zurück, sobald eine andere Übung angezeigt wird.
 * `weightOverride`: wird gesetzt, wenn der Nutzer einen Gewichts-Vorschlag übernimmt.
 * `startWithWarmup`: erster Satz ist vorausgewählt „Aufwärmen“ (Training: Übung heute noch nicht gemacht).
 */
@Composable
fun SetInput(
    store: GymStore,
    exercise: String,
    onAdded: () -> Unit = {},
    weightOverride: Double? = null,
    startWithWarmup: Boolean = false,
    goalReps: Int? = null
) {
    Column { // Stoppuhr, Eingabezeile und −/+ untereinander
    val cardio = store.groupOf(exercise).isCardio
    val timed = isTimed(exercise)
    // Satz-Typ des nächsten Satzes; springt nach jedem Satz auf Normal zurück (erster Satz im Training ggf. Aufwärmen)
    var type by remember(exercise) { mutableStateOf(if (startWithWarmup) SetType.WARMUP else SetType.NORMAL) }
    // Vorbelegung passend zum Satz-Typ (Aufwärmen leichter, Arbeitssatz mit Wdh.-Ziel)
    fun prefill(t: SetType): Pair<String, String> {
        val (w, r) = setPrefill(store.setsFor(exercise), t, goalReps)
        return (w?.let { fmtWU(it) } ?: "") to (r?.toString() ?: "")
    }
    val initial = remember(exercise) { prefill(type) }
    var weightText by remember(exercise) { mutableStateOf(initial.first) }
    var repsText by remember(exercise) { mutableStateOf(initial.second) }
    // Typ wechseln: unveränderte Felder gehen mit (eigene Eingaben bleiben stehen)
    fun changeType(t: SetType) {
        if (weightText to repsText == prefill(type)) prefill(t).let { weightText = it.first; repsText = it.second }
        type = t
    }
    // „+“ mit ungültiger Eingabe getippt → Felder rot markieren (bis zum nächsten gültigen Satz)
    var tried by remember(exercise) { mutableStateOf(false) }
    // Ungewöhnlicher Wert (Tippfehler?) → Nachfrage: Gewicht, Wdh. und Hinweistext
    var confirmOdd by remember(exercise) { mutableStateOf<Triple<Double, Int, String>?>(null) }
    // Nach einem Arbeitssatz: freiwillige Abfrage „Wdh. in Reserve“ (Effort.kt); ID des Satzes, null = keine Abfrage
    var rirFor by remember(exercise) { mutableStateOf<Long?>(null) }
    // Übernommener Vorschlag überschreibt das Gewichtsfeld
    LaunchedEffect(weightOverride) { if (weightOverride != null) weightText = fmtWU(weightOverride) }

    // Stoppuhr für Zeitübungen: Startzeitpunkt (null = steht)
    // Läuft global je Übung (stopwatchStarts), damit sie beim Wischen im Training nicht zurückgesetzt wird
    val watchStart = stopwatchStarts[exercise]
    fun setWatch(v: Long?) { if (v == null) stopwatchStarts.remove(exercise) else stopwatchStarts[exercise] = v }
    var watchNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(watchStart) {
        while (watchStart != null) {
            watchNow = System.currentTimeMillis()
            delay(100)
        }
    }

    if (timed) {
        val running = watchStart != null
        val elapsedSec = watchStart?.let { ((watchNow - it) / 1000).toInt().coerceAtLeast(0) }
            ?: (repsText.toIntOrNull() ?: 0)
        Card(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Stoppuhr", "Stopwatch"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${elapsedSec / 60}:${(elapsedSec % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        if (running) {
                            // Stopp: gemessene Sekunden ins Feld übernehmen
                            val sec = ((System.currentTimeMillis() - watchStart!!) / 1000).toInt()
                            repsText = sec.coerceAtLeast(1).toString()
                            setWatch(null)
                        } else {
                            watchNow = System.currentTimeMillis()
                            setWatch(watchNow)
                        }
                    },
                    colors = if (running) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.buttonColors()
                ) {
                    if (!running) AppIcon(Ico.PLAY, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) tr("Stopp", "Stop") else tr("Start", "Start"))
                }
            }
        }
    }

    // Satz speichern (nach gültiger Eingabe bzw. nach Bestätigung eines ungewöhnlichen Werts)
    fun add(w: Double, r: Int) {
        val added = store.addSet(exercise, w, r, type)
        rirFor = added.id.takeIf { type != SetType.WARMUP && !cardio && !timed }
        // Nach dem Aufwärmen: Felder auf den Arbeitssatz umstellen
        if (type == SetType.WARMUP) prefill(SetType.NORMAL).let { weightText = it.first; repsText = it.second }
        type = SetType.NORMAL
        setWatch(null)
        tried = false
        onAdded()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Satz-Typ: antippen = nächster Typ (N → A → D → V); bei Cardio nicht sinnvoll
        if (!cardio) {
            SetTypeButton(type) { changeType(SetType.entries[(type.ordinal + 1) % SetType.entries.size]) }
            Spacer(Modifier.width(8.dp))
        }
        // Gewicht: bei Kraftübungen immer, bei Zeitübungen nur, wenn ein Gerät im Spiel ist
        if (!cardio && (!timed || equipmentOf(exercise) != Equipment.KOERPER)) {
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it; tried = false },
                label = { Text(wUnit) },
                singleLine = true,
                isError = tried && parseW(weightText).let { (it == null && !timed) || (it != null && it < 0) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
        }
        OutlinedTextField(
            value = repsText,
            onValueChange = { repsText = it; tried = false },
            isError = tried && (repsText.trim().toIntOrNull() ?: 0) <= 0,
            label = {
                Text(
                    when {
                        cardio -> tr("Minuten", "Minutes")
                        timed -> tr("Sekunden", "Seconds")
                        else -> tr("Wdh.", "Reps")
                    }
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            // Komma als Dezimaltrenner erlauben; bei Cardio (und Zeitübungen ohne Gerät) ist das Gewicht 0
            val w = if (cardio) 0.0 else parseW(weightText) ?: if (timed) 0.0 else null
            val r = repsText.trim().toIntOrNull()
            if (w != null && w >= 0 && r != null && r > 0) { // wie EditSetDialog: kein negatives Gewicht
                val odd = implausibleSet(store, exercise, w, r)
                if (odd != null) confirmOdd = Triple(w, r, odd) else add(w, r)
            } else tried = true // statt still nichts zu tun: Felder rot markieren
        }, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }
    }
    // Schnell ändern ohne Tastatur: −/+ unter den Feldern (Gewicht ±2,5 kg; Wdh. ±1, Minuten ±1, Sekunden ±5)
    val showWeight = !cardio && (!timed || equipmentOf(exercise) != Equipment.KOERPER)
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!cardio) Spacer(Modifier.width(64.dp + 8.dp)) // unter dem Satz-Typ-Knopf
        if (showWeight) {
            StepButtons(Modifier.weight(1f), if (isLb) "5" else tr("2,5", "2.5")) { d ->
                // in der angezeigten Einheit rechnen (5 lbs bzw. 2,5 kg pro Tipp)
                val v = (weightText.replace(',', '.').toDoubleOrNull() ?: 0.0) + d * (if (isLb) 5.0 else 2.5)
                weightText = fmtW(v.coerceAtLeast(0.0))
            }
            Spacer(Modifier.width(8.dp))
        }
        StepButtons(Modifier.weight(1f), if (timed) "5" else "1") { d ->
            val v = (repsText.trim().toIntOrNull() ?: 0) + d * (if (timed) 5 else 1)
            repsText = v.coerceAtLeast(1).toString()
        }
        Spacer(Modifier.width(56.dp + 8.dp)) // unter dem ＋
    }
    // Aufwärm-Rampe (Warmup.kt)
    if (showWeight && !timed) SetHelpers(exercise, weightText) { w, r ->
        weightText = fmtWU(w); repsText = r.toString(); type = SetType.WARMUP
    }
    if (tried) Text(
        when {
            cardio -> tr("Bitte Minuten eintragen.", "Please enter minutes.")
            timed -> tr("Bitte Sekunden eintragen (oder Stoppuhr nutzen).", "Please enter seconds (or use the stopwatch).")
            else -> tr("Bitte Gewicht und Wiederholungen eintragen.", "Please enter weight and reps.")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
    )
    rirFor?.let { id -> RirPrompt(onPick = { store.setRir(id, it); rirFor = null }, onSkip = { rirFor = null }) }
    confirmOdd?.let { (w, r, text) ->
        AlertDialog(
            onDismissRequest = { confirmOdd = null },
            title = { Text(tr("Wirklich eintragen?", "Log this set?")) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirmOdd = null; add(w, r) }) { Text(tr("Ja, eintragen", "Yes, log it")) } },
            dismissButton = { TextButton(onClick = { confirmOdd = null }) { Text(tr("Korrigieren", "Fix it")) } }
        )
    }
    }
}

/**
 * Tippfehler-Schutz: Hinweistext, wenn ein Satz ungewöhnlich ist (z. B. 800 statt 80 kg) – sonst null.
 * Ein falscher Rekord bliebe sonst dauerhaft (Rekorde, XP, Abzeichen, Diagramme). Web: implausibleSet in exercise.js.
 * Gewicht: mehr als 1,5× und mindestens 10 kg über dem bisherigen Höchstgewicht der Übung (ohne Verlauf: über 300 kg).
 */
fun implausibleSet(store: GymStore, exercise: String, weight: Double, reps: Int): String? {
    val cardio = store.groupOf(exercise).isCardio
    val timed = isTimed(exercise)
    if (cardio) return if (reps > 300) tr("$reps Minuten sind über 5 Stunden. Tippfehler?", "$reps minutes is over 5 hours. Typo?") else null
    if (timed && reps > 1800) return tr("$reps Sekunden sind über 30 Minuten. Tippfehler?", "$reps seconds is over 30 minutes. Typo?")
    if (!timed && reps > 100) return tr("$reps Wiederholungen in einem Satz? Tippfehler?", "$reps reps in one set? Typo?")
    val best = store.setsOf(exercise).maxOfOrNull { it.weight } ?: 0.0
    return when {
        best > 0 && weight > best * 1.5 && weight - best >= 10 -> tr(
            "${fmtKg(weight)} ist deutlich mehr als dein bisheriges Höchstgewicht (${fmtKg(best)}). Tippfehler?",
            "${fmtKg(weight)} is far more than your heaviest so far (${fmtKg(best)}). Typo?"
        )
        best == 0.0 && weight > 300 -> tr("${fmtKg(weight)}? Tippfehler?", "${fmtKg(weight)}? Typo?")
        else -> null
    }
}

/**
 * Vorbelegung (Gewicht, Wdh.) für den nächsten Satz; `sets` = Sätze der Übung, neueste zuerst.
 * Aufwärmen: letzter Aufwärmsatz, sonst halbes Arbeitsgewicht (auf 2,5 kg gerundet) × 10.
 * Sonst: letzter Arbeitssatz (Normal/Versagen), Wdh. = Ziel aus dem Trainingsmodus, falls bekannt.
 * Web: setPrefill in exercise.js.
 */
fun setPrefill(sets: List<GymSet>, type: SetType, goalReps: Int?): Pair<Double?, Int?> {
    val top = sets.firstOrNull { it.isTop } ?: sets.firstOrNull()
    if (type == SetType.WARMUP) {
        sets.firstOrNull { it.type == SetType.WARMUP }?.let { return it.weight to it.reps }
        if (top != null) return roundPlate(top.weight * 0.5) to 10
    }
    return top?.weight to (goalReps ?: top?.reps)
}

/** Zwei kleine Knöpfe „− x“ / „+ x“; onStep(−1 oder +1). */
@Composable
private fun StepButtons(modifier: Modifier, step: String, onStep: (Int) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(-1 to "−", 1 to "+").forEach { (d, sign) ->
            FilledTonalButton(
                onClick = { onStep(d) },
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("$sign$step", style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        }
    }
}

// ---------- Einzelner Satz: Zeile + Bearbeiten-Dialog ----------

/**
 * Eine Zeile „1. 60 kg × 8“ mit Stift-Icon. Der Stift öffnet einen Dialog zum Ändern oder Löschen.
 * Wird im Übungs-Verlauf und in der „Heute“-Liste des Trainingsmodus verwendet.
 */
/** Umschalter für den Satz-Typ: Kürzel groß (N/A/D/V), darunter der Name; gleiche Höhe wie die Eingabefelder. */
@Composable
fun SetTypeButton(type: SetType, onClick: () -> Unit) {
    val color = if (type == SetType.NORMAL) MaterialTheme.colorScheme.primary else type.color
    Column(
        Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(type.short.ifEmpty { "N" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(type.label, fontSize = 10.sp, maxLines = 1, color = color)
    }
}

/** Kleiner farbiger Hinweis „Drop“/„Versagen“/„Aufwärmen“ hinter einem Satz (Normal: nichts). */
@Composable
fun SetTypeTag(type: SetType) {
    if (type == SetType.NORMAL) return
    Text(
        type.label,
        style = MaterialTheme.typography.labelSmall,
        color = type.color,
        modifier = Modifier
            .padding(start = 8.dp)
            .border(1.dp, type.color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/** `label` = „1.“, „2.“ … bzw. „A“ bei Aufwärmsätzen (siehe setLabels). */
@Composable
fun SetRow(store: GymStore, set: GymSet, label: String, cardio: Boolean) {
    var editing by remember { mutableStateOf(false) }
    // Ganze Zeile antippen = bearbeiten (kein Stift-Symbol)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { editing = true; Hints.used(Hints.SET) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Aufwärmsätze grau, damit die Arbeitssätze hervorstechen
        val muted = if (set.isWork) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
        Text(label, color = if (set.isWork) Color.Unspecified else set.type.color, fontWeight = if (set.isWork) null else FontWeight.Bold)
        Text(" ${fmtSet(set, cardio)}", color = muted)
        if (set.type != SetType.WARMUP) SetTypeTag(set.type)
        set.rir?.let { Text(" · " + rirLabel(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.weight(1f))
        Text(
            SimpleDateFormat("HH:mm", appLocale()).format(Date(set.time)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (editing) {
        EditSetDialog(
            set = set,
            cardio = cardio,
            onDismiss = { editing = false },
            onSave = { w, r, t, rir -> store.updateSet(set, w, r, t, rir); editing = false },
            onDelete = {
                store.deleteSet(set)
                editing = false
                Undo.show(tr("Satz gelöscht", "Set deleted")) { store.restoreSet(set) }
            }
        )
    }
}

/**
 * Dialog zum Ändern eines Satzes. „Löschen“ löscht sofort; unten erscheint „Rückgängig“ (Undo.kt).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSetDialog(
    set: GymSet,
    cardio: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double, Int, SetType, Int?) -> Unit,
    onDelete: () -> Unit
) {
    var weightText by remember { mutableStateOf(fmtWU(set.weight)) }
    var repsText by remember { mutableStateOf(set.reps.toString()) }
    var type by remember { mutableStateOf(set.type) }
    var rir by remember { mutableStateOf(set.rir) }

    val w = if (cardio) 0.0 else parseW(weightText)
    val r = repsText.trim().toIntOrNull()
    val valid = w != null && w >= 0 && r != null && r > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Eintrag bearbeiten", "Edit entry")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    fmtDateTime(set.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!cardio) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text(wUnit) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it },
                        label = {
                            Text(
                                when {
                                    cardio -> tr("Minuten", "Minutes")
                                    isTimed(set.exercise) -> tr("Sekunden", "Seconds")
                                    else -> tr("Wdh.", "Reps")
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                // Satz-Typ (nicht bei Cardio)
                if (!cardio) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SetType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                        }
                    }
                }
                // Wdh. in Reserve (optional, nur Kraft mit Wiederholungen)
                if (!cardio && !isTimed(set.exercise) && type != SetType.WARMUP) {
                    Text(tr("Wdh. in Reserve (optional)", "Reps in reserve (optional)"), style = MaterialTheme.typography.labelLarge)
                    RirChips(rir) { rir = it }
                }
                // Löschen: rot, sofort (mit „Rückgängig“ in der Leiste unten)
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    AppIcon(Ico.DELETE, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Eintrag löschen", "Delete entry"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(w!!, r!!, type, rir.takeIf { type != SetType.WARMUP }) }, enabled = valid) { Text(tr("Speichern", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

// ---------- Bildschirm 2: Übung mit Sätzen & Fortschritt ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(store: GymStore, exercise: String, onBack: () -> Unit) {
    val group = store.groupOf(exercise)
    val cardio = group.isCardio
    val timed = isTimed(exercise)
    val history = store.setsFor(exercise)
    var confirmDelete by remember { mutableStateOf(false) }
    var editMuscles by remember { mutableStateOf(false) }
    // Vom Nutzer übernommener Gewichts-Vorschlag (füllt das kg-Feld)
    var appliedWeight by remember { mutableStateOf<Double?>(null) }

    // Reiter: Training (Eingabe + Verlauf), Fortschritt, Info (Muskeln + Wiki)
    val tabs = listOf(tr("Training", "Training"), tr("Fortschritt", "Progress"), tr("Info", "Info"))
    var tab by rememberSaveable(exercise) { mutableIntStateOf(0) }

    // Nach Tag gruppiert, neuester Tag zuerst
    val days = history.groupBy { fmtDate(it.time) }

    // Übungsbild als durchsichtiger Hintergrund; Kopfzeile und Reiter dann ebenfalls durchsichtig
    val img = hasExerciseImage(exercise)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    ExerciseBackdrop(exercise)
    Scaffold(
        containerColor = if (img) Color.Transparent else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
        Column {
            TopAppBar(
                colors = if (img) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
                else TopAppBarDefaults.topAppBarColors(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseIcon(exercise, group, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(exName(exercise))
                    }
                },
                navigationIcon = { IconAction(Ico.BACK, onClick = onBack) },
                actions = {
                    FavoriteButton(store, exercise)
                    IconAction(Ico.DELETE, onClick = { confirmDelete = true })
                }
            )
            PrimaryTabRow(
                selectedTabIndex = tab.coerceAtMost(tabs.size - 1),
                containerColor = if (img) Color.Transparent else TabRowDefaults.primaryContainerColor
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        text = { Text(label, maxLines = 1) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding() // Tastatur verdeckt sonst die Satz-Eingabe (Edge-to-Edge)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (tab) {
                // ----- Training: Rekord-Zeile, Vorschlag, Eingabe, Verlauf pro Tag -----
                0 -> {
                    item { RecordLine(history, cardio, timed) }
                    item { ProgressionHint(store, exercise, onApply = { appliedWeight = it }) }
                    item { SetInput(store, exercise, weightOverride = appliedWeight) }
                    item { NoteButton(store, exercise) }
                    if (days.isNotEmpty()) item {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(tr("Verlauf", "History"), style = MaterialTheme.typography.titleMedium)
                            if (Hints.show(Hints.SET)) Text(
                                tr("Satz antippen zum Bearbeiten", "Tap a set to edit"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    days.forEach { (day, daySets) ->
                        item(key = day) { HistoryDayCard(store, day, daySets, cardio, timed) }
                    }
                }

                // ----- Fortschritt: Rekord, Zuwachs, Diagramm -----
                1 -> item { ExerciseProgress(history, cardio, timed) }

                // ----- Info: Muskeln (Vorder-/Rückseite) + Wiki (Ausführung, Hinweise) -----
                else -> {
                    val muscles = musclesOf(exercise)
                    val custom = catalogGroup(exercise) == null
                    if (muscles != null) {
                        item { MuscleSideCard(BodyView.FRONT, muscles.levels(), colors = glassCardColors()) }
                        item { MuscleSideCard(BodyView.BACK, muscles.levels(), showList = true, colors = glassCardColors()) }
                    } else item {
                        Text(tr("Noch keine Muskeln festgelegt.", "No muscles set yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (custom) item {
                        TextButton(onClick = { editMuscles = true }) {
                            Text(if (muscles != null) tr("Muskeln bearbeiten", "Edit muscles") else tr("Muskeln festlegen", "Set muscles"))
                        }
                    }
                    if (wikiFor(exercise) != null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            WikiContent(exercise, showHeader = false, showMuscles = false)
                        }
                    }
                }
            }
        }
    }

    }

    if (editMuscles) MuscleEditDialog(store, exercise, onDismiss = { editMuscles = false })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Übung löschen?", "Delete exercise?")) },
            text = {
                Text(
                    tr(
                        "„${exName(exercise)}“ und alle zugehörigen Sätze werden gelöscht.",
                        "\"${exName(exercise)}\" and all of its sets will be deleted."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.deleteExercise(exercise)
                    onBack()
                }) { Text(tr("Löschen", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(tr("Abbrechen", "Cancel")) }
            }
        )
    }
}



// ---------- Reiter „Fortschritt“ ----------

/** Gruppierung der Fortschritts-Werte. */
private enum class ProgressBucket(val de: String, val en: String) { DAY("Tage", "Days"), WEEK("Wochen", "Weeks"), MONTH("Monate", "Months") }

/** Kennzahl im Diagramm (Kraftübungen). */
private enum class ProgressMetric(val de: String, val en: String) { MAX("Max. Gewicht", "Max weight"), ORM("Geschätztes 1RM", "Estimated 1RM"), VOLUME("Volumen", "Volume") }

/**
 * Rekord, Zuwachs seit dem ersten Training und Verlaufsdiagramm.
 * Kraft: Max. Gewicht / geschätztes 1RM / Volumen; Cardio: Minuten; Zeitübungen: längste Zeit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseProgress(allSets: List<GymSet>, cardio: Boolean, timed: Boolean) {
    // Aufwärm- und Drop-Sätze zählen nicht für Rekord und Fortschritt
    val history = allSets.top()
    var bucket by rememberSaveable { mutableStateOf(ProgressBucket.DAY) }
    var metric by rememberSaveable { mutableStateOf(ProgressMetric.MAX) }
    if (history.isEmpty()) {
        Text(tr("Noch keine Sätze eingetragen.", "No sets logged yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val strength = !cardio && !timed
    val unit = when { cardio -> "min"; timed -> "s"; else -> wUnit }

    // Wert eines Zeitabschnitts
    fun value(l: List<GymSet>): Double = when {
        cardio -> l.sumOf { it.reps }.toDouble()
        timed -> l.maxOf { it.reps }.toDouble()
        metric == ProgressMetric.ORM -> toUnit(Math.round(l.maxOf { oneRepMax(it.weight, it.reps) } * 2) / 2.0)
        metric == ProgressMetric.VOLUME -> toUnit(l.sumOf { it.weight * it.reps })
        else -> toUnit(l.maxOf { it.weight })
    }
    // Zeitabschnitte, älteste zuerst
    val key: (Long) -> Int = when (bucket) {
        ProgressBucket.DAY -> { t -> dayKey(t) }
        ProgressBucket.WEEK -> { t -> dayKey(startOfWeek(t)) }
        ProgressBucket.MONTH -> { t -> monthIndex(t) }
    }
    val groups = history.sortedBy { it.time }.groupBy { key(it.time) }.values.toList()
    val values = groups.map(::value)

    // Rekord und Zuwachs (Tageswerte, unabhängig von der Gruppierung)
    val perDay = history.sortedBy { it.time }.groupBy { dayKey(it.time) }.values.map(::value)
    val record = perDay.max()
    val gain = record - perDay.first()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), colors = glassCardColors()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Rekord", "Record"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("🏆 ${fmtW(record)} $unit", style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(tr("seit dem ersten Training", "since first session"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (if (gain > 0) "+" else "") + "${fmtW(gain)} $unit" + if (gain > 0) " ↑" else "",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (gain > 0) completionColor(1f) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (strength) {
            val best = history.maxBy { it.weight }
            Text(
                tr("Bester Satz: ", "Best set: ") + fmtSet(best, false) + " · " + tr("geschätztes 1RM ", "estimated 1RM ") +
                    "${fmtKg(Math.round(history.maxOf { oneRepMax(it.weight, it.reps) } * 2) / 2.0)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressMetric.entries.forEach { m ->
                    FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(tr(m.de, m.en)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressBucket.entries.forEach { b ->
                FilterChip(selected = bucket == b, onClick = { bucket = b }, label = { Text(tr(b.de, b.en)) })
            }
        }
        if (values.size >= 2) {
            val title = when {
                cardio -> tr("Minuten", "Minutes")
                timed -> tr("Längste Zeit", "Longest hold")
                else -> tr(metric.de, metric.en)
            } + " " + when (bucket) {
                ProgressBucket.DAY -> tr("pro Trainingstag", "per training day")
                ProgressBucket.WEEK -> tr("pro Woche", "per week")
                ProgressBucket.MONTH -> tr("pro Monat", "per month")
            }
            ProgressChart(values, title = title, unit = unit, colors = glassCardColors())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtShortDate(groups.first().first().time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtShortDate(groups.last().first().time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                tr("Ab zwei Einträgen in dieser Einteilung erscheint hier ein Diagramm.", "A chart appears once there are two entries for this grouping."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Prozente vom 1RM (Effort.kt)
        if (strength) OneRmTable(Math.round(history.maxOf { oneRepMax(it.weight, it.reps) } * 2) / 2.0)
    }
}

/** Leicht durchsichtige Karten (Übungsdetails mit Hintergrundbild) – überall gleich. */
@Composable
fun glassCardColors(): CardColors =
    CardDefaults.cardColors(
        containerColor = CardDefaults.cardColors().containerColor.copy(alpha = 0.82f),
        // Schriftfarbe ausdrücklich setzen: bei durchsichtigem Hintergrund kann Compose sie nicht ableiten (sonst Schwarz)
        contentColor = MaterialTheme.colorScheme.onSurface
    )

/**
 * Motivierende Zeile mit dem Rekord (oben im Reiter „Training“):
 * erstes Mal / neuer Rekord heute / bisheriger Rekord mit Ansporn.
 * Kraft: schwerster Satz (bei Gleichstand mehr Wdh.); Cardio und Zeitübungen: längste Einheit.
 */
@Composable
fun RecordLine(allSets: List<GymSet>, cardio: Boolean, timed: Boolean) {
    val history = allSets.top() // ohne Aufwärm-/Drop-Sätze
    val today = dayKey(System.currentTimeMillis())
    val best = if (cardio || timed) history.maxByOrNull { it.reps }
    else history.maxWithOrNull(compareBy<GymSet>({ it.weight }, { it.reps }))
    val before = history.filter { dayKey(it.time) != today }
    val beatsOld = best != null && dayKey(best.time) == today && before.isNotEmpty() &&
        before.none { if (cardio || timed) it.reps >= best.reps else it.weight > best.weight || (it.weight == best.weight && it.reps >= best.reps) }

    val (emoji, text) = when {
        best == null -> "💪" to tr("Erstes Mal – heute setzt du deinen ersten Rekord!", "First time – today you set your first record!")
        beatsOld -> "🔥" to tr("Neuer Rekord heute: ", "New record today: ") + fmtSet(best, cardio) + tr(" – stark!", " – great job!")
        else -> "🏆" to tr("Rekord: ", "Record: ") + fmtSet(best, cardio) + " (${fmtDate(best.time)})" +
            tr(" – schaffst du heute mehr?", " – can you beat it today?")
    }
    Card(Modifier.fillMaxWidth(), colors = glassCardColors()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * Karte eines Trainingstags im Verlauf (Übungsdetails und Trainingsmodus):
 * Kopfzeile mit Sätzen/Volumen, darunter die Sätze – neuester oben, Zeile antippen = bearbeiten.
 */
@Composable
fun HistoryDayCard(store: GymStore, day: String, daySets: List<GymSet>, cardio: Boolean, timed: Boolean) {
    val work = daySets.work() // Kopfzeile ohne Aufwärmsätze
    Card(Modifier.fillMaxWidth(), colors = glassCardColors()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (cardio)
                    "$day · " + plural(work.size, tr("Einheit", "session"), tr("Einheiten", "sessions")) +
                        " · ${work.sumOf { it.reps }} " + tr("Min gesamt", "min total")
                else if (timed)
                    "$day · " + plural(work.size, tr("Satz", "set"), tr("Sätze", "sets")) +
                        " · " + tr("gesamt ", "total ") + fmtSeconds(work.sumOf { it.reps })
                else
                    "$day · " + plural(work.size, tr("Satz", "set"), tr("Sätze", "sets")) +
                        " · ${fmtVol(work.sumOf { it.weight * it.reps })} " + tr("Volumen", "volume"),
                style = MaterialTheme.typography.titleSmall
            )
            daySets.firstOrNull()?.let { store.noteFor(it.exercise, dayKey(it.time)) }?.let { note ->
                Text(
                    "📝 $note",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Neuester Satz oben; Nummer bleibt die Reihenfolge im Training
            val sorted = daySets.sortedBy { it.time }
            val labels = setLabels(sorted)
            sorted.indices.reversed().forEach { i -> SetRow(store, sorted[i], labels[i], cardio) }
        }
    }
}

/**
 * „📝 Notiz“ für heute: zeigt die Notiz der Übung (Tag = heute) oder „Notiz hinzufügen“;
 * Tippen öffnet einen Dialog. Gespeichert pro Übung und Tag (GymStore.setNote).
 */
@Composable
fun NoteButton(store: GymStore, exercise: String) {
    val today = dayKey(System.currentTimeMillis())
    val note = store.noteFor(exercise, today)
    var editing by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { editing = true }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📝", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(
            note ?: tr("Notiz für heute hinzufügen", "Add a note for today"),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (note != null) FontStyle.Italic else FontStyle.Normal,
            color = if (note != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
        )
    }
    if (editing) {
        var text by remember { mutableStateOf(note ?: "") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(tr("Notiz", "Note")) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(tr("z. B. Schulter zwickt, Sitz Stufe 4 …", "e.g. shoulder twinge, seat level 4 …")) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { store.setNote(exercise, today, text); editing = false }) { Text(tr("Speichern", "Save")) } },
            dismissButton = {
                Row {
                    if (note != null) TextButton(onClick = { store.setNote(exercise, today, ""); editing = false }) {
                        Text(tr("Löschen", "Delete"), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editing = false }) { Text(tr("Abbrechen", "Cancel")) }
                }
            }
        )
    }
}

/** Laufende Stoppuhren (Zeitübungen) je Übung: Startzeit. Außerhalb von SetInput, weil der Pager im Training
 *  weggewischte Seiten verwirft – die Stoppuhr liefe sonst nicht weiter. (Web: inputState in exercise.js) */
private val stopwatchStarts = mutableStateMapOf<String, Long>()
