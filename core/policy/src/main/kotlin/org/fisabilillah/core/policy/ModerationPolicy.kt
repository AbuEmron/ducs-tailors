package org.fisabilillah.core.policy

import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Appeal
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.EscalationLevel
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.ModerationAction
import org.fisabilillah.core.model.ModerationActionType
import org.fisabilillah.core.model.Report
import org.fisabilillah.core.model.ReportAbuseAssessment
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportSeverity
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import kotlin.time.Duration.Companion.minutes

/**
 * The rules the safety team operates under.
 *
 * Two commitments run through all of it. First, automated signals never decide anything —
 * they change the order of a queue. Second, every consequential action leaves a record
 * that the person who took it cannot alter, because the hardest abuse to defend against
 * on a platform like this is not the harasser but the moderator who decides they know best.
 */
public object ModerationPolicy {

    /** How quickly a report of this category must reach a human. */
    public fun triageTarget(category: ReportCategory): TriageTarget = when (category.severity) {
        ReportSeverity.CRITICAL -> TriageTarget.IMMEDIATE
        ReportSeverity.HIGH -> TriageTarget.WITHIN_24_HOURS
        ReportSeverity.MEDIUM -> TriageTarget.WITHIN_3_DAYS
        ReportSeverity.LOW -> TriageTarget.ROUTINE
    }

    /** Where a case starts. Child-safety matters do not begin in a general queue. */
    public fun initialEscalation(category: ReportCategory): EscalationLevel = when (category) {
        ReportCategory.CHILD_SAFETY,
        ReportCategory.GROOMING,
        -> EscalationLevel.SAFETY_ADMINISTRATOR

        ReportCategory.THREATS,
        ReportCategory.EXTREMISM_OR_VIOLENCE,
        ReportCategory.ILLEGAL_CONTENT,
        -> EscalationLevel.EXTERNAL_REFERRAL

        ReportCategory.SEXUAL_CONTENT,
        ReportCategory.COERCION,
        ReportCategory.FRAUD,
        ReportCategory.DONATION_MISUSE,
        -> EscalationLevel.SENIOR_MODERATOR

        else -> EscalationLevel.STANDARD
    }

    /**
     * Whether a moderator holding [roles] may take this action.
     *
     * A permanent ban and a revocation of verification are not routine moderation. They
     * require a safety administrator, which means at least two people are involved before
     * anyone loses their account for good.
     */
    public fun isAuthorized(type: ModerationActionType, roles: Set<AccountRole>): Boolean {
        val isSafetyAdmin = AccountRole.SAFETY_ADMINISTRATOR in roles ||
            AccountRole.PLATFORM_ADMINISTRATOR in roles
        val isModerator = AccountRole.MODERATOR in roles || isSafetyAdmin
        if (!isModerator) return false
        return if (type.requiresSeniorApproval) isSafetyAdmin else true
    }

    /**
     * Whether an appeal may be decided by this reviewer.
     *
     * The person who imposed a restriction cannot be the person who hears the appeal
     * against it. Without this, an appeals process is decoration.
     */
    public fun canReviewAppeal(
        appeal: Appeal,
        reviewerId: UserId,
        reviewerRoles: Set<AccountRole>,
        originalActions: List<ModerationAction>,
    ): AppealReviewDecision {
        if (reviewerId == appeal.appellantId) {
            return AppealReviewDecision.Refused("A member cannot review their own appeal.")
        }
        if (AccountRole.MODERATOR !in reviewerRoles &&
            AccountRole.SAFETY_ADMINISTRATOR !in reviewerRoles &&
            AccountRole.PLATFORM_ADMINISTRATOR !in reviewerRoles
        ) {
            return AppealReviewDecision.Refused("Only the safety team can review appeals.")
        }
        if (originalActions.any { it.moderatorId == reviewerId }) {
            return AppealReviewDecision.Refused(
                "This appeal must be reviewed by someone who was not involved in the " +
                    "original decision.",
            )
        }
        return AppealReviewDecision.Permitted
    }

    /**
     * Whether a sender may still unsend [message].
     *
     * The window is short on purpose. Unsend exists so someone can retract a message sent
     * in haste, not so that abuse can be deleted before it is reported — which is why the
     * original body is preserved for the safety team either way.
     */
    public fun canUnsend(message: Message, requesterId: UserId, now: Timestamp): UnsendDecision {
        if (message.senderId != requesterId) {
            return UnsendDecision.Refused("You can only unsend your own messages.")
        }
        if (message.isUnsent) {
            return UnsendDecision.Refused("This message has already been unsent.")
        }
        val deadline = message.createdAt + Message.UNSEND_WINDOW_MINUTES.minutes
        if (now > deadline) {
            return UnsendDecision.Refused(
                "Messages can only be unsent within ${Message.UNSEND_WINDOW_MINUTES} minutes " +
                    "of sending.",
            )
        }
        return UnsendDecision.Permitted(
            preserveOriginalForModeration = true,
            noticeToParticipants = "This message was unsent by the sender.",
        )
    }

