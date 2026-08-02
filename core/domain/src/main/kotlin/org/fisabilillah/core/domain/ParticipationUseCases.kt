package org.fisabilillah.core.domain

import org.fisabilillah.core.model.ApplicationId
import org.fisabilillah.core.model.ApplicationStatus
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.CommitmentId
import org.fisabilillah.core.model.CommitmentStatus
import org.fisabilillah.core.model.CommitmentSubject
import org.fisabilillah.core.model.EnrollmentId
import org.fisabilillah.core.model.EnrollmentStatus
import org.fisabilillah.core.model.LearningEnrollment
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.ListingStatus
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.Punctuality
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.RequestResponse
import org.fisabilillah.core.model.RequestStatus
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.VolunteerApplication
import org.fisabilillah.core.model.VolunteerOpportunity
import kotlin.time.Duration.Companion.minutes

/**
 * Applying to volunteer.
 *
 * Where an activity involves minors or entering someone's home, a background check is not
 * a preference the organiser can waive in the application flow — the check is required
 * before the application can be submitted at all.
 */
public class ApplyToOpportunityUseCase(
    private val opportunities: OpportunityRepository,
    private val profiles: ProfileRepository,
    private val restrictions: RestrictionRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        opportunityId: ListingId,
        message: String,
    ): Outcome<VolunteerApplication> {
        val opportunity = opportunities.find(opportunityId)
            ?: return Outcome.NotFound("that opportunity")
        val volunteer = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val now = clock.now()

        if (restrictions.activeFor(principal.userId, now)
                .any { it.capability.covers(RestrictedCapability.APPLY_TO_OPPORTUNITIES) }
        ) {
            return Outcome.refused(
                "Your account is currently restricted from applying to opportunities.",
                RefusalCode.RESTRICTED,
            )
        }
        if (!opportunity.status.acceptsApplications) {
            return Outcome.refused("This opportunity is not accepting applications.")
        }
        if (opportunity.isFull) {
            return Outcome.refused("This opportunity is full.")
        }
        if (!opportunity.genderArrangement.admits(volunteer.gender)) {
            return Outcome.refused(
                "This activity is arranged as ${opportunity.genderArrangement.displayName.lowercase()}.",
            )
        }
        if (opportunity.backgroundCheckIsMandatory &&
            !(volunteer.verificationLevel atLeast VerificationLevel.BACKGROUND_CHECKED)
        ) {
            return Outcome.refused(
                "This activity involves people who need extra protection, so a completed " +
                    "background check is required before you can apply.",
                RefusalCode.NEEDS_VERIFICATION,
            )
        }
        if (opportunities.applicationsOf(principal.userId)
                .any { it.opportunityId == opportunityId && it.status == ApplicationStatus.SUBMITTED }
        ) {
            return Outcome.refused("You have already applied to this.", RefusalCode.CONFLICT)
        }

        val application = opportunities.saveApplication(
            VolunteerApplication(
                id = ApplicationId(ids.newId()),
                opportunityId = opportunityId,
                volunteerId = principal.userId,
                message = message,
                status = ApplicationStatus.SUBMITTED,
                createdAt = now,
                updatedAt = now,
            ),
        )

        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = opportunity.organizerId,
                kind = NotificationKind.APPLICATION_DECIDED,
                title = "A volunteer applied",
                body = "${volunteer.displayName} applied to \"${opportunity.title}\".",
                deepLink = "fisabilillah://opportunity/${opportunityId.value}",
                createdAt = now,
            ),
        )
        return Outcome.Success(application)
    }

    /** The organiser's decision. Accepting also creates the volunteer's commitment. */
    public suspend fun decide(
        principal: Principal,
        applicationId: ApplicationId,
        accept: Boolean,
        declineReason: String? = null,
    ): Outcome<VolunteerApplication> {
        val all = opportunities.applicationsOf(principal.userId)
        val application = all.firstOrNull { it.id == applicationId }
            ?: findApplicationAsOrganizer(principal, applicationId)
            ?: return Outcome.NotFound("that application")

        val opportunity = opportunities.find(application.opportunityId)
            ?: return Outcome.NotFound("that opportunity")
        if (opportunity.organizerId != principal.userId) {
            return Outcome.refused("Only the organiser can decide on applications.")
        }

        val now = clock.now()
        val updated = opportunities.saveApplication(
            application.copy(
                status = if (accept) ApplicationStatus.ACCEPTED else ApplicationStatus.DECLINED,
                decidedBy = principal.userId,
                decidedAt = now,
                declineReason = declineReason,
                updatedAt = now,
            ),
        )
        if (accept) {
            opportunities.save(
                opportunity.copy(
                    volunteersConfirmed = opportunity.volunteersConfirmed + 1,
                    status = if (opportunity.volunteersConfirmed + 1 >= opportunity.volunteersNeeded) {
                        ListingStatus.FULL
                    } else {
                        opportunity.status
                    },
                    updatedAt = now,
                ),
            )
        }
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = application.volunteerId,
                kind = NotificationKind.APPLICATION_DECIDED,
                title = if (accept) "You are confirmed" else "Application update",
                body = if (accept) {
                    "You are confirmed for \"${opportunity.title}\"."
                } else {
                    declineReason ?: "Your application was not taken forward this time."
                },
                deepLink = "fisabilillah://opportunity/${opportunity.id.value}",
                createdAt = now,
            ),
        )
        return Outcome.Success(updated)
    }

    private suspend fun findApplicationAsOrganizer(
        principal: Principal,
        applicationId: ApplicationId,
    ): VolunteerApplication? {
        val organised = opportunities.search(OpportunitySearchCriteria(openOnly = false, limit = 200))
            .items.filter { it.organizerId == principal.userId }
        for (opportunity in organised) {
            opportunities.applicationsFor(opportunity.id)
                .firstOrNull { it.id == applicationId }
                ?.let { return it }
        }
        return null
    }
}

