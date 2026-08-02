package org.fisabilillah.core.policy

import kotlinx.datetime.Instant
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Appeal
import org.fisabilillah.core.model.AppealId
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.EngagementDuration
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.GuardianRelationship
import org.fisabilillah.core.model.IntroductionForm
import org.fisabilillah.core.model.IntroductionId
import org.fisabilillah.core.model.IntroductionLimits
import org.fisabilillah.core.model.IntroductionStatus
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.ModerationAction
import org.fisabilillah.core.model.ModerationActionId
import org.fisabilillah.core.model.ModerationActionType
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.PrivateContactDetails
import org.fisabilillah.core.model.Report
import org.fisabilillah.core.model.ReportAbuseAssessment
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportId
import org.fisabilillah.core.model.ReportState
import org.fisabilillah.core.model.ReportTarget
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactId
import org.fisabilillah.core.model.TrustedContactRole
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.VerificationLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class IntroductionPolicyTest {

    private val brother = Fixtures.profile("brother", gender = Gender.MALE)
    private val sister = Fixtures.profile("sister", gender = Gender.FEMALE)

    private val wali = TrustedContact(
        id = TrustedContactId("wali-1"),
        ownerId = sister.id,
        name = "Sulayman Siddiqui",
        relationship = GuardianRelationship.FATHER,
        role = TrustedContactRole.WALI,
        privateContact = PrivateContactDetails(
            email = "wali@example.test",
            phone = "+44 20 7946 0155",
        ),
        linkedUserId = UserId("wali-user"),
        createdAt = Fixtures.NOW,
        updatedAt = Fixtures.NOW,
    )

    private fun settings(build: FormalIntroductionSettings.() -> FormalIntroductionSettings = { this }) =
        FormalIntroductionSettings(
            userId = sister.id,
            enabled = true,
            guardianContactId = wali.id,
            minimumVerification = VerificationLevel.IDENTITY_VERIFIED,
            acceptRequestsFrom = AudienceScope.VERIFIED_ONLY,
        ).build()

    private fun context(
        settingsValue: FormalIntroductionSettings = settings(),
        guardian: TrustedContact? = wali,
        build: IntroductionContext.() -> IntroductionContext = { this },
    ) = IntroductionContext(
        sender = brother,
        recipient = sister,
        recipientSettings = settingsValue,
        recipientGuardian = guardian,
        now = Fixtures.NOW,
        currentYear = Fixtures.YEAR,
    ).build()

    private val form = IntroductionForm(
        statedIntention = "I am looking to marry, and I would like my family to speak to yours " +
            "about whether there is compatibility here.",
        aboutSelf = "I am twenty-nine, I work as an electrician, and I live with my mother and " +
            "younger brother in Riverside. I pray at Northfield.",
        familyContext = "My father passed some years ago. My uncle would represent me, and my " +
            "mother is closely involved in the decision.",
        practiceAndPriorities = "I try to keep the five prayers in the masjid where work allows, " +
            "and I have been studying with the imam on Tuesdays for two years.",
        livingSituationAndPlans = "I intend to stay in Ashbourne. I am saving towards a house " +
            "and expect to be in a position to marry within the year.",
        guardianOrRepresentativeName = "Yusuf Karim",
        guardianOrRepresentativeRelationship = GuardianRelationship.PATERNAL_UNCLE,
        confirmsMarriageConsideration = true,
        confirmsConductRules = true,
    )

    private fun denied(decision: IntroductionDecision): IntroductionDenialReason {
        assertTrue(decision is IntroductionDecision.Denied, "expected Denied but was $decision")
        return (decision as IntroductionDecision.Denied).reason
    }

    @Test
    fun `a complete, verified, guardian-backed request is allowed`() {
        assertEquals(IntroductionDecision.Allowed, IntroductionPolicy.canSubmit(context()))
    }

    @Test
    @DisplayName("a member with the feature switched off is indistinguishable from any other refusal")
    fun refusalsAreIndistinguishable() {
        val disabled = denied(
            IntroductionPolicy.canSubmit(context(settingsValue = settings { copy(enabled = false) })),
        )
        val blocked = denied(
            IntroductionPolicy.canSubmit(context { copy(senderBlockedByRecipient = true) }),
        )
        val noGuardian = denied(
            IntroductionPolicy.canSubmit(context(guardian = null)),
        )

        // Three quite different situations, one identical message. A determined sender
        // learns nothing about which door is closed or how to open it.
        assertEquals(disabled.userFacingMessage, blocked.userFacingMessage)
        assertEquals(blocked.userFacingMessage, noGuardian.userFacingMessage)
    }

    @Test
    fun `introductions require identity verification`() {
        val decision = IntroductionPolicy.canSubmit(
            context {
                copy(sender = Fixtures.profile("brother", verification = VerificationLevel.EMAIL_VERIFIED))
            },
        )
        assertEquals(IntroductionDenialReason.SENDER_NOT_VERIFIED, denied(decision))
    }

    @Test
    fun `a second request to the same person is refused`() {
        val decision = IntroductionPolicy.canSubmit(
            context { copy(senderPriorRequestsToThisRecipient = 1) },
        )
        assertEquals(IntroductionDenialReason.ALREADY_SUBMITTED, denied(decision))
    }

    @Test
    fun `sending in volume is refused`() {
        assertEquals(
            IntroductionDenialReason.TOO_MANY_OPEN,
            denied(
                IntroductionPolicy.canSubmit(
                    context { copy(senderOpenRequestCount = IntroductionLimits.MAX_CONCURRENT_OUTGOING) },
                ),
            ),
        )
        assertEquals(
            IntroductionDenialReason.TOO_MANY_RECENT,
            denied(
                IntroductionPolicy.canSubmit(
                    context { copy(senderRequestsInLast30Days = IntroductionLimits.MAX_PER_30_DAYS) },
                ),
            ),
        )
    }

    @Test
    fun `an intermediary is only accepted when the member allowed one`() {
        val intermediary = wali.copy(role = TrustedContactRole.INTERMEDIARY)

        assertEquals(
            IntroductionDenialReason.NO_GUARDIAN_CONFIGURED,
            denied(
                IntroductionPolicy.canSubmit(
                    context(
                        settingsValue = settings { copy(allowIntermediaryInsteadOfWali = false) },
                        guardian = intermediary,
                    ),
                ),
            ),
        )

        assertEquals(
            IntroductionDecision.Allowed,
            IntroductionPolicy.canSubmit(
                context(
                    settingsValue = settings { copy(allowIntermediaryInsteadOfWali = true) },
                    guardian = intermediary,
                ),
            ),
        )
    }

    @Test
    fun `an introduction between two people of the same gender is refused`() {
        assertEquals(
            IntroductionDenialReason.SAME_GENDER,
            denied(
                IntroductionPolicy.canSubmit(
                    context { copy(recipient = Fixtures.profile("brother2", gender = Gender.MALE)) },
                ),
            ),
        )
    }

    @Test
    fun `the recipient's own screening preference decides who sees it first`() {
        assertEquals(
            IntroductionStatus.AWAITING_RECIPIENT,
            IntroductionPolicy.statusOnSubmission(settings { copy(recipientReviewsFirst = true) }),
        )
        assertEquals(
            IntroductionStatus.FORWARDED_TO_GUARDIAN,
            IntroductionPolicy.statusOnSubmission(settings { copy(recipientReviewsFirst = false) }),
        )
    }

    @Test
    @DisplayName("there is no transition from submitted straight to a conversation")
    fun noShortcutToConversation() {
        assertFalse(
            IntroductionPolicy.canTransition(
                IntroductionStatus.SUBMITTED,
                IntroductionStatus.GUARDIAN_ENGAGED,
            ),
        )
        assertFalse(
            IntroductionPolicy.canTransition(
                IntroductionStatus.AWAITING_RECIPIENT,
                IntroductionStatus.GUARDIAN_ENGAGED,
            ),
        )
        // The only route runs through the guardian.
        assertTrue(
            IntroductionPolicy.canTransition(
                IntroductionStatus.FORWARDED_TO_GUARDIAN,
                IntroductionStatus.GUARDIAN_ENGAGED,
            ),
        )
    }

    @Test
    fun `terminal states cannot be revived`() {
        for (terminal in listOf(
            IntroductionStatus.DECLINED,
            IntroductionStatus.LAPSED,
            IntroductionStatus.BLOCKED_BY_RECIPIENT,
            IntroductionStatus.WITHDRAWN_BY_SENDER,
            IntroductionStatus.CLOSED_BY_MODERATION,
        )) {
            assertTrue(
                IntroductionPolicy.allowedTransitions(terminal).isEmpty(),
                "$terminal must be terminal",
            )
        }
    }

    @Test
    @DisplayName("the guardian is required in the conversation under every configuration")
    fun guardianAlwaysPresent() {
        val request = FormalIntroductionRequest(
            id = IntroductionId("i1"),
            senderId = brother.id,
            recipientId = sister.id,
            status = IntroductionStatus.FORWARDED_TO_GUARDIAN,
            form = form,
            conductAgreementAcceptedAt = Fixtures.NOW,
            senderVerificationAtSubmission = VerificationLevel.IDENTITY_VERIFIED,
            guardianContactId = wali.id,
            createdAt = Fixtures.NOW,
        )

        for (mustInclude in listOf(true, false)) {
            val decision = IntroductionPolicy.conversationEligibility(
                request,
                settings { copy(guardianMustBeIncludedThroughout = mustInclude) },
            )
            assertTrue(decision is IntroductionConversationDecision.Allowed)
            assertTrue((decision as IntroductionConversationDecision.Allowed).guardianRequired)
        }
    }

    @Test
    fun `a conversation cannot be opened before the guardian has the introduction`() {
        val request = FormalIntroductionRequest(
            id = IntroductionId("i1"),
            senderId = brother.id,
            recipientId = sister.id,
            status = IntroductionStatus.AWAITING_RECIPIENT,
            form = form,
            conductAgreementAcceptedAt = Fixtures.NOW,
            senderVerificationAtSubmission = VerificationLevel.IDENTITY_VERIFIED,
            createdAt = Fixtures.NOW,
        )
        assertTrue(
            IntroductionPolicy.conversationEligibility(request, settings())
                is IntroductionConversationDecision.NotYet,
        )
    }

    @Test
    fun `an unanswered request lapses on its own`() {
        val request = FormalIntroductionRequest(
            id = IntroductionId("i1"),
            senderId = brother.id,
            recipientId = sister.id,
            status = IntroductionStatus.AWAITING_RECIPIENT,
            form = form,
            conductAgreementAcceptedAt = Fixtures.NOW,
            senderVerificationAtSubmission = VerificationLevel.IDENTITY_VERIFIED,
            createdAt = Fixtures.NOW,
        )
        assertFalse(IntroductionPolicy.hasLapsed(request, Fixtures.NOW + 20.days))
        assertTrue(
            IntroductionPolicy.hasLapsed(
                request,
                Fixtures.NOW + IntroductionLimits.LAPSE_AFTER_DAYS.days,
            ),
        )
    }

    @Test
    @DisplayName("a declined sender is told nothing beyond the fact that it ended")
    fun outcomesRevealNothing() {
        val declined = IntroductionPolicy.senderFacingOutcome(IntroductionStatus.DECLINED)
        val lapsed = IntroductionPolicy.senderFacingOutcome(IntroductionStatus.LAPSED)
        val blocked = IntroductionPolicy.senderFacingOutcome(IntroductionStatus.BLOCKED_BY_RECIPIENT)

        assertEquals(declined, lapsed)
        assertEquals(lapsed, blocked)
        assertFalse(declined.contains("declin", ignoreCase = true))
    }

    @Test
    fun `the form rejects a token effort and an unaccepted conduct agreement`() {
        val tooShort = IntroductionPolicy.validateForm(
            form.copy(statedIntention = "interested", aboutSelf = "hi"),
        )
        assertTrue(tooShort is ValidationResult.Invalid)

        val noConduct = IntroductionPolicy.validateForm(form.copy(confirmsConductRules = false))
        assertTrue(noConduct is ValidationResult.Invalid)
        assertTrue(
            (noConduct as ValidationResult.Invalid).errors.any { it.field == "confirmsConductRules" },
        )
    }

    @Test
    fun `guardian contact details are stripped from the redacted form`() {
        val redacted = wali.redacted()
        val text = redacted.toString()
        assertFalse(text.contains("wali@example.test"))
        assertFalse(text.contains("7946"))
        assertEquals("Sulayman Siddiqui", redacted.name)
    }

    @Test
    fun `the conduct rules are stated, not implied`() {
        assertTrue(IntroductionForm.CONDUCT_RULES.size >= 5)
        assertTrue(
            IntroductionForm.CONDUCT_RULES.any { it.contains("not a way to start a casual") },
        )
        assertTrue(
            IntroductionForm.CONDUCT_RULES.any { it.contains("no reply is itself an answer") },
        )
    }

    @Test
    fun `the platform refers religious questions to a qualified scholar`() {
        assertTrue(IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE.contains("does not give religious rulings"))
        assertTrue(IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE.contains("qualified scholar"))
    }
}

