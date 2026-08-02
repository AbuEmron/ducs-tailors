package org.fisabilillah.app.ui.screens.create

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import org.fisabilillah.core.model.CommunityKind
import org.fisabilillah.core.model.DeliveryFormat
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.LearningLevel
import org.fisabilillah.core.model.LearningSubject
import org.fisabilillah.core.model.MembershipPolicy
import org.fisabilillah.core.model.Methodology
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.ServiceGroup
import org.fisabilillah.core.model.SourceReference
import org.fisabilillah.core.model.TeachingCapacity
import org.fisabilillah.core.policy.ValidationError

internal data class ClassDraft(
    val title: String = "",
    val summary: String = "",
    val subject: LearningSubject = LearningSubject.ARABIC_READING,
    val level: LearningLevel = LearningLevel.BEGINNER,
    val capacity: TeachingCapacity = TeachingCapacity.PEER_HELPER,
    val format: DeliveryFormat = DeliveryFormat.IN_PERSON,
    val maxStudents: Int = 8,
    val methodology: Methodology? = null,
    val sourceTitle: String = "",
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    val sameGenderStudentsOnly: Boolean = false,
    val isPeerLearning: Boolean = false,
) {
    val sources: List<SourceReference>
        get() = listOfNotNull(sourceTitle.trim().ifBlank { null }?.let { SourceReference(title = it) })
}

