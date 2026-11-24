package com.example.gymtracker

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Einstellungen als eigener Bildschirm (früher ein Dialog) ----------
//
// Übersicht ohne langes Scrollen: Darstellung (Design/Sprache/Einheit) je in einer Zeile, alles Größere als
// Zeile mit Kurz-Zusammenfassung → Unterseite mit den eigentlichen Einstellungen (ProfileSettings, CycleSettings …).
// Web: settingsScreen in screens/settings.js (nav.settings / nav.settingsPage).

private enum class SettingsPage { PROFILE, CYCLE, REMINDER, HEALTH, BACKUP }

@Composable
private fun SettingsPage.title(): String = when (this) {
    SettingsPage.PROFILE -> tr("Profil für Kalorien", "Profile for calories")
    SettingsPage.CYCLE -> tr("Trainingsblock mit Deload", "Training block with deload")
    SettingsPage.REMINDER -> tr("Trainings-Erinnerung", "Workout reminder")
    SettingsPage.HEALTH -> "Health Connect"
    SettingsPage.BACKUP -> tr("Automatisches Backup", "Automatic backup")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: GymStore, onBack: () -> Unit) {
    // Unterseite als Name speichern (rememberSaveable kann Strings, übersteht Drehen)
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { SettingsPage.valueOf(it) }
    // Zurück auf einer Unterseite → Übersicht (dieser BackHandler steht nach dem in GymApp und hat daher Vorrang)
    BackHandler(enabled = page != null) { pageName = null }
    val backup = rememberBackupActions(store)
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(page?.title() ?: tr("Einstellungen", "Settings")) },
            navigationIcon = { IconAction(Ico.BACK, onClick = { if (page != null) pageName = null else onBack() }) }
        )
    }) { padding ->
        // eigene Scroll-Position je Seite (key = Seite)
        val scroll = key(page) { rememberScrollState() }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (page) {
                null -> SettingsOverview(store, backup, onPage = { pageName = it.name }, onReset = { confirmReset = true })
                SettingsPage.PROFILE -> ProfileSettings(store)
                SettingsPage.CYCLE -> CycleSettings(store)
                SettingsPage.REMINDER -> ReminderSettings()
                SettingsPage.HEALTH -> HealthSettings(store)
                SettingsPage.BACKUP -> AutoBackupSettings(store)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmReset) ResetProgressDialog(store, onDismiss = { confirmReset = false })
}

