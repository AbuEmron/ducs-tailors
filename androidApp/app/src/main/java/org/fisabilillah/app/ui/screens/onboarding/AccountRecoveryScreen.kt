package org.fisabilillah.app.ui.screens.onboarding

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * Account recovery, which this build does not have.
 *
 * The screen exists rather than the route being removed, because someone who cannot get in
 * will look for it, and a dead end that explains itself is better than a form that collects
 * an email address and quietly does nothing. What it does instead is describe the process
 * that will exist, so the reader can judge whether it will actually help them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountRecoveryScreen(onBack: () -> Unit) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Getting back in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            RefusalNotice(
                message = "Account recovery is not available in this build. There is no " +
                    "authentication yet, so there is no credential to reset and no account " +
                    "to restore. Nothing you enter here would reach anyone.",
                actionLabel = "Back to sign in",
                onAction = onBack,
            )

            SectionHeader(
                title = "How recovery will work",
                subtitle = "Written down now so it can be argued with before it is built.",
            )

            Step(
                number = 1,
                title = "A link to your verified email address",
                body = "A single-use link, valid for a short period, sent only to the " +
                    "address already confirmed on the account. If you no longer have access " +
                    "to that address, this route will not work and step three applies.",
            )
            Step(
                number = 2,
                title = "A second factor where you set one up",
                body = "If you added a second factor, it is asked for after the link and not " +
                    "instead of it. Losing a phone should not, on its own, hand your account " +
                    "to whoever finds it.",
            )
            Step(
                number = 3,
                title = "A reviewed request when neither is possible",
                body = "Handled by a person, deliberately slowly, and recorded in the audit " +
                    "log with the reviewer's name attached. Impersonating someone in order " +
                    "to take over their account is one of the more damaging things that can " +
                    "happen on a platform like this, so a delay is the correct trade.",
            )

            SectionDivider()

            DisclaimerCard(
                title = "What support will never do",
                text = "Support staff cannot read your messages in order to confirm who you " +
                    "are, cannot tell you who else holds an account, and cannot move a " +
                    "guardian contact or a trusted contact to a new address on your word " +
                    "alone. If anyone claiming to be from this platform asks you for a " +
                    "password, a recovery link or a verification code, they are not from " +
                    "this platform.",
            )

            SectionHeader(title = "If you are locked out for a safety reason")
            ContentCard {
                Text(
                    text = "An account restricted after a moderation decision is not a " +
                        "recovery problem, and this route will not lift it. Restrictions " +
                        "come with a stated reason and a right of appeal, and the appeal is " +
                        "decided by someone who was not part of the original decision.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.md))

            Text(
                text = "In the meantime",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = spacing.screenHorizontal)
                    .semantics { heading() },
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = "This build signs in by choosing one of the sample accounts, and any " +
                    "of them can be chosen at any time. Nothing is lost, because nothing is " +
                    "kept once the app is closed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))

            PrivacyNote(
                text = "No email address, phone number or identity document is collected on " +
                    "this screen.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))

            SecondaryButton(
                text = "Back to sign in",
                onClick = onBack,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = "$number. $title",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