    /**
     * Whether this pattern of reports looks like the reporting tool being used as a weapon.
     *
     * Getting this wrong in either direction hurts someone: too eager and a genuine victim
     * is disbelieved, too cautious and a harasser silences the person they are harassing.
     * The output is therefore a flag for review, never an automatic sanction.
     */
    public fun assessReportPattern(
        recentReportsByReporter: List<Report>,
        upheldCount: Int,
    ): ReportPatternAssessment {
        val total = recentReportsByReporter.size
        if (total < 3) return ReportPatternAssessment.Normal

        val dismissed = recentReportsByReporter.count {
            it.state == org.fisabilillah.core.model.ReportState.DISMISSED
        }
        val distinctTargets = recentReportsByReporter.mapNotNull { report ->
            (report.target as? org.fisabilillah.core.model.ReportTarget.User)?.id
        }.distinct().size

        val alreadyJudgedAbusive = recentReportsByReporter.count {
            it.abuseAssessment == ReportAbuseAssessment.RETALIATORY ||
                it.abuseAssessment == ReportAbuseAssessment.KNOWINGLY_FALSE
        }

        return when {
            alreadyJudgedAbusive >= 2 -> ReportPatternAssessment.LikelyAbusive(
                "This member has previously filed reports found to be retaliatory or false.",
            )

            total >= 5 && distinctTargets == 1 && upheldCount == 0 ->
                ReportPatternAssessment.NeedsReview(
                    "Repeated reports about a single member, none of which were upheld. " +
                        "Check whether this is persistent harassment or a genuine pattern " +
                        "that earlier reviews missed.",
                )

            total >= 6 && dismissed * 2 >= total -> ReportPatternAssessment.NeedsReview(
                "A high proportion of this member's reports were dismissed.",
            )

            else -> ReportPatternAssessment.Normal
        }
    }

    /**
     * Every moderation action produces exactly one audit entry, and this is where the
     * mapping lives so that no action type can be added without deciding how it is logged.
     */
    public fun auditActionFor(type: ModerationActionType): AuditAction = when (type) {
        ModerationActionType.ACCOUNT_SUSPENDED,
        ModerationActionType.ACCOUNT_BANNED,
        -> AuditAction.ACCOUNT_STATUS_CHANGED

        ModerationActionType.VERIFICATION_REVOKED -> AuditAction.VERIFICATION_CHANGED
        ModerationActionType.FEATURE_RESTRICTED -> AuditAction.RESTRICTION_IMPOSED
        ModerationActionType.RESTRICTION_LIFTED -> AuditAction.RESTRICTION_LIFTED
        ModerationActionType.APPEAL_UPHELD,
        ModerationActionType.APPEAL_REJECTED,
        -> AuditAction.APPEAL_DECIDED

        else -> AuditAction.MODERATION_ACTION
    }

    /** What a member is told when something is done to their account. */
    public fun enforcementNotice(action: ModerationAction): String = buildString {
        append("A moderator has taken the following action on your account: ")
        append(action.type.displayName.lowercase())
        append(". Reason: ")
        append(action.rationale)
        if (action.expiresAt != null) {
            append(" This is in force until ")
            append(action.expiresAt.toString())
            append('.')
        }
        if (action.type.isReversible) {
            append(" You can appeal this decision, and the appeal will be reviewed by " +
                "someone who was not involved in it.")
        }
    }
}

public enum class TriageTarget(public val displayName: String) {
    IMMEDIATE("Immediately"),
    WITHIN_24_HOURS("Within 24 hours"),
    WITHIN_3_DAYS("Within 3 days"),
    ROUTINE("Routine"),
}

public sealed interface AppealReviewDecision {
    public data object Permitted : AppealReviewDecision
    public data class Refused(val message: String) : AppealReviewDecision
}

public sealed interface UnsendDecision {
    public data class Permitted(
        val preserveOriginalForModeration: Boolean,
        val noticeToParticipants: String,
    ) : UnsendDecision

    public data class Refused(val message: String) : UnsendDecision
}

public sealed interface ReportPatternAssessment {
    public data object Normal : ReportPatternAssessment
    public data class NeedsReview(val note: String) : ReportPatternAssessment
    public data class LikelyAbusive(val note: String) : ReportPatternAssessment
}
