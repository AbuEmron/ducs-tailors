package org.fisabilillah.core.domain

import org.fisabilillah.core.model.Attestation
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.Qualification
import org.fisabilillah.core.model.QualificationId
import org.fisabilillah.core.model.QualificationReviewState
import org.fisabilillah.core.model.LearningSubject
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.VerificationMethod
import org.fisabilillah.core.model.VerificationRequest
import org.fisabilillah.core.model.VerificationRequestId
import org.fisabilillah.core.model.VerificationRequestState
import org.fisabilillah.core.policy.ValidationError

/**
 * Ordered by [VerificationLevel.rank] rather than by declaration order.
 *
 * The two happen to agree today. Relying on that would mean a future level inserted in the
 * middle of the enum silently reorders every comparison in this file, and the direction it
 * would fail in is "somebody is treated as more verified than they are".
 */
private val BY_RANK: Comparator<VerificationLevel> = compareBy { it.rank }

/**
 * Trust is granted, never claimed.
 *
 * This is the missing half of the trust system. Everything that *reads* a verification
 * level has existed since the first release — the contact policy, the safeguard floors,
 * the whole formal-introduction workflow — and nothing could ever *raise* one. A member
 * reached `EMAIL_VERIFIED` at onboarding and stopped there, which meant every safeguard
 * written against `IDENTITY_VERIFIED` was unreachable, and the introduction feature, the
 * most carefully built thing in the product, could not be used by anybody at all.
 *
 * ## The rules that matter
 *
 * A member submits a request and supplies evidence. They cannot set a level, they cannot
 * review their own request, and the level they ask for is a request rather than an
 * assertion. A reviewer grants at most what the *method* can justify: a phone confirmation
 * cannot produce `IDENTITY_VERIFIED` however generous the reviewer feels, because the
 * ceiling lives on [VerificationMethod] and not in the reviewer's judgement.
 *
 * And a level can be taken away. A background check is a statement about a date in the
 * past; revocation is what makes it honest to display one at all.
 */
public class RequestVerificationUseCase(
    private val verifications: VerificationRepository,
    private val profiles: ProfileRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val requestedLevel: VerificationLevel,
        val method: VerificationMethod,
        val evidenceRefs: List<String> = emptyList(),
        val note: String? = null,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<VerificationRequest> {
        val errors = validate(command)
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")

        // Asking for something you already hold is not an error worth a form field, but it
        // is worth refusing: an approval would write an audit entry claiming a change that
        // did not happen.
        if (profile.verificationLevel atLeast command.requestedLevel) {
            return Outcome.refused(
                "Your account already holds ${command.requestedLevel.displayName.lowercase()}.",
            )
        }

        val open = verifications.forUser(principal.userId).filter { it.isOpen }
        if (open.isNotEmpty()) {
            return Outcome.refused(
                "You already have a verification request waiting. We will come back to you " +
                    "on that one before you send another.",
            )
        }

        val now = clock.now()
        val saved = verifications.save(
            VerificationRequest(
                id = VerificationRequestId(ids.newId()),
                // From the principal, not the command. There is no field for it.
                userId = principal.userId,
                requestedLevel = command.requestedLevel,
                method = command.method,
                evidenceRefs = command.evidenceRefs,
                note = command.note?.trim()?.ifBlank { null },
                state = VerificationRequestState.SUBMITTED,
                createdAt = now,
                updatedAt = now,
            ),
        )

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.VERIFICATION_CHANGED,
                subjectType = "verification_request",
                subjectId = saved.id.value,
                summary = "Requested ${command.requestedLevel.displayName} via " +
                    command.method.displayName,
                occurredAt = now,
            ),
        )
        return Outcome.Success(saved)
    }

    private fun validate(command: Command): List<ValidationError> = buildList {
        if (command.requestedLevel == VerificationLevel.NONE) {
            add(ValidationError("requestedLevel", "Choose what you would like verified."))
        }
        if (!(command.method.ceiling atLeast command.requestedLevel)) {
            add(
                ValidationError(
                    "method",
                    "A ${command.method.displayName.lowercase()} can confirm at most " +
                        "${command.method.ceiling.displayName.lowercase()}.",
                ),
            )
        }
        // Anything above a confirmed channel needs something a reviewer can actually look at.
        val needsEvidence = command.method != VerificationMethod.EMAIL &&
            command.method != VerificationMethod.PHONE
        if (needsEvidence && command.evidenceRefs.isEmpty()) {
            add(
                ValidationError(
                    "evidenceRefs",
                    "Attach the document you would like checked. It is stored privately and " +
                        "only a reviewer can open it.",
                ),
            )
        }
    }
}

