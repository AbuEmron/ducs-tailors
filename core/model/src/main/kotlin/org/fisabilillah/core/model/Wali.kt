package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A person a member has nominated to stand with them — a wali, a family member, an imam,
 * or another trusted intermediary.
 *
 * The contact details on this record are the single most sensitive thing the platform
 * holds. They are never returned by a search, never included in a profile response, and
 * never shown to a counterparty. When an introduction is approved and forwarded, the
 * platform contacts the guardian; it does not hand out their number.
 */
@Serializable
public data class TrustedContact(
    val id: TrustedContactId,
    val ownerId: UserId,
    val name: String,
    val relationship: GuardianRelationship,
    val role: TrustedContactRole,
    /**
     * Held separately from the rest of the record so that the ordinary read path cannot
     * accidentally include it. Repository implementations must not return this to anyone
     * other than [ownerId].
     */
    val privateContact: PrivateContactDetails? = null,
    val preferredContactMethod: ContactMethod = ContactMethod.IN_APP,
    val verificationState: GuardianVerificationState = GuardianVerificationState.UNVERIFIED,
    val verifiedAt: Timestamp? = null,
    /** Present in the app as a member in their own right, if they have an account. */
    val linkedUserId: UserId? = null,
    val active: Boolean = true,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(name.isNotBlank()) { "A trusted contact needs a name" }
    }

    /**
     * The form of this record that may leave the owner's own device or session.
     *
     * Everything that could be used to contact this person outside the platform is gone.
     * A counterparty in an introduction learns that a guardian exists and how they prefer
     * to be approached — nothing more.
     */
    public fun redacted(): RedactedTrustedContact = RedactedTrustedContact(
        id = id,
        name = name,
        relationship = relationship,
        role = role,
        preferredContactMethod = preferredContactMethod,
        verificationState = verificationState,
    )
}

/**
 * Contact details for a guardian.
 *
 * This type exists so that a reviewer can grep for it and find every place in the
 * codebase where guardian contact information is handled.
 */
@Serializable
public data class PrivateContactDetails(
    val email: String? = null,
    val phone: String? = null,
    val notes: String? = null,
)

/** A guardian record with every contact detail removed. Safe to send to a counterparty. */
@Serializable
public data class RedactedTrustedContact(
    val id: TrustedContactId,
    val name: String,
    val relationship: GuardianRelationship,
    val role: TrustedContactRole,
    val preferredContactMethod: ContactMethod,
    val verificationState: GuardianVerificationState,
)

@Serializable
public enum class GuardianRelationship(public val displayName: String) {
    FATHER("Father"),
    BROTHER("Brother"),
    PATERNAL_GRANDFATHER("Paternal grandfather"),
    PATERNAL_UNCLE("Paternal uncle"),
    OTHER_MALE_RELATIVE("Other male relative"),
    MOTHER("Mother"),
    OTHER_FAMILY("Other family member"),
    IMAM("Imam"),
    COMMUNITY_ELDER("Community elder"),
    APPROVED_INTERMEDIARY("Approved community intermediary"),
    ;
}

@Serializable
public enum class TrustedContactRole(public val displayName: String, public val explanation: String) {
    /** Receives and handles marriage introductions. */
    WALI(
        "Wali",
        "Receives formal introductions on your behalf and speaks for you in that process.",
    ),

    /** Named as an intermediary where a wali is not available. */
    INTERMEDIARY(
        "Trusted intermediary",
        "Acts in place of a wali where one is unavailable, according to your circumstances.",
    ),

    /** Copied into ordinary conversations under a safeguard, not part of introductions. */
    CONVERSATION_GUARDIAN(
        "Conversation guardian",
        "Added to conversations you have chosen to have supervised.",
    ),

    /** An emergency contact for volunteering. */
    SAFETY_CONTACT(
        "Safety contact",
        "Reachable if something goes wrong while you are volunteering.",
    ),
    ;
}

@Serializable
public enum class ContactMethod(public val displayName: String) {
    IN_APP("Through the app"),
    EMAIL("Email"),
    PHONE("Phone"),
    THROUGH_MASJID("Through our masjid"),
}

@Serializable
public enum class GuardianVerificationState(public val displayName: String) {
    UNVERIFIED("Not confirmed"),
    INVITE_SENT("Invitation sent"),
    CONFIRMED("Confirmed by the guardian"),
    DECLINED("Declined the role"),
}

/**
 * A member's configuration for the Formal Family Introduction process.
 *
 * Off by default. There is no browsing, no swiping, no availability badge, and nothing
 * searchable: a person only ever encounters this feature because someone they already
 * came across through service or study submitted a serious, structured request, and even
 * then only if the recipient chose to be reachable that way.
 */
