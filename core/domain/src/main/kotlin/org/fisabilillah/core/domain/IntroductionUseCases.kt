package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.ContactRequirement
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.ConversationMember
import org.fisabilillah.core.model.ConversationRole
import org.fisabilillah.core.model.ConversationState
import org.fisabilillah.core.model.EngagementDuration
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.IntroductionClosureReason
import org.fisabilillah.core.model.IntroductionForm
import org.fisabilillah.core.model.IntroductionId
import org.fisabilillah.core.model.IntroductionParticipant
import org.fisabilillah.core.model.IntroductionParticipantRole
import org.fisabilillah.core.model.IntroductionStatus
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.MessageKind
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.RedactedTrustedContact
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.policy.IntroductionContext
import org.fisabilillah.core.policy.IntroductionDecision
import org.fisabilillah.core.policy.IntroductionPolicy
import org.fisabilillah.core.policy.ValidationResult
import kotlin.time.Duration.Companion.days

/**
 * Submitting a formal expression of interest.
 *
 * Nothing about this resembles a message. A sender fills in a long structured form, agrees
 * to conduct rules that are recorded against their account, and then hears nothing unless
 * the other family chooses to take it further. There is no read receipt, no delivery
 * confirmation, no way to tell whether the recipient even has the feature switched on, and
 * no second attempt.
 */
public class SubmitIntroductionUseCase(
    private val profiles: ProfileRepository,
    private val introductions: IntroductionRepository,
    private val trustedContacts: TrustedContactRepository,
    private val blocks: BlockRepository,
    private val restrictions: RestrictionRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val assembler: ContactContextAssembler,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        recipientId: UserId,
        form: IntroductionForm,
    ): Outcome<FormalIntroductionRequest> {
        when (val validation = IntroductionPolicy.validateForm(form)) {
            is ValidationResult.Invalid -> return Outcome.Invalid(validation.errors)
            is ValidationResult.Valid -> Unit
        }

        val sender = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val recipient = profiles.find(recipientId) ?: return Outcome.NotFound("that member")
        val settings = introductions.settingsFor(recipientId)
            ?: FormalIntroductionSettings(userId = recipientId)

        val guardian: TrustedContact? = settings.guardianContactId
            ?.let { trustedContacts.find(it) }

        val now = clock.now()
        val context = IntroductionContext(
            sender = sender,
            senderRestrictions = restrictions.activeFor(sender.id, now),
            recipient = recipient,
            recipientSettings = settings,
            recipientGuardian = guardian,
            senderBlockedByRecipient = blocks.isBlocked(recipientId, sender.id),
            recipientBlockedBySender = blocks.isBlocked(sender.id, recipientId),
            sharedOrganizationIds = assembler.sharedOrganizations(sender.id, recipientId),
            senderOpenRequestCount = introductions.outgoingFrom(sender.id).count { it.isOpen },
            senderRequestsInLast30Days = introductions.countFrom(sender.id, now - 30.days),
            senderPriorRequestsToThisRecipient = introductions.countBetween(sender.id, recipientId),
            recipientOpenRequestCount = introductions.incomingFor(recipientId).count { it.isOpen },
            now = now,
            currentYear = clock.currentYear(),
        )

        when (val decision = IntroductionPolicy.canSubmit(context)) {
            is IntroductionDecision.Denied ->
                return Outcome.Refused(decision.reason.userFacingMessage)
            IntroductionDecision.Allowed -> Unit
        }

        val status = IntroductionPolicy.statusOnSubmission(settings)
        val request = introductions.save(
            FormalIntroductionRequest(
                id = IntroductionId(ids.newId()),
                senderId = sender.id,
                recipientId = recipientId,
                status = status,
                form = form,
                conductAgreementAcceptedAt = now,
                senderVerificationAtSubmission = sender.verificationLevel,
                guardianContactId = settings.guardianContactId,
                forwardedAt = if (status == IntroductionStatus.FORWARDED_TO_GUARDIAN) now else null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        introductions.addParticipant(
            IntroductionParticipant(
                introductionId = request.id,
                userId = sender.id,
                role = IntroductionParticipantRole.SENDER,
                addedAt = now,
                mayAccessGuardianContact = false,
            ),
        )
        introductions.addParticipant(
            IntroductionParticipant(
                introductionId = request.id,
                userId = recipientId,
                role = IntroductionParticipantRole.RECIPIENT,
                addedAt = now,
                // The recipient owns the guardian record, so this is their own information.
                mayAccessGuardianContact = true,
            ),
        )

        // Who is told depends entirely on the recipient's configuration. A member whose
        // family arrangement is that everything goes to their wali first is never shown
        // the request at all.
        val notifyUserId = when (status) {
            IntroductionStatus.AWAITING_RECIPIENT -> recipientId
            IntroductionStatus.FORWARDED_TO_GUARDIAN -> guardian?.linkedUserId
            else -> null
        }
        if (notifyUserId != null) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = notifyUserId,
                    kind = NotificationKind.INTRODUCTION_RECEIVED,
                    title = "A formal introduction has been received",
                    body = "Someone has submitted a formal introduction through the " +
                        "guardian-led process.",
                    deepLink = "fisabilillah://introduction/${request.id.value}",
                    createdAt = now,
                ),
            )
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = sender.id,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.INTRODUCTION_SUBMITTED,
                subjectType = "introduction",
                subjectId = request.id.value,
                summary = "Submitted; initial status ${status.name}; conduct rules accepted",
                occurredAt = now,
            ),
        )

        return Outcome.Success(request)
    }
}

