package org.fisabilillah.core.policy

import kotlinx.datetime.Instant
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.RecordedSignal
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportSeverity
import org.fisabilillah.core.model.SafetySignal
import org.fisabilillah.core.model.SafetySignalId
import org.fisabilillah.core.model.SafetySignalKind
import org.fisabilillah.core.model.SignalConfidence
import org.fisabilillah.core.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private val NOW = Instant.parse("2026-08-02T12:00:00Z")
private val SENDER = UserId("sender-1")

private var counter = 0

private fun signal(
    kind: SafetySignalKind,
    confidence: SignalConfidence = SignalConfidence.MEDIUM,
    conversation: String = "conv-1",
    at: Instant = NOW,
    caseId: ModerationCaseId? = null,
): RecordedSignal = RecordedSignal(
    id = SafetySignalId("sig-${counter++}"),
    messageId = MessageId("msg-${counter}"),
    conversationId = ConversationId(conversation),
    senderId = SENDER,
    signal = SafetySignal(
        kind = kind,
        confidence = confidence,
        explanation = "The message contains the phrase \"beautiful\".",
    ),
    caseId = caseId,
    observedAt = at,
)

/**
 * The rules that decide when a pile of signals becomes somebody's job.
 *
 * The two failure modes being guarded against pull in opposite directions: a policy that
 * escalates too readily buries the queue so the real reports are never reached, and one
 * that waits for certainty never fires at all. Both leave the same person unprotected.
 */
class SignalEscalationPolicyTest {

    @Test
    @DisplayName("nothing recorded is nothing to escalate")
    fun emptyIsQuiet() {
        assertEquals(SignalEscalationPolicy.Escalation.None, SignalEscalationPolicy.assess(emptyList(), NOW))
    }

    @Test
    @DisplayName("one affectionate message is not a case")
    fun singleFlirtationIsNotACase() {
        val result = SignalEscalationPolicy.assess(
            listOf(signal(SafetySignalKind.POSSIBLE_FLIRTATION)),
            NOW,
        )
        assertEquals(SignalEscalationPolicy.Escalation.None, result)
    }

    @Test
    @DisplayName("a low-confidence signal never escalates on its own, however many there are")
    fun lowConfidenceNeverEscalatesAlone() {
        val phoneNumbers = (1..6).map {
            signal(
                SafetySignalKind.POSSIBLE_CONTACT_DETAIL_SHARING,
                confidence = SignalConfidence.LOW,
                conversation = "conv-$it",
            )
        }
        assertEquals(
            SignalEscalationPolicy.Escalation.None,
            SignalEscalationPolicy.assess(phoneNumbers, NOW),
        )
    }

    @Test
    @DisplayName("the same kind of signal three times becomes a case")
    fun repetitionBecomesAPattern() {
        val result = SignalEscalationPolicy.assess(
            (1..3).map { signal(SafetySignalKind.POSSIBLE_FLIRTATION, at = NOW - it.hours) },
            NOW,
        ) as SignalEscalationPolicy.Escalation.RaiseCase

        assertEquals(ReportCategory.FLIRTATION_OR_PURSUIT, result.category)
        assertEquals(3, result.basis.size)
    }

