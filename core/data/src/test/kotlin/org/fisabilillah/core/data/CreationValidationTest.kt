package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.CreateOpportunityUseCase
import org.fisabilillah.core.domain.CreateServiceRequestUseCase
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.model.BeneficiaryType
import org.fisabilillah.core.model.DeliveryFormat
import org.fisabilillah.core.model.RequestUrgency
import org.fisabilillah.core.model.RequestVisibility
import org.fisabilillah.core.model.ServiceCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The two creation forms tell people what is wrong instead of crashing.
 *
 * Both models check their invariants in `init`, and both use cases used to be handed an
 * already-constructed model built from raw form input. The consequence was not a poor
 * message — it was an `IllegalArgumentException` on a blank title, and a background-check
 * refusal that could never be reached because the constructor threw first.
 */
@DisplayName("Creating an opportunity or a request")
class CreationValidationTest {

    private fun Harness.opportunityCommand(
        title: String = "Saturday food parcel packing",
        summary: String = "Packing and labelling parcels in the hall.",
        category: ServiceCategory = ServiceCategory.FOOD_DISTRIBUTION,
        city: String = "Northfield",
        volunteersNeeded: Int = 6,
        completionCriteria: String = "All parcels labelled and stacked by the door.",
        backgroundCheckRequired: Boolean = false,
        childSafeguardingRequired: Boolean = false,
    ) = CreateOpportunityUseCase.Command(
        title = title,
        summary = summary,
        category = category,
        beneficiaryType = BeneficiaryType.NEIGHBOURHOOD,
        city = city,
        countryCode = "GB",
        format = DeliveryFormat.IN_PERSON,
        volunteersNeeded = volunteersNeeded,
        completionCriteria = completionCriteria,
        startsAt = clock.now() + 7.days,
        endsAt = clock.now() + 7.days + 4.hours,
        backgroundCheckRequired = backgroundCheckRequired,
        childSafeguardingRequired = childSafeguardingRequired,
    )

    private fun Harness.requestCommand(
        title: String = "Lift to a hospital appointment",
        description: String = "Thursday morning, and I cannot manage the bus with the crutches.",
        city: String = "Northfield",
        visibility: RequestVisibility = RequestVisibility.IDENTIFIED,
        exactAddress: String? = null,
        peopleAffected: Int = 1,
    ) = CreateServiceRequestUseCase.Command(
        title = title,
        description = description,
        category = ServiceCategory.TRANSPORTATION,
        urgency = RequestUrgency.SOON,
        visibility = visibility,
        city = city,
        countryCode = "GB",
        exactAddress = exactAddress,
        peopleAffected = peopleAffected,
    )

    // ── Opportunities ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a blank title is a message, not an exception")
    fun blankTitleIsReported() = runTest {
        val harness = Harness()
        val before = harness.store.opportunities.size

        val outcome = harness.graph.createOpportunity(
            harness.principal(SeedData.abdullah),
            harness.opportunityCommand(title = "   "),
        )

        val invalid = outcome as Outcome.Invalid
        assertEquals("title", invalid.errors.single().field)
        assertEquals(before, harness.store.opportunities.size, "nothing should have been saved")
    }

    @Test
    @DisplayName("every problem with the form is reported at once, not one per attempt")
    fun allErrorsComeBackTogether() = runTest {
        val harness = Harness()
        val outcome = harness.graph.createOpportunity(
            harness.principal(SeedData.abdullah),
            harness.opportunityCommand(
                title = "",
                summary = "",
                city = "",
                volunteersNeeded = 0,
                completionCriteria = "",
            ),
        ) as Outcome.Invalid

        assertEquals(
            setOf("title", "summary", "city", "volunteersNeeded", "completionCriteria"),
            outcome.errors.map { it.field }.toSet(),
        )
    }

    @Test
    @DisplayName("the background-check message an organiser used never to see")
    fun theSafeguardingMessageIsReachable() = runTest {
        val harness = Harness()
        val outcome = harness.graph.createOpportunity(
            harness.principal(SeedData.abdullah),
            harness.opportunityCommand(
                category = ServiceCategory.YOUTH_MENTORSHIP,
                backgroundCheckRequired = false,
                childSafeguardingRequired = false,
            ),
        ) as Outcome.Invalid

        val fields = outcome.errors.map { it.field }
        assertTrue("backgroundCheckRequired" in fields, fields.toString())
        assertTrue("childSafeguardingRequired" in fields, fields.toString())
        assertTrue(
            outcome.errors.first { it.field == "backgroundCheckRequired" }
                .message.contains("extra protection"),
        )
    }

