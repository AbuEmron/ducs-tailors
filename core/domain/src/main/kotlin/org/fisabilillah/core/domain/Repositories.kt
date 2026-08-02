package org.fisabilillah.core.domain

import kotlinx.coroutines.flow.Flow
import org.fisabilillah.core.model.Appeal
import org.fisabilillah.core.model.RecordedSignal
import org.fisabilillah.core.model.SafetySignalId
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
import org.fisabilillah.core.model.ConsentRecord
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.IntroductionId
import org.fisabilillah.core.model.IntroductionParticipant
import org.fisabilillah.core.model.LearningEnrollment
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.MessageRedaction
import org.fisabilillah.core.model.ModerationAction
import org.fisabilillah.core.model.ModerationCase
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.Notification
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
import org.fisabilillah.core.model.Report
import org.fisabilillah.core.model.ReportId
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.RequestResponse
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.SafetyIncident
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.Skill
import org.fisabilillah.core.model.TaskId
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.TrustRecord
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactId
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VolunteerApplication
import org.fisabilillah.core.model.VolunteerOpportunity

/**
 * The ports the domain layer depends on.
 *
 * These are written so that a Supabase/PostgREST client, a Room database, or the in-memory
 * development fixture can all satisfy them without the use cases changing. Note that none
 * of them take a "current user" parameter for authorisation purposes: authorisation is
 * decided in the use case against a [Principal], and enforced again in the database by
 * row-level security. A repository is a store, not a gatekeeper.
 */

public interface ProfileRepository {
    public suspend fun find(id: UserId): Profile?
    public suspend fun findAll(ids: Collection<UserId>): List<Profile>

    /**
     * Every profile.
     *
     * Only two callers, and both are the platform acting on itself rather than a member
     * looking at other members: the staff list, and scheduled maintenance which has to
     * sweep everybody. Anything a *member* sees goes through [search], which filters by
     * what that member is entitled to see.
     */
    public suspend fun all(): List<Profile>
    public suspend fun save(profile: Profile): Profile
    public fun observe(id: UserId): Flow<Profile?>

    /**
     * Candidate matches for discovery. Filtering by the searcher's eligibility to *see*
     * each result happens in the use case, because it depends on safeguards this
     * repository has no business interpreting.
     */
    public suspend fun search(criteria: PeopleSearchCriteria, cursor: String? = null): Page<Profile>
}

public interface SafeguardRepository {
    public suspend fun forUser(id: UserId): UserSafeguards?
    public suspend fun save(safeguards: UserSafeguards): UserSafeguards
    public fun observe(id: UserId): Flow<UserSafeguards?>
}

public interface BlockRepository {
    public suspend fun isBlocked(blocker: UserId, blocked: UserId): Boolean
    public suspend fun blockedBy(userId: UserId): Set<UserId>
    public suspend fun blocking(userId: UserId): Set<UserId>
    public suspend fun add(block: Block)
    public suspend fun remove(blocker: UserId, blocked: UserId)
}

public interface RestrictionRepository {
    public suspend fun activeFor(userId: UserId, now: Timestamp): List<Restriction>
    public suspend fun add(restriction: Restriction): Restriction
    public suspend fun lift(id: org.fisabilillah.core.model.RestrictionId, by: UserId, at: Timestamp)
}

public interface ConversationRepository {
    public suspend fun find(id: ConversationId): Conversation?
    public suspend fun save(conversation: Conversation): Conversation
    public suspend fun forUser(userId: UserId, cursor: String? = null): Page<Conversation>
    public fun observeForUser(userId: UserId): Flow<List<Conversation>>

    /** Conversations [userId] opened at or after [since]. Used for rate limiting. */
    public suspend fun countStartedBy(userId: UserId, since: Timestamp): Int

    public suspend fun betweenUsers(a: UserId, b: UserId): List<Conversation>

    /**
     * How many times [initiator] has approached [recipient] and been turned away — a
     * conversation the recipient ended, or one that expired without a single reply.
     * Persisting after a refusal is the most common form of unwanted pursuit, so it is
     * counted rather than inferred from message history at read time.
     */
    public suspend fun countDeclinedApproaches(initiator: UserId, recipient: UserId): Int

