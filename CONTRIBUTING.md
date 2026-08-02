# Contributing

Thank you for working on this. Most of what follows is ordinary; the parts that are not are
about where safety rules are allowed to live, and they are not negotiable.

Read the [root README](README.md) first, particularly the status section. The Android module
has never been compiled, the data layer is a development fixture, authentication is a
stand-in, and payments are off. Knowing that will save you an hour of confusion.

---

## Running the tests

### The shared core — always, before pushing anything

```console
$ ./gradlew build test
```

From the repository root. Needs a JDK 17 or newer and nothing else: no Android SDK, no
emulator, no access to Google's Maven repository. **162 tests currently pass** across
`:core:policy` (109) and `:core:data` (49).

Narrower runs while you work:

```console
$ ./gradlew :core:policy:test     # the decision layer, fast
$ ./gradlew :core:data:test       # end to end through the real use cases
```

HTML reports land in `core/policy/build/reports/tests/test/index.html` and
`core/data/build/reports/tests/test/index.html`.

### The backend row-level security tests — if you touched `backend/`

```console
$ backend/supabase/run_local_tests.sh
```

Needs PostgreSQL 16 locally. It creates a throwaway database, applies the local `auth.*`
shim, then every migration in filename order, then the seed, then **100 assertions** against
the policies. Non-zero exit on any failure.

`KEEP_DB=1` leaves the database behind for inspection. `PGDATABASE_TEST`, `PG_SUPERUSER` and
`PG_BIN` override the obvious things.

**Never apply `backend/supabase/tests/00_bootstrap.sql` to a Supabase project.** It is a
local-only shim.

### The Android client — if you have the SDK

```console
$ cd androidApp && ./gradlew assembleDebug
```

Open `androidApp/` in Android Studio, not the repository root. Expect compile errors on a
first build; see [`docs/local-setup.md`](docs/local-setup.md).

---

## Where safety rules must live

**Every rule about who may do what to whom lives in `core/policy/`. Never in a screen. Never
in a repository.**

This is the single most important convention in the repository, and it is worth being
explicit about why, because the alternatives all sound reasonable in the moment.

**Not in a screen.** A rule enforced in a Compose function is enforced on exactly one path
through the product. A second screen, a deep link, a notification tap, or the future iOS
client all bypass it, and nothing tells you they have. The Android module cannot even be
compiled in the environments where the safety tests run, so a rule that lives there is a rule
CI cannot check.

**Not in a repository.** `Repositories.kt` says it in a comment: *a repository is a store, not
a gatekeeper*. None of the repository interfaces takes a "current user" for authorisation
purposes, deliberately. If you add a filter inside a repository, two things happen: the rule
becomes invisible to the policy tests, and the next implementation of that interface — the
Supabase-backed one that does not exist yet — silently does not have it.

**Not in a view model.** Same reasoning as a screen, one layer down.

### The layers

| Layer | Contains | Must not contain |
| --- | --- | --- |
| `core/model` | Types, enums, derived properties, `init` validation | Any decision that depends on who is asking |
| `core/policy` | **Every safety decision.** Pure functions, no I/O, no coroutines | Repository access, clocks, anything that cannot be called from a test with hand-built inputs |
| `core/domain` | Repository ports; use cases that gather facts, call a policy, write state, and write an audit entry | Rules that could have been expressed as a pure function |
| `core/data` | The in-memory fixture and seed data | Anything the production path will not also do |
| `androidApp` | Rendering, navigation, input | Any decision at all |

The test for whether something is in the right place: **can you write a test for it in
`:core:policy` with no repositories and no clock?** If yes, it belongs there. If it needs
facts from several stores, the gathering belongs in a use case or in
`ContactContextAssembler`, and the decision still belongs in a policy.

### The pattern to follow

`ContactPolicy.evaluate` is the reference implementation. `ContactContextAssembler` touches
nine repositories to build a `ContactContext`; `ContactPolicy.evaluate` takes that context and
returns a decision; `StartConversationUseCase` acts on the decision. The decision function
performs no I/O and is therefore exhaustively testable, which is exactly why it can be trusted
with the thing the whole product rests on.

### And enforce it twice

Anything that protects a person is enforced in `core/policy` **and** in the database's
row-level security, independently. Neither is a substitute for the other. A bug in a use case
is caught by RLS; a client that talks to PostgREST directly still meets the policies. If you
add a rule in one place, add it in the other and add an assertion to
`backend/supabase/tests/rls_tests.sql`.

---

## Adding to an enum

Most of the enums in `core/model` are consumed by exhaustive `when` expressions, so the
compiler will point you at what needs a case. Two of them additionally need a decision from
you.

