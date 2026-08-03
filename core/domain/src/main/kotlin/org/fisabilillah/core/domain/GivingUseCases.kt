package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.Campaign
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.CampaignVerification
import org.fisabilillah.core.model.CheckoutSession
import org.fisabilillah.core.model.Donation
import org.fisabilillah.core.model.DonationFeatureFlags
import org.fisabilillah.core.model.DonationId
import org.fisabilillah.core.model.DonationState
import org.fisabilillah.core.model.GivingLimits
import org.fisabilillah.core.model.Money
import org.fisabilillah.core.model.Organization
import org.fisabilillah.core.policy.ValidationError

/**
 * Taking somebody's sadaqah.
 *
 * The shape of this file follows one rule: **the client never decides anything that costs
 * money.** The donor chooses an amount and a campaign, and that is the whole of their
 * input. Which organisation receives it, in what currency, whether that campaign is
 * allowed to collect at all, and whether the payment actually happened are every one
 * decided away from the device — the first three from the campaign row, the fourth from a
 * signed webhook the app cannot forge or even observe.
 *
 * The consequence worth stating plainly: **nothing in this file marks a donation as
 * settled.** [DonateUseCase] can only ever produce an `AWAITING_PAYMENT` record. A
 * donation becomes real when the payment processor says so, through
 * `supabase/functions/stripe-webhook`, and a client session has no write path to that
 * state at all. If a future change adds one, it has made it possible to claim a donation
 * that never happened.
 */
public class DonateUseCase(
    private val campaigns: CampaignRepository,
    private val organizations: OrganizationRepository,
    private val donations: DonationRepository,
    private val payments: PaymentGateway,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    /**
     * What the donor supplies.
     *
     * Note what is absent: no currency, no organisation, no fee, no net amount. Passing a
     * currency from the client would let somebody offer £5 and be charged 5 of something
     * more expensive; it is read from the campaign instead.
     */
    public data class Command(
        val campaignId: CampaignId,
        val amountMinorUnits: Long,
        val anonymous: Boolean = false,
        val intention: String? = null,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<CheckoutSession> {
        if (!payments.isConfigured) {
            return Outcome.Refused(
                "Giving is not available in this version of the app.",
            )
        }

        val campaign = campaigns.find(command.campaignId)
            ?: return Outcome.NotFound("that campaign")

        val refusal = refusalFor(campaign, organizations.find(campaign.organizationId))
        if (refusal != null) return Outcome.Refused(refusal)

        val errors = validate(command, campaign.currencyCode)
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val now = clock.now()
        val donation = Donation(
            id = DonationId(ids.newId()),
            campaignId = campaign.id,
            donorId = principal.userId,
            amount = Money(command.amountMinorUnits, campaign.currencyCode),
            anonymous = command.anonymous,
            state = DonationState.AWAITING_PAYMENT,
            createdAt = now,
            updatedAt = now,
        )

        val session = payments.startCheckout(
            PaymentGateway.CheckoutRequest(
                donationId = donation.id,
                campaignId = campaign.id,
                campaignTitle = campaign.title,
                amount = donation.amount,
                donorId = principal.userId,
                anonymous = command.anonymous,
            ),
        ) ?: return Outcome.Refused(
            "The payment page could not be opened. Nothing has been charged. Please try " +
                "again in a moment.",
        )

        donations.save(donation.copy(providerReference = session.providerReference))

        // Recorded at the *attempt*, not at settlement, and deliberately: a pattern of
        // started-and-abandoned donations against one campaign is a fraud signal, and it
        // is invisible if only completed payments are written down.
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.DONATION_STARTED,
                subjectType = "campaign",
                subjectId = campaign.id.value,
                summary = "Started a donation of ${describe(donation.amount)} to " +
                    "\"${campaign.title}\"",
                metadata = mapOf("donation" to donation.id.value),
                occurredAt = now,
            ),
        )

        return Outcome.Success(session)
    }

    /**
     * Why this campaign may not be given to, or null when it may.
     *
     * The organisation is re-checked here even though enabling payments already required
     * it, because verification expires. A campaign switched on in March by an organisation
     * whose registration lapsed in June must stop collecting in June, and the only way
     * that happens without somebody remembering is if the check runs at the point of
     * giving rather than at the point of enabling.
     */
    private fun refusalFor(campaign: Campaign, organization: Organization?): String? = when {
        organization == null ->
            "This campaign's organisation could not be found, so nothing can be sent to it."

        !organization.verification.registrationChecked ->
            DonationFeatureFlags.unverifiedNotice

        campaign.verification != CampaignVerification.VERIFIED ->
            DonationFeatureFlags.unverifiedNotice

        !campaign.canAcceptDonations ->
            campaign.givingUnavailableReason ?: DonationFeatureFlags.unverifiedNotice

        else -> null
    }

    private fun validate(command: Command, currencyCode: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (command.amountMinorUnits < GivingLimits.MINIMUM_MINOR_UNITS) {
            errors += ValidationError(
                field = "amount",
                message = "The smallest donation is " +
                    describe(GivingLimits.minimum(currencyCode)) +
                    ". Below that the processing fee takes most of it.",
            )
        }
        if (command.amountMinorUnits > GivingLimits.MAXIMUM_MINOR_UNITS) {
            errors += ValidationError(
                field = "amount",
                message = "The largest donation through the app is " +
                    describe(GivingLimits.maximum(currencyCode)) + ". " +
                    GivingLimits.ceilingNotice,
            )
        }
        if (command.intention != null && command.intention.length > MAX_INTENTION) {
            errors += ValidationError(
                field = "intention",
                message = "Keep this under $MAX_INTENTION characters.",
            )
        }
        if (command.recurringRequested) {
            errors += ValidationError(
                field = "recurring",
                message = "Regular giving is not available yet.",
            )
        }
        return errors
    }

    private companion object {
        const val MAX_INTENTION = 200

        /** Always false today; present so the field cannot be silently honoured later. */
        val Command.recurringRequested: Boolean get() = false
    }
}

