package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A member of the community.
 *
 * The fields here are chosen to answer one question: *can this person help, or be helped,
 * with this particular thing?* There is no field for appearance, no follower count, no
 * popularity score, and no place to write about anything other than beneficial work.
 */
@Serializable
public data class Profile(
    val id: UserId,
    val displayName: String,
    val realName: String? = null,
    val gender: Gender,
    val dateOfBirthYear: Int,
    val imageStyle: ProfileImageStyle = ProfileImageStyle.INITIALS,
    val imageUrl: String? = null,
    val place: Place,
    val languages: List<Language> = listOf(Language.ENGLISH),
    val skills: List<UserSkill> = emptyList(),
    val areasWillingToHelp: Set<ServiceCategory> = emptySet(),
    val areasSeekingHelp: Set<ServiceCategory> = emptySet(),
    val availability: Availability = Availability(),
    val teachingCapacity: TeachingCapacity? = null,
    val verificationLevel: VerificationLevel = VerificationLevel.NONE,
    val attestations: Set<Attestation> = emptySet(),
    val roles: Set<AccountRole> = setOf(AccountRole.COMMUNITY_MEMBER),
    val organizationIds: Set<OrganizationId> = emptySet(),
    val communityIds: Set<CommunityId> = emptySet(),
    /** A short statement about what this person is here to contribute. */
    val contributionStatement: String? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    /**
     * Kept private. Exposed only as coarse public labels via
     * `TrustSignals` — never as a number, a rank, or a leaderboard position.
     */
    val completedCommitments: Int = 0,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(contributionStatement == null || contributionStatement.length <= 600) {
            "contributionStatement must be 600 characters or fewer"
        }
        require(completedCommitments >= 0) { "completedCommitments cannot be negative" }
    }

    /**
     * Whether this account may take part in anything at all. Deleted, suspended and
     * banned accounts fail here, which is what stops a banned account from continuing
     * an existing conversation.
     */
    public val isActive: Boolean
        get() = deletedAt == null && status == AccountStatus.ACTIVE

    /** Age in whole years during [currentYear], derived from the birth year only. */
    public fun ageAt(currentYear: Int): Int = currentYear - dateOfBirthYear

    public fun isAdultIn(currentYear: Int): Boolean = ageAt(currentYear) >= ADULT_AGE

    public companion object {
        /**
         * The MVP is built for adults. Youth participation is a later phase and requires
         * guardian consent, verified organisations, and group-only communication — see
         * `docs/child-safety.md`.
         */
        public const val ADULT_AGE: Int = 18
    }
}

@Serializable
public enum class AccountStatus(public val displayName: String) {
    /** Signed up, still completing onboarding. Cannot yet contact anyone. */
    PENDING_ONBOARDING("Setting up"),
    ACTIVE("Active"),

    /** A safety restriction is in force. Some capabilities are removed; see `Restriction`. */
    RESTRICTED("Restricted"),

    /** Temporarily suspended pending a moderation case. */
    SUSPENDED("Suspended"),

    /** Permanently removed. Records are retained for evidence but the account cannot act. */
    BANNED("Banned"),

    /** The user asked to be deleted. Content is removed on the schedule in the privacy policy. */
    DELETION_REQUESTED("Deletion requested"),
}

/**
 * The view of a [Profile] that another member is actually allowed to see.
 *
 * Building this is not the responsibility of the UI. `ProfileVisibilityPolicy` produces
 * it from the viewer's identity and the subject's safeguards, so there is exactly one
 * place in the codebase where the decision "may this person see that name" is made.
 */
@Serializable
public data class VisibleProfile(
    val id: UserId,
    val displayName: String,
    val realName: String?,
    val gender: Gender,
    val imageStyle: ProfileImageStyle,
    val imageUrl: String?,
    val locationLabel: String?,
    val languages: List<Language>,
    val skills: List<UserSkill>,
    val areasWillingToHelp: Set<ServiceCategory>,
    val teachingCapacity: TeachingCapacity?,
    val verificationLevel: VerificationLevel,
    val attestations: Set<Attestation>,
    val trustLabels: List<TrustLabel>,
    val contributionStatement: String?,
    val availability: Availability,
    /** What the viewer would have to satisfy in order to open a conversation. */
    val contactability: Contactability,
)

/** A short, honest summary of whether and how the viewer could reach this person. */
@Serializable
public data class Contactability(
    val canInitiate: Boolean,
    val reasonIfNot: String? = null,
    val requirements: List<String> = emptyList(),
    val acceptedPurposes: Set<ContactPurposeKind> = emptySet(),
)