/** A reviewer's decision on a verification request. */
public class DecideVerificationUseCase(
    private val verifications: VerificationRepository,
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend fun queue(principal: Principal): Outcome<List<VerificationRequest>> {
        if (!principal.isModerator) {
            return Outcome.refused("Only the safety team can review verification requests.")
        }
        return Outcome.Success(verifications.openRequests())
    }

    public suspend fun approve(
        principal: Principal,
        requestId: VerificationRequestId,
        note: String,
    ): Outcome<VerificationRequest> = decide(principal, requestId, approved = true, note = note)

    public suspend fun reject(
        principal: Principal,
        requestId: VerificationRequestId,
        note: String,
    ): Outcome<VerificationRequest> = decide(principal, requestId, approved = false, note = note)

    private suspend fun decide(
        principal: Principal,
        requestId: VerificationRequestId,
        approved: Boolean,
        note: String,
    ): Outcome<VerificationRequest> {
        if (!principal.isModerator) {
            return Outcome.refused("Only the safety team can review verification requests.")
        }
        if (note.isBlank()) {
            return Outcome.invalid("note", "Say why. The member is shown this in full.")
        }

        val request = verifications.find(requestId) ?: return Outcome.NotFound("that request")
        if (!request.isOpen) {
            return Outcome.refused("That request has already been decided.")
        }
        // Nobody verifies themselves, whatever roles they hold.
        if (request.userId == principal.userId) {
            return Outcome.refused("You cannot decide a verification request about your own account.")
        }

        val now = clock.now()
        val decided = verifications.save(
            request.copy(
                state = if (approved) {
                    VerificationRequestState.APPROVED
                } else {
                    VerificationRequestState.REJECTED
                },
                reviewedBy = principal.userId,
                reviewedAt = now,
                decisionNote = note.trim(),
                updatedAt = now,
            ),
        )

        if (approved) {
            val profile = profiles.find(request.userId)
            if (profile != null) {
                // The method's ceiling wins over the request. A reviewer who approves an
                // identity level on the strength of a phone confirmation gets the phone
                // level, not the one they clicked.
                val granted = minOf(request.requestedLevel, request.method.ceiling, BY_RANK)
                profiles.save(
                    profile.copy(
                        verificationLevel = maxOf(profile.verificationLevel, granted, BY_RANK),
                        updatedAt = now,
                    ),
                )
            }
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.VERIFICATION_CHANGED,
                subjectType = "verification_request",
                subjectId = requestId.value,
                summary = if (approved) {
                    "Approved ${request.requestedLevel.displayName}: ${note.trim()}"
                } else {
                    "Not approved: ${note.trim()}"
                },
                occurredAt = now,
            ),
        )

        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = request.userId,
                kind = NotificationKind.VERIFICATION_OUTCOME,
                title = if (approved) "Your verification was approved" else "Your verification was not approved",
                body = note.trim(),
                createdAt = now,
            ),
        )
        return Outcome.Success(decided)
    }

    /**
     * Taking a level away.
     *
     * The reason this exists at all: a background check describes a date in the past, and
     * a platform that can only ever add trust markers ends up displaying a badge it knows
     * to be wrong. Revocation drops the profile back to whatever its remaining approved
     * requests still justify, rather than to zero — losing a background check should not
     * also un-confirm somebody's email address.
     */
    public suspend fun revoke(
        principal: Principal,
        requestId: VerificationRequestId,
        reason: String,
    ): Outcome<VerificationRequest> {
        if (!principal.isSafetyAdmin) {
            return Outcome.refused("Revoking a verification requires a safety administrator.")
        }
        if (reason.isBlank()) {
            return Outcome.invalid("reason", "Say why this is being revoked.")
        }

        val request = verifications.find(requestId) ?: return Outcome.NotFound("that request")
        if (!request.grantsLevel) {
            return Outcome.refused("That request does not currently grant anything.")
        }

        val now = clock.now()
        val revoked = verifications.save(
            request.copy(
                state = VerificationRequestState.REVOKED,
                revokedAt = now,
                revokedBy = principal.userId,
                decisionNote = reason.trim(),
                updatedAt = now,
            ),
        )

        val profile = profiles.find(request.userId)
        if (profile != null) {
            val remaining = verifications.forUser(request.userId)
                .filter { it.grantsLevel }
                .map { minOf(it.requestedLevel, it.method.ceiling, BY_RANK) }
                .maxByOrNull { it.rank }
                ?: VerificationLevel.NONE
            profiles.save(profile.copy(verificationLevel = remaining, updatedAt = now))
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.VERIFICATION_CHANGED,
                subjectType = "verification_request",
                subjectId = requestId.value,
                summary = "Revoked: ${reason.trim()}",
                occurredAt = now,
            ),
        )
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = request.userId,
                kind = NotificationKind.VERIFICATION_OUTCOME,
                title = "A verification on your account was withdrawn",
                body = reason.trim(),
                createdAt = now,
            ),
        )
        return Outcome.Success(revoked)
    }
}

