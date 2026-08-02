package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.Block
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.ContactRequirement
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.ConversationMember
import org.fisabilillah.core.model.ConversationRole
import org.fisabilillah.core.model.ConversationState
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.MessageKind
import org.fisabilillah.core.model.MessageRedaction
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.SafetySignal
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.policy.ContactDecision
import org.fisabilillah.core.policy.ContactPolicy
import org.fisabilillah.core.policy.ContentSignals
import org.fisabilillah.core.policy.MessageDecision
import org.fisabilillah.core.policy.ModerationPolicy
import org.fisabilillah.core.policy.OversightRequirement
import org.fisabilillah.core.policy.PurposeValidator
import org.fisabilillah.core.policy.UnsendDecision
import org.fisabilillah.core.policy.ValidationResult

/** What the caller supplies to open a conversation. */
public data class StartConversationCommand(
    val recipientId: UserId,
    val purpose: ContactPurpose,
    val openingMessage: String,
)

public data class StartedConversation(
    val conversation: Conversation,
    val firstMessage: Message,
    val requirementsApplied: Set<ContactRequirement>,
)

/**
 * The one and only way a conversation comes into existence.
 *
 * Every refusal path returns before anything is written, and the oversight participants
 * the recipient's safeguards call for are added as members of the thread at creation —
 * not invited afterwards, not optional, and not removable by the person who opened it.
 */
