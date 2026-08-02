package org.fisabilillah.core.auth

/**
 * A transport that answers from a script and records what it was asked.
 *
 * Enough to assert the two things that actually matter about this module: that the right
 * request went out (with the right token, and without anything that should not be in it),
 * and that the response was turned into the right [AuthFailure].
 */
internal class FakeTransport(
    private val handler: (HttpRequest) -> HttpResponse,
) : HttpTransport {

    val sent: MutableList<HttpRequest> = mutableListOf()

    override suspend fun send(request: HttpRequest): HttpResponse {
        sent += request
        return handler(request)
    }

    val lastBody: String get() = sent.last().body.orEmpty()

    fun authorizationOf(index: Int): String? = sent[index].headers["Authorization"]

    companion object {
        fun always(status: Int, body: String): FakeTransport =
            FakeTransport { HttpResponse(status, body) }

        /** Answers each request from the queue in order. */
        fun sequence(vararg responses: Pair<Int, String>): FakeTransport {
            val queue = ArrayDeque(responses.toList())
            return FakeTransport {
                val (status, body) = queue.removeFirstOrNull()
                    ?: error("the fake transport ran out of scripted responses")
                HttpResponse(status, body)
            }
        }

        fun failing(): HttpTransport = object : HttpTransport {
            override suspend fun send(request: HttpRequest): HttpResponse =
                throw HttpTransportException("no network in this test")
        }
    }
}

internal object Fixtures {
    val config: SupabaseConfig = SupabaseConfig(
        projectUrl = "https://example-project.supabase.co",
        publishableKey = "sb_publishable_test_key",
    )

    const val USER_ID = "9f1b0c3e-0000-4000-8000-000000000001"

    fun tokenResponse(
        access: String = "access-token-1",
        refresh: String = "refresh-token-1",
        expiresIn: Long = 3600,
        email: String = "amina@example.test",
    ): String = """
        {"access_token":"$access","refresh_token":"$refresh","expires_in":$expiresIn,
         "token_type":"bearer",
         "user":{"id":"$USER_ID","email":"$email","email_confirmed_at":"2026-01-01T00:00:00Z"}}
    """.trimIndent()

    fun memberRow(
        hasProfile: Boolean = true,
        roles: String = """["member"]""",
        verification: String = "basic",
        gender: String = "female",
    ): String = """
        [{"user_id":"$USER_ID","display_name":"Amina S.","contact_email":"amina@example.test",
          "gender":"$gender","locale":"en","timezone":"Europe/London",
          "verification_level":"$verification","role_keys":$roles,
          "has_profile":$hasProfile,"covenant_accepted":true,"email_confirmed":true}]
    """.trimIndent()
}
