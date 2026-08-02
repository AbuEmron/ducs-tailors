# Testing

## Where things stand

Two suites exist and both pass.

| Suite | How to run it | Result |
| --- | --- | --- |
| Shared core, JVM | `./gradlew test` from the repository root | **158 tests, all passing** |
| Database, row-level security | `backend/supabase/run_local_tests.sh` | **100 assertions, all passing** |

There is a third thing that does not exist yet: the Android module has no passing test run,
because it has never been compiled. See [What is not covered](#what-is-not-covered).

---

## The shared core suite

```console
$ ./gradlew test
```

Runs on any machine with a JDK 17 or newer. No Android SDK, no emulator, no Google Maven.
JUnit 5, configured once in the root `build.gradle.kts` for every module that applies the
Kotlin JVM plugin.

### Breakdown

| Module | Test class | Tests |
| --- | --- | --- |
| `:core:policy` | `ContactPolicyTest` (six nested groups) | 30 |
| | `IntroductionPolicyTest` | 18 |
| | `ModerationPolicyTest` | 13 |
| | `ContentSignalsTest` | 11 |
| | `ProductPrincipleTest` | 10 |
| | `SafeguardResolverTest` | 8 |
| | `VerificationPolicyTest` | 8 |
| | `VisibilityPolicyTest` | 6 |
| | `TrustPolicyTest` | 5 |
| | **subtotal** | **109** |
| `:core:data` | `CriticalFlowsTest` | 25 |
| | `DiscoveryBehaviourTest` | 8 |
| | `SeedDataTest` | 8 |
| | `MessagingBehaviourTest` | 5 |
| | `SafeguardFloorScopeTest` | 3 |
| | **subtotal** | **49** |
| | **total** | **158** |

Before `ProductPrincipleTest` was added the figure was 148. Those ten tests assert on the
shape of the domain rather than on behaviour, and are described below.

### `:core:policy` — the decision layer

These tests call pure functions with hand-built inputs from
`core/policy/src/test/.../Fixtures.kt`. No repositories, no clock, no coroutines. That is
what makes it affordable to enumerate cases rather than sample them.

**`ContactPolicyTest`** is organised into nested groups that follow the ordered checks in
`ContactPolicy.evaluate`:

| Group | What it pins down |
| --- | --- |
| `blocking` | A block in either direction is a hard stop, checked before anything that could leak information about the recipient's settings |
| `purpose` | A purpose must be user-selectable, must carry the right kind of subject, and a declined purpose is refused |
| `cross-gender contact` | Each `CrossGenderConversationStructure` value produces the requirement and oversight it promises; a guardian-required recipient with no available guardian is refused rather than quietly downgraded |
| `account state and restrictions` | Inactive, suspended, banned and restricted accounts on either side |
| `audience, verification and organisation scope` | Every `AudienceScope` value against a viewer who does and does not satisfy it |
| `time, rate limits and persistence` | Quiet hours across a window that wraps midnight, the ten-conversation window, and the refusal to allow a second approach after a decline |

**`SafeguardResolverTest`** covers the one rule everything else rests on. Its headline case
is named *"an organisation floor can only tighten, never loosen"*, and there is a companion
named *"no preset is presented as more religious than another"*.

**`IntroductionPolicyTest`** covers the state machine and the refusal design. Named cases
include *"a member with the feature switched off is indistinguishable from any other
refusal"*, *"there is no transition from submitted straight to a conversation"*, *"the
guardian is required in the conversation under every configuration"*, and *"a declined
sender is told nothing beyond the fact that it ended"*.

**`ModerationPolicyTest`** covers triage targets, initial escalation per category, who may
take which action, *"an appeal cannot be reviewed by the moderator who took the original
action"*, *"unsend preserves the original for the safety team"*, and *"a pattern of
retaliatory reports is flagged, not silently acted on"*.

**`ContentSignalsTest`** includes *"flirtation carries more weight in a cross-gender thread
than in a same-gender one"* and *"every signal is advisory and explains itself"* — the
latter asserting that each `SafetySignal` carries the phrase that triggered it, so a
moderator can dismiss a keyword match rather than being handed an uninterrogable score.

**`VisibilityPolicyTest`**, **`TrustPolicyTest`** and **`VerificationPolicyTest`** cover what
a viewer may see, the fact that no public trust label carries a rankable number, and that a
member cannot make themselves a scholar, moderator, or administrator.

**`ProductPrincipleTest`** is unusual. It asserts on the structure of the domain: that no
publicly visible type carries a field whose name looks like a vanity metric, that the
private `TrustRecord` never leaves as a number, that `ContentSignals.DISCLOSURE` still says
automation never restricts an account, and that `Viewer` has no field a future change could
start weighting results by. Its own comment explains why it exists: the failure mode this
product most needs to defend against is not a bug but drift, and an engineer who genuinely
needs a follower count will have to delete a test that says why it should not exist.

### `:core:data` — end to end

These run through the real use cases against seeded data, using `Harness` from
`core/data/src/test/.../TestHarness.kt`: a fully wired `CoreGraph` with a `FixedClock` the
test can move and a `SequentialIdGenerator` so identifiers are readable in failures.

This matters. A rule enforced only in a policy object is a rule a future screen can bypass by
talking to a repository directly. These tests demonstrate the rules survive the whole call
path from command to store.

#### `CriticalFlowsTest` — the ten must-not-fail flows

`core/data/src/test/kotlin/org/fisabilillah/core/data/CriticalFlowsTest.kt`, 25 tests
organised around the ten flows the product specification names.

| # | Flow | What the tests actually assert |
| --- | --- | --- |
| 1 | **A blocked user cannot contact the blocker** | After Khadija blocks Abdullah, his attempt returns `RefusalCode.BLOCKED` with the message "This member cannot be contacted." and *no conversation row is created*. A second test proves blocking also ends threads that already exist, and that the blocked party cannot post to them. |
| 2 | **Cross-gender restrictions cannot be bypassed** | Abdullah is refused by Khadija (sisters only); Aminah, sending the identical request, succeeds — which shows the refusal was about the safeguard rather than the listing or the wording. A second test proves a guardian-guided member gets her wali into the thread automatically, as a member, from the first message. |
| 3 | **No conversation without a valid purpose** | A too-short reason and a missing subject both return field-addressed `Invalid` outcomes and create nothing. A separate test sends an opening that reads as a chat-up line and asserts the refusal both mentions "service purpose" and points at the guardian-led route rather than simply saying no. |
| 4 | **Wali contact details are never exposed** | Before forwarding, the sender is not even told a guardian exists (`NotFound`). After forwarding they receive a name and a relationship — and the test asserts the serialised result contains no email fragment, no phone fragment, and none of the owner's private note. The access itself writes a `GUARDIAN_CONTACT_ACCESSED` audit entry. |
| 5 | **Introductions follow the recipient's configuration** | With `recipientReviewsFirst = true` the request stops at `AWAITING_RECIPIENT` and the guardian is *not* notified. Flipped to false, an identical submission lands at `FORWARDED_TO_GUARDIAN` and the guardian is. Two further tests cover a member with the feature off, and permanent closure blocking both a second introduction and an ordinary conversation. A fifth proves only the guardian can open the conversation, that the sender and the recipient are both refused, and that the resulting thread has exactly three members. |
| 6 | **Every moderation action writes an audit record** | A `FEATURE_RESTRICTED` action grows the audit log, records `RESTRICTION_IMPOSED`, names the acting administrator, carries the rationale text, and the restriction actually takes effect. A structural test asserts `AuditLogRepository` exposes `append` and no method beginning with `update` or `delete`, and that `ModerationRepository` has `appendAction` but no `updateAction` or `deleteAction`. Two more prove an ordinary member cannot moderate at all and a plain moderator cannot impose a permanent ban. |
| 7 | **Organisations are isolated** | No cross-organisation administration exists in the fixture, and an organisation-scoped search returns only that organisation's listings. |
| 8 | **Nobody can self-assign verification or scholar status** | A client submits a profile claiming `SCHOLAR`, `MODERATOR`, `PLATFORM_ADMINISTRATOR`, `BACKGROUND_CHECKED` and a qualification attestation. What is saved keeps only `VOLUNTEER`, sets `EMAIL_VERIFIED`, empties the attestations — and writes an audit entry recording the attempt. A companion test proves onboarding requires the mandatory consents and an adult declaration. |
| 9 | **A suspended account cannot continue a conversation** | After suspension the sender is refused in a thread they were already in, while the other party continues normally and the history stays intact for review. A second test proves a frozen conversation accepts nothing from anyone. |
| 10 | **Exact addresses stay private** | Browsing shows "Eastgate, Ashbourne" and no street address, and the request's anonymous visibility hides the requester's name. After the requester discloses to one named volunteer, that volunteer sees "14 Eastgate Rise" and everybody else still sees the neighbourhood. An `EXACT_LOCATION_DISCLOSED` audit entry is written. A second test proves only the requester can release their own address. |

Four supporting invariants round it out: unsending hides a message from participants while
the preserved original still reaches a later report; a critical report opens a case
immediately at `SAFETY_ADMINISTRATOR` escalation rather than waiting for triage; a profile
image is hidden from the opposite gender when the subject chose that; and the ten flows are
exercised through the same use cases the Android app calls.

#### The other `:core:data` classes

- **`SafeguardFloorScopeTest`** is small and important. It proves a community floor applies
  inside that community's space (the youth programme refuses an identity-verified but not
  background-checked volunteer), does *not* follow the same volunteers into unrelated
  conversations (the same two accounts succeed about a masjid clean-up), and that an
  organisation floor travels with its members everywhere.