@Serializable
public data class FormalIntroductionSettings(
    val userId: UserId,
    val enabled: Boolean = false,
    val guardianContactId: TrustedContactId? = null,

    /**
     * Whether the member sees a request before their guardian does. Some people want to
     * screen; others want everything to go to their wali first. Both are legitimate.
     */
    val recipientReviewsFirst: Boolean = true,

    /** Whether every subsequent exchange must include the guardian. */
    val guardianMustBeIncludedThroughout: Boolean = true,

    /** Whether an imam or intermediary may act in place of a wali. */
    val allowIntermediaryInsteadOfWali: Boolean = false,

    /** Who may submit at all. Enforced before a request is ever created. */
    val acceptRequestsFrom: AudienceScope = AudienceScope.VERIFIED_ONLY,
    val minimumVerification: VerificationLevel = VerificationLevel.IDENTITY_VERIFIED,
    val requireSharedOrganization: Boolean = false,

    /** Non-sensitive information the member is willing to share once a request is accepted. */
    val intentionsStatement: String? = null,
    val compatibilityNotes: List<CompatibilityNote> = emptyList(),

    /** Hard ceiling on how many open requests can exist at once. */
    val maxOpenRequests: Int = 3,

    val updatedAt: Timestamp = Instant.EPOCH,
) {
    init {
        require(maxOpenRequests in 1..10) { "maxOpenRequests must be between 1 and 10" }
        require(intentionsStatement == null || intentionsStatement.length <= 1000) {
            "intentionsStatement must be 1000 characters or fewer"
        }
    }

    /**
     * The feature cannot be on without someone to receive introductions. Enforced here
     * and again in the policy layer, because a request forwarded to nobody is exactly the
     * failure mode this whole design exists to prevent.
     */
    public val isUsable: Boolean
        get() = enabled && guardianContactId != null
}

/** A neutral, non-sensitive point of compatibility. Deliberately not free-form about a person. */
@Serializable
public data class CompatibilityNote(
    val topic: CompatibilityTopic,
    val detail: String,
) {
    init {
        require(detail.length <= 300) { "A compatibility note must be 300 characters or fewer" }
    }
}

@Serializable
public enum class CompatibilityTopic(public val displayName: String) {
    PRACTICE_AND_PRIORITIES("Practice and priorities"),
    EDUCATION("Education"),
    WORK("Work"),
    LANGUAGES("Languages spoken at home"),
    LOCATION_PLANS("Where I intend to live"),
    FAMILY_EXPECTATIONS("Family expectations"),
    TIMELINE("Timeline"),
    ;
}

/**
 * A serious, structured expression of interest.
 *
 * The state machine below is the whole feature. There is no path from [Submitted] to a
 * private chat; the only way two people end up in a conversation is
 * [Approved] → [ForwardedToGuardian] → a guardian-inclusive thread, and only when the
 * recipient's settings allow it.
 */
@Serializable
public data class FormalIntroductionRequest(
    val id: IntroductionId,
    val senderId: UserId,
    val recipientId: UserId,
    val status: IntroductionStatus = IntroductionStatus.SUBMITTED,
    val form: IntroductionForm,
    /** Recorded at submission so a sender cannot later claim they were not told. */
    val conductAgreementAcceptedAt: Timestamp,
    val senderVerificationAtSubmission: VerificationLevel,
    val recipientDecidedAt: Timestamp? = null,
    val recipientDecisionNote: String? = null,
    val forwardedAt: Timestamp? = null,
    val guardianContactId: TrustedContactId? = null,
    val guardianRespondedAt: Timestamp? = null,
    val conversationId: ConversationId? = null,
    val closedAt: Timestamp? = null,
    val closureReason: IntroductionClosureReason? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    public val isOpen: Boolean
        get() = closedAt == null && status.isOpen
}

/**
 * The form a sender must complete.
 *
 * Long enough to be a deterrent to anyone who is not serious, and structured enough that
 * a guardian receives something they can actually evaluate.
 */
