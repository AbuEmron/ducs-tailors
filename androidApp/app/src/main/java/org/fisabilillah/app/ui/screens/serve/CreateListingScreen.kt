package org.fisabilillah.app.ui.screens.serve

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
import org.fisabilillah.core.model.BeneficiaryType
import org.fisabilillah.core.model.DeliveryFormat
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.ServiceGroup
import org.fisabilillah.core.policy.ValidationError

/**
 * What an organiser types when publishing an opportunity.
 *
 * Every field here exists because a volunteer needs the answer before they agree to come.
 * The background-check switch is the one control on this screen an organiser cannot turn
 * off: where the category involves children or somebody's home, it is locked on and the
 * reason is stated next to it.
 */
internal data class CreateListingDraft(
    val title: String = "",
    val summary: String = "",
    val category: ServiceCategory = ServiceCategory.COMMUNITY_CLEANUP,
    val beneficiaryType: BeneficiaryType = BeneficiaryType.NEIGHBOURHOOD,
    val city: String = "",
    val format: DeliveryFormat = DeliveryFormat.IN_PERSON,
    val volunteersNeeded: Int = 1,
    val physicalRequirements: String = "",
    val safetyNotes: String = "",
    val backgroundCheckRequired: Boolean = false,
    val childSafeguardingRequired: Boolean = false,
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    val expensesReimbursed: Boolean = false,
    val completionCriteria: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateListingScreen(
    errors: List<ValidationError>,
    /** A reason the whole thing was refused, as opposed to a field needing attention. */
    refusal: String?,
    submitting: Boolean,
    onSubmit: (CreateListingDraft) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var draft by remember { mutableStateOf(CreateListingDraft()) }
    var volunteersText by remember { mutableStateOf("1") }

    val checkIsLocked = draft.category.requiresBackgroundCheckByDefault ||
        draft.childSafeguardingRequired

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post an opportunity") },
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
        ScreenColumn(contentPadding = padding) {
            // A refusal is a different thing from a validation error and is shown
            // differently: nothing the person can fix by editing a field.
            if (refusal != null) {
                RefusalNotice(message = refusal)
            } else if (errors.isNotEmpty()) {
                RefusalNotice(
                    message = "Some details still need attention. The fields concerned are " +
                        "marked below.",
                )
            }

            SectionHeader(
                title = "What needs doing",
                subtitle = "Plainly, as you would say it to someone standing next to you",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                LabelledField(
                    label = "Title",
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    placeholder = "Saturday food parcel packing",
                    error = listingErrorFor(errors, "title"),
                )
                LabelledField(
                    label = "Summary",
                    value = draft.summary,
                    onValueChange = { draft = draft.copy(summary = it) },
                    helper = "What a volunteer will actually be doing.",
                    error = listingErrorFor(errors, "summary"),
                    singleLine = false,
                    minLines = 4,
                )
                LabelledField(
                    label = "City or town",
                    value = draft.city,
                    onValueChange = { draft = draft.copy(city = it) },
                    helper = "Volunteers see the area, not a street address.",
                    error = listingErrorFor(errors, "city"),
                )
                LabelledField(
                    label = "Volunteers needed",
                    value = volunteersText,
                    onValueChange = { text ->
                        volunteersText = text.filter { it.isDigit() }.take(4)
                        draft = draft.copy(
                            volunteersNeeded = volunteersText.toIntOrNull() ?: 0,
                        )
                    },
                    helper = "How many people you need. Not a target to beat.",
                    error = listingErrorFor(errors, "volunteersNeeded"),
                    keyboardType = KeyboardType.Number,
                )
            }

            SectionHeader(
                title = "Kind of work",
                subtitle = "Some categories carry a mandatory background check",
            )
            for (group in ServiceGroup.entries) {
                SectionHeader(title = group.displayName)
                for (category in ServiceCategory.entries.filter { it.group == group }) {
                    ChoiceRow(
                        title = category.displayName,
                        description = categoryNote(category),
                        selected = draft.category == category,
                        onSelect = {
                            draft = draft.copy(
                                category = category,
                                backgroundCheckRequired =
                                    category.requiresBackgroundCheckByDefault ||
                                        draft.backgroundCheckRequired,
                            )
                        },
                    )
                }
            }
            listingErrorFor(errors, "category")?.let { FieldError(it) }

            SectionHeader(title = "Who benefits")
            for (type in BeneficiaryType.entries) {
                ChoiceRow(
                    title = type.displayName,
                    description = beneficiaryNote(type),
                    selected = draft.beneficiaryType == type,
                    onSelect = { draft = draft.copy(beneficiaryType = type) },
                )
            }

            SectionHeader(title = "How people take part")
            for (format in DeliveryFormat.entries) {
                ChoiceRow(
                    title = format.displayName,
                    description = formatNote(format),
                    selected = draft.format == format,
                    onSelect = { draft = draft.copy(format = format) },
                )
            }

            SectionHeader(
                title = "Gender arrangement",
                subtitle = "State this honestly. People decide whether to come based on it",
            )
            for (arrangement in GenderArrangement.entries) {
                ChoiceRow(
                    title = arrangement.displayName,
                    description = genderNote(arrangement),
                    selected = draft.genderArrangement == arrangement,
                    onSelect = { draft = draft.copy(genderArrangement = arrangement) },
                )
            }

            SectionDivider()

            SectionHeader(title = "Safeguards")

            SafeguardToggle(
                title = "A background check is required",
                description = "Volunteers cannot start until a check is complete.",
                checked = draft.backgroundCheckRequired || checkIsLocked,
                onCheckedChange = { draft = draft.copy(backgroundCheckRequired = it) },
                lockedReason = if (checkIsLocked) {
                    "This cannot be turned off. ${draft.category.displayName} brings " +
                        "volunteers into contact with children or into someone's home, and a " +
                        "check is required before anyone may begin."
                } else {
                    null
                },
            )

            SafeguardToggle(
                title = "Child safeguarding policy applies",
                description = "Turn this on if volunteers will be around children at any " +
                    "point. It also makes the background check mandatory.",
                checked = draft.childSafeguardingRequired,
                onCheckedChange = { on ->
                    draft = draft.copy(
                        childSafeguardingRequired = on,
                        backgroundCheckRequired = if (on) true else draft.backgroundCheckRequired,
                    )
                },
            )

            SafeguardToggle(
                title = "Reasonable expenses are reimbursed",
                description = "Say so plainly either way. A volunteer paying their own bus " +
                    "fare should know that before they agree.",
                checked = draft.expensesReimbursed,
                onCheckedChange = { draft = draft.copy(expensesReimbursed = it) },
            )

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                LabelledField(
                    label = "Physical requirements",
                    value = draft.physicalRequirements,
                    onValueChange = { draft = draft.copy(physicalRequirements = it) },
                    placeholder = "Lifting boxes of up to 10kg, standing for two hours",
                    helper = "Leave blank only if there genuinely are none.",
                    error = listingErrorFor(errors, "physicalRequirements"),
                    singleLine = false,
                    minLines = 3,
                )
                LabelledField(
                    label = "Safety notes",
                    value = draft.safetyNotes,
                    onValueChange = { draft = draft.copy(safetyNotes = it) },
                    placeholder = "Wear closed shoes. A first-aider is on site.",
                    error = listingErrorFor(errors, "safetyNotes"),
                    singleLine = false,
                    minLines = 3,
                )
            }

            SectionHeader(
                title = "When the work is finished",
                subtitle = "Required. A volunteer should know when they are done",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                LabelledField(
                    label = "Completion criteria",
                    value = draft.completionCriteria,
                    onValueChange = { draft = draft.copy(completionCriteria = it) },
                    placeholder = "All 60 parcels packed, labelled and loaded into the van",
                    helper = "Required. Open-ended commitments are the most common reason " +
                        "volunteers quietly stop coming.",
                    error = listingErrorFor(errors, "completionCriteria"),
                    singleLine = false,
                    minLines = 3,
                )
            }

            DisclaimerCard(
                title = "What publishing means",
                text = "You are responsible for how this activity runs. The platform does " +
                    "not inspect activities, supervise them, or stand behind them, and " +
                    "nothing here is an endorsement by us of you or of the work.",
            )

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                PrivacyNote(
                    text = "Your display name is shown on the listing. Volunteers who apply " +
                        "will be able to message you.",
                )
                Spacer(Modifier.height(spacing.md))
                PrimaryButton(
                    text = "Publish this opportunity",
                    onClick = { onSubmit(draft) },
                    enabled = draft.title.isNotBlank() &&
                        draft.summary.isNotBlank() &&
                        draft.completionCriteria.isNotBlank() &&
                        draft.volunteersNeeded > 0,
                    loading = submitting,
                )
            }
        }
    }
}

