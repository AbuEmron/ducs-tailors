package org.fisabilillah.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fisabilillah.core.domain.AuditLogRepository
import org.fisabilillah.core.domain.BlockRepository
import org.fisabilillah.core.domain.CampaignRepository
import org.fisabilillah.core.domain.CommitmentRepository
import org.fisabilillah.core.domain.CommunityRepository
import org.fisabilillah.core.domain.ConsentRepository
import org.fisabilillah.core.domain.ConversationRepository
import org.fisabilillah.core.domain.IntroductionRepository
import org.fisabilillah.core.domain.LearningRepository
import org.fisabilillah.core.domain.LearningSearchCriteria
import org.fisabilillah.core.domain.ModerationRepository
import org.fisabilillah.core.domain.NotificationRepository
import org.fisabilillah.core.domain.OpportunityRepository
import org.fisabilillah.core.domain.OpportunitySearchCriteria
import org.fisabilillah.core.domain.OrganizationRepository
import org.fisabilillah.core.domain.PeopleSearchCriteria
import org.fisabilillah.core.domain.ProfileRepository
import org.fisabilillah.core.domain.ProjectRepository
import org.fisabilillah.core.domain.QualificationRepository
import org.fisabilillah.core.domain.DeviceSessionRepository
import org.fisabilillah.core.domain.VerificationRepository
import org.fisabilillah.core.domain.RequestSearchCriteria
import org.fisabilillah.core.domain.RestrictionRepository
import org.fisabilillah.core.domain.ServiceRequestRepository
import org.fisabilillah.core.domain.SkillRepository
import org.fisabilillah.core.domain.TrustRepository
import org.fisabilillah.core.domain.TrustedContactRepository
import org.fisabilillah.core.model.Appeal
import org.fisabilillah.core.model.AppealId
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.Block
import org.fisabilillah.core.model.Campaign
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.CommitmentId
import org.fisabilillah.core.model.Community
import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.CommunityMember
import org.fisabilillah.core.model.ConsentKind
import org.fisabilillah.core.model.ConsentRecord
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.ConversationState
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.IntroductionId
import org.fisabilillah.core.model.IntroductionParticipant
import org.fisabilillah.core.model.IntroductionStatus
import org.fisabilillah.core.model.LearningEnrollment
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.ListingStatus
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.MessageRedaction
import org.fisabilillah.core.model.ModerationAction
import org.fisabilillah.core.model.ModerationCase
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.Organization
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.OrganizationMember
import org.fisabilillah.core.model.Page
import org.fisabilillah.core.model.PrivateImpactRecord
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ProjectId
import org.fisabilillah.core.model.ProjectMember
import org.fisabilillah.core.model.ProjectTask
import org.fisabilillah.core.model.Qualification
import org.fisabilillah.core.model.QualificationId
import org.fisabilillah.core.model.VerificationRequest
import org.fisabilillah.core.model.VerificationRequestId
import org.fisabilillah.core.model.QualificationReviewState
import org.fisabilillah.core.model.Report
import org.fisabilillah.core.model.ReportId
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.RequestResponse
import org.fisabilillah.core.model.RequestStatus
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.RestrictionId
import org.fisabilillah.core.model.RecordedSignal
import org.fisabilillah.core.model.SafetyIncident
import org.fisabilillah.core.model.SafetySignalId
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.Skill
import org.fisabilillah.core.model.SkillId
import org.fisabilillah.core.model.TaskId
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.DeviceSession
import org.fisabilillah.core.model.TaskEndorsement
import org.fisabilillah.core.model.TrustRecord
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactId
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VolunteerApplication
import org.fisabilillah.core.model.VolunteerOpportunity

/**
 * # Development fixture
 *
 * In-memory implementations of every repository port, backed by ordinary maps.
 *
 * These exist so the application can be run, demonstrated and tested end to end before a
 * server is stood up — and so that the safety-critical use cases can be exercised against
 * real data without a database. They are **not** a production data layer: nothing is
 * persisted, nothing is transactional, and the authorisation guarantees that row-level
 * security provides in `backend/supabase/migrations/0013_row_level_security.sql` are
 * absent here.
 *
 * The production implementation of these same interfaces talks to Postgres through
 * Supabase, where every one of these tables carries RLS policies. The use-case layer is
 * identical in both cases, which is the point of putting the rules there.
 */
public class InMemoryStore {
    public val mutex: Mutex = Mutex()

