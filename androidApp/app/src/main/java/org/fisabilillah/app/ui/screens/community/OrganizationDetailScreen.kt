package org.fisabilillah.app.ui.screens.community

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
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.components.VerificationBadge
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Organization

/**
 * One organisation.
 *
 * The verification section says what was actually checked — registration documents — and
 * then says, in the same breath, what that does not amount to. The platform checks papers.
 * It does not audit accounts, inspect activities, or vouch for anyone's character, and a
 * badge that let a reader assume otherwise would be doing harm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizationDetailScreen(
    organisation: Organization?,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organisation") },
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
        if (organisation == null) {
            ScreenColumn(contentPadding = padding) {
                EmptyState(
                    title = "This organisation is not available",
                    body = "It may have closed its account, or it may be awaiting review.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            SectionHeader(title = organisation.name, subtitle = organisation.kind.displayName)

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text(text = organisation.summary, style = MaterialTheme.typography.bodyLarge)
            }

            SectionHeader(title = "Details")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(label = "Kind", value = organisation.kind.displayName)
                DetailRow(label = "Where", value = organisation.place.publicLabel)
                DetailRow(label = "Status", value = organisation.status.displayName)
                val website = organisation.websiteUrl
                if (website != null) {
                    DetailRow(label = "Website", value = website)
                }
                val email = organisation.contactEmail
                if (email != null) {
                    DetailRow(label = "Contact", value = email)
                }
            }

            SectionHeader(title = "What has been checked")
            val badge = organisation.verification.badge
            if (badge != null) {
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    VerificationBadge(
                        label = badge.displayName,
                        whatItDoesNotMean = badge.whatItDoesNotMean,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = badge.whatItDoesNotMean,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    val registrationNumber = organisation.verification.registrationNumber
                    if (registrationNumber != null) {
                        DetailRow(label = "Registration number", value = registrationNumber)
                    }
                    val jurisdiction = organisation.verification.registrationJurisdiction
                    if (jurisdiction != null) {
                        DetailRow(label = "Registered in", value = jurisdiction)
                    }
                    val checkedAt = organisation.verification.checkedAt
                    if (checkedAt != null) {
                        DetailRow(
                            label = "Documents checked on",
                            value = checkedAt.toString().take(10),
                        )
                    }
                    val expiresAt = organisation.verification.expiresAt
                    if (expiresAt != null) {
                        DetailRow(
                            label = "This check lapses on",
                            value = expiresAt.toString().take(10),
                        )
                    }
                }
            } else {
                DisclaimerCard(
                    title = "Nothing has been checked",
                    text = "This organisation's registration documents have not been " +
                        "verified. That does not mean anything is wrong; it means the " +
                        "platform has checked nothing and you should judge for yourself.",
                )
            }

            DisclaimerCard(
                title = "What verification is not",
                text = "Checking registration documents is not an audit. The platform does " +
                    "not inspect how money is spent, does not supervise activities, and " +
                    "takes no position on any organisation's religious or professional " +
                    "standing. An organisation, not the platform, is responsible for what it " +
                    "runs.",
            )

            if (!organisation.canRaiseFunds) {
                DisclaimerCard(
                    title = "This organisation cannot collect funds here",
                    text = "Fundraising through the platform is available only to " +
                        "organisations whose registration has been checked and whose account " +
                        "is active. Anything you give directly is between you and them.",
                )
            }

            SectionHeader(title = "Safeguards this organisation imposes")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                val floorLines = organisationFloorLines(organisation)
                if (floorLines.isEmpty()) {
                    Text(
                        text = "This organisation adds no extra restrictions of its own. " +
                            "Your own safeguards apply as they are.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "These apply in its own spaces, on top of your own settings. " +
                            "They can only make things stricter for you, never looser.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    for (line in floorLines) {
                        Text(text = "• $line", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun organisationFloorLines(organisation: Organization): List<String> {
    val floor = organisation.safeguardFloor
    return buildList {
        floor.contactableBy?.let { add("Who may start a conversation: ${it.displayName}") }
        floor.minimumVerificationToContact?.let {
            add("Minimum verification to contact someone: ${it.displayName}")
        }
        floor.crossGenderStructure?.let {
            add("Conversations with the opposite gender: ${it.displayName}")
        }
        floor.moderatorPresence?.let { add("A moderator is present: ${it.displayName}") }
        if (floor.requireGroupContext) {
            add("Conversations must happen inside a group thread rather than privately")
        }
        if (floor.requireThirdParty) {
            add("A third party must be in the conversation")
        }
        floor.voiceCallsAllowedFrom?.let { add("Voice calls: ${it.displayName}") }
        floor.videoCallsAllowedFrom?.let { add("Video calls: ${it.displayName}") }
        floor.oneToOneMeetingsAllowedFrom?.let { add("Meeting one to one: ${it.displayName}") }
        if (floor.meetingsMustBeInPublicPlaces) {
            add("Meetings arranged here must be in public places")
        }
        if (floor.forbidFormalIntroductions) {
            add("Formal introductions cannot be started in its spaces")
        }
        if (floor.requireBackgroundCheckForMinorContact) {
            add("A background check is required for any contact with someone under eighteen")
        }
    }
}