class ModerationPolicyTest {

    private val moderatorRoles = setOf(AccountRole.MODERATOR)
    private val safetyAdminRoles = setOf(AccountRole.SAFETY_ADMINISTRATOR)

    @Test
    fun `a permanent ban requires a safety administrator`() {
        assertFalse(ModerationPolicy.isAuthorized(ModerationActionType.ACCOUNT_BANNED, moderatorRoles))
        assertTrue(ModerationPolicy.isAuthorized(ModerationActionType.ACCOUNT_BANNED, safetyAdminRoles))
    }

    @Test
    fun `an ordinary member cannot take any moderation action`() {
        for (type in ModerationActionType.entries) {
            assertFalse(
                ModerationPolicy.isAuthorized(type, setOf(AccountRole.COMMUNITY_MEMBER)),
                "$type must not be available to an ordinary member",
            )
        }
    }

    @Test
    @DisplayName("an appeal cannot be reviewed by the moderator who took the original action")
    fun appealsNeedAFreshReviewer() {
        val appeal = Appeal(
            id = AppealId("a1"),
            caseId = ModerationCaseId("c1"),
            appellantId = UserId("member"),
            statement = "I was reported by someone I had reported first.",
        )
        val originalAction = ModerationAction(
            id = ModerationActionId("m1"),
            caseId = ModerationCaseId("c1"),
            moderatorId = UserId("mod-a"),
            type = ModerationActionType.FEATURE_RESTRICTED,
            rationale = "Repeated unwanted contact",
        )

        val sameModerator = ModerationPolicy.canReviewAppeal(
            appeal, UserId("mod-a"), safetyAdminRoles, listOf(originalAction),
        )
        assertTrue(sameModerator is AppealReviewDecision.Refused)

        val differentModerator = ModerationPolicy.canReviewAppeal(
            appeal, UserId("mod-b"), safetyAdminRoles, listOf(originalAction),
        )
        assertEquals(AppealReviewDecision.Permitted, differentModerator)
    }

