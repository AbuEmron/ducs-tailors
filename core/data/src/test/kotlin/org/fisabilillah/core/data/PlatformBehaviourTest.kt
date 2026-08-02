package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.fisabilillah.core.domain.OpportunitySearchCriteria
import org.fisabilillah.core.domain.PeopleSearchCriteria
import org.fisabilillah.core.domain.RefusalCode
import org.fisabilillah.core.domain.StartConversationCommand
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.ContactRequirement
import org.fisabilillah.core.model.ConversationRole
import org.fisabilillah.core.model.DonationFeatureFlags
import org.fisabilillah.core.model.ImpactVisibility
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.ProjectId
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.QuietHours
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.policy.ContactPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Organisation and community safeguard floors")
class SafeguardFloorScopeTest {

    @Test
    @DisplayName("a community floor applies inside that community's space")
    fun communityFloorAppliesInItsSpace() = runTest {
        val harness = Harness()

        // The youth programme requires a current background check from anyone getting in
        // touch about it. Abdullah is identity verified but not background checked.
        val refusal = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(
                    subject = PurposeSubject.Opportunity(ListingId("opp-youth-programme")),
                    reason = "I would like to help with the Saturday youth programme sessions.",
                    action = "Please let me know how to get involved.",
                ),
                openingMessage = "Salam, I would like to volunteer with the young people.",
            ),
        ).expectRefused()
        assertEquals(RefusalCode.NEEDS_VERIFICATION, refusal.code)
    }

    @Test
    @DisplayName("a community floor does not follow its volunteers into unrelated conversations")
    fun communityFloorDoesNotLeak() = runTest {
        val harness = Harness()

        // The same two people, the same accounts, a different subject. Yusuf runs the youth
        // programme; that must not make him unreachable about a masjid clean-up.
        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(
                    subject = PurposeSubject.Opportunity(ListingId("opp-masjid-cleanup")),
                ),
                openingMessage = "I can bring ladders and a set of tools.",
            ),
        ).expectSuccess()
        assertNotNull(started.conversation.id)
    }

    @Test
    @DisplayName("an organisation floor travels with its members")
    fun organizationFloorApplies() = runTest {
        val harness = Harness()

        // Northfield requires a moderator on cross-gender threads. Aminah is a member, and
        // her own settings already ask for her wali, so both apply.
        val started = harness.graph.startConversation(
            harness.principal(SeedData.yusuf),
            StartConversationCommand(
                recipientId = SeedData.aminah,
                purpose = harness.purpose(
                    kind = ContactPurposeKind.COMMUNITY_PROJECT,
                    subject = PurposeSubject.Project(ProjectId("project-masjid-booking")),
                    reason = "The masjid booking project needs someone to write the screen copy.",
                    action = "Would you review the wording on the booking screens?",
                ),
                openingMessage = "Salam sister, would you be able to look at the wording?",
            ),
        ).expectSuccess()

        assertTrue(ContactRequirement.MODERATOR_PRESENT in started.requirementsApplied)
        assertTrue(ContactRequirement.GUARDIAN_PRESENT in started.requirementsApplied)
        assertTrue(
            started.conversation.members.any { it.role == ConversationRole.MODERATOR },
            "the masjid's floor must actually put a moderator in the thread",
        )
    }
}

@DisplayName("Messaging behaviour")
class MessagingBehaviourTest {

