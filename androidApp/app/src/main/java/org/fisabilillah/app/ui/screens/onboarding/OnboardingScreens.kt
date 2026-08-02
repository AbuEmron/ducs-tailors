package org.fisabilillah.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import java.time.Year
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.ConsentKind
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.Language
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.ServiceGroup
import org.fisabilillah.core.policy.ContentSignals

/**
 * Everything collected while setting up an account, held in one place.
 *
 * Kept as a single immutable value rather than four screens each owning a piece, so that
 * going back a step cannot silently lose an answer, and so the completeness rules live next
 * to the data they describe rather than being re-derived in each screen's button.
 */
internal data class OnboardingState(
    /** What other members see. Not required to be a legal name. */
    val displayName: String = "",

    /** Optional, and shown to others only where name visibility settings allow it. */
    val realName: String = "",

    /** Null until chosen. Never defaulted, because guessing it would be worse than asking. */
    val gender: Gender? = null,

    /** Zero means not yet given. */
    val birthYear: Int = 0,

    val city: String = "",

    /** BCP-47 tags, matching [Language.tag]. */
    val languages: Set<String> = setOf(Language.ENGLISH.tag),

    val areasWillingToHelp: Set<ServiceCategory> = emptySet(),
    val areasSeekingHelp: Set<ServiceCategory> = emptySet(),

    val safeguardPreset: SafeguardPresetName = SafeguardPresetName.COMMUNITY_SERVICE,

    val acceptedConsents: Set<ConsentKind> = emptySet(),
) {
    /** True when the profile step has everything it needs. */
    fun profileIsComplete(currentYear: Int): Boolean =
        displayName.isNotBlank() &&
            gender != null &&
            city.isNotBlank() &&
            languages.isNotEmpty() &&
            birthYearIsPlausible(currentYear)

    fun birthYearIsPlausible(currentYear: Int): Boolean {
        if (birthYear == 0) return false
        val age = currentYear - birthYear
        return age in 18..120
    }

    /**
     * The skills step can be finished without selecting anything. Someone who arrives with
     * nothing to offer and nothing to ask for is still welcome, and blocking them here
     * would only teach them to tick boxes at random.
     */
    val skillsAreComplete: Boolean get() = true

    /** Any category selected that would require a background check before taking part. */
    val selectionsNeedingBackgroundCheck: List<ServiceCategory>
        get() = areasWillingToHelp.filter { it.requiresBackgroundCheckByDefault }

    val consentsAreComplete: Boolean
        get() = ConsentKind.entries.filter { it.required }.all { it in acceptedConsents }
}

// ── Step 1: profile ──────────────────────────────────────────────────────────

/**
 * The smallest amount of information the platform can work with.
 *
 * There is no photograph field and no biography field on purpose. Neither is needed to
 * match someone to work they can do, and both are the fields a person is most likely to
 * regret filling in.
 */
