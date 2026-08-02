package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * The reason a conversation exists.
 *
 * There is no such thing as a conversation without one of these. This single constraint
 * is the structural reason the platform cannot quietly become a place for casual private
 * pursuit: there is no code path that produces an unattached thread between two people.
 */
@Serializable
public enum class ContactPurposeKind(
    public val displayName: String,
    public val description: String,
    /** Whether this purpose requires a listing, project, or request to point at. */
    public val requiresSubject: Boolean,
) {
    VOLUNTEER_OPPORTUNITY(
        "Volunteering",
        "About a specific opportunity to help.",
        requiresSubject = true,
    ),
    LEARNING_REQUEST(
        "Learning request",
        "You want to study something this person teaches.",
        requiresSubject = true,
    ),
    TEACHING_OFFER(
        "Teaching offer",
        "You are offering to teach something this person is trying to learn.",
        requiresSubject = true,
    ),
    COMMUNITY_PROJECT(
        "Community project",
        "About a project you are both involved in or joining.",
        requiresSubject = true,
    ),
    ASSISTANCE_REQUEST(
        "Assistance request",
        "About a practical need someone has posted.",
        requiresSubject = true,
    ),
    ORGANIZATION_INQUIRY(
        "Organisation enquiry",
        "A question for a masjid, charity, or community organisation.",
        requiresSubject = false,
    ),
    MODERATION_MATTER(
        "Moderation matter",
        "Contact from or to the safety team about a report or restriction.",
        requiresSubject = false,
    ),
    FORMAL_INTRODUCTION(
        "Formal family introduction",
        "The guardian-led marriage introduction process. Never an ordinary chat.",
        requiresSubject = false,
    ),
    ;

    /**
     * Purposes an ordinary member may pick when starting a thread. Moderation threads are
     * opened by the safety team, and introductions only ever come into being through the
     * dedicated workflow — neither can be selected from a compose screen.
     */
    public val userSelectable: Boolean
        get() = this != MODERATION_MATTER && this != FORMAL_INTRODUCTION

    public companion object {
        public val selectable: List<ContactPurposeKind> = entries.filter { it.userSelectable }
    }
}

/** What the purpose points at, when it points at something. */
@Serializable
public sealed interface PurposeSubject {
    @Serializable
    public data class Opportunity(val id: ListingId) : PurposeSubject

    @Serializable
    public data class LearningOffer(val id: ListingId) : PurposeSubject

    @Serializable
    public data class Request(val id: RequestId) : PurposeSubject

    @Serializable
    public data class Project(val id: ProjectId) : PurposeSubject

    @Serializable
    public data class Organization(val id: OrganizationId) : PurposeSubject

    @Serializable
    public data class Community(val id: CommunityId) : PurposeSubject
}

/**
 * The structured opening a person must complete before a new thread exists.
 *
 * A blank "salam :)" cannot be sent to a stranger. The sender has to say what they want,
 * about what, for how long, and who else should be present — which both protects the
 * recipient and makes it obvious, later, when a thread has drifted away from why it began.
 */
@Serializable
public data class ContactPurpose(
    val kind: ContactPurposeKind,
    val subject: PurposeSubject? = null,
    /** Why the sender is making contact, in their own words. */
    val reasonForContact: String,
    /** What they are actually asking the recipient to do. */
    val requestedAction: String,
    val expectedDuration: EngagementDuration,
    val requestGuardianPresent: Boolean = false,
    val requestModeratorPresent: Boolean = false,
    val requestOrganizationRepresentative: OrganizationId? = null,
) {
    public companion object {
        public const val MIN_REASON_LENGTH: Int = 20
        public const val MAX_REASON_LENGTH: Int = 800
        public const val MIN_ACTION_LENGTH: Int = 10
        public const val MAX_ACTION_LENGTH: Int = 400
    }
}

/** Roughly how long the sender expects the engagement to last. */
@Serializable
public enum class EngagementDuration(public val displayName: String) {
    ONE_OFF("A single session or task"),
    SHORT_TERM("A few weeks"),
    ONGOING_TERM("A term or season"),
    OPEN_ENDED("Open ended"),
    ;
}

/** Where a thread is in its life. */
@Serializable
public enum class ConversationState(public val displayName: String) {
    /** Opened, awaiting the recipient's first reply. */
    AWAITING_RESPONSE("Awaiting response"),
    ACTIVE("Active"),

    /** The work is done. Read-only, and it will archive itself on the owner's schedule. */
    COMPLETED("Completed"),
    ARCHIVED("Archived"),

    /** Ended by a participant. No further messages, by anyone. */
    ENDED("Ended"),

    /** Frozen by the safety team while a case is open. Evidence is preserved. */
    FROZEN("Frozen by moderation"),
    ;

    public val acceptsNewMessages: Boolean
        get() = this == AWAITING_RESPONSE || this == ACTIVE
}

/** Why someone is in a thread. */
@Serializable
public enum class ConversationRole(public val displayName: String) {
    INITIATOR("Started the conversation"),
    RECIPIENT("Recipient"),
    PARTICIPANT("Participant"),

    /** A wali or trusted contact added under the recipient's safeguards. */
    GUARDIAN("Guardian or trusted contact"),

