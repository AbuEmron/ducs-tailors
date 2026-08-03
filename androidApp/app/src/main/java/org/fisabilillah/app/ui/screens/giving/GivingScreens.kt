package org.fisabilillah.app.ui.screens.giving

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import org.fisabilillah.app.ui.components.ChoiceRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.FactChip
import org.fisabilillah.app.ui.components.LabelledField
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrimaryButton
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.domain.MyDonationsUseCase
import org.fisabilillah.core.model.Campaign
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.DonationFeatureFlags
import org.fisabilillah.core.model.DonationState
import org.fisabilillah.core.model.GivingLimits
import org.fisabilillah.core.model.Money
import org.fisabilillah.core.policy.ValidationError

/**
 * Campaigns that are collecting.
 *
 * Ordered by when they close rather than by how much they have raised. A list sorted by
 * total is a leaderboard, and a leaderboard of charitable appeals rewards the ones that are
 * already doing well at the expense of the ones that are not — which is the opposite of
 * what a hardship fund is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampaignsScreen(
    campaigns: List<Campaign>,
    loading: Boolean,
    onOpen: (CampaignId) -> Unit,
    onMyGiving: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Giving") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            LoadingState(label = "Loading campaigns", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            if (campaigns.isEmpty()) {
                EmptyState(
                    title = "No appeals are open",
                    body = "When a verified organisation opens one it will appear here.",
                )
            } else {
                for (campaign in campaigns) {
                    ContentCard(
                        onClick = { onOpen(campaign.id) },
                        contentDescription = "${campaign.title}. ${campaign.summary}",
                    ) {
                        Text(
                            text = campaign.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        Text(
                            text = campaign.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        FactChip(label = campaign.fundType.displayName)
                        if (!campaign.canAcceptDonations) {
                            Spacer(Modifier.height(spacing.xxs))
                            FactChip(label = "Not collecting")
                        }
                    }
                }
            }

            SectionDivider()
            ContentCard(onClick = onMyGiving, contentDescription = "Your giving") {
                Text(
                    text = "Your giving",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = "What you have given, and what reached the campaign after fees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.sm))
            PrivacyNote(
                text = "Appeals are listed by when they close, not by how much they have " +
                    "raised. Nothing here is ranked.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/**
 * One appeal, and the decision to give to it.
 *
 * The disclosures are above the amount field rather than below the button. Who receives
 * the money, whether it is zakat-eligible and on whose authority, and the fact that the
 * processor takes a fee are all things a person should know *before* choosing a number,
 * not while a payment page is loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DonateScreen(
    campaign: Campaign?,
    organizationName: String?,
    errors: List<ValidationError>,
    refusal: String?,
    submitting: Boolean,
    onGive: (amountMinorUnits: Long, anonymous: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    var amount by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(campaign?.title ?: "Give") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (campaign == null) {
            LoadingState(label = "Loading", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))
            if (refusal != null) RefusalNotice(message = refusal)

            ContentCard {
                Text(
                    text = campaign.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.sm))
                DetailRow(label = "Received by", value = organizationName ?: "—")
                DetailRow(label = "Fund", value = campaign.fundType.displayName)
                campaign.restrictedFundNotes?.let {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Zakat is a ruling, not a product feature. The attestation names who made it.
            val attestation = campaign.zakatAttestation
            if (attestation != null) {
                DisclaimerCard(
                    title = "Zakat eligibility was stated by ${attestation.attestedByName}",
                    text = attestation.statement + "\n\n" + attestation.disclaimer,
                )
            }

            val unavailable = campaign.givingUnavailableReason
            if (unavailable != null) {
                DisclaimerCard(title = "This appeal is not collecting", text = unavailable)
                Spacer(Modifier.height(spacing.md))
                return@ScreenColumn
            }

            SectionDivider()
            SectionHeader(title = "How much")

            LabelledField(
                label = "Amount in ${campaign.currencyCode}",
                value = amount,
                onValueChange = { entered -> amount = entered.filter { it.isDigit() || it == '.' } },
                placeholder = "25",
                helper = "Between ${describe(GivingLimits.minimum(campaign.currencyCode))} and " +
                    "${describe(GivingLimits.maximum(campaign.currencyCode))}.",
                error = errors.firstOrNull { it.field == "amount" }?.message,
                keyboardType = KeyboardType.Decimal,
                enabled = !submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            SectionHeader(title = "Whose name")
            ChoiceRow(
                title = "Give quietly",
                description = "The organisation sees the donation and not who sent it. " +
                    "Sadaqah given quietly is the default here for a reason.",
                selected = anonymous,
                onSelect = { anonymous = true },
            )
            ChoiceRow(
                title = "Let them know it was me",
                description = "Your name is shown to the receiving organisation. It is " +
                    "never shown to other members.",
                selected = !anonymous,
                onSelect = { anonymous = false },
            )

            Spacer(Modifier.height(spacing.sm))
            PrivacyNote(
                text = DonationFeatureFlags.feeNotice,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.sm))
            PrimaryButton(
                text = "Continue to payment",
                onClick = { onGive(parseMinorUnits(amount), anonymous) },
                enabled = amount.isNotBlank() && !submitting,
                loading = submitting,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )

            Spacer(Modifier.height(spacing.xs))
            PrivacyNote(
                text = "Payment is taken on the processor's own page, in your browser. " +
                    "Your card details are never typed into this app and never reach it.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/**
 * A donor's own record.
 *
 * Shows unfinished attempts as well as completed ones. A list that quietly drops the
 * abandoned ones looks tidier and hides the case that actually matters — somebody who
 * believes they gave and did not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyGivingScreen(
    lines: List<MyDonationsUseCase.Line>,
    loading: Boolean,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your giving") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            LoadingState(label = "Loading", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))

            if (lines.isEmpty()) {
                EmptyState(
                    title = "Nothing yet",
                    body = "Donations you make through the app are recorded here, with what " +
                        "reached the campaign after the processor's fee.",
                )
                return@ScreenColumn
            }

            for (line in lines) {
                ContentCard {
                    Text(
                        text = line.campaignTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(spacing.xxs))
                    DetailRow(label = "You gave", value = describe(line.donation.amount))
                    DetailRow(
                        label = "Reached the campaign",
                        value = line.reachedTheCampaign?.let { describe(it) }
                            // Not "the full amount", and not an estimate. Until the
                            // processor reports the fee, this is genuinely not known.
                            ?: "Not known yet",
                    )
                    DetailRow(label = "State", value = line.donation.state.displayName)
                    if (line.donation.state == DonationState.AWAITING_PAYMENT) {
                        Spacer(Modifier.height(spacing.xxs))
                        Text(
                            text = "This was started and not finished. Nothing has been " +
                                "charged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.sm))
            PrivacyNote(
                text = "This record is yours. It is not shown to other members, and the " +
                    "receiving organisation sees your name only on donations you chose not " +
                    "to give quietly.",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

/**
 * "12.50" to 1250.
 *
 * Returns zero for anything unparseable rather than throwing, and lets the use case
 * produce the message: a validation error the donor can read beats a crash, and the floor
 * check refuses zero anyway.
 */
private fun parseMinorUnits(entered: String): Long {
    val cleaned = entered.trim()
    if (cleaned.isEmpty()) return 0
    val parts = cleaned.split('.')
    val whole = parts[0].toLongOrNull() ?: return 0
    val fraction = when {
        parts.size < 2 -> 0L
        else -> parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    }
    return whole * 100 + fraction
}

private fun describe(money: Money): String {
    val symbol = when (money.currencyCode) {
        "GBP" -> "£"
        "USD" -> "$"
        "EUR" -> "€"
        else -> "${money.currencyCode} "
    }
    val whole = money.minorUnits / 100
    val part = money.minorUnits % 100
    return if (part == 0L) "$symbol$whole" else "$symbol$whole.${part.toString().padStart(2, '0')}"
}
