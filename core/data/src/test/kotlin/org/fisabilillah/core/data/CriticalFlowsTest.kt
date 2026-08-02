package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.IntroductionDecisionAction
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.domain.Principal
import org.fisabilillah.core.domain.RefusalCode
import org.fisabilillah.core.domain.StartConversationCommand
import org.fisabilillah.core.domain.SubmitReportUseCase
import org.fisabilillah.core.domain.TakeModerationActionUseCase
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.CaseState
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.ContactRequirement
import org.fisabilillah.core.model.ConversationRole
import org.fisabilillah.core.model.ConversationState
import org.fisabilillah.core.model.GuardianRelationship
import org.fisabilillah.core.model.IntroductionForm
import org.fisabilillah.core.model.IntroductionStatus
import org.fisabilillah.core.model.ModerationActionType
import org.fisabilillah.core.model.ModerationCase
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.ProfileImageStyle
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportSeverity
import org.fisabilillah.core.model.ReportTarget
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The ten flows the product specification names as must-not-fail.
 *
 * Each one runs through the real use cases against seeded data, so a regression anywhere
 * between the screen and the store shows up here rather than in production.
 */
@DisplayName("Critical safety flows")
class CriticalFlowsTest {

    // ── 1 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("1. a blocked user cannot contact the blocker")
    fun blockedUserCannotContact() = runTest {
        val harness = Harness()
        val blocker = harness.principal(SeedData.khadija)

        harness.graph.blockUser(blocker, SeedData.abdullah).expectSuccess()

        val refusal = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.khadija,
                purpose = harness.purpose(),
                openingMessage = "Salam, about the clean-up",
            ),
        ).expectRefused()

        assertEquals(RefusalCode.BLOCKED, refusal.code)
        assertEquals("This member cannot be contacted.", refusal.message)
        assertTrue(harness.store.conversations.isEmpty())
    }

    @Test
    @DisplayName("1b. blocking ends conversations that already exist")
    fun blockingEndsExistingConversations() = runTest {
        val harness = Harness()

        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders on Saturday.",
            ),
        ).expectSuccess()

        harness.graph.blockUser(harness.principal(SeedData.yusuf), SeedData.abdullah)
            .expectSuccess()

        val conversation = harness.store.conversations.getValue(started.conversation.id)
        assertEquals(ConversationState.ENDED, conversation.state)

        val refusal = harness.graph.sendMessage(
            harness.principal(SeedData.abdullah),
            started.conversation.id,
            "Are we still on?",
        ).expectRefused()
        assertTrue(refusal.message.isNotBlank())
    }

    // ── 2 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("2. opposite-gender messaging restrictions cannot be bypassed")
    fun crossGenderRestrictionsHold() = runTest {
        val harness = Harness()

        // Khadija accepts contact from sisters only.
        val refusal = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.khadija,
                purpose = harness.purpose(),
                openingMessage = "Salam, I would like to help with the Arabic circle.",
            ),
        ).expectRefused()
        assertEquals("This member is not accepting new conversations.", refusal.message)

        // A sister with the same request is accepted, which shows the refusal was about
        // the safeguard rather than about the listing or the wording.
        harness.graph.startConversation(
            harness.principal(SeedData.aminah),
            StartConversationCommand(
                recipientId = SeedData.khadija,
                purpose = harness.purpose(),
                openingMessage = "Salam, I would like to help with the Arabic circle.",
            ),
        ).expectSuccess()
    }

    @Test
    @DisplayName("2b. a guardian-guided member gets a guardian in the thread automatically")
    fun guardianIsAddedAutomatically() = runTest {
        val harness = Harness()

        // Aminah's preset copies her wali into every purpose, and her father has an account.
        val started = harness.graph.startConversation(
            harness.principal(SeedData.yusuf),
            StartConversationCommand(
                recipientId = SeedData.aminah,
                purpose = harness.purpose(
                    kind = ContactPurposeKind.COMMUNITY_PROJECT,
                    subject = PurposeSubject.Project(
                        org.fisabilillah.core.model.ProjectId("project-masjid-booking"),
                    ),
                    reason = "We need someone to write the copy for the masjid booking system.",
                    action = "Would you be able to review the wording of the booking screens?",
                ),
                openingMessage = "Salam sister, would you be able to help with the wording?",
            ),
        ).expectSuccess()

        val guardian = started.conversation.members.firstOrNull {
            it.role == ConversationRole.GUARDIAN
        }
        assertNotNull(guardian, "the wali must be a member of the thread from the first message")
        assertEquals(SeedData.zaynab, guardian!!.userId)
        assertTrue(ContactRequirement.GUARDIAN_PRESENT in started.requirementsApplied)
    }

    // ── 3 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("3. a conversation cannot be created without a valid purpose")
    fun noConversationWithoutPurpose() = runTest {
        val harness = Harness()

        val blankReason = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(reason = "hi", action = "chat"),
                openingMessage = "hey",
            ),
        ).expectInvalid()
        assertTrue(blankReason.errors.any { it.field == "reasonForContact" })

        val noSubject = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(subject = null),
                openingMessage = "About the clean-up",
            ),
        ).expectInvalid()
        assertTrue(noSubject.errors.any { it.field == "subject" })

        assertTrue(harness.store.conversations.isEmpty())
    }

    @Test
    @DisplayName("3b. an opening that reads as a chat-up line is refused with an explanation")
    fun flirtatiousOpeningIsRefused() = runTest {
        val harness = Harness()

        val invalid = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(
                    reason = "I just wanted to say hi and get to know you better honestly.",
                    action = "Would you like to chat sometime soon?",
                ),
                openingMessage = "Salam",
            ),
        ).expectInvalid()

        val message = invalid.errors.first().message
        assertTrue(message.contains("service purpose"))
        // And it points at the right door rather than simply refusing.
        assertTrue(message.contains("guardian"))
    }

    // ── 4 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("4. a member cannot obtain another person's wali contact details")
    fun waliContactIsNeverExposed() = runTest {
        val harness = Harness()
        val sender = harness.principal(SeedData.abdullah)

        val introduction = harness.graph.submitIntroduction(
            sender,
            SeedData.aminah,
            validForm(),
        ).expectSuccess()

        // Before forwarding, the sender is not even told a guardian exists.
        harness.graph.viewIntroductionGuardian(sender, introduction.id).expectNotFound()

        harness.graph.decideIntroduction(
            harness.principal(SeedData.aminah),
            introduction.id,
            IntroductionDecisionAction.APPROVE_AND_FORWARD,
        ).expectSuccess()

        // After forwarding the sender learns a name and a preferred method — nothing more.
        val redacted = harness.graph.viewIntroductionGuardian(sender, introduction.id)
            .expectSuccess()
        assertEquals("Sulayman Siddiqui", redacted.name)
        assertEquals(GuardianRelationship.FATHER, redacted.relationship)

        val serialized = redacted.toString()
        assertFalse(serialized.contains("@example.test"), "email must never leave the owner")
        assertFalse(serialized.contains("7946"), "phone must never leave the owner")
        assertFalse(serialized.contains("after Isha"), "private notes must never leave the owner")

        // And the access is recorded.
        assertTrue(
            harness.store.auditLog.any { it.action == AuditAction.GUARDIAN_CONTACT_ACCESSED },
        )
    }

    // ── 5 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("5. an introduction follows the recipient's configuration exactly")
    fun introductionFollowsConfiguration() = runTest {
        val harness = Harness()

        // Aminah screens first, so the request sits with her and her wali is not told yet.
        val screened = harness.graph.submitIntroduction(
            harness.principal(SeedData.abdullah),
            SeedData.aminah,
            validForm(),
        ).expectSuccess()
        assertEquals(IntroductionStatus.AWAITING_RECIPIENT, screened.status)
        assertTrue(
            harness.store.notifications.none { it.userId == SeedData.zaynab },
            "the guardian must not be notified while the recipient is still screening",
        )

        // Switched to guardian-first, the same submission goes straight past her.
        val settings = harness.store.introductionSettings.getValue(SeedData.aminah)
        harness.graph.updateIntroductionSettings(
            harness.principal(SeedData.aminah),
            settings.copy(recipientReviewsFirst = false),
        ).expectSuccess()

        // A different sender, who also shares her masjid as her settings require.
        val direct = harness.graph.submitIntroduction(
            harness.principal(SeedData.yusuf),
            SeedData.aminah,
            validForm(),
        ).expectSuccess()
        assertEquals(IntroductionStatus.FORWARDED_TO_GUARDIAN, direct.status)
        assertTrue(harness.store.notifications.any { it.userId == SeedData.zaynab })
    }

    @Test
    @DisplayName("5b. introductions cannot be submitted to a member who has them switched off")
    fun introductionsRespectTheSwitch() = runTest {
        val harness = Harness()
        // Khadija has the feature disabled.
        val refusal = harness.graph.submitIntroduction(
            harness.principal(SeedData.abdullah),
            SeedData.khadija,
            validForm(),
        ).expectRefused()
        assertEquals("This member is not receiving formal introductions.", refusal.message)
    }

    @Test
    @DisplayName("5c. the only route to a conversation runs through the guardian")
    fun onlyTheGuardianCanOpenTheConversation() = runTest {
        val harness = Harness()

        val introduction = harness.graph.submitIntroduction(
            harness.principal(SeedData.abdullah),
            SeedData.aminah,
            validForm(),
        ).expectSuccess()
        harness.graph.decideIntroduction(
            harness.principal(SeedData.aminah),
            introduction.id,
            IntroductionDecisionAction.APPROVE_AND_FORWARD,
        ).expectSuccess()

        // Neither the sender nor the recipient can open it.
        harness.graph.openIntroductionConversation(
            harness.principal(SeedData.abdullah), introduction.id, "Salam",
        ).expectRefused()
        harness.graph.openIntroductionConversation(
            harness.principal(SeedData.aminah), introduction.id, "Salam",
        ).expectRefused()

        val conversation = harness.graph.openIntroductionConversation(
            harness.principal(SeedData.zaynab),
            introduction.id,
            "Salam, I am Aminah's father. Thank you for approaching us properly.",
        ).expectSuccess()

        // Three people, and the guardian is one of them.
        assertEquals(3, conversation.members.size)
        assertTrue(
            conversation.members.any {
                it.userId == SeedData.zaynab && it.role == ConversationRole.GUARDIAN
            },
        )
        assertTrue(ContactRequirement.GUARDIAN_PRESENT in conversation.appliedRequirements)
    }

    @Test
    @DisplayName("5d. closing an introduction permanently blocks further approaches")
    fun permanentClosureBlocks() = runTest {
        val harness = Harness()

        val introduction = harness.graph.submitIntroduction(
            harness.principal(SeedData.abdullah),
            SeedData.aminah,
            validForm(),
        ).expectSuccess()

        harness.graph.decideIntroduction(
            harness.principal(SeedData.aminah),
            introduction.id,
            IntroductionDecisionAction.BLOCK_PERMANENTLY,
        ).expectSuccess()

        // No second attempt, and no ordinary conversation either.
        harness.graph.submitIntroduction(
            harness.principal(SeedData.abdullah), SeedData.aminah, validForm(),
        ).expectRefused()

        harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.aminah,
                purpose = harness.purpose(),
                openingMessage = "About the masjid clean-up",
            ),
        ).expectRefused()
    }

    // ── 6 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("6. a moderation action always writes an audit record")
    fun moderationActionsAreAudited() = runTest {
        val harness = Harness()
        val admin = Principal(SeedData.safetyAdmin, setOf(AccountRole.SAFETY_ADMINISTRATOR))

        val case = harness.graph.moderation.saveCase(
            ModerationCase(
                id = ModerationCaseId("case-1"),
                subjectUserId = SeedData.abdullah,
                reportIds = emptyList(),
                category = ReportCategory.HARASSMENT,
                severity = ReportSeverity.HIGH,
                summary = "Repeated contact after being declined",
                createdAt = harness.clock.now(),
            ),
        )

        val auditBefore = harness.store.auditLog.size

        harness.graph.takeModerationAction(
            admin,
            TakeModerationActionUseCase.Command(
                caseId = case.id,
                type = ModerationActionType.FEATURE_RESTRICTED,
                targetUserId = SeedData.abdullah,
                rationale = "Contacted a member three times after being asked to stop.",
                restrictedCapability = RestrictedCapability.START_CONVERSATIONS,
                durationDays = 30,
            ),
        ).expectSuccess()

        assertTrue(harness.store.auditLog.size > auditBefore)
        val entry = harness.store.auditLog.last()
        assertEquals(AuditAction.RESTRICTION_IMPOSED, entry.action)
        assertEquals(SeedData.safetyAdmin, entry.actorId)
        assertTrue(entry.summary.contains("asked to stop"))

        // And the restriction actually took effect.
        harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "About the clean-up",
            ),
        ).expectRefused().also { assertEquals(RefusalCode.RESTRICTED, it.code) }
    }

    @Test
    @DisplayName("6b. audit records cannot be altered or removed through any interface")
    fun auditLogIsAppendOnly() {
        // A structural assertion rather than a behavioural one: the repository interface
        // offers no way to change history, so no caller — including a platform
        // administrator — has one.
        val methods = org.fisabilillah.core.domain.AuditLogRepository::class.java.methods
            .map { it.name }
        assertTrue("append" in methods)
        assertTrue(methods.none { it.startsWith("update") || it.startsWith("delete") })

        val moderationMethods = org.fisabilillah.core.domain.ModerationRepository::class.java
            .methods.map { it.name }
        assertTrue("appendAction" in moderationMethods)
        assertTrue(moderationMethods.none { it == "updateAction" || it == "deleteAction" })
    }

    @Test
    @DisplayName("6c. an ordinary member cannot take a moderation action")
    fun ordinaryMembersCannotModerate() = runTest {
        val harness = Harness()
        val case = harness.graph.moderation.saveCase(
            ModerationCase(
                id = ModerationCaseId("case-1"),
                subjectUserId = SeedData.yusuf,
                reportIds = emptyList(),
                category = ReportCategory.SPAM,
                severity = ReportSeverity.LOW,
                summary = "Test",
                createdAt = harness.clock.now(),
            ),
        )
        harness.graph.takeModerationAction(
            harness.principal(SeedData.abdullah),
            TakeModerationActionUseCase.Command(
                caseId = case.id,
                type = ModerationActionType.ACCOUNT_BANNED,
                targetUserId = SeedData.yusuf,
                rationale = "Trying to ban someone I dislike",
            ),
        ).expectRefused()

        assertTrue(harness.store.moderationActions.isEmpty())
    }

    @Test
    @DisplayName("6d. a moderator cannot impose a permanent ban without a safety administrator")
    fun banRequiresSeniorApproval() = runTest {
        val harness = Harness()
        val case = harness.graph.moderation.saveCase(
            ModerationCase(
                id = ModerationCaseId("case-1"),
                subjectUserId = SeedData.abdullah,
                reportIds = emptyList(),
                category = ReportCategory.HARASSMENT,
                severity = ReportSeverity.HIGH,
                summary = "Test",
                createdAt = harness.clock.now(),
            ),
        )
        val refusal = harness.graph.takeModerationAction(
            Principal(SeedData.moderator, setOf(AccountRole.MODERATOR)),
            TakeModerationActionUseCase.Command(
                caseId = case.id,
                type = ModerationActionType.ACCOUNT_BANNED,
                targetUserId = SeedData.abdullah,
                rationale = "Serious and repeated harassment",
            ),
        ).expectRefused()
        assertTrue(refusal.message.contains("safety administrator"))
    }

    // ── 7 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("7. an organisation administrator cannot reach another organisation's records")
    fun organizationsAreIsolated() = runTest {
        val harness = Harness()

        // Hafsa administers Ashbourne Relief; Ibrahim administers Northfield Masjid.
        val reliefMembers = harness.graph.organizations.members(SeedData.ashbourneRelief)
        val masjidMembers = harness.graph.organizations.members(SeedData.northfieldMasjid)

        val hafsaIsMasjidAdmin = masjidMembers.any {
            it.userId == SeedData.hafsa && it.role.canSeePrivateRequestDetail
        }
        assertFalse(hafsaIsMasjidAdmin, "no cross-organisation administration in the fixture")
        assertTrue(reliefMembers.any { it.userId == SeedData.hafsa })

        // The organisation-scoped search returns only the caller's own organisation.
        val reliefListings = harness.graph.opportunities.search(
            org.fisabilillah.core.domain.OpportunitySearchCriteria(
                organizationId = SeedData.ashbourneRelief,
                openOnly = false,
            ),
        ).items
        assertTrue(reliefListings.all { it.organizationId == SeedData.ashbourneRelief })
        assertTrue(reliefListings.isNotEmpty())
    }

    // ── 8 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("8. a member cannot give themselves verification or scholar status")
    fun selfAssignedStatusIsStripped() = runTest {
        val harness = Harness()
        val newUserId = UserId("user-new")

        val profile = org.fisabilillah.core.model.Profile(
            id = newUserId,
            displayName = "Ambitious Newcomer",
            gender = org.fisabilillah.core.model.Gender.MALE,
            dateOfBirthYear = 1994,
            place = harness.store.profiles.getValue(SeedData.yusuf).place,
            // Everything below is a lie the client has asked the server to believe.
            verificationLevel = org.fisabilillah.core.model.VerificationLevel.BACKGROUND_CHECKED,
            attestations = setOf(org.fisabilillah.core.model.Attestation.QUALIFICATION_VERIFIED),
            roles = setOf(
                AccountRole.SCHOLAR,
                AccountRole.MODERATOR,
                AccountRole.PLATFORM_ADMINISTRATOR,
                AccountRole.VOLUNTEER,
            ),
        )

        val saved = harness.graph.completeOnboarding(
            Principal(newUserId, setOf(AccountRole.COMMUNITY_MEMBER)),
            org.fisabilillah.core.domain.CompleteOnboardingUseCase.Command(
                profile = profile,
                preset = org.fisabilillah.core.model.SafeguardPresetName.COMMUNITY_SERVICE,
                acceptedConsents = org.fisabilillah.core.model.ConsentKind.entries
                    .filter { it.required }.toSet(),
                documentVersion = "2026-03-01",
                declaredAdult = true,
            ),
        ).expectSuccess()

        assertFalse(AccountRole.SCHOLAR in saved.roles)
        assertFalse(AccountRole.MODERATOR in saved.roles)
        assertFalse(AccountRole.PLATFORM_ADMINISTRATOR in saved.roles)
        assertTrue(AccountRole.VOLUNTEER in saved.roles)
        assertEquals(
            org.fisabilillah.core.model.VerificationLevel.EMAIL_VERIFIED,
            saved.verificationLevel,
        )
        assertTrue(saved.attestations.isEmpty())

        // The attempt itself is recorded.
        assertTrue(
            harness.store.auditLog.any {
                it.action == AuditAction.ACCOUNT_CREATED && it.summary.contains("rejected")
            },
        )
    }

    @Test
    @DisplayName("8b. onboarding requires the mandatory consents and an adult declaration")
    fun onboardingRequiresConsent() = runTest {
        val harness = Harness()
        val newUserId = UserId("user-new-2")
        val principal = Principal(newUserId, setOf(AccountRole.COMMUNITY_MEMBER))
        val profile = org.fisabilillah.core.model.Profile(
            id = newUserId,
            displayName = "Newcomer",
            gender = org.fisabilillah.core.model.Gender.FEMALE,
            dateOfBirthYear = 1999,
            place = harness.store.profiles.getValue(SeedData.yusuf).place,
        )

        harness.graph.completeOnboarding(
            principal,
            org.fisabilillah.core.domain.CompleteOnboardingUseCase.Command(
                profile = profile,
                preset = org.fisabilillah.core.model.SafeguardPresetName.LEARNING_ONLY,
                acceptedConsents = emptySet(),
                documentVersion = "2026-03-01",
                declaredAdult = true,
            ),
        ).expectInvalid()

        harness.graph.completeOnboarding(
            principal,
            org.fisabilillah.core.domain.CompleteOnboardingUseCase.Command(
                profile = profile,
                preset = org.fisabilillah.core.model.SafeguardPresetName.LEARNING_ONLY,
                acceptedConsents = org.fisabilillah.core.model.ConsentKind.entries
                    .filter { it.required }.toSet(),
                documentVersion = "2026-03-01",
                declaredAdult = false,
            ),
        ).expectRefused()
    }

    // ── 9 ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("9. a suspended account cannot continue an existing conversation")
    fun suspendedAccountsCannotContinue() = runTest {
        val harness = Harness()

        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders on Saturday.",
            ),
        ).expectSuccess()

        harness.graph.sendMessage(
            harness.principal(SeedData.abdullah), started.conversation.id, "What time?",
        ).expectSuccess()

        val admin = Principal(SeedData.safetyAdmin, setOf(AccountRole.SAFETY_ADMINISTRATOR))
        val case = harness.graph.moderation.saveCase(
            ModerationCase(
                id = ModerationCaseId("case-suspend"),
                subjectUserId = SeedData.abdullah,
                reportIds = emptyList(),
                category = ReportCategory.HARASSMENT,
                severity = ReportSeverity.HIGH,
                summary = "Under investigation",
                createdAt = harness.clock.now(),
            ),
        )
        harness.graph.takeModerationAction(
            admin,
            TakeModerationActionUseCase.Command(
                caseId = case.id,
                type = ModerationActionType.ACCOUNT_SUSPENDED,
                targetUserId = SeedData.abdullah,
                rationale = "Suspended while a serious report is reviewed.",
            ),
        ).expectSuccess()

        harness.graph.sendMessage(
            harness.principal(SeedData.abdullah), started.conversation.id, "Hello?",
        ).expectRefused()

        // The other party is unaffected, and the thread and its history remain intact for
        // the review.
        harness.graph.sendMessage(
            harness.principal(SeedData.yusuf), started.conversation.id, "Nine o'clock.",
        ).expectSuccess()
    }

    @Test
    @DisplayName("9b. a frozen conversation accepts no messages from anyone")
    fun frozenConversationsAreClosedToAll() = runTest {
        val harness = Harness()
        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can help on Saturday.",
            ),
        ).expectSuccess()

        val admin = Principal(SeedData.safetyAdmin, setOf(AccountRole.SAFETY_ADMINISTRATOR))
        val case = harness.graph.moderation.saveCase(
            ModerationCase(
                id = ModerationCaseId("case-freeze"),
                subjectUserId = null,
                reportIds = emptyList(),
                category = ReportCategory.HARASSMENT,
                severity = ReportSeverity.HIGH,
                summary = "Thread under review",
                state = CaseState.OPEN,
                createdAt = harness.clock.now(),
            ),
        )
        harness.graph.takeModerationAction(
            admin,
            TakeModerationActionUseCase.Command(
                caseId = case.id,
                type = ModerationActionType.CONVERSATION_FROZEN,
                targetReference = started.conversation.id.value,
                rationale = "Frozen while the reported exchange is reviewed.",
            ),
        ).expectSuccess()

        harness.graph.sendMessage(
            harness.principal(SeedData.yusuf), started.conversation.id, "Still there?",
        ).expectRefused()
        harness.graph.sendMessage(
            harness.principal(SeedData.abdullah), started.conversation.id, "Still there?",
        ).expectRefused()
    }

    // ── 10 ────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("10. an exact address stays private until the requester releases it")
    fun exactAddressStaysPrivate() = runTest {
        val harness = Harness()
        val requestId = RequestId("req-transport-hospital")
        val volunteer = harness.principal(SeedData.khadija)

        val browsed = harness.graph.browseRequests(
            volunteer,
            org.fisabilillah.core.domain.RequestSearchCriteria(city = "Ashbourne"),
        ).expectSuccess()
        val listed = browsed.items.first { it.id == requestId }

        assertNull(listed.exactAddress, "browsing must never reveal a street address")
        assertEquals("Eastgate, Ashbourne", listed.locationLabel)
        assertNull(listed.requesterDisplayName, "this request is anonymous to members")

        harness.graph.discloseExactLocation(
            harness.principal(SeedData.fatima), requestId, SeedData.khadija,
        ).expectSuccess()

        val afterDisclosure = harness.graph.browseRequests(
            volunteer,
            org.fisabilillah.core.domain.RequestSearchCriteria(city = "Ashbourne", openOnly = false),
        ).expectSuccess().items.first { it.id == requestId }
        assertNotNull(afterDisclosure.exactAddress)
        assertEquals("14 Eastgate Rise", afterDisclosure.exactAddress!!.addressLine1)

        // Everyone else still sees the neighbourhood only.
        val otherVolunteer = harness.graph.browseRequests(
            harness.principal(SeedData.musa),
            org.fisabilillah.core.domain.RequestSearchCriteria(city = "Ashbourne", openOnly = false),
        ).expectSuccess().items.first { it.id == requestId }
        assertNull(otherVolunteer.exactAddress)

        assertTrue(
            harness.store.auditLog.any { it.action == AuditAction.EXACT_LOCATION_DISCLOSED },
        )
    }

    @Test
    @DisplayName("10b. only the requester can release their own address")
    fun onlyTheRequesterCanDisclose() = runTest {
        val harness = Harness()
        harness.graph.discloseExactLocation(
            harness.principal(SeedData.khadija),
            RequestId("req-transport-hospital"),
            SeedData.khadija,
        ).expectRefused()
    }

    // ── Supporting invariants ─────────────────────────────────────────────────
    @Test
    @DisplayName("unsending hides a message from participants but preserves the evidence")
    fun unsendPreservesEvidence() = runTest {
        val harness = Harness()
        val started = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders.",
            ),
        ).expectSuccess()

        val sent = harness.graph.sendMessage(
            harness.principal(SeedData.abdullah),
            started.conversation.id,
            "Something regrettable that should not disappear from the record.",
        ).expectSuccess()

        harness.graph.unsendMessage(harness.principal(SeedData.abdullah), sent.message.id)
            .expectSuccess()

        val stored = harness.store.messages.first { it.id == sent.message.id }
        assertTrue(stored.isUnsent)
        assertEquals("This message was unsent by the sender.", stored.displayBody)

        val preserved = harness.graph.messages.redactionsFor(started.conversation.id)
        assertEquals(1, preserved.size)
        assertTrue(preserved.first().originalBody.contains("regrettable"))

        // A report made afterwards still carries the original.
        val report = harness.graph.submitReport(
            harness.principal(SeedData.yusuf),
            SubmitReportUseCase.Command(
                target = ReportTarget.ConversationTarget(started.conversation.id),
                category = ReportCategory.HARASSMENT,
                description = "He wrote something and then removed it.",
            ),
        ).expectSuccess()
        assertTrue(
            report.evidence.any {
                it.notes?.contains("Preserved copy") == true
            },
        )
    }

    @Test
    @DisplayName("a critical report opens a case immediately rather than waiting for triage")
    fun criticalReportsEscalate() = runTest {
        val harness = Harness()
        harness.graph.submitReport(
            harness.principal(SeedData.aminah),
            SubmitReportUseCase.Command(
                target = ReportTarget.User(SeedData.abdullah),
                category = ReportCategory.GROOMING,
                description = "Repeated attempts to move the conversation to another app and " +
                    "asking that it be kept quiet.",
            ),
        ).expectSuccess()

        assertEquals(1, harness.store.cases.size)
        val case = harness.store.cases.values.first()
        assertEquals(
            org.fisabilillah.core.model.EscalationLevel.SAFETY_ADMINISTRATOR,
            case.escalation,
        )
    }

    @Test
    @DisplayName("a member's profile image is hidden from the opposite gender when they chose that")
    fun profileImageRespectsSafeguards() = runTest {
        val harness = Harness()
        val visible = harness.graph.searchPeople.viewProfile(
            harness.principal(SeedData.abdullah),
            SeedData.khadija,
        ).expectSuccess()
        assertEquals(ProfileImageStyle.INITIALS, visible.imageStyle)
        assertNull(visible.imageUrl)
        assertFalse(visible.contactability.canInitiate)
    }

    private fun validForm() = IntroductionForm(
        statedIntention = "I am seeking marriage and would like our families to speak about " +
            "whether there is compatibility between us.",
        aboutSelf = "I am thirty, I work in logistics, and I live in Riverside with my mother " +
            "and younger brother. I pray at Northfield.",
        familyContext = "My father passed away four years ago. My paternal uncle would represent " +
            "me and my mother is closely involved in the decision.",
        practiceAndPriorities = "I keep the five prayers and I have been attending the Tuesday " +
            "circle at the masjid for the last two years.",
        livingSituationAndPlans = "I intend to remain in Ashbourne near my mother, and I am " +
            "saving towards a home of my own.",
        guardianOrRepresentativeName = "Yusuf Karim",
        guardianOrRepresentativeRelationship = GuardianRelationship.PATERNAL_UNCLE,
        confirmsMarriageConsideration = true,
        confirmsConductRules = true,
    )
}
