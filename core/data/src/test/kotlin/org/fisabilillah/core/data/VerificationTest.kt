package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.domain.Principal
import org.fisabilillah.core.domain.RequestVerificationUseCase
import org.fisabilillah.core.domain.SubmitQualificationUseCase
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Attestation
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.QualificationReviewState
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.VerificationMethod
import org.fisabilillah.core.model.VerificationRequestState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Trust can now be earned, which means the introduction workflow can now be used.
 *
 * Everything that *reads* a verification level has existed since the first release, and
 * nothing could raise one: a member reached `EMAIL_VERIFIED` at onboarding and stopped
 * there for ever. Every safeguard written against `IDENTITY_VERIFIED` was therefore
 * unreachable, and the formal-introduction feature — the most carefully built thing in
 * the product — could not be used by anybody.
 */
@DisplayName("Verification and qualifications")
class VerificationTest {

    private fun Harness.moderator() = Principal(SeedData.moderator, setOf(AccountRole.MODERATOR))
    private fun Harness.admin() = Principal(SeedData.safetyAdmin, setOf(AccountRole.SAFETY_ADMINISTRATOR))
    private fun Harness.scholar() = Principal(SeedData.ibrahim, setOf(AccountRole.SCHOLAR))

    private fun identityRequest() = RequestVerificationUseCase.Command(
        requestedLevel = VerificationLevel.IDENTITY_VERIFIED,
        method = VerificationMethod.DOCUMENT_PROVIDER,
        evidenceRefs = listOf("private/verification/daniel-passport.jpg"),
        note = "Passport, checked by the provider on the 3rd.",
    )

