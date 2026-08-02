package org.fisabilillah.app.ui.screens.trust

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
import androidx.compose.ui.text.input.KeyboardType
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
import org.fisabilillah.core.domain.MyVerificationUseCase
import org.fisabilillah.core.model.Qualification
import org.fisabilillah.core.model.QualificationId
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.VerificationMethod
import org.fisabilillah.core.model.VerificationRequest
import org.fisabilillah.core.model.VerificationRequestId
import org.fisabilillah.core.policy.ValidationError

/**
 * What the platform has checked about you, and how to ask it to check more.
 *
 * The screen leads with what each level *does not* mean, taken verbatim from
 * [VerificationLevel.whatItDoesNotMean]. That is the whole point of the trust design: a
 * badge that a reader interprets as "this person is safe" is worse than no badge, and the
 * only defence is to say what it actually covers next to it, every time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyVerificationScreen(
    state: MyVerificationUseCase.State?,
    loading: Boolean,
    refusal: String?,
    onRequest: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verification") },
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

            val current = state?.currentLevel ?: VerificationLevel.NONE
            ContentCard {
                Text(
                    text = current.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = "What this does not mean: ${current.whatItDoesNotMean}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state != null && state.requests.isNotEmpty()) {
                SectionHeader(title = "Your requests")
                for (request in state.requests) {
                    ContentCard {
                        Text(
                            text = request.requestedLevel.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        DetailRow(label = "State", value = request.state.displayName)
                        DetailRow(label = "Method", value = request.method.displayName)
                        if (request.decisionNote != null) {
                            Spacer(Modifier.height(spacing.xxs))
                            Text(
                                text = request.decisionNote!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.sm))

            if (state?.hasOpenRequest == true) {
                PrivacyNote(
                    text = "You have a request being looked at. We will come back to you on " +
                        "that one before you send another.",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            } else if (!loading) {
                PrimaryButton(
                    text = "Ask for something to be verified",
                    onClick = onRequest,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }

            SectionDivider()

            SectionHeader(title = "What the levels mean")
            for (level in VerificationLevel.entries.filter { it != VerificationLevel.NONE }) {
                ContentCard {
                    Text(
                        text = level.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xxs))
                    Text(
                        text = level.whatItDoesNotMean,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/** Asking for a level. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequestVerificationScreen(
    errors: List<ValidationError>,
    refusal: String?,
    submitting: Boolean,
    onSubmit: (VerificationLevel, VerificationMethod, List<String>, String?) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var level by remember { mutableStateOf(VerificationLevel.IDENTITY_VERIFIED) }
    var method by remember { mutableStateOf(VerificationMethod.DOCUMENT_PROVIDER) }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask to be verified") },
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
                title = "What would you like checked?",
                subtitle = "Each one is a different thing, and none of them is a character reference.",
            )
            for (option in VerificationLevel.entries.filter { it != VerificationLevel.NONE }) {
                ChoiceRow(
                    title = option.displayName,
                    description = option.whatItDoesNotMean,
                    selected = level == option,
                    onSelect = { level = option },
                )
            }
            errorFor(errors, "requestedLevel")?.let {
                RefusalNotice(message = it)
            }

            SectionHeader(
                title = "How",
                subtitle = "A method can only ever confirm so much, and the platform holds " +
                    "you to that ceiling rather than to what you asked for.",
            )
            for (option in VerificationMethod.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = "Confirms at most: ${option.ceiling.displayName.lowercase()}.",
                    selected = method == option,
                    onSelect = { method = option },
                )
            }
            errorFor(errors, "method")?.let { RefusalNotice(message = it) }

            SectionHeader(title = "Your evidence")
            LabelledField(
                label = "Document reference",
                value = reference,
                onValueChange = { reference = it },
                helper = "In this build a reference stands in for an upload. The document " +
                    "itself is stored privately and only a reviewer can open it.",
                error = errorFor(errors, "evidenceRefs"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "Anything you want the reviewer to know",
                value = note,
                onValueChange = { note = it },
                singleLine = false,
                minLines = 3,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            PrimaryButton(
                text = "Send this for review",
                onClick = {
                    onSubmit(
                        level,
                        method,
                        listOfNotNull(reference.trim().ifBlank { null }),
                        note.trim().ifBlank { null },
                    )
                },
                enabled = !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.sm))
            DisclaimerCard(
                title = "A badge is not a recommendation",
                text = "Verification confirms a specific fact and nothing else. It is not a " +
                    "statement about someone's character, their religious correctness, or " +
                    "whether their advice is sound — and the app will keep saying so next " +
                    "to every badge it shows.",
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/** Claiming a credential, and seeing what happened to earlier claims. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyQualificationsScreen(
    qualifications: List<Qualification>,
    errors: List<ValidationError>,
    refusal: String?,
    submitting: Boolean,
    onSubmit: (title: String, issuingBody: String, year: Int?) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Qualifications") },
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
                title = "A claim is shown as a claim",
                text = "Until somebody qualified has checked it, what you enter here is " +
                    "displayed as something you have said about yourself — because that is " +
                    "what it is. Confirmation is done by a listed scholar or a moderator, " +
                    "not by the platform taking your word for it.",
            )

            if (qualifications.isNotEmpty()) {
                SectionHeader(title = "What you have claimed")
                for (item in qualifications) {
                    ContentCard {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        DetailRow(label = "Issued by", value = item.issuingBody)
                        DetailRow(label = "State", value = item.reviewState.displayName)
                        if (item.reviewerNote != null) {
                            Spacer(Modifier.height(spacing.xxs))
                            Text(
                                text = item.reviewerNote!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionHeader(title = "Add one")
            LabelledField(
                label = "What is it called",
                value = title,
                onValueChange = { title = it },
                error = errorFor(errors, "title"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "Who issued it",
                value = body,
                onValueChange = { body = it },
                helper = "A reviewer needs somewhere to check.",
                error = errorFor(errors, "issuingBody"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "Year",
                value = year,
                onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                keyboardType = KeyboardType.Number,
                error = errorFor(errors, "issuedYear"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            PrimaryButton(
                text = "Submit for review",
                onClick = { onSubmit(title, body, year.toIntOrNull()) },
                enabled = !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/**
 * The reviewer's side of both queues.
 *
 * One screen rather than two, because they are the same job done by overlapping people —
 * a moderator sees both, a scholar sees only the qualifications — and splitting them would
 * mean a scholar hunting for the one they can act on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrustReviewScreen(
    verifications: List<VerificationRequest>,
    qualifications: List<Qualification>,
    canReviewVerifications: Boolean,
    refusal: String?,
    submitting: Boolean,
    onDecideVerification: (VerificationRequestId, Boolean, String) -> Unit,
    onDecideQualification: (QualificationId, Boolean, String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trust review") },
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

            if (canReviewVerifications) {
                SectionHeader(
                    title = "Identity requests",
                    subtitle = "You are granting a fact, not an opinion about a person.",
                )
                if (verifications.isEmpty()) {
                    EmptyState(title = "Nothing waiting", body = "No identity requests to review.")
                } else {
                    for (request in verifications) {
                        DecisionCard(
                            heading = request.requestedLevel.displayName,
                            facts = listOf(
                                "Method" to request.method.displayName,
                                "Ceiling" to request.method.ceiling.displayName,
                                "Evidence" to "${request.evidenceRefs.size} attached",
                            ),
                            note = request.note,
                            submitting = submitting,
                            onApprove = { onDecideVerification(request.id, true, it) },
                            onReject = { onDecideVerification(request.id, false, it) },
                        )
                    }
                }
                SectionDivider()
            }

            SectionHeader(
                title = "Qualification claims",
                subtitle = "Whether a credential is real is a question for somebody who " +
                    "knows the field.",
            )
            if (qualifications.isEmpty()) {
                EmptyState(title = "Nothing waiting", body = "No qualification claims to review.")
            } else {
                for (claim in qualifications) {
                    DecisionCard(
                        heading = claim.title,
                        facts = listOf(
                            "Issued by" to claim.issuingBody,
                            "Year" to (claim.issuedYear?.toString() ?: "Not given"),
                        ),
                        note = null,
                        submitting = submitting,
                        onApprove = { onDecideQualification(claim.id, true, it) },
                        onReject = { onDecideQualification(claim.id, false, it) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun DecisionCard(
    heading: String,
    facts: List<Pair<String, String>>,
    note: String?,
    submitting: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var reason by remember { mutableStateOf("") }

    ContentCard {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.xxs))
        for ((label, value) in facts) DetailRow(label = label, value = value)
        if (note != null) {
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    LabelledField(
        label = "What you checked",
        value = reason,
        onValueChange = { reason = it },
        helper = "Shown to the member in full.",
        singleLine = false,
        minLines = 3,
        enabled = !submitting,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )
    PrimaryButton(
        text = "Confirm",
        onClick = { onApprove(reason) },
        enabled = reason.isNotBlank() && !submitting,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )
    Spacer(Modifier.height(spacing.xs))
    SecondaryButton(
        text = "Do not confirm",
        onClick = { onReject(reason) },
        enabled = reason.isNotBlank() && !submitting,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )
    Spacer(Modifier.height(spacing.md))
}

private fun errorFor(errors: List<ValidationError>, field: String): String? =
    errors.firstOrNull { it.field == field }?.message