    public val profiles: MutableMap<UserId, Profile> = linkedMapOf()
    public val safeguards: MutableMap<UserId, UserSafeguards> = linkedMapOf()
    public val blocks: MutableSet<Pair<UserId, UserId>> = linkedSetOf()
    public val blockRecords: MutableList<Block> = mutableListOf()
    public val restrictions: MutableList<Restriction> = mutableListOf()
    public val conversations: MutableMap<ConversationId, Conversation> = linkedMapOf()
    public val messages: MutableList<Message> = mutableListOf()
    public val redactions: MutableList<MessageRedaction> = mutableListOf()
    public val declinedApproaches: MutableMap<Pair<UserId, UserId>, Int> = linkedMapOf()
    public val trustedContacts: MutableMap<TrustedContactId, TrustedContact> = linkedMapOf()
    public val introductionSettings: MutableMap<UserId, FormalIntroductionSettings> = linkedMapOf()
    public val introductions: MutableMap<IntroductionId, FormalIntroductionRequest> = linkedMapOf()
    public val introductionParticipants: MutableList<IntroductionParticipant> = mutableListOf()
    public val organizations: MutableMap<OrganizationId, Organization> = linkedMapOf()
    public val organizationMembers: MutableList<OrganizationMember> = mutableListOf()
    public val communities: MutableMap<CommunityId, Community> = linkedMapOf()
    public val communityMembers: MutableList<CommunityMember> = mutableListOf()
    public val learningOfferings: MutableMap<ListingId, LearningOffering> = linkedMapOf()
    public val enrollments: MutableList<LearningEnrollment> = mutableListOf()
    public val opportunities: MutableMap<ListingId, VolunteerOpportunity> = linkedMapOf()
    public val applications: MutableList<VolunteerApplication> = mutableListOf()
    public val serviceRequests: MutableMap<RequestId, ServiceRequest> = linkedMapOf()
    public val requestResponses: MutableList<RequestResponse> = mutableListOf()
    public val projects: MutableMap<ProjectId, Project> = linkedMapOf()
    public val projectMembers: MutableList<ProjectMember> = mutableListOf()
    public val projectTasks: MutableMap<TaskId, ProjectTask> = linkedMapOf()
    public val commitments: MutableMap<CommitmentId, Commitment> = linkedMapOf()
    public val trustRecords: MutableMap<UserId, TrustRecord> = linkedMapOf()
    public val impactRecords: MutableMap<UserId, PrivateImpactRecord> = linkedMapOf()
    public val reports: MutableMap<ReportId, Report> = linkedMapOf()
    public val cases: MutableMap<ModerationCaseId, ModerationCase> = linkedMapOf()
    public val moderationActions: MutableList<ModerationAction> = mutableListOf()
    public val appeals: MutableMap<AppealId, Appeal> = linkedMapOf()
    public val incidents: MutableList<SafetyIncident> = mutableListOf()
    public val safetySignals: MutableList<RecordedSignal> = mutableListOf()
    public val auditLog: MutableList<AuditLogEntry> = mutableListOf()
    public val notifications: MutableList<Notification> = mutableListOf()
    public val consents: MutableList<ConsentRecord> = mutableListOf()
    public val qualifications: MutableList<Qualification> = mutableListOf()
    public val verificationRequests: MutableMap<VerificationRequestId, VerificationRequest> = linkedMapOf()
    public val endorsements: MutableList<TaskEndorsement> = mutableListOf()
    public val deviceSessions: MutableMap<String, DeviceSession> = linkedMapOf()
    public val campaigns: MutableMap<CampaignId, Campaign> = linkedMapOf()
    public val skills: MutableMap<SkillId, Skill> = linkedMapOf()

    /** Bumped on every write so the observable flows below re-emit. */
    public val revision: MutableStateFlow<Long> = MutableStateFlow(0)

    public fun touch() {
        revision.value = revision.value + 1
    }
}

public class InMemoryProfileRepository(private val store: InMemoryStore) : ProfileRepository {
    override suspend fun find(id: UserId): Profile? = store.mutex.withLock { store.profiles[id] }

    override suspend fun all(): List<Profile> =
        store.mutex.withLock { store.profiles.values.toList() }

    override suspend fun findAll(ids: Collection<UserId>): List<Profile> =
        store.mutex.withLock { ids.mapNotNull { store.profiles[it] } }

    override suspend fun save(profile: Profile): Profile = store.mutex.withLock {
        store.profiles[profile.id] = profile
        store.touch()
        profile
    }

    override fun observe(id: UserId): Flow<Profile?> =
        store.revision.map { store.profiles[id] }

    override suspend fun search(criteria: PeopleSearchCriteria, cursor: String?): Page<Profile> =
        store.mutex.withLock {
            val skillQuery = criteria.skillQuery
            val minimumVerification = criteria.minimumVerification
            val matches = store.profiles.values.asSequence()
                .filter { it.isActive }
                .filter { profile ->
                    skillQuery == null || profile.skills.any {
                        it.skill.displayName.contains(skillQuery, ignoreCase = true)
                    }
                }
                .filter { profile ->
                    criteria.categories.isEmpty() ||
                        profile.areasWillingToHelp.any { it in criteria.categories }
                }
                .filter { profile ->
                    criteria.languages.isEmpty() ||
                        profile.languages.any { it.tag in criteria.languages }
                }
                .filter { criteria.city == null || it.place.approximate.city == criteria.city }
                .filter { profile ->
                    minimumVerification == null ||
                        (profile.verificationLevel atLeast minimumVerification)
                }
                .filter { profile ->
                    criteria.teachingCapacities.isEmpty() ||
                        profile.teachingCapacity in criteria.teachingCapacities
                }
                .filter { profile ->
                    criteria.organizationId == null ||
                        criteria.organizationId in profile.organizationIds
                }
                .filter { profile ->
                    criteria.availableOn == null ||
                        profile.availability.windows.any { it.day == criteria.availableOn }
                }
                // Ordered by display name, deliberately. There is no ranking to apply and
                // no signal worth ranking by.
                .sortedBy { it.displayName }
                .take(criteria.limit)
                .toList()
            Page(matches, null, matches.size)
        }
}

