package org.fisabilillah.core.auth

import java.util.concurrent.atomic.AtomicReference

/**
 * Where a session survives the app being closed.
 *
 * A port, because the answer is platform-specific and genuinely security-relevant: on
 * Android the implementation encrypts with a key held in the hardware-backed keystore
 * (see `KeystoreSessionStore` in the application module), and on a JVM test it is a
 * variable. Putting the interface here keeps [MemberSession] identical in both.
 *
 * What is stored is a refresh token, which is a bearer credential for the account for as
 * long as it lives. It is not a password and cannot be turned back into one, but anybody
 * holding it is signed in as that person until it is revoked — so [clear] is not
 * housekeeping, it is the thing that makes signing out mean something.
 */
public interface SessionStore {
    public fun load(): StoredSession?
    public fun save(session: StoredSession)
    public fun clear()
}

/**
 * The persisted form of a session.
 *
 * The access token is deliberately not stored. It is short-lived, it can always be
 * obtained again from the refresh token, and writing it to disk widens what a stolen
 * device gives away for no benefit at all.
 */
public data class StoredSession(
    val refreshToken: String,
    val userId: String,
    val email: String,
)

/** For tests and for a first run before anything has been saved. */
public class InMemorySessionStore(initial: StoredSession? = null) : SessionStore {
    private val ref = AtomicReference(initial)
    override fun load(): StoredSession? = ref.get()
    override fun save(session: StoredSession) { ref.set(session) }
    override fun clear() { ref.set(null) }
}
