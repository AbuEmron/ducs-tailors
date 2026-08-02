package org.fisabilillah.core.policy

import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.IntroductionForm
import org.fisabilillah.core.model.IntroductionLimits
import org.fisabilillah.core.model.IntroductionStatus
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactRole
import org.fisabilillah.core.model.VerificationLevel
import kotlin.time.Duration.Companion.days

/** Everything needed to decide whether a formal introduction may be submitted. */
public data class IntroductionContext(
    val sender: Profile,
    val senderRestrictions: List<Restriction> = emptyList(),
    val recipient: Profile,
    val recipientSettings: FormalIntroductionSettings,
    val recipientGuardian: TrustedContact?,
    val senderBlockedByRecipient: Boolean = false,
    val recipientBlockedBySender: Boolean = false,
    val sharedOrganizationIds: Set<OrganizationId> = emptySet(),
    val senderOpenRequestCount: Int = 0,
    val senderRequestsInLast30Days: Int = 0,
    val senderPriorRequestsToThisRecipient: Int = 0,
    val recipientOpenRequestCount: Int = 0,
    val now: Timestamp,
    val currentYear: Int,
)

public sealed interface IntroductionDecision {
    public data object Allowed : IntroductionDecision
    public data class Denied(val reason: IntroductionDenialReason) : IntroductionDecision
}

public enum class IntroductionDenialReason(public val userFacingMessage: String) {
    /**
     * Deliberately identical to [FEATURE_DISABLED]. Whether a person has introductions
     * switched off, has blocked you, or simply does not meet your verification level is
     * none of the sender's business — and a sender who can tell the difference can work
     * out which door to try next.
     */
    NOT_AVAILABLE("This member is not receiving formal introductions."),
    FEATURE_DISABLED("This member is not receiving formal introductions."),
    NO_GUARDIAN_CONFIGURED("This member is not receiving formal introductions."),
    SENDER_RESTRICTED("Your account cannot submit formal introductions at the moment."),
    SENDER_NOT_VERIFIED(
        "Formal introductions require identity verification. You can complete this in " +
            "Settings before submitting.",
    ),
    SENDER_ACCOUNT_NOT_ACTIVE("Your account cannot submit formal introductions at the moment."),
    SAME_GENDER("Formal introductions are between a man and a woman."),
    SELF_REQUEST("You cannot submit a formal introduction to yourself."),
    ALREADY_SUBMITTED(
        "You have already submitted a formal introduction to this member. Please respect " +
            "their answer.",
    ),
    TOO_MANY_OPEN(
        "You already have the maximum number of open introductions. Please wait for those " +
            "to conclude before submitting another.",
    ),
    TOO_MANY_RECENT(
        "You have submitted several introductions recently. This process is meant to be " +
            "considered, not sent in volume.",
    ),
    RECIPIENT_AT_CAPACITY("This member is not receiving formal introductions at the moment."),
    NOT_AN_ADULT("Formal introductions are only available between adults."),
    FORM_INCOMPLETE("Please complete every part of the form before submitting."),
    CONDUCT_NOT_ACCEPTED("You must accept the conduct rules before submitting."),
}

/**
 * The Formal Family Introduction workflow.
 *
 * What makes this safe is not any single check but the shape of the whole thing: there is
 * no browsing, no ranking, no visible outcome, no way to know whether a request was even
 * read, and no path anywhere in this file from "interested" to "private conversation".
 * The only exit into a conversation runs through a guardian.
 *
 * The platform makes no religious ruling about anyone's circumstances. A member who has no
 * wali available may nominate an imam or another trusted intermediary; whether that is
 * correct for them is a question for a qualified scholar who knows their situation, and
 * the app says so rather than deciding.
 */
public object IntroductionPolicy {

