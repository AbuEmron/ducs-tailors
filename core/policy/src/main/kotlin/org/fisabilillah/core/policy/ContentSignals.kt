package org.fisabilillah.core.policy

import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.SafetySignal
import org.fisabilillah.core.model.SafetySignalKind
import org.fisabilillah.core.model.SignalConfidence

/**
 * Lightweight, explainable checks on message text.
 *
 * Four things this deliberately is not. It is not a classifier, it is not a censor, it
 * does not act, and it does not run on anything a member has not been told about — the
 * consent record for automated safety processing spells out exactly these categories in
 * exactly these words.
 *
 * What it produces is a reason to look, ordered by how strong the reason is. Every one of
 * these signals is carried into the safety queue with the phrase that triggered it, so a
 * moderator can see immediately that "brother" matched a keyword and dismiss it, rather
 * than being handed a score they cannot interrogate.
 *
 * Keeping this in the shared core rather than on a server has a cost — the rules are
 * visible to anyone who decompiles the app — and a benefit: nothing is sent anywhere to
 * produce these, so the checks that flag a thread run on the device that typed it.
 */
public object ContentSignals {

    private val SEXUAL_TERMS = listOf(
        "sexy", "hot pics", "nudes", "send pics", "your body", "kiss", "in bed",
        "turn me on", "make love",
    )

    private val FLIRTATION_TERMS = listOf(
        "beautiful", "gorgeous", "handsome", "cute", "pretty face", "your eyes",
        "my heart", "falling for you", "i love you", "miss you", "dream about you",
        "you're the one", "marry me", "be mine", "babe", "baby", "darling", "sweetheart",
    )

    private val OFF_PLATFORM_TERMS = listOf(
        "whatsapp", "telegram", "snapchat", "instagram", "insta", "signal app",
        "add me on", "text me at", "call me on", "my number", "dm me on", "off this app",
        "outside this app", "email me at",
    )

    private val FINANCIAL_TERMS = listOf(
        "send money", "wire transfer", "gift card", "crypto", "bitcoin", "usdt",
        "western union", "cash app", "investment opportunity", "guaranteed return",
        "urgent transfer", "bank details", "sort code", "iban", "paypal me",
    )

    private val GROOMING_TERMS = listOf(
        "don't tell anyone", "dont tell anyone", "our secret", "keep this between us",
        "delete this chat", "your parents don't need", "you're mature for", "how old are you really",
    )

    private val CONTACT_DETAIL_PATTERN = Regex(
        """(\+?\d[\d\s\-().]{7,}\d)|([\w.+-]+@[\w-]+\.[\w.]{2,})""",
    )

    /**
     * Signals for a single message.
     *
     * [conversationIsCrossGender] raises the weight of flirtation signals rather than
     * creating them: the same sentence between two brothers arranging a food delivery and
     * between a man and a woman in a tutoring thread does not carry the same risk, and
     * pretending otherwise produces either noise or blindness.
     */
    public fun forMessage(
        body: String,
        conversationIsCrossGender: Boolean,
    ): List<SafetySignal> {
        val text = body.lowercase()
        val signals = mutableListOf<SafetySignal>()

        matched(text, SEXUAL_TERMS)?.let {
            signals += SafetySignal(
                kind = SafetySignalKind.POSSIBLE_SEXUAL_CONTENT,
                confidence = SignalConfidence.HIGH,
                explanation = "The message contains the phrase \"$it\".",
            )
        }
        matched(text, FLIRTATION_TERMS)?.let {
            signals += SafetySignal(
                kind = SafetySignalKind.POSSIBLE_FLIRTATION,
                confidence = if (conversationIsCrossGender) SignalConfidence.MEDIUM else SignalConfidence.LOW,
                explanation = "The message contains the phrase \"$it\", which may be " +
                    "affectionate rather than related to the conversation's purpose.",
            )
        }
        matched(text, OFF_PLATFORM_TERMS)?.let {
            signals += SafetySignal(
                kind = SafetySignalKind.POSSIBLE_OFF_PLATFORM_MOVE,
                confidence = SignalConfidence.MEDIUM,
                explanation = "The message mentions \"$it\". Moving a conversation off the " +
                    "platform removes the safeguards the other person chose.",
            )
        }
        matched(text, FINANCIAL_TERMS)?.let {
            signals += SafetySignal(
                kind = SafetySignalKind.POSSIBLE_FINANCIAL_SOLICITATION,
                confidence = SignalConfidence.HIGH,
                explanation = "The message mentions \"$it\".",
            )
        }
        matched(text, GROOMING_TERMS)?.let {
            signals += SafetySignal(
                kind = SafetySignalKind.POSSIBLE_FLIRTATION,
                confidence = SignalConfidence.HIGH,
                explanation = "The message contains \"$it\", a phrase associated with " +
                    "isolating someone from the people around them.",
            )
        }
        if (CONTACT_DETAIL_PATTERN.containsMatchIn(body)) {
            signals += SafetySignal(
                kind = SafetySignalKind.POSSIBLE_CONTACT_DETAIL_SHARING,
                confidence = SignalConfidence.LOW,
                explanation = "The message appears to contain a phone number or email address.",
            )
        }

        return signals
    }

