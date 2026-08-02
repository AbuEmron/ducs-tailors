package org.fisabilillah.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * The shared component vocabulary.
 *
 * Screens are assembled from these rather than from raw Material components, for two
 * reasons. The obvious one is consistency. The less obvious one is that several of these —
 * [SafeguardBanner], [PrivacyNote], [DisclaimerCard], [VerificationBadge] — carry safety
 * meaning, and centralising them means a screen cannot show a verification mark without
 * also showing what it does not prove.
 */

// ── Structure ────────────────────────────────────────────────────────────────

/** A page section with a heading that screen readers announce as one. */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = FiSabilillahTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) action()
    }
}

/** The standard scrolling page body, with the screen gutter already applied. */
@Composable
internal fun ScreenColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = spacing.xxl),
        content = content,
    )
}

/** A raised card. Used for anything a person can act on. */
@Composable
internal fun ContentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    val shape = MaterialTheme.shapes.medium
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs)

    Card(
        modifier = if (contentDescription != null) {
            base.semantics { this.contentDescription = contentDescription }
        } else {
            base
        },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .fillMaxWidth()
                .padding(spacing.md),
            content = content,
        )
    }
}

// ── Safety and privacy surfaces ──────────────────────────────────────────────

/**
 * The banner at the top of a conversation, stating what it is for and what terms are in
 * force. Present on every thread without exception — a person should never have to
 * remember why they are talking to someone, or who else is reading.
 */
@Composable
internal fun SafeguardBanner(
    purposeLabel: String,
    subjectTitle: String,
    requirements: List<String>,
    modifier: Modifier = Modifier,
) {
    val spacing = FiSabilillahTheme.spacing
    val safeguard = FiSabilillahTheme.safeguard

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = safeguard.safeguardActiveContainer,
        contentColor = safeguard.safeguardOnContainer,
    ) {
        Column(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = purposeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(spacing.xxs))
            Text(text = subjectTitle, style = MaterialTheme.typography.bodySmall)
            if (requirements.isNotEmpty()) {
                Spacer(Modifier.height(spacing.xs))
                for (requirement in requirements) {
                    Text(
                        text = "• $requirement",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** A quiet line explaining who can see something. Used wherever privacy is not obvious. */
@Composable
internal fun PrivacyNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = FiSabilillahTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = FiSabilillahTheme.safeguard.privateIndicator,
            modifier = Modifier.size(14.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(spacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A disclaimer that must be read, not a footnote.
 *
 * Used for peer-support limits, verification meaning, and the reminder that this app does
 * not issue religious rulings.
 */
@Composable
internal fun DisclaimerCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val spacing = FiSabilillahTheme.spacing
    val safeguard = FiSabilillahTheme.safeguard

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        shape = MaterialTheme.shapes.small,
        color = safeguard.cautionContainer,
        contentColor = safeguard.cautionOnContainer,
    ) {
        Row(Modifier.padding(spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(spacing.xs))
            Column {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(spacing.xxs))
                }
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * A verification mark.
 *
 * [whatItDoesNotMean] is required rather than optional. A badge without it transfers trust
 * the platform has not earned, and making the honest half mandatory at the type level is
 * the only way to be sure a screen cannot leave it out.
 */
@Composable
internal fun VerificationBadge(
    label: String,
    whatItDoesNotMean: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val spacing = FiSabilillahTheme.spacing
    val safeguard = FiSabilillahTheme.safeguard

    Surface(
        modifier = modifier
            .heightIn(min = 32.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "$label. $whatItDoesNotMean" },
        shape = CircleShape,
        color = safeguard.verifiedContainer,
        contentColor = safeguard.verifiedOnContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(spacing.xxs))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A neutral chip for a fact: a skill, a language, a category. Never a metric. */
@Composable
internal fun FactChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val spacing = FiSabilillahTheme.spacing
    Surface(
        modifier = modifier.heightIn(min = 28.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(spacing.xxs))
            }
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Shown when oversight is present in a conversation. Deliberately conspicuous. */
@Composable
internal fun OversightChip(role: String, modifier: Modifier = Modifier) {
    val safeguard = FiSabilillahTheme.safeguard
    FactChip(
        label = role,
        modifier = modifier,
        icon = Icons.Filled.Shield,
        container = safeguard.oversightContainer,
        content = safeguard.oversightOnContainer,
    )
}

// ── Actions ──────────────────────────────────────────────────────────────────

@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val spacing = FiSabilillahTheme.spacing
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minimumTouchTarget),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(spacing.xs))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = FiSabilillahTheme.spacing
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minimumTouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A destructive or safety action: block, report, end conversation. */
@Composable
internal fun CautionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = FiSabilillahTheme.spacing
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minimumTouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

// ── Inputs ───────────────────────────────────────────────────────────────────

@Composable
internal fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    val spacing = FiSabilillahTheme.spacing
    Column(modifier = modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
            ),
            supportingText = when {
                error != null -> {
                    { Text(error, color = MaterialTheme.colorScheme.error) }
                }
                helper != null -> {
                    { Text(helper) }
                }
                else -> null
            },
        )
    }
}

/**
 * A single safeguard switch, with its consequence spelled out underneath.
 *
 * The description is not decoration. Thirty toggles labelled only "Allow video calls" is a
 * form; the same thirty with a plain sentence about what changes is a decision someone can
 * actually make.
 */
@Composable
internal fun SafeguardToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lockedReason: String? = null,
) {
    val spacing = FiSabilillahTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minimumTouchTarget)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lockedReason != null) {
                Spacer(Modifier.height(spacing.xxs))
                PrivacyNote(lockedReason)
            }
        }
        Spacer(Modifier.width(spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled && lockedReason == null,
        )
    }
}

/** A single choice in a list of options, with an explanation. */
@Composable
internal fun ChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FiSabilillahTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xxs)
            .clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = spacing.minimumTouchTarget)
                .padding(spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Spacer(Modifier.width(spacing.sm))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(spacing.xxs))
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── States ───────────────────────────────────────────────────────────────────

/** An empty state that says what to do next rather than merely that there is nothing. */
@Composable
internal fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = FiSabilillahTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(spacing.md))
            SecondaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}

/** A refusal, shown in place. Always states the reason the domain layer gave. */
@Composable
internal fun RefusalNotice(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = FiSabilillahTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(spacing.md)) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(spacing.xs))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
internal fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading") {
    Box(
        modifier = modifier.fillMaxWidth().padding(FiSabilillahTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/**
 * Initials, in place of a photograph.
 *
 * The default representation of a person throughout the app. A photograph is shown only
 * where the subject has both uploaded one and chosen to make it visible to this viewer.
 */
@Composable
internal fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** A horizontal rule with the app's spacing already applied. */
@Composable
internal fun SectionDivider(modifier: Modifier = Modifier) {
    val spacing = FiSabilillahTheme.spacing
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier.padding(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.sm,
        ),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** A label-and-value row, for detail screens. */
@Composable
internal fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val spacing = FiSabilillahTheme.spacing
    Column(modifier = modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A row of chips that wraps. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChipRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val spacing = FiSabilillahTheme.spacing
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        for (label in labels) FactChip(label = label)
    }
}
