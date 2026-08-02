package org.fisabilillah.core.policy

import kotlinx.datetime.LocalTime
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.LiveMediaPermissions
import org.fisabilillah.core.model.LiveOversight
import org.fisabilillah.core.model.LiveRole
import org.fisabilillah.core.model.LiveSession
import org.fisabilillah.core.model.LiveSessionKind
import org.fisabilillah.core.model.LiveSessionMedium
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.RecordingPolicy
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VerificationLevel

/** Everything the live-session gate needs. Assembled by the use-case layer; no I/O here. */
public data class LiveJoinContext(
    val session: LiveSession,
    val host: Profile,
    val joiner: Profile,
    /** Already combined with any organisation or community floor. */
    val joinerEffectiveSafeguards: UserSafeguards,
    val joinerRestrictions: List<Restriction> = emptyList(),
    val requestedRole: LiveRole = LiveRole.PARTICIPANT,

    val joinerBlockedByHost: Boolean = false,
    val hostBlockedByJoiner: Boolean = false,
    /** Anyone already in the room who has blocked the joiner, or whom the joiner has blocked. */
    val blockedParticipantsPresent: Boolean = false,

    val isEnrolled: Boolean = false,
    val sharedOrganizationIds: Set<OrganizationId> = emptySet(),
    val moderatorPresent: Boolean = false,
    val guardianPresent: Boolean = false,

    /** Genders currently in the room, so a "mixed room" is a fact rather than a guess. */
    val gendersPresent: Set<Gender> = emptySet(),

    val joinerLocalTime: LocalTime,
    val now: Timestamp,
    val currentYear: Int,
)

public sealed interface LiveJoinDecision {
    /**
     * The person may enter, with exactly these media permissions. The caller must apply
     * them rather than treat them as a suggestion.
     */
    public data class Admitted(
        val permissions: LiveMediaPermissions,
        val appliedOversight: Set<LiveOversight>,
        val notices: List<String>,
    ) : LiveJoinDecision

    public data class Refused(val reason: LiveJoinRefusal) : LiveJoinDecision
}

public enum class LiveJoinRefusal(public val userFacingMessage: String) {
    SESSION_NOT_OPEN("This session is not open."),
    SESSION_FULL("This session is full."),
    NOT_ENROLLED("You need to be enrolled in this class before joining the session."),
    ACCOUNT_NOT_ACTIVE("Your account cannot join live sessions at the moment."),
    RESTRICTED("Your account is currently restricted from joining live sessions."),
    BLOCKED("You cannot join this session."),
    GENDER_ARRANGEMENT("This session is arranged for a different group."),
    OUTSIDE_ORGANIZATION("This session is limited to members of the organising body."),
    VERIFICATION_TOO_LOW(
        "This session is limited to members who have verified more of their account.",
    ),
    MINOR_ADULT_MIXING("Live sessions between adults and under-18s are not available."),
    ONE_TO_ONE_CROSS_GENDER_WITHOUT_OVERSIGHT(
        "A one-to-one session with the opposite gender needs a guardian, a third party, or " +
            "a moderator present. Ask the host to add one, or join the group session instead.",
    ),
    QUIET_HOURS("You have set quiet hours that cover this time."),
    TRANSPORT_UNAVAILABLE(
        "Live audio and video are not connected yet on this platform. You can see the " +
            "session and who is invited, but nothing can be carried.",
    ),
}

/**
 * Who may enter a live room, and what they may switch on once inside.
 *
 * The second half is the part that matters. Most platforms treat a call as a binary — you
 * are in or you are out — and leave everything after that to social pressure inside the
 * room. Here the room is configured from each person's own settings before they arrive, so
 * a woman in a mixed class does not have to decline a request to turn her camera on. It was
 * never available to be requested.
 */
public object LiveSessionPolicy {