public class StartConversationUseCase(
    private val profiles: ProfileRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val trustedContacts: TrustedContactRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val assembler: ContactContextAssembler,
    private val oversight: OversightDirectory,
    private val subjects: SubjectTitleResolver,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        command: StartConversationCommand,
    ): Outcome<StartedConversation> {
        val initiator = profiles.find(principal.userId)
            ?: return Outcome.NotFound("your profile")
        val recipient = profiles.find(command.recipientId)
            ?: return Outcome.NotFound("that member")

        when (val validated = PurposeValidator.validate(command.purpose)) {
            is ValidationResult.Invalid -> return Outcome.Invalid(validated.errors)
            is ValidationResult.Valid -> Unit
        }
        if (command.openingMessage.isBlank()) {
            return Outcome.invalid("openingMessage", "Write your opening message.")
        }
        if (command.openingMessage.length > Message.MAX_LENGTH) {
            return Outcome.invalid(
                "openingMessage",
                "Messages must be under ${Message.MAX_LENGTH} characters.",
            )
        }

        val context = assembler.assemble(principal, initiator, recipient, command.purpose)
        val decision = ContactPolicy.evaluate(context)
        if (decision is ContactDecision.Denied) {
            // Recorded so that a pattern of refused approaches to the same person is
            // visible to the safety team even though the recipient never saw any of them.
            auditLog.append(
                entry(
                    actor = principal,
                    action = AuditAction.CONVERSATION_OPENED,
                    subjectType = "conversation_attempt",
                    subjectId = recipient.id.value,
                    summary = "Refused: " + decision.reasons.joinToString { it.auditReason },
                ),
            )
            return Outcome.Refused(decision.userFacingMessage, refusalCode(decision))
        }

        val allowed = decision as ContactDecision.Allowed
        val now = clock.now()

        val members = mutableListOf(
            ConversationMember(initiator.id, ConversationRole.INITIATOR, now),
            ConversationMember(recipient.id, ConversationRole.RECIPIENT, now),
        )

        for (requirement in allowed.requiredOversight) {
            val member = resolveOversight(requirement, recipient.id, command.purpose, now)
                ?: return Outcome.Refused(
                    message = missingOversightMessage(requirement),
                    code = RefusalCode.NEEDS_GUARDIAN,
                )
            if (members.none { it.userId == member.userId }) members += member
        }

        val conversationId = ConversationId(ids.newId())
        val conversation = Conversation(
            id = conversationId,
            purpose = command.purpose,
            members = members,
            state = ConversationState.AWAITING_RESPONSE,
            subjectTitle = subjects.titleFor(command.purpose),
            appliedRequirements = allowed.requirements,
            lastMessageAt = now,
            createdAt = now,
            updatedAt = now,
        )
        conversations.save(conversation)

        val opening = Message(
            id = MessageId(ids.newId()),
            conversationId = conversationId,
            senderId = initiator.id,
            kind = MessageKind.TEXT,
            body = command.openingMessage,
            createdAt = now,
            updatedAt = now,
        )
        messages.save(opening)

        // A system message states the terms in the thread itself, so that everyone present
        // — including a guardian who was added without being asked — can see why they are
        // there and what was agreed.
        if (allowed.requirements.isNotEmpty()) {
            messages.save(
                Message(
                    id = MessageId(ids.newId()),
                    conversationId = conversationId,
                    senderId = null,
                    kind = MessageKind.SYSTEM,
                    body = requirementsNotice(allowed.requirements),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = recipient.id,
                kind = NotificationKind.CONVERSATION_REQUEST,
                title = "New ${command.purpose.kind.displayName.lowercase()} request",
                body = command.purpose.requestedAction,
                deepLink = "fisabilillah://conversation/${conversationId.value}",
                createdAt = now,
            ),
        )
        for (overseer in conversation.oversightIds) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = overseer,
                    kind = NotificationKind.CONVERSATION_REQUEST,
                    title = "You have been added to a conversation",
                    body = "You are present in this conversation because of the safeguards " +
                        "in force.",
                    deepLink = "fisabilillah://conversation/${conversationId.value}",
                    createdAt = now,
                ),
            )
        }

        auditLog.append(
            entry(
                actor = principal,
                action = AuditAction.CONVERSATION_OPENED,
                subjectType = "conversation",
                subjectId = conversationId.value,
                summary = "Opened for ${command.purpose.kind.name} with " +
                    "${conversation.oversightIds.size} oversight participants",
            ),
        )

        return Outcome.Success(
            StartedConversation(conversation, opening, allowed.requirements),
        )
    }

    private suspend fun resolveOversight(
        requirement: OversightRequirement,
        recipientId: UserId,
        purpose: ContactPurpose,
        now: org.fisabilillah.core.model.Timestamp,
    ): ConversationMember? = when (requirement) {
        OversightRequirement.RECIPIENT_GUARDIAN ->
            assembler.availableGuardian(recipientId)?.linkedUserId
                ?.let { ConversationMember(it, ConversationRole.GUARDIAN, now) }

        OversightRequirement.MODERATOR -> {
            val communityId = (purpose.subject as? PurposeSubject.Community)?.id
            val moderatorId = communityId?.let { oversight.communityModerator(it) }
                ?: oversight.availableModerator()
            moderatorId?.let { ConversationMember(it, ConversationRole.MODERATOR, now) }
        }

        OversightRequirement.THIRD_PARTY ->
            assembler.availableThirdParty(recipientId)?.linkedUserId
                ?.let { ConversationMember(it, ConversationRole.THIRD_PARTY, now) }

        OversightRequirement.ORGANIZATION_REPRESENTATIVE -> {
            val orgId: OrganizationId? = purpose.requestOrganizationRepresentative
                ?: (purpose.subject as? PurposeSubject.Organization)?.id
            orgId?.let { oversight.organizationRepresentative(it) }
                ?.let { ConversationMember(it, ConversationRole.ORGANIZATION_REPRESENTATIVE, now) }
        }
    }

    private fun missingOversightMessage(requirement: OversightRequirement): String =
        when (requirement) {
            OversightRequirement.RECIPIENT_GUARDIAN ->
                "This member's settings require their guardian to be present, and no " +
                    "guardian is available right now."
            OversightRequirement.MODERATOR ->
                "This conversation needs a moderator present and none is available right " +
                    "now. Please try again shortly."
            OversightRequirement.THIRD_PARTY ->
                "This member's settings require a third party to be present, and none is " +
                    "available right now."
            OversightRequirement.ORGANIZATION_REPRESENTATIVE ->
                "No representative of that organisation is available right now."
        }

    private fun requirementsNotice(requirements: Set<ContactRequirement>): String =
        "This conversation has terms attached to it:\n" +
            requirements.joinToString("\n") { "• ${it.displayName} — ${it.explanation}" }

    private fun refusalCode(decision: ContactDecision.Denied): RefusalCode =
        when (decision.reasons.first()) {
            org.fisabilillah.core.policy.DenialReason.BLOCKED_BY_RECIPIENT,
            org.fisabilillah.core.policy.DenialReason.INITIATOR_BLOCKED_RECIPIENT,
            -> RefusalCode.BLOCKED

            org.fisabilillah.core.policy.DenialReason.INITIATOR_RESTRICTED,
            org.fisabilillah.core.policy.DenialReason.RECIPIENT_RESTRICTED,
            -> RefusalCode.RESTRICTED

            org.fisabilillah.core.policy.DenialReason.RATE_LIMIT_EXCEEDED,
            org.fisabilillah.core.policy.DenialReason.QUIET_HOURS,
            -> RefusalCode.RATE_LIMITED

            org.fisabilillah.core.policy.DenialReason.VERIFICATION_TOO_LOW ->
                RefusalCode.NEEDS_VERIFICATION

            org.fisabilillah.core.policy.DenialReason.GUARDIAN_REQUIRED_BUT_UNAVAILABLE ->
                RefusalCode.NEEDS_GUARDIAN

            org.fisabilillah.core.policy.DenialReason.INITIATOR_NOT_ACTIVE,
            org.fisabilillah.core.policy.DenialReason.RECIPIENT_NOT_AVAILABLE,
            -> RefusalCode.ACCOUNT_INACTIVE

            else -> RefusalCode.NOT_PERMITTED
        }

    private fun entry(
        actor: Principal,
        action: AuditAction,
        subjectType: String,
        subjectId: String,
        summary: String,
    ) = AuditLogEntry(
        id = AuditLogId(ids.newId()),
        actorId = actor.userId,
        actorRoleAtTime = actor.roles.firstOrNull(),
        action = action,
        subjectType = subjectType,
        subjectId = subjectId,
        summary = summary,
        occurredAt = clock.now(),
    )
}