@Composable
private fun FieldError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(
            horizontal = FiSabilillahTheme.spacing.screenHorizontal,
            vertical = FiSabilillahTheme.spacing.xxs,
        ),
    )
}

private fun listingErrorFor(errors: List<ValidationError>, field: String): String? =
    errors.firstOrNull { it.field == field }?.message

private fun categoryNote(category: ServiceCategory): String = when {
    category.involvesMinors ->
        "Involves children. A background check and safeguarding policy are required."
    category.involvesHomeVisits ->
        "Involves visiting someone at home. A background check is required."
    else -> category.group.displayName
}

private fun beneficiaryNote(type: BeneficiaryType): String = when (type) {
    BeneficiaryType.INDIVIDUAL ->
        "One household. Take particular care with what you publish about them."
    BeneficiaryType.MASJID -> "The masjid itself benefits from the work."
    BeneficiaryType.ORGANIZATION -> "An organisation is the beneficiary."
    BeneficiaryType.NEIGHBOURHOOD -> "The surrounding area benefits."
    BeneficiaryType.WIDER_PUBLIC -> "Open to anyone, Muslim or not."
}

private fun formatNote(format: DeliveryFormat): String = when (format) {
    DeliveryFormat.IN_PERSON -> "Everyone attends in person at the stated place."
    DeliveryFormat.ONLINE -> "Nobody needs to travel."
    DeliveryFormat.HYBRID -> "Some in person, some online. Say which is which in the summary."
}

private fun genderNote(arrangement: GenderArrangement): String = when (arrangement) {
    GenderArrangement.BROTHERS_ONLY -> "Only brothers take part."
    GenderArrangement.SISTERS_ONLY -> "Only sisters take part."
    GenderArrangement.SEPARATE_SESSIONS -> "Brothers and sisters attend at separate times."
    GenderArrangement.FAMILIES_WELCOME -> "Families attend together."
    GenderArrangement.MIXED_WITH_SUPERVISION ->
        "Mixed, with organisers present throughout. Say who is supervising in the summary."
    GenderArrangement.NOT_APPLICABLE ->
        "Nobody meets anyone. Use this only where the work is genuinely solitary or online."
}
