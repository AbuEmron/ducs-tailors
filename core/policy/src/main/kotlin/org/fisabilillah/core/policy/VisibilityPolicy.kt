package org.fisabilillah.core.policy

import org.fisabilillah.core.model.ApproximateLocation
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.Contactability
import org.fisabilillah.core.model.ExactLocation
import org.fisabilillah.core.model.LocationPrecision
import org.fisabilillah.core.model.NameVisibility
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.Place
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.ProfileImageStyle
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.TrustLabel
import org.fisabilillah.core.model.TrustRecord
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.VisibleProfile

/** Who is doing the looking. */
public data class Viewer(
    val id: UserId?,
    val gender: org.fisabilillah.core.model.Gender?,
    val verificationLevel: VerificationLevel = VerificationLevel.NONE,
    val organizationIds: Set<OrganizationId> = emptySet(),
    val isStaff: Boolean = false,
    /** Set when the viewer is an organiser of something the subject has committed to. */
    val isOrganizerOfSharedCommitment: Boolean = false,
) {
    public companion object {
        /** A signed-out visitor. Sees the least of anyone. */
        public val ANONYMOUS: Viewer = Viewer(id = null, gender = null)
    }
}

/**
 * Decides what one member is allowed to see of another.
 *
 * The UI never makes these decisions. A screen that receives a [VisibleProfile] cannot
 * accidentally render a real name or a precise location, because those fields are simply
 * absent unless this object put them there.
 */
public object VisibilityPolicy {

    /** Whether [subject] appears in search results and discovery for [viewer] at all. */
    public fun isDiscoverable(
        subject: Profile,
        safeguards: UserSafeguards,
        viewer: Viewer,
        sharedOrganizationIds: Set<OrganizationId>,
        viewerBlockedBySubject: Boolean = false,
    ): Boolean {
        if (!subject.isActive) return viewer.isStaff
        if (viewer.isStaff) return true
        if (viewer.id == subject.id) return true
        if (viewerBlockedBySubject) return false

        return admits(
            scope = safeguards.profileDiscoverableBy,
            viewer = viewer,
            subjectGender = subject.gender,
            sharedOrganizationIds = sharedOrganizationIds,
        )
    }

    public fun visibleProfile(
        subject: Profile,
        safeguards: UserSafeguards,
        trust: TrustRecord,
        viewer: Viewer,
        sharedOrganizationIds: Set<OrganizationId>,
        contactability: Contactability,
    ): VisibleProfile {
        val isSelf = viewer.id == subject.id
        val sameGender = viewer.gender != null && viewer.gender == subject.gender

        val realName = when {
            isSelf || viewer.isStaff -> subject.realName
            else -> when (safeguards.nameVisibility) {
                NameVisibility.REAL_NAME_PUBLIC -> subject.realName
                NameVisibility.REAL_NAME_TO_VERIFIED ->
                    subject.realName.takeIf {
                        viewer.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED
                    }
                NameVisibility.REAL_NAME_TO_ORGANIZERS ->
                    subject.realName.takeIf { viewer.isOrganizerOfSharedCommitment }
                NameVisibility.DISPLAY_NAME_ONLY -> null
            }
        }

        val imageVisible = isSelf || admits(
            scope = safeguards.profileImageVisibleTo,
            viewer = viewer,
            subjectGender = subject.gender,
            sharedOrganizationIds = sharedOrganizationIds,
        )
        val imageStyle = if (imageVisible) subject.imageStyle else ProfileImageStyle.INITIALS
        val imageUrl = subject.imageUrl.takeIf {
            imageVisible && subject.imageStyle == ProfileImageStyle.PHOTOGRAPH
        }

        return VisibleProfile(
            id = subject.id,
            displayName = subject.displayName,
            realName = realName,
            gender = subject.gender,
            imageStyle = imageStyle,
            imageUrl = imageUrl,
            locationLabel = locationLabel(subject.place, safeguards.locationPrecision, isSelf),
            languages = subject.languages,
            skills = subject.skills,
            areasWillingToHelp = subject.areasWillingToHelp,
            teachingCapacity = subject.teachingCapacity,
            verificationLevel = subject.verificationLevel,
            attestations = subject.attestations,
            trustLabels = TrustPolicy.publicLabels(subject, trust),
            contributionStatement = subject.contributionStatement,
            availability = subject.availability,
            contactability = contactability,
        ).let { if (sameGender || isSelf || viewer.isStaff) it else withoutImageDetail(it, safeguards) }
    }

    private fun withoutImageDetail(
        profile: VisibleProfile,
        safeguards: UserSafeguards,
    ): VisibleProfile =
        if (safeguards.profileImageVisibleTo.excludesOppositeGender) {
            profile.copy(imageStyle = ProfileImageStyle.INITIALS, imageUrl = null)
        } else {
            profile
        }

