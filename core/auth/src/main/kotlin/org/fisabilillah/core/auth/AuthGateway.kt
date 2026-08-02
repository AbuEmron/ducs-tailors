package org.fisabilillah.core.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.fisabilillah.core.model.UserId
import kotlin.time.Duration.Companion.seconds

/**
 * Creating, proving and ending a session.
 *
 * Everything above this interface — the sign-in screen, the session manager, every view
 * model — is written against it and not against Supabase, so the day the platform moves to
 * a different identity provider the change stops here.
 */
public interface AuthGateway {

    /**
     * Create an account. Returns [SignUpOutcome.ConfirmationSent] in the ordinary case and
     * deliberately does not distinguish a new address from one already registered.
     */
    public suspend fun signUp(email: String, password: String): AuthResult<SignUpOutcome>

    public suspend fun signIn(email: String, password: String): AuthResult<AuthSession>

    /** Exchanges a refresh token for a new session. The old refresh token is spent. */
    public suspend fun refresh(refreshToken: String): AuthResult<AuthSession>

    /** Revokes the session server-side. Best effort: local state is cleared regardless. */
    public suspend fun signOut(accessToken: String): AuthResult<Unit>

    /** Sends a password-reset message. Succeeds whether or not the address is registered. */
    public suspend fun sendRecoveryEmail(email: String): AuthResult<Unit>

    /** Sends the confirmation message again. Same silence about whether the address exists. */
    public suspend fun resendConfirmation(email: String): AuthResult<Unit>
}

/**
 * [AuthGateway] against Supabase's GoTrue API.
 *
 * The interesting work in here is not the request shapes, which are trivial, but the
 * translation of GoTrue's responses into [AuthFailure] — because GoTrue is more candid
 * than this platform wants to be. It will happily tell an unauthenticated caller that an
 * address is already registered, or that a user exists but has not confirmed. On a
 * platform whose membership list includes women who have not told anyone they are looking
 * for a marriage introduction, "does this address have an account here" is not a question
 * a stranger gets an answer to. Sign-up and recovery therefore report the same thing
 * whatever the truth is, and a wrong password is indistinguishable from an unknown address.
 */
