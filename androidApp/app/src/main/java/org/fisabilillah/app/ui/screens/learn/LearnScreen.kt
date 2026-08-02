package org.fisabilillah.app.ui.screens.learn

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ChipRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.LearnState
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.LearningCost
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.Money

/**
 * Classes, circles and tutoring on offer.
 *
 * No class is ranked, featured, or ordered by how many people signed up. What a person
 * needs in order to choose is the subject, the level, who is teaching and in what capacity
 * — so that is what a card shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LearnScreen(
    state: LearnState,
    onSelect: (ListingId) -> Unit,
    onToggleFreeOnly: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Learn") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SectionHeader(
                    title = "Study with your community",
                    subtitle = "Each listing states the teacher's capacity in their own words",
                )
            }

            item {
                DisclaimerCard(
                    title = "This platform does not issue religious rulings",
                    text = "Classes here are arrangements between members. Nothing on this " +
                        "platform is a fatwa, and a question about your own circumstances " +
                        "belongs with a qualified scholar who knows them.",
                )
            }

            item {
                SafeguardToggle(
                    title = "Show only classes with no fee",
                    description = "Includes classes offered free and those asking for a " +
                        "suggested donation you are free to ignore.",
                    checked = state.freeOnly,
                    onCheckedChange = onToggleFreeOnly,
                )
            }

            when {
                state.loading -> item { LoadingState(label = "Loading classes") }

                state.offerings.isEmpty() -> item {
                    EmptyState(
                        title = "No classes listed here yet",
                        body = "Nothing matches what you are looking at. Try turning the " +
                            "filter off, or ask in one of your communities whether anyone " +
                            "is teaching.",
                    )
                }

                else -> items(state.offerings) { offering ->
                    LearningSummaryCard(
                        offering = offering,
                        onClick = { onSelect(offering.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LearningSummaryCard(
    offering: LearningOffering,
    onClick: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard(
        onClick = onClick,
        contentDescription = "${offering.title}. ${offering.subject.displayName}. " +
            "${offering.level.displayName}.",
    ) {
        Text(text = offering.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = offering.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (offering.isPeerLearning) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "Peer learning. Members studying together, not authoritative " +
                    "religious instruction.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(spacing.sm))
        ChipRow(
            labels = buildList {
                add(offering.subject.displayName)
                add(offering.level.displayName)
                add(offering.format.displayName)
                add(offering.language.displayName)
                add(learnCostLabel(offering.cost))
                if (offering.genderArrangement != GenderArrangement.NOT_APPLICABLE) {
                    add(offering.genderArrangement.displayName)
                }
            },
        )

        Spacer(Modifier.height(spacing.sm))
        Text(
            text = "Taught as: ${offering.instructorCapacity.displayName}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (offering.placesRemaining == 0) {
                "No places left at the moment"
            } else {
                "${offering.placesRemaining} of ${offering.maxStudents} places still open"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        val startsOn = offering.startsOn
        if (startsOn != null) {
            Text(
                text = "Starts ${startsOn.toString().take(10)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun learnCostLabel(cost: LearningCost): String = when (cost) {
    is LearningCost.Free -> "Free"
    is LearningCost.SuggestedDonation ->
        "Suggested donation ${learnMoneyLabel(cost.amount)}"
    is LearningCost.Fee -> "${learnMoneyLabel(cost.amount)} ${cost.per.displayName}"
}

internal fun learnMoneyLabel(money: Money): String {
    val major = money.minorUnits / 100
    val minor = money.minorUnits % 100
    val minorText = if (minor < 10) "0$minor" else minor.toString()
    return "${money.currencyCode} $major.$minorText"
}
