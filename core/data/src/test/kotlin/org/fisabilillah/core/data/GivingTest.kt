package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.DonateUseCase
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.domain.PaymentGateway
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.CampaignVerification
import org.fisabilillah.core.model.CheckoutSession
import org.fisabilillah.core.model.DonationState
import org.fisabilillah.core.model.GivingLimits
import org.fisabilillah.core.model.OrganizationVerification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Giving, end to end through the real use cases.
 *
 * The theme running through these is that the donor's device is not trusted with anything
 * that costs money. It supplies an amount and a campaign; everything else — the currency,
 * the recipient, whether collection is permitted at all, and above all whether the payment
 * happened — is decided somewhere the donor cannot reach.
 */
@DisplayName("Giving")
internal class GivingTest {

    /**
     * A payment processor that never fails, and records what it was asked for.
     *
     * Deliberately has no way to report success back into the application: there is no
     * `settle()` here because there is none in the port either. Settlement arrives by
     * webhook, and a test that could fake it would be testing a path that does not exist.
     */
    private class FakeGateway(
        override val isConfigured: Boolean = true,
    ) : PaymentGateway {
        val requests: MutableList<PaymentGateway.CheckoutRequest> = mutableListOf()
        var reachable: Boolean = true

        override suspend fun startCheckout(
            request: PaymentGateway.CheckoutRequest,
        ): CheckoutSession? {
            requests += request
            if (!reachable) return null
            return CheckoutSession(
                url = "https://checkout.stripe.com/c/pay/${request.donationId.value}",
                donationId = request.donationId,
                providerReference = "cs_test_${request.donationId.value}",
                expiresAt = request.amount.let { SeedData.EPOCH },
            )
        }
    }

    private val winterFund = CampaignId("campaign-winter-fund")

    /** The seeded campaign is verified but not switched on; most tests need it switched on. */
    private suspend fun Harness.enablePayments() {
        val campaign = graph.campaigns.find(winterFund)!!
        graph.campaigns.save(campaign.copy(paymentsEnabled = true))
    }

    @Test
    fun `a campaign nobody has verified cannot be given to`() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(payments = gateway)
        val campaign = harness.graph.campaigns.find(winterFund)!!
        harness.graph.campaigns.save(
            campaign.copy(verification = CampaignVerification.PENDING),
        )

