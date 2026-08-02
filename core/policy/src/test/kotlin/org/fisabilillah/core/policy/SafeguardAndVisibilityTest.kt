package org.fisabilillah.core.policy

import org.fisabilillah.core.model.Attestation
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.Contactability
import org.fisabilillah.core.model.CrossGenderConversationStructure
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.LocationPrecision
import org.fisabilillah.core.model.ModeratorPresenceRule
import org.fisabilillah.core.model.NameVisibility
import org.fisabilillah.core.model.ProfileImageStyle
import org.fisabilillah.core.model.SafeguardFloor
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.TrustLabel
import org.fisabilillah.core.model.TrustRecord
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.VerificationLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SafeguardResolverTest {

    private val user = UserId("member")

    @Test
    @DisplayName("an organisation floor can only tighten, never loosen")
    fun floorsOnlyTighten() {
        val chosen = Fixtures.safeguards("member").copy(
            contactableBy = AudienceScope.SAME_GENDER_ONLY,
            videoCallsAllowedFrom = AudienceScope.NOBODY,
            minimumVerificationToContactMe = VerificationLevel.IDENTITY_VERIFIED,
        )
        val permissiveFloor = SafeguardFloor(
            contactableBy = AudienceScope.EVERYONE,
            videoCallsAllowedFrom = AudienceScope.EVERYONE,
            minimumVerificationToContact = VerificationLevel.NONE,
        )

        val effective = SafeguardResolver.effective(chosen, permissiveFloor)

        assertEquals(AudienceScope.SAME_GENDER_ONLY, effective.contactableBy)
        assertEquals(AudienceScope.NOBODY, effective.videoCallsAllowedFrom)
        assertEquals(VerificationLevel.IDENTITY_VERIFIED, effective.minimumVerificationToContactMe)
    }

    @Test
    fun `a stricter floor is applied on top of the member's own choice`() {
        val chosen = Fixtures.safeguards("member").copy(
            contactableBy = AudienceScope.EVERYONE,
            crossGenderStructure = CrossGenderConversationStructure.DIRECT_WITH_PURPOSE,
            moderatorPresence = ModeratorPresenceRule.NEVER,
        )
        val strictFloor = SafeguardFloor(
            contactableBy = AudienceScope.MY_ORGANIZATIONS_ONLY,
            crossGenderStructure = CrossGenderConversationStructure.GROUP_CONTEXT_ONLY,
            moderatorPresence = ModeratorPresenceRule.ALWAYS,
            requireGroupContext = true,
        )

        val effective = SafeguardResolver.effective(chosen, strictFloor)

        assertEquals(AudienceScope.MY_ORGANIZATIONS_ONLY, effective.contactableBy)
        assertEquals(
            CrossGenderConversationStructure.GROUP_CONTEXT_ONLY,
            effective.crossGenderStructure,
        )
        assertEquals(ModeratorPresenceRule.ALWAYS, effective.moderatorPresence)
        assertTrue(effective.requireGroupContext)
    }

    @Test
    fun `multiple floors compose to the strictest of all of them`() {
        val chosen = Fixtures.safeguards("member").copy(contactableBy = AudienceScope.EVERYONE)
        val effective = SafeguardResolver.effective(
            chosen,
            listOf(
                SafeguardFloor(contactableBy = AudienceScope.VERIFIED_ONLY),
                SafeguardFloor(contactableBy = AudienceScope.SAME_GENDER_ONLY),
                SafeguardFloor(contactableBy = AudienceScope.MY_ORGANIZATIONS_ONLY),
            ),
        )
        assertEquals(AudienceScope.SAME_GENDER_ONLY, effective.contactableBy)
    }

    @Test
    fun `an organisation can forbid introductions but cannot enable them`() {
        val enabled = Fixtures.safeguards("member").copy(acceptFormalIntroductions = true)
        assertFalse(
            SafeguardResolver.effective(enabled, SafeguardFloor(forbidFormalIntroductions = true))
                .acceptFormalIntroductions,
        )

        val disabled = Fixtures.safeguards("member").copy(acceptFormalIntroductions = false)
        assertFalse(
            SafeguardResolver.effective(disabled, SafeguardFloor(forbidFormalIntroductions = false))
                .acceptFormalIntroductions,
        )
    }

    @Test
    fun `tightening is always recognised as at least as strict`() {
        val current = SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, user)
        val stricter = SafeguardPresets.forName(SafeguardPresetName.MAXIMUM_PRIVACY, user)
        assertTrue(SafeguardResolver.isAtLeastAsStrict(stricter, current))
        assertFalse(SafeguardResolver.isAtLeastAsStrict(current, stricter))
    }

    @Test
    fun `loosening is reported field by field in plain language`() {
        val current = Fixtures.safeguards("member").copy(
            contactableBy = AudienceScope.SAME_GENDER_ONLY,
            locationPrecision = LocationPrecision.REGION,
            acceptFormalIntroductions = false,
        )
        val candidate = current.copy(
            contactableBy = AudienceScope.EVERYONE,
            locationPrecision = LocationPrecision.EXACT,
            acceptFormalIntroductions = true,
        )

        val loosened = SafeguardResolver.loosenedFields(candidate, current)

        assertEquals(3, loosened.size)
        assertTrue(loosened.any { it.contains("start a conversation") })
        assertTrue(loosened.any { it.contains("location") })
        assertTrue(loosened.any { it.contains("formal family introductions") })
    }

    @Test
    @DisplayName("no preset is presented as more religious than another")
    fun presetsAreCircumstancesNotRankings() {
        // A guard against a future change that adds a score, level, or ordering to the
        // preset type. Presets describe situations; ranking them would be a claim about
        // people that this product must never make.
        val names = SafeguardPresetName.entries.map { it.name }
        assertTrue(names.none { it.contains("LEVEL") || it.contains("TIER") })
        for (preset in SafeguardPresetName.entries) {
            assertTrue(preset.summary.isNotBlank())
            assertFalse(preset.summary.contains("better", ignoreCase = true))
            assertFalse(preset.summary.contains("more religious", ignoreCase = true))
        }
    }

    @Test
    fun `every preset produces a usable set of safeguards`() {
        for (preset in SafeguardPresetName.entries) {
            val safeguards = SafeguardPresets.forName(preset, user)
            assertEquals(user, safeguards.userId)
            assertTrue(safeguards.autoArchiveAfterDays in 1..365)
        }
    }
}

