package org.fisabilillah.core.policy

import kotlinx.datetime.Instant
import org.fisabilillah.core.model.RecordedSignal
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportSeverity
import org.fisabilillah.core.model.SafetySignalKind
import org.fisabilillah.core.model.SignalConfidence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * When a pile of signals becomes something a person should look at.
 *
 * [ContentSignals] answers "did this message contain anything worth noticing". It does not
 * and must not answer "is this person a problem", because one message almost never
 * supports that. This does the second job, and it does it by counting: a phrase once is
 * noise, the same kind of phrase to six different people in a week is a pattern, and the
 * difference between them is the whole reason for keeping a history.
 *
 * ## What this deliberately does not do
 *
 * **It never restricts anybody.** The only thing it produces is a case in the safety
 * queue with the signals attached. Every restriction on this platform is applied by a
 * named moderator through `TakeModerationActionUseCase`, is written to an append-only
 * audit log, and is appealable. An automated system that could mute someone would be a
 * system that mutes the wrong someone, and the person it silences would have nobody to
 * argue with.
 *
 * **It does not tell the sender.** A member is not notified that their message tripped a
 * check. Telling them would turn the checks into a puzzle to be solved — reword until the
 * warning stops — and the people most motivated to solve it are the ones the checks exist
 * for. The consent record for automated safety processing says plainly that this happens;
 * that is the honest disclosure, and it is made once, up front, rather than as a hint at
 * the moment of detection.
 *
 * **It does not act on low confidence alone.** A phone number in a message between two
 * organisers arranging a lift is the normal case, not the suspicious one.
 */
public object SignalEscalationPolicy {

    /** How far back a pattern is assembled from. */
    public val WINDOW: Duration = 7.days

    /** Distinct medium-or-better signals within [WINDOW] that make a pattern. */
    public const val PATTERN_THRESHOLD: Int = 3

    /**
     * Distinct people on the receiving end that make a pattern by itself, at a lower
     * signal count. Someone sending one affectionate message to one person is a
     * misjudgement; sending one to each of three is a search.
     */
    public const val DISTINCT_CONVERSATION_THRESHOLD: Int = 3

    /**
     * Kinds serious enough that a single high-confidence occurrence is worth a human
     * looking, without waiting for a pattern.
     *
     * Sexual content and isolation attempts are here because the cost of being slow about
     * them is measured in harm to a person, and the cost of being wrong is that a
     * moderator reads a conversation and closes the case. Financial solicitation is here
     * because fraud moves faster than a weekly window.
     */
    private val ACT_ON_FIRST_OCCURRENCE: Map<SafetySignalKind, ReportCategory> = mapOf(
        SafetySignalKind.POSSIBLE_SEXUAL_CONTENT to ReportCategory.SEXUAL_CONTENT,
        SafetySignalKind.POSSIBLE_ISOLATION_ATTEMPT to ReportCategory.GROOMING,
        SafetySignalKind.POSSIBLE_FINANCIAL_SOLICITATION to ReportCategory.FRAUD,
    )

    /** Which category a pattern of a given kind is filed under. */
    private val PATTERN_CATEGORY: Map<SafetySignalKind, ReportCategory> = mapOf(
        SafetySignalKind.POSSIBLE_FLIRTATION to ReportCategory.FLIRTATION_OR_PURSUIT,
        SafetySignalKind.POSSIBLE_SEXUAL_CONTENT to ReportCategory.SEXUAL_CONTENT,
        SafetySignalKind.POSSIBLE_ISOLATION_ATTEMPT to ReportCategory.GROOMING,
        SafetySignalKind.POSSIBLE_FINANCIAL_SOLICITATION to ReportCategory.FRAUD,
        SafetySignalKind.POSSIBLE_OFF_PLATFORM_MOVE to ReportCategory.FLIRTATION_OR_PURSUIT,
        SafetySignalKind.POSSIBLE_CONTACT_DETAIL_SHARING to ReportCategory.PRIVACY_VIOLATION,
        SafetySignalKind.REPEATED_CONTACT_AFTER_DECLINE to ReportCategory.HARASSMENT,
        SafetySignalKind.HIGH_VOLUME_NEW_CONVERSATIONS to ReportCategory.SPAM,
        SafetySignalKind.POSSIBLE_PURPOSE_DRIFT to ReportCategory.FLIRTATION_OR_PURSUIT,
    )

    public sealed interface Escalation {
        /** Nothing to do. The signals are still stored; they are simply not a case yet. */
        public data object None : Escalation

