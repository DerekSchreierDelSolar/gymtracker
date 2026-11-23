package com.example.gymtracker

import android.content.Context
import android.content.Intent
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.HealthPermissions
import android.health.connect.InsertRecordsResponse
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.RecordIdFilter
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.Metadata
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.WeightRecord
import android.health.connect.datatypes.BodyFatRecord
import android.health.connect.datatypes.units.Mass
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gymtracker.ui.theme.GymTrackerTheme
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

// ---------- Health Connect (Android 14+) ----------
//
// Health Connect ist die zentrale Gesundheitsdatenbank von Android (wie Apple Health): Apps schreiben dort
// Datensätze („Records“) hinein und lesen die anderer Apps, jeweils mit Erlaubnis pro Datentyp.
// Ab Android 14 gehört die Schnittstelle zum System (android.health.connect) → keine zusätzliche Bibliothek.
// Auf älteren Geräten ist die Funktion einfach nicht da (available() = false).
//
// Trainings + kcal schreiben: siehe HealthWorkouts.kt.
// Abgleich Körpergewicht in beide Richtungen:
//  - eigene Einträge → Health Connect, jeweils mit clientRecordId „gymtracker-weight-<Zeit>“. Erneutes Schreiben
//    mit derselben ID überschreibt (Upsert, wie MERGE in SQL) → keine Doppelten, auch bei wiederholtem Abgleich.
//  - Einträge anderer Apps (Waage, Samsung Health …) → GymTracker. Schon übernommene Record-IDs merken wir uns
//    (`seen`), damit ein in der App gelöschter Eintrag nicht wiederkommt. Übernommene Einträge (`imported` =
//    Zeitpunkte) schreiben wir nicht zurück, sonst stünden sie in Health Connect doppelt.
// Einstellungen in SharedPreferences (gerätegebunden, nicht im Export – die Erlaubnis gilt nur auf diesem Gerät).

object HealthSync {
    private const val PREFS = "health_connect"
    private const val ID_PREFIX = "gymtracker-weight-"
    private const val AUTO_INTERVAL_MS = 15L * 60 * 1000   // beim Öffnen der App höchstens alle 15 min

    val PERMISSIONS = arrayOf(
        HealthPermissions.READ_WEIGHT, HealthPermissions.WRITE_WEIGHT,
        HealthPermissions.WRITE_EXERCISE, HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED,
        HealthPermissions.READ_HEART_RATE,
        HealthPermissions.READ_RESTING_HEART_RATE, HealthPermissions.READ_HEART_RATE_VARIABILITY, HealthPermissions.READ_SLEEP,
        HealthPermissions.READ_BODY_FAT
    )

    /** Abgleich eingeschaltet? (Compose-State für die Einstellungen) */
    var enabled by mutableStateOf(false)
        private set
    var lastSync by mutableLongStateOf(0L)
        private set
    /** Beim letzten Abgleich neu übernommene Einträge */
    var lastImported by mutableIntStateOf(0)
        private set
    /** Beim letzten Abgleich geschriebene bzw. aktualisierte Trainings */
    var lastWorkouts by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Health Connect auf diesem Gerät vorhanden? (Android 14+ und Dienst erreichbar) */
    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 && manager(context) != null

    private fun manager(context: Context): HealthConnectManager? =
        if (Build.VERSION.SDK_INT >= 34) context.getSystemService(HealthConnectManager::class.java) else null

    private fun granted(context: Context, p: String) =
        context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun canRead(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.READ_WEIGHT)
    fun canWrite(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.WRITE_WEIGHT)
    fun canWriteWorkouts(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.WRITE_EXERCISE)
    fun canWriteKcal(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED)
    fun canReadHeartRate(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.READ_HEART_RATE)
    fun canReadResting(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.READ_RESTING_HEART_RATE)
    fun canReadHrv(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.READ_HEART_RATE_VARIABILITY)
    fun canReadSleep(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.READ_SLEEP)
    fun canReadBodyFat(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context, HealthPermissions.READ_BODY_FAT)
    /** Mindestens eine Berechtigung erteilt? */
    fun anyGranted(context: Context) = Build.VERSION.SDK_INT >= 34 && PERMISSIONS.any { granted(context, it) }
    /** Alle Berechtigungen erteilt? */
    fun allGranted(context: Context) = Build.VERSION.SDK_INT >= 34 && PERMISSIONS.all { granted(context, it) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        enabled = p.getBoolean("enabled", false) && available(context)
        lastSync = p.getLong("last", 0L)
        lastImported = p.getInt("lastImported", 0)
        lastWorkouts = p.getInt("lastWorkouts", 0)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        error = null
        prefs(context).edit().putBoolean("enabled", on).apply()
    }