@Composable
internal fun OnboardingProfileScreen(
    state: OnboardingState,
    onUpdate: ((OnboardingState) -> OnboardingState) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    val currentYear = remember { Year.now().value }

    OnboardingScaffold(title = "About you", step = 1, onBack = onBack) {
        SectionHeader(
            title = "Your name here",
            subtitle = "A display name is enough. Your legal name is optional.",
        )

        Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
            LabelledField(
                label = "Display name",
                value = state.displayName,
                onValueChange = { value -> onUpdate { it.copy(displayName = value) } },
                placeholder = "How you would like to be addressed",
                helper = "Shown to everyone you interact with.",
            )
            LabelledField(
                label = "Legal name (optional)",
                value = state.realName,
                onValueChange = { value -> onUpdate { it.copy(realName = value) } },
                helper = "Only shown where your name visibility settings allow it. " +
                    "Organisers of work involving children or home visits will normally " +
                    "need it before you can take part.",
            )
        }

        PrivacyNote(
            text = "By default others see your display name only. You can loosen or tighten " +
                "that at any time in your privacy settings.",
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )

        SectionDivider()

        SectionHeader(
            title = "Brother or sister",
            subtitle = "Recorded so the safeguards you choose can actually be applied.",
        )

        for (option in Gender.entries) {
            ChoiceRow(
                title = option.displayName,
                description = when (option) {
                    Gender.MALE -> "You will be addressed as a brother, and any safeguard " +
                        "about contact between unrelated men and women will treat you as one."
                    Gender.FEMALE -> "You will be addressed as a sister, and any safeguard " +
                        "about contact between unrelated men and women will treat you as one."
                },
                selected = state.gender == option,
                onSelect = { onUpdate { it.copy(gender = option) } },
            )
        }

        Spacer(Modifier.height(spacing.xs))

        PrivacyNote(
            text = "This is set once. Changing it afterwards needs support to act, and the " +
                "change is recorded, because tutoring arrangements, conversation rules and " +
                "the introduction process all depend on it staying stable.",
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )

        SectionDivider()

        SectionHeader(
            title = "Year of birth and area",
            subtitle = "Used to confirm you are an adult and to show you nearby work.",
        )

        Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
            LabelledField(
                label = "Year of birth",
                value = if (state.birthYear == 0) "" else state.birthYear.toString(),
                onValueChange = { value ->
                    val digits = value.filter { character -> character.isDigit() }.take(4)
                    val parsed = digits.toIntOrNull() ?: 0
                    onUpdate { it.copy(birthYear = parsed) }
                },
                placeholder = "For example, 1994",
                helper = "Only the year. Your full date of birth is never asked for.",
                error = if (state.birthYear == 0 || state.birthYearIsPlausible(currentYear)) {
                    null
                } else {
                    "This platform is for people aged eighteen or over."
                },
                keyboardType = KeyboardType.Number,
            )
            LabelledField(
                label = "City or town",
                value = state.city,
                onValueChange = { value -> onUpdate { it.copy(city = value) } },
                placeholder = "For example, Birmingham",
                helper = "Others see your city, never your address.",
            )
        }

        SectionDivider()

        SectionHeader(
            title = "Languages you can help in",
            subtitle = "Choose at least one.",
        )

        for (language in Language.common) {
            CheckRow(
                title = language.displayName,
                description = "",
                checked = language.tag in state.languages,
                onCheckedChange = { checked ->
                    onUpdate { current ->
                        val next = if (checked) {
                            current.languages + language.tag
                        } else {
                            current.languages - language.tag
                        }
                        current.copy(languages = next)
                    }
                },
            )
        }

        Spacer(Modifier.height(spacing.md))

        PrimaryButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            enabled = state.profileIsComplete(currentYear),
        )

        if (!state.profileIsComplete(currentYear)) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "A display name, whether you are a brother or a sister, a plausible " +
                    "year of birth, a city and at least one language are needed to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }

        Spacer(Modifier.height(spacing.md))
    }
}

// ── Step 2: what you can help with ───────────────────────────────────────────

/**
 * Two lists rather than one: what a person can give, and what they would like help with.
 *
 * Asking for both, side by side and in the same words, is a small thing that changes how
 * the platform reads. Somewhere to say "I need help with this" that looks exactly like
 * "I can help with this" makes asking ordinary rather than an admission.
 */
