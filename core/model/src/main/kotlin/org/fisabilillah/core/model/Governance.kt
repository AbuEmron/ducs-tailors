package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * An immutable record of something consequential.
 *
 * There is no update path and no delete path for this type anywhere in the system — not
 * in the repository interfaces, not in the row-level security policies, not for platform
 * administrators. That is the point.
 */
@Serializable
public data class AuditLogEntry(
    val id: AuditLogId,
    val actorId: UserId?,
    val actorRoleAtTime: AccountRole?,
    val action: AuditAction,
    val subjectType: String,
    val subjectId: String,
    val summary: String,
    val metadata: Map<String, String> = emptyMap(),
    val ipHash: String? = null,
    val deviceHash: String? = null,
    val occurredAt: Timestamp = Instant.EPOCH,
)

@Serializable
public enum class AuditAction(public val displayName: String) {
    ACCOUNT_CREATED("Account created"),
    ACCOUNT_STATUS_CHANGED("Account status changed"),
    ROLE_GRANTED("Role granted"),
    ROLE_REVOKED("Role revoked"),
    VERIFICATION_CHANGED("Verification changed"),
    QUALIFICATION_REVIEWED("Qualification reviewed"),
    SAFEGUARDS_CHANGED("Safeguards changed"),
    CONVERSATION_OPENED("Conversation opened"),
    CONVERSATION_STATE_CHANGED("Conversation state changed"),
    OVERSIGHT_PARTICIPANT_ADDED("Oversight participant added"),
    MESSAGE_UNSENT("Message unsent"),
    BLOCK_CREATED("Block created"),
    BLOCK_REMOVED("Block removed"),
    REPORT_SUBMITTED("Report submitted"),
    MODERATION_ACTION("Moderation action"),
    RESTRICTION_IMPOSED("Restriction imposed"),
    RESTRICTION_LIFTED("Restriction lifted"),
    APPEAL_DECIDED("Appeal decided"),
    INTRODUCTION_SUBMITTED("Introduction submitted"),
    INTRODUCTION_STATUS_CHANGED("Introduction status changed"),
    GUARDIAN_CONTACT_ACCESSED("Guardian contact accessed"),
    EXACT_LOCATION_DISCLOSED("Exact location disclosed"),
    ORGANIZATION_VERIFIED("Organisation verified"),
    CAMPAIGN_ZAKAT_ATTESTED("Campaign zakat attestation recorded"),
    DATA_EXPORTED("Data exported"),
    DELETION_REQUESTED("Deletion requested"),
    CONSENT_RECORDED("Consent recorded"),
    LOGIN_FROM_NEW_DEVICE("Login from a new device"),
}

/**
 * A record that a person agreed to something, when, and to which version.
 *
 * Automated safety processing in particular is consented to explicitly rather than buried
 * in a terms document, because telling people plainly what is scanned and why is the only
 * version of this that respects them.
 */
@Serializable
public data class ConsentRecord(
    val id: ConsentRecordId,
    val userId: UserId,
    val kind: ConsentKind,
    val documentVersion: String,
    val granted: Boolean,
    val recordedAt: Timestamp,
    val withdrawnAt: Timestamp? = null,
) {
    public val isCurrent: Boolean get() = granted && withdrawnAt == null
}