public class InMemorySafeguardRepository(private val store: InMemoryStore) :
    org.fisabilillah.core.domain.SafeguardRepository {
    override suspend fun forUser(id: UserId): UserSafeguards? =
        store.mutex.withLock { store.safeguards[id] }

    override suspend fun save(safeguards: UserSafeguards): UserSafeguards = store.mutex.withLock {
        store.safeguards[safeguards.userId] = safeguards
        store.touch()
        safeguards
    }

    override fun observe(id: UserId): Flow<UserSafeguards?> =
        store.revision.map { store.safeguards[id] }
}

public class InMemoryBlockRepository(private val store: InMemoryStore) : BlockRepository {
    override suspend fun isBlocked(blocker: UserId, blocked: UserId): Boolean =
        store.mutex.withLock { (blocker to blocked) in store.blocks }

    override suspend fun blockedBy(userId: UserId): Set<UserId> = store.mutex.withLock {
        store.blocks.filter { it.second == userId }.map { it.first }.toSet()
    }

    override suspend fun blocking(userId: UserId): Set<UserId> = store.mutex.withLock {
        store.blocks.filter { it.first == userId }.map { it.second }.toSet()
    }

    override suspend fun add(block: Block): Unit = store.mutex.withLock {
        store.blocks += block.blockerId to block.blockedId
        store.blockRecords += block
        store.touch()
    }

    override suspend fun remove(blocker: UserId, blocked: UserId): Unit = store.mutex.withLock {
        store.blocks -= (blocker to blocked)
        store.touch()
    }
}

public class InMemoryRestrictionRepository(private val store: InMemoryStore) : RestrictionRepository {
    override suspend fun activeFor(userId: UserId, now: Timestamp): List<Restriction> =
        store.mutex.withLock {
            store.restrictions.filter { it.userId == userId && it.isActiveAt(now) }
        }

    override suspend fun add(restriction: Restriction): Restriction = store.mutex.withLock {
        store.restrictions += restriction
        store.touch()
        restriction
    }

    override suspend fun lift(id: RestrictionId, by: UserId, at: Timestamp): Unit =
        store.mutex.withLock {
            val index = store.restrictions.indexOfFirst { it.id == id }
            if (index >= 0) {
                store.restrictions[index] =
                    store.restrictions[index].copy(liftedAt = at, liftedBy = by)
                store.touch()
            }
        }
}

public class InMemoryConversationRepository(private val store: InMemoryStore) :
    ConversationRepository {

    override suspend fun find(id: ConversationId): Conversation? =
        store.mutex.withLock { store.conversations[id] }

    override suspend fun save(conversation: Conversation): Conversation = store.mutex.withLock {
        val previous = store.conversations[conversation.id]
        store.conversations[conversation.id] = conversation

        // Counting refused approaches at the moment a thread ends is what lets the contact
        // gate refuse a second attempt without scanning message history at read time.
        if (previous != null &&
            previous.state.acceptsNewMessages &&
            conversation.state == ConversationState.ENDED
        ) {
            val initiator = conversation.members.firstOrNull {
                it.role == org.fisabilillah.core.model.ConversationRole.INITIATOR
            }?.userId
            val recipient = conversation.members.firstOrNull {
                it.role == org.fisabilillah.core.model.ConversationRole.RECIPIENT
            }?.userId
            val neverReplied = store.messages.none {
                it.conversationId == conversation.id && it.senderId == recipient
            }
            if (initiator != null && recipient != null && neverReplied) {
                val key = initiator to recipient
                store.declinedApproaches[key] = (store.declinedApproaches[key] ?: 0) + 1
            }
        }
        store.touch()
        conversation
    }

    override suspend fun forUser(userId: UserId, cursor: String?): Page<Conversation> =
        store.mutex.withLock {
            val items = store.conversations.values
                .filter { it.activeMember(userId) != null && it.deletedAt == null }
                .sortedByDescending { it.lastMessageAt ?: it.createdAt }
            Page(items, null, items.size)
        }

    override fun observeForUser(userId: UserId): Flow<List<Conversation>> =
        store.revision.map { _ ->
            store.conversations.values
                .filter { it.activeMember(userId) != null && it.deletedAt == null }
                .sortedByDescending { it.lastMessageAt ?: it.createdAt }
        }

    override suspend fun countStartedBy(userId: UserId, since: Timestamp): Int =
        store.mutex.withLock {
            store.conversations.values.count { conversation ->
                conversation.createdAt >= since &&
                    conversation.members.any {
                        it.userId == userId &&
                            it.role == org.fisabilillah.core.model.ConversationRole.INITIATOR
                    }
            }
        }

    override suspend fun betweenUsers(a: UserId, b: UserId): List<Conversation> =
        store.mutex.withLock {
            store.conversations.values.filter { conversation ->
                val ids = conversation.members.map { it.userId }.toSet()
                a in ids && b in ids
            }
        }

    override suspend fun needingArchive(now: Timestamp): List<Conversation> =
        store.mutex.withLock {
            store.conversations.values.filter { conversation ->
                conversation.archiveAfter != null &&
                    conversation.archiveAfter!! <= now &&
                    conversation.state != ConversationState.ARCHIVED &&
                    conversation.state != ConversationState.ENDED
            }
        }

    override suspend fun countDeclinedApproaches(initiator: UserId, recipient: UserId): Int =
        store.mutex.withLock { store.declinedApproaches[initiator to recipient] ?: 0 }
}