class VisibilityPolicyTest {

    private val sister = Fixtures.profile("sister", gender = Gender.FEMALE).copy(
        realName = "Aminah Siddiqui",
        imageStyle = ProfileImageStyle.PHOTOGRAPH,
        imageUrl = "https://example.test/photo.jpg",
    )

    private fun viewer(gender: Gender, verification: VerificationLevel = VerificationLevel.IDENTITY_VERIFIED) =
        Viewer(
            id = UserId("viewer"),
            gender = gender,
            verificationLevel = verification,
        )

    @Test
    fun `a real name is withheld when the member shows a display name only`() {
        val visible = VisibilityPolicy.visibleProfile(
            subject = sister,
            safeguards = Fixtures.safeguards("sister")
                .copy(nameVisibility = NameVisibility.DISPLAY_NAME_ONLY),
            trust = TrustRecord(sister.id),
            viewer = viewer(Gender.FEMALE),
            sharedOrganizationIds = emptySet(),
            contactability = Contactability(canInitiate = true),
        )
        assertNull(visible.realName)
        assertEquals("Sister", visible.displayName)
    }

    @Test
    fun `a real name reaches an organiser when the member allowed exactly that`() {
        val visible = VisibilityPolicy.visibleProfile(
            subject = sister,
            safeguards = Fixtures.safeguards("sister")
                .copy(nameVisibility = NameVisibility.REAL_NAME_TO_ORGANIZERS),
            trust = TrustRecord(sister.id),
            viewer = viewer(Gender.FEMALE).copy(isOrganizerOfSharedCommitment = true),
            sharedOrganizationIds = emptySet(),
            contactability = Contactability(canInitiate = true),
        )
        assertEquals("Aminah Siddiqui", visible.realName)
    }

    @Test
    fun `a photograph is not shown to the opposite gender when set to same gender only`() {
        val toBrother = VisibilityPolicy.visibleProfile(
            subject = sister,
            safeguards = Fixtures.safeguards("sister")
                .copy(profileImageVisibleTo = AudienceScope.SAME_GENDER_ONLY),
            trust = TrustRecord(sister.id),
            viewer = viewer(Gender.MALE),
            sharedOrganizationIds = emptySet(),
            contactability = Contactability(canInitiate = true),
        )
        assertEquals(ProfileImageStyle.INITIALS, toBrother.imageStyle)
        assertNull(toBrother.imageUrl)

        val toSister = VisibilityPolicy.visibleProfile(
            subject = sister,
            safeguards = Fixtures.safeguards("sister")
                .copy(profileImageVisibleTo = AudienceScope.SAME_GENDER_ONLY),
            trust = TrustRecord(sister.id),
            viewer = viewer(Gender.FEMALE),
            sharedOrganizationIds = emptySet(),
            contactability = Contactability(canInitiate = true),
        )
        assertEquals(ProfileImageStyle.PHOTOGRAPH, toSister.imageStyle)
        assertNotNull(toSister.imageUrl)
    }

