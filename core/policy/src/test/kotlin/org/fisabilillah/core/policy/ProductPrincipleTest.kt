package org.fisabilillah.core.policy

import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.Campaign
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.CampaignStatus
import org.fisabilillah.core.model.CampaignVerification
import org.fisabilillah.core.model.FundType
import org.fisabilillah.core.model.Money
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.DonationFeatureFlags
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.TeachingCapacity
import org.fisabilillah.core.model.TrustLabel
import org.fisabilillah.core.model.TrustRecord
import org.fisabilillah.core.model.VisibleProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Guards on the product's stated principles.
 *
 * These are unusual tests: they assert things about the *shape* of the domain rather than
 * about behaviour. They exist because the failure mode this product most needs to defend
 * against is not a bug — it is drift. A follower count added in good faith, a "top
 * volunteers" list built to encourage people, a notification designed to bring someone
 * back: each is a small, reasonable-sounding change, and together they turn a service
 * platform into the thing it was built not to be.
 *
 * A future engineer who genuinely needs to add one of these will have to delete a test that
 * says why it should not exist. That is the point.
 */
@DisplayName("Product principles")
class ProductPrincipleTest {

    private val vanityTerms = listOf(
        "follower", "following", "like", "likes", "upvote", "karma", "score", "rank",
        "ranking", "popularity", "trending", "streak", "leaderboard", "views", "viewcount",
        "impressions", "reactions", "endorsementcount", "admirer", "visitor",
    )

    @Test
    @DisplayName("no publicly visible type carries a vanity metric")
    fun noVanityMetricsOnPublicTypes() {
        val publicTypes = listOf(VisibleProfile::class.java, Profile::class.java)
        for (type in publicTypes) {
            for (field in type.declaredFields) {
                val name = field.name.lowercase()
                val offending = vanityTerms.firstOrNull { name.contains(it) }
                assertTrue(
                    offending == null,
                    "${type.simpleName}.${field.name} looks like a vanity metric ($offending). " +
                        "This product measures completed service, not attention.",
                )
            }
        }
    }

    @Test
    @DisplayName("the private trust record never leaves as a number")
    fun trustIsNeverExposedNumerically() {
        // TrustRecord is full of counts, deliberately — it is the safety team's and the
        // organiser's working material. What matters is that the only function producing a
        // member-visible result returns labels, not figures.
        val labels = TrustPolicy.publicLabels(
            profile = Fixtures.profile("member"),
            trust = TrustRecord(
                userId = org.fisabilillah.core.model.UserId("member"),
                commitmentsCompleted = 137,
                onTimeArrivals = 130,
                organizerConfirmations = 137,
            ),
        )
        assertTrue(labels.all { it is TrustLabel })

        // The exact figure is nowhere in what a viewer sees.
        val rendered = labels.joinToString { it.displayName }
        assertFalse(rendered.contains("137"))
    }

    @Test
    @DisplayName("every conversation purpose is a reason to act, never a reason to browse")
    fun purposesAreServiceOriented() {
        // Each purpose either points at a piece of work, or is a channel the platform itself
        // opens. There is no "just saying hello", and no purpose a user can pick that means
        // "I found you interesting".
        for (kind in ContactPurposeKind.entries) {
            if (kind.userSelectable) {
                assertTrue(
                    kind.requiresSubject || kind == ContactPurposeKind.ORGANIZATION_INQUIRY,
                    "${kind.name} is user-selectable but points at nothing, which makes it " +
                        "an unsolicited direct message with extra steps.",
                )
            }
        }
        assertFalse(ContactPurposeKind.FORMAL_INTRODUCTION.userSelectable)
        assertFalse(ContactPurposeKind.MODERATION_MATTER.userSelectable)
    }

    @Test
    @DisplayName("no notification exists to bring someone back for its own sake")
    fun notificationsAreAboutObligations() {
        val engagementBait = listOf(
            "streak", "missyou", "comeback", "trending", "popular", "viewedyourprofile",
            "someoneviewed", "dontmiss", "reminder_to_open",
        )
        for (kind in NotificationKind.entries) {
            val name = kind.name.lowercase().replace("_", "")
            val offending = engagementBait.firstOrNull { name.contains(it) }
            assertTrue(
                offending == null,
                "${kind.name} looks like engagement bait ($offending). Every notification " +
                    "here should correspond to a commitment, a conversation, or a safety matter.",
            )
        }
    }

    @Test
    @DisplayName("roles that carry authority or religious standing cannot be self-assigned")
    fun authorityIsNeverSelfAsserted() {
        val mustNotBeSelfAssignable = setOf(
            AccountRole.SCHOLAR,
            AccountRole.TEACHER,
            AccountRole.MODERATOR,
            AccountRole.SAFETY_ADMINISTRATOR,
            AccountRole.PLATFORM_ADMINISTRATOR,
            AccountRole.CHARITY_ORGANIZATION,
            AccountRole.MASJID_ORGANIZATION,
            AccountRole.WALI_CONTACT,
        )
        for (role in mustNotBeSelfAssignable) {
            assertFalse(
                role.selfAssignable,
                "${role.name} must never be something a member can grant themselves.",
            )
        }
    }