### A new `ModerationActionType` must decide its audit mapping

`ModerationPolicy.auditActionFor` maps every action type to exactly one `AuditAction`. The
mapping lives in one place *specifically so that no action type can be added without deciding
how it is logged* — the source says so.

The `when` has an `else` branch falling through to `AuditAction.MODERATION_ACTION`, so **the
compiler will not stop you.** Falling through by accident is the failure mode this convention
exists to prevent: an action that changes somebody's account status but is logged as a generic
"moderation action" is an action that is much harder to find later.

When you add a type, decide and record all four of:

| Decision | Where |
| --- | --- |
| Which `AuditAction` it maps to, explicitly | `ModerationPolicy.auditActionFor` |
| Whether it is reversible | `ModerationActionType.isReversible` — this drives whether the member is told they can appeal |
| Whether it needs a safety administrator | `ModerationActionType.requiresSeniorApproval` |
| What side effect it has, if any | `TakeModerationActionUseCase.applySideEffects` |

Then add a test in `ModerationPolicyTest` and a line to
[`docs/moderation-guide.md`](docs/moderation-guide.md), which is read by people who do not
read code.

### A new `ContactPurposeKind`

Decide `requiresSubject`, decide whether it is user-selectable, add the case to
`PurposeValidator.subjectMatchesKind`, and check whether it should be in any preset's
`declinedPurposes`.

### A new `ReportCategory`

Decide its severity and group, decide its initial escalation in
`ModerationPolicy.initialEscalation` (the `when` has an `else` — the same caution applies),
and add it to the tables in [`docs/moderation-guide.md`](docs/moderation-guide.md).

### A new `AudienceScope` or other ordered scale

Every ordered enum carries a `strictness` value and a `stricter(a, b)` companion function. A
new value must slot into the ordering correctly, and every `when` over the enum in
`ContactPolicy`, `VisibilityPolicy` and `IntroductionPolicy` needs a case. The compiler will
find them; deciding what each should do is yours.

---

## Review checklist

### Any change

- [ ] `./gradlew build test` passes.
- [ ] No new dependency was added to a `core` module without a reason in the pull request.
      **No Android or Google Maven dependency, ever** — it would break the property that the
      safety tests run anywhere a JDK does.
- [ ] British English in user-facing strings and in comments.
- [ ] A comment explains *why*, not *what*. The existing code sets a high bar here; match it.

### Changes touching safeguards

- [ ] Combining a member's settings with any floor still **only ever tightens**. If you added
      a field to `SafeguardFloor`, `SafeguardResolver.apply` handles it by taking the stricter
      value, and there is a test.
- [ ] The new field is reflected in `isAtLeastAsStrict` and in `loosenedFields`, so a member
      is told plainly when a change opens something up.
- [ ] You have decided whether the field belongs on an **organisation** floor (which travels
      with a member everywhere) or a **community** floor (which applies only inside that
      space), and the reasoning survives the youth-programme test in
      [`docs/safeguards.md`](docs/safeguards.md).
- [ ] No preset has been made to look more or less religious than another. Presets are
      circumstances, not rankings, and a test asserts it.
- [ ] Every new `DenialReason` has a `userFacingMessage` that is **no more specific than it
      needs to be** about the recipient's settings, and an `auditReason` that is precise.
      Compare against the existing ones: four different situations deliberately share two
      messages.
- [ ] [`docs/safeguards.md`](docs/safeguards.md) has been updated. A safeguarding lead reads
      it, and it must stay true.

### Changes touching the wali workflow

- [ ] Guardian contact details still cross the boundary **only** through
      `TrustedContact.redacted()`. If you wrote a second path, that is the change under
      review, and it needs to be argued for explicitly.
- [ ] The only route into a conversation is still `FORWARDED_TO_GUARDIAN` → the guardian
      opening it. No new transition creates a two-person thread.
- [ ] `IntroductionPolicy.conversationEligibility` still returns `guardianRequired = true`
      unconditionally.
- [ ] Every new transition is in `allowedTransitions`, and the diagram in
      [`docs/wali-workflow.md`](docs/wali-workflow.md) has been updated to match.
- [ ] Refusals about the recipient still all produce the same sentence. A sender must not be
      able to distinguish "switched off" from "blocked you" from "you are not verified
      enough".
- [ ] `senderFacingOutcome` still tells a sender nothing about who decided or why.
- [ ] Rate limits are unchanged, or the change is argued for: two open, four per thirty days,
      one per recipient ever, twenty-one days to lapse.
- [ ] Nothing new is browsable, searchable, filterable or rankable.

