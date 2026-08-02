package org.fisabilillah.core.domain

import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.MembershipStatus
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.SafeguardFloor
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactRole
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.policy.ContactContext
import org.fisabilillah.core.policy.SafeguardResolver
import kotlin.time.Duration.Companion.hours

/**
 * Gathers the facts the contact gate needs.
 *
 * Deliberately separate from [org.fisabilillah.core.policy.ContactPolicy]: assembling this
 * touches nine repositories, and keeping that away from the decision itself is what allows
 * the decision to be tested exhaustively without a database.
 */
public class ContactContextAssembler(
    private val profiles: ProfileRepository,
    private val safeguards: SafeguardRepository,
    private val blocks: BlockRepository,
    private val restrictions: RestrictionRepository,
    private val organizations: OrganizationRepository,
    private val communities: CommunityRepository,
    private val trustedContacts: TrustedContactRepository,
    private val conversations: ConversationRepository,
    private val spaces: SpaceResolver,
    private val clock: AppClock,
) {

    public suspend fun assemble(
        principal: Principal,
        initiator: Profile,
        recipient: Profile,
        purpose: ContactPurpose,
    ): ContactContext {
        val now = clock.now()

        val recipientOwnSafeguards = safeguards.forUser(recipient.id)
            ?: UserSafeguards(userId = recipient.id)
        val floors = contextualFloorsFor(recipient.id, purpose)
        val effective = SafeguardResolver.effective(recipientOwnSafeguards, floors)

        val initiatorSafeguards = safeguards.forUser(initiator.id)
            ?: UserSafeguards(userId = initiator.id)

        val sharedOrganizations = sharedOrganizations(initiator.id, recipient.id)
        val sharedCommunities = sharedCommunities(initiator.id, recipient.id, purpose)

        val guardian = availableGuardian(recipient.id)

        return ContactContext(
            initiator = initiator,
            initiatorSafeguards = initiatorSafeguards,
            initiatorRestrictions = restrictions.activeFor(initiator.id, now),
            recipient = recipient,
            recipientEffectiveSafeguards = effective,
            recipientRestrictions = restrictions.activeFor(recipient.id, now),
            purpose = purpose,
            initiatorBlockedRecipient = blocks.isBlocked(initiator.id, recipient.id),
            recipientBlockedInitiator = blocks.isBlocked(recipient.id, initiator.id),
            sharedOrganizationIds = sharedOrganizations,
            sharedCommunityIds = sharedCommunities,
            initiatorIsStaff = principal.isStaff,
            recipientHasGuardianAvailable = guardian != null,
            conversationsStartedInWindow = conversations.countStartedBy(
                initiator.id,
                now - RATE_WINDOW_HOURS.hours,
            ),
            priorConversationsWithRecipient =
                conversations.betweenUsers(initiator.id, recipient.id).size,
            priorDeclinesFromRecipient =
                conversations.countDeclinedApproaches(initiator.id, recipient.id),
            recipientLocalTime = clock.localTimeIn(recipient.availability.timeZoneId),
            now = now,
            currentYear = clock.currentYear(),
        )
    }

    /**
     * The safeguards in force for a member outside any particular space.
     *
     * Organisation floors are included because affiliation is not situational: a member of
     * a masjid that requires oversight on cross-gender threads has that requirement
     * wherever they are on the platform. Community floors are not, for the reason given on
     * [contextualFloorsFor].
     */
    public suspend fun effectiveSafeguards(userId: UserId): UserSafeguards {
        val own = safeguards.forUser(userId) ?: UserSafeguards(userId = userId)
        return SafeguardResolver.effective(own, floorsFor(userId))
    }

    /** Floors from the organisations [userId] belongs to. */
    public suspend fun floorsFor(userId: UserId): List<SafeguardFloor> =
        organizations.membershipsOf(userId)
            .filter { it.isActive }
            .mapNotNull { organizations.find(it.organizationId)?.safeguardFloor }
            .filter { it != SafeguardFloor.NONE }

    /**
     * The floors that apply to a conversation about [purpose].
     *
     * Organisation floors, plus the floor of the community the conversation is actually
     * taking place in — hence "inside their spaces".
     *
     * The distinction matters and is easy to get wrong. A youth programme rightly demands
     * that anyone talking to it holds a current background check. Applying that floor to
     * every conversation its volunteers have would mean a brother who helps with Saturday
     * football could no longer be asked about a house move, which is not what the
     * programme asked for and not something it has any business deciding.
     */
    public suspend fun contextualFloorsFor(
        userId: UserId,
        purpose: ContactPurpose?,
    ): List<SafeguardFloor> {
        val organizationFloors = floorsFor(userId)
        if (purpose == null) return organizationFloors

        val space = spaces.spaceOf(purpose.subject)
        val floors = organizationFloors.toMutableList()

        space.organizationId
            ?.takeIf { id -> organizations.membershipsOf(userId).any { it.organizationId == id && it.isActive } }
            ?.let { organizations.find(it)?.safeguardFloor }
            ?.takeIf { it != SafeguardFloor.NONE }
            ?.let { if (it !in floors) floors += it }

        space.communityId
            ?.takeIf { id ->
                communities.membershipsOf(userId)
                    .any { it.communityId == id && it.status == MembershipStatus.ACTIVE }
            }
            ?.let { communities.find(it)?.safeguardFloor }
            ?.takeIf { it != SafeguardFloor.NONE }
            ?.let { floors += it }

        return floors
    }

    public suspend fun sharedOrganizations(a: UserId, b: UserId): Set<OrganizationId> {
        val first = organizations.membershipsOf(a).filter { it.isActive }
            .map { it.organizationId }.toSet()
        if (first.isEmpty()) return emptySet()
        val second = organizations.membershipsOf(b).filter { it.isActive }
            .map { it.organizationId }.toSet()
        return first intersect second
    }

    public suspend fun sharedCommunities(
        a: UserId,
        b: UserId,
        purpose: ContactPurpose? = null,
    ): Set<CommunityId> {
        val first = communities.membershipsOf(a)
            .filter { it.status == MembershipStatus.ACTIVE }
            .map { it.communityId }.toSet()
        val second = communities.membershipsOf(b)
            .filter { it.status == MembershipStatus.ACTIVE }
            .map { it.communityId }.toSet()
        val shared = (first intersect second).toMutableSet()

        // A conversation about a community the two people are both in counts as group
        // context even if the membership records were only just created.
        val subject = purpose?.subject
        if (subject is PurposeSubject.Community && subject.id in first && subject.id in second) {
            shared += subject.id
        }
        return shared
    }

    /**
     * A guardian who could actually be added to a conversation.
     *
     * Requires a linked account: a name and a phone number in someone's settings is not a
     * participant, and quietly treating it as one would mean telling a member their wali
     * was present when nobody was reading the thread.
     */
    public suspend fun availableGuardian(userId: UserId): TrustedContact? =
        trustedContacts.forOwner(userId).firstOrNull {
            it.active &&
                it.deletedAt == null &&
                it.linkedUserId != null &&
                (
                    it.role == TrustedContactRole.WALI ||
                        it.role == TrustedContactRole.INTERMEDIARY ||
                        it.role == TrustedContactRole.CONVERSATION_GUARDIAN
                    )
        }

    public suspend fun availableThirdParty(userId: UserId): TrustedContact? =
        trustedContacts.forOwner(userId).firstOrNull {
            it.active && it.deletedAt == null && it.linkedUserId != null
        }

    public suspend fun profileOrNull(id: UserId): Profile? = profiles.find(id)

    private companion object {
        const val RATE_WINDOW_HOURS = 24
    }
}

/**
 * The organisation or community a conversation belongs to.
 *
 * Resolved from whatever the purpose points at: a listing published by a masjid puts the
 * conversation inside that masjid, a project attached to a community puts it inside that
 * community, and a bare organisation enquiry names the organisation directly.
 */
public data class ConversationSpace(
    val organizationId: OrganizationId? = null,
    val communityId: CommunityId? = null,
) {
    public companion object {
        public val NONE: ConversationSpace = ConversationSpace()
    }
}

public interface SpaceResolver {
    public suspend fun spaceOf(subject: PurposeSubject?): ConversationSpace
}

/** Someone the platform can add to a conversation to provide oversight. */
public interface OversightDirectory {
    /** A moderator available to sit in on a conversation, if one is. */
    public suspend fun availableModerator(): UserId?

    /** A moderator for a specific community, preferred over a general one. */
    public suspend fun communityModerator(communityId: CommunityId): UserId?

    /** Someone entitled to represent [organizationId] in a conversation. */
    public suspend fun organizationRepresentative(organizationId: OrganizationId): UserId?
}
