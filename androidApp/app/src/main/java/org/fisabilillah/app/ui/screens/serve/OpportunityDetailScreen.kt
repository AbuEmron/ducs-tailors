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
import org.fisabilillah.app.ui.components.CautionButton
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SafeguardBanner
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.components.VerificationBadge
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.VolunteerOpportunity

/**
 * One opportunity, in full.
 *
 * The safeguards come first, above the description. The most common way volunteering goes
 * wrong is not malice but surprise — somebody arrives to find the work, the setting, or
 * the arrangements were not what they understood. Everything that could surprise a person
 * is stated before the button that commits them to anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpportunityDetailScreen(
    opportunity: VolunteerOpportunity?,
    organiserName: String,
    organisationName: String?,
    applying: Boolean,
    refusal: String?,
    onApply: (String) -> Unit,
    onMessageOrganiser: () -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opportunity") },
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
        if (opportunity == null) {
            ScreenColumn(contentPadding = padding) {
                EmptyState(
                    title = "This opportunity is not available",
                    body = "It may have been withdrawn by the organiser, or removed. " +
                        "Nothing you did caused this.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            SafeguardBanner(
                purposeLabel = "Terms of this activity",
                subjectTitle = opportunity.title,
                requirements = safeguardRequirements(opportunity),
            )

            SectionHeader(title = opportunity.title, subtitle = opportunity.category.displayName)

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text(text = opportunity.summary, style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.height(spacing.md))
                VerificationBadge(
                    label = opportunity.verificationStatus.displayName,
                    whatItDoesNotMean = opportunity.verificationStatus.explanation,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = opportunity.verificationStatus.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader(title = "What this involves")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(
                    label = "Organised by",
                    value = if (organisationName != null) {
                        "$organiserName, for $organisationName"
                    } else {
                        organiserName
                    },
                )
                DetailRow(label = "Who it is for", value = opportunity.beneficiaryType.displayName)
                DetailRow(label = "Format", value = opportunity.format.displayName)
                DetailRow(label = "Where", value = opportunity.place.publicLabel)
                DetailRow(
                    label = "When",
                    value = "${detailDateLabel(opportunity.startsAt)} to " +
                        detailDateLabel(opportunity.endsAt),
                )
                DetailRow(
                    label = "Volunteers",
                    value = if (opportunity.isFull) {
                        "All ${opportunity.volunteersNeeded} places are taken at the moment"
                    } else {
                        "${opportunity.placesRemaining} of ${opportunity.volunteersNeeded} " +
                            "places still open"
                    },
                )
                DetailRow(label = "Status", value = opportunity.status.displayName)
            }

            SectionHeader(
                title = "When you are finished",
                subtitle = "So you know what completing this actually means",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text(
                    text = opportunity.completionCriteria,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionHeader(title = "Arrangements and safeguards")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(
                    label = "Gender arrangement",
                    value = opportunity.genderArrangement.displayName,
                )
                DetailRow(
                    label = "Physical requirements",
                    value = opportunity.physicalRequirements
                        ?: "The organiser has not stated any. Ask before you commit if this " +
                        "matters for you.",
                )
                DetailRow(
                    label = "Safety notes",
                    value = opportunity.safetyNotes ?: "The organiser has not added any.",
                )
                DetailRow(
                    label = "Expenses",
                    value = if (opportunity.expensesReimbursed) {
                        "Reasonable expenses are reimbursed by the organiser."
                    } else {
                        "Expenses are not reimbursed. Anything you spend is your own."
                    },
                )
                val transportNotes = opportunity.transportNotes
                DetailRow(
                    label = "Transport",
                    value = when {
                        transportNotes != null -> transportNotes
                        opportunity.transportProvided -> "Transport is provided."
                        else -> "Transport is not provided. Please arrange your own."
                    },
                )
            }

            if (opportunity.backgroundCheckRequired || opportunity.backgroundCheckIsMandatory) {
                DisclaimerCard(
                    title = "A background check is required",
                    text = if (opportunity.backgroundCheckIsMandatory) {
                        "This work brings volunteers into contact with people who may not be " +
                            "able to protect themselves — children, or somebody in their own " +
                            "home. The check is required by the platform and the organiser " +
                            "cannot waive it. A completed check looks backwards at records " +
                            "that exist; it is not a guarantee about anyone's future " +
                            "conduct, which is why the other arrangements above still apply."
                    } else {
                        "The organiser has asked for a background check before anyone " +
                            "starts. A completed check looks backwards at records that " +
                            "exist; it is not a guarantee about anyone's future conduct, " +
                            "which is why the other arrangements above still apply."
                    },
                )
            }

            if (opportunity.childSafeguardingRequired) {
                DisclaimerCard(
                    title = "Child safeguarding applies",
                    text = "You will be asked to follow the organiser's child safeguarding " +
                        "policy, which includes never being alone and unobserved with a " +
                        "child, and reporting any concern to the named lead rather than " +
                        "handling it yourself.",
                )
            }

            SectionDivider()

            SectionHeader(
                title = "Offer to take part",
                subtitle = "Tell the organiser what you can do and when you are free",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                LabelledField(
                    label = "Your message to the organiser",
                    value = message,
                    onValueChange = { message = it },
                    placeholder = "What you can help with, and the times that suit you",
                    helper = "The organiser reads this before deciding.",
                    singleLine = false,
                    minLines = 4,
                )
                Spacer(Modifier.height(spacing.xs))
                PrivacyNote(
                    text = "Applying shares your display name, your profile and this message " +
                        "with the organiser. It does not share your address or contact details.",
                )
                Spacer(Modifier.height(spacing.md))
                PrimaryButton(
                    text = "Apply to help",
                    onClick = { onApply(message) },
                    enabled = opportunity.status.acceptsApplications &&
                        !opportunity.isFull &&
                        message.isNotBlank(),
                    loading = applying,
                )
                if (!opportunity.status.acceptsApplications) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "This listing is not accepting applications. Its status is " +
                            "${opportunity.status.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (opportunity.isFull) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Every place is taken at the moment. You could message the " +
                            "organiser to ask whether that changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (refusal != null) {
                Spacer(Modifier.height(spacing.xs))
                RefusalNotice(message = refusal)
            }

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Spacer(Modifier.height(spacing.md))
                SecondaryButton(text = "Message the organiser", onClick = onMessageOrganiser)
                Spacer(Modifier.height(spacing.lg))
                CautionButton(text = "Report this listing", onClick = onReport)
            }
        }
    }
}

/** The lines shown in the banner at the top. Only what is actually in force is listed. */
private fun safeguardRequirements(opportunity: VolunteerOpportunity): List<String> = buildList {
    add("Gender arrangement: ${opportunity.genderArrangement.displayName}")
    if (opportunity.backgroundCheckRequired || opportunity.backgroundCheckIsMandatory) {
        add("A background check must be completed before you start")
    }
    if (opportunity.childSafeguardingRequired) {
        add("The organiser's child safeguarding policy applies")
    }
    if (opportunity.physicalRequirements != null) {
        add("Physical requirements: ${opportunity.physicalRequirements}")
    }
    if (opportunity.safetyNotes != null) {
        add("Safety: ${opportunity.safetyNotes}")
    }
    add(
        if (opportunity.expensesReimbursed) {
            "Reasonable expenses are reimbursed"
        } else {
            "Expenses are not reimbursed"
        },
    )
}

private fun detailDateLabel(timestamp: Timestamp): String = timestamp.toString().take(10)
