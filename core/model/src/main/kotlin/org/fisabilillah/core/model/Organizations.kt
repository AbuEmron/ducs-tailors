package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** A masjid, charity, school, or community body with an account on the platform. */
@Serializable
public data class Organization(
    val id: OrganizationId,
    val name: String,
    val kind: OrganizationKind,
    val summary: String,
    val place: Place,
    val websiteUrl: String? = null,
    val contactEmail: String? = null,
    val verification: OrganizationVerification = OrganizationVerification.unverified(),
    /** The floor this organisation imposes inside its own spaces. Stricter only. */
    val safeguardFloor: SafeguardFloor = SafeguardFloor.NONE,
    val status: OrganizationStatus = OrganizationStatus.PENDING_REVIEW,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {
    init {
        require(name.isNotBlank()) { "An organisation needs a name" }
    }

    public val canRaiseFunds: Boolean
        get() = verification.registrationChecked && status == OrganizationStatus.ACTIVE
}

@Serializable
public enum class OrganizationKind(public val displayName: String) {
    MASJID("Masjid"),
    CHARITY("Charity or nonprofit"),
    SCHOOL("School or institute"),
    COMMUNITY_GROUP("Community group"),
    PROFESSIONAL_BODY("Professional body"),
}

@Serializable
public enum class OrganizationStatus(public val displayName: String) {
    PENDING_REVIEW("Awaiting review"),
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    CLOSED("Closed"),
}

/**
 * What has actually been checked about an organisation.
 *
 * Note what is deliberately absent: there is no field asserting that an organisation is
 * trustworthy, spends money well, or is religiously sound. The platform checks documents.
 * It does not audit, and it must never imply that it does.
 */
@Serializable
public data class OrganizationVerification(
    val registrationChecked: Boolean = false,
    val registrationNumber: String? = null,
    val registrationJurisdiction: String? = null,
    val checkedAt: Timestamp? = null,
    val checkedBy: UserId? = null,
    val documentRefs: List<String> = emptyList(),
    val expiresAt: Timestamp? = null,
) {
    public val badge: Attestation? =
        if (registrationChecked) Attestation.ORGANIZATION_VERIFIED else null

    public companion object {
        public fun unverified(): OrganizationVerification = OrganizationVerification()
    }
}

@Serializable
public data class OrganizationMember(
    val organizationId: OrganizationId,
    val userId: UserId,
    val role: OrganizationRole,
    val title: String? = null,
    val addedBy: UserId? = null,
    val joinedAt: Timestamp = Instant.EPOCH,
    val leftAt: Timestamp? = null,
) {
    public val isActive: Boolean get() = leftAt == null
}

@Serializable
public enum class OrganizationRole(
    public val displayName: String,
    public val canManageMembers: Boolean,
    public val canPublishListings: Boolean,
    public val canSeePrivateRequestDetail: Boolean,
) {
    MEMBER("Member", false, false, false),
    COORDINATOR("Coordinator", false, true, false),
    ADMINISTRATOR("Administrator", true, true, true),
    ;
}

/**
 * A moderated space: a masjid's own area, a learning circle, a volunteer team.
 *
 * Communities are where most of the platform's conversation happens, precisely because
 * group context is safer than private context for almost everything.
 */
@Serializable
public data class Community(
    val id: CommunityId,
    val name: String,
    val kind: CommunityKind,
    val summary: String,
    val organizationId: OrganizationId? = null,
    val place: Place? = null,
    val membershipPolicy: MembershipPolicy = MembershipPolicy.APPROVAL_REQUIRED,
    val rules: List<CommunityRule> = emptyList(),
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    val safeguardFloor: SafeguardFloor = SafeguardFloor.NONE,
    val moderatorIds: Set<UserId> = emptySet(),
    val memberCount: Int = 0,
    val isArchived: Boolean = false,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class CommunityKind(public val displayName: String) {
    MASJID_SPACE("Masjid"),
    LOCAL_COMMUNITY("Local community"),
    LEARNING_CIRCLE("Learning circle"),
    VOLUNTEER_TEAM("Volunteer team"),
    PROFESSIONAL_GROUP("Professional group"),
    NEW_MUSLIM_SUPPORT("New Muslim support"),
    FAMILY_SUPPORT("Family support"),
    COMMUNITY_PROJECT("Community project"),
    CHARITABLE_ORGANIZATION("Charitable organisation"),
}

@Serializable
public enum class MembershipPolicy(public val displayName: String) {
    OPEN("Anyone may join"),
    APPROVAL_REQUIRED("Approval required"),
    INVITE_ONLY("Invitation only"),
    ORGANIZATION_MEMBERS_ONLY("Organisation members only"),
}

@Serializable
public data class CommunityRule(
    val order: Int,
    val title: String,
    val detail: String,
)

@Serializable
public data class CommunityMember(
    val communityId: CommunityId,
    val userId: UserId,
    val role: CommunityMemberRole = CommunityMemberRole.MEMBER,
    val status: MembershipStatus = MembershipStatus.PENDING,
    val joinedAt: Timestamp = Instant.EPOCH,
    val removedAt: Timestamp? = null,
    val removedBy: UserId? = null,
    val removalReason: String? = null,
)

@Serializable
public enum class CommunityMemberRole(public val displayName: String, public val canModerate: Boolean) {
    MEMBER("Member", false),
    ORGANIZER("Organiser", false),
    MODERATOR("Moderator", true),
}

@Serializable
public enum class MembershipStatus(public val displayName: String) {
    PENDING("Awaiting approval"),
    ACTIVE("Member"),
    DECLINED("Not approved"),
    LEFT("Left"),
    REMOVED("Removed"),
}

/** A piece of work several people are doing together. */
@Serializable
public data class Project(
    val id: ProjectId,
    val title: String,
    val summary: String,
    val category: ServiceCategory,
    val organizerId: UserId,
    val organizationId: OrganizationId? = null,
    val communityId: CommunityId? = null,
    val place: Place? = null,
    val format: DeliveryFormat = DeliveryFormat.HYBRID,
    val startsAt: Timestamp? = null,
    val targetCompletionAt: Timestamp? = null,
    val neededSkills: List<Skill> = emptyList(),
    val volunteersNeeded: Int = 0,
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    val status: ProjectStatus = ProjectStatus.PLANNING,
    val isPubliclyListed: Boolean = true,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class ProjectStatus(public val displayName: String) {
    PLANNING("Planning"),
    RECRUITING("Looking for volunteers"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
}

@Serializable
public data class ProjectMember(
    val projectId: ProjectId,
    val userId: UserId,
    val role: ProjectRole = ProjectRole.CONTRIBUTOR,
    val joinedAt: Timestamp = Instant.EPOCH,
    val leftAt: Timestamp? = null,
)

@Serializable
public enum class ProjectRole(public val displayName: String, public val canAssignTasks: Boolean) {
    CONTRIBUTOR("Contributor", false),
    COORDINATOR("Coordinator", true),
    LEAD("Lead", true),
}

@Serializable
public data class ProjectTask(
    val id: TaskId,
    val projectId: ProjectId,
    val title: String,
    val detail: String? = null,
    val assigneeId: UserId? = null,
    val dueAt: Timestamp? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val requiredSkills: List<Skill> = emptyList(),
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class TaskStatus(public val displayName: String) {
    OPEN("Open"),
    CLAIMED("Claimed"),
    IN_PROGRESS("In progress"),
    BLOCKED("Blocked"),
    DONE("Done"),
    CANCELLED("Cancelled"),
}