public class InMemoryMessageRepository(private val store: InMemoryStore) :
    org.fisabilillah.core.domain.MessageRepository {

    override suspend fun find(id: MessageId): Message? =
        store.mutex.withLock { store.messages.firstOrNull { it.id == id } }

    override suspend fun save(message: Message): Message = store.mutex.withLock {
        val index = store.messages.indexOfFirst { it.id == message.id }
        if (index >= 0) store.messages[index] = message else store.messages += message
        store.touch()
        message
    }

    override suspend fun forConversation(id: ConversationId, cursor: String?): Page<Message> =
        store.mutex.withLock {
            val items = store.messages
                .filter { it.conversationId == id && it.deletedAt == null }
                .sortedBy { it.createdAt }
            Page(items, null, items.size)
        }

    override fun observeConversation(id: ConversationId): Flow<List<Message>> =
        store.revision.map { _ ->
            store.messages
                .filter { it.conversationId == id && it.deletedAt == null }
                .sortedBy { it.createdAt }
        }

    override suspend fun preserveRedaction(redaction: MessageRedaction): Unit =
        store.mutex.withLock {
            store.redactions += redaction
            store.touch()
        }

    override suspend fun redactionsFor(id: ConversationId): List<MessageRedaction> =
        store.mutex.withLock { store.redactions.filter { it.conversationId == id } }
}

public class InMemoryTrustedContactRepository(private val store: InMemoryStore) :
    TrustedContactRepository {

    override suspend fun forOwner(ownerId: UserId): List<TrustedContact> =
        store.mutex.withLock {
            store.trustedContacts.values.filter { it.ownerId == ownerId && it.deletedAt == null }
        }

    override suspend fun find(id: TrustedContactId): TrustedContact? =
        store.mutex.withLock { store.trustedContacts[id] }

    override suspend fun save(contact: TrustedContact): TrustedContact = store.mutex.withLock {
        store.trustedContacts[contact.id] = contact
        store.touch()
        contact
    }

    override suspend fun delete(id: TrustedContactId): Unit = store.mutex.withLock {
        store.trustedContacts.remove(id)
        store.touch()
    }
}

public class InMemoryIntroductionRepository(private val store: InMemoryStore) :
    IntroductionRepository {

    override suspend fun settingsFor(userId: UserId): FormalIntroductionSettings? =
        store.mutex.withLock { store.introductionSettings[userId] }

    override suspend fun saveSettings(
        settings: FormalIntroductionSettings,
    ): FormalIntroductionSettings = store.mutex.withLock {
        store.introductionSettings[settings.userId] = settings
        store.touch()
        settings
    }

    override suspend fun find(id: IntroductionId): FormalIntroductionRequest? =
        store.mutex.withLock { store.introductions[id] }

    override suspend fun save(request: FormalIntroductionRequest): FormalIntroductionRequest =
        store.mutex.withLock {
            store.introductions[request.id] = request
            store.touch()
            request
        }

    override suspend fun incomingFor(userId: UserId): List<FormalIntroductionRequest> =
        store.mutex.withLock { store.introductions.values.filter { it.recipientId == userId } }

    override suspend fun outgoingFrom(userId: UserId): List<FormalIntroductionRequest> =
        store.mutex.withLock { store.introductions.values.filter { it.senderId == userId } }

    override suspend fun countFrom(senderId: UserId, since: Timestamp): Int =
        store.mutex.withLock {
            store.introductions.values.count { it.senderId == senderId && it.createdAt >= since }
        }

    override suspend fun countBetween(senderId: UserId, recipientId: UserId): Int =
        store.mutex.withLock {
            store.introductions.values.count {
                it.senderId == senderId && it.recipientId == recipientId
            }
        }

    override suspend fun participants(id: IntroductionId): List<IntroductionParticipant> =
        store.mutex.withLock { store.introductionParticipants.filter { it.introductionId == id } }

    override suspend fun addParticipant(participant: IntroductionParticipant): Unit =
        store.mutex.withLock {
            store.introductionParticipants.removeAll {
                it.introductionId == participant.introductionId && it.userId == participant.userId
            }
            store.introductionParticipants += participant
            store.touch()
        }
}

public class InMemoryOrganizationRepository(private val store: InMemoryStore) :
    OrganizationRepository {

    override suspend fun find(id: OrganizationId): Organization? =
        store.mutex.withLock { store.organizations[id] }

    override suspend fun findAll(ids: Collection<OrganizationId>): List<Organization> =
        store.mutex.withLock { ids.mapNotNull { store.organizations[it] } }

    override suspend fun save(organization: Organization): Organization = store.mutex.withLock {
        store.organizations[organization.id] = organization
        store.touch()
        organization
    }

    override suspend fun members(id: OrganizationId): List<OrganizationMember> =
        store.mutex.withLock { store.organizationMembers.filter { it.organizationId == id } }

    override suspend fun membershipsOf(userId: UserId): List<OrganizationMember> =
        store.mutex.withLock { store.organizationMembers.filter { it.userId == userId } }

    override suspend fun saveMember(member: OrganizationMember): Unit = store.mutex.withLock {
        store.organizationMembers.removeAll {
            it.organizationId == member.organizationId && it.userId == member.userId
        }
        store.organizationMembers += member
        store.touch()
    }

    override suspend fun search(query: String): List<Organization> = store.mutex.withLock {
        store.organizations.values.filter { it.name.contains(query, ignoreCase = true) }
    }
}

