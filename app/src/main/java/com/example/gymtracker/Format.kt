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

/** Volumen (kg × Wdh.) zur Anzeige: ab 1000 ganzzahlig mit Tausendertrennzeichen (DE 10.753, EN 10,753), sonst wie fmtW.
 *  Nur zum Anzeigen – nie in Eingabefelder (dort würde „1.000“ als 1 gelesen). Web: fmtVolume in core.js. */
fun fmtVolume(v: Double): String = if (v >= 1000) String.format(appLocale(), "%,d", Math.round(v)) else fmtW(v)

/** kcal-Anzeige mit Tausenderpunkt wie das Volumen (z. B. „1.467“). */
fun fmtKcal(v: Number): String = fmtVolume(Math.round(v.toDouble()).toDouble())

fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

fun fmtDate(t: Long): String =
    SimpleDateFormat(tr("dd.MM.yyyy", "dd/MM/yyyy"), appLocale()).format(Date(t))

fun fmtDateTime(t: Long): String =
    SimpleDateFormat(tr("dd.MM.yyyy, HH:mm 'Uhr'", "dd/MM/yyyy, HH:mm"), appLocale()).format(Date(t))

/** Dauer als „m:ss“ bzw. „h:mm:ss“. */
fun fmtDuration(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.GERMANY, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.GERMANY, "%d:%02d", m, s)
}


