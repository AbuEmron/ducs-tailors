package org.fisabilillah.core.auth

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.UserId

/**
 * Where this client talks to.
 *
 * Only the publishable key ever appears here. The service-role key bypasses every
 * row-level security policy in the database, and a key shipped inside an APK is a key
 * anybody can read out of it — so the type cannot carry one. If a service key is ever
 * needed for a back-office job, that job does not run on a phone.
 */
public data class SupabaseConfig(
    /** e.g. `https://mqrooikosbhwjcdssatf.supabase.co`, with no trailing slash. */
    val projectUrl: String,
    /** The publishable (`sb_publishable_…`) or legacy anon key. Safe to ship. */
    val publishableKey: String,
) {
    init {
        require(projectUrl.startsWith("https://")) {
            "The project URL must be https; auth tokens are not sent over plaintext"
        }
        require(!projectUrl.endsWith("/")) { "The project URL must not end in a slash" }
        require(publishableKey.isNotBlank()) { "A publishable key is required" }
        require(!publishableKey.contains("service_role") && !publishableKey.startsWith("sb_secret_")) {
            "That looks like a service-role key. It must never be given to a client."
        }
    }

    internal val authUrl: String get() = "$projectUrl/auth/v1"
    internal val restUrl: String get() = "$projectUrl/rest/v1"
}

/**
 * A signed-in session.
 *
 * [accessToken] is the JWT the database sees as `auth.uid()`. Nothing in this client
 * decodes it to decide what the holder may do: the claims inside are a convenience, and
 * every actual permission is re-derived server-side by a policy or a definer function.
 */
public data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val userId: UserId,
    val email: String,
) {
    public fun isFresh(now: Instant, margin: Duration = REFRESH_MARGIN): Boolean =
        now + margin < expiresAt

    public companion object {
        /**
         * Refresh a minute before the token actually dies. A request that starts valid and
         * arrives expired fails in a way the user reads as "the app logged me out".
         */
        public val REFRESH_MARGIN: Duration = 60.seconds
    }
}

/**
 * The result of an authentication call.
 *
 * A sealed pair rather than an exception, because every caller here has to handle failure —
 * these are network calls against a service that will be slow, rate-limited and occasionally
 * down, and a screen that forgets to catch would sign somebody out silently.
 */
public sealed interface AuthResult<out T> {
    public data class Success<T>(val value: T) : AuthResult<T>
    public data class Failure(val reason: AuthFailure, val detail: String? = null) : AuthResult<Nothing>
}

/**
 * Why an authentication call did not succeed.
 *
 * [message] is what a person is shown, and it is written to be *unhelpful to an attacker*
 * in the same way [org.fisabilillah.core.policy.ContactPolicy]'s refusals are. In
 * particular there is no "no account with that address" and no "wrong password": both
 * arrive as [INVALID_CREDENTIALS], because the difference between them is an account
 * enumeration oracle, and on a platform where the membership list is itself sensitive —
 * a woman's presence here is not something a stranger gets to confirm — that oracle is
 * worth more to the wrong person than the password is.
 *
 * [detail] on the Failure carries the precise cause for the log, in the same
 * user-facing/audit split used throughout the policy layer.
 */
public enum class AuthFailure(public val message: String) {
    INVALID_CREDENTIALS(
        "Those details do not match an account. Check the address and the password, and " +
            "try again.",
    ),
    EMAIL_NOT_CONFIRMED(
        "This account still needs its email address confirmed. Open the message we sent " +
            "and follow the link, then sign in again.",
    ),
    WEAK_PASSWORD(
        "Choose a longer password — at least eight characters, and not one you use " +
            "anywhere else.",
    ),
    RATE_LIMITED(
        "Too many attempts. Wait a few minutes before trying again.",
    ),
    SESSION_EXPIRED(
        "You have been signed out. Sign in again to continue.",
    ),
    ALREADY_REGISTERED(
        "This account already has a profile.",
    ),
    NETWORK(
        "The app could not reach the server. Check your connection and try again.",
    ),
    SERVER(
        "Something went wrong at our end. Nothing you did caused it. Please try again " +
            "shortly.",
    ),
}

/**
 * What happened when somebody signed up.
 *
 * [ConfirmationSent] is returned whether or not the address was already registered.
 * Supabase will tell the client the difference; this client refuses to pass it on, for the
 * reason given on [AuthFailure.INVALID_CREDENTIALS]. Someone who genuinely already has an
 * account sees "check your email", opens their inbox, finds no new message and the old one
 * still works — which is exactly what should happen and reveals nothing to anyone else.
 */
public sealed interface SignUpOutcome {
    /** Confirmation is on. There is no session yet, and there should not be one. */
    public data object ConfirmationSent : SignUpOutcome

