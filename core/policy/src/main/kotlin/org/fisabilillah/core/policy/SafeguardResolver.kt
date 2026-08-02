package org.fisabilillah.core.policy

import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.CrossGenderConversationStructure
import org.fisabilillah.core.model.LocationPrecision
import org.fisabilillah.core.model.ModeratorPresenceRule
import org.fisabilillah.core.model.NameVisibility
import org.fisabilillah.core.model.ProfileImageStyle
import org.fisabilillah.core.model.QuietHours
import org.fisabilillah.core.model.SafeguardFloor
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VerificationLevel

/**
 * Turns a member's own settings, plus the floors imposed by the organisations and
 * communities they belong to, into the single set of rules that actually applies.
 *
 * One rule governs everything here: **combining can only ever tighten**. An organisation
 * can require that cross-gender threads in its space carry a moderator; it can never
 * switch a member's video calls back on, lower their verification requirement, or make
 * them discoverable when they chose not to be.
 */
public object SafeguardResolver {

    /**
     * The rules in force for [user] once every applicable [floors] entry has been applied.
     *
     * Note that the result is still a [UserSafeguards]: the rest of the system never needs
     * to know whether a restriction came from the person or from their masjid, only what
     * the restriction is.
     */
    public fun effective(user: UserSafeguards, floors: List<SafeguardFloor>): UserSafeguards {
        if (floors.isEmpty()) return user
        return floors.fold(user) { acc, floor -> apply(acc, floor) }
    }

    public fun effective(user: UserSafeguards, floor: SafeguardFloor): UserSafeguards =
        apply(user, floor)

    private fun apply(user: UserSafeguards, floor: SafeguardFloor): UserSafeguards = user.copy(
        contactableBy = tighten(user.contactableBy, floor.contactableBy),
        minimumVerificationToContactMe = tighten(
            user.minimumVerificationToContactMe,
            floor.minimumVerificationToContact,
        ),
        crossGenderStructure = floor.crossGenderStructure
            ?.let { CrossGenderConversationStructure.stricter(user.crossGenderStructure, it) }
            ?: user.crossGenderStructure,
        moderatorPresence = floor.moderatorPresence
            ?.let { ModeratorPresenceRule.stricter(user.moderatorPresence, it) }
            ?: user.moderatorPresence,
        requireGroupContext = user.requireGroupContext || floor.requireGroupContext,
        requireThirdParty = user.requireThirdParty || floor.requireThirdParty,
        voiceCallsAllowedFrom = tighten(user.voiceCallsAllowedFrom, floor.voiceCallsAllowedFrom),
        videoCallsAllowedFrom = tighten(user.videoCallsAllowedFrom, floor.videoCallsAllowedFrom),
        oneToOneMeetingsAllowedFrom = tighten(
            user.oneToOneMeetingsAllowedFrom,
            floor.oneToOneMeetingsAllowedFrom,
        ),
        meetingsMustBeInPublicPlaces =
            user.meetingsMustBeInPublicPlaces || floor.meetingsMustBeInPublicPlaces,
        meetingsRequireThirdParty =
            user.meetingsRequireThirdParty || floor.meetingsRequireThirdParty,
        acceptFormalIntroductions =
            user.acceptFormalIntroductions && !floor.forbidFormalIntroductions,
    )

    private fun tighten(current: AudienceScope, floor: AudienceScope?): AudienceScope =
        if (floor == null) current else AudienceScope.stricter(current, floor)

    private fun tighten(current: VerificationLevel, floor: VerificationLevel?): VerificationLevel =
        if (floor == null) current else if (current.rank >= floor.rank) current else floor

