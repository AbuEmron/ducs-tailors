package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.LiveMediaPermissions
import org.fisabilillah.core.model.LiveParticipant
import org.fisabilillah.core.model.LiveRole
import org.fisabilillah.core.model.LiveSession
import org.fisabilillah.core.model.LiveSessionFeatureFlags
import org.fisabilillah.core.model.LiveSessionId
import org.fisabilillah.core.model.LiveSessionKind
import org.fisabilillah.core.model.LiveSessionState
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.RecordingPolicy
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.policy.LiveJoinContext
import org.fisabilillah.core.policy.LiveJoinDecision
import org.fisabilillah.core.policy.LiveSessionPolicy
import org.fisabilillah.core.policy.RecordingDecision

/** Storage for live sessions. */
public interface LiveSessionRepository {
    public suspend fun find(id: LiveSessionId): LiveSession?
    public suspend fun save(session: LiveSession): LiveSession
    public suspend fun upcomingFor(userId: UserId, from: Timestamp): List<LiveSession>
    public suspend fun hostedBy(userId: UserId): List<LiveSession>
    public suspend fun forSubject(subjectId: String): List<LiveSession>
}

/**
 * The media transport.
 *
 * Deliberately a port with no implementation in this release. Carrying other people's
 * voices is not something to improvise: it needs a provider agreement, TURN infrastructure,
 * a position on recording law in every jurisdiction the platform operates in, and an
 * abuse-reporting path that works for audio — you cannot screenshot a voice.
 *
 * The shape is fixed here so the rest of the system can be built and tested against it, and
 * so that whoever wires up LiveKit, Jitsi or an SFU of their own has an interface to satisfy
 * rather than a blank page. See `docs/live-sessions.md`.
 */
public interface LiveSessionTransport {
    /** Creates the provider-side room and returns its identifier. */
    public suspend fun createRoom(session: LiveSession): String

    /**
     * A short-lived, single-use credential for one participant.
     *
     * It must encode [permissions] on the provider side too. Enforcing the camera rule only
     * in the application would mean a modified client could publish video the person's
     * safeguards forbid, so the token has to carry it.
     */
    public suspend fun issueJoinToken(
        session: LiveSession,
        userId: UserId,
        permissions: LiveMediaPermissions,
    ): String

    public suspend fun closeRoom(session: LiveSession)

    /** Removes one participant immediately — for a moderator halting a session. */
    public suspend fun ejectParticipant(session: LiveSession, userId: UserId)
}

public data class JoinedSession(
    val session: LiveSession,
    val permissions: LiveMediaPermissions,
    val notices: List<String>,
    /** Null while no transport is configured. The UI shows the preview notice instead. */
    val joinToken: String?,
)

/**
 * Joining a live room.
 *
 * The permissions the gate returns are applied to the participant record, not merely shown.
 * A later change that let the host override them would have to edit this file.
 */
