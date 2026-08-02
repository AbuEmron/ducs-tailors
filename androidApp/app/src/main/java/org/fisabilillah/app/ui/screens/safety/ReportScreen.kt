package org.fisabilillah.app.ui.screens.safety

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
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SafeguardToggle
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ReportState
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportGroup

/**
 * Making a report.
 *
 * The categories are granular rather than collapsed into a single "inappropriate" bucket,
 * because a bucket forces the safety team to re-derive what happened from free text and
 * makes it impossible to see that eleven people have reported the same person for the same
 * specific thing.
 *
 * The note about false reports is placed last and worded carefully. It has to be said — a
 * reporting tool with no cost attached becomes a weapon — but a victim already doubting
 * whether they are overreacting will read a heavy-handed warning as a reason to close the
 * screen. So it names *knowingly* false and retaliatory reports, and says plainly that
 * being mistaken is not a violation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportScreen(
    state: ReportState,
    onSetCategory: (ReportCategory) -> Unit,
    onSetDescription: (String) -> Unit,
    onSetIncludeEvidence: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report") },
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
        if (state.submitted) {
            ScreenColumn(contentPadding = padding) {
                Spacer(Modifier.height(spacing.lg))
                ContentCard {
                    Text(
                        text = "Your report has been received",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "A person will read it. You will be told when it has been " +
                            "reviewed, under safety notices. You will not be told what " +
                            "action was taken against another member, because that is " +
                            "their business and not yours — this is not us dismissing what " +
                            "you reported.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "The person you reported is not told who reported them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            ContentCard {
                Text(
                    text = "What happens to this",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "A person reads every report. Automated checks decide only which " +
                        "report a human looks at first — they never decide an outcome, " +
                        "remove anything, or restrict an account on their own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val refusal = state.refusal
            if (refusal != null) {
                RefusalNotice(message = refusal)
            }

            // ── Category, grouped ────────────────────────────────────────────
            for (group in ReportGroup.entries) {
                val categories = ReportCategory.entries.filter { it.group == group }

                SectionHeader(
                    title = group.displayName,
                    subtitle = when (group) {
                        ReportGroup.CONDUCT ->
                            "How someone is behaving towards you or someone else."
                        ReportGroup.SAFETY ->
                            "Something that puts a person at risk. These reach a human " +
                                "fastest."
                        ReportGroup.INTEGRITY ->
                            "Money, credentials, or someone claiming to be what they are " +
                                "not."
                    },
                )

                for (category in categories) {
                    ChoiceRow(
                        title = category.displayName,
                        description = "Severity: ${category.severity.displayName}." + (
                            if (category.requiresImmediateHumanReview) {
                                " This goes to a human immediately."
                            } else {
                                ""
                            }
                            ),
                        selected = state.category == category,
                        onSelect = { onSetCategory(category) },
                    )
                }
            }

            SectionDivider()

            // ── What happened ────────────────────────────────────────────────
            SectionHeader(
                title = "What happened",
                subtitle = "In your own words. A moderator reads this before anything else.",
            )

            LabelledField(
                label = "Describe what happened",
                value = state.description,
                onValueChange = onSetDescription,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                helper = "Include when it happened and anything that would not be obvious " +
                    "from the messages themselves.",
                singleLine = false,
                minLines = 5,
            )

            // ── Evidence ─────────────────────────────────────────────────────
            SectionHeader(title = "Evidence")

            SafeguardToggle(
                title = "Attach a copy of the conversation",
                description = "A snapshot is taken now and preserved. If the other person " +
                    "later unsends a message, deletes a listing, or rewrites their " +
                    "profile, the copy taken at this moment survives it. Turning this off " +
                    "means a moderator has only your description to work from.",
                checked = state.includeEvidence,
                onCheckedChange = onSetIncludeEvidence,
            )

            ContentCard {
                Text(
                    text = "Unsent messages are included",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "When someone unsends a message, you stop seeing it but a copy " +
                        "is kept where only the safety team can read it. That copy is " +
                        "attached to this report. Unsending is not a way to say something " +
                        "and erase the proof before you can report it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.md))

            PrimaryButton(
                text = "Submit this report",
                onClick = onSubmit,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = state.category != null && state.description.isNotBlank(),
                loading = state.submitting,
            )

            Spacer(Modifier.height(spacing.md))

            SectionHeader(title = "One thing to be aware of")

            ContentCard {
                Text(
                    text = "Being wrong is not a violation. Reporting something that turns " +
                        "out to be innocent, or that a moderator decides not to act on, " +
                        "costs you nothing at all — please report anyway if something feels " +
                        "wrong to you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Knowingly false reports and reports made to retaliate against " +
                        "someone are a separate matter, and are themselves a violation of " +
                        "the guidelines. This is stated because a reporting tool with no " +
                        "cost attached gets used as a weapon against the people it was " +
                        "built to protect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "Your name is not shown to the person you are reporting. It is " +
                    "visible to the safety team, because a report nobody can be asked " +
                    "about cannot be investigated.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}