/** What the recipient (or their guardian) can do with an incoming introduction. */
public enum class IntroductionDecisionAction {
    APPROVE_AND_FORWARD,
    DECLINE,
    BLOCK_PERMANENTLY,
}

/**
 * The recipient's decision.
 *
 * Approving does not open a conversation between the two people. It passes the
 * introduction to the guardian, and only the guardian can take it further — which is the
 * whole point of the feature and the reason it is not simply a filtered inbox.
 */
public class DecideIntroductionUseCase(
    private val introductions: IntroductionRepository,
    private val trustedContacts: TrustedContactRepository,
    private val blocks: BlockRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        introductionId: IntroductionId,
        action: IntroductionDecisionAction,
        note: String? = null,
    ): Outcome<FormalIntroductionRequest> {
        val request = introductions.find(introductionId)
            ?: return Outcome.NotFound("that introduction")

        if (request.recipientId != principal.userId) {
            return Outcome.refused("Only the recipient can decide on this introduction.")
        }
        if (!request.isOpen) {
            return Outcome.refused("This introduction has already concluded.", RefusalCode.CONFLICT)
        }

        val now = clock.now()
        val settings = introductions.settingsFor(principal.userId)
            ?: FormalIntroductionSettings(userId = principal.userId)

        val nextStatus = when (action) {
            IntroductionDecisionAction.APPROVE_AND_FORWARD -> IntroductionStatus.APPROVED_BY_RECIPIENT
            IntroductionDecisionAction.DECLINE -> IntroductionStatus.DECLINED
            IntroductionDecisionAction.BLOCK_PERMANENTLY -> IntroductionStatus.BLOCKED_BY_RECIPIENT
        }
        if (!IntroductionPolicy.canTransition(request.status, nextStatus)) {
            return Outcome.refused(
                "That is not a valid step for this introduction.",
                RefusalCode.CONFLICT,
            )
        }

        var updated = request.copy(
            status = nextStatus,
            recipientDecidedAt = now,
            recipientDecisionNote = note,
            updatedAt = now,
        )

        when (action) {
            IntroductionDecisionAction.APPROVE_AND_FORWARD -> {
                val guardian = settings.guardianContactId?.let { trustedContacts.find(it) }
                    ?: return Outcome.refused(
                        "You need a wali or trusted intermediary set up before an " +
                            "introduction can be forwarded.",
                        RefusalCode.NEEDS_GUARDIAN,
                    )
                updated = updated.copy(
                    status = IntroductionStatus.FORWARDED_TO_GUARDIAN,
                    forwardedAt = now,
                    guardianContactId = guardian.id,
                )
                val guardianUserId = guardian.linkedUserId
                if (guardianUserId != null) {
                    introductions.addParticipant(
                        IntroductionParticipant(
                            introductionId = introductionId,
                            userId = guardianUserId,
                            role = IntroductionParticipantRole.RECIPIENT_GUARDIAN,
                            addedAt = now,
                            mayAccessGuardianContact = true,
                        ),
                    )
                    notifications.add(
                        Notification(
                            id = NotificationId(ids.newId()),
                            userId = guardianUserId,
                            kind = NotificationKind.INTRODUCTION_RECEIVED,
                            title = "A formal introduction has been passed to you",
                            body = "You have been asked to consider a formal introduction " +
                                "on behalf of someone in your care.",
                            deepLink = "fisabilillah://introduction/${introductionId.value}",
                            createdAt = now,
                        ),
                    )
                }
            }

            IntroductionDecisionAction.DECLINE ->
                updated = updated.copy(
                    closedAt = now,
                    closureReason = IntroductionClosureReason.RECIPIENT_DECLINED,
                )

            IntroductionDecisionAction.BLOCK_PERMANENTLY -> {
                updated = updated.copy(
                    closedAt = now,
                    closureReason = IntroductionClosureReason.RECIPIENT_BLOCKED_SENDER,
                )
                blocks.add(
                    org.fisabilillah.core.model.Block(
                        blockerId = principal.userId,
                        blockedId = request.senderId,
                        reason = "Closed a formal introduction permanently",
                        createdAt = now,
                    ),
                )
            }
        }

        val saved = introductions.save(updated)

        // The sender is told only that the process has concluded. No reason, no name of
        // whoever decided, and nothing that would invite an argument or another attempt.
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = request.senderId,
                kind = NotificationKind.INTRODUCTION_STATUS,
                title = "Formal introduction update",
                body = IntroductionPolicy.senderFacingOutcome(saved.status),
                createdAt = now,
            ),
        )

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.INTRODUCTION_STATUS_CHANGED,
                subjectType = "introduction",
                subjectId = introductionId.value,
                summary = "${request.status.name} -> ${saved.status.name} by recipient",
                occurredAt = now,
            ),
        )

        return Outcome.Success(saved)
    }
}

