package org.fisabilillah.app.ui.screens.requests

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
import org.fisabilillah.app.ui.components.CautionButton
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Money
import org.fisabilillah.core.model.VisibleServiceRequest

/**
 * One request for help.
 *
 * Nothing on this screen is rendered that the domain layer did not hand over. The exact
 * address appears only when it was actually released to this viewer, and a support total
 * appears only when the person asking chose to show one — an unfilled need is not a
 * fundraising thermometer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequestDetailScreen(
    request: VisibleServiceRequest?,
    responding: Boolean,
    refusal: String?,
    onRespond: (String) -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request") },
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
        if (request == null) {
            ScreenColumn(contentPadding = padding) {
                EmptyState(
                    title = "This request is not available",
                    body = "It may have been fulfilled, withdrawn, or it may no longer be " +
                        "visible to you.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            SectionHeader(title = request.title, subtitle = request.category.displayName)

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text(text = request.description, style = MaterialTheme.typography.bodyLarge)
            }

            SectionHeader(title = "Details")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(label = "Asked by", value = requesterLabel(request))
                val mediator = request.mediatingOrganizationName
                if (mediator != null) {
                    DetailRow(label = "Handled by", value = mediator)
                }
                DetailRow(label = "Kind of help", value = request.category.displayName)
                DetailRow(label = "Urgency", value = request.urgency.displayName)
                DetailRow(label = "Area", value = request.locationLabel)
                DetailRow(label = "Status", value = request.status.displayName)
                DetailRow(
                    label = "Open until",
                    value = request.expiresAt.toString().take(10),
                )

                // Rendered only where the domain layer released it to this viewer. A total
                // that was never turned on does not exist here at all.
                val supportTotal = request.supportTotal
                if (supportTotal != null) {
                    DetailRow(
                        label = "Support received so far",
                        value = requestMoneyLabel(supportTotal),
                    )
                }
            }

            val exactAddress = request.exactAddress
            if (exactAddress != null) {
                SectionHeader(title = "Address")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    val lines = listOfNotNull(
                        exactAddress.addressLine1,
                        exactAddress.addressLine2,
                        exactAddress.postalCode,
                    )
                    for (line in lines) {
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(spacing.xs))
                    PrivacyNote(
                        text = "This address was shared with you because your offer of help " +
                            "was accepted. It is not public, and passing it to anyone else " +
                            "is a serious breach of the terms.",
                    )
                }
            } else {
                Column(
                    Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.xs,
                    ),
                ) {
                    PrivacyNote(
                        text = "Only the area is shown. The exact address is shared with a " +
                            "helper after the person asking has accepted their offer, and " +
                            "only if they choose to send it.",
                    )
                }
            }

            DisclaimerCard(
                title = "Before you offer",
                text = "Please only offer what you can genuinely see through. Somebody who " +
                    "is counting on help that does not arrive is worse off than before they " +
                    "asked. Do not ask for documents, do not ask what happened, and do not " +
                    "share anything about this request with anyone else.",
            )

            SectionDivider()

            SectionHeader(
                title = "Offer to help",
                subtitle = "Say what you can do and by when",
            )
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                LabelledField(
                    label = "Your message",
                    value = message,
                    onValueChange = { message = it },
                    placeholder = "What you can do, and when you could do it",
                    helper = "The person asking reads this before deciding.",
                    singleLine = false,
                    minLines = 4,
                    enabled = request.canRespond,
                )
                Spacer(Modifier.height(spacing.xs))
                PrivacyNote(
                    text = "Offering shares your display name and profile with the person " +
                        "asking. It does not share your address or contact details.",
                )
                Spacer(Modifier.height(spacing.md))
                PrimaryButton(
                    text = "Offer to help",
                    onClick = { onRespond(message) },
                    enabled = request.canRespond && message.isNotBlank(),
                    loading = responding,
                )
                if (!request.canRespond) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "This request is not taking offers at the moment. Its status " +
                            "is ${request.status.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (refusal != null) {
                Spacer(Modifier.height(spacing.xs))
                RefusalNotice(message = refusal)
            }

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Spacer(Modifier.height(spacing.lg))
                CautionButton(text = "Report this request", onClick = onReport)
            }
        }
    }
}

private fun requestMoneyLabel(money: Money): String {
    val major = money.minorUnits / 100
    val minor = money.minorUnits % 100
    val minorText = if (minor < 10) "0$minor" else minor.toString()
    return "${money.currencyCode} $major.$minorText"
}