    public fun canSubmit(context: IntroductionContext): IntroductionDecision {
        val settings = context.recipientSettings

        if (context.sender.id == context.recipient.id) {
            return deny(IntroductionDenialReason.SELF_REQUEST)
        }
        if (!context.sender.isActive) {
            return deny(IntroductionDenialReason.SENDER_ACCOUNT_NOT_ACTIVE)
        }
        if (isRestricted(context.senderRestrictions, context.now)) {
            return deny(IntroductionDenialReason.SENDER_RESTRICTED)
        }

        // Everything below this line resolves to the same message so that a sender learns
        // nothing about the recipient from a refusal.
        if (!context.recipient.isActive) return deny(IntroductionDenialReason.NOT_AVAILABLE)
        if (context.senderBlockedByRecipient) return deny(IntroductionDenialReason.NOT_AVAILABLE)
        if (context.recipientBlockedBySender) return deny(IntroductionDenialReason.NOT_AVAILABLE)
        if (!settings.enabled) return deny(IntroductionDenialReason.FEATURE_DISABLED)
        if (!settings.isUsable || context.recipientGuardian == null) {
            return deny(IntroductionDenialReason.NO_GUARDIAN_CONFIGURED)
        }
        if (!isEligibleGuardian(context.recipientGuardian, settings)) {
            return deny(IntroductionDenialReason.NO_GUARDIAN_CONFIGURED)
        }

        if (context.sender.gender == context.recipient.gender) {
            return deny(IntroductionDenialReason.SAME_GENDER)
        }
        if (!context.sender.isAdultIn(context.currentYear) ||
            !context.recipient.isAdultIn(context.currentYear)
        ) {
            return deny(IntroductionDenialReason.NOT_AN_ADULT)
        }

        // The sender's own verification is checked against their own message, because this
        // one is actionable: they can go and verify.
        if (!(context.sender.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED)) {
            return deny(IntroductionDenialReason.SENDER_NOT_VERIFIED)
        }
        if (!(context.sender.verificationLevel atLeast settings.minimumVerification)) {
            return deny(IntroductionDenialReason.SENDER_NOT_VERIFIED)
        }

        if (!audienceAdmits(settings.acceptRequestsFrom, context)) {
            return deny(IntroductionDenialReason.NOT_AVAILABLE)
        }
        if (settings.requireSharedOrganization && context.sharedOrganizationIds.isEmpty()) {
            return deny(IntroductionDenialReason.NOT_AVAILABLE)
        }

        if (context.senderPriorRequestsToThisRecipient >= IntroductionLimits.MAX_PER_RECIPIENT_EVER) {
            return deny(IntroductionDenialReason.ALREADY_SUBMITTED)
        }
        if (context.senderOpenRequestCount >= IntroductionLimits.MAX_CONCURRENT_OUTGOING) {
            return deny(IntroductionDenialReason.TOO_MANY_OPEN)
        }
        if (context.senderRequestsInLast30Days >= IntroductionLimits.MAX_PER_30_DAYS) {
            return deny(IntroductionDenialReason.TOO_MANY_RECENT)
        }
        if (context.recipientOpenRequestCount >= settings.maxOpenRequests) {
            return deny(IntroductionDenialReason.RECIPIENT_AT_CAPACITY)
        }

        return IntroductionDecision.Allowed
    }

