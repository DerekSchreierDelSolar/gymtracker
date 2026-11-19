package com.example.gymtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// ---------- Automatisches Backup ----------
//
// Der Nutzer wählt einmal einen Ordner (Storage Access Framework, OPEN_DOCUMENT_TREE – geht auch für
// Cloud-Ordner wie Google Drive). Beim Verlassen der App (MainActivity.onStop) wird höchstens einmal
// pro Tag eine Sicherung „gymtracker-auto-JJJJ-MM-TT.json“ dort abgelegt; die 7 neuesten bleiben.
// Einstellungen liegen in SharedPreferences (gerätegebunden, NICHT im Backup/Export –
// eine Ordner-Berechtigung gilt nur auf diesem Gerät).

object AutoBackup {
    private const val PREFS = "auto_backup"
    private const val PREFIX = "gymtracker-auto-"
    private const val KEEP = 7
    private const val MIN_INTERVAL_MS = 20L * 3600 * 1000   // „einmal am Tag“, etwas Spielraum

    /** Gewählter Ordner (Tree-URI) oder null = aus. Compose-State für die Einstellungen. */
    var folder by mutableStateOf<Uri?>(null)
        private set
    /** Zeitpunkt der letzten automatischen Sicherung (0 = noch nie). */
    var last by mutableLongStateOf(0L)
        private set
    /** Letzter Fehler (z. B. Ordner gelöscht oder Berechtigung entzogen), sonst null. */
    var error by mutableStateOf<String?>(null)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        folder = p.getString("uri", null)?.let(Uri::parse)
        last = p.getLong("last", 0L)
    }

    /** Ordner übernehmen (Ergebnis von OpenDocumentTree) und dauerhafte Schreibberechtigung sichern. */
    fun setFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        folder = uri
        error = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("uri", uri.toString()).apply()
    }

    fun disable(context: Context) {
        folder?.let { u ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        folder = null
        error = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("uri").apply()
    }

    /** Lesbarer Ordnername: „primary:Download/GymTracker“ → „Download/GymTracker“. */
    fun folderLabel(): String? = folder?.let { u ->
        runCatching { DocumentsContract.getTreeDocumentId(u).substringAfter(':').ifEmpty { "/" } }.getOrNull()
    }

    /** Sichern, wenn ein Ordner gewählt ist und die letzte Sicherung länger als einen Tag her ist. */
    fun runIfDue(context: Context, store: GymStore) {
        if (folder == null || System.currentTimeMillis() - last < MIN_INTERVAL_MS) return
        runNow(context, store)
    }

    /**
     * Jetzt sichern (im Hintergrund-Thread; Dateizugriff über den Ordner kann bei Cloud-Anbietern dauern).
     * Die JSON-Daten werden vorher im aufrufenden (Haupt-)Thread erzeugt.
     */
    fun runNow(context: Context, store: GymStore, onDone: ((Boolean) -> Unit)? = null) {
        val tree = folder ?: return
        val json = store.exportJson()
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val ok = runCatching { write(app, tree, json) }
            main.post {
                if (ok.isSuccess) {
                    last = System.currentTimeMillis()
                    error = null
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last", last).apply()
                    store.markExported()   // zählt als Export („Letzter Export“ im Menü)
                } else {
                    error = ok.exceptionOrNull()?.message ?: "?"
                }
                onDone?.invoke(ok.isSuccess)
            }
        }.start()
    }

    private fun write(context: Context, tree: Uri, json: String) {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
        val name = "$PREFIX$date.json"

        // Vorhandene Sicherungen auflisten: (Dokument-ID, Name)
        val existing = mutableListOf<Pair<String, String>>()
        resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: continue
                if (n.startsWith(PREFIX) && n.endsWith(".json")) existing += c.getString(0) to n
            }
        }

        // Heutige Sicherung überschreiben bzw. neu anlegen
        val today = existing.firstOrNull { it.second == name }
        val target = if (today != null) DocumentsContract.buildDocumentUriUsingTree(tree, today.first)
        else DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: error("Datei konnte nicht angelegt werden")
        resolver.openOutputStream(target, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: error("Datei nicht beschreibbar")

        // Nur die neuesten KEEP behalten (Name enthält das Datum → alphabetisch = zeitlich)
        val all = (existing.map { it.second } + name).distinct().sortedDescending()
        for ((id, n) in existing) if (n !in all.take(KEEP)) {
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}

/** Abschnitt „Automatisches Backup“ im Einstellungen-Dialog. */
@Composable
fun AutoBackupSettings(store: GymStore) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            AutoBackup.setFolder(context, uri)
            busy = true
            AutoBackup.runNow(context, store) { busy = false }   // gleich eine erste Sicherung anlegen
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Automatisches Backup", "Automatic backup"), style = MaterialTheme.typography.titleSmall)
        val label = AutoBackup.folderLabel()
        if (label == null) {
            Text(
                tr("Sichert deine Daten täglich in einen Ordner deiner Wahl (die letzten 7 bleiben).",
                    "Backs up your data daily to a folder of your choice (keeps the last 7)."),
                style = small, color = muted
            )
            OutlinedButton(onClick = { pickFolder.launch(null) }) {
                Text(tr("Ordner wählen", "Choose folder"))
            }
        } else {
            Text("📁 $label", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (busy) tr("Sichere …", "Backing up …")
                else if (AutoBackup.last == 0L) tr("Noch keine Sicherung", "No backup yet")
                else tr("Letzte Sicherung: ", "Last backup: ") + fmtDateTime(AutoBackup.last),
                style = small, color = muted
            )
            AutoBackup.error?.let {
                Text(
                    tr("Fehler: ", "Error: ") + it, style = small,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { busy = true; AutoBackup.runNow(context, store) { busy = false } }
                ) { Text(tr("Jetzt sichern", "Back up now")) }
                TextButton(onClick = { AutoBackup.disable(context) }) {
                    Text(tr("Ausschalten", "Turn off"))
                }
            }
        }
    }
}
