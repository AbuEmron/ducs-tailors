package org.fisabilillah.core.policy

import kotlinx.datetime.LocalTime
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.LiveOversight
import org.fisabilillah.core.model.LiveParticipant
import org.fisabilillah.core.model.LiveRole
import org.fisabilillah.core.model.LiveSession
import org.fisabilillah.core.model.LiveSessionFeatureFlags
import org.fisabilillah.core.model.LiveSessionId
import org.fisabilillah.core.model.LiveSessionKind
import org.fisabilillah.core.model.LiveSessionMedium
import org.fisabilillah.core.model.LiveSessionState
import org.fisabilillah.core.model.LiveMediaPermissions
import org.fisabilillah.core.model.QuietHours
import org.fisabilillah.core.model.RecordingPolicy
import org.fisabilillah.core.model.RestrictedCapability
import org.fisabilillah.core.model.Restriction
import org.fisabilillah.core.model.RestrictionId
import org.fisabilillah.core.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

@DisplayName("Live sessions")
class LiveSessionPolicyTest {

    private val teacher = Fixtures.profile("teacher", gender = Gender.MALE)
    private val sister = Fixtures.profile("sister", gender = Gender.FEMALE)
    private val brother = Fixtures.profile("brother", gender = Gender.MALE)

    private fun session(
        kind: LiveSessionKind = LiveSessionKind.CLASS,
        medium: LiveSessionMedium = LiveSessionMedium.VIDEO,
        arrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
        maxParticipants: Int = 20,
        state: LiveSessionState = LiveSessionState.LIVE,
        oversight: Set<LiveOversight> = emptySet(),
        recording: RecordingPolicy = RecordingPolicy.NEVER,
        participants: List<LiveParticipant> = emptyList(),
        consents: Set<UserId> = emptySet(),
        requiresEnrolment: Boolean = false,
    ) = LiveSession(
        id = LiveSessionId("s1"),
        title = "Arabic reading, week three",
        kind = kind,
        medium = medium,
        hostId = teacher.id,
        genderArrangement = arrangement,
        scheduledStart = Fixtures.NOW,
        scheduledEnd = Fixtures.NOW + 1.hours,
        maxParticipants = maxParticipants,
        participants = participants,
        recordingPolicy = recording,
        recordingConsents = consents,
        requiresEnrolment = requiresEnrolment,
        requiredOversight = oversight,
        state = state,
    )

    private fun context(
        session: LiveSession = session(),
        joiner: org.fisabilillah.core.model.Profile = brother,
        safeguards: org.fisabilillah.core.model.UserSafeguards =
            Fixtures.safeguards(joiner.id.value),
        role: LiveRole = LiveRole.PARTICIPANT,
        gendersPresent: Set<Gender> = setOf(Gender.MALE),
        build: LiveJoinContext.() -> LiveJoinContext = { this },
    ) = LiveJoinContext(
        session = session,
        host = teacher,
        joiner = joiner,
        joinerEffectiveSafeguards = safeguards,
        requestedRole = role,
        gendersPresent = gendersPresent,
        joinerLocalTime = LocalTime(14, 0),
        now = Fixtures.NOW,
        currentYear = Fixtures.YEAR,
    ).build()

    private fun admitted(d: LiveJoinDecision): LiveJoinDecision.Admitted {
        assertTrue(d is LiveJoinDecision.Admitted, "expected admitted but was $d")
        return d as LiveJoinDecision.Admitted
    }

    private fun refused(d: LiveJoinDecision): LiveJoinRefusal {
        assertTrue(d is LiveJoinDecision.Refused, "expected refused but was $d")
        return (d as LiveJoinDecision.Refused).reason
    }

    @Test
    fun `a member of the right group joins an open class`() {
        admitted(LiveSessionPolicy.canJoin(context()))
    }