public class InMemoryCommunityRepository(private val store: InMemoryStore) : CommunityRepository {
    override suspend fun find(id: CommunityId): Community? =
        store.mutex.withLock { store.communities[id] }

    override suspend fun save(community: Community): Community = store.mutex.withLock {
        store.communities[community.id] = community
        store.touch()
        community
    }

    override suspend fun members(id: CommunityId): List<CommunityMember> =
        store.mutex.withLock { store.communityMembers.filter { it.communityId == id } }

    override suspend fun membershipsOf(userId: UserId): List<CommunityMember> =
        store.mutex.withLock { store.communityMembers.filter { it.userId == userId } }

    override suspend fun saveMember(member: CommunityMember): Unit = store.mutex.withLock {
        store.communityMembers.removeAll {
            it.communityId == member.communityId && it.userId == member.userId
        }
        store.communityMembers += member
        store.touch()
    }

    override suspend fun list(cursor: String?): Page<Community> = store.mutex.withLock {
        val items = store.communities.values.filter { !it.isArchived }
        Page(items, null, items.size)
    }
}

public class InMemoryLearningRepository(private val store: InMemoryStore) : LearningRepository {
    override suspend fun find(id: ListingId): LearningOffering? =
        store.mutex.withLock { store.learningOfferings[id] }

    override suspend fun save(offering: LearningOffering): LearningOffering = store.mutex.withLock {
        store.learningOfferings[offering.id] = offering
        store.touch()
        offering
    }

    override suspend fun search(
        criteria: LearningSearchCriteria,
        cursor: String?,
    ): Page<LearningOffering> = store.mutex.withLock {
        val items = store.learningOfferings.values.asSequence()
            .filter { it.deletedAt == null }
            .filter { !criteria.openPlacesOnly || it.status == ListingStatus.OPEN }
            .filter { criteria.subjects.isEmpty() || it.subject in criteria.subjects }
            .filter { criteria.levels.isEmpty() || it.level in criteria.levels }
            .filter { criteria.languageTag == null || it.language.tag == criteria.languageTag }
            .filter { criteria.format == null || it.format == criteria.format }
            .filter { criteria.city == null || it.place?.approximate?.city == criteria.city }
            .filter {
                !criteria.freeOnly || it.cost is org.fisabilillah.core.model.LearningCost.Free
            }
            .filter {
                criteria.organizationId == null || it.organizationId == criteria.organizationId
            }
            .sortedBy { it.title }
            .take(criteria.limit)
            .toList()
        Page(items, null, items.size)
    }

    override suspend fun enrollmentsFor(offeringId: ListingId): List<LearningEnrollment> =
        store.mutex.withLock { store.enrollments.filter { it.offeringId == offeringId } }

    override suspend fun enrollmentsOf(studentId: UserId): List<LearningEnrollment> =
        store.mutex.withLock { store.enrollments.filter { it.studentId == studentId } }

    override suspend fun saveEnrollment(enrollment: LearningEnrollment): LearningEnrollment =
        store.mutex.withLock {
            val index = store.enrollments.indexOfFirst { it.id == enrollment.id }
            if (index >= 0) store.enrollments[index] = enrollment else store.enrollments += enrollment
            store.touch()
            enrollment
        }
}

public class InMemoryOpportunityRepository(private val store: InMemoryStore) :
    OpportunityRepository {

    override suspend fun find(id: ListingId): VolunteerOpportunity? =
        store.mutex.withLock { store.opportunities[id] }

    override suspend fun save(opportunity: VolunteerOpportunity): VolunteerOpportunity =
        store.mutex.withLock {
            store.opportunities[opportunity.id] = opportunity
            store.touch()
            opportunity
        }

    override suspend fun search(
        criteria: OpportunitySearchCriteria,
        cursor: String?,
    ): Page<VolunteerOpportunity> = store.mutex.withLock {
        val from = criteria.from
        val until = criteria.until
        val items = store.opportunities.values.asSequence()
            .filter { it.deletedAt == null }
            .filter { !criteria.openOnly || it.status == ListingStatus.OPEN }
            .filter { criteria.categories.isEmpty() || it.category in criteria.categories }
            .filter { criteria.city == null || it.place.approximate.city == criteria.city }
            .filter { from == null || it.endsAt >= from }
            .filter { until == null || it.startsAt <= until }
            .filter { criteria.format == null || it.format == criteria.format }
            .filter {
                criteria.organizationId == null || it.organizationId == criteria.organizationId
            }
            .filter {
                criteria.skillIds.isEmpty() || it.neededSkills.any { skill -> skill.id in criteria.skillIds }
            }
            .filter { !criteria.excludeBackgroundCheckRequired || !it.backgroundCheckRequired }
            .sortedBy { it.startsAt }
            .take(criteria.limit)
            .toList()
        Page(items, null, items.size)
    }

    override suspend fun applicationsFor(opportunityId: ListingId): List<VolunteerApplication> =
        store.mutex.withLock { store.applications.filter { it.opportunityId == opportunityId } }

    override suspend fun applicationsOf(volunteerId: UserId): List<VolunteerApplication> =
        store.mutex.withLock { store.applications.filter { it.volunteerId == volunteerId } }

    override suspend fun saveApplication(application: VolunteerApplication): VolunteerApplication =
        store.mutex.withLock {
            val index = store.applications.indexOfFirst { it.id == application.id }
            if (index >= 0) {
                store.applications[index] = application
            } else {
                store.applications += application
            }
            store.touch()
            application
        }
}

