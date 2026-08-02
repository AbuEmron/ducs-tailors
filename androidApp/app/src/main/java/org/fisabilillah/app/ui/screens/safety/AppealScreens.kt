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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.domain.AppealQueueUseCase
import org.fisabilillah.core.domain.MyModerationRecordUseCase
import org.fisabilillah.core.model.AppealId
import org.fisabilillah.core.model.AppealState
import org.fisabilillah.core.model.ModerationCaseId

/**
 * What has been done to my account.
 *
 * The platform's own rules say every restriction is appealable. Until this screen existed
 * that was true only on paper: the restriction was applied by a moderator, announced in one
 * notification, and after that lived in a table the member cannot read. Somebody who was
 * asleep when it arrived had no way to find out what had happened, let alone argue with it.
 *
 * So the reason the moderator recorded is reproduced here in full. A person told only that
 * they are "restricted from starting conversations" cannot write an appeal; they can only
 * guess at what they are accused of, and an appeal written from a guess wastes the reader's
 * time as much as the writer's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyRestrictionsScreen(
    records: List<MyModerationRecordUseCase.RestrictionRecord>,
    loading: Boolean,
    refusal: String?,
    onAppeal: (ModerationCaseId) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Restrictions on your account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            if (refusal != null) {
                RefusalNotice(message = refusal)
            }

            if (!loading && records.isEmpty()) {
                EmptyState(
                    title = "Nothing is restricted",
                    body = "No limits are in place on your account. If one is ever applied " +
                        "you will be told, and it will appear here with the reason and a " +
                        "way to appeal.",
                )
                return@ScreenColumn
            }

            for (record in records) {
                ContentCard {
                    Text(
                        text = record.restriction.capability.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(spacing.xxs))
                    Text(
                        text = record.restriction.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    DetailRow(
                        label = "In place until",
                        value = record.restriction.expiresAt?.toString() ?: "No end date set",
                    )

                    val appeal = record.appeal
                    if (appeal != null) {
                        Spacer(Modifier.height(spacing.xs))
                        DetailRow(label = "Your appeal", value = appeal.state.displayName)
                        if (appeal.decisionNote != null) {
                            Spacer(Modifier.height(spacing.xxs))
                            Text(
                                text = appeal.decisionNote!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val caseId = record.case?.id
                if (record.canAppeal && caseId != null) {
                    SecondaryButton(
                        text = "Appeal this decision",
                        onClick = { onAppeal(caseId) },
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
                } else if (caseId == null) {
                    PrivacyNote(
                        text = "This limit was not applied through a case, so there is nothing " +
                            "to appeal against here. Contact the safety team through the " +
                            "safety centre.",
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
                }
                Spacer(Modifier.height(spacing.sm))
            }

            SectionDivider()

            PrivacyNote(
                text = "Appealing does not lift the restriction while it is being read, and " +
                    "it is never held against you. It is read by someone who was not part of " +
                    "the original decision.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/** Writing the appeal itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubmitAppealScreen(
    caseSummary: String?,
    submitting: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var statement by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appeal a decision") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            if (error != null) {
                RefusalNotice(message = error)
            }

            if (caseSummary != null) {
                SectionHeader(title = "What the decision was about")
                ContentCard {
                    Text(
                        text = caseSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(
                title = "Your account of it",
                subtitle = "In your own words. There is no form to fill in.",
            )

            LabelledField(
                label = "Why you are appealing",
                value = statement,
                onValueChange = { statement = it },
                helper = "What the decision got wrong, or anything that was not known at " +
                    "the time. Take the space you need.",
                singleLine = false,
                minLines = 8,
                enabled = !submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            PrimaryButton(
                text = "Send this appeal",
                onClick = { onSubmit(statement) },
                enabled = statement.isNotBlank() && !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.sm))

            DisclaimerCard(
                title = "Who reads this",
                text = "Somebody on the safety team who had no part in the original " +
                    "decision. That is a rule the software enforces, not a convention: the " +
                    "moderator who restricted your account cannot review your appeal " +
                    "against it.",
            )

            PrivacyNote(
                text = "Your appeal is kept with the case. It is not shown to whoever " +
                    "reported you, and it is not published anywhere.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/**
 * The safety team's side.
 *
 * An appeal a reviewer may not decide is still shown, with the reason, rather than hidden.
 * Hiding it would leave the queue looking empty to the one moderator who cannot act on it,
 * and they would have no way to know somebody else needs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppealQueueScreen(
    items: List<AppealQueueUseCase.QueueItem>,
    loading: Boolean,
    refusal: String?,
    submitting: Boolean,
    onDecide: (AppealId, AppealState, String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appeals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            if (refusal != null) {
                RefusalNotice(message = refusal)
            }

            if (!loading && items.isEmpty()) {
                EmptyState(
                    title = "No appeals waiting",
                    body = "Appeals appear here as they are lodged, oldest first.",
                )
                return@ScreenColumn
            }

            for (item in items) {
                AppealCard(item = item, submitting = submitting, onDecide = onDecide)
            }
            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun AppealCard(
    item: AppealQueueUseCase.QueueItem,
    submitting: Boolean,
    onDecide: (AppealId, AppealState, String) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var note by remember { mutableStateOf("") }

    ContentCard {
        Text(
            text = item.case?.summary ?: "Case not found",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.xs))
        DetailRow(label = "Appeal state", value = item.appeal.state.displayName)
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = item.appeal.statement,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!item.reviewableByMe) {
        RefusalNotice(
            message = "You took the original decision on this case, so somebody else on the " +
                "team has to read this appeal.",
        )
        return
    }

    LabelledField(
        label = "Your reasons",
        value = note,
        onValueChange = { note = it },
        helper = "Shown to the member in full. Write it as though they are reading it, " +
            "because they are.",
        singleLine = false,
        minLines = 4,
        enabled = !submitting,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )

    PrimaryButton(
        text = "Uphold the appeal and lift the restriction",
        onClick = { onDecide(item.appeal.id, AppealState.UPHELD, note) },
        enabled = note.isNotBlank() && !submitting,
        loading = submitting,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )
    Spacer(Modifier.height(spacing.xs))
    SecondaryButton(
        text = "Partly uphold",
        onClick = { onDecide(item.appeal.id, AppealState.PARTIALLY_UPHELD, note) },
        enabled = note.isNotBlank() && !submitting,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )
    Spacer(Modifier.height(spacing.xs))
    SecondaryButton(
        text = "Reject the appeal",
        onClick = { onDecide(item.appeal.id, AppealState.REJECTED, note) },
        enabled = note.isNotBlank() && !submitting,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )
    Spacer(Modifier.height(spacing.md))
}