    /**
     * Whether [candidate] is at least as strict as [current] in every respect.
     *
     * Used to enforce the promise that a member can always tighten their own settings.
     * Loosening is allowed too — this is not a ratchet — but the UI uses this to tell a
     * person plainly when a change would open something up, rather than letting a
     * thirty-switch screen quietly widen their exposure.
     */
    public fun isAtLeastAsStrict(candidate: UserSafeguards, current: UserSafeguards): Boolean {
        if (candidate.profileDiscoverableBy.strictness < current.profileDiscoverableBy.strictness) return false
        if (candidate.contactableBy.strictness < current.contactableBy.strictness) return false
        if (candidate.profileImageVisibleTo.strictness < current.profileImageVisibleTo.strictness) return false
        if (candidate.voiceCallsAllowedFrom.strictness < current.voiceCallsAllowedFrom.strictness) return false
        if (candidate.videoCallsAllowedFrom.strictness < current.videoCallsAllowedFrom.strictness) return false
        if (candidate.oneToOneMeetingsAllowedFrom.strictness < current.oneToOneMeetingsAllowedFrom.strictness) return false
        if (candidate.crossGenderStructure.strictness < current.crossGenderStructure.strictness) return false
        if (candidate.moderatorPresence.strictness < current.moderatorPresence.strictness) return false
        if (candidate.nameVisibility.strictness < current.nameVisibility.strictness) return false
        if (candidate.locationPrecision.strictness < current.locationPrecision.strictness) return false
        if (candidate.minimumVerificationToContactMe.rank < current.minimumVerificationToContactMe.rank) return false
        if (current.requireGroupContext && !candidate.requireGroupContext) return false
        if (current.requireThirdParty && !candidate.requireThirdParty) return false
        if (current.meetingsMustBeInPublicPlaces && !candidate.meetingsMustBeInPublicPlaces) return false
        if (current.meetingsRequireThirdParty && !candidate.meetingsRequireThirdParty) return false
        if (!current.declinedPurposes.all { it in candidate.declinedPurposes }) return false
        return true
    }

    /**
     * Fields a change would open up, in plain language, so a member can be shown exactly
     * what they are about to loosen before they confirm it.
     */
    public fun loosenedFields(candidate: UserSafeguards, current: UserSafeguards): List<String> {
        val loosened = mutableListOf<String>()
        if (candidate.profileDiscoverableBy.strictness < current.profileDiscoverableBy.strictness) {
            loosened += "More people will be able to find your profile"
        }
        if (candidate.contactableBy.strictness < current.contactableBy.strictness) {
            loosened += "More people will be able to start a conversation with you"
        }
        if (candidate.profileImageVisibleTo.strictness < current.profileImageVisibleTo.strictness) {
            loosened += "More people will be able to see your profile image"
        }
        if (candidate.crossGenderStructure.strictness < current.crossGenderStructure.strictness) {
            loosened += "Conversations with the opposite gender will have less structure around them"
        }
        if (candidate.moderatorPresence.strictness < current.moderatorPresence.strictness) {
            loosened += "A moderator will no longer be present in as many conversations"
        }
        if (candidate.videoCallsAllowedFrom.strictness < current.videoCallsAllowedFrom.strictness) {
            loosened += "More people will be able to video call you"
        }
        if (candidate.voiceCallsAllowedFrom.strictness < current.voiceCallsAllowedFrom.strictness) {
            loosened += "More people will be able to voice call you"
        }
        if (candidate.oneToOneMeetingsAllowedFrom.strictness < current.oneToOneMeetingsAllowedFrom.strictness) {
            loosened += "More people will be able to arrange a one-to-one meeting with you"
        }
        if (candidate.nameVisibility.strictness < current.nameVisibility.strictness) {
            loosened += "More people will be able to see your real name"
        }
        if (candidate.locationPrecision.strictness < current.locationPrecision.strictness) {
            loosened += "Your location will be shown more precisely"
        }
        if (candidate.minimumVerificationToContactMe.rank < current.minimumVerificationToContactMe.rank) {
            loosened += "Less-verified members will be able to contact you"
        }
        if (current.requireGroupContext && !candidate.requireGroupContext) {
            loosened += "Conversations will no longer be restricted to group contexts"
        }
        if (current.requireThirdParty && !candidate.requireThirdParty) {
            loosened += "A third party will no longer be required in your conversations"
        }
        if (current.meetingsRequireThirdParty && !candidate.meetingsRequireThirdParty) {
            loosened += "Meetings will no longer be expected to have a third person present"
        }
        if (!current.acceptFormalIntroductions && candidate.acceptFormalIntroductions) {
            loosened += "You will start receiving formal family introductions"
        }
        return loosened
    }
}