- **`MessagingBehaviourTest`** covers quiet hours stopping new conversations but not
  existing ones, the rate limit end to end, oversight being announced in the thread rather
  than added silently, either participant ending a thread, and a non-member being unable to
  post.
- **`DiscoveryBehaviourTest`** covers search being ordered by name rather than anything
  gameable, a non-discoverable member disappearing from results, background checks being
  required to apply to work with young people, a sisters-only activity refusing a brother, a
  same-gender-only teacher being enforced rather than displayed, a youth listing being
  unpublishable without safeguarding, the home digest containing nothing about other
  people's standing, and a commitment counting only once the organiser confirms it.
- **`SeedDataTest`** guards the fixture itself: every scenario the specification names is
  present, no sample record contains a routable email address or phone number, a peer-led
  class is labelled as peer learning, the verified campaign still cannot take a payment,
  every seeded member has safeguards from the moment their account exists, every preset is
  represented, every listing states its completion criteria, and exactly one account is
  presented as a verified scholar.

---

## The database suite

```console
$ backend/supabase/run_local_tests.sh
```

Requires PostgreSQL 16 locally. The script:

1. Starts the cluster if it is down.
2. Drops and recreates a throwaway database (`fisabilillah_test` by default).
3. Applies `tests/00_bootstrap.sql`, the local-only `auth.*` shim that recreates on plain
   PostgreSQL what Supabase provides. **Never apply this to a Supabase project.**
