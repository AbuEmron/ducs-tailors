package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A fundraising campaign.
 *
 * Payments used to sit behind a single compile-time flag that was false for every campaign
 * at once. That flag is gone, and what replaces it is deliberately narrower rather than
 * wider: [paymentsEnabled] is per campaign, is set by an administrator after verification,
 * and is rejected by the database unless the campaign and its receiving organisation are
 * both verified. A global switch is the wrong shape for this, because the question "may
 * this money be taken" is never a question about the platform. It is a question about one
 * campaign, one organisation, and one set of documents somebody checked.
 *
 * The compliance checklist in `docs/payment-compliance.md` has not gone away. Most of it is
 * organisational rather than technical — charitable-solicitation registration, tax
 * treatment, KYB on the receiving organisation — and none of it is asserted by this code.
 * What this code does is refuse to let money move for a campaign nobody has verified.
 */
@Serializable
public data class Campaign(
    val id: CampaignId,
    val organizationId: OrganizationId,
    val title: String,
    val summary: String,
    val fundType: FundType,
    val goal: Money?,
    val raised: Money,
    val currencyCode: String,
    /**
     * Never set by an organisation on its own behalf. Flipping this requires an
     * attestation from a qualified body, recorded in [zakatAttestation].
     */
    val zakatEligible: Boolean = false,
    val zakatAttestation: ZakatAttestation? = null,
    val restrictedFundNotes: String? = null,
    val verification: CampaignVerification = CampaignVerification.PENDING,
    /**
     * Whether this campaign may take money.
     *
     * Never set by a client. `trg_campaigns_guard_payments` in migration 0019 rejects the
     * write unless the campaign is verified and its organisation's registration has been
     * checked, and the column is not in the update policy's reach for an organisation
     * administrator. The field is here so the app can render honestly, not so the app can
     * decide.
     */
    val paymentsEnabled: Boolean = false,
    val allowsRecurring: Boolean = false,
    val allowsAnonymous: Boolean = true,
    val startsAt: Timestamp? = null,
    val endsAt: Timestamp? = null,
    val status: CampaignStatus = CampaignStatus.DRAFT,
    val updates: List<CampaignUpdate> = emptyList(),
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(title.isNotBlank()) { "A campaign needs a title" }
        require(!zakatEligible || zakatAttestation != null) {
            "A campaign cannot be marked zakat eligible without a recorded attestation"
        }
        require(!paymentsEnabled || verification == CampaignVerification.VERIFIED) {
            "A campaign cannot take payments before it is verified"
        }
        require(!allowsRecurring || DonationFeatureFlags.recurringEnabled) {
            "Recurring giving is not available"
        }
    }

    public val canAcceptDonations: Boolean
        get() = status == CampaignStatus.ACTIVE &&
            verification == CampaignVerification.VERIFIED &&
            paymentsEnabled

    /**
     * Why this campaign cannot take money, in words a donor should see, or null when it
     * can. Deliberately does not distinguish "not verified" from "verification rejected" —
     * a rejected campaign is a moderation matter and saying so publicly invites argument
     * with the donor rather than with the reviewer.
     */
    public val givingUnavailableReason: String?
        get() = when {
            canAcceptDonations -> null
            status != CampaignStatus.ACTIVE -> "This campaign is not currently collecting."
            else -> DonationFeatureFlags.unverifiedNotice
        }
}

@Serializable
public enum class FundType(public val displayName: String) {
    SADAQAH("Sadaqah"),
    ZAKAT("Zakat"),
    EMERGENCY_AID("Emergency aid"),
    GENERAL_OPERATIONS("General operations"),
    RESTRICTED_PROJECT("Restricted project fund"),
}

/**
 * A record that a qualified organisation or scholar has assessed a campaign's zakat
 * eligibility. The platform records the attestation; it does not make the ruling.
 */
@Serializable
public data class ZakatAttestation(
    val attestedByOrganizationId: OrganizationId,
    val attestedByName: String,
    val statement: String,
    val documentRef: String? = null,
    val attestedAt: Timestamp,
    val recordedBy: UserId,
) {
    public val disclaimer: String =
        "Zakat eligibility was stated by the named body, not by this platform. If your " +
            "circumstances are unusual, ask a qualified scholar who knows them."
}

@Serializable
public enum class CampaignVerification(public val displayName: String) {
    PENDING("Awaiting verification"),
    VERIFIED("Verified"),
    REJECTED("Not verified"),
    SUSPENDED("Suspended"),
}

