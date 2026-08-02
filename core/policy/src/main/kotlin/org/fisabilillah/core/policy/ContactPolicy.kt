package org.fisabilillah.core.policy

import kotlinx.datetime.LocalTime
import org.fisabilillah.core.model.AccountStatus
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.ContactRequirement
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.ConversationRole
import org.fisabilillah.core.model.CrossGenderConversationStructure
import org.fisabilillah.core.model.ModeratorPresenceRule
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards

/**
 * Everything the gate needs in order to decide. Assembled by the use-case layer from
 * repositories; the policy itself performs no I/O and therefore can be exhaustively tested.
 */
public data class ContactContext(
    val initiator: Profile,
    val initiatorSafeguards: UserSafeguards,
    val initiatorRestrictions: List<Restriction> = emptyList(),

    val recipient: Profile,
    /** Already combined with any organisation or community floor by [SafeguardResolver]. */
    val recipientEffectiveSafeguards: UserSafeguards,
    val recipientRestrictions: List<Restriction> = emptyList(),

    val purpose: ContactPurpose,

    val initiatorBlockedRecipient: Boolean = false,
    val recipientBlockedInitiator: Boolean = false,

    val sharedOrganizationIds: Set<OrganizationId> = emptySet(),
    val sharedCommunityIds: Set<CommunityId> = emptySet(),

    /** The initiator holds a moderator or safety-administrator role. */
    val initiatorIsStaff: Boolean = false,

    /** Whether the recipient has nominated someone who could be added to the thread. */
    val recipientHasGuardianAvailable: Boolean = false,

    /** Conversations the initiator has opened in the trailing window. */
    val conversationsStartedInWindow: Int = 0,
    /** Conversations the initiator has opened with this recipient before. */
    val priorConversationsWithRecipient: Int = 0,
    /** Requests the recipient has previously declined from this initiator. */
    val priorDeclinesFromRecipient: Int = 0,

    /** The recipient's local time, used for their quiet hours. */
    val recipientLocalTime: LocalTime,
    val now: Timestamp,
    val currentYear: Int,
)

/** The gate's verdict. */
public sealed interface ContactDecision {

    /**
     * The conversation may be created — subject to [requirements], which the caller must
     * apply rather than treat as advice. [requiredOversight] names the people who have to
     * be in the thread from its first message.
     */
    public data class Allowed(
        val requirements: Set<ContactRequirement>,
        val requiredOversight: Set<OversightRequirement>,
    ) : ContactDecision

    public data class Denied(val reasons: List<DenialReason>) : ContactDecision {
        init {
            require(reasons.isNotEmpty()) { "A denial must state at least one reason" }
            require(reasons.distinct().size == reasons.size) {
                "A denial must not repeat a reason: $reasons"
            }
        }

        /**
         * What the initiator is told. Deliberately vague about the recipient's settings:
         * "she has blocked you" and "she only accepts messages from verified members" tell
         * a determined person very different amounts about how to get through.
         */
        public val userFacingMessage: String get() = reasons.first().userFacingMessage
    }
}

/** Someone who must be added to the conversation when it is created. */
public enum class OversightRequirement {
    RECIPIENT_GUARDIAN,
    MODERATOR,
    THIRD_PARTY,
    ORGANIZATION_REPRESENTATIVE,
}

/**
 * Why contact was refused.
 *
 * [userFacingMessage] is what the initiator sees; [auditReason] is what is recorded. They
 * differ on purpose — the audit trail should be precise, and the message to a possible
 * harasser should not be a hint about which setting to work around.
 */
