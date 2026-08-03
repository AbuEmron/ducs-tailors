package org.fisabilillah.core.policy

import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Attestation
import org.fisabilillah.core.model.Campaign
import org.fisabilillah.core.model.DonationFeatureFlags
import org.fisabilillah.core.model.Organization
import org.fisabilillah.core.model.Qualification
import org.fisabilillah.core.model.QualificationReviewState
import org.fisabilillah.core.model.TeachingCapacity
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.ZakatAttestation

/**
 * What a member is and is not allowed to assert about themselves.
 *
 * Nothing in this file is a courtesy check that the UI could skip. A platform where
 * someone can type "scholar" into a field and have it rendered as a badge is a platform
 * that will, sooner or later, help a confident stranger give religious rulings to a
 * vulnerable new Muslim.
 */
public object VerificationPolicy {

    /**
     * Roles a member may add to their own account.
     *
     * Everything else is granted by the platform after a review, and the server must
     * reject a client that supplies anything outside this set — see the row-level security
     * policies in `backend/supabase/migrations/0013_row_level_security.sql`.
     */
    public fun selfAssignableRoles(): Set<AccountRole> =
        AccountRole.entries.filter { it.selfAssignable }.toSet()

    public fun canSelfAssign(role: AccountRole): Boolean = role.selfAssignable

    /**
     * Filters a requested role set down to what the requester is actually entitled to,
     * preserving roles they already hold.
     */
    public fun sanitizeRoleRequest(
        requested: Set<AccountRole>,
        currentlyHeld: Set<AccountRole>,
    ): RoleSanitizationResult {
        val permitted = requested.filter { it.selfAssignable || it in currentlyHeld }.toSet()
        val rejected = requested - permitted
        return RoleSanitizationResult(
            granted = permitted + AccountRole.COMMUNITY_MEMBER,
            rejected = rejected,
        )
    }

    /**
     * Whether a member may present themselves in this teaching capacity right now.
     *
     * A capacity requiring review is displayed as *claimed* until a reviewer confirms it,
     * and the difference is visible to students rather than buried.
     */
    public fun teachingCapacityState(
        capacity: TeachingCapacity,
        qualifications: List<Qualification>,
    ): CapacityState {
        if (!capacity.requiresQualificationReview) return CapacityState.Permitted
        val verified = qualifications.any { it.reviewState == QualificationReviewState.VERIFIED }
        return if (verified) {
            CapacityState.Permitted
        } else {
            val pending = qualifications.any {
                it.reviewState == QualificationReviewState.SUBMITTED ||
                    it.reviewState == QualificationReviewState.UNDER_REVIEW
            }
            CapacityState.ClaimedPendingReview(
                displayLabel = "${capacity.displayName} (claimed, not yet verified)",
                reviewInProgress = pending,
            )
        }
    }

    /**
     * The plain-language explanation shown with every badge.
     *
     * A verification badge that does not say what it excludes is worse than no badge: it
     * transfers trust the platform has not earned and cannot honour.
     */
    public fun badgeExplanation(level: VerificationLevel): BadgeExplanation = BadgeExplanation(
        title = level.displayName,
        whatWasChecked = when (level) {
            VerificationLevel.NONE -> "Nothing has been checked."
            VerificationLevel.EMAIL_VERIFIED -> "An email address was confirmed."
            VerificationLevel.PHONE_VERIFIED -> "A phone number was confirmed."
            VerificationLevel.IDENTITY_VERIFIED ->
                "A government identity document was checked by our verification provider."
            VerificationLevel.BACKGROUND_CHECKED ->
                "A criminal-record check was completed on the date shown."
        },
        whatItDoesNotMean = level.whatItDoesNotMean,
    )

    public fun badgeExplanation(attestation: Attestation): BadgeExplanation = BadgeExplanation(
        title = attestation.displayName,
        whatWasChecked = when (attestation) {
            Attestation.ORGANIZATION_VERIFIED -> "Registration documents were checked."
            Attestation.MASJID_AFFILIATED -> "A masjid confirmed this account represents them."
            Attestation.QUALIFICATION_VERIFIED ->
                "A stated certificate or licence was checked against the issuing body."
            Attestation.WALI_CONTACT_VERIFIED ->
                "The named guardian confirmed they act in this role."
        },
        whatItDoesNotMean = attestation.whatItDoesNotMean,
    )

    /**
     * Whether an organisation may be shown as able to receive funds.
     *
     * Registration is the whole of the test here, and it is checked at the moment of
     * asking rather than remembered from when the campaign was approved — organisation
     * verification carries an expiry date, and a lapsed one has to stop the money the same
     * day it lapses rather than the next time somebody reviews the account.
     *
     * A campaign has its own further gate on top of this one; see
     * `Campaign.canAcceptDonations`. Both must hold. That is not redundancy: this answers
     * "may this body receive money at all", the other answers "may this particular appeal
     * collect it".
     */
    public fun canOfferDonations(organization: Organization): GivingAvailability = when {
        !organization.verification.registrationChecked ->
            GivingAvailability.Unavailable(DonationFeatureFlags.unverifiedNotice)

        else -> GivingAvailability.Available
    }

    /**
     * Zakat eligibility is never something the platform decides, and never something an
     * organisation may assert about its own campaign without a recorded attestation from a
     * qualified body.
     */
    public fun zakatEligibilityFor(campaign: Campaign): ZakatEligibility {
        val attestation: ZakatAttestation = campaign.zakatAttestation
            ?: return ZakatEligibility.NotDeclared

        return ZakatEligibility.Attested(
            byName = attestation.attestedByName,
            statement = attestation.statement,
            disclaimer = attestation.disclaimer,
        )
    }
}

public data class RoleSanitizationResult(
    val granted: Set<AccountRole>,
    val rejected: Set<AccountRole>,
) {
    public val hadRejections: Boolean get() = rejected.isNotEmpty()
}

public sealed interface CapacityState {
    public data object Permitted : CapacityState
    public data class ClaimedPendingReview(
        val displayLabel: String,
        val reviewInProgress: Boolean,
    ) : CapacityState
}

public data class BadgeExplanation(
    val title: String,
    val whatWasChecked: String,
    val whatItDoesNotMean: String,
)

public sealed interface GivingAvailability {
    public data object Available : GivingAvailability
    public data class Preview(val notice: String) : GivingAvailability
    public data class Unavailable(val reason: String) : GivingAvailability
}

public sealed interface ZakatEligibility {
    public data object NotDeclared : ZakatEligibility
    public data class Attested(
        val byName: String,
        val statement: String,
        val disclaimer: String,
    ) : ZakatEligibility
}
