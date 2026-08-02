package org.fisabilillah.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fisabilillah.core.data.CoreGraph
import org.fisabilillah.core.data.SeedData
import org.fisabilillah.core.data.SystemClock
import org.fisabilillah.core.data.UuidIdGenerator
import org.fisabilillah.core.domain.Principal
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.UserId

/**
 * The application's object graph.
 *
 * Hand-wired. With the shared core already exposing a single [CoreGraph] there is very
 * little left for a dependency-injection framework to do here, and avoiding one keeps the
 * build free of annotation processing — which matters when the same core has to be
 * consumable from a plain JVM test and, later, from an iOS client.
 *
 * ## Development data source
 *
 * The graph is currently backed by the in-memory repositories from `core:data`, seeded with
 * the sample community. This is a **development fixture and is labelled as one throughout**:
 * nothing survives a process restart, and none of the row-level security guarantees in
 * `backend/supabase/migrations/0013_row_level_security.sql` apply to it.
 *
 * Swapping in the real backend means constructing [CoreGraph] with Supabase-backed
 * implementations of the same repository interfaces. No use case, view model, or screen
 * changes — that separation is the whole reason the core is a standalone build.
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

        private fun create(@Suppress("UNUSED_PARAMETER") context: Context): AppGraph {
            val core = CoreGraph(clock = SystemClock(), ids = UuidIdGenerator())
            SeedData.populate(core.store)
            return AppGraph(core, SessionManager(core))
        }
    }
}

/**
 * Who is signed in.
 *
 * The principal is derived from stored account state, never from anything a screen passes
 * in. A view model that wanted to act as somebody else would have to change this class,
 * which is a reviewable act rather than an accident.
 *
 * Authentication itself is a development stand-in: [signInAs] selects one of the seeded
 * accounts. The production implementation obtains a session from Supabase Auth and derives
 * the principal from the verified token, and every screen below this line is unaffected by
 * that change.
 */
internal class SessionManager(private val core: CoreGraph) {

    private val _principal = MutableStateFlow<Principal?>(null)
    val principal: StateFlow<Principal?> = _principal.asStateFlow()

    val isSignedIn: Boolean get() = _principal.value != null

    /** The accounts offered on the development sign-in screen. */
    fun availableAccounts(): List<AccountOption> = core.store.profiles.values
        .filter { it.isActive }
        .map { profile ->
            AccountOption(
                userId = profile.id,
                displayName = profile.displayName,
                summary = profile.roles.joinToString { it.displayName },
            )
        }
        .sortedBy { it.displayName }

    fun signInAs(userId: UserId) {
        val profile = core.store.profiles[userId] ?: return
        _principal.value = Principal(userId = profile.id, roles = profile.roles)
    }

    fun signOut() {
        _principal.value = null
    }

    /** Re-reads roles after onboarding or a role grant, so the UI reflects the change. */
    fun refresh() {
        val current = _principal.value ?: return
        val profile = core.store.profiles[current.userId] ?: return
        _principal.value = current.copy(roles = profile.roles)
    }

    fun requirePrincipal(): Principal =
        _principal.value ?: error("A screen requested the principal while signed out")

    data class AccountOption(
        val userId: UserId,
        val displayName: String,
        val summary: String,
    )
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
