package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Live audio and video.
 *
 * A teacher takes an Arabic class from Cairo for eleven students in four countries; a
 * project team talks through a food distribution the night before; a student reads to a
 * Qur'an teacher and is corrected in real time. None of that works over text.
 *
 * The design problem is that a live room is the most intimate surface a platform like this
 * can offer, and the easiest to misuse. The answer taken here is that **a person's
 * safeguards travel into the room with them**. If a sister's settings say her camera is not
 * available to unrelated men, then in a mixed class her camera is switched off and locked
 * off — not by the host, not by a moderator, and not by her under pressure from either. She
 * does not have to defend the boundary in the moment, because the room already knows it.
 *
 * Everything else follows the same rules as the rest of the platform: a session is bound to
 * a purpose, one-to-one cross-gender sessions carry oversight or do not happen, recording
 * requires everybody's consent, and nothing here is exempt from blocks or restrictions.
 */
@Serializable
public data class LiveSession(
    val id: LiveSessionId,
    val title: String,
    val kind: LiveSessionKind,
    val medium: LiveSessionMedium,
    val hostId: UserId,

    /** What the session is for. A live room is purpose-bound exactly like a conversation. */
    val subject: PurposeSubject? = null,
    val learningSubject: LearningSubject? = null,
    val organizationId: OrganizationId? = null,
    val communityId: CommunityId? = null,

    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    val scheduledStart: Timestamp,
    val scheduledEnd: Timestamp,
    val actualStart: Timestamp? = null,
    val actualEnd: Timestamp? = null,

    val maxParticipants: Int = 50,
    val participants: List<LiveParticipant> = emptyList(),

    val recordingPolicy: RecordingPolicy = RecordingPolicy.NEVER,
    val recordingConsents: Set<UserId> = emptySet(),

    /** Only people already enrolled or accepted may join. */
    val requiresEnrolment: Boolean = true,

    /** Oversight the room was created with, mirroring a conversation's requirements. */
    val requiredOversight: Set<LiveOversight> = emptySet(),

    val state: LiveSessionState = LiveSessionState.SCHEDULED,

    /**
     * The transport room identifier from whichever media provider is configured. Null until
     * a provider is wired up — see `LiveSessionTransport` in the domain layer.
     */
    val transportRoomId: String? = null,

    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(title.isNotBlank()) { "A live session needs a title" }
        require(maxParticipants in 2..500) { "maxParticipants must be between 2 and 500" }
        require(scheduledEnd > scheduledStart) { "A session must end after it starts" }
    }

    /**
     * Who is in the room right now.
     *
     * Filters on removal as well as leaving. Counting somebody a moderator has just ejected
     * would let them keep occupying a place, keep satisfying an oversight requirement, and
     * keep blocking a recording consent they will never give.
     */
    public val activeParticipants: List<LiveParticipant>
        get() = participants.filter { it.isPresent }

    public val isOneToOne: Boolean
        get() = kind == LiveSessionKind.ONE_TO_ONE_TUTORING || maxParticipants == 2

    public fun participant(userId: UserId): LiveParticipant? =
        participants.firstOrNull { it.userId == userId && it.isPresent }

    /**
     * Recording may only begin when everyone currently in the room has said yes.
     *
     * Not "everyone who was invited", and not "everyone who has not objected". A person who
     * joins after recording started is asked again, and if they decline the recording stops.
     */
    public val recordingPermitted: Boolean
        get() = when (recordingPolicy) {
            RecordingPolicy.NEVER -> false
            RecordingPolicy.WITH_CONSENT_OF_EVERY_PARTICIPANT ->
                activeParticipants.isNotEmpty() &&
                    activeParticipants.all { it.userId in recordingConsents }
        }
}

