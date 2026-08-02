package org.fisabilillah.app.ui.screens.moderation

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.FactChip
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.UserId

/**
 * Appointing and removing staff.
 *
 * The most consequential screen in the application, and it is written to feel like it. A
 * grant here creates somebody who can read private conversations and restrict accounts;
 * the disclaimer says so, the reason field is mandatory, and both facts appear before the
 * buttons rather than after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoleAdministrationScreen(
    staff: List<Profile>,
    grantable: List<AccountRole>,
    refusal: String?,
    submitting: Boolean,
    onGrant: (UserId, AccountRole, String) -> Unit,
    onRevoke: (UserId, AccountRole, String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var memberId by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(AccountRole.MODERATOR) }
    var reason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))
            if (refusal != null) RefusalNotice(message = refusal)

            DisclaimerCard(
                title = "You are creating somebody who can read private conversations",
                text = "A moderator can open a reported thread, see a member's safeguards, " +
                    "and restrict an account. A safety administrator can do more. Every " +
                    "grant and every removal is written to a log with no delete path — " +
                    "including yours, and including this one.",
            )

            SectionHeader(title = "Current staff")
            if (staff.isEmpty()) {
                EmptyState(
                    title = "Nobody holds a staff role",
                    body = "Reports will arrive with nobody to read them until somebody does.",
                )
            } else {
                for (person in staff) {
                    ContentCard {
                        Text(
                            text = person.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        for (held in person.roles.filter { it.isStaff }) {
                            DetailRow(label = held.displayName, value = person.id.value)
                        }
                    }
                }
            }

            SectionDivider()

            SectionHeader(
                title = "Grant or remove",
                subtitle = "You cannot change the roles on your own account, in either " +
                    "direction. That is deliberate.",
            )
            LabelledField(
                label = "Member id",
                value = memberId,
                onValueChange = { memberId = it },
                helper = "Taken from their profile. There is no search here on purpose — " +
                    "browsing people to hand out powers is not a flow this product wants.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            for (option in grantable) {
                ChoiceRow(
                    title = option.displayName,
                    description = if (option.isStaff) {
                        "A staff role. Carries access to other members' private material."
                    } else {
                        "A listing role. Changes how this member is described, not what " +
                            "they can read."
                    },
                    selected = role == option,
                    onSelect = { role = option },
                )
            }
            LabelledField(
                label = "Why",
                value = reason,
                onValueChange = { reason = it },
                helper = "Written to the permanent record and shown to the member.",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            val ready = memberId.isNotBlank() && reason.isNotBlank() && !submitting
            PrimaryButton(
                text = "Grant ${role.displayName.lowercase()}",
                onClick = { onGrant(UserId(memberId.trim()), role, reason) },
                enabled = ready,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.xs))
            SecondaryButton(
                text = "Remove ${role.displayName.lowercase()}",
                onClick = { onRevoke(UserId(memberId.trim()), role, reason) },
                enabled = ready,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.sm))
            PrivacyNote(
                text = "The last platform administrator cannot be removed. An installation " +
                    "with nobody able to grant roles cannot be repaired from inside the app.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/**
 * Where you are signed in.
 *
 * A list without a way to act on it is an anxiety generator, so the revoke controls are on
 * the same screen and the "end everything else" button is the prominent one — somebody
 * looking at this page is usually looking because they are worried.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceSessionsScreen(
    sessions: List<org.fisabilillah.core.model.DeviceSession>,
    currentSessionId: String?,
    refusal: String?,
    submitting: Boolean,
    onRevoke: (String) -> Unit,
    onRevokeAllOthers: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Where you are signed in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))
            if (refusal != null) RefusalNotice(message = refusal)

            val active = sessions.filter { it.isActive }
            if (active.isEmpty()) {
                EmptyState(
                    title = "No other sessions",
                    body = "This is the only device signed in to your account.",
                )
            } else {
                if (active.size > 1) {
                    PrimaryButton(
                        text = "This wasn't me — end every other session",
                        onClick = onRevokeAllOthers,
                        enabled = !submitting,
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
                    Spacer(Modifier.height(spacing.sm))
                }

                for (session in active) {
                    ContentCard {
                        Text(
                            text = session.deviceLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        DetailRow(label = "Platform", value = session.platform)
                        DetailRow(label = "Last used", value = session.lastSeenAt.toString())
                        if (session.id == currentSessionId) {
                            Spacer(Modifier.height(spacing.xxs))
                            FactChip(label = "This device")
                        }
                    }
                    if (session.id != currentSessionId) {
                        SecondaryButton(
                            text = "End this session",
                            onClick = { onRevoke(session.id) },
                            enabled = !submitting,
                            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                        )
                    }
                    Spacer(Modifier.height(spacing.sm))
                }
            }

            PrivacyNote(
                text = "Ending a session signs that device out. If you did not recognise one " +
                    "of these, change your password afterwards — ending the session does not " +
                    "stop somebody who knows it from signing back in.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}