/**
 * The guardian opening a conversation.
 *
 * The resulting thread has three people in it. There is no configuration, anywhere, that
 * produces a two-person version — [IntroductionPolicy.conversationEligibility] returns
 * `guardianRequired = true` unconditionally, and this use case adds them as a member
 * before the thread is saved.
 */
public class OpenIntroductionConversationUseCase(
    private val introductions: IntroductionRepository,
    private val trustedContacts: TrustedContactRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        introductionId: IntroductionId,
        openingMessage: String,
    ): Outcome<Conversation> {
        val request = introductions.find(introductionId)
            ?: return Outcome.NotFound("that introduction")
        val settings = introductions.settingsFor(request.recipientId)
            ?: FormalIntroductionSettings(userId = request.recipientId)

        val guardian = request.guardianContactId?.let { trustedContacts.find(it) }
            ?: return Outcome.refused("No guardian is attached to this introduction.")
        val guardianUserId = guardian.linkedUserId
            ?: return Outcome.refused(
                "The guardian does not have an account, so a conversation cannot be opened " +
                    "here. Contact them through the method they chose.",
            )

        if (principal.userId != guardianUserId) {
            return Outcome.refused("Only the guardian can open this conversation.")
        }

        when (IntroductionPolicy.conversationEligibility(request, settings)) {
            is org.fisabilillah.core.policy.IntroductionConversationDecision.NotYet ->
                return Outcome.refused(
                    "A conversation can only be opened once the guardian has received the " +
                        "introduction.",
                )
            is org.fisabilillah.core.policy.IntroductionConversationDecision.Allowed -> Unit
        }
        val existingConversationId = request.conversationId
        if (existingConversationId != null) {
            return conversations.find(existingConversationId)
                ?.let { Outcome.Success(it) }
                ?: Outcome.NotFound("that conversation")
        }

        val now = clock.now()
        val conversationId = ConversationId(ids.newId())
        val conversation = conversations.save(
            Conversation(
                id = conversationId,
                purpose = ContactPurpose(
                    kind = ContactPurposeKind.FORMAL_INTRODUCTION,
                    reasonForContact = "A guardian-led conversation following a formal " +
                        "family introduction.",
                    requestedAction = "Discuss the introduction with the guardian present.",
                    expectedDuration = EngagementDuration.SHORT_TERM,
                    requestGuardianPresent = true,
                ),
                members = listOf(
                    ConversationMember(guardianUserId, ConversationRole.GUARDIAN, now),
                    ConversationMember(request.senderId, ConversationRole.PARTICIPANT, now),
                    ConversationMember(request.recipientId, ConversationRole.RECIPIENT, now),
                ),
                state = ConversationState.ACTIVE,
                subjectTitle = "Formal family introduction",
                appliedRequirements = setOf(
                    ContactRequirement.GUARDIAN_PRESENT,
                    ContactRequirement.WRITTEN_PURPOSE_REQUIRED,
                    ContactRequirement.NO_VOICE_CALLS,
                    ContactRequirement.NO_VIDEO_CALLS,
                    ContactRequirement.PUBLIC_MEETINGS_ONLY,
                ),
                lastMessageAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )

        messages.save(
            Message(
                id = MessageId(ids.newId()),
                conversationId = conversationId,
                senderId = null,
                kind = MessageKind.SYSTEM,
                body = "This conversation follows a formal family introduction. The " +
                    "guardian is a member of it and will remain so. " +
                    IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        messages.save(
            Message(
                id = MessageId(ids.newId()),
                conversationId = conversationId,
                senderId = guardianUserId,
                kind = MessageKind.TEXT,
                body = openingMessage,
                createdAt = now,
                updatedAt = now,
            ),
        )

        introductions.save(
            request.copy(
                status = IntroductionStatus.GUARDIAN_ENGAGED,
                guardianRespondedAt = now,
                conversationId = conversationId,
                updatedAt = now,
            ),
        )

        for (userId in listOf(request.senderId, request.recipientId)) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = userId,
                    kind = NotificationKind.INTRODUCTION_STATUS,
                    title = "The guardian has opened a conversation",
                    body = "A guardian-led conversation about the formal introduction has " +
                        "been opened.",
                    deepLink = "fisabilillah://conversation/${conversationId.value}",
                    createdAt = now,
                ),
            )
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.INTRODUCTION_STATUS_CHANGED,
                subjectType = "introduction",
                subjectId = introductionId.value,
                summary = "Guardian opened a guardian-inclusive conversation",
                occurredAt = now,
            ),
        )

        return Outcome.Success(conversation)
    }
}