public enum class DenialReason(
    public val userFacingMessage: String,
    public val auditReason: String,
) {
    SELF_CONTACT(
        "You cannot start a conversation with yourself.",
        "initiator and recipient are the same account",
    ),
    INITIATOR_NOT_ACTIVE(
        "Your account cannot start conversations at the moment.",
        "initiator account is not active",
    ),
    RECIPIENT_NOT_AVAILABLE(
        "This member cannot be contacted.",
        "recipient account is not active",
    ),
    BLOCKED_BY_RECIPIENT(
        "This member cannot be contacted.",
        "recipient has blocked the initiator",
    ),
    INITIATOR_BLOCKED_RECIPIENT(
        "You have blocked this member. Unblock them first if you want to reach them.",
        "initiator has blocked the recipient",
    ),
    INITIATOR_RESTRICTED(
        "Your account is currently restricted from starting conversations.",
        "an active restriction removes the initiator's ability to start conversations",
    ),
    RECIPIENT_RESTRICTED(
        "This member cannot be contacted.",
        "an active restriction removes the recipient's ability to receive messages",
    ),
    PURPOSE_NOT_USER_SELECTABLE(
        "That kind of conversation cannot be started here.",
        "purpose is not user-selectable and must come through its own workflow",
    ),
    PURPOSE_REQUIRES_SUBJECT(
        "Choose the opportunity, class, request, or project this is about.",
        "purpose requires a subject and none was supplied",
    ),
    PURPOSE_DECLINED_BY_RECIPIENT(
        "This member is not open to that kind of request.",
        "recipient has declined this purpose",
    ),
    VERIFICATION_TOO_LOW(
        "This member only accepts messages from members who have verified more of their " +
            "account. You can raise your verification level in Settings.",
        "initiator verification below the recipient's minimum",
    ),
    NOT_CONTACTABLE(
        "This member is not accepting new conversations.",
        "recipient's contactable-by scope excludes the initiator",
    ),
    CROSS_GENDER_CONTACT_CLOSED(
        "This member is not accepting new conversations.",
        "recipient's settings do not permit contact from the opposite gender",
    ),
    OUTSIDE_ORGANIZATION(
        "This member is only contactable by people in their masjid or organisations.",
        "no shared organisation between initiator and recipient",
    ),
    GROUP_CONTEXT_REQUIRED(
        "This member keeps conversations inside a shared group or project. Join the group " +
            "first, or reply in its thread.",
        "recipient requires group context and no shared community was supplied",
    ),
    GUARDIAN_REQUIRED_BUT_UNAVAILABLE(
        "This member's settings require a guardian in the conversation, and one is not " +
            "available right now.",
        "guardian presence required but the recipient has no available guardian",
    ),
    QUIET_HOURS(
        "This member is not accepting new conversations at this time of day. Try again " +
            "later.",
        "recipient's quiet hours block new conversations",
    ),
    RATE_LIMIT_EXCEEDED(
        "You have started a lot of new conversations recently. Please wait before starting " +
            "another.",
        "initiator exceeded the new-conversation rate limit",
    ),
    REPEATED_AFTER_DECLINE(
        "You have already been in touch about this and did not receive a reply. Please do " +
            "not send another request.",
        "initiator attempted contact after being declined",
    ),
    MINOR_ADULT_DIRECT_CONTACT(
        "Direct messages between adults and under-18s are not available.",
        "adult-to-minor direct contact is prohibited",
    ),
    STAFF_ONLY_PURPOSE(
        "That kind of conversation can only be started by the safety team.",
        "purpose reserved for platform staff",
    ),
    INTRODUCTION_MUST_USE_WORKFLOW(
        "Marriage enquiries go through the Formal Family Introduction process, not " +
            "through messages.",
        "attempt to open an introduction conversation outside the introduction workflow",
    ),
}

/**
 * The single place that decides whether one member may open a conversation with another.
 *
 * Everything about the platform's resistance to becoming a place for private pursuit
 * rests on this function and on the fact that no other code path creates a conversation.
 * It is written as one long, ordered sequence of checks rather than as clever composition,
 * because a reviewer needs to be able to read it top to bottom and satisfy themselves that
 * nothing was missed.
 */
public object ContactPolicy {

    /** New conversations one member may start within [RateLimits.NEW_CONVERSATION_WINDOW_HOURS]. */
    public object RateLimits {
        public const val NEW_CONVERSATIONS_PER_WINDOW: Int = 10
        public const val NEW_CONVERSATION_WINDOW_HOURS: Int = 24

        /** After this many unanswered or declined approaches, further contact is refused. */
        public const val MAX_APPROACHES_AFTER_DECLINE: Int = 0
    }

