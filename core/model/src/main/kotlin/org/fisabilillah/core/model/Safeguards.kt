package org.fisabilillah.core.model

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * Who is allowed to do a particular thing to or with a user.
 *
 * The values are ordered from most open to most closed by [strictness]. Every safeguard
 * field that answers a "who may…" question uses this type, which lets two settings be
 * combined by simply taking the stricter one — the rule the whole safeguard system is
 * built on.
 */
@Serializable
public enum class AudienceScope(
    public val strictness: Int,
    public val displayName: String,
) {
    EVERYONE(0, "Anyone on the platform"),
    VERIFIED_ONLY(1, "Verified members only"),
    MY_ORGANIZATIONS_ONLY(2, "People in my masjid or organisations"),
    SAME_GENDER_ONLY(3, "Same gender only"),
    SAME_GENDER_VERIFIED_ONLY(4, "Verified members of the same gender"),
    NOBODY(5, "No one"),
    ;

    public val excludesOppositeGender: Boolean
        get() = this == SAME_GENDER_ONLY || this == SAME_GENDER_VERIFIED_ONLY || this == NOBODY

    public val requiresVerification: Boolean
        get() = this == VERIFIED_ONLY || this == SAME_GENDER_VERIFIED_ONLY

    public val requiresSharedOrganization: Boolean
        get() = this == MY_ORGANIZATIONS_ONLY

    public companion object {
        /** The stricter of two scopes. Used when combining user, organisation and community settings. */
        public fun stricter(a: AudienceScope, b: AudienceScope): AudienceScope =
            if (a.strictness >= b.strictness) a else b
    }
}

/** How a person's face, if any, is shown. Photographs are never required. */
@Serializable
public enum class ProfileImageStyle(public val displayName: String) {
    NONE("No image"),
    INITIALS("Initials"),
    GEOMETRIC_AVATAR("Geometric avatar"),
    PHOTOGRAPH("Photograph"),
}

/** How precisely a person's whereabouts are shown to others. */
@Serializable
public enum class LocationPrecision(
    public val strictness: Int,
    public val displayName: String,
) {
    EXACT(0, "Exact location"),
    NEIGHBOURHOOD(1, "Neighbourhood"),
    CITY(2, "City only"),
    REGION(3, "Region only"),
    HIDDEN(4, "Hidden"),
    ;

    public companion object {
        public fun stricter(a: LocationPrecision, b: LocationPrecision): LocationPrecision =
            if (a.strictness >= b.strictness) a else b
    }
}

/** How a person's legal name is exposed. */
@Serializable
public enum class NameVisibility(
    public val strictness: Int,
    public val displayName: String,
) {
    REAL_NAME_PUBLIC(0, "Show my real name"),
    REAL_NAME_TO_VERIFIED(1, "Real name to verified members only"),
    REAL_NAME_TO_ORGANIZERS(2, "Real name only to organisers I commit to"),
    DISPLAY_NAME_ONLY(3, "Display name only"),
    ;

    public companion object {
        public fun stricter(a: NameVisibility, b: NameVisibility): NameVisibility =
            if (a.strictness >= b.strictness) a else b
    }
}

/** When a moderator must be a party to a conversation. */
@Serializable
public enum class ModeratorPresenceRule(
    public val strictness: Int,
    public val displayName: String,
) {
    NEVER(0, "Not required"),
    CROSS_GENDER_ONLY(1, "For conversations with the opposite gender"),
    ALWAYS(2, "For every conversation I am part of"),
    ;

    public companion object {
        public fun stricter(a: ModeratorPresenceRule, b: ModeratorPresenceRule): ModeratorPresenceRule =
            if (a.strictness >= b.strictness) a else b
    }
}

