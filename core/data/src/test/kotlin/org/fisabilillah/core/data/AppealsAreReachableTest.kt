package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.domain.TakeModerationActionUseCase
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.AppealState
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.ModerationActionType
import org.fisabilillah.core.model.ModerationCase
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportSeverity
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.domain.Principal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * A restricted member can find out what happened and argue with it.
 *
 * `ReviewAppealUseCase` has existed, been wired into the graph and been tested at the
 * policy layer since the first release, and no screen or route reached it at either end.
 * A right of appeal that nothing in the product can reach is not a right of appeal; it is
 * a paragraph in a document.
 */
@DisplayName("Appeals")
class AppealsAreReachableTest {

    /** Restricts a member the way a moderator actually would, and returns the case. */
    private suspend fun Harness.restrict(
        moderator: Principal,
        subject: org.fisabilillah.core.model.UserId,
        reason: String = "Repeatedly contacting members after being asked to stop.",
    ): ModerationCaseId {
        val now = clock.now()
        val case = graph.moderation.saveCase(
            ModerationCase(
                id = ModerationCaseId("case-appeal-test"),
                subjectUserId = subject,
                reportIds = emptyList(),
                category = ReportCategory.HARASSMENT,
                severity = ReportSeverity.HIGH,
                summary = reason,
                createdAt = now,
                updatedAt = now,
            ),
        )
        graph.takeModerationAction(
            moderator,
            TakeModerationActionUseCase.Command(
                caseId = case.id,
                type = ModerationActionType.FEATURE_RESTRICTED,
                targetUserId = subject,
                restrictedCapability = RestrictedCapability.START_CONVERSATIONS,
                rationale = reason,
                durationDays = 30,
            ),
        ).expectSuccess()
        return case.id
    }

    private fun Harness.moderator() = Principal(SeedData.moderator, setOf(AccountRole.MODERATOR))

    private fun Harness.secondModerator() =
        Principal(SeedData.safetyAdmin, setOf(AccountRole.SAFETY_ADMINISTRATOR))

    @Test
    @DisplayName("a restricted member can see what was restricted and why, in the moderator's own words")
    fun theMemberCanSeeTheReason() = runTest {
        val harness = Harness()
        val reason = "Repeatedly contacting members after being asked to stop."
        harness.restrict(harness.moderator(), SeedData.abdullah, reason)

        val records = harness.graph.myModerationRecord(
            harness.principal(SeedData.abdullah),
        ).expectSuccess()

        val record = records.single()
        assertEquals(RestrictedCapability.START_CONVERSATIONS, record.restriction.capability)
        assertEquals(reason, record.restriction.reason)
        assertNotNull(record.case)
        assertTrue(record.canAppeal)
    }

    @Test
    @DisplayName("a member with nothing restricted sees an empty list rather than a refusal")
    fun anUnrestrictedMemberSeesNothing() = runTest {
        val harness = Harness()
        val records = harness.graph.myModerationRecord(
            harness.principal(SeedData.yusuf),
        ).expectSuccess()
        assertTrue(records.isEmpty())
    }

    @Test
    @DisplayName("the member appeals, and a second moderator upholds it, and the restriction lifts")
    fun theWholeRoundTrip() = runTest {
        val harness = Harness()
        val caseId = harness.restrict(harness.moderator(), SeedData.abdullah)

        harness.graph.reviewAppeal.submit(
            harness.principal(SeedData.abdullah),
            caseId,
            "The person I contacted is my cousin and had asked me to call her father.",
        ).expectSuccess()

        // The safety team sees it waiting.
        val queue = harness.graph.appealQueue(harness.secondModerator()).expectSuccess()
        assertEquals(1, queue.size)
        assertTrue(queue.single().reviewableByMe)

        harness.graph.reviewAppeal.decide(
            harness.secondModerator(),
            queue.single().appeal.id,
            AppealState.UPHELD,
            "Confirmed with the member's family. The restriction is lifted.",
        ).expectSuccess()

        // The restriction is gone, and the member can see the outcome and the reasons.
        assertTrue(
            harness.graph.restrictions.activeFor(SeedData.abdullah, harness.clock.now()).isEmpty(),
        )
        assertTrue(
            harness.graph.myModerationRecord(harness.principal(SeedData.abdullah))
                .expectSuccess()
                .isEmpty(),
        )
        assertTrue(
            harness.store.notifications.any {
                it.userId == SeedData.abdullah && it.body.contains("restriction is lifted")
            },
            "the member must be told the outcome",
        )
    }

