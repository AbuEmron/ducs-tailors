# Fi Sabilillah

A Muslim service-oriented community platform: volunteering, Islamic learning, mutual aid,
community projects, purpose-bound messaging, and a wali-centred marriage-introduction
workflow.

**This is a place to serve, learn, build, and support one another for the sake of Allah —
not a place to seek attention, privately pursue people, or exploit vulnerability.**

It is deliberately not a social network and deliberately not a dating app. The constraint
is structural rather than cultural: there is no code path that produces a conversation
without a stated purpose, no field on any publicly visible type that could be ranked, and
no route from "interested in someone" to "private chat with them" that does not pass
through a guardian.

---

## The ten product principles

| # | Principle | Where it lives in the code |
| --- | --- | --- |
| 1 | **Service before self-presentation.** A profile answers one question: can this person help, or be helped, with this particular thing. There is no field for appearance, no follower count, no popularity score. | `core/model/src/main/kotlin/org/fisabilillah/core/model/Profile.kt` |
| 2 | **Every conversation has a stated purpose.** A blank "salam" cannot be sent to a stranger. The sender says what they want, about what, for how long, and who else should be present. | `ContactPurpose`, `ContactPurposeKind`, `PurposeValidator` |
| 3 | **Boundaries belong to the person who set them.** A member's settings can be combined with an organisation's or a community's floor, and combining can only ever tighten. | `core/policy/.../SafeguardResolver.kt` |
| 4 | **Marriage is a family matter, not a marketplace.** No browsing, no swiping, no availability badge, no ranking, no visible outcome. One structured request, and only a guardian can open a conversation. | `core/policy/.../IntroductionPolicy.kt` |
| 5 | **A guardian's contact details never leave their owner.** A counterparty learns that a guardian exists, their name, and how they prefer to be approached. Never an email address or a phone number. | `TrustedContact.redacted()`, `RedactedTrustedContact` |
| 6 | **Trust is granted, never claimed.** Scholar, teacher, moderator and verification status cannot be self-assigned; a client that asks for them is silently reduced and the attempt is logged. | `AccountRole.selfAssignable`, `VerificationPolicy.sanitizeRoleRequest` |
| 7 | **Automation flags; people decide.** Message checks are advisory, explainable, and run on the device. Nothing they produce removes content, restricts an account, or closes a case. | `core/policy/.../ContentSignals.kt`, `SafetySignal.isAdvisoryOnly` |
| 8 | **Moderators are accountable.** Every consequential action writes an audit record. There is no update or delete path for that record anywhere — including for platform administrators. | `AuditLogRepository`, `ModerationPolicy.auditActionFor` |
| 9 | **Need is not content.** A person asking for food or a lift home keeps their name and their street address until they decide otherwise, one named recipient at a time. | `ServiceRequest`, `DiscloseExactLocationUseCase` |
| 10 | **A record of service is between a person and their Lord.** Impact summaries are private by default and are never aggregated, ranked, or compared. | `PrivateImpactRecord.sincerityNote` |

---

## What this deliberately does not have

- **No infinite scroll.** Pagination is cursor-based and finite (`Page` in `core/model/.../Common.kt`).
- **No follower counts** and no "following" of anybody.
- **No public popularity rankings.** `TrustPolicy` exposes a handful of labels and has no
  method anywhere that returns a number, a percentage, or anything sortable.
- **No streaks** and no notification whose purpose is to bring someone back into the app.
  Every entry in `NotificationKind` corresponds to something the person actually needs to
  know: a commitment they made, a reply they are waiting for, a safety matter.
- **No swipe interface.** The introduction workflow has one entry point: a long structured
  form that a serious person completes once.
- **No public like counts** and no reactions of any kind.
- **No appearance-based discovery.** `PeopleSearchCriteria` filters on skills, categories,
  languages, city, availability and verification. There is nothing else to filter on.
- **No public good-deed leaderboards.** `ImpactVisibility` has exactly two values: private,
  or selected projects on a profile — with no numbers.

A test suite guards these: `core/policy/src/test/.../ProductPrincipleTest.kt` asserts on the
*shape* of the domain, so adding a follower count means deleting a test that explains why it
should not exist.

