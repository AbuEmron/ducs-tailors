package org.fisabilillah.app.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fisabilillah.app.ui.components.CautionButton
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.OversightChip
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SafeguardBanner
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ConversationState
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.MessageKind
import org.fisabilillah.core.model.UserId

/**
 * A single conversation.
 *
 * Three things on this screen are not negotiable, and they are the reason it does not look
 * like an ordinary chat.
 *
 * The banner is pinned rather than scrolled: the purpose and the terms in force are true
 * for the whole thread, so they stay on screen for the whole thread. The oversight row is
 * always rendered when anyone is present for oversight, because a person discovering
 * halfway through that their words were being read by a guardian would be a betrayal no
 * amount of small print repairs. And adding a wali or a moderator is a labelled button in
 * the thread itself — the moment someone wants a witness is the moment they are least able
 * to go hunting through a settings screen for one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationScreen(
    state: ConversationState,
    currentUserId: UserId,
    onSend: (String) -> Unit,
    onUnsend: (MessageId) -> Unit,
    onAddGuardian: () -> Unit,
    onAddModerator: () -> Unit,
    onEndConversation: () -> Unit,
    onBlock: (UserId) -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
    transientError: String?,
) {
    val spacing = FiSabilillahTheme.spacing
    var safetyOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    val conversation = state.conversation
    val otherParticipants = conversation
        ?.participantIds
        ?.filter { it != currentUserId }
        .orEmpty()
    val title = when {
        conversation == null -> "Conversation"
        otherParticipants.isEmpty() -> conversation.subjectTitle
        else -> otherParticipants.joinToString(", ") {
            state.participantNames[it] ?: "Member"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { safetyOpen = !safetyOpen }) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = if (safetyOpen) {
                                "Hide safety actions"
                            } else {
                                "Show safety actions"
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                draft = draft,
                onDraftChange = { if (it.length <= Message.MAX_LENGTH) draft = it },
                canSend = state.canSend,
                disabledReason = disabledReason(conversation),
                onSend = {
                    val body = draft.trim()
                    if (body.isNotEmpty()) {
                        onSend(body)
                        draft = ""
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) {
                LoadingState(label = "Loading this conversation")
                return@Column
            }
            if (conversation == null) {
                RefusalNotice(
                    message = state.refusal
                        ?: "This conversation is no longer available to you.",
                )
                return@Column
            }

            SafeguardBanner(
                purposeLabel = conversation.purpose.kind.displayName,
                subjectTitle = conversation.subjectTitle,
                requirements = conversation.appliedRequirements
                    .map { "${it.displayName}: ${it.explanation}" },
            )

            OversightRow(
                conversation = conversation,
                names = state.participantNames,
            )

            QuickOversightActions(
                onAddGuardian = onAddGuardian,
                onAddModerator = onAddModerator,
            )

            if (safetyOpen) {
                SafetyPanel(
                    otherParticipants = otherParticipants,
                    names = state.participantNames,
                    onReport = onReport,
                    onBlock = onBlock,
                    onEndConversation = onEndConversation,
                )
            }

            if (transientError != null) {
                RefusalNotice(message = transientError)
            }
            if (state.refusal != null) {
                RefusalNotice(message = state.refusal)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                item { Spacer(Modifier.height(spacing.sm)) }

                items(state.messages) { message ->
                    MessageRow(
                        message = message,
                        isMine = message.senderId == currentUserId,
                        senderName = message.senderId
                            ?.let { state.participantNames[it] }
                            ?: "Member",
                        onUnsend = onUnsend,
                    )
                }

                item { Spacer(Modifier.height(spacing.md)) }
            }
        }
    }
}

private fun disabledReason(conversation: Conversation?): String = when {
    conversation == null -> "This conversation is not available."
    conversation.state.acceptsNewMessages ->
        "You can write a message."
    else -> "This conversation is ${conversation.state.displayName.lowercase()}. " +
        "No one can add to it, including the people already in it. Everything already " +
        "written is kept, so it can still be reported."
}

// ── Oversight ────────────────────────────────────────────────────────────────

@Composable
private fun OversightRow(
    conversation: Conversation,
    names: Map<UserId, String>,
) {
    val spacing = FiSabilillahTheme.spacing
    val oversight = conversation.members.filter { it.isActive && it.role.isOversight }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.xs,
            ),
        ) {
            if (oversight.isEmpty()) {
                Text(
                    text = "No one else is reading this conversation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Also reading this conversation",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xxs))
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    for (member in oversight) {
                        OversightChip(
                            role = "${names[member.userId] ?: "Member"} — " +
                                member.role.displayName,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickOversightActions(
    onAddGuardian: () -> Unit,
    onAddModerator: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            SecondaryButton(
                text = "Add my wali",
                onClick = onAddGuardian,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Add a moderator",
                onClick = onAddModerator,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = "Either of these takes effect straight away, and everyone in the " +
                "conversation is told who joined. You do not need a reason and no one is " +
                "asked to approve it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SafetyPanel(
    otherParticipants: List<UserId>,
    names: Map<UserId, String>,
    onReport: () -> Unit,
    onBlock: (UserId) -> Unit,
    onEndConversation: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    SectionHeader(
        title = "Safety actions",
        subtitle = "Nothing here asks you to explain yourself first.",
    )
    Column(modifier = Modifier.padding(horizontal = spacing.screenHorizontal)) {
        CautionButton(
            text = "Report this conversation",
            onClick = onReport,
        )
        Spacer(Modifier.height(spacing.xs))

        for (participant in otherParticipants) {
            CautionButton(
                text = "Block ${names[participant] ?: "this member"}",
                onClick = { onBlock(participant) },
            )
            Spacer(Modifier.height(spacing.xs))
        }

        CautionButton(
            text = "End this conversation",
            onClick = onEndConversation,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = "Blocking stops this person reaching you anywhere on the platform. " +
                "Ending the conversation stops new messages from everyone in it. Neither " +
                "deletes what has already been written — that record is what a report is " +
                "built from.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
    }
}

// ── Messages ─────────────────────────────────────────────────────────────────

@Composable
private fun MessageRow(
    message: Message,
    isMine: Boolean,
    senderName: String,
    onUnsend: (MessageId) -> Unit,
) {
    val isFromPlatform = message.kind == MessageKind.SYSTEM ||
        message.kind == MessageKind.PURPOSE_REMINDER ||
        message.kind == MessageKind.PARTICIPANT_CHANGE

    if (isFromPlatform) {
        PlatformNote(message)
    } else {
        PersonMessage(
            message = message,
            isMine = isMine,
            senderName = senderName,
            onUnsend = onUnsend,
        )
    }
}

/**
 * A message the platform wrote, not a person.
 *
 * Centred, quiet, and without an avatar or a name, so that it cannot be mistaken for
 * something the other party said. A purpose reminder rendered as a chat bubble would read
 * as one person nagging the other.
 */
