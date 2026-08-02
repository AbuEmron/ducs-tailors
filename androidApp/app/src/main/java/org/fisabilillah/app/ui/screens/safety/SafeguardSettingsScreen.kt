package org.fisabilillah.app.ui.screens.safety

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.datetime.LocalTime
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.SafeguardEditorState
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.CrossGenderConversationStructure
import org.fisabilillah.core.model.LocationPrecision
import org.fisabilillah.core.model.ModeratorPresenceRule
import org.fisabilillah.core.model.NameVisibility
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VerificationLevel

/**
 * The safeguards screen.
 *
 * This is the most important settings screen in the application, and it is written on the
 * assumption that a grid of thirty switches labelled only with their field names is not a
 * real choice for anybody. Every control states its consequence in a sentence, because the
 * question a person is actually asking is never "what does `crossGenderStructure` mean" —
 * it is "if I change this, what happens to me".
 *
 * Two things here are load-bearing rather than decorative.
 *
 * The first is the loosening notice. Tightening a safeguard is safe by definition;
 * loosening one is the moment a person becomes reachable in a way they were not before, so
 * every loosened field is spelled out in plain words *before* the save button, not
 * afterwards in a toast.
 *
 * The second is the statement attached to the presets. A named preset called "Family and
 * wali guided" sitting above one called "Community service only" reads, unless you say
 * otherwise, as a ranking of religiosity. It is not one. They describe circumstances.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SafeguardSettingsScreen(
    state: SafeguardEditorState,
    onEdit: ((UserSafeguards) -> UserSafeguards) -> Unit,
    onApplyPreset: (SafeguardPresetName) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safeguards") },
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
        val draft = state.draft
        if (draft == null) {
            Column(Modifier.padding(padding)) { LoadingState(label = "Loading your safeguards") }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            ContentCard {
                Text(
                    text = "You can make any of these stricter whenever you like, and the " +
                        "change takes effect at once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Nothing here is applied retrospectively to conversations that " +
                        "already exist. If you want a conversation you are already in to " +
                        "have a guardian or a moderator present, you can add one from the " +
                        "conversation itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.tightenedByOrganization) {
                OrganizationFloorNote()
            }

            val refusal = state.refusal
            if (refusal != null) {
                RefusalNotice(message = refusal)
            }

            // ── Presets ──────────────────────────────────────────────────────
            SectionHeader(
                title = "Starting points",
                subtitle = "Choosing one replaces every setting below. You can then change " +
                    "any of them individually.",
            )

            ContentCard {
                Text(
                    text = "No preset here is more religious than another.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "They describe circumstances, not levels of piety. A sister " +
                        "using the community service preset has not chosen a lesser option " +
                        "than one using the wali-guided preset, and the platform will never " +
                        "present them as a ranking or show anyone else which you picked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (preset in SafeguardPresetName.selectable) {
                ChoiceRow(
                    title = preset.displayName,
                    description = preset.summary,
                    selected = draft.presetName == preset,
                    onSelect = { onApplyPreset(preset) },
                )
            }

            SectionDivider()

            // ── Discovery ────────────────────────────────────────────────────
            SectionHeader(
                title = "Discovery",
                subtitle = "Whether you appear to someone who has not met you.",
            )

            ScopeChooser(
                title = "Who can find my profile",
                description = "People outside this group will not see you in any search or " +
                    "suggestion.",
                current = draft.profileDiscoverableBy,
                onSelect = { scope -> onEdit { it.copy(profileDiscoverableBy = scope) } },
            )

            SectionHeader(title = "My name")
            for (option in NameVisibility.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = when (option) {
                        NameVisibility.REAL_NAME_PUBLIC ->
                            "Anyone who can see your profile sees your real name."
                        NameVisibility.REAL_NAME_TO_VERIFIED ->
                            "Only members whose identity has been checked see your real name."
                        NameVisibility.REAL_NAME_TO_ORGANIZERS ->
                            "Only the organiser of something you have actually committed " +
                                "to sees your real name."
                        NameVisibility.DISPLAY_NAME_ONLY ->
                            "Nobody sees your real name through this platform."
                    },
                    selected = draft.nameVisibility == option,
                    onSelect = { onEdit { it.copy(nameVisibility = option) } },
                )
            }

            SectionHeader(title = "My location")
            for (option in LocationPrecision.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = when (option) {
                        LocationPrecision.EXACT ->
                            "Not recommended. Your exact location is otherwise released " +
                                "only by a separate decision you make each time."
                        LocationPrecision.NEIGHBOURHOOD ->
                            "Enough for someone to judge whether you are nearby."
                        LocationPrecision.CITY -> "Your city and nothing finer."
                        LocationPrecision.REGION -> "Your region and nothing finer."
                        LocationPrecision.HIDDEN ->
                            "No location at all. You will not appear in distance-based " +
                                "searches."
                    },
                    selected = draft.locationPrecision == option,
                    onSelect = { onEdit { it.copy(locationPrecision = option) } },
                )
            }

            ScopeChooser(
                title = "Who can see my profile image",
                description = "Applies only if you have chosen to have one at all.",
                current = draft.profileImageVisibleTo,
                onSelect = { scope -> onEdit { it.copy(profileImageVisibleTo = scope) } },
            )

            SectionDivider()

            // ── Who may contact me ───────────────────────────────────────────
            SectionHeader(
                title = "Who may contact me",
                subtitle = "Who is allowed to open a conversation with you at all.",
            )

            ScopeChooser(
                title = "Who can start a conversation with me",
                description = "Anyone outside this group is refused before a message is " +
                    "ever written, and is not told why.",
                current = draft.contactableBy,
                onSelect = { scope -> onEdit { it.copy(contactableBy = scope) } },
            )

            SectionHeader(title = "Minimum verification to contact me")
            for (level in VerificationLevel.entries) {
                ChoiceRow(
                    title = level.displayName,
                    description = level.whatItDoesNotMean,
                    selected = draft.minimumVerificationToContactMe == level,
                    onSelect = { onEdit { it.copy(minimumVerificationToContactMe = level) } },
                )
            }

            SafeguardToggle(
                title = "Only people in my masjid or organisations may contact me",
                description = "Applies on top of everything above. Someone with no " +
                    "organisation in common with you cannot reach you at all.",
                checked = draft.onlyMyOrganizationsMayContactMe,
                onCheckedChange = { value ->
                    onEdit { it.copy(onlyMyOrganizationsMayContactMe = value) }
                },
            )

            SafeguardToggle(
                title = "Require a written explanation as well",
                description = "Every conversation already carries a structured purpose. " +
                    "This additionally requires the sender to explain themselves in their " +
                    "own words before you are shown anything.",
                checked = draft.requireWrittenPurposeStatement,
                onCheckedChange = { value ->
                    onEdit { it.copy(requireWrittenPurposeStatement = value) }
                },
            )

            SectionHeader(
                title = "Kinds of request I do not want",
                subtitle = "Switching one on means those requests are refused before you " +
                    "see them.",
            )
            for (kind in ContactPurposeKind.selectable) {
                SafeguardToggle(
                    title = "Decline: ${kind.displayName}",
                    description = kind.description,
                    checked = kind in draft.declinedPurposes,
                    onCheckedChange = { declined ->
                        onEdit {
                            it.copy(
                                declinedPurposes = if (declined) {
                                    it.declinedPurposes + kind
                                } else {
                                    it.declinedPurposes - kind
                                },
                            )
                        }
                    },
                )
            }

            SectionDivider()

            // ── Cross-gender conversations ───────────────────────────────────
            SectionHeader(
                title = "Cross-gender conversations",
                subtitle = "How a conversation with someone of the opposite gender is " +
                    "arranged, if you allow one at all.",
            )

            for (option in CrossGenderConversationStructure.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = when (option) {
                        CrossGenderConversationStructure.DIRECT_WITH_PURPOSE ->
                            "One to one. The conversation still carries a stated purpose " +
                                "and is still recorded."
                        CrossGenderConversationStructure.GROUP_CONTEXT_ONLY ->
                            "No private thread. Anything said happens inside an existing " +
                                "group, project or class conversation."
                        CrossGenderConversationStructure.THIRD_PARTY_PRESENT ->
                            "A third party you have nominated is added when the " +
                                "conversation is created and can read all of it."
                        CrossGenderConversationStructure.GUARDIAN_PRESENT ->
                            "Your wali or trusted contact is added when the conversation " +
                                "is created and can read all of it."
                    },
                    selected = draft.crossGenderStructure == option,
                    onSelect = { onEdit { it.copy(crossGenderStructure = option) } },
                )
            }

            SectionHeader(title = "When a moderator must be present")
            for (option in ModeratorPresenceRule.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = when (option) {
                        ModeratorPresenceRule.NEVER ->
                            "No moderator joins unless something is reported."
                        ModeratorPresenceRule.CROSS_GENDER_ONLY ->
                            "A member of the safety team is in every conversation you have " +
                                "with the opposite gender and can read all of it."
                        ModeratorPresenceRule.ALWAYS ->
                            "A member of the safety team is in every conversation you are " +
                                "part of and can read all of it."
                    },
                    selected = draft.moderatorPresence == option,
                    onSelect = { onEdit { it.copy(moderatorPresence = option) } },
                )
            }

            SafeguardToggle(
                title = "Keep my conversations inside groups and projects",
                description = "No one can open a one-to-one thread with you. Everything " +
                    "happens where other people can see it.",
                checked = draft.requireGroupContext,
                onCheckedChange = { value -> onEdit { it.copy(requireGroupContext = value) } },
            )

            SafeguardToggle(
                title = "Always require a third party in my conversations",
                description = "Someone you have nominated is added to any new conversation " +
                    "and can read all of it.",
                checked = draft.requireThirdParty,
                onCheckedChange = { value -> onEdit { it.copy(requireThirdParty = value) } },
            )

            SectionHeader(
                title = "Copy my trusted contact in on these",
                subtitle = "Your wali or trusted contact is added to the conversation when " +
                    "it is created.",
            )
            for (kind in ContactPurposeKind.selectable) {
                SafeguardToggle(
                    title = kind.displayName,
                    description = kind.description,
                    checked = kind in draft.guardianCopiedOnPurposes,
                    onCheckedChange = { copied ->
                        onEdit {
                            it.copy(
                                guardianCopiedOnPurposes = if (copied) {
                                    it.guardianCopiedOnPurposes + kind
                                } else {
                                    it.guardianCopiedOnPurposes - kind
                                },
                            )
                        }
                    },
                )
            }

            SectionDivider()

            // ── Calls and meetings ───────────────────────────────────────────
            SectionHeader(
                title = "Calls and meetings",
                subtitle = "Voice, video, and meeting in person.",
            )

            ScopeChooser(
                title = "Who may voice call me",
                description = "Anyone outside this group is not offered the option at all.",
                current = draft.voiceCallsAllowedFrom,
                onSelect = { scope -> onEdit { it.copy(voiceCallsAllowedFrom = scope) } },
            )

            ScopeChooser(
                title = "Who may video call me",
                description = "Anyone outside this group is not offered the option at all.",
                current = draft.videoCallsAllowedFrom,
                onSelect = { scope -> onEdit { it.copy(videoCallsAllowedFrom = scope) } },
            )

            ScopeChooser(
                title = "Who may arrange a one-to-one meeting with me",
                description = "This governs what the app will help arrange. It cannot " +
                    "govern what happens away from it.",
                current = draft.oneToOneMeetingsAllowedFrom,
                onSelect = { scope ->
                    onEdit { it.copy(oneToOneMeetingsAllowedFrom = scope) }
                },
            )

            SafeguardToggle(
                title = "Meetings should be in public places",
                description = "Any meeting arranged through the app carries this as a " +
                    "stated condition that everyone present can see.",
                checked = draft.meetingsMustBeInPublicPlaces,
                onCheckedChange = { value ->
                    onEdit { it.copy(meetingsMustBeInPublicPlaces = value) }
                },
            )

            SafeguardToggle(
                title = "Meetings require a third party",
                description = "A meeting arranged through the app states that someone else " +
                    "will be there.",
                checked = draft.meetingsRequireThirdParty,
                onCheckedChange = { value ->
                    onEdit { it.copy(meetingsRequireThirdParty = value) }
                },
            )

            SectionDivider()

            // ── Lifecycle ────────────────────────────────────────────────────
            SectionHeader(
                title = "Conversation lifecycle",
                subtitle = "What happens to a conversation once the work it existed for is " +
                    "finished.",
            )

            SafeguardToggle(
                title = "Archive conversations once the work is complete",
                description = "Archiving hides the conversation and stops new messages. It " +
                    "never destroys anything needed for a report.",
                checked = draft.autoArchiveAfterCompletion,
                onCheckedChange = { value ->
                    onEdit { it.copy(autoArchiveAfterCompletion = value) }
                },
            )

            ArchiveDaysField(
                current = draft.autoArchiveAfterDays,
                onChange = { days -> onEdit { it.copy(autoArchiveAfterDays = days) } },
            )

            SectionDivider()

            // ── Formal introductions ─────────────────────────────────────────
            SectionHeader(
                title = "Formal introductions",
                subtitle = "The wali-led marriage introduction process. Off unless you " +
                    "switch it on.",
            )

            SafeguardToggle(
                title = "Accept formal introductions",
                description = "Nobody can submit a marriage introduction to you while this " +
                    "is off, and they are told nothing about why. Turning it on also " +
                    "requires you to nominate someone to receive them.",
                checked = draft.acceptFormalIntroductions,
                onCheckedChange = { value ->
                    onEdit { it.copy(acceptFormalIntroductions = value) }
                },
            )

            SafeguardToggle(
                title = "Introductions go straight to my guardian",
                description = "When this is on, you do not see a request before your wali " +
                    "does. Some families prefer this and it is a legitimate choice; so is " +
                    "the opposite.",
                checked = draft.introductionsGoDirectlyToGuardian,
                onCheckedChange = { value ->
                    onEdit { it.copy(introductionsGoDirectlyToGuardian = value) }
                },
            )

            SectionDivider()

            // ── Quiet hours ──────────────────────────────────────────────────
            SectionHeader(
                title = "Quiet hours",
                subtitle = "A window each day in which stricter rules apply.",
            )

            SafeguardToggle(
                title = "Use quiet hours",
                description = "Currently ${draft.quietHours.start} to ${draft.quietHours.end}.",
                checked = draft.quietHours.enabled,
                onCheckedChange = { value ->
                    onEdit { it.copy(quietHours = it.quietHours.copy(enabled = value)) }
                },
            )

            HourField(
                label = "Quiet hours start (hour, 0 to 23)",
                current = draft.quietHours.start.hour,
                onChange = { hour ->
                    onEdit {
                        it.copy(quietHours = it.quietHours.copy(start = LocalTime(hour, 0)))
                    }
                },
            )

            HourField(
                label = "Quiet hours end (hour, 0 to 23)",
                current = draft.quietHours.end.hour,
                onChange = { hour ->
                    onEdit {
                        it.copy(quietHours = it.quietHours.copy(end = LocalTime(hour, 0)))
                    }
                },
            )

            SafeguardToggle(
                title = "No new conversations during quiet hours",
                description = "Conversations you are already in continue as normal.",
                checked = draft.quietHours.blockNewConversations,
                onCheckedChange = { value ->
                    onEdit {
                        it.copy(quietHours = it.quietHours.copy(blockNewConversations = value))
                    }
                },
            )

            SafeguardToggle(
                title = "No calls during quiet hours",
                description = "Calls are not offered to anyone during the window.",
                checked = draft.quietHours.blockCalls,
                onCheckedChange = { value ->
                    onEdit { it.copy(quietHours = it.quietHours.copy(blockCalls = value)) }
                },
            )

            SectionDivider()

            // ── Save ─────────────────────────────────────────────────────────
            LoosenedFieldsNotice(state.loosenedFields)

            PrimaryButton(
                text = "Save these safeguards",
                onClick = onSave,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = state.hasUnsavedChanges,
                loading = state.saving,
            )

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "Changing your safeguards is written to your audit log, so you can " +
                    "always see when something changed and from what device.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

// ── Shared pieces, also used by the privacy controls screen ──────────────────

/**
 * A chooser over [AudienceScope].
 *
 * The scopes are ordered from most open to most closed by their own `strictness`, and the
 * list is rendered in that order so that moving down the screen always means closing a
 * door rather than opening one.
 */
