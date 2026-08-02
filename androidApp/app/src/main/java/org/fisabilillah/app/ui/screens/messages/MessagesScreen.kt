package org.fisabilillah.app.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.FactChip
import org.fisabilillah.app.ui.components.InitialsAvatar
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.OversightChip
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ConversationListState
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.UserId

/**
 * The list of conversations.
 *
 * Every row states what the conversation is for and who else is in it, because the two
 * questions a person asks when they open this screen are "what was this about" and "who
 * can read it". Neither is left to memory, and neither is one tap away.
 *
 * There is no unread count badge on the tab and no ordering by anything other than the
 * repository's own order. A messages list that competes for attention is a messages list
 * that has started to want something from the person reading it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesScreen(
    state: ConversationListState,
    currentUserId: UserId,
    nameFor: (UserId) -> String,
    onOpen: (ConversationId) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = { TopAppBar(title = { Text("Messages") }) },
    ) { padding ->
        when {
            state.loading -> Column(Modifier.padding(padding).fillMaxSize()) {
                LoadingState(label = "Loading your conversations")
            }

            state.conversations.isEmpty() -> Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    title = "No conversations yet",
                    body = "Conversations here begin from a specific piece of work: an " +
                        "opportunity, a class, a project, or a request for help. When you " +
                        "get in touch with someone, you say what it is about first.",
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item {
                    Spacer(Modifier.height(spacing.xs))
                    PrivacyNote(
                        text = "Every conversation carries a stated purpose, and anyone " +
                            "present for oversight is named on the conversation itself.",
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
                    Spacer(Modifier.height(spacing.xs))
                }

                items(state.conversations) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        currentUserId = currentUserId,
                        nameFor = nameFor,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    currentUserId: UserId,
    nameFor: (UserId) -> String,
    onOpen: (ConversationId) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    val otherParticipants = conversation.participantIds.filter { it != currentUserId }
    val heading = if (otherParticipants.isEmpty()) {
        conversation.subjectTitle
    } else {
        otherParticipants.joinToString(", ") { nameFor(it) }
    }

    val oversight = conversation.members
        .filter { it.isActive && it.role.isOversight }
        .map { it.role.displayName }

    ContentCard(
        onClick = { onOpen(conversation.id) },
        contentDescription = "Conversation with $heading about " +
            "${conversation.purpose.kind.displayName}. ${conversation.subjectTitle}. " +
            if (oversight.isEmpty()) {
                "No one else is present."
            } else {
                "Also present: ${oversight.joinToString(", ")}."
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name = heading)
            Spacer(Modifier.width(spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = conversation.purpose.kind.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(spacing.xs))

        Text(
            text = conversation.subjectTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.xs))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FactChip(label = conversation.state.displayName)
        }

        if (oversight.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                for (role in oversight.distinct()) {
                    OversightChip(role = "$role is in this conversation")
                }
            }
        }
    }
}