---

## Repository layout

```
.
├── core/                      Shared platform core — pure Kotlin/JVM, no Android
│   ├── model/                 Domain types: profiles, safeguards, messaging, wali, moderation
│   ├── policy/                Every safety decision: contact gate, introductions, visibility
│   ├── domain/                Repository ports and use cases; the only writers of state
│   └── data/                  In-memory development fixture, seed data, composition root
├── androidApp/                Android client — a separate Gradle build (Compose, Material 3)
│   └── app/src/main/java/org/fisabilillah/app/
│                              di/ · ui/theme · ui/components · ui/navigation · ui/viewmodel · ui/screens
├── backend/                   PostgreSQL / Supabase schema, RLS policies and SQL tests
│   ├── supabase/migrations/   0001 … 0015, applied in filename order
│   ├── supabase/tests/        100 assertion-based authorisation tests
│   └── docs/                  data-model.md · rls-model.md · threat-model.md
├── docs/                      Architecture, operations, and the safety documentation set
├── gradle/libs.versions.toml  Single version catalogue, shared by both Gradle builds
└── .github/workflows/ci.yml   Three jobs: shared core, database, Android client
```

---

## Quick start

There are **two Gradle builds**, and which one you open matters.

### The shared core (any machine with a JDK 17 or newer)

```console
$ ./gradlew build test
```

No Android SDK, no emulator, no access to Google's Maven repository. This is the build that
contains every safety-critical decision, and it is the one to run before pushing anything.

### The Android client

```console
$ cd androidApp
$ ./gradlew assembleDebug
```

**Open `androidApp/` in Android Studio, not the repository root.** The root is a plain
Kotlin/JVM build and Android Studio will not recognise it as an Android project. The
`androidApp` build includes the root as a composite build and substitutes the
`org.fisabilillah:core-*` coordinates for the sibling projects, so a change in `core/` is
picked up immediately without publishing anything.

### The database

```console
$ backend/supabase/run_local_tests.sh
```

Requires a local PostgreSQL 16. See [`backend/README.md`](backend/README.md).

Full instructions, including what to do when the Android build fails on first run, are in
[`docs/local-setup.md`](docs/local-setup.md).

---

## The test story

Verified by running `./gradlew test` from the repository root:

| Module | Tests | What it covers |
| --- | --- | --- |
| `:core:policy` | 109 | The contact gate, safeguard resolution, visibility, the introduction state machine, moderation authority, content signals, verification, and the product-principle guards |
| `:core:data` | 53 | The same rules exercised end to end through the real use cases against seeded data |
| **Total** | **162** | all passing |

Before the product-principle suite was added the figure was 148; the ten additional tests
assert on the shape of the domain rather than on behaviour.

The centrepiece is `core/data/src/test/kotlin/org/fisabilillah/core/data/CriticalFlowsTest.kt`,
organised around the ten flows the product specification names as must-not-fail:

1. A blocked user cannot contact the blocker — and blocking ends conversations that already exist.
2. Opposite-gender messaging restrictions cannot be bypassed.
3. A conversation cannot be created without a valid purpose.
4. A member cannot obtain another person's wali contact details.
5. An introduction follows the recipient's configuration exactly.
6. Every moderation action writes an audit record.
7. An organisation administrator cannot reach another organisation's records.
8. Nobody can give themselves verification or scholar status.
9. A suspended account cannot continue an existing conversation.
10. An exact address stays private until the requester releases it.

Separately, `backend/supabase/run_local_tests.sh` applies all fifteen migrations to a
throwaway database on a real PostgreSQL 16 cluster, loads the seed, and runs **100
assertions** against the row-level security policies. It has been run and passes.

More detail in [`docs/testing.md`](docs/testing.md).

---

## Status and limitations

This section is deliberately near the top of the document rather than at the bottom. Read
it before forming an impression of how finished this is.

### The Android module has not been compiled

The Compose UI was written in an environment where `dl.google.com` is blocked by egress
policy, so the Android SDK, the Android Gradle Plugin, and the AndroidX and Compose
artifacts could not be downloaded. The shared core was fully built and tested and the
database layer was fully applied and tested; **the Android module was never compiled even
once.**

