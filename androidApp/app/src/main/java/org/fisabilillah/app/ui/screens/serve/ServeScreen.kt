package org.fisabilillah.app.ui.screens.serve

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ChipRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ServeState
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.ListingStatus
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.VolunteerOpportunity

/**
 * Opportunities to give time.
 *
 * The ordering is whatever the domain layer returned. Nothing here sorts by popularity,
 * counts views, or tells anyone how many other people are looking — the only numbers on
 * this screen are how many volunteers are still needed and when the work happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServeScreen(
    state: ServeState,
    onSelect: (ListingId) -> Unit,
    onToggleHideBackgroundCheck: (Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = { TopAppBar(title = { Text("Serve") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SectionHeader(
                    title = "Ways to help",
                    subtitle = state.city?.let { "Near $it" }
                        ?: "Everywhere the platform is running",
                    action = {
                        TextButton(
                            onClick = onCreate,
                            modifier = Modifier.heightIn(min = spacing.minimumTouchTarget),
                        ) {
                            Text("Post one")
                        }
                    },
                )
            }

            item {
                SafeguardToggle(
                    title = "Hide roles that need a background check",
                    description = "Some work with children, or in someone's home, cannot " +
                        "begin until a check is complete. Hide those if you are not able " +
                        "to complete one at the moment.",
                    checked = state.hideBackgroundCheckRequired,
                    onCheckedChange = onToggleHideBackgroundCheck,
                )
            }

            item {
                PrivacyNote(
                    text = "Applying shares your display name and your message with the " +
                        "organiser. It does not share your address or contact details.",
                    modifier = Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.xs,
                    ),
                )
            }

            when {
                state.loading -> item { LoadingState(label = "Loading opportunities") }

                state.opportunities.isEmpty() -> item {
                    EmptyState(
                        title = "Nothing listed here yet",
                        body = "No opportunities match what you are looking at. You could " +
                            "widen the filter, or post something your community needs help " +
                            "with.",
                        actionLabel = "Post an opportunity",
                        onAction = onCreate,
                    )
                }

                else -> items(state.opportunities) { opportunity ->
                    OpportunitySummaryCard(
                        opportunity = opportunity,
                        onClick = { onSelect(opportunity.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OpportunitySummaryCard(
    opportunity: VolunteerOpportunity,
    onClick: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard(
        onClick = onClick,
        contentDescription = "${opportunity.title}. ${opportunity.category.displayName}. " +
            "${opportunity.place.publicLabel}.",
    ) {
        Text(text = opportunity.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = opportunity.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.sm))
        ChipRow(
            labels = buildList {
                add(opportunity.category.displayName)
                add(opportunity.format.displayName)
                add(opportunity.place.publicLabel)
                if (opportunity.genderArrangement != GenderArrangement.NOT_APPLICABLE) {
                    add(opportunity.genderArrangement.displayName)
                }
            },
        )

        Spacer(Modifier.height(spacing.sm))
        Text(
            text = "${serveDateLabel(opportunity.startsAt)} to " +
                serveDateLabel(opportunity.endsAt),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (opportunity.isFull) {
                "No places left at the moment"
            } else {
                "${opportunity.placesRemaining} of ${opportunity.volunteersNeeded} " +
                    "places still open"
            },
            style = MaterialTheme.typography.bodySmall,
        )

        if (opportunity.backgroundCheckRequired || opportunity.backgroundCheckIsMandatory) {
            Text(
                text = "A background check is required before you can start.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (opportunity.status != ListingStatus.OPEN) {
            Text(
                text = "Status: ${opportunity.status.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A plain calendar date. Deliberately unadorned; the ISO form reads the same everywhere. */
private fun serveDateLabel(timestamp: Timestamp): String = timestamp.toString().take(10)