    /**
     * Conversations whose auto-archive deadline has passed.
     *
     * The deadline has been written on every conversation since the first release and read
     * by nothing. A promise the interface makes to somebody who is not looking is still a
     * promise.
     */
    public suspend fun needingArchive(now: Timestamp): List<Conversation>
}

public interface MessageRepository {
    public suspend fun find(id: MessageId): Message?
    public suspend fun save(message: Message): Message
    public suspend fun forConversation(id: ConversationId, cursor: String? = null): Page<Message>
    public fun observeConversation(id: ConversationId): Flow<List<Message>>

    /** Writes the moderator-only preserved copy when a message is unsent. */
    public suspend fun preserveRedaction(redaction: MessageRedaction)
    public suspend fun redactionsFor(id: ConversationId): List<MessageRedaction>
}

public interface TrustedContactRepository {
    /** Every contact belonging to [ownerId], including private contact details. */
    public suspend fun forOwner(ownerId: UserId): List<TrustedContact>
    public suspend fun find(id: TrustedContactId): TrustedContact?
    public suspend fun save(contact: TrustedContact): TrustedContact
    public suspend fun delete(id: TrustedContactId)
}

public interface IntroductionRepository {
    public suspend fun settingsFor(userId: UserId): FormalIntroductionSettings?
    public suspend fun saveSettings(settings: FormalIntroductionSettings): FormalIntroductionSettings

    public suspend fun find(id: IntroductionId): FormalIntroductionRequest?
    public suspend fun save(request: FormalIntroductionRequest): FormalIntroductionRequest
    public suspend fun incomingFor(userId: UserId): List<FormalIntroductionRequest>
    public suspend fun outgoingFrom(userId: UserId): List<FormalIntroductionRequest>
    public suspend fun countFrom(senderId: UserId, since: Timestamp): Int
    public suspend fun countBetween(senderId: UserId, recipientId: UserId): Int

    public suspend fun participants(id: IntroductionId): List<IntroductionParticipant>
    public suspend fun addParticipant(participant: IntroductionParticipant)
}

public interface OrganizationRepository {
    public suspend fun find(id: OrganizationId): Organization?
    public suspend fun findAll(ids: Collection<OrganizationId>): List<Organization>
    public suspend fun save(organization: Organization): Organization
    public suspend fun members(id: OrganizationId): List<OrganizationMember>
    public suspend fun membershipsOf(userId: UserId): List<OrganizationMember>
    public suspend fun saveMember(member: OrganizationMember)
    public suspend fun search(query: String): List<Organization>
}

public interface CommunityRepository {
    public suspend fun find(id: CommunityId): Community?
    public suspend fun save(community: Community): Community
    public suspend fun members(id: CommunityId): List<CommunityMember>
    public suspend fun membershipsOf(userId: UserId): List<CommunityMember>
    public suspend fun saveMember(member: CommunityMember)
    public suspend fun list(cursor: String? = null): Page<Community>
}

public interface LearningRepository {
    public suspend fun find(id: ListingId): LearningOffering?
    public suspend fun save(offering: LearningOffering): LearningOffering
    public suspend fun search(criteria: LearningSearchCriteria, cursor: String? = null): Page<LearningOffering>
    public suspend fun enrollmentsFor(offeringId: ListingId): List<LearningEnrollment>
    public suspend fun enrollmentsOf(studentId: UserId): List<LearningEnrollment>
    public suspend fun saveEnrollment(enrollment: LearningEnrollment): LearningEnrollment
}

public interface OpportunityRepository {
    public suspend fun find(id: ListingId): VolunteerOpportunity?
    public suspend fun save(opportunity: VolunteerOpportunity): VolunteerOpportunity
    public suspend fun search(criteria: OpportunitySearchCriteria, cursor: String? = null): Page<VolunteerOpportunity>
    public suspend fun applicationsFor(opportunityId: ListingId): List<VolunteerApplication>
    public suspend fun applicationsOf(volunteerId: UserId): List<VolunteerApplication>
    public suspend fun saveApplication(application: VolunteerApplication): VolunteerApplication
}

public interface ServiceRequestRepository {
    public suspend fun find(id: RequestId): ServiceRequest?
    public suspend fun save(request: ServiceRequest): ServiceRequest
    public suspend fun search(criteria: RequestSearchCriteria, cursor: String? = null): Page<ServiceRequest>
    public suspend fun responsesFor(requestId: RequestId): List<RequestResponse>
    public suspend fun saveResponse(response: RequestResponse): RequestResponse
    public suspend fun byRequester(userId: UserId): List<ServiceRequest>
}

