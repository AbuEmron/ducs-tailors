package org.fisabilillah.app.ui.screens.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.FactChip
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ScreenState
import org.fisabilillah.core.domain.ModerationQueueUseCase
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.Timestamp

/**
 * The safety team's queue.
 *
 * Presented in exactly the order the use case produced — severity, then escalation, then
 * age. Re-sorting it in the UI would be a way for a moderator to work the easy cases first,
 * and the whole point of ordering by severity in the domain layer is that nobody gets to
 * make that choice.
 *
 * The notice about permanence is on this screen rather than only on the case screen,
 * because it is addressed to the moderator before they start, not after they have decided
 * to do something.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModeratorDashboardScreen(
    state: ScreenState<List<ModerationQueueUseCase.QueueItem>>,
    auditTrail: List<AuditLogEntry>,
    isSafetyAdmin: Boolean,
    onOpenCase: (ModerationCaseId) -> Unit,
    onAppeals: () -> Unit,
    onTrustReview: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moderation queue") },
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
        val refusal = state.refusal
        val queue = state.data.orEmpty()

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {

            item {
                Spacer(Modifier.height(spacing.xs))
                DisclaimerCard(
                    title = "Everything you do here is permanent",
                    text = "Every moderation action writes an audit entry recording who " +
                        "took it, when, and why. There is no update path and no delete " +
                        "path for either record — not for you, not for a senior moderator, " +
                        "and not for a platform administrator. A moderator who misuses " +
                        "their access leaves a trail they cannot clean up. That is " +
                        "deliberate, and it protects members from us.",
                )
            }

            item {
                // Appeals sit alongside the queue rather than inside it. They are the one
                // part of this screen where a member is arguing back, and burying them
                // under the open cases would mean the busiest moderator never sees them.
                SecondaryButton(
                    text = "Appeals waiting to be read",
                    onClick = onAppeals,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
                Spacer(Modifier.height(spacing.xs))
                SecondaryButton(
                    text = "Verification and qualification requests",
                    onClick = onTrustReview,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
                Spacer(Modifier.height(spacing.sm))
            }

            when {
                state.loading -> item { LoadingState(label = "Loading the queue") }

                refusal != null -> item {
                    RefusalNotice(message = refusal)
                }

                queue.isEmpty() -> item {
                    EmptyState(
                        title = "Nothing open",
                        body = "There are no open cases at the moment.",
                    )
                }

                else -> {
                    item {
                        SectionHeader(
                            title = "Open cases",
                            subtitle = "In the order the triage rules produced. Please work " +
                                "from the top.",
                        )
                    }

                    items(queue) { queueItem ->
                        QueueItemCard(item = queueItem, onOpenCase = onOpenCase)
                    }
                }
            }

            if (isSafetyAdmin) {
                item {
                    SectionHeader(
                        title = "Recent audit entries",
                        subtitle = "Read-only, and permanently so.",
                    )
                }

                if (auditTrail.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Nothing recorded yet",
                            body = "Audit entries appear here as actions are taken.",
                        )
                    }
                } else {
                    items(auditTrail) { entry -> AuditEntryCard(entry) }
                }
            }

            item { Spacer(Modifier.height(spacing.xl)) }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: ModerationQueueUseCase.QueueItem,
    onOpenCase: (ModerationCaseId) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    val case = item.case

    ContentCard(
        onClick = { onOpenCase(case.id) },
        contentDescription = "${case.category.displayName}. " +
            "Severity ${case.severity.displayName}. " +
            "Needs a human ${item.triage.displayName.lowercase()}. " +
            "${item.reports} reports. Open the case.",
    ) {
        Text(
            text = case.category.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(spacing.xs))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FactChip(label = "Severity: ${case.severity.displayName}")
            FactChip(label = item.triage.displayName)
        }

        Spacer(Modifier.height(spacing.xs))

        Text(
            text = case.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.xs))

        DetailRow(label = "Needs a human", value = item.triage.displayName)
        DetailRow(label = "Escalation", value = case.escalation.displayName)
        DetailRow(label = "Case state", value = case.state.displayName)
        DetailRow(label = "Reports in this case", value = item.reports.toString())
        DetailRow(label = "Opened", value = readableInstant(case.createdAt))

        val assignee = case.assignedTo
        DetailRow(
            label = "Assigned to",
            value = assignee?.value ?: "Nobody yet",
        )
    }
}

@Composable
internal fun AuditEntryCard(entry: AuditLogEntry) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = entry.action.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = entry.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        DetailRow(label = "When", value = readableInstant(entry.occurredAt))
        DetailRow(label = "Actor", value = entry.actorId?.value ?: "The platform")
        DetailRow(
            label = "Role at the time",
            value = entry.actorRoleAtTime?.displayName ?: "Not recorded",
        )
        DetailRow(label = "Subject", value = "${entry.subjectType} ${entry.subjectId}")

        if (entry.metadata.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            for ((key, value) in entry.metadata) {
                Text(
                    text = "$key: $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** ISO instant, trimmed to the minute. An audit trail wants a timestamp, not "an hour ago". */
internal fun readableInstant(timestamp: Timestamp): String =
    timestamp.toString().take(16).replace('T', ' ')
