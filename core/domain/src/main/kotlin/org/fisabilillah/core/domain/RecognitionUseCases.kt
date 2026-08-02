package org.fisabilillah.core.domain

import org.fisabilillah.core.model.CommitmentId
import org.fisabilillah.core.model.CommitmentStatus
import org.fisabilillah.core.model.CommitmentSubject
import org.fisabilillah.core.model.IncidentKind
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportSeverity
import org.fisabilillah.core.model.SafetyIncident
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.TaskEndorsement
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.ModerationCase
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.CaseState

/**
 * Saying somebody did well, without inventing a popularity contest.
 *
 * [TaskEndorsement] has existed as a type with nothing that produces one, and the reason
 * to be careful about adding that is in the product principles: no vanity metrics, no
 * rankings, no follower counts. An endorsement here is therefore deliberately not a
 * "like".
 *
 * Three constraints keep it that way. It can only be given by somebody who was actually
 * there — it is bound to a completed commitment, so there is no endorse button on a
 * profile. It can be given once per commitment. And what it feeds is
 * `PrivateImpactRecord`, which the member sees and nobody else ranks.
 */
public class EndorseTaskUseCase(
    private val commitments: CommitmentRepository,
    private val opportunities: OpportunityRepository,
    private val trust: TrustRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        commitmentId: CommitmentId,
        category: ServiceCategory,
        note: String?,
    ): Outcome<TaskEndorsement> {
        val commitment = commitments.find(commitmentId)
            ?: return Outcome.NotFound("that commitment")

        if (commitment.userId == principal.userId) {
            return Outcome.refused("You cannot endorse your own work.")
        }
        if (commitment.status != CommitmentStatus.COMPLETED) {
            return Outcome.refused(
                "This can be said once the work is finished and the organiser has confirmed it.",
            )
        }
        // Only somebody who was there. For an opportunity that means the organiser; there
        // is deliberately no path for a bystander who liked the sound of it.
        val subject = commitment.subject
        if (subject is CommitmentSubject.Opportunity) {
            val opportunity = opportunities.find(subject.id)
            if (opportunity?.organizerId != principal.userId) {
                return Outcome.refused(
                    "Only the organiser of this work can say how it went.",
                )
            }
        }
        if (note != null && note.length > TaskEndorsement.MAX_NOTE) {
            return Outcome.invalid("note", "Keep it under ${TaskEndorsement.MAX_NOTE} characters.")
        }

        val now = clock.now()
        val endorsement = TaskEndorsement(
            fromUserId = principal.userId,
            aboutUserId = commitment.userId,
            category = category,
            commitmentId = commitmentId,
            note = note?.trim()?.ifBlank { null },
            createdAt = now,
        )

        val already = trust.endorsementsFor(commitment.userId)
        if (already.any { it.commitmentId == commitmentId && it.fromUserId == principal.userId }) {
            return Outcome.refused("You have already said this about that work.")
        }
        trust.saveEndorsement(endorsement)

        val record = trust.recordFor(commitment.userId)
        trust.save(
            record.copy(
                taskEndorsements = record.taskEndorsements +
                    (category to (record.taskEndorsements[category] ?: 0) + 1),
            ),
        )

        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = commitment.userId,
                kind = NotificationKind.COMMITMENT_CHANGED,
                title = "Someone said how your help went",
                body = endorsement.note ?: "The organiser confirmed the work you did.",
                createdAt = now,
            ),
        )
        return Outcome.Success(endorsement)
    }
}

/**
 * Something went wrong during real-world service.
 *
 * Distinct from a report, and the difference matters. A report is about a *person* and
 * goes to the safety queue as a possible violation. An incident is about an *occasion* —
 * somebody was hurt, a room was unsupervised, a lift never arrived — and most of them are
 * nobody's fault. Filing them under "report" would mean an organiser who did the right
 * thing by telling us has accused a volunteer of something.
 *
 * The ones that involve a child or a supervision failure do raise a case, because those
 * are the two where "nobody's fault" is a judgement for the safeguarding team rather than
 * for the person filing.
 */
