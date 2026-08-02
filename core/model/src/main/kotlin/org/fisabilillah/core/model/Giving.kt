package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A fundraising campaign.
 *
 * The data model is complete; the money is not. Payment processing sits behind
 * [DonationFeatureFlags] and stays off until the legal, tax, charitable-solicitation,
 * identity-verification, anti-fraud and payment-provider work in
 * `docs/payment-compliance.md` is finished. Handling other people's sadaqah is not
 * something to improvise.
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
    }

    public val canAcceptDonations: Boolean
        get() = status == CampaignStatus.ACTIVE &&
            verification == CampaignVerification.VERIFIED &&
            DonationFeatureFlags.paymentsEnabled
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
    val providerReference: String? = null,
    val receiptRef: String? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class DonationState(public val displayName: String) {
    /** Recorded by the app while payments are disabled. No money moved. */
    SANDBOX_RECORDED("Recorded in sandbox"),
    PENDING("Pending"),
    SETTLED("Settled"),
    FAILED("Failed"),
    REFUNDED("Refunded"),
    DISPUTED("Disputed"),
}

/**
 * Compile-time switches for the giving module.
 *
 * Payments are off. Turning them on is not a code change alone — see
 * `docs/payment-compliance.md` for the checklist that has to be complete first.
 */
public object DonationFeatureFlags {
    public const val paymentsEnabled: Boolean = false
    public const val recurringEnabled: Boolean = false

    public const val disabledNotice: String =
        "Giving is in preview. You can see how campaigns will work, but no payment can " +
            "be taken yet: the platform is not authorised to handle charitable funds " +
            "until its registration, tax and anti-fraud checks are complete."
}
