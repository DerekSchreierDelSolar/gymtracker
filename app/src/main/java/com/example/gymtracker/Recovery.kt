package com.example.gymtracker

import android.content.Context
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.HeartRateVariabilityRmssdRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.RestingHeartRateRecord
import android.health.connect.datatypes.SleepSessionRecord
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.Instant
import java.util.Calendar

// ---------- Erholungs-Check (nur Android, Daten aus Health Connect) ----------
//
// Ruhepuls und HRV (Herzfrequenzvariabilität, RMSSD) von heute im Vergleich zum eigenen Durchschnitt der 14 Tage davor,
// dazu die Schlafdauer der letzten Nacht → Ampel auf der Startseite. Liefert z. B. eine Smartwatch, die nachts misst.
// Gelesen beim Health-Connect-Abgleich (HealthSync.sync), zwischengespeichert in SharedPreferences (gerätegebunden,
// nicht im Export – ändert sich täglich und gehört nicht zum Trainingsverlauf).

/** Rohwerte: heute + Durchschnitt der 14 Tage davor (null = keine Messung / zu wenig Tage) */
data class RecoveryData(
    val restingToday: Int? = null,
    val restingBase: Double? = null,
    val hrvToday: Double? = null,
    val hrvBase: Double? = null,
    /** Schlaf der letzten Nacht in Minuten (ohne Wachphasen) */
    val sleepMinutes: Int? = null,
    /** Zeitpunkt des Lesens */
    val time: Long = 0L
) {
    val isEmpty get() = restingToday == null && hrvToday == null && sleepMinutes == null

    fun toJson(): JSONObject = JSONObject().apply {
        restingToday?.let { put("restingToday", it) }
        restingBase?.let { put("restingBase", it) }
        hrvToday?.let { put("hrvToday", it) }
        hrvBase?.let { put("hrvBase", it) }
        sleepMinutes?.let { put("sleep", it) }
        put("time", time)
    }

    companion object {
        fun fromJson(o: JSONObject) = RecoveryData(
            o.optInt("restingToday", -1).takeIf { it > 0 },
            o.optDouble("restingBase").takeIf { !it.isNaN() },
            o.optDouble("hrvToday").takeIf { !it.isNaN() },
            o.optDouble("hrvBase").takeIf { !it.isNaN() },
            o.optInt("sleep", -1).takeIf { it >= 0 },
            o.optLong("time", 0L)
        )
    }
}

/** Ein bewerteter Teilwert: 0 = gut, 1 = mittel, 2 = schlecht, null = keine Bewertung möglich (z. B. noch kein Vergleich) */
data class RecoveryPart(val icon: String, val label: String, val value: String, val compare: String?, val level: Int?)

/** Gesamtbewertung: 0 = gut erholt (grün), 1 = etwas angeschlagen (gelb), 2 = schlecht erholt (rot) */
data class RecoveryRating(val level: Int, val parts: List<RecoveryPart>)

object Recovery {
    private const val PREFS = "health_connect"
    private const val DAY = 86_400_000L

    var data by mutableStateOf(RecoveryData())
        private set

