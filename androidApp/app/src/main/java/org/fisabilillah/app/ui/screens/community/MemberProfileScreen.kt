package org.fisabilillah.app.ui.screens.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fisabilillah.app.ui.components.CautionButton
import org.fisabilillah.app.ui.components.ChipRow
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.InitialsAvatar
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.components.VerificationBadge
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.VisibleProfile

/**
 * Another member's profile.
 *
 * Everything on this page answers one question: could this person help with this
 * particular thing, and on what terms. There is nothing about appearance, nothing to
 * scroll through for its own sake, and no way to reach anyone without first reading what
 * they have asked of the people who write to them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemberProfileScreen(
    profile: VisibleProfile?,
    onMessage: () -> Unit,
    onFormalIntroduction: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Member") },
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
        if (profile == null) {
            ScreenColumn(contentPadding = padding) {
                EmptyState(
                    title = "This profile is not available",
                    body = "The member may have closed their account, or their settings may " +
                        "not allow you to see it.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            Row(
                modifier = Modifier.padding(
                    horizontal = spacing.screenHorizontal,
                    vertical = spacing.md,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Initials, always. Even where a member has a photograph and has chosen to
                // show it to this viewer, there is no image loading library in this project,
                // so nothing is fetched over the network and initials stand in.
                InitialsAvatar(name = profile.displayName, size = 64.dp)
                Spacer(Modifier.width(spacing.md))
                Column {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val realName = profile.realName
                    if (realName != null && realName != profile.displayName) {
                        Text(
                            text = realName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val locationLabel = profile.locationLabel
                    if (locationLabel != null) {
                        Text(
                            text = locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val contributionStatement = profile.contributionStatement
            if (contributionStatement != null) {
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    Text(
                        text = contributionStatement,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            val teachingCapacity = profile.teachingCapacity
            if (teachingCapacity != null) {
                SectionHeader(title = "Teaches as")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    Text(
                        text = teachingCapacity.displayName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                DisclaimerCard(text = teachingCapacity.disclaimer)
            }

            SectionHeader(title = "What they can help with")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                if (profile.areasWillingToHelp.isEmpty()) {
                    Text(
                        text = "This member has not listed any areas yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ChipRow(labels = profile.areasWillingToHelp.map { it.displayName })
                }
            }

            SectionHeader(
                title = "Skills",
                subtitle = "Self-declared, and shown as such",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                if (profile.skills.isEmpty()) {
                    Text(
                        text = "No skills listed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ChipRow(
                        labels = profile.skills.map { userSkill ->
                            "${userSkill.skill.displayName} — " +
                                userSkill.proficiency.displayName
                        },
                    )
                }
            }

            SectionHeader(title = "Languages")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                ChipRow(labels = profile.languages.map { it.displayName })
            }

            SectionHeader(title = "When they are free")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                val hoursPerWeek = profile.availability.hoursPerWeek
                DetailRow(
                    label = "Time offered",
                    value = if (hoursPerWeek != null) {
                        "About $hoursPerWeek hours a week"
                    } else {
                        "Not stated"
                    },
                )
                DetailRow(label = "Time zone", value = profile.availability.timeZoneId)
                DetailRow(
                    label = "Urgent requests",
                    value = if (profile.availability.openToUrgentRequests) {
                        "Open to being asked at short notice"
                    } else {
                        "Prefers not to be asked at short notice"
                    },
                )
                val availabilityNotes = profile.availability.notes
                if (availabilityNotes != null) {
                    DetailRow(label = "Notes", value = availabilityNotes)
                }
                if (profile.availability.windows.isNotEmpty()) {
                    DetailRow(
                        label = "Usual times",
                        value = profile.availability.windows.joinToString("\n") { window ->
                            val day = window.day.name.lowercase()
                                .replaceFirstChar { letter -> letter.uppercaseChar() }
                            "$day, ${window.start} to ${window.end}"
                        },
                    )
                }
            }

            SectionHeader(
                title = "What has been checked",
                subtitle = "And what it does not prove",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                VerificationBadge(
                    label = profile.verificationLevel.displayName,
                    whatItDoesNotMean = profile.verificationLevel.whatItDoesNotMean,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = profile.verificationLevel.whatItDoesNotMean,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                for (attestation in profile.attestations) {
                    Spacer(Modifier.height(spacing.sm))
                    VerificationBadge(
                        label = attestation.displayName,
                        whatItDoesNotMean = attestation.whatItDoesNotMean,
                    )
                    Spacer(Modifier.height(spacing.xxs))
                    Text(
                        text = attestation.whatItDoesNotMean,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (profile.trustLabels.isNotEmpty()) {
                SectionHeader(
                    title = "Record of service",
                    subtitle = "Labels, not scores. Nothing here is ranked or sortable",
                )
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    for (label in profile.trustLabels) {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = label.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        Text(
                            text = label.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionDivider()

            SectionHeader(title = "Getting in touch")

            val contactability = profile.contactability
            if (!contactability.canInitiate) {
                RefusalNotice(
                    message = contactability.reasonIfNot
                        ?: "This member is not accepting new conversations at the moment.",
                )
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    Spacer(Modifier.height(spacing.md))
                    PrimaryButton(
                        text = "Message",
                        onClick = onMessage,
                        enabled = false,
                    )
                }
            } else {
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    if (contactability.requirements.isEmpty()) {
                        Text(
                            text = "This member has not set any extra conditions on being " +
                                "contacted. The platform's own rules still apply: state your " +
                                "purpose, and keep to it.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = "Before you write, these terms apply:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        for (requirement in contactability.requirements) {
                            Text(
                                text = "• $requirement",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.sm))
                    PrivacyNote(
                        text = "You will be asked to state a purpose. It is shown to this " +
                            "member, and it stays at the top of the conversation.",
                    )
                    Spacer(Modifier.height(spacing.md))
                    PrimaryButton(text = "Message", onClick = onMessage)
                }
            }

            SectionDivider()

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                CautionButton(text = "Block this member", onClick = onBlock)
                Spacer(Modifier.height(spacing.sm))
                CautionButton(text = "Report this member", onClick = onReport)
            }

            SectionDivider()

            // Deliberately last, deliberately quiet, and deliberately nowhere near the
            // message button. This is not a dating app, and a prominent control here would
            // make it read like one.
            Column(
                modifier = Modifier.padding(
                    horizontal = spacing.screenHorizontal,
                    vertical = spacing.sm,
                ),
            ) {
                Text(
                    text = "If you are considering marriage, a formal introduction goes " +
                        "through this member's wali or trusted contact rather than through " +
                        "private messages. It is a separate process with its own rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onFormalIntroduction,
                    modifier = Modifier.heightIn(min = spacing.minimumTouchTarget),
                ) {
                    Text(
                        text = "Request a formal introduction",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
