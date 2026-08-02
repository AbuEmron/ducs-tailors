package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.CommitmentUseCase
import org.fisabilillah.core.domain.ReportSafetyIncidentUseCase
import org.fisabilillah.core.model.CommitmentId
import org.fisabilillah.core.model.CommitmentSubject
import org.fisabilillah.core.model.IncidentKind
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportSeverity
import org.fisabilillah.core.model.ServiceCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * Recognition, incidents and devices — three types that existed with nothing reaching them.
 *
 * The endorsement one is the interesting case. The product principles forbid vanity
 * metrics, rankings and follower counts, so adding "somebody said you did well" needed to
 * be done in a way that cannot become a leaderboard.
 */
@DisplayName("Recognition, incidents and devices")
class RecognitionTest {

    /** A completed commitment on a seeded opportunity, confirmed by its organiser. */
    private suspend fun Harness.completedCommitment(): Pair<CommitmentId, org.fisabilillah.core.model.UserId> {
        val listing = store.opportunities.values.first { it.organizerId == SeedData.yusuf }
        val volunteer = principal(SeedData.abdullah)

        val commitment = graph.commitmentActions.create(
            volunteer,
            CommitmentSubject.Opportunity(listing.id),
            "Helping at ${listing.title}",
            clock.now() + 1.hours,
            clock.now() + 4.hours,
            null,
        ).expectSuccess()

        clock.advance(1.hours)
        graph.commitmentActions.checkIn(volunteer, commitment.id).expectSuccess()
        clock.advance(3.hours)
        graph.commitmentActions.checkOut(volunteer, commitment.id).expectSuccess()
        graph.commitmentActions
            .confirmByOrganizer(principal(SeedData.yusuf), commitment.id, attended = true)
            .expectSuccess()

        return commitment.id to listing.organizerId
    }

    // ── Endorsements ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the organiser who was there can say how it went, once")
    fun anEndorsementIsBoundToAnOccasion() = runTest {
        val harness = Harness()
        val (commitmentId, organiser) = harness.completedCommitment()

        val endorsement = harness.graph.endorseTask(
            harness.principal(organiser),
            commitmentId,
            ServiceCategory.FOOD_DISTRIBUTION,
            "Arrived early and stayed to clear up.",
        ).expectSuccess()

        assertEquals(SeedData.abdullah, endorsement.aboutUserId)
        assertEquals(
            1,
            harness.graph.trust.recordFor(SeedData.abdullah)
                .taskEndorsements[ServiceCategory.FOOD_DISTRIBUTION],
        )

        assertTrue(
            harness.graph.endorseTask(
                harness.principal(organiser),
                commitmentId,
                ServiceCategory.FOOD_DISTRIBUTION,
                "Saying it again.",
            ).expectRefused().message.contains("already said this"),
        )
    }

    @Test
    @DisplayName("a bystander cannot endorse: there is no button on a profile")
    fun onlySomebodyWhoWasThere() = runTest {
        val harness = Harness()
        val (commitmentId, _) = harness.completedCommitment()

        assertTrue(
            harness.graph.endorseTask(
                harness.principal(SeedData.daniel),
                commitmentId,
                ServiceCategory.FOOD_DISTRIBUTION,
                "Sounds good!",
            ).expectRefused().message.contains("Only the organiser"),
        )
    }

    @Test
    @DisplayName("nobody endorses their own work")
    fun noSelfEndorsement() = runTest {
        val harness = Harness()
        val (commitmentId, _) = harness.completedCommitment()

        assertTrue(
            harness.graph.endorseTask(
                harness.principal(SeedData.abdullah),
                commitmentId,
                ServiceCategory.FOOD_DISTRIBUTION,
                null,
            ).expectRefused().message.contains("your own work"),
        )
    }

    @Test
    @DisplayName("nothing can be said until the work is actually finished")
    fun nothingBeforeTheWorkIsDone() = runTest {
        val harness = Harness()
        val listing = harness.store.opportunities.values.first { it.organizerId == SeedData.yusuf }
        val commitment = harness.graph.commitmentActions.create(
            harness.principal(SeedData.abdullah),
            CommitmentSubject.Opportunity(listing.id),
            "Helping",
            harness.clock.now() + 1.hours,
            harness.clock.now() + 4.hours,
            null,
        ).expectSuccess()

        assertTrue(
            harness.graph.endorseTask(
                harness.principal(SeedData.yusuf),
                commitment.id,
                ServiceCategory.FOOD_DISTRIBUTION,
                null,
            ).expectRefused().message.contains("once the work is finished"),
        )
    }