    fun load(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("recovery", null)?.let {
            runCatching { data = RecoveryData.fromJson(JSONObject(it)) }
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Bewertung der heutigen Werte; null, wenn nichts von heute vorliegt. */
    fun rating(d: RecoveryData = data): RecoveryRating? {
        if (d.isEmpty || d.time < startOfToday()) return null
        val parts = buildList {
            d.restingToday?.let { today ->
                val base = d.restingBase
                // Gerundet bewerten, damit Anzeige („+2“) und Ampel zusammenpassen
                val diff = base?.let { Math.round(today - it).toInt() }
                add(RecoveryPart(
                    "❤", tr("Ruhepuls", "Resting HR"), "$today bpm",
                    base?.let { tr("Ø ${Math.round(it)}", "avg ${Math.round(it)}") + " (" + signed(diff!!) + ")" },
                    diff?.let { if (it >= 5) 2 else if (it >= 2) 1 else 0 }
                ))
            }
            d.hrvToday?.let { today ->
                val base = d.hrvBase
                // Gerundet auf ganze Prozent bewerten (wie angezeigt)
                val pct = base?.takeIf { it > 0 }?.let { Math.round((today / it - 1) * 100).toInt() }
                add(RecoveryPart(
                    "〰", "HRV", "${Math.round(today)} ms",
                    base?.let { tr("Ø ${Math.round(it)}", "avg ${Math.round(it)}") + " (" + signed(pct!!) + " %)" },
                    pct?.let { if (it <= -15) 2 else if (it <= -5) 1 else 0 }
                ))
            }
            d.sleepMinutes?.let { m ->
                add(RecoveryPart(
                    "🌙", tr("Schlaf", "Sleep"), "${m / 60}:${(m % 60).toString().padStart(2, '0')} h", null,
                    if (m < 360) 2 else if (m < 420) 1 else 0
                ))
            }
        }
        if (parts.isEmpty()) return null
        // Summe der Bewertungen: einmal schlecht (2) → gelb, zweimal schlecht (4) → rot
        val score = parts.sumOf { it.level ?: 0 }
        return RecoveryRating(if (score >= 4) 2 else if (score >= 2) 1 else 0, parts)
    }

    private fun signed(n: Int) = if (n > 0) "+$n" else if (n < 0) "−${-n}" else "±0"

    fun message(level: Int): String = when (level) {
        0 -> tr("Gut erholt – volle Belastung möglich 💪", "Well recovered – go all out 💪")
        1 -> tr("Etwas angeschlagen – normal trainieren, aber nicht ans Limit", "A bit worn – train normally, but not to the limit")
        else -> tr("Schlecht erholt – heute locker trainieren oder Pause", "Poorly recovered – train light today or rest")
    }

    fun color(level: Int): Color = when (level) {
        0 -> Color(0xFF43A047)
        1 -> Color(0xFFFFB300)
        else -> Color(0xFFE53935)
    }

    // ----- Lesen aus Health Connect -----

    /** Ruhepuls, HRV und Schlaf lesen (je nach Berechtigung), danach `data` setzen und speichern. Fehler: einfach weiter. */
    @RequiresApi(34)
    fun read(context: Context, m: HealthConnectManager, onDone: () -> Unit) {
        val now = System.currentTimeMillis()
        val today = startOfToday()
        val from = Instant.ofEpochMilli(today - 15 * DAY)
        var result = RecoveryData(time = now)

        /** Heute: letzter Wert seit Mitternacht; Vergleich: Ø der Tageswerte (je Tag der letzte) der 14 Tage davor, mind. 3 Tage */
        fun todayAndBase(values: List<Pair<Long, Double>>): Pair<Double?, Double?> {
            val todayVal = values.filter { it.first >= today }.maxByOrNull { it.first }?.second
            val perDay = values.filter { it.first in (today - 14 * DAY) until today }
                .groupBy { dayKey(it.first) }.values.map { day -> day.maxBy { it.first }.second }
            return todayVal to perDay.takeIf { it.size >= 3 }?.average()
        }

        val steps = mutableListOf<(() -> Unit) -> Unit>()
        if (HealthSync.canReadResting(context)) steps += { next ->
            readAll(context, m, RestingHeartRateRecord::class.java, from) { recs ->
                val (t, b) = todayAndBase(recs.map { it.time.toEpochMilli() to it.beatsPerMinute.toDouble() })
                result = result.copy(restingToday = t?.let { Math.round(it).toInt() }, restingBase = b)
                next()
            }
        }
        if (HealthSync.canReadHrv(context)) steps += { next ->
            readAll(context, m, HeartRateVariabilityRmssdRecord::class.java, from) { recs ->
                val (t, b) = todayAndBase(recs.map { it.time.toEpochMilli() to it.heartRateVariabilityMillis })
                result = result.copy(hrvToday = t, hrvBase = b)
                next()
            }
        }
        if (HealthSync.canReadSleep(context)) steps += { next ->
            // Letzte Nacht: Schlaf, der in den letzten 18 Stunden geendet hat (auch Mittagsschlaf zählt mit)
            readAll(context, m, SleepSessionRecord::class.java, Instant.ofEpochMilli(now - 2 * DAY)) { recs ->
                val awake = setOf(
                    SleepSessionRecord.StageType.STAGE_TYPE_AWAKE, SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_OUT_OF_BED
                )
                val nights = recs.filter { it.endTime.toEpochMilli() > now - 18 * 3_600_000L }
                val minutes = nights.sumOf { s ->
                    val total = s.endTime.toEpochMilli() - s.startTime.toEpochMilli()
                    val awakeMs = s.stages.filter { it.type in awake }.sumOf { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() }
                    (total - awakeMs) / 60_000L
                }.toInt()
                result = result.copy(sleepMinutes = minutes.takeIf { nights.isNotEmpty() })
                next()
            }
        }

        fun run(i: Int) {
            if (i >= steps.size) {
                data = result
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("recovery", result.toJson().toString()).apply()
                onDone()
                return
            }
            steps[i] { run(i + 1) }
        }
        run(0)
    }

    /** Alle Einträge eines Typs seit `from` (seitenweise); bei Fehler leere Liste. */
    @RequiresApi(34)
    private fun <T : Record> readAll(context: Context, m: HealthConnectManager, type: Class<T>, from: Instant, onDone: (List<T>) -> Unit) {
        val all = mutableListOf<T>()
        val range = TimeInstantRangeFilter.Builder().setStartTime(from).setEndTime(Instant.now().plusSeconds(3600)).build()
        fun page(token: Long?) {
            val req = ReadRecordsRequestUsingFilters.Builder(type).setTimeRangeFilter(range).setPageSize(1000)
                .apply { if (token != null) setPageToken(token) }.build()
            m.readRecords(req, context.mainExecutor, object : OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException> {
                override fun onResult(r: ReadRecordsResponse<T>) {
                    all += r.records
                    if (r.nextPageToken != -1L) page(r.nextPageToken) else onDone(all)
                }
                override fun onError(e: HealthConnectException) = onDone(all)
            })
        }
        page(null)
    }
}

/** Karte „Erholung heute“ auf der Startseite (nur wenn heutige Werte vorliegen). */
@Composable
fun RecoveryCard() {
    val rating = Recovery.rating() ?: return
    var info by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(Recovery.color(rating.level), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(tr("Erholung heute", "Recovery today"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { info = true }) { Text("ⓘ") }
            }
            Text(Recovery.message(rating.level), style = MaterialTheme.typography.bodyLarge)
            rating.parts.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.icon, modifier = Modifier.width(26.dp))
                    Text(p.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(p.value, style = MaterialTheme.typography.bodyMedium)
                    p.compare?.let {
                        Text("  $it", style = MaterialTheme.typography.bodySmall,
                            color = p.level?.takeIf { l -> l > 0 }?.let { l -> Recovery.color(l) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (info) AlertDialog(
        onDismissRequest = { info = false },
        title = { Text(tr("Erholung heute", "Recovery today")) },
        text = {
            Text(tr(
                "Aus den Werten deiner Smartwatch (über Health Connect):\n" +
                    "• Ruhepuls: 2+ Schläge über deinem Ø der letzten 14 Tage = leicht erhöht, 5+ = deutlich (Stress, Infekt, zu wenig Erholung).\n" +
                    "• HRV (Herzfrequenzvariabilität): 5 % unter deinem Ø = leicht, 15 % = deutlich erniedrigt. Höher ist besser.\n" +
                    "• Schlaf: unter 7 h = etwas wenig, unter 6 h = zu wenig.\n" +
                    "Ein deutlich schlechter Wert → gelb, zwei → rot. Für den Vergleich braucht es Messungen an mindestens 3 Tagen.\n\n" +
                    "Nur eine Orientierung, keine medizinische Bewertung – hör auch auf dein Gefühl.",
                "From your smartwatch data (via Health Connect):\n" +
                    "• Resting heart rate: 2+ beats above your 14-day average = slightly up, 5+ = clearly up (stress, infection, too little recovery).\n" +
                    "• HRV (heart rate variability): 5 % below your average = slightly, 15 % = clearly down. Higher is better.\n" +
                    "• Sleep: under 7 h = a bit short, under 6 h = too little.\n" +
                    "One clearly bad value → yellow, two → red. The comparison needs measurements on at least 3 days.\n\n" +
                    "Just a guide, not a medical assessment – listen to your body too."
            ))
        },
        confirmButton = { TextButton(onClick = { info = false }) { Text("OK") } }
    )
}
