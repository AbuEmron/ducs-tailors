package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Why someone is reporting something.
 *
 * Kept granular on purpose. A single "inappropriate" bucket forces the safety team to
 * re-derive what actually happened from free text, and it makes it impossible to see that
 * eleven separate people have reported the same person for the same specific behaviour.
 */
@Serializable
public enum class ReportCategory(
    public val displayName: String,
    public val severity: ReportSeverity,
    public val group: ReportGroup,
) {
    FLIRTATION_OR_PURSUIT(
        "Flirtation or unwanted pursuit",
        ReportSeverity.HIGH,
        ReportGroup.CONDUCT,
    ),
    SEXUAL_CONTENT("Sexual content", ReportSeverity.CRITICAL, ReportGroup.CONDUCT),
    WALI_PROCESS_CIRCUMVENTION(
        "Going around the guardian process",
        ReportSeverity.HIGH,
        ReportGroup.CONDUCT,
    ),
    HARASSMENT("Harassment", ReportSeverity.HIGH, ReportGroup.CONDUCT),
    THREATS("Threats", ReportSeverity.CRITICAL, ReportGroup.SAFETY),
    HATE_SPEECH("Hate speech", ReportSeverity.CRITICAL, ReportGroup.SAFETY),
    SECTARIAN_ABUSE("Sectarian abuse", ReportSeverity.HIGH, ReportGroup.CONDUCT),
    RELIGIOUS_MISINFORMATION(
        "Presenting unqualified religious claims as authoritative",
        ReportSeverity.MEDIUM,
        ReportGroup.INTEGRITY,
    ),
    FRAUD("Fraud or scam", ReportSeverity.CRITICAL, ReportGroup.INTEGRITY),
    DONATION_MISUSE("Misuse of donations", ReportSeverity.CRITICAL, ReportGroup.INTEGRITY),
    IMPERSONATION("Impersonation", ReportSeverity.HIGH, ReportGroup.INTEGRITY),
    UNSAFE_VOLUNTEERING("Unsafe volunteering", ReportSeverity.HIGH, ReportGroup.SAFETY),
    CHILD_SAFETY("Child safety concern", ReportSeverity.CRITICAL, ReportGroup.SAFETY),
    GROOMING("Grooming behaviour", ReportSeverity.CRITICAL, ReportGroup.SAFETY),
    COERCION("Coercion or manipulation", ReportSeverity.CRITICAL, ReportGroup.SAFETY),
    PROFESSIONAL_MISCONDUCT("Professional misconduct", ReportSeverity.HIGH, ReportGroup.INTEGRITY),
    SPAM("Spam", ReportSeverity.LOW, ReportGroup.INTEGRITY),
    PRIVACY_VIOLATION("Privacy violation", ReportSeverity.HIGH, ReportGroup.SAFETY),
    EXTREMISM_OR_VIOLENCE("Extremism or violence", ReportSeverity.CRITICAL, ReportGroup.SAFETY),
    ILLEGAL_CONTENT("Illegal content", ReportSeverity.CRITICAL, ReportGroup.SAFETY),
    ;

    /** Reports that must reach a human quickly, whatever the automated signals say. */
    public val requiresImmediateHumanReview: Boolean
        get() = severity == ReportSeverity.CRITICAL
}

@Serializable
public enum class ReportSeverity(public val rank: Int, public val displayName: String) {
    LOW(0, "Low"),
    MEDIUM(1, "Medium"),
    HIGH(2, "High"),
    CRITICAL(3, "Critical"),
}

@Serializable
public enum class ReportGroup(public val displayName: String) {
    CONDUCT("Conduct"),
    SAFETY("Safety"),
    INTEGRITY("Integrity"),
}

/** What is being reported. */
@Serializable
public sealed interface ReportTarget {
    @Serializable
    public data class User(val id: UserId) : ReportTarget

    @Serializable
    public data class MessageTarget(val id: MessageId, val conversationId: ConversationId) : ReportTarget

    @Serializable
    public data class ConversationTarget(val id: ConversationId) : ReportTarget

    @Serializable
    public data class Listing(val id: ListingId) : ReportTarget

    @Serializable
    public data class Request(val id: RequestId) : ReportTarget

    @Serializable
    public data class Campaign(val id: CampaignId) : ReportTarget

