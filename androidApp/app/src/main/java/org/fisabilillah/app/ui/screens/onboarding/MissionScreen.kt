package org.fisabilillah.app.ui.screens.onboarding

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * The ten principles the product is built on, written out rather than implied.
 *
 * The tenth is the one that matters most and it is placed near the top rather than at the
 * end: no software can guarantee an environment free of fitnah. Every safeguard below it
 * is a reduction in risk, not a removal of it, and a screen that lists nine promises and
 * buries the honest one at the bottom is doing the same thing it claims to be against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MissionScreen(onBack: () -> Unit) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Why this exists") },
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
            Spacer(Modifier.height(spacing.sm))

            Text(
                text = "This is a place to serve, learn, build, and support one another for " +
                    "the sake of Allah — not a place to seek attention, privately pursue " +
                    "people, or exploit vulnerability.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = spacing.screenHorizontal)
                    .semantics { heading() },
            )

            Spacer(Modifier.height(spacing.md))

            DisclaimerCard(
                title = "What no software can do",
                text = "No software can guarantee an environment free of fitnah. Nothing on " +
                    "this platform should be read as such a promise. Safeguards, oversight " +
                    "and purpose-bound conversation reduce particular risks; they do not " +
                    "remove temptation, they do not make anyone trustworthy, and they cannot " +
                    "substitute for your own taqwa and judgement. If something here feels " +
                    "wrong to you, that feeling is better evidence than any setting we offer.",
            )

            SectionDivider()

            SectionHeader(
                title = "The ten principles",
                subtitle = "What the product chooses when two good things conflict.",
            )

            Principle(
                number = 1,
                title = "Benefit over entertainment",
                body = "Every screen exists so that something useful happens. There is no " +
                    "feed whose purpose is to hold your attention, and no content that is " +
                    "here only because it performs well.",
            )
            Principle(
                number = 2,
                title = "Service over popularity",
                body = "The unit this platform records is a commitment kept: whether someone " +
                    "turned up and finished what they said they would. Not posts, not likes, " +
                    "not reach.",
            )
            Principle(
                number = 3,
                title = "Privacy over exposure",
                body = "The default representation of a person is their initials. A " +
                    "photograph is never required. Someone asking for food or a lift home " +
                    "does not have to make their circumstances public to receive help.",
            )
            Principle(
                number = 4,
                title = "Substance over vanity metrics",
                body = "There are no follower counts, no leaderboards and no streaks. A " +
                    "record of your own service is private to you, is not ranked against " +
                    "anyone, and stays private permanently if that is what you want.",
            )
            Principle(
                number = 5,
                title = "Accountability over anonymity",
                body = "Consequential actions are written to a log that has no edit path and " +
                    "no delete path, for anyone, including platform administrators. Anonymity " +
                    "protects a harasser more reliably than it protects anyone else.",
            )
            Principle(
                number = 6,
                title = "Consent over unsolicited access",
                body = "Nobody can reach you simply because they found you. A conversation " +
                    "carries a stated purpose, you decide which purposes you are open to, " +
                    "and you can decline a whole category of contact without explaining why.",
            )
            Principle(
                number = 7,
                title = "Wali involvement over private romantic interaction",
                body = "Where marriage is the intention, the guardian is part of the process " +
                    "from the beginning. There is no private romantic channel to fall back " +
                    "on, because a safeguard that can be routed around is decoration.",
            )
            Principle(
                number = 8,
                title = "Community welfare over engagement addiction",
                body = "Notifications correspond to things you actually need to know — a " +
                    "commitment you made, a reply you are waiting for, a safety matter. " +
                    "Nothing is sent to bring you back into the app for its own sake.",
            )
            Principle(
                number = 9,
                title = "User-controlled safeguards over one universal standard",
                body = "People's circumstances differ, and a single set of rules imposed on " +
                    "everyone would be wrong for most of them. You set your own boundaries, " +
                    "you may always make them stricter, and no arrangement is presented as " +
                    "more religious than another.",
            )
            Principle(
                number = 10,
                title = "Honesty about the limits",
                body = "No software can guarantee an environment free of fitnah. We would " +
                    "rather say that plainly, at the start, than let a badge or a switch " +
                    "imply a safety we cannot deliver.",
            )

            SectionDivider()

            SectionHeader(title = "How to hold us to this")
            ContentCard {
                Text(
                    text = "If a feature on this platform seems to work against one of these " +
                        "principles, that is a defect and it is worth reporting as one. The " +
                        "safety centre accepts reports about the product itself, not only " +
                        "about other members.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun Principle(number: Int, title: String, body: String) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = "$number. $title",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
