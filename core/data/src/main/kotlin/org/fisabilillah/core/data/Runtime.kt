package org.fisabilillah.core.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.fisabilillah.core.domain.AddOversightUseCase
import org.fisabilillah.core.domain.AppClock
import org.fisabilillah.core.domain.ApplyToOpportunityUseCase
import org.fisabilillah.core.domain.BlockUserUseCase
import org.fisabilillah.core.domain.BrowseRequestsUseCase
import org.fisabilillah.core.domain.CommitmentUseCase
import org.fisabilillah.core.domain.CompleteOnboardingUseCase
import org.fisabilillah.core.domain.ContactContextAssembler
import org.fisabilillah.core.domain.ConversationSpace
import org.fisabilillah.core.domain.CreateOpportunityUseCase
import org.fisabilillah.core.domain.DecideIntroductionUseCase
import org.fisabilillah.core.domain.DiscloseExactLocationUseCase
import org.fisabilillah.core.domain.EndConversationUseCase
import org.fisabilillah.core.domain.EnrollInLearningUseCase
import org.fisabilillah.core.domain.GetEffectiveSafeguardsUseCase
import org.fisabilillah.core.domain.HomeDigestUseCase
import org.fisabilillah.core.domain.IdGenerator
import org.fisabilillah.core.domain.LapseIntroductionsUseCase
import org.fisabilillah.core.domain.ListConversationsUseCase
import org.fisabilillah.core.domain.ManageTrustedContactsUseCase
import org.fisabilillah.core.domain.ModerationQueueUseCase
import org.fisabilillah.core.domain.OpenIntroductionConversationUseCase
import org.fisabilillah.core.domain.OversightDirectory
import org.fisabilillah.core.domain.PrivateImpactUseCase
import org.fisabilillah.core.domain.RespondToRequestUseCase
import org.fisabilillah.core.domain.ReviewAppealUseCase
import org.fisabilillah.core.domain.SearchPeopleUseCase
import org.fisabilillah.core.domain.SendMessageUseCase
import org.fisabilillah.core.domain.StartConversationUseCase
import org.fisabilillah.core.domain.SpaceResolver
import org.fisabilillah.core.domain.SubjectTitleResolver
import org.fisabilillah.core.domain.SubmitIntroductionUseCase
import org.fisabilillah.core.domain.SubmitReportUseCase
import org.fisabilillah.core.domain.TakeModerationActionUseCase
import org.fisabilillah.core.domain.UnsendMessageUseCase
import org.fisabilillah.core.domain.UpdateIntroductionSettingsUseCase
import org.fisabilillah.core.domain.UpdateSafeguardsUseCase
import org.fisabilillah.core.domain.ViewIntroductionGuardianUseCase
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.CommunityMemberRole
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.MembershipStatus
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import java.util.concurrent.atomic.AtomicLong

/** The real clock. */
public class SystemClock(private val timeZone: TimeZone = TimeZone.UTC) : AppClock {
    override fun now(): Timestamp = Clock.System.now()

    override fun currentYear(): Int = now().toLocalDateTime(timeZone).year

    override fun localTimeIn(timeZoneId: String): LocalTime {
        val zone = runCatching { TimeZone.of(timeZoneId) }.getOrDefault(TimeZone.UTC)
        return now().toLocalDateTime(zone).time
    }
}

/**
 * A clock a test can move.
 *
 * Every rule in this codebase that depends on time — quiet hours, rate limit windows, the
 * unsend window, introduction lapse, restriction expiry — is testable because of this.
 */
public class FixedClock(
    private var instant: Timestamp,
    private val year: Int = 2026,
    private var localTime: LocalTime = LocalTime(12, 0),
) : AppClock {
    override fun now(): Timestamp = instant
    override fun currentYear(): Int = year
    override fun localTimeIn(timeZoneId: String): LocalTime = localTime

    public fun advance(duration: kotlin.time.Duration) {
        instant += duration
    }

    public fun setLocalTime(time: LocalTime) {
        localTime = time
    }

    public fun setNow(value: Timestamp) {
        instant = value
    }
}