    public fun evaluate(context: ContactContext): ContactDecision {
        val denials = mutableListOf<DenialReason>()

        // ── Identity and account state ─────────────────────────────────────────
        if (context.initiator.id == context.recipient.id) {
            return ContactDecision.Denied(listOf(DenialReason.SELF_CONTACT))
        }
        if (!context.initiator.isActive) denials += DenialReason.INITIATOR_NOT_ACTIVE
        if (!context.recipient.isActive) denials += DenialReason.RECIPIENT_NOT_AVAILABLE

        // ── Blocks. Checked before anything else that could leak information. ──
        if (context.recipientBlockedInitiator) denials += DenialReason.BLOCKED_BY_RECIPIENT
        if (context.initiatorBlockedRecipient) denials += DenialReason.INITIATOR_BLOCKED_RECIPIENT

        // ── Restrictions in force ──────────────────────────────────────────────
        if (isRestricted(context.initiatorRestrictions, RestrictedCapability.START_CONVERSATIONS, context.now) ||
            isRestricted(context.initiatorRestrictions, RestrictedCapability.SEND_MESSAGES, context.now)
        ) {
            denials += DenialReason.INITIATOR_RESTRICTED
        }
        if (isRestricted(context.recipientRestrictions, RestrictedCapability.SEND_MESSAGES, context.now)) {
            denials += DenialReason.RECIPIENT_RESTRICTED
        }

        // A block or a restriction is a hard stop. Returning here also means the reasons
        // below cannot be used to probe a recipient's settings once they have blocked you.
        if (denials.isNotEmpty()) return ContactDecision.Denied(denials.distinct())

        // ── The purpose itself ─────────────────────────────────────────────────
        val purpose = context.purpose
        when (purpose.kind) {
            ContactPurposeKind.FORMAL_INTRODUCTION ->
                return ContactDecision.Denied(listOf(DenialReason.INTRODUCTION_MUST_USE_WORKFLOW))

            ContactPurposeKind.MODERATION_MATTER ->
                if (!context.initiatorIsStaff) {
                    return ContactDecision.Denied(listOf(DenialReason.STAFF_ONLY_PURPOSE))
                }

            else -> Unit
        }
        if (!purpose.kind.userSelectable && !context.initiatorIsStaff) {
            denials += DenialReason.PURPOSE_NOT_USER_SELECTABLE
        }
        if (purpose.kind.requiresSubject && purpose.subject == null) {
            denials += DenialReason.PURPOSE_REQUIRES_SUBJECT
        }

        val safeguards = context.recipientEffectiveSafeguards
        if (purpose.kind in safeguards.declinedPurposes) {
            denials += DenialReason.PURPOSE_DECLINED_BY_RECIPIENT
        }

        // ── Age. The MVP is for adults; adult-to-minor DMs do not exist. ───────
        val initiatorIsAdult = context.initiator.isAdultIn(context.currentYear)
        val recipientIsAdult = context.recipient.isAdultIn(context.currentYear)
        if (initiatorIsAdult != recipientIsAdult) {
            denials += DenialReason.MINOR_ADULT_DIRECT_CONTACT
        }

        // ── Verification floor ─────────────────────────────────────────────────
        if (!(context.initiator.verificationLevel atLeast safeguards.minimumVerificationToContactMe)) {
            denials += DenialReason.VERIFICATION_TOO_LOW
        }

        // ── Audience scope ─────────────────────────────────────────────────────
        val sameGender = context.initiator.gender == context.recipient.gender
        val hasSharedOrganization = context.sharedOrganizationIds.isNotEmpty()
        when (val scope = safeguards.contactableBy) {
            AudienceScope.NOBODY -> denials += DenialReason.NOT_CONTACTABLE

            AudienceScope.SAME_GENDER_ONLY,
            AudienceScope.SAME_GENDER_VERIFIED_ONLY,
            -> {
                if (!sameGender) denials += DenialReason.CROSS_GENDER_CONTACT_CLOSED
                if (scope.requiresVerification &&
                    !(context.initiator.verificationLevel atLeast MINIMUM_VERIFIED)
                ) {
                    denials += DenialReason.VERIFICATION_TOO_LOW
                }
            }

            AudienceScope.VERIFIED_ONLY ->
                if (!(context.initiator.verificationLevel atLeast MINIMUM_VERIFIED)) {
                    denials += DenialReason.VERIFICATION_TOO_LOW
                }

            AudienceScope.MY_ORGANIZATIONS_ONLY ->
                if (!hasSharedOrganization) denials += DenialReason.OUTSIDE_ORGANIZATION

            AudienceScope.EVERYONE -> Unit
        }
        if (safeguards.onlyMyOrganizationsMayContactMe && !hasSharedOrganization) {
            denials += DenialReason.OUTSIDE_ORGANIZATION
        }

        // ── Quiet hours ────────────────────────────────────────────────────────
        if (safeguards.quietHours.blockNewConversations &&
            safeguards.quietHours.covers(context.recipientLocalTime)
        ) {
            denials += DenialReason.QUIET_HOURS
        }

        // ── Rate limits and persistence after a decline ────────────────────────
        if (context.conversationsStartedInWindow >= RateLimits.NEW_CONVERSATIONS_PER_WINDOW) {
            denials += DenialReason.RATE_LIMIT_EXCEEDED
        }
        if (context.priorDeclinesFromRecipient > RateLimits.MAX_APPROACHES_AFTER_DECLINE) {
            denials += DenialReason.REPEATED_AFTER_DECLINE
        }

        // ── Structure required for cross-gender contact ─────────────────────────
        val requirements = mutableSetOf<ContactRequirement>()
        val oversight = mutableSetOf<OversightRequirement>()

        if (safeguards.requireWrittenPurposeStatement) {
            requirements += ContactRequirement.WRITTEN_PURPOSE_REQUIRED
        }

        if (!sameGender) {
            when (safeguards.crossGenderStructure) {
                CrossGenderConversationStructure.DIRECT_WITH_PURPOSE -> Unit

                CrossGenderConversationStructure.GROUP_CONTEXT_ONLY -> {
                    if (context.sharedCommunityIds.isEmpty()) {
                        denials += DenialReason.GROUP_CONTEXT_REQUIRED
                    }
                    requirements += ContactRequirement.GROUP_CONTEXT_ONLY
                }

                CrossGenderConversationStructure.THIRD_PARTY_PRESENT -> {
                    requirements += ContactRequirement.THIRD_PARTY_PRESENT
                    oversight += OversightRequirement.THIRD_PARTY
                }

                CrossGenderConversationStructure.GUARDIAN_PRESENT -> {
                    if (!context.recipientHasGuardianAvailable) {
                        denials += DenialReason.GUARDIAN_REQUIRED_BUT_UNAVAILABLE
                    }
                    requirements += ContactRequirement.GUARDIAN_PRESENT
                    oversight += OversightRequirement.RECIPIENT_GUARDIAN
                }
            }
        }

        if (safeguards.requireGroupContext) {
            if (context.sharedCommunityIds.isEmpty()) denials += DenialReason.GROUP_CONTEXT_REQUIRED
            requirements += ContactRequirement.GROUP_CONTEXT_ONLY
        }
        if (safeguards.requireThirdParty) {
            requirements += ContactRequirement.THIRD_PARTY_PRESENT
            oversight += OversightRequirement.THIRD_PARTY
        }

        // ── Oversight the recipient asked for ──────────────────────────────────
        when (safeguards.moderatorPresence) {
            ModeratorPresenceRule.NEVER -> Unit
            ModeratorPresenceRule.CROSS_GENDER_ONLY -> if (!sameGender) {
                requirements += ContactRequirement.MODERATOR_PRESENT
                oversight += OversightRequirement.MODERATOR
            }
            ModeratorPresenceRule.ALWAYS -> {
                requirements += ContactRequirement.MODERATOR_PRESENT
                oversight += OversightRequirement.MODERATOR
            }
        }

        if (purpose.kind in safeguards.guardianCopiedOnPurposes) {
            if (!context.recipientHasGuardianAvailable) {
                denials += DenialReason.GUARDIAN_REQUIRED_BUT_UNAVAILABLE
            }
            requirements += ContactRequirement.GUARDIAN_PRESENT
            oversight += OversightRequirement.RECIPIENT_GUARDIAN
        }

        // The sender may also ask for oversight of their own accord. Requests to *add*
        // supervision are always honoured; requests to remove it are not possible.
        if (purpose.requestGuardianPresent && context.recipientHasGuardianAvailable) {
            requirements += ContactRequirement.GUARDIAN_PRESENT
            oversight += OversightRequirement.RECIPIENT_GUARDIAN
        }
        if (purpose.requestModeratorPresent) {
            requirements += ContactRequirement.MODERATOR_PRESENT
            oversight += OversightRequirement.MODERATOR
        }
        if (purpose.requestOrganizationRepresentative != null) {
            requirements += ContactRequirement.ORGANIZATION_REPRESENTATIVE_PRESENT
            oversight += OversightRequirement.ORGANIZATION_REPRESENTATIVE
        }

        // ── Call and meeting terms recorded on the thread ──────────────────────
        if (!callsPermitted(safeguards.voiceCallsAllowedFrom, sameGender, context)) {
            requirements += ContactRequirement.NO_VOICE_CALLS
        }
        if (!callsPermitted(safeguards.videoCallsAllowedFrom, sameGender, context)) {
            requirements += ContactRequirement.NO_VIDEO_CALLS
        }
        if (safeguards.meetingsMustBeInPublicPlaces) {
            requirements += ContactRequirement.PUBLIC_MEETINGS_ONLY
        }
        if (safeguards.meetingsRequireThirdParty) {
            requirements += ContactRequirement.THIRD_PARTY_AT_MEETINGS
        }
        if (safeguards.autoArchiveAfterCompletion) {
            requirements += ContactRequirement.ARCHIVES_ON_COMPLETION
        }

        return if (denials.isEmpty()) {
            ContactDecision.Allowed(requirements, oversight)
        } else {
            // Distinct because the audience scope and the explicit verification floor can
            // independently reach the same conclusion, and telling someone twice that they
            // are not verified enough helps nobody.
            ContactDecision.Denied(denials.distinct())
        }
    }