    // ── Incidents ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an ordinary incident is recorded without accusing anybody")
    fun anIncidentIsNotAReport() = runTest {
        val harness = Harness()
        val casesBefore = harness.store.cases.size

        val incident = harness.graph.reportIncident(
            harness.principal(SeedData.yusuf),
            ReportSafetyIncidentUseCase.Command(
                kind = IncidentKind.INJURY,
                description = "A volunteer cut their hand on a broken crate. First aid given.",
            ),
        ).expectSuccess()

        assertEquals(IncidentKind.INJURY, incident.kind)
        assertNull(incident.caseId, "an injury is not an accusation")
        assertEquals(casesBefore, harness.store.cases.size)
    }

    @Test
    @DisplayName("a supervision failure raises a case whatever the reporter thinks")
    fun supervisionFailuresEscalate() = runTest {
        val harness = Harness()
        val incident = harness.graph.reportIncident(
            harness.principal(SeedData.yusuf),
            ReportSafetyIncidentUseCase.Command(
                kind = IncidentKind.NO_SUPERVISION,
                description = "Two volunteers were left alone with the group for twenty minutes.",
            ),
        ).expectSuccess()

        assertNotNull(incident.caseId)
        val case = harness.store.cases.getValue(incident.caseId!!)
        assertEquals(ReportCategory.UNSAFE_VOLUNTEERING, case.category)
    }

    @Test
    @DisplayName("anything on a listing involving young people is filed as child safety")
    fun childSafetyOutranksEverything() = runTest {
        val harness = Harness()
        val youth = harness.store.opportunities.values.first { it.childSafeguardingRequired }

        val incident = harness.graph.reportIncident(
            harness.principal(SeedData.yusuf),
            ReportSafetyIncidentUseCase.Command(
                kind = IncidentKind.PROPERTY_DAMAGE,
                description = "A window was broken during the session.",
                listingId = ListingId(youth.id.value),
            ),
        ).expectSuccess()

        val case = harness.store.cases.getValue(incident.caseId!!)
        assertEquals(ReportCategory.CHILD_SAFETY, case.category)
        assertEquals(ReportSeverity.CRITICAL, case.severity)
    }

    @Test
    @DisplayName("an incident needs a description in the reporter's own words")
    fun anIncidentNeedsWords() = runTest {
        val harness = Harness()
        val outcome = harness.graph.reportIncident(
            harness.principal(SeedData.yusuf),
            ReportSafetyIncidentUseCase.Command(IncidentKind.OTHER, "   "),
        ) as org.fisabilillah.core.domain.Outcome.Invalid
        assertEquals("description", outcome.errors.single().field)
    }

    // ── Devices ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the first sign-in is not announced; the second is")
    fun theAlertIsNotNoise() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.abdullah)

        harness.graph.devices.record(member, "Pixel 7", "android", null).expectSuccess()
        assertFalse(
            harness.store.notifications.any { it.userId == SeedData.abdullah },
            "telling somebody about a new sign-in the first time they sign in teaches them to ignore it",
        )

        harness.graph.devices.record(member, "Old laptop", "web", null).expectSuccess()
        assertTrue(
            harness.store.notifications.any {
                it.userId == SeedData.abdullah && it.title.contains("New sign-in")
            },
        )
    }

    @Test
    @DisplayName("a member can end another session, but only their own")
    fun sessionsAreYourOwn() = runTest {
        val harness = Harness()
        val mine = harness.graph.devices
            .record(harness.principal(SeedData.abdullah), "Pixel 7", "android", null)
            .expectSuccess()

        assertTrue(
            harness.graph.devices
                .revoke(harness.principal(SeedData.yusuf), mine.id)
                .expectRefused().message.contains("your own sessions"),
        )

        harness.graph.devices.revoke(harness.principal(SeedData.abdullah), mine.id).expectSuccess()
        assertFalse(harness.graph.deviceSessions.find(mine.id)!!.isActive)
    }

    @Test
    @DisplayName("the this-wasn't-me button ends everything except the device asking")
    fun revokeAllOthers() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.abdullah)
        val keep = harness.graph.devices.record(member, "Pixel 7", "android", null).expectSuccess()
        harness.graph.devices.record(member, "Old laptop", "web", null).expectSuccess()
        harness.graph.devices.record(member, "Library computer", "web", null).expectSuccess()

        val ended = harness.graph.devices.revokeAllOthers(member, keep.id).expectSuccess()
        assertEquals(2, ended)
        assertTrue(harness.graph.deviceSessions.find(keep.id)!!.isActive)
    }
}
