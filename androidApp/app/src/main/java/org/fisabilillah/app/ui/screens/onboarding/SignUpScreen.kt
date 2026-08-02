package org.fisabilillah.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * Account creation begins with an age gate, and the age gate is the whole screen.
 *
 * An age declaration that is one grey line of small print under a button is a formality.
 * This one is a decision a person has to make deliberately, with the consequence of getting
 * it wrong stated next to it, because the platform's child-safety posture rests entirely on
 * adults being adults here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignUpScreen(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var declaredAdult by remember { mutableStateOf(false) }
    var acceptedPurpose by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create an account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            SectionHeader(
                title = "This platform is for adults",
                subtitle = "Two things to confirm before anything else.",
            )

            ContentCard {
                Text(
                    text = "Accounts here are for people aged eighteen or over. That is not " +
                        "a formality. Volunteering brings members into contact with " +
                        "vulnerable people and, in some categories, with children, and the " +
                        "safeguards that make that acceptable assume every account belongs " +
                        "to an adult who can be held responsible for what they do.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DisclaimerCard(
                title = "Declaring an age you are not is a terms violation",
                text = "If you are under eighteen, please do not continue. Declaring an age " +
                    "you are not breaches the terms of use, and an account found to have " +
                    "done so is closed. This is also for your own protection: the " +
                    "protections a platform owes a young person are different from the ones " +
                    "it owes an adult, and we cannot give you the right ones if we have been " +
                    "told the wrong thing.",
            )

            CheckRow(
                title = "I am eighteen years of age or older",
                description = "You are making this declaration yourself, and it is recorded " +
                    "with the date on which you made it.",
                checked = declaredAdult,
                onCheckedChange = { declaredAdult = it },
            )

            CheckRow(
                title = "I understand what this platform is for",
                description = "A place to serve, learn, build and support one another for the " +
                    "sake of Allah — not a place to seek attention, privately pursue people, " +
                    "or exploit vulnerability.",
                checked = acceptedPurpose,
                onCheckedChange = { acceptedPurpose = it },
            )

            Spacer(Modifier.height(spacing.sm))

            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = declaredAdult && acceptedPurpose,
            )

            Spacer(Modifier.height(spacing.xs))

            Text(
                text = if (declaredAdult && acceptedPurpose) {
                    "Next you will set up a profile, choose the areas you can help with, and " +
                        "choose your safeguards. You can change all of it afterwards."
                } else {
                    "Both confirmations above are needed before you can continue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionDivider()

            SectionHeader(title = "If you are under eighteen")
            ContentCard {
                Text(
                    text = "There is nothing here for you yet, and that is deliberate rather " +
                        "than an oversight. Youth participation is planned as a later phase " +
                        "and will not open until three things are in place: consent from a " +
                        "parent or guardian who holds their own account, activities run only " +
                        "by organisations the platform has verified, and background checks " +
                        "on the adults involved. Until all three exist, opening the platform " +
                        "to young people would be putting a feature ahead of their safety.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.md))

            PrivacyNote(
                text = "Your date of birth is not asked for or stored. Only the declaration " +
                    "itself, and when you made it, is recorded.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))

            SecondaryButton(
                text = "I already have an account",
                onClick = onSignIn,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minimumTouchTarget)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(spacing.sm))
        Column {
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
        }
    }
}
