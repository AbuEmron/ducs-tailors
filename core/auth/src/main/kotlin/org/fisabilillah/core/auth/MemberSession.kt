package org.fisabilillah.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.UserId

/**
 * The state of the person using the app.
 *
 * Four states rather than a boolean, because "signed in" is not one thing here. An account
 * can exist, be confirmed, and still have no profile — that is the gap between GoTrue and
 * this schema, and it is a state the interface has to be able to show rather than crash on.
 */
public sealed interface MemberSessionState {

    /** Before [MemberSession.restore] has finished. Show the splash, not the sign-in screen. */
    public data object Unknown : MemberSessionState

    public data object SignedOut : MemberSessionState

    /** Signed in, but `register_member` has not run yet. Onboarding owns the screen. */
    public data class NeedsOnboarding(val identity: MemberIdentity) : MemberSessionState

    public data class SignedIn(val identity: MemberIdentity) : MemberSessionState

    public val identityOrNull: MemberIdentity?
        get() = when (this) {
            is NeedsOnboarding -> identity
            is SignedIn -> identity
            else -> null
        }

    public val userId: UserId? get() = identityOrNull?.userId
    public val roles: Set<AccountRole> get() = identityOrNull?.roles.orEmpty()
}

/**
 * Holds the session and keeps its token usable.
 *
 * The one rule worth stating plainly: **nothing outside this class ever reads the access
 * token from a field.** Callers ask [accessToken], which refreshes if the token is within
 * a minute of expiry and returns null if the session is finished. That is what stops the
 * app from making a stream of requests with a dead credential and showing the user a
 * screenful of errors that all mean "sign in again".
 *
 * Refresh is guarded by a mutex. Without it, four screens waking at once on a cold start
 * would each spend the same refresh token, three of them would be rejected, and the app
 * would sign the person out for no reason.
 */
