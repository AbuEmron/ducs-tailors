package org.fisabilillah.app.ui.screens.safety

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.TrustedContactsState
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.IntroductionId
import org.fisabilillah.core.model.IntroductionLimits
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactRole
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.policy.IntroductionPolicy

/**
 * Configuring the formal family introduction process.
 *
 * The feature is off, and stays off until a person has both switched it on and nominated
 * someone to receive introductions on their behalf. That second condition is not an
 * implementation detail to be surfaced as a validation error at the wrong moment — a
 * request forwarded to nobody is precisely the failure this whole design exists to prevent
 * — so the switch is explained and held closed rather than allowed to fail later.
 *
 * The platform makes no ruling about anyone's circumstances. Whether an imam or another
 * intermediary may act where a wali is unavailable differs by school and by situation, and
 * the app says so instead of deciding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WaliSettingsScreen(
    state: TrustedContactsState,
    onSaveSettings: (FormalIntroductionSettings) -> Unit,
    onOpenIntroduction: (IntroductionId) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Formal introductions") },
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
        val settings = state.settings
        if (state.loading || settings == null) {
            Column(Modifier.padding(padding)) { LoadingState(label = "Loading your settings") }
            return@Scaffold
        }

        val eligible = state.contacts.filter { it.active && isEligibleRole(it, settings) }
        val hasSomeoneToReceive = eligible.isNotEmpty()

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            ContentCard {
                Text(
                    text = "What this is",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "A structured, guardian-led way for someone to express serious " +
                        "interest in marriage. There is no browsing, no availability badge, " +
                        "nothing searchable, and no way for anyone to tell whether you have " +
                        "this switched on. Someone reaches this only by submitting a long " +
                        "written form, and the only route from that form to a conversation " +
                        "runs through the person you nominate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DisclaimerCard(
                title = "The platform makes no religious ruling",
                text = IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE,
            )

            if (state.refusal != null) {
                RefusalNotice(message = state.refusal)
            }

            SectionDivider()

            // ── The switch ───────────────────────────────────────────────────
            SectionHeader(title = "Receiving introductions")

            SafeguardToggle(
                title = "Accept formal introductions",
                description = "While this is off, nobody can submit one to you. They are " +
                    "told only that you are not receiving introductions — never that you " +
                    "have it switched off, never that you declined, and never anything " +
                    "that would let them work out which.",
                checked = settings.enabled,
                onCheckedChange = { value -> onSaveSettings(settings.copy(enabled = value)) },
                lockedReason = if (hasSomeoneToReceive) {
                    null
                } else {
                    "You need to nominate a wali or a trusted intermediary before this can " +
                        "be switched on. This is not a formality: introductions are " +
                        "forwarded to that person, and one forwarded to nobody would sit " +
                        "unanswered while a sender waited. Add someone under trusted " +
                        "contacts first."
                },
            )

            if (!hasSomeoneToReceive) {
                PrivacyNote(
                    text = "A trusted contact with the wali role qualifies. So does one " +
                        "with the intermediary role, if you have allowed an intermediary " +
                        "to act in place of a wali below.",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }

            // ── Who receives them ────────────────────────────────────────────
            if (hasSomeoneToReceive) {
                SectionHeader(
                    title = "Who receives them",
                    subtitle = "Their contact details are never shown to a sender. The " +
                        "platform approaches them; nobody is handed their number.",
                )
                for (contact in eligible) {
                    ChoiceRow(
                        title = contact.name,
                        description = "${contact.relationship.displayName} — " +
                            "${contact.role.displayName}. " +
                            "Confirmation: ${contact.verificationState.displayName}.",
                        selected = settings.guardianContactId == contact.id,
                        onSelect = {
                            onSaveSettings(settings.copy(guardianContactId = contact.id))
                        },
                    )
                }
            }

            SectionDivider()

            // ── Order of review ──────────────────────────────────────────────
            SectionHeader(title = "Who sees a request first")

            SafeguardToggle(
                title = "I want to see requests before my wali does",
                description = "On: a request comes to you first, and nothing reaches your " +
                    "wali unless you allow it through. Off: everything goes straight to " +
                    "your wali and you never see a request they did not pass on. Some " +
                    "people want to screen; others want everything to go to their wali " +
                    "first. Both are legitimate, and the platform has no view on which you " +
                    "should pick.",
                checked = settings.recipientReviewsFirst,
                onCheckedChange = { value ->
                    onSaveSettings(settings.copy(recipientReviewsFirst = value))
                },
            )

            SafeguardToggle(
                title = "My guardian stays in every exchange",
                description = "Any conversation that comes out of an introduction includes " +
                    "your guardian throughout. Note that the guardian is part of the " +
                    "introduction conversation whatever you choose here — this feature does " +
                    "not offer a private thread, and switching this off does not create one.",
                checked = settings.guardianMustBeIncludedThroughout,
                onCheckedChange = { value ->
                    onSaveSettings(settings.copy(guardianMustBeIncludedThroughout = value))
                },
            )

            SectionDivider()

            // ── Intermediary ─────────────────────────────────────────────────
            SectionHeader(title = "If you have no wali available")

            ContentCard {
                Text(
                    text = "When this applies",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Some people have no wali who is able or willing to act — a " +
                        "convert whose family are not Muslim, someone estranged from their " +
                        "father, someone whose family are in another country. In those " +
                        "situations an imam or another trusted community figure is often " +
                        "asked to act instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Whether that is correct in your circumstances is not something " +
                        "this platform will tell you. It differs by school and by " +
                        "situation, and the only person who can answer it is a qualified " +
                        "scholar who knows you. The setting below is a technical " +
                        "arrangement, not a religious opinion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SafeguardToggle(
                title = "Allow an imam or intermediary to act in place of a wali",
                description = "A trusted contact with the intermediary role becomes " +
                    "eligible to receive introductions on your behalf.",
                checked = settings.allowIntermediaryInsteadOfWali,
                onCheckedChange = { value ->
                    onSaveSettings(settings.copy(allowIntermediaryInsteadOfWali = value))
                },
            )

            SectionDivider()

            // ── Who may submit ───────────────────────────────────────────────
            SectionHeader(
                title = "Who may submit at all",
                subtitle = "Checked before a request is ever created. A sender who does " +
                    "not qualify is told only that you are not receiving introductions.",
            )

            ScopeChooser(
                title = "Accept requests from",
                description = "Anyone outside this group cannot submit.",
                current = settings.acceptRequestsFrom,
                onSelect = { scope ->
                    onSaveSettings(settings.copy(acceptRequestsFrom = scope))
                },
            )

            SectionHeader(title = "Minimum verification of a sender")
            for (level in VerificationLevel.entries) {
                ChoiceRow(
                    title = level.displayName,
                    description = level.whatItDoesNotMean,
                    selected = settings.minimumVerification == level,
                    onSelect = {
                        onSaveSettings(settings.copy(minimumVerification = level))
                    },
                )
            }

            SafeguardToggle(
                title = "Only people from a masjid or organisation I belong to",
                description = "A sender with nothing in common with you cannot submit.",
                checked = settings.requireSharedOrganization,
                onCheckedChange = { value ->
                    onSaveSettings(settings.copy(requireSharedOrganization = value))
                },
            )

            MaxOpenRequestsField(
                current = settings.maxOpenRequests,
                onChange = { value -> onSaveSettings(settings.copy(maxOpenRequests = value)) },
            )

            PrivacyNote(
                text = "Limits also apply to senders regardless of your settings: at most " +
                    "${IntroductionLimits.MAX_CONCURRENT_OUTGOING} open at once, at most " +
                    "${IntroductionLimits.MAX_PER_30_DAYS} in any thirty days, and never " +
                    "more than one to the same person. A request left unanswered for " +
                    "${IntroductionLimits.LAPSE_AFTER_DAYS} days closes itself. Silence is " +
                    "a complete answer and is treated as one.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionDivider()

            // ── The queues ───────────────────────────────────────────────────
            SectionHeader(
                title = "Introductions to you",
                subtitle = "Nobody is told how many of these there are, including senders.",
            )

            if (state.incoming.isEmpty()) {
                EmptyState(title = "Nothing waiting", body = "There is nothing to review.")
            } else {
                for (request in state.incoming) {
                    IncomingCard(request = request, onOpen = onOpenIntroduction)
                }
            }

            SectionHeader(title = "Introductions you have sent")

            if (state.outgoing.isEmpty()) {
                EmptyState(
                    title = "You have not sent any",
                    body = "An introduction is submitted from a member's profile, and only " +
                        "when they are receiving them.",
                )
            } else {
                for (request in state.outgoing) {
                    OutgoingCard(request = request, onOpen = onOpenIntroduction)
                }
            }

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

private fun isEligibleRole(
    contact: TrustedContact,
    settings: FormalIntroductionSettings,
): Boolean = when (contact.role) {
    TrustedContactRole.WALI -> true
    TrustedContactRole.INTERMEDIARY -> settings.allowIntermediaryInsteadOfWali
    else -> false
}

@Composable
private fun IncomingCard(
    request: FormalIntroductionRequest,
    onOpen: (IntroductionId) -> Unit,
) {
    ContentCard(
        onClick = { onOpen(request.id) },
        contentDescription = "An introduction, ${request.status.displayName}. Open to review.",
    ) {
        Text(
            text = "A formal introduction",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DetailRow(label = "Status", value = request.status.displayName)
        DetailRow(
            label = "Intention",
            value = request.form.statedIntention.take(160),
        )
    }
}

@Composable
private fun OutgoingCard(
    request: FormalIntroductionRequest,
    onOpen: (IntroductionId) -> Unit,
) {
    ContentCard(
        onClick = { onOpen(request.id) },
        contentDescription = "An introduction you sent. " +
            IntroductionPolicy.senderFacingOutcome(request.status),
    ) {
        Text(
            text = "An introduction you sent",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = IntroductionPolicy.senderFacingOutcome(request.status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The model refuses anything outside one to ten, so the value is only pushed when it fits. */
@Composable
private fun MaxOpenRequestsField(
    current: Int,
    onChange: (Int) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var text by remember(current) { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()

    LabelledField(
        label = "Most open requests at once",
        value = text,
        onValueChange = { entry ->
            text = entry.filter { it.isDigit() }.take(2)
            val value = text.toIntOrNull()
            if (value != null && value in 1..10) onChange(value)
        },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        helper = "Between 1 and 10. Once you are at this number, further senders are told " +
            "only that you are not receiving introductions at the moment.",
        error = if (parsed != null && parsed in 1..10) null else "Enter a number between 1 and 10.",
        keyboardType = KeyboardType.Number,
    )
}