public class InMemoryServiceRequestRepository(private val store: InMemoryStore) :
    ServiceRequestRepository {

    override suspend fun find(id: RequestId): ServiceRequest? =
        store.mutex.withLock { store.serviceRequests[id] }

    override suspend fun save(request: ServiceRequest): ServiceRequest = store.mutex.withLock {
        store.serviceRequests[request.id] = request
        store.touch()
        request
    }

    override suspend fun search(
        criteria: RequestSearchCriteria,
        cursor: String?,
    ): Page<ServiceRequest> = store.mutex.withLock {
        val items = store.serviceRequests.values.asSequence()
            .filter { it.deletedAt == null }
            .filter { !criteria.openOnly || it.status == RequestStatus.OPEN }
            .filter { criteria.categories.isEmpty() || it.category in criteria.categories }
            .filter { criteria.city == null || it.place.approximate.city == criteria.city }
            .filter { criteria.urgency == null || it.urgency == criteria.urgency }
            .filter {
                criteria.organizationId == null ||
                    it.mediatingOrganizationId == criteria.organizationId
            }
            // Urgency first, then oldest first — a queue, not a feed.
            .sortedWith(compareByDescending<ServiceRequest> { it.urgency.rank }.thenBy { it.createdAt })
            .take(criteria.limit)
            .toList()
        Page(items, null, items.size)
    }

    override suspend fun responsesFor(requestId: RequestId): List<RequestResponse> =
        store.mutex.withLock { store.requestResponses.filter { it.requestId == requestId } }

    override suspend fun saveResponse(response: RequestResponse): RequestResponse =
        store.mutex.withLock {
            val index = store.requestResponses.indexOfFirst { it.id == response.id }
            if (index >= 0) {
                store.requestResponses[index] = response
            } else {
                store.requestResponses += response
            }
            store.touch()
            response
        }

    override suspend fun byRequester(userId: UserId): List<ServiceRequest> =
        store.mutex.withLock { store.serviceRequests.values.filter { it.requesterId == userId } }
}

public class InMemoryProjectRepository(private val store: InMemoryStore) : ProjectRepository {
    override suspend fun find(id: ProjectId): Project? = store.mutex.withLock { store.projects[id] }

    override suspend fun save(project: Project): Project = store.mutex.withLock {
        store.projects[project.id] = project
        store.touch()
        project
    }

    override suspend fun list(cursor: String?): Page<Project> = store.mutex.withLock {
        val items = store.projects.values.filter { it.deletedAt == null }
        Page(items, null, items.size)
    }

    override suspend fun members(id: ProjectId): List<ProjectMember> =
        store.mutex.withLock { store.projectMembers.filter { it.projectId == id } }

    override suspend fun saveMember(member: ProjectMember): Unit = store.mutex.withLock {
        store.projectMembers.removeAll { it.projectId == member.projectId && it.userId == member.userId }
        store.projectMembers += member
        store.touch()
    }

    override suspend fun tasks(id: ProjectId): List<ProjectTask> =
        store.mutex.withLock { store.projectTasks.values.filter { it.projectId == id } }

    override suspend fun saveTask(task: ProjectTask): ProjectTask = store.mutex.withLock {
        store.projectTasks[task.id] = task
        store.touch()
        task
    }

    override suspend fun findTask(id: TaskId): ProjectTask? =
        store.mutex.withLock { store.projectTasks[id] }

    override suspend fun projectsOf(userId: UserId): List<Project> = store.mutex.withLock {
        val ids = store.projectMembers.filter { it.userId == userId }.map { it.projectId }.toSet()
        store.projects.values.filter { it.id in ids || it.organizerId == userId }
    }
}

public class InMemoryCommitmentRepository(private val store: InMemoryStore) : CommitmentRepository {
    override suspend fun find(id: CommitmentId): Commitment? =
        store.mutex.withLock { store.commitments[id] }

    override suspend fun save(commitment: Commitment): Commitment = store.mutex.withLock {
        store.commitments[commitment.id] = commitment
        store.touch()
        commitment
    }

    override suspend fun forUser(userId: UserId): List<Commitment> =
        store.mutex.withLock { store.commitments.values.filter { it.userId == userId } }

    override suspend fun upcoming(userId: UserId, from: Timestamp): List<Commitment> =
        store.mutex.withLock {
            store.commitments.values
                .filter { it.userId == userId && it.endsAt >= from }
                .sortedBy { it.startsAt }
        }

    override fun observeForUser(userId: UserId): Flow<List<Commitment>> =
        store.revision.map { _ ->
            store.commitments.values.filter { it.userId == userId }.sortedBy { it.startsAt }
        }
}

public class InMemoryTrustRepository(private val store: InMemoryStore) : TrustRepository {
    override suspend fun recordFor(userId: UserId): TrustRecord =
        store.mutex.withLock { store.trustRecords[userId] ?: TrustRecord(userId) }

    override suspend fun save(record: TrustRecord): TrustRecord = store.mutex.withLock {
        store.trustRecords[record.userId] = record
        store.touch()
        record
    }

    override suspend fun impactFor(userId: UserId): PrivateImpactRecord = store.mutex.withLock {
        store.impactRecords[userId] ?: PrivateImpactRecord(userId, "This year")
    }