/** Enrolling in a class or circle. */
public class EnrollInLearningUseCase(
    private val learning: LearningRepository,
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        offeringId: ListingId,
        message: String? = null,
    ): Outcome<LearningEnrollment> {
        val offering = learning.find(offeringId) ?: return Outcome.NotFound("that class")
        val student = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val instructor = profiles.find(offering.instructorId)

        if (offering.status != ListingStatus.OPEN) {
            return Outcome.refused("This class is not currently open for enrolment.")
        }
        if (offering.placesRemaining == 0) {
            return Outcome.refused("This class is full.")
        }
        if (!offering.genderArrangement.admits(student.gender)) {
            return Outcome.refused(
                "This class is arranged as ${offering.genderArrangement.displayName.lowercase()}.",
            )
        }
        // A teacher who only teaches students of their own gender is expressing a
        // safeguard, not a preference, and it is enforced rather than displayed.
        if (offering.sameGenderStudentsOnly && instructor != null &&
            instructor.gender != student.gender
        ) {
            return Outcome.refused("This teacher takes students of the same gender only.")
        }
        if (learning.enrollmentsOf(principal.userId)
                .any { it.offeringId == offeringId && it.status != EnrollmentStatus.WITHDRAWN }
        ) {
            return Outcome.refused("You have already asked to join this class.", RefusalCode.CONFLICT)
        }

        val now = clock.now()
        val enrollment = learning.saveEnrollment(
            LearningEnrollment(
                id = EnrollmentId(ids.newId()),
                offeringId = offeringId,
                studentId = principal.userId,
                status = EnrollmentStatus.REQUESTED,
                message = message,
                createdAt = now,
                updatedAt = now,
            ),
        )
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = offering.instructorId,
                kind = NotificationKind.ENROLLMENT_DECIDED,
                title = "A student asked to join",
                body = "${student.displayName} asked to join \"${offering.title}\".",
                deepLink = "fisabilillah://learning/${offeringId.value}",
                createdAt = now,
            ),
        )
        return Outcome.Success(enrollment)
    }
}