    @Test
    @DisplayName("the same message to three different people is a pattern sooner than three to one")
    fun spreadCountsForMore() {
        // Two signals, but in two conversations: not yet.
        val two = SignalEscalationPolicy.assess(
            listOf(
                signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "a"),
                signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "b"),
            ),
            NOW,
        )
        assertEquals(SignalEscalationPolicy.Escalation.None, two)

        val three = SignalEscalationPolicy.assess(
            listOf(
                signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "a"),
                signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "b"),
                signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "c"),
            ),
            NOW,
        )
        assertTrue(three is SignalEscalationPolicy.Escalation.RaiseCase)
    }

    @Test
    @DisplayName("one confident sexual-content signal does not wait for a pattern")
    fun seriousSignalsActOnFirstOccurrence() {
        val result = SignalEscalationPolicy.assess(
            listOf(signal(SafetySignalKind.POSSIBLE_SEXUAL_CONTENT, SignalConfidence.HIGH)),
            NOW,
        ) as SignalEscalationPolicy.Escalation.RaiseCase

        assertEquals(ReportCategory.SEXUAL_CONTENT, result.category)
        assertEquals(ReportSeverity.CRITICAL, result.severity)
    }

    @Test
    @DisplayName("an attempt to isolate someone is filed as grooming, not as flirtation")
    fun isolationIsNotFiledAsRomance() {
        val result = SignalEscalationPolicy.assess(
            listOf(signal(SafetySignalKind.POSSIBLE_ISOLATION_ATTEMPT, SignalConfidence.HIGH)),
            NOW,
        ) as SignalEscalationPolicy.Escalation.RaiseCase

        assertEquals(ReportCategory.GROOMING, result.category)
        assertEquals(ReportSeverity.CRITICAL, result.severity)
    }

    @Test
    @DisplayName("a pattern is filed a notch below a report of the same thing")
    fun countedEvidenceDoesNotOutrankAPerson() {
        val result = SignalEscalationPolicy.assess(
            (1..3).map { signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "c$it") },
            NOW,
        ) as SignalEscalationPolicy.Escalation.RaiseCase

        // FLIRTATION_OR_PURSUIT is HIGH when a person reports it.
        assertEquals(ReportSeverity.HIGH, ReportCategory.FLIRTATION_OR_PURSUIT.severity)
        assertEquals(ReportSeverity.MEDIUM, result.severity)
    }

    @Test
    @DisplayName("signals older than the window are not counted")
    fun oldSignalsExpire() {
        val stale = (1..5).map {
            signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "c$it", at = NOW - 30.days)
        }
        assertEquals(SignalEscalationPolicy.Escalation.None, SignalEscalationPolicy.assess(stale, NOW))
    }

    @Test
    @DisplayName("a signal already attached to a case is not counted again")
    fun countedSignalsDoNotRaiseASecondCase() {
        val alreadyUsed = (1..5).map {
            signal(
                SafetySignalKind.POSSIBLE_FLIRTATION,
                conversation = "c$it",
                caseId = ModerationCaseId("case-1"),
            )
        }
        assertEquals(
            SignalEscalationPolicy.Escalation.None,
            SignalEscalationPolicy.assess(alreadyUsed, NOW),
        )
    }

    @Test
    @DisplayName("when two patterns qualify, the more serious one is the one filed")
    fun theWorseConcernWins() {
        val mixed = (1..3).map { signal(SafetySignalKind.POSSIBLE_CONTACT_DETAIL_SHARING, conversation = "c$it") } +
            (1..3).map { signal(SafetySignalKind.POSSIBLE_ISOLATION_ATTEMPT, SignalConfidence.MEDIUM, "d$it") }

        val result = SignalEscalationPolicy.assess(mixed, NOW) as SignalEscalationPolicy.Escalation.RaiseCase
        assertEquals(ReportCategory.GROOMING, result.category)
    }

    @Test
    @DisplayName("the summary states the evidence and reaches no conclusion about the person")
    fun theSummaryDoesNotEditorialise() {
        val result = SignalEscalationPolicy.assess(
            (1..3).map { signal(SafetySignalKind.POSSIBLE_FLIRTATION, conversation = "c$it") },
            NOW,
        ) as SignalEscalationPolicy.Escalation.RaiseCase

        val summary = result.summary.lowercase()
        assertTrue(summary.contains("3 messages were flagged"), result.summary)
        assertTrue(summary.contains("across 3 conversations"), result.summary)
        assertTrue(
            summary.contains("nothing has been done to the account"),
            "the moderator must be told no action was taken automatically: ${result.summary}",
        )
        assertTrue(summary.contains("read the conversation before deciding"), result.summary)

        for (verdict in listOf("predator", "harasser", "guilty", "should be", "recommend", "obviously")) {
            assertFalse(summary.contains(verdict), "the summary must not editorialise: ${result.summary}")
        }
    }
}
