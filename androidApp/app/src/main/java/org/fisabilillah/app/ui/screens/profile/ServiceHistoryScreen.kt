package org.fisabilillah.app.ui.screens.profile

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
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.PrivateImpactRecord
import org.fisabilillah.core.model.Timestamp

/**
 * Your own record of service.
 *
 * The whole design of this screen is an argument against a feature the platform will not
 * build. There is no streak, no total shown to anyone else, no comparison against other
 * members, no badge for volume and no monthly summary designed to be shared. A record of
 * what someone did for the sake of Allah is between them and their Lord, and turning it
 * into a score is the fastest way to corrupt the thing it was measuring.
 *
 * What remains is useful and quiet: what you said you would do, and whether you did it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceHistoryScreen(
    commitments: List<Commitment>,
    impact: PrivateImpactRecord?,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service history") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                Spacer(Modifier.height(spacing.xs))
                PrivacyNote(
                    text = "This page is visible only to you. Nothing on it appears on your " +
                        "profile, in search, or to any organiser you have volunteered with.",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }

            if (impact != null) {
                item {
                    SectionHeader(
                        title = "Your summary",
                        subtitle = impact.periodLabel,
                    )
                    ContentCard {
                        DetailRow(
                            label = "Commitments completed",
                            value = impact.commitmentsCompleted.toString(),
                        )
                        DetailRow(
                            label = "Hours given",
                            value = impact.hoursGiven.toString(),
                        )
                        DetailRow(
                            label = "People helped",
                            value = impact.peopleHelped.toString(),
                        )

                        if (impact.categories.isNotEmpty()) {
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = "Kinds of work",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(spacing.xxs))
                            for ((category, count) in impact.categories) {
                                Text(
                                    text = "${category.displayName} — $count",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            text = impact.sincerityNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "What you committed to",
                    subtitle = "In the order it is held, not ranked.",
                )
            }

            if (commitments.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing recorded yet",
                        body = "When you apply to an opportunity, enrol in a class, or " +
                            "offer to help with a request, it appears here.",
                    )
                }
            } else {
                items(commitments) { commitment ->
                    CommitmentCard(commitment)
                }
            }

            item { Spacer(Modifier.height(spacing.xl)) }
        }
    }
}

@Composable
private fun CommitmentCard(commitment: Commitment) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = commitment.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = commitment.status.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        DetailRow(label = "Starts", value = readableInstant(commitment.startsAt))
        DetailRow(label = "Ends", value = readableInstant(commitment.endsAt))

        val place = commitment.place
        if (place != null) {
            DetailRow(label = "Where", value = place.publicLabel)
        }

        val punctuality = commitment.punctuality
        if (punctuality != null) {
            DetailRow(label = "Attendance", value = punctuality.displayName)
        }
    }
}

/** ISO instant, trimmed to the minute. Deliberately plain rather than "3 days ago". */
private fun readableInstant(timestamp: Timestamp): String =
    timestamp.toString().take(16).replace('T', ' ')