/** Offering help against a posted request. */
public class RespondToRequestUseCase(
    private val requests: ServiceRequestRepository,
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        requestId: RequestId,
        message: String,
    ): Outcome<RequestResponse> {
        val request = requests.find(requestId) ?: return Outcome.NotFound("that request")
        if (request.requesterId == principal.userId) {
            return Outcome.refused("This is your own request.")
        }
        if (request.status != RequestStatus.OPEN) {
            return Outcome.refused("This request is no longer open.")
        }
        val now = clock.now()
        val response = requests.saveResponse(
            RequestResponse(
                id = ApplicationId(ids.newId()),
                requestId = requestId,
                responderId = principal.userId,
                message = message,
                offeredCategory = request.category,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val responder = profiles.find(principal.userId)
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = request.requesterId,
                kind = NotificationKind.REQUEST_RESPONSE,
                title = "Someone offered to help",
                body = "${responder?.displayName ?: "A member"} responded to your request.",
                deepLink = "fisabilillah://request/${requestId.value}",
                createdAt = now,
            ),
        )
        return Outcome.Success(response)
    }
}

/**
 * Releasing a street address to a specific helper.
 *
 * The one place in the codebase where a precise location becomes visible to someone else,
 * and it requires the requester to act, names exactly who is being told, and writes an
 * audit entry. Everything else in the system reads the approximate location.
 */
public class DiscloseExactLocationUseCase(
    private val requests: ServiceRequestRepository,
    private val auditLog: AuditLogRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        requestId: RequestId,
        discloseTo: UserId,
    ): Outcome<ServiceRequest> {
        val request = requests.find(requestId) ?: return Outcome.NotFound("that request")
        if (request.requesterId != principal.userId) {
            return Outcome.refused("Only the person who posted this can share its address.")
        }
        val now = clock.now()
        val updated = requests.save(
            request.copy(
                exactLocationDisclosed = true,
                exactLocationDisclosedTo = request.exactLocationDisclosedTo + discloseTo,
                assignedHelperId = request.assignedHelperId ?: discloseTo,
                status = if (request.status == RequestStatus.OPEN) {
                    RequestStatus.IN_PROGRESS
                } else {
                    request.status
                },
                updatedAt = now,
            ),
        )
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.EXACT_LOCATION_DISCLOSED,
                subjectType = "service_request",
                subjectId = requestId.value,
                summary = "Exact address released to ${discloseTo.value}",
                occurredAt = now,
            ),
        )
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = discloseTo,
                kind = NotificationKind.REQUEST_RESPONSE,
                title = "An address has been shared with you",
                body = "The full address for \"${request.title}\" is now visible to you. " +
                    "Please treat it carefully.",
                deepLink = "fisabilillah://request/${requestId.value}",
                createdAt = now,
            ),
        )
        return Outcome.Success(updated)
    }
}

/**
 * Turning up, doing the work, and being confirmed.
 *
 * Completion needs the organiser's confirmation as well as the volunteer's check-out.
 * Self-reported good deeds are not a trust signal, and making them one would reward
 * exactly the wrong instinct.
 */
