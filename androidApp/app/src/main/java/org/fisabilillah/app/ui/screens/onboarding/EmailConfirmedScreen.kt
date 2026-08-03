package org.fisabilillah.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * "Your email address is confirmed."
 *
 * Small, and worth its own screen. The alternative — sending the browser to whatever the
 * project's Site URL happens to be — produced a page that could not load, which meant a
 * confirmation that had actually succeeded was indistinguishable from one that had failed.
 * Somebody in that position tries again, which invalidates the link they were sent, which
 * makes the second attempt fail for real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmailConfirmedScreen(
    onContinue: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = { TopAppBar(title = { Text("Address confirmed") }) },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.lg))

            Text(
                text = "Your email address is confirmed",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = spacing.screenHorizontal)
                    .semantics { heading() },
            )
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = "Sign in with the address and password you just chose. The next few " +
                    "screens ask how you want to be contacted before anything else.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.lg))
            PrimaryButton(
                text = "Sign in",
                onClick = onContinue,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))
            PrivacyNote(
                text = "Confirming an address proves you can receive mail at it. It is not " +
                    "a check of who you are, and no part of this platform treats it as one.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
    }
}