/** Sequential identifiers. Readable in logs and stable across a test run. */
public class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private val counter = AtomicLong(0)
    override fun newId(): String = "$prefix-${counter.incrementAndGet()}"
}

/** Random identifiers for real use. */
public class UuidIdGenerator : IdGenerator {
    override fun newId(): String = java.util.UUID.randomUUID().toString()
}

/** Reads the title of whatever a conversation's purpose points at. */
public class StoreSubjectTitleResolver(private val store: InMemoryStore) : SubjectTitleResolver {
    override suspend fun titleFor(purpose: ContactPurpose): String =
        when (val subject = purpose.subject) {
            null -> purpose.kind.displayName
            is PurposeSubject.Opportunity ->
                store.opportunities[subject.id]?.title ?: purpose.kind.displayName
            is PurposeSubject.LearningOffer ->
                store.learningOfferings[subject.id]?.title ?: purpose.kind.displayName
            is PurposeSubject.Request ->
                store.serviceRequests[subject.id]?.title ?: purpose.kind.displayName
            is PurposeSubject.Project ->
                store.projects[subject.id]?.title ?: purpose.kind.displayName
            is PurposeSubject.Organization ->
                store.organizations[subject.id]?.name ?: purpose.kind.displayName
            is PurposeSubject.Community ->
                store.communities[subject.id]?.name ?: purpose.kind.displayName
        }
}

/** Works out which masjid, charity, or community space a conversation belongs to. */
public class StoreSpaceResolver(private val store: InMemoryStore) : SpaceResolver {
    override suspend fun spaceOf(subject: PurposeSubject?): ConversationSpace = when (subject) {
        null -> ConversationSpace.NONE

        is PurposeSubject.Opportunity -> store.opportunities[subject.id]
            ?.let { ConversationSpace(it.organizationId, it.communityId) }
            ?: ConversationSpace.NONE

        is PurposeSubject.LearningOffer -> store.learningOfferings[subject.id]
            ?.let { ConversationSpace(it.organizationId, it.communityId) }
            ?: ConversationSpace.NONE

        is PurposeSubject.Project -> store.projects[subject.id]
            ?.let { ConversationSpace(it.organizationId, it.communityId) }
            ?: ConversationSpace.NONE

        is PurposeSubject.Request -> store.serviceRequests[subject.id]
            ?.let { ConversationSpace(organizationId = it.mediatingOrganizationId) }
            ?: ConversationSpace.NONE

        is PurposeSubject.Organization -> ConversationSpace(organizationId = subject.id)

        is PurposeSubject.Community -> store.communities[subject.id]
            ?.let { ConversationSpace(it.organizationId, it.id) }
            ?: ConversationSpace(communityId = subject.id)
    }
}

/** Finds someone able to sit in on a conversation when a safeguard calls for it. */
public class StoreOversightDirectory(private val store: InMemoryStore) : OversightDirectory {

    override suspend fun availableModerator(): UserId? =
        store.profiles.values.firstOrNull { profile ->
            profile.isActive && profile.roles.any { it == AccountRole.MODERATOR }
        }?.id

    override suspend fun communityModerator(communityId: CommunityId): UserId? {
        val community = store.communities[communityId] ?: return availableModerator()
        val fromMembers = store.communityMembers.firstOrNull {
            it.communityId == communityId &&
                it.status == MembershipStatus.ACTIVE &&
                it.role == CommunityMemberRole.MODERATOR
        }?.userId
        return fromMembers ?: community.moderatorIds.firstOrNull() ?: availableModerator()
    }

    override suspend fun organizationRepresentative(organizationId: OrganizationId): UserId? =
        store.organizationMembers.firstOrNull {
            it.organizationId == organizationId &&
                it.isActive &&
                it.role.canPublishListings
        }?.userId
}