@Serializable
public enum class CampaignStatus(public val displayName: String) {
    DRAFT("Draft"),
    PENDING_REVIEW("Awaiting review"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    CLOSED("Closed"),
}

@Serializable
public data class CampaignUpdate(
    val postedAt: Timestamp,
    val postedBy: UserId,
    val title: String,
    val body: String,
)

@Serializable
public data class Donation(
    val id: DonationId,
    val campaignId: CampaignId,
    val donorId: UserId?,
    val amount: Money,
    val anonymous: Boolean = false,
    val recurring: Boolean = false,
    val state: DonationState = DonationState.SANDBOX_RECORDED,
    /**
     * The payment processor's identifier for this attempt. Opaque, and deliberately the
     * only thing about the payment that is stored here: no card number, no last four
     * digits, no expiry, no name on the card. Everything the donor typed was typed on
     * the processor's own page and never reached this application.
     */
    val providerReference: String? = null,
    val receiptRef: String? = null,
    /**
     * What the campaign actually receives, after the processor's fee. Null until the
     * payment settles, because until then it is a guess. Shown to the donor because a
     * donor who believes all of it arrives has been misled by omission.
     */
    val netAmount: Money? = null,
    val settledAt: Timestamp? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {
    init {
        require(netAmount == null || netAmount.currencyCode == amount.currencyCode) {
            "A donation's net amount must be in the currency it was given in"
        }
        require(!recurring || DonationFeatureFlags.recurringEnabled) {
            "Recurring giving is not available"
        }
    }
}

@Serializable
public enum class DonationState(public val displayName: String) {
    /** Recorded before payments existed. No money moved. Retained, never created. */
    SANDBOX_RECORDED("Recorded in sandbox"),

    /**
     * A checkout page has been opened and the donor has not finished. Most donations that
     * stay in this state forever are people who changed their mind, which is their right,
     * and nothing chases them.
     */
    AWAITING_PAYMENT("Awaiting payment"),

    /** The processor has the money and has not yet confirmed it as final. */
    PENDING("Pending"),
    SETTLED("Settled"),
    FAILED("Failed"),
    REFUNDED("Refunded"),
    DISPUTED("Disputed"),
    ;

    /** Whether this donation should count towards a campaign's total. */
    public val counts: Boolean get() = this == SETTLED
}

/** The payment processors this platform can use. */
@Serializable
public enum class PaymentProvider(public val displayName: String) {
    STRIPE("Stripe"),
}

/**
 * A checkout page to send the donor to.
 *
 * The url is the processor's own hosted page. It is opened in a browser rather than a
 * web view inside the app, so the donor can see the address bar and the padlock and
 * satisfy themselves about who they are paying — which is not possible in a web view, and
 * is the single easiest thing a fake donation flow imitates.
 */
public data class CheckoutSession(
    val url: String,
    val donationId: DonationId,
    val providerReference: String,
    val expiresAt: Timestamp,
) {
    init {
        require(url.startsWith("https://")) { "A checkout page must be served over https" }
    }
}

/**
 * The bounds a single donation must fall within.
 *
 * A floor exists because a payment processor's fixed fee makes a very small donation cost
 * more to collect than it delivers, and taking it anyway is not generosity, it is waste. A
 * ceiling exists because large amounts carry anti-money-laundering obligations this
 * platform has not yet built; somebody wanting to give more than the ceiling is asked to
 * arrange it with the organisation directly, which is a slower path with a human in it.
 */
public object GivingLimits {
    public const val MINIMUM_MINOR_UNITS: Long = 100
    public const val MAXIMUM_MINOR_UNITS: Long = 500_000

    public fun minimum(currencyCode: String): Money = Money(MINIMUM_MINOR_UNITS, currencyCode)
    public fun maximum(currencyCode: String): Money = Money(MAXIMUM_MINOR_UNITS, currencyCode)

    public const val ceilingNotice: String =
        "For a gift larger than this, please contact the organisation directly. Amounts " +
            "above the limit need checks this platform cannot do automatically."
}

/**
 * Switches for the giving module that are still off.
 *
 * `paymentsEnabled` used to live here as a compile-time constant and no longer exists:
 * whether money may move is a property of a verified campaign, not of the build. See
 * [Campaign.paymentsEnabled].
 */
public object DonationFeatureFlags {
    /**
     * Recurring giving is off. It is not a matter of wiring a subscription: a standing
     * mandate needs strong customer authentication handled at the point the mandate is
     * created rather than at each charge, a cancellation route the donor can find without
     * asking anyone, and a position on what happens to a monthly gift when the campaign it
     * was for closes. None of those exist.
     */
    public const val recurringEnabled: Boolean = false

    public const val unverifiedNotice: String =
        "This campaign cannot take donations yet. Its registration documents have not " +
            "been checked, and this platform does not pass money to an organisation " +
            "nobody has verified."

    public const val feeNotice: String =
        "The payment processor takes a fee from each donation. There is no platform fee " +
            "on top of it — nothing is deducted for running this service. The exact " +
            "amount that reached the campaign is shown on your record once it settles."
}
