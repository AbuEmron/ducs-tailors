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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * Getting back in.
 *
 * The first step — a reset link to the confirmed address — is live. The rest of the page
 * describes the steps that are not, because someone locked out of an account whose email
 * they have also lost needs to know whether waiting will help them, and a screen that only
 * offers the one route it has implemented leaves them guessing.
 *
 * The reply is the same whether or not the address has an account. See [SignUpMessage] for
 * the same reasoning applied to registration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountRecoveryScreen(
    onSendResetLink: suspend (email: String) -> String?,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

            SectionHeader(
                title = "Send a reset link",
                subtitle = "To the address you registered with.",
            )

            if (error != null) {
                RefusalNotice(message = error!!)
            }

            if (sent) {
                ContentCard {
                    Text(
                        text = "If that address has an account here, a reset link is on its " +
                            "way to it. The link works once and expires shortly. We do not " +
                            "say whether an account exists, because that would let anyone " +
                            "with a list of addresses find out who is a member.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            LabelledField(
                label = "Email address",
                value = email,
                onValueChange = { email = it; error = null; sent = false },
                keyboardType = KeyboardType.Email,
                enabled = !working,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            PrimaryButton(
                text = "Send the link",
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        error = onSendResetLink(email.trim())
                        sent = error == null
                        working = false
                    }
                },
                enabled = email.isNotBlank() && !working,
                loading = working,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionDivider()

            SectionHeader(
                title = "If that does not work",
                subtitle = "The rest of recovery is not built yet. Written down so it can be " +
                    "argued with before it is.",
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
                text = "If you no longer have the address on the account, there is currently " +
                    "no way for the safety team to verify that the account is yours, so there " +
                    "is nothing they can do. That is a real gap, not a policy: identity " +
                    "review is step three above and is not built.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))

            PrivacyNote(
                text = "The address you type here is used to send the link and for nothing " +
                    "else. No phone number or identity document is asked for.",
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
