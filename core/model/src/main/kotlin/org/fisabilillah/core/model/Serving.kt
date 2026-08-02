package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * The kinds of help people give one another here.
 *
 * Held as an enum rather than a lookup table because the categories drive matching,
 * safeguard defaults and child-safety rules, and adding one is a product decision rather
 * than a piece of routine administration.
 */
@Serializable
public enum class ServiceCategory(
    public val displayName: String,
    public val group: ServiceGroup,
    /** True where the work routinely brings a volunteer into contact with minors. */
    public val involvesMinors: Boolean = false,
    /** True where the work routinely brings a volunteer into someone's home. */
    public val involvesHomeVisits: Boolean = false,
) {
    ARABIC_TUTORING("Arabic tutoring", ServiceGroup.TEACHING),
    QURAN_SUPPORT("Qur'an reading support", ServiceGroup.TEACHING),
    NEW_MUSLIM_SUPPORT("New Muslim support", ServiceGroup.TEACHING),
    YOUTH_MENTORSHIP("Youth mentorship", ServiceGroup.TEACHING, involvesMinors = true),
    BUSINESS_MENTORSHIP("Business mentorship", ServiceGroup.PROFESSIONAL),
    TRADE_SKILLS_MENTORING("Trade skills mentoring", ServiceGroup.PROFESSIONAL),
    CAREER_GUIDANCE("Career guidance", ServiceGroup.PROFESSIONAL),
    RESUME_ASSISTANCE("CV and résumé help", ServiceGroup.PROFESSIONAL),
    JOB_SEARCH_HELP("Job search help", ServiceGroup.PROFESSIONAL),
    PROFESSIONAL_SERVICES("Professional services", ServiceGroup.PROFESSIONAL),
    TECHNOLOGY_SUPPORT("Technology support", ServiceGroup.PROFESSIONAL),
    TRANSLATION("Translation", ServiceGroup.PROFESSIONAL),

    ELDER_ASSISTANCE("Elder assistance", ServiceGroup.CARE, involvesHomeVisits = true),
    DISABILITY_SUPPORT("Disability support", ServiceGroup.CARE, involvesHomeVisits = true),
    CHILDCARE_ASSISTANCE("Childcare assistance", ServiceGroup.CARE, involvesMinors = true),
    MEAL_PREPARATION("Meal preparation", ServiceGroup.CARE),
    BEREAVEMENT_SUPPORT("Funeral and bereavement help", ServiceGroup.CARE),
    PRISON_REENTRY_SUPPORT("Prison re-entry support", ServiceGroup.CARE),

    TRANSPORTATION("Transportation", ServiceGroup.PRACTICAL),
    FOOD_DISTRIBUTION("Food distribution", ServiceGroup.PRACTICAL),
    CLOTHING_DISTRIBUTION("Clothing distribution", ServiceGroup.PRACTICAL),
    MASJID_MAINTENANCE("Masjid maintenance", ServiceGroup.PRACTICAL),
    COMMUNITY_CLEANUP("Community clean-up", ServiceGroup.PRACTICAL),
    ENVIRONMENTAL_PROJECT("Environmental project", ServiceGroup.PRACTICAL),
    EMERGENCY_RESPONSE("Emergency community response", ServiceGroup.PRACTICAL),
    DISASTER_RELIEF("Disaster relief", ServiceGroup.PRACTICAL),
    COMMUNITY_SERVICE_TRAINING("Community service training", ServiceGroup.PRACTICAL),
    ;

    public val requiresBackgroundCheckByDefault: Boolean
        get() = involvesMinors || involvesHomeVisits
}

@Serializable
public enum class ServiceGroup(public val displayName: String) {
    TEACHING("Teaching and mentoring"),
    PROFESSIONAL("Professional and technical"),
    CARE("Care and support"),
    PRACTICAL("Practical and logistics"),
}