        val outcome = harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 5_000),
        )

        assertTrue(outcome is Outcome.Refused, "expected a refusal, got $outcome")
        assertTrue(harness.store.donations.isEmpty(), "nothing may be recorded")
        assertTrue(gateway.requests.isEmpty(), "the processor must never have been called")
    }

    @Test
    fun `verification alone is not permission to collect`() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(payments = gateway)

        // The seeded campaign is VERIFIED and its organisation's registration is checked.
        // It still refuses, because an administrator has not enabled it.
        assertEquals(
            CampaignVerification.VERIFIED,
            harness.graph.campaigns.find(winterFund)!!.verification,
        )

        val outcome = harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 5_000),
        )

        assertTrue(outcome is Outcome.Refused)
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    @DisplayName("an organisation whose registration has lapsed stops collecting immediately")
    fun lapsedRegistrationStopsCollection() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(payments = gateway)
        harness.enablePayments()

        // The campaign stays enabled and verified. Only the organisation changes. This is
        // the case that a check performed at enable-time rather than at give-time misses.
        val organization = harness.graph.organizations.find(SeedData.ashbourneRelief)!!
        harness.graph.organizations.save(
            organization.copy(verification = OrganizationVerification(registrationChecked = false)),
        )

        val outcome = harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 5_000),
        )

        assertTrue(outcome is Outcome.Refused, "expected a refusal, got $outcome")
        assertTrue(harness.store.donations.isEmpty())
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    fun `an amount below the floor or above the ceiling is refused and records nothing`() =
        runTest {
            val gateway = FakeGateway()
            val harness = Harness(payments = gateway)
            harness.enablePayments()
            val donor = harness.principal(SeedData.abdullah)

            val tooSmall = harness.graph.donate(
                donor,
                DonateUseCase.Command(
                    campaignId = winterFund,
                    amountMinorUnits = GivingLimits.MINIMUM_MINOR_UNITS - 1,
                ),
            )
            val tooLarge = harness.graph.donate(
                donor,
                DonateUseCase.Command(
                    campaignId = winterFund,
                    amountMinorUnits = GivingLimits.MAXIMUM_MINOR_UNITS + 1,
                ),
            )

            assertTrue(tooSmall is Outcome.Invalid, "expected Invalid, got $tooSmall")
            assertTrue(tooLarge is Outcome.Invalid, "expected Invalid, got $tooLarge")
            assertEquals("amount", (tooSmall as Outcome.Invalid).errors.single().field)
            assertTrue(
                (tooLarge as Outcome.Invalid).errors.single().message
                    .contains("contact the organisation directly"),
                "the ceiling must point somewhere, not just say no",
            )
            assertTrue(harness.store.donations.isEmpty())
            assertTrue(gateway.requests.isEmpty())
        }

    @Test
    @DisplayName("a started donation is never recorded as settled")
    fun startingIsNotSettling() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(payments = gateway)
        harness.enablePayments()

        val outcome = harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 2_500),
        )

        val session = (outcome as Outcome.Success).value
        assertTrue(session.url.startsWith("https://"))

        val donation = harness.store.donations.values.single()
        assertEquals(DonationState.AWAITING_PAYMENT, donation.state)
        assertNull(donation.settledAt)
        assertNull(donation.netAmount, "the net amount is unknown until the processor says")
        assertEquals(session.providerReference, donation.providerReference)

        // Nothing anywhere in the application can move it on from here.
        assertFalse(
            harness.store.donations.values.any { it.state == DonationState.SETTLED },
        )
        assertEquals(
            0,
            harness.graph.campaigns.find(winterFund)!!.raised.minorUnits,
            "an unsettled donation must not count towards the total",
        )
    }

    @Test
    fun `the currency comes from the campaign rather than from the caller`() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(payments = gateway)
        harness.enablePayments()

        harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 2_500),
        ).expectSuccess()

        // The command has no currency field at all — this asserts that stays true, because
        // adding one would let a caller offer £25 and be billed 25 of something dearer.
        val request = gateway.requests.single()
        assertEquals("GBP", request.amount.currencyCode)
        assertEquals(2_500, request.amount.minorUnits)
        assertEquals(
            "GBP",
            harness.store.donations.values.single().amount.currencyCode,
        )
    }

    @Test
    @DisplayName("the processor is told which donation it is paying for")
    fun theProcessorCarriesTheDonationId() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(payments = gateway)
        harness.enablePayments()

        harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 2_500),
        ).expectSuccess()

        // Without this the webhook has a payment and no way to know what it settles, and
        // the money arrives with nobody's name on it.
        val request = gateway.requests.single()
        assertEquals(harness.store.donations.values.single().id, request.donationId)
        assertEquals(winterFund, request.campaignId)
    }

    @Test
    fun `an unreachable processor charges nothing and says so`() = runTest {
        val gateway = FakeGateway().apply { reachable = false }
        val harness = Harness(payments = gateway)
        harness.enablePayments()

        val outcome = harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 2_500),
        )

        assertTrue(outcome is Outcome.Refused)
        assertTrue(
            (outcome as Outcome.Refused).message.contains("Nothing has been charged"),
            "a failure here frightens people; say plainly that no money moved",
        )
        assertTrue(harness.store.donations.isEmpty())
    }

    @Test
    fun `a build with no payment processor refuses without pretending`() = runTest {
        val harness = Harness()
        harness.enablePayments()

        val outcome = harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 2_500),
        )

        assertTrue(outcome is Outcome.Refused)
        assertTrue(harness.store.donations.isEmpty())
    }

    @Test
    fun `starting a donation is written to the permanent record`() = runTest {
        val harness = Harness(payments = FakeGateway())
        harness.enablePayments()
        val before = harness.store.auditLog.size

        harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 2_500),
        ).expectSuccess()

        val entry = harness.store.auditLog.drop(before).single()
        assertEquals(AuditAction.DONATION_STARTED, entry.action)
        assertEquals(SeedData.abdullah, entry.actorId)
        assertEquals(winterFund.value, entry.subjectId)
        assertTrue(entry.summary.contains("£25"), "the amount belongs in the record")
    }

    @Test
    fun `only a platform administrator can allow a campaign to collect`() = runTest {
        val harness = Harness(payments = FakeGateway())

        // Hafsa administers Ashbourne Relief. She can edit her own campaign; she cannot
        // decide it may take money.
        val asOrgAdmin = harness.graph.campaignPayments.enable(
            harness.principal(SeedData.hafsa),
            winterFund,
            reason = "We are ready to launch",
        )
        assertTrue(asOrgAdmin is Outcome.Refused, "expected a refusal, got $asOrgAdmin")
        assertFalse(harness.graph.campaigns.find(winterFund)!!.paymentsEnabled)

        val asAdmin = harness.graph.campaignPayments.enable(
            harness.principal(SeedData.safetyAdmin),
            winterFund,
            reason = "Registration and bank account verified on 12 March.",
        )
        assertTrue(asAdmin is Outcome.Success, "expected success, got $asAdmin")
        assertTrue(harness.graph.campaigns.find(winterFund)!!.paymentsEnabled)

        val entry = harness.store.auditLog.last()
        assertEquals(AuditAction.CAMPAIGN_PAYMENTS_ENABLED, entry.action)
        assertTrue(entry.summary.contains("Registration and bank account verified"))
    }

    @Test
    fun `an administrator cannot switch on a campaign that was never verified`() = runTest {
        val harness = Harness(payments = FakeGateway())
        val campaign = harness.graph.campaigns.find(winterFund)!!
        harness.graph.campaigns.save(campaign.copy(verification = CampaignVerification.PENDING))

        val outcome = harness.graph.campaignPayments.enable(
            harness.principal(SeedData.safetyAdmin),
            winterFund,
            reason = "Looks fine to me",
        )

        assertTrue(outcome is Outcome.Refused, "expected a refusal, got $outcome")
        assertFalse(harness.graph.campaigns.find(winterFund)!!.paymentsEnabled)
    }

    @Test
    fun `a donor sees their own giving and nobody else's`() = runTest {
        val harness = Harness(payments = FakeGateway())
        harness.enablePayments()

        harness.graph.donate(
            harness.principal(SeedData.abdullah),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 2_500),
        ).expectSuccess()
        harness.graph.donate(
            harness.principal(SeedData.yusuf),
            DonateUseCase.Command(campaignId = winterFund, amountMinorUnits = 10_000),
        ).expectSuccess()

        val mine = harness.graph.myDonations(harness.principal(SeedData.abdullah))
            .expectSuccess()
        assertEquals(1, mine.size)
        assertEquals(2_500, mine.single().donation.amount.minorUnits)
        assertNull(
            mine.single().reachedTheCampaign,
            "what reached the campaign is not guessed at before it settles",
        )
        assertNotNull(mine.single().campaignTitle)
    }
}