    @Nested
    @DisplayName("safeguards travel into the room")
    inner class SafeguardsTravel {

        @Test
        @DisplayName("a camera closed to the opposite gender stays closed in a mixed class")
        fun cameraStaysClosedInMixedRoom() {
            val decision = admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        joiner = sister,
                        safeguards = Fixtures.safeguards("sister").copy(
                            videoCallsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
                            voiceCallsAllowedFrom = AudienceScope.EVERYONE,
                        ),
                        gendersPresent = setOf(Gender.MALE, Gender.FEMALE),
                    ),
                ),
            )

            assertFalse(decision.permissions.mayEnableCamera)
            assertTrue(decision.permissions.mayEnableMicrophone)
            // And the reason is stated, so the interface shows an explanation rather than a
            // dead button.
            assertNotNull(decision.permissions.cameraLockedReason)
            assertTrue(
                decision.permissions.cameraLockedReason!!.contains("Nobody can ask you to change"),
            )
        }

        @Test
        fun `the same person's camera is available in a sisters-only room`() {
            val decision = admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        session = session(arrangement = GenderArrangement.SISTERS_ONLY),
                        joiner = sister,
                        safeguards = Fixtures.safeguards("sister").copy(
                            videoCallsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
                        ),
                        gendersPresent = setOf(Gender.FEMALE),
                    ),
                ),
            )
            assertTrue(decision.permissions.mayEnableCamera)
        }

        @Test
        @DisplayName("neither the host nor a moderator can widen a participant's own setting")
        fun nobodyCanWidenSomeoneElsesBoundary() {
            // The room is full video, the host wants everybody visible, a moderator is
            // present — and the person's own setting still wins.
            val decision = admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        session = session(
                            medium = LiveSessionMedium.VIDEO,
                            oversight = setOf(LiveOversight.MODERATOR_PRESENT),
                        ),
                        joiner = sister,
                        safeguards = Fixtures.safeguards("sister")
                            .copy(videoCallsAllowedFrom = AudienceScope.NOBODY),
                        gendersPresent = setOf(Gender.MALE, Gender.FEMALE),
                    ) { copy(moderatorPresent = true) },
                ),
            )
            assertFalse(decision.permissions.mayEnableCamera)
        }

        @Test
        fun `an instructor-only video room keeps students off camera regardless`() {
            val decision = admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        session = session(medium = LiveSessionMedium.HOST_VIDEO_ONLY),
                        joiner = brother,
                        safeguards = Fixtures.safeguards("brother")
                            .copy(videoCallsAllowedFrom = AudienceScope.EVERYONE),
                    ),
                ),
            )
            assertFalse(decision.permissions.mayEnableCamera)
            assertTrue(decision.permissions.mayEnableMicrophone)
            assertTrue(decision.permissions.cameraLockedReason!!.contains("heard, not seen"))
        }

        @Test
        fun `the host is not blocked by their own participant-facing settings`() {
            val decision = admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        joiner = teacher,
                        safeguards = Fixtures.safeguards("teacher")
                            .copy(videoCallsAllowedFrom = AudienceScope.NOBODY),
                    ),
                ),
            )
            assertTrue(decision.permissions.mayEnableCamera)
            assertTrue(decision.permissions.mayShareScreen)
        }

        @Test
        fun `voice closed to the opposite gender still leaves the text chat open`() {
            val decision = admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        joiner = sister,
                        safeguards = Fixtures.safeguards("sister").copy(
                            voiceCallsAllowedFrom = AudienceScope.SAME_GENDER_ONLY,
                            videoCallsAllowedFrom = AudienceScope.NOBODY,
                        ),
                        gendersPresent = setOf(Gender.MALE, Gender.FEMALE),
                    ),
                ),
            )
            assertFalse(decision.permissions.mayEnableMicrophone)
            assertTrue(decision.permissions.mayUseTextChat)
            assertTrue(decision.permissions.microphoneLockedReason!!.contains("text chat"))
        }
    }

    @Nested
    @DisplayName("who may enter at all")
    inner class Entry {

        @Test
        fun `a one-to-one cross-gender session needs somebody else present`() {
            assertEquals(
                LiveJoinRefusal.ONE_TO_ONE_CROSS_GENDER_WITHOUT_OVERSIGHT,
                refused(
                    LiveSessionPolicy.canJoin(
                        context(
                            session = session(
                                kind = LiveSessionKind.ONE_TO_ONE_TUTORING,
                                maxParticipants = 2,
                            ),
                            joiner = sister,
                            gendersPresent = setOf(Gender.MALE),
                        ),
                    ),
                ),
            )
        }

        @Test
        fun `the same session is permitted once a guardian is in it`() {
            admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        session = session(
                            kind = LiveSessionKind.ONE_TO_ONE_TUTORING,
                            maxParticipants = 2,
                            oversight = setOf(LiveOversight.GUARDIAN_PRESENT),
                        ),
                        joiner = sister,
                        gendersPresent = setOf(Gender.MALE),
                    ) { copy(guardianPresent = true) },
                ),
            )
        }

        @Test
        fun `a same-gender one-to-one tutorial needs no oversight`() {
            admitted(
                LiveSessionPolicy.canJoin(
                    context(
                        session = session(
                            kind = LiveSessionKind.ONE_TO_ONE_TUTORING,
                            maxParticipants = 2,
                        ),
                        joiner = brother,
                        gendersPresent = setOf(Gender.MALE),
                    ),
                ),
            )
        }

        @Test
        fun `a blocked member cannot join a room the blocker is in`() {
            assertEquals(
                LiveJoinRefusal.BLOCKED,
                refused(
                    LiveSessionPolicy.canJoin(context { copy(blockedParticipantsPresent = true) }),
                ),
            )
            assertEquals(
                LiveJoinRefusal.BLOCKED,
                refused(LiveSessionPolicy.canJoin(context { copy(joinerBlockedByHost = true) })),
            )
        }

        @Test
        fun `a restriction on live sessions is enforced`() {
            val restriction = Restriction(
                id = RestrictionId("r"),
                userId = brother.id,
                capability = RestrictedCapability.JOIN_LIVE_SESSIONS,
                reason = "Under review",
                imposedBy = UserId("mod"),
                caseId = null,
                startsAt = Fixtures.NOW,
            )
            assertEquals(
                LiveJoinRefusal.RESTRICTED,
                refused(
                    LiveSessionPolicy.canJoin(context { copy(joinerRestrictions = listOf(restriction)) }),
                ),
            )
        }

        @Test
        fun `a sisters-only session does not admit a brother`() {
            assertEquals(
                LiveJoinRefusal.GENDER_ARRANGEMENT,
                refused(
                    LiveSessionPolicy.canJoin(
                        context(session = session(arrangement = GenderArrangement.SISTERS_ONLY)),
                    ),
                ),
            )
        }

        @Test
        fun `an unenrolled member cannot walk into a class that requires enrolment`() {
            assertEquals(
                LiveJoinRefusal.NOT_ENROLLED,
                refused(
                    LiveSessionPolicy.canJoin(context(session = session(requiresEnrolment = true))),
                ),
            )
        }

        @Test
        fun `quiet hours cover calls as well as new conversations`() {
            assertEquals(
                LiveJoinRefusal.QUIET_HOURS,
                refused(
                    LiveSessionPolicy.canJoin(
                        context(
                            safeguards = Fixtures.safeguards("brother").copy(
                                quietHours = QuietHours(
                                    enabled = true,
                                    start = LocalTime(21, 0),
                                    end = LocalTime(7, 0),
                                ),
                            ),
                        ) { copy(joinerLocalTime = LocalTime(23, 0)) },
                    ),
                ),
            )
        }

        @Test
        fun `an ended session cannot be joined`() {
            assertEquals(
                LiveJoinRefusal.SESSION_NOT_OPEN,
                refused(
                    LiveSessionPolicy.canJoin(context(session = session(state = LiveSessionState.ENDED))),
                ),
            )
        }

        @Test
        fun `a session stopped by moderation cannot be rejoined`() {
            assertEquals(
                LiveJoinRefusal.SESSION_NOT_OPEN,
                refused(
                    LiveSessionPolicy.canJoin(
                        context(session = session(state = LiveSessionState.HALTED_BY_MODERATION)),
                    ),
                ),
            )
        }

        @Test
        fun `adults and minors do not share a live room`() {
            assertEquals(
                LiveJoinRefusal.MINOR_ADULT_MIXING,
                refused(
                    LiveSessionPolicy.canJoin(
                        context(joiner = Fixtures.profile("young", birthYear = 2012)),
                    ),
                ),
            )
        }
    }

    @Nested
    @DisplayName("oversight and recording")
    inner class Oversight {

        @Test
        fun `an oversight participant observes rather than takes part`() {
            val decision = admitted(
                LiveSessionPolicy.canJoin(context(role = LiveRole.GUARDIAN)),
            )
            assertFalse(decision.permissions.mayEnableCamera)
            assertFalse(decision.permissions.mayEnableMicrophone)
            assertTrue(decision.permissions.cameraLockedReason!!.contains("guardian"))
        }

        @Test
        @DisplayName("recording needs everybody present to have agreed, not a majority")
        fun recordingNeedsEveryone() {
            val present = listOf(
                participant(teacher.id),
                participant(brother.id),
                participant(sister.id),
            )
            val partial = session(
                recording = RecordingPolicy.WITH_CONSENT_OF_EVERY_PARTICIPANT,
                participants = present,
                consents = setOf(teacher.id, brother.id),
            )
            val decision = LiveSessionPolicy.recordingDecision(partial)
            assertTrue(decision is RecordingDecision.NotPermitted)
            assertTrue((decision as RecordingDecision.NotPermitted).message.contains("1 person has"))

            val complete = partial.copy(
                recordingConsents = setOf(teacher.id, brother.id, sister.id),
            )
            assertEquals(RecordingDecision.Permitted, LiveSessionPolicy.recordingDecision(complete))
        }

        @Test
        fun `a never-recorded session cannot be recorded by any route`() {
            val s = session(
                recording = RecordingPolicy.NEVER,
                participants = listOf(participant(teacher.id)),
                consents = setOf(teacher.id),
            )
            assertTrue(LiveSessionPolicy.recordingDecision(s) is RecordingDecision.NotPermitted)
            assertFalse(s.recordingPermitted)
        }

        @Test
        fun `a teaching room repeats the instructor's capacity disclaimer`() {
            val disclosures = LiveSessionPolicy.requiredDisclosures(
                session(kind = LiveSessionKind.QURAN_RECITATION),
                hostCapacityDisclaimer = "Peer learning. Not a substitute for a qualified scholar.",
            )
            assertTrue(disclosures.any { it.contains("Not a substitute") })
            assertTrue(disclosures.any { it.contains("does not give religious rulings") })
        }

        @Test
        fun `the preview notice is shown while no media provider is connected`() {
            assertFalse(LiveSessionFeatureFlags.transportConfigured)
            val decision = admitted(LiveSessionPolicy.canJoin(context()))
            assertTrue(
                decision.notices.any { it.contains("not finished connecting a media provider") },
            )
        }
    }

    @Test
    @DisplayName("an introduction room is never something a member can create")
    fun introductionRoomsComeOnlyFromTheWorkflow() {
        assertFalse(LiveSessionKind.GUARDIAN_INCLUSIVE_INTRODUCTION.userCreatable)
        assertTrue(LiveSessionKind.CLASS.userCreatable)
    }

    private fun participant(userId: UserId) = LiveParticipant(
        sessionId = LiveSessionId("s1"),
        userId = userId,
        role = LiveRole.PARTICIPANT,
        joinedAt = Fixtures.NOW,
        permissions = LiveMediaPermissions.full(),
    )
}
