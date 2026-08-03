package org.fisabilillah.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fisabilillah.app.BuildConfig
import org.fisabilillah.app.session.KeystoreSessionStore
import org.fisabilillah.app.ui.navigation.Routes
import org.fisabilillah.core.auth.AuthFailure
import org.fisabilillah.core.auth.AuthResult
import org.fisabilillah.core.auth.MemberIdentity
import org.fisabilillah.core.auth.MemberRegistration
import org.fisabilillah.core.auth.MemberSession
import org.fisabilillah.core.auth.MemberSessionState
import org.fisabilillah.core.auth.SignUpOutcome
import org.fisabilillah.core.auth.SupabaseAuthGateway
import org.fisabilillah.core.auth.SupabaseConfig
import org.fisabilillah.core.auth.JdkHttpTransport
import org.fisabilillah.core.auth.SupabaseMemberDirectory
import org.fisabilillah.core.payments.SupabaseCheckoutGateway
import org.fisabilillah.core.data.CoreGraph
import org.fisabilillah.core.data.SeedData
import org.fisabilillah.core.data.SystemClock
import org.fisabilillah.core.data.UuidIdGenerator
import org.fisabilillah.core.domain.Principal
import org.fisabilillah.core.model.AccountRole

/**
 * The application's object graph.
 *
 * Hand-wired. With the shared core already exposing a single [CoreGraph] there is very
 * little left for a dependency-injection framework to do here, and avoiding one keeps the
 * build free of annotation processing — which matters when the same core has to be
 * consumable from a plain JVM test and, later, from an iOS client.
 *
 * ## What is real and what is not
 *
 * **Authentication is real.** Sign-in goes to Supabase Auth, the password is checked by a
 * server this app cannot influence, and the identity that comes back — the account id, the
 * roles, the verification level — is read out of the database over an authenticated
 * request. Nothing about who you are is decided on the phone.
 *
 * **The content is still the development fixture.** Opportunities, requests, conversations
 * and communities come from the in-memory repositories in `core:data`, seeded with the
 * sample community, and none of the row-level security guarantees in
 * `backend/supabase/migrations/0013_row_level_security.sql` apply to them. A signed-in
 * member gets a profile in that fixture so the rest of the app works, and it does not
 * survive a restart.
 *
 * Finishing the job means constructing [CoreGraph] with Supabase-backed implementations of
 * the same repository interfaces. No use case, view model, or screen changes when that
 * happens — that separation is the whole reason the core is a standalone build.
 */
internal class AppGraph private constructor(
    val core: CoreGraph,
    val session: SessionManager,
) {
    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }

        private fun create(context: Context): AppGraph {
            val config = SupabaseConfig(
                projectUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                // Must also be listed under Authentication -> URL Configuration ->
                // Redirect URLs on the Supabase project. If it is not, GoTrue ignores it
                // and falls back to the Site URL, which is where the dead
                // "couldn't be reached" page came from.
                emailRedirectTo = Routes.EMAIL_CONFIRMED_URI,
            )
            val members = MemberSession(
                gateway = SupabaseAuthGateway(config),
                directory = SupabaseMemberDirectory(config),
                store = KeystoreSessionStore(context),
            )

            // Giving is the one part of the app that already reaches a server for
            // something other than authentication, and it has to: the Stripe key lives in
            // an edge function, and a key that reached this APK would be a published key.
            // The token is fetched per call rather than held, so a checkout started on a
            // screen that has been open a while still carries a live session.
            val payments = SupabaseCheckoutGateway(
                config = config,
                transport = JdkHttpTransport(),
                accessToken = { members.accessToken() },
            )

            val core = CoreGraph(
                clock = SystemClock(),
                ids = UuidIdGenerator(),
                payments = payments,
            )
            SeedData.populate(core.store)

            return AppGraph(core, SessionManager(core, members))
        }
    }
}

/** What the interface should do next after an authentication attempt. */
internal sealed interface SignInResult {
    /** Signed in and set up. Go to the home screen. */
    data object Ready : SignInResult

    /** Signed in, but there is no profile yet. Onboarding owns the screen. */
    data object NeedsOnboarding : SignInResult

    /** No usable session. Stay on, or return to, the sign-in screen. */
    data object SignedOut : SignInResult

    /** The attempt failed. [message] is safe to put in front of a person. */
    data class Failed(val message: String) : SignInResult
}

/**
 * Who is signed in.
 *
 * The principal is derived from the verified session and the database's answer about that
 * account, never from anything a screen passes in. In particular [Principal.roles] is
 * whatever `current_member()` returned: a role granted or revoked server-side takes effect
 * the next time this class asks, and there is no path by which a screen, a view model or a
 * saved preference can add one.
 */