/**
 * The preset definitions.
 *
 * A note that belongs in the source as much as in the interface: these are circumstances,
 * not rankings. Someone on [SafeguardPresetName.COMMUNITY_SERVICE] is not less careful
 * than someone on [SafeguardPresetName.MAXIMUM_PRIVACY]. A revert who is the only Muslim
 * in their town and a sister living with her family have different needs, and neither is
 * a measure of the other's religion. The UI must never sort, badge, or compare these.
 */
public object SafeguardPresets {

    public fun forName(name: SafeguardPresetName, userId: UserId): UserSafeguards = when (name) {
        SafeguardPresetName.MAXIMUM_PRIVACY -> maximumPrivacy(userId)
        SafeguardPresetName.FAMILY_AND_WALI_GUIDED -> familyAndWaliGuided(userId)
        SafeguardPresetName.COMMUNITY_SERVICE -> communityService(userId)
        SafeguardPresetName.LEARNING_ONLY -> learningOnly(userId)
        SafeguardPresetName.ORGANIZATION_MANAGED -> organizationManaged(userId)
        SafeguardPresetName.CUSTOM -> communityService(userId).copy(
            presetName = SafeguardPresetName.CUSTOM,
        )
    }

    private fun maximumPrivacy(userId: UserId): UserSafeguards = UserSafeguards(
        userId = userId,
        presetName = SafeguardPresetName.MAXIMUM_PRIVACY,
        profileDiscoverableBy = AudienceScope.NOBODY,
        nameVisibility = NameVisibility.DISPLAY_NAME_ONLY,
        locationPrecision = LocationPrecision.REGION,
        profileImageStyle = ProfileImageStyle.NONE,
        profileImageVisibleTo = AudienceScope.NOBODY,
        contactableBy = AudienceScope.NOBODY,
        minimumVerificationToContactMe = VerificationLevel.IDENTITY_VERIFIED,
        crossGenderStructure = CrossGenderConversationStructure.GUARDIAN_PRESENT,
        moderatorPresence = ModeratorPresenceRule.CROSS_GENDER_ONLY,
        requireGroupContext = true,
        voiceCallsAllowedFrom = AudienceScope.NOBODY,
        videoCallsAllowedFrom = AudienceScope.NOBODY,
        oneToOneMeetingsAllowedFrom = AudienceScope.NOBODY,
        meetingsMustBeInPublicPlaces = true,
        meetingsRequireThirdParty = true,
        acceptFormalIntroductions = false,
        quietHours = QuietHours(enabled = true),
    )

    private fun familyAndWaliGuided(userId: UserId): UserSafeguards = UserSafeguards(
        userId = userId,
        presetName = SafeguardPresetName.FAMILY_AND_WALI_GUIDED,
        profileDiscoverableBy = AudienceScope.VERIFIED_ONLY,
        nameVisibility = NameVisibility.REAL_NAME_TO_ORGANIZERS,
        locationPrecision = LocationPrecision.CITY,
        profileImageStyle = ProfileImageStyle.INITIALS,
        profileImageVisibleTo = AudienceScope.SAME_GENDER_ONLY,
        contactableBy = AudienceScope.VERIFIED_ONLY,
        minimumVerificationToContactMe = VerificationLevel.PHONE_VERIFIED,
        crossGenderStructure = CrossGenderConversationStructure.GUARDIAN_PRESENT,
        guardianCopiedOnPurposes = ContactPurposeKind.entries.toSet(),
        voiceCallsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
        videoCallsAllowedFrom = AudienceScope.NOBODY,
        oneToOneMeetingsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
        meetingsMustBeInPublicPlaces = true,
        acceptFormalIntroductions = true,
        introductionsGoDirectlyToGuardian = true,
    )