public interface ProjectRepository {
    public suspend fun find(id: ProjectId): Project?
    public suspend fun save(project: Project): Project
    public suspend fun list(cursor: String? = null): Page<Project>
    public suspend fun members(id: ProjectId): List<ProjectMember>
    public suspend fun saveMember(member: ProjectMember)
    public suspend fun tasks(id: ProjectId): List<ProjectTask>
    public suspend fun saveTask(task: ProjectTask): ProjectTask
    public suspend fun findTask(id: TaskId): ProjectTask?
    public suspend fun projectsOf(userId: UserId): List<Project>
}

public interface CommitmentRepository {
    public suspend fun find(id: CommitmentId): Commitment?
    public suspend fun save(commitment: Commitment): Commitment
    public suspend fun forUser(userId: UserId): List<Commitment>
    public suspend fun upcoming(userId: UserId, from: Timestamp): List<Commitment>
    public fun observeForUser(userId: UserId): Flow<List<Commitment>>
}

public interface TrustRepository {
    public suspend fun recordFor(userId: UserId): TrustRecord
    public suspend fun save(record: TrustRecord): TrustRecord
    public suspend fun impactFor(userId: UserId): PrivateImpactRecord
    public suspend fun saveImpact(record: PrivateImpactRecord)
}

public interface ModerationRepository {
    public suspend fun saveReport(report: Report): Report
    public suspend fun findReport(id: ReportId): Report?
    public suspend fun reportsBy(reporterId: UserId, since: Timestamp): List<Report>
    public suspend fun reportsAbout(userId: UserId): List<Report>
    public suspend fun openReports(cursor: String? = null): Page<Report>

    public suspend fun saveCase(case: ModerationCase): ModerationCase
    public suspend fun findCase(id: ModerationCaseId): ModerationCase?
    public suspend fun openCases(cursor: String? = null): Page<ModerationCase>

    /** Append-only. There is intentionally no update or delete for actions. */
    public suspend fun appendAction(action: ModerationAction): ModerationAction
    public suspend fun actionsFor(caseId: ModerationCaseId): List<ModerationAction>

    public suspend fun saveAppeal(appeal: Appeal): Appeal
    public suspend fun findAppeal(id: AppealId): Appeal?
    public suspend fun appealsFor(caseId: ModerationCaseId): List<Appeal>
    public suspend fun openAppeals(): List<Appeal>

    public suspend fun saveIncident(incident: SafetyIncident): SafetyIncident

    // ── Automated signals ──────────────────────────────────────────────────────
    // Stored so that a pattern can be seen across messages; see SignalEscalationPolicy.

    public suspend fun recordSignals(signals: List<RecordedSignal>)

    /**
     * Signals attributed to one sender since [since].
     *
     * Sender-scoped rather than conversation-scoped on purpose: the thing worth detecting
     * is one person doing the same thing to several people, and a query that can only see
     * inside a single thread is blind to exactly that.
     */
    public suspend fun signalsBy(senderId: UserId, since: Timestamp): List<RecordedSignal>

    /** Marks signals as having contributed to a case, so they are not counted twice. */
    public suspend fun attachSignalsToCase(ids: List<SafetySignalId>, caseId: ModerationCaseId)

    /** For the moderator's view of a case: what the automated checks actually saw. */
    public suspend fun signalsForCase(caseId: ModerationCaseId): List<RecordedSignal>
}

/** Append-only by contract. Implementations must not expose an update or delete path. */
public interface AuditLogRepository {
    public suspend fun append(entry: AuditLogEntry)
    public suspend fun forSubject(subjectType: String, subjectId: String): List<AuditLogEntry>
    public suspend fun byActor(actorId: UserId): List<AuditLogEntry>
    public suspend fun recent(limit: Int = 100): List<AuditLogEntry>
}

public interface NotificationRepository {
    public suspend fun add(notification: Notification): Notification
    public suspend fun forUser(userId: UserId): List<Notification>
    public suspend fun markRead(id: org.fisabilillah.core.model.NotificationId, at: Timestamp)
    public fun observeForUser(userId: UserId): Flow<List<Notification>>
    public suspend fun unreadCount(userId: UserId): Int
}