/** Who benefits from a piece of work. Recorded so expectations are set honestly up front. */
@Serializable
public enum class BeneficiaryType(public val displayName: String) {
    INDIVIDUAL("An individual or family"),
    MASJID("A masjid"),
    ORGANIZATION("An organisation"),
    NEIGHBOURHOOD("A neighbourhood"),
    WIDER_PUBLIC("The wider public"),
}

/** Whether people meet in person, online, or both. */
@Serializable
public enum class DeliveryFormat(public val displayName: String) {
    IN_PERSON("In person"),
    ONLINE("Online"),
    HYBRID("In person and online"),
}

/** How genders are arranged for an activity. */
@Serializable
public enum class GenderArrangement(public val displayName: String) {
    BROTHERS_ONLY("Brothers only"),
    SISTERS_ONLY("Sisters only"),
    SEPARATE_SESSIONS("Separate sessions for brothers and sisters"),
    FAMILIES_WELCOME("Families welcome"),
    MIXED_WITH_SUPERVISION("Mixed, supervised by the organisers"),
    NOT_APPLICABLE("Not applicable"),
    ;

    public fun admits(gender: Gender): Boolean = when (this) {
        BROTHERS_ONLY -> gender == Gender.MALE
        SISTERS_ONLY -> gender == Gender.FEMALE
        else -> true
    }
}

/**
 * A chance to give time or skill.
 *
 * Everything an organiser must state up front is a required field, because the most
 * common way volunteering goes wrong is not malice — it is a volunteer arriving to find
 * the work was not what they were told.
 */
@Serializable
public data class VolunteerOpportunity(
    val id: ListingId,
    val title: String,
    val summary: String,
    val category: ServiceCategory,
    val organizerId: UserId,
    val organizationId: OrganizationId? = null,
    val communityId: CommunityId? = null,
    val beneficiaryType: BeneficiaryType,
    val place: Place,
    val format: DeliveryFormat,
    val startsAt: Timestamp,
    val endsAt: Timestamp,
    val neededSkills: List<Skill> = emptyList(),
    val volunteersNeeded: Int,
    val volunteersConfirmed: Int = 0,
    val physicalRequirements: String? = null,
    val safetyNotes: String? = null,
    val backgroundCheckRequired: Boolean = false,
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    val transportProvided: Boolean = false,
    val transportNotes: String? = null,
    val childSafeguardingRequired: Boolean = false,
    val expensesReimbursed: Boolean = false,
    val completionCriteria: String,
    val verificationStatus: ListingVerification = ListingVerification.UNVERIFIED,
    val status: ListingStatus = ListingStatus.OPEN,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(volunteersNeeded > 0) { "volunteersNeeded must be positive" }
        require(volunteersConfirmed >= 0) { "volunteersConfirmed cannot be negative" }
        require(endsAt >= startsAt) { "endsAt must not precede startsAt" }
        require(completionCriteria.isNotBlank()) {
            "completionCriteria must be stated so a volunteer knows when they are done"
        }
    }

    public val placesRemaining: Int get() = (volunteersNeeded - volunteersConfirmed).coerceAtLeast(0)
    public val isFull: Boolean get() = placesRemaining == 0

    /**
     * Work with minors or in someone's home must carry a background-check requirement.
     * Checked by the validator rather than in `init` so an organiser gets a helpful
     * message rather than an exception.
     */
    public val backgroundCheckIsMandatory: Boolean
        get() = category.requiresBackgroundCheckByDefault || childSafeguardingRequired
}

