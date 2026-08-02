# Live voice and video sessions

*Written for two readers: an engineer new to this codebase, and a community safeguarding
lead who does not write software. The section [Safeguards travel into the
room](#safeguards-travel-into-the-room) is the one that matters most, and it requires no
engineering knowledge.*

> **Status, stated plainly at the top.** No audio and no video can be carried. There is no
> WebRTC stack, no SFU, no TURN server and no media provider. What exists is the data model,
> the rules that decide who may enter a room and what they may switch on once inside, and a
> tested proof that those rules behave as described. See [What is not
> built](#what-is-not-built).

---

## What it is for

Text does not carry everything. A student learning to read Arabic needs to be heard and
corrected in the moment. A teacher in Cairo taking eleven students across four countries
cannot do that by message. A project team distributing food the following morning needs
fifteen minutes on a call, not forty messages.

`LiveSessionKind` in
`core/model/src/main/kotlin/org/fisabilillah/core/model/LiveSessions.kt` enumerates the
eight rooms the platform recognises:

| Kind | What it is | Teaching room |
| --- | --- | --- |
| `CLASS` | A scheduled class with an instructor and enrolled students. | yes |
| `STUDY_CIRCLE` | Members studying together. Explicitly not authoritative instruction. | yes |
| `QURAN_RECITATION` | Reading aloud and being corrected by a teacher. | yes |
| `ONE_TO_ONE_TUTORING` | A single student with a single teacher. | yes |
| `PROJECT_MEETING` | A working call for a community project. | no |
| `ORGANIZATION_BRIEFING` | A masjid or organisation speaking to its members. | no |
| `VOLUNTEER_COORDINATION` | Arranging an activity with the people taking part. | no |
| `GUARDIAN_INCLUSIVE_INTRODUCTION` | A conversation following a formal introduction, with the guardian present throughout. | no |

The "teaching room" column is `LiveSessionKind.isInstruction`. It carries a consequence:
`LiveSessionPolicy.requiredDisclosures` repeats the instructor's capacity disclaimer into
the room, so a student who joined a live call has not thereby been told *less* about their
teacher's qualifications than a student who read the class listing.

The last row is the one to read carefully. `LiveSessionKind.userCreatable` returns `false`
for `GUARDIAN_INCLUSIVE_INTRODUCTION`, which means no member can schedule an introduction
room from the interface. It comes into being only through the guardian-led workflow, exactly
as its text conversation does. `LiveSessionPolicyTest`, "an introduction room is never
something a member can create", asserts this.

---

## Safeguards travel into the room

### The problem this solves

Most platforms treat a call as a switch. You are in the room or you are not, and everything
after that is settled socially, in the moment, in front of everyone.

That arrangement puts the cost on the wrong person. A woman in a mixed class who does not
show her face to unrelated men has to decline, out loud, when a host says "everyone camera
on please" — and she has to decline again the next week, and again in front of a group who
may or may not understand why. The boundary holds only as long as she is willing to defend
it under social pressure. That is not a safeguard. That is an obligation to perform one.

### What this platform does instead

**The room is configured from each person's own settings before they arrive.** If a sister's
safeguards say her camera is not available to unrelated men, then in a mixed class her
camera is switched off and locked off. Not hidden, not discouraged — *not available to be
requested*. The button is not there for the host to press, and it is not there for her to
press either.

She does not have to say no, because nobody can ask.

### Where this lives in the code

`LiveMediaPermissions` (`LiveSessions.kt`) is the record of what one person may switch on in
one room:

```
mayEnableMicrophone   mayEnableCamera   mayShareScreen   mayUseTextChat
cameraLockedReason    microphoneLockedReason
```

It is computed once, when the person joins, by `LiveSessionPolicy.mediaPermissions` in
`core/policy/src/main/kotlin/org/fisabilillah/core/policy/LiveSessionPolicy.kt`, and it is
stored on their `LiveParticipant` record. The comment on `LiveJoinDecision.Admitted` states
the contract the rest of the system must honour: *"The caller must apply them rather than
treat them as a suggestion."*

### Why a locked camera carries a reason

The two `…LockedReason` fields exist so the interface can say plainly *why* a control is
unavailable, rather than showing a dead button.

A greyed-out camera icon with no explanation teaches a person that the app is broken, or
that they have done something wrong. A sentence teaches them what their own settings are
doing for them. The strings are written out in `mediaPermissions`, and they differ by cause:

| Cause | What the person is told |
| --- | --- |
| Voice-only room | "This is a voice-only session." |
| `HOST_VIDEO_ONLY` room | "In this session only the instructor is on camera. You will be heard, not seen." |
| Their own setting, mixed room | "Your safeguards do not make your camera available to the opposite gender, and this session is mixed. **Nobody can ask you to change that here.**" |
| Their own setting, any room | "Your safeguards do not have your camera switched on for sessions like this. You can change that in Safeguards." |

The emphasised sentence is the whole design in eleven words, and it is asserted verbatim by
the test `a camera closed to the opposite gender stays closed in a mixed class`, which checks
`cameraLockedReason` contains `"Nobody can ask you to change"`.

The microphone has the same treatment, and the message deliberately points at what remains
open rather than only at what is closed: *"Your safeguards do not make your voice available
to the opposite gender, and this session is mixed. You can still take part in the text
chat."* The test `voice closed to the opposite gender still leaves the text chat open`
asserts both that `mayEnableMicrophone` is false and that `mayUseTextChat` is true.

### Neither the host nor a moderator can widen someone else's setting

This is worth stating on its own, because it is the property that makes the rest true.

Read the camera branch of `mediaPermissions`. The question it asks is never "does the host
allow cameras". It is "does *this person's own setting* allow their camera to be seen by the
people who are actually in this room". There is no parameter for the host's preference and
no branch that consults a moderator's authority. A host can make a room stricter — by
choosing `AUDIO_ONLY` or `HOST_VIDEO_ONLY` — and cannot make it more permissive for anybody
but themselves.

The test `neither the host nor a moderator can widen a participant's own setting` sets up the
maximal case: the room is full video, oversight is `MODERATOR_PRESENT`, a moderator is
actually in the room, and the participant's `videoCallsAllowedFrom` is `NOBODY`. The
assertion is one line: `assertFalse(decision.permissions.mayEnableCamera)`.

The mirror-image test, `the same person's camera is available in a sisters-only room`, shows
the setting is not a blanket prohibition. The same person with the same
`SAME_GENDER_ONLY` setting has her camera available in a `SISTERS_ONLY` room. Her rule is
about *who is present*, and the room honours the rule rather than a simplification of it.

### The host is not locked out of their own class

`the host is not blocked by their own participant-facing settings` covers the case that
would otherwise be an obvious bug: a teacher whose own `videoCallsAllowedFrom` is `NOBODY`
would be unable to teach on camera. `mediaPermissions` computes `isHost` and exempts them —
`videoCallsAllowedFrom` describes who may call *you*, not whether you may appear in a room
you are running.

---

## The entry rules, each traced to its test

Every rule below is enforced by `LiveSessionPolicy.canJoin`, which returns either
`LiveJoinDecision.Admitted` or `LiveJoinDecision.Refused(LiveJoinRefusal)`. The refusal enum
carries the exact sentence the member sees.

| Rule | Where | Test that proves it |
| --- | --- | --- |
| A one-to-one cross-gender session requires oversight | `canJoin`, "One-to-one across genders needs somebody else present" | `a one-to-one cross-gender session needs somebody else present` |
| …and is permitted once oversight exists | same | `the same session is permitted once a guardian is in it` |
| …and does not apply to same-gender tutorials | `session.isOneToOne && crossGender` | `a same-gender one-to-one tutorial needs no oversight` |
| Blocks apply to rooms, in both directions, and to anyone already present | `joinerBlockedByHost \|\| hostBlockedByJoiner \|\| blockedParticipantsPresent` | `a blocked member cannot join a room the blocker is in` |
| Restrictions apply | `isRestricted(...)` against `RestrictedCapability.JOIN_LIVE_SESSIONS` | `a restriction on live sessions is enforced` |
| Quiet hours cover calls | `safeguards.quietHours.blockCalls && covers(joinerLocalTime)` | `quiet hours cover calls as well as new conversations` |
| Gender arrangement is honoured | `session.genderArrangement.admits(joiner.gender)` | `a sisters-only session does not admit a brother` |
| Enrolment is required where the class requires it | `session.requiresEnrolment && !isEnrolled` | `an unenrolled member cannot walk into a class that requires enrolment` |
| Adults only; adults and minors never share a room | `joinerIsAdult != hostIsAdult` | `adults and minors do not share a live room` |
| A closed or halted room cannot be joined | `session.state.acceptsJoins` | `an ended session cannot be joined`, `a session stopped by moderation cannot be rejoined` |
| Oversight roles observe rather than participate | `requestedRole.isOversight` → `LiveMediaPermissions.observing(...)` | `an oversight participant observes rather than takes part` |
| The preview notice is shown while no provider is connected | `buildNotices` | `the preview notice is shown while no media provider is connected` |

Twenty-four tests in total, in `LiveSessionPolicyTest`
(`core/policy/src/test/kotlin/org/fisabilillah/core/policy/LiveSessionPolicyTest.kt`), split
across three nested groups plus two top-level cases: `safeguards travel into the room` (6),
`who may enter at all` (11), `oversight and recording` (5), top level (2).

### A note on the one-to-one cross-gender rule

This is the same rule as private messaging, for the same reason. An oversight participant
makes a cross-gender tutorial a supervised tutorial. Without one it is two people alone,
which this platform does not arrange.

**A caveat a safeguarding lead should know about.** The check is satisfied when *either* a
guardian or moderator is actually present (`moderatorPresent`, `guardianPresent`) *or* the
session was merely *configured* with `requiredOversight` containing something other than
`RECORDED_FOR_SAFEGUARDING`. The second half is a declaration, not a presence. A host could
declare `GUARDIAN_PRESENT` on a room that no guardian ever joins, and the gate would admit
the session. The test that covers this case sets both flags at once, so it does not
distinguish them. This is recorded as a known gap in
[`implementation-matrix.md`](implementation-matrix.md).

### Oversight roles

`LiveRole` marks `GUARDIAN`, `MODERATOR`, `ORGANIZATION_REPRESENTATIVE` and `THIRD_PARTY` as
`isOversight = true`. Anyone joining in one of those roles gets
`LiveMediaPermissions.observing(...)`: no camera, no microphone, and a stated reason —
*"You are present as guardian. You can see and hear the session and intervene, and you are
not on camera."*

That wording is deliberate on both sides. A guardian is not a silent surveillance device;
they can intervene. But they are not a participant either, and a room where the guardian is
on camera makes them a party to the conversation rather than a witness to it.

`LiveOversight` (five values) mirrors `ContactRequirement` for text conversations, and each
value carries a plain-language `explanation` that
`LiveSessionPolicy.requiredDisclosures` reads out before the room opens.

### `HOST_VIDEO_ONLY`, and why teachers want it

`LiveSessionMedium` has three values: `AUDIO_ONLY`, `VIDEO`, and `HOST_VIDEO_ONLY`
("Instructor on camera, students by voice").

The third is not a compromise. It is the arrangement most teachers of mixed classes actually
want, and asking for it should not require any student to disclose anything about their own
settings. In a `HOST_VIDEO_ONLY` room the instructor can show a text, a whiteboard, or their
own mouth forming a letter — which matters enormously for tajwid — while every student is
heard and none is seen. Nobody in the room has to work out who did or did not want their
camera on, because the question never arises.

`an instructor-only video room keeps students off camera regardless` asserts that a student
whose own `videoCallsAllowedFrom` is `EVERYONE` still has `mayEnableCamera == false`, with
the reason "heard, not seen". The room's arrangement is checked before the person's setting,
and the stricter of the two wins — the same rule as everywhere else on the platform.

---

## Recording

### The policy

`RecordingPolicy` has exactly two values, and there is no third:

- `NEVER` — "This session is not recorded. Nothing is stored except who attended."
- `WITH_CONSENT_OF_EVERY_PARTICIPANT` — "Recording cannot start until every person in the
  room has agreed, and it stops if someone joins who has not."

`a never-recorded session cannot be recorded by any route` asserts that a `NEVER` session
returns `NotPermitted` from `LiveSessionPolicy.recordingDecision` *and* false from
`LiveSession.recordingPermitted`, even when every participant has consented. There is no
override.

### Why consent is unanimous and continuing

Three ways a platform can handle recording consent:

1. **Majority.** The room votes. This is the worst option available, because the person most
   likely to object — the one who is quiet, new, junior, or the only woman in a room of men —
   is exactly the person a vote overrules.
2. **Silence as agreement.** Recording starts, a banner appears, anyone who objects speaks
   up. This puts the cost back on the person with the boundary, which is precisely the
   pattern the media-permissions design exists to eliminate.
3. **Unanimous and continuing.** Recording cannot begin until every person currently present
   has said yes, and consent already given by ten people does not cover an eleventh who
   arrives later.

This platform takes the third. `recordingDecision` filters
`session.activeParticipants` for anyone not in `session.recordingConsents` and refuses while
that list is non-empty, with a count in the message ("1 person has not agreed to being
recorded. Recording cannot start."). The test `recording needs everybody present to have
agreed, not a majority` builds a room of three with two consents, asserts the refusal and its
wording, then adds the third consent and asserts `RecordingDecision.Permitted`.

### Somebody joins late

`recordingPermitted` and `recordingDecision` both read `activeParticipants` *at the moment
they are called*, not a snapshot taken when recording began. A person who joins after
recording started is a new member of `activeParticipants` and is not in `recordingConsents`,
so the decision flips to `NotPermitted` and the recording must stop.

`LiveRecordingConsentUseCase.setConsent` in
`core/domain/src/main/kotlin/org/fisabilillah/core/domain/LiveSessionUseCases.kt` handles
withdrawal symmetrically — it removes the user from `recordingConsents` and returns the
recomputed decision, with the comment: *"Consent that cannot be withdrawn is not consent."*

**What is not yet enforced anywhere:** nothing stops a recording, because nothing records.
The decision function is correct and tested; the machinery that would obey it does not
exist. When a provider is wired up, the join path must re-evaluate `recordingDecision` on
every arrival and departure, and the transport must be able to stop a recording mid-session.

---

## What is not built

`LiveSessionFeatureFlags.transportConfigured` is `false`, and the test
`the preview notice is shown while no media provider is connected` asserts it.

What is missing, precisely:

- **No media transport.** No WebRTC peer connections, no SFU, no TURN or STUN servers, no
  provider credentials, no signalling.
- **No storage.** There is no `LiveSessionRepository` implementation. `core/data` has no
  in-memory store for live sessions and `SeedData` contains none.
- **No wiring.** `CoreGraph` in `core/data/src/main/kotlin/org/fisabilillah/core/data/Runtime.kt`
  does not construct any live-session use case.
- **No database.** No table, no RLS policy, and no migration under
  `backend/supabase/migrations/` mentions live sessions.
- **No interface.** There is no route in
  `androidApp/app/src/main/java/org/fisabilillah/app/ui/navigation/Routes.kt` and no screen
  under `ui/screens/` for scheduling, joining, or being in a session. The KDoc on
  `LiveSessionFeatureFlags` claims "the domain model, the safeguard gating **and the
  interface** are complete and tested"; the first two are true and the third is not.

`LiveSessionFeatureFlags.notConfiguredNotice` is the sentence members see, and it is honest
about the position: *"Live sessions are in preview. You can schedule a session, see who is
invited, and see exactly what your safeguards will permit once you are in the room — but no
audio or video can be carried yet, because the platform has not finished connecting a media
provider."*

### The checklist before that flag is flipped

Flipping `transportConfigured` is not a code change. Every item below has to be true first.

**1. Media provider agreement and data-processing terms.**
A signed contract with whoever carries the media (LiveKit Cloud, Daily, Twilio, a
self-hosted SFU), with a data-processing agreement that names the sub-processors, states
where media is relayed and where any recording is stored, and gives a deletion commitment
that matches this platform's retention policy in [`privacy-model.md`](privacy-model.md).
A provider whose default is to retain session metadata indefinitely is not usable without
that default changed in writing.

**2. TURN and STUN infrastructure.**
Direct peer connections fail behind carrier-grade NAT and most corporate networks. A relay
is not optional, it is the path a large fraction of real sessions will take, and relayed
media is the expensive kind. Requirements: geographically distributed TURN, credential
rotation, and capacity planning that assumes the worst case rather than the median.

**3. Per-jurisdiction legal position on recording consent.**
Recording law is not uniform, and this platform's users are not in one country. Some
jurisdictions require all-party consent; some require one-party; some treat a recorded
religious class differently from a recorded business call; some impose obligations on the
*storer* rather than the recorder. The unanimous-consent rule is at least as strict as any
of these, which is a good starting position — but "at least as strict" is a legal opinion,
not an engineering assertion, and it needs one from a lawyer per operating jurisdiction
before recording is offered anywhere.

**4. An abuse-reporting path that works for live audio.**
This is the hardest item and the one most likely to be skipped. **You cannot screenshot a
voice.** Every reporting mechanism on this platform today captures evidence at the moment of
reporting — `SubmitReportUseCase` snapshots the conversation and pulls the preserved
originals of unsent messages, because by the time a moderator looks the evidence may be
gone. Live audio has no equivalent. If someone says something abusive in a room that is not
being recorded, there is nothing to attach to a report, and a report with nothing behind it
protects nobody.

  Options, none of them free:
  - A short rolling buffer that a reporter can seal — which means recording everything
    briefly, which conflicts with `RecordingPolicy.NEVER` meaning what it says.
  - Contemporaneous corroboration from other participants, which is weak and pressures
    witnesses.
  - Requiring oversight presence on the higher-risk room types, so there is a witness by
    construction.

  A decision has to be made, written down, and told to members before their first live
  session — not after the first incident.

**5. Moderator join-in-progress.**
A moderator must be able to enter a live room that is already running, in response to a
report, without the host's cooperation and without being able to be removed. Today
`EndLiveSessionUseCase` lets a moderator halt a session
(`LiveSessionState.HALTED_BY_MODERATION`), which is a blunt instrument: it ends the class for
everybody including the person who needed help. Joining is the proportionate response and it
does not exist.

  The same requirement runs through the transport: `LiveSessionTransport.ejectParticipant`
  exists in the port's shape, but a provider that only lets the room creator eject people is
  not sufficient.

**6. Bandwidth and cost model.**
Relayed video is the single most expensive thing a platform of this kind can offer, and the
cost scales with participants × duration × resolution, not with users. A charity-adjacent
platform that discovers this after launch has to choose between a bill it cannot pay and
switching off a feature people have built their week around. Model it first, including the
`HOST_VIDEO_ONLY` case (cheap) against full mixed video (not).

**7. The child-safety position, before any youth use.**
The platform is adults only — `Profile.ADULT_AGE` is 18, and
`canJoin` refuses any room where `joinerIsAdult != hostIsAdult`. Live audio and video with
minors is a categorically different safeguarding problem from text with minors, and
[`child-safety.md`](child-safety.md) lists what must be true before *any* youth
functionality ships. Nothing in this document relaxes that. Live sessions must not be the
feature that quietly becomes the first youth surface.

---

## For engineers: where the transport plugs in

### Current state of the port

A `LiveSessionTransport` interface exists in
`core/domain/src/main/kotlin/org/fisabilillah/core/domain/LiveSessionUseCases.kt`. At the
time of writing that file is **untracked in git** — it was added to the working tree
alongside this documentation and is not in commit `a1a18dc`, which introduced the model and
the policy. Treat its shape as provisional until it lands.

It declares four operations:

```kotlin
public interface LiveSessionTransport {
    public suspend fun createRoom(session: LiveSession): String
    public suspend fun issueJoinToken(
        session: LiveSession,
        userId: UserId,
        permissions: LiveMediaPermissions,
    ): String
    public suspend fun closeRoom(session: LiveSession)
    public suspend fun ejectParticipant(session: LiveSession, userId: UserId)
}
```

`createRoom` returns the provider-side identifier that is stored on
`LiveSession.transportRoomId`, which is `null` for every session today.

### The one constraint that is not negotiable

`issueJoinToken` takes `LiveMediaPermissions` and **must encode them on the provider side**.

Enforcing the camera rule only in the Android application means a modified client — or a
person driving the provider's SDK directly with a valid token — can publish video that the
participant's safeguards forbid. The permission has to be a property of the credential, not
a property of the UI that requested it. Every serious media provider supports per-token
publish grants; a provider that does not is disqualified by this requirement alone.

Concretely, `mayEnableCamera == false` must translate into a token that carries no
video-publish grant, and `mayEnableMicrophone == false` into a token with no audio-publish
grant. A provider-side room configuration is not sufficient on its own, because the
permissions differ per participant within the same room.

### Where the calls go

The two consumers already exist and both guard on the flag:

- `JoinLiveSessionUseCase` calls `transport.issueJoinToken(...)` only when
  `LiveSessionFeatureFlags.transportConfigured && transport != null`, and returns
  `JoinedSession.joinToken = null` otherwise. The policy decision, the participant record and
  the audit entry are all written regardless — the room's bookkeeping works without a
  provider, which is what makes the preview honest rather than decorative.
- `EndLiveSessionUseCase` calls `transport.closeRoom(...)` under the same guard.

`transport` is a nullable constructor parameter in both, so a null implementation is a valid
production configuration today.

### What still has to be written

| Piece | Where it would go | State |
| --- | --- | --- |
| `LiveSessionRepository` implementation | `core/data/.../InMemoryRepositories.kt` | does not exist |
| Live-session wiring | `CoreGraph` in `core/data/.../Runtime.kt` | does not exist |
| `LiveSessionTransport` implementation | a new `core/transport` module or an Android-side adapter | does not exist |
| Tables, RLS policies, migration | `backend/supabase/migrations/` | does not exist |
| Routes and screens | `androidApp/.../navigation/Routes.kt`, `ui/screens/live/` | does not exist |
| Tests for the use cases | `core/data/src/test/` (integration) | does not exist — only `LiveSessionPolicy` is tested |
| `TRANSPORT_UNAVAILABLE` refusal | declared in `LiveJoinRefusal`; never returned by any code path | dead enum value |

The last row is worth flagging: `LiveJoinRefusal.TRANSPORT_UNAVAILABLE` exists with a
user-facing message but `LiveSessionPolicy` never returns it. Joining a room while no
transport is configured currently succeeds, with the preview notice attached. That may be the
intended behaviour — a member can see exactly what their safeguards will permit before any
media flows — but it should be a decision recorded somewhere rather than an unused enum
value.

---

## Related

- [`implementation-matrix.md`](implementation-matrix.md) — every feature against its screen,
  entity, permission, test and honest status.
- [`safeguards.md`](safeguards.md) — the safeguard system these rules read from.
- [`child-safety.md`](child-safety.md) — the adults-only position and what must be true
  before that changes.
- [`privacy-model.md`](privacy-model.md) — retention, consent, and what is collected.
- [`payment-compliance.md`](payment-compliance.md) — the other feature held behind a flag,
  and the model this checklist follows.
