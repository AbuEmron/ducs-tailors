package org.fisabilillah.app.ui.screens.onboarding

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

/**
 * Sign in.
 *
 * Two fields and one button. The restraint is the point: this is the screen a person meets
 * when they are already slightly anxious — they have forgotten something, or they are on a
 * new phone — and every extra element on it is a thing to be confused by.
 *
 * The failure message is whatever the authentication layer returned, unedited. That layer
 * gives the same answer for a wrong password and an address with no account, deliberately,
 * so this screen must not try to be more helpful than it: "we don't have that address"
 * would tell anyone with a list of addresses which of the people they know are members
 * here, and on this platform that is not a harmless fact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignInScreen(
    onSignIn: suspend (email: String, password: String) -> String?,
    onSignUp: () -> Unit,
    onRecover: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !working

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") },
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
                title = "Welcome back",
                subtitle = "Sign in with the address you registered.",
            )

            if (error != null) {
                RefusalNotice(message = error!!)
            }

            LabelledField(
                label = "Email address",
                value = email,
                onValueChange = { email = it; error = null },
                keyboardType = KeyboardType.Email,
                enabled = !working,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            LabelledField(
                label = "Password",
                value = password,
                onValueChange = { password = it; error = null },
                keyboardType = KeyboardType.Password,
                secret = true,
                enabled = !working,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.sm))

            PrimaryButton(
                text = "Sign in",
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        error = onSignIn(email.trim(), password)
                        working = false
                    }
                },
                enabled = canSubmit,
                loading = working,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.sm))

            PrivacyNote(
                text = "Your password is checked by the server and never stored on this " +
                    "device. Staying signed in keeps a token that can be revoked, not your " +
                    "password.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionDivider()

            SecondaryButton(
                text = "Create an account instead",
                onClick = onSignUp,
                enabled = !working,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.xs))
            SecondaryButton(
                text = "I cannot get into my account",
                onClick = onRecover,
                enabled = !working,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.md))
        }
    }
}