    @Test
    fun `location precision controls what a viewer is told`() {
        val place = Fixtures.place
        assertEquals("Ashbourne", VisibilityPolicy.locationLabel(place, LocationPrecision.CITY, false))
        assertEquals("Midshire", VisibilityPolicy.locationLabel(place, LocationPrecision.REGION, false))
        assertEquals("Northfield", VisibilityPolicy.locationLabel(place, LocationPrecision.NEIGHBOURHOOD, false))
        assertNull(VisibilityPolicy.locationLabel(place, LocationPrecision.HIDDEN, false))
    }

    @Test
    fun `a member who is not discoverable does not appear to an ordinary viewer`() {
        assertFalse(
            VisibilityPolicy.isDiscoverable(
                subject = sister,
                safeguards = Fixtures.safeguards("sister")
                    .copy(profileDiscoverableBy = AudienceScope.NOBODY),
                viewer = viewer(Gender.FEMALE),
                sharedOrganizationIds = emptySet(),
            ),
        )
    }

    @Test
    fun `someone who has been blocked cannot see the person who blocked them`() {
        assertFalse(
            VisibilityPolicy.isDiscoverable(
                subject = sister,
                safeguards = Fixtures.safeguards("sister"),
                viewer = viewer(Gender.FEMALE),
                sharedOrganizationIds = emptySet(),
                viewerBlockedBySubject = true,
            ),
        )
    }
}

class TrustPolicyTest {

    @Test
    @DisplayName("public trust labels never carry a number a viewer could rank by")
    fun labelsAreNotScores() {
        for (label in TrustLabel.entries) {
            assertTrue(label.explanation.isNotBlank())
        }
        // The only labels mentioning a count are deliberate thresholds, not totals.
        val withDigits = TrustLabel.entries.filter { it.displayName.any(Char::isDigit) }
        assertEquals(
            setOf(TrustLabel.COMPLETED_FIVE_COMMITMENTS, TrustLabel.COMPLETED_TWENTY_COMMITMENTS),
            withDigits.toSet(),
        )
    }

    @Test
    fun `a new member is labelled as new rather than as untrustworthy`() {
        val labels = TrustPolicy.publicLabels(
            profile = Fixtures.profile("new", verification = VerificationLevel.EMAIL_VERIFIED),
            trust = TrustRecord(UserId("new")),
        )
        assertTrue(TrustLabel.NEW_MEMBER in labels)
        assertFalse(TrustLabel.NO_UNRESOLVED_SAFETY_RESTRICTIONS in labels)
    }

    @Test
    fun `commitment thresholds produce the coarse label and not the count`() {
        val labels = TrustPolicy.publicLabels(
            profile = Fixtures.profile("active"),
            trust = TrustRecord(UserId("active"), commitmentsCompleted = 37, organizerConfirmations = 37),
        )
        assertTrue(TrustLabel.COMPLETED_TWENTY_COMMITMENTS in labels)
        assertFalse(TrustLabel.COMPLETED_FIVE_COMMITMENTS in labels)
    }

    @Test
    fun `an active restriction removes the clean-record label`() {
        val labels = TrustPolicy.publicLabels(
            profile = Fixtures.profile("restricted"),
            trust = TrustRecord(
                UserId("restricted"),
                commitmentsCompleted = 10,
                activeRestrictions = 1,
            ),
        )
        assertFalse(TrustLabel.NO_UNRESOLVED_SAFETY_RESTRICTIONS in labels)
    }

    @Test
    fun `an organiser summary is offered for a decision, not as a verdict`() {
        val summary = TrustPolicy.organizerSummary(TrustRecord(UserId("x")))
        assertTrue(summary.contains("not a mark against them"))
    }
}

class VerificationPolicyTest {

    @Test
    @DisplayName("a member cannot make themselves a scholar, moderator, or administrator")
    fun sensitiveRolesAreNotSelfAssignable() {
        val result = VerificationPolicy.sanitizeRoleRequest(
            requested = setOf(
                org.fisabilillah.core.model.AccountRole.SCHOLAR,
                org.fisabilillah.core.model.AccountRole.MODERATOR,
                org.fisabilillah.core.model.AccountRole.PLATFORM_ADMINISTRATOR,
                org.fisabilillah.core.model.AccountRole.TEACHER,
                org.fisabilillah.core.model.AccountRole.VOLUNTEER,
            ),
            currentlyHeld = emptySet(),
        )

        assertTrue(result.hadRejections)
        assertTrue(org.fisabilillah.core.model.AccountRole.SCHOLAR in result.rejected)
        assertTrue(org.fisabilillah.core.model.AccountRole.MODERATOR in result.rejected)
        assertTrue(org.fisabilillah.core.model.AccountRole.PLATFORM_ADMINISTRATOR in result.rejected)
        assertTrue(org.fisabilillah.core.model.AccountRole.TEACHER in result.rejected)
        assertTrue(org.fisabilillah.core.model.AccountRole.VOLUNTEER in result.granted)
    }