    /** Health-Connect-Seite mit den Berechtigungen dieser App (dort kann man sie auch wieder entziehen). */
    fun manageIntent(context: Context): Intent =
        Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)

    private fun imported(context: Context): MutableSet<String> =
        prefs(context).getStringSet("imported", emptySet())!!.toMutableSet()   // Kopie: Android-Set nie direkt ändern

    // ----- Änderungen aus der App (GymStore.onWeightChange) -----

    /** Ein Eintrag wurde hinzugefügt (old = null), gelöscht (new = null) oder geändert. */
    fun onWeightChange(context: Context, old: WeightEntry?, new: WeightEntry?) {
        if (!enabled || Build.VERSION.SDK_INT < 34 || !canWrite(context)) return
        val imp = imported(context)
        if (old != null && old.time.toString() in imp) {
            // Eintrag einer anderen App: dort nichts ändern (dürfen wir nicht); geändert bleibt er „fremd“
            if (new != null) prefs(context).edit().putStringSet("imported", imp + new.time.toString()).apply()
            return
        }
        val m = manager(context) ?: return
        if (old != null && (new == null || new.time != old.time)) deleteOwn(context, m, old)
        if (new != null) insert(context, m, listOf(record(new)), {}, { error = it.message })
    }

    // ----- Abgleich -----

    /** Beim Öffnen der App: abgleichen, wenn eingeschaltet und der letzte Abgleich etwas her ist. */
    fun syncIfDue(context: Context, store: GymStore) {
        if (enabled && System.currentTimeMillis() - lastSync >= AUTO_INTERVAL_MS) sync(context, store)
    }

    /** Eigene Einträge schreiben, Trainings übertragen, dann neue Einträge anderer Apps übernehmen. */
    fun sync(context: Context, store: GymStore) {
        if (busy || Build.VERSION.SDK_INT < 34) return
        val m = manager(context) ?: return
        val app = context.applicationContext
        if (!anyGranted(app)) {
            error = tr("Keine Berechtigung – bitte in Health Connect erlauben.", "No permission – please allow it in Health Connect.")
            return
        }
        busy = true
        error = null
        val imp = imported(app)
        val own = store.weights.filter { it.time.toString() !in imp }
        var workouts = 0
        // Ablauf als Kette von Rückrufen (die Health-Connect-Aufrufe sind asynchron, ähnlich Promises in JS)
        val readStep = {
            // Körperfett anderer Apps (Measures.kt), danach fremdes Gewicht
            val weightStep = { if (canRead(app)) readForeign(app, m, store) { done(app, it, workouts) } else done(app, 0, workouts) }
            if (canReadBodyFat(app)) readBodyFat(app, m, store) { weightStep() } else weightStep()
        }
        val heartStep = { next: () -> Unit ->
            if (canReadHeartRate(app)) readHeartRates(app, m, store, next) else next()
        }
        val workoutStep = {
            if (canWriteWorkouts(app) || canWriteKcal(app))
                HealthWorkouts.sync(app, m, store, onDone = { workouts = it; readStep() }, onError = ::fail)
            else readStep()
        }
        // Erholungs-Check (Recovery.kt): Ruhepuls, HRV, Schlaf
        val recoveryStep = { next: () -> Unit ->
            if (canReadResting(app) || canReadHrv(app) || canReadSleep(app)) Recovery.read(app, m, next) else next()
        }
        // Reihenfolge: Gewicht schreiben → Puls holen (ändert ggf. die kcal) → Erholung → Trainings schreiben → fremdes Gewicht lesen
        val afterWeights = { heartStep { recoveryStep { workoutStep() } } }
        if (canWrite(app) && own.isNotEmpty()) insert(app, m, own.map(::record), { afterWeights() }, ::fail)
        else afterWeights()
    }

    private fun done(context: Context, imported: Int, workouts: Int) {
        busy = false
        lastSync = System.currentTimeMillis()
        lastImported = imported
        lastWorkouts = workouts
        prefs(context).edit().putLong("last", lastSync).putInt("lastImported", imported)
            .putInt("lastWorkouts", workouts).apply()
    }

    private fun fail(e: Exception) {
        busy = false
        error = e.message ?: e.javaClass.simpleName
    }

    @RequiresApi(34)
    private fun record(e: WeightEntry): Record {
        val time = Instant.ofEpochMilli(e.time)
        return WeightRecord.Builder(metadata(ID_PREFIX + e.time), time, Mass.fromGrams(e.kg * 1000))
            .setZoneOffset(ZoneId.systemDefault().rules.getOffset(time))
            .build()
    }

    /** Metadaten eigener Einträge: feste ID (für Upsert) und höhere Version → bestehender Eintrag wird ersetzt. */
    @RequiresApi(34)
    fun metadata(clientId: String): Metadata = Metadata.Builder()
        .setClientRecordId(clientId)
        .setClientRecordVersion(System.currentTimeMillis())
        .setRecordingMethod(Metadata.RECORDING_METHOD_MANUAL_ENTRY)
        .build()

    /** Einträge schreiben (in Paketen, Health Connect begrenzt die Anzahl pro Aufruf). */
    @RequiresApi(34)
    fun insert(
        context: Context, m: HealthConnectManager, records: List<Record>,
        onDone: () -> Unit, onError: (HealthConnectException) -> Unit
    ) {
        val chunks = records.chunked(500)
        fun next(i: Int) {
            if (i >= chunks.size) { onDone(); return }
            m.insertRecords(chunks[i], context.mainExecutor,
                object : OutcomeReceiver<InsertRecordsResponse, HealthConnectException> {
                    override fun onResult(r: InsertRecordsResponse) = next(i + 1)
                    override fun onError(e: HealthConnectException) = onError(e)
                })
        }
        next(0)
    }

    /** Eigene Einträge über ihre clientRecordId löschen (nicht vorhandene werden ignoriert). */
    @RequiresApi(34)
    fun delete(context: Context, m: HealthConnectManager, ids: List<RecordIdFilter>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) { onDone(); return }
        m.deleteRecords(ids, context.mainExecutor,
            object : OutcomeReceiver<Void, HealthConnectException> {
                override fun onResult(r: Void?) = onDone()
                override fun onError(e: HealthConnectException) = onDone()   // nicht vorhanden o. Ä. – egal
            })
    }

    @RequiresApi(34)
    private fun deleteOwn(context: Context, m: HealthConnectManager, e: WeightEntry) =
        delete(context, m, listOf(RecordIdFilter.fromClientRecordId(WeightRecord::class.java, ID_PREFIX + e.time)))

    /**
     * Puls zu Trainings der letzten 7 Tage holen (z. B. von einer Smartwatch, die ihn an Health Connect liefert).
     * Uhren übertragen oft erst nach einer Weile → Trainings ohne Puls bei jedem Abgleich erneut versuchen, Trainings
     * des letzten Tages auch mit Puls neu lesen (es können noch Messwerte nachkommen). Fehler: einfach weiter.
     */
    @RequiresApi(34)
    private fun readHeartRates(context: Context, m: HealthConnectManager, store: GymStore, onDone: () -> Unit) {
        val now = System.currentTimeMillis()
        val todo = store.sessions.filter {
            it.id >= 0 && it.end > now - 7 * 86_400_000L && (it.hr == null || it.hr.fine.isEmpty() || it.end > now - 86_400_000L)
        }
        fun next(i: Int) {
            if (i >= todo.size) { onDone(); return }
            val t = todo[i]
            val req = ReadRecordsRequestUsingFilters.Builder(HeartRateRecord::class.java)
                .setTimeRangeFilter(
                    TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.ofEpochMilli(t.start)).setEndTime(Instant.ofEpochMilli(t.end)).build()
                )
                .setPageSize(5000)
                .build()
            m.readRecords(req, context.mainExecutor,
                object : OutcomeReceiver<ReadRecordsResponse<HeartRateRecord>, HealthConnectException> {
                    override fun onResult(r: ReadRecordsResponse<HeartRateRecord>) {
                        val samples = r.records.flatMap { rec ->
                            rec.samples.map { it.time.toEpochMilli() to it.beatsPerMinute.toInt() }
                        }
                        HeartRate.of(samples, t.start, t.end)?.let { store.setHeartRate(t.id, it) }
                        next(i + 1)
                    }
                    override fun onError(e: HealthConnectException) = next(i + 1)
                })
        }
        next(0)
    }

    /** Körperfett-Werte (z. B. smarte Waage) der letzten 2 Jahre lesen; neue als Körpermaß übernehmen (Duplikat ±1 min / 0,1 %). */
    @RequiresApi(34)
    private fun readBodyFat(context: Context, m: HealthConnectManager, store: GymStore, onDone: () -> Unit) {
        val p = prefs(context)
        val seen = p.getStringSet("seenFat", emptySet())!!.toMutableSet()
        val found = mutableListOf<Measure>()
        val req = ReadRecordsRequestUsingFilters.Builder(BodyFatRecord::class.java)
            .setTimeRangeFilter(TimeInstantRangeFilter.Builder()
                .setStartTime(Instant.now().minusSeconds(730L * 86400)).setEndTime(Instant.now().plusSeconds(86400)).build())
            .setPageSize(1000).build()
        m.readRecords(req, context.mainExecutor,
            object : OutcomeReceiver<ReadRecordsResponse<BodyFatRecord>, HealthConnectException> {
                override fun onResult(r: ReadRecordsResponse<BodyFatRecord>) {
                    for (rec in r.records) {
                        if (rec.metadata.id in seen) continue
                        seen += rec.metadata.id
                        val v = Math.round(rec.percentage.value * 10) / 10.0
                        val e = Measure(rec.time.toEpochMilli(), MeasureType.BODYFAT, v)
                        if (v !in MeasureType.BODYFAT.min..MeasureType.BODYFAT.max) continue
                        val dup = (store.measures + found).any {
                            it.type == MeasureType.BODYFAT && kotlin.math.abs(it.time - e.time) < 60_000 && kotlin.math.abs(it.value - v) < 0.1
                        }
                        if (!dup) found += e
                    }
                    p.edit().putStringSet("seenFat", seen).apply()
                    store.importMeasures(found)
                    onDone()
                }
                override fun onError(e: HealthConnectException) = onDone()
            })
    }

    /** Alle Gewichts-Einträge lesen (seitenweise), neue von anderen Apps in den Store übernehmen. */
    @RequiresApi(34)
    private fun readForeign(context: Context, m: HealthConnectManager, store: GymStore, onDone: (Int) -> Unit) {
        val p = prefs(context)
        val seen = p.getStringSet("seen", emptySet())!!.toMutableSet()
        val imp = imported(context)
        val found = mutableListOf<WeightEntry>()
        val range = TimeInstantRangeFilter.Builder()
            .setStartTime(Instant.EPOCH).setEndTime(Instant.now().plusSeconds(86400)).build()

        fun page(token: Long?) {
            val req = ReadRecordsRequestUsingFilters.Builder(WeightRecord::class.java)
                .setTimeRangeFilter(range).setPageSize(1000)
                .apply { if (token != null) setPageToken(token) }
                .build()
            m.readRecords(req, context.mainExecutor,
                object : OutcomeReceiver<ReadRecordsResponse<WeightRecord>, HealthConnectException> {
                    override fun onResult(r: ReadRecordsResponse<WeightRecord>) {
                        for (rec in r.records) {
                            val md = rec.metadata
                            if (md.dataOrigin.packageName == context.packageName || md.id in seen) continue
                            seen += md.id
                            val e = WeightEntry(rec.time.toEpochMilli(), Math.round(rec.weight.inGrams / 100.0) / 10.0)
                            // Gleicher Wert zur (fast) gleichen Zeit schon vorhanden → nicht doppelt
                            val dup = (store.weights + found).any {
                                kotlin.math.abs(it.time - e.time) < 60_000 && kotlin.math.abs(it.kg - e.kg) < 0.05
                            }
                            if (!dup) { found += e; imp += e.time.toString() }
                        }
                        if (r.nextPageToken != -1L) page(r.nextPageToken)
                        else {
                            p.edit().putStringSet("seen", seen).putStringSet("imported", imp).apply()
                            store.importWeights(found)
                            onDone(found.size)
                        }
                    }
                    override fun onError(e: HealthConnectException) = fail(e)
                })
        }
        page(null)
    }
}