@Composable
internal fun OnboardingSkillsScreen(
    state: OnboardingState,
    onUpdate: ((OnboardingState) -> OnboardingState) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    OnboardingScaffold(title = "Where you can help", step = 2, onBack = onBack) {
        ContentCard {
            Text(
                text = "Nothing here is a commitment. It decides which opportunities and " +
                    "requests you are shown, and which ones you may be told about. You can " +
                    "change any of it later, and you can leave both lists empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader(
            title = "What you can help with",
            subtitle = "Choose as few or as many as are honest.",
        )

        for (group in ServiceGroup.entries) {
            GroupHeading(group.displayName)
            for (category in ServiceCategory.entries.filter { it.group == group }) {
                CheckRow(
                    title = category.displayName,
                    description = categoryNote(category),
                    checked = category in state.areasWillingToHelp,
                    onCheckedChange = { checked ->
                        onUpdate { current ->
                            val next = if (checked) {
                                current.areasWillingToHelp + category
                            } else {
                                current.areasWillingToHelp - category
                            }
                            current.copy(areasWillingToHelp = next)
                        }
                    },
                )
            }
        }

        if (state.selectionsNeedingBackgroundCheck.isNotEmpty()) {
            DisclaimerCard(
                title = "Some of these will need a background check",
                text = "You have chosen " +
                    state.selectionsNeedingBackgroundCheck.joinToString { it.displayName } +
                    ". Work with children, or work inside somebody's home, requires a " +
                    "completed background check before you can take part. Choosing it here " +
                    "is fine; you will be asked for the check when you apply for something " +
                    "specific, and it is the organiser rather than the platform who decides " +
                    "whether you go.",
            )
        }

        SectionDivider()

        SectionHeader(
            title = "What you would like help with",
            subtitle = "Asking is not a lesser thing than giving.",
        )

        for (group in ServiceGroup.entries) {
            GroupHeading(group.displayName)
            for (category in ServiceCategory.entries.filter { it.group == group }) {
                CheckRow(
                    title = category.displayName,
                    description = "",
                    checked = category in state.areasSeekingHelp,
                    onCheckedChange = { checked ->
                        onUpdate { current ->
                            val next = if (checked) {
                                current.areasSeekingHelp + category
                            } else {
                                current.areasSeekingHelp - category
                            }
                            current.copy(areasSeekingHelp = next)
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(spacing.sm))

        PrivacyNote(
            text = "What you would like help with is private. It is used to show you " +
                "relevant offers and is never displayed on your profile or shown to anyone " +
                "you have not contacted.",
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )

        Spacer(Modifier.height(spacing.md))

        PrimaryButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            enabled = state.skillsAreComplete,
        )

        Spacer(Modifier.height(spacing.md))
    }
}

// ── Step 3: safeguards ───────────────────────────────────────────────────────

/**
 * Choosing a starting point for your own boundaries.
 *
 * The presets are not a ladder and the screen says so twice, at the top and at the bottom.
 * Ordering them by strictness with the strictest last, or marking one "recommended", would
 * turn a decision about somebody's circumstances into a public statement about their
 * religiosity — which is precisely the dynamic this product exists to avoid.
 */
@Composable
internal fun OnboardingSafeguardsScreen(
    state: OnboardingState,
    onUpdate: ((OnboardingState) -> OnboardingState) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    OnboardingScaffold(title = "Your safeguards", step = 3, onBack = onBack) {
        DisclaimerCard(
            title = "No preset is more religious than another",
            text = "These describe circumstances, not piety. A sister whose family is " +
                "closely involved and a brother who is here only to fix things at the masjid " +
                "need different settings, and neither choice says anything about the person " +
                "who made it. Nothing you choose here is shown to other members as a label, " +
                "and the platform never ranks these or recommends one over the rest.",
        )

        SectionHeader(
            title = "Choose a starting point",
            subtitle = "Every setting inside a preset can be changed afterwards.",
        )

        for (preset in SafeguardPresetName.selectable) {
            ChoiceRow(
                title = preset.displayName,
                description = if (preset == SafeguardPresetName.CUSTOM) {
                    preset.summary + " You will be taken through the full list of settings " +
                        "after onboarding, starting from the community service defaults."
                } else {
                    preset.summary
                },
                selected = state.safeguardPreset == preset,
                onSelect = { onUpdate { it.copy(safeguardPreset = preset) } },
            )
        }

        Spacer(Modifier.height(spacing.sm))

        SectionHeader(title = "What a preset does not do")
        ContentCard {
            Text(
                text = "A preset sets the starting values of about thirty separate settings — " +
                    "who can find you, who can start a conversation, whether calls are " +
                    "offered, how conversations with the opposite gender are structured, and " +
                    "whether a trusted contact is added to them. It does not lock anything. " +
                    "You may always make a setting stricter than the preset chose, and you " +
                    "may loosen most of them, though a masjid or organisation you join may " +
                    "hold a floor you cannot go below inside their own spaces.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(spacing.xs))

        PrivacyNote(
            text = "Your safeguards are yours. Other members see the effect of them — that " +
                "a conversation needs a stated purpose, or that a third party is present — " +
                "never the settings themselves or which preset you started from.",
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )

        Spacer(Modifier.height(spacing.md))

        PrimaryButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )

        Spacer(Modifier.height(spacing.md))
    }
}

// ── Step 4: consent ──────────────────────────────────────────────────────────

/**
 * Consent, itemised.
 *
 * Automated safety processing gets the exact words the code that performs it carries, taken
 * from [ContentSignals.DISCLOSURE] rather than paraphrased here, so a change to what the
 * checks actually do cannot leave a friendlier description behind on this screen.
 */
@Composable
internal fun OnboardingConsentScreen(
    state: OnboardingState,
    onUpdate: ((OnboardingState) -> OnboardingState) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    errorMessage: String?,
) {
    val spacing = FiSabilillahTheme.spacing
    val required = remember { ConsentKind.entries.filter { it.required } }

    // The required items are shown switched on and locked, so the state has to agree with
    // what the screen is telling the reader. Pressing "Create my account" is the act of
    // consent; declining them means not creating an account at all.
    LaunchedEffect(Unit) {
        onUpdate { current ->
            current.copy(acceptedConsents = current.acceptedConsents + required)
        }
    }

    OnboardingScaffold(title = "What you are agreeing to", step = 4, onBack = onBack) {
        if (errorMessage != null) {
            RefusalNotice(message = errorMessage)
        }

        ContentCard {
            Text(
                text = "The first five cannot be switched off. They are the terms the " +
                    "platform operates on rather than preferences, and if you do not agree " +
                    "with them the right thing to do is not to create an account. The last " +
                    "two are yours to decide, now or later, and nothing is withheld from you " +
                    "for declining them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader(
            title = "Required",
            subtitle = "Recorded with the date and the version of the document.",
        )

        for (kind in ConsentKind.entries.filter { it.required }) {
            SafeguardToggle(
                title = kind.displayName,
                description = if (kind == ConsentKind.AUTOMATED_SAFETY_PROCESSING) {
                    ContentSignals.DISCLOSURE
                } else {
                    kind.plainLanguage
                },
                checked = true,
                onCheckedChange = { },
                lockedReason = "Required in order to have an account here.",
            )
        }

        SectionDivider()

        SectionHeader(
            title = "Optional",
            subtitle = "You can change either of these at any time in your settings.",
        )

        for (kind in ConsentKind.entries.filter { !it.required }) {
            SafeguardToggle(
                title = kind.displayName,
                description = kind.plainLanguage,
                checked = kind in state.acceptedConsents,
                onCheckedChange = { checked ->
                    onUpdate { current ->
                        val next = if (checked) {
                            current.acceptedConsents + kind
                        } else {
                            current.acceptedConsents - kind
                        }
                        current.copy(acceptedConsents = next)
                    }
                },
            )
        }

        SectionDivider()

        SectionHeader(title = "Reading them in full")
        ContentCard {
            Text(
                text = "The terms of use, the privacy policy and the community guidelines " +
                    "are available in full from your profile settings, before and after you " +
                    "create an account. They are currently placeholder documents pending " +
                    "legal review, and each one says so at the top.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(spacing.xs))

        PrivacyNote(
            text = "Withdrawing a consent later is a supported action rather than a request " +
                "you have to make. Withdrawing a required one closes the account, which is " +
                "the honest consequence of no longer agreeing to the terms.",
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )

        Spacer(Modifier.height(spacing.md))

        PrimaryButton(
            text = "Create my account",
            onClick = onFinish,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            enabled = state.consentsAreComplete,
        )

        Spacer(Modifier.height(spacing.md))
    }
}

// ── Shared pieces ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScaffold(
    title: String,
    step: Int,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            Text(
                text = "Step $step of 4",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.xs))
            content()
        }
    }
}

@Composable
private fun GroupHeading(text: String) {
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(
                start = spacing.screenHorizontal,
                end = spacing.screenHorizontal,
                top = spacing.md,
                bottom = spacing.xxs,
            )
            .semantics { heading() },
    )
}

private fun categoryNote(category: ServiceCategory): String = when {
    category.involvesMinors ->
        "Involves working with children. A background check is required before you take part."
    category.involvesHomeVisits ->
        "Involves visiting someone's home. A background check is required before you take part."
    else -> ""
}

@Composable
private fun CheckRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minimumTouchTarget)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(spacing.sm))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
