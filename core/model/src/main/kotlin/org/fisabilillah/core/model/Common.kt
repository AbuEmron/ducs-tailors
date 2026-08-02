package org.fisabilillah.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** An instant in time, always stored in UTC. */
public typealias Timestamp = Instant

/** The zero instant, used as a "not set yet" default on records that are about to be persisted. */
public val Instant.Companion.EPOCH: Instant
    get() = fromEpochSeconds(0)

/**
 * A place, stored at two precisions.
 *
 * The exact coordinates and street address of a person asking for food or a lift home are
 * among the most sensitive things this platform holds. They live in [exact] and are only
 * ever released by an explicit decision recorded elsewhere; everything shown by default
 * comes from [approximate].
 */
@Serializable
public data class Place(
    val approximate: ApproximateLocation,
    val exact: ExactLocation? = null,
) {
    /** The form safe to show to an arbitrary viewer. */
    public val publicLabel: String get() = approximate.label
}

@Serializable
public data class ApproximateLocation(
    val label: String,
    val city: String,
    val region: String? = null,
    val countryCode: String,
    /** Coordinates rounded to roughly a neighbourhood, suitable for a distance filter. */
    val coarseLatitude: Double? = null,
    val coarseLongitude: Double? = null,
)

/**
 * A precise address. Never serialised into a list response and never returned by a
 * repository without an accompanying disclosure decision.
 */
@Serializable
public data class ExactLocation(
    val addressLine1: String,
    val addressLine2: String? = null,
    val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** A language a person speaks, as a BCP-47 tag plus a display name. */
@Serializable
public data class Language(
    val tag: String,
    val displayName: String,
) {
    public companion object {
        public val ENGLISH: Language = Language("en", "English")
        public val ARABIC: Language = Language("ar", "Arabic")
        public val URDU: Language = Language("ur", "Urdu")
        public val SOMALI: Language = Language("so", "Somali")
        public val BENGALI: Language = Language("bn", "Bengali")
        public val TURKISH: Language = Language("tr", "Turkish")
        public val FRENCH: Language = Language("fr", "French")
        public val SPANISH: Language = Language("es", "Spanish")
        public val MALAY: Language = Language("ms", "Malay")

        public val common: List<Language> = listOf(
            ENGLISH, ARABIC, URDU, SOMALI, BENGALI, TURKISH, FRENCH, SPANISH, MALAY,
        )
    }
}

/** A recurring window in which someone is free to help or to study. */
@Serializable
public data class AvailabilityWindow(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start < end) { "An availability window must start before it ends" }
    }
}

/** How much of themselves a person is offering. */
@Serializable
public data class Availability(
    val windows: List<AvailabilityWindow> = emptyList(),
    val hoursPerWeek: Int? = null,
    val timeZoneId: String = "UTC",
    val notes: String? = null,
    val openToUrgentRequests: Boolean = false,
) {
    init {
        require(hoursPerWeek == null || hoursPerWeek in 0..168) {
            "hoursPerWeek must be within a week"
        }
    }
}

/**
 * Money, as minor units plus a currency code. Kept as a whole number so that no
 * donation total ever passes through a floating point value.
 */
@Serializable
public data class Money(
    val minorUnits: Long,
    val currencyCode: String,
) {
    init {
        require(currencyCode.length == 3) { "currencyCode must be an ISO 4217 code" }
        require(minorUnits >= 0) { "Money cannot be negative" }
    }

    public operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode) { "Cannot add different currencies" }
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    public companion object {
        public fun zero(currencyCode: String): Money = Money(0, currencyCode)
    }
}

/** A skill offered or sought, drawn from an administrator-editable lookup table. */
@Serializable
public data class Skill(
    val id: SkillId,
    val slug: String,
    val displayName: String,
    val category: SkillCategory,
    val retired: Boolean = false,
)

@Serializable
public enum class SkillCategory(public val displayName: String) {
    LANGUAGE("Language"),
    ISLAMIC_STUDIES("Islamic studies"),
    TRADES("Trades"),
    PROFESSIONAL("Professional"),
    TECHNOLOGY("Technology"),
    CARE("Care and support"),
    LOGISTICS("Logistics"),
    CREATIVE("Creative"),
    OTHER("Other"),
}

/** How well someone knows a skill they are offering. Self-declared, and labelled as such. */
@Serializable
public enum class SkillProficiency(public val displayName: String) {
    LEARNING("Still learning"),
    COMPETENT("Competent"),
    EXPERIENCED("Experienced"),
    PROFESSIONAL("Professional"),
}

@Serializable
public data class UserSkill(
    val skill: Skill,
    val proficiency: SkillProficiency,
    val willingToTeach: Boolean = false,
    val seekingHelpWith: Boolean = false,
    val yearsOfExperience: Int? = null,
)

/** Common shape for anything that can be soft-deleted and audited. */
public interface Auditable {
    public val createdAt: Timestamp
    public val updatedAt: Timestamp
    public val deletedAt: Timestamp?
}

/** A page of results. Deliberately cursor-based and finite — there is no infinite scroll here. */
@Serializable
public data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val totalKnown: Int? = null,
) {
    public val hasMore: Boolean get() = nextCursor != null

    public fun <R> map(transform: (T) -> R): Page<R> =
        Page(items.map(transform), nextCursor, totalKnown)

    public companion object {
        public fun <T> empty(): Page<T> = Page(emptyList(), null, 0)
    }
}
