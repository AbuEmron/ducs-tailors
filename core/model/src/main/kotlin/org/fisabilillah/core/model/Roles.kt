package org.fisabilillah.core.model

import kotlinx.serialization.Serializable

/**
 * Account roles. A person may hold several of these at once, but the sensitive ones
 * can only be granted by the platform after verification — never self-asserted.
 *
 * @property selfAssignable whether an ordinary user may add this role to themselves
 *   during onboarding. Anything that carries authority over other people, or that
 *   implies religious or professional standing, is deliberately not self-assignable.
 */
@Serializable
public enum class AccountRole(
    public val selfAssignable: Boolean,
    public val displayName: String,
) {
    COMMUNITY_MEMBER(true, "Community member"),
    LEARNER(true, "Learner"),
    VOLUNTEER(true, "Volunteer"),
    PROJECT_ORGANIZER(true, "Project organiser"),

    /** Requires qualification review before the role becomes visible to others. */
    TEACHER(false, "Teacher or tutor"),
    SCHOLAR(false, "Scholar or qualified educator"),

    CHARITY_ORGANIZATION(false, "Charity or nonprofit organisation"),
    MASJID_ORGANIZATION(false, "Masjid or community organisation"),

    MODERATOR(false, "Moderator"),
    SAFETY_ADMINISTRATOR(false, "Safety administrator"),
    PLATFORM_ADMINISTRATOR(false, "Platform administrator"),

    /** Held by someone acting as a guardian contact for another user. */
    WALI_CONTACT(false, "Wali or guardian contact"),
    ;

    public val isStaff: Boolean
        get() = this == MODERATOR || this == SAFETY_ADMINISTRATOR || this == PLATFORM_ADMINISTRATOR
}

/**
 * How a person may describe the capacity in which they teach or help.
 *
 * The platform deliberately refuses to flatten these into a single "teacher" badge.
 * A sincere volunteer who reads Qur'an well is not a scholar, and presenting them as
 * one would mislead the very people who came here looking for reliable knowledge.
 */
@Serializable
public enum class TeachingCapacity(
    public val displayName: String,
    public val requiresQualificationReview: Boolean,
    public val disclaimer: String,
) {
    PEER_HELPER(
        displayName = "Peer helper",
        requiresQualificationReview = false,
        disclaimer = "A fellow community member sharing what they know. Not a substitute " +
            "for a qualified scholar, teacher, or licensed professional.",
    ),
    GENERAL_VOLUNTEER(
        displayName = "General volunteer",
        requiresQualificationReview = false,
        disclaimer = "Offering practical help, not religious or professional instruction.",
    ),
    STUDENT_OF_KNOWLEDGE(
        displayName = "Student of knowledge",
        requiresQualificationReview = false,
        disclaimer = "Currently studying under teachers. Shares what they have learned and " +
            "refers questions beyond their level to qualified scholars.",
    ),
    ARABIC_TUTOR(
        displayName = "Arabic tutor",
        requiresQualificationReview = true,
        disclaimer = "Teaches the Arabic language. Language instruction is not religious " +
            "rulings.",
    ),
    QURAN_TEACHER(
        displayName = "Qur'an teacher",
        requiresQualificationReview = true,
        disclaimer = "Teaches recitation, tajwid, or memorisation. Ask about their ijaza or " +
            "training if that matters to you.",
    ),
    VERIFIED_SCHOLAR(
        displayName = "Verified scholar",
        requiresQualificationReview = true,
        disclaimer = "Qualifications were reviewed by the platform. Verification confirms " +
            "credentials only. It is not an endorsement of any particular opinion, and it " +
            "does not replace consulting a qualified scholar who knows your circumstances.",
    ),
    LICENSED_PROFESSIONAL(
        displayName = "Licensed professional",
        requiresQualificationReview = true,
        disclaimer = "Licence details were reviewed. Conversations here are not a formal " +
            "professional engagement and do not create a client relationship.",
    ),
    ;

    public val isReligiousInstruction: Boolean
        get() = this == QURAN_TEACHER || this == VERIFIED_SCHOLAR || this == STUDENT_OF_KNOWLEDGE
}

/**
 * What the platform has actually checked about a person.
 *
 * The ordering matters: [rank] is used whenever a safeguard says "only people verified
 * at least this far may contact me".
 */
@Serializable
public enum class VerificationLevel(
    public val rank: Int,
    public val displayName: String,
    /** Plain-language statement of what this badge does *not* mean. */
    public val whatItDoesNotMean: String,
) {
    NONE(0, "Not verified", "Nothing about this account has been checked."),
    EMAIL_VERIFIED(
        1,
        "Email verified",
        "Only that an email address was confirmed. Nothing about the person behind it.",
    ),
    PHONE_VERIFIED(
        2,
        "Phone verified",
        "Only that a phone number was confirmed. It does not confirm a name or identity.",
    ),
    IDENTITY_VERIFIED(
        3,
        "Identity verified",
        "A government identity document was checked by our verification provider. It does " +
            "not mean the platform vouches for this person's character, religious " +
            "correctness, or professional advice.",
    ),
    BACKGROUND_CHECKED(
        4,
        "Background checked",
        "A background check was completed on the date shown. Checks look backwards, not " +
            "forwards, and they do not cover every jurisdiction.",
    ),
    ;

    public infix fun atLeast(other: VerificationLevel): Boolean = rank >= other.rank
}

/**
 * Verifications that attach to something other than a person's identity. Kept separate
 * from [VerificationLevel] because they are not ordered — an organisation can be verified
 * without any of its staff being background checked, and vice versa.
 */
@Serializable
public enum class Attestation(
    public val displayName: String,
    public val whatItDoesNotMean: String,
) {
    ORGANIZATION_VERIFIED(
        "Organisation verified",
        "Registration documents were checked. It is not an audit of how funds are spent.",
    ),
    MASJID_AFFILIATED(
        "Masjid affiliated",
        "A masjid confirmed this account represents them. The masjid, not the platform, " +
            "stands behind that.",
    ),
    QUALIFICATION_VERIFIED(
        "Qualification confirmed",
        "The stated certificate or licence was checked. It is not an endorsement of any " +
            "opinion this person holds.",
    ),
    WALI_CONTACT_VERIFIED(
        "Guardian contact verified",
        "The guardian confirmed they act in this role. It says nothing about anyone's " +
            "suitability for marriage.",
    ),
}

/**
 * Recorded so that safeguards about unrelated men and women can be honoured.
 *
 * This value is set once during onboarding and thereafter is only changeable by support
 * with an audit record, because everything from tutoring arrangements to the introduction
 * workflow depends on it being stable.
 */
@Serializable
public enum class Gender(public val displayName: String) {
    MALE("Brother"),
    FEMALE("Sister"),
    ;

    public val opposite: Gender get() = if (this == MALE) FEMALE else MALE
}