    @Test
    @DisplayName("every teaching capacity states its own limits")
    fun teachingCapacitiesAreHonest() {
        for (capacity in TeachingCapacity.entries) {
            assertTrue(
                capacity.disclaimer.isNotBlank(),
                "${capacity.name} has no disclaimer, so a student would have nothing telling " +
                    "them what this person is and is not.",
            )
        }
        // The three that imply religious authority must all require review.
        assertTrue(TeachingCapacity.VERIFIED_SCHOLAR.requiresQualificationReview)
        assertTrue(TeachingCapacity.QURAN_TEACHER.requiresQualificationReview)
        assertTrue(TeachingCapacity.LICENSED_PROFESSIONAL.requiresQualificationReview)

        // And peer help must say plainly that it is not a substitute.
        assertTrue(TeachingCapacity.PEER_HELPER.disclaimer.contains("Not a substitute"))
    }

    @Test
    @DisplayName("a campaign cannot take money before somebody has verified it")
    fun givingRequiresVerification() {
        // This replaces an older guard that asserted one global flag was false. A global
        // flag was the wrong shape: it made "may money move" a property of the build
        // rather than of the campaign, so flipping it switched on every campaign at once,
        // including ones nobody had checked. The invariant that matters is narrower and is
        // enforced by the type itself.
        val unverified = campaignFixture(CampaignVerification.PENDING)
        assertFalse(unverified.canAcceptDonations)

        assertThrows(IllegalArgumentException::class.java) {
            unverified.copy(paymentsEnabled = true)
        }

        val verified = campaignFixture(CampaignVerification.VERIFIED)
        assertFalse(
            verified.canAcceptDonations,
            "Verification alone must not be enough: an administrator still has to enable it.",
        )
        assertTrue(verified.copy(paymentsEnabled = true).canAcceptDonations)

        // Regular giving is still off, and for reasons that are not about compliance
        // paperwork — see the flag's own comment.
        assertFalse(DonationFeatureFlags.recurringEnabled)
        assertThrows(IllegalArgumentException::class.java) {
            verified.copy(allowsRecurring = true)
        }
    }

    @Test
    @DisplayName("the donor is told the processor takes a fee")
    fun feeIsDisclosed() {
        // A donor who believes every penny arrives has been misled by omission, and the
        // omission is the easy thing to ship.
        assertTrue(DonationFeatureFlags.feeNotice.contains("fee"))
        assertTrue(
            DonationFeatureFlags.feeNotice.contains("no platform fee", ignoreCase = true),
            "If a platform fee is ever introduced, this copy has to change with it.",
        )
    }

    @Test
    @DisplayName("the platform never claims a fitnah-free environment")
    fun noOverclaiming() {
        // Wherever the product speaks about safety, it describes what it does rather than
        // promising an outcome no software can deliver.
        val safetyCopy = listOf(
            ContentSignals.DISCLOSURE,
            IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE,
            DonationFeatureFlags.unverifiedNotice,
            DonationFeatureFlags.feeNotice,
        )
        val overclaims = listOf("guarantee", "guaranteed", "completely safe", "fitnah-free", "risk-free")
        for (copy in safetyCopy) {
            for (claim in overclaims) {
                assertFalse(
                    copy.contains(claim, ignoreCase = true),
                    "Copy overclaims with \"$claim\": $copy",
                )
            }
        }
    }

    @Test
    @DisplayName("automated checks are described as advisory everywhere they are described")
    fun automationNeverDecides() {
        assertTrue(ContentSignals.DISCLOSURE.contains("never restrict an account"))
        assertTrue(ContentSignals.DISCLOSURE.contains("flag a conversation for a human"))

        val signals = ContentSignals.forMessage("send pics", conversationIsCrossGender = true)
        assertTrue(signals.isNotEmpty())
        assertTrue(signals.all { it.isAdvisoryOnly })
    }

    @Test
    @DisplayName("visibility decisions consult safeguards, never anything rankable")
    fun visibilityHasNothingToRankBy() {
        // The viewer's identity is the only input to what they are shown. There is no
        // affinity, no engagement history, and no relevance score for a future change to
        // start weighting results by.
        val viewerFields = Viewer::class.java.declaredFields.map { it.name.lowercase() }
        val rankable = listOf("score", "affinity", "relevance", "weight", "engagement", "rank")
        for (field in viewerFields) {
            assertTrue(
                rankable.none { field.contains(it) },
                "Viewer.$field would let discovery start ranking people.",
            )
        }
        assertEquals(
            emptyList<String>(),
            viewerFields.filter { it.contains("history") },
        )
    }

    /** The smallest campaign that satisfies the type. Nothing here is under test. */
    private fun campaignFixture(verification: CampaignVerification): Campaign = Campaign(
        id = CampaignId("campaign-fixture"),
        organizationId = OrganizationId("org-fixture"),
        title = "Winter food parcels",
        summary = "Parcels for families in the area over winter.",
        fundType = FundType.SADAQAH,
        goal = Money(500_000, "GBP"),
        raised = Money.zero("GBP"),
        currencyCode = "GBP",
        verification = verification,
        status = CampaignStatus.ACTIVE,
    )
}