    public fun canJoin(context: LiveJoinContext): LiveJoinDecision {
        val session = context.session
        val safeguards = context.joinerEffectiveSafeguards

        // ── Room state ─────────────────────────────────────────────────────────
        if (!session.state.acceptsJoins) return refuse(LiveJoinRefusal.SESSION_NOT_OPEN)
        if (session.activeParticipants.size >= session.maxParticipants &&
            session.participant(context.joiner.id) == null
        ) {
            return refuse(LiveJoinRefusal.SESSION_FULL)
        }

        // ── The person ─────────────────────────────────────────────────────────
        if (!context.joiner.isActive) return refuse(LiveJoinRefusal.ACCOUNT_NOT_ACTIVE)
        if (isRestricted(context.joinerRestrictions, context.now)) {
            return refuse(LiveJoinRefusal.RESTRICTED)
        }

        // A block ends the possibility of sharing a room, in either direction, and whether
        // it is the host or another participant who is blocked.
        if (context.joinerBlockedByHost || context.hostBlockedByJoiner ||
            context.blockedParticipantsPresent
        ) {
            return refuse(LiveJoinRefusal.BLOCKED)
        }

        // ── Age. Adults only, as everywhere else in this release. ──────────────
        val joinerIsAdult = context.joiner.isAdultIn(context.currentYear)
        val hostIsAdult = context.host.isAdultIn(context.currentYear)
        if (joinerIsAdult != hostIsAdult) return refuse(LiveJoinRefusal.MINOR_ADULT_MIXING)

        // ── Arrangement and eligibility ────────────────────────────────────────
        if (!session.genderArrangement.admits(context.joiner.gender)) {
            return refuse(LiveJoinRefusal.GENDER_ARRANGEMENT)
        }
        if (session.requiresEnrolment && !context.isEnrolled &&
            context.requestedRole == LiveRole.PARTICIPANT
        ) {
            return refuse(LiveJoinRefusal.NOT_ENROLLED)
        }
        if (session.organizationId != null &&
            safeguards.onlyMyOrganizationsMayContactMe &&
            context.sharedOrganizationIds.isEmpty()
        ) {
            return refuse(LiveJoinRefusal.OUTSIDE_ORGANIZATION)
        }

        val sameGenderRoom = context.gendersPresent.none { it != context.joiner.gender }
        val crossGender = !sameGenderRoom

        // ── One-to-one across genders needs somebody else present ──────────────
        //
        // The same rule as private messaging, for the same reason. An oversight participant
        // makes it a supervised tutorial; without one it is simply two people alone, which
        // this platform does not arrange.
        if (session.isOneToOne && crossGender && context.requestedRole == LiveRole.PARTICIPANT) {
            val overseen = context.moderatorPresent || context.guardianPresent ||
                session.requiredOversight.any { it != LiveOversight.RECORDED_FOR_SAFEGUARDING }
            if (!overseen) {
                return refuse(LiveJoinRefusal.ONE_TO_ONE_CROSS_GENDER_WITHOUT_OVERSIGHT)
            }
        }

        // ── Quiet hours ────────────────────────────────────────────────────────
        if (safeguards.quietHours.blockCalls &&
            safeguards.quietHours.covers(context.joinerLocalTime)
        ) {
            return refuse(LiveJoinRefusal.QUIET_HOURS)
        }

        // ── What they may switch on ────────────────────────────────────────────
        val permissions = mediaPermissions(context, crossGender)
        val notices = buildNotices(context, permissions, crossGender)

        return LiveJoinDecision.Admitted(
            permissions = permissions,
            appliedOversight = session.requiredOversight,
            notices = notices,
        )
    }

    /**
     * The camera and microphone rules for one person in one room.
     *
     * Read the camera branch carefully: the question is never "does the host allow cameras",
     * it is "does *this person's own setting* allow their camera to be seen by the people
     * who are actually in this room". A host cannot widen it and a moderator cannot widen it.
     */
    public fun mediaPermissions(
        context: LiveJoinContext,
        crossGender: Boolean,
    ): LiveMediaPermissions {
        val session = context.session
        val safeguards = context.joinerEffectiveSafeguards

        if (context.requestedRole.isOversight) {
            return LiveMediaPermissions.observing(
                "You are present as ${context.requestedRole.displayName.lowercase()}. You " +
                    "can see and hear the session and intervene, and you are not on camera.",
            )
        }

        val isHost = context.joiner.id == session.hostId

        // Microphone: governed by the person's voice-call scope.
        val micAllowed = isHost || admits(
            scope = safeguards.voiceCallsAllowedFrom,
            joiner = context.joiner,
            crossGender = crossGender,
            sharedOrganizations = context.sharedOrganizationIds,
        )

        // Camera: the room's medium first, then the person's own video scope.
        val roomCarriesVideoForThisPerson = when (session.medium) {
            LiveSessionMedium.AUDIO_ONLY -> false
            LiveSessionMedium.VIDEO -> true
            LiveSessionMedium.HOST_VIDEO_ONLY -> isHost
        }
        val personAllowsCamera = admits(
            scope = safeguards.videoCallsAllowedFrom,
            joiner = context.joiner,
            crossGender = crossGender,
            sharedOrganizations = context.sharedOrganizationIds,
        )
        val cameraAllowed = roomCarriesVideoForThisPerson && (isHost || personAllowsCamera)

        val cameraReason = when {
            cameraAllowed -> null
            session.medium == LiveSessionMedium.AUDIO_ONLY ->
                "This is a voice-only session."
            session.medium == LiveSessionMedium.HOST_VIDEO_ONLY ->
                "In this session only the instructor is on camera. You will be heard, not seen."
            !personAllowsCamera && crossGender ->
                "Your safeguards do not make your camera available to the opposite gender, " +
                    "and this session is mixed. Nobody can ask you to change that here."
            !personAllowsCamera ->
                "Your safeguards do not have your camera switched on for sessions like this. " +
                    "You can change that in Safeguards."
            else -> "Your camera is not available in this session."
        }

        val micReason = if (micAllowed) {
            null
        } else if (crossGender) {
            "Your safeguards do not make your voice available to the opposite gender, and " +
                "this session is mixed. You can still take part in the text chat."
        } else {
            "Your safeguards do not have voice switched on for sessions like this."
        }

        return LiveMediaPermissions(
            mayEnableMicrophone = micAllowed,
            mayEnableCamera = cameraAllowed,
            mayShareScreen = isHost || context.requestedRole == LiveRole.CO_HOST,
            mayUseTextChat = true,
            cameraLockedReason = cameraReason,
            microphoneLockedReason = micReason,
        )
    }

