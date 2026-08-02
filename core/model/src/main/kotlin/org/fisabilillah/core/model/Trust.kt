package org.fisabilillah.core.model

import kotlinx.serialization.Serializable

/**
 * The only reputation information anyone else ever sees.
 *
 * There is no score behind these, no stars, no ranking, and no way to sort people by
 * them. Each label answers a specific question a person might reasonably have before
 * agreeing to meet a stranger to move furniture or to hand their child's tutoring over
 * to someone — and nothing beyond that.
 */
@Serializable
public enum class TrustLabel(
    public val displayName: String,
    public val explanation: String,
) {
    IDENTITY_VERIFIED(
        "Identity verified",
        "A government identity document was checked. This says nothing about character.",
    ),
    COMPLETED_FIVE_COMMITMENTS(
        "Completed 5+ service commitments",
        "This member has finished at least five commitments that an organiser confirmed.",
    ),
    COMPLETED_TWENTY_COMMITMENTS(
        "Completed 20+ service commitments",
        "This member has finished at least twenty commitments that an organiser confirmed.",
    ),
    QUALIFICATION_CONFIRMED(
        "Qualification confirmed",
        "A stated certificate or licence was checked by the platform.",
    ),
    ORGANIZATION_REPRESENTATIVE(
        "Organisation representative",
        "Acts for a verified organisation, which stands behind them.",
    ),
    RELIABLE_ORGANIZER(
        "Reliable organiser",
        "Has run several activities that finished as described.",
    ),
    NO_UNRESOLVED_SAFETY_RESTRICTIONS(
        "No unresolved safety restrictions",
        "There is no safety restriction currently in force on this account.",
    ),
    BACKGROUND_CHECKED(
        "Background checked",
        "A background check was completed. Checks look backwards, not forwards.",
    ),
    NEW_MEMBER(
        "New member",
        "Recently joined. Nothing is known either way yet.",
    ),
    ;
}

/**
 * The private, contextual view of how someone has actually behaved.
 *
 * Never returned to another member — not the numbers, not a derived score, not a
 * percentile. It exists so that organisers can be shown a plain-language summary when
 * they are deciding whether to accept someone onto a sensitive activity, and so the
 * safety team has something to reason about.
 */
@Serializable
public data class TrustRecord(
    val userId: UserId,
    val commitmentsCompleted: Int = 0,
    val commitmentsCancelledLate: Int = 0,
    val noShows: Int = 0,
    val onTimeArrivals: Int = 0,
    val lateArrivals: Int = 0,
    val organizerConfirmations: Int = 0,
    val activitiesOrganized: Int = 0,
    val activitiesOrganizedCompleted: Int = 0,
    val boundaryViolationsUpheld: Int = 0,
    val upheldReportsAgainst: Int = 0,
    val dismissedReportsAgainst: Int = 0,
    val retaliatoryReportsFiled: Int = 0,
    val activeRestrictions: Int = 0,
    val qualificationsVerified: Int = 0,
    val taskEndorsements: Map<ServiceCategory, Int> = emptyMap(),
) {
    public val reliabilityIsEstablished: Boolean
        get() = commitmentsCompleted >= 5 && noShows * 4 <= commitmentsCompleted

    public val isNew: Boolean
        get() = commitmentsCompleted == 0 && activitiesOrganized == 0
}

/**
 * A specific, narrow endorsement: "this person helped me with this, and it went well."
 *
 * Attached to a category rather than a person, capped in length, and never aggregated
 * into a public total. The distinction matters: an endorsement is information about a
 * piece of work, not a vote for a personality.
 */
@Serializable
public data class TaskEndorsement(
    val fromUserId: UserId,
    val aboutUserId: UserId,
    val category: ServiceCategory,
    val commitmentId: CommitmentId,
    val note: String? = null,
    val createdAt: Timestamp,
) {
    init {
        require(fromUserId != aboutUserId) { "A person cannot endorse themselves" }
        require(note == null || note.length <= 240) { "An endorsement note must be brief" }
    }
}