@Composable
private fun SettingsOverview(store: GymStore, backup: BackupActions, onPage: (SettingsPage) -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    val off = tr("Aus", "Off")

    SettingsGroup(tr("Darstellung", "Appearance")) {
        ChoiceRow(tr("Design", "Theme"), listOf("system" to tr("System", "System"), "light" to tr("Hell", "Light"), "dark" to tr("Dunkel", "Dark")),
            store.themeMode) { store.setTheme(it) }
        LanguageRow(store)
        // nur Anzeige/Eingabe – gespeichert wird immer in kg, Umschalten ändert keine Daten
        ChoiceRow(tr("Einheit", "Unit"), listOf("kg" to "kg", "lb" to "lbs"), weightUnit) { store.setUnit(it) }
    }

    SettingsGroup(tr("Training", "Training")) {
        val p = store.profile
        NavRow("👤", SettingsPage.PROFILE.title(),
            listOfNotNull(
                p.sex?.let { if (it == "m") tr("Männlich", "Male") else tr("Weiblich", "Female") },
                p.birthYear?.toString(),
                p.heightCm?.let { "$it cm" }
            ).joinToString(" · ").ifEmpty { tr("Nicht ausgefüllt – für genauere kcal", "Not set – for more accurate calories") }
        ) { onPage(SettingsPage.PROFILE) }
        val c = store.cycle
        NavRow("📆", SettingsPage.CYCLE.title(),
            if (c == null) off
            else cycleWeek(c)?.let { w -> tr("An · Woche $w von ${c.length}", "On · week $w of ${c.length}") } ?: tr("An", "On")
        ) { onPage(SettingsPage.CYCLE) }
        val minutes = Reminder.minutes(context)
        NavRow("🔔", SettingsPage.REMINDER.title(),
            if (Reminder.enabled(context)) tr("An · ", "On · ") + "%02d:%02d".format(minutes / 60, minutes % 60) else off
        ) { onPage(SettingsPage.REMINDER) }
    }

    SettingsGroup(tr("Verbindungen & Daten", "Connections & data")) {
        NavRow("❤", "Health Connect",
            when {
                !HealthSync.available(context) -> tr("Ab Android 14", "From Android 14")
                !HealthSync.enabled -> off
                HealthSync.lastSync > 0 -> tr("An · zuletzt ", "On · last ") + fmtDateTime(HealthSync.lastSync)
                else -> tr("An", "On")
            }
        ) { onPage(SettingsPage.HEALTH) }
        NavRow("💾", SettingsPage.BACKUP.title(),
            AutoBackup.folderLabel()?.let { label ->
                "📁 $label" + if (AutoBackup.last > 0) " · " + fmtDate(AutoBackup.last) else ""
            } ?: off
        ) { onPage(SettingsPage.BACKUP) }
        NavRow("📤", tr("Daten exportieren", "Export data"),
            if (store.lastExport == 0L) tr("Noch nie exportiert", "Never exported")
            else tr("Letzter Export: ", "Last export: ") + fmtDate(store.lastExport),
            chevron = false
        ) { backup.export() }
        NavRow("📥", tr("Daten importieren", "Import data"), tr("Ersetzt die aktuellen Daten", "Replaces the current data"),
            chevron = false) { backup.import() }
    }

    // Fortschritt zurücksetzen (rot, mit Nachfrage und danach „Rückgängig“)
    SettingsGroup(null) {
        NavRow("🗑", tr("Fortschritt zurücksetzen", "Reset progress"),
            tr("Übungen und Workouts bleiben erhalten", "Exercises and workouts are kept"),
            color = MaterialTheme.colorScheme.error, chevron = false, onClick = onReset)
    }
}

/** Karte mit kleiner Überschrift darüber (null = ohne). */
@Composable
private fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) Text(
            title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

/** Sprache: ein Feld mit der aktuellen Sprache, antippen = Liste aller Sprachen (6 passen nicht als Chips in eine Zeile). */
@Composable
private fun LanguageRow(store: GymStore) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(tr("Sprache", "Language"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f)) {
            SegmentChip("🌐 " + (LANGUAGES.firstOrNull { it.first == appLanguage }?.second ?: appLanguage) + "  ▾", true, { open = true },
                Modifier.fillMaxWidth())
            DropdownMenu(open, onDismissRequest = { open = false }) {
                LANGUAGES.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name, fontWeight = if (code == appLanguage) FontWeight.Bold else null) },
                        trailingIcon = if (code == appLanguage) ({ Text("✓") }) else null,
                        onClick = { open = false; store.setLanguage(code) }
                    )
                }
            }
        }
    }
}

/** Eine Zeile: Beschriftung links, gleich breite Auswahl-Knöpfe rechts (statt Überschrift + Chip-Reihe darunter). */
@Composable
private fun ChoiceRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(76.dp))
        options.forEach { (key, text) ->
            SegmentChip(text, selected == key, { onSelect(key) }, Modifier.weight(1f))
        }
    }
}