    /** The project has email confirmation switched off, so a session came back at once. */
    public data class SignedIn(val session: AuthSession) : SignUpOutcome
}

/**
 * The signed-in account as the database describes it — the return of `current_member()`.
 *
 * Every field here is server-derived. [roles] in particular is read from `user_roles`
 * through a definer function, never from anything the client asserted and never from the
 * JWT body: a token is signed, but the roles inside it are a snapshot from issue time, and
 * a role revoked ten minutes ago must stop working now rather than at the next refresh.
 */
public data class MemberIdentity(
    val userId: UserId,
    val hasProfile: Boolean,
    val displayName: String?,
    val email: String?,
    val gender: Gender?,
    val locale: String?,
    val timezone: String?,
    val verificationLevel: VerificationLevel,
    val roles: Set<AccountRole>,
    val covenantAccepted: Boolean,
    val emailConfirmed: Boolean,
) {
    /** True while the account exists but onboarding has not produced a profile yet. */
    public val needsOnboarding: Boolean get() = !hasProfile
}

/** Mirrors the `public.verification_level` enum. Ordered from least to most trusted. */
public enum class VerificationLevel(public val wireName: String, public val displayName: String) {
    UNVERIFIED("unverified", "Not verified"),
    BASIC("basic", "Email confirmed"),
    COMMUNITY_VOUCHED("community_vouched", "Vouched for by the community"),
    ORG_VERIFIED("org_verified", "Verified by an organisation"),
    SCHOLAR_VERIFIED("scholar_verified", "Verified by a listed scholar"),
    ;

    public companion object {
        /**
         * Unknown values fall back to [UNVERIFIED], never to something more trusting. If a
         * future migration adds a level this build has never heard of, treating it as the
         * bottom of the ladder costs a member some access; guessing upward would hand a
         * stranger a trust marker nobody granted.
         */
        public fun fromWire(value: String?): VerificationLevel =
            entries.firstOrNull { it.wireName == value } ?: UNVERIFIED
    }
}

/**
 * The `public.roles.key` values and what they mean to the client.
 *
 * Unrecognised keys are dropped rather than mapped to anything. A build that meets a role
 * it does not know about behaves as though the person does not hold it, which is the safe
 * direction: the alternative is a client that invents a permission from a string.
 */
internal object RoleKeys {
    private val byKey: Map<String, AccountRole> = mapOf(
        "member" to AccountRole.COMMUNITY_MEMBER,
        "organizer" to AccountRole.PROJECT_ORGANIZER,
        "scholar" to AccountRole.SCHOLAR,
        "moderator" to AccountRole.MODERATOR,
        "platform_admin" to AccountRole.PLATFORM_ADMINISTRATOR,
        "safeguarding_lead" to AccountRole.SAFETY_ADMINISTRATOR,
    )

    fun toRoles(keys: List<String>?): Set<AccountRole> =
        keys.orEmpty().mapNotNull { byKey[it] }.toSet()
}

/**
 * What a new member supplies to `register_member()`.
 *
 * Notably absent: any role, any verification level, and the account id. Those are decided
 * by the database from the bearer token, so there is no field here for a caller to lie in.
 */
public data class MemberRegistration(
    val displayName: String,
    val gender: Gender,
    val acceptedCovenant: Boolean,
    val locale: String = "en",
    val timezone: String = "UTC",
    val yearOfBirth: Int? = null,
    val city: String? = null,
    val countryCode: String? = null,
) {
    init {
        require(displayName.trim().length >= 2) { "A display name of at least two characters is required" }
        require(countryCode == null || countryCode.length == 2) { "A country code is two letters" }
    }
}

// ---------------------------------------------------------------------------
// Wire shapes. Internal: nothing outside this module should be handling the
// raw JSON, and none of these types leak into the application's model.
// ---------------------------------------------------------------------------

@Serializable
internal data class TokenResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val user: UserResponse? = null,
)

@Serializable
internal data class UserResponse(
    val id: String? = null,
    val email: String? = null,
    val email_confirmed_at: String? = null,
    val confirmed_at: String? = null,
)

@Serializable
internal data class ErrorResponse(
    val error: String? = null,
    val error_description: String? = null,
    val msg: String? = null,
    val message: String? = null,
    val error_code: String? = null,
    val code: String? = null,
) {
    val bestDescription: String?
        get() = error_description ?: msg ?: message ?: error
}

@Serializable
internal data class CurrentMemberRow(
    val user_id: String? = null,
    val display_name: String? = null,
    val contact_email: String? = null,
    val gender: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val verification_level: String? = null,
    val role_keys: List<String>? = null,
    val has_profile: Boolean = false,
    val covenant_accepted: Boolean = false,
    val email_confirmed: Boolean = false,
)