@Serializable
public data class IntroductionForm(
    val statedIntention: String,
    val aboutSelf: String,
    val familyContext: String,
    val practiceAndPriorities: String,
    val livingSituationAndPlans: String,
    val guardianOrRepresentativeName: String,
    val guardianOrRepresentativeRelationship: GuardianRelationship,
    /** The sender confirms this is a marriage enquiry and not a way to start chatting. */
    val confirmsMarriageConsideration: Boolean,
    val confirmsConductRules: Boolean,
    val questionsForTheFamily: List<String> = emptyList(),
) {
    /*
     * Deliberately no `require` block.
     *
     * Every field here is raw text a person typed into a form. If the constructor threw,
     * the only way to tell someone their answer was too short would be to catch an
     * exception and guess which field caused it — so validation lives in
     * `IntroductionPolicy.validateForm`, which returns field-addressed errors the screen
     * can show inline. Constructing an incomplete form is expected; submitting one is not.
     */

    public companion object {
        public const val MIN_FIELD: Int = 40
        public const val MAX_FIELD: Int = 1500
        public const val MAX_QUESTIONS: Int = 10

        public val CONDUCT_RULES: List<String> = listOf(
            "This is a request for marriage consideration. It is not a way to start a " +
                "casual conversation.",
            "I will not attempt to contact this person privately, on this platform or " +
                "elsewhere, outside the process they have chosen.",
            "I accept that a guardian or trusted representative may be present in every " +
                "exchange.",
            "I accept that no reply is itself an answer, and I will not send a second " +
                "request or approach them through anyone else.",
            "I will not share anything from this process with anyone outside it.",
            "I understand the platform makes no religious ruling about my circumstances, " +
                "and that I should consult a qualified scholar who knows my situation.",
        )
    }
}

@Serializable
public enum class IntroductionStatus(
    public val displayName: String,
    public val isOpen: Boolean,
) {
    /** Submitted; awaiting whoever the recipient's settings say sees it first. */
    SUBMITTED("Submitted", true),

    /** With the recipient for a decision. */
    AWAITING_RECIPIENT("With the recipient", true),

    /** The recipient allowed it through; awaiting the guardian. */
    APPROVED_BY_RECIPIENT("Approved by the recipient", true),

    /** Delivered to the guardian or intermediary through a protected channel. */
    FORWARDED_TO_GUARDIAN("With the guardian", true),

    /** The guardian opened a guardian-inclusive conversation. */
    GUARDIAN_ENGAGED("In conversation", true),

    /** Declined. The sender is told only that the process ended. */
    DECLINED("Declined", false),

    /** Left without an answer. Silence is a complete answer and is treated as one. */
    LAPSED("No response", false),

    /** The recipient closed the door permanently on this sender. */
    BLOCKED_BY_RECIPIENT("Closed", false),

    WITHDRAWN_BY_SENDER("Withdrawn", false),
    CLOSED_BY_MODERATION("Closed by moderation", false),
    ;
}

@Serializable
public enum class IntroductionClosureReason(public val displayName: String) {
    RECIPIENT_DECLINED("The recipient declined"),
    GUARDIAN_DECLINED("The guardian declined"),
    SENDER_WITHDREW("The sender withdrew"),
    LAPSED_WITHOUT_RESPONSE("No response within the window"),
    RECIPIENT_BLOCKED_SENDER("The recipient closed this permanently"),
    MODERATION("Closed by the safety team"),
    SETTINGS_DISABLED("The recipient turned introductions off"),
}

/** Who is party to an introduction once it has been forwarded. */
@Serializable
public data class IntroductionParticipant(
    val introductionId: IntroductionId,
    val userId: UserId,
    val role: IntroductionParticipantRole,
    val addedAt: Timestamp = Instant.EPOCH,
    /**
     * Whether this participant is entitled to the guardian's real contact details.
     * Only ever true for the recipient's own side.
     */
    val mayAccessGuardianContact: Boolean = false,
)

@Serializable
public enum class IntroductionParticipantRole(public val displayName: String) {
    SENDER("Sender"),
    RECIPIENT("Recipient"),
    RECIPIENT_GUARDIAN("Recipient's guardian"),
    SENDER_REPRESENTATIVE("Sender's representative"),
    MODERATOR("Moderator"),
}

/**
 * Limits that apply to introductions regardless of anyone's settings.
 *
 * Someone sending the same structured form to thirty people is not being serious, and no
 * per-recipient setting can catch that on its own.
 */
public object IntroductionLimits {
    /** Open requests one sender may have at any time. */
    public const val MAX_CONCURRENT_OUTGOING: Int = 2

    /** Requests a sender may submit in a rolling 30 days, across all recipients. */
    public const val MAX_PER_30_DAYS: Int = 4

    /** A sender may never submit twice to the same recipient. */
    public const val MAX_PER_RECIPIENT_EVER: Int = 1

    /** Days before an unanswered request lapses on its own. */
    public const val LAPSE_AFTER_DAYS: Int = 21
}