/** Abschnitt „Health Connect“ im Einstellungen-Dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthSettings(store: GymStore) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    // Berechtigungen können sich außerhalb der App ändern → nach Rückkehr neu prüfen
    var refresh by remember { mutableIntStateOf(0) }
    val any = remember(refresh) { HealthSync.anyGranted(context) }
    val all = remember(refresh) { HealthSync.allGranted(context) }
    var denied by remember { mutableStateOf(false) }

    val manage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
        if (HealthSync.anyGranted(context)) denied = false
        if (HealthSync.enabled) HealthSync.sync(context, store)
    }
    // Health-Berechtigungen sind normale Laufzeit-Berechtigungen; Android zeigt dafür die Health-Connect-Seite.
    // Nach zweimaligem Ablehnen zeigt Android sie nicht mehr → dann direkt die Health-Connect-Einstellungen öffnen.
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        if (HealthSync.anyGranted(context)) {
            denied = false
            HealthSync.setEnabled(context, true)
            HealthSync.sync(context, store)
        } else denied = true
        if (result.values.none { it } && result.isNotEmpty() && HealthSync.enabled)
            manage.launch(HealthSync.manageIntent(context))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!HealthSync.available(context)) {
            Text(
                tr("Abgleich mit anderen Gesundheits-Apps – ab Android 14 verfügbar.",
                    "Sync with other health apps – available from Android 14."),
                style = small, color = muted
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr("Körpergewicht abgleichen, Trainings eintragen, Puls und Erholung übernehmen – für Samsung Health, Fitbit, Google Fit, Smartwatches, smarte Waagen …",
                        "Sync body weight, log workouts, import heart rate and recovery – for Samsung Health, Fitbit, Google Fit, smartwatches, smart scales …"),
                    style = small, color = muted
                )
            }
            Switch(
                checked = HealthSync.enabled,
                onCheckedChange = { on ->
                    if (!on) HealthSync.setEnabled(context, false)
                    else if (any) { HealthSync.setEnabled(context, true); HealthSync.sync(context, store) }
                    else request.launch(HealthSync.PERMISSIONS)
                }
            )
        }
        if (denied) {
            Text(
                tr("Keine Berechtigung erteilt. Du kannst sie in Health Connect erlauben.",
                    "No permission granted. You can allow it in Health Connect."),
                style = small, color = MaterialTheme.colorScheme.error
            )
            OutlinedButton(onClick = { manage.launch(HealthSync.manageIntent(context)) }) {
                Text(tr("Health Connect öffnen", "Open Health Connect"))
            }
        }
        if (HealthSync.enabled) {
            // Was ist erlaubt? ✓ / – je Datentyp
            val items = remember(refresh) {
                listOf(
                    tr("Gewicht lesen", "Read weight") to HealthSync.canRead(context),
                    tr("Gewicht eintragen", "Write weight") to HealthSync.canWrite(context),
                    tr("Trainings eintragen", "Write workouts") to HealthSync.canWriteWorkouts(context),
                    tr("kcal eintragen", "Write calories") to HealthSync.canWriteKcal(context),
                    tr("Puls lesen", "Read heart rate") to HealthSync.canReadHeartRate(context),
                    tr("Ruhepuls lesen", "Read resting HR") to HealthSync.canReadResting(context),
                    tr("HRV lesen", "Read HRV") to HealthSync.canReadHrv(context),
                    tr("Schlaf lesen", "Read sleep") to HealthSync.canReadSleep(context),
                    tr("Körperfett lesen", "Read body fat") to HealthSync.canReadBodyFat(context)
                )
            }
            // FlowRow: ganze Einträge umbrechen statt mitten im Text
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { (label, ok) ->
                    Text(
                        (if (ok) "✓ " else "– ") + label, style = small,
                        color = if (ok) muted else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                if (HealthSync.busy) tr("Gleiche ab …", "Syncing …")
                else if (HealthSync.lastSync == 0L) tr("Noch nicht abgeglichen", "Not synced yet")
                else tr("Zuletzt abgeglichen: ", "Last sync: ") + fmtDateTime(HealthSync.lastSync) +
                    (if (HealthSync.lastImported > 0)
                        " · " + tr("${HealthSync.lastImported} Gewicht(e) übernommen", "${HealthSync.lastImported} weight(s) imported")
                    else "") +
                    (if (HealthSync.lastWorkouts > 0)
                        " · " + tr(plural(HealthSync.lastWorkouts, "Training", "Trainings") + " eingetragen",
                            plural(HealthSync.lastWorkouts, "workout", "workouts") + " logged")
                    else ""),
                style = small, color = muted
            )
            HealthSync.error?.let {
                Text(tr("Fehler: ", "Error: ") + it, style = small, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    enabled = !HealthSync.busy && any,
                    onClick = { HealthSync.sync(context, store) }
                ) { Text(tr("Jetzt abgleichen", "Sync now")) }
                if (!all) TextButton(onClick = { request.launch(HealthSync.PERMISSIONS) }) {
                    Text(tr("Fehlende erlauben", "Allow missing"))
                } else TextButton(onClick = { manage.launch(HealthSync.manageIntent(context)) }) {
                    Text(tr("Berechtigungen", "Permissions"))
                }
            }
        }
    }
}

/**
 * Datenschutz-Hinweis, den Health Connect auf der Berechtigungsseite verlinkt („Datenschutzerklärung“).
 * Pflicht: ohne diese Activity (Manifest: activity-alias mit VIEW_PERMISSION_USAGE) zeigt Android 14
 * keine Berechtigungsanfrage an.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sprache und Design aus den gespeicherten Einstellungen (die App selbst läuft evtl. gerade nicht)
        var theme = "system"
        runCatching {
            val f = File(filesDir, "gym_data.json")
            val s = if (f.exists()) JSONObject(f.readText()).optJSONObject("settings") else null
            s?.optString("language")?.takeIf { it == "de" || it == "en" }?.let { appLanguage = it }
            s?.optString("theme")?.let { theme = it }
        }
        setContent {
            val dark = when (theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            DisposableEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(SystemBarStyle.auto(t, t) { dark }, SystemBarStyle.auto(t, t) { dark })
                onDispose { }
            }
            GymTrackerTheme(darkTheme = dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(tr("GymTracker & Health Connect", "GymTracker & Health Connect"),
                            style = MaterialTheme.typography.headlineSmall)
                        Text(tr(
                            "GymTracker nutzt Health Connect nur für Körpergewicht, Trainings, Puls und Erholung.\n\n" +
                                "• Gewicht lesen: Werte anderer Apps (z. B. einer smarten Waage) erscheinen im Tab „Körper“ und verbessern die Kalorien-Schätzung.\n" +
                                "• Gewicht eintragen: Gewicht, das du in GymTracker einträgst, wird auch in Health Connect gespeichert.\n" +
                                "• Trainings und kcal eintragen: deine Trainings (Übungen, Sätze, Dauer) und der geschätzte Verbrauch erscheinen in anderen Fitness-Apps.\n" +
                                "• Puls lesen: Pulswerte während deiner Trainings (z. B. von einer Smartwatch) erscheinen in der Trainingsübersicht und machen die Kalorien genauer.\n" +
                                "• Ruhepuls, HRV und Schlaf lesen: daraus zeigt die Startseite, wie gut du heute erholt bist.\n\n" +
                                "GymTracker hat keinen Server und sendet nichts ins Internet. Welche anderen Apps die Daten in Health Connect sehen, legst du dort selbst fest. " +
                                "Du kannst die Berechtigungen jederzeit in Health Connect entziehen oder den Abgleich in den Einstellungen ausschalten.",
                            "GymTracker uses Health Connect only for body weight, workouts, heart rate and recovery.\n\n" +
                                "• Read weight: values from other apps (e.g. a smart scale) appear in the \"Body\" tab and improve the calorie estimate.\n" +
                                "• Write weight: weight you log in GymTracker is also saved to Health Connect.\n" +
                                "• Write workouts and calories: your workouts (exercises, sets, duration) and the estimated calories appear in other fitness apps.\n" +
                                "• Read heart rate: heart rate during your workouts (e.g. from a smartwatch) appears in the workout summary and makes calories more accurate.\n" +
                                "• Read resting heart rate, HRV and sleep: the home screen uses them to show how well recovered you are today.\n\n" +
                                "GymTracker has no server and sends nothing to the internet. You decide in Health Connect which other apps can see the data. " +
                                "You can revoke the permissions in Health Connect at any time or turn off syncing in the settings."
                        ))
                        Button(onClick = { finish() }) { Text("OK") }
                    }
                }
            }
        }
    }
}
