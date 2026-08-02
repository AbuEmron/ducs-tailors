package org.fisabilillah.app.ui.screens.safety

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
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * Reports you have made.
 *
 * The honest content of this screen is what it does *not* show. A person who reports
 * someone is told when their report has been reviewed, and nothing about what happened to
 * the other member — not the action taken, not the duration, not whether it was upheld in
 * the terms the reporter would recognise.
 *
 * That is not the platform being evasive. Publishing an outcome to a reporter turns a
 * safety process into a scoreboard, gives someone a way to confirm whether a retaliatory
 * report landed, and exposes a member's private disciplinary record to whoever complained
 * about them loudest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyReportsScreen(onBack: () -> Unit) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your reports") },
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

            SectionHeader(title = "Where your reports appear")

            ContentCard {
                Text(
                    text = "When a report you made has been reviewed, you are told under " +
                        "safety notices in your notifications. There is no separate queue " +
                        "here that updates as a case moves along, because watching a case " +
                        "progress is not something that helps anybody.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            SectionHeader(title = "What you are told")

            ContentCard {
                Bullet("That your report was received.")
                Bullet("That a person has reviewed it.")
                Bullet("If it concerned your own conversation, whether that conversation " +
                    "has been frozen while the case is open.")
            }

            SectionHeader(title = "What you are not told")

            ContentCard {
                Bullet("What action was taken against another member, if any.")
                Bullet("Whether they were warned, restricted, suspended, or nothing at all.")
                Bullet("Whether anyone else has reported the same person.")
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Another member's disciplinary record is theirs, in the same way " +
                        "yours is yours. Handing it to whoever reported them would give " +
                        "someone a way to test whether a retaliatory report worked, and " +
                        "would turn a safety process into a thing to be scored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader(title = "If nothing seems to have changed")

            ContentCard {
                Text(
                    text = "If the behaviour you reported is continuing, report it again " +
                        "with what has happened since. A second report about ongoing " +
                        "conduct is not a duplicate and is not held against you. You can " +
                        "also block the person at any point, from any conversation with " +
                        "them, without reporting anything at all.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(spacing.md))

            PrivacyNote(
                text = "Every report you have made is included in your data export, along " +
                    "with the evidence captured at the time.",
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
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = spacing.xxs),
    )
}