@Composable
internal fun ScopeChooser(
    title: String,
    description: String,
    current: AudienceScope,
    onSelect: (AudienceScope) -> Unit,
) {
    SectionHeader(title = title, subtitle = description)
    for (scope in AudienceScope.entries.sortedBy { it.strictness }) {
        ChoiceRow(
            title = scope.displayName,
            description = consequenceOf(scope),
            selected = current == scope,
            onSelect = { onSelect(scope) },
        )
    }
}

internal fun consequenceOf(scope: AudienceScope): String = when (scope) {
    AudienceScope.EVERYONE ->
        "No restriction. Any member of the platform qualifies."
    AudienceScope.VERIFIED_ONLY ->
        "Only members whose identity has been checked by our verification provider."
    AudienceScope.MY_ORGANIZATIONS_ONLY ->
        "Only people who share a masjid or organisation with you."
    AudienceScope.SAME_GENDER_ONLY ->
        "Only members of the same gender as you."
    AudienceScope.SAME_GENDER_VERIFIED_ONLY ->
        "Only verified members of the same gender as you."
    AudienceScope.NOBODY ->
        "Nobody at all. You would reach out yourself when you choose to."
}

/**
 * Everything the pending save would open up.
 *
 * Shown before the save button rather than after it. A person who is about to become
 * reachable in a new way should be told in the sentence they can act on, not in a
 * confirmation they read once the change has already happened.
 */