@Serializable
public enum class ConsentKind(
    public val displayName: String,
    public val plainLanguage: String,
    public val required: Boolean,
) {
    TERMS_OF_USE(
        "Terms of use",
        "The rules for using this platform.",
        required = true,
    ),
    PRIVACY_POLICY(
        "Privacy policy",
        "What information we hold, why, and for how long.",
        required = true,
    ),
    COMMUNITY_GUIDELINES(
        "Community guidelines",
        "How members are expected to treat one another here.",
        required = true,
    ),
    AUTOMATED_SAFETY_PROCESSING(
        "Automated safety checks",
        "Messages are checked automatically for a narrow set of safety signals — sexual " +
            "content, grooming patterns, scam patterns, and attempts to move a conversation " +
            "off the platform. The checks flag threads for a human to look at. They never " +
            "restrict an account on their own, and no person reads your messages unless a " +
            "report is made or a flag is raised.",
        required = true,
    ),
    ADULT_AGE_DECLARATION(
        "Age declaration",
        "This platform is for adults. Declaring an age you are not is a violation of the terms.",
        required = true,
    ),
    LOCATION_USE(
        "Location",
        "Your approximate area is used to show you nearby opportunities. Your exact " +
            "location is never shared without a separate decision by you.",
        required = false,
    ),
    NOTIFICATIONS(
        "Notifications",
        "Reminders about commitments you made and messages in your conversations.",
        required = false,
    ),
    ;
}

@Serializable
public data class Notification(
    val id: NotificationId,
    val userId: UserId,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val deepLink: String? = null,
    val readAt: Timestamp? = null,
    val createdAt: Timestamp = Instant.EPOCH,
) {
    public val isRead: Boolean get() = readAt != null
}

/**
 * Every notification here corresponds to something a person actually needs to know:
 * a commitment they made, a reply they are waiting for, a safety matter.
 *
 * There is deliberately no notification for someone viewing your profile, no streak
 * reminder, no "people are talking about…", and nothing whose purpose is to bring a user
 * back into the app for its own sake.
 */
@Serializable
public enum class NotificationKind(public val displayName: String, public val group: NotificationGroup) {
    NEW_MESSAGE("New message", NotificationGroup.CONVERSATIONS),
    CONVERSATION_REQUEST("New conversation request", NotificationGroup.CONVERSATIONS),
    PURPOSE_REMINDER("Conversation purpose reminder", NotificationGroup.CONVERSATIONS),

    COMMITMENT_REMINDER("Upcoming commitment", NotificationGroup.COMMITMENTS),
    COMMITMENT_CHANGED("A commitment changed", NotificationGroup.COMMITMENTS),
    CHECK_IN_DUE("Time to check in", NotificationGroup.COMMITMENTS),

    APPLICATION_DECIDED("Your application was decided", NotificationGroup.ACTIVITY),
    ENROLLMENT_DECIDED("Your enrolment was decided", NotificationGroup.ACTIVITY),
    REQUEST_RESPONSE("Someone offered to help", NotificationGroup.ACTIVITY),
    MATCHING_REQUEST("A request matches your skills", NotificationGroup.ACTIVITY),
    PROJECT_UPDATE("Project update", NotificationGroup.ACTIVITY),
    COMMUNITY_ANNOUNCEMENT("Community announcement", NotificationGroup.ACTIVITY),

    INTRODUCTION_RECEIVED("A formal introduction was received", NotificationGroup.INTRODUCTIONS),
    INTRODUCTION_STATUS("Introduction update", NotificationGroup.INTRODUCTIONS),

    SAFETY_NOTICE("Safety notice", NotificationGroup.SAFETY),
    MODERATION_OUTCOME("Moderation outcome", NotificationGroup.SAFETY),
    APPEAL_OUTCOME("Appeal outcome", NotificationGroup.SAFETY),
    NEW_DEVICE_LOGIN("New sign-in", NotificationGroup.SAFETY),
    VERIFICATION_OUTCOME("Verification outcome", NotificationGroup.SAFETY),
}

@Serializable
public enum class NotificationGroup(public val displayName: String) {
    CONVERSATIONS("Conversations"),
    COMMITMENTS("Commitments"),
    ACTIVITY("Activity"),
    INTRODUCTIONS("Formal introductions"),
    SAFETY("Safety"),
}

