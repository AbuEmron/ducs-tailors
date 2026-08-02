package org.fisabilillah.core.domain

import org.fisabilillah.core.policy.ValidationError

/**
 * The result of asking the domain layer to do something.
 *
 * Deliberately not [Result]: a refusal here is usually an ordinary, expected answer — the
 * recipient does not accept messages from strangers — rather than an exception. Modelling
 * those as failures to be thrown makes the calling code treat them as bugs, and the UI
 * then has nothing sensible to show.
 */
public sealed interface Outcome<out T> {

    public data class Success<T>(val value: T) : Outcome<T>

    /** The action is not permitted. [message] is safe to show the user as-is. */
    public data class Refused(
        val message: String,
        val code: RefusalCode = RefusalCode.NOT_PERMITTED,
    ) : Outcome<Nothing>

    /** The input was malformed. Errors are field-addressed so a form can show them inline. */
    public data class Invalid(val errors: List<ValidationError>) : Outcome<Nothing>

    /** Something referenced does not exist, or the caller may not know that it does. */
    public data class NotFound(val what: String) : Outcome<Nothing>

    public val isSuccess: Boolean get() = this is Success

    public fun valueOrNull(): T? = (this as? Success)?.value

    public fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Refused -> this
        is Invalid -> this
        is NotFound -> this
    }

    public companion object {
        public fun refused(message: String, code: RefusalCode = RefusalCode.NOT_PERMITTED): Outcome<Nothing> =
            Refused(message, code)

        public fun invalid(field: String, message: String): Outcome<Nothing> =
            Invalid(listOf(ValidationError(field, message)))
    }
}

/**
 * A coarse machine-readable reason, for callers that need to branch (retry timers, offering
 * a verification prompt) without matching on message text.
 */
public enum class RefusalCode {
    NOT_PERMITTED,
    BLOCKED,
    RESTRICTED,
    RATE_LIMITED,
    NEEDS_VERIFICATION,
    NEEDS_GUARDIAN,
    FEATURE_DISABLED,
    ACCOUNT_INACTIVE,
    CONFLICT,
}

/** A clock, so that every time-dependent rule can be tested deterministically. */
public interface AppClock {
    public fun now(): org.fisabilillah.core.model.Timestamp

    /** The year, used for age checks. Separate so a test does not need a calendar. */
    public fun currentYear(): Int

    /** The local time in [timeZoneId], used for a member's quiet hours. */
    public fun localTimeIn(timeZoneId: String): kotlinx.datetime.LocalTime
}

/** The signed-in identity, resolved by the client and never taken from request input. */
public data class Principal(
    val userId: org.fisabilillah.core.model.UserId,
    val roles: Set<org.fisabilillah.core.model.AccountRole>,
) {
    public val isStaff: Boolean get() = roles.any { it.isStaff }
    public val isModerator: Boolean
        get() = org.fisabilillah.core.model.AccountRole.MODERATOR in roles || isSafetyAdmin
    public val isSafetyAdmin: Boolean
        get() = org.fisabilillah.core.model.AccountRole.SAFETY_ADMINISTRATOR in roles ||
            org.fisabilillah.core.model.AccountRole.PLATFORM_ADMINISTRATOR in roles
}