    @Test
    @DisplayName("the moderator who imposed the restriction cannot review the appeal against it")
    fun theOriginalDeciderIsExcluded() = runTest {
        val harness = Harness()
        val caseId = harness.restrict(harness.moderator(), SeedData.abdullah)
        harness.graph.reviewAppeal.submit(
            harness.principal(SeedData.abdullah),
            caseId,
            "I think this was a misunderstanding.",
        ).expectSuccess()

        val appealId = harness.graph.moderation.openAppeals().single().id

        val refusal = harness.graph.reviewAppeal.decide(
            harness.moderator(),
            appealId,
            AppealState.REJECTED,
            "Standing by my decision.",
        ).expectRefused()
        assertTrue(refusal.message.contains("not involved in the original decision"))

        // ...and the queue says so up front rather than offering a button that is refused.
        val queue = harness.graph.appealQueue(harness.moderator()).expectSuccess()
        assertFalse(queue.single().reviewableByMe)
    }

    @Test
    @DisplayName("a member cannot appeal a decision about somebody else")
    fun appealsAreYourOwn() = runTest {
        val harness = Harness()
        val caseId = harness.restrict(harness.moderator(), SeedData.abdullah)

        val refusal = harness.graph.reviewAppeal.submit(
            harness.principal(SeedData.yusuf),
            caseId,
            "I would like to appeal on his behalf.",
        ).expectRefused()
        assertTrue(refusal.message.contains("your own account"))
    }

    @Test
    @DisplayName("an empty appeal is refused with a message rather than filed")
    fun anEmptyAppealIsRefused() = runTest {
        val harness = Harness()
        val caseId = harness.restrict(harness.moderator(), SeedData.abdullah)

        val outcome = harness.graph.reviewAppeal.submit(
            harness.principal(SeedData.abdullah),
            caseId,
            "   ",
        ) as Outcome.Invalid
        assertEquals("statement", outcome.errors.single().field)
        assertTrue(harness.graph.moderation.openAppeals().isEmpty())
    }

    @Test
    @DisplayName("an ordinary member cannot read the appeal queue")
    fun theQueueIsStaffOnly() = runTest {
        val harness = Harness()
        val refusal = harness.graph.appealQueue(
            harness.principal(SeedData.yusuf),
        ).expectRefused()
        assertTrue(refusal.message.contains("safety team"))
    }

    @Test
    @DisplayName("a rejected appeal leaves the restriction in place and is recorded")
    fun rejectionIsRecorded() = runTest {
        val harness = Harness()
        val caseId = harness.restrict(harness.moderator(), SeedData.abdullah)
        harness.graph.reviewAppeal.submit(
            harness.principal(SeedData.abdullah),
            caseId,
            "I do not think I did anything wrong.",
        ).expectSuccess()

        val appealId = harness.graph.moderation.openAppeals().single().id
        harness.graph.reviewAppeal.decide(
            harness.secondModerator(),
            appealId,
            AppealState.REJECTED,
            "The messages continued after two clear requests to stop.",
        ).expectSuccess()

        assertTrue(
            harness.graph.restrictions.activeFor(SeedData.abdullah, harness.clock.now()).isNotEmpty(),
        )
        assertTrue(
            harness.graph.auditLog.recent(50).any { it.action == AuditAction.APPEAL_DECIDED },
            "the decision must be in the append-only log",
        )

        // And the member is shown the state of their appeal rather than a fresh empty form.
        val record = harness.graph.myModerationRecord(harness.principal(SeedData.abdullah))
            .expectSuccess()
            .single()
        assertEquals(AppealState.REJECTED, record.appeal?.state)
        assertFalse(record.canAppeal)
    }
}
