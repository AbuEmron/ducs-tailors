package org.fisabilillah.core.auth

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

private val NOW = Instant.parse("2026-08-02T12:00:00Z")

class SupabaseAuthGatewayTest {

    private fun gateway(transport: HttpTransport) =
        SupabaseAuthGateway(Fixtures.config, transport, FixedClock(NOW))

    // -----------------------------------------------------------------
    // Account enumeration. The reason these tests exist is spelled out on
    // AuthFailure: the membership list here is itself sensitive.
    // -----------------------------------------------------------------

    @Test
    @DisplayName("signing up with an address that already exists looks exactly like a new one")
    fun signUpDoesNotRevealExistingAccounts() = runTest {
        val fresh = gateway(FakeTransport.always(200, """{"user":{"id":"${Fixtures.USER_ID}"}}"""))
        val taken = gateway(
            FakeTransport.always(422, """{"code":"user_already_exists","msg":"User already registered"}"""),
        )

        val a = fresh.signUp("new@example.test", "a-long-enough-password")
        val b = taken.signUp("known@example.test", "a-long-enough-password")

        assertEquals(AuthResult.Success(SignUpOutcome.ConfirmationSent), a)
        assertEquals(AuthResult.Success(SignUpOutcome.ConfirmationSent), b)
    }

    @Test
    @DisplayName("a wrong password and an unknown address give the same message")
    fun signInFailuresAreIndistinguishable() = runTest {
        val wrongPassword = gateway(
            FakeTransport.always(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""),
        )
        val noSuchUser = gateway(
            FakeTransport.always(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""),
        )

        val a = wrongPassword.signIn("amina@example.test", "wrong") as AuthResult.Failure
        val b = noSuchUser.signIn("nobody@example.test", "whatever") as AuthResult.Failure

        assertEquals(AuthFailure.INVALID_CREDENTIALS, a.reason)
        assertEquals(a.reason.message, b.reason.message)
        assertFalse(a.reason.message.contains("account", ignoreCase = true) &&
            a.reason.message.contains("exist", ignoreCase = true))
    }

    @Test
    @DisplayName("password recovery reports success for an address that does not exist")
    fun recoveryIsSilentAboutExistence() = runTest {
        val notFound = gateway(FakeTransport.always(404, """{"msg":"User not found"}"""))
        assertTrue(notFound.sendRecoveryEmail("nobody@example.test") is AuthResult.Success)
    }

    @Test
    @DisplayName("but recovery still reports a rate limit, which reveals nothing about the address")
    fun recoveryStillReportsRateLimits() = runTest {
        val limited = gateway(FakeTransport.always(429, """{"msg":"too many requests"}"""))
        val result = limited.sendRecoveryEmail("someone@example.test") as AuthResult.Failure
        assertEquals(AuthFailure.RATE_LIMITED, result.reason)
    }

    // -----------------------------------------------------------------
    // Sessions
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a successful sign-in produces a session that expires when the server said")
    fun signInBuildsSession() = runTest {
        val transport = FakeTransport.always(200, Fixtures.tokenResponse(expiresIn = 1800))
        val result = gateway(transport).signIn("amina@example.test", "correct-horse") as AuthResult.Success

        assertEquals("access-token-1", result.value.accessToken)
        assertEquals("refresh-token-1", result.value.refreshToken)
        assertEquals(Fixtures.USER_ID, result.value.userId.value)
        assertEquals(NOW.plus(1800.seconds), result.value.expiresAt)
    }

    @Test
    @DisplayName("a session one minute from expiry is already treated as stale")
    fun freshnessLeavesRoomToRefresh() {
        val session = AuthSession(
            accessToken = "a",
            refreshToken = "r",
            expiresAt = NOW + 45.seconds,
            userId = org.fisabilillah.core.model.UserId(Fixtures.USER_ID),
            email = "amina@example.test",
        )
        assertFalse(session.isFresh(NOW))
        assertTrue(session.isFresh(NOW - 600.seconds))
    }

    @Test
    @DisplayName("a rejected refresh token ends the session rather than asking to try again")
    fun rejectedRefreshIsTerminal() = runTest {
        val transport = FakeTransport.always(400, """{"error":"invalid_grant","msg":"Invalid Refresh Token"}""")
        val result = gateway(transport).refresh("spent-token") as AuthResult.Failure
        assertEquals(AuthFailure.SESSION_EXPIRED, result.reason)
    }

    @Test
    @DisplayName("an unconfirmed address is reported plainly, because the password already matched")
    fun unconfirmedEmailIsNamed() = runTest {
        val transport = FakeTransport.always(400, """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}""")
        val result = gateway(transport).signIn("amina@example.test", "correct-horse") as AuthResult.Failure
        assertEquals(AuthFailure.EMAIL_NOT_CONFIRMED, result.reason)
    }