    @Test
    fun `a member cannot review their own appeal`() {
        val appeal = Appeal(
            id = AppealId("a1"),
            caseId = ModerationCaseId("c1"),
            appellantId = UserId("member"),
            statement = "Please reconsider.",
        )
        val decision = ModerationPolicy.canReviewAppeal(
            appeal, UserId("member"), safetyAdminRoles, emptyList(),
        )
        assertTrue(decision is AppealReviewDecision.Refused)
    }

    @Test
    @DisplayName("unsend preserves the original for the safety team")
    fun unsendPreservesEvidence() {
        val message = Message(
            id = MessageId("m1"),
            conversationId = org.fisabilillah.core.model.ConversationId("c1"),
            senderId = UserId("sender"),
            body = "Something the sender immediately regretted",
            createdAt = Fixtures.NOW,
        )
        val decision = ModerationPolicy.canUnsend(message, UserId("sender"), Fixtures.NOW + 1.minutes)
        assertTrue(decision is UnsendDecision.Permitted)
        assertTrue((decision as UnsendDecision.Permitted).preserveOriginalForModeration)
    }

    @Test
    fun `unsend closes after its window so abuse cannot be erased later`() {
        val message = Message(
            id = MessageId("m1"),
            conversationId = org.fisabilillah.core.model.ConversationId("c1"),
            senderId = UserId("sender"),
            body = "Abusive content",
            createdAt = Fixtures.NOW,
        )
        val decision = ModerationPolicy.canUnsend(
            message,
            UserId("sender"),
            Fixtures.NOW + (Message.UNSEND_WINDOW_MINUTES + 1).minutes,
        )
        assertTrue(decision is UnsendDecision.Refused)
    }