    /** Present because a safeguard or organisation rule requires oversight. */
    MODERATOR("Moderator"),
    ORGANIZATION_REPRESENTATIVE("Organisation representative"),

    /** A third party the recipient chose to have present. */
    THIRD_PARTY("Third party"),
    ;

    /**
     * Oversight participants can read and intervene but are not the point of the thread.
     * The UI shows them distinctly so no one is under any illusion about who is present.
     */
    public val isOversight: Boolean
        get() = this == GUARDIAN || this == MODERATOR || this == ORGANIZATION_REPRESENTATIVE ||
            this == THIRD_PARTY
}

@Serializable
public data class ConversationMember(
    val userId: UserId,
    val role: ConversationRole,
    val joinedAt: Timestamp,
    val leftAt: Timestamp? = null,
    val mutedUntil: Timestamp? = null,
) {
    public val isActive: Boolean get() = leftAt == null
}

@Serializable
public data class Conversation(
    val id: ConversationId,
    val purpose: ContactPurpose,
    val members: List<ConversationMember>,
    val state: ConversationState = ConversationState.AWAITING_RESPONSE,
    val subjectTitle: String,
    /** Requirements that were imposed when this thread was created, kept for the banner. */
    val appliedRequirements: Set<ContactRequirement> = emptySet(),
    val lastMessageAt: Timestamp? = null,
    val archiveAfter: Timestamp? = null,
    val frozenByCaseId: ModerationCaseId? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(members.isNotEmpty()) { "A conversation must have members" }
    }

    public fun member(userId: UserId): ConversationMember? =
        members.firstOrNull { it.userId == userId }

    public fun activeMember(userId: UserId): ConversationMember? =
        members.firstOrNull { it.userId == userId && it.isActive }

    public val participantIds: Set<UserId>
        get() = members.filter { it.isActive && !it.role.isOversight }.map { it.userId }.toSet()

    public val oversightIds: Set<UserId>
        get() = members.filter { it.isActive && it.role.isOversight }.map { it.userId }.toSet()
}

/**
 * A condition attached to a conversation because of someone's safeguards.
 *
 * These are computed once, at creation, and are then displayed in the thread banner so
 * that everybody present knows the terms they agreed to.
 */
@Serializable
public enum class ContactRequirement(
    public val displayName: String,
    public val explanation: String,
) {
    WRITTEN_PURPOSE_REQUIRED(
        "Stated purpose",
        "This conversation has a stated purpose and is expected to stay on it.",
    ),
    GUARDIAN_PRESENT(
        "Guardian present",
        "The recipient's wali or trusted contact is in this conversation.",
    ),
    MODERATOR_PRESENT(
        "Moderator present",
        "A moderator is in this conversation because of the safeguards in force.",
    ),
    THIRD_PARTY_PRESENT(
        "Third party present",
        "A third party chosen by the recipient is in this conversation.",
    ),
    GROUP_CONTEXT_ONLY(
        "Group context",
        "This conversation stays inside its group or project thread.",
    ),
    ORGANIZATION_REPRESENTATIVE_PRESENT(
        "Organisation present",
        "A representative of the organisation is in this conversation.",
    ),
    NO_VOICE_CALLS("No voice calls", "Voice calls are switched off for this conversation."),
    NO_VIDEO_CALLS("No video calls", "Video calls are switched off for this conversation."),
    PUBLIC_MEETINGS_ONLY(
        "Public meetings only",
        "Any meeting arranged here should be in a public place.",
    ),
    THIRD_PARTY_AT_MEETINGS(
        "Third party at meetings",
        "Any meeting arranged here should have a third person present.",
    ),
    ARCHIVES_ON_COMPLETION(
        "Archives when finished",
        "This conversation closes itself once the work is complete.",
    ),
}

/** What a message carries. */
@Serializable
public enum class MessageKind {
    TEXT,
    SYSTEM,
    ATTACHMENT,

    /** Emitted by the platform when a thread drifts from its stated purpose. */
    PURPOSE_REMINDER,

    /** Emitted when oversight joins or leaves, so the record is unambiguous. */
    PARTICIPANT_CHANGE,
}

@Serializable
public data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val senderId: UserId?,
    val kind: MessageKind = MessageKind.TEXT,
    val body: String,
    val attachmentUrl: String? = null,
    /**
     * Set when a sender unsends. The body is hidden from participants, but the original
     * is preserved in a moderator-only record so that an abuser cannot delete the proof
     * of what they wrote.
     */
    val unsentAt: Timestamp? = null,
    val readBy: Set<UserId> = emptySet(),
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    public val isUnsent: Boolean get() = unsentAt != null

    /** What participants see. Moderators read the preserved original instead. */
    public val displayBody: String
        get() = if (isUnsent) "This message was unsent by the sender." else body

    public companion object {
        public const val MAX_LENGTH: Int = 4000

        /**
         * A sender may unsend within this window. After it, the message stands — otherwise
         * "unsend" becomes a tool for saying something abusive and erasing it before the
         * recipient can report it.
         */
        public const val UNSEND_WINDOW_MINUTES: Int = 5
    }
}

/** The moderator-only preserved copy of an unsent message. */
@Serializable
public data class MessageRedaction(
    val messageId: MessageId,
    val conversationId: ConversationId,
    val senderId: UserId?,
    val originalBody: String,
    val redactedAt: Timestamp,
)