internal class SessionManager(
    private val core: CoreGraph,
    private val members: MemberSession,
) {
    private val _principal = MutableStateFlow<Principal?>(null)
    val principal: StateFlow<Principal?> = _principal.asStateFlow()

    /** Null until [restore] has run. Used to hold the splash rather than flash sign-in. */
    private val _identity = MutableStateFlow<MemberIdentity?>(null)
    val identity: StateFlow<MemberIdentity?> = _identity.asStateFlow()

    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    val isSignedIn: Boolean get() = _principal.value != null

    /** Reinstate the session saved on this device, if any. Called once at start-up. */
    suspend fun restore(): SignInResult {
        val result = adopt(members.restore())
        _restored.value = true
        return result
    }

    suspend fun signIn(email: String, password: String): SignInResult =
        when (val result = members.signIn(email, password)) {
            is AuthResult.Success -> adopt(result.value)
            is AuthResult.Failure -> SignInResult.Failed(result.reason.message)
        }

    /**
     * Create an account.
     *
     * Returns the message to show. With email confirmation on — which is the setting this
     * platform wants — there is no session yet and the person is told to go to their inbox.
     */
    suspend fun signUp(email: String, password: String): SignUpResult =
        when (val result = members.signUp(email, password)) {
            is AuthResult.Failure -> SignUpResult.Failed(result.reason.message)
            is AuthResult.Success -> when (val outcome = result.value) {
                is SignUpOutcome.ConfirmationSent -> SignUpResult.CheckYourEmail
                is SignUpOutcome.SignedIn -> {
                    adopt(members.state.value)
                    SignUpResult.SignedIn
                }
            }
        }

    suspend fun sendRecoveryEmail(email: String): String? =
        when (val result = members.recoverPassword(email)) {
            is AuthResult.Success -> null
            is AuthResult.Failure -> result.reason.message
        }

    /**
     * Register the profile with the database, then mirror it into the local fixture.
     *
     * The server call is what matters; it is the row every row-level security policy will
     * check. [AuthFailure.ALREADY_REGISTERED] is not treated as an error, because it means
     * the database already holds the profile this device was about to create — which is
     * exactly the situation when somebody reinstalls the app.
     */
    suspend fun completeServerRegistration(registration: MemberRegistration): String? =
        when (val result = members.completeRegistration(registration)) {
            is AuthResult.Success -> {
                adopt(result.value)
                null
            }
            is AuthResult.Failure ->
                if (result.reason == AuthFailure.ALREADY_REGISTERED) null else result.reason.message
        }

    suspend fun signOut() {
        members.signOut()
        _principal.value = null
        _identity.value = null
    }

    /** Re-reads roles from the server after onboarding or a role grant. */
    suspend fun refresh() {
        when (val result = members.refreshIdentity()) {
            is AuthResult.Success -> adopt(result.value)
            is AuthResult.Failure ->
                if (result.reason == AuthFailure.SESSION_EXPIRED) {
                    _principal.value = null
                    _identity.value = null
                }
        }
    }

    fun requirePrincipal(): Principal =
        _principal.value ?: error("A screen requested the principal while signed out")

    /**
     * Adopt an authentication state.
     *
     * Onboarding is decided by the *local* profile as well as the server's, because the
     * content layer is still the in-memory fixture: an account that exists in the database
     * but has no profile in this process has nothing for the home screen to show, and
     * sending it there would be a crash rather than a welcome.
     */
    private fun adopt(state: MemberSessionState): SignInResult {
        val identity = state.identityOrNull
        if (identity == null) {
            _principal.value = null
            _identity.value = null
            return SignInResult.SignedOut
        }

        _identity.value = identity
        _principal.value = Principal(userId = identity.userId, roles = identity.roles)

        val localProfile = core.store.profiles[identity.userId]
        return if (identity.hasProfile && localProfile != null) {
            SignInResult.Ready
        } else {
            SignInResult.NeedsOnboarding
        }
    }
}

internal sealed interface SignUpResult {
    /** The ordinary case: confirmation is on, so there is nothing to sign in with yet. */
    data object CheckYourEmail : SignUpResult

    /** The project has confirmation switched off, so the account is usable at once. */
    data object SignedIn : SignUpResult

    data class Failed(val message: String) : SignUpResult
}

/**
 * A view-model factory that hands each view model the graph and the current principal.
 *
 * Written once here rather than repeated per view model. The `create` lambda receives both,
 * so a view model never has to reach for a static.
 */
internal fun <T : ViewModel> viewModelFactory(
    graph: AppGraph,
    create: (AppGraph, Principal) -> T,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>, extras: CreationExtras): VM =
        create(graph, graph.session.requirePrincipal()) as VM
}

/** For view models that must work before anyone is signed in (sign-in, onboarding). */
internal fun <T : ViewModel> anonymousViewModelFactory(
    graph: AppGraph,
    create: (AppGraph) -> T,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>, extras: CreationExtras): VM =
        create(graph) as VM
}

/** True when the signed-in account may see the moderation area. */
internal fun Principal?.canModerate(): Boolean =
    this != null && (AccountRole.MODERATOR in roles || isSafetyAdmin)
