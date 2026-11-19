package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Hilfsfunktionen ----------

fun fmtW(w: Double): String =
    if (w % 1.0 == 0.0) w.toLong().toString()
    else String.format(appLocale(), "%.1f", w) // DE: 62,5 – EN: 62.5

fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

fun fmtDate(t: Long): String =
    SimpleDateFormat(tr("dd.MM.yyyy", "MM/dd/yyyy"), appLocale()).format(Date(t))

fun fmtDateTime(t: Long): String =
    SimpleDateFormat(tr("dd.MM.yyyy, HH:mm 'Uhr'", "MM/dd/yyyy, h:mm a"), appLocale()).format(Date(t))

/** Dauer als „m:ss“ bzw. „h:mm:ss“. */
fun fmtDuration(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.GERMANY, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.GERMANY, "%d:%02d", m, s)
}


