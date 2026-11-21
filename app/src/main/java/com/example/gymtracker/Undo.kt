package com.example.gymtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * „Gelöscht – Rückgängig“: statt Nachfrage-Dialog wird sofort gelöscht und 8 s lang eine Leiste
 * mit „Rückgängig“ gezeigt (wie Gmail). Global, damit die Leiste auch nach Bildschirmwechsel bleibt.
 * Aufruf: Undo.show("Satz gelöscht") { store.restoreSet(set) }
 */
object Undo {
    var message by mutableStateOf<String?>(null)
        private set
    /** Zähler, damit eine neue Meldung den 8-s-Timer neu startet */
    var version by mutableIntStateOf(0)
        private set
    private var action: (() -> Unit)? = null

    fun show(msg: String, undo: () -> Unit) {
        message = msg
        action = undo
        version++
    }

    fun undo() {
        action?.invoke()
        clear()
    }

    fun clear() {
        message = null
        action = null
    }
}

/** Leiste unten über allem (in MainActivity um GymApp gelegt). */
@Composable
fun BoxScope.UndoBar() {
    val msg = Undo.message ?: return
    LaunchedEffect(Undo.version) {
        delay(8000)
        Undo.clear()
    }
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp) // über Navigation und schwebendem Button
            .fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(msg, Modifier.weight(1f).padding(vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { Undo.undo() }) {
                Text(tr("Rückgängig", "Undo"), color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}