Expect compile errors on the first real build — missing imports, signature mismatches
between screens and their view models, and Compose API drift. Fixing them is the first task
of the next phase, and it is a mechanical one: the logic those screens call is already
tested.

### The data layer is a development fixture

The app today is backed by the in-memory repositories in
`core/data/src/main/kotlin/org/fisabilillah/core/data/InMemoryRepositories.kt`, seeded from
`SeedData`. Nothing persists across a process restart, and none of the row-level security
guarantees in `backend/supabase/migrations/0013_row_level_security.sql` apply to it.

The production path is the Supabase/Postgres schema in `backend/`, behind the same
repository interfaces declared in `core/domain/.../Repositories.kt`. Swapping it in changes
no use case, no view model, and no screen.

### Authentication is a development stand-in

`SessionManager.signInAs` in `androidApp/app/src/main/java/org/fisabilillah/app/di/AppGraph.kt`
selects one of the seeded accounts. Real authentication (Supabase Auth) is not wired up.

### Payments are disabled

`DonationFeatureFlags.paymentsEnabled` is `false` and `Campaign.canAcceptDonations` reads it
directly. The data model and the giving UX exist; no money can move. The checklist that must
be complete before the flag is flipped is in
[`docs/payment-compliance.md`](docs/payment-compliance.md).

### Screen copy is inline

Strings are written in the Compose sources rather than extracted to `strings.xml`. This is a
deliberate first-release trade-off, recorded in `androidApp/app/src/main/res/values/strings.xml`
itself, and it is the first thing to change before any localisation work.

### There is no image loading library

No Coil, no Glide, nothing. Profile photographs therefore fall back to initials everywhere:
`InitialsAvatar` in `androidApp/app/src/main/java/org/fisabilillah/app/ui/components/Components.kt`
is the default representation of a person throughout the app.

---

## Documentation

### Engineering

| Document | What it is for |
| --- | --- |
| [`docs/architecture.md`](docs/architecture.md) | The two-build split, module boundaries, and why the core is Android-free |
| [`docs/local-setup.md`](docs/local-setup.md) | Getting all three parts running on a fresh machine |
| [`docs/testing.md`](docs/testing.md) | What is tested, how to run it, and what is not covered |
| [`docs/deployment.md`](docs/deployment.md) | Migrations, secrets, release checklist |
| [`docs/native-roadmap.md`](docs/native-roadmap.md) | What it takes to add an iOS client |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Where safety rules must live, and the review checklist |

### Safety and policy — written to be readable without an engineering background

| Document | What it is for |
| --- | --- |
| [`docs/safeguards.md`](docs/safeguards.md) | How a member's boundaries work, and how organisations can tighten them |
| [`docs/wali-workflow.md`](docs/wali-workflow.md) | The guardian-led introduction process, end to end |
| [`docs/moderation-guide.md`](docs/moderation-guide.md) | The handbook for a human moderator |
| [`docs/privacy-model.md`](docs/privacy-model.md) | What is collected, why, and what deletion does and does not remove |
| [`docs/child-safety.md`](docs/child-safety.md) | The adults-only position and what must exist before youth functionality ships |
| [`docs/payment-compliance.md`](docs/payment-compliance.md) | The checklist that must be complete before payments are enabled |

### Database

| Document | What it is for |
| --- | --- |
| [`backend/README.md`](backend/README.md) | Running the schema and its tests |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Tables, enums, relationships, helper functions |
| [`backend/docs/rls-model.md`](backend/docs/rls-model.md) | Who may read and write every table, and why |
| [`backend/docs/threat-model.md`](backend/docs/threat-model.md) | Assets, actors, attack paths, mitigations, residual risk |

---

## A note on religious matters

The platform issues no rulings. Arrangements around a wali, an intermediary, and marriage
differ by school and by circumstance, and the app says so wherever the subject arises —
`IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE` is the exact wording. Zakat eligibility is
recorded as an attestation from a named qualified body, never asserted by the platform.
Verification confirms documents, not character, and every badge carries a plain statement of
what it does not mean.