    private fun communityService(userId: UserId): UserSafeguards = UserSafeguards(
        userId = userId,
        presetName = SafeguardPresetName.COMMUNITY_SERVICE,
        profileDiscoverableBy = AudienceScope.EVERYONE,
        nameVisibility = NameVisibility.DISPLAY_NAME_ONLY,
        locationPrecision = LocationPrecision.CITY,
        profileImageStyle = ProfileImageStyle.INITIALS,
        profileImageVisibleTo = AudienceScope.SAME_GENDER_ONLY,
        // Open, with a confirmed email as the floor. Requiring identity verification here
        // would shut out exactly the people this platform is for — someone three months
        // into Islam, or new to a city — while barely inconveniencing anyone determined.
        contactableBy = AudienceScope.EVERYONE,
        minimumVerificationToContactMe = VerificationLevel.EMAIL_VERIFIED,
        crossGenderStructure = CrossGenderConversationStructure.DIRECT_WITH_PURPOSE,
        voiceCallsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
        videoCallsAllowedFrom = AudienceScope.NOBODY,
        oneToOneMeetingsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
        meetingsMustBeInPublicPlaces = true,
        acceptFormalIntroductions = false,
    )

    private fun learningOnly(userId: UserId): UserSafeguards = UserSafeguards(
        userId = userId,
        presetName = SafeguardPresetName.LEARNING_ONLY,
        profileDiscoverableBy = AudienceScope.VERIFIED_ONLY,
        contactableBy = AudienceScope.VERIFIED_ONLY,
        minimumVerificationToContactMe = VerificationLevel.EMAIL_VERIFIED,
        declinedPurposes = setOf(
            ContactPurposeKind.VOLUNTEER_OPPORTUNITY,
            ContactPurposeKind.ASSISTANCE_REQUEST,
            ContactPurposeKind.COMMUNITY_PROJECT,
        ),
        crossGenderStructure = CrossGenderConversationStructure.DIRECT_WITH_PURPOSE,
        profileImageStyle = ProfileImageStyle.INITIALS,
        profileImageVisibleTo = AudienceScope.SAME_GENDER_ONLY,
        voiceCallsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
        videoCallsAllowedFrom = AudienceScope.NOBODY,
        oneToOneMeetingsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
        acceptFormalIntroductions = false,
    )

    private fun organizationManaged(userId: UserId): UserSafeguards = UserSafeguards(
        userId = userId,
        presetName = SafeguardPresetName.ORGANIZATION_MANAGED,
        profileDiscoverableBy = AudienceScope.MY_ORGANIZATIONS_ONLY,
        contactableBy = AudienceScope.MY_ORGANIZATIONS_ONLY,
        onlyMyOrganizationsMayContactMe = true,
        minimumVerificationToContactMe = VerificationLevel.EMAIL_VERIFIED,
        crossGenderStructure = CrossGenderConversationStructure.GROUP_CONTEXT_ONLY,
        moderatorPresence = ModeratorPresenceRule.CROSS_GENDER_ONLY,
        profileImageStyle = ProfileImageStyle.INITIALS,
        profileImageVisibleTo = AudienceScope.MY_ORGANIZATIONS_ONLY,
        voiceCallsAllowedFrom = AudienceScope.MY_ORGANIZATIONS_ONLY,
        videoCallsAllowedFrom = AudienceScope.NOBODY,
        oneToOneMeetingsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
        meetingsMustBeInPublicPlaces = true,
        acceptFormalIntroductions = false,
    )
}