/** A donor's own record of what they gave. Never another person's. */
public class MyDonationsUseCase(
    private val donations: DonationRepository,
    private val campaigns: CampaignRepository,
) {
    public data class Line(
        val donation: Donation,
        val campaignTitle: String,
        /** Present once settled, absent before. Never estimated. */
        val reachedTheCampaign: Money?,
    )

    public suspend operator fun invoke(principal: Principal): Outcome<List<Line>> {
        val mine = donations.forDonor(principal.userId)
            .filter { it.state != DonationState.SANDBOX_RECORDED }
            .sortedByDescending { it.createdAt }

        return Outcome.Success(
            mine.map { donation ->
                Line(
                    donation = donation,
                    campaignTitle = campaigns.find(donation.campaignId)?.title
                        ?: "A campaign that has since closed",
                    reachedTheCampaign = donation.netAmount,
                )
            },
        )
    }
}

/**
 * Allowing a campaign to take money.
 *
 * Separated from ordinary campaign editing on purpose. An organisation administrator can
 * change their campaign's title and description; they cannot switch on its ability to
 * collect funds, because that decision is about documents somebody else has checked.
 */
public class EnableCampaignPaymentsUseCase(
    private val campaigns: CampaignRepository,
    private val organizations: OrganizationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {
    public suspend fun enable(
        principal: Principal,
        campaignId: CampaignId,
        reason: String,
    ): Outcome<Campaign> = change(principal, campaignId, enabled = true, reason = reason)

    public suspend fun disable(
        principal: Principal,
        campaignId: CampaignId,
        reason: String,
    ): Outcome<Campaign> = change(principal, campaignId, enabled = false, reason = reason)

    private suspend fun change(
        principal: Principal,
        campaignId: CampaignId,
        enabled: Boolean,
        reason: String,
    ): Outcome<Campaign> {
        if (!principal.isSafetyAdmin) {
            return Outcome.Refused(
                "Only a platform administrator can decide whether a campaign may collect " +
                    "money.",
            )
        }
        if (reason.isBlank()) {
            return Outcome.Invalid(
                listOf(ValidationError("reason", "Say why. This is a permanent record.")),
            )
        }

        val campaign = campaigns.find(campaignId) ?: return Outcome.NotFound("that campaign")

        if (enabled) {
            if (campaign.verification != CampaignVerification.VERIFIED) {
                return Outcome.Refused(
                    "This campaign has not been verified, so it cannot be allowed to " +
                        "collect.",
                )
            }
            val organization = organizations.find(campaign.organizationId)
                ?: return Outcome.NotFound("that campaign's organisation")
            if (!organization.verification.registrationChecked) {
                return Outcome.Refused(
                    "${organization.name} has not completed registration checks.",
                )
            }
        }

        val now = clock.now()
        val saved = campaigns.save(campaign.copy(paymentsEnabled = enabled, updatedAt = now))

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = if (enabled) {
                    AuditAction.CAMPAIGN_PAYMENTS_ENABLED
                } else {
                    AuditAction.CAMPAIGN_PAYMENTS_DISABLED
                },
                subjectType = "campaign",
                subjectId = campaign.id.value,
                summary = "\"${campaign.title}\": $reason",
                occurredAt = now,
            ),
        )
        return Outcome.Success(saved)
    }
}

/**
 * The port to a payment processor.
 *
 * One method, because one method is all the application is allowed to do: ask for a page
 * to send somebody to. There is deliberately no `charge`, no `capture`, and no
 * `markAsPaid`. An implementation that added one would be moving the decision about
 * whether money moved into a process this application controls, and the whole design here
 * rests on it not being.
 */
public interface PaymentGateway {

    public data class CheckoutRequest(
        val donationId: DonationId,
        val campaignId: CampaignId,
        val campaignTitle: String,
        val amount: Money,
        val donorId: org.fisabilillah.core.model.UserId,
        val anonymous: Boolean,
    )

    /**
     * False when this build has no payment processor behind it at all — a fixture, a test,
     * or a deployment where the keys were never set. Distinguished from a processor that
     * is configured but temporarily unreachable, because the two need different words: one
     * is "this is not available", the other is "try again shortly".
     */
    public val isConfigured: Boolean get() = true

    /** Null when the processor could not be reached. Never throws for an ordinary failure. */
    public suspend fun startCheckout(request: CheckoutRequest): CheckoutSession?
}

/**
 * The gateway used when nothing is wired up.
 *
 * It refuses rather than pretending, and it is the default in [CoreGraph] so that a build
 * without payment configuration cannot quietly appear to take money.
 */
public object NoPaymentProcessor : PaymentGateway {
    override val isConfigured: Boolean get() = false
    override suspend fun startCheckout(request: PaymentGateway.CheckoutRequest): CheckoutSession? =
        null
}

/** Storage for donation records. */
public interface DonationRepository {
    public suspend fun find(id: DonationId): Donation?
    public suspend fun save(donation: Donation): Donation
    public suspend fun forDonor(donorId: org.fisabilillah.core.model.UserId): List<Donation>
    public suspend fun forCampaign(campaignId: CampaignId): List<Donation>
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
