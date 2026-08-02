package org.fisabilillah.core.auth

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Gender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** A clock the test moves by hand, so token expiry can be reached without waiting an hour. */
private class MovableClock(var at: Instant) : Clock {
    override fun now(): Instant = at
}

private val START = Instant.parse("2026-08-02T12:00:00Z")

class MemberSessionTest {

    private fun session(
        authTransport: FakeTransport,
        restTransport: FakeTransport,
        store: SessionStore = InMemorySessionStore(),
        clock: Clock = MovableClock(START),
    ) = MemberSession(
        gateway = SupabaseAuthGateway(Fixtures.config, authTransport, clock),
        directory = SupabaseMemberDirectory(Fixtures.config, restTransport),
        store = store,
        clock = clock,
    )

    @Test
    @DisplayName("signing in with a profile lands on SignedIn with the server's roles")
    fun signInWithProfile() = runTest {
        val member = session(
            authTransport = FakeTransport.always(200, Fixtures.tokenResponse()),
            restTransport = FakeTransport.always(200, Fixtures.memberRow(roles = """["member","moderator"]""")),
        )

        val result = member.signIn("amina@example.test", "correct-horse") as AuthResult.Success
        val state = result.value as MemberSessionState.SignedIn

        assertEquals(setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.MODERATOR), state.identity.roles)
        assertEquals(Gender.FEMALE, state.identity.gender)
        assertEquals(VerificationLevel.BASIC, state.identity.verificationLevel)
        assertEquals(state, member.state.value)
    }

    @Test
    @DisplayName("an account with no profile yet is NeedsOnboarding, not an error")
    fun signInWithoutProfile() = runTest {
        val member = session(
            authTransport = FakeTransport.always(200, Fixtures.tokenResponse()),
            restTransport = FakeTransport.always(200, Fixtures.memberRow(hasProfile = false, roles = "[]")),
        )

        val result = member.signIn("newcomer@example.test", "correct-horse") as AuthResult.Success
        val state = result.value as MemberSessionState.NeedsOnboarding
        assertTrue(state.identity.needsOnboarding)
        assertTrue(state.identity.roles.isEmpty())
    }

    @Test
    @DisplayName("a role key this build has never heard of is dropped, not guessed at")
    fun unknownRolesAreDropped() = runTest {
        val member = session(
            authTransport = FakeTransport.always(200, Fixtures.tokenResponse()),
            restTransport = FakeTransport.always(
                200,
                Fixtures.memberRow(roles = """["member","regional_supervisor"]"""),
            ),
        )

        val result = member.signIn("amina@example.test", "correct-horse") as AuthResult.Success
        assertEquals(setOf(AccountRole.COMMUNITY_MEMBER), result.value.roles)
    }

    @Test
    @DisplayName("a verification level this build has never heard of is treated as unverified")
    fun unknownVerificationFallsDownwards() {
        assertEquals(VerificationLevel.UNVERIFIED, VerificationLevel.fromWire("archangel_verified"))
        assertEquals(VerificationLevel.UNVERIFIED, VerificationLevel.fromWire(null))
        assertEquals(VerificationLevel.SCHOLAR_VERIFIED, VerificationLevel.fromWire("scholar_verified"))
    }

    @Test
    @DisplayName("restoring on a cold start refreshes the stored token")
    fun restoreRefreshes() = runTest {
        val store = InMemorySessionStore(
            StoredSession("stored-refresh", Fixtures.USER_ID, "amina@example.test"),
        )
        val auth = FakeTransport.always(200, Fixtures.tokenResponse(access = "fresh-access"))
        val member = session(auth, FakeTransport.always(200, Fixtures.memberRow()), store)

        val state = member.restore()
        assertTrue(state is MemberSessionState.SignedIn)
        assertTrue(auth.lastBody.contains("stored-refresh"))
        assertEquals("fresh-access", member.accessToken())
    }

    @Test
    @DisplayName("a refresh token the server has disowned signs the device out and forgets it")
    fun deadRefreshTokenIsForgotten() = runTest {
        val store = InMemorySessionStore(
            StoredSession("revoked", Fixtures.USER_ID, "amina@example.test"),
        )
        val member = session(
            authTransport = FakeTransport.always(400, """{"error":"invalid_grant"}"""),
            restTransport = FakeTransport.always(200, Fixtures.memberRow()),
            store = store,
        )

        assertEquals(MemberSessionState.SignedOut, member.restore())
        assertNull(store.load())
        assertNull(member.accessToken())
    }

    @Test
    @DisplayName("an expiring token is refreshed before the caller is handed it")
    fun accessTokenRefreshesWhenStale() = runTest {
        val clock = MovableClock(START)
        val auth = FakeTransport.sequence(
            200 to Fixtures.tokenResponse(access = "first", refresh = "r1", expiresIn = 3600),
            200 to Fixtures.tokenResponse(access = "second", refresh = "r2", expiresIn = 3600),
        )
        val member = session(auth, FakeTransport.always(200, Fixtures.memberRow()), clock = clock)

        member.signIn("amina@example.test", "correct-horse")
        assertEquals("first", member.accessToken())

        // Move to inside the refresh margin.
        clock.at = START + 3590.seconds
        assertEquals("second", member.accessToken())
        assertTrue(auth.sent.last().body!!.contains("r1"), "the refresh should spend the stored token")
    }

    @Test
    @DisplayName("a token refreshed once is reused, not refreshed again on every call")
    fun refreshHappensOnceForManyCallers() = runTest {
        val clock = MovableClock(START)
        val auth = FakeTransport.sequence(
            200 to Fixtures.tokenResponse(access = "first", refresh = "r1", expiresIn = 10),
            200 to Fixtures.tokenResponse(access = "second", refresh = "r2", expiresIn = 3600),
        )
        val member = session(auth, FakeTransport.always(200, Fixtures.memberRow()), clock = clock)
        member.signIn("amina@example.test", "correct-horse")

        val tokens = listOf(member.accessToken(), member.accessToken(), member.accessToken())

        // The scripted transport throws once it runs out, so a second refresh would have
        // failed the test outright: three callers produced exactly one between them. The
        // mutex in accessToken() is what makes the same true when they arrive at once.
        assertEquals(listOf("second", "second", "second"), tokens)
    }

    @Test
    @DisplayName("signing out clears the device even when the server call fails")
    fun signOutAlwaysClearsLocally() = runTest {
        val store = InMemorySessionStore()
        val member = session(
            authTransport = FakeTransport.sequence(
                200 to Fixtures.tokenResponse(),
                500 to """{"msg":"gateway down"}""",
            ),
            restTransport = FakeTransport.always(200, Fixtures.memberRow()),
            store = store,
        )
        member.signIn("amina@example.test", "correct-horse")
        assertTrue(store.load() != null)

        member.signOut()

        assertEquals(MemberSessionState.SignedOut, member.state.value)
        assertNull(store.load())
    }

    @Test
    @DisplayName("registration sends no role, no verification level and no account id")
    fun registrationCannotAssertPrivilegedValues() = runTest {
        val rest = FakeTransport.sequence(
            200 to Fixtures.memberRow(hasProfile = false, roles = "[]", verification = "unverified"),
            200 to "\"${Fixtures.USER_ID}\"",
            200 to Fixtures.memberRow(roles = """["member"]""", verification = "unverified"),
        )
        val member = session(FakeTransport.always(200, Fixtures.tokenResponse()), rest)
        member.signIn("newcomer@example.test", "correct-horse")

        val result = member.completeRegistration(
            MemberRegistration(
                displayName = "Yahya T.",
                gender = Gender.MALE,
                acceptedCovenant = true,
                city = "Northgate",
                countryCode = "gb",
            ),
        ) as AuthResult.Success

        val body = rest.sent[1].body.orEmpty()
        assertTrue(body.contains("\"p_display_name\":\"Yahya T.\""), body)
        assertTrue(body.contains("\"p_gender\":\"male\""), body)
        assertTrue(body.contains("\"p_country_code\":\"GB\""), body)
        assertFalse(body.contains("role"), "registration must not name a role: $body")
        assertFalse(body.contains("verification"), "registration must not name a verification level: $body")
        assertFalse(body.contains("user_id"), "registration must not name an account id: $body")
        assertFalse(body.contains("p_contact_email"), "the address comes from auth.users: $body")

        assertEquals(setOf(AccountRole.COMMUNITY_MEMBER), result.value.roles)
        assertEquals(VerificationLevel.UNVERIFIED, result.value.identityOrNull?.verificationLevel)
    }

    @Test
    @DisplayName("every request to the database carries the session token, not the publishable key")
    fun restRequestsAreAuthenticated() = runTest {
        val rest = FakeTransport.always(200, Fixtures.memberRow())
        val member = session(FakeTransport.always(200, Fixtures.tokenResponse()), rest)
        member.signIn("amina@example.test", "correct-horse")

        assertEquals("Bearer access-token-1", rest.sent.single().headers["Authorization"])
        assertEquals("sb_publishable_test_key", rest.sent.single().headers["apikey"])
    }

    @Test
    @DisplayName("a token the database cannot describe is not treated as a session")
    fun unknownAccountIsNotSignedIn() = runTest {
        val member = session(
            authTransport = FakeTransport.always(200, Fixtures.tokenResponse()),
            restTransport = FakeTransport.always(200, "[]"),
        )
        val result = member.signIn("ghost@example.test", "correct-horse") as AuthResult.Success
        assertEquals(MemberSessionState.SignedOut, result.value)
    }
}
