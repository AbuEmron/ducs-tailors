package org.fisabilillah.app.ui.screens.profile

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
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
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
import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.CommitmentId
import org.fisabilillah.core.model.CommitmentStatus

/**
 * Turning up, and being counted as having turned up.
 *
 * Commitments are the unit this platform says it cares about — every trust label is
 * derived from them — and until this screen existed there was no way to complete one. The
 * labels were therefore unearnable by any real member, which made the reputation design a
 * description of something that could not happen.
 *
 * There is deliberately no cancel-without-notice button. A commitment somebody is relying
 * on is cancelled by telling them, which is a message, not a state change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyCommitmentsScreen(
    commitments: List<Commitment>,
    /** Commitments on this member's own listings, waiting for them to confirm attendance. */
    awaitingMyConfirmation: List<Commitment>,
    loading: Boolean,
    refusal: String?,
    submitting: Boolean,
    onCheckIn: (CommitmentId) -> Unit,
    onCheckOut: (CommitmentId) -> Unit,
    onConfirm: (CommitmentId, Boolean) -> Unit,
    onEndorse: (CommitmentId) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your commitments") },
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

            if (!loading && commitments.isEmpty() && awaitingMyConfirmation.isEmpty()) {
                EmptyState(
                    title = "Nothing arranged",
                    body = "When you agree to help with something it appears here, with a " +
                        "way to check in on the day.",
                )
                return@ScreenColumn
            }

            if (commitments.isNotEmpty()) {
                SectionHeader(
                    title = "Yours",
                    subtitle = "Check in when you arrive and check out when you are done.",
                )
                for (commitment in commitments) {
                    CommitmentCard(
                        commitment = commitment,
                        submitting = submitting,
                        onCheckIn = { onCheckIn(commitment.id) },
                        onCheckOut = { onCheckOut(commitment.id) },
                    )
                }
            }

            if (awaitingMyConfirmation.isNotEmpty()) {
                SectionDivider()
                SectionHeader(
                    title = "Waiting on you",
                    subtitle = "People who said they would help with something you are " +
                        "organising. Confirming is what makes their record real.",
                )
                for (commitment in awaitingMyConfirmation) {
                    ConfirmationCard(
                        commitment = commitment,
                        submitting = submitting,
                        onConfirm = { attended -> onConfirm(commitment.id, attended) },
                        onEndorse = { onEndorse(commitment.id) },
                    )
                }
            }

            Spacer(Modifier.height(spacing.sm))
            PrivacyNote(
                text = "Your reliability is derived from these and shown only to you. There " +
                    "is no league table, and nobody is ranked against anybody.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun CommitmentCard(
    commitment: Commitment,
    submitting: Boolean,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard {
        Text(
            text = commitment.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.xxs))
        DetailRow(label = "Starts", value = commitment.startsAt.toString())
        DetailRow(label = "State", value = commitment.status.displayName)
        commitment.punctuality?.let {
            Spacer(Modifier.height(spacing.xxs))
            FactChip(label = it.displayName)
        }
    }

    when {
        commitment.checkedInAt == null && commitment.status == CommitmentStatus.SCHEDULED -> {
            PrimaryButton(
                text = "I have arrived",
                onClick = onCheckIn,
                enabled = !submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
        commitment.checkedInAt != null && commitment.checkedOutAt == null -> {
            SecondaryButton(
                text = "I am finished",
                onClick = onCheckOut,
                enabled = !submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
        else -> Unit
    }
    Spacer(Modifier.height(spacing.sm))
}

@Composable
private fun ConfirmationCard(
    commitment: Commitment,
    submitting: Boolean,
    onConfirm: (Boolean) -> Unit,
    onEndorse: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard {
        Text(
            text = commitment.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.xxs))
        DetailRow(
            label = "Checked in",
            value = commitment.checkedInAt?.toString() ?: "Did not check in",
        )
        DetailRow(label = "State", value = commitment.status.displayName)
    }

    if (commitment.organizerConfirmedAt == null) {
        PrimaryButton(
            text = "They were there",
            onClick = { onConfirm(true) },
            enabled = !submitting,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )
        Spacer(Modifier.height(spacing.xs))
        SecondaryButton(
            text = "They did not come",
            onClick = { onConfirm(false) },
            enabled = !submitting,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )
        Spacer(Modifier.height(spacing.xs))
        PrivacyNote(
            text = "Recording a no-show affects somebody's record. If they told you they " +
                "could not make it, that is not a no-show — say they were not there only " +
                "if they simply did not come.",
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )
    } else if (commitment.status == CommitmentStatus.COMPLETED) {
        SecondaryButton(
            text = "Say how it went",
            onClick = onEndorse,
            enabled = !submitting,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )
    }
    Spacer(Modifier.height(spacing.sm))
}

/**
 * Saying how somebody's help went.
 *
 * Reachable only from a commitment the organiser has already confirmed, which is what
 * stops this from being a "like" on a profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EndorseScreen(
    submitting: Boolean,
    refusal: String?,
    onSubmit: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var note by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How it went") },
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

            SectionHeader(
                title = "A sentence about this occasion",
                subtitle = "Not a testimonial. Something specific that happened.",
            )
            LabelledField(
                label = "What you would tell another organiser",
                value = note,
                onValueChange = { note = it.take(240) },
                helper = "${240 - note.length} characters left.",
                singleLine = false,
                minLines = 4,
                enabled = !submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            PrimaryButton(
                text = "Record this",
                onClick = { onSubmit(note.trim().ifBlank { null }) },
                enabled = !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.sm))
            PrivacyNote(
                text = "This goes to the member's own record. It is not published on a " +
                    "profile, it is not counted into a score anyone can see, and nobody is " +
                    "ranked by it.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}
