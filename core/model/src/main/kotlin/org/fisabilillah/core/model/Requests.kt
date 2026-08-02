package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Someone asking for help.
 *
 * The hardest design problem on this platform. A person who needs food this week should
 * be able to say so without their neighbours seeing it, without their address being
 * public, and without their difficulty becoming content for anyone to browse through.
 *
 * Three things follow from that, and they are enforced rather than encouraged:
 *  - [exactLocationDisclosed] starts false and only flips through an explicit decision.
 *  - [visibility] can hide the requester's identity from everyone except moderators and
 *    the organisation mediating.
 *  - No response count, no view count, and no donation total is exposed unless the
 *    requester turns it on.
 */
@Serializable
public data class ServiceRequest(
    val id: RequestId,
    val requesterId: UserId,
    val title: String,
    val description: String,
    val category: ServiceCategory,
    val urgency: RequestUrgency = RequestUrgency.STANDARD,
    val visibility: RequestVisibility = RequestVisibility.IDENTIFIED,
    /** The place. Only the approximate part is ever returned to a general viewer. */
    val place: Place,
    val exactLocationDisclosed: Boolean = false,
    val exactLocationDisclosedTo: Set<UserId> = emptySet(),
    /** An organisation may take the request on and act as the intermediary. */
    val mediatingOrganizationId: OrganizationId? = null,
    val peopleAffected: Int = 1,
    /** A ceiling the requester or organisation sets so that help stays proportionate. */
    val maximumAssistance: Money? = null,
    /** Supporting documents. Stored privately; only reviewers ever see them. */
    val eligibilityDocumentRefs: List<String> = emptyList(),
    val fraudReviewState: FraudReviewState = FraudReviewState.NOT_REQUIRED,
    val expiresAt: Timestamp,
    val status: RequestStatus = RequestStatus.OPEN,
    val assignedHelperId: UserId? = null,
    val handoffPlan: String? = null,
    /** Off by default. Nobody's need becomes a public fundraising thermometer by accident. */
    val showSupportTotals: Boolean = false,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(description.isNotBlank()) { "description must not be blank" }
        require(peopleAffected >= 1) { "peopleAffected must be at least one" }
    }

    /** Whether [viewer] is entitled to the street address rather than the area. */
    public fun exactLocationVisibleTo(viewer: UserId): Boolean =
        viewer == requesterId ||
            (exactLocationDisclosed && viewer in exactLocationDisclosedTo)
}

@Serializable
public enum class RequestUrgency(public val displayName: String, public val rank: Int) {
    STANDARD("Standard", 0),
    SOON("Needed soon", 1),
    URGENT("Urgent", 2),
    ;
}

@Serializable
public enum class RequestVisibility(public val displayName: String, public val explanation: String) {
    IDENTIFIED(
        "Show my name",
        "Members who can see this request will see who posted it.",
    ),
    ANONYMOUS_TO_MEMBERS(
        "Hide my name from members",
        "Your name is hidden from everyone except moderators, who can always see it so " +
            "that the request can be checked.",
    ),
    ORGANIZATION_MEDIATED(
        "Handled by an organisation",
        "Only the organisation you chose and the moderators see who you are. To everyone " +
            "else this is the organisation's request.",
    ),
    ORGANIZATION_ONLY(
        "Only my organisation",
        "The request is not shown outside the organisation you selected.",
    ),
}

@Serializable
public enum class RequestStatus(public val displayName: String) {
    DRAFT("Draft"),
    PENDING_REVIEW("Awaiting review"),
    OPEN("Open"),
    IN_PROGRESS("Being helped"),
    FULFILLED("Fulfilled"),
    EXPIRED("Expired"),
    WITHDRAWN("Withdrawn"),
    REMOVED_BY_MODERATION("Removed"),
}

@Serializable
public enum class FraudReviewState(public val displayName: String) {
    NOT_REQUIRED("Not required"),
    PENDING("Under review"),
    CLEARED("Cleared"),
    FLAGGED("Flagged"),
}

/** An offer of help against a request. */
@Serializable
public data class RequestResponse(
    val id: ApplicationId,
    val requestId: RequestId,
    val responderId: UserId,
    val message: String,
    val offeredCategory: ServiceCategory,
    val status: ApplicationStatus = ApplicationStatus.SUBMITTED,
    val acceptedAt: Timestamp? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

/** The public form of a request: what a general member is allowed to see. */
@Serializable
public data class VisibleServiceRequest(
    val id: RequestId,
    val title: String,
    val description: String,
    val category: ServiceCategory,
    val urgency: RequestUrgency,
    val locationLabel: String,
    val exactAddress: ExactLocation?,
    val requesterDisplayName: String?,
    val mediatingOrganizationName: String?,
    val expiresAt: Timestamp,
    val status: RequestStatus,
    val canRespond: Boolean,
    val supportTotal: Money?,
)