    @Test
    fun `quiet hours stop new conversations but not existing ones`() = runTest {
        val harness = Harness()

        harness.graph.updateSafeguards(
            harness.principal(SeedData.yusuf),
            harness.store.safeguards.getValue(SeedData.yusuf).copy(
                quietHours = QuietHours(enabled = true, start = LocalTime(21, 0), end = LocalTime(7, 0)),
            ),
        ).expectSuccess()

        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders.",
            ),
        ).expectSuccess()

        harness.clock.setLocalTime(LocalTime(23, 30))

        // A reply in an open thread is unaffected — quiet hours are about being approached,
        // not about being silenced.
        harness.graph.sendMessage(
            harness.principal(SeedData.abdullah), started.conversation.id, "Also bringing a mop.",
        ).expectSuccess()

        val refusal = harness.graph.startConversation(
            harness.principal(SeedData.musa),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can help too.",
            ),
        ).expectRefused()
        assertEquals(RefusalCode.RATE_LIMITED, refusal.code)
    }

    @Test
    fun `the new-conversation rate limit is enforced end to end`() = runTest {
        val harness = Harness()
        val busy = harness.principal(SeedData.abdullah)
        val recipients = listOf(
            SeedData.yusuf, SeedData.musa, SeedData.ibrahim, SeedData.hafsa,
        )

        var opened = 0
        repeat(ContactPolicy.RateLimits.NEW_CONVERSATIONS_PER_WINDOW + 2) { index ->
            val outcome = harness.graph.startConversation(
                busy,
                StartConversationCommand(
                    recipientId = recipients[index % recipients.size],
                    purpose = harness.purpose(
                        reason = "About the masjid clean-up on Saturday, attempt number $index.",
                    ),
                    openingMessage = "I can help with the clean-up.",
                ),
            )
            if (outcome is org.fisabilillah.core.domain.Outcome.Success) opened++
        }

        assertTrue(
            opened <= ContactPolicy.RateLimits.NEW_CONVERSATIONS_PER_WINDOW,
            "opened $opened conversations, above the limit",
        )
    }

    @Test
    fun `oversight participants are announced in the thread rather than added silently`() = runTest {
        val harness = Harness()
        val started = harness.graph.startConversation(
            harness.principal(SeedData.yusuf),
            StartConversationCommand(
                recipientId = SeedData.aminah,
                purpose = harness.purpose(
                    kind = ContactPurposeKind.COMMUNITY_PROJECT,
                    subject = PurposeSubject.Project(ProjectId("project-masjid-booking")),
                    reason = "The masjid booking project needs someone to write the screen copy.",
                    action = "Would you review the wording on the booking screens?",
                ),
                openingMessage = "Salam sister, would you be able to look at the wording?",
            ),
        ).expectSuccess()

        val systemMessages = harness.graph.messages
            .forConversation(started.conversation.id).items
            .filter { it.kind == org.fisabilillah.core.model.MessageKind.SYSTEM }

        assertTrue(systemMessages.isNotEmpty())
        assertTrue(systemMessages.first().body.contains("Guardian present"))
    }

    @Test
    fun `either participant can end a conversation and nobody can speak afterwards`() = runTest {
        val harness = Harness()
        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders.",
            ),
        ).expectSuccess()

        harness.graph.endConversation(harness.principal(SeedData.yusuf), started.conversation.id)
            .expectSuccess()

        harness.graph.sendMessage(
            harness.principal(SeedData.abdullah), started.conversation.id, "Are you there?",
        ).expectRefused()
    }

    @Test
    fun `a member who is not part of a conversation cannot post to it`() = runTest {
        val harness = Harness()
        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders.",
            ),
        ).expectSuccess()

        harness.graph.sendMessage(
            harness.principal(SeedData.musa), started.conversation.id, "Who are you two?",
        ).expectRefused()
    }
}

@DisplayName("Discovery and participation")
class DiscoveryBehaviourTest {

    @Test
    @DisplayName("search results are not ranked by anything a person could game")
    fun searchIsNotRanked() = runTest {
        val harness = Harness()
        val results = harness.graph.searchPeople(
            harness.principal(SeedData.daniel),
            PeopleSearchCriteria(city = "Ashbourne"),
        ).expectSuccess()

        val names = results.items.map { it.displayName }
        assertEquals(names.sorted(), names, "results must be ordered by name, not by popularity")

        // And there is no field to rank by even if someone wanted to.
        val fields = org.fisabilillah.core.model.VisibleProfile::class.java.declaredFields
            .map { it.name.lowercase() }
        assertTrue(fields.none { it.contains("score") || it.contains("rank") || it.contains("popularity") })
    }

    @Test
    fun `a member who is not discoverable does not appear in search`() = runTest {
        val harness = Harness()
        harness.graph.updateSafeguards(
            harness.principal(SeedData.musa),
            harness.store.safeguards.getValue(SeedData.musa)
                .copy(profileDiscoverableBy = AudienceScope.NOBODY),
        ).expectSuccess()

        val results = harness.graph.searchPeople(
            harness.principal(SeedData.abdullah),
            PeopleSearchCriteria(city = "Ashbourne"),
        ).expectSuccess()

        assertTrue(results.items.none { it.id == SeedData.musa })
    }