@Serializable
public enum class LiveSessionKind(
    public val displayName: String,
    public val description: String,
    /** Whether the room exists to teach, which brings the teaching disclosures with it. */
    public val isInstruction: Boolean = false,
) {
    CLASS(
        "Class",
        "A scheduled class with an instructor and enrolled students.",
        isInstruction = true,
    ),
    STUDY_CIRCLE(
        "Study circle",
        "Members studying together. Not authoritative instruction.",
        isInstruction = true,
    ),
    QURAN_RECITATION(
        "Qur'an reading session",
        "Reading aloud and being corrected by a teacher.",
        isInstruction = true,
    ),
    ONE_TO_ONE_TUTORING(
        "One-to-one tutoring",
        "A single student with a single teacher.",
        isInstruction = true,
    ),
    PROJECT_MEETING(
        "Project meeting",
        "A working call for a community project.",
    ),
    ORGANIZATION_BRIEFING(
        "Organisation briefing",
        "A masjid or organisation speaking to its members.",
    ),
    VOLUNTEER_COORDINATION(
        "Volunteer coordination",
        "Arranging an activity with the people taking part.",
    ),
    GUARDIAN_INCLUSIVE_INTRODUCTION(
        "Guardian-led family introduction",
        "A conversation following a formal introduction. The guardian is present throughout.",
    ),
    ;

    /**
     * Rooms an ordinary member may create from the interface. An introduction room is only
     * ever opened by a guardian through the introduction workflow, exactly as its text
     * conversation is.
     */
    public val userCreatable: Boolean
        get() = this != GUARDIAN_INCLUSIVE_INTRODUCTION
}

@Serializable
public enum class LiveSessionMedium(
    public val displayName: String,
    public val carriesVideo: Boolean,
) {
    AUDIO_ONLY("Voice only", carriesVideo = false),
    VIDEO("Voice and video", carriesVideo = true),

    /**
     * The host's camera and screen only. Students are heard but not seen, which is the
     * arrangement most teachers of mixed classes actually want.
     */
    HOST_VIDEO_ONLY("Instructor on camera, students by voice", carriesVideo = true),
}

@Serializable
public enum class LiveSessionState(public val displayName: String) {
    SCHEDULED("Scheduled"),

    /** Open for people to arrive, but the host has not started. */
    LOBBY_OPEN("Waiting to start"),
    LIVE("Live now"),
    ENDED("Ended"),
    CANCELLED("Cancelled"),

    /** Stopped by the safety team. Participants are removed and the room cannot reopen. */
    HALTED_BY_MODERATION("Stopped by moderation"),
    ;

    public val acceptsJoins: Boolean get() = this == LOBBY_OPEN || this == LIVE
}

@Serializable
public data class LiveParticipant(
    val sessionId: LiveSessionId,
    val userId: UserId,
    val role: LiveRole,
    val joinedAt: Timestamp,
    val leftAt: Timestamp? = null,

    /**
     * What this person is *permitted* to switch on, computed from their own safeguards and
     * the room's arrangement when they joined. Distinct from whether it is currently on.
     */
    val permissions: LiveMediaPermissions,

    val cameraOn: Boolean = false,
    val microphoneOn: Boolean = false,
    val handRaised: Boolean = false,

    /** Removed by the host or a moderator, with a reason recorded. */
    val removedAt: Timestamp? = null,
    val removalReason: String? = null,
) {
    public val isPresent: Boolean get() = leftAt == null && removedAt == null
}

@Serializable
public enum class LiveRole(public val displayName: String, public val isOversight: Boolean = false) {
    HOST("Host"),
    CO_HOST("Co-host"),
    PARTICIPANT("Participant"),
    GUARDIAN("Guardian", isOversight = true),
    MODERATOR("Moderator", isOversight = true),
    ORGANIZATION_REPRESENTATIVE("Organisation representative", isOversight = true),
    THIRD_PARTY("Third party", isOversight = true),
}

/**
 * What a participant may switch on.
 *
 * Computed once, on joining, from the person's own safeguards and the room's composition.
 * A locked capability comes with [cameraLockedReason] or [microphoneLockedReason] so the
 * interface can say plainly why, rather than showing a dead button.
 */
