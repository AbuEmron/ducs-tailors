package org.fisabilillah.core.policy

import kotlinx.datetime.LocalTime
import org.fisabilillah.core.model.AccountStatus
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.ContactRequirement
import org.fisabilillah.core.model.CrossGenderConversationStructure
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.ModeratorPresenceRule
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.QuietHours
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.RestrictionId
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.VerificationLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContactPolicyTest {

    private fun allowed(decision: ContactDecision): ContactDecision.Allowed {
        assertTrue(decision is ContactDecision.Allowed, "expected Allowed but was $decision")
        return decision as ContactDecision.Allowed
    }

    private fun denied(decision: ContactDecision): ContactDecision.Denied {
        assertTrue(decision is ContactDecision.Denied, "expected Denied but was $decision")
        return decision as ContactDecision.Denied
    }

    @Test
    @DisplayName("a well-formed service request between two brothers is allowed")
    fun baselineAllowed() {
        val decision = ContactPolicy.evaluate(Fixtures.context())
        val result = allowed(decision)
        assertTrue(ContactRequirement.WRITTEN_PURPOSE_REQUIRED in result.requirements)
        assertTrue(result.requiredOversight.isEmpty())
    }

    @Nested
    @DisplayName("blocking")
    inner class Blocking {

        @Test
        fun `a blocked user cannot contact the blocker`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context { copy(recipientBlockedInitiator = true) },
            )
            assertEquals(listOf(DenialReason.BLOCKED_BY_RECIPIENT), denied(decision).reasons)
        }

        @Test
        fun `the block message reveals nothing about why`() {
            val decision = denied(
                ContactPolicy.evaluate(Fixtures.context { copy(recipientBlockedInitiator = true) }),
            )
            // Identical to what an unrelated closed account produces, so a blocked person
            // cannot distinguish a block from any other refusal.
            assertEquals("This member cannot be contacted.", decision.userFacingMessage)
        }

        @Test
        fun `a block short-circuits before any safeguard reason is evaluated`() {
            // The recipient also fails verification and audience checks. Only the block
            // is reported, so the refusal cannot be used to probe their settings.
            val decision = denied(
                ContactPolicy.evaluate(
                    Fixtures.context(
                        initiator = Fixtures.profile("initiator", verification = VerificationLevel.NONE),
                        recipientSafeguards = Fixtures.safeguards("recipient").copy(
                            contactableBy = AudienceScope.NOBODY,
                            minimumVerificationToContactMe = VerificationLevel.BACKGROUND_CHECKED,
                        ),
                    ) { copy(recipientBlockedInitiator = true) },
                ),
            )
            assertEquals(listOf(DenialReason.BLOCKED_BY_RECIPIENT), decision.reasons)
        }

        @Test
        fun `blocking someone also stops the blocker from reaching them`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context { copy(initiatorBlockedRecipient = true) },
            )
            assertTrue(DenialReason.INITIATOR_BLOCKED_RECIPIENT in denied(decision).reasons)
        }
    }

    @Nested
    @DisplayName("purpose")
    inner class Purpose {

        @Test
        fun `a conversation cannot be opened without a subject when the purpose needs one`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(purpose = Fixtures.purpose(subject = null)),
            )
            assertTrue(DenialReason.PURPOSE_REQUIRES_SUBJECT in denied(decision).reasons)
        }

        @Test
        fun `marriage interest cannot be expressed through messaging`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    purpose = Fixtures.purpose(
                        kind = ContactPurposeKind.FORMAL_INTRODUCTION,
                        subject = null,
                    ),
                ),
            )
            assertEquals(
                listOf(DenialReason.INTRODUCTION_MUST_USE_WORKFLOW),
                denied(decision).reasons,
            )
        }

        @Test
        fun `an ordinary member cannot open a moderation thread`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    purpose = Fixtures.purpose(
                        kind = ContactPurposeKind.MODERATION_MATTER,
                        subject = null,
                    ),
                ),
            )
            assertEquals(listOf(DenialReason.STAFF_ONLY_PURPOSE), denied(decision).reasons)
        }

        @Test
        fun `the safety team can open a moderation thread`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    purpose = Fixtures.purpose(
                        kind = ContactPurposeKind.MODERATION_MATTER,
                        subject = null,
                    ),
                ) { copy(initiatorIsStaff = true) },
            )
            allowed(decision)
        }

        @Test
        fun `a purpose the recipient has declined is refused`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    recipientSafeguards = Fixtures.safeguards("recipient").copy(
                        declinedPurposes = setOf(ContactPurposeKind.VOLUNTEER_OPPORTUNITY),
                    ),
                ),
            )
            assertTrue(DenialReason.PURPOSE_DECLINED_BY_RECIPIENT in denied(decision).reasons)
        }
    }

    @Nested
    @DisplayName("cross-gender contact")
    inner class CrossGender {

        private val sister = Fixtures.profile("sister", gender = Gender.FEMALE)

        @Test
        fun `same-gender-only closes contact from the opposite gender`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    recipient = sister,
                    recipientSafeguards = Fixtures.safeguards("sister").copy(
                        contactableBy = AudienceScope.SAME_GENDER_ONLY,
                    ),
                ),
            )
            assertTrue(DenialReason.CROSS_GENDER_CONTACT_CLOSED in denied(decision).reasons)
        }

        @Test
        fun `a same-gender member is unaffected by a same-gender-only setting`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    initiator = Fixtures.profile("initiator", gender = Gender.FEMALE),
                    recipient = sister,
                    recipientSafeguards = Fixtures.safeguards("sister").copy(
                        contactableBy = AudienceScope.SAME_GENDER_ONLY,
                    ),
                ),
            )
            allowed(decision)
        }

        @Test
        fun `a guardian-present setting forces a guardian into the thread`() {
            val decision = allowed(
                ContactPolicy.evaluate(
                    Fixtures.context(
                        recipient = sister,
                        recipientSafeguards = Fixtures.safeguards("sister").copy(
                            crossGenderStructure = CrossGenderConversationStructure.GUARDIAN_PRESENT,
                        ),
                    ) { copy(recipientHasGuardianAvailable = true) },
                ),
            )
            assertTrue(ContactRequirement.GUARDIAN_PRESENT in decision.requirements)
            assertTrue(OversightRequirement.RECIPIENT_GUARDIAN in decision.requiredOversight)
        }

        @Test
        fun `contact is refused rather than downgraded when no guardian is available`() {
            // The important half of the previous test: the requirement is never quietly
            // dropped because nobody could be found to satisfy it.
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    recipient = sister,
                    recipientSafeguards = Fixtures.safeguards("sister").copy(
                        crossGenderStructure = CrossGenderConversationStructure.GUARDIAN_PRESENT,
                    ),
                ) { copy(recipientHasGuardianAvailable = false) },
            )
            assertTrue(
                DenialReason.GUARDIAN_REQUIRED_BUT_UNAVAILABLE in denied(decision).reasons,
            )
        }

        @Test
        fun `group-context-only requires a shared community`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    recipient = sister,
                    recipientSafeguards = Fixtures.safeguards("sister").copy(
                        crossGenderStructure = CrossGenderConversationStructure.GROUP_CONTEXT_ONLY,
                    ),
                ),
            )
            assertTrue(DenialReason.GROUP_CONTEXT_REQUIRED in denied(decision).reasons)
        }

        @Test
        fun `a moderator is required only for cross-gender threads when that rule is set`() {
            val crossGender = allowed(
                ContactPolicy.evaluate(
                    Fixtures.context(
                        recipient = sister,
                        recipientSafeguards = Fixtures.safeguards("sister").copy(
                            moderatorPresence = ModeratorPresenceRule.CROSS_GENDER_ONLY,
                        ),
                    ),
                ),
            )
            assertTrue(OversightRequirement.MODERATOR in crossGender.requiredOversight)

            val sameGender = allowed(
                ContactPolicy.evaluate(
                    Fixtures.context(
                        recipientSafeguards = Fixtures.safeguards("recipient").copy(
                            moderatorPresence = ModeratorPresenceRule.CROSS_GENDER_ONLY,
                        ),
                    ),
                ),
            )
            assertFalse(OversightRequirement.MODERATOR in sameGender.requiredOversight)
        }
    }

    @Nested
    @DisplayName("account state and restrictions")
    inner class AccountState {

        @Test
        fun `a banned account cannot start conversations`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    initiator = Fixtures.profile("initiator", status = AccountStatus.BANNED),
                ),
            )
            assertTrue(DenialReason.INITIATOR_NOT_ACTIVE in denied(decision).reasons)
        }

        @Test
        fun `a deleted account cannot be contacted`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    recipient = Fixtures.profile("recipient", deletedAt = Fixtures.NOW),
                ),
            )
            assertTrue(DenialReason.RECIPIENT_NOT_AVAILABLE in denied(decision).reasons)
        }

        @Test
        fun `a restriction on starting conversations is enforced`() {
            val restriction = Restriction(
                id = RestrictionId("r1"),
                userId = UserId("initiator"),
                capability = RestrictedCapability.START_CONVERSATIONS,
                reason = "Repeated unwanted contact",
                imposedBy = UserId("moderator"),
                caseId = null,
                startsAt = Fixtures.NOW,
            )
            val decision = ContactPolicy.evaluate(
                Fixtures.context { copy(initiatorRestrictions = listOf(restriction)) },
            )
            assertTrue(DenialReason.INITIATOR_RESTRICTED in denied(decision).reasons)
        }

        @Test
        fun `an expired restriction no longer applies`() {
            val restriction = Restriction(
                id = RestrictionId("r1"),
                userId = UserId("initiator"),
                capability = RestrictedCapability.START_CONVERSATIONS,
                reason = "Warning period",
                imposedBy = UserId("moderator"),
                caseId = null,
                startsAt = Fixtures.NOW - kotlin.time.Duration.parse("48h"),
                expiresAt = Fixtures.NOW - kotlin.time.Duration.parse("1h"),
            )
            val decision = ContactPolicy.evaluate(
                Fixtures.context { copy(initiatorRestrictions = listOf(restriction)) },
            )
            allowed(decision)
        }
    }

    @Nested
    @DisplayName("audience, verification and organisation scope")
    inner class Scope {

        @Test
        fun `an unverified member cannot reach someone requiring verification`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    initiator = Fixtures.profile("initiator", verification = VerificationLevel.EMAIL_VERIFIED),
                    recipientSafeguards = Fixtures.safeguards("recipient").copy(
                        minimumVerificationToContactMe = VerificationLevel.IDENTITY_VERIFIED,
                    ),
                ),
            )
            assertTrue(DenialReason.VERIFICATION_TOO_LOW in denied(decision).reasons)
        }

        @Test
        fun `organisation-only contact needs a shared organisation`() {
            val closed = ContactPolicy.evaluate(
                Fixtures.context(
                    recipientSafeguards = Fixtures.safeguards("recipient").copy(
                        contactableBy = AudienceScope.MY_ORGANIZATIONS_ONLY,
                    ),
                ),
            )
            assertTrue(DenialReason.OUTSIDE_ORGANIZATION in denied(closed).reasons)

            val shared = ContactPolicy.evaluate(
                Fixtures.context(
                    recipientSafeguards = Fixtures.safeguards("recipient").copy(
                        contactableBy = AudienceScope.MY_ORGANIZATIONS_ONLY,
                    ),
                ) { copy(sharedOrganizationIds = setOf(OrganizationId("org-1"))) },
            )
            allowed(shared)
        }

        @Test
        fun `nobody means nobody`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context(
                    recipientSafeguards = Fixtures.safeguards("recipient").copy(
                        contactableBy = AudienceScope.NOBODY,
                    ),
                ),
            )
            assertTrue(DenialReason.NOT_CONTACTABLE in denied(decision).reasons)
        }
    }

    @Nested
    @DisplayName("time, rate limits and persistence")
    inner class Limits {

        @Test
        fun `quiet hours block new conversations and wrap past midnight`() {
            val safeguards = Fixtures.safeguards("recipient").copy(
                quietHours = QuietHours(
                    enabled = true,
                    start = LocalTime(21, 0),
                    end = LocalTime(7, 0),
                ),
            )
            val inWindow = ContactPolicy.evaluate(
                Fixtures.context(recipientSafeguards = safeguards, localTime = LocalTime(23, 30)),
            )
            assertTrue(DenialReason.QUIET_HOURS in denied(inWindow).reasons)

            val alsoInWindow = ContactPolicy.evaluate(
                Fixtures.context(recipientSafeguards = safeguards, localTime = LocalTime(2, 0)),
            )
            assertTrue(DenialReason.QUIET_HOURS in denied(alsoInWindow).reasons)

            val outside = ContactPolicy.evaluate(
                Fixtures.context(recipientSafeguards = safeguards, localTime = LocalTime(10, 0)),
            )
            allowed(outside)
        }

        @Test
        fun `the new-conversation rate limit is enforced`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context {
                    copy(
                        conversationsStartedInWindow =
                            ContactPolicy.RateLimits.NEW_CONVERSATIONS_PER_WINDOW,
                    )
                },
            )
            assertTrue(DenialReason.RATE_LIMIT_EXCEEDED in denied(decision).reasons)
        }

        @Test
        fun `a second approach after being turned away is refused`() {
            val decision = ContactPolicy.evaluate(
                Fixtures.context { copy(priorDeclinesFromRecipient = 1) },
            )
            assertTrue(DenialReason.REPEATED_AFTER_DECLINE in denied(decision).reasons)
        }
    }

    @Test
    fun `adults and minors cannot message each other directly`() {
        val decision = ContactPolicy.evaluate(
            Fixtures.context(
                initiator = Fixtures.profile("adult", birthYear = 1990),
                recipient = Fixtures.profile("minor", birthYear = 2012),
            ),
        )
        assertTrue(DenialReason.MINOR_ADULT_DIRECT_CONTACT in denied(decision).reasons)
    }

    @Test
    fun `a member cannot start a conversation with themselves`() {
        val self = Fixtures.profile("same")
        val decision = ContactPolicy.evaluate(
            Fixtures.context(initiator = self, recipient = self),
        )
        assertEquals(listOf(DenialReason.SELF_CONTACT), denied(decision).reasons)
    }

    @Test
    fun `call and meeting terms are recorded on the thread`() {
        val decision = allowed(
            ContactPolicy.evaluate(
                Fixtures.context(
                    recipientSafeguards = Fixtures.safeguards("recipient").copy(
                        videoCallsAllowedFrom = AudienceScope.NOBODY,
                        meetingsMustBeInPublicPlaces = true,
                    ),
                ),
            ),
        )
        assertTrue(ContactRequirement.NO_VIDEO_CALLS in decision.requirements)
        assertTrue(ContactRequirement.PUBLIC_MEETINGS_ONLY in decision.requirements)
    }

    @Test
    fun `a sender may add oversight but never remove it`() {
        val decision = allowed(
            ContactPolicy.evaluate(
                Fixtures.context(
                    purpose = Fixtures.purpose().copy(requestModeratorPresent = true),
                    recipientSafeguards = Fixtures.safeguards("recipient").copy(
                        moderatorPresence = ModeratorPresenceRule.NEVER,
                    ),
                ),
            ),
        )
        assertTrue(OversightRequirement.MODERATOR in decision.requiredOversight)
    }
}