    @Test
    fun `applying to work with young people requires a background check`() = runTest {
        val harness = Harness()

        val refusal = harness.graph.applyToOpportunity(
            harness.principal(SeedData.abdullah),
            ListingId("opp-youth-programme"),
            "I would like to help on Saturdays.",
        ).expectRefused()
        assertEquals(RefusalCode.NEEDS_VERIFICATION, refusal.code)
        assertTrue(refusal.message.contains("background check"))

        // Yusuf is background checked, so the same application succeeds.
        harness.graph.applyToOpportunity(
            harness.principal(SeedData.yusuf),
            ListingId("opp-youth-programme"),
            "I already coordinate this programme.",
        ).expectSuccess()
    }

    @Test
    fun `a sisters-only activity does not accept a brother's application`() = runTest {
        val harness = Harness()
        val refusal = harness.graph.applyToOpportunity(
            harness.principal(SeedData.musa),
            ListingId("opp-elder-transport"),
            "I can drive.",
        ).expectRefused()
        assertTrue(refusal.message.contains("sisters only", ignoreCase = true))
    }

    @Test
    fun `a teacher who takes same-gender students only is enforced, not merely displayed`() = runTest {
        val harness = Harness()
        val refusal = harness.graph.enrollInLearning(
            harness.principal(SeedData.daniel),
            ListingId("learn-arabic-beginners"),
        ).expectRefused()
        assertTrue(refusal.message.contains("sisters only", ignoreCase = true))
    }

    @Test
    fun `an opportunity for young people cannot be published without safeguarding`() = runTest {
        val harness = Harness()
        val existing = harness.store.opportunities.getValue(ListingId("opp-youth-programme"))

        val invalid = harness.graph.createOpportunity(
            harness.principal(SeedData.yusuf),
            existing.copy(
                id = ListingId("opp-new-youth"),
                backgroundCheckRequired = false,
                childSafeguardingRequired = false,
            ),
        ).expectInvalid()
        assertTrue(invalid.errors.any { it.field == "backgroundCheckRequired" })
    }

    @Test
    fun `the home digest is about the member's own obligations, not other people's activity`() = runTest {
        val harness = Harness()
        val digest = harness.graph.homeDigest(harness.principal(SeedData.yusuf)).expectSuccess()

        assertEquals("Yusuf A.", digest.greetingName)
        assertTrue(digest.safetyReminder.isNotBlank())
        assertEquals(ImpactVisibility.PRIVATE, digest.impact.visibility)

        // No field anywhere on the digest exposes another member's standing.
        val fields = digest::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.none { it.contains("follower") || it.contains("trending") || it.contains("popular") })
    }

    @Test
    fun `a completed commitment counts only once the organiser confirms it`() = runTest {
        val harness = Harness()
        val volunteer = harness.principal(SeedData.abdullah)
        val opportunityId = ListingId("opp-masjid-cleanup")
        val opportunity = harness.store.opportunities.getValue(opportunityId)

        val commitment = harness.graph.commitmentActions.create(
            volunteer,
            org.fisabilillah.core.model.CommitmentSubject.Opportunity(opportunityId),
            title = opportunity.title,
            startsAt = opportunity.startsAt,
            endsAt = opportunity.endsAt,
            place = opportunity.place,
        ).expectSuccess()

        harness.clock.setNow(opportunity.startsAt)
        harness.graph.commitmentActions.checkIn(volunteer, commitment.id).expectSuccess()
        harness.graph.commitmentActions.checkOut(volunteer, commitment.id).expectSuccess()

        val beforeConfirmation = harness.graph.trust.recordFor(SeedData.abdullah)
        assertEquals(4, beforeConfirmation.commitmentsCompleted)

        // The volunteer cannot confirm their own attendance.
        harness.graph.commitmentActions
            .confirmByOrganizer(volunteer, commitment.id, attended = true)
            .expectRefused()

        harness.graph.commitmentActions.confirmByOrganizer(
            harness.principal(SeedData.yusuf), commitment.id, attended = true,
        ).expectSuccess()

        assertEquals(5, harness.graph.trust.recordFor(SeedData.abdullah).commitmentsCompleted)
    }
}

@DisplayName("Seed data")
class SeedDataTest {

