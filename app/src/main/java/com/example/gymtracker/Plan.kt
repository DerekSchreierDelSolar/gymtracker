package com.example.gymtracker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Calendar

// ---------- Trainingsplan: Workouts an Wochentagen + Erinnerung ----------
//
// Workout.days = Trainingstage (1 = Montag … 7 = Sonntag), gespeichert im Backup.
// Die Erinnerung (Ein/Aus + Uhrzeit) ist gerätegebunden (SharedPreferences, wie das automatische Backup)
// und nutzt AlarmManager: einmal täglich zur gewählten Zeit prüft ReminderReceiver, ob heute ein
// geplantes Workout noch offen ist, und zeigt dann eine Benachrichtigung. Danach wird der nächste Tag geplant.

/** Wochentag 1 = Montag … 7 = Sonntag */
fun weekdayOf(time: Long = System.currentTimeMillis()): Int {
    val dow = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.DAY_OF_WEEK) // So = 1 … Sa = 7
    return (dow + 5) % 7 + 1
}

fun weekdayShort(d: Int): String =
    tr(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[d - 1], listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[d - 1])

fun weekdayLong(d: Int): String = tr(
    listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")[d - 1],
    listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[d - 1]
)

/** „Mo · Mi · Fr“ */
fun daysLabel(days: Set<Int>): String = days.sorted().joinToString(" · ") { weekdayShort(it) }

/** Vorschlag laut Plan für die Startseite: Workout + Überschrift („Heute dran“, „Morgen dran“, „Als Nächstes · Mittwoch“). */
data class PlanPick(val workout: Workout, val title: String)

/** Heute geplantes Workout, das heute noch nicht trainiert wurde (null = keins). */
fun dueToday(store: GymStore): Workout? {
    val today = weekdayOf()
    val key = dayKey(System.currentTimeMillis())
    val done = store.sessions.filter { dayKey(it.start) == key }.map { it.workoutId }.toSet()
    return store.workouts.firstOrNull { today in it.days && it.exercises.isNotEmpty() && it.id !in done }
}

fun planPick(store: GymStore): PlanPick? {
    val planned = store.workouts.filter { it.days.isNotEmpty() && it.exercises.isNotEmpty() }
    if (planned.isEmpty()) return null
    dueToday(store)?.let { return PlanPick(it, tr("Heute dran", "Today")) }
    val today = weekdayOf()
    for (k in 1..7) {
        val d = (today - 1 + k) % 7 + 1
        planned.firstOrNull { d in it.days }?.let {
            return PlanPick(it, if (k == 1) tr("Morgen dran", "Tomorrow") else tr("Als Nächstes · ", "Next · ") + weekdayLong(d))
        }
    }
    return null
}

/** Trainingstage eines Workouts als Chips Mo–So (antippen = an/aus). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutDaysRow(store: GymStore, workout: Workout, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(tr("Trainingstage", "Training days"), style = MaterialTheme.typography.titleSmall)
        Text(
            tr("Geplante Tage erscheinen auf der Startseite als „Heute dran“.", "Planned days show up on the home screen as \"Today\"."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d ->
                FilterChip(selected = d in workout.days, onClick = { store.toggleDay(workout.id, d) }, label = { Text(weekdayShort(d)) })
            }
        }
    }
}

// ---------- Erinnerung (nur Android) ----------

object Reminder {
    private const val PREFS = "reminder"
    private const val CHANNEL = "training_reminder"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)
    /** Uhrzeit in Minuten nach Mitternacht (Standard 17:00) */
    fun minutes(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("minutes", 17 * 60)

    fun set(context: Context, on: Boolean, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).putInt("minutes", minutes).apply()
        schedule(context)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Nächsten Termin planen (bzw. löschen, wenn aus). Ungefähre Uhrzeit reicht – dafür braucht es keine Sonderrechte. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context))
        if (!enabled(context)) return
        val m = minutes(context)
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 30_000) add(Calendar.DAY_OF_MONTH, 1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending(context))
    }

    /** Benachrichtigung „Heute dran: Push-Tag“; Tippen öffnet die App. */
    fun notify(context: Context, workout: Workout) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Trainings-Erinnerung", "Workout reminder"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, CHANNEL) else android.app.Notification.Builder(context)
        val n = b.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Heute dran: ", "Today: ") + workout.name + " 💪")
            .setContentText(tr("Tippe, um dein Training zu starten.", "Tap to start your workout."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}

/** Wird vom AlarmManager (täglich) und nach dem Neustart des Handys aufgerufen. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && Reminder.enabled(context)) {
            val store = GymStore(context.applicationContext) // lädt die Daten und stellt die App-Sprache ein
            dueToday(store)?.let { Reminder.notify(context, it) }
        }
        Reminder.schedule(context) // nächsten Tag planen
    }
}

/** Einstellungen: Erinnerung an geplanten Tagen ein/aus + Uhrzeit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettings() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Reminder.enabled(context)) }
    var minutes by remember { mutableIntStateOf(Reminder.minutes(context)) }
    var pickTime by remember { mutableStateOf(false) }
    // Ab Android 13 muss der Nutzer Benachrichtigungen erlauben
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        on = granted
        Reminder.set(context, granted, minutes)
    }
    fun enable(value: Boolean) {
        if (value && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        on = value
        Reminder.set(context, value, minutes)
    }
    val time = "%02d:%02d".format(minutes / 60, minutes % 60)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Trainings-Erinnerung", "Workout reminder"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("An geplanten Trainingstagen, wenn du noch nicht trainiert hast.", "On planned training days if you haven't trained yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = on, onCheckedChange = ::enable)
        }
        if (on) TextButton(onClick = { pickTime = true }) { Text(tr("Uhrzeit: ", "Time: ") + time) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(tr("Uhrzeit der Erinnerung", "Reminder time")) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    minutes = state.hour * 60 + state.minute
                    Reminder.set(context, on, minutes)
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(tr("Abbrechen", "Cancel")) } }
        )
    }
}
