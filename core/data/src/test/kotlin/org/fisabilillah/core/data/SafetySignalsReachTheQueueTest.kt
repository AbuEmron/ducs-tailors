package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.StartConversationCommand
import org.fisabilillah.core.model.CaseState
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.SafetySignalKind
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.policy.SignalEscalationPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * The on-device checks now protect somebody.
 *
 * Before this, `SendMessageUseCase` computed a list of signals, returned it in
 * `SentMessage`, and every caller dropped it on the floor. The detection was thoroughly
 * tested and completely inert: a man could send the same message to thirty sisters and
 * the platform's own checks would notice all thirty times and remember none of them.
 *
 * These tests run the whole path — real use cases, real repositories — and assert on what
 * a moderator would actually find in the queue.
 */
@DisplayName("Safety signals reach the moderation queue")
class SafetySignalsReachTheQueueTest {

    private suspend fun Harness.openThread(from: UserId, to: UserId, opening: String) =
        graph.startConversation(
            principal(from),
            StartConversationCommand(
                recipientId = to,
                purpose = purpose(),
                openingMessage = opening,
            ),
        ).expectSuccess().conversation.id

    @Test
    @DisplayName("a flagged message is kept even when it is not enough to raise anything")
    fun signalsAreStoredBeforeTheyAreEnough() = runTest {
        val harness = Harness()
        val conversation = harness.openThread(
            SeedData.abdullah,
            SeedData.yusuf,
            "I can bring ladders on Saturday.",
        )

        val sent = harness.graph.sendMessage(
            harness.principal(SeedData.abdullah),
            conversation,
            "You have a beautiful way of putting things, brother.",
        ).expectSuccess()

        assertTrue(sent.signals.isNotEmpty(), "the check should have fired")
        assertNull(sent.raisedCaseId, "one signal between two brothers is not a case")

        val stored = harness.graph.moderation.signalsBy(
            SeedData.abdullah,
            harness.clock.now() - 7.days,
        )
        assertEquals(1, stored.size)
        assertEquals(SafetySignalKind.POSSIBLE_FLIRTATION, stored.single().signal.kind)
        assertEquals(conversation, stored.single().conversationId)
        assertNull(stored.single().caseId)
    }

    @Test
    @DisplayName("the same approach to three different people becomes a case")
    fun aPatternAcrossPeopleRaisesACase() = runTest {
        val harness = Harness()
        // Three people this sender is genuinely permitted to contact. The obvious version
        // of this test -- one man approaching three women -- cannot be written against the
        // seed, because the safeguards refuse the second and third conversations outright.
        // That is the platform working, and it is why the pattern this test uses is one
        // that does not depend on gender: repeatedly pushing people off the platform,
        // which is an attempt to get around whatever safeguards they chose.
        val recipients = listOf(SeedData.yusuf, SeedData.ibrahim, SeedData.musa)

        var raised: org.fisabilillah.core.model.ModerationCaseId? = null
        for (recipient in recipients) {
            val conversation = harness.openThread(
                SeedData.abdullah,
                recipient,
                "Salam, I saw the listing and I can help on Saturday.",
            )
            harness.clock.advance(5.minutes)
            val sent = harness.graph.sendMessage(
                harness.principal(SeedData.abdullah),
                conversation,
                "Easier if you add me on whatsapp, this app is a hassle.",
            ).expectSuccess()
            raised = raised ?: sent.raisedCaseId
        }

        assertNotNull(raised, "three approaches to three people should reach the queue")

        val case = harness.store.cases.getValue(raised!!)
        assertEquals(SeedData.abdullah, case.subjectUserId)
        assertEquals(ReportCategory.FLIRTATION_OR_PURSUIT, case.category)
        assertEquals(CaseState.OPEN, case.state)
        assertTrue(
            case.reportIds.isEmpty(),
            "nobody reported this; a moderator must be able to tell that apart from a person asking for help",
        )

        // The evidence travels with the case rather than being re-derived.
        val evidence = harness.graph.moderation.signalsForCase(case.id)
        assertEquals(3, evidence.size)
        assertEquals(3, evidence.map { it.conversationId }.distinct().size)
    }

    @Test
    @DisplayName("nothing is done to the account, and the sender is not told")
    fun escalationRestrictsNobodyAndWarnsNobody() = runTest {
        val harness = Harness()
        val conversation = harness.openThread(
            SeedData.abdullah,
            SeedData.yusuf,
            "Salam, about the food parcels this weekend.",
        )

        val notificationsBefore = harness.store.notifications.count { it.userId == SeedData.abdullah }

        val sent = harness.graph.sendMessage(
            harness.principal(SeedData.abdullah),
            conversation,
            "Send money to this bitcoin wallet and you will get a guaranteed return.",
        ).expectSuccess()

        assertNotNull(sent.raisedCaseId, "confident financial solicitation should not wait")

        // The message was still delivered. Detection is not moderation, and silently
        // swallowing a message would be a decision no human made.
        val thread = harness.graph.messages.forConversation(conversation).items
        assertTrue(thread.any { it.id == sent.message.id })

        // No restriction was applied by anyone or anything.
        assertTrue(
            harness.graph.restrictions.activeFor(SeedData.abdullah, harness.clock.now()).isEmpty(),
            "an automated check must never restrict an account",
        )

        // And the sender learns nothing, so the checks cannot be probed by rewording.
        val notificationsAfter = harness.store.notifications.count { it.userId == SeedData.abdullah }
        assertEquals(notificationsBefore, notificationsAfter)
    }

    @Test
    @DisplayName("a second flagged message does not open a second case about the same person")
    fun onlyOneOpenAutomatedCasePerPerson() = runTest {
        val harness = Harness()
        val conversation = harness.openThread(
            SeedData.abdullah,
            SeedData.yusuf,
            "Salam, about the food parcels this weekend.",
        )

        val first = harness.graph.sendMessage(
            harness.principal(SeedData.abdullah),
            conversation,
            "Send money to this bitcoin wallet, guaranteed return.",
        ).expectSuccess()
        harness.clock.advance(5.minutes)
        val second = harness.graph.sendMessage(
            harness.principal(SeedData.abdullah),
            conversation,
            "Use western union, it is an investment opportunity.",
        ).expectSuccess()

        assertEquals(first.raisedCaseId, second.raisedCaseId)

        val automatedCases = harness.store.cases.values.filter {
            it.subjectUserId == SeedData.abdullah && it.reportIds.isEmpty()
        }
        assertEquals(1, automatedCases.size, "the queue must not fill with duplicates of one concern")

        // Both messages' signals hang off that one case.
        assertEquals(2, harness.graph.moderation.signalsForCase(first.raisedCaseId!!).size)
    }

    @Test
    @DisplayName("an ordinary message leaves no trace in the safety store at all")
    fun cleanMessagesRecordNothing() = runTest {
        val harness = Harness()
        val conversation = harness.openThread(
            SeedData.abdullah,
            SeedData.yusuf,
            "I can bring ladders on Saturday.",
        )

        harness.graph.sendMessage(
            harness.principal(SeedData.abdullah),
            conversation,
            "I will be there at eight with the ladders and a spare bucket.",
        ).expectSuccess()

        assertTrue(
            harness.graph.moderation
                .signalsBy(SeedData.abdullah, harness.clock.now() - SignalEscalationPolicy.WINDOW)
                .isEmpty(),
            "a message that trips nothing should not be recorded anywhere",
        )
        assertTrue(harness.store.cases.isEmpty())
    }
}