public interface ConsentRepository {
    public suspend fun record(consent: ConsentRecord): ConsentRecord
    public suspend fun forUser(userId: UserId): List<ConsentRecord>
    public suspend fun hasCurrentConsent(userId: UserId, kind: org.fisabilillah.core.model.ConsentKind): Boolean
}

public interface QualificationRepository {
    public suspend fun find(id: QualificationId): Qualification?
    public suspend fun forUser(userId: UserId): List<Qualification>
    public suspend fun save(qualification: Qualification): Qualification
    public suspend fun pendingReview(): List<Qualification>
}

/**
 * Verification requests.
 *
 * Deliberately not folded into [QualificationRepository]. A qualification is a claim about
 * what somebody can do and is reviewed by whoever knows the subject; a verification request
 * is a claim about who they are and is reviewed by the safety team. Sharing a table would
 * mean sharing a policy, and the two need different ones.
 */
public interface VerificationRepository {
    public suspend fun find(id: VerificationRequestId): VerificationRequest?
    public suspend fun forUser(userId: UserId): List<VerificationRequest>
    public suspend fun save(request: VerificationRequest): VerificationRequest
    public suspend fun openRequests(): List<VerificationRequest>
}

public interface CampaignRepository {
    public suspend fun find(id: CampaignId): Campaign?
    public suspend fun save(campaign: Campaign): Campaign
    public suspend fun list(cursor: String? = null): Page<Campaign>
    public suspend fun forOrganization(id: OrganizationId): List<Campaign>
}

public interface SkillRepository {
    public suspend fun all(): List<Skill>
    public suspend fun find(id: org.fisabilillah.core.model.SkillId): Skill?
}

// ── Search criteria ────────────────────────────────────────────────────────────
//
// Note what these do not contain: no sort-by-popularity, no sort-by-attractiveness, no
// "people like you", no response-rate ranking. Discovery here is a filter over stated
// skills, availability and verification, which is the only kind of matching a service
// platform actually needs.

public data class PeopleSearchCriteria(
    val skillQuery: String? = null,
    val categories: Set<org.fisabilillah.core.model.ServiceCategory> = emptySet(),
    val languages: Set<String> = emptySet(),
    val city: String? = null,
    val radiusKm: Int? = null,
    val minimumVerification: org.fisabilillah.core.model.VerificationLevel? = null,
    val teachingCapacities: Set<org.fisabilillah.core.model.TeachingCapacity> = emptySet(),
    val sameGenderOnly: Boolean = false,
    val organizationId: OrganizationId? = null,
    val availableOn: kotlinx.datetime.DayOfWeek? = null,
    val limit: Int = 25,
)

public data class LearningSearchCriteria(
    val subjects: Set<org.fisabilillah.core.model.LearningSubject> = emptySet(),
    val levels: Set<org.fisabilillah.core.model.LearningLevel> = emptySet(),
    val languageTag: String? = null,
    val format: org.fisabilillah.core.model.DeliveryFormat? = null,
    val city: String? = null,
    val freeOnly: Boolean = false,
    val sameGenderOnly: Boolean = false,
    val organizationId: OrganizationId? = null,
    val openPlacesOnly: Boolean = true,
    val limit: Int = 25,
)

public data class OpportunitySearchCriteria(
    val categories: Set<org.fisabilillah.core.model.ServiceCategory> = emptySet(),
    val city: String? = null,
    val radiusKm: Int? = null,
    val from: Timestamp? = null,
    val until: Timestamp? = null,
    val format: org.fisabilillah.core.model.DeliveryFormat? = null,
    val organizationId: OrganizationId? = null,
    val skillIds: Set<org.fisabilillah.core.model.SkillId> = emptySet(),
    val excludeBackgroundCheckRequired: Boolean = false,
    val openOnly: Boolean = true,
    val limit: Int = 25,
)

public data class RequestSearchCriteria(
    val categories: Set<org.fisabilillah.core.model.ServiceCategory> = emptySet(),
    val city: String? = null,
    val urgency: org.fisabilillah.core.model.RequestUrgency? = null,
    val organizationId: OrganizationId? = null,
    val openOnly: Boolean = true,
    val limit: Int = 25,
)