/** How conversations with the opposite gender are structured, if they are permitted at all. */
@Serializable
public enum class CrossGenderConversationStructure(
    public val strictness: Int,
    public val displayName: String,
) {
    /** A one-to-one thread, still purpose-bound and still logged. */
    DIRECT_WITH_PURPOSE(0, "Direct, with a stated purpose"),

    /** Must happen inside an existing group, project, or class thread. */
    GROUP_CONTEXT_ONLY(1, "Only inside a group or project thread"),

    /** A third party of the recipient's choosing is added to the thread on creation. */
    THIRD_PARTY_PRESENT(2, "A third party must be in the conversation"),

    /** The recipient's wali or trusted contact is added to the thread on creation. */
    GUARDIAN_PRESENT(3, "My wali or trusted contact must be in the conversation"),
    ;

    public companion object {
        public fun stricter(
            a: CrossGenderConversationStructure,
            b: CrossGenderConversationStructure,
        ): CrossGenderConversationStructure = if (a.strictness >= b.strictness) a else b
    }
}

/**
 * A window during the day in which stricter rules apply — for example, no new
 * conversations after Isha.
 */
@Serializable
public data class QuietHours(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime(21, 0),
    val end: LocalTime = LocalTime(7, 0),
    /** New conversations cannot be opened during the window. Existing threads still work. */
    val blockNewConversations: Boolean = true,
    /** Calls are not offered during the window. */
    val blockCalls: Boolean = true,
) {
    /**
     * True when [time] falls inside the window, correctly handling a window that wraps
     * past midnight (the common case for an evening-to-morning rule).
     */
    public fun covers(time: LocalTime): Boolean {
        if (!enabled) return false
        if (start == end) return false
        return if (start < end) {
            time >= start && time < end
        } else {
            time >= start || time < end
        }
    }
}

/**
 * The complete set of a person's self-chosen boundaries.
 *
 * Two rules govern this type everywhere it is used:
 *
 *  1. A user may always make their own settings **stricter**. Loosening is possible too,
 *     but never below a floor imposed by an organisation or community they have joined.
 *  2. No preset is more Islamically correct than another. The presets are conveniences
 *     for common circumstances, and the product must never present them as a ranking of
 *     anyone's religiosity.
 */
@Serializable
public data class UserSafeguards(
    val userId: UserId,
    val presetName: SafeguardPresetName = SafeguardPresetName.COMMUNITY_SERVICE,

    // ── Discovery ──────────────────────────────────────────────────────────────
    val profileDiscoverableBy: AudienceScope = AudienceScope.EVERYONE,
    val nameVisibility: NameVisibility = NameVisibility.DISPLAY_NAME_ONLY,
    val locationPrecision: LocationPrecision = LocationPrecision.CITY,
    val profileImageStyle: ProfileImageStyle = ProfileImageStyle.INITIALS,
    val profileImageVisibleTo: AudienceScope = AudienceScope.SAME_GENDER_ONLY,

    // ── Who may start a conversation ───────────────────────────────────────────
    val contactableBy: AudienceScope = AudienceScope.EVERYONE,
    val minimumVerificationToContactMe: VerificationLevel = VerificationLevel.EMAIL_VERIFIED,
    val onlyMyOrganizationsMayContactMe: Boolean = false,

    /**
     * Whether the opening message must carry a written purpose statement in addition to
     * the structural purpose every conversation already has. Attaching a purpose is not
     * optional on this platform; this only controls whether the sender must also explain
     * themselves in prose.
     */
    val requireWrittenPurposeStatement: Boolean = true,

    /** Purposes this person is simply not open to receiving. */
    val declinedPurposes: Set<ContactPurposeKind> = emptySet(),

    // ── Shape of cross-gender contact ──────────────────────────────────────────
    val crossGenderStructure: CrossGenderConversationStructure =
        CrossGenderConversationStructure.DIRECT_WITH_PURPOSE,
    val moderatorPresence: ModeratorPresenceRule = ModeratorPresenceRule.NEVER,
    val requireGroupContext: Boolean = false,
    val requireThirdParty: Boolean = false,

    /** Purposes for which the user's wali or trusted contact is added to the thread. */
    val guardianCopiedOnPurposes: Set<ContactPurposeKind> = emptySet(),

    // ── Calls and meetings ─────────────────────────────────────────────────────
    val voiceCallsAllowedFrom: AudienceScope = AudienceScope.SAME_GENDER_ONLY,
    val videoCallsAllowedFrom: AudienceScope = AudienceScope.NOBODY,
    val oneToOneMeetingsAllowedFrom: AudienceScope = AudienceScope.SAME_GENDER_ONLY,
    val meetingsMustBeInPublicPlaces: Boolean = true,
    val meetingsRequireThirdParty: Boolean = false,

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    /**
     * Archive a thread once the work it existed for is finished. Archiving hides the
     * thread and stops new messages; it never destroys evidence needed for a report.
     */
    val autoArchiveAfterCompletion: Boolean = true,
    val autoArchiveAfterDays: Int = 14,

    // ── Marriage introductions ─────────────────────────────────────────────────
    val acceptFormalIntroductions: Boolean = false,
    val introductionsGoDirectlyToGuardian: Boolean = true,

    // ── Time-of-day ────────────────────────────────────────────────────────────
    val quietHours: QuietHours = QuietHours(),

    val updatedAt: Timestamp = Timestamp.EPOCH,
) {
    init {
        require(autoArchiveAfterDays in 1..365) {
            "autoArchiveAfterDays must be between 1 and 365"
        }
    }
}