    /**
     * Whether a thread has wandered away from what it was opened for.
     *
     * Produces a gentle, visible reminder in the thread rather than a report. Most drift
     * is innocent — two people arranging a masjid clean-up end up talking about a job
     * opening — and the right response to that is a note, not a moderator.
     */
    public fun purposeDrift(
        purpose: ContactPurpose,
        recentMessageBodies: List<String>,
    ): SafetySignal? {
        if (recentMessageBodies.size < MIN_MESSAGES_BEFORE_DRIFT_CHECK) return null

        val purposeWords =
            contentWords(purpose.reasonForContact + " " + purpose.requestedAction)
        if (purposeWords.isEmpty()) return null

        val recentWords = contentWords(recentMessageBodies.takeLast(DRIFT_WINDOW).joinToString(" "))
        if (recentWords.isEmpty()) return null

        val overlap = purposeWords.intersect(recentWords).size
        val ratio = overlap.toDouble() / purposeWords.size

        return if (ratio < DRIFT_THRESHOLD) {
            SafetySignal(
                kind = SafetySignalKind.POSSIBLE_PURPOSE_DRIFT,
                confidence = SignalConfidence.LOW,
                explanation = "Recent messages have little in common with what this " +
                    "conversation was opened for.",
            )
        } else {
            null
        }
    }

    /** The reminder posted into a thread that has drifted. Written to be easy to receive. */
    public fun driftReminder(purpose: ContactPurpose): String =
        "A reminder that this conversation was opened about ${purpose.kind.displayName.lowercase()}: " +
            "\"${purpose.requestedAction}\". If you need to talk about something else, it is " +
            "usually better to start a conversation for that instead."

    /**
     * Whether a member is opening threads at a rate that warrants a look. Never an
     * automatic restriction — a new volunteer coordinator legitimately does this.
     */
    public fun conversationVolumeSignal(startedInWindow: Int): SafetySignal? =
        if (startedInWindow >= HIGH_VOLUME_THRESHOLD) {
            SafetySignal(
                kind = SafetySignalKind.HIGH_VOLUME_NEW_CONVERSATIONS,
                confidence = SignalConfidence.MEDIUM,
                explanation = "$startedInWindow new conversations were opened in a short period.",
            )
        } else {
            null
        }

    private fun matched(haystack: String, needles: List<String>): String? =
        needles.firstOrNull { haystack.contains(it) }

    /**
     * The words in [text] that actually carry its subject.
     *
     * Filtering common words matters more here than it looks. Without it, a thread that has
     * moved entirely off topic still shares "about", "would" and "please" with its stated
     * purpose, which is enough overlap to keep the drift check quiet — the check reads as
     * working while catching nothing.
     */
    private fun contentWords(text: String): Set<String> =
        text.lowercase()
            .split(NON_WORD)
            .filter { it.length > 3 && it !in STOP_WORDS }
            .toSet()

    private val STOP_WORDS = setOf(
        "about", "after", "again", "also", "because", "been", "before", "being", "both",
        "could", "does", "doing", "down", "each", "from", "have", "here", "into", "just",
        "know", "like", "make", "many", "more", "most", "much", "must", "need", "only",
        "other", "over", "same", "should", "some", "such", "than", "that", "their", "them",
        "then", "there", "these", "they", "this", "those", "through", "time", "very",
        "want", "well", "were", "what", "when", "where", "which", "while", "will", "with",
        "would", "your", "yours", "please", "thanks", "thank", "salam", "assalamu",
        "alaikum", "walaikum", "jazak", "allah", "khayr", "inshallah", "brother", "sister",
    )

    private val NON_WORD = Regex("""\W+""")

    /**
     * Below this proportion of shared subject words, the conversation is treated as having
     * moved on. Chosen to be forgiving: a false reminder is a small irritation, and a
     * missed one costs nothing beyond a reminder nobody saw.
     */
    private const val DRIFT_THRESHOLD = 0.25
    private const val DRIFT_WINDOW = 10
    private const val MIN_MESSAGES_BEFORE_DRIFT_CHECK = 6
    private const val HIGH_VOLUME_THRESHOLD = 8

    /**
     * What members are told, verbatim, before any of this runs. Kept next to the code it
     * describes so the two cannot drift apart.
     */
    public const val DISCLOSURE: String =
        "Messages are checked automatically for a narrow set of safety signals: sexual " +
            "content, patterns associated with grooming, scam patterns, attempts to move " +
            "the conversation off the platform, and drift away from a conversation's stated " +
            "purpose. These checks run on your device. They flag a conversation for a human " +
            "to look at; they never restrict an account, remove content, or decide anything " +
            "on their own. No one reads your messages unless a report is made or a flag is " +
            "raised."
}
