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
import android.health.connect.datatypes.WeightRecord
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

    val PERMISSIONS = arrayOf(HealthPermissions.READ_WEIGHT, HealthPermissions.WRITE_WEIGHT)

    /** Abgleich eingeschaltet? (Compose-State für die Einstellungen) */
    var enabled by mutableStateOf(false)
        private set
    var lastSync by mutableLongStateOf(0L)
        private set
    /** Beim letzten Abgleich neu übernommene Einträge */
    var lastImported by mutableIntStateOf(0)
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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        enabled = p.getBoolean("enabled", false) && available(context)
        lastSync = p.getLong("last", 0L)
        lastImported = p.getInt("lastImported", 0)
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
        if (new != null) insertOwn(context, m, listOf(new)) { }
    }

    // ----- Abgleich -----

    /** Beim Öffnen der App: abgleichen, wenn eingeschaltet und der letzte Abgleich etwas her ist. */
    fun syncIfDue(context: Context, store: GymStore) {
        if (enabled && System.currentTimeMillis() - lastSync >= AUTO_INTERVAL_MS) sync(context, store)
    }

    /** Eigene Einträge schreiben, dann neue Einträge anderer Apps übernehmen. Ergebnis in lastSync/error. */
    fun sync(context: Context, store: GymStore) {
        if (busy || Build.VERSION.SDK_INT < 34) return
        val m = manager(context) ?: return
        val app = context.applicationContext
        val read = canRead(app)
        val write = canWrite(app)
        if (!read && !write) {
            error = tr("Keine Berechtigung – bitte in Health Connect erlauben.", "No permission – please allow it in Health Connect.")
            return
        }
        busy = true
        error = null
        val imp = imported(app)
        val own = store.weights.filter { it.time.toString() !in imp }
        val afterWrite = {
            if (read) readForeign(app, m, store) { done(app, it) } else done(app, 0)
        }
        if (write && own.isNotEmpty()) insertOwn(app, m, own) { afterWrite() } else afterWrite()
    }

    private fun done(context: Context, count: Int) {
        busy = false
        lastSync = System.currentTimeMillis()
        lastImported = count
        prefs(context).edit().putLong("last", lastSync).putInt("lastImported", count).apply()
    }

    private fun fail(e: Exception) {
        busy = false
        error = e.message ?: e.javaClass.simpleName
    }

    @RequiresApi(34)
    private fun record(e: WeightEntry): WeightRecord {
        val time = Instant.ofEpochMilli(e.time)
        val meta = Metadata.Builder()
            .setClientRecordId(ID_PREFIX + e.time)
            .setClientRecordVersion(System.currentTimeMillis())   // höhere Version → bestehender Eintrag wird ersetzt
            .setRecordingMethod(Metadata.RECORDING_METHOD_MANUAL_ENTRY)
            .build()
        return WeightRecord.Builder(meta, time, Mass.fromGrams(e.kg * 1000))
            .setZoneOffset(ZoneId.systemDefault().rules.getOffset(time))
            .build()
    }

    /** Eigene Einträge schreiben (in Paketen, Health Connect begrenzt die Anzahl pro Aufruf). */
    @RequiresApi(34)
    private fun insertOwn(context: Context, m: HealthConnectManager, entries: List<WeightEntry>, onDone: () -> Unit) {
        val chunks = entries.chunked(500)
        fun next(i: Int) {
            if (i >= chunks.size) { onDone(); return }
            m.insertRecords(chunks[i].map<WeightEntry, Record>(::record), context.mainExecutor,
                object : OutcomeReceiver<InsertRecordsResponse, HealthConnectException> {
                    override fun onResult(r: InsertRecordsResponse) = next(i + 1)
                    override fun onError(e: HealthConnectException) {
                        if (busy) fail(e) else error = e.message
                    }
                })
        }
        next(0)
    }

    @RequiresApi(34)
    private fun deleteOwn(context: Context, m: HealthConnectManager, e: WeightEntry) {
        m.deleteRecords(listOf(RecordIdFilter.fromClientRecordId(WeightRecord::class.java, ID_PREFIX + e.time)),
            context.mainExecutor,
            object : OutcomeReceiver<Void, HealthConnectException> {
                override fun onResult(r: Void?) {}
                override fun onError(e: HealthConnectException) {}   // nicht vorhanden o. Ä. – egal
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
    val read = remember(refresh) { HealthSync.canRead(context) }
    val write = remember(refresh) { HealthSync.canWrite(context) }
    var denied by remember { mutableStateOf(false) }

    // Health-Berechtigungen sind normale Laufzeit-Berechtigungen; Android zeigt dafür die Health-Connect-Seite
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        if (result.values.any { it }) {
            denied = false
            HealthSync.setEnabled(context, true)
            HealthSync.sync(context, store)
        } else denied = true
    }
    val manage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
        if (HealthSync.canRead(context) || HealthSync.canWrite(context)) denied = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!HealthSync.available(context)) {
            Text("Health Connect", style = MaterialTheme.typography.titleSmall)
            Text(
                tr("Abgleich mit anderen Gesundheits-Apps – ab Android 14 verfügbar.",
                    "Sync with other health apps – available from Android 14."),
                style = small, color = muted
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Health Connect", style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("Körpergewicht mit anderen Apps abgleichen (z. B. smarte Waage, Samsung Health, Fitbit).",
                        "Sync body weight with other apps (e.g. smart scale, Samsung Health, Fitbit)."),
                    style = small, color = muted
                )
            }
            Switch(
                checked = HealthSync.enabled,
                onCheckedChange = { on ->
                    if (!on) HealthSync.setEnabled(context, false)
                    else if (read || write) { HealthSync.setEnabled(context, true); HealthSync.sync(context, store) }
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
            if (!read || !write) Text(
                if (read) tr("Nur Lesen erlaubt – deine Einträge werden nicht übertragen.", "Read only – your entries are not sent.")
                else if (write) tr("Nur Schreiben erlaubt – Werte anderer Apps werden nicht übernommen.", "Write only – values from other apps are not imported.")
                else tr("Keine Berechtigung – bitte in Health Connect erlauben.", "No permission – please allow it in Health Connect."),
                style = small, color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (HealthSync.busy) tr("Gleiche ab …", "Syncing …")
                else if (HealthSync.lastSync == 0L) tr("Noch nicht abgeglichen", "Not synced yet")
                else tr("Zuletzt abgeglichen: ", "Last sync: ") + fmtDateTime(HealthSync.lastSync) +
                    if (HealthSync.lastImported > 0)
                        " · " + tr("${HealthSync.lastImported} neu übernommen", "${HealthSync.lastImported} imported")
                    else "",
                style = small, color = muted
            )
            HealthSync.error?.let {
                Text(tr("Fehler: ", "Error: ") + it, style = small, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    enabled = !HealthSync.busy && (read || write),
                    onClick = { HealthSync.sync(context, store) }
                ) { Text(tr("Jetzt abgleichen", "Sync now")) }
                TextButton(onClick = { manage.launch(HealthSync.manageIntent(context)) }) {
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
                            "GymTracker liest und schreibt nur dein Körpergewicht.\n\n" +
                                "• Lesen: Gewichtswerte anderer Apps (z. B. einer smarten Waage) erscheinen im Tab „Körper“ und verbessern die Kalorien-Schätzung.\n" +
                                "• Schreiben: Gewicht, das du in GymTracker einträgst, wird auch in Health Connect gespeichert.\n\n" +
                                "Alle Daten bleiben auf deinem Gerät. GymTracker hat keinen Server und sendet nichts ins Internet. " +
                                "Du kannst die Berechtigungen jederzeit in Health Connect entziehen oder den Abgleich in den Einstellungen ausschalten.",
                            "GymTracker only reads and writes your body weight.\n\n" +
                                "• Read: weight values from other apps (e.g. a smart scale) appear in the \"Body\" tab and improve the calorie estimate.\n" +
                                "• Write: weight you log in GymTracker is also saved to Health Connect.\n\n" +
                                "All data stays on your device. GymTracker has no server and sends nothing to the internet. " +
                                "You can revoke the permissions in Health Connect at any time or turn off syncing in the settings."
                        ))
                        Button(onClick = { finish() }) { Text("OK") }
                    }
                }
            }
        }
    }
}