/** A claimed credential, and what the platform did about it. */
@Serializable
public data class Qualification(
    val id: QualificationId,
    val userId: UserId,
    val title: String,
    val issuingBody: String,
    val issuedYear: Int? = null,
    val subject: LearningSubject? = null,
    val documentRefs: List<String> = emptyList(),
    val reviewState: QualificationReviewState = QualificationReviewState.SUBMITTED,
    /** Never writable by the claimant. Set only by a reviewer. */
    val verifiedAt: Timestamp? = null,
    val verifiedBy: UserId? = null,
    val reviewerNote: String? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

/**
 * A member asking for their identity to be checked.
 *
 * Separate from [Qualification], which is a claim about what somebody can *do*. This is a
 * claim about who they *are*, and the platform treats the two differently: a teaching
 * certificate is confirmed by a scholar who knows the field, an identity document is
 * confirmed by a provider whose whole business is documents.
 *
 * The evidence is a list of storage references, never the documents themselves and never
 * a copy of what is on them. A table holding scans of passports is a table worth
 * attacking; a table holding paths into a private bucket, readable only by a reviewer, is
 * merely a table.
 */
@Serializable
public data class VerificationRequest(
    val id: VerificationRequestId,
    val userId: UserId,
    /** What the member is asking to be granted. Never what they are asserting they have. */
    val requestedLevel: VerificationLevel,
    val method: VerificationMethod,
    val evidenceRefs: List<String> = emptyList(),
    val note: String? = null,
    val state: VerificationRequestState = VerificationRequestState.SUBMITTED,
    /** Never writable by the requester. Set only by a reviewer. */
    val reviewedBy: UserId? = null,
    val reviewedAt: Timestamp? = null,
    val decisionNote: String? = null,
    /** Set when a level that was granted is later taken away. */
    val revokedAt: Timestamp? = null,
    val revokedBy: UserId? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(requestedLevel != VerificationLevel.NONE) {
            "A request must ask for something above NONE"
        }
    }

    public val isOpen: Boolean
        get() = state == VerificationRequestState.SUBMITTED ||
            state == VerificationRequestState.UNDER_REVIEW

    public val grantsLevel: Boolean
        get() = state == VerificationRequestState.APPROVED && revokedAt == null
}

@Serializable
public enum class VerificationRequestState(public val displayName: String) {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under review"),
    APPROVED("Approved"),
    REJECTED("Not approved"),
    WITHDRAWN("Withdrawn"),
    REVOKED("Revoked"),
}

/**
 * How a level was established.
 *
 * Recorded because "identity verified" means something different depending on how, and a
 * member reading somebody's profile is entitled to know which. A document checked by a
 * provider and a document eyeballed by a volunteer are not the same claim.
 */
@Serializable
public enum class VerificationMethod(
    public val displayName: String,
    /** The highest level this method can ever justify on its own. */
    public val ceiling: VerificationLevel,
) {
    EMAIL("Email confirmation", VerificationLevel.EMAIL_VERIFIED),
    PHONE("Phone confirmation", VerificationLevel.PHONE_VERIFIED),
    DOCUMENT_PROVIDER("Identity document, checked by a provider", VerificationLevel.IDENTITY_VERIFIED),
    IN_PERSON_AT_ORGANIZATION(
        "Seen in person by a verified organisation",
        VerificationLevel.IDENTITY_VERIFIED,
    ),
    BACKGROUND_CHECK_PROVIDER("Background check", VerificationLevel.BACKGROUND_CHECKED),
}

@Serializable
public enum class QualificationReviewState(public val displayName: String) {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under review"),
    VERIFIED("Verified"),
    NOT_VERIFIED("Not verified"),
    EXPIRED("Expired"),
    REVOKED("Revoked"),
}

/** A device or browser session, so a person can see and end their own sign-ins. */
@Serializable
public data class DeviceSession(
    val id: String,
    val userId: UserId,
    val deviceLabel: String,
    val platform: String,
    val ipHash: String?,
    val createdAt: Timestamp,
    val lastSeenAt: Timestamp,
    val revokedAt: Timestamp? = null,
) {
    public val isActive: Boolean get() = revokedAt == null
}