public class CommitmentUseCase(
    private val commitments: CommitmentRepository,
    private val trust: TrustRepository,
    private val opportunities: OpportunityRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend fun create(
        principal: Principal,
        subject: CommitmentSubject,
        title: String,
        startsAt: org.fisabilillah.core.model.Timestamp,
        endsAt: org.fisabilillah.core.model.Timestamp,
        place: org.fisabilillah.core.model.Place?,
    ): Outcome<Commitment> {
        val now = clock.now()
        return Outcome.Success(
            commitments.save(
                Commitment(
                    id = CommitmentId(ids.newId()),
                    userId = principal.userId,
                    subject = subject,
                    title = title,
                    startsAt = startsAt,
                    endsAt = endsAt,
                    place = place,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    public suspend fun checkIn(
        principal: Principal,
        commitmentId: CommitmentId,
    ): Outcome<Commitment> {
        val commitment = commitments.find(commitmentId)
            ?: return Outcome.NotFound("that commitment")
        if (commitment.userId != principal.userId) {
            return Outcome.refused("You can only check in to your own commitments.")
        }
        val now = clock.now()
        val punctuality = when {
            now <= commitment.startsAt + GRACE_MINUTES.minutes -> Punctuality.ON_TIME
            now <= commitment.startsAt + LATE_MINUTES.minutes -> Punctuality.SLIGHTLY_LATE
            else -> Punctuality.LATE
        }
        return Outcome.Success(
            commitments.save(
                commitment.copy(
                    status = CommitmentStatus.CHECKED_IN,
                    checkedInAt = now,
                    punctuality = punctuality,
                    updatedAt = now,
                ),
            ),
        )
    }

    public suspend fun checkOut(
        principal: Principal,
        commitmentId: CommitmentId,
    ): Outcome<Commitment> {
        val commitment = commitments.find(commitmentId)
            ?: return Outcome.NotFound("that commitment")
        if (commitment.userId != principal.userId) {
            return Outcome.refused("You can only check out of your own commitments.")
        }
        val now = clock.now()
        return Outcome.Success(
            commitments.save(commitment.copy(checkedOutAt = now, updatedAt = now)),
        )
    }

    /** The organiser confirming that someone actually did what they said they would. */
    public suspend fun confirmByOrganizer(
        principal: Principal,
        commitmentId: CommitmentId,
        attended: Boolean,
    ): Outcome<Commitment> {
        val commitment = commitments.find(commitmentId)
            ?: return Outcome.NotFound("that commitment")

        val subject = commitment.subject
        if (subject is CommitmentSubject.Opportunity) {
            val opportunity = opportunities.find(subject.id)
            if (opportunity?.organizerId != principal.userId) {
                return Outcome.refused("Only the organiser can confirm this commitment.")
            }
        }

        val now = clock.now()
        val updated = commitments.save(
            commitment.copy(
                status = if (attended) CommitmentStatus.COMPLETED else CommitmentStatus.NO_SHOW,
                organizerConfirmedAt = now,
                organizerConfirmedBy = principal.userId,
                punctuality = if (attended) commitment.punctuality else Punctuality.DID_NOT_ATTEND,
                updatedAt = now,
            ),
        )

        val record = trust.recordFor(commitment.userId)
        trust.save(
            if (attended) {
                record.copy(
                    commitmentsCompleted = record.commitmentsCompleted + 1,
                    organizerConfirmations = record.organizerConfirmations + 1,
                    onTimeArrivals = record.onTimeArrivals +
                        if (commitment.punctuality == Punctuality.ON_TIME) 1 else 0,
                    lateArrivals = record.lateArrivals +
                        if (commitment.punctuality == Punctuality.LATE) 1 else 0,
                )
            } else {
                record.copy(noShows = record.noShows + 1)
            },
        )

        if (attended) {
            val impact = trust.impactFor(commitment.userId)
            val hours = ((commitment.endsAt - commitment.startsAt).inWholeMinutes / 60).toInt()
            trust.saveImpact(
                impact.copy(
                    commitmentsCompleted = impact.commitmentsCompleted + 1,
                    hoursGiven = impact.hoursGiven + hours.coerceAtLeast(0),
                ),
            )
        }

        return Outcome.Success(updated)
    }

    private companion object {
        const val GRACE_MINUTES = 10
        const val LATE_MINUTES = 30
    }
}

/** A member's own record of service. Private unless they choose otherwise. */
public class PrivateImpactUseCase(
    private val trust: TrustRepository,
) {
    public suspend operator fun invoke(
        principal: Principal,
    ): org.fisabilillah.core.model.PrivateImpactRecord = trust.impactFor(principal.userId)
}

/** Creating a volunteer opportunity, with the safeguards an organiser owes their volunteers. */
public class CreateOpportunityUseCase(
    private val opportunities: OpportunityRepository,
    private val clock: AppClock,
) {
    public suspend operator fun invoke(
        principal: Principal,
        draft: VolunteerOpportunity,
    ): Outcome<VolunteerOpportunity> {
        if (draft.organizerId != principal.userId) {
            return Outcome.refused("You can only publish opportunities you are organising.")
        }
        if (draft.completionCriteria.isBlank()) {
            return Outcome.invalid(
                "completionCriteria",
                "Say how a volunteer will know the work is finished.",
            )
        }
        if (draft.category.requiresBackgroundCheckByDefault && !draft.backgroundCheckRequired) {
            return Outcome.invalid(
                "backgroundCheckRequired",
                "This kind of work involves people who need extra protection, so a " +
                    "background check must be required.",
            )
        }
        if (draft.category.involvesMinors && !draft.childSafeguardingRequired) {
            return Outcome.invalid(
                "childSafeguardingRequired",
                "Work with young people needs child safeguarding arrangements in place.",
            )
        }
        val now = clock.now()
        return Outcome.Success(
            opportunities.save(draft.copy(createdAt = now, updatedAt = now)),
        )
    }
}