/** Zeile mit Symbol, Titel, Zusammenfassung und › (öffnet eine Unterseite bzw. führt eine Aktion aus). */
@Composable
private fun NavRow(
    emoji: String,
    title: String,
    summary: String,
    color: Color = Color.Unspecified,
    chevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            Text(
                summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (chevron) AppIcon(Ico.CHEVRON, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Nachfrage „Fortschritt zurücksetzen?“; danach „Rückgängig“ über die UndoBar. */
@Composable
private fun ResetProgressDialog(store: GymStore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Fortschritt zurücksetzen?", "Reset progress?")) },
        text = {
            val sets = plural(store.sets.size, tr("Satz", "set"), tr("Sätze", "sets"))
            val units = plural(store.sessions.size, tr("Trainingseinheit", "workout"), tr("Trainingseinheiten", "workouts"))
            val weights = plural(store.weights.size, tr("Gewichtseintrag", "weight entry"), tr("Gewichtseinträge", "weight entries"))
            Text(
                tr(
                    "$sets, $units und $weights werden gelöscht. Tipp: vorher „Daten exportieren“.",
                    "$sets, $units and $weights will be deleted. Tip: \"Export data\" first."
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val snap = store.resetProgress()
                    onDismiss()
                    Undo.show(tr("Fortschritt zurückgesetzt", "Progress reset")) { store.restoreProgress(snap) }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(tr("Zurücksetzen", "Reset")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

// ---------- Export / Import (⋮-Menü der Startseite und Einstellungen) ----------

/** Auslöser für die Android-Dateidialoge „Speichern unter …“ bzw. „Öffnen …“. */
class BackupActions(val export: () -> Unit, val import: () -> Unit)

/**
 * Richtet Export/Import ein und zeigt bei Bedarf die Nachfrage „Sicherung importieren?“.
 * (Ausnahmsweise eine Composable mit Rückgabewert – wie rememberLauncherForActivityResult, nur mit Dialog.)
 */
@Composable
fun rememberBackupActions(store: GymStore): BackupActions {
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    // Inhalt einer gewählten Backup-Datei, die noch bestätigt werden muss
    var pendingImport by remember { mutableStateOf<String?>(null) }

    // Die App braucht dafür keine Speicher-Berechtigung: der Nutzer wählt den Ort selbst.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error(tr("Datei nicht beschreibbar", "File not writable"))
            out.use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) }
        }.onSuccess {
            store.markExported()
            toast(tr("Backup gespeichert", "Backup saved"))
        }.onFailure { toast(tr("Export fehlgeschlagen: ", "Export failed: ") + it.message) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Höchstens 20 MB lesen (echte Sicherungen sind weit kleiner): eine riesige Datei würde sonst
        // den Arbeitsspeicher sprengen und die App abstürzen lassen
        val maxBytes = 20 * 1024 * 1024
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(maxBytes + 1)
                if (bytes.size > maxBytes) null else String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
        if (text != null && store.inspectBackup(text) != null) pendingImport = text
        else toast(tr("Das ist keine gültige GymTracker-Sicherung.", "This is not a valid GymTracker backup."))
    }

    val importText = pendingImport
    val info = importText?.let { store.inspectBackup(it) }
    if (importText != null && info != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(tr("Sicherung importieren?", "Import backup?")) },
            text = {
                Text(
                    tr("Die Sicherung enthält:", "The backup contains:") + "\n" +
                        "• ${plural(info.exercises, tr("Übung", "exercise"), tr("Übungen", "exercises"))}\n" +
                        "• ${plural(info.sets, tr("Satz", "set"), tr("Sätze", "sets"))}\n" +
                        "• ${plural(info.workouts, tr("Workout", "workout"), tr("Workouts", "workouts"))}\n" +
                        "• ${plural(info.sessions, tr("Trainingseinheit", "session"), tr("Trainingseinheiten", "sessions"))}\n\n" +
                        tr(
                            "Deine aktuellen Daten werden dadurch ersetzt. Eine Kopie davon wird vorher in der App abgelegt.",
                            "Your current data will be replaced. A copy of it is kept in the app first."
                        )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    runCatching { store.importJson(importText) }
                        .onSuccess { toast(tr("Sicherung importiert", "Backup imported")) }
                        .onFailure { toast(tr("Import fehlgeschlagen: ", "Import failed: ") + it.message) }
                }) { Text(tr("Importieren", "Import")) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(tr("Abbrechen", "Cancel")) } }
        )
    }

    return BackupActions(
        export = {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
            exportLauncher.launch("gymtracker-backup-$date.json")
        },
        // */*: manche Dateimanager kennzeichnen .json nicht als application/json
        import = { importLauncher.launch(arrayOf("*/*")) }
    )
}