4. Applies all fifteen migrations in filename order, printing `ok` or `failed` per file.
5. Applies `seed/seed.sql`.
6. Runs `tests/rls_tests.sql` and counts `PASS:` and `FAIL:` lines.

It exits non-zero on any migration, seed, or assertion failure, and it has been run against a
real PostgreSQL 16:

```
PASS: schema summary -- 58 tables, 162 policies
---------------------------------------------------------------
RESULT: PASS  (100 assertions passed)
```

These are authorisation tests, not schema tests: they set a session's `auth.uid()` and check
what that user can and cannot select, insert, update and delete. `0013_row_level_security.sql`
ends with an assertion that fails the migration itself if any table in `public` has RLS off,
and the suite re-asserts it.

`KEEP_DB=1` leaves the database for inspection. `PGDATABASE_TEST`, `PG_SUPERUSER` and
`PG_BIN` override the obvious things.

The coverage map is in [`backend/docs/rls-model.md`](../backend/docs/rls-model.md) §"Test
coverage". The rule when adding a policy is in
[`backend/README.md`](../backend/README.md): pair every negative assertion with a positive
control, because a policy that denies *everyone* otherwise passes by accident.

---

## Continuous integration

`.github/workflows/ci.yml`, three jobs, on every push and pull request:

| Job | What it does |
| --- | --- |
| **Shared core (JVM)** | JDK 21, `./gradlew build test`, uploads the HTML reports. Needs no SDK, no emulator, and no Google Maven access — which is the whole point of keeping the core Android-free. |
| **Database schema and row-level security** | A `postgres:16` service container, `psql` installed, `backend/supabase/run_local_tests.sh`. |
| **Android client** | Sets up the Android SDK and runs `assembleDebug testDebugUnitTest` in `androidApp/`. The workflow states in a comment that this job is expected to need fixing on its first successful run. |

---

## What is not covered

State this plainly to anyone picking the project up.

**The Android module has no test run at all.** It has never been compiled. `androidApp/app`
declares JUnit 5 and `kotlinx-coroutines-test` for unit tests and Espresso plus
`compose-ui-test-junit4` for instrumentation, but there are no test sources yet and no
evidence any of it works. The first task is a successful `assembleDebug`; the second is a
view-model test for at least the compose-conversation and safeguard-settings screens, which
are where a UI bug could actually cost someone their privacy.

**No property-based or fuzz testing.** `ContactPolicy.evaluate` is a long ordered sequence
of checks with many interacting inputs. It is covered case by case and the cases are
well-chosen, but a generator over `UserSafeguards` and `SafeguardFloor` asserting that
`SafeguardResolver.effective` never returns anything looser than its input would be worth
more than the next ten hand-written tests.

**No concurrency testing.** `InMemoryStore` guards writes with a `Mutex`; nothing exercises
contention. The production store is PostgreSQL, so this matters less than it appears, but
the fixture is what the app runs on today.

**No performance or load testing anywhere.**

**Nothing tests the Supabase-backed repository implementations** because they do not exist
yet. When they do, the honest test is the `:core:data` suite re-run against them — the
interfaces are identical, so it should be a matter of substituting the graph.

**No accessibility testing.** The components set semantics deliberately
(`SectionHeader` announces as a heading, `InitialsAvatar` clears its semantics so a screen
reader does not read initials at a person), but nothing verifies it.

---

## Writing a new test

- A rule about **who may do what** belongs in `:core:policy` as a pure-function test. If you
  cannot write it there, the rule is probably in the wrong layer.
- A rule that must **survive the whole call path** belongs in `:core:data` using `Harness`.
- Anything **time-dependent** uses `FixedClock`; do not reach for `Clock.System`.
- Anything touching **row-level security** needs an assertion in
  `backend/supabase/tests/rls_tests.sql` as well, with a positive control.
- If you are adding a `ModerationActionType`, `ContactPurposeKind`, `ReportCategory` or
  `AudienceScope` value, add the case to the exhaustive `when` in the corresponding policy
  and a test for it. See [`CONTRIBUTING.md`](../CONTRIBUTING.md).