public data class SentMessage(
    val message: Message,
    val signals: List<SafetySignal>,
    val driftReminderPosted: Boolean,
)

/** Adds a message to an existing thread. */
public class SendMessageUseCase(
    private val profiles: ProfileRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val blocks: BlockRepository,
    private val restrictions: RestrictionRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        conversationId: ConversationId,
        body: String,
    ): Outcome<SentMessage> {
        if (body.isBlank()) return Outcome.invalid("body", "Write a message first.")
        if (body.length > Message.MAX_LENGTH) {
            return Outcome.invalid("body", "Messages must be under ${Message.MAX_LENGTH} characters.")
        }

        val conversation = conversations.find(conversationId)
            ?: return Outcome.NotFound("that conversation")
        val sender = profiles.find(principal.userId)
            ?: return Outcome.NotFound("your profile")

        val now = clock.now()
        val blockedEitherWay = blocks.blocking(sender.id) + blocks.blockedBy(sender.id)

        val decision = ContactPolicy.canSendMessage(
            conversation = conversation,
            sender = sender,
            senderRestrictions = restrictions.activeFor(sender.id, now),
            blockedCounterpartIds = blockedEitherWay,
            now = now,
        )
        if (decision is MessageDecision.Denied) {
            return Outcome.Refused(decision.message)
        }

        val message = messages.save(
            Message(
                id = MessageId(ids.newId()),
                conversationId = conversationId,
                senderId = sender.id,
                kind = MessageKind.TEXT,
                body = body,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val counterpartGenders = profiles
            .findAll(conversation.participantIds - sender.id)
            .map { it.gender }
        val crossGender = counterpartGenders.any { it != sender.gender }
        val signals = ContentSignals.forMessage(body, crossGender)

        // Purpose drift produces a note in the thread, visible to everyone in it. It is a
        // reminder between two adults, not a report and not a punishment.
        val recent = messages.forConversation(conversationId).items.map { it.body }
        val drift = ContentSignals.purposeDrift(conversation.purpose, recent)
        var driftPosted = false
        if (drift != null && conversation.lastDriftReminderIsStale(now)) {
            messages.save(
                Message(
                    id = MessageId(ids.newId()),
                    conversationId = conversationId,
                    senderId = null,
                    kind = MessageKind.PURPOSE_REMINDER,
                    body = ContentSignals.driftReminder(conversation.purpose),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            driftPosted = true
        }

        // The thread becomes active the moment someone other than the person who opened it
        // replies. Until then it is a request, and the recipient owes nothing.
        val initiatorId = conversation.members
            .firstOrNull { it.role == ConversationRole.INITIATOR }?.userId
        val nextState =
            if (conversation.state == ConversationState.AWAITING_RESPONSE && sender.id != initiatorId) {
                ConversationState.ACTIVE
            } else {
                conversation.state
            }
        conversations.save(
            conversation.copy(state = nextState, lastMessageAt = now, updatedAt = now),
        )

        for (recipient in conversation.members.filter { it.isActive && it.userId != sender.id }) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = recipient.userId,
                    kind = NotificationKind.NEW_MESSAGE,
                    title = conversation.subjectTitle,
                    body = body.take(120),
                    deepLink = "fisabilillah://conversation/${conversationId.value}",
                    createdAt = now,
                ),
            )
        }

        return Outcome.Success(SentMessage(message, signals, driftPosted))
    }
}

