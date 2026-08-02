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
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.policy.ContentSignals

/**
 * The safety centre.
 *
 * One page that answers the questions someone has when something has gone wrong: what
 * happens if I report this, who reads it, what is scanned automatically, and where do I
 * change what people can do to me. Deliberately not a help centre with forty articles —
 * a person in distress reads one screen, if that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SafetyCentreScreen(
    onMyReports: () -> Unit,
    onMyRestrictions: () -> Unit,
    onGuidelines: () -> Unit,
    onPrivacyControls: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety centre") },
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

            ContentCard {
                Text(
                    text = "If you are in immediate danger",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Contact your local emergency services first. This platform is " +
                        "not an emergency service and cannot reach anyone on your behalf " +
                        "quickly enough to help in that situation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader(title = "What happens when you report something")

            ContentCard {
                Numbered(1, "Evidence is captured at the moment you report, not later. A " +
                    "message that is unsent afterwards, a listing that is taken down, a " +
                    "profile that is rewritten — the copy taken when you reported survives " +
                    "all of it.")
                Numbered(2, "A person reads it. Automated checks decide which report a " +
                    "human looks at first; they never decide the outcome, remove content, " +
                    "or restrict an account on their own.")
                Numbered(3, "The person you reported is not told who reported them.")
                Numbered(4, "Anything a moderator does is permanently recorded against " +
                    "their name and cannot be edited or deleted by anyone.")
            }

            SectionHeader(title = "Your reports")

            NavCard(
                title = "Reports you have made",
                description = "What you have reported, and what you will and will not be " +
                    "told about the outcome.",
                onClick = onMyReports,
            )

            // Reachable without being invited. The alternative -- offering the appeal route
            // only in the notification announcing a restriction -- leaves anyone who was
            // asleep, or who cleared it, with no way in at all.
            NavCard(
                title = "Restrictions on your account, and appeals",
                description = "Anything the safety team has limited, the reason they gave, " +
                    "and how to argue with it.",
                onClick = onMyRestrictions,
            )

            NavCard(
                title = "Community guidelines",
                description = "How members are expected to treat one another here, and " +
                    "what counts as a violation.",
                onClick = onGuidelines,
            )

            NavCard(
                title = "Privacy controls",
                description = "What other members can see about you, and how precisely.",
                onClick = onPrivacyControls,
            )

            SectionHeader(title = "Automated checks")

            DisclaimerCard(
                title = "What is checked, and what it can and cannot do",
                text = ContentSignals.DISCLOSURE,
            )

            Spacer(Modifier.height(spacing.md))

            PrivacyNote(
                text = "Blocking someone is always available from any conversation with " +
                    "them and does not require you to explain yourself, report anything, " +
                    "or wait for anyone to agree.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun NavCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard(onClick = onClick, contentDescription = "$title. $description") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Numbered(number: Int, text: String) {
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = "$number.  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = spacing.xxs),
    )
}