    @Test
    @DisplayName("signing out treats an already-dead token as success")
    fun signOutToleratesDeadToken() = runTest {
        val transport = FakeTransport.always(401, """{"msg":"invalid token"}""")
        assertTrue(gateway(transport).signOut("stale") is AuthResult.Success)
    }

    @Test
    @DisplayName("a short password is refused before it reaches the network")
    fun weakPasswordNeverLeavesTheDevice() = runTest {
        val transport = FakeTransport.always(200, Fixtures.tokenResponse())
        val result = gateway(transport).signUp("new@example.test", "short") as AuthResult.Failure
        assertEquals(AuthFailure.WEAK_PASSWORD, result.reason)
        assertTrue(transport.sent.isEmpty(), "nothing should have been sent")
    }

    @Test
    @DisplayName("a transport failure is a network failure, not a credential failure")
    fun transportFailureIsNotBlamedOnTheUser() = runTest {
        val result = gateway(FakeTransport.failing()).signIn("amina@example.test", "correct-horse")
        assertEquals(AuthFailure.NETWORK, (result as AuthResult.Failure).reason)
    }

    // -----------------------------------------------------------------
    // What goes on the wire
    // -----------------------------------------------------------------

    @Test
    @DisplayName("requests carry the publishable key and nothing that looks like a secret")
    fun requestsCarryOnlyThePublishableKey() = runTest {
        val transport = FakeTransport.always(200, Fixtures.tokenResponse())
        gateway(transport).signIn("amina@example.test", "correct-horse")

        val request = transport.sent.single()
        assertEquals("sb_publishable_test_key", request.headers["apikey"])
        assertTrue(request.url.startsWith("https://example-project.supabase.co/auth/v1/token"))
        assertNotNull(request.body)
    }

    @Test
    @DisplayName("a service-role key cannot be put into the client configuration at all")
    fun serviceKeysAreRefused() {
        val failures = listOf("sb_secret_abcdef", "eyJ...service_role...")
        for (key in failures) {
            val error = runCatching {
                SupabaseConfig("https://example-project.supabase.co", key)
            }.exceptionOrNull()
            assertNotNull(error, "a $key key should have been refused")
        }
    }

    @Test
    @DisplayName("a plaintext project URL is refused")
    fun plaintextUrlIsRefused() {
        assertNotNull(
            runCatching { SupabaseConfig("http://example-project.supabase.co", "sb_publishable_x") }
                .exceptionOrNull(),
        )
    }

    // -----------------------------------------------------------------
    // Where the confirmation link sends people afterwards.
    //
    // These exist because of a real failure: with no redirect configured,
    // GoTrue verified the address correctly and then sent the phone's
    // browser to the project's default Site URL -- http://localhost:3000
    // -- which showed "couldn't be reached". The account worked. Every
    // visible signal said it had not.
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the confirmation link is pointed back at the app when one is configured")
    fun signUpCarriesTheRedirect() = runTest {
        val transport = FakeTransport.always(200, """{"user":{"id":"${Fixtures.USER_ID}"}}""")
        val configured = SupabaseAuthGateway(
            Fixtures.config.copy(emailRedirectTo = "fisabilillah://auth/confirmed"),
            transport,
            FixedClock(NOW),
        )

        configured.signUp("new@example.test", "a-long-enough-password")

        val sent = transport.sent.single()
        assertTrue(
            sent.url.contains("redirect_to=fisabilillah%3A%2F%2Fauth%2Fconfirmed"),
            "the redirect must be percent-encoded into the query: ${sent.url}",
        )
    }

    @Test
    @DisplayName("password recovery and resend carry it too")
    fun theOtherMailsCarryTheRedirect() = runTest {
        val configured = { transport: FakeTransport ->
            SupabaseAuthGateway(
                Fixtures.config.copy(emailRedirectTo = "fisabilillah://auth/confirmed"),
                transport,
                FixedClock(NOW),
            )
        }

        val recovery = FakeTransport.always(200, "{}")
        configured(recovery).sendRecoveryEmail("someone@example.test")
        assertTrue(recovery.sent.single().url.contains("redirect_to="))

        // A person who has forgotten their password and then lands on a browser error has
        // been failed twice in a row.
        val resend = FakeTransport.always(200, "{}")
        configured(resend).resendConfirmation("someone@example.test")
        assertTrue(resend.sent.single().url.contains("redirect_to="))
    }

    @Test
    @DisplayName("with no redirect configured the URL is left exactly as it was")
    fun noRedirectMeansNoQuery() = runTest {
        val transport = FakeTransport.always(200, """{"user":{"id":"${Fixtures.USER_ID}"}}""")
        gateway(transport).signUp("new@example.test", "a-long-enough-password")

        val url = transport.sent.single().url
        assertTrue(url.endsWith("/signup"), "expected a bare signup URL, got $url")
    }
}
