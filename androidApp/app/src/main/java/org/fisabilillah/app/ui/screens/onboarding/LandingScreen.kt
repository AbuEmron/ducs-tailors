package org.fisabilillah.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * The front door.
 *
 * Deliberately plain. There is no hero image, no testimonial, no member count and no
 * "join thousands of Muslims" — every one of those is a persuasion device, and a platform
 * whose whole argument is that it will not manipulate people cannot open by doing exactly
 * that. What the screen does instead is state what this is, state what it is not, and let
 * someone read the mission before they hand over anything at all.
 */
@Composable
internal fun LandingScreen(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onMission: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xl))

            Text(
                text = "Fi Sabilillah",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = spacing.screenHorizontal)
                    .semantics { heading() },
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "A community platform for service, learning and mutual aid.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.lg))

            ContentCard {
                Text(
                    text = "This is a place to serve, learn, build, and support one another " +
                        "for the sake of Allah — not a place to seek attention, privately " +
                        "pursue people, or exploit vulnerability.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(spacing.md))

            SectionHeader(
                title = "What this is",
                subtitle = "Four things you can actually do here.",
            )
            ContentCard {
                Bullet("Volunteer your time and skills for work in your area, with what is " +
                    "expected of you stated before you commit.")
                Bullet("Learn — Qur'an, Arabic and other subjects — from people whose stated " +
                    "capacity is shown plainly, alongside what it does not mean.")
                Bullet("Ask for help when you need it, and offer it when you can, without " +
                    "your circumstances becoming public.")
                Bullet("Where marriage is the intention, a formal introduction that involves " +
                    "the wali from the beginning rather than after the fact.")
            }

            Spacer(Modifier.height(spacing.md))

            SectionHeader(
                title = "What this is not",
                subtitle = "Stated up front so nobody arrives expecting it.",
            )
            ContentCard {
                Bullet("Not a social network. There is no feed to scroll, no follower count, " +
                    "and no way to measure yourself against anyone.")
                Bullet("Not a dating app. Private romantic conversation is not a feature that " +
                    "was left out; it is one the product refuses.")
                Bullet("Not a source of religious rulings. Nothing here replaces a qualified " +
                    "scholar who knows your circumstances.")
                Bullet("Not a place to browse people. Profiles are found through the work " +
                    "someone is offering or seeking, and contact carries a stated purpose.")
            }

            Spacer(Modifier.height(spacing.lg))

            PrimaryButton(
                text = "Create an account",
                onClick = onSignUp,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.xs))
            SecondaryButton(
                text = "Sign in",
                onClick = onSignIn,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.xs))
            SecondaryButton(
                text = "Read the mission first",
                onClick = onMission,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.lg))

            PrivacyNote(
                text = "Creating an account asks for a display name, an approximate area and " +
                    "the kinds of help you are open to. A photograph is never required, and " +
                    "your exact location is never shared without a separate decision by you.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))

            Text(
                text = "This platform is for adults. Youth participation is a later phase " +
                    "and will require guardian consent and verified organisations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = spacing.xxs),
    )
}
