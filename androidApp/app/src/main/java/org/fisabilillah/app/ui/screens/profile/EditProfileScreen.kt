package org.fisabilillah.app.ui.screens.profile

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
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.ProfileImageStyle

/** The ceiling the model enforces on a contribution statement. */
private const val MAX_STATEMENT = 600

/**
 * Editing your profile.
 *
 * The editable fields are the ones that help someone decide whether you can help them or
 * they can help you. There is no field for how you look, nothing about marital status
 * outside the formal introduction workflow, and no free-text box that invites a person to
 * describe themselves as anything other than someone with something to offer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileScreen(
    profile: Profile?,
    saving: Boolean,
    onSave: (Profile) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit your profile") },
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
        if (profile == null) {
            Column(Modifier.padding(padding)) { LoadingState(label = "Loading your profile") }
            return@Scaffold
        }

        var displayName by remember(profile.id) { mutableStateOf(profile.displayName) }
        var statement by remember(profile.id) {
            mutableStateOf(profile.contributionStatement.orEmpty())
        }
        var cityLabel by remember(profile.id) {
            mutableStateOf(profile.place.approximate.label)
        }
        var city by remember(profile.id) { mutableStateOf(profile.place.approximate.city) }
        var imageStyle by remember(profile.id) { mutableStateOf(profile.imageStyle) }

        ScreenColumn(contentPadding = padding) {

            Spacer(Modifier.height(spacing.xs))

            SectionHeader(title = "How you are shown")

            LabelledField(
                label = "Display name",
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                helper = "This is what other members see. It does not have to be your " +
                    "legal name.",
                error = if (displayName.isBlank()) "A display name is required." else null,
            )

            LabelledField(
                label = "What you are here to contribute",
                value = statement,
                onValueChange = { if (it.length <= MAX_STATEMENT) statement = it },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                helper = "Optional. ${statement.length} of $MAX_STATEMENT characters.",
                singleLine = false,
                minLines = 4,
            )

            SectionHeader(
                title = "Your area",
                subtitle = "Approximate only. Your exact address is never derived from this.",
            )

            LabelledField(
                label = "Area label",
                value = cityLabel,
                onValueChange = { cityLabel = it },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                helper = "For example, a neighbourhood or district name.",
            )

            LabelledField(
                label = "City",
                value = city,
                onValueChange = { city = it },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionHeader(
                title = "Profile image",
                subtitle = "A photograph is never required, and it is never the default.",
            )

            for (style in ProfileImageStyle.entries) {
                ChoiceRow(
                    title = style.displayName,
                    description = when (style) {
                        ProfileImageStyle.NONE ->
                            "Nothing is shown in place of an image."
                        ProfileImageStyle.INITIALS ->
                            "Your initials on a plain background. This is the default."
                        ProfileImageStyle.GEOMETRIC_AVATAR ->
                            "A generated pattern. Nothing about it depicts a person."
                        ProfileImageStyle.PHOTOGRAPH ->
                            "A photograph, shown only to the audience you choose under " +
                                "privacy controls."
                    },
                    selected = imageStyle == style,
                    onSelect = { imageStyle = style },
                )
            }

            Spacer(Modifier.height(spacing.md))

            ContentCard {
                Text(
                    text = "Not editable here",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Your verification level, your roles and anything a reviewer " +
                        "confirmed are set by the platform, not by you. That is what makes " +
                        "them worth anything to the person reading your profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.md))

            PrimaryButton(
                text = "Save changes",
                onClick = {
                    onSave(
                        profile.copy(
                            displayName = displayName.trim(),
                            contributionStatement = statement.trim().ifBlank { null },
                            imageStyle = imageStyle,
                            place = profile.place.copy(
                                approximate = profile.place.approximate.copy(
                                    label = cityLabel.trim(),
                                    city = city.trim(),
                                ),
                            ),
                        ),
                    )
                },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                enabled = displayName.isNotBlank(),
                loading = saving,
            )

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "Who can see your real name, your area and your image is decided " +
                    "under privacy controls, not here. Changing this page does not change " +
                    "who it is shown to.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
    }
}