    /**
     * Whether [sender] may add a message to an existing [conversation].
     *
     * Separate from [evaluate] because the conditions are different: a thread that was
     * legitimately opened can still stop accepting messages when it is completed, frozen
     * by moderation, ended by a participant — or when one of the two people involved
     * blocks the other, leaves, or loses their account.
     */
    public fun canSendMessage(
        conversation: Conversation,
        sender: Profile,
        senderRestrictions: List<Restriction>,
        blockedCounterpartIds: Set<UserId>,
        now: Timestamp,
    ): MessageDecision {
        if (!sender.isActive) {
            return MessageDecision.Denied("Your account cannot send messages at the moment.")
        }
        if (isRestricted(senderRestrictions, RestrictedCapability.SEND_MESSAGES, now)) {
            return MessageDecision.Denied("Your account is currently restricted from sending messages.")
        }
        val member = conversation.activeMember(sender.id)
            ?: return MessageDecision.Denied("You are not part of this conversation.")

        if (!conversation.state.acceptsNewMessages) {
            return MessageDecision.Denied(
                when (conversation.state) {
                    org.fisabilillah.core.model.ConversationState.FROZEN ->
                        "This conversation is on hold while the safety team reviews it."
                    org.fisabilillah.core.model.ConversationState.ENDED ->
                        "This conversation has been ended."
                    else -> "This conversation is closed."
                },
            )
        }
        val mutedUntil = member.mutedUntil
        if (mutedUntil != null && now < mutedUntil) {
            return MessageDecision.Denied("You are muted in this conversation.")
        }

        // A block ends the thread for both of them, whichever direction it runs in.
        val counterparts = conversation.participantIds - sender.id
        if (counterparts.any { it in blockedCounterpartIds }) {
            return MessageDecision.Denied("This conversation is no longer available.")
        }

        // Oversight participants observe and intervene; they are not there to chat.
        if (member.role == ConversationRole.MODERATOR ||
            member.role == ConversationRole.GUARDIAN ||
            member.role == ConversationRole.THIRD_PARTY ||
            member.role == ConversationRole.ORGANIZATION_REPRESENTATIVE
        ) {
            return MessageDecision.Allowed(asOversight = true)
        }
        return MessageDecision.Allowed(asOversight = false)
    }