    override suspend fun saveImpact(record: PrivateImpactRecord): Unit = store.mutex.withLock {
        store.impactRecords[record.userId] = record
        store.touch()
    }

    override suspend fun saveEndorsement(endorsement: TaskEndorsement): Unit =
        store.mutex.withLock {
            store.endorsements += endorsement
            store.touch()
        }

    override suspend fun endorsementsFor(userId: UserId): List<TaskEndorsement> =
        store.mutex.withLock { store.endorsements.filter { it.aboutUserId == userId } }
}

public class InMemoryModerationRepository(private val store: InMemoryStore) : ModerationRepository {
    override suspend fun saveReport(report: Report): Report = store.mutex.withLock {
        store.reports[report.id] = report
        store.touch()
        report
    }

    override suspend fun findReport(id: ReportId): Report? =
        store.mutex.withLock { store.reports[id] }

    override suspend fun reportsBy(reporterId: UserId, since: Timestamp): List<Report> =
        store.mutex.withLock {
            store.reports.values.filter { it.reporterId == reporterId && it.createdAt >= since }
        }

    override suspend fun reportsAbout(userId: UserId): List<Report> = store.mutex.withLock {
        store.reports.values.filter {
            (it.target as? org.fisabilillah.core.model.ReportTarget.User)?.id == userId
        }
    }

    override suspend fun openReports(cursor: String?): Page<Report> = store.mutex.withLock {
        val items = store.reports.values.filter {
            it.state == org.fisabilillah.core.model.ReportState.RECEIVED ||
                it.state == org.fisabilillah.core.model.ReportState.TRIAGED
        }
        Page(items, null, items.size)
    }

    override suspend fun saveCase(case: ModerationCase): ModerationCase = store.mutex.withLock {
        store.cases[case.id] = case
        store.touch()
        case
    }

    override suspend fun findCase(id: ModerationCaseId): ModerationCase? =
        store.mutex.withLock { store.cases[id] }

    override suspend fun openCases(cursor: String?): Page<ModerationCase> = store.mutex.withLock {
        val items = store.cases.values.filter {
            it.state != org.fisabilillah.core.model.CaseState.CLOSED
        }
        Page(items, null, items.size)
    }

    /**
     * Append only. There is no update or delete overload, and adding one would be a
     * reviewable change to a file whose whole purpose is that moderators cannot rewrite
     * what they did.
     */
    override suspend fun appendAction(action: ModerationAction): ModerationAction =
        store.mutex.withLock {
            store.moderationActions += action
            store.touch()
            action
        }

    override suspend fun actionsFor(caseId: ModerationCaseId): List<ModerationAction> =
        store.mutex.withLock { store.moderationActions.filter { it.caseId == caseId } }

    override suspend fun saveAppeal(appeal: Appeal): Appeal = store.mutex.withLock {
        store.appeals[appeal.id] = appeal
        store.touch()
        appeal
    }

    override suspend fun findAppeal(id: AppealId): Appeal? = store.mutex.withLock { store.appeals[id] }

    override suspend fun appealsFor(caseId: ModerationCaseId): List<Appeal> =
        store.mutex.withLock { store.appeals.values.filter { it.caseId == caseId } }

    override suspend fun openAppeals(): List<Appeal> = store.mutex.withLock {
        store.appeals.values.filter {
            it.state == org.fisabilillah.core.model.AppealState.SUBMITTED ||
                it.state == org.fisabilillah.core.model.AppealState.UNDER_REVIEW
        }
    }

    override suspend fun saveIncident(incident: SafetyIncident): SafetyIncident =
        store.mutex.withLock {
            store.incidents += incident
            store.touch()
            incident
        }

    override suspend fun recordSignals(signals: List<RecordedSignal>): Unit =
        store.mutex.withLock {
            store.safetySignals += signals
            store.touch()
        }

    override suspend fun signalsBy(senderId: UserId, since: Timestamp): List<RecordedSignal> =
        store.mutex.withLock {
            store.safetySignals.filter { it.senderId == senderId && it.observedAt >= since }
        }

    override suspend fun attachSignalsToCase(
        ids: List<SafetySignalId>,
        caseId: ModerationCaseId,
    ): Unit = store.mutex.withLock {
        val wanted = ids.toSet()
        for (index in store.safetySignals.indices) {
            val signal = store.safetySignals[index]
            // Never re-point a signal that already belongs to a case. A signal is evidence
            // for the case that first rested on it, and moving it would quietly hollow out
            // an earlier decision.
            if (signal.id in wanted && signal.caseId == null) {
                store.safetySignals[index] = signal.copy(caseId = caseId)
            }
        }
        store.touch()
    }

    override suspend fun signalsForCase(caseId: ModerationCaseId): List<RecordedSignal> =
        store.mutex.withLock { store.safetySignals.filter { it.caseId == caseId } }
}

/** Append-only, as the interface requires. No update, no delete, not even for administrators. */
public class InMemoryAuditLogRepository(private val store: InMemoryStore) : AuditLogRepository {
    override suspend fun append(entry: AuditLogEntry): Unit = store.mutex.withLock {
        store.auditLog += entry
        store.touch()
    }

    override suspend fun forSubject(subjectType: String, subjectId: String): List<AuditLogEntry> =
        store.mutex.withLock {
            store.auditLog.filter { it.subjectType == subjectType && it.subjectId == subjectId }
        }

