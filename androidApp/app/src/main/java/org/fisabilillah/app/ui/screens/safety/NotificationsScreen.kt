package org.fisabilillah.app.ui.screens.safety

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.FactChip
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationGroup
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.Timestamp

/**
 * Notifications.
 *
 * Grouped by what they are about rather than shown as one undifferentiated stream, so that
 * a safety notice is never buried under project updates.
 *
 * Every notification here corresponds to something the person actually needs to know: a
 * commitment they made, a reply they are waiting for, a safety matter. There is no
 * notification for someone viewing your profile, no streak, no digest of what other people
 * have been doing, and nothing whose purpose is to bring you back into the application for
 * its own sake. Unread state is shown as a word, not only as a colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsScreen(
    notifications: List<Notification>,
    onMarkRead: (NotificationId) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (notifications.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                item {
                    EmptyState(
                        title = "Nothing to tell you",
                        body = "You will hear from us about commitments you have made, " +
                            "replies in your conversations, and anything that concerns " +
                            "your safety. Nothing else.",
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            for (group in NotificationGroup.entries) {
                val inGroup = notifications.filter { it.kind.group == group }
                if (inGroup.isEmpty()) continue

                item(key = "header-${group.name}") {
                    SectionHeader(
                        title = group.displayName,
                        subtitle = subtitleFor(group),
                    )
                }

                items(inGroup) { notification ->
                    NotificationCard(
                        notification = notification,
                        onMarkRead = onMarkRead,
                        onOpen = onOpen,
                    )
                }
            }

            item {
                Spacer(Modifier.height(spacing.md))
                PrivacyNote(
                    text = "This platform does not notify anyone that you looked at their " +
                        "profile, and it does not send reminders whose only purpose is to " +
                        "bring you back.",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
                Spacer(Modifier.height(spacing.xl))
            }
        }
    }
}

private fun subtitleFor(group: NotificationGroup): String = when (group) {
    NotificationGroup.CONVERSATIONS -> "Replies and reminders in threads you are part of."
    NotificationGroup.COMMITMENTS -> "Things you said you would turn up to."
    NotificationGroup.ACTIVITY -> "Outcomes of applications, enrolments and requests."
    NotificationGroup.INTRODUCTIONS -> "The formal introduction process."
    NotificationGroup.SAFETY -> "Reports, moderation outcomes and sign-ins."
}

@Composable
private fun NotificationCard(
    notification: Notification,
    onMarkRead: (NotificationId) -> Unit,
    onOpen: (String) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    val deepLink = notification.deepLink

    ContentCard(
        contentDescription = "${if (notification.isRead) "Read" else "Unread"}. " +
            "${notification.title}. ${notification.body}",
    ) {
        Row {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!notification.isRead) {
                Spacer(Modifier.width(spacing.xs))
                FactChip(label = "Unread")
            }
        }

        Spacer(Modifier.height(spacing.xxs))

        Text(
            text = notification.kind.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.xs))

        Text(
            text = notification.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.xs))

        Text(
            text = readableInstant(notification.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.sm))

        if (deepLink != null) {
            SecondaryButton(
                text = "Open",
                onClick = {
                    if (!notification.isRead) onMarkRead(notification.id)
                    onOpen(deepLink)
                },
            )
            Spacer(Modifier.height(spacing.xs))
        }

        if (!notification.isRead) {
            SecondaryButton(
                text = "Mark as read",
                onClick = { onMarkRead(notification.id) },
            )
        }
    }
}

/** ISO instant, trimmed to the minute. Deliberately plain rather than "2 hours ago". */
private fun readableInstant(timestamp: Timestamp): String =
    timestamp.toString().take(16).replace('T', ' ')
