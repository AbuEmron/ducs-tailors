package org.fisabilillah.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.fisabilillah.app.di.SessionManager
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.InitialsAvatar
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.UserId

/**
 * Development sign-in: pick one of the seeded accounts.
 *
 * This screen is not dressed up as authentication, because pretending would make it harder
 * rather than easier to replace. There is no password field, nothing is checked, and the
 * disclaimer says so in the first thing a reader's eye lands on. Real authentication
 * (Supabase Auth) is a documented next step, and the session boundary it will sit behind
 * already exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignInScreen(
    accounts: List<SessionManager.AccountOption>,
    onSignIn: (UserId) -> Unit,
    onSignUp: () -> Unit,
    onRecover: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            DisclaimerCard(
                title = "Development sign-in",
                text = "This build has no authentication. Choosing an account below simply " +
                    "adopts one of the sample profiles that ship with the app, and any " +
                    "device running this build can do the same. Nothing is checked, no " +
                    "password is asked for, and none of the database access rules that " +
                    "protect real accounts apply here. Real sign-in, backed by Supabase " +
                    "Auth, is the next piece of work.",
            )

            SectionHeader(
                title = "Sample accounts",
                subtitle = "Each one has different roles, so the app behaves differently.",
            )

            if (accounts.isEmpty()) {
                EmptyState(
                    title = "No sample accounts were loaded",
                    body = "The development data set did not populate. Restart the app; if " +
                        "the list is still empty, the seed data has failed to load.",
                )
            } else {
                for (account in accounts) {
                    AccountCard(account = account, onSelect = { onSignIn(account.userId) })
                }
            }

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "Sample accounts and everything they do live in memory only. Nothing " +
                    "survives closing the app, and no data leaves the device.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionDivider()

            SecondaryButton(
                text = "Create an account instead",
                onClick = onSignUp,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.xs))
            SecondaryButton(
                text = "I cannot get into my account",
                onClick = onRecover,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun AccountCard(
    account: SessionManager.AccountOption,
    onSelect: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard(
        onClick = onSelect,
        contentDescription = "Sign in as ${account.displayName}. ${account.summary}",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name = account.displayName)
            Spacer(Modifier.width(spacing.sm))
            Column {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = account.summary.ifBlank { "No roles recorded" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
