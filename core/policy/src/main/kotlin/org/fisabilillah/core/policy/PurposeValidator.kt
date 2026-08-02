package org.fisabilillah.core.policy

import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.PurposeSubject

/** A field-level problem with a submitted form. */
public data class ValidationError(
    val field: String,
    val message: String,
)

/** The outcome of validating something a user typed. */
public sealed interface ValidationResult<out T> {
    public data class Valid<T>(val value: T) : ValidationResult<T>
    public data class Invalid(val errors: List<ValidationError>) : ValidationResult<Nothing>

    public val isValid: Boolean get() = this is Valid

    public fun errorsOrEmpty(): List<ValidationError> =
        if (this is Invalid) errors else emptyList()
}

/**
 * Checks the structured opening that every new conversation must carry.
 *
 * The length floors are not bureaucracy. A person who has to write twenty considered words
 * about why they are getting in touch, and ten about what they are actually asking for,
 * cannot send "hey" to forty people — and the recipient gets something they can judge
 * before deciding whether to engage at all.
 */
public object PurposeValidator {

    /** Fragments that suggest the "purpose" is really an opening line. */
    private val FLIRTATIOUS_OPENERS = listOf(
        "you look", "your picture", "your photo", "beautiful", "gorgeous", "handsome",
        "pretty", "cute", "attractive", "single?", "are you single", "marry me",
        "wanna chat", "want to chat", "just wanted to say hi", "get to know you better",
        "dm me", "text me", "my number is", "whatsapp me", "snap me", "insta",
    )

    public fun validate(purpose: ContactPurpose): ValidationResult<ContactPurpose> {
        val errors = mutableListOf<ValidationError>()

        val reason = purpose.reasonForContact.trim()
        val action = purpose.requestedAction.trim()

        if (reason.length < ContactPurpose.MIN_REASON_LENGTH) {
            errors += ValidationError(
                field = "reasonForContact",
                message = "Please say a little more about why you are getting in touch " +
                    "(at least ${ContactPurpose.MIN_REASON_LENGTH} characters).",
            )
        }
        if (reason.length > ContactPurpose.MAX_REASON_LENGTH) {
            errors += ValidationError(
                field = "reasonForContact",
                message = "Please keep this under ${ContactPurpose.MAX_REASON_LENGTH} characters.",
            )
        }
        if (action.length < ContactPurpose.MIN_ACTION_LENGTH) {
            errors += ValidationError(
                field = "requestedAction",
                message = "Please say what you are asking this member to do " +
                    "(at least ${ContactPurpose.MIN_ACTION_LENGTH} characters).",
            )
        }
        if (action.length > ContactPurpose.MAX_ACTION_LENGTH) {
            errors += ValidationError(
                field = "requestedAction",
                message = "Please keep this under ${ContactPurpose.MAX_ACTION_LENGTH} characters.",
            )
        }

        if (purpose.kind.requiresSubject && purpose.subject == null) {
            errors += ValidationError(
                field = "subject",
                message = "Choose the listing, class, request, or project this is about.",
            )
        }
        val subject = purpose.subject
        if (subject != null && !subjectMatchesKind(purpose.kind, subject)) {
            errors += ValidationError(
                field = "subject",
                message = "That does not match the kind of request you selected.",
            )
        }
        if (!purpose.kind.userSelectable) {
            errors += ValidationError(
                field = "kind",
                message = "That kind of conversation cannot be started from here.",
            )
        }

        val combined = "$reason $action".lowercase()
        val opener = FLIRTATIOUS_OPENERS.firstOrNull { combined.contains(it) }
        if (opener != null) {
            errors += ValidationError(
                field = "reasonForContact",
                message = "This does not read like a request for help, teaching, or " +
                    "community work. Conversations here need a service purpose. If you are " +
                    "interested in marriage, that is a separate process led by a guardian.",
            )
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid(purpose.copy(reasonForContact = reason, requestedAction = action))
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    private fun subjectMatchesKind(kind: ContactPurposeKind, subject: PurposeSubject): Boolean =
        when (kind) {
            ContactPurposeKind.VOLUNTEER_OPPORTUNITY -> subject is PurposeSubject.Opportunity
            ContactPurposeKind.LEARNING_REQUEST,
            ContactPurposeKind.TEACHING_OFFER,
            -> subject is PurposeSubject.LearningOffer

            ContactPurposeKind.COMMUNITY_PROJECT -> subject is PurposeSubject.Project
            ContactPurposeKind.ASSISTANCE_REQUEST -> subject is PurposeSubject.Request
            ContactPurposeKind.ORGANIZATION_INQUIRY ->
                subject is PurposeSubject.Organization || subject is PurposeSubject.Community

            ContactPurposeKind.MODERATION_MATTER,
            ContactPurposeKind.FORMAL_INTRODUCTION,
            -> true
        }

    /**
     * A short line shown at the top of every thread, so that both people can see at a
     * glance what this conversation is for and notice when it has stopped being about that.
     */
    public fun bannerText(purpose: ContactPurpose, subjectTitle: String): String =
        "${purpose.kind.displayName} · $subjectTitle"
}
