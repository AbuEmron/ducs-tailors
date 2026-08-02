package org.fisabilillah.app.ui.screens.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.CautionButton
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * Export and deletion.
 *
 * The honest part of this screen is the list of what deletion does *not* remove. Evidence
 * attached to an open safety case survives, and it has to: a platform where deleting your
 * account erases the record of what you did to someone is a platform that rewards abusers
 * for leaving. Saying so here, before anyone presses the button, is the only defensible
 * way to hold that line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDataScreen(
    onExport: () -> Unit,
    onRequestDeletion: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var deletionConfirmed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your data") },
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
        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            SectionHeader(
                title = "Export everything held about you",
                subtitle = "A machine-readable copy, sent to your verified email address.",
            )

            ContentCard {
                Text(
                    text = "The export contains your profile, your safeguards, your " +
                        "commitments, your conversations, your reports and the audit " +
                        "entries recorded against your account. Your trusted contacts' " +
                        "details are included because they are yours to hold.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.sm))
                SecondaryButton(text = "Request an export", onClick = onExport)
            }

            PrivacyNote(
                text = "An export is itself recorded in the audit log, so that a stolen " +
                    "session cannot quietly take a copy of your life.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.lg))

            SectionHeader(title = "Delete your account")

            ContentCard {
                Text(
                    text = "What deletion removes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Bullet("Your profile, and with it your name, your area, your skills and " +
                    "your contribution statement.")
                Bullet("Your safeguards, your trusted contacts and their contact details.")
                Bullet("Your private service record and impact summary.")
                Bullet("Your listings and requests, and your place in anything you had " +
                    "committed to.")
                Bullet("Your messages are removed from view for everyone in your " +
                    "conversations.")
            }

            DisclaimerCard(
                title = "What deletion does not remove",
                text = "Evidence attached to an open safety case is kept. If someone has " +
                    "reported you, or you have reported someone, the messages and " +
                    "snapshots preserved for that case survive your deletion until the " +
                    "case is closed and the retention period in the privacy policy has " +
                    "passed. Audit log entries about moderation decisions are permanent " +
                    "and cannot be deleted by anyone, including administrators. Records " +
                    "we are required by law to keep — for example around donations — are " +
                    "also retained.\n\nThis is not a loophole. A platform where deleting " +
                    "your account erases what you did to another member is a platform " +
                    "that protects the wrong person.",
            )

            Spacer(Modifier.height(spacing.sm))

            SafeguardToggle(
                title = "I have read what deletion does and does not remove",
                description = "The button below stays unavailable until this is ticked. " +
                    "Deletion cannot be undone once the retention period has passed.",
                checked = deletionConfirmed,
                onCheckedChange = { deletionConfirmed = it },
            )

            CautionButton(
                text = "Request deletion of my account",
                onClick = onRequestDeletion,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = deletionConfirmed,
            )

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "Your account moves to a deletion-requested state immediately and " +
                    "you can no longer be contacted. If you signed in again during the " +
                    "grace period stated in the privacy policy, you would be asked whether " +
                    "you meant to cancel the request.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = spacing.xxs),
    )
}
