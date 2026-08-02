package org.fisabilillah.app.ui.screens.requests

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
import org.fisabilillah.core.model.RequestUrgency
import org.fisabilillah.core.model.RequestVisibility
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.ServiceGroup
import org.fisabilillah.core.policy.ValidationError

/**
 * What somebody types when they need help.
 *
 * The defaults are the cautious ones. Support totals are off, the exact address is
 * optional and held back until the person releases it, and every visibility option
 * explains in its own words who will be able to see what.
 */
internal data class CreateRequestDraft(
    val title: String = "",
    val description: String = "",
    val category: ServiceCategory = ServiceCategory.FOOD_DISTRIBUTION,
    val urgency: RequestUrgency = RequestUrgency.STANDARD,
    val visibility: RequestVisibility = RequestVisibility.IDENTIFIED,
    val city: String = "",
    /** Blank means not given. It is never shown to anyone until you release it. */
    val exactAddress: String = "",
    /** Whether an organisation should take this on and act as the intermediary. */
    val mediatingOrganisation: Boolean = false,
    val peopleAffected: Int = 1,
    /** Off by default, and deliberately so. */
    val showSupportTotals: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateRequestScreen(
    errors: List<ValidationError>,
    submitting: Boolean,
    onSubmit: (CreateRequestDraft) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var draft by remember { mutableStateOf(CreateRequestDraft()) }
    var peopleText by remember { mutableStateOf("1") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask for help") },
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
            if (errors.isNotEmpty()) {
                RefusalNotice(
                    message = "Some details still need attention. The fields concerned are " +
                        "marked below.",
                )
            }

            DisclaimerCard(
                title = "Asking is not a burden on anyone",
                text = "Only say as much as you are comfortable saying. You do not have to " +
                    "explain how you came to need help, and nobody helping you is entitled " +
                    "to ask.",
            )

            SectionHeader(title = "What you need")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                LabelledField(
                    label = "Title",
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    placeholder = "A lift to a hospital appointment on Thursday",
                    error = requestErrorFor(errors, "title"),
                )
                LabelledField(
                    label = "Description",
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it) },
                    helper = "What would actually help, and when.",
                    error = requestErrorFor(errors, "description"),
                    singleLine = false,
                    minLines = 5,
                )
                LabelledField(
                    label = "How many people this affects",
                    value = peopleText,
                    onValueChange = { text ->
                        peopleText = text.filter { it.isDigit() }.take(3)
                        draft = draft.copy(peopleAffected = peopleText.toIntOrNull() ?: 0)
                    },
                    helper = "Including yourself. This helps people bring the right amount.",
                    error = requestErrorFor(errors, "peopleAffected"),
                    keyboardType = KeyboardType.Number,
                )
            }

            SectionHeader(title = "Kind of help")
            for (group in ServiceGroup.entries) {
                SectionHeader(title = group.displayName)
                for (category in ServiceCategory.entries.filter { it.group == group }) {
                    ChoiceRow(
                        title = category.displayName,
                        description = group.displayName,
                        selected = draft.category == category,
                        onSelect = { draft = draft.copy(category = category) },
                    )
                }
            }

            SectionHeader(title = "How soon")
            for (urgency in RequestUrgency.entries) {
                ChoiceRow(
                    title = urgency.displayName,
                    description = urgencyNote(urgency),
                    selected = draft.urgency == urgency,
                    onSelect = { draft = draft.copy(urgency = urgency) },
                )
            }

            SectionDivider()

            SectionHeader(
                title = "Who sees that this is you",
                subtitle = "Moderators can always see who posted a request, so that it can " +
                    "be checked. Nobody else sees more than you choose here",
            )
            for (visibility in RequestVisibility.entries) {
                ChoiceRow(
                    title = visibility.displayName,
                    description = visibility.explanation,
                    selected = draft.visibility == visibility,
                    onSelect = { draft = draft.copy(visibility = visibility) },
                )
            }

            SafeguardToggle(
                title = "Ask an organisation to handle this for me",
                description = "A masjid or charity acts as the intermediary. They see your " +
                    "details; the people helping deal with them rather than with you.",
                checked = draft.mediatingOrganisation,
                onCheckedChange = { draft = draft.copy(mediatingOrganisation = it) },
            )

            SectionHeader(title = "Where")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                LabelledField(
                    label = "City or town",
                    value = draft.city,
                    onValueChange = { draft = draft.copy(city = it) },
                    helper = "This is all that other members see.",
                    error = requestErrorFor(errors, "city"),
                )
                LabelledField(
                    label = "Exact address (optional)",
                    value = draft.exactAddress,
                    onValueChange = { draft = draft.copy(exactAddress = it) },
                    helper = "Held back until you release it. You can leave this blank and " +
                        "add it later.",
                    error = requestErrorFor(errors, "exactAddress"),
                    singleLine = false,
                    minLines = 2,
                )
                Spacer(Modifier.height(spacing.xs))
                PrivacyNote(
                    text = "Your address is never shown on the request. It is released only " +
                        "to a helper whose offer you have accepted, and only when you decide " +
                        "to send it.",
                )
            }

            SectionDivider()

            SafeguardToggle(
                title = "Show a running total of support received",
                description = "Off by default. Leaving it off means nobody sees how much " +
                    "help has come in, so your situation does not become a public tally that " +
                    "people watch. Turn it on only if a total genuinely helps you, for " +
                    "instance where an organisation is coordinating a collection.",
                checked = draft.showSupportTotals,
                onCheckedChange = { draft = draft.copy(showSupportTotals = it) },
            )

            DisclaimerCard(
                title = "What the platform does and does not do",
                text = "We pass your request to members who may be able to help. We do not " +
                    "verify need, we do not hold funds, and we do not guarantee that anyone " +
                    "will respond. If you are in immediate danger, please contact the " +
                    "emergency services rather than waiting here.",
            )

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Spacer(Modifier.height(spacing.md))
                PrimaryButton(
                    text = "Post this request",
                    onClick = { onSubmit(draft) },
                    enabled = draft.title.isNotBlank() &&
                        draft.description.isNotBlank() &&
                        draft.peopleAffected >= 1,
                    loading = submitting,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "You can withdraw a request at any time, and it closes by itself " +
                        "when it expires.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun requestErrorFor(errors: List<ValidationError>, field: String): String? =
    errors.firstOrNull { it.field == field }?.message

private fun urgencyNote(urgency: RequestUrgency): String = when (urgency) {
    RequestUrgency.STANDARD -> "There is no particular deadline."
    RequestUrgency.SOON -> "Within the next week or so."
    RequestUrgency.URGENT ->
        "Within a day or two. Please use this only when it is genuinely the case."
}
