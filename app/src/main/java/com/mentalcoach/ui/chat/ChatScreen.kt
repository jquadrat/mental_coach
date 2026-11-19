package com.mentalcoach.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mentalcoach.R
import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.Lang
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Suggestion
import com.mentalcoach.ui.components.MicButton
import com.mentalcoach.ui.components.PreviewData
import com.mentalcoach.ui.components.PrimaryButton
import com.mentalcoach.ui.components.SecondaryButton
import com.mentalcoach.ui.theme.MentalCoachTheme

@Composable
fun ChatScreen(
    chat: List<ChatTurn>,
    suggestion: Suggestion?,
    nextTitle: String?,
    draft: String,
    lang: Lang,
    sending: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onNext: () -> Unit,
    onEndVisit: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(chat.size, suggestion) { list.animateScrollToItem(maxOf(0, chat.size)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 880.dp).fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(chat) { _, turn -> Bubble(turn) }
                if (suggestion != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (suggestion.why != chat.lastOrNull()?.text) {
                                    Text(suggestion.why, style = MaterialTheme.typography.bodyLarge)
                                }
                                if (suggestion.isNext && nextTitle != null) {
                                    PrimaryButton(stringResource(R.string.start_next, nextTitle), onNext, Modifier.fillMaxWidth())
                                    SecondaryButton(stringResource(R.string.end_visit_create), onEndVisit, Modifier.fillMaxWidth())
                                } else {
                                    PrimaryButton(stringResource(R.string.end_visit_create), onEndVisit, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                    modifier =
                        Modifier.weight(1f).onPreviewKeyEvent { e ->
                            // A hardware Enter sends (Shift+Enter makes a new line), like the web reference.
                            if (e.key == Key.Enter && !e.isShiftPressed) {
                                if (e.type == KeyEventType.KeyUp) onSend()
                                true
                            } else {
                                false
                            }
                        },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                MicButton(lang, draft, onDraft, onStopSpeaking)
                val sendDesc = stringResource(R.string.send)
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.semantics { contentDescription = sendDesc }.padding(0.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, null) }
            }
            if (suggestion == null) {
                TextButton(onClick = onEndVisit, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.end_visit_now))
                }
            }
        }
    }
}

@Composable
private fun Bubble(turn: ChatTurn) {
    val coach = turn.role == Role.COACH
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (coach) Arrangement.Start else Arrangement.End) {
        Surface(
            color = if (coach) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, if (coach) 20.dp else 4.dp, if (coach) 4.dp else 20.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 620.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    stringResource(if (coach) R.string.coach else R.string.you),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(turn.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(name = "Chat EN", widthDp = 900, heightDp = 800, showBackground = true)
@Composable
private fun ChatPreviewEn() =
    MentalCoachTheme {
        ChatScreen(PreviewData.run.chat, PreviewData.visit.suggestion, "Mindful walk", "", Lang.EN, false, {}, {}, {}, {}, {})
    }

@Preview(name = "Chat DE", widthDp = 400, heightDp = 800, showBackground = true, locale = "de")
@Composable
private fun ChatPreviewDe() =
    MentalCoachTheme {
        ChatScreen(PreviewData.run.chat, null, null, "Ruhiger", Lang.DE, false, {}, {}, {}, {}, {})
    }