    @Test
    @DisplayName("a safeguarding requirement is added by the category even when the form says no")
    fun theCategoryWinsOverTheForm() = runTest {
        val harness = Harness()
        val saved = harness.graph.createOpportunity(
            harness.principal(SeedData.abdullah),
            harness.opportunityCommand(
                category = ServiceCategory.YOUTH_MENTORSHIP,
                backgroundCheckRequired = true,
                childSafeguardingRequired = true,
            ),
        ).expectSuccess()

        assertTrue(saved.backgroundCheckRequired)
        assertTrue(saved.childSafeguardingRequired)
    }

    @Test
    @DisplayName("the organiser is the signed-in account, whatever the form contained")
    fun theOrganiserIsTheCaller() = runTest {
        val harness = Harness()
        val saved = harness.graph.createOpportunity(
            harness.principal(SeedData.abdullah),
            harness.opportunityCommand(),
        ).expectSuccess()

        assertEquals(SeedData.abdullah, saved.organizerId)
    }

    @Test
    @DisplayName("a valid opportunity is saved once and is findable")
    fun happyPath() = runTest {
        val harness = Harness()
        val saved = harness.graph.createOpportunity(
            harness.principal(SeedData.abdullah),
            harness.opportunityCommand(),
        ).expectSuccess()

        assertNotNull(harness.graph.opportunities.find(saved.id))
        assertEquals("Saturday food parcel packing", saved.title)
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a request with no description is refused rather than saved empty")
    fun blankRequestIsReported() = runTest {
        val harness = Harness()
        val outcome = harness.graph.createServiceRequest(
            harness.principal(SeedData.khadija),
            harness.requestCommand(description = ""),
        ) as Outcome.Invalid

        assertEquals("description", outcome.errors.single().field)
    }

    @Test
    @DisplayName("hiding your name and attaching your address is queried before it is stored")
    fun anonymityAndAnAddressDoNotSitTogether() = runTest {
        val harness = Harness()
        val outcome = harness.graph.createServiceRequest(
            harness.principal(SeedData.khadija),
            harness.requestCommand(
                visibility = RequestVisibility.ANONYMOUS_TO_MEMBERS,
                exactAddress = "14 Mill Lane",
            ),
        ) as Outcome.Invalid

        assertEquals("exactAddress", outcome.errors.single().field)
    }

    @Test
    @DisplayName("a street address is stored but never part of what a viewer sees")
    fun theAddressStaysPrivate() = runTest {
        val harness = Harness()
        val saved = harness.graph.createServiceRequest(
            harness.principal(SeedData.khadija),
            harness.requestCommand(exactAddress = "14 Mill Lane"),
        ).expectSuccess()

        assertNotNull(saved.place.exact)
        assertFalse(saved.exactLocationDisclosed)
        assertTrue(saved.exactLocationDisclosedTo.isEmpty())
        assertFalse(saved.exactLocationVisibleTo(SeedData.abdullah))
        assertTrue(saved.exactLocationVisibleTo(SeedData.khadija))
    }

    @Test
    @DisplayName("support totals stay off unless the requester turned them on")
    fun totalsAreOffByDefault() = runTest {
        val harness = Harness()
        val saved = harness.graph.createServiceRequest(
            harness.principal(SeedData.khadija),
            harness.requestCommand(),
        ).expectSuccess()

        assertFalse(saved.showSupportTotals)
    }

    @Test
    @DisplayName("the requester is the signed-in account, and the request expires")
    fun theRequesterIsTheCaller() = runTest {
        val harness = Harness()
        val saved = harness.graph.createServiceRequest(
            harness.principal(SeedData.khadija),
            harness.requestCommand(),
        ).expectSuccess()

        assertEquals(SeedData.khadija, saved.requesterId)
        assertTrue(saved.expiresAt > harness.clock.now())
        assertNull(saved.assignedHelperId)
    }
}
