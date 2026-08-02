package org.fisabilillah.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.FactChip
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ScreenState
import org.fisabilillah.core.domain.HomeDigest
import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ProjectId
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.VolunteerOpportunity

/**
 * Home.
 *
 * Everything here is either something the member promised to do, something near them that
 * needs doing, or something matching what they said they can help with. There is no feed,
 * no "what's happening", no counts of other people's activity, and nothing that refreshes
 * itself to give someone a reason to keep looking.
 *
 * The one number on the screen is the count of unread notifications, and the one summary
 * is the member's own private service record — which says, in as many words, that it is
 * private and is not ranked against anyone.
 */
@Composable
internal fun HomeScreen(
    state: ScreenState<HomeDigest>,
    contentPadding: PaddingValues,
    onOpenOpportunity: (ListingId) -> Unit,
    onOpenRequest: (RequestId) -> Unit,
    onOpenLearning: (ListingId) -> Unit,
    onOpenProject: (ProjectId) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenServiceHistory: () -> Unit,
    onSeeAllServe: () -> Unit,
    onSeeAllRequests: () -> Unit,
    onSeeAllLearn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FiSabilillahTheme.spacing

    when {
        state.loading -> LoadingState(modifier.padding(contentPadding))

        state.refusal != null -> RefusalNotice(
            message = state.refusal,
            modifier = modifier.padding(contentPadding),
        )

        state.data == null -> EmptyState(
            title = "Nothing to show yet",
            body = "Once your profile is set up, opportunities near you will appear here.",
            modifier = modifier.padding(contentPadding),
        )

        else -> {
            val digest = state.data
            ScreenColumn(modifier = modifier, contentPadding = contentPadding) {
                GreetingRow(
                    name = digest.greetingName,
                    unreadNotifications = digest.unreadNotifications,
                    onOpenNotifications = onOpenNotifications,
                )

                SafetyReminderCard(digest.safetyReminder)

                if (digest.upcomingCommitments.isNotEmpty()) {
                    SectionHeader(
                        title = "What you have promised",
                        subtitle = "Commitments you have made, soonest first.",
                    )
                    for (commitment in digest.upcomingCommitments) {
                        CommitmentCard(commitment)
                    }
                }

                if (digest.nearbyOpportunities.isNotEmpty()) {
                    SectionHeader(
                        title = "Nearby, and needing people",
                        subtitle = "Ordered by when they happen, not by popularity.",
                        action = {
                            TextButton(onClick = onSeeAllServe) { Text("See all") }
                        },
                    )
                    for (opportunity in digest.nearbyOpportunities) {
                        OpportunityCard(opportunity) { onOpenOpportunity(opportunity.id) }
                    }
                }

                if (digest.matchingRequests.isNotEmpty()) {
                    SectionHeader(
                        title = "Someone has asked for help you can give",
                        subtitle = "Matched to the areas you said you can help with.",
                        action = {
                            TextButton(onClick = onSeeAllRequests) { Text("See all") }
                        },
                    )
                    for (request in digest.matchingRequests) {
                        RequestCard(request) { onOpenRequest(request.id) }
                    }
                }

                if (digest.learningCircles.isNotEmpty()) {
                    SectionHeader(
                        title = "Study circles",
                        action = {
                            TextButton(onClick = onSeeAllLearn) { Text("See all") }
                        },
                    )
                    for (offering in digest.learningCircles) {
                        LearningCard(offering) { onOpenLearning(offering.id) }
                    }
                }

                if (digest.projectsNeedingHelp.isNotEmpty()) {
                    SectionHeader(title = "Projects looking for volunteers")
                    for (project in digest.projectsNeedingHelp) {
                        ProjectCard(project) { onOpenProject(project.id) }
                    }
                }

                Spacer(Modifier.height(spacing.md))
                PrivateImpactCard(
                    commitmentsCompleted = digest.impact.commitmentsCompleted,
                    hoursGiven = digest.impact.hoursGiven,
                    sincerityNote = digest.impact.sincerityNote,
                    onOpen = onOpenServiceHistory,
                )
            }
        }
    }
}

@Composable
private fun GreetingRow(
    name: String,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spacing.screenHorizontal,
                end = spacing.xs,
                top = spacing.md,
                bottom = spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Assalamu alaikum,",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        IconButton(onClick = onOpenNotifications) {
            BadgedBox(
                badge = {
                    if (unreadNotifications > 0) {
                        Badge { Text(unreadNotifications.coerceAtMost(99).toString()) }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = if (unreadNotifications > 0) {
                        "Notifications, $unreadNotifications unread"
                    } else {
                        "Notifications"
                    },
                )
            }
        }
    }
}

/** One quiet, practical reminder a day. Rotates by date, never by engagement. */
@Composable
private fun SafetyReminderCard(reminder: String) {
    val spacing = FiSabilillahTheme.spacing
    val safeguard = FiSabilillahTheme.safeguard

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        shape = MaterialTheme.shapes.medium,
        color = safeguard.safeguardActiveContainer,
        contentColor = safeguard.safeguardOnContainer,
    ) {
        Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Text(text = reminder, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CommitmentCard(commitment: Commitment) {
    ContentCard(contentDescription = "Commitment: ${commitment.title}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(FiSabilillahTheme.spacing.xs))
            Text(
                text = commitment.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(FiSabilillahTheme.spacing.xxs))
        Text(
            text = commitment.status.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        commitment.place?.let {
            Text(
                text = it.publicLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OpportunityCard(opportunity: VolunteerOpportunity, onClick: () -> Unit) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard(onClick = onClick, contentDescription = "Opportunity: ${opportunity.title}") {
        Text(text = opportunity.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = opportunity.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FactChip(label = opportunity.category.displayName)
            FactChip(label = opportunity.place.publicLabel)
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            // Places remaining, not "42 people viewing". The difference is the product.
            text = if (opportunity.isFull) {
                "Full"
            } else {
                "${opportunity.placesRemaining} of ${opportunity.volunteersNeeded} places left"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (opportunity.backgroundCheckRequired) {
            Spacer(Modifier.height(spacing.xxs))
            PrivacyNote("A background check is required for this work.")
        }
    }
}

@Composable
private fun RequestCard(request: ServiceRequest, onClick: () -> Unit) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard(onClick = onClick, contentDescription = "Request: ${request.title}") {
        Text(text = request.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = request.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
        Spacer(Modifier.height(spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FactChip(label = request.category.displayName)
            // The area, never the address. The address is released by the requester to one
            // person, once they have accepted their help.
            FactChip(label = request.place.publicLabel)
        }
    }
}

@Composable
private fun LearningCard(offering: LearningOffering, onClick: () -> Unit) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard(onClick = onClick, contentDescription = "Class: ${offering.title}") {
        Text(text = offering.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = offering.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
        Spacer(Modifier.height(spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FactChip(label = offering.subject.displayName)
            FactChip(label = offering.level.displayName)
        }
        if (offering.isPeerLearning) {
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = "Peer learning — members studying together, not authoritative " +
                    "religious instruction.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    ContentCard(onClick = onClick, contentDescription = "Project: ${project.title}") {
        Text(text = project.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(FiSabilillahTheme.spacing.xxs))
        Text(
            text = project.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
    }
}

@Composable
private fun PrivateImpactCard(
    commitmentsCompleted: Int,
    hoursGiven: Int,
    sincerityNote: String,
    onOpen: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard(onClick = onOpen) {
        Text(
            text = "Your own record",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = "$commitmentsCompleted commitments completed · $hoursGiven hours given",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(spacing.xs))
        PrivacyNote(sincerityNote)
    }
}