    @Test
    fun `the sample data covers every scenario the product specification names`() = runTest {
        val harness = Harness()
        val store = harness.store

        assertTrue(store.learningOfferings.containsKey(ListingId("learn-arabic-beginners")))
        assertTrue(store.learningOfferings.containsKey(ListingId("learn-tajwid")))
        assertTrue(store.learningOfferings.containsKey(ListingId("learn-new-muslim")))
        assertTrue(store.learningOfferings.containsKey(ListingId("learn-electrical")))
        assertTrue(store.opportunities.containsKey(ListingId("opp-cv-clinic")))
        assertTrue(store.opportunities.containsKey(ListingId("opp-food-distribution")))
        assertTrue(store.opportunities.containsKey(ListingId("opp-masjid-cleanup")))
        assertTrue(store.opportunities.containsKey(ListingId("opp-elder-transport")))
        assertTrue(store.opportunities.containsKey(ListingId("opp-youth-programme")))
        assertTrue(store.projects.containsKey(ProjectId("project-masjid-booking")))
        assertTrue(store.campaigns.isNotEmpty())
        assertTrue(store.introductionSettings.containsKey(SeedData.aminah))
    }

    @Test
    fun `no sample record contains a routable contact detail`() = runTest {
        val harness = Harness()
        val everything = buildString {
            harness.store.profiles.values.forEach { append(it.toString()) }
            harness.store.organizations.values.forEach { append(it.toString()) }
            harness.store.trustedContacts.values.forEach { append(it.toString()) }
        }
        // Reserved ranges only: nothing here can reach a real person.
        val emails = Regex("""[\w.+-]+@[\w.-]+""").findAll(everything).map { it.value }.toList()
        assertTrue(
            emails.all { it.endsWith(".test") },
            "sample emails must use the reserved .test domain: $emails",
        )
        val phones = Regex("""\+?\d[\d\s]{7,}\d""").findAll(everything).map { it.value }.toList()
        assertTrue(
            phones.all { it.replace(" ", "").contains("2079460") },
            "sample phone numbers must use the reserved range: $phones",
        )
    }

    @Test
    fun `a peer-led class is labelled as peer learning wherever it is shown`() = runTest {
        val harness = Harness()
        val offering = harness.store.learningOfferings.getValue(ListingId("learn-new-muslim"))
        assertTrue(offering.isPeerLearning)
        assertTrue(offering.capacityDisclaimer.contains("not authoritative religious instruction"))
    }

    @Test
    fun `the verified campaign still cannot take a payment`() = runTest {
        val harness = Harness()
        val campaign = harness.store.campaigns.values.first()
        assertEquals(
            org.fisabilillah.core.model.CampaignVerification.VERIFIED,
            campaign.verification,
        )
        assertFalse(campaign.canAcceptDonations, "payments must stay behind the flag")
        assertFalse(DonationFeatureFlags.paymentsEnabled)
        assertFalse(campaign.zakatEligible)
    }

    @Test
    fun `every seeded member has safeguards from the moment their account exists`() = runTest {
        val harness = Harness()
        for (userId in harness.store.profiles.keys) {
            assertNotNull(
                harness.store.safeguards[userId],
                "$userId has no safeguards, so they could be contacted before choosing how",
            )
        }
    }

    @Test
    fun `each preset is represented in the sample data`() = runTest {
        val harness = Harness()
        val presets = harness.store.safeguards.values.map { it.presetName }.toSet()
        assertTrue(SafeguardPresetName.COMMUNITY_SERVICE in presets)
        assertTrue(SafeguardPresetName.FAMILY_AND_WALI_GUIDED in presets)
        assertTrue(SafeguardPresetName.LEARNING_ONLY in presets)
        assertTrue(SafeguardPresetName.ORGANIZATION_MANAGED in presets)
        assertTrue(SafeguardPresetName.MAXIMUM_PRIVACY in presets)
    }

    @Test
    fun `every listing in the sample data states how a volunteer knows they are finished`() = runTest {
        val harness = Harness()
        val open = harness.graph.opportunities.search(
            OpportunitySearchCriteria(openOnly = false, limit = 100),
        ).items
        assertTrue(open.isNotEmpty())
        for (opportunity in open) {
            assertTrue(
                opportunity.completionCriteria.isNotBlank(),
                "${opportunity.title} does not say when the work is done",
            )
        }
    }

    @Test
    fun `the imam is the only account presented as a verified scholar`() = runTest {
        val harness = Harness()
        val scholars = harness.store.profiles.values.filter {
            it.teachingCapacity == org.fisabilillah.core.model.TeachingCapacity.VERIFIED_SCHOLAR
        }
        assertEquals(1, scholars.size)
        assertEquals(SeedData.ibrahim, scholars.first().id)
        assertTrue(scholars.first().verificationLevel atLeast VerificationLevel.IDENTITY_VERIFIED)
    }
}
