package org.fisabilillah.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** The subjects that can be taught and studied here. */
@Serializable
public enum class LearningSubject(
    public val displayName: String,
    public val field: LearningField,
    /**
     * True where the subject is religious instruction rather than a skill. These carry
     * extra disclosure requirements: methodology, sources, and the teacher's capacity.
     */
    public val isReligiousInstruction: Boolean = false,
) {
    QURANIC_ARABIC("Qur'anic Arabic", LearningField.ARABIC),
    MODERN_STANDARD_ARABIC("Modern Standard Arabic", LearningField.ARABIC),
    ARABIC_READING("Arabic reading", LearningField.ARABIC),
    TAJWID("Tajwid", LearningField.QURAN, isReligiousInstruction = true),
    QURAN_MEMORISATION("Qur'an memorisation support", LearningField.QURAN, isReligiousInstruction = true),
    AQIDAH("Foundational aqidah", LearningField.ISLAMIC_SCIENCES, isReligiousInstruction = true),
    FIQH("Fiqh", LearningField.ISLAMIC_SCIENCES, isReligiousInstruction = true),
    SEERAH("Seerah", LearningField.ISLAMIC_SCIENCES, isReligiousInstruction = true),
    HADITH_STUDIES("Hadith studies", LearningField.ISLAMIC_SCIENCES, isReligiousInstruction = true),
    ADAB_AND_CHARACTER("Islamic character and manners", LearningField.ISLAMIC_SCIENCES, isReligiousInstruction = true),
    NEW_MUSLIM_FOUNDATIONS("New Muslim foundations", LearningField.ISLAMIC_SCIENCES, isReligiousInstruction = true),
    YOUTH_MENTORSHIP("Youth mentorship", LearningField.COMMUNITY),
    CAREER_SKILLS("Career and professional skills", LearningField.PROFESSIONAL),
    TECHNOLOGY("Technology", LearningField.PROFESSIONAL),
    ENTREPRENEURSHIP("Entrepreneurship", LearningField.PROFESSIONAL),
    COMMUNITY_SERVICE_TRAINING("Community service training", LearningField.COMMUNITY),
}

@Serializable
public enum class LearningField(public val displayName: String) {
    ARABIC("Arabic language"),
    QURAN("Qur'an"),
    ISLAMIC_SCIENCES("Islamic sciences"),
    PROFESSIONAL("Professional skills"),
    COMMUNITY("Community"),
}

@Serializable
public enum class LearningLevel(public val displayName: String) {
    ABSOLUTE_BEGINNER("Absolute beginner"),
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    MIXED("Mixed levels"),
}

/**
 * The school of thought or method a class follows.
 *
 * Disclosed rather than adjudicated. The platform takes no position on which is correct;
 * it insists only that a student knows what they are walking into before they enrol.
 */
@Serializable
public enum class Methodology(public val displayName: String) {
    HANAFI("Hanafi"),
    MALIKI("Maliki"),
    SHAFII("Shafi'i"),
    HANBALI("Hanbali"),
    COMPARATIVE("Comparative across schools"),
    NOT_MADHHAB_SPECIFIC("Not madhhab specific"),
    OTHER_DISCLOSED("Other, described by the teacher"),
}

/** What a class costs, if anything. */
@Serializable
public sealed interface LearningCost {
    @Serializable
    public data object Free : LearningCost

    @Serializable
    public data class SuggestedDonation(val amount: Money) : LearningCost

    @Serializable
    public data class Fee(val amount: Money, val per: FeePeriod) : LearningCost
}

@Serializable
public enum class FeePeriod(public val displayName: String) {
    PER_SESSION("per session"),
    PER_MONTH("per month"),
    PER_TERM("per term"),
}

/**
 * A class, circle, or one-to-one tutoring arrangement on offer.
 *
 * Religious subjects additionally require [methodology] and [sourceReferences], and the
 * platform will not publish them without a stated [TeachingCapacity]. A sincere volunteer
 * may absolutely teach — they simply may not be presented as something they are not.
 */
@Serializable
public data class LearningOffering(
    val id: ListingId,
    val title: String,
    val summary: String,
    val subject: LearningSubject,
    val level: LearningLevel,
    val instructorId: UserId,
    val instructorCapacity: TeachingCapacity,
    val instructorVerification: VerificationLevel = VerificationLevel.NONE,
    val organizationId: OrganizationId? = null,
    val communityId: CommunityId? = null,
    val methodology: Methodology? = null,
    val methodologyNotes: String? = null,
    val language: Language = Language.ENGLISH,
    val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
    /** Teachers may require that students match their own gender for one-to-one study. */
    val sameGenderStudentsOnly: Boolean = false,
    val format: DeliveryFormat,
    val place: Place? = null,
    val schedule: List<AvailabilityWindow> = emptyList(),
    val scheduleNotes: String? = null,
    val startsOn: Timestamp? = null,
    val maxStudents: Int,
    val enrolledCount: Int = 0,
    val cost: LearningCost = LearningCost.Free,
    val requiredMaterials: List<String> = emptyList(),
    val learningObjectives: List<String> = emptyList(),
    val sourceReferences: List<SourceReference> = emptyList(),
    val isPeerLearning: Boolean = false,
    val status: ListingStatus = ListingStatus.OPEN,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable {

    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(maxStudents > 0) { "maxStudents must be positive" }
        require(enrolledCount >= 0) { "enrolledCount cannot be negative" }
    }

    public val placesRemaining: Int get() = (maxStudents - enrolledCount).coerceAtLeast(0)

    /**
     * The line shown under the instructor's name. Peer learning is always labelled as
     * such, so nobody mistakes a study partner for a teacher.
     */
    public val capacityDisclaimer: String
        get() = if (isPeerLearning) {
            "Peer learning. Members studying together — this is not authoritative religious " +
                "instruction. " + instructorCapacity.disclaimer
        } else {
            instructorCapacity.disclaimer
        }
}

/**
 * A citation. Religious teaching material must be attributable, and nothing produced by
 * software on this platform is ever presented as a ruling.
 */
@Serializable
public data class SourceReference(
    val title: String,
    val author: String? = null,
    val locator: String? = null,
    val url: String? = null,
    val note: String? = null,
) {
    init {
        require(title.isNotBlank()) { "A source reference needs a title" }
    }
}

@Serializable
public data class LearningEnrollment(
    val id: EnrollmentId,
    val offeringId: ListingId,
    val studentId: UserId,
    val status: EnrollmentStatus = EnrollmentStatus.REQUESTED,
    val message: String? = null,
    val decidedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    override val createdAt: Timestamp = Instant.EPOCH,
    override val updatedAt: Timestamp = Instant.EPOCH,
    override val deletedAt: Timestamp? = null,
) : Auditable

@Serializable
public enum class EnrollmentStatus(public val displayName: String) {
    REQUESTED("Requested"),
    ENROLLED("Enrolled"),
    DECLINED("Not accepted"),
    WITHDRAWN("Withdrawn"),
    COMPLETED("Completed"),
    WAITLISTED("Waitlisted"),
}
