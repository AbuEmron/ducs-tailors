package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.StartConversationCommand
import org.fisabilillah.core.model.ContactRequirement
import org.fisabilillah.core.model.SafeguardFloor
import org.fisabilillah.core.model.UserSafeguards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

/**
 * Every safeguard field a member can set must actually govern something.
 *
 * A settings screen full of switches that change nothing is worse than a screen with fewer
 * switches: it tells someone their boundary is being kept when it is not. These tests walk
 * the fields on [UserSafeguards] and [SafeguardFloor] and assert that each one reaches the
 * behaviour it claims to control.
 */
@DisplayName("Declared safeguards are enforced")
class SafeguardsAreEnforcedTest {

    @Test
    @DisplayName("a member who asks for a third party at meetings has it recorded on the thread")
    fun meetingThirdPartyReachesTheThread() = runTest {
        val harness = Harness()

        harness.graph.updateSafeguards(
            harness.principal(SeedData.yusuf),
            harness.store.safeguards.getValue(SeedData.yusuf)
                .copy(meetingsRequireThirdParty = true),
        ).expectSuccess()

        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders on Saturday.",
            ),
        ).expectSuccess()

        assertTrue(
            ContactRequirement.THIRD_PARTY_AT_MEETINGS in started.requirementsApplied,
            "the setting was saved but never reached the conversation's terms",
        )
    }

    @Test
    @DisplayName("the archive schedule a member chose is applied to the thread")
    fun archiveScheduleIsApplied() = runTest {
        val harness = Harness()

        harness.graph.updateSafeguards(
            harness.principal(SeedData.yusuf),
            harness.store.safeguards.getValue(SeedData.yusuf).copy(
                autoArchiveAfterCompletion = true,
                autoArchiveAfterDays = 21,
            ),
        ).expectSuccess()

        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders on Saturday.",
            ),
        ).expectSuccess()

        val archiveAfter = started.conversation.archiveAfter
        assertNotNull(archiveAfter, "the thread has no archive deadline despite the setting")
        assertEquals(harness.clock.now() + 21.days, archiveAfter)
    }

    @Test
    @DisplayName("a member who switched archiving off gets no deadline")
    fun archivingCanBeSwitchedOff() = runTest {
        val harness = Harness()

        harness.graph.updateSafeguards(
            harness.principal(SeedData.yusuf),
            harness.store.safeguards.getValue(SeedData.yusuf)
                .copy(autoArchiveAfterCompletion = false),
        ).expectSuccess()

        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders on Saturday.",
            ),
        ).expectSuccess()

        assertNull(started.conversation.archiveAfter)
    }

    @Test
    @DisplayName("every field on SafeguardFloor is read by the resolver")
    fun everyFloorFieldIsRead() {
        // A floor field the resolver ignores is a promise an organisation cannot keep. This
        // walks the type reflectively so that adding one without wiring it up fails here.
        val base = UserSafeguards(userId = SeedData.yusuf)
        // Instance fields only: the companion, its `NONE` constant, and the serializer
        // array the serialization plugin generates are all static.
        val floorFields = SafeguardFloor::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .filterNot { it.startsWith("$") }

        val everythingTightened = SafeguardFloor(
            contactableBy = org.fisabilillah.core.model.AudienceScope.NOBODY,
            minimumVerificationToContact = org.fisabilillah.core.model.VerificationLevel.BACKGROUND_CHECKED,
            crossGenderStructure =
                org.fisabilillah.core.model.CrossGenderConversationStructure.GUARDIAN_PRESENT,
            moderatorPresence = org.fisabilillah.core.model.ModeratorPresenceRule.ALWAYS,
            requireGroupContext = true,
            requireThirdParty = true,
            voiceCallsAllowedFrom = org.fisabilillah.core.model.AudienceScope.NOBODY,
            videoCallsAllowedFrom = org.fisabilillah.core.model.AudienceScope.NOBODY,
            oneToOneMeetingsAllowedFrom = org.fisabilillah.core.model.AudienceScope.NOBODY,
            meetingsMustBeInPublicPlaces = true,
            meetingsRequireThirdParty = true,
            forbidFormalIntroductions = true,
        )

        val effective = org.fisabilillah.core.policy.SafeguardResolver
            .effective(base.copy(acceptFormalIntroductions = true), everythingTightened)

        // Twelve fields, each with an observable consequence.
        assertEquals(12, floorFields.size, "SafeguardFloor gained a field: wire it up below")
        assertEquals(org.fisabilillah.core.model.AudienceScope.NOBODY, effective.contactableBy)
        assertEquals(
            org.fisabilillah.core.model.VerificationLevel.BACKGROUND_CHECKED,
            effective.minimumVerificationToContactMe,
        )
        assertEquals(
            org.fisabilillah.core.model.CrossGenderConversationStructure.GUARDIAN_PRESENT,
            effective.crossGenderStructure,
        )
        assertEquals(
            org.fisabilillah.core.model.ModeratorPresenceRule.ALWAYS,
            effective.moderatorPresence,
        )
        assertTrue(effective.requireGroupContext)
        assertTrue(effective.requireThirdParty)
        assertEquals(org.fisabilillah.core.model.AudienceScope.NOBODY, effective.voiceCallsAllowedFrom)
        assertEquals(org.fisabilillah.core.model.AudienceScope.NOBODY, effective.videoCallsAllowedFrom)
        assertEquals(
            org.fisabilillah.core.model.AudienceScope.NOBODY,
            effective.oneToOneMeetingsAllowedFrom,
        )
        assertTrue(effective.meetingsMustBeInPublicPlaces)
        assertTrue(effective.meetingsRequireThirdParty)
        assertTrue(!effective.acceptFormalIntroductions)
    }
}
