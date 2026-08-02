package org.fisabilillah.app.ui.screens.safety

import androidx.compose.foundation.layout.Column
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
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.RedactedTrustedContact
import org.fisabilillah.core.policy.IntroductionPolicy

/**
 * A single formal introduction.
 *
 * What each person sees is different, and the differences are the safety design.
 *
 * The sender sees one sentence: the policy's own sender-facing outcome. No reason, no name
 * of whoever decided, no position in a queue, no count of anyone else. A rejection that
 * carries detail invites argument and a rejection that is visible invites humiliation, and
 * withholding both is worth more than any amount of transparency here would be.
 *
 * The recipient sees the form and can approve, decline, or close the door permanently. The
 * guardian, and only the guardian, sees the affordance that opens a conversation — and it
 * says plainly that they stay in it.
 *
 * Nowhere on this screen, in any of the three views, is a guardian's email address or
 * telephone number rendered. Only [RedactedTrustedContact] reaches this layer, and it does
 * not carry them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IntroductionDetailScreen(
    request: FormalIntroductionRequest?,
    guardian: RedactedTrustedContact?,
    viewerIsRecipient: Boolean,
    viewerIsGuardian: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onClosePermanently: () -> Unit,
    onOpenGuardianConversation: (String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var openingMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Formal introduction") },
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
        if (request == null) {
            Column(Modifier.padding(padding)) {
                LoadingState(label = "Loading this introduction")
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            // ── The sender's view ────────────────────────────────────────────
            if (!viewerIsRecipient && !viewerIsGuardian) {
                ContentCard {
                    Text(
                        text = IntroductionPolicy.senderFacingOutcome(request.status),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                PrivacyNote(
                    text = "You will not be told whether this was read, who considered it, " +
                        "why it concluded, or whether anyone else has submitted one. None " +
                        "of that is withheld to be unkind — it is withheld because knowing " +
                        "it would put pressure on a family who owe you nothing.",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )

                DisclaimerCard(
                    title = "The platform makes no religious ruling",
                    text = IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE,
                )

                Spacer(Modifier.height(spacing.xl))
                return@ScreenColumn
            }

            // ── The recipient's and guardian's view ──────────────────────────
            ContentCard {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = request.status.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "The sender is never told anything beyond a single neutral " +
                        "sentence. You are under no obligation to reply, and no reply is " +
                        "itself a complete answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (guardian != null) {
                SectionHeader(
                    title = "The guardian on the recipient's side",
                    subtitle = "Shown without contact details, to everyone, always.",
                )
                ContentCard {
                    DetailRow(label = "Name", value = guardian.name)
                    DetailRow(label = "Relationship", value = guardian.relationship.displayName)
                    DetailRow(label = "Role", value = guardian.role.displayName)
                    DetailRow(
                        label = "How the platform approaches them",
                        value = guardian.preferredContactMethod.displayName,
                    )
                    DetailRow(
                        label = "Confirmation",
                        value = guardian.verificationState.displayName,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    PrivacyNote(
                        text = "Their email address and telephone number are not shown " +
                            "here and are not available to anyone in this process. The " +
                            "platform contacts them; it does not hand out their details.",
                    )
                }
            }

            SectionDivider()

            SectionHeader(title = "What the sender wrote")

            ContentCard {
                Text(
                    text = "Their intention",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = request.form.statedIntention,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            FormSection("About themselves", request.form.aboutSelf)
            FormSection("Their family context", request.form.familyContext)
            FormSection("Their practice and priorities", request.form.practiceAndPriorities)
            FormSection(
                "Their living situation and plans",
                request.form.livingSituationAndPlans,
            )

            ContentCard {
                Text(
                    text = "Who acts for them",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = "${request.form.guardianOrRepresentativeName} — " +
                        request.form.guardianOrRepresentativeRelationship.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (request.form.questionsForTheFamily.isNotEmpty()) {
                SectionHeader(title = "Questions they asked")
                ContentCard {
                    for (question in request.form.questionsForTheFamily) {
                        Text(
                            text = "•  $question",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = spacing.xxs),
                        )
                    }
                }
            }

            ContentCard {
                Text(
                    text = "They accepted the conduct rules when they submitted this, and " +
                        "the time they did so is recorded. Their verification level at " +
                        "that moment was " +
                        "${request.senderVerificationAtSubmission.displayName.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = request.senderVerificationAtSubmission.whatItDoesNotMean,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionDivider()

            // ── The recipient's decision ─────────────────────────────────────
            if (viewerIsRecipient && request.isOpen) {
                SectionHeader(
                    title = "Your decision",
                    subtitle = "You do not have to give a reason for any of these, and no " +
                        "reason you give is passed on.",
                )

                PrimaryButton(
                    text = "Allow this through to my guardian",
                    onClick = onApprove,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )

                Spacer(Modifier.height(spacing.xs))

                SecondaryButton(
                    text = "Decline",
                    onClick = onDecline,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )

                Spacer(Modifier.height(spacing.xs))

                CautionButton(
                    text = "Close this permanently",
                    onClick = onClosePermanently,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )

                Spacer(Modifier.height(spacing.xs))

                Text(
                    text = "Declining ends this request. Closing permanently also stops " +
                        "this person from ever submitting another one to you, and they are " +
                        "told only that the process has concluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }

            // ── The guardian's decision ──────────────────────────────────────
            if (viewerIsGuardian && request.isOpen) {
                SectionHeader(
                    title = "Opening a conversation",
                    subtitle = "Only you can do this, and you remain in it.",
                )

                ContentCard {
                    Text(
                        text = "You stay in the conversation throughout",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "There is no arrangement under which the two of them end up " +
                            "speaking alone. You are a member of this conversation from its " +
                            "first message, you can read everything in it, and you can end " +
                            "it at any point. This platform does not provide a route to a " +
                            "private thread out of this process, and it will not.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LabelledField(
                    label = "Your opening message",
                    value = openingMessage,
                    onValueChange = { openingMessage = it },
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    helper = "What you would like to say or ask to begin with.",
                    singleLine = false,
                    minLines = 4,
                )

                PrimaryButton(
                    text = "Open a conversation with the sender",
                    onClick = { onOpenGuardianConversation(openingMessage.trim()) },
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    enabled = openingMessage.isNotBlank(),
                )

                Spacer(Modifier.height(spacing.xs))

                SecondaryButton(
                    text = "Decline on the family's behalf",
                    onClick = onDecline,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }

            DisclaimerCard(
                title = "The platform makes no religious ruling",
                text = IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE,
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun FormSection(title: String, body: String) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