/**
 * Named starting points for [UserSafeguards].
 *
 * These exist because a blank grid of thirty switches is not a real choice for anyone.
 * They are starting points only, and the UI must say so.
 */
@Serializable
public enum class SafeguardPresetName(
    public val displayName: String,
    public val summary: String,
) {
    MAXIMUM_PRIVACY(
        "Maximum privacy",
        "You are not discoverable, no one can start a conversation with you, and you " +
            "reach out only when you choose to.",
    ),
    FAMILY_AND_WALI_GUIDED(
        "Family and wali guided",
        "Your trusted contact is included in conversations with the opposite gender, and " +
            "any marriage enquiry goes to them first.",
    ),
    COMMUNITY_SERVICE(
        "Community service only",
        "Open to service, learning and project requests. Marriage enquiries are off.",
    ),
    LEARNING_ONLY(
        "Learning only",
        "You can be reached about teaching and study. Everything else is closed.",
    ),
    ORGANIZATION_MANAGED(
        "Organisation managed",
        "Only people in your masjid or organisations can find or contact you, and your " +
            "organisation's rules apply on top of yours.",
    ),
    CUSTOM("Custom", "Settings you have chosen yourself."),
    ;

    public companion object {
        /** Presets offered during onboarding, in the order they are shown. */
        public val selectable: List<SafeguardPresetName> = listOf(
            COMMUNITY_SERVICE,
            LEARNING_ONLY,
            FAMILY_AND_WALI_GUIDED,
            ORGANIZATION_MANAGED,
            MAXIMUM_PRIVACY,
            CUSTOM,
        )
    }
}

/**
 * A floor that an organisation or community imposes on its members inside its own spaces.
 *
 * An organisation can only ever make things stricter. It cannot use this to open up a
 * member who has chosen to be closed — for example, a masjid may require that all
 * cross-gender threads in its space include a moderator, but it can never switch a
 * sister's video calls back on.
 */
@Serializable
public data class SafeguardFloor(
    val contactableBy: AudienceScope? = null,
    val minimumVerificationToContact: VerificationLevel? = null,
    val crossGenderStructure: CrossGenderConversationStructure? = null,
    val moderatorPresence: ModeratorPresenceRule? = null,
    val requireGroupContext: Boolean = false,
    val requireThirdParty: Boolean = false,
    val voiceCallsAllowedFrom: AudienceScope? = null,
    val videoCallsAllowedFrom: AudienceScope? = null,
    val oneToOneMeetingsAllowedFrom: AudienceScope? = null,
    val meetingsMustBeInPublicPlaces: Boolean = false,
    val forbidFormalIntroductions: Boolean = false,
    val requireBackgroundCheckForMinorContact: Boolean = true,
) {
    public companion object {
        public val NONE: SafeguardFloor = SafeguardFloor()
    }
}