        public data class RaiseCase(
            val category: ReportCategory,
            val severity: ReportSeverity,
            /** What a moderator reads first. States the evidence, claims nothing. */
            val summary: String,
            /** The signals this case rests on, so they can be marked as counted. */
            val basis: List<RecordedSignal>,
        ) : Escalation
    }

    /**
     * Assess everything recorded against one sender.
     *
     * [recent] should already be limited to that sender and to signals that have not
     * already contributed to a case — a signal counted once must not keep raising new
     * cases every time another message arrives, which would bury the queue in duplicates
     * of the same concern.
     */
    public fun assess(recent: List<RecordedSignal>, now: Instant): Escalation {
        val inWindow = recent.filter { it.caseId == null && it.observedAt >= now - WINDOW }
        if (inWindow.isEmpty()) return Escalation.None

        // Serious and confident: do not wait for a second occurrence.
        val urgent = inWindow.firstOrNull {
            it.signal.confidence == SignalConfidence.HIGH &&
                it.signal.kind in ACT_ON_FIRST_OCCURRENCE
        }
        if (urgent != null) {
            val category = ACT_ON_FIRST_OCCURRENCE.getValue(urgent.signal.kind)
            // Everything of the same kind in the window rides along, so the moderator sees
            // whether this was once or the sixth time.
            val basis = inWindow.filter { it.signal.kind == urgent.signal.kind }
            return Escalation.RaiseCase(
                category = category,
                severity = category.severity,
                summary = summarise(category, basis, urgent = true),
                basis = basis,
            )
        }

        // Otherwise look for a pattern. Low-confidence signals are counted towards the
        // spread of conversations but never towards the threshold on their own.
        val considered = inWindow.filter { it.signal.confidence != SignalConfidence.LOW }
        if (considered.isEmpty()) return Escalation.None

        val byKind = considered.groupBy { it.signal.kind }
        val pattern = byKind.entries
            .filter { (_, signals) ->
                signals.size >= PATTERN_THRESHOLD ||
                    signals.map { it.conversationId }.distinct().size >= DISTINCT_CONVERSATION_THRESHOLD
            }
            // If several kinds qualify, take the one the platform treats most seriously.
            .maxByOrNull { (kind, signals) ->
                val category = PATTERN_CATEGORY[kind] ?: ReportCategory.HARASSMENT
                category.severity.rank * 1000 + signals.size
            }
            ?: return Escalation.None

        val (kind, signals) = pattern
        val category = PATTERN_CATEGORY[kind] ?: ReportCategory.HARASSMENT
        return Escalation.RaiseCase(
            category = category,
            // A pattern assembled by counting is not the same evidence as a report from a
            // person, so it is filed a notch below the category's headline severity unless
            // that would take it below medium. It still reaches a human; it does not jump
            // the queue ahead of somebody who actually asked for help.
            severity = softened(category.severity),
            summary = summarise(category, signals, urgent = false),
            basis = signals,
        )
    }

    private fun softened(severity: ReportSeverity): ReportSeverity = when (severity) {
        ReportSeverity.CRITICAL -> ReportSeverity.HIGH
        ReportSeverity.HIGH -> ReportSeverity.MEDIUM
        else -> severity
    }

    /**
     * The text a moderator reads first.
     *
     * Written to describe what was observed and nothing else. No conclusion, no
     * recommendation, and no adjective about the person — the queue entry has to survive
     * being read by a moderator who is tired, and the failure mode of a summary that
     * editorialises is a decision made before the conversation is opened.
     */
    private fun summarise(
        category: ReportCategory,
        signals: List<RecordedSignal>,
        urgent: Boolean,
    ): String {
        val conversations = signals.map { it.conversationId }.distinct().size
        val opening = if (urgent) {
            "Automated check: ${category.displayName.lowercase()}."
        } else {
            "Automated check: a repeated pattern consistent with " +
                "${category.displayName.lowercase()}."
        }
        val count = if (signals.size == 1) {
            "One message was flagged"
        } else {
            "${signals.size} messages were flagged"
        }
        val spread = if (conversations == 1) {
            "in one conversation"
        } else {
            "across $conversations conversations"
        }
        val examples = signals.take(3).joinToString(" ") { it.signal.explanation }
        return "$opening $count $spread. $examples " +
            "This was raised by an automated check and nothing has been done to the " +
            "account. Read the conversation before deciding."
    }
}
