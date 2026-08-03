package org.fisabilillah.app.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fisabilillah.app.ui.components.ChipRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.InitialsAvatar
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.components.VerificationBadge
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.MyProfileState

/**
 * Your own account.
 *
 * Ordered by what protects a person rather than by what the platform would like them to
 * do. Safeguards come before anything else; the service record sits well down the page and
 * is described as private, because a volunteering history displayed prominently to its
 * owner is one small step from being displayed to everyone else.
 *
 * There is nothing here to complete, no profile-strength meter, and no prompt to add a
 * photograph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    state: MyProfileState,
    onEditProfile: () -> Unit,
    onSafeguards: () -> Unit,
    onPrivacy: () -> Unit,
    onTrustedContacts: () -> Unit,
    onWaliSettings: () -> Unit,
    onServiceHistory: () -> Unit,
    onGiving: () -> Unit,
    onCommitments: () -> Unit,
    onVerification: () -> Unit,
    onQualifications: () -> Unit,
    onCreateClass: () -> Unit,
    onCreateProject: () -> Unit,
    onCreateCommunity: () -> Unit,
    onDevices: () -> Unit,
    onRoles: () -> Unit,
    onSafetyCentre: () -> Unit,
    onNotifications: () -> Unit,
    onAccountData: () -> Unit,
    onModeration: () -> Unit,
    showModeration: Boolean,
    onSignOut: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = { TopAppBar(title = { Text("You") }) },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.padding(padding)) { LoadingState(label = "Loading your account") }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            val profile = state.profile

            Spacer(Modifier.height(spacing.xs))

            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(name = profile?.displayName ?: "Member", size = 56.dp)
                    Spacer(Modifier.width(spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = profile?.displayName ?: "Member",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (profile != null) {
                            Spacer(Modifier.height(spacing.xxs))
                            Text(
                                text = profile.place.publicLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (profile != null) {
                    Spacer(Modifier.height(spacing.sm))
                    VerificationBadge(
                        label = profile.verificationLevel.displayName,
                        whatItDoesNotMean = profile.verificationLevel.whatItDoesNotMean,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = profile.verificationLevel.whatItDoesNotMean,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val statement = profile.contributionStatement
                    if (statement != null) {
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            text = statement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (profile.languages.isNotEmpty()) {
                        Spacer(Modifier.height(spacing.sm))
                        ChipRow(labels = profile.languages.map { it.displayName })
                    }

                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "Account status: ${profile.status.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(spacing.sm))
                SecondaryButton(text = "Edit your profile", onClick = onEditProfile)
            }

            // ── Safeguards first ─────────────────────────────────────────────
            SectionHeader(
                title = "Your boundaries",
                subtitle = "Who may find you, who may contact you, and on what terms.",
            )

            SettingRow(
                title = "Safeguards",
                description = "Discovery, contact, cross-gender conversations, calls, " +
                    "meetings and quiet hours. You can make any of these stricter at any " +
                    "time.",
                onClick = onSafeguards,
            )

            SettingRow(
                title = "Privacy controls",
                description = "What other members can see: your name, your area, your " +
                    "profile image.",
                onClick = onPrivacy,
            )

            SettingRow(
                title = "Trusted contacts",
                description = if (state.trustedContacts.isEmpty()) {
                    "No one nominated yet. A trusted contact can be added to conversations " +
                        "you choose to have supervised."
                } else {
                    "${state.trustedContacts.size} nominated. Their contact details are " +
                        "held privately and are never shown to another member."
                },
                onClick = onTrustedContacts,
            )

            SettingRow(
                title = "Formal introductions",
                description = if (state.introductionSettings?.enabled == true) {
                    "On. Requests go through the person you have nominated."
                } else {
                    "Off. No one can submit a marriage introduction to you."
                },
                onClick = onWaliSettings,
            )

            SectionDivider()

            // ── Safety ───────────────────────────────────────────────────────
            SectionHeader(title = "Safety")

            SettingRow(
                title = "Safety centre",
                description = "Reports you have made, the community guidelines, and what " +
                    "happens when something is reported.",
                onClick = onSafetyCentre,
            )

            SettingRow(
                title = "Notifications",
                description = "Only things you asked for or committed to. Nothing here " +
                    "exists to bring you back into the app.",
                onClick = onNotifications,
            )

            SectionDivider()

            // ── Your own record ──────────────────────────────────────────────
            SectionHeader(title = "Your record")

            SettingRow(
                title = "Your commitments",
                description = "Check in when you arrive, and confirm the people helping " +
                    "with what you organise.",
                onClick = onCommitments,
            )

            SettingRow(
                title = "Giving",
                description = "Appeals that are collecting, and a record of what you have " +
                    "given. Payment is taken on the processor's own page, never in the app.",
                onClick = onGiving,
            )

            SettingRow(
                title = "Service history",
                description = "What you have committed to and completed. Private to you.",
                onClick = onServiceHistory,
            )

            SettingRow(
                title = "Verification",
                description = "What the platform has checked about you, what each level " +
                    "does not mean, and how to ask for more.",
                onClick = onVerification,
            )

            SettingRow(
                title = "Qualifications",
                description = "Credentials you have claimed, and what a reviewer said " +
                    "about them.",
                onClick = onQualifications,
            )

            SettingRow(
                title = "Where you are signed in",
                description = "Every device with access to your account, and a way to end " +
                    "any of them.",
                onClick = onDevices,
            )

            SettingRow(
                title = "Your data",
                description = "Export everything held about you, or ask for your account " +
                    "to be deleted.",
                onClick = onAccountData,
            )

            SectionDivider()

            // ── Offering something ───────────────────────────────────────────
            SectionHeader(
                title = "Offer something",
                subtitle = "The platform is what its members bring to it.",
            )

            SettingRow(
                title = "Teach a class",
                description = "A subject you can help others with. You will be asked in " +
                    "what capacity, and what you teach from.",
                onClick = onCreateClass,
            )

            SettingRow(
                title = "Start a project",
                description = "A piece of work several people will do together.",
                onClick = onCreateProject,
            )

            SettingRow(
                title = "Start a community",
                description = "A space with its own rules, which you would moderate.",
                onClick = onCreateCommunity,
            )

            if (showModeration) {
                SectionDivider()
                SectionHeader(
                    title = "Safety team",
                    subtitle = "Everything you do here is permanently recorded against " +
                        "your name.",
                )
                SettingRow(
                    title = "Moderation queue",
                    description = "Open cases awaiting a human.",
                    onClick = onModeration,
                )
                SettingRow(
                    title = "Roles",
                    description = "Appoint and remove staff. The most consequential screen " +
                        "in the application.",
                    onClick = onRoles,
                )
            }

            Spacer(Modifier.height(spacing.lg))

            SecondaryButton(
                text = "Sign out",
                onClick = onSignOut,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))

            PrivacyNote(
                text = "Your service record, your safeguards and your trusted contacts are " +
                    "yours. None of them are shown to other members, and none of them are " +
                    "ranked against anyone else's.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard(onClick = onClick, contentDescription = "$title. $description") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
