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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.TrustedContactsState
import org.fisabilillah.core.model.ContactMethod
import org.fisabilillah.core.model.GuardianRelationship
import org.fisabilillah.core.model.PrivateContactDetails
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactId
import org.fisabilillah.core.model.TrustedContactRole
import org.fisabilillah.core.model.UserId
import java.util.UUID

/**
 * The people a member has nominated to stand with them.
 *
 * The contact details collected here are the most sensitive thing the platform holds, and
 * they are collected for exactly one reason: so that the platform can reach a guardian on
 * the member's behalf. They are never returned by a search, never included in a profile,
 * and never shown to a counterparty — an approved introduction results in the platform
 * contacting the guardian, not in anybody being handed a phone number.
 *
 * Existing contacts are therefore listed without their details, even to their owner. There
 * is no operational reason to render a phone number onto a screen that might be over
 * someone's shoulder, and the discipline of never doing it is worth more than the
 * convenience of doing it once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrustedContactsScreen(
    state: TrustedContactsState,
    onSave: (TrustedContact) -> Unit,
    onBack: () -> Unit,
    currentUserId: UserId,
) {
    val spacing = FiSabilillahTheme.spacing
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trusted contacts") },
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
        if (state.loading) {
            Column(Modifier.padding(padding)) {
                LoadingState(label = "Loading your trusted contacts")
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            ContentCard {
                Text(
                    text = "Someone who stands with you",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "A wali, a family member, an imam, or another person you trust. " +
                        "Depending on the role you give them, they may receive formal " +
                        "introductions on your behalf, be added to conversations you have " +
                        "chosen to have supervised, or be the person we contact if " +
                        "something goes wrong while you are volunteering.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.refusal != null) {
                RefusalNotice(message = state.refusal)
            }

            SectionHeader(
                title = "Your contacts",
                subtitle = "Nominating someone does not notify them until you ask us to.",
            )

            if (state.contacts.isEmpty()) {
                EmptyState(
                    title = "Nobody nominated yet",
                    body = "You do not need a trusted contact to use this platform. You do " +
                        "need one before formal introductions can be switched on.",
                )
            } else {
                for (contact in state.contacts) {
                    ContactCard(contact)
                }
            }

            SectionDivider()

            if (!adding) {
                SecondaryButton(
                    text = "Add a trusted contact",
                    onClick = { adding = true },
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            } else {
                AddContactForm(
                    currentUserId = currentUserId,
                    onCancel = { adding = false },
                    onSave = { contact ->
                        onSave(contact)
                        adding = false
                    },
                )
            }

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun ContactCard(contact: TrustedContact) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = contact.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xs))
        DetailRow(label = "Relationship", value = contact.relationship.displayName)
        DetailRow(label = "Role", value = contact.role.displayName)
        Text(
            text = contact.role.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        DetailRow(
            label = "How we would approach them",
            value = contact.preferredContactMethod.displayName,
        )
        DetailRow(label = "Confirmation", value = contact.verificationState.displayName)

        Spacer(Modifier.height(spacing.xs))
        PrivacyNote(
            text = "Their contact details are held privately and are not displayed, " +
                "including to you. They are never shown to another member under any " +
                "circumstances.",
        )
    }
}

@Composable
private fun AddContactForm(
    currentUserId: UserId,
    onCancel: () -> Unit,
    onSave: (TrustedContact) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(GuardianRelationship.FATHER) }
    var role by remember { mutableStateOf(TrustedContactRole.WALI) }
    var method by remember { mutableStateOf(ContactMethod.IN_APP) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    SectionHeader(title = "Add a trusted contact")

    LabelledField(
        label = "Their name",
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        error = if (name.isBlank()) "A name is required." else null,
    )

    SectionHeader(title = "Their relationship to you")
    for (option in GuardianRelationship.entries) {
        ChoiceRow(
            title = option.displayName,
            description = "Recorded so that the role you give them makes sense to anyone " +
                "who has to act on it.",
            selected = relationship == option,
            onSelect = { relationship = option },
        )
    }

    SectionHeader(
        title = "What you are asking them to do",
        subtitle = "This decides what they are actually able to see and act on.",
    )
    for (option in TrustedContactRole.entries) {
        ChoiceRow(
            title = option.displayName,
            description = option.explanation,
            selected = role == option,
            onSelect = { role = option },
        )
    }

    SectionHeader(
        title = "How we should approach them",
        subtitle = "Used when the platform needs to reach them on your behalf.",
    )
    for (option in ContactMethod.entries) {
        ChoiceRow(
            title = option.displayName,
            description = when (option) {
                ContactMethod.IN_APP ->
                    "They would be invited to this platform and contacted here."
                ContactMethod.EMAIL -> "We would email them."
                ContactMethod.PHONE -> "We would telephone them."
                ContactMethod.THROUGH_MASJID ->
                    "We would go through your masjid rather than contacting them directly."
            },
            selected = method == option,
            onSelect = { method = option },
        )
    }

    SectionHeader(
        title = "Their details",
        subtitle = "Optional, and only used by the platform to reach them.",
    )

    PrivacyNote(
        text = "These details are stored separately from everything else, are never " +
            "returned by any search or profile, and are never shown to another member. " +
            "Access to them by the platform is written to the audit log.",
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )

    LabelledField(
        label = "Email address",
        value = email,
        onValueChange = { email = it },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        keyboardType = KeyboardType.Email,
    )

    LabelledField(
        label = "Phone number",
        value = phone,
        onValueChange = { phone = it },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        keyboardType = KeyboardType.Phone,
    )

    LabelledField(
        label = "Anything we should know",
        value = notes,
        onValueChange = { notes = it },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        helper = "For example, the best time of day to reach them.",
        singleLine = false,
        minLines = 3,
    )

    Spacer(Modifier.height(spacing.md))

    PrimaryButton(
        text = "Save this contact",
        onClick = {
            onSave(
                TrustedContact(
                    id = TrustedContactId(UUID.randomUUID().toString()),
                    ownerId = currentUserId,
                    name = name.trim(),
                    relationship = relationship,
                    role = role,
                    privateContact = PrivateContactDetails(
                        email = email.trim().ifBlank { null },
                        phone = phone.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                    ),
                    preferredContactMethod = method,
                ),
            )
        },
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        enabled = name.isNotBlank(),
    )

    Spacer(Modifier.height(spacing.xs))

    SecondaryButton(
        text = "Cancel",
        onClick = onCancel,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
    )
}