    @Test
    fun `a role already granted by the platform is preserved`() {
        val result = VerificationPolicy.sanitizeRoleRequest(
            requested = setOf(org.fisabilillah.core.model.AccountRole.SCHOLAR),
            currentlyHeld = setOf(org.fisabilillah.core.model.AccountRole.SCHOLAR),
        )
        assertFalse(result.hadRejections)
        assertTrue(org.fisabilillah.core.model.AccountRole.SCHOLAR in result.granted)
    }

    @Test
    fun `a claimed teaching capacity is labelled as unverified until it is reviewed`() {
        val state = VerificationPolicy.teachingCapacityState(
            capacity = org.fisabilillah.core.model.TeachingCapacity.VERIFIED_SCHOLAR,
            qualifications = emptyList(),
        )
        assertTrue(state is CapacityState.ClaimedPendingReview)
        assertTrue((state as CapacityState.ClaimedPendingReview).displayLabel.contains("not yet verified"))
    }

    @Test
    fun `a peer helper needs no review and says plainly what they are not`() {
        assertEquals(
            CapacityState.Permitted,
            VerificationPolicy.teachingCapacityState(
                capacity = org.fisabilillah.core.model.TeachingCapacity.PEER_HELPER,
                qualifications = emptyList(),
            ),
        )
        assertTrue(
            org.fisabilillah.core.model.TeachingCapacity.PEER_HELPER.disclaimer
                .contains("Not a substitute"),
        )
    }

    @Test
    @DisplayName("every badge states what it does not mean")
    fun badgesAreHonest() {
        for (level in VerificationLevel.entries) {
            val explanation = VerificationPolicy.badgeExplanation(level)
            assertTrue(explanation.whatItDoesNotMean.isNotBlank())
        }
        for (attestation in Attestation.entries) {
            val explanation = VerificationPolicy.badgeExplanation(attestation)
            assertTrue(explanation.whatItDoesNotMean.isNotBlank())
        }
        assertTrue(
            VerificationLevel.IDENTITY_VERIFIED.whatItDoesNotMean.contains("character"),
        )
    }

    @Test
    fun `giving stays in preview while payments are switched off`() {
        val organization = org.fisabilillah.core.model.Organization(
            id = org.fisabilillah.core.model.OrganizationId("org"),
            name = "Verified Relief",
            kind = org.fisabilillah.core.model.OrganizationKind.CHARITY,
            summary = "A verified charity",
            place = Fixtures.place,
            verification = org.fisabilillah.core.model.OrganizationVerification(
                registrationChecked = true,
            ),
            status = org.fisabilillah.core.model.OrganizationStatus.ACTIVE,
        )
        val availability = VerificationPolicy.canOfferDonations(organization)
        assertTrue(
            availability is GivingAvailability.Preview,
            "payments must stay behind the flag until compliance work is complete",
        )
    }

    @Test
    fun `a campaign is never zakat eligible without a recorded attestation`() {
        val campaign = org.fisabilillah.core.model.Campaign(
            id = org.fisabilillah.core.model.CampaignId("c"),
            organizationId = org.fisabilillah.core.model.OrganizationId("org"),
            title = "Winter fund",
            summary = "Emergency support",
            fundType = org.fisabilillah.core.model.FundType.EMERGENCY_AID,
            goal = null,
            raised = org.fisabilillah.core.model.Money(0, "GBP"),
            currencyCode = "GBP",
        )
        assertEquals(ZakatEligibility.NotDeclared, VerificationPolicy.zakatEligibilityFor(campaign))
    }

    @Test
    fun `marking a campaign zakat eligible without an attestation is rejected outright`() {
        val error = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            org.fisabilillah.core.model.Campaign(
                id = org.fisabilillah.core.model.CampaignId("c"),
                organizationId = org.fisabilillah.core.model.OrganizationId("org"),
                title = "Zakat fund",
                summary = "Claims zakat eligibility",
                fundType = org.fisabilillah.core.model.FundType.ZAKAT,
                goal = null,
                raised = org.fisabilillah.core.model.Money(0, "GBP"),
                currencyCode = "GBP",
                zakatEligible = true,
                zakatAttestation = null,
            )
        }
        assertTrue(error.message!!.contains("attestation"))
    }
}