    @Test
    fun `a member cannot unsend someone else's message`() {
        val message = Message(
            id = MessageId("m1"),
            conversationId = org.fisabilillah.core.model.ConversationId("c1"),
            senderId = UserId("sender"),
            body = "Hello",
            createdAt = Fixtures.NOW,
        )
        assertTrue(
            ModerationPolicy.canUnsend(message, UserId("someone-else"), Fixtures.NOW)
                is UnsendDecision.Refused,
        )
    }

    @Test
    fun `child safety and grooming reports start above the general queue`() {
        assertEquals(
            org.fisabilillah.core.model.EscalationLevel.SAFETY_ADMINISTRATOR,
            ModerationPolicy.initialEscalation(ReportCategory.CHILD_SAFETY),
        )
        assertEquals(
            org.fisabilillah.core.model.EscalationLevel.EXTERNAL_REFERRAL,
            ModerationPolicy.initialEscalation(ReportCategory.THREATS),
        )
        assertEquals(TriageTarget.IMMEDIATE, ModerationPolicy.triageTarget(ReportCategory.GROOMING))
    }

    @Test
    @DisplayName("a pattern of retaliatory reports is flagged, not silently acted on")
    fun retaliatoryReportingIsVisible() {
        val reports = (1..6).map {
            Report(
                id = ReportId("r$it"),
                reporterId = UserId("reporter"),
                target = ReportTarget.User(UserId("target")),
                category = ReportCategory.HARASSMENT,
                description = "Report $it",
                state = ReportState.DISMISSED,
                createdAt = Fixtures.NOW,
            )
        }
        val assessment = ModerationPolicy.assessReportPattern(reports, upheldCount = 0)
        assertTrue(assessment is ReportPatternAssessment.NeedsReview)
        // The note names both possibilities rather than presuming bad faith.
        assertTrue((assessment as ReportPatternAssessment.NeedsReview).note.contains("harassment"))
        assertTrue(assessment.note.contains("genuine pattern"))
    }

