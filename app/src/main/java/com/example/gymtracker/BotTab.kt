package com.example.gymtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Tab „Fragen“: Chat mit dem Bot (Bot.kt). Antworten mit Übungen zeigen Chips, die das Wiki-Blatt öffnen.
 * Unten Vorschläge der letzten Bot-Antwort und das Eingabefeld.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotTab(modifier: Modifier) {
    val messages = Bot.history
    if (messages.isEmpty()) messages.add(Bot.greeting())
    var input by remember { mutableStateOf("") }
    var wiki by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    // Neue Nachricht → nach unten scrollen
    LaunchedEffect(messages.size) { list.animateScrollToItem(messages.lastIndex) }

    fun send(text: String) {
        Bot.ask(text)
        input = ""
    }

    Column(modifier.imePadding()) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(messages) { _, m -> Bubble(m, onExercise = { wiki = it }) }
        }
        // Vorschläge der letzten Antwort
        val suggestions = messages.lastOrNull()?.suggestions.orEmpty()
        if (suggestions.isNotEmpty()) Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { s -> SuggestionChip(onClick = { send(s) }, label = { Text(s) }) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(tr("Frag mich etwas …", "Ask me something …")) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank(), modifier = Modifier.size(52.dp)) {
                AppIcon(Ico.SEND)
            }
        }
    }
    wiki?.let { WikiSheet(it) { wiki = null } }
}

/** Sprechblase: eigene Fragen rechts (Akzentfarbe), Antworten links; darunter Übungs-Chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(m: BotMessage, onExercise: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (m.fromUser) 18.dp else 4.dp, bottomEnd = if (m.fromUser) 4.dp else 18.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                m.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (m.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            for ((title, bases) in m.groups) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bases.forEach { b -> AssistChip(onClick = { onExercise(b) }, label = { Text(baseName(b)) }) }
                }
            }
        }
    }
}