@Composable
private fun PlatformNote(message: Message) {
    val spacing = FiSabilillahTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message.displayBody,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PersonMessage(
    message: Message,
    isMine: Boolean,
    senderName: String,
    onUnsend: (MessageId) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xxs),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (isMine) "You" else senderName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxs))

        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isMine) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Text(
                text = message.displayBody,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (message.isUnsent) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.padding(spacing.sm),
            )
        }

        if (isMine && !message.isUnsent) {
            TextButton(onClick = { onUnsend(message.id) }) {
                Text(
                    text = "Unsend",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = "You can unsend for ${Message.UNSEND_WINDOW_MINUTES} minutes. " +
                    "The other person stops seeing it; a copy is kept for the safety team.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Composer ─────────────────────────────────────────────────────────────────

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    canSend: Boolean,
    disabledReason: String,
    onSend: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.xs,
            ),
        ) {
            if (!canSend) {
                Text(
                    text = disabledReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xs))
            }

            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    enabled = canSend,
                    label = { Text("Message") },
                    minLines = 1,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.width(spacing.xs))
                IconButton(
                    onClick = onSend,
                    enabled = canSend && draft.isNotBlank(),
                    modifier = Modifier.heightIn(min = spacing.minimumTouchTarget)
                        .width(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send this message",
                    )
                }
            }
        }
    }
}