@Serializable
public data class LiveMediaPermissions(
    val mayEnableMicrophone: Boolean,
    val mayEnableCamera: Boolean,
    val mayShareScreen: Boolean = false,
    val mayUseTextChat: Boolean = true,
    val cameraLockedReason: String? = null,
    val microphoneLockedReason: String? = null,
) {
    public companion object {
        /** A participant who may speak but not appear. The common case in a mixed class. */
        public fun voiceOnly(reason: String): LiveMediaPermissions = LiveMediaPermissions(
            mayEnableMicrophone = true,
            mayEnableCamera = false,
            cameraLockedReason = reason,
        )

        public fun full(): LiveMediaPermissions = LiveMediaPermissions(
            mayEnableMicrophone = true,
            mayEnableCamera = true,
            mayShareScreen = true,
        )

        /** Present, hearing, not participating. Used for oversight roles by default. */
        public fun observing(reason: String): LiveMediaPermissions = LiveMediaPermissions(
            mayEnableMicrophone = false,
            mayEnableCamera = false,
            cameraLockedReason = reason,
            microphoneLockedReason = reason,
        )
    }
}

@Serializable
public enum class RecordingPolicy(
    public val displayName: String,
    public val explanation: String,
) {
    NEVER(
        "Never recorded",
        "This session is not recorded. Nothing is stored except who attended.",
    ),
    WITH_CONSENT_OF_EVERY_PARTICIPANT(
        "Recorded only with everyone's agreement",
        "Recording cannot start until every person in the room has agreed, and it stops if " +
            "someone joins who has not.",
    ),
}

/** Oversight a live room was created with. Mirrors `ContactRequirement` for conversations. */
@Serializable
public enum class LiveOversight(
    public val displayName: String,
    public val explanation: String,
) {
    GUARDIAN_PRESENT(
        "Guardian present",
        "A guardian or trusted contact is in this room for its whole duration.",
    ),
    MODERATOR_PRESENT(
        "Moderator present",
        "A moderator is in this room because of the safeguards in force.",
    ),
    THIRD_PARTY_PRESENT(
        "Third party present",
        "A third party chosen by a participant is in this room.",
    ),
    ORGANIZATION_REPRESENTATIVE_PRESENT(
        "Organisation present",
        "A representative of the organisation is in this room.",
    ),
    RECORDED_FOR_SAFEGUARDING(
        "Attendance recorded",
        "Who attended and for how long is recorded, as the organisation's safeguarding " +
            "policy requires.",
    ),
}

/**
 * An invitation to a live session.
 *
 * Separate from enrolment: being enrolled in a class does not by itself put a room on
 * anybody's calendar, and a one-off call needs an invitation that can be declined without
 * leaving the class.
 */
@Serializable
public data class LiveSessionInvite(
    val sessionId: LiveSessionId,
    val userId: UserId,
    val invitedBy: UserId,
    val role: LiveRole = LiveRole.PARTICIPANT,
    val invitedAt: Timestamp,
    val respondedAt: Timestamp? = null,
    val accepted: Boolean? = null,
)

/**
 * Feature flags for live sessions.
 *
 * The domain model, the safeguard gating and the interface are complete and tested. What is
 * *not* present is a media transport: no WebRTC, no SFU, no provider credentials. Turning
 * [transportConfigured] on is not a code change alone — see `docs/live-sessions.md` for what
 * has to be true first, including the media-provider agreement, TURN infrastructure, the
 * abuse-reporting path for live audio, and the legal position on recording in each
 * jurisdiction the platform operates in.
 */
public object LiveSessionFeatureFlags {
    public const val transportConfigured: Boolean = false

    public const val notConfiguredNotice: String =
        "Live sessions are in preview. You can schedule a session, see who is invited, and " +
            "see exactly what your safeguards will permit once you are in the room — but no " +
            "audio or video can be carried yet, because the platform has not finished " +
            "connecting a media provider."
}