public class MemberSession(
    private val gateway: AuthGateway,
    private val directory: MemberDirectory,
    private val store: SessionStore,
    private val clock: Clock = Clock.System,
) {
    private val _state = MutableStateFlow<MemberSessionState>(MemberSessionState.Unknown)
    public val state: StateFlow<MemberSessionState> = _state.asStateFlow()

    private val refreshLock = Mutex()

    @Volatile
    private var current: AuthSession? = null

    /**
     * Bring back the session saved on this device, if there is one.
     *
     * Called once at start-up. A refresh token the server has disowned ends as
     * [MemberSessionState.SignedOut] with the stored copy deleted, rather than as an
     * error the user is asked to do something about.
     */
    public suspend fun restore(): MemberSessionState {
        val stored = store.load()
        if (stored == null) {
            _state.value = MemberSessionState.SignedOut
            return MemberSessionState.SignedOut
        }
        return when (val refreshed = gateway.refresh(stored.refreshToken)) {
            is AuthResult.Success -> adopt(refreshed.value)
            is AuthResult.Failure -> {
                store.clear()
                current = null
                _state.value = MemberSessionState.SignedOut
                MemberSessionState.SignedOut
            }
        }
    }

    public suspend fun signUp(email: String, password: String): AuthResult<SignUpOutcome> {
        return when (val result = gateway.signUp(email, password)) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> {
                // With confirmation on -- which is the default and the setting this
                // platform wants -- there is no session yet, and the screen says so.
                (result.value as? SignUpOutcome.SignedIn)?.let { adopt(it.session) }
                result
            }
        }
    }

    public suspend fun signIn(email: String, password: String): AuthResult<MemberSessionState> =
        when (val result = gateway.signIn(email, password)) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> AuthResult.Success(adopt(result.value))
        }

    /**
     * Finish onboarding: create the profile the whole schema hangs off.
     *
     * The identity that comes back is read from the database rather than assembled from
     * what was submitted, so the app shows the role that was actually granted and the
     * verification level that was actually recorded — which, for a new member, is none.
     */
    public suspend fun completeRegistration(
        registration: MemberRegistration,
    ): AuthResult<MemberSessionState> {
        val token = accessToken()
            ?: return AuthResult.Failure(AuthFailure.SESSION_EXPIRED, "completeRegistration: no session")

        return when (val result = directory.register(token, registration)) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> AuthResult.Success(publish(result.value))
        }
    }

    /**
     * Ask for a password-reset message.
     *
     * Reports success whether or not the address is registered -- see [AuthFailure] for
     * why this platform will not confirm who has an account here.
     */
    public suspend fun recoverPassword(email: String): AuthResult<Unit> =
        gateway.sendRecoveryEmail(email)

    /** Send the confirmation message again, for someone who lost or never got the first. */
    public suspend fun resendConfirmation(email: String): AuthResult<Unit> =
        gateway.resendConfirmation(email)

    /** Re-reads roles and verification from the server. Cheap, and the only honest way. */
    public suspend fun refreshIdentity(): AuthResult<MemberSessionState> {
        val token = accessToken()
            ?: return AuthResult.Failure(AuthFailure.SESSION_EXPIRED, "refreshIdentity: no session")
        return when (val result = directory.currentMember(token)) {
            is AuthResult.Failure -> {
                if (result.reason == AuthFailure.SESSION_EXPIRED) signOut()
                result
            }
            is AuthResult.Success -> AuthResult.Success(publish(result.value))
        }
    }

    /**
     * A usable access token, refreshed if it is about to expire.
     *
     * Returns null when there is no live session, which callers must treat as "stop and
     * show the sign-in screen" rather than as "try again without a token".
     */
    public suspend fun accessToken(): String? {
        current?.let { if (it.isFresh(clock.now())) return it.accessToken }

        return refreshLock.withLock {
            // Re-check inside the lock: whoever held it first has probably just done this.
            current?.let { if (it.isFresh(clock.now())) return@withLock it.accessToken }

            val refreshToken = current?.refreshToken ?: store.load()?.refreshToken
            if (refreshToken == null) {
                _state.value = MemberSessionState.SignedOut
                return@withLock null
            }
            when (val result = gateway.refresh(refreshToken)) {
                is AuthResult.Success -> {
                    remember(result.value)
                    result.value.accessToken
                }
                is AuthResult.Failure -> {
                    store.clear()
                    current = null
                    _state.value = MemberSessionState.SignedOut
                    null
                }
            }
        }
    }

    /**
     * End the session.
     *
     * The stored token is dropped and the state is set to signed out **before** the
     * network call, and regardless of what it returns. A sign-out that fails because the
     * train went into a tunnel must still sign the person out of the device in their hand;
     * the server-side revocation is the part that can be retried, not the part the user
     * is waiting on.
     */
    public suspend fun signOut() {
        val token = current?.accessToken
        store.clear()
        current = null
        _state.value = MemberSessionState.SignedOut
        if (token != null) gateway.signOut(token)
    }

    private suspend fun adopt(session: AuthSession): MemberSessionState {
        remember(session)
        return when (val identity = directory.currentMember(session.accessToken)) {
            is AuthResult.Success -> publish(identity.value)
            is AuthResult.Failure -> {
                // A valid token whose account the database cannot describe is not a
                // session this app can act on.
                store.clear()
                current = null
                _state.value = MemberSessionState.SignedOut
                MemberSessionState.SignedOut
            }
        }
    }

    private fun remember(session: AuthSession) {
        current = session
        store.save(
            StoredSession(
                refreshToken = session.refreshToken,
                userId = session.userId.value,
                email = session.email,
            ),
        )
    }

    private fun publish(identity: MemberIdentity): MemberSessionState {
        val next = if (identity.hasProfile) {
            MemberSessionState.SignedIn(identity)
        } else {
            MemberSessionState.NeedsOnboarding(identity)
        }
        _state.value = next
        return next
    }
}