    @Test
    fun `a small number of reports is not treated as a pattern`() {
        val reports = (1..2).map {
            Report(
                id = ReportId("r$it"),
                reporterId = UserId("reporter"),
                target = ReportTarget.User(UserId("target-$it")),
                category = ReportCategory.SPAM,
                description = "Report $it",
                createdAt = Fixtures.NOW,
            )
        }
        assertEquals(
            ReportPatternAssessment.Normal,
            ModerationPolicy.assessReportPattern(reports, upheldCount = 0),
        )
    }

    @Test
    fun `a reporter previously found to be acting in bad faith is flagged`() {
        val reports = (1..3).map {
            Report(
                id = ReportId("r$it"),
                reporterId = UserId("reporter"),
                target = ReportTarget.User(UserId("target")),
                category = ReportCategory.HARASSMENT,
                description = "Report $it",
                abuseAssessment = if (it <= 2) {
                    ReportAbuseAssessment.RETALIATORY
                } else {
                    ReportAbuseAssessment.NOT_ASSESSED
                },
                createdAt = Fixtures.NOW,
            )
        }
        assertTrue(
            ModerationPolicy.assessReportPattern(reports, upheldCount = 0)
                is ReportPatternAssessment.LikelyAbusive,
        )
    }

    @Test
    fun `every moderation action maps to an audit action`() {
        for (type in ModerationActionType.entries) {
            // No exception and no null: adding a new action type forces a decision about
            // how it is recorded.
            ModerationPolicy.auditActionFor(type)
        }
    }

    @Test
    fun `a moderation action cannot be recorded without a rationale`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            ModerationAction(
                id = ModerationActionId("m1"),
                caseId = ModerationCaseId("c1"),
                moderatorId = UserId("mod"),
                type = ModerationActionType.WARNING_ISSUED,
                rationale = "   ",
            )
        }
    }
}

class ContentSignalsTest {

    @Test
    fun `sexual content is flagged with the phrase that triggered it`() {
        val signals = ContentSignals.forMessage("send pics of yourself", conversationIsCrossGender = true)
        val sexual = signals.first { it.kind == org.fisabilillah.core.model.SafetySignalKind.POSSIBLE_SEXUAL_CONTENT }
        assertTrue(sexual.explanation.contains("send pics"))
    }

