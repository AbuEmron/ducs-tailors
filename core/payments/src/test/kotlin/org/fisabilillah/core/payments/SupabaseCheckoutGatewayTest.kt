package org.fisabilillah.core.payments

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.auth.HttpRequest
import org.fisabilillah.core.auth.HttpResponse
import org.fisabilillah.core.auth.HttpTransport
import org.fisabilillah.core.auth.HttpTransportException
import org.fisabilillah.core.auth.SupabaseConfig
import org.fisabilillah.core.domain.PaymentGateway
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.DonationId
import org.fisabilillah.core.model.Money
import org.fisabilillah.core.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The checkout gateway")
internal class SupabaseCheckoutGatewayTest {

    private class FakeTransport(
        var status: Int = 200,
        var body: String = "",
        var throwOnSend: Boolean = false,
    ) : HttpTransport {
        val sent: MutableList<HttpRequest> = mutableListOf()

        override suspend fun send(request: HttpRequest): HttpResponse {
            sent += request
            if (throwOnSend) throw HttpTransportException("no route to host")
            return HttpResponse(status, body)
        }
    }

    private val config = SupabaseConfig(
        projectUrl = "https://project.supabase.co",
        publishableKey = "sb_publishable_example",
    )

    private val request = PaymentGateway.CheckoutRequest(
        donationId = DonationId("donation-1"),
        campaignId = CampaignId("campaign-1"),
        campaignTitle = "Winter hardship fund",
        amount = Money(2_500, "GBP"),
        donorId = UserId("user-1"),
        anonymous = false,
    )

    private fun gateway(
        transport: HttpTransport,
        token: String? = "token",
    ) = SupabaseCheckoutGateway(config, transport) { token }

    private val goodResponse = """
        {
          "donationId": "server-side-id",
          "providerReference": "cs_test_123",
          "url": "https://checkout.stripe.com/c/pay/cs_test_123",
          "expiresAt": "2026-08-03T12:00:00Z"
        }
    """.trimIndent()

    @Test
    fun `a checkout page is returned and its identifiers come from the server`() = runTest {
        val transport = FakeTransport(body = goodResponse)

        val session = gateway(transport).startCheckout(request)

        assertNotNull(session)
        assertEquals("https://checkout.stripe.com/c/pay/cs_test_123", session!!.url)
        assertEquals("cs_test_123", session.providerReference)
        // Not the id this client proposed. The server's row is the real one.
        assertEquals(DonationId("server-side-id"), session.donationId)
    }

    @Test
    @DisplayName("nothing about Stripe appears in the request")
    fun theClientNeverTalksToStripe() = runTest {
        val transport = FakeTransport(body = goodResponse)
        gateway(transport).startCheckout(request)

        val sent = transport.sent.single()
        assertTrue(sent.url.startsWith("https://project.supabase.co/functions/v1/"))
        assertFalse(sent.url.contains("stripe"), "the client must never call Stripe directly")

        val everythingSent = sent.url + sent.headers.values.joinToString() + sent.body.orEmpty()
        for (fragment in listOf("sk_live", "sk_test", "rk_live", "service_role")) {
            assertFalse(
                everythingSent.contains(fragment),
                "a secret-shaped value reached the wire: $fragment",
            )
        }
    }

    @Test
    @DisplayName("the request carries no currency, donor or fee")
    fun theClientDecidesAlmostNothing() = runTest {
        val transport = FakeTransport(body = goodResponse)
        gateway(transport).startCheckout(request)

        val body = transport.sent.single().body.orEmpty()
        assertTrue(body.contains("campaign-1"))
        assertTrue(body.contains("2500"))
        // If any of these ever start being sent, the server has been given the chance to
        // trust them, which is the whole class of bug this design exists to prevent.
        for (field in listOf("currency", "GBP", "donorId", "user-1", "fee", "netAmount")) {
            assertFalse(body.contains(field), "\"$field\" must not be sent by the client")
        }
    }

    @Test
    fun `a page that is not https is refused however the server phrased it`() = runTest {
        val transport = FakeTransport(
            body = goodResponse.replace(
                "https://checkout.stripe.com",
                "http://checkout.stripe.com.evil.example",
            ),
        )

        assertNull(
            gateway(transport).startCheckout(request),
            "a plaintext or look-alike checkout page is the one thing worth refusing locally",
        )
    }

    @Test
    fun `a refusal from the function is not turned into a checkout page`() = runTest {
        val transport = FakeTransport(status = 409, body = """{"error":"not collecting"}""")
        assertNull(gateway(transport).startCheckout(request))
    }

    @Test
    fun `an unreachable server is a null rather than an exception`() = runTest {
        val transport = FakeTransport(throwOnSend = true)
        assertNull(gateway(transport).startCheckout(request))
    }

    @Test
    fun `a malformed response is refused rather than half-read`() = runTest {
        val transport = FakeTransport(body = "not json at all")
        assertNull(gateway(transport).startCheckout(request))

        val missingUrl = FakeTransport(body = """{"providerReference":"cs_1"}""")
        assertNull(gateway(missingUrl).startCheckout(request))
    }

    @Test
    fun `no session means no request is made at all`() = runTest {
        val transport = FakeTransport(body = goodResponse)
        assertNull(gateway(transport, token = null).startCheckout(request))
        assertTrue(transport.sent.isEmpty(), "an anonymous caller must not reach the function")
    }

    @Test
    fun `the member's token is what authorises the call`() = runTest {
        val transport = FakeTransport(body = goodResponse)
        gateway(transport, token = "the-access-token").startCheckout(request)

        assertEquals(
            "Bearer the-access-token",
            transport.sent.single().headers["Authorization"],
        )
    }
}
