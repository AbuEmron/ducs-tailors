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
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Community
import org.fisabilillah.core.model.MembershipPolicy
import org.fisabilillah.core.model.SafeguardFloor

/**
 * One community, including everything a person is agreeing to by joining.
 *
 * The rules and the safeguard floor are given the same weight as the description, because
 * joining is the moment somebody accepts terms — and terms a person did not read are terms
 * the platform effectively imposed on them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityDetailScreen(
    community: Community?,
    memberCount: Int,
    organisationName: String?,
    onJoin: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community") },
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
        if (community == null) {
            ScreenColumn(contentPadding = padding) {
                EmptyState(
                    title = "This community is not available",
                    body = "It may have been archived, or it may be invitation-only.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            SectionHeader(title = community.name, subtitle = community.kind.displayName)

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text(text = community.summary, style = MaterialTheme.typography.bodyLarge)
            }

            SectionHeader(title = "Details")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                if (organisationName != null) {
                    DetailRow(label = "Run by", value = organisationName)
                }
                DetailRow(label = "Kind", value = community.kind.displayName)
                DetailRow(
                    label = "Where",
                    value = community.place?.publicLabel ?: "Not tied to one place.",
                )
                DetailRow(
                    label = "Gender arrangement",
                    value = community.genderArrangement.displayName,
                )
                DetailRow(label = "Joining", value = community.membershipPolicy.displayName)
                DetailRow(
                    label = "Members",
                    value = if (memberCount == 1) "1 member" else "$memberCount members",
                )
                if (community.isArchived) {
                    DetailRow(
                        label = "Status",
                        value = "Archived. Kept for reference and no longer active.",
                    )
                }
            }

            SectionHeader(
                title = "The rules here",
                subtitle = "You are agreeing to these by joining",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                if (community.rules.isEmpty()) {
                    Text(
                        text = "The moderators have not written any rules of their own. The " +
                            "platform's community guidelines still apply.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    for (rule in community.rules.sortedBy { it.order }) {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "${rule.order}. ${rule.title}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        Text(
                            text = rule.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionHeader(title = "Safeguards inside this community")
            DisclaimerCard(
                title = "These apply in addition to your own settings",
                text = "A community can only make your safeguards stricter, never looser. " +
                    "Nothing a community sets can undo a limit you have chosen for " +
                    "yourself, and nothing here quietly widens who may contact you. If a " +
                    "community's floor is stricter than your own setting, the stricter one " +
                    "applies while you are in this space.",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                val floorLines = safeguardFloorLines(community.safeguardFloor)
                if (floorLines.isEmpty()) {
                    Text(
                        text = "This community adds no extra restrictions of its own. Your " +
                            "own safeguards apply as they are.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    for (line in floorLines) {
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            SectionDivider()

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                PrivacyNote(
                    text = "Joining shares your display name and profile with the members " +
                        "and moderators of this community.",
                )
                Spacer(Modifier.height(spacing.md))
                PrimaryButton(
                    text = joinLabel(community.membershipPolicy),
                    onClick = onJoin,
                    enabled = !community.isArchived &&
                        community.membershipPolicy != MembershipPolicy.INVITE_ONLY,
                )
                if (community.membershipPolicy == MembershipPolicy.INVITE_ONLY) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "This community is joined by invitation only. A moderator " +
                            "would need to invite you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun joinLabel(policy: MembershipPolicy): String = when (policy) {
    MembershipPolicy.OPEN -> "Join this community"
    MembershipPolicy.APPROVAL_REQUIRED -> "Ask to join"
    MembershipPolicy.INVITE_ONLY -> "Invitation only"
    MembershipPolicy.ORGANIZATION_MEMBERS_ONLY -> "Join as an organisation member"
}

/** Only what the floor actually sets. An unset field imposes nothing and is not listed. */
private fun safeguardFloorLines(floor: SafeguardFloor): List<String> = buildList {
    floor.contactableBy?.let { add("Who may start a conversation here: ${it.displayName}") }
    floor.minimumVerificationToContact?.let {
        add("Minimum verification to contact someone here: ${it.displayName}")
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
        add("Formal introductions cannot be started from this community")
    }
    if (floor.requireBackgroundCheckForMinorContact) {
        add("A background check is required for any contact with someone under eighteen")
    }
}
