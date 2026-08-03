package org.fisabilillah.core.payments

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.fisabilillah.core.auth.HttpRequest
import org.fisabilillah.core.auth.HttpTransport
import org.fisabilillah.core.auth.HttpTransportException
import org.fisabilillah.core.auth.SupabaseConfig
import org.fisabilillah.core.domain.PaymentGateway
import org.fisabilillah.core.model.CheckoutSession
import org.fisabilillah.core.model.DonationId

/**
 * Asks the `donation-checkout` edge function for a Stripe checkout page.
 *
 * There is no Stripe key in this class, no Stripe host, and no Stripe request. That is not
 * an accident of layering — it is the design. A key that reaches a client is a key that
 * has been published, because an APK is a zip file somebody else is holding. The only
 * thing this application knows how to do is ask a server it is authenticated to for a URL,
 * and the only thing it gets back is that URL.
 *
 * Note what it also does not send: no currency, no fee, no donor id, and no assertion
 * about whether the campaign may collect. All four are derived server-side. The request
 * body carries a campaign, an amount, and a yes-or-no about anonymity, and a caller who
 * tampers with any of the three gets a refusal from the function rather than a different
 * charge.
 */
public class SupabaseCheckoutGateway(
    private val config: SupabaseConfig,
    private val transport: HttpTransport,
    /**
     * Supplies the signed-in member's access token, or null when there is no session.
     *
     * A function rather than a token, because a token held at construction is a token that
     * expires while the screen is open; this is called at the moment of use so the session
     * can refresh underneath it.
     */
    private val accessToken: suspend () -> String?,
) : PaymentGateway {

    override val isConfigured: Boolean get() = true

    override suspend fun startCheckout(
        request: PaymentGateway.CheckoutRequest,
    ): CheckoutSession? {
        val token = accessToken() ?: return null

        val body = buildJsonObject {
            put("campaignId", JsonPrimitive(request.campaignId.value))
            put("amountMinorUnits", JsonPrimitive(request.amount.minorUnits))
            put("anonymous", JsonPrimitive(request.anonymous))
        }

        val response = try {
            transport.send(
                HttpRequest(
                    method = "POST",
                    url = "${config.projectUrl}/functions/v1/donation-checkout",
                    headers = mapOf(
                        "apikey" to config.publishableKey,
                        "Authorization" to "Bearer $token",
                        "Content-Type" to "application/json",
                    ),
                    body = body.toString(),
                ),
            )
        } catch (_: HttpTransportException) {
            // A refusal the donor can act on is produced by the use case above; this layer
            // says only "it did not happen", because it genuinely cannot tell the
            // difference between a flat battery of a server and a tunnel.
            return null
        }

        if (!response.isSuccess) return null

        val payload = runCatching { Json.parseToJsonElement(response.body) as? JsonObject }
            .getOrNull() ?: return null

        val url = payload["url"]?.jsonPrimitive?.contentOrNull() ?: return null
        val reference = payload["providerReference"]?.jsonPrimitive?.contentOrNull() ?: return null
        val donationId = payload["donationId"]?.jsonPrimitive?.contentOrNull() ?: return null

        // The server decides where the donor goes. Refusing anything that is not https is
        // still worth doing here: a redirect to a look-alike page is the single most
        // valuable thing an attacker could achieve against a giving flow, and the check
        // costs one line.
        if (!url.startsWith("https://")) return null

        return CheckoutSession(
            url = url,
            donationId = DonationId(donationId),
            providerReference = reference,
            expiresAt = payload["expiresAt"]?.jsonPrimitive?.contentOrNull()
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: Instant.DISTANT_FUTURE,
        )
    }
}

private fun JsonPrimitive.contentOrNull(): String? = content.takeIf { it.isNotBlank() }
