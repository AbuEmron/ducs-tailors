package org.fisabilillah.app.ui.screens.safety

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.fisabilillah.app.ui.viewmodel.IntroductionFormState
import org.fisabilillah.core.model.GuardianRelationship
import org.fisabilillah.core.model.IntroductionForm
import org.fisabilillah.core.model.IntroductionLimits
import org.fisabilillah.core.policy.IntroductionPolicy

/**
 * Submitting a formal introduction.
 *
 * This screen is deliberately slow. Five long written answers, a named representative, six
 * conduct rules each requiring its own acknowledgement, and two confirmations — none of
 * which can be skipped and none of which is pre-ticked. Friction is usually a design
 * failure; here it is the feature. Someone who is serious about marriage will spend twenty
 * minutes on this without resentment, and someone who is looking for a way to open a
 * private conversation will not.
 *
 * Every field goes to a family. The character floor exists so that what arrives is
 * something a guardian can actually evaluate rather than a sentence and a hope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubmitIntroductionScreen(
    state: IntroductionFormState,
    onUpdate: ((IntroductionFormState) -> IntroductionFormState) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var acknowledged by remember { mutableStateOf(emptySet<Int>()) }

    val allRulesAcknowledged =
        acknowledged.size == IntroductionForm.CONDUCT_RULES.size
    val canSubmit = allRulesAcknowledged &&
        state.confirmsMarriage &&
        state.confirmsConduct &&
        !state.submitting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Formal introduction") },
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
                        text = "Your introduction has been submitted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "You will be told if it moves forward. You will not be told " +
                            "whether it was read, who is considering it, or how many others " +
                            "there are, because none of that is yours to know and all of it " +
                            "invites pressure.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "If you hear nothing within " +
                            "${IntroductionLimits.LAPSE_AFTER_DAYS} days, that is an " +
                            "answer. Please accept it as one, and do not approach this " +
                            "person another way or through anyone else.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            val recipientName = state.recipient?.displayName ?: "this member"

            ContentCard {
                Text(
                    text = "This goes to a family, not to $recipientName's inbox",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Depending on how they have set this up, what you write may be " +
                        "read first by their wali rather than by them. Write it as you " +
                        "would write to someone's father, because you may well be.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DisclaimerCard(
                title = "The platform makes no religious ruling",
                text = IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE,
            )

            val refusal = state.refusal
            if (refusal != null) {
                RefusalNotice(message = refusal)
            }

            SectionDivider()

            // ── Conduct rules ────────────────────────────────────────────────
            SectionHeader(
                title = "What you are agreeing to",
                subtitle = "Each of these has to be acknowledged separately. The date and " +
                    "time you accepted them is recorded.",
            )

            IntroductionForm.CONDUCT_RULES.forEachIndexed { index, rule ->
                ConductRuleRow(
                    rule = rule,
                    checked = index in acknowledged,
                    onCheckedChange = { checked ->
                        acknowledged = if (checked) {
                            acknowledged + index
                        } else {
                            acknowledged - index
                        }
                    },
                )
            }

            if (!allRulesAcknowledged) {
                PrivacyNote(
                    text = "${acknowledged.size} of " +
                        "${IntroductionForm.CONDUCT_RULES.size} acknowledged. The form " +
                        "below can be filled in, but it cannot be submitted until all of " +
                        "them are.",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }

            SectionDivider()

            // ── The form ─────────────────────────────────────────────────────
            SectionHeader(
                title = "About you",
                subtitle = "Each answer needs at least ${IntroductionForm.MIN_FIELD} " +
                    "characters.",
            )

            LongField(
                label = "Your intention",
                value = state.statedIntention,
                onValueChange = { value -> onUpdate { it.copy(statedIntention = value) } },
                helper = "Why you are submitting this, plainly.",
                error = state.errorFor("statedIntention"),
            )

            LongField(
                label = "About yourself",
                value = state.aboutSelf,
                onValueChange = { value -> onUpdate { it.copy(aboutSelf = value) } },
                helper = "Who you are, what you do, and how you spend your time.",
                error = state.errorFor("aboutSelf"),
            )

            LongField(
                label = "Your family context",
                value = state.familyContext,
                onValueChange = { value -> onUpdate { it.copy(familyContext = value) } },
                helper = "Who your family are and whether they know you are doing this.",
                error = state.errorFor("familyContext"),
            )

            LongField(
                label = "Your practice and priorities",
                value = state.practiceAndPriorities,
                onValueChange = { value ->
                    onUpdate { it.copy(practiceAndPriorities = value) }
                },
                helper = "How you practise and what matters most to you. This is a " +
                    "description, not a claim about anyone else.",
                error = state.errorFor("practiceAndPriorities"),
            )

            LongField(
                label = "Your living situation and plans",
                value = state.livingSituationAndPlans,
                onValueChange = { value ->
                    onUpdate { it.copy(livingSituationAndPlans = value) }
                },
                helper = "Where you live, where you intend to live, and what you are " +
                    "working towards.",
                error = state.errorFor("livingSituationAndPlans"),
            )

            SectionDivider()

            // ── Your representative ──────────────────────────────────────────
            SectionHeader(
                title = "Who will act for you",
                subtitle = "The person on your side of this. Name them; do not put their " +
                    "contact details anywhere on this form.",
            )

            LabelledField(
                label = "Their name",
                value = state.guardianName,
                onValueChange = { value -> onUpdate { it.copy(guardianName = value) } },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                error = state.errorFor("guardianOrRepresentativeName"),
            )

            SectionHeader(title = "Their relationship to you")
            for (option in GuardianRelationship.entries) {
                ChoiceRow(
                    title = option.displayName,
                    description = "They would speak for you in this process.",
                    selected = state.guardianRelationship == option,
                    onSelect = { onUpdate { it.copy(guardianRelationship = option) } },
                )
            }

            SectionDivider()

            // ── Confirmations ────────────────────────────────────────────────
            SectionHeader(title = "Two confirmations")

            SafeguardToggle(
                title = "This is a request for marriage consideration",
                description = "It is not a way to begin a conversation, and I am not " +
                    "submitting it to get to know someone first.",
                checked = state.confirmsMarriage,
                onCheckedChange = { value -> onUpdate { it.copy(confirmsMarriage = value) } },
            )
            FieldMessage(state.errorFor("confirmsMarriageConsideration"))

            SafeguardToggle(
                title = "I accept the conduct rules above",
                description = "I have read all " +
                    "${IntroductionForm.CONDUCT_RULES.size} of them and I accept every one.",
                checked = state.confirmsConduct,
                onCheckedChange = { value -> onUpdate { it.copy(confirmsConduct = value) } },
            )
            FieldMessage(state.errorFor("confirmsConductRules"))

            Spacer(Modifier.height(spacing.md))

            PrimaryButton(
                text = "Submit this introduction",
                onClick = onSubmit,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = canSubmit,
                loading = state.submitting,
            )

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "You may submit at most " +
                    "${IntroductionLimits.MAX_PER_RECIPIENT_EVER} introduction to any one " +
                    "person, ever. You will not be told whether this was read, whether " +
                    "anyone else has submitted one, or why it concluded if it does.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun ConductRuleRow(
    rule: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minimumTouchTarget)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                text = rule,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LongField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    helper: String,
    error: String?,
) {
    val spacing = FiSabilillahTheme.spacing
    val remaining = IntroductionForm.MIN_FIELD - value.trim().length

    LabelledField(
        label = label,
        value = value,
        onValueChange = { if (it.length <= IntroductionForm.MAX_FIELD) onValueChange(it) },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        helper = if (remaining > 0) {
            "$helper At least ${IntroductionForm.MIN_FIELD} characters — " +
                "$remaining to go."
        } else {
            "$helper ${value.trim().length} of ${IntroductionForm.MAX_FIELD} characters."
        },
        error = error,
        singleLine = false,
        minLines = 5,
    )
}

@Composable
private fun FieldMessage(message: String?) {
    if (message == null) return
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.xxs,
        ),
    )
}
