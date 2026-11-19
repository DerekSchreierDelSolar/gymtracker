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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush

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

/** Hat die Übung ein Bild? (für durchsichtige Kopfzeilen über dem Hintergrundbild) */
fun hasExerciseImage(name: String): Boolean = ExerciseImages.keyFor(name) != null

/**
 * Übungsbild als dezenter Hintergrund (in einer Box hinter dem Inhalt):
 * oben gut sichtbar, nach unten weich in die Hintergrundfarbe auslaufend, damit Text lesbar bleibt.
 */
@Composable
fun BoxScope.ExerciseBackdrop(name: String) {
    val bmp = ExerciseImages.keyFor(name)?.let(ExerciseImages::bitmap) ?: return
    val bg = MaterialTheme.colorScheme.background
    // Quadratisch in voller Breite oben (ganzes Motiv sichtbar), darunter normaler Hintergrund
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to bg.copy(alpha = 0.1f),
                        0.55f to bg.copy(alpha = 0.45f),
                        1f to bg
                    )
                )
        )
    }
}