    // ── The core loop ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a member requests, a moderator approves, and the level actually rises")
    fun theLevelRises() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.daniel)

        val before = harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel
        assertFalse(before atLeast VerificationLevel.IDENTITY_VERIFIED)

        val request = harness.graph.requestVerification(member, identityRequest()).expectSuccess()
        assertEquals(VerificationRequestState.SUBMITTED, request.state)

        harness.graph.decideVerification.approve(
            harness.moderator(),
            request.id,
            "Document matched the name on the account.",
        ).expectSuccess()

        assertEquals(
            VerificationLevel.IDENTITY_VERIFIED,
            harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel,
        )
        assertTrue(
            harness.store.notifications.any {
                it.userId == SeedData.daniel && it.title.contains("approved")
            },
        )
        assertTrue(
            harness.graph.auditLog.recent(50).any { it.action == AuditAction.VERIFICATION_CHANGED },
        )
    }

    @Test
    @DisplayName("a rejection changes nothing about the account and says why")
    fun rejectionChangesNothing() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.daniel)
        val before = harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel

        val request = harness.graph.requestVerification(member, identityRequest()).expectSuccess()
        harness.graph.decideVerification.reject(
            harness.moderator(),
            request.id,
            "The document was too blurred to read. Please send another.",
        ).expectSuccess()

        assertEquals(before, harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel)
        assertTrue(
            harness.store.notifications.any { it.body.contains("too blurred") },
            "the member must be told what to do next",
        )
    }

    // ── What a member cannot do ───────────────────────────────────────────────

    @Test
    @DisplayName("the request records the caller, so nobody can request on another account")
    fun theRequesterIsTheCaller() = runTest {
        val harness = Harness()
        val request = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            identityRequest(),
        ).expectSuccess()

        assertEquals(SeedData.daniel, request.userId)
    }

    @Test
    @DisplayName("a method cannot justify more than it can justify")
    fun theMethodCeilingHolds() = runTest {
        val harness = Harness()
        val invalid = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            RequestVerificationUseCase.Command(
                requestedLevel = VerificationLevel.BACKGROUND_CHECKED,
                method = VerificationMethod.PHONE,
            ),
        ) as Outcome.Invalid

        assertEquals("method", invalid.errors.single().field)
    }

    @Test
    @DisplayName("a generous reviewer still cannot grant more than the method supports")
    fun theCeilingBeatsTheReviewer() = runTest {
        val harness = Harness()
        // A request the validator allows: identity, by document.
        val request = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            identityRequest(),
        ).expectSuccess()

        // Now imagine the evidence was only ever a phone confirmation. The stored method
        // is what the grant is computed from, not the level that was asked for.
        harness.graph.verifications.save(request.copy(method = VerificationMethod.PHONE))

        harness.graph.decideVerification.approve(
            harness.moderator(),
            request.id,
            "Approving.",
        ).expectSuccess()

        assertEquals(
            VerificationLevel.PHONE_VERIFIED,
            harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel,
            "the method's ceiling must win over the level that was requested",
        )
    }

    @Test
    @DisplayName("evidence is required for anything above a confirmed channel")
    fun evidenceIsRequired() = runTest {
        val harness = Harness()
        val invalid = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            RequestVerificationUseCase.Command(
                requestedLevel = VerificationLevel.IDENTITY_VERIFIED,
                method = VerificationMethod.DOCUMENT_PROVIDER,
                evidenceRefs = emptyList(),
            ),
        ) as Outcome.Invalid

        assertEquals("evidenceRefs", invalid.errors.single().field)
    }

    @Test
    @DisplayName("only one request at a time")
    fun oneOpenRequestAtATime() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.daniel)
        harness.graph.requestVerification(member, identityRequest()).expectSuccess()

        val refusal = harness.graph.requestVerification(member, identityRequest()).expectRefused()
        assertTrue(refusal.message.contains("already have a verification request"))
    }

    // ── What a reviewer cannot do ─────────────────────────────────────────────

    @Test
    @DisplayName("nobody approves their own verification, whatever roles they hold")
    fun nobodyVerifiesThemselves() = runTest {
        val harness = Harness()
        // Daniel, but wearing a moderator's roles: the point is that the roles do not
        // matter, so the test needs somebody who could otherwise make the request.
        val moderator = Principal(SeedData.daniel, setOf(AccountRole.MODERATOR))

        val request = harness.graph.requestVerification(
            moderator,
            identityRequest(),
        ).expectSuccess()

        val refusal = harness.graph.decideVerification
            .approve(moderator, request.id, "Looks fine to me.")
            .expectRefused()
        assertTrue(refusal.message.contains("your own account"))
    }

    @Test
    @DisplayName("an ordinary member cannot decide, and cannot see the queue")
    fun decisionsAreStaffOnly() = runTest {
        val harness = Harness()
        val request = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            identityRequest(),
        ).expectSuccess()

        assertTrue(
            harness.graph.decideVerification
                .approve(harness.principal(SeedData.abdullah), request.id, "Fine")
                .expectRefused().message.contains("safety team"),
        )
        assertTrue(
            harness.graph.decideVerification
                .queue(harness.principal(SeedData.abdullah))
                .expectRefused().message.contains("safety team"),
        )
    }

    @Test
    @DisplayName("a decision needs a reason, because the member is shown it")
    fun aDecisionNeedsAReason() = runTest {
        val harness = Harness()
        val request = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            identityRequest(),
        ).expectSuccess()

        val invalid = harness.graph.decideVerification
            .approve(harness.moderator(), request.id, "  ") as Outcome.Invalid
        assertEquals("note", invalid.errors.single().field)
    }

    @Test
    @DisplayName("a decided request cannot be decided again")
    fun decisionsAreFinal() = runTest {
        val harness = Harness()
        val request = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            identityRequest(),
        ).expectSuccess()

        harness.graph.decideVerification
            .approve(harness.moderator(), request.id, "Checked.")
            .expectSuccess()

        assertTrue(
            harness.graph.decideVerification
                .reject(harness.admin(), request.id, "Changed my mind.")
                .expectRefused().message.contains("already been decided"),
        )
    }

    // ── Taking it away ────────────────────────────────────────────────────────

    @Test
    @DisplayName("revoking drops to what the remaining approvals still justify, not to zero")
    fun revocationFallsBackRatherThanCollapsing() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.daniel)

        // A phone confirmation, approved.
        val phone = harness.graph.requestVerification(
            member,
            RequestVerificationUseCase.Command(
                requestedLevel = VerificationLevel.PHONE_VERIFIED,
                method = VerificationMethod.PHONE,
            ),
        ).expectSuccess()
        harness.graph.decideVerification.approve(harness.moderator(), phone.id, "Code confirmed.")
            .expectSuccess()

        // Then a background check, approved.
        val check = harness.graph.requestVerification(
            member,
            RequestVerificationUseCase.Command(
                requestedLevel = VerificationLevel.BACKGROUND_CHECKED,
                method = VerificationMethod.BACKGROUND_CHECK_PROVIDER,
                evidenceRefs = listOf("private/verification/daniel-dbs.pdf"),
            ),
        ).expectSuccess()
        harness.graph.decideVerification.approve(harness.moderator(), check.id, "Clear.")
            .expectSuccess()
        assertEquals(
            VerificationLevel.BACKGROUND_CHECKED,
            harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel,
        )

        // The check is withdrawn. The phone confirmation is untouched.
        harness.graph.decideVerification.revoke(
            harness.admin(),
            check.id,
            "The certificate has expired and has not been renewed.",
        ).expectSuccess()

        assertEquals(
            VerificationLevel.PHONE_VERIFIED,
            harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel,
            "losing a background check should not also un-confirm a phone number",
        )
    }

    @Test
    @DisplayName("revoking requires a safety administrator, not merely a moderator")
    fun revocationIsSenior() = runTest {
        val harness = Harness()
        val request = harness.graph.requestVerification(
            harness.principal(SeedData.daniel),
            identityRequest(),
        ).expectSuccess()
        harness.graph.decideVerification.approve(harness.moderator(), request.id, "Checked.")
            .expectSuccess()

        assertTrue(
            harness.graph.decideVerification
                .revoke(harness.moderator(), request.id, "Second thoughts.")
                .expectRefused().message.contains("safety administrator"),
        )
    }

    // ── Qualifications ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a claimed qualification is stored as a claim, never as a confirmation")
    fun aClaimIsAClaim() = runTest {
        val harness = Harness()
        val saved = harness.graph.submitQualification(
            harness.principal(SeedData.daniel),
            SubmitQualificationUseCase.Command(
                title = "Ijazah in Hafs 'an 'Asim",
                issuingBody = "Shaykh Muhammad al-Amin",
                issuedYear = 2019,
            ),
        ).expectSuccess()

        assertEquals(QualificationReviewState.SUBMITTED, saved.reviewState)
        assertEquals(null, saved.verifiedAt)
        assertEquals(null, saved.verifiedBy)
        assertFalse(
            harness.graph.profiles.find(SeedData.daniel)!!
                .attestations.contains(Attestation.QUALIFICATION_VERIFIED),
            "claiming must not produce the badge",
        )
    }

    @Test
    @DisplayName("a scholar can confirm one, and the badge appears")
    fun aScholarCanConfirm() = runTest {
        val harness = Harness()
        val claim = harness.graph.submitQualification(
            harness.principal(SeedData.daniel),
            SubmitQualificationUseCase.Command(
                title = "Ijazah in Hafs 'an 'Asim",
                issuingBody = "Shaykh Muhammad al-Amin",
            ),
        ).expectSuccess()

        harness.graph.reviewQualification.decide(
            harness.scholar(),
            claim.id,
            verified = true,
            note = "I contacted the shaykh's office and they confirmed the chain.",
        ).expectSuccess()

        assertTrue(
            harness.graph.profiles.find(SeedData.daniel)!!
                .attestations.contains(Attestation.QUALIFICATION_VERIFIED),
        )
    }

    @Test
    @DisplayName("declining one removes the badge when it was the last confirmed claim")
    fun theBadgeDoesNotOutliveWhatItStandsFor() = runTest {
        val harness = Harness()
        val claim = harness.graph.submitQualification(
            harness.principal(SeedData.daniel),
            SubmitQualificationUseCase.Command(title = "Ijazah", issuingBody = "A shaykh"),
        ).expectSuccess()

        harness.graph.reviewQualification
            .decide(harness.scholar(), claim.id, verified = true, note = "Confirmed.")
            .expectSuccess()
        harness.graph.reviewQualification
            .decide(harness.scholar(), claim.id, verified = false, note = "Withdrawn on review.")
            .expectSuccess()

        assertFalse(
            harness.graph.profiles.find(SeedData.daniel)!!
                .attestations.contains(Attestation.QUALIFICATION_VERIFIED),
        )
    }

    @Test
    @DisplayName("an ordinary member cannot review a qualification, and nobody reviews their own")
    fun qualificationReviewIsRestricted() = runTest {
        val harness = Harness()
        val claim = harness.graph.submitQualification(
            harness.principal(SeedData.daniel),
            SubmitQualificationUseCase.Command(title = "Ijazah", issuingBody = "A shaykh"),
        ).expectSuccess()

        assertTrue(
            harness.graph.reviewQualification
                .decide(harness.principal(SeedData.abdullah), claim.id, true, "Looks right")
                .expectRefused().message.contains("moderator or a listed scholar"),
        )

        val own = harness.graph.submitQualification(
            harness.scholar(),
            SubmitQualificationUseCase.Command(title = "Ijazah", issuingBody = "A shaykh"),
        ).expectSuccess()
        assertTrue(
            harness.graph.reviewQualification
                .decide(harness.scholar(), own.id, true, "Mine, and genuine")
                .expectRefused().message.contains("your own qualification"),
        )
    }

    @Test
    @DisplayName("a member can see their own level and the requests behind it")
    fun aMemberCanSeeTheirOwnStanding() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.daniel)
        harness.graph.requestVerification(member, identityRequest()).expectSuccess()

        val state = harness.graph.myVerification(member).expectSuccess()
        assertTrue(state.hasOpenRequest)
        assertEquals(1, state.requests.size)
    }
}