/**
 * What a counterparty is allowed to learn about the other side's guardian.
 *
 * A name, a stated relationship, and how they prefer to be approached. Never an email
 * address, never a phone number, never a note the member wrote for themselves. If this
 * function is the only way guardian information leaves the domain layer — and it is —
 * then a leak requires someone to write a new one, which is a reviewable act.
 */
public class ViewIntroductionGuardianUseCase(
    private val introductions: IntroductionRepository,
    private val trustedContacts: TrustedContactRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        introductionId: IntroductionId,
    ): Outcome<RedactedTrustedContact> {
        val request = introductions.find(introductionId)
            ?: return Outcome.NotFound("that introduction")

        val participants = introductions.participants(introductionId)
        val me = participants.firstOrNull { it.userId == principal.userId }
            ?: return Outcome.NotFound("that introduction")

        val guardian = request.guardianContactId?.let { trustedContacts.find(it) }
            ?: return Outcome.NotFound("a guardian for this introduction")

        // Before the introduction has been forwarded there is nothing to disclose, not even
        // that a guardian exists.
        if (request.status != IntroductionStatus.FORWARDED_TO_GUARDIAN &&
            request.status != IntroductionStatus.GUARDIAN_ENGAGED
        ) {
            return Outcome.NotFound("a guardian for this introduction")
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.GUARDIAN_CONTACT_ACCESSED,
                subjectType = "trusted_contact",
                subjectId = guardian.id.value,
                summary = "Redacted guardian details viewed by ${me.role.name}",
                occurredAt = clock.now(),
            ),
        )

        return Outcome.Success(guardian.redacted())
    }
}

/** Closes introductions nobody answered. Silence is an answer, and it expires on its own. */
public class LapseIntroductionsUseCase(
    private val introductions: IntroductionRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {
    public suspend operator fun invoke(userId: UserId): Int {
        val now = clock.now()
        var lapsed = 0
        for (request in introductions.outgoingFrom(userId)) {
            if (IntroductionPolicy.hasLapsed(request, now)) {
                introductions.save(
                    request.copy(
                        status = IntroductionStatus.LAPSED,
                        closedAt = now,
                        closureReason = IntroductionClosureReason.LAPSED_WITHOUT_RESPONSE,
                        updatedAt = now,
                    ),
                )
                notifications.add(
                    Notification(
                        id = NotificationId(ids.newId()),
                        userId = request.senderId,
                        kind = NotificationKind.INTRODUCTION_STATUS,
                        title = "Formal introduction update",
                        body = IntroductionPolicy.senderFacingOutcome(IntroductionStatus.LAPSED),
                        createdAt = now,
                    ),
                )
                lapsed++
            }
        }
        return lapsed
    }
}

/** Saving a member's own introduction configuration. */
public class UpdateIntroductionSettingsUseCase(
    private val introductions: IntroductionRepository,
    private val trustedContacts: TrustedContactRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {
    public suspend operator fun invoke(
        principal: Principal,
        settings: FormalIntroductionSettings,
    ): Outcome<FormalIntroductionSettings> {
        if (settings.userId != principal.userId) {
            return Outcome.refused("You can only change your own settings.")
        }
        if (settings.enabled) {
            val guardianId = settings.guardianContactId
                ?: return Outcome.refused(
                    "Add a wali or trusted intermediary before switching formal " +
                        "introductions on. Introductions are always received by them.",
                    RefusalCode.NEEDS_GUARDIAN,
                )
            val guardian = trustedContacts.find(guardianId)
                ?: return Outcome.NotFound("that trusted contact")
            if (guardian.ownerId != principal.userId) {
                return Outcome.refused("That trusted contact does not belong to you.")
            }
        }
        val now = clock.now()
        val saved = introductions.saveSettings(settings.copy(updatedAt = now))
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.SAFEGUARDS_CHANGED,
                subjectType = "formal_introduction_settings",
                subjectId = principal.userId.value,
                summary = "Introductions ${if (saved.enabled) "enabled" else "disabled"}",
                occurredAt = now,
            ),
        )
        return Outcome.Success(saved)
    }
}
