package org.fisabilillah.app.ui.screens.messages

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
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ComposeState
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.EngagementDuration
import org.fisabilillah.core.model.PurposeSubject

/**
 * Starting a conversation.
 *
 * This is a form, not a chat box, and that is the single most important design decision in
 * the product. A blank message field addressed to a stranger is an invitation to write
 * "salam, how are you" — which is how every general-purpose platform ends up hosting
 * private pursuit whatever its rules say. Asking for a purpose, a subject, an ask and a
 * duration costs a sincere person ninety seconds and costs an insincere one the whole
 * pretext.
 *
 * Nothing here is validated locally. The messages shown against each field are the ones the
 * contact gate produced, so what this screen says and what the system does cannot drift
 * apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeConversationScreen(
    state: ComposeState,
    subjects: List<Pair<PurposeSubject, String>>,
    onUpdatePurposeKind: (ContactPurposeKind) -> Unit,
    onUpdateSubject: (PurposeSubject?, String?) -> Unit,
    onUpdateReason: (String) -> Unit,
    onUpdateAction: (String) -> Unit,
    onUpdateDuration: (EngagementDuration) -> Unit,
    onToggleGuardian: (Boolean) -> Unit,
    onToggleModerator: (Boolean) -> Unit,
    onUpdateOpening: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    val recipientName = state.recipient?.displayName ?: "this member"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Get in touch") },
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
                    text = "Every conversation on this platform has a stated purpose.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "There is no way to open an unattached thread with someone here. " +
                        "That is deliberate: it is what keeps this from becoming a place " +
                        "where people are approached privately for reasons they were never " +
                        "told. What you write below is shown to $recipientName before they " +
                        "decide whether to reply, and it stays at the top of the " +
                        "conversation afterwards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.refusal != null) {
                RefusalNotice(message = state.refusal)
            }

            // ── Purpose ──────────────────────────────────────────────────────
            SectionHeader(
                title = "What is this about",
                subtitle = "Pick the one that fits. If none of them do, this is probably " +
                    "not a conversation for this platform.",
            )

            for (kind in ContactPurposeKind.selectable) {
                ChoiceRow(
                    title = kind.displayName,
                    description = kind.description,
                    selected = state.purposeKind == kind,
                    onSelect = { onUpdatePurposeKind(kind) },
                )
            }

            FieldError(state.errorFor("kind"))

            // ── Subject ──────────────────────────────────────────────────────
            if (state.purposeKind.requiresSubject) {
                SectionHeader(
                    title = "Which one",
                    subtitle = "This kind of request has to point at something real that " +
                        "already exists.",
                )

                if (subjects.isEmpty()) {
                    EmptyState(
                        title = "Nothing to attach yet",
                        body = "There is no opportunity, class or project available to " +
                            "attach to this request at the moment.",
                    )
                } else {
                    for ((subject, label) in subjects) {
                        ChoiceRow(
                            title = label,
                            description = "Attach this request to this listing.",
                            selected = state.subject == subject,
                            onSelect = { onUpdateSubject(subject, label) },
                        )
                    }
                }

                FieldError(state.errorFor("subject"))
            }

            // ── Reason and ask ───────────────────────────────────────────────
            SectionHeader(title = "Why you are getting in touch")

            LabelledField(
                label = "Your reason",
                value = state.reason,
                onValueChange = onUpdateReason,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                placeholder = "In your own words",
                helper = "At least ${ContactPurpose.MIN_REASON_LENGTH} characters. " +
                    "${state.reason.length} so far.",
                error = state.errorFor("reasonForContact"),
                singleLine = false,
                minLines = 4,
            )

            LabelledField(
                label = "What you are asking them to do",
                value = state.action,
                onValueChange = onUpdateAction,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                placeholder = "The specific thing you need",
                helper = "At least ${ContactPurpose.MIN_ACTION_LENGTH} characters. " +
                    "Being concrete here is a kindness — it lets someone say yes or no " +
                    "without a further exchange.",
                error = state.errorFor("requestedAction"),
                singleLine = false,
                minLines = 3,
            )

            // ── Duration ─────────────────────────────────────────────────────
            SectionHeader(
                title = "How long you expect this to take",
                subtitle = "An honest estimate. Nobody is held to it.",
            )

            for (duration in EngagementDuration.entries) {
                ChoiceRow(
                    title = duration.displayName,
                    description = when (duration) {
                        EngagementDuration.ONE_OFF ->
                            "One occasion, and then it is done."
                        EngagementDuration.SHORT_TERM ->
                            "A handful of sessions over a few weeks."
                        EngagementDuration.ONGOING_TERM ->
                            "A regular commitment across a term or season."
                        EngagementDuration.OPEN_ENDED ->
                            "No end point in mind. Say so plainly if that is the case."
                    },
                    selected = state.duration == duration,
                    onSelect = { onUpdateDuration(duration) },
                )
            }

            // ── Oversight ────────────────────────────────────────────────────
            SectionHeader(
                title = "Who else should be present",
                subtitle = "Optional. The recipient's own safeguards may add someone " +
                    "regardless of what you choose here.",
            )

            SafeguardToggle(
                title = "Ask for the recipient's wali or trusted contact to be present",
                description = "If the recipient has nominated someone, they are added to " +
                    "the conversation from the first message and can read everything in it.",
                checked = state.requestGuardian,
                onCheckedChange = onToggleGuardian,
            )

            SafeguardToggle(
                title = "Ask for a moderator to be present",
                description = "A member of the safety team joins and can read everything " +
                    "in the conversation. Some people prefer this when the subject is " +
                    "sensitive or the two of you have not met.",
                checked = state.requestModerator,
                onCheckedChange = onToggleModerator,
            )

            // ── Opening message ──────────────────────────────────────────────
            SectionHeader(
                title = "Your opening message",
                subtitle = "Sent along with everything above.",
            )

            LabelledField(
                label = "Opening message",
                value = state.openingMessage,
                onValueChange = onUpdateOpening,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                helper = "Keep it to the matter at hand.",
                error = state.errorFor("openingMessage"),
                singleLine = false,
                minLines = 4,
            )

            Spacer(Modifier.height(spacing.md))

            PrimaryButton(
                text = "Send this request",
                onClick = onSubmit,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                loading = state.submitting,
            )

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "$recipientName can decline without giving a reason, and can end " +
                    "the conversation at any point. Sending this again after no reply is " +
                    "treated as unwanted contact.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
    }
}

@Composable
private fun FieldError(message: String?) {
    if (message == null) return
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.xxs,
        ),
    )
}