    private fun callsPermitted(
        scope: AudienceScope,
        sameGender: Boolean,
        context: ContactContext,
    ): Boolean = when (scope) {
        AudienceScope.NOBODY -> false
        AudienceScope.EVERYONE -> true
        AudienceScope.VERIFIED_ONLY -> context.initiator.verificationLevel atLeast MINIMUM_VERIFIED
        AudienceScope.SAME_GENDER_ONLY -> sameGender
        AudienceScope.SAME_GENDER_VERIFIED_ONLY ->
            sameGender && (context.initiator.verificationLevel atLeast MINIMUM_VERIFIED)
        AudienceScope.MY_ORGANIZATIONS_ONLY -> context.sharedOrganizationIds.isNotEmpty()
    }

    private fun isRestricted(
        restrictions: List<Restriction>,
        capability: RestrictedCapability,
        now: Timestamp,
    ): Boolean = restrictions.any { it.isActiveAt(now) && it.capability.covers(capability) }

    /**
     * What "verified" means for the purposes of an audience scope. Identity verification is
     * the bar: an email address is not an identity.
     */
    private val MINIMUM_VERIFIED = org.fisabilillah.core.model.VerificationLevel.IDENTITY_VERIFIED
}

public sealed interface MessageDecision {
    public data class Allowed(val asOversight: Boolean) : MessageDecision
    public data class Denied(val message: String) : MessageDecision
}

/** True when the account is in a state that ends every conversation it is part of. */
public fun Profile.cannotContinueConversations(): Boolean =
    deletedAt != null ||
        status == AccountStatus.BANNED ||
        status == AccountStatus.SUSPENDED ||
        status == AccountStatus.DELETION_REQUESTED