/**
 * Unsends a message.
 *
 * The body stops being visible to participants and the original is written to a
 * moderator-only record in the same operation. Neither half is optional: an unsend that
 * destroyed the evidence would hand every harasser a delete button for their own abuse.
 */
public class UnsendMessageUseCase(
    private val messages: MessageRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        messageId: MessageId,
    ): Outcome<Message> {
        val message = messages.find(messageId) ?: return Outcome.NotFound("that message")
        val now = clock.now()

        return when (val decision = ModerationPolicy.canUnsend(message, principal.userId, now)) {
            is UnsendDecision.Refused -> Outcome.Refused(decision.message)
            is UnsendDecision.Permitted -> {
                messages.preserveRedaction(
                    MessageRedaction(
                        messageId = message.id,
                        conversationId = message.conversationId,
                        senderId = message.senderId,
                        originalBody = message.body,
                        redactedAt = now,
                    ),
                )
                val updated = messages.save(message.copy(unsentAt = now, updatedAt = now))
                auditLog.append(
                    AuditLogEntry(
                        id = AuditLogId(ids.newId()),
                        actorId = principal.userId,
                        actorRoleAtTime = principal.roles.firstOrNull(),
                        action = AuditAction.MESSAGE_UNSENT,
                        subjectType = "message",
                        subjectId = messageId.value,
                        summary = "Message unsent; original preserved for moderation review",
                        occurredAt = now,
                    ),
                )
                Outcome.Success(updated)
            }
        }
    }
}

/**
 * Blocking someone.
 *
 * Takes effect on existing conversations as well as new ones — a block that only stopped
 * future contact would leave the person who needed it still reachable in every thread they
 * already shared.
 */
public class BlockUserUseCase(
    private val blocks: BlockRepository,
    private val conversations: ConversationRepository,
    private val introductions: IntroductionRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        target: UserId,
        reason: String? = null,
    ): Outcome<Unit> {
        if (principal.userId == target) {
            return Outcome.refused("You cannot block yourself.")
        }
        val now = clock.now()
        blocks.add(Block(blockerId = principal.userId, blockedId = target, reason = reason, createdAt = now))

        for (conversation in conversations.betweenUsers(principal.userId, target)) {
            if (conversation.state.acceptsNewMessages) {
                conversations.save(
                    conversation.copy(state = ConversationState.ENDED, updatedAt = now),
                )
            }
        }

        // An open introduction from a blocked sender is closed permanently, not left in a
        // queue where a later change of settings could quietly revive it.
        for (request in introductions.incomingFor(principal.userId)) {
            if (request.senderId == target && request.isOpen) {
                introductions.save(
                    request.copy(
                        status = org.fisabilillah.core.model.IntroductionStatus.BLOCKED_BY_RECIPIENT,
                        closedAt = now,
                        closureReason =
                            org.fisabilillah.core.model.IntroductionClosureReason.RECIPIENT_BLOCKED_SENDER,
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
                action = AuditAction.BLOCK_CREATED,
                subjectType = "user",
                subjectId = target.value,
                summary = "Block created; shared conversations ended",
                occurredAt = now,
            ),
        )
        return Outcome.Success(Unit)
    }
}

/** Ends a conversation for everyone in it. Available to either participant. */
public class EndConversationUseCase(
    private val conversations: ConversationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {
    public suspend operator fun invoke(
        principal: Principal,
        conversationId: ConversationId,
    ): Outcome<Conversation> {
        val conversation = conversations.find(conversationId)
            ?: return Outcome.NotFound("that conversation")
        if (conversation.activeMember(principal.userId) == null) {
            return Outcome.refused("You are not part of this conversation.")
        }
        val now = clock.now()
        val updated = conversations.save(
            conversation.copy(state = ConversationState.ENDED, updatedAt = now),
        )
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.CONVERSATION_STATE_CHANGED,
                subjectType = "conversation",
                subjectId = conversationId.value,
                summary = "Conversation ended by a participant",
                occurredAt = now,
            ),
        )
        return Outcome.Success(updated)
    }
}