    override suspend fun byActor(actorId: UserId): List<AuditLogEntry> =
        store.mutex.withLock { store.auditLog.filter { it.actorId == actorId } }

    override suspend fun recent(limit: Int): List<AuditLogEntry> =
        store.mutex.withLock { store.auditLog.takeLast(limit).reversed() }
}

public class InMemoryNotificationRepository(private val store: InMemoryStore) :
    NotificationRepository {

    override suspend fun add(notification: Notification): Notification = store.mutex.withLock {
        store.notifications += notification
        store.touch()
        notification
    }

    override suspend fun forUser(userId: UserId): List<Notification> = store.mutex.withLock {
        store.notifications.filter { it.userId == userId }.sortedByDescending { it.createdAt }
    }

    override suspend fun markRead(id: NotificationId, at: Timestamp): Unit = store.mutex.withLock {
        val index = store.notifications.indexOfFirst { it.id == id }
        if (index >= 0) {
            store.notifications[index] = store.notifications[index].copy(readAt = at)
            store.touch()
        }
    }

    override fun observeForUser(userId: UserId): Flow<List<Notification>> =
        store.revision.map { _ ->
            store.notifications.filter { it.userId == userId }.sortedByDescending { it.createdAt }
        }

    override suspend fun unreadCount(userId: UserId): Int =
        store.mutex.withLock { store.notifications.count { it.userId == userId && !it.isRead } }
}

public class InMemoryConsentRepository(private val store: InMemoryStore) : ConsentRepository {
    override suspend fun record(consent: ConsentRecord): ConsentRecord = store.mutex.withLock {
        store.consents += consent
        store.touch()
        consent
    }

    override suspend fun forUser(userId: UserId): List<ConsentRecord> =
        store.mutex.withLock { store.consents.filter { it.userId == userId } }

    override suspend fun hasCurrentConsent(userId: UserId, kind: ConsentKind): Boolean =
        store.mutex.withLock {
            store.consents.any { it.userId == userId && it.kind == kind && it.isCurrent }
        }
}

public class InMemoryVerificationRepository(private val store: InMemoryStore) :
    VerificationRepository {

    override suspend fun find(id: VerificationRequestId): VerificationRequest? =
        store.mutex.withLock { store.verificationRequests[id] }

    override suspend fun forUser(userId: UserId): List<VerificationRequest> =
        store.mutex.withLock { store.verificationRequests.values.filter { it.userId == userId } }

    override suspend fun save(request: VerificationRequest): VerificationRequest =
        store.mutex.withLock {
            store.verificationRequests[request.id] = request
            store.touch()
            request
        }

    override suspend fun openRequests(): List<VerificationRequest> =
        store.mutex.withLock { store.verificationRequests.values.filter { it.isOpen } }
}

public class InMemoryQualificationRepository(private val store: InMemoryStore) :
    QualificationRepository {

    override suspend fun find(id: QualificationId): Qualification? =
        store.mutex.withLock { store.qualifications.firstOrNull { it.id == id } }

    override suspend fun forUser(userId: UserId): List<Qualification> =
        store.mutex.withLock { store.qualifications.filter { it.userId == userId } }

    override suspend fun save(qualification: Qualification): Qualification = store.mutex.withLock {
        val index = store.qualifications.indexOfFirst { it.id == qualification.id }
        if (index >= 0) {
            store.qualifications[index] = qualification
        } else {
            store.qualifications += qualification
        }
        store.touch()
        qualification
    }

    override suspend fun pendingReview(): List<Qualification> = store.mutex.withLock {
        store.qualifications.filter {
            it.reviewState == QualificationReviewState.SUBMITTED ||
                it.reviewState == QualificationReviewState.UNDER_REVIEW
        }
    }
}

public class InMemoryCampaignRepository(private val store: InMemoryStore) : CampaignRepository {
    override suspend fun find(id: CampaignId): Campaign? =
        store.mutex.withLock { store.campaigns[id] }

    override suspend fun save(campaign: Campaign): Campaign = store.mutex.withLock {
        store.campaigns[campaign.id] = campaign
        store.touch()
        campaign
    }

    override suspend fun list(cursor: String?): Page<Campaign> = store.mutex.withLock {
        val items = store.campaigns.values.filter { it.deletedAt == null }
        Page(items, null, items.size)
    }

    override suspend fun forOrganization(id: OrganizationId): List<Campaign> =
        store.mutex.withLock { store.campaigns.values.filter { it.organizationId == id } }
}

public class InMemorySkillRepository(private val store: InMemoryStore) : SkillRepository {
    override suspend fun all(): List<Skill> =
        store.mutex.withLock { store.skills.values.filter { !it.retired } }

    override suspend fun find(id: SkillId): Skill? = store.mutex.withLock { store.skills[id] }
}

public class InMemoryDeviceSessionRepository(private val store: InMemoryStore) :
    DeviceSessionRepository {

    override suspend fun find(id: String): DeviceSession? =
        store.mutex.withLock { store.deviceSessions[id] }

    override suspend fun forUser(userId: UserId): List<DeviceSession> =
        store.mutex.withLock { store.deviceSessions.values.filter { it.userId == userId } }

    override suspend fun save(session: DeviceSession): Unit = store.mutex.withLock {
        store.deviceSessions[session.id] = session
        store.touch()
    }
}