public class SupabaseAuthGateway(
    private val config: SupabaseConfig,
    private val transport: HttpTransport = JdkHttpTransport(),
    private val clock: Clock = Clock.System,
) : AuthGateway {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    override suspend fun signUp(email: String, password: String): AuthResult<SignUpOutcome> {
        val normalised = email.trim()
        if (password.length < MIN_PASSWORD_LENGTH) {
            return AuthResult.Failure(AuthFailure.WEAK_PASSWORD, "password shorter than $MIN_PASSWORD_LENGTH")
        }

        val response = post(
            path = "signup",
            body = buildJsonObject {
                put("email", normalised)
                put("password", password)
            },
        ) ?: return AuthResult.Failure(AuthFailure.NETWORK, "signup: transport failed")

        if (response.isSuccess) {
            val token = decode<TokenResponse>(response.body)
            val session = token?.toSession(clock.now())
            return AuthResult.Success(
                if (session != null) SignUpOutcome.SignedIn(session) else SignUpOutcome.ConfirmationSent,
            )
        }

        val error = decode<ErrorResponse>(response.body)
        // "already registered" is a real answer to a question we are not willing to
        // answer. It becomes the same reply a genuinely new address gets. The precise
        // cause still reaches the log through `detail`.
        if (response.status == 422 && error.mentionsExistingUser()) {
            return AuthResult.Success(SignUpOutcome.ConfirmationSent)
        }
        if (response.status == 422 && error.mentionsWeakPassword()) {
            return AuthResult.Failure(AuthFailure.WEAK_PASSWORD, error?.bestDescription)
        }
        return AuthResult.Failure(classify(response.status, error), error?.bestDescription)
    }

    override suspend fun signIn(email: String, password: String): AuthResult<AuthSession> {
        val response = post(
            path = "token?grant_type=password",
            body = buildJsonObject {
                put("email", email.trim())
                put("password", password)
            },
        ) ?: return AuthResult.Failure(AuthFailure.NETWORK, "signIn: transport failed")

        if (!response.isSuccess) {
            val error = decode<ErrorResponse>(response.body)
            // GoTrue distinguishes "not confirmed" from "wrong password". The first is
            // safe to pass on -- it is only reachable by someone who already proved the
            // password -- and withholding it would leave a person stuck with no idea why.
            if (error.mentionsUnconfirmedEmail()) {
                return AuthResult.Failure(AuthFailure.EMAIL_NOT_CONFIRMED, error?.bestDescription)
            }
            return AuthResult.Failure(classify(response.status, error), error?.bestDescription)
        }

        val session = decode<TokenResponse>(response.body)?.toSession(clock.now())
            ?: return AuthResult.Failure(AuthFailure.SERVER, "signIn: unreadable token response")
        return AuthResult.Success(session)
    }

    override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
        val response = post(
            path = "token?grant_type=refresh_token",
            body = buildJsonObject { put("refresh_token", refreshToken) },
        ) ?: return AuthResult.Failure(AuthFailure.NETWORK, "refresh: transport failed")

        if (!response.isSuccess) {
            val error = decode<ErrorResponse>(response.body)
            // A rejected refresh token is not a retryable error: it means this device's
            // session is finished, and the honest response is to sign the person out
            // rather than to keep trying with a credential the server has disowned.
            if (response.status in 400..403) {
                return AuthResult.Failure(AuthFailure.SESSION_EXPIRED, error?.bestDescription)
            }
            return AuthResult.Failure(classify(response.status, error), error?.bestDescription)
        }

        val session = decode<TokenResponse>(response.body)?.toSession(clock.now())
            ?: return AuthResult.Failure(AuthFailure.SERVER, "refresh: unreadable token response")
        return AuthResult.Success(session)
    }

    override suspend fun signOut(accessToken: String): AuthResult<Unit> {
        val response = post(
            path = "logout",
            body = JsonObject(emptyMap()),
            accessToken = accessToken,
        ) ?: return AuthResult.Failure(AuthFailure.NETWORK, "signOut: transport failed")

        // 401 means the token was already dead, which is the state we were asking for.
        return if (response.isSuccess || response.status == 401) {
            AuthResult.Success(Unit)
        } else {
            AuthResult.Failure(classify(response.status, decode<ErrorResponse>(response.body)))
        }
    }

    override suspend fun sendRecoveryEmail(email: String): AuthResult<Unit> =
        fireAndForget("recover", email)

    override suspend fun resendConfirmation(email: String): AuthResult<Unit> =
        fireAndForget("resend", email, extra = { put("type", "signup") })

    /**
     * For the two endpoints whose answer must not depend on whether the address exists.
     * Only a transport failure or a rate limit is reported; a 4xx that would reveal the
     * account's existence is swallowed and reported as success.
     */
    private suspend fun fireAndForget(
        path: String,
        email: String,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): AuthResult<Unit> {
        val response = post(
            path = path,
            body = buildJsonObject {
                put("email", email.trim())
                extra()
            },
        ) ?: return AuthResult.Failure(AuthFailure.NETWORK, "$path: transport failed")

        if (response.status == 429) {
            return AuthResult.Failure(AuthFailure.RATE_LIMITED, "$path: rate limited")
        }
        if (response.status >= 500) {
            return AuthResult.Failure(AuthFailure.SERVER, "$path: status ${response.status}")
        }
        return AuthResult.Success(Unit)
    }

    private suspend fun post(
        path: String,
        body: JsonObject,
        accessToken: String? = null,
    ): HttpResponse? = try {
        transport.send(
            HttpRequest(
                method = "POST",
                url = "${config.authUrl}/$path",
                headers = buildMap {
                    put("apikey", config.publishableKey)
                    put("Content-Type", "application/json")
                    put("Authorization", "Bearer ${accessToken ?: config.publishableKey}")
                },
                body = json.encodeToString(JsonObject.serializer(), body),
            ),
        )
    } catch (e: HttpTransportException) {
        null
    }

    private inline fun <reified T> decode(body: String): T? = try {
        if (body.isBlank()) null else json.decodeFromString<T>(body)
    } catch (e: Exception) {
        null
    }

    private fun classify(status: Int, error: ErrorResponse?): AuthFailure = when {
        status == 429 -> AuthFailure.RATE_LIMITED
        status == 401 || status == 400 -> AuthFailure.INVALID_CREDENTIALS
        status == 403 && error.mentionsUnconfirmedEmail() -> AuthFailure.EMAIL_NOT_CONFIRMED
        status == 403 -> AuthFailure.INVALID_CREDENTIALS
        status >= 500 -> AuthFailure.SERVER
        else -> AuthFailure.SERVER
    }

    private fun TokenResponse.toSession(now: Instant): AuthSession? {
        val access = access_token ?: return null
        val refresh = refresh_token ?: return null
        val id = user?.id ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = now + (expires_in ?: DEFAULT_EXPIRY_SECONDS).seconds,
            userId = UserId(id),
            email = user.email.orEmpty(),
        )
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val DEFAULT_EXPIRY_SECONDS = 3600L
    }
}

private fun ErrorResponse?.text(): String =
    listOfNotNull(this?.error, this?.error_description, this?.msg, this?.message, this?.error_code, this?.code)
        .joinToString(" ")
        .lowercase()

private fun ErrorResponse?.mentionsExistingUser(): Boolean =
    text().let { it.contains("already registered") || it.contains("user_already_exists") }

private fun ErrorResponse?.mentionsWeakPassword(): Boolean =
    text().let { it.contains("password") && (it.contains("weak") || it.contains("at least")) }

private fun ErrorResponse?.mentionsUnconfirmedEmail(): Boolean =
    text().let { it.contains("not confirmed") || it.contains("email_not_confirmed") }