@Serializable
public enum class ListingStatus(public val displayName: String) {
    DRAFT("Draft"),
    PENDING_REVIEW("Awaiting review"),
    OPEN("Open"),
    FULL("Full"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    REMOVED_BY_MODERATION("Removed"),
    ;

    public val acceptsApplications: Boolean get() = this == OPEN
}

@Serializable
public enum class ListingVerification(
    public val displayName: String,
    public val explanation: String,
) {
    UNVERIFIED(
        "Not verified",
        "Posted by a member. Nothing about it has been checked by the platform.",
    ),
    ORGANIZER_IDENTITY_VERIFIED(
        "Organiser identity verified",
        "The organiser's identity was verified. The activity itself was not inspected.",
    ),
    ORGANIZATION_BACKED(
        "Organisation backed",
        "A verified organisation stands behind this. They, not the platform, are responsible " +
            "for how it runs.",
    ),
}

/** Someone offering to take part. */
@Serializable
public data class VolunteerApplication(
    val id: ApplicationId,
    val opportunityId: ListingId,
    val volunteerId: UserId,
    val message: String,
    val status: ApplicationStatus = ApplicationStatus.SUBMITTED,
    val decidedBy: UserId? = null,
    val decidedAt: Timestamp? = null,
    val declineReason: String? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class ApplicationStatus(public val displayName: String) {
    SUBMITTED("Submitted"),
    ACCEPTED("Accepted"),
    DECLINED("Not accepted"),
    WITHDRAWN("Withdrawn"),
    WAITLISTED("Waitlisted"),
}

/**
 * A promise someone made to be somewhere and do something.
 *
 * Commitments are the unit the platform actually cares about: not posts, not likes, but
 * whether a person turned up and finished what they said they would.
 */
@Serializable
public data class Commitment(
    val id: CommitmentId,
    val userId: UserId,
    val subject: CommitmentSubject,
    val title: String,
    val startsAt: Timestamp,
    val endsAt: Timestamp,
    val place: Place?,
    val status: CommitmentStatus = CommitmentStatus.SCHEDULED,
    val checkedInAt: Timestamp? = null,
    val checkedOutAt: Timestamp? = null,
    val organizerConfirmedAt: Timestamp? = null,
    val organizerConfirmedBy: UserId? = null,
    val punctuality: Punctuality? = null,
    val incidentReported: Boolean = false,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public sealed interface CommitmentSubject {
    @Serializable
    public data class Opportunity(val id: ListingId) : CommitmentSubject

    @Serializable
    public data class LearningSession(val id: ListingId) : CommitmentSubject

    @Serializable
    public data class ProjectTask(val id: TaskId) : CommitmentSubject

    @Serializable
    public data class RequestFulfilment(val id: RequestId) : CommitmentSubject
}

@Serializable
public enum class CommitmentStatus(public val displayName: String) {
    SCHEDULED("Scheduled"),
    CHECKED_IN("Checked in"),
    COMPLETED("Completed"),
    NO_SHOW("Did not attend"),
    CANCELLED_BY_VOLUNTEER("Cancelled by volunteer"),
    CANCELLED_BY_ORGANIZER("Cancelled by organiser"),
}

@Serializable
public enum class Punctuality(public val displayName: String) {
    ON_TIME("On time"),
    SLIGHTLY_LATE("Slightly late"),
    LATE("Late"),
    DID_NOT_ATTEND("Did not attend"),
}

/**
 * A person's own record of what they have done.
 *
 * Private by default and permanently private if the user wants it that way. Nothing here
 * is ever aggregated into a public ranking; the point is that a person can look back on
 * their own work, not that anyone else can measure them by it.
 */
@Serializable
public data class PrivateImpactRecord(
    val userId: UserId,
    val periodLabel: String,
    val commitmentsCompleted: Int = 0,
    val hoursGiven: Int = 0,
    val peopleHelped: Int = 0,
    val categories: Map<ServiceCategory, Int> = emptyMap(),
    val visibility: ImpactVisibility = ImpactVisibility.PRIVATE,
) {
    /**
     * A quiet reminder shown alongside the summary. The platform's own view is that a
     * record of service is between a person and their Lord.
     */
    public val sincerityNote: String =
        "This summary is private to you. It is not shown to anyone else and it is not " +
            "ranked against anyone."
}

@Serializable
public enum class ImpactVisibility(public val displayName: String) {
    PRIVATE("Only me"),

    /** The person chose to list some completed projects on their profile. No numbers. */
    SELECTED_PROJECTS_ON_PROFILE("Selected projects on my profile"),
}