/**
 * The composition root for the shared core.
 *
 * Hand-wired rather than annotation-driven. With this many collaborators a dependency
 * graph you can read top to bottom is worth more than the few lines a framework would
 * save, and it keeps the core free of any injection library — which matters when the same
 * code has to be consumable from an Android application, a test, and eventually an iOS
 * client.
 */
public class CoreGraph(
    public val store: InMemoryStore = InMemoryStore(),
    public val clock: AppClock = SystemClock(),
    public val ids: IdGenerator = UuidIdGenerator(),
) {
    public val profiles: InMemoryProfileRepository = InMemoryProfileRepository(store)
    public val safeguards: InMemorySafeguardRepository = InMemorySafeguardRepository(store)
    public val blocks: InMemoryBlockRepository = InMemoryBlockRepository(store)
    public val restrictions: InMemoryRestrictionRepository = InMemoryRestrictionRepository(store)
    public val conversations: InMemoryConversationRepository = InMemoryConversationRepository(store)
    public val messages: InMemoryMessageRepository = InMemoryMessageRepository(store)
    public val trustedContacts: InMemoryTrustedContactRepository =
        InMemoryTrustedContactRepository(store)
    public val introductions: InMemoryIntroductionRepository = InMemoryIntroductionRepository(store)
    public val organizations: InMemoryOrganizationRepository = InMemoryOrganizationRepository(store)
    public val communities: InMemoryCommunityRepository = InMemoryCommunityRepository(store)
    public val learning: InMemoryLearningRepository = InMemoryLearningRepository(store)
    public val opportunities: InMemoryOpportunityRepository = InMemoryOpportunityRepository(store)
    public val requests: InMemoryServiceRequestRepository = InMemoryServiceRequestRepository(store)
    public val projects: InMemoryProjectRepository = InMemoryProjectRepository(store)
    public val commitments: InMemoryCommitmentRepository = InMemoryCommitmentRepository(store)
    public val trust: InMemoryTrustRepository = InMemoryTrustRepository(store)
    public val moderation: InMemoryModerationRepository = InMemoryModerationRepository(store)
    public val auditLog: InMemoryAuditLogRepository = InMemoryAuditLogRepository(store)
    public val notifications: InMemoryNotificationRepository = InMemoryNotificationRepository(store)
    public val consents: InMemoryConsentRepository = InMemoryConsentRepository(store)
    public val qualifications: InMemoryQualificationRepository =
        InMemoryQualificationRepository(store)
    public val campaigns: InMemoryCampaignRepository = InMemoryCampaignRepository(store)
    public val skills: InMemorySkillRepository = InMemorySkillRepository(store)

    public val oversight: OversightDirectory = StoreOversightDirectory(store)
    public val subjectTitles: SubjectTitleResolver = StoreSubjectTitleResolver(store)
    public val spaces: SpaceResolver = StoreSpaceResolver(store)

    public val assembler: ContactContextAssembler = ContactContextAssembler(
        profiles = profiles,
        safeguards = safeguards,
        blocks = blocks,
        restrictions = restrictions,
        organizations = organizations,
        communities = communities,
        trustedContacts = trustedContacts,
        conversations = conversations,
        spaces = spaces,
        clock = clock,
    )

    // ── Use cases ──────────────────────────────────────────────────────────────
    public val startConversation: StartConversationUseCase = StartConversationUseCase(
        profiles, conversations, messages, trustedContacts, notifications, auditLog,
        assembler, oversight, subjectTitles, ids, clock,
    )
    public val sendMessage: SendMessageUseCase = SendMessageUseCase(
        profiles, conversations, messages, blocks, restrictions, notifications, moderation,
        ids, clock,
    )
    public val unsendMessage: UnsendMessageUseCase =
        UnsendMessageUseCase(messages, auditLog, ids, clock)
    public val blockUser: BlockUserUseCase =
        BlockUserUseCase(blocks, conversations, introductions, auditLog, ids, clock)
    public val endConversation: EndConversationUseCase =
        EndConversationUseCase(conversations, auditLog, ids, clock)
    public val addOversight: AddOversightUseCase = AddOversightUseCase(
        conversations, messages, auditLog, assembler, oversight, ids, clock,
    )
    public val listConversations: ListConversationsUseCase = ListConversationsUseCase(conversations)

    public val submitIntroduction: SubmitIntroductionUseCase = SubmitIntroductionUseCase(
        profiles, introductions, trustedContacts, blocks, restrictions, notifications,
        auditLog, assembler, ids, clock,
    )
    public val decideIntroduction: DecideIntroductionUseCase = DecideIntroductionUseCase(
        introductions, trustedContacts, blocks, notifications, auditLog, ids, clock,
    )
    public val openIntroductionConversation: OpenIntroductionConversationUseCase =
        OpenIntroductionConversationUseCase(
            introductions, trustedContacts, conversations, messages, notifications, auditLog,
            ids, clock,
        )
    public val viewIntroductionGuardian: ViewIntroductionGuardianUseCase =
        ViewIntroductionGuardianUseCase(introductions, trustedContacts, auditLog, ids, clock)
    public val lapseIntroductions: LapseIntroductionsUseCase =
        LapseIntroductionsUseCase(introductions, notifications, ids, clock)
    public val updateIntroductionSettings: UpdateIntroductionSettingsUseCase =
        UpdateIntroductionSettingsUseCase(introductions, trustedContacts, auditLog, ids, clock)

    public val updateSafeguards: UpdateSafeguardsUseCase =
        UpdateSafeguardsUseCase(safeguards, auditLog, assembler, ids, clock)
    public val effectiveSafeguards: GetEffectiveSafeguardsUseCase =
        GetEffectiveSafeguardsUseCase(assembler)
    public val manageTrustedContacts: ManageTrustedContactsUseCase =
        ManageTrustedContactsUseCase(trustedContacts, auditLog, ids, clock)
    public val completeOnboarding: CompleteOnboardingUseCase =
        CompleteOnboardingUseCase(profiles, safeguards, consents, auditLog, ids, clock)

    public val submitReport: SubmitReportUseCase = SubmitReportUseCase(
        moderation, conversations, messages, restrictions, auditLog, ids, clock,
    )
    public val takeModerationAction: TakeModerationActionUseCase = TakeModerationActionUseCase(
        moderation, profiles, restrictions, conversations, notifications, auditLog, ids, clock,
    )
    public val reviewAppeal: ReviewAppealUseCase =
        ReviewAppealUseCase(moderation, restrictions, notifications, auditLog, ids, clock)
    public val moderationQueue: ModerationQueueUseCase = ModerationQueueUseCase(moderation)

    public val homeDigest: HomeDigestUseCase = HomeDigestUseCase(
        profiles, commitments, opportunities, requests, learning, projects, notifications,
        conversations, trust, clock,
    )
    public val searchPeople: SearchPeopleUseCase =
        SearchPeopleUseCase(profiles, safeguards, blocks, trust, assembler)
    public val browseRequests: BrowseRequestsUseCase =
        BrowseRequestsUseCase(requests, profiles, organizations)

    public val applyToOpportunity: ApplyToOpportunityUseCase = ApplyToOpportunityUseCase(
        opportunities, profiles, restrictions, notifications, ids, clock,
    )
    public val enrollInLearning: EnrollInLearningUseCase =
        EnrollInLearningUseCase(learning, profiles, notifications, ids, clock)
    public val respondToRequest: RespondToRequestUseCase =
        RespondToRequestUseCase(requests, profiles, notifications, ids, clock)
    public val discloseExactLocation: DiscloseExactLocationUseCase =
        DiscloseExactLocationUseCase(requests, auditLog, notifications, ids, clock)
    public val commitmentActions: CommitmentUseCase =
        CommitmentUseCase(commitments, trust, opportunities, ids, clock)
    public val privateImpact: PrivateImpactUseCase = PrivateImpactUseCase(trust)
    public val createOpportunity: CreateOpportunityUseCase = CreateOpportunityUseCase(
        opportunities, clock,
    )
}