/**
 * Adds a guardian, trusted person, organiser, or moderator to a conversation in one step.
 *
 * Only ever adds oversight. There is no corresponding remove, because a person who can be
 * removed from a thread by the person they are supervising is not oversight.
 */
public class AddOversightUseCase(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val auditLog: AuditLogRepository,
    private val assembler: ContactContextAssembler,
    private val oversight: OversightDirectory,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        conversationId: ConversationId,
        kind: OversightRequirement,
    ): Outcome<Conversation> {
        val conversation = conversations.find(conversationId)
            ?: return Outcome.NotFound("that conversation")
        if (conversation.activeMember(principal.userId) == null) {
            return Outcome.refused("You are not part of this conversation.")
        }
        val now = clock.now()

        val (userId, role) = when (kind) {
            OversightRequirement.RECIPIENT_GUARDIAN ->
                assembler.availableGuardian(principal.userId)?.linkedUserId to ConversationRole.GUARDIAN
            OversightRequirement.THIRD_PARTY ->
                assembler.availableThirdParty(principal.userId)?.linkedUserId to ConversationRole.THIRD_PARTY
            OversightRequirement.MODERATOR ->
                oversight.availableModerator() to ConversationRole.MODERATOR
            OversightRequirement.ORGANIZATION_REPRESENTATIVE ->
                null to ConversationRole.ORGANIZATION_REPRESENTATIVE
        }
        if (userId == null) {
            return Outcome.refused(
                "No one is available to add in that role right now.",
                RefusalCode.NEEDS_GUARDIAN,
            )
        }
        if (conversation.members.any { it.userId == userId && it.isActive }) {
            return Outcome.Success(conversation)
        }

        val updated = conversations.save(
            conversation.copy(
                members = conversation.members + ConversationMember(userId, role, now),
                updatedAt = now,
            ),
        )
        messages.save(
            Message(
                id = MessageId(ids.newId()),
                conversationId = conversationId,
                senderId = null,
                kind = MessageKind.PARTICIPANT_CHANGE,
                body = "${role.displayName} was added to this conversation.",
                createdAt = now,
                updatedAt = now,
            ),
        )
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.OVERSIGHT_PARTICIPANT_ADDED,
                subjectType = "conversation",
                subjectId = conversationId.value,
                summary = "${role.name} added",
                occurredAt = now,
            ),
        )
        return Outcome.Success(updated)
    }
}

/** Resolves the human-readable title of whatever a purpose points at. */
public interface SubjectTitleResolver {
    public suspend fun titleFor(purpose: ContactPurpose): String
}

/** Supplies identifiers. Injected so that tests get stable, readable ids. */
public interface IdGenerator {
    public fun newId(): String
}

/**
 * Whether enough time has passed since the last drift reminder to post another. Without
 * this a thread that has genuinely moved on gets a reminder after every message, which
 * teaches people to ignore it.
 */
private fun Conversation.lastDriftReminderIsStale(
    now: org.fisabilillah.core.model.Timestamp,
): Boolean {
    val last = lastMessageAt ?: return true
    return (now - last).inWholeHours >= 1
}

/** Purposes that may never be selected from a compose screen. */
public val RESERVED_PURPOSES: Set<ContactPurposeKind> = setOf(
    ContactPurposeKind.MODERATION_MATTER,
    ContactPurposeKind.FORMAL_INTRODUCTION,
)
