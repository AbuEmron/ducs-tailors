package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AccountStatus
import org.fisabilillah.core.model.Appeal
import org.fisabilillah.core.model.AppealId
import org.fisabilillah.core.model.AppealState
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.CaseState
import org.fisabilillah.core.model.ConversationState
import org.fisabilillah.core.model.EvidenceKind
import org.fisabilillah.core.model.ModerationAction
import org.fisabilillah.core.model.ModerationActionId
import org.fisabilillah.core.model.ModerationActionType
import org.fisabilillah.core.model.ModerationCase
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.Report
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportEvidence
import org.fisabilillah.core.model.ReportId
import org.fisabilillah.core.model.ReportState
import org.fisabilillah.core.model.ReportTarget
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.RestrictionId
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.policy.AppealReviewDecision
import org.fisabilillah.core.policy.ModerationPolicy
import org.fisabilillah.core.policy.ReportPatternAssessment
import kotlin.time.Duration.Companion.days

/**
 * Making a report.
 *
 * Evidence is captured at the moment of reporting rather than fetched later, because by
 * the time a moderator looks the message may have been unsent, the listing taken down, and
 * the profile rewritten. A report with nothing behind it protects nobody.
 */
public class SubmitReportUseCase(
    private val moderation: ModerationRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val restrictions: RestrictionRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val target: ReportTarget,
        val category: ReportCategory,
        val description: String,
        val includeConversationEvidence: Boolean = true,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<Report> {
        if (command.description.isBlank()) {
            return Outcome.invalid("description", "Please describe what happened.")
        }
        val now = clock.now()

        if (restrictions.activeFor(principal.userId, now)
                .any { it.capability.covers(RestrictedCapability.SUBMIT_REPORTS) }
        ) {
            return Outcome.refused(
                "Your account is currently restricted from submitting reports.",
                RefusalCode.RESTRICTED,
            )
        }

        val reportId = ReportId(ids.newId())
        val evidence = mutableListOf<ReportEvidence>()

        if (command.includeConversationEvidence) {
            val conversationId = when (val target = command.target) {
                is ReportTarget.MessageTarget -> target.conversationId
                is ReportTarget.ConversationTarget -> target.id
                else -> null
            }
            if (conversationId != null) {
                conversations.find(conversationId)?.let { conversation ->
                    evidence += ReportEvidence(
                        id = ids.newId(),
                        reportId = reportId,
                        kind = EvidenceKind.CONVERSATION_SNAPSHOT,
                        reference = conversation.id.value,
                        capturedAt = now,
                        capturedBy = principal.userId,
                        notes = "Purpose: ${conversation.purpose.kind.name}; " +
                            "requirements: ${conversation.appliedRequirements.joinToString { it.name }}",
                    )
                }
                // Unsent messages are included by reference: the preserved originals live
                // in the moderator-only store and are the whole reason unsend does not
                // destroy them.
                val redactions = messages.redactionsFor(conversationId)
                for (redaction in redactions) {
                    evidence += ReportEvidence(
                        id = ids.newId(),
                        reportId = reportId,
                        kind = EvidenceKind.MESSAGE_SNAPSHOT,
                        reference = redaction.messageId.value,
                        capturedAt = now,
                        capturedBy = null,
                        notes = "Preserved copy of a message the sender unsent",
                    )
                }
            }
        }

        val report = moderation.saveReport(
            Report(
                id = reportId,
                reporterId = principal.userId,
                target = command.target,
                category = command.category,
                description = command.description,
                evidence = evidence,
                state = ReportState.RECEIVED,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // A critical report opens a case immediately rather than waiting for triage.
        if (command.category.requiresImmediateHumanReview) {
            val subject = (command.target as? ReportTarget.User)?.id
            val case = moderation.saveCase(
                ModerationCase(
                    id = ModerationCaseId(ids.newId()),
                    subjectUserId = subject,
                    reportIds = listOf(reportId),
                    category = command.category,
                    severity = command.category.severity,
                    escalation = ModerationPolicy.initialEscalation(command.category),
                    state = CaseState.OPEN,
                    summary = "Auto-opened for ${command.category.displayName}",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            moderation.saveReport(report.copy(caseId = case.id, state = ReportState.TRIAGED))
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.REPORT_SUBMITTED,
                subjectType = "report",
                subjectId = reportId.value,
                summary = "${command.category.name} reported; ${evidence.size} evidence items " +
                    "preserved",
                occurredAt = now,
            ),
        )

        return Outcome.Success(report)
    }

    /**
     * Whether this reporter's recent pattern warrants a look. Surfaced to the safety team
     * alongside the report, never used to silently discard it.
     */
    public suspend fun patternAssessment(reporterId: UserId): ReportPatternAssessment {
        val recent = moderation.reportsBy(reporterId, clock.now() - 90.days)
        val upheld = recent.count { it.state == ReportState.ACTIONED }
        return ModerationPolicy.assessReportPattern(recent, upheld)
    }
}

/**
 * A moderator acting on a case.
 *
 * Three things happen together or not at all: the action is appended, an audit entry is
 * written, and the member is told. The audit entry is not a side effect that a future
 * refactor might drop — a moderator who could act without leaving a record is the single
 * most dangerous account on the platform.
 */
public class TakeModerationActionUseCase(
    private val moderation: ModerationRepository,
    private val profiles: ProfileRepository,
    private val restrictions: RestrictionRepository,
    private val conversations: ConversationRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val caseId: ModerationCaseId,
        val type: ModerationActionType,
        val targetUserId: UserId? = null,
        val targetReference: String? = null,
        val rationale: String,
        val restrictedCapability: RestrictedCapability? = null,
        val durationDays: Int? = null,
        val notifyUser: Boolean = true,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<ModerationAction> {
        if (!ModerationPolicy.isAuthorized(command.type, principal.roles)) {
            return Outcome.refused(
                if (principal.isModerator) {
                    "This action requires a safety administrator."
                } else {
                    "Only the safety team can take moderation actions."
                },
            )
        }
        if (command.rationale.isBlank()) {
            return Outcome.invalid("rationale", "Record why this action is being taken.")
        }
        val case = moderation.findCase(command.caseId) ?: return Outcome.NotFound("that case")

        val now = clock.now()
        val expiresAt: Timestamp? = command.durationDays?.let { now + it.days }

        val action = moderation.appendAction(
            ModerationAction(
                id = ModerationActionId(ids.newId()),
                caseId = case.id,
                moderatorId = principal.userId,
                type = command.type,
                targetUserId = command.targetUserId,
                targetReference = command.targetReference,
                rationale = command.rationale,
                notifiedUser = command.notifyUser,
                expiresAt = expiresAt,
                performedAt = now,
            ),
        )

        // The audit entry is written before any state change, so that an action which
        // fails halfway still leaves a trace of having been attempted.
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = ModerationPolicy.auditActionFor(command.type),
                subjectType = "moderation_case",
                subjectId = case.id.value,
                summary = "${command.type.name}: ${command.rationale}",
                metadata = buildMap {
                    command.targetUserId?.let { put("targetUserId", it.value) }
                    command.targetReference?.let { put("targetReference", it) }
                    expiresAt?.let { put("expiresAt", it.toString()) }
                },
                occurredAt = now,
            ),
        )

        applySideEffects(command, action, now)

        if (command.notifyUser && command.targetUserId != null) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = command.targetUserId,
                    kind = NotificationKind.MODERATION_OUTCOME,
                    title = "A moderation decision has been made",
                    body = ModerationPolicy.enforcementNotice(action),
                    createdAt = now,
                ),
            )
        }

        return Outcome.Success(action)
    }

    private suspend fun applySideEffects(
        command: Command,
        action: ModerationAction,
        now: Timestamp,
    ) {
        val target = command.targetUserId
        when (command.type) {
            ModerationActionType.FEATURE_RESTRICTED -> {
                if (target != null) {
                    restrictions.add(
                        Restriction(
                            id = RestrictionId(ids.newId()),
                            userId = target,
                            capability = command.restrictedCapability ?: RestrictedCapability.ALL,
                            reason = command.rationale,
                            imposedBy = action.moderatorId,
                            caseId = action.caseId,
                            startsAt = now,
                            expiresAt = action.expiresAt,
                        ),
                    )
                }
            }

            ModerationActionType.ACCOUNT_SUSPENDED ->
                target?.let { setStatus(it, AccountStatus.SUSPENDED, now) }

            ModerationActionType.ACCOUNT_BANNED ->
                target?.let { setStatus(it, AccountStatus.BANNED, now) }

            ModerationActionType.CONVERSATION_FROZEN -> {
                val conversationId = command.targetReference
                    ?.let { org.fisabilillah.core.model.ConversationId(it) }
                if (conversationId != null) {
                    conversations.find(conversationId)?.let {
                        conversations.save(
                            it.copy(
                                state = ConversationState.FROZEN,
                                frozenByCaseId = action.caseId,
                                updatedAt = now,
                            ),
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    private suspend fun setStatus(userId: UserId, status: AccountStatus, now: Timestamp) {
        profiles.find(userId)?.let {
            profiles.save(it.copy(status = status, updatedAt = now))
        }
    }
}

/**
 * Appeals.
 *
 * Reviewed by someone who was not involved in the original decision — checked here rather
 * than left to convention, because an appeals process staffed by the person being appealed
 * against is worse than none at all: it produces a record that says the decision was
 * reviewed.
 */
/**
 * What was done to me, and can I argue with it.
 *
 * Every restriction on this platform is appealable, and until this existed there was no
 * way for a member to find out what they were appealing: the restriction is applied by a
 * moderator, notified once, and after that lived only in a table the member cannot read.
 * A right of appeal nobody can reach is not a right of appeal.
 *
 * What comes back is deliberately complete. The reason recorded by the moderator is
 * included verbatim, because a person told only that they are "restricted from starting
 * conversations" cannot form an argument, and the appeal that follows would be a guess.
 */
public class MyModerationRecordUseCase(
    private val restrictions: RestrictionRepository,
    private val moderation: ModerationRepository,
    private val clock: AppClock,
) {

    public data class RestrictionRecord(
        val restriction: Restriction,
        /** The case it came from, when there is one. */
        val case: ModerationCase?,
        /** An appeal already lodged against that case, if any. */
        val appeal: Appeal?,
    ) {
        /**
         * One appeal per case. A member who has already appealed is shown the state of
         * that appeal rather than an empty form, and cannot lodge a second one while the
         * first is being read.
         */
        public val canAppeal: Boolean
            get() = case != null &&
                (appeal == null || appeal.state == AppealState.WITHDRAWN)
    }

    public suspend operator fun invoke(principal: Principal): Outcome<List<RestrictionRecord>> {
        val now = clock.now()
        val active = restrictions.activeFor(principal.userId, now)

        val records = active.map { restriction ->
            val case = restriction.caseId?.let { moderation.findCase(it) }
            val appeal = restriction.caseId
                ?.let { moderation.appealsFor(it) }
                ?.firstOrNull { it.appellantId == principal.userId }
            RestrictionRecord(restriction, case, appeal)
        }
        return Outcome.Success(records)
    }
}

/** Appeals waiting for the safety team. */
public class AppealQueueUseCase(
    private val moderation: ModerationRepository,
) {
    public data class QueueItem(
        val appeal: Appeal,
        val case: ModerationCase?,
        /**
         * False when this reviewer took the original action. The refusal is enforced in
         * [ReviewAppealUseCase.decide]; surfacing it here means the interface can say why
         * rather than offering a button that will be refused.
         */
        val reviewableByMe: Boolean,
    )

    public suspend operator fun invoke(principal: Principal): Outcome<List<QueueItem>> {
        if (!principal.isModerator) {
            return Outcome.refused("Only the safety team can see appeals.")
        }
        val items = moderation.openAppeals().map { appeal ->
            val actions = moderation.actionsFor(appeal.caseId)
            QueueItem(
                appeal = appeal,
                case = moderation.findCase(appeal.caseId),
                reviewableByMe = ModerationPolicy.canReviewAppeal(
                    appeal = appeal,
                    reviewerId = principal.userId,
                    reviewerRoles = principal.roles,
                    originalActions = actions,
                ) == AppealReviewDecision.Permitted,
            )
        }.sortedBy { it.appeal.createdAt }
        return Outcome.Success(items)
    }
}

public class ReviewAppealUseCase(
    private val moderation: ModerationRepository,
    private val restrictions: RestrictionRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend fun submit(
        principal: Principal,
        caseId: ModerationCaseId,
        statement: String,
    ): Outcome<Appeal> {
        if (statement.isBlank()) {
            return Outcome.invalid("statement", "Explain why you are appealing.")
        }
        val case = moderation.findCase(caseId) ?: return Outcome.NotFound("that case")
        if (case.subjectUserId != principal.userId) {
            return Outcome.refused("You can only appeal a decision about your own account.")
        }
        val now = clock.now()
        val appeal = moderation.saveAppeal(
            Appeal(
                id = AppealId(ids.newId()),
                caseId = caseId,
                appellantId = principal.userId,
                statement = statement,
                state = AppealState.SUBMITTED,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return Outcome.Success(appeal)
    }

    public suspend fun decide(
        principal: Principal,
        appealId: AppealId,
        state: AppealState,
        decisionNote: String,
    ): Outcome<Appeal> {
        val appeal = moderation.findAppeal(appealId) ?: return Outcome.NotFound("that appeal")
        val originalActions = moderation.actionsFor(appeal.caseId)

        when (
            val decision = ModerationPolicy.canReviewAppeal(
                appeal = appeal,
                reviewerId = principal.userId,
                reviewerRoles = principal.roles,
                originalActions = originalActions,
            )
        ) {
            is AppealReviewDecision.Refused -> return Outcome.refused(decision.message)
            AppealReviewDecision.Permitted -> Unit
        }

        val now = clock.now()
        val saved = moderation.saveAppeal(
            appeal.copy(
                state = state,
                reviewedBy = principal.userId,
                reviewedAt = now,
                decisionNote = decisionNote,
                updatedAt = now,
            ),
        )

        if (state == AppealState.UPHELD) {
            for (restriction in restrictions.activeFor(appeal.appellantId, now)) {
                if (restriction.caseId == appeal.caseId) {
                    restrictions.lift(restriction.id, principal.userId, now)
                }
            }
        }

        moderation.appendAction(
            ModerationAction(
                id = ModerationActionId(ids.newId()),
                caseId = appeal.caseId,
                moderatorId = principal.userId,
                type = if (state == AppealState.UPHELD) {
                    ModerationActionType.APPEAL_UPHELD
                } else {
                    ModerationActionType.APPEAL_REJECTED
                },
                targetUserId = appeal.appellantId,
                rationale = decisionNote,
                performedAt = now,
            ),
        )

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.APPEAL_DECIDED,
                subjectType = "appeal",
                subjectId = appealId.value,
                summary = "${state.name}: $decisionNote",
                occurredAt = now,
            ),
        )

        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = appeal.appellantId,
                kind = NotificationKind.APPEAL_OUTCOME,
                title = "Your appeal has been reviewed",
                body = "${state.displayName}. $decisionNote",
                createdAt = now,
            ),
        )

        return Outcome.Success(saved)
    }
}

/** The moderator's queue, ordered by how urgently each item needs a human. */
public class ModerationQueueUseCase(
    private val moderation: ModerationRepository,
) {
    public suspend operator fun invoke(principal: Principal): Outcome<List<QueueItem>> {
        if (!principal.isModerator) {
            return Outcome.refused("Only the safety team can see the moderation queue.")
        }
        val cases = moderation.openCases().items
        val items = cases.map { case ->
            QueueItem(
                case = case,
                triage = ModerationPolicy.triageTarget(case.category),
                reports = case.reportIds.size,
            )
        }.sortedWith(
            compareByDescending<QueueItem> { it.case.severity.rank }
                .thenByDescending { it.case.escalation.rank }
                .thenBy { it.case.createdAt },
        )
        return Outcome.Success(items)
    }

    public data class QueueItem(
        val case: ModerationCase,
        val triage: org.fisabilillah.core.policy.TriageTarget,
        val reports: Int,
    )
}