public class ReportSafetyIncidentUseCase(
    private val moderation: ModerationRepository,
    private val opportunities: OpportunityRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val kind: IncidentKind,
        val description: String,
        val commitmentId: CommitmentId? = null,
        val listingId: ListingId? = null,
        val organizationNotified: Boolean = false,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<SafetyIncident> {
        if (command.description.isBlank()) {
            return Outcome.invalid("description", "Say what happened, in your own words.")
        }

        val now = clock.now()

        // Two kinds are escalated whatever the reporter thinks. Supervision failures and
        // anything on a listing involving young people are exactly the cases where the
        // person on the ground is least placed to decide it was nothing.
        val involvesMinors = command.listingId
            ?.let { opportunities.find(it) }
            ?.childSafeguardingRequired == true
        val escalate = command.kind == IncidentKind.NO_SUPERVISION ||
            command.kind == IncidentKind.BOUNDARY_VIOLATION ||
            involvesMinors

        val caseId = if (escalate) {
            val case = moderation.saveCase(
                ModerationCase(
                    id = ModerationCaseId(ids.newId()),
                    subjectUserId = null,
                    reportIds = emptyList(),
                    category = if (involvesMinors) {
                        ReportCategory.CHILD_SAFETY
                    } else {
                        ReportCategory.UNSAFE_VOLUNTEERING
                    },
                    severity = if (involvesMinors) ReportSeverity.CRITICAL else ReportSeverity.HIGH,
                    state = CaseState.OPEN,
                    summary = "Incident reported: ${command.kind.displayName}. " +
                        command.description.trim(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            case.id
        } else {
            null
        }

        val incident = moderation.saveIncident(
            SafetyIncident(
                id = ids.newId(),
                reportedBy = principal.userId,
                commitmentId = command.commitmentId,
                listingId = command.listingId,
                kind = command.kind,
                description = command.description.trim(),
                occurredAt = now,
                organizationNotified = command.organizationNotified,
                caseId = caseId,
                createdAt = now,
            ),
        )
        return Outcome.Success(incident)
    }
}

/**
 * Where a member is signed in.
 *
 * The model has existed with nothing producing one, which meant a person who suspected
 * somebody else had their password had no way to look and no way to act. Both halves
 * matter: a list without a revoke button is an anxiety generator.
 */
public class DeviceSessionUseCase(
    private val devices: DeviceSessionRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend fun record(
        principal: Principal,
        deviceLabel: String,
        platform: String,
        ipHash: String?,
    ): Outcome<org.fisabilillah.core.model.DeviceSession> {
        val now = clock.now()
        val existing = devices.forUser(principal.userId)
        val session = org.fisabilillah.core.model.DeviceSession(
            id = ids.newId(),
            userId = principal.userId,
            deviceLabel = deviceLabel.ifBlank { "Unknown device" },
            platform = platform,
            // A hash rather than the address. Enough to say "somewhere different"; not
            // enough to be a location history of the member on their own account page.
            ipHash = ipHash,
            createdAt = now,
            lastSeenAt = now,
        )
        devices.save(session)

        // Only after the first one. Telling somebody about a "new sign-in" the first time
        // they ever sign in is noise that teaches people to ignore the alert.
        if (existing.isNotEmpty()) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = principal.userId,
                    kind = NotificationKind.NEW_DEVICE_LOGIN,
                    title = "New sign-in on ${session.deviceLabel}",
                    body = "If this was not you, end that session and change your password.",
                    createdAt = now,
                ),
            )
        }
        return Outcome.Success(session)
    }

    public suspend fun mine(principal: Principal): Outcome<List<org.fisabilillah.core.model.DeviceSession>> =
        Outcome.Success(devices.forUser(principal.userId).sortedByDescending { it.lastSeenAt })

    public suspend fun revoke(principal: Principal, sessionId: String): Outcome<Unit> {
        val session = devices.find(sessionId) ?: return Outcome.NotFound("that session")
        if (session.userId != principal.userId) {
            return Outcome.refused("You can only end your own sessions.")
        }
        devices.save(session.copy(revokedAt = clock.now()))
        return Outcome.Success(Unit)
    }

    /** The "this wasn't me" button: everything except the device asking. */
    public suspend fun revokeAllOthers(principal: Principal, keep: String): Outcome<Int> {
        val now = clock.now()
        var ended = 0
        for (session in devices.forUser(principal.userId)) {
            if (session.id != keep && session.isActive) {
                devices.save(session.copy(revokedAt = now))
                ended++
            }
        }
        return Outcome.Success(ended)
    }
}
