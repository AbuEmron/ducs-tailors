package org.fisabilillah.app.ui.screens.moderation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.ModerationAction
import org.fisabilillah.core.model.ModerationActionType
import org.fisabilillah.core.model.ModerationCase

/** The shortest rationale that is worth writing down. */
private const val MIN_RATIONALE = 20

/**
 * A single moderation case.
 *
 * Two constraints shape this screen.
 *
 * A written rationale is required before any action can be taken. Not encouraged, not
 * pre-filled, not optional for the small ones — required. An action with no stated reason
 * is unreviewable on appeal and indistinguishable from an abuse of access, and the model
 * itself refuses to construct one.
 *
 * Actions that need a safety administrator are shown to everyone but are only actionable by
 * one, with the reason stated. Hiding them would leave a standard moderator unable to tell
 * whether an option exists at all; showing them without explanation would look arbitrary.
 * A permanent ban requires two people by design, so that nobody loses their account for
 * good on one person's judgement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModerationCaseScreen(
    case: ModerationCase?,
    actions: List<ModerationAction>,
    canTakeSeniorActions: Boolean,
    refusal: String?,
    onAct: (ModerationActionType, String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var selectedType by remember { mutableStateOf<ModerationActionType?>(null) }
    var rationale by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Case") },
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
        if (case == null) {
            Column(Modifier.padding(padding)) { LoadingState(label = "Loading this case") }
            return@Scaffold
        }

        val chosen = selectedType
        val rationaleLongEnough = rationale.trim().length >= MIN_RATIONALE
        val chosenIsPermitted = chosen != null &&
            (!chosen.requiresSeniorApproval || canTakeSeniorActions)

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            if (refusal != null) {
                RefusalNotice(message = refusal)
            }

            // ── The case ─────────────────────────────────────────────────────
            ContentCard {
                Text(
                    text = case.category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = case.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.sm))
                DetailRow(label = "Severity", value = case.severity.displayName)
                DetailRow(label = "Escalation", value = case.escalation.displayName)
                DetailRow(label = "State", value = case.state.displayName)
                DetailRow(label = "Reports in this case", value = case.reportIds.size.toString())
                DetailRow(label = "Opened", value = readableInstant(case.createdAt))
                DetailRow(
                    label = "Subject",
                    value = case.subjectUserId?.value ?: "No individual subject",
                )
                DetailRow(
                    label = "Assigned to",
                    value = case.assignedTo?.value ?: "Nobody yet",
                )
                val outcome = case.outcome
                if (outcome != null) {
                    DetailRow(label = "Outcome", value = outcome.displayName)
                }
            }

            PrivacyNote(
                text = "Automated signals attached to a case are advisory. They exist to " +
                    "bring the right case to a person sooner. They never decide an " +
                    "outcome, and a signal on its own is not evidence of anything.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionDivider()

            // ── What has already been done ───────────────────────────────────
            SectionHeader(
                title = "Actions already taken",
                subtitle = "Append-only. Nothing here can be edited or removed.",
            )

            if (actions.isEmpty()) {
                EmptyState(
                    title = "Nothing yet",
                    body = "No action has been taken on this case.",
                )
            } else {
                for (action in actions) {
                    ActionCard(action)
                }
            }

            SectionDivider()

            // ── Taking an action ─────────────────────────────────────────────
            SectionHeader(
                title = "Take an action",
                subtitle = "Choose one, then write down why.",
            )

            for (type in ModerationActionType.entries) {
                val needsSenior = type.requiresSeniorApproval
                val permitted = !needsSenior || canTakeSeniorActions

                if (permitted) {
                    ChoiceRow(
                        title = type.displayName,
                        description = buildString {
                            append(
                                if (type.isReversible) {
                                    "This can be reversed later."
                                } else {
                                    "This cannot be reversed."
                                },
                            )
                            if (needsSenior) {
                                append(
                                    " Requires a safety administrator, which you are.",
                                )
                            }
                        },
                        selected = chosen == type,
                        onSelect = { selectedType = type },
                    )
                } else {
                    UnavailableActionRow(
                        title = type.displayName,
                        reason = "This requires a safety administrator. A permanent ban, a " +
                            "suspension, a revocation of verification and an external " +
                            "referral each need a second person, so that nobody loses " +
                            "their account or their standing on one person's judgement. " +
                            "Escalate the case instead.",
                    )
                }
            }

            SectionHeader(
                title = "Why you are taking it",
                subtitle = "Required. This is what an appeal is reviewed against.",
            )

            LabelledField(
                label = "Rationale",
                value = rationale,
                onValueChange = { rationale = it },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                helper = "At least $MIN_RATIONALE characters. State what you saw and which " +
                    "guideline it breaches, not your conclusion about the person.",
                error = if (rationale.isEmpty() || rationaleLongEnough) {
                    null
                } else {
                    "A rationale of at least $MIN_RATIONALE characters is required."
                },
                singleLine = false,
                minLines = 4,
            )

            Spacer(Modifier.height(spacing.sm))

            PrimaryButton(
                text = "Record this action",
                onClick = {
                    val type = chosen
                    if (type != null) onAct(type, rationale.trim())
                },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = chosenIsPermitted && rationaleLongEnough,
            )

            if (chosen == null) {
                Text(
                    text = "Choose an action above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.xxs,
                    ),
                )
            } else if (!rationaleLongEnough) {
                Text(
                    text = "No rationale, no action. This is not a formality — an action " +
                        "with no stated reason cannot be reviewed on appeal and cannot be " +
                        "told apart from an abuse of access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.xxs,
                    ),
                )
            }

            Spacer(Modifier.height(spacing.md))

            DisclaimerCard(
                title = "This is permanent",
                text = "Recording an action writes an audit entry naming you, the time, " +
                    "the case, and the rationale you wrote. There is no update path and no " +
                    "delete path for that entry anywhere in the system — not for you, not " +
                    "for a senior moderator, and not for a platform administrator. It is " +
                    "the only structural protection that actually works against insider " +
                    "misuse, and it protects you as much as it protects the member.",
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun ActionCard(action: ModerationAction) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = action.type.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xs))
        DetailRow(label = "Rationale", value = action.rationale)
        DetailRow(label = "Moderator", value = action.moderatorId.value)
        DetailRow(label = "When", value = readableInstant(action.performedAt))
        DetailRow(
            label = "Member told",
            value = if (action.notifiedUser) "Yes" else "No",
        )

        val expiry = action.expiresAt
        if (expiry != null) {
            DetailRow(label = "Expires", value = readableInstant(expiry))
        }

        val target = action.targetUserId
        if (target != null) {
            DetailRow(label = "Target", value = target.value)
        }
    }
}

/** An action this moderator cannot take, shown with the reason rather than hidden. */
@Composable
private fun UnavailableActionRow(title: String, reason: String) {
    val spacing = FiSabilillahTheme.spacing

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xxs),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(Modifier.padding(spacing.md)) {
            Text(
                text = "$title — not available to you",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(text = reason, style = MaterialTheme.typography.bodySmall)
        }
    }
}
