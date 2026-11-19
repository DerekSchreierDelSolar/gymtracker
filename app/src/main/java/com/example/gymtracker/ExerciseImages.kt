package com.example.gymtracker

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

// ---------- Übungsbilder (ersetzen die Piktogramme, wo vorhanden) ----------
//
// Ablage: app/src/main/assets/exercises/<schlüssel>.jpg (512 × 512, JPEG ~50 KB).
// Schlüssel = Übungsname ohne Umlaute/Sonderzeichen (exerciseImageKey), z. B.
//   „Bankdrücken (Langhantel)“ → bankdruecken-langhantel.jpg   (nur diese Variante)
//   „Bankdrücken“ (Grundübung) → bankdruecken.jpg              (alle Varianten ohne eigenes Bild)
// Die Web-Version bekommt die Bilder per web/tools/gen-data.mjs (Kopie nach web/img/exercises/).

/** „Bankdrücken (Langhantel)“ → „bankdruecken-langhantel“. */
fun exerciseImageKey(name: String): String =
    name.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

object ExerciseImages {
    private var assets: AssetManager? = null
    /** Vorhandene Bild-Schlüssel (Dateinamen ohne .jpg). */
    private var keys: Set<String> = emptySet()
    private val cache = HashMap<String, ImageBitmap>()

    fun load(context: Context) {
        assets = context.assets
        keys = context.assets.list("exercises").orEmpty()
            .filter { it.endsWith(".jpg") }
            .map { it.removeSuffix(".jpg") }
            .toSet()
    }

    /** Schlüssel des Bildes einer Übung: eigenes Bild, sonst das der Grundübung, sonst null. */
    fun keyFor(name: String): String? {
        exerciseImageKey(name).takeIf { it in keys }?.let { return it }
        val base = CATALOG.firstOrNull { it.name == name }?.base ?: return null
        return exerciseImageKey(base).takeIf { it in keys }
    }

    /** Bild laden (einmal dekodiert, danach aus dem Speicher). */
    fun bitmap(key: String): ImageBitmap? = cache[key] ?: runCatching {
        assets!!.open("exercises/$key.jpg").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }.getOrNull()?.also { cache[key] = it }
}

/** Übungsbild (quadratisch zugeschnitten); zeichnet nichts, wenn es keins gibt. */
@Composable
fun ExerciseImage(name: String, modifier: Modifier = Modifier): Boolean {
    val bmp = ExerciseImages.keyFor(name)?.let(ExerciseImages::bitmap) ?: return false
    Image(bmp, contentDescription = exName(name), modifier = modifier, contentScale = ContentScale.Crop)
    return true
}
