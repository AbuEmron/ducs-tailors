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
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.SafeguardEditorState
import org.fisabilillah.core.model.LocationPrecision
import org.fisabilillah.core.model.NameVisibility
import org.fisabilillah.core.model.ProfileImageStyle
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.policy.ContentSignals

/**
 * Privacy controls.
 *
 * A deliberate subset of the safeguards screen: only the settings that answer "what can
 * another person see about me". They are separated out because the two questions people
 * arrive with are different — "who can reach me" is about contact, and "what do they know
 * about me" is about exposure — and a single screen of forty controls answers neither well.
 *
 * The automated-checks disclosure sits here in full, in the same words the consent record
 * uses. Telling someone plainly what is scanned, that it runs on their own device, and that
 * it never decides anything is the only version of this that respects them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyControlsScreen(
    state: SafeguardEditorState,
    onEdit: ((UserSafeguards) -> UserSafeguards) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy controls") },
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
            Column(Modifier.padding(padding)) { LoadingState(label = "Loading your settings") }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            ContentCard {
                Text(
                    text = "What another member can learn about you",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Your exact address is not governed by anything on this page. " +
                        "It is released only by a separate decision you make, one recipient " +
                        "at a time, and every release is recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.tightenedByOrganization) {
                OrganizationFloorNote()
            }

            if (state.refusal != null) {
                RefusalNotice(message = state.refusal)
            }

            ScopeChooser(
                title = "Who can find my profile",
                description = "People outside this group do not see you in any search.",
                current = draft.profileDiscoverableBy,
                onSelect = { scope -> onEdit { it.copy(profileDiscoverableBy = scope) } },
            )

            SectionDivider()

            SectionHeader(
                title = "My name",
                subtitle = "Your display name is always shown. This governs your real name.",
            )
            for (option in NameVisibility.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = when (option) {
                        NameVisibility.REAL_NAME_PUBLIC ->
                            "Anyone who can see your profile sees your real name."
                        NameVisibility.REAL_NAME_TO_VERIFIED ->
                            "Only members whose identity has been checked."
                        NameVisibility.REAL_NAME_TO_ORGANIZERS ->
                            "Only the organiser of something you have committed to, and " +
                                "only once you have committed."
                        NameVisibility.DISPLAY_NAME_ONLY ->
                            "Nobody sees your real name through this platform."
                    },
                    selected = draft.nameVisibility == option,
                    onSelect = { onEdit { it.copy(nameVisibility = option) } },
                )
            }

            SectionDivider()

            SectionHeader(
                title = "My location",
                subtitle = "How precisely your whereabouts are described to others.",
            )
            for (option in LocationPrecision.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = when (option) {
                        LocationPrecision.EXACT ->
                            "Not recommended. Choose a coarser setting unless you have a " +
                                "specific reason."
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

            SectionDivider()

            SectionHeader(
                title = "My profile image",
                subtitle = "A photograph is never required and is never the default.",
            )
            for (style in ProfileImageStyle.entries) {
                ChoiceRow(
                    title = style.displayName,
                    description = when (style) {
                        ProfileImageStyle.NONE -> "Nothing is shown."
                        ProfileImageStyle.INITIALS -> "Your initials on a plain background."
                        ProfileImageStyle.GEOMETRIC_AVATAR ->
                            "A generated pattern that depicts nothing."
                        ProfileImageStyle.PHOTOGRAPH ->
                            "A photograph, shown only to the audience you choose below."
                    },
                    selected = draft.profileImageStyle == style,
                    onSelect = { onEdit { it.copy(profileImageStyle = style) } },
                )
            }

            ScopeChooser(
                title = "Who can see my profile image",
                description = "Applies only if you have chosen to have one.",
                current = draft.profileImageVisibleTo,
                onSelect = { scope -> onEdit { it.copy(profileImageVisibleTo = scope) } },
            )

            SectionDivider()

            SectionHeader(title = "Automated safety checks")
            DisclaimerCard(
                title = "What is checked, and what it can and cannot do",
                text = ContentSignals.DISCLOSURE,
            )

            SectionDivider()

            LoosenedFieldsNotice(state.loosenedFields)

            PrimaryButton(
                text = "Save privacy settings",
                onClick = onSave,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = state.hasUnsavedChanges,
                loading = state.saving,
            )

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "These settings govern what the platform shows. They cannot govern " +
                    "what a person who has already seen something does with it.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}
