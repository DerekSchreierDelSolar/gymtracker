package com.example.gymtracker

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.media.AudioAttributes
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Geführtes Training: eine Übung nach der anderen.
 * Die Sätze werden ganz normal gespeichert – das Training merkt sich nur die Startzeit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    store: GymStore,
    workout: Workout,
    start: Long,
    index: Int,
    onIndex: (Int) -> Unit,
    onFinish: () -> Unit
) {
    var confirmEnd by remember { mutableStateOf(false) }
    // Zurück-Taste beendet das Training nicht versehentlich, sondern fragt nach
    BackHandler { confirmEnd = true }

    // Stoppuhr: LaunchedEffect startet eine Coroutine, die regelmäßig `now` aktualisiert.
    // Sie wird automatisch beendet, wenn der Bildschirm verlassen wird.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            now = System.currentTimeMillis()
        }
    }

    // Bildschirm während des Trainings anlassen, damit Stoppuhr und Pausen-Timer
    // sichtbar bleiben und die App beim Ablauf der Pause vibrieren darf.
    // DisposableEffect: onDispose läuft, wenn der Trainingsbildschirm verlassen wird.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Pausen-Timer: Endzeitpunkt und Gesamtlänge (für den Fortschrittsbalken), null = keine Pause
    var restEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var restTotal by rememberSaveable { mutableLongStateOf(0L) }
    val context = LocalContext.current
    // Startet bei jeder Änderung von restEnd neu (z. B. +15 s) und wartet bis zum Ende der Pause.
    // Parallel zeigt RestTimer den Countdown als Benachrichtigung (sichtbar, wenn das Handy in der Tasche ist).
    LaunchedEffect(restEnd) {
        val end = restEnd ?: return@LaunchedEffect
        RestTimer.show(context, end, workout.exercises.getOrElse(index) { "" })
        delay((end - System.currentTimeMillis()).coerceAtLeast(0))
        vibrate(context)
        RestTimer.done(context) // im Hintergrund: „Pause vorbei“
        restEnd = null
    }
    // Training verlassen → Countdown-Benachrichtigung entfernen
    DisposableEffect(Unit) { onDispose { RestTimer.cancel(context) } }
    // Ab Android 13 einmalig um Erlaubnis für Benachrichtigungen bitten (für den Pausen-Timer)
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("rest", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("asked", false) &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("asked", true).apply()
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Countdown-Ticks: bei 3, 2 und 1 Sekunde(n) Rest kurz vibrieren
    val restSecondsLeft = restEnd?.let { ((it - now + 999) / 1000).toInt() }
    LaunchedEffect(restSecondsLeft) {
        if (restSecondsLeft != null && restSecondsLeft in 1..3) tick(context)
    }
    fun startRest() {
        if (store.restSeconds <= 0) return
        restTotal = store.restSeconds * 1000L
        restEnd = System.currentTimeMillis() + restTotal
    }
    fun adjustRest(deltaMs: Long) {
        val end = restEnd ?: return
        val newEnd = end + deltaMs
        if (newEnd <= System.currentTimeMillis()) {
            RestTimer.cancel(context)
            restEnd = null
        } else {
            restEnd = newEnd
            restTotal = (restTotal + deltaMs).coerceAtLeast(newEnd - System.currentTimeMillis())
        }
    }

    val n = workout.exercises.size
    val totalSets = workout.exercises.sumOf { store.setsBetween(it, start, Long.MAX_VALUE).count { s -> s.isWork } }
    // Fortschritt wie in der Übersicht: Ziel-Sätze, pro Übung höchstens das Ziel
    val goalTotal = workout.exercises.sumOf { store.targetFor(workout, it) }
    val goalDone = workout.exercises.sumOf {
        minOf(store.setsBetween(it, start, Long.MAX_VALUE).count { s -> s.isWork }, store.targetFor(workout, it))
    }
    val goalPercent = Math.round(goalCompletion(goalDone, goalTotal, totalSets) * 100)

    // ----- Wischen zwischen Übungen -----
    // HorizontalPager: eine Seite pro Übung. `index` (aus GymApp) und die Pager-Seite
    // werden in beide Richtungen abgeglichen:
    //  - Buttons/Chips ändern `index` → Pager scrollt animiert dorthin
    //  - Wischen → sobald die Seite eingerastet ist („settled“), wird `index` aktualisiert
    val pagerState = rememberPagerState(initialPage = index) { n }
    val currentOnIndex by rememberUpdatedState(onIndex)
    LaunchedEffect(index) {
        if (pagerState.currentPage != index) pagerState.animateScrollToPage(index)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnIndex(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${index + 1}/$n · ${workout.name}")
                        Text(
                            "⏱ " + fmtDuration(now - start),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = { TextButton(onClick = { confirmEnd = true }) { Text(tr("Beenden", "Finish")) } }
            )
        },
        bottomBar = {
            Column {
                BottomAppBar {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onIndex(index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text(tr("‹ Vorherige", "‹ Previous")) }
                        if (index < n - 1) {
                            Button(onClick = { onIndex(index + 1) }, modifier = Modifier.weight(1f)) {
                                Text(tr("Nächste ›", "Next ›"))
                            }
                        } else {
                            Button(onClick = { confirmEnd = true }, modifier = Modifier.weight(1f)) {
                                Text(tr("Training beenden", "Finish workout"))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Fester Kopf: Fortschritt + Sprungleiste zu allen Übungen (✓ = Ziel erreicht)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                // LazyRow statt Row, damit wir automatisch zur aktuellen Übung scrollen können
                val chipState = rememberLazyListState()
                LaunchedEffect(index) { chipState.animateScrollToItem(index) }
                LazyRow(
                    state = chipState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(workout.exercises) { i, e ->
                        val count = store.setsBetween(e, start, Long.MAX_VALUE).count { it.isWork } // ohne Aufwärmsätze
                        val goal = store.targetFor(workout, e)
                        // ✓ = Ziel erreicht; bei optionalen Übungen: mindestens ein Satz
                        val done = if (goal == 0) count > 0 else count >= goal
                        FilterChip(
                            selected = i == index,
                            onClick = { onIndex(i) },
                            // nur Name (+ ✓), ohne Satzzähler – der steht auf der Seite der Übung
                            // ✓ = Ziel erreicht, • = angefangen (mind. ein Satz), sonst nur der Name
                            label = { Text((if (done) "✓ " else if (count > 0) "• " else "") + exName(e)) }
                        )
                    }
                }
            }

            // Wischbarer Bereich: eine Seite pro Übung
            HorizontalPager(
                state = pagerState,
                key = { workout.exercises[it] },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                TrainingExercisePage(
                    store, workout, start,
                    name = workout.exercises[page],
                    now = now, restEnd = restEnd, restTotal = restTotal,
                    onSkipRest = { restEnd = null; RestTimer.cancel(context) },
                    onAdded = { startRest() }
                )
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(tr("Training beenden?", "Finish workout?")) },
            text = {
                val setsText = plural(totalSets, tr("Satz", "set"), tr("Sätze", "sets")) +
                    tr(" in ", " in ") + fmtDuration(now - start)
                Text(
                    when {
                        totalSets == 0 -> tr(
                            "Du hast noch keinen Satz eingetragen – das Training wird verworfen.",
                            "You haven't logged any sets yet – this workout will be discarded."
                        )
                        goalTotal == 0 -> "$setsText."
                        else -> "$setsText – " + tr(
                            "$goalDone von $goalTotal Ziel-Sätzen ($goalPercent %).",
                            "$goalDone of $goalTotal target sets ($goalPercent %)."
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; onFinish() }) { Text(tr("Beenden", "Finish")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text(tr("Weiter trainieren", "Keep training")) }
            }
        )
    }
}

/** Inhalt einer Übung im Trainingsmodus (eine Seite im HorizontalPager). */
@Composable
fun TrainingExercisePage(
    store: GymStore,
    workout: Workout,
    start: Long,
    name: String,
    now: Long,
    restEnd: Long?,
    restTotal: Long,
    onSkipRest: () -> Unit,
    onAdded: () -> Unit
) {
    val group = store.groupOf(name)
    val cardio = group.isCardio
    val timed = isTimed(name)
    val today = store.setsBetween(name, start, Long.MAX_VALUE)
    val todayWork = today.count { it.isWork } // Aufwärmsätze zählen nicht fürs Ziel
    val target = store.targetFor(workout, name)
    // Frühere Trainings dieser Übung (vor diesem Training), nach Tag gruppiert, neuester zuerst
    val previousDays = store.setsFor(name).filter { it.time < start }.groupBy { fmtDate(it.time) }
    val resting = restEnd != null && restEnd > now

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Übungsname, rechts ⓘ fürs Wiki
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exName(name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                WikiButton(name)
            }
        }

        // Motivierende Zeile mit dem Rekord (wie in den Übungsdetails)
        item { RecordLine(store.setsFor(name), cardio, timed) }

        // Drei Kreise: Wiederholungs-Ziel · Pause · Sätze dieser Übung
        item {
            val goal = repsGoal(store, name, start)
            val restOptions = listOf(0, 60, 90, 120, 180)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                CircleStat(
                    progress = 1f,
                    color = MaterialTheme.colorScheme.primary,
                    value = goal?.first ?: "–",
                    label = goal?.second ?: tr("Wdh.-Ziel", "Rep goal")
                )
                if (resting) {
                    CircleStat(
                        progress = (restEnd!! - now).toFloat() / restTotal.coerceAtLeast(1),
                        color = Color(0xFFE8703A),
                        value = fmtDuration(restEnd - now + 999),
                        label = tr("Pause · tippen = weiter", "Rest · tap to skip"),
                        big = true,
                        onClick = onSkipRest
                    )
                } else {
                    val sec = store.restSeconds
                    CircleStat(
                        progress = if (sec > 0) 1f else 0f,
                        color = MaterialTheme.colorScheme.primary,
                        value = if (sec > 0) fmtDuration(sec * 1000L) else tr("Aus", "Off"),
                        label = tr("Pause", "Rest"),
                        big = true,
                        // nächste Pausenlänge (Aus → 1 min → 90 s → 2 min → 3 min → Aus)
                        onClick = { store.setRest(restOptions[(restOptions.indexOf(sec) + 1) % restOptions.size]) }
                    )
                }
                val reached = target > 0 && todayWork >= target
                CircleStat(
                    progress = if (target > 0) todayWork.toFloat() / target else if (todayWork > 0) 1f else 0f,
                    color = if (reached) completionColor(1f) else Color(0xFFE88A93),
                    value = if (target > 0) "$todayWork/$target" else "$todayWork",
                    label = if (reached) tr("Ziel erreicht ✓", "Goal reached ✓") else tr("Sätze erledigt", "Sets done")
                )
            }
        }

        // Nach jedem eingetragenen Satz startet die Pause
        item { SetInput(store, name, onAdded = onAdded) }
        item { NoteButton(store, name) }

        // Heutige Sätze (neuester oben)
        if (today.isNotEmpty()) {
            item { Text(tr("Heute", "Today"), style = MaterialTheme.typography.titleMedium) }
            val sorted = today.sortedBy { it.time }
            val labels = setLabels(sorted)
            sorted.indices.reversed().forEach { i ->
                item(key = sorted[i].id) { SetRow(store, sorted[i], labels[i], cardio) }
            }
        }

        // Verlauf: frühere Trainings dieser Übung
        if (previousDays.isEmpty()) item {
            Text(
                tr("Erstes Mal – viel Erfolg!", "First time – good luck!"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else item {
            Text(tr("Verlauf", "History"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        previousDays.forEach { (day, daySets) ->
            item(key = "hist_$day") { HistoryDayCard(store, day, daySets, cardio, timed) }
        }
    }
}

/**
 * Wiederholungs-Ziel für den ersten Kreis: (Wert, Beschriftung) oder null ohne frühere Trainings.
 * Kraft: wie der 💡-Vorschlag (doppelte Progression) – nach einer Gewichtserhöhung 8, sonst bisher max. + 1.
 * Zeitübung: längste Zeit + 10 s. Cardio: Minuten vom letzten Mal.
 */
fun repsGoal(store: GymStore, name: String, before: Long): Pair<String, String>? {
    val prev = store.setsFor(name).filter { it.time < before && it.isTop } // ohne Aufwärm-/Drop-Sätze
    val lastDay = prev.firstOrNull()?.let { dayKey(it.time) } ?: return null
    val last = prev.filter { dayKey(it.time) == lastDay }
    if (store.groupOf(name).isCardio) return "${last.sumOf { it.reps }}" to tr("Minuten zuletzt", "Minutes last time")
    if (isTimed(name)) return fmtSeconds(last.maxOf { it.reps } + 10) to tr("Zeit-Ziel", "Time goal")
    val top = last.maxOf { it.weight }
    val atTop = last.filter { it.weight == top }
    val heavier = top > 0 && atTop.size >= 2 && atTop.minOf { it.reps } >= 8
    return "${if (heavier) 8 else atTop.maxOf { it.reps } + 1}" to tr("Wdh.-Ziel", "Rep goal") // eindeutig: Ziel für heute
}

/**
 * Schlanker Kreis mit Wert in der Mitte und Beschriftung darunter (Trainingsseite).
 * progress 0..1 = Anteil des Bogens ab 12 Uhr; big = etwas größer (mittlerer Kreis).
 */
@Composable
fun CircleStat(
    progress: Float,
    color: Color,
    value: String,
    label: String,
    big: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val track = color.copy(alpha = 0.18f)
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "circle")
    val size = if (big) 104.dp else 88.dp
    Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.toPx() - 8.dp.toPx() - stroke, size.toPx() - 8.dp.toPx() - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (animated > 0f) drawArc(
                    color, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Text(value, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Pausen-Countdown (unten im Trainingsmodus): großer Ring, der sich im Uhrzeigersinn leert,
 * die Restzeit in der Mitte. In den letzten 3 Sekunden wird der Ring orange und pulsiert.
 */
@Composable
fun RestTimerBar(
    remainingMs: Long,
    totalMs: Long,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSkip: () -> Unit
) {
    val fraction = if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    // Weiches Nachlaufen: Der Ring springt nicht in 200-ms-Schritten, sondern gleitet
    val animated by animateFloatAsState(fraction, animationSpec = tween(250), label = "rest")
    val final = remainingMs <= 3_000
    val ringColor = if (final) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    // Leichtes Pulsieren in den letzten Sekunden (Größe 100 % ↔ 106 %)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = if (final) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse"
    )

    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    // Restzeit als Bogen ab 12 Uhr
                    drawArc(ringColor, -90f, 360f * animated, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                // +999: aufrunden, damit „0:01“ bis zum Schluss angezeigt wird statt vorher „0:00“
                Text(
                    fmtDuration(remainingMs + 999),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr("Pause", "Rest"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (final) tr("Gleich geht's weiter …", "Get ready …")
                    else tr("Nächster Satz in ", "Next set in ") + fmtDuration(remainingMs + 999),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−15 s") }
                    OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+15 s") }
                }
                TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(tr("Überspringen", "Skip"))
                }
            }
        }
    }
}

/** Ganz kurzes Antippen per Vibration (Countdown 3-2-1). */
fun tick(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator == null || !vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(40)
    }
}

/** Kurzes Doppel-Vibrieren am Ende der Pause. */
fun vibrate(context: Context) {
    val pattern = longArrayOf(0, 300, 150, 300) // Pause, an, aus, an (ms)
    // Ab Android 12 (API 31) über VibratorManager, davor direkt über den Vibrator-Service
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator == null || !vibrator.hasVibrator()) return
    // Als „Wecker“-Vibration markieren, damit sie auch im Lautlos-Modus durchkommt.
    // VibrationEffect gibt es erst ab Android 8 (API 26), minSdk ist 24.
    if (Build.VERSION.SDK_INT >= 33) {
        vibrator.vibrate(
            VibrationEffect.createWaveform(pattern, -1),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
        )
    } else if (Build.VERSION.SDK_INT >= 26) {
        @Suppress("DEPRECATION")
        vibrator.vibrate(
            VibrationEffect.createWaveform(pattern, -1),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }
}
