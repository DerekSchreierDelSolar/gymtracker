package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Hilfsfunktionen ----------

fun fmtW(w: Double): String {
    val r = Math.round(w * 10) / 10.0 // auf 0,1 runden (sonst „225,0“ nach lbs → kg → lbs)
    return if (r % 1.0 == 0.0) r.toLong().toString()
    else String.format(appLocale(), "%.1f", r) // DE: 62,5 – EN: 62.5
}

// ---------- Gewichtseinheit (kg / lbs) ----------
// Gespeichert wird IMMER in kg. Nur Anzeige und Eingabe rechnen in die gewählte Einheit um
// (wie eine View in SQL, die eine Spalte umrechnet – die Tabelle bleibt gleich). Web: core.js.

/** "kg" oder "lb" – globaler Compose-State wie appLanguage, gesetzt über store.setUnit(). */
var weightUnit by mutableStateOf("kg")
val isLb: Boolean get() = weightUnit == "lb"
const val LB_PER_KG = 2.20462262

/** Einheit zur Anzeige: „kg“ bzw. „lbs“. */
val wUnit: String get() = if (isLb) "lbs" else "kg"
/** kg → angezeigte Einheit (Zahl). */
fun toUnit(kg: Double): Double = if (isLb) kg * LB_PER_KG else kg
/** Eingabe in der angezeigten Einheit → kg. */
fun fromUnit(v: Double): Double = if (isLb) v / LB_PER_KG else v
/** Gewicht (kg) als Zahl in der angezeigten Einheit, z. B. für Eingabefelder. */
fun fmtWU(kg: Double): String = fmtW(toUnit(kg))
/** Gewicht (kg) mit Einheit: „62,5 kg“ bzw. „137.8 lbs“. */
//   = geschütztes Leerzeichen: Zahl und Einheit werden nie auf zwei Zeilen getrennt
fun fmtKg(kg: Double): String = "${fmtWU(kg)} $wUnit"
/** Volumen (kg × Wdh.) mit Einheit, Tausenderpunkt wie fmtVolume. */
fun fmtVol(kg: Double): String = "${fmtVolume(toUnit(kg))} $wUnit"
/** Großes Volumen: Tonnen bzw. 1.000 lbs („k lbs“) – Zahl in dieser Einheit und Name. */
fun toBig(kg: Double): Double = toUnit(kg) / 1000
val bigUnit: String get() = if (isLb) "k lbs" else "t"
/** Eingabefeld lesen („62,5“ oder „62.5“) → kg, null wenn keine Zahl. */
fun parseW(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.let(::fromUnit)
/** Kleinster sinnvoller Gewichtsschritt (Scheibenpaar) in kg: 2,5 kg bzw. 5 lbs. */
val plateStep: Double get() = if (isLb) 5 / LB_PER_KG else 2.5
/** Auf ein Vielfaches von plateStep runden (in der angezeigten Einheit glatt). */
fun roundPlate(kg: Double): Double = Math.round(kg / plateStep) * plateStep

/** Volumen (kg × Wdh.) zur Anzeige: ab 1000 ganzzahlig mit Tausendertrennzeichen (DE 10.753, EN 10,753), sonst wie fmtW.
 *  Nur zum Anzeigen – nie in Eingabefelder (dort würde „1.000“ als 1 gelesen). Web: fmtVolume in core.js. */
fun fmtVolume(v: Double): String = if (v >= 1000) String.format(appLocale(), "%,d", Math.round(v)) else fmtW(v)

/** kcal-Anzeige mit Tausenderpunkt wie das Volumen (z. B. „1.467“). */
fun fmtKcal(v: Number): String = fmtVolume(Math.round(v.toDouble()).toDouble())

fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

/** Datum mit Punkten (Deutsch, Türkisch) oder Schrägstrichen (Englisch, Französisch, Italienisch, Spanisch) – immer Tag zuerst. */
val dateDots: Boolean get() = appLanguage == "de" || appLanguage == "tr"

fun fmtDate(t: Long): String =
    SimpleDateFormat(if (dateDots) "dd.MM.yyyy" else "dd/MM/yyyy", appLocale()).format(Date(t))

fun fmtDateTime(t: Long): String =
    SimpleDateFormat(if (isGerman) "dd.MM.yyyy, HH:mm 'Uhr'" else if (dateDots) "dd.MM.yyyy, HH:mm" else "dd/MM/yyyy, HH:mm", appLocale()).format(Date(t))

/** Dauer als „m:ss“ bzw. „h:mm:ss“. */
fun fmtDuration(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.GERMANY, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.GERMANY, "%d:%02d", m, s)
}


