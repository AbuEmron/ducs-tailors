package org.fisabilillah.app.ui.screens.community

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
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Community
import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.GenderArrangement

/**
 * Moderated spaces a person can join.
 *
 * Communities are listed alphabetically by whatever the domain layer returned. There is no
 * "most active" ordering, because a small circle of six people meeting weekly is not worse
 * than a large one, and ranking them would say otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityScreen(
    communities: List<Community>,
    onSelect: (CommunityId) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Communities") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SectionHeader(
                    title = "Places to belong",
                    subtitle = "Most of what happens here happens in a group, on purpose",
                )
            }

            item {
                DisclaimerCard(
                    text = "Every community sets its own rules and its own safeguards. " +
                        "Those apply on top of your own settings and can only make things " +
                        "stricter for you, never looser.",
                )
            }

            if (communities.isEmpty()) {
                item {
                    EmptyState(
                        title = "No communities listed",
                        body = "Nothing is open to join at the moment. Communities that are " +
                            "invitation-only do not appear here.",
                    )
                }
            } else {
                items(communities) { community ->
                    CommunitySummaryCard(
                        community = community,
                        onClick = { onSelect(community.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunitySummaryCard(
    community: Community,
    onClick: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard(
        onClick = onClick,
        contentDescription = "${community.name}. ${community.kind.displayName}.",
    ) {
        Text(text = community.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = community.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.sm))
        ChipRow(
            labels = buildList {
                add(community.kind.displayName)
                add(community.membershipPolicy.displayName)
                val place = community.place
                if (place != null) add(place.publicLabel)
                if (community.genderArrangement != GenderArrangement.NOT_APPLICABLE) {
                    add(community.genderArrangement.displayName)
                }
            },
        )

        if (community.rules.isNotEmpty()) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = "${community.rules.size} rules you would be agreeing to",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (community.isArchived) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "Archived. It is kept for reference and is no longer active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
