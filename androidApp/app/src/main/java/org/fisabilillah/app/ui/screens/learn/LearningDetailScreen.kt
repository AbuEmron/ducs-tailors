package org.fisabilillah.app.ui.screens.learn

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
import org.fisabilillah.app.ui.components.CautionButton
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.components.VerificationBadge
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.LearningOffering

/**
 * One class, in full.
 *
 * The instructor's capacity disclaimer is rendered unconditionally, above everything a
 * person might act on. Somebody looking for reliable knowledge should never have to work
 * out for themselves whether the person teaching is a scholar, a student, or a neighbour
 * who reads well — and peer learning is labelled as peer learning every time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LearningDetailScreen(
    offering: LearningOffering?,
    instructorName: String,
    enrolling: Boolean,
    refusal: String?,
    onEnrol: () -> Unit,
    onMessageInstructor: () -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Class") },
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
        if (offering == null) {
            ScreenColumn(contentPadding = padding) {
                EmptyState(
                    title = "This class is not available",
                    body = "It may have been withdrawn by the instructor, or removed.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            SectionHeader(title = offering.title, subtitle = offering.subject.displayName)

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text(text = offering.summary, style = MaterialTheme.typography.bodyLarge)
            }

            if (offering.isPeerLearning) {
                DisclaimerCard(
                    title = "This is peer learning",
                    text = "The people here are studying together as equals. Nobody in this " +
                        "group is presented as a teacher, and nothing said in it should be " +
                        "taken as an authoritative answer.",
                )
            }

            // Always shown, never conditional. The capacity disclaimer is the single most
            // important sentence on this screen.
            DisclaimerCard(
                title = "Who is teaching, and in what capacity",
                text = offering.capacityDisclaimer,
            )

            SectionHeader(title = "The instructor")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(label = "Name", value = instructorName)
                DetailRow(
                    label = "Stated capacity",
                    value = offering.instructorCapacity.displayName,
                )
                Spacer(Modifier.height(spacing.xs))
                VerificationBadge(
                    label = offering.instructorVerification.displayName,
                    whatItDoesNotMean = offering.instructorVerification.whatItDoesNotMean,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = offering.instructorVerification.whatItDoesNotMean,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader(
                title = "Method and sources",
                subtitle = "Disclosed so you know what you are walking into",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(
                    label = "Methodology",
                    value = offering.methodology?.displayName
                        ?: "The instructor has not stated one.",
                )
                val methodologyNotes = offering.methodologyNotes
                if (methodologyNotes != null) {
                    DetailRow(label = "In the instructor's words", value = methodologyNotes)
                }

                Spacer(Modifier.height(spacing.xs))
                Text(text = "Sources used", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(spacing.xxs))
                if (offering.sourceReferences.isEmpty()) {
                    Text(
                        text = "The instructor has not listed any sources. It is reasonable " +
                            "to ask what is being taught from before you enrol.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    for (reference in offering.sourceReferences) {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = reference.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val supporting = listOfNotNull(
                            reference.author,
                            reference.locator,
                            reference.note,
                        )
                        if (supporting.isNotEmpty()) {
                            Text(
                                text = supporting.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionHeader(title = "Practical details")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(label = "Level", value = offering.level.displayName)
                DetailRow(label = "Language", value = offering.language.displayName)
                DetailRow(label = "Format", value = offering.format.displayName)
                DetailRow(
                    label = "Where",
                    value = offering.place?.publicLabel
                        ?: "Online, or arranged with the instructor.",
                )
                DetailRow(label = "Cost", value = learnCostLabel(offering.cost))
                DetailRow(
                    label = "Places",
                    value = if (offering.placesRemaining == 0) {
                        "All ${offering.maxStudents} places are taken at the moment"
                    } else {
                        "${offering.placesRemaining} of ${offering.maxStudents} still open"
                    },
                )
                val startsOn = offering.startsOn
                if (startsOn != null) {
                    DetailRow(label = "Starts", value = startsOn.toString().take(10))
                }
                if (offering.schedule.isNotEmpty()) {
                    DetailRow(
                        label = "Schedule",
                        value = offering.schedule.joinToString("\n") { window ->
                            val day = window.day.name.lowercase()
                                .replaceFirstChar { letter -> letter.uppercaseChar() }
                            "$day, ${window.start} to ${window.end}"
                        },
                    )
                }
                val scheduleNotes = offering.scheduleNotes
                if (scheduleNotes != null) {
                    DetailRow(label = "Timing notes", value = scheduleNotes)
                }
                DetailRow(label = "Status", value = offering.status.displayName)
            }

            SectionHeader(title = "Arrangements")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(
                    label = "Gender arrangement",
                    value = offering.genderArrangement.displayName,
                )
                DetailRow(
                    label = "One-to-one study",
                    value = if (offering.sameGenderStudentsOnly) {
                        "The instructor teaches students of the same gender only."
                    } else {
                        "The instructor has not restricted this by gender."
                    },
                )
            }

            if (offering.learningObjectives.isNotEmpty()) {
                SectionHeader(title = "What you will cover")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    for (objective in offering.learningObjectives) {
                        Text(
                            text = "• $objective",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (offering.requiredMaterials.isNotEmpty()) {
                SectionHeader(title = "What to bring")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    for (material in offering.requiredMaterials) {
                        Text(
                            text = "• $material",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            DisclaimerCard(
                title = "The platform does not issue religious rulings",
                text = "Nothing arranged here is a fatwa, and no class replaces a qualified " +
                    "scholar. A question about your own circumstances — your family, your " +
                    "work, your obligations — belongs with someone local who knows them and " +
                    "can be asked follow-up questions.",
            )

            SectionDivider()

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                PrivacyNote(
                    text = "Asking to enrol shares your display name and profile with the " +
                        "instructor so they can decide.",
                )
                Spacer(Modifier.height(spacing.md))
                PrimaryButton(
                    text = "Ask to enrol",
                    onClick = onEnrol,
                    enabled = offering.status.acceptsApplications,
                    loading = enrolling,
                )
                if (!offering.status.acceptsApplications) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "This class is not taking enrolments. Its status is " +
                            "${offering.status.displayName}.",
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
                SecondaryButton(text = "Message the instructor", onClick = onMessageInstructor)
                Spacer(Modifier.height(spacing.lg))
                CautionButton(text = "Report this class", onClick = onReport)
            }
        }
    }
}