    @Serializable
    public data class Organization(val id: OrganizationId) : ReportTarget

    @Serializable
    public data class Introduction(val id: IntroductionId) : ReportTarget
}

@Serializable
public data class Report(
    val id: ReportId,
    val reporterId: UserId,
    val target: ReportTarget,
    val category: ReportCategory,
    val description: String,
    val evidence: List<ReportEvidence> = emptyList(),
    val state: ReportState = ReportState.RECEIVED,
    val caseId: ModerationCaseId? = null,
    /** Set when a review concludes the report itself was abusive or retaliatory. */
    val abuseAssessment: ReportAbuseAssessment = ReportAbuseAssessment.NOT_ASSESSED,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class ReportState(public val displayName: String) {
    RECEIVED("Received"),
    TRIAGED("Triaged"),
    UNDER_REVIEW("Under review"),
    ACTIONED("Action taken"),
    DISMISSED("No action"),
    MERGED_INTO_CASE("Merged into an existing case"),
}

/**
 * Reporting is a safety tool, and like every safety tool it can be turned into a weapon.
 * Assessing this explicitly means a pattern of retaliatory reports is itself visible and
 * actionable, rather than silently costing an innocent person their account.
 */
@Serializable
public enum class ReportAbuseAssessment(public val displayName: String) {
    NOT_ASSESSED("Not assessed"),
    GOOD_FAITH("Made in good faith"),
    MISTAKEN_BUT_SINCERE("Mistaken but sincere"),
    RETALIATORY("Retaliatory"),
    KNOWINGLY_FALSE("Knowingly false"),
}

/** Evidence attached to a report. Write-once: it is never edited or deleted, only added to. */
@Serializable
public data class ReportEvidence(
    val id: String,
    val reportId: ReportId,
    val kind: EvidenceKind,
    val reference: String,
    val capturedAt: Timestamp,
    val capturedBy: UserId?,
    val notes: String? = null,
)

@Serializable
public enum class EvidenceKind(public val displayName: String) {
    MESSAGE_SNAPSHOT("Message snapshot"),
    CONVERSATION_SNAPSHOT("Conversation snapshot"),
    PROFILE_SNAPSHOT("Profile snapshot"),
    LISTING_SNAPSHOT("Listing snapshot"),
    UPLOADED_FILE("Uploaded file"),
    AUTOMATED_SIGNAL("Automated signal"),
    MODERATOR_NOTE("Moderator note"),
}

@Serializable
public data class ModerationCase(
    val id: ModerationCaseId,
    val subjectUserId: UserId?,
    val reportIds: List<ReportId>,
    val category: ReportCategory,
    val severity: ReportSeverity,
    val escalation: EscalationLevel = EscalationLevel.STANDARD,
    val state: CaseState = CaseState.OPEN,
    val assignedTo: UserId? = null,
    val summary: String,
    val closedAt: Timestamp? = null,
    val outcome: CaseOutcome? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class CaseState(public val displayName: String) {
    OPEN("Open"),
    AWAITING_INFORMATION("Awaiting information"),
    ESCALATED("Escalated"),
    CLOSED("Closed"),
}

@Serializable
public enum class EscalationLevel(public val displayName: String, public val rank: Int) {
    STANDARD("Standard", 0),
    SENIOR_MODERATOR("Senior moderator", 1),
    SAFETY_ADMINISTRATOR("Safety administrator", 2),
    /** Reserved for matters that may need reporting to authorities. */
    EXTERNAL_REFERRAL("External referral", 3),
}

@Serializable
public enum class CaseOutcome(public val displayName: String) {
    NO_ACTION("No action"),
    GUIDANCE_GIVEN("Guidance given"),
    WARNING_ISSUED("Warning issued"),
    FEATURE_RESTRICTED("Feature restricted"),
    TEMPORARILY_SUSPENDED("Temporarily suspended"),
    PERMANENTLY_BANNED("Permanently banned"),
    CONTENT_REMOVED("Content removed"),
    REFERRED_EXTERNALLY("Referred externally"),
    REPORTER_ACTIONED("Action taken against the reporter"),
}

/**
 * Something a moderator did.
 *
 * Every one of these writes an [AuditLogEntry]. There is no update or delete path for
 * either record. A moderator who abuses their access leaves a trail they cannot clean up,
 * which is the only structural protection that actually works against insider misuse.
 */
@Serializable
public data class ModerationAction(
    val id: ModerationActionId,
    val caseId: ModerationCaseId,
    val moderatorId: UserId,
    val type: ModerationActionType,
    val targetUserId: UserId? = null,
    val targetReference: String? = null,
    val rationale: String,
    val notifiedUser: Boolean = true,
    val expiresAt: Timestamp? = null,
    val performedAt: Timestamp = Instant.EPOCH,
) {
    init {
        require(rationale.isNotBlank()) {
            "Every moderation action must record why it was taken"
        }
    }
}

@Serializable
public enum class ModerationActionType(
    public val displayName: String,
    public val isReversible: Boolean,
    public val requiresSeniorApproval: Boolean,
) {
    NOTE_ADDED("Note added", true, false),
    WARNING_ISSUED("Warning issued", true, false),
    CONTENT_REMOVED("Content removed", true, false),
    CONVERSATION_FROZEN("Conversation frozen", true, false),
    FEATURE_RESTRICTED("Feature restricted", true, false),
    ACCOUNT_SUSPENDED("Account suspended", true, true),
    ACCOUNT_BANNED("Account banned", false, true),
    VERIFICATION_REVOKED("Verification revoked", true, true),
    ORGANIZATION_SUSPENDED("Organisation suspended", true, true),
    CAMPAIGN_SUSPENDED("Campaign suspended", true, true),
    RESTRICTION_LIFTED("Restriction lifted", true, false),
    APPEAL_UPHELD("Appeal upheld", true, false),
    APPEAL_REJECTED("Appeal rejected", true, false),
    EXTERNAL_REFERRAL("Referred externally", false, true),
}

/** A capability taken away from an account for a period. */
@Serializable
public data class Restriction(
    val id: RestrictionId,
    val userId: UserId,
    val capability: RestrictedCapability,
    val reason: String,
    val imposedBy: UserId,
    val caseId: ModerationCaseId?,
    val startsAt: Timestamp,
    val expiresAt: Timestamp? = null,
    val liftedAt: Timestamp? = null,
    val liftedBy: UserId? = null,
) {
    public fun isActiveAt(now: Timestamp): Boolean =
        liftedAt == null && now >= startsAt && (expiresAt == null || now < expiresAt)
}

@Serializable
public enum class RestrictedCapability(public val displayName: String) {
    START_CONVERSATIONS("Starting conversations"),
    SEND_MESSAGES("Sending messages"),
    CREATE_LISTINGS("Creating listings"),
    CREATE_REQUESTS("Creating help requests"),
    APPLY_TO_OPPORTUNITIES("Applying to opportunities"),
    SUBMIT_INTRODUCTIONS("Submitting formal introductions"),
    JOIN_COMMUNITIES("Joining communities"),
    UPLOAD_FILES("Uploading files"),
    SUBMIT_REPORTS("Submitting reports"),
    RECEIVE_DONATIONS("Receiving donations"),
    JOIN_LIVE_SESSIONS("Joining live classes and calls"),
    HOST_LIVE_SESSIONS("Hosting live classes and calls"),
    ALL("All activity"),
    ;

    public fun covers(other: RestrictedCapability): Boolean = this == ALL || this == other
}

/** One user preventing another from reaching them at all. */
@Serializable
public data class Block(
    val blockerId: UserId,
    val blockedId: UserId,
    val reason: String? = null,
    val createdAt: Timestamp = Instant.EPOCH,
) {
    init {
        require(blockerId != blockedId) { "A user cannot block themselves" }
    }
}

@Serializable
public data class Appeal(
    val id: AppealId,
    val caseId: ModerationCaseId,
    val appellantId: UserId,
    val statement: String,
    val state: AppealState = AppealState.SUBMITTED,
    /** Must differ from the moderator who took the original action. */
    val reviewedBy: UserId? = null,
    val reviewedAt: Timestamp? = null,
    val decisionNote: String? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class AppealState(public val displayName: String) {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under review"),
    UPHELD("Upheld — restriction lifted"),
    PARTIALLY_UPHELD("Partly upheld"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn"),
}

/** Something that went wrong during real-world service, recorded so it can be learned from. */
@Serializable
public data class SafetyIncident(
    val id: String,
    val reportedBy: UserId,
    val commitmentId: CommitmentId?,
    val listingId: ListingId?,
    val kind: IncidentKind,
    val description: String,
    val occurredAt: Timestamp,
    val organizationNotified: Boolean = false,
    val caseId: ModerationCaseId? = null,
    val createdAt: Timestamp = Instant.EPOCH,
)

@Serializable
public enum class IncidentKind(public val displayName: String) {
    INJURY("Injury"),
    UNSAFE_CONDITIONS("Unsafe conditions"),
    BOUNDARY_VIOLATION("Boundary violation"),
    NO_SUPERVISION("Supervision missing"),
    PROPERTY_DAMAGE("Property damage"),
    OTHER("Other"),
}

/**
 * The output of automated content checks.
 *
 * Advisory only. Nothing here removes content, restricts an account, or closes a case on
 * its own — every one of those outcomes requires a person. Automation exists to make sure
 * a human looks at the right thing sooner, not to replace the human.
 */
@Serializable
public data class SafetySignal(
    val kind: SafetySignalKind,
    val confidence: SignalConfidence,
    val explanation: String,
) {
    public val isAdvisoryOnly: Boolean = true
}

@Serializable
public enum class SafetySignalKind(public val displayName: String) {
    POSSIBLE_FLIRTATION("Possible flirtation"),
    POSSIBLE_SEXUAL_CONTENT("Possible sexual content"),

    /**
     * Phrases that work to cut someone off from the people around them -- "our secret",
     * "delete this chat", "your parents don't need to know".
     *
     * Separate from [POSSIBLE_FLIRTATION], which is where these used to land. Under that
     * label a grooming pattern arrived in the safety queue looking like clumsy romantic
     * interest, which is the wrong thing for a moderator to read first and the wrong
     * category to route by: isolation of a person from their family is the shape of
     * grooming and of coercive control, and neither is flirtation.
     */
    POSSIBLE_ISOLATION_ATTEMPT("Attempt to isolate someone"),
    POSSIBLE_CONTACT_DETAIL_SHARING("Contact details being shared"),
    POSSIBLE_OFF_PLATFORM_MOVE("Attempt to move off the platform"),
    POSSIBLE_FINANCIAL_SOLICITATION("Money being requested"),
    POSSIBLE_PURPOSE_DRIFT("Conversation has drifted from its purpose"),
    REPEATED_CONTACT_AFTER_DECLINE("Repeated contact after being declined"),
    HIGH_VOLUME_NEW_CONVERSATIONS("Unusual number of new conversations"),
}

@Serializable
public enum class SignalConfidence(public val displayName: String, public val rank: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2),
}

/**
 * A signal that actually happened, kept.
 *
 * [SafetySignal] is the output of a check; this is the record that the check fired, on a
 * particular message, sent by a particular person, at a particular time. The distinction
 * matters because almost nothing here is decidable from one message: "beautiful" once is
 * noise, and "beautiful" to eleven different sisters in a week is a pattern, and only a
 * stored history can tell them apart.
 *
 * Kept narrow on purpose. The message body is **not** copied in — the signal names the
 * message and carries the phrase that matched, and a moderator who needs the surrounding
 * conversation opens the case and reads it there, which is a step that gets audited. A
 * table holding a copy of every flagged private message would be a second, quieter store
 * of exactly the material the platform is most careful about.
 */
@Serializable
public data class RecordedSignal(
    val id: SafetySignalId,
    val messageId: MessageId,
    val conversationId: ConversationId,
    /** Who sent the message the signal fired on. Never the recipient. */
    val senderId: UserId,
    val signal: SafetySignal,
    /** Set once the signal has contributed to a case, so it is not counted twice. */
    val caseId: ModerationCaseId? = null,
    val observedAt: Timestamp,
)

/**
 * Capabilities that can be withdrawn, extended for live rooms.
 *
 * Kept here rather than on [RestrictedCapability] itself so the enum stays a single
 * declaration; see `RestrictedCapability.JOIN_LIVE_SESSIONS` and `HOST_LIVE_SESSIONS`.
 */
public val LIVE_SESSION_CAPABILITIES: Set<RestrictedCapability> = setOf(
    RestrictedCapability.JOIN_LIVE_SESSIONS,
    RestrictedCapability.HOST_LIVE_SESSIONS,
)