    /**
     * Whether recording may start right now.
     *
     * Deliberately strict: everybody currently present must have agreed. A person who
     * arrives later and declines stops it, because consent given by ten people does not
     * cover the eleventh.
     */
    public fun recordingDecision(session: LiveSession): RecordingDecision = when {
        session.recordingPolicy == RecordingPolicy.NEVER ->
            RecordingDecision.NotPermitted("This session is never recorded.")

        session.activeParticipants.isEmpty() ->
            RecordingDecision.NotPermitted("There is nobody in the session.")

        else -> {
            val missing = session.activeParticipants
                .filter { it.userId !in session.recordingConsents }
            if (missing.isEmpty()) {
                RecordingDecision.Permitted
            } else {
                RecordingDecision.NotPermitted(
                    "${missing.size} " +
                        (if (missing.size == 1) "person has" else "people have") +
                        " not agreed to being recorded. Recording cannot start.",
                )
            }
        }
    }

    /**
     * What a host must disclose before a room opens.
     *
     * A teaching room carries the instructor's capacity disclaimer, the same one shown on
     * the class listing, because a student who joined a live call has not thereby been told
     * more about their teacher's qualifications than a student who read the page.
     */
    public fun requiredDisclosures(session: LiveSession, hostCapacityDisclaimer: String?): List<String> =
        buildList {
            if (!org.fisabilillah.core.model.LiveSessionFeatureFlags.transportConfigured) {
                add(org.fisabilillah.core.model.LiveSessionFeatureFlags.notConfiguredNotice)
            }
            if (session.kind.isInstruction && hostCapacityDisclaimer != null) {
                add(hostCapacityDisclaimer)
            }
            if (session.kind == LiveSessionKind.QURAN_RECITATION ||
                session.kind == LiveSessionKind.STUDY_CIRCLE
            ) {
                add(
                    "Questions about your own circumstances should go to a qualified scholar " +
                        "who knows them. This platform does not give religious rulings.",
                )
            }
            add(session.recordingPolicy.explanation)
            for (oversight in session.requiredOversight) {
                add("${oversight.displayName}. ${oversight.explanation}")
            }
        }

    private fun buildNotices(
        context: LiveJoinContext,
        permissions: LiveMediaPermissions,
        crossGender: Boolean,
    ): List<String> = buildList {
        if (!org.fisabilillah.core.model.LiveSessionFeatureFlags.transportConfigured) {
            add(org.fisabilillah.core.model.LiveSessionFeatureFlags.notConfiguredNotice)
        }
        permissions.cameraLockedReason?.let { add(it) }
        permissions.microphoneLockedReason?.let { add(it) }
        if (crossGender && context.session.requiredOversight.isNotEmpty()) {
            add(
                "This session is mixed and has oversight in place: " +
                    context.session.requiredOversight.joinToString { it.displayName } + ".",
            )
        }
        if (context.session.recordingPolicy != RecordingPolicy.NEVER) {
            add(context.session.recordingPolicy.explanation)
        }
    }

    private fun admits(
        scope: AudienceScope,
        joiner: Profile,
        crossGender: Boolean,
        sharedOrganizations: Set<OrganizationId>,
    ): Boolean = when (scope) {
        AudienceScope.NOBODY -> false
        AudienceScope.EVERYONE -> true
        AudienceScope.VERIFIED_ONLY ->
            joiner.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED
        AudienceScope.MY_ORGANIZATIONS_ONLY -> sharedOrganizations.isNotEmpty()
        AudienceScope.SAME_GENDER_ONLY -> !crossGender
        AudienceScope.SAME_GENDER_VERIFIED_ONLY ->
            !crossGender && (joiner.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED)
    }

    private fun isRestricted(restrictions: List<Restriction>, now: Timestamp): Boolean =
        restrictions.any {
            it.isActiveAt(now) && it.capability.covers(RestrictedCapability.JOIN_LIVE_SESSIONS)
        }

    private fun refuse(reason: LiveJoinRefusal): LiveJoinDecision =
        LiveJoinDecision.Refused(reason)
}

public sealed interface RecordingDecision {
    public data object Permitted : RecordingDecision
    public data class NotPermitted(val message: String) : RecordingDecision
}