/** What a member can see about their own verification. */
public class MyVerificationUseCase(
    private val verifications: VerificationRepository,
    private val profiles: ProfileRepository,
) {
    public data class State(
        val currentLevel: VerificationLevel,
        val requests: List<VerificationRequest>,
    ) {
        public val hasOpenRequest: Boolean get() = requests.any { it.isOpen }
    }

    public suspend operator fun invoke(principal: Principal): Outcome<State> {
        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        return Outcome.Success(
            State(
                currentLevel = profile.verificationLevel,
                requests = verifications.forUser(principal.userId).sortedByDescending { it.createdAt },
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Qualifications
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Claiming a credential.
 *
 * A qualification is a claim about competence, and the platform's position throughout is
 * that a claim and a confirmation are different things that must never be shown the same
 * way. So the claim is stored exactly as made, marked as a claim, and displayed as one
 * until somebody qualified says otherwise.
 */
public class SubmitQualificationUseCase(
    private val qualifications: QualificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val title: String,
        val issuingBody: String,
        val issuedYear: Int? = null,
        val subject: LearningSubject? = null,
        val documentRefs: List<String> = emptyList(),
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<Qualification> {
        val errors = buildList {
            if (command.title.isBlank()) {
                add(ValidationError("title", "What is the qualification called?"))
            }
            if (command.issuingBody.isBlank()) {
                add(
                    ValidationError(
                        "issuingBody",
                        "Who issued it? A reviewer needs somewhere to check.",
                    ),
                )
            }
            val year = command.issuedYear
            if (year != null && (year < 1900 || year > clock.currentYear())) {
                add(ValidationError("issuedYear", "That year does not look right."))
            }
        }
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val now = clock.now()
        val saved = qualifications.save(
            Qualification(
                id = QualificationId(ids.newId()),
                userId = principal.userId,
                title = command.title.trim(),
                issuingBody = command.issuingBody.trim(),
                issuedYear = command.issuedYear,
                subject = command.subject,
                documentRefs = command.documentRefs,
                // Forced. There is no parameter a claimant could put a decision in.
                reviewState = QualificationReviewState.SUBMITTED,
                verifiedAt = null,
                verifiedBy = null,
                reviewerNote = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.QUALIFICATION_REVIEWED,
                subjectType = "qualification",
                subjectId = saved.id.value,
                summary = "Claimed: ${saved.title} (${saved.issuingBody})",
                occurredAt = now,
            ),
        )
        return Outcome.Success(saved)
    }
}

/**
 * Confirming or declining a claimed credential.
 *
 * A scholar may decide these as well as a moderator, and that is the point: whether an
 * ijazah is real is not a question a safety team is equipped to answer, and pretending
 * otherwise would produce a badge that means "somebody in an office was satisfied".
 */
public class ReviewQualificationUseCase(
    private val qualifications: QualificationRepository,
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend fun queue(principal: Principal): Outcome<List<Qualification>> {
        if (!canReview(principal)) {
            return Outcome.refused("Only a moderator or a listed scholar can review qualifications.")
        }
        return Outcome.Success(qualifications.pendingReview())
    }

    public suspend fun decide(
        principal: Principal,
        qualificationId: QualificationId,
        verified: Boolean,
        note: String,
    ): Outcome<Qualification> {
        if (!canReview(principal)) {
            return Outcome.refused("Only a moderator or a listed scholar can review qualifications.")
        }
        if (note.isBlank()) {
            return Outcome.invalid("note", "Say what you checked. The claimant sees this.")
        }

        val existing = qualifications.find(qualificationId)
            ?: return Outcome.NotFound("that qualification")
        if (existing.userId == principal.userId) {
            return Outcome.refused("You cannot review your own qualification.")
        }

        val now = clock.now()
        val decided = qualifications.save(
            existing.copy(
                reviewState = if (verified) {
                    QualificationReviewState.VERIFIED
                } else {
                    QualificationReviewState.NOT_VERIFIED
                },
                verifiedAt = if (verified) now else null,
                verifiedBy = if (verified) principal.userId else null,
                reviewerNote = note.trim(),
                updatedAt = now,
            ),
        )

        // The attestation is what other members see. It is added on confirmation and
        // removed again if the last confirmed qualification goes away, so the badge never
        // outlives what it stands for.
        val profile = profiles.find(existing.userId)
        if (profile != null) {
            val stillHasOne = qualifications.forUser(existing.userId)
                .any { it.id != decided.id && it.reviewState == QualificationReviewState.VERIFIED }
            val attestations = if (verified || stillHasOne) {
                profile.attestations + Attestation.QUALIFICATION_VERIFIED
            } else {
                profile.attestations - Attestation.QUALIFICATION_VERIFIED
            }
            profiles.save(profile.copy(attestations = attestations, updatedAt = now))
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.QUALIFICATION_REVIEWED,
                subjectType = "qualification",
                subjectId = qualificationId.value,
                summary = if (verified) "Confirmed: ${note.trim()}" else "Not confirmed: ${note.trim()}",
                occurredAt = now,
            ),
        )
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = existing.userId,
                kind = NotificationKind.VERIFICATION_OUTCOME,
                title = if (verified) {
                    "A qualification was confirmed"
                } else {
                    "A qualification was not confirmed"
                },
                body = note.trim(),
                createdAt = now,
            ),
        )
        return Outcome.Success(decided)
    }

    public suspend fun mine(principal: Principal): Outcome<List<Qualification>> =
        Outcome.Success(qualifications.forUser(principal.userId))

    private fun canReview(principal: Principal): Boolean =
        principal.isModerator ||
            org.fisabilillah.core.model.AccountRole.SCHOLAR in principal.roles
}