### Changes touching moderation

- [ ] Every consequential action still writes exactly one audit entry, and the entry is
      written **before** the state change.
- [ ] No update or delete path has appeared on `AuditLogRepository` or on moderation actions,
      evidence, or message redactions — in the interfaces or in the SQL. A test asserts this
      structurally; if you had to change that test, stop and reconsider.
- [ ] The original decision-maker still cannot hear the appeal.
- [ ] A new action type has decided its audit mapping (see above).
- [ ] Unsend still preserves the original in the same operation that hides it.
- [ ] Any new automated signal is **advisory**, carries the reason it fired, and cannot by
      itself restrict an account, remove content, or close a case.
- [ ] If you changed what `ContentSignals` looks at, `ContentSignals.DISCLOSURE` and
      `ConsentKind.AUTOMATED_SAFETY_PROCESSING` have been updated to match. Those strings are
      a promise to members and they sit next to the code specifically so the two cannot drift
      apart.
- [ ] [`docs/moderation-guide.md`](docs/moderation-guide.md) has been updated.

### Changes touching privacy

- [ ] Exact locations still leave through `DiscloseExactLocationUseCase` and nowhere else,
      still require the requester to act, still name the recipient, and still write an audit
      entry.
- [ ] No new field on a publicly visible type could be ranked, sorted, or compared. If
      `ProductPrincipleTest` fails, the test is right and the field is wrong.
- [ ] The private impact record and the private trust record are still private.

### Changes touching giving

- [ ] `DonationFeatureFlags.paymentsEnabled` is still `false`. Turning it on is not a code
      change alone — see [`docs/payment-compliance.md`](docs/payment-compliance.md).
- [ ] Zakat eligibility is still only ever recorded as an attestation from a named qualified
      body, never asserted by the platform.

---

## Style

- **British English** throughout: organisation, recognise, behaviour, apologise. The domain
  language follows suit — "masjid" not "mosque", "wali" not "guardian" where the specific
  role is meant, "brother" and "sister" as the display names for gender.
- **Calm and plain.** No marketing voice, no exclamation marks, no emoji, in code or in
  documentation.
- **Comments explain reasoning.** The most valuable comments in this codebase say why a
  decision was made and what would go wrong otherwise — read the header of `ContactPolicy`, of
  `ContentSignals`, or of `IntroductionForm`. A comment restating the code is worse than none.
- **User-facing strings say what a person can do next**, where there is something they can do.
  Compare the two verification refusals: one tells the sender how to raise their own
  verification level, the other deliberately says nothing about the recipient.
- **Do not extract for its own sake.** `ContactPolicy.evaluate` is one long ordered function
  on purpose, because a reviewer needs to read it top to bottom and satisfy themselves that
  nothing was missed. Cleverness there is a cost, not a saving.

---

## Documentation

Some of these documents are read by people who do not write software. If you change what the
software does, change them in the same pull request.

| If you changed | Update |
| --- | --- |
| Safeguards, presets, floors, the contact gate | [`docs/safeguards.md`](docs/safeguards.md) |
| The introduction workflow or its state machine | [`docs/wali-workflow.md`](docs/wali-workflow.md) |
| Report categories, escalation, moderator authority, appeals | [`docs/moderation-guide.md`](docs/moderation-guide.md) |
| What is collected, retained, exported or deleted | [`docs/privacy-model.md`](docs/privacy-model.md) |
| The age gate or background-check enforcement | [`docs/child-safety.md`](docs/child-safety.md) |
| Anything about payments | [`docs/payment-compliance.md`](docs/payment-compliance.md) |
| Module boundaries or the build split | [`docs/architecture.md`](docs/architecture.md) |
| Test counts or coverage | [`docs/testing.md`](docs/testing.md) and the README |
| Tables, policies, or helper functions | `backend/docs/*` — see [`backend/README.md`](backend/README.md) |

---

## Things that need a conversation before a pull request

Not prohibited. But each of these removes a property the product was built around, and a pull
request is the wrong place to have the argument for the first time.

- Anything that produces a public count, ranking, score, or leaderboard.
- Anything that makes members browsable by appearance, or by anything other than what they can
  do.
- Any second path by which a conversation comes into existence.
- Any way for two people to reach a private thread through the introduction workflow.
- Any weakening of the append-only property of the audit trail.
- Any automated decision that acts without a person.
- Any notification whose purpose is to bring somebody back into the app rather than to tell
  them something they need to know.
- Removing a test in `ProductPrincipleTest`. Each one exists to stop a specific, reasonable-
  sounding change, and the comment at the top of that file explains why deleting one is
  supposed to be difficult.