    /** The place label at the precision the subject chose. */
    public fun locationLabel(
        place: Place,
        precision: LocationPrecision,
        isSelf: Boolean,
    ): String? {
        if (isSelf) return place.exact?.addressLine1 ?: place.approximate.label
        val approximate: ApproximateLocation = place.approximate
        return when (precision) {
            LocationPrecision.EXACT -> approximate.label
            LocationPrecision.NEIGHBOURHOOD -> approximate.label
            LocationPrecision.CITY -> approximate.city
            LocationPrecision.REGION -> approximate.region ?: approximate.countryCode
            LocationPrecision.HIDDEN -> null
        }
    }

    /**
     * The street address of a help request.
     *
     * Returns null unless the requester has explicitly released it to this viewer. A
     * volunteer sees where to go once they have been accepted and the requester has
     * agreed — not while browsing.
     */
    public fun exactAddressFor(
        request: ServiceRequest,
        viewer: Viewer,
    ): ExactLocation? {
        val viewerId = viewer.id ?: return null
        if (viewer.isStaff) return request.place.exact
        return if (request.exactLocationVisibleTo(viewerId)) request.place.exact else null
    }

    /** Whether [scope] lets [viewer] through. */
    public fun admits(
        scope: AudienceScope,
        viewer: Viewer,
        subjectGender: org.fisabilillah.core.model.Gender,
        sharedOrganizationIds: Set<OrganizationId>,
    ): Boolean = when (scope) {
        AudienceScope.NOBODY -> false
        AudienceScope.EVERYONE -> true
        AudienceScope.VERIFIED_ONLY ->
            viewer.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED

        AudienceScope.MY_ORGANIZATIONS_ONLY -> sharedOrganizationIds.isNotEmpty()
        AudienceScope.SAME_GENDER_ONLY -> viewer.gender == subjectGender
        AudienceScope.SAME_GENDER_VERIFIED_ONLY ->
            viewer.gender == subjectGender &&
                (viewer.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED)
    }
}

/**
 * Turns private behaviour into the handful of public labels described in the product
 * principles.
 *
 * Note what this cannot do: there is no method here that returns a number, a percentage,
 * or anything sortable. If a future screen wants to rank members by reliability, it will
 * find there is nothing to rank them by, which is the intended outcome.
 */
public object TrustPolicy {

    public fun publicLabels(profile: Profile, trust: TrustRecord): List<TrustLabel> {
        val labels = mutableListOf<TrustLabel>()

        if (profile.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED) {
            labels += TrustLabel.IDENTITY_VERIFIED
        }
        if (profile.verificationLevel atLeast VerificationLevel.BACKGROUND_CHECKED) {
            labels += TrustLabel.BACKGROUND_CHECKED
        }
        when {
            trust.commitmentsCompleted >= 20 -> labels += TrustLabel.COMPLETED_TWENTY_COMMITMENTS
            trust.commitmentsCompleted >= 5 -> labels += TrustLabel.COMPLETED_FIVE_COMMITMENTS
        }
        if (trust.qualificationsVerified > 0) labels += TrustLabel.QUALIFICATION_CONFIRMED
        if (trust.activitiesOrganizedCompleted >= 3) labels += TrustLabel.RELIABLE_ORGANIZER
        if (trust.activeRestrictions == 0 && trust.boundaryViolationsUpheld == 0 &&
            !trust.isNew
        ) {
            labels += TrustLabel.NO_UNRESOLVED_SAFETY_RESTRICTIONS
        }
        if (trust.isNew) labels += TrustLabel.NEW_MEMBER

        return labels
    }

    /**
     * A plain-language summary for an organiser deciding whether to accept someone onto a
     * sensitive activity. Shown only to that organiser, only for that decision, and never
     * stored on the volunteer's profile.
     */
    public fun organizerSummary(trust: TrustRecord): String {
        if (trust.isNew) {
            return "New to the platform. Nothing is known about their reliability yet — " +
                "which is not a mark against them."
        }
        val parts = mutableListOf<String>()
        parts += "${trust.commitmentsCompleted} commitments completed"
        if (trust.noShows > 0) parts += "${trust.noShows} missed without notice"
        if (trust.commitmentsCancelledLate > 0) {
            parts += "${trust.commitmentsCancelledLate} cancelled late"
        }
        if (trust.activeRestrictions > 0) parts += "has an active safety restriction"
        return parts.joinToString(", ")
    }

    /**
     * Whether the platform should quietly hold someone back from sensitive work.
     * Advisory to the organiser, never an automatic exclusion.
     */
    public fun warrantsOrganizerAttention(trust: TrustRecord): Boolean =
        trust.activeRestrictions > 0 ||
            trust.boundaryViolationsUpheld > 0 ||
            (trust.noShows >= 3 && trust.noShows * 2 > trust.commitmentsCompleted)
}