public class JoinLiveSessionUseCase(
    private val sessions: LiveSessionRepository,
    private val profiles: ProfileRepository,
    private val blocks: BlockRepository,
    private val restrictions: RestrictionRepository,
    private val learning: LearningRepository,
    private val auditLog: AuditLogRepository,
    private val assembler: ContactContextAssembler,
    private val transport: LiveSessionTransport?,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        sessionId: LiveSessionId,
        role: LiveRole = LiveRole.PARTICIPANT,
    ): Outcome<JoinedSession> {
        val session = sessions.find(sessionId) ?: return Outcome.NotFound("that session")
        val joiner = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val host = profiles.find(session.hostId) ?: return Outcome.NotFound("the host")

        val now = clock.now()
        val presentIds = session.activeParticipants.map { it.userId }.toSet()
        val blockedEitherWay = blocks.blocking(joiner.id) + blocks.blockedBy(joiner.id)

        val gendersPresent = profiles.findAll(presentIds).map { it.gender }.toSet()
            .ifEmpty { setOf(host.gender) }

        val enrolled = session.subject?.let {
            learning.enrollmentsOf(joiner.id).any { enrolment ->
                enrolment.status == org.fisabilillah.core.model.EnrollmentStatus.ENROLLED
            }
        } ?: true

        val context = LiveJoinContext(
            session = session,
            host = host,
            joiner = joiner,
            joinerEffectiveSafeguards = assembler.effectiveSafeguards(joiner.id),
            joinerRestrictions = restrictions.activeFor(joiner.id, now),
            requestedRole = role,
            joinerBlockedByHost = blocks.isBlocked(host.id, joiner.id),
            hostBlockedByJoiner = blocks.isBlocked(joiner.id, host.id),
            blockedParticipantsPresent = presentIds.any { it in blockedEitherWay },
            isEnrolled = enrolled,
            sharedOrganizationIds = assembler.sharedOrganizations(joiner.id, host.id),
            moderatorPresent = session.activeParticipants.any { it.role == LiveRole.MODERATOR },
            guardianPresent = session.activeParticipants.any { it.role == LiveRole.GUARDIAN },
            gendersPresent = gendersPresent,
            joinerLocalTime = clock.localTimeIn(joiner.availability.timeZoneId),
            now = now,
            currentYear = clock.currentYear(),
        )

        val decision = LiveSessionPolicy.canJoin(context)
        if (decision is LiveJoinDecision.Refused) {
            auditLog.append(
                entry(
                    principal,
                    AuditAction.CONVERSATION_OPENED,
                    "live_session_join_attempt",
                    sessionId.value,
                    "Refused: ${decision.reason.name}",
                    now,
                ),
            )
            return Outcome.refused(decision.reason.userFacingMessage)
        }

        val admitted = decision as LiveJoinDecision.Admitted

        val participant = LiveParticipant(
            sessionId = sessionId,
            userId = joiner.id,
            role = role,
            joinedAt = now,
            permissions = admitted.permissions,
        )
        val updated = sessions.save(
            session.copy(
                participants = session.participants.filterNot { it.userId == joiner.id } + participant,
                updatedAt = now,
            ),
        )

        // Attendance is a safeguarding record for anything an organisation runs, and the
        // basis of the private impact summary for a class. It is not a public metric.
        auditLog.append(
            entry(
                principal,
                AuditAction.CONVERSATION_OPENED,
                "live_session",
                sessionId.value,
                "Joined as ${role.name}; camera=${admitted.permissions.mayEnableCamera} " +
                    "mic=${admitted.permissions.mayEnableMicrophone}",
                now,
            ),
        )

        val token = if (LiveSessionFeatureFlags.transportConfigured && transport != null) {
            transport.issueJoinToken(updated, joiner.id, admitted.permissions)
        } else {
            null
        }

        return Outcome.Success(
            JoinedSession(
                session = updated,
                permissions = admitted.permissions,
                notices = admitted.notices,
                joinToken = token,
            ),
        )
    }

    private fun entry(
        actor: Principal,
        action: AuditAction,
        subjectType: String,
        subjectId: String,
        summary: String,
        now: Timestamp,
    ) = AuditLogEntry(
        id = AuditLogId(ids.newId()),
        actorId = actor.userId,
        actorRoleAtTime = actor.roles.firstOrNull(),
        action = action,
        subjectType = subjectType,
        subjectId = subjectId,
        summary = summary,
        occurredAt = now,
    )
}

/** Scheduling a session. */
public class ScheduleLiveSessionUseCase(
    private val sessions: LiveSessionRepository,
    private val restrictions: RestrictionRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {
    public suspend operator fun invoke(
        principal: Principal,
        draft: LiveSession,
        inviteeIds: List<UserId> = emptyList(),
    ): Outcome<LiveSession> {
        if (draft.hostId != principal.userId) {
            return Outcome.refused("You can only schedule sessions you are hosting.")
        }
        if (!draft.kind.userCreatable) {
            return Outcome.refused(
                "That kind of session is only opened through its own process, not scheduled " +
                    "here.",
            )
        }
        val now = clock.now()
        if (restrictions.activeFor(principal.userId, now).any {
                it.capability.covers(org.fisabilillah.core.model.RestrictedCapability.HOST_LIVE_SESSIONS)
            }
        ) {
            return Outcome.refused(
                "Your account is currently restricted from hosting live sessions.",
                RefusalCode.RESTRICTED,
            )
        }
        if (draft.scheduledStart < now) {
            return Outcome.invalid("scheduledStart", "Choose a time in the future.")
        }

        val saved = sessions.save(draft.copy(createdAt = now, updatedAt = now))

        for (invitee in inviteeIds) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = invitee,
                    kind = NotificationKind.COMMITMENT_REMINDER,
                    title = saved.title,
                    body = "A live ${saved.kind.displayName.lowercase()} has been scheduled.",
                    deepLink = "fisabilillah://live/${saved.id.value}",
                    createdAt = now,
                ),
            )
        }
        return Outcome.Success(saved)
    }
}