@Composable
internal fun LoosenedFieldsNotice(fields: List<String>) {
    if (fields.isEmpty()) return
    val spacing = FiSabilillahTheme.spacing
    val safeguard = FiSabilillahTheme.safeguard

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        shape = MaterialTheme.shapes.small,
        color = safeguard.cautionContainer,
        contentColor = safeguard.cautionOnContainer,
    ) {
        Column(Modifier.padding(spacing.md)) {
            Text(
                text = "Saving this would open the following up",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(spacing.xs))
            for (field in fields) {
                Text(
                    text = "•  $field",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = spacing.xxs),
                )
            }
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "Nothing here is wrong to choose. You are being shown it because a " +
                    "change in this direction is one you should make on purpose rather " +
                    "than by accident.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Shown when a masjid or organisation has imposed a floor on top of a person's own settings. */
@Composable
internal fun OrganizationFloorNote() {
    val spacing = FiSabilillahTheme.spacing
    PrivacyNote(
        text = "A masjid or organisation you belong to has added stricter rules on top of " +
            "your own, so what actually applies to you inside their spaces may be tighter " +
            "than what you see here. An organisation can only ever tighten a safeguard. It " +
            "can never loosen one you have chosen, and it cannot switch anything back on " +
            "that you have switched off.",
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    )
}

// ── Small numeric fields ─────────────────────────────────────────────────────

/**
 * The archive window.
 *
 * The model refuses anything outside one to three hundred and sixty-five days, so the
 * value is only pushed upstream once it parses inside that range. The alternative — letting
 * an empty field reach a `require` block — is a crash on a settings screen.
 */
@Composable
private fun ArchiveDaysField(
    current: Int,
    onChange: (Int) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var text by remember(current) { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1..365

    LabelledField(
        label = "Archive after this many days",
        value = text,
        onValueChange = { entry ->
            text = entry.filter { it.isDigit() }.take(3)
            val value = text.toIntOrNull()
            if (value != null && value in 1..365) onChange(value)
        },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        helper = "Between 1 and 365 days after the work is finished.",
        error = if (valid) null else "Enter a number between 1 and 365.",
        keyboardType = KeyboardType.Number,
    )
}

@Composable
private fun HourField(
    label: String,
    current: Int,
    onChange: (Int) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var text by remember(current) { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 0..23

    LabelledField(
        label = label,
        value = text,
        onValueChange = { entry ->
            text = entry.filter { it.isDigit() }.take(2)
            val value = text.toIntOrNull()
            if (value != null && value in 0..23) onChange(value)
        },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        helper = "Whole hours, on a 24-hour clock.",
        error = if (valid) null else "Enter an hour between 0 and 23.",
        keyboardType = KeyboardType.Number,
    )
}
