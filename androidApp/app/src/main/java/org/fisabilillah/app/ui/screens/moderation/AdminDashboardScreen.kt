package org.fisabilillah.app.ui.screens.moderation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.AuditLogEntry

/**
 * The administrator's view of the audit trail.
 *
 * There is nothing on this screen but a record and the statement that the record cannot be
 * altered. An administrator who could quietly edit or remove an entry would make the entire
 * audit log worthless — the value of the trail is precisely that the most privileged
 * account on the platform cannot clean up after itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminDashboardScreen(
    auditTrail: List<AuditLogEntry>,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit trail") },
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
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {

            item {
                Spacer(Modifier.height(spacing.xs))
                DisclaimerCard(
                    title = "This record cannot be changed",
                    text = "Audit entries have no update path and no delete path anywhere " +
                        "in this system: not in the repository interfaces, not in the " +
                        "row-level security policies, and not for you. Administrator " +
                        "access is read-only here by design. If an entry is wrong, the " +
                        "correction is a new entry, never an edit to an old one.",
                )
            }

            item {
                SectionHeader(
                    title = "Recent entries",
                    subtitle = "Newest first, as the log returned them.",
                )
            }

            if (auditTrail.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing recorded yet",
                        body = "Entries appear here as consequential things happen.",
                    )
                }
            } else {
                items(auditTrail) { entry -> AuditEntryCard(entry) }
            }

            item {
                Spacer(Modifier.height(spacing.md))
                PrivacyNote(
                    text = "The log records what was done and by whom. It is not a way to " +
                        "read members' messages: opening a conversation as staff is itself " +
                        "an auditable action, and it appears in this trail like anything " +
                        "else.",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
                Spacer(Modifier.height(spacing.xl))
            }
        }
    }
}