/** Consenting, or declining, to being recorded. */
public class LiveRecordingConsentUseCase(
    private val sessions: LiveSessionRepository,
    private val clock: AppClock,
) {
    public suspend fun setConsent(
        principal: Principal,
        sessionId: LiveSessionId,
        consenting: Boolean,
    ): Outcome<RecordingDecision> {
        val session = sessions.find(sessionId) ?: return Outcome.NotFound("that session")
        if (session.recordingPolicy == RecordingPolicy.NEVER) {
            return Outcome.refused("This session is never recorded.")
        }
        if (session.participant(principal.userId) == null) {
            return Outcome.refused("You are not in this session.")
        }

        val updated = sessions.save(
            session.copy(
                recordingConsents = if (consenting) {
                    session.recordingConsents + principal.userId
                } else {
                    session.recordingConsents - principal.userId
                },
                updatedAt = clock.now(),
            ),
        )
        // Withdrawing consent stops a recording that is already running. Consent that
        // cannot be withdrawn is not consent.
        return Outcome.Success(LiveSessionPolicy.recordingDecision(updated))
    }
}

/** Ending a session, either by the host or by the safety team. */
public class EndLiveSessionUseCase(
    private val sessions: LiveSessionRepository,
    private val auditLog: AuditLogRepository,
    private val transport: LiveSessionTransport?,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {
    public suspend operator fun invoke(
        principal: Principal,
        sessionId: LiveSessionId,
        haltedByModeration: Boolean = false,
    ): Outcome<LiveSession> {
        val session = sessions.find(sessionId) ?: return Outcome.NotFound("that session")

        val isHost = session.hostId == principal.userId
        if (!isHost && !principal.isModerator) {
            return Outcome.refused("Only the host or the safety team can end this session.")
        }
        if (haltedByModeration && !principal.isModerator) {
            return Outcome.refused("Only the safety team can halt a session.")
        }

        val now = clock.now()
        val updated = sessions.save(
            session.copy(
                state = if (haltedByModeration) {
                    LiveSessionState.HALTED_BY_MODERATION
                } else {
                    LiveSessionState.ENDED
                },
                actualEnd = now,
                participants = session.participants.map {
                    if (it.leftAt == null) it.copy(leftAt = now) else it
                },
                updatedAt = now,
            ),
        )
        if (LiveSessionFeatureFlags.transportConfigured && transport != null) {
            transport.closeRoom(updated)
        }
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = if (haltedByModeration) {
                    AuditAction.MODERATION_ACTION
                } else {
                    AuditAction.CONVERSATION_STATE_CHANGED
                },
                subjectType = "live_session",
                subjectId = sessionId.value,
                summary = if (haltedByModeration) {
                    "Session halted by the safety team"
                } else {
                    "Session ended by the host"
                },
                occurredAt = now,
            ),
        )
        return Outcome.Success(updated)
    }
}

/** Sessions a member is due to attend, for the home dashboard and the calendar. */
public class UpcomingLiveSessionsUseCase(
    private val sessions: LiveSessionRepository,
    private val clock: AppClock,
) {
    public suspend operator fun invoke(principal: Principal): List<LiveSession> =
        sessions.upcomingFor(principal.userId, clock.now())
            .filter { it.state != LiveSessionState.CANCELLED }
            .sortedBy { it.scheduledStart }
}

/** Kinds of live room that carry teaching disclosures, for the interface to check. */
public val INSTRUCTIONAL_SESSION_KINDS: Set<LiveSessionKind> =
    LiveSessionKind.entries.filter { it.isInstruction }.toSet()
