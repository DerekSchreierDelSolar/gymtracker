package com.example.gymtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Pausen-Timer als Benachrichtigung (nur Android): während der Pause eine stille Benachrichtigung mit
 * Countdown (das System zählt selbst herunter, die App muss dafür nichts tun). Ist die App beim Ende der
 * Pause nicht sichtbar, kommt „Pause vorbei“ mit Ton/Vibration. Ohne Benachrichtigungs-Erlaubnis
 * (ab Android 13 nötig) passiert einfach nichts – der Timer in der App läuft trotzdem.
 */
object RestTimer {
    private const val CH_RUNNING = "rest_running"
    private const val CH_DONE = "rest_done"
    private const val ID = 2

    /**
     * Ende der Pause über einen Handler des Haupt-Threads statt über Compose: Compose pausiert seine
     * Timer, solange die App nicht sichtbar ist – genau dann wird die Meldung aber gebraucht.
     */
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Wird von MainActivity in onStart/onStop gesetzt */
    @Volatile var appVisible = true

    private fun channels(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < 26) return
        nm.createNotificationChannel(NotificationChannel(CH_RUNNING, tr("Pause läuft", "Rest running"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, tr("Pause vorbei", "Rest over"), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @Suppress("DEPRECATION")
    private fun builder(context: Context, channel: String) =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel) else Notification.Builder(context)

    /** Countdown bis `end` anzeigen und das Ende planen (ersetzt eine laufende Anzeige, z. B. nach +15 s). */
    fun show(context: Context, end: Long, exercise: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        channels(nm)
        val b = builder(context, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Pause", "Rest"))
            .setContentText(tr("Danach: ", "Next: ") + exName(exercise))
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp(context))
        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true)
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter((end - System.currentTimeMillis()).coerceAtLeast(1000))
        // Ende planen (ersetzt einen früheren Termin, z. B. nach +15 s)
        val app = context.applicationContext
        pending?.let { handler.removeCallbacks(it) }
        pending = Runnable { pending = null; done(app) }.also { handler.postDelayed(it, (end - System.currentTimeMillis()).coerceAtLeast(0)) }
        runCatching { nm.notify(ID, b.build()) }
    }

    /** Pause zu Ende: Countdown entfernen; ist die App im Hintergrund, laut melden. */
    fun done(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(ID)
        if (appVisible) return
        channels(nm)
        @Suppress("DEPRECATION")
        val b = builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Pause vorbei 💪", "Rest over 💪"))
            .setContentText(tr("Weiter mit dem nächsten Satz.", "On to the next set."))
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL) // Ton + Vibration (vor Android 8; danach über den Kanal)
            .setContentIntent(openApp(context))
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(60_000)
        runCatching { nm.notify(ID, b.build()) }
    }

    /** Pause übersprungen / Training beendet */
    fun cancel(context: Context) {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}
