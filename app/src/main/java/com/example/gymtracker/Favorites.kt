package com.example.gymtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Lieblingsübungen (★): `GymStore.favorites` (JSON `favorites` = [Übungsnamen] nur wenn nicht leer).
 * In allen Übungslisten stehen Favoriten zuerst (`favoritesFirst`, nach `sortedByName` aufrufen),
 * in gruppierten Listen als eigener Abschnitt „★ Favoriten“ ganz oben (`FavoritesHeader`).
 * Markieren: Stern oben im Übungsdetail (`FavoriteButton`) bzw. im Kachel-Menü. Web: favorites in store.js / core.js.
 */

/** Goldgelb – gut sichtbar in hellem und dunklem Design. */
val FAVORITE_COLOR = Color(0xFFFFB300)

/** Favoriten nach vorn, sonst bleibt die Reihenfolge (stabil) – also erst alphabetisch sortieren, dann das hier. */
fun <T> List<T>.favoritesFirst(store: GymStore, name: (T) -> String): List<T> {
    if (store.favorites.isEmpty()) return this
    val (fav, rest) = partition { store.isFavorite(name(it)) }
    return fav + rest
}

/** Kleiner ausgefüllter Stern hinter/über einem Übungsnamen. */
@Composable
fun FavoriteMark(modifier: Modifier = Modifier) {
    AppIcon(Ico.STAR_FILLED, modifier.size(14.dp), tint = FAVORITE_COLOR)
}

/** Stern-Knopf zum Markieren (Top-Leiste, Zeilen). */
@Composable
fun FavoriteButton(store: GymStore, name: String) {
    val fav = store.isFavorite(name)
    IconButton(onClick = { store.toggleFavorite(name) }) {
        AppIcon(
            if (fav) Ico.STAR_FILLED else Ico.STAR,
            tint = if (fav) FAVORITE_COLOR else LocalContentColor.current
        )
    }
}

/**
 * Überschrift des Favoriten-Abschnitts (passt zu GroupHeader).
 * Mit `onToggle` aufklappbar: Anzahl + Pfeil, ganze Zeile antippbar.
 */
@Composable
fun FavoritesHeader(count: Int = 0, expanded: Boolean = true, onToggle: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onToggle != null) Modifier.clickable(onClick = onToggle).then(base) else base
    ) {
        AppIcon(Ico.STAR_FILLED, Modifier.size(16.dp), tint = FAVORITE_COLOR)
        Spacer(Modifier.width(8.dp))
        Text(tr("Favoriten", "Favorites"), style = MaterialTheme.typography.titleMedium)
        if (onToggle != null) {
            Text(" ($count)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            AppIcon(Ico.CHEVRON, Modifier.size(20.dp).rotate(if (expanded) 270f else 90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Übungsname, bei Favoriten mit Stern dahinter (Listenzeilen). */
@Composable
fun NameWithStar(store: GymStore, name: String, label: String = exName(name)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f, fill = false))
        if (store.isFavorite(name)) { Spacer(Modifier.width(6.dp)); FavoriteMark() }
    }
}