/**
 * Offering to teach something.
 *
 * The two sections that would not appear on an ordinary "create a class" form are the ones
 * that matter most here. **In what capacity** is a claim about standing, and the ones
 * implying religious authority are not self-assignable — a member can teach what they know
 * without claiming to be a scholar, and the form says which is which rather than letting
 * somebody pick the grandest option. **What you teach from** is required for religious
 * subjects, because a class on fiqh that names no methodology and no source is exactly what
 * the platform's learning principles exist to prevent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateClassScreen(
    errors: List<ValidationError>,
    refusal: String?,
    submitting: Boolean,
    onSubmit: (ClassDraft) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var draft by remember { mutableStateOf(ClassDraft()) }
    var studentsText by remember { mutableStateOf("8") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offer a class") },
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

            SectionHeader(title = "What you are offering")
            LabelledField(
                label = "Title",
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                error = errorFor(errors, "title"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "What students will actually study",
                value = draft.summary,
                onValueChange = { draft = draft.copy(summary = it) },
                singleLine = false,
                minLines = 4,
                error = errorFor(errors, "summary"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "Places",
                value = studentsText,
                onValueChange = {
                    studentsText = it.filter { c -> c.isDigit() }.take(3)
                    draft = draft.copy(maxStudents = studentsText.toIntOrNull() ?: 0)
                },
                keyboardType = KeyboardType.Number,
                error = errorFor(errors, "maxStudents"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionHeader(title = "Subject")
            for (subject in LearningSubject.entries) {
                ChoiceRow(
                    title = subject.displayName,
                    description = if (subject.isReligiousInstruction) {
                        "Religious instruction — you will be asked what you teach from."
                    } else {
                        subject.field.displayName
                    },
                    selected = draft.subject == subject,
                    onSelect = { draft = draft.copy(subject = subject) },
                )
            }

            SectionHeader(
                title = "In what capacity",
                subtitle = "Each one comes with a disclaimer students are shown.",
            )
            for (capacity in TeachingCapacity.entries) {
                ChoiceRow(
                    title = capacity.displayName + if (capacity.requiresQualificationReview) {
                        " (needs a confirmed qualification)"
                    } else {
                        ""
                    },
                    description = capacity.disclaimer,
                    selected = draft.capacity == capacity,
                    onSelect = { draft = draft.copy(capacity = capacity) },
                )
            }
            errorFor(errors, "capacity")?.let { RefusalNotice(message = it) }

            if (draft.subject.isReligiousInstruction) {
                SectionHeader(
                    title = "What you teach from",
                    subtitle = "Students are entitled to know before they enrol, and this " +
                        "platform does not present one school as the default.",
                )
                for (methodology in Methodology.entries) {
                    ChoiceRow(
                        title = methodology.displayName,
                        description = "",
                        selected = draft.methodology == methodology,
                        onSelect = { draft = draft.copy(methodology = methodology) },
                    )
                }
                errorFor(errors, "methodology")?.let { RefusalNotice(message = it) }

                LabelledField(
                    label = "A text or source this rests on",
                    value = draft.sourceTitle,
                    onValueChange = { draft = draft.copy(sourceTitle = it) },
                    error = errorFor(errors, "sourceReferences"),
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
                SafeguardToggle(
                    title = "This is peer study, not instruction",
                    description = "People learning together rather than one person teaching. " +
                        "A study circle does not need to name a source.",
                    checked = draft.isPeerLearning,
                    onCheckedChange = { draft = draft.copy(isPeerLearning = it) },
                )
            }

            SectionHeader(title = "Who it is for")
            for (arrangement in GenderArrangement.entries) {
                ChoiceRow(
                    title = arrangement.displayName,
                    description = "",
                    selected = draft.genderArrangement == arrangement,
                    onSelect = { draft = draft.copy(genderArrangement = arrangement) },
                )
            }
            SafeguardToggle(
                title = "One-to-one study with students of my own gender only",
                description = "Applies to private study, not to a group class.",
                checked = draft.sameGenderStudentsOnly,
                onCheckedChange = { draft = draft.copy(sameGenderStudentsOnly = it) },
            )

            SectionDivider()
            PrimaryButton(
                text = "Publish this class",
                onClick = { onSubmit(draft) },
                enabled = !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.sm))
            DisclaimerCard(
                title = "Your badge is the one the platform granted",
                text = "The verification shown next to your name on this listing is read " +
                    "from your account, not from this form. You cannot describe yourself as " +
                    "more checked than you are.",
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

internal data class ProjectDraft(
    val title: String = "",
    val summary: String = "",
    val category: ServiceCategory = ServiceCategory.COMMUNITY_CLEANUP,
    val volunteersNeeded: Int = 0,
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    val isPubliclyListed: Boolean = true,
)

/** Starting a piece of work several people will do together. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateProjectScreen(
    errors: List<ValidationError>,
    refusal: String?,
    submitting: Boolean,
    onSubmit: (ProjectDraft) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var draft by remember { mutableStateOf(ProjectDraft()) }
    var volunteersText by remember { mutableStateOf("0") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start a project") },
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

            SectionHeader(title = "What it is")
            LabelledField(
                label = "Name",
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                error = errorFor(errors, "title"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "What it is trying to achieve",
                value = draft.summary,
                onValueChange = { draft = draft.copy(summary = it) },
                singleLine = false,
                minLines = 4,
                error = errorFor(errors, "summary"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "Volunteers needed",
                value = volunteersText,
                onValueChange = {
                    volunteersText = it.filter { c -> c.isDigit() }.take(4)
                    draft = draft.copy(volunteersNeeded = volunteersText.toIntOrNull() ?: 0)
                },
                helper = "Leave at zero if you are not sure yet.",
                keyboardType = KeyboardType.Number,
                error = errorFor(errors, "volunteersNeeded"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionHeader(title = "Kind of work")
            for (group in ServiceGroup.entries) {
                SectionHeader(title = group.displayName)
                for (category in ServiceCategory.entries.filter { it.group == group }) {
                    ChoiceRow(
                        title = category.displayName,
                        description = if (category.involvesMinors) {
                            "Involves young people. Safeguarding applies."
                        } else {
                            ""
                        },
                        selected = draft.category == category,
                        onSelect = { draft = draft.copy(category = category) },
                    )
                }
            }

            SafeguardToggle(
                title = "List this publicly",
                description = "Off means only people you invite can see it.",
                checked = draft.isPubliclyListed,
                onCheckedChange = { draft = draft.copy(isPubliclyListed = it) },
            )

            PrimaryButton(
                text = "Start the project",
                onClick = { onSubmit(draft) },
                enabled = !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

internal data class CommunityDraft(
    val name: String = "",
    val summary: String = "",
    val kind: CommunityKind = CommunityKind.LOCAL_COMMUNITY,
    val membershipPolicy: MembershipPolicy = MembershipPolicy.APPROVAL_REQUIRED,
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
)

/** Founding a space. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateCommunityScreen(
    errors: List<ValidationError>,
    refusal: String?,
    submitting: Boolean,
    onSubmit: (CommunityDraft) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var draft by remember { mutableStateOf(CommunityDraft()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start a community") },
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

            SectionHeader(title = "The space")
            LabelledField(
                label = "Name",
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                error = errorFor(errors, "name"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            LabelledField(
                label = "Who it is for and what happens in it",
                value = draft.summary,
                onValueChange = { draft = draft.copy(summary = it) },
                singleLine = false,
                minLines = 4,
                error = errorFor(errors, "summary"),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionHeader(title = "What kind of space")
            for (kind in CommunityKind.entries) {
                ChoiceRow(
                    title = kind.displayName,
                    description = "",
                    selected = draft.kind == kind,
                    onSelect = { draft = draft.copy(kind = kind) },
                )
            }

            SectionHeader(
                title = "Who can join",
                subtitle = "You will be its first moderator, so this is your decision to " +
                    "make and yours to keep.",
            )
            for (policy in MembershipPolicy.entries) {
                ChoiceRow(
                    title = policy.displayName,
                    description = "",
                    selected = draft.membershipPolicy == policy,
                    onSelect = { draft = draft.copy(membershipPolicy = policy) },
                )
            }

            SectionHeader(title = "Arrangement")
            for (arrangement in GenderArrangement.entries) {
                ChoiceRow(
                    title = arrangement.displayName,
                    description = "",
                    selected = draft.genderArrangement == arrangement,
                    onSelect = { draft = draft.copy(genderArrangement = arrangement) },
                )
            }
            errorFor(errors, "genderArrangement")?.let { RefusalNotice(message = it) }

            PrimaryButton(
                text = "Found this community",
                onClick = { onSubmit(draft) },
                enabled = !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.sm))
            PrivacyNote(
                text = "A single-gender space is enforced when people join, not left to a " +
                    "moderator to notice.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

private fun errorFor(errors: List<ValidationError>, field: String): String? =
    errors.firstOrNull { it.field == field }?.message