    @Test
    @DisplayName("flirtation carries more weight in a cross-gender thread than in a same-gender one")
    fun contextChangesWeightNotExistence() {
        val crossGender = ContentSignals.forMessage("you have beautiful handwriting", true)
        val sameGender = ContentSignals.forMessage("you have beautiful handwriting", false)

        assertEquals(1, crossGender.size)
        assertEquals(1, sameGender.size)
        assertEquals(org.fisabilillah.core.model.SignalConfidence.MEDIUM, crossGender.first().confidence)
        assertEquals(org.fisabilillah.core.model.SignalConfidence.LOW, sameGender.first().confidence)
    }

    @Test
    fun `attempts to move off the platform are surfaced`() {
        val signals = ContentSignals.forMessage("just add me on whatsapp instead", true)
        assertTrue(
            signals.any {
                it.kind == org.fisabilillah.core.model.SafetySignalKind.POSSIBLE_OFF_PLATFORM_MOVE
            },
        )
    }

    @Test
    fun `scam patterns are surfaced`() {
        val signals = ContentSignals.forMessage(
            "I need you to send money by wire transfer today",
            false,
        )
        assertTrue(
            signals.any {
                it.kind ==
                    org.fisabilillah.core.model.SafetySignalKind.POSSIBLE_FINANCIAL_SOLICITATION
            },
        )
    }

    @Test
    fun `an ordinary message about the work produces no signals`() {
        val signals = ContentSignals.forMessage(
            "I can bring the van at nine and help load the parcels.",
            conversationIsCrossGender = true,
        )
        assertTrue(signals.isEmpty(), "unexpected signals: $signals")
    }

    @Test
    @DisplayName("every signal is advisory and explains itself")
    fun signalsNeverDecide() {
        val signals = ContentSignals.forMessage("send pics, my number is 07700 900123", true)
        assertTrue(signals.isNotEmpty())
        for (signal in signals) {
            assertTrue(signal.isAdvisoryOnly)
            assertTrue(signal.explanation.isNotBlank())
        }
    }

    @Test
    fun `drift is only checked once a thread has some history`() {
        val purpose = ContactPurpose(
            kind = ContactPurposeKind.VOLUNTEER_OPPORTUNITY,
            reasonForContact = "About the Saturday food distribution parcels and the van",
            requestedAction = "Confirm where to bring the van",
            expectedDuration = EngagementDuration.ONE_OFF,
        )
        assertEquals(null, ContentSignals.purposeDrift(purpose, listOf("hello", "salam")))
    }

    @Test
    fun `a thread that has wandered produces a gentle reminder`() {
        val purpose = ContactPurpose(
            kind = ContactPurposeKind.VOLUNTEER_OPPORTUNITY,
            reasonForContact = "About the Saturday food distribution parcels and the van",
            requestedAction = "Confirm where to bring the van and parcels",
            expectedDuration = EngagementDuration.ONE_OFF,
        )
        val wandered = List(8) { "completely unrelated chatter about football results tonight" }
        val drift = ContentSignals.purposeDrift(purpose, wandered)
        assertTrue(drift != null)

        val reminder = ContentSignals.driftReminder(purpose)
        assertTrue(reminder.contains("better to start a conversation for that instead"))
    }

    @Test
    fun `a thread still on topic produces no drift signal`() {
        val purpose = ContactPurpose(
            kind = ContactPurposeKind.VOLUNTEER_OPPORTUNITY,
            reasonForContact = "About the Saturday food distribution parcels and the van",
            requestedAction = "Confirm where to bring the van and parcels",
            expectedDuration = EngagementDuration.ONE_OFF,
        )
        val onTopic = List(8) { "bringing the van for the Saturday distribution parcels" }
        assertEquals(null, ContentSignals.purposeDrift(purpose, onTopic))
    }

    @Test
    fun `the disclosure describes exactly what the checks do`() {
        assertTrue(ContentSignals.DISCLOSURE.contains("never restrict an account"))
        assertTrue(ContentSignals.DISCLOSURE.contains("run on your device"))
    }

    @Test
    fun `a fixed instant keeps these tests deterministic`() {
        assertEquals(Instant.parse("2026-06-01T12:00:00Z"), Fixtures.NOW)
    }
}