    /** Checks the form itself. Separate from [canSubmit] so field errors can be shown inline. */
    public fun validateForm(form: IntroductionForm): ValidationResult<IntroductionForm> {
        val errors = mutableListOf<ValidationError>()

        fun requireField(name: String, value: String, label: String) {
            if (value.trim().length < IntroductionForm.MIN_FIELD) {
                errors += ValidationError(
                    name,
                    "$label needs at least ${IntroductionForm.MIN_FIELD} characters. This " +
                        "goes to a family, so please write it properly.",
                )
            }
            if (value.length > IntroductionForm.MAX_FIELD) {
                errors += ValidationError(
                    name,
                    "$label must be under ${IntroductionForm.MAX_FIELD} characters.",
                )
            }
        }

        requireField("statedIntention", form.statedIntention, "Your intention")
        requireField("aboutSelf", form.aboutSelf, "About yourself")
        requireField("familyContext", form.familyContext, "Your family context")
        requireField("practiceAndPriorities", form.practiceAndPriorities, "Practice and priorities")
        requireField("livingSituationAndPlans", form.livingSituationAndPlans, "Living situation and plans")

        if (form.guardianOrRepresentativeName.isBlank()) {
            errors += ValidationError(
                "guardianOrRepresentativeName",
                "Name the guardian or representative who will act for you.",
            )
        }
        if (!form.confirmsMarriageConsideration) {
            errors += ValidationError(
                "confirmsMarriageConsideration",
                "Confirm that this is a request for marriage consideration.",
            )
        }
        if (!form.confirmsConductRules) {
            errors += ValidationError(
                "confirmsConductRules",
                "You must accept the conduct rules before submitting.",
            )
        }
        if (form.questionsForTheFamily.size > IntroductionForm.MAX_QUESTIONS) {
            errors += ValidationError(
                "questionsForTheFamily",
                "Please keep this to ${IntroductionForm.MAX_QUESTIONS} questions or fewer.",
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid(form) else ValidationResult.Invalid(errors)
    }

    /**
     * The next state, given the recipient's configuration.
     *
     * A recipient who chose not to screen never sees the request at all — it goes straight
     * to their wali, which is precisely the arrangement some families want and which no
     * general-purpose messaging product can offer.
     */
    public fun statusOnSubmission(settings: FormalIntroductionSettings): IntroductionStatus =
        if (settings.recipientReviewsFirst) {
            IntroductionStatus.AWAITING_RECIPIENT
        } else {
            IntroductionStatus.FORWARDED_TO_GUARDIAN
        }

    /** Whether a transition is permitted. Anything not listed here simply cannot happen. */
    public fun canTransition(from: IntroductionStatus, to: IntroductionStatus): Boolean =
        to in allowedTransitions(from)

    public fun allowedTransitions(from: IntroductionStatus): Set<IntroductionStatus> = when (from) {
        IntroductionStatus.SUBMITTED -> setOf(
            IntroductionStatus.AWAITING_RECIPIENT,
            IntroductionStatus.FORWARDED_TO_GUARDIAN,
            IntroductionStatus.WITHDRAWN_BY_SENDER,
            IntroductionStatus.CLOSED_BY_MODERATION,
            IntroductionStatus.LAPSED,
        )

        IntroductionStatus.AWAITING_RECIPIENT -> setOf(
            IntroductionStatus.APPROVED_BY_RECIPIENT,
            IntroductionStatus.DECLINED,
            IntroductionStatus.BLOCKED_BY_RECIPIENT,
            IntroductionStatus.WITHDRAWN_BY_SENDER,
            IntroductionStatus.LAPSED,
            IntroductionStatus.CLOSED_BY_MODERATION,
        )

        IntroductionStatus.APPROVED_BY_RECIPIENT -> setOf(
            IntroductionStatus.FORWARDED_TO_GUARDIAN,
            IntroductionStatus.DECLINED,
            IntroductionStatus.WITHDRAWN_BY_SENDER,
            IntroductionStatus.CLOSED_BY_MODERATION,
            IntroductionStatus.LAPSED,
        )

        IntroductionStatus.FORWARDED_TO_GUARDIAN -> setOf(
            IntroductionStatus.GUARDIAN_ENGAGED,
            IntroductionStatus.DECLINED,
            IntroductionStatus.BLOCKED_BY_RECIPIENT,
            IntroductionStatus.WITHDRAWN_BY_SENDER,
            IntroductionStatus.LAPSED,
            IntroductionStatus.CLOSED_BY_MODERATION,
        )

        IntroductionStatus.GUARDIAN_ENGAGED -> setOf(
            IntroductionStatus.DECLINED,
            IntroductionStatus.BLOCKED_BY_RECIPIENT,
            IntroductionStatus.WITHDRAWN_BY_SENDER,
            IntroductionStatus.CLOSED_BY_MODERATION,
        )

        // Terminal.
        IntroductionStatus.DECLINED,
        IntroductionStatus.LAPSED,
        IntroductionStatus.BLOCKED_BY_RECIPIENT,
        IntroductionStatus.WITHDRAWN_BY_SENDER,
        IntroductionStatus.CLOSED_BY_MODERATION,
        -> emptySet()
    }

    /**
     * Whether a conversation may be opened for this introduction, and who must be in it.
     *
     * Note that there is no configuration under which the two people end up alone: the
     * guardian is always a member. A recipient who wants to speak without their wali
     * present is not using this feature — they are outside it, and the platform does not
     * provide a route there.
     */
    public fun conversationEligibility(
        request: FormalIntroductionRequest,
        settings: FormalIntroductionSettings,
    ): IntroductionConversationDecision {
        if (request.status != IntroductionStatus.FORWARDED_TO_GUARDIAN &&
            request.status != IntroductionStatus.GUARDIAN_ENGAGED
        ) {
            return IntroductionConversationDecision.NotYet(
                "A conversation can only be opened once the guardian has received the " +
                    "introduction.",
            )
        }
        if (request.guardianContactId == null) {
            return IntroductionConversationDecision.NotYet(
                "No guardian is attached to this introduction.",
            )
        }
        if (!settings.guardianMustBeIncludedThroughout) {
            // Even when the recipient has not asked for the guardian to be in every message,
            // the introduction thread itself is guardian-inclusive. This is the floor.
            return IntroductionConversationDecision.Allowed(guardianRequired = true)
        }
        return IntroductionConversationDecision.Allowed(guardianRequired = true)
    }

    /** Whether an unanswered request has now lapsed. Silence is a complete answer. */
    public fun hasLapsed(request: FormalIntroductionRequest, now: Timestamp): Boolean {
        if (!request.isOpen) return false
        val deadline = request.createdAt + IntroductionLimits.LAPSE_AFTER_DAYS.days
        return now >= deadline
    }

    /**
     * What the sender is told about an outcome.
     *
     * Never "she said no", never a reason, never a count of how many others are in the
     * queue. A rejection that carries detail invites argument, and a rejection that is
     * public invites humiliation. Neither belongs anywhere near this.
     */
    public fun senderFacingOutcome(status: IntroductionStatus): String = when (status) {
        IntroductionStatus.SUBMITTED,
        IntroductionStatus.AWAITING_RECIPIENT,
        IntroductionStatus.APPROVED_BY_RECIPIENT,
        -> "Your introduction has been submitted. You will be told if it moves forward."

        IntroductionStatus.FORWARDED_TO_GUARDIAN ->
            "Your introduction has been passed to the family's guardian."

        IntroductionStatus.GUARDIAN_ENGAGED ->
            "The guardian has opened a conversation with you."

        IntroductionStatus.DECLINED,
        IntroductionStatus.LAPSED,
        IntroductionStatus.BLOCKED_BY_RECIPIENT,
        -> "This introduction has concluded. Please do not submit another or make contact " +
            "another way."

        IntroductionStatus.WITHDRAWN_BY_SENDER -> "You withdrew this introduction."
        IntroductionStatus.CLOSED_BY_MODERATION ->
            "This introduction was closed by the safety team."
    }

    private fun isEligibleGuardian(
        contact: TrustedContact,
        settings: FormalIntroductionSettings,
    ): Boolean {
        if (!contact.active || contact.deletedAt != null) return false
        return when (contact.role) {
            TrustedContactRole.WALI -> true
            TrustedContactRole.INTERMEDIARY -> settings.allowIntermediaryInsteadOfWali
            else -> false
        }
    }

    private fun audienceAdmits(scope: AudienceScope, context: IntroductionContext): Boolean =
        when (scope) {
            AudienceScope.NOBODY -> false
            AudienceScope.EVERYONE -> true
            AudienceScope.VERIFIED_ONLY,
            AudienceScope.SAME_GENDER_VERIFIED_ONLY,
            -> context.sender.verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED

            AudienceScope.MY_ORGANIZATIONS_ONLY -> context.sharedOrganizationIds.isNotEmpty()

            // An introduction is by definition cross-gender, so a same-gender-only scope
            // means the feature is effectively closed.
            AudienceScope.SAME_GENDER_ONLY -> false
        }

    private fun isRestricted(restrictions: List<Restriction>, now: Timestamp): Boolean =
        restrictions.any {
            it.isActiveAt(now) &&
                it.capability.covers(RestrictedCapability.SUBMIT_INTRODUCTIONS)
        }

    private fun deny(reason: IntroductionDenialReason): IntroductionDecision =
        IntroductionDecision.Denied(reason)

    /** The note shown wherever this feature appears. */
    public const val RELIGIOUS_GUIDANCE_NOTICE: String =
        "This platform does not give religious rulings. Arrangements around a wali, an " +
            "intermediary, and marriage differ by school and by circumstance — please ask a " +
            "qualified scholar who knows your situation."
}

public sealed interface IntroductionConversationDecision {
    public data class Allowed(val guardianRequired: Boolean) : IntroductionConversationDecision
    public data class NotYet(val message: String) : IntroductionConversationDecision
}
