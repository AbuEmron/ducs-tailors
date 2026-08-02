# Implementation matrix

*Every feature the product specification names, mapped to the screen that shows it, the
component that implements it, the roles it applies to, the data entity behind it, the
permission or safeguard rule that governs it, the test that proves it, and an honest
completion status.*

The specification requires this matrix to exist before any visual polish begins, and
requires it to confirm that no original feature has been lost. **It does not confirm that.**

Of 304 rows: 157 are **Implemented and tested**, 107 are **Implemented, untested** (62 of
those are Android screens that have never compiled), 21 are **Model and UI only**, 3 are
**Behind feature flag**, and **16 are Not implemented**. The 16 are collected in [What is
genuinely missing](#what-is-genuinely-missing) rather than scattered where they are easy to
miss, and most of them are marked **Deliberate** — a decision recorded, not a gap left.

| Status | §1–17 features | §18 screens | Total |
| --- | --- | --- | --- |
| Implemented and tested | 157 | 0 | **157** |
| Implemented, untested | 45 | 62 | **107** |
| Model and UI only (no transport/provider) | 21 | 0 | **21** |
| Behind feature flag | 3 | 0 | **3** |
| Not implemented | 12 | 4 | **16** |
| **Total** | **238** | **66** | **304** |

These counts are produced by reading the tables below, not by hand.

---

## How to read this

`Status` is one of exactly five values:

| Status | Means |
| --- | --- |
| **Implemented and tested** | The code exists and a named test in the suite exercises it. |
| **Implemented, untested** | The code exists. No test names it. It may work; nothing proves it does. |
| **Model and UI only (no transport/provider)** | Types, rules and interface exist. The external system that would make it real does not. |
| **Behind feature flag** | Deliberately switched off in code, with a documented checklist before it can be switched on. |
| **Not implemented** | Nothing in the repository does this. |

Two things every row shares and neither column repeats:

- **Every Android screen is unverified.** The app compiles — GitHub Actions builds a debug
  APK on every push — but compiling is not running, and no test, instrumented or otherwise,
  opens a single screen. See [What is claimed but
  unverified](#what-is-claimed-but-unverified).
- **Every database permission is enforced twice** — once in `:core:policy` and again by
  row-level security in `backend/supabase/migrations/0013_row_level_security.sql`. The
  `Permission / safeguard rule` column names the policy-layer rule; the RLS equivalent is in
  [`backend/docs/rls-model.md`](../backend/docs/rls-model.md).

### The suites this matrix cites

| Suite | Command | Result |
| --- | --- | --- |
| Shared core, JVM | `./gradlew test` | **303 tests, all passing** — `:core:policy` 149, `:core:data` 128, `:core:auth` 26 |
| Database, row-level security | `backend/supabase/run_local_tests.sh` | **130 assertions passed**, on a throwaway PostgreSQL 16 cluster with the whole migration set applied |
| Android instrumentation or unit | — | **none; the module has never compiled locally** |

The Kotlin figures are read from `core/*/build/test-results/test/*.xml`; the database figure
is the runner's own summary line. The Android app is compiled on GitHub Actions — a green
APK build proves it compiles, and nothing more, because the module has no tests of its own.
[`docs/testing.md`](testing.md) carries the same figures, broken down per test class.

---

## 1. Core user roles

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Twelve account roles, each with a stated authority level | `OnboardingProfileScreen`, `ProfileScreen` | `AccountRole` (`core/model/.../Roles.kt`) | all | `user_roles` | `AccountRole.selfAssignable` | `roles that carry authority or religious standing cannot be self-assigned` (`ProductPrincipleTest`) | Implemented and tested |
| Self-assignable roles limited to member, learner, volunteer, organiser | `OnboardingProfileScreen` | `VerificationPolicy.sanitizeRoleRequest` | all | `user_roles` | Client role request is filtered, then rejected again by `trg_user_roles_no_escalation` | `a member cannot make themselves a scholar, moderator, or administrator` (`VerificationPolicyTest`); `8. a member cannot give themselves verification or scholar status` (`CriticalFlowsTest`) | Implemented and tested |
| Teacher / scholar roles require qualification review | `ProfileScreen`, `LearningDetailScreen` | `VerificationPolicy.teachingCapacityState` | teacher, scholar | `qualifications` | `TeachingCapacity.requiresQualificationReview`; badge reads "(claimed, not yet verified)" until reviewed | `a claimed teaching capacity is labelled as unverified until it is reviewed` (`VerificationPolicyTest`) | Implemented and tested |
| Seven teaching capacities, each with its own disclaimer | `LearningDetailScreen`, `MemberProfileScreen` | `TeachingCapacity` | teacher, scholar, peer helper | `profiles.teaching_capacity` | Every value carries a non-empty `disclaimer` | `every teaching capacity states its own limits` (`ProductPrincipleTest`) | Implemented and tested |
| Organisation accounts (masjid, charity) | `OrganizationDetailScreen` | `Organization`, `OrganizationRole` | charity, masjid | `organizations`, `organization_members` | `OrganizationRole.canManageMembers` / `canPublishListings` / `canSeePrivateRequestDetail`; roster never crosses an org boundary | `7. an organisation administrator cannot reach another organisation's records` (`CriticalFlowsTest`) | Implemented and tested |
| Moderator and safety-administrator roles | `ModeratorDashboardScreen`, `AdminDashboardScreen` | `AccountRole.isStaff`, `Principal.isModerator` / `isSafetyAdmin` | moderator, safety admin | `user_roles` | `ModerationPolicy.isAuthorized` | `an ordinary member cannot take any moderation action` (`ModerationPolicyTest`) | Implemented and tested |
| Wali / guardian contact role | `WaliSettingsScreen`, `TrustedContactsScreen` | `AccountRole.WALI_CONTACT`, `TrustedContactRole` | wali contact | `trusted_contacts`, `wali_profiles` | Not self-assignable; `Attestation.WALI_CONTACT_VERIFIED` | `an intermediary is only accepted when the member allowed one` (`IntroductionPolicyTest`) | Implemented and tested |
| Role grant / revoke by an administrator | `RoleAdministrationScreen` | `ManageRolesUseCase` | platform admin | `ROLE_GRANTED`/`ROLE_REVOKED` audit | No self-service, no self-editing, last administrator protected | `AuthoringAndAdministrationTest` (4) | Implemented and tested |

## 2. Main application areas and navigation

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Six-tab primary navigation, none of them a browse surface | all | `PrimaryDestination` (`ui/navigation/Routes.kt`) | all | — | No "discover"/"explore" tab by design | — | Implemented, untested |
| 62 named routes, all wired to a composable | all | `Routes`, `FiSabilillahNavHost` | all | — | — | — | Implemented, untested |
| Home digest: own obligations, never other people's activity | `HomeScreen` | `HomeDigestUseCase`, `HomeDigest` | all | `commitments`, `volunteer_opportunities`, `service_requests` | No popularity ordering; no engagement section | `the home digest is about the member's own obligations, not other people's activity` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Daily safety reminder | `HomeScreen` | `SafetyReminders.forToday` | all | — | Rotates deterministically on epoch day | — | Implemented, untested |
| Deep links (`fisabilillah://…`) emitted with notifications | `NotificationsScreen` | `Notification.deepLink` | all | `notifications` | — | — | Implemented, untested |
| Cursor pagination, no infinite scroll | list screens | `Page<T>` (`core/model/.../Common.kt`) | all | — | Finite by construction | — | Implemented, untested |

## 3. User-controlled safeguards — every field on `UserSafeguards`

All fields live on `UserSafeguards` in
`core/model/src/main/kotlin/org/fisabilillah/core/model/Safeguards.kt`, are edited on
`SafeguardSettingsScreen` / `PrivacyControlsScreen` via `SafeguardViewModel` and
`UpdateSafeguardsUseCase`, and are stored in `user_safeguards` (self-only RLS, `FORCE`).
The table below names the field-specific rule and test.

| Feature (field) | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `presetName` | `OnboardingSafeguardsScreen`, `SafeguardSettingsScreen` | `SafeguardPresets.forName` | all | `user_safeguards.preset` | Six presets, none ranked above another | `no preset is presented as more religious than another`; `every preset produces a usable set of safeguards` (`SafeguardResolverTest`) | Implemented and tested |
| `profileDiscoverableBy` | `PrivacyControlsScreen` | `VisibilityPolicy.isDiscoverable` | all | `user_safeguards` | `AudienceScope` admits/refuses the viewer | `a member who is not discoverable does not appear to an ordinary viewer` (`VisibilityPolicyTest`); `a member who is not discoverable does not appear in search` (`DiscoveryBehaviourTest`) | Implemented and tested |
| `nameVisibility` | `PrivacyControlsScreen` | `VisibilityPolicy.visibleProfile` | all | `user_safeguards` | Four-level `NameVisibility` ladder | `a real name is withheld when the member shows a display name only`; `a real name reaches an organiser when the member allowed exactly that` (`VisibilityPolicyTest`) | Implemented and tested |
| `locationPrecision` | `PrivacyControlsScreen` | `VisibilityPolicy.locationLabel` | all | `user_safeguards` | Exact → neighbourhood → city → region → hidden | `location precision controls what a viewer is told` (`VisibilityPolicyTest`) | Implemented and tested |
| `profileImageStyle` | `EditProfileScreen` | `ProfileImageStyle` | all | `profiles.image_style` | Photograph never required | — | Implemented, untested |
| `profileImageVisibleTo` | `PrivacyControlsScreen` | `VisibilityPolicy.withoutImageDetail` | all | `user_safeguards` | `AudienceScope.excludesOppositeGender` downgrades to initials | `a photograph is not shown to the opposite gender when set to same gender only` (`VisibilityPolicyTest`); `a member's profile image is hidden from the opposite gender when they chose that` (`CriticalFlowsTest`) | Implemented and tested |
| `contactableBy` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate`, audience-scope branch | all | `user_safeguards` | `NOT_CONTACTABLE`, `CROSS_GENDER_CONTACT_CLOSED`, `OUTSIDE_ORGANIZATION` | `nobody means nobody`; `same-gender-only closes contact from the opposite gender`; `organisation-only contact needs a shared organisation` (`ContactPolicyTest`) | Implemented and tested |
| `minimumVerificationToContactMe` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate`, verification floor | all | `user_safeguards` | `VerificationLevel atLeast` | `an unverified member cannot reach someone requiring verification` (`ContactPolicyTest`) | Implemented and tested |
| `onlyMyOrganizationsMayContactMe` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate` | all | `user_safeguards` | `DenialReason.OUTSIDE_ORGANIZATION` | `organisation-only contact needs a shared organisation` (`ContactPolicyTest`) | Implemented and tested |
| `requireWrittenPurposeStatement` | `SafeguardSettingsScreen` | `ContactRequirement.WRITTEN_PURPOSE_REQUIRED` | all | `conversation_purposes` | Applied to the thread at creation | `3. a conversation cannot be created without a valid purpose` (`CriticalFlowsTest`) | Implemented and tested |
| `declinedPurposes` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate` | all | `user_safeguards` | `DenialReason.PURPOSE_DECLINED_BY_RECIPIENT` | `a purpose the recipient has declined is refused` (`ContactPolicyTest`) | Implemented and tested |
| `crossGenderStructure` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate`, cross-gender branch | all | `user_safeguards` | Four shapes: direct, group-only, third party, guardian | `group-context-only requires a shared community`; `a guardian-present setting forces a guardian into the thread` (`ContactPolicyTest`); `2b. a guardian-guided member gets a guardian in the thread automatically` (`CriticalFlowsTest`) | Implemented and tested |
| `moderatorPresence` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate` | all | `user_safeguards` | `NEVER` / `CROSS_GENDER_ONLY` / `ALWAYS` | `a moderator is required only for cross-gender threads when that rule is set` (`ContactPolicyTest`) | Implemented and tested |
| `requireGroupContext` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate` | all | `user_safeguards` | `DenialReason.GROUP_CONTEXT_REQUIRED` | `every field on SafeguardFloor is read by the resolver` (`SafeguardsAreEnforcedTest`) | Implemented and tested |
| `requireThirdParty` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate` | all | `user_safeguards` | Adds `OversightRequirement.THIRD_PARTY` | `every field on SafeguardFloor is read by the resolver` (`SafeguardsAreEnforcedTest`) | Implemented and tested |
| `guardianCopiedOnPurposes` | `WaliSettingsScreen` | `ContactPolicy.evaluate`; `StartConversationUseCase.resolveOversight` | all | `user_safeguards` | Guardian added at creation; refused if none available | `contact is refused rather than downgraded when no guardian is available` (`ContactPolicyTest`) | Implemented and tested |
| `voiceCallsAllowedFrom` | `SafeguardSettingsScreen` | `ContactPolicy.callsPermitted`; `LiveSessionPolicy.mediaPermissions` | all | `user_safeguards` | Recorded as `ContactRequirement.NO_VOICE_CALLS`; locks the microphone in a live room | `call and meeting terms are recorded on the thread` (`ContactPolicyTest`); `voice closed to the opposite gender still leaves the text chat open` (`LiveSessionPolicyTest`) | Implemented and tested |
| `videoCallsAllowedFrom` | `SafeguardSettingsScreen` | `ContactPolicy.callsPermitted`; `LiveSessionPolicy.mediaPermissions` | all | `user_safeguards` | `ContactRequirement.NO_VIDEO_CALLS`; locks the camera in a live room | `a camera closed to the opposite gender stays closed in a mixed class`; `neither the host nor a moderator can widen a participant's own setting` (`LiveSessionPolicyTest`) | Implemented and tested |
| `oneToOneMeetingsAllowedFrom` | `SafeguardSettingsScreen` | `SafeguardResolver`, `isAtLeastAsStrict` / `loosenedFields` | all | `user_safeguards` | Combined stricter-wins; reported when loosened | `loosening is reported field by field in plain language` (`SafeguardResolverTest`) | Implemented, untested *(no rule consumes this field at a decision point; it is stored, combined and reported, but nothing refuses a meeting)* |
| `meetingsMustBeInPublicPlaces` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate` | all | `user_safeguards` | `ContactRequirement.PUBLIC_MEETINGS_ONLY` on the thread | `call and meeting terms are recorded on the thread` (`ContactPolicyTest`) | Implemented and tested |
| `meetingsRequireThirdParty` | `SafeguardSettingsScreen` | `ContactPolicy.evaluate` | all | `user_safeguards` | `ContactRequirement.THIRD_PARTY_AT_MEETINGS` | `a member who asks for a third party at meetings has it recorded on the thread` (`SafeguardsAreEnforcedTest`) | Implemented and tested |
| `autoArchiveAfterCompletion` | `SafeguardSettingsScreen` | `StartConversationUseCase`, `Conversation.archiveAfter` | all | `conversations.archive_after` | Deadline set at creation | `a member who switched archiving off gets no deadline` (`SafeguardsAreEnforcedTest`) | Implemented and tested |
| `autoArchiveAfterDays` | `SafeguardSettingsScreen` | `StartConversationUseCase` | all | `conversations` | 1–365, validated in `init` | `the archive schedule a member chose is applied to the thread` (`SafeguardsAreEnforcedTest`) | Implemented and tested |
| Archiving actually happening on schedule | — | `RunScheduledMaintenanceUseCase` | all | `conversations` | Runs as the platform, not as a member | `a conversation past its archive deadline is archived, and only then` | Implemented and tested |
| `acceptFormalIntroductions` | `WaliSettingsScreen` | `IntroductionPolicy.canSubmit`; `SafeguardResolver` | all | `formal_introduction_settings` | Off by default; a floor can forbid but never enable | `an organisation can forbid introductions but cannot enable them` (`SafeguardResolverTest`) | Implemented and tested |
| `introductionsGoDirectlyToGuardian` | `WaliSettingsScreen` | `IntroductionPolicy.statusOnSubmission` | all | `formal_introduction_settings` | Decides whether the recipient sees it at all | `the recipient's own screening preference decides who sees it first` (`IntroductionPolicyTest`) | Implemented and tested |
| `quietHours` (`enabled`, `start`, `end`, `blockNewConversations`, `blockCalls`) | `SafeguardSettingsScreen` | `QuietHours.covers`; `ContactPolicy`; `LiveSessionPolicy` | all | `user_safeguards` | Wraps past midnight; blocks new threads and calls but never existing threads | `quiet hours block new conversations and wrap past midnight` (`ContactPolicyTest`); `quiet hours stop new conversations but not existing ones` (`MessagingBehaviourTest`); `quiet hours cover calls as well as new conversations` (`LiveSessionPolicyTest`) | Implemented and tested |
| Stricter-wins combination with organisation and community floors | `SafeguardSettingsScreen` | `SafeguardResolver.effective` | all | `organizations.safeguard_floor`, `communities.safeguard_floor` | Combining can only tighten | `an organisation floor can only tighten, never loosen`; `multiple floors compose to the strictest of all of them` (`SafeguardResolverTest`) | Implemented and tested |
| Organisation floors travel; community floors stay in their space | — | `ContactContextAssembler.contextualFloorsFor` | all | as above | Distinct code paths, deliberately | `an organisation floor travels with its members`; `a community floor does not follow its volunteers into unrelated conversations` (`SafeguardFloorScopeTest`) | Implemented and tested |
| Plain-language warning before a change loosens anything | `SafeguardSettingsScreen` | `SafeguardResolver.loosenedFields` | all | `audit_logs` | Loosening is allowed but named, and audited | `loosening is reported field by field in plain language` (`SafeguardResolverTest`) | Implemented and tested |
| No safeguard field can be added without being wired in | — | reflective walk of `SafeguardFloor` | — | — | Test fails if a field exists that the resolver ignores | `every field on SafeguardFloor is read by the resolver` (`SafeguardsAreEnforcedTest`) | Implemented and tested |

## 4. Anti-hookup and purpose-based messaging architecture

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| No conversation can exist without a purpose | `ComposeConversationScreen` | `ContactPurpose`, `StartConversationUseCase` | all | `conversation_purposes` (deferrable constraint trigger) | No code path produces an unattached thread | `3. a conversation cannot be created without a valid purpose` (`CriticalFlowsTest`) | Implemented and tested |
| Eight purposes; six user-selectable | `ComposeConversationScreen` | `ContactPurposeKind.userSelectable` | all | `conversation_purposes.kind` | Moderation and introduction purposes are not selectable | `an ordinary member cannot open a moderation thread`; `the safety team can open a moderation thread` (`ContactPolicyTest`) | Implemented and tested |
| Purposes that require a subject cannot be opened without one | `ComposeConversationScreen` | `PurposeValidator.subjectMatchesKind` | all | `conversation_purposes.subject_id` | `PURPOSE_REQUIRES_SUBJECT` | `a conversation cannot be opened without a subject when the purpose needs one` (`ContactPolicyTest`) | Implemented and tested |
| Structured opening: reason ≥ 20 chars, requested action ≥ 10, duration | `ComposeConversationScreen` | `PurposeValidator.validate` | all | `conversation_purposes` | Length floors make bulk "hey" impossible | `3b. an opening that reads as a chat-up line is refused with an explanation` (`CriticalFlowsTest`) | Implemented and tested |
| Chat-up-line detection in the purpose itself | `ComposeConversationScreen` | `PurposeValidator.FLIRTATIOUS_OPENERS` (22 phrases) | all | — | Refused with an explanation pointing at the introduction process | `3b. …` (`CriticalFlowsTest`) | Implemented and tested |
| Purpose banner at the top of every thread | `ConversationScreen` | `PurposeValidator.bannerText` | all | `conversation_purposes` | — | — | Implemented, untested |
| Marriage interest cannot be expressed through messaging | `ComposeConversationScreen` | `ContactPolicy.evaluate`, early return | all | — | `INTRODUCTION_MUST_USE_WORKFLOW` | `marriage interest cannot be expressed through messaging` (`ContactPolicyTest`) | Implemented and tested |
| Purpose is immutable once declared | `ConversationScreen` | RLS: no UPDATE policy on `conversation_purposes` | all | `conversation_purposes` | A thread's stated purpose can never be rewritten | RLS suite | Implemented and tested |
| Purpose-drift detection and a gentle in-thread reminder | `ConversationScreen` | `ContentSignals.purposeDrift`, `driftReminder` | all | `messages` (`PURPOSE_REMINDER`) | Advisory; a note between adults, never a report | `a thread that has wandered produces a gentle reminder`; `a thread still on topic produces no drift signal`; `drift is only checked once a thread has some history` (`ContentSignalsTest`) | Implemented and tested |
| Drift reminders rate-limited to one per hour | `ConversationScreen` | `Conversation.lastDriftReminderIsStale` | all | `conversations.last_message_at` | — | — | Implemented, untested |
| Oversight participants added at creation, never invited afterwards | `ConversationScreen` | `StartConversationUseCase`, `ConversationRole.isOversight` | all | `conversation_members` | Requirements are applied, not suggested | `2b. a guardian-guided member gets a guardian in the thread automatically` (`CriticalFlowsTest`) | Implemented and tested |
| Oversight can be added, never removed | `ConversationScreen` | `AddOversightUseCase` (no counterpart) | all | `conversation_members` | There is no remove path | `a sender may add oversight but never remove it` (`ContactPolicyTest`) | Implemented and tested |
| Oversight joining is announced in the thread | `ConversationScreen` | `MessageKind.PARTICIPANT_CHANGE` | all | `messages` | Record is unambiguous | `oversight participants are announced in the thread rather than added silently` (`MessagingBehaviourTest`) | Implemented and tested |
| Rate limit: 10 new conversations per 24 hours | `ComposeConversationScreen` | `ContactPolicy.RateLimits` | all | `conversations` | `RATE_LIMIT_EXCEEDED` | `the new-conversation rate limit is enforced` (`ContactPolicyTest`); `…end to end` (`MessagingBehaviourTest`) | Implemented and tested |
| No second approach after being turned away | `ComposeConversationScreen` | `ContactPolicy`, `countDeclinedApproaches` | all | `conversations` | `MAX_APPROACHES_AFTER_DECLINE = 0` | `a second approach after being turned away is refused` (`ContactPolicyTest`) | Implemented and tested |
| Blocks end existing threads as well as new ones | `MemberProfileScreen`, `ConversationScreen` | `BlockUserUseCase` | all | `blocks`, `conversations` | Bidirectional; the blocked person is never told | `1. a blocked user cannot contact the blocker`; `1b. blocking ends conversations that already exist` (`CriticalFlowsTest`); `the block message reveals nothing about why` (`ContactPolicyTest`) | Implemented and tested |
| Blocks short-circuit before any safeguard reason is evaluated | — | `ContactPolicy.evaluate`, early return | all | — | Prevents probing a recipient's settings | `a block short-circuits before any safeguard reason is evaluated` (`ContactPolicyTest`) | Implemented and tested |
| Either participant can end a thread | `ConversationScreen` | `EndConversationUseCase` | all | `conversations.state` | `ENDED` accepts no further messages | `either participant can end a conversation and nobody can speak afterwards` (`MessagingBehaviourTest`) | Implemented and tested |
| Unsend within 5 minutes, original preserved for the safety team | `ConversationScreen` | `UnsendMessageUseCase`, `ModerationPolicy.canUnsend` | all | `message_redactions` (moderator-only, `FORCE`) | Unsend is not an evidence-destruction primitive | `unsending hides a message from participants but preserves the evidence` (`CriticalFlowsTest`); `unsend closes after its window so abuse cannot be erased later`; `a member cannot unsend someone else's message` (`ModerationPolicyTest`) | Implemented and tested |
| On-device safety signals: sexual, flirtation, isolation, off-platform, financial, contact details | `ConversationScreen` | `ContentSignals.forMessage` | all | `message_safety_signals` (advisory only) | Never restricts, never removes, never decides | `sexual content is flagged with the phrase that triggered it`; `scam patterns are surfaced`; `attempts to move off the platform are surfaced`; `every signal is advisory and explains itself` (`ContentSignalsTest`) | Implemented and tested |
| Signals are stored so a pattern across people can be seen | — | `SendMessageUseCase`, `ModerationRepository.recordSignals` | all | `message_safety_signals` | Moderator-read only; no client write path; no copy of the message body | 5 assertions in `SafetySignalsReachTheQueueTest`, 10 in the RLS suite | Implemented and tested |
| Accumulated signals open a case in the safety queue | — | `SignalEscalationPolicy` | all | `moderation_cases` | Never restricts and never notifies the sender; one open automated case per person | 12 assertions in `SignalEscalationPolicyTest` | Implemented and tested |
| Cross-gender context raises signal weight rather than creating signals | — | `ContentSignals.forMessage(conversationIsCrossGender)` | all | — | — | `flirtation carries more weight in a cross-gender thread than in a same-gender one` (`ContentSignalsTest`) | Implemented and tested |
| Signals reach a moderator queue | — | — | moderator | — | — | Not implemented *(`SendMessageUseCase` returns signals in `SentMessage`; nothing persists them or raises a case)* |
| High-volume conversation signal | — | `ContentSignals.conversationVolumeSignal` | all | — | Advisory, threshold 8 | — | Implemented, untested *(no caller invokes it)* |
| Adults and minors cannot message directly | `ComposeConversationScreen` | `ContactPolicy.evaluate`, age branch | all | `profiles.date_of_birth_year` | `MINOR_ADULT_DIRECT_CONTACT` | `adults and minors cannot message each other directly` (`ContactPolicyTest`) | Implemented and tested |
| Refusal messages vaguer than the audit record | `ComposeConversationScreen` | `DenialReason.userFacingMessage` vs `auditReason` | all | `audit_logs` | A refusal must not hint at which setting to work around | `the block message reveals nothing about why` (`ContactPolicyTest`) | Implemented and tested |
| Refused attempts are audited even though the recipient never sees them | — | `StartConversationUseCase`, `conversation_attempt` entry | all | `audit_logs` | Pattern of refused approaches is visible to the safety team | — | Implemented, untested |

## 5. Wali-centred Formal Family Introduction

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Off by default; nothing browsable | `WaliSettingsScreen` | `FormalIntroductionSettings.enabled = false` | all | `formal_introduction_settings` | `is_open_to_introductions` defaults false; `visible_to` defaults `wali_referral_only` | `a member with the feature switched off is indistinguishable from any other refusal` (`IntroductionPolicyTest`); `5b. …` (`CriticalFlowsTest`) | Implemented and tested |
| Cannot be enabled without a wali or intermediary | `WaliSettingsScreen` | `FormalIntroductionSettings.isUsable`; `UpdateIntroductionSettingsUseCase` | all | `trusted_contacts` | Enforced in the model and again in the use case | `an intermediary is only accepted when the member allowed one` (`IntroductionPolicyTest`) | Implemented and tested |
| Ten-field structured form with 40-character floors | `SubmitIntroductionScreen` | `IntroductionForm`, `IntroductionPolicy.validateForm` | all | `formal_introduction_requests` | Field-addressed errors, shown inline | `the form rejects a token effort and an unaccepted conduct agreement` (`IntroductionPolicyTest`) | Implemented and tested |
| Six conduct rules, recorded against the sender's account | `SubmitIntroductionScreen` | `IntroductionForm.CONDUCT_RULES`, `conductAgreementAcceptedAt` | all | `formal_introduction_requests` | Stated, not implied | `the conduct rules are stated, not implied` (`IntroductionPolicyTest`) | Implemented and tested |
| Identity verification required of the sender | `SubmitIntroductionScreen` | `IntroductionPolicy.canSubmit` | all | `user_verifications` | `IDENTITY_VERIFIED` minimum, plus the recipient's own floor | `introductions require identity verification` (`IntroductionPolicyTest`) | Implemented and tested |
| Cross-gender only, adults only | `SubmitIntroductionScreen` | `IntroductionPolicy.canSubmit` | all | `profiles` | `SAME_GENDER`, `NOT_AN_ADULT` | `an introduction between two people of the same gender is refused` (`IntroductionPolicyTest`) | Implemented and tested |
| Volume limits: 2 open, 4 per 30 days, 1 per recipient ever | `SubmitIntroductionScreen` | `IntroductionLimits` | all | `formal_introduction_requests` | Applies regardless of anyone's settings | `sending in volume is refused`; `a second request to the same person is refused` (`IntroductionPolicyTest`) | Implemented and tested |
| Recipient's screening preference decides who sees it first | `IntroductionDetailScreen` | `IntroductionPolicy.statusOnSubmission` | all, wali | `formal_introduction_settings` | A member may never see a request at all | `the recipient's own screening preference decides who sees it first` (`IntroductionPolicyTest`); `5. an introduction follows the recipient's configuration exactly` (`CriticalFlowsTest`) | Implemented and tested |
| Eleven-state machine with explicit permitted transitions | `IntroductionDetailScreen` | `IntroductionPolicy.allowedTransitions` | all, wali, moderator | `formal_introduction_requests.status` | Anything not listed cannot happen | `terminal states cannot be revived`; `there is no transition from submitted straight to a conversation` (`IntroductionPolicyTest`) | Implemented and tested |
| The only exit into a conversation runs through the guardian | `IntroductionDetailScreen` | `OpenIntroductionConversationUseCase` | wali | `conversations` | `conversationEligibility` returns `guardianRequired = true` unconditionally | `the guardian is required in the conversation under every configuration` (`IntroductionPolicyTest`); `5c. the only route to a conversation runs through the guardian` (`CriticalFlowsTest`) | Implemented and tested |
| Guardian contact details never reach the counterparty | `IntroductionDetailScreen` | `TrustedContact.redacted()`, `ViewIntroductionGuardianUseCase` | all | `wali_profiles` (contact columns SELECT-revoked from every role) | Only `app.get_wali_contact(...)` reads them, and it writes a disclosure ledger row | `guardian contact details are stripped from the redacted form` (`IntroductionPolicyTest`); `4. a member cannot obtain another person's wali contact details` (`CriticalFlowsTest`) | Implemented and tested |
| Guardian access is audited | — | `AuditAction.GUARDIAN_CONTACT_ACCESSED` | all | `audit_logs`, `wali_contact_disclosures` | Append-only; the ward can always audit it | RLS suite (`wali_contact_disclosures` insert/update/delete revoked) | Implemented and tested |
| Silence is a complete answer — 21-day lapse | — | `IntroductionPolicy.hasLapsed`, `LapseIntroductionsUseCase` | all | `formal_introduction_requests` | `LAPSE_AFTER_DAYS = 21` | `an unanswered request lapses on its own` (`IntroductionPolicyTest`) | Implemented and tested |
| Lapsing actually running | — | — | — | — | — | Not implemented *(`LapseIntroductionsUseCase` exists but no scheduler calls it and no screen invokes it)* |
| Sender told nothing beyond "it concluded" | `IntroductionDetailScreen` | `IntroductionPolicy.senderFacingOutcome` | all | — | No reason, no name, no queue position | `a declined sender is told nothing beyond the fact that it ended` (`IntroductionPolicyTest`) | Implemented and tested |
| Every refusal is identical, so a sender learns nothing | `SubmitIntroductionScreen` | `IntroductionDenialReason` — three values share one message | all | — | Deliberate collision of `NOT_AVAILABLE` / `FEATURE_DISABLED` / `NO_GUARDIAN_CONFIGURED` | `a member with the feature switched off is indistinguishable from any other refusal` (`IntroductionPolicyTest`) | Implemented and tested |
| Closing the door permanently also blocks | `IntroductionDetailScreen` | `DecideIntroductionUseCase`, `BLOCK_PERMANENTLY` | all | `blocks` | Open introductions from a blocked sender are closed, not queued | `5d. closing an introduction permanently blocks further approaches` (`CriticalFlowsTest`) | Implemented and tested |
| Religious-guidance notice wherever the feature appears | `WaliSettingsScreen`, `SubmitIntroductionScreen`, introduction thread | `IntroductionPolicy.RELIGIOUS_GUIDANCE_NOTICE` | all | — | The platform issues no rulings | `the platform refers religious questions to a qualified scholar` (`IntroductionPolicyTest`) | Implemented and tested |
| Guardian-inclusive **live** conversation | — | `LiveSessionKind.GUARDIAN_INCLUSIVE_INTRODUCTION` | wali | — | `userCreatable = false` | `an introduction room is never something a member can create` (`LiveSessionPolicyTest`) | Model and UI only (no transport/provider) |

## 6. Learning system

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Sixteen subjects across five fields, religious ones flagged | `LearnScreen` | `LearningSubject.isReligiousInstruction` | learner, teacher | `learning_subjects` | Religious subjects carry extra disclosure requirements | `a peer-led class is labelled as peer learning wherever it is shown` (`SeedDataTest`) | Implemented and tested |
| Methodology disclosed, never adjudicated | `LearningDetailScreen` | `Methodology` (7 values) | teacher | `learning_offerings.methodology` | The platform takes no position on which is correct | — | Implemented, untested |
| Source references required for religious material | `LearningDetailScreen` | `SourceReference` | teacher, scholar | `learning_offerings` | Attributable material only | — | Implemented, untested *(the type exists and requires a title; nothing refuses to publish a religious offering without one)* |
| Capacity disclaimer under every instructor's name | `LearningDetailScreen` | `LearningOffering.capacityDisclaimer` | teacher, learner | `learning_offerings` | Peer learning is always labelled as such | `a peer-led class is labelled as peer learning wherever it is shown` (`SeedDataTest`); `the imam is the only account presented as a verified scholar` (`SeedDataTest`) | Implemented and tested |
| Enrolment request → teacher decision | `LearningDetailScreen` | `EnrollInLearningUseCase` | learner, teacher | `learning_enrollments` | Student enrols, teacher decides | — | Implemented, untested |
| Gender arrangement enforced on enrolment | `LearningDetailScreen` | `EnrollInLearningUseCase`, `GenderArrangement.admits` | learner | `learning_offerings` | Refused, not hidden | `a sisters-only activity does not accept a brother's application` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Same-gender-students-only is enforced, not displayed | `LearningDetailScreen` | `EnrollInLearningUseCase`, `sameGenderStudentsOnly` | teacher, learner | `learning_offerings` | A teacher's safeguard, not a preference | `a teacher who takes same-gender students only is enforced, not merely displayed` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Cost model: free, suggested donation, fee | `LearningDetailScreen` | `LearningCost` sealed interface | teacher | `learning_offerings` | No payment can be taken (see §10) | — | Implemented, untested |
| Class capacity and waitlist | `LearningDetailScreen` | `LearningOffering.placesRemaining`, `EnrollmentStatus.WAITLISTED` | teacher, learner | `learning_enrollments` | Refused when full | — | Implemented, untested |
| Search by subject, level, language, format, city, free-only | `LearnScreen` | `LearningSearchCriteria`, `LearnViewModel` | all | `learning_offerings` | No popularity ordering | `search results are not ranked by anything a person could game` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Creating a learning offering | `CreateClassScreen` | `CreateLearningOfferingUseCase` | teacher | `learning_offerings` | Capacity gated on a confirmed qualification; religious subjects must name a methodology and a source | 6 assertions in `AuthoringAndAdministrationTest` | Implemented and tested |
| Live class, study circle, Qur'an recitation rooms | — | `LiveSessionKind` (see §7) | teacher, learner | — | `isInstruction` repeats the capacity disclaimer | `a teaching room repeats the instructor's capacity disclaimer` (`LiveSessionPolicyTest`) | Model and UI only (no transport/provider) |

## 7. Live voice and video sessions

Full treatment in [`live-sessions.md`](live-sessions.md). Everything in this section is
gated by `LiveSessionFeatureFlags.transportConfigured = false`.

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Eight session kinds, four of them instructional | — | `LiveSessionKind` (`core/model/.../LiveSessions.kt`) | teacher, learner, organiser, wali | — | `isInstruction`, `userCreatable` | `an introduction room is never something a member can create` | Model and UI only (no transport/provider) |
| Safeguards computed into the room before a person arrives | — | `LiveSessionPolicy.mediaPermissions`, `LiveMediaPermissions` | all | — | The person's own scope governs; the host cannot widen it | `a camera closed to the opposite gender stays closed in a mixed class`; `the same person's camera is available in a sisters-only room` | Model and UI only (no transport/provider) |
| A locked control states its reason | — | `cameraLockedReason`, `microphoneLockedReason` | all | — | Four distinct causes, four distinct sentences | `a camera closed…` asserts the reason text | Model and UI only (no transport/provider) |
| Neither host nor moderator can widen another person's setting | — | `mediaPermissions` — no branch consults host preference | host, moderator | — | — | `neither the host nor a moderator can widen a participant's own setting` | Model and UI only (no transport/provider) |
| Host is exempt from their own participant-facing settings | — | `isHost` in `mediaPermissions` | host | — | — | `the host is not blocked by their own participant-facing settings` | Model and UI only (no transport/provider) |
| `HOST_VIDEO_ONLY` medium | — | `LiveSessionMedium` | teacher | — | Students heard, not seen, regardless of their own setting | `an instructor-only video room keeps students off camera regardless` | Model and UI only (no transport/provider) |
| Text chat stays open when voice is closed | — | `LiveMediaPermissions.mayUseTextChat` | all | — | Defaults true; never revoked by a voice scope | `voice closed to the opposite gender still leaves the text chat open` | Model and UI only (no transport/provider) |
| One-to-one cross-gender requires oversight | — | `LiveSessionPolicy.canJoin` | all | — | `ONE_TO_ONE_CROSS_GENDER_WITHOUT_OVERSIGHT` | `a one-to-one cross-gender session needs somebody else present`; `the same session is permitted once a guardian is in it`; `a same-gender one-to-one tutorial needs no oversight` | Model and UI only (no transport/provider) |
| Blocks apply to rooms, in both directions | — | `canJoin` | all | `blocks` | Host block, joiner block, or any blocked participant present | `a blocked member cannot join a room the blocker is in` | Model and UI only (no transport/provider) |
| `JOIN_LIVE_SESSIONS` / `HOST_LIVE_SESSIONS` restrictions | — | `RestrictedCapability`, `LIVE_SESSION_CAPABILITIES` | moderator | `restrictions` | `RESTRICTED` | `a restriction on live sessions is enforced` | Model and UI only (no transport/provider) |
| Quiet hours cover calls | — | `canJoin`, `quietHours.blockCalls` | all | `user_safeguards` | `QUIET_HOURS` | `quiet hours cover calls as well as new conversations` | Model and UI only (no transport/provider) |
| Gender arrangement on a room | — | `GenderArrangement.admits` | all | — | `GENDER_ARRANGEMENT` | `a sisters-only session does not admit a brother` | Model and UI only (no transport/provider) |
| Enrolment required for a class | — | `session.requiresEnrolment` | learner | `learning_enrollments` | `NOT_ENROLLED` | `an unenrolled member cannot walk into a class that requires enrolment` | Model and UI only (no transport/provider) |
| Adults only | — | `canJoin`, `isAdultIn` | all | `profiles` | `MINOR_ADULT_MIXING` | `adults and minors do not share a live room` | Model and UI only (no transport/provider) |
| Ended and moderator-halted rooms refuse joins | — | `LiveSessionState.acceptsJoins` | all | — | `SESSION_NOT_OPEN` | `an ended session cannot be joined`; `a session stopped by moderation cannot be rejoined` | Model and UI only (no transport/provider) |
| Oversight roles observe rather than participate | — | `LiveRole.isOversight`, `LiveMediaPermissions.observing` | wali, moderator, org rep, third party | — | No camera, no microphone, stated reason | `an oversight participant observes rather than takes part` | Model and UI only (no transport/provider) |
| Recording: unanimous and continuing consent | — | `RecordingPolicy`, `LiveSessionPolicy.recordingDecision` | all | — | Everyone present must agree; a late arrival who declines stops it | `recording needs everybody present to have agreed, not a majority`; `a never-recorded session cannot be recorded by any route` | Model and UI only (no transport/provider) |
| Withdrawing recording consent | — | `LiveRecordingConsentUseCase.setConsent` | all | — | Consent that cannot be withdrawn is not consent | — | Implemented, untested |
| Teaching disclosures repeated into the room | — | `LiveSessionPolicy.requiredDisclosures` | teacher, learner | — | Capacity disclaimer plus the no-rulings notice | `a teaching room repeats the instructor's capacity disclaimer` | Model and UI only (no transport/provider) |
| Preview notice while no provider is connected | — | `LiveSessionFeatureFlags.notConfiguredNotice` | all | — | Shown in `buildNotices` and `requiredDisclosures` | `the preview notice is shown while no media provider is connected` | Behind feature flag |
| Scheduling a session | — | `ScheduleLiveSessionUseCase` | teacher, organiser | — | Host = self; `HOST_LIVE_SESSIONS` restriction; future start; `userCreatable` | — | Implemented, untested |
| Joining a session (bookkeeping, audit, permissions applied) | — | `JoinLiveSessionUseCase` | all | `audit_logs` | Permissions written onto the participant record | — | Implemented, untested |
| Ending or halting a session | — | `EndLiveSessionUseCase` | host, moderator | — | Only the host or the safety team; only the safety team may halt | — | Implemented, untested |
| Media transport | — | `LiveSessionTransport` (port only, **untracked in git**) | — | — | Join token must encode permissions provider-side | — | Not implemented |
| Live-session storage | — | `LiveSessionRepository` (interface only) | — | — | — | — | Not implemented |
| Live-session database tables and RLS | — | — | — | — | — | — | Not implemented |
| Live-session screens and routes | — | — | — | — | — | — | Not implemented |
| Moderator join-in-progress | — | — | moderator | — | — | — | Not implemented |

## 8. Volunteering and mutual aid

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Twenty-seven service categories in four groups | `ServeScreen`, `CreateListingScreen` | `ServiceCategory`, `ServiceGroup` | all | `service_categories` | `involvesMinors` / `involvesHomeVisits` drive safeguarding | `the sample data covers every scenario the product specification names` (`SeedDataTest`) | Implemented and tested |
| Opportunity listing with mandatory completion criteria | `CreateListingScreen`, `OpportunityDetailScreen` | `VolunteerOpportunity.init`, `CreateOpportunityUseCase` | organiser | `volunteer_opportunities` | Blank criteria refused | `every listing in the sample data states how a volunteer knows they are finished` (`SeedDataTest`) | Implemented and tested |
| Background check mandatory for minors and home visits | `CreateListingScreen`, `OpportunityDetailScreen` | `backgroundCheckIsMandatory`; `CreateOpportunityUseCase`; `ApplyToOpportunityUseCase` | organiser, volunteer | `volunteer_opportunities` | Refused at publish *and* at apply | `an opportunity for young people cannot be published without safeguarding`; `applying to work with young people requires a background check` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Validation errors from listing and request creation shown to the author | `CreateListingScreen`, `CreateRequestScreen` | `CreateOpportunityUseCase.Command`, `CreateServiceRequestUseCase.Command` | organiser, all | `volunteer_opportunities`, `service_requests` | Commands carry fields as typed, so the model's `init` cannot throw before validation runs | 17 assertions in `CreationValidationTest` | Implemented and tested |
| A category's safeguarding requirement cannot be switched off by the form | `CreateListingScreen` | `CreateOpportunityUseCase` | organiser | `volunteer_opportunities` | The category wins, and the organiser is told why | `a safeguarding requirement is added by the category even when the form says no` | Implemented and tested |
| Anonymity and a street address are queried before they are stored together | `CreateRequestScreen` | `CreateServiceRequestUseCase` | all | `service_requests` | — | `hiding your name and attaching your address is queried before it is stored` | Implemented and tested |
| Gender arrangement on an activity | `OpportunityDetailScreen` | `GenderArrangement.admits` | volunteer | `volunteer_opportunities` | Refused on apply | `a sisters-only activity does not accept a brother's application` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Application → organiser decision → commitment | `OpportunityDetailScreen` | `ApplyToOpportunityUseCase.decide` | organiser, volunteer | `volunteer_applications`, `commitments` | Applicant cannot pre-approve themselves (RLS) | RLS suite | Implemented, untested |
| Listing verification tiers, each stating its limit | `OpportunityDetailScreen` | `ListingVerification` | all | `volunteer_opportunities` | Organisation-backed means the organisation, not the platform, is responsible | — | Implemented, untested |
| Check-in with punctuality, check-out | `MyCommitmentsScreen` | `CommitmentUseCase` | volunteer | `commitments` | Only your own; a no-show is distinguished from a cancellation | `RecognitionTest` exercises the whole lifecycle | Implemented and tested |
| Organiser confirmation before a commitment counts | — | `CommitmentUseCase.confirmByOrganizer` | organiser | `commitment_confirmations` | Nobody confirms their own commitment (`trg_commitment_confirmations_not_self`) | `a completed commitment counts only once the organiser confirms it` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Projects, project members, tasks | `ProjectsScreen`, `ProjectDetailScreen` | `Project`, `ProjectMember`, `ProjectTask` | organiser, volunteer | `projects`, `project_members`, `project_tasks` | `ProjectRole.canAssignTasks`; you cannot promote yourself to lead (RLS) | RLS suite | Implemented, untested |
| Creating a project or a task | `CreateProjectScreen` | `CreateProjectUseCase`, `ProjectTaskUseCase` | organiser | `projects`, `project_tasks` | Only an organiser adds tasks; only the holder or an organiser moves one | 3 assertions in `AuthoringAndAdministrationTest` | Implemented and tested |
| Search by category, city, radius, date, format, skill | `ServeScreen` | `OpportunitySearchCriteria` | all | `volunteer_opportunities` | Nothing sortable by popularity | `search results are not ranked by anything a person could game` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Safety incident recording | — | `SafetyIncident`, `ModerationRepository.saveIncident` | moderator | `safety_incidents` | Moderator-only; the subject never reads it (`FORCE`) | RLS suite | Implemented, untested *(no use case and no screen create one)* |

## 9. Help requests

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Request with four visibility modes | `CreateRequestScreen`, `RequestDetailScreen` | `RequestVisibility` | all | `service_requests` | Anonymous to members; moderators always see the requester | — | Implemented, untested |
| Approximate location by default; exact address withheld | `RequestsScreen`, `RequestDetailScreen` | `Place`, `BrowseRequestsUseCase.toVisible`, `VisibilityPolicy.exactAddressFor` | all | `service_requests` / `service_request_private_details` | **The doorstep rule** — `app.can_see_exact_location()` | `10. an exact address stays private until the requester releases it` (`CriticalFlowsTest`) | Implemented and tested |
| Only the requester can release their own address | `RequestDetailScreen` | `DiscloseExactLocationUseCase` | requester | `service_request_private_details` | Names the recipient, writes `EXACT_LOCATION_DISCLOSED` | `10b. only the requester can release their own address` (`CriticalFlowsTest`) | Implemented and tested |
| Support totals hidden unless the requester turns them on | `RequestDetailScreen` | `ServiceRequest.showSupportTotals` | all | `service_requests` | Nobody's difficulty becomes a progress bar by accident | — | Implemented, untested |
| Urgency levels and expiry | `RequestsScreen` | `RequestUrgency`, `expiresAt` | all | `service_requests` | — | — | Implemented, untested |
| Organisation-mediated requests | `RequestDetailScreen` | `mediatingOrganizationId`, `ORGANIZATION_MEDIATED` | charity, masjid | `service_requests` | Only the chosen organisation and moderators see the requester | — | Implemented, untested |
| Offering help against a request | `RequestDetailScreen` | `RespondToRequestUseCase` | volunteer | `request_responses` | You cannot offer help to someone who blocked you (RLS) | RLS suite | Implemented, untested |
| Fraud review state | — | `FraudReviewState` | moderator | `service_requests` | — | — | Implemented, untested *(the field exists; nothing sets it and no screen shows it)* |
| Maximum-assistance ceiling | `CreateRequestScreen` | `maximumAssistance: Money?` | requester, charity | `service_requests` | Keeps help proportionate | — | Implemented, untested |
| Eligibility documents, reviewer-only | — | `eligibilityDocumentRefs` | moderator | `service_requests` | Stored privately | — | Not implemented *(a list of string references with no upload path, no storage and no reviewer screen)* |

## 10. Donations

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Payments switched off in code | `GivingComplianceScreen` | `DonationFeatureFlags.paymentsEnabled = false` | all | `campaigns` (`check (payments_enabled = false)`) | No card, token or bank field exists anywhere | `payments stay disabled until the compliance work is finished` (`ProductPrincipleTest`); `the verified campaign still cannot take a payment` (`SeedDataTest`) | Behind feature flag |
| Campaign model with fund types and verification tiers | — | `Campaign`, `FundType`, `CampaignVerification` | charity, masjid | `campaigns`, `campaign_verifications` | An organisation cannot attest to itself (`FORCE`) | RLS suite | Implemented, untested |
| Zakat eligibility requires a recorded attestation from a qualified body | — | `Campaign.init`, `VerificationPolicy.zakatEligibilityFor` | charity, scholar | `campaigns.zakat_eligible` (trigger-guarded) | The platform records the attestation; it does not make the ruling | `a campaign is never zakat eligible without a recorded attestation`; `marking a campaign zakat eligible without an attestation is rejected outright` (`VerificationPolicyTest`) | Implemented and tested |
| Giving shown as preview rather than as a broken donate button | `GivingComplianceScreen` | `VerificationPolicy.canOfferDonations` → `GivingAvailability.Preview` | all | — | Collecting intent the platform cannot honour is itself a harm | `giving stays in preview while payments are switched off` (`VerificationPolicyTest`) | Behind feature flag |
| Anonymous donations stay anonymous even from the recipient | — | `Donation.anonymous` | donor, charity | `donations` | Org admins read non-anonymous rows only (`FORCE`) | RLS suite | Implemented, untested |
| Campaign list, detail and donate screens | — | — | all | `campaigns` | — | — | Not implemented *(no route, no screen, no view model; the only giving surface is the legal explanation page)* |
| Payment provider integration | — | — | — | — | — | — | Not implemented — see [`payment-compliance.md`](payment-compliance.md) |

## 11. Trust and verification

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Five verification levels, ranked | `ProfileScreen`, `MemberProfileScreen` | `VerificationLevel.rank`, `atLeast` | all | `user_verifications` | Trust is granted, never claimed (`FORCE`, mod-only UPDATE) | `8. a member cannot give themselves verification or scholar status` (`CriticalFlowsTest`) | Implemented and tested |
| Every badge states what it does **not** mean | `MemberProfileScreen` | `VerificationLevel.whatItDoesNotMean`, `VerificationPolicy.badgeExplanation` | all | — | A badge without its limits transfers trust the platform has not earned | `every badge states what it does not mean` (`VerificationPolicyTest`) | Implemented and tested |
| Four attestations, unordered, separate from identity | `OrganizationDetailScreen`, `MemberProfileScreen` | `Attestation` | organisation, wali, teacher | `organization_verifications`, `qualifications` | An organisation can be verified without any staff being background-checked | `every badge states what it does not mean` (`VerificationPolicyTest`) | Implemented and tested |
| Organisation verification checks documents, never conduct | `OrganizationDetailScreen` | `OrganizationVerification` | charity, masjid | `organization_verifications` | No field asserts trustworthiness; orgs never verify themselves | `7. an organisation administrator cannot reach another organisation's records` (`CriticalFlowsTest`) | Implemented and tested |
| Qualification submission and review | `MyQualificationsScreen`, `TrustReviewScreen` | `SubmitQualificationUseCase`, `ReviewQualificationUseCase` | teacher, scholar, moderator | `qualifications` | A claim is displayed as a claim; reviewed by a moderator or a listed scholar; never your own | 4 assertions in `VerificationTest` | Implemented and tested |
| Verification upgrade flow (identity, phone, background check) | — | — | all | `user_verifications` | Provider-backed | — | Implemented and tested *(`RequestVerificationScreen`, `DecideVerificationUseCase`; a method's ceiling beats the reviewer, and a level can be revoked)* |

## 12. Reputation without vanity

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Nine coarse trust labels, none of them a number | `MemberProfileScreen` | `TrustLabel`, `TrustPolicy.publicLabels` | all | derived | No method returns anything sortable | `public trust labels never carry a number a viewer could rank by`; `commitment thresholds produce the coarse label and not the count` (`TrustPolicyTest`) | Implemented and tested |
| Private trust record never leaves as a number | — | `TrustRecord` | all | `private` (no public projection) | Not returned to another member in any form | `the private trust record never leaves as a number` (`ProductPrincipleTest`) | Implemented and tested |
| Organiser summary offered for one decision, never stored on a profile | — | `TrustPolicy.organizerSummary`, `warrantsOrganizerAttention` | organiser | derived | Advisory; never an automatic exclusion | `an organiser summary is offered for a decision, not as a verdict` (`TrustPolicyTest`) | Implemented and tested |
| New members labelled as new, not as untrustworthy | `MemberProfileScreen` | `TrustLabel.NEW_MEMBER`, `TrustRecord.isNew` | all | — | "which is not a mark against them" | `a new member is labelled as new rather than as untrustworthy` (`TrustPolicyTest`) | Implemented and tested |
| Private impact record, never ranked | `ServiceHistoryScreen` | `PrivateImpactRecord`, `PrivateImpactUseCase` | all | `private_impact_records` | Self-only RLS; **no moderator read, no aggregate, no leaderboard** | `the private trust record never leaves as a number` (`ProductPrincipleTest`) | Implemented and tested |
| No vanity metric on any publicly visible type | — | reflective check over public model types | — | — | Adding a follower count means deleting a test that explains why it should not exist | `no publicly visible type carries a vanity metric` (`ProductPrincipleTest`) | Implemented and tested |
| No notification exists to bring someone back for its own sake | `NotificationsScreen` | `NotificationKind` (21 values) | all | `notifications` | No profile-view, streak or "people are talking about" kind | `no notification exists to bring someone back for its own sake` (`ProductPrincipleTest`) | Implemented and tested |
| Task endorsements, attached to a category rather than a person | `EndorseScreen` | `EndorseTaskUseCase` | volunteer, organiser | `task_endorsements` | Bound to a confirmed commitment; no endorse button on a profile; once per occasion | 4 assertions in `RecognitionTest` | Implemented and tested |

## 13. Moderation and governance

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Twenty granular report categories with severities | `ReportScreen` | `ReportCategory`, `ReportSeverity`, `ReportGroup` | all | `reports` | Granular so eleven reports about one behaviour are visible as a pattern | `a critical report opens a case immediately rather than waiting for triage` (`CriticalFlowsTest`) | Implemented and tested |
| Evidence captured at the moment of reporting | `ReportScreen` | `SubmitReportUseCase`, `ReportEvidence` | all | `report_evidence` (append-only, `FORCE`) | Includes preserved originals of unsent messages | `unsending hides a message from participants but preserves the evidence` (`CriticalFlowsTest`) | Implemented and tested |
| Critical reports open a case immediately | `ModeratorDashboardScreen` | `SubmitReportUseCase`, `requiresImmediateHumanReview` | moderator | `moderation_cases` | Does not wait for triage | `a critical report opens a case immediately rather than waiting for triage` (`CriticalFlowsTest`) | Implemented and tested |
| Child-safety and grooming start above the general queue | `ModeratorDashboardScreen` | `ModerationPolicy.initialEscalation` | safety admin | `moderation_cases.escalation` | `SAFETY_ADMINISTRATOR`; threats and illegal content start at `EXTERNAL_REFERRAL` | `child safety and grooming reports start above the general queue` (`ModerationPolicyTest`) | Implemented and tested |
| Triage targets by severity | `ModeratorDashboardScreen` | `ModerationPolicy.triageTarget` | moderator | — | Immediate → 24h → 3 days → routine | — | Implemented, untested |
| Queue ordered by urgency, not by age | `ModeratorDashboardScreen` | `ModerationQueueUseCase` | moderator | `moderation_cases` | Severity, then escalation, then age | — | Implemented, untested |
| Every moderation action requires a rationale | `ModerationCaseScreen` | `ModerationAction.init` | moderator | `moderation_actions` | Blank rationale throws | `a moderation action cannot be recorded without a rationale` (`ModerationPolicyTest`) | Implemented and tested |
| Every moderation action writes an audit entry | — | `TakeModerationActionUseCase`, `ModerationPolicy.auditActionFor` | moderator | `audit_logs` | Written *before* the state change | `6. a moderation action always writes an audit record`; `every moderation action maps to an audit action` (`ModerationPolicyTest`) | Implemented and tested |
| Permanent ban and verification revocation need a safety administrator | `ModerationCaseScreen` | `ModerationActionType.requiresSeniorApproval` | safety admin | — | At least two people before anyone loses an account for good | `a permanent ban requires a safety administrator` (`ModerationPolicyTest`); `6d. …` (`CriticalFlowsTest`) | Implemented and tested |
| Audit log has no update or delete path anywhere | — | `AuditLogRepository` (append only); RLS revokes UPDATE/DELETE | all | `audit_logs` | Not even for platform administrators | `6b. audit records cannot be altered or removed through any interface` (`CriticalFlowsTest`) | Implemented and tested |
| Appeals reviewed by someone uninvolved | — | `ModerationPolicy.canReviewAppeal`, `ReviewAppealUseCase` | moderator, safety admin | `appeals` | The moderator who acted may not review the appeal | `an appeal cannot be reviewed by the moderator who took the original action`; `a member cannot review their own appeal` (`ModerationPolicyTest`) | Implemented and tested |
| Appeal submission and appeal review screens | `MyRestrictionsScreen`, `SubmitAppealScreen`, `AppealQueueScreen` | `MyModerationRecordUseCase`, `AppealQueueUseCase`, `ReviewAppealUseCase` | all, moderator | `appeals`, `restrictions` | The reason is reproduced verbatim; one appeal per case; a reviewer who acted originally is shown why they cannot decide | 8 assertions in `AppealsAreReachableTest` | Implemented and tested |
| A member can reach their own restrictions without being invited | `SafetyCentreScreen` → `MY_RESTRICTIONS` | `MyModerationRecordUseCase` | all | `restrictions` | Not only from the notification that announced it | `a restricted member can see what was restricted and why` | Implemented and tested |
| Retaliatory reporting detected and flagged, never auto-actioned | `ModeratorDashboardScreen` | `ModerationPolicy.assessReportPattern` | moderator | `reports.abuse_assessment` | Output is a flag for review, never a sanction | `a pattern of retaliatory reports is flagged, not silently acted on`; `a small number of reports is not treated as a pattern`; `a reporter previously found to be acting in bad faith is flagged` (`ModerationPolicyTest`) | Implemented and tested |
| Thirteen restrictable capabilities | `ModerationCaseScreen` | `RestrictedCapability`, `Restriction.isActiveAt` | moderator | `restrictions` | You can always see why you are restricted | `a restriction on starting conversations is enforced`; `an expired restriction no longer applies` (`ContactPolicyTest`) | Implemented and tested |
| Conversation freezing preserves evidence | `ModerationCaseScreen` | `ConversationState.FROZEN` | moderator | `conversations` | Accepts no messages from anyone, including oversight | `9b. a frozen conversation accepts no messages from anyone` (`CriticalFlowsTest`) | Implemented and tested |
| Suspended and banned accounts cannot continue existing threads | — | `Profile.isActive`, `cannotContinueConversations()` | all | `profiles.status` | Checked on every send | `9. a suspended account cannot continue an existing conversation`; `a banned account cannot start conversations` | Implemented and tested |
| Enforcement notice telling a member what happened and that they may appeal | — | `ModerationPolicy.enforcementNotice` | all | `notifications` | Moderation is not secret | — | Implemented, untested |
| My-reports view for the reporter | `MyReportsScreen` | — | all | `reports` | Reporter reads their own; the reported user never sees it | — | Implemented, untested |
| Consent records for terms, privacy, guidelines, automated processing, age | `OnboardingConsentScreen` | `ConsentKind`, `ConsentRecord`, `CompleteOnboardingUseCase` | all | `consent_records` (DELETE revoked) | Five required consents; automated processing is spelled out in plain language | `8b. onboarding requires the mandatory consents and an adult declaration` (`CriticalFlowsTest`) | Implemented and tested |
| Automated checks described identically wherever they are described | `OnboardingConsentScreen`, `PrivacyPolicyScreen` | `ContentSignals.DISCLOSURE`, `ConsentKind.AUTOMATED_SAFETY_PROCESSING` | all | — | Kept next to the code so the two cannot drift | `the disclosure describes exactly what the checks do`; `automated checks are described as advisory everywhere they are described` | Implemented and tested |
| Device sessions a member can see and end | `DeviceSessionsScreen` | `DeviceSessionUseCase` | all | `device_sessions` | The first sign-in is not announced; the IP is hashed | 3 assertions in `RecognitionTest` | Implemented and tested |

## 14. Child and vulnerable-person safety

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Adults only; 18 is the threshold | `OnboardingProfileScreen`, `OnboardingConsentScreen` | `Profile.ADULT_AGE`, `isAdultIn` | all | `profiles.date_of_birth_year` | `ConsentKind.ADULT_AGE_DECLARATION` is required | `8b. onboarding requires the mandatory consents and an adult declaration` (`CriticalFlowsTest`) | Implemented and tested |
| Adult-to-minor direct messaging does not exist as a code path | `ComposeConversationScreen` | `ContactPolicy.evaluate` | all | — | `MINOR_ADULT_DIRECT_CONTACT` | `adults and minors cannot message each other directly` (`ContactPolicyTest`) | Implemented and tested |
| Adult-to-minor live rooms do not exist as a code path | — | `LiveSessionPolicy.canJoin` | all | — | `MINOR_ADULT_MIXING` | `adults and minors do not share a live room` (`LiveSessionPolicyTest`) | Model and UI only (no transport/provider) |
| Work with minors requires safeguarding arrangements at publish | `CreateListingScreen` | `CreateOpportunityUseCase`, `childSafeguardingRequired` | organiser | `volunteer_opportunities` | Refused, with a field-addressed message | `an opportunity for young people cannot be published without safeguarding` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Work with minors requires a completed background check to apply | `OpportunityDetailScreen` | `ApplyToOpportunityUseCase` | volunteer | `user_verifications` | `BACKGROUND_CHECKED` | `applying to work with young people requires a background check` (`DiscoveryBehaviourTest`) | Implemented and tested |
| Isolation phrase signals ("our secret", "delete this chat") | `ConversationScreen` | `ContentSignals.GROOMING_TERMS` (8 phrases) → `POSSIBLE_ISOLATION_ATTEMPT` | all | `message_safety_signals` | High confidence, advisory, phrase quoted; files as `GROOMING`, not as flirtation | `an attempt to isolate someone is filed as grooming, not as flirtation` | Implemented and tested |
| `CHILD_SAFETY` and `GROOMING` report categories escalate above the queue | `ReportScreen` | `ModerationPolicy.initialEscalation` | safety admin | `moderation_cases` | `SAFETY_ADMINISTRATOR` | `child safety and grooming reports start above the general queue` (`ModerationPolicyTest`) | Implemented and tested |
| Age assurance beyond a self-declaration | — | — | all | — | — | — | Not implemented — a stated gap in [`child-safety.md`](child-safety.md) |
| Guardian-consent flow for youth participation | — | `ConsentRecord` has a guardian read path in RLS | wali | `consent_records` | — | — | Not implemented — deliberately deferred; see [`child-safety.md`](child-safety.md) |

## 15. Community spaces

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Nine community kinds | `CommunityScreen`, `CommunityDetailScreen` | `CommunityKind` | all | `communities` | Private communities invisible to non-members | RLS suite | Implemented, untested |
| Four membership policies | `CommunityDetailScreen` | `MembershipPolicy` | all | `community_members` | You cannot make yourself a community moderator | RLS suite | Implemented, untested |
| Community rules | `CommunityDetailScreen` | `CommunityRule` | all | `community_rules` | Visible wherever the community is | — | Implemented, untested |
| Community safeguard floor, applied only inside that space | — | `Community.safeguardFloor`, `contextualFloorsFor` | all | `communities.safeguard_floor` | A youth programme's floor does not follow a volunteer into an unrelated thread | `a community floor applies inside that community's space`; `a community floor does not follow its volunteers into unrelated conversations` (`SafeguardFloorScopeTest`) | Implemented and tested |
| Organisation safeguard floor, which does travel | — | `Organization.safeguardFloor`, `floorsFor` | all | `organizations.safeguard_floor` | Affiliation is not situational | `an organisation floor travels with its members` (`SafeguardFloorScopeTest`) | Implemented and tested |
| Community moderators | `CommunityDetailScreen` | `CommunityMemberRole.canModerate`; `OversightDirectory.communityModerator` | moderator | `community_members` | Preferred over a general moderator when oversight is needed | — | Implemented, untested |
| Group context as a cross-gender safeguard | `CommunityDetailScreen`, `ComposeConversationScreen` | `CrossGenderConversationStructure.GROUP_CONTEXT_ONLY` | all | `communities` | Refused without a shared community | `group-context-only requires a shared community` (`ContactPolicyTest`) | Implemented and tested |
| Creating or joining a community from the app | `CreateCommunityScreen` | `CreateCommunityUseCase`, `CommunityMembershipUseCase` | all | `communities`, `community_members` | Gender arrangement enforced at the door; leaving is unconditional | 5 assertions in `AuthoringAndAdministrationTest` | Implemented and tested |
| Community announcements | — | `NotificationKind.COMMUNITY_ANNOUNCEMENT` | organiser | `notifications` | — | — | Not implemented *(the notification kind exists; nothing emits it)* |

## 16. Profile design

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| No field for appearance, follower count or popularity | `ProfileScreen`, `MemberProfileScreen` | `Profile` | all | `profiles` | Every field answers "can this person help, or be helped, with this?" | `no publicly visible type carries a vanity metric` (`ProductPrincipleTest`) | Implemented and tested |
| Contribution statement, capped at 600 characters | `EditProfileScreen` | `Profile.init` | all | `profiles` | About beneficial work, not about the person | — | Implemented, untested |
| Skills with self-declared proficiency, labelled as self-declared | `EditProfileScreen`, `MemberProfileScreen` | `UserSkill`, `SkillProficiency` | all | `user_skills` | Self-endorsement trigger-guarded in the database | RLS suite | Implemented, untested |
| Languages, availability windows, time zone | `EditProfileScreen` | `Language`, `Availability`, `AvailabilityWindow` | all | `profiles` | Drives quiet-hours evaluation | `quiet hours block new conversations and wrap past midnight` (`ContactPolicyTest`) | Implemented and tested |
| Areas willing to help / seeking help | `EditProfileScreen` | `Profile.areasWillingToHelp` / `areasSeekingHelp` | all | `profiles` | Drives the home digest's matching, which does not learn from taps | `the home digest is about the member's own obligations, not other people's activity` (`DiscoveryBehaviourTest`) | Implemented and tested |
| The viewer only ever receives `VisibleProfile`, never `Profile` | `MemberProfileScreen` | `VisibilityPolicy.visibleProfile` | all | — | A screen cannot accidentally render a real name; the field is absent | `a real name is withheld when the member shows a display name only` (`VisibilityPolicyTest`) | Implemented and tested |
| Contactability shown up front, before a long message is written | `MemberProfileScreen` | `Contactability`, `SearchPeopleUseCase.contactability` | all | — | A dry run of the contact gate | `4. a member cannot obtain another person's wali contact details` (`CriticalFlowsTest`) | Implemented and tested |
| Four profile image styles; photograph never required | `EditProfileScreen` | `ProfileImageStyle` | all | `profiles` | Downgraded to initials for a viewer who is not permitted the image | `a photograph is not shown to the opposite gender when set to same gender only` (`VisibilityPolicyTest`) | Implemented and tested |
| Image upload and storage | `EditProfileScreen` | `Profile.imageUrl` | all | `profiles` | — | — | Not implemented *(a URL field with no upload path and no storage bucket)* |
| Data export | `AccountDataScreen` | `ExportMyDataUseCase` | all | `audit_logs` (`DATA_EXPORTED`) | States what it deliberately omits | `an export says what it contains and what it deliberately does not` | Implemented and tested |
| Deletion request | `AccountDataScreen` | `RequestAccountDeletionUseCase` | all | `profiles.status` | States what survives and why; cancellable inside the grace period | `deletion is requested, states what survives, and can be cancelled` | Implemented and tested |

## 17. Security requirements

| Feature | Screen | Component | Roles | Data entity | Permission / safeguard rule | Test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Typed identifiers so one entity's id cannot be passed as another's | — | 26 `@JvmInline value class` ids (`core/model/.../Ids.kt`) | — | — | The compiler is the cheapest reviewer available | compile-time | Implemented and tested |
| Authorisation decided in the use case and enforced again by RLS | — | `Principal`; 162 policies in `0013_row_level_security.sql` | all | every table | A repository is a store, not a gatekeeper | 130 assertions in `rls_tests.sql` | Implemented and tested |
| Never trusting client-supplied roles or verification | `OnboardingProfileScreen` | `CompleteOnboardingUseCase` overwrites both | all | `user_roles`, `user_verifications` | Sanitised in Kotlin, rejected again by trigger | `8. a member cannot give themselves verification or scholar status` (`CriticalFlowsTest`) | Implemented and tested |
| Five append-only tables with UPDATE and DELETE revoked | — | RLS grants | all | `audit_logs`, `report_evidence`, `moderation_actions`, `message_redactions`, `wali_contact_disclosures` | Absolutely, including for administrators | `6b. audit records cannot be altered or removed through any interface` (`CriticalFlowsTest`) | Implemented and tested |
| `FORCE ROW LEVEL SECURITY` on the sensitive tables | — | `0013_row_level_security.sql` | all | `user_safeguards`, `trusted_contacts`, `messages`, `reports`, … | The table owner is not exempt | RLS suite | Implemented and tested |
| Guardian contact columns SELECT-revoked from every role | — | `wali_profiles`; `app.get_wali_contact(...)` | all | `wali_profiles` | A definer function is the only reader, and it writes a ledger row | `4. a member cannot obtain another person's wali contact details` (`CriticalFlowsTest`) | Implemented and tested |
| Blocks are symmetric and invisible to the blocked person | — | `blocks` policies; `ContactPolicy` | all | `blocks` | `SELECT` is blocker-only | `the block message reveals nothing about why` (`ContactPolicyTest`) | Implemented and tested |
| On-device safety checks — nothing is sent anywhere to produce them | `ConversationScreen` | `ContentSignals` in `:core:policy` | all | — | Cost: the rules are visible in a decompiled app. Benefit: no message leaves the device to be scored | `the disclosure describes exactly what the checks do` (`ContentSignalsTest`) | Implemented and tested |
| No floating point anywhere near money | — | `Money(minorUnits: Long, currencyCode)` | all | `donations`, `campaigns` | Cannot be negative; currencies cannot be mixed | `every preset produces a usable set of safeguards` covers construction; `Money.init` throws | Implemented, untested |
| Authentication | `SignInScreen`, `SignUpScreen`, `AccountRecoveryScreen` | `core:auth` (`AuthGateway`, `MemberSession`), `SessionManager` | all | `auth.users`, `public.register_member()`, `public.current_member()` | Sign-up, sign-in and recovery all refuse to reveal whether an address has an account | 26 tests in `core/auth`, 20 SQL assertions in sections 16–19 | Implemented and tested — see [`authentication.md`](authentication.md) |
| Refresh token at rest | — | `KeystoreSessionStore` | all | — | AES-256-GCM under a hardware-backed key; the access token is never written to disk | Not unit-tested (Android keystore is not available off-device) | Implemented, untested |
| Session and device management | `DeviceSessionsScreen` | `DeviceSessionUseCase` | all | `device_sessions` | End one, or end everything else | 3 assertions in `RecognitionTest` | Implemented and tested |
| Transport security to a real backend | — | — | — | — | — | — | Not implemented *(the Android app talks to `InMemoryStore`; no network layer exists)* |

## 18. Screen inventory

The product specification's §44 screen list is not a file in this repository, so this
inventory is taken from `Routes.kt` — the codebase's own enumeration of screens — and
cross-checked against the files under `ui/screens/`. All **62** declared routes have a
`composable(...)` in `FiSabilillahNavHost.kt` and a matching `@Composable` function; that
correspondence is checked mechanically, not by eye, and one route (`REVIEW_APPEAL`) was
deleted rather than listed, because nothing navigated to it. Screens the product areas
above imply but which no route declares are listed as **Not implemented** at the end.

| Screen | Route | File | Status |
| --- | --- | --- | --- |
| Landing | `landing` | `onboarding/LandingScreen.kt` | Implemented, untested |
| Mission | `mission` | `onboarding/MissionScreen.kt` | Implemented, untested |
| Sign in | `sign-in` | `onboarding/SignInScreen.kt` | Implemented, untested |
| Sign up | `sign-up` | `onboarding/SignUpScreen.kt` | Implemented, untested |
| Account recovery | `account-recovery` | `onboarding/AccountRecoveryScreen.kt` | Implemented, untested |
| Onboarding — profile | `onboarding/profile` | `onboarding/OnboardingScreens.kt` | Implemented, untested |
| Onboarding — skills | `onboarding/skills` | `onboarding/OnboardingScreens.kt` | Implemented, untested |
| Onboarding — safeguards | `onboarding/safeguards` | `onboarding/OnboardingScreens.kt` | Implemented, untested |
| Onboarding — consent | `onboarding/consent` | `onboarding/OnboardingScreens.kt` | Implemented, untested |
| Home | `home` | `home/HomeScreen.kt` | Implemented, untested |
| Serve | `serve` | `serve/ServeScreen.kt` | Implemented, untested |
| Opportunity detail | `opportunity/{id}` | `serve/OpportunityDetailScreen.kt` | Implemented, untested |
| Create listing | `create/listing` | `serve/CreateListingScreen.kt` | Implemented, untested |
| Learn | `learn` | `learn/LearnScreen.kt` | Implemented, untested |
| Learning detail | `learning/{id}` | `learn/LearningDetailScreen.kt` | Implemented, untested |
| Requests | `requests` | `requests/RequestsScreen.kt` | Implemented, untested |
| Request detail | `request/{id}` | `requests/RequestDetailScreen.kt` | Implemented, untested |
| Create request | `create/request` | `requests/CreateRequestScreen.kt` | Implemented, untested |
| Projects | `projects` | `projects/ProjectsScreen.kt` | Implemented, untested |
| Project detail | `project/{id}` | `projects/ProjectDetailScreen.kt` | Implemented, untested |
| Community | `community` | `community/CommunityScreen.kt` | Implemented, untested |
| Community detail | `community/{id}` | `community/CommunityDetailScreen.kt` | Implemented, untested |
| Organisation detail | `organization/{id}` | `community/OrganizationDetailScreen.kt` | Implemented, untested |
| Member profile | `member/{id}` | `community/MemberProfileScreen.kt` | Implemented, untested |
| Messages | `messages` | `messages/MessagesScreen.kt` | Implemented, untested |
| Conversation | `conversation/{id}` | `messages/ConversationScreen.kt` | Implemented, untested |
| Compose conversation | `compose/{recipientId}` | `messages/ComposeConversationScreen.kt` | Implemented, untested |
| Profile | `profile` | `profile/ProfileScreen.kt` | Implemented, untested |
| Edit profile | `settings/profile` | `profile/EditProfileScreen.kt` | Implemented, untested |
| Service history | `profile/history` | `profile/ServiceHistoryScreen.kt` | Implemented, untested |
| Account data — export and deletion | `settings/account-data` | `profile/AccountDataScreen.kt` | Implemented, untested |
| Where you are signed in | `settings/devices` | `moderation/AdministrationScreens.kt` | Implemented, untested |
| Your verification | `trust/verification` | `trust/TrustScreens.kt` | Implemented, untested |
| Ask to be verified | `trust/verification/request` | `trust/TrustScreens.kt` | Implemented, untested |
| Your qualifications | `trust/qualifications` | `trust/TrustScreens.kt` | Implemented, untested |
| Trust review queue | `moderation/trust` | `trust/TrustScreens.kt` | Implemented, untested |
| Your commitments | `commitments` | `profile/CommitmentScreens.kt` | Implemented, untested |
| Say how it went | `commitments/endorse/{id}` | `profile/CommitmentScreens.kt` | Implemented, untested |
| Teach a class | `create/class` | `create/AuthoringScreens.kt` | Implemented, untested |
| Start a project | `create/project` | `create/AuthoringScreens.kt` | Implemented, untested |
| Start a community | `create/community` | `create/AuthoringScreens.kt` | Implemented, untested |
| Roles | `admin/roles` | `moderation/AdministrationScreens.kt` | Implemented, untested |
| Safeguard settings | `settings/safeguards` | `safety/SafeguardSettingsScreen.kt` | Implemented, untested |
| Privacy controls | `settings/privacy` | `safety/PrivacyControlsScreen.kt` | Implemented, untested |
| Trusted contacts | `settings/trusted-contacts` | `safety/TrustedContactsScreen.kt` | Implemented, untested |
| Wali settings | `settings/wali` | `safety/WaliSettingsScreen.kt` | Implemented, untested |
| Submit introduction | `introduction/submit/{recipientId}` | `safety/SubmitIntroductionScreen.kt` | Implemented, untested |
| Introduction detail | `introduction/{id}` | `safety/IntroductionDetailScreen.kt` | Implemented, untested |
| Safety centre | `safety` | `safety/SafetyCentreScreen.kt` | Implemented, untested |
| My reports | `safety/reports` | `safety/MyReportsScreen.kt` | Implemented, untested |
| Your moderation record | `safety/restrictions` | `safety/AppealScreens.kt` | Implemented, untested |
| Submit an appeal | `safety/appeal/{caseId}` | `safety/AppealScreens.kt` | Implemented, untested |
| Report | `report/{targetType}/{targetId}` | `safety/ReportScreen.kt` | Implemented, untested |
| Notifications | `notifications` | `safety/NotificationsScreen.kt` | Implemented, untested |
| Appeal queue | `moderation/appeals` | `safety/AppealScreens.kt` | Implemented, untested |
| Moderator dashboard | `moderation` | `moderation/ModeratorDashboardScreen.kt` | Implemented, untested |
| Moderation case | `moderation/case/{id}` | `moderation/ModerationCaseScreen.kt` | Implemented, untested |
| Admin dashboard | `admin` | `moderation/AdminDashboardScreen.kt` | Implemented, untested |
| Terms | `legal/terms` | `legal/StaticScreens.kt` | Implemented, untested |
| Privacy policy | `legal/privacy` | `legal/StaticScreens.kt` | Implemented, untested |
| Community guidelines | `legal/guidelines` | `legal/StaticScreens.kt` | Implemented, untested |
| Giving compliance | `legal/giving` | `legal/StaticScreens.kt` | Implemented, untested |
| **Live session lobby / room** | — | — | **Not implemented** |
| **Schedule a live session** | — | — | **Not implemented** |
| **Campaign list / detail / donate** | — | — | **Not implemented** |
| **People search** | — | — | **Not implemented** *(`SearchPeopleUseCase` and `PeopleViewModel` exist and are wired into `MEMBER_PROFILE`, but no route lists or searches people — deliberate, per the "no browse surface" principle, and worth recording as a decision rather than an omission)* |

---

## What is genuinely missing

Everything the product specification describes is now implemented except the items below.
Three of them are **deliberate deferrals** rather than gaps, and are marked as such — each
one is a case where shipping the feature would be worse than not having it.

| # | Missing | Why, and what it would take |
| --- | --- | --- |
| 1 | **The content layer still talks to `InMemoryStore`.** Authentication reaches Supabase; opportunities, requests, conversations, classes, projects and communities do not, so the 162 row-level security policies still guard nothing the app reads. | Supabase-backed implementations of the repository interfaces in `core/domain/.../Repositories.kt`. The ports were designed for this and do not need to change; `core:auth` already supplies the authenticated token every call will need. This is infrastructure work rather than product work, and it is the single largest remaining item. |
| 2 | **Live-session media transport.** The domain model, the safeguard gating and the interface are complete and tested; no audio or video can be carried. | **Deliberate.** See the checklist in [`live-sessions.md`](live-sessions.md). Seven items, of which the abuse-reporting path for live audio and the legal position on recording per jurisdiction are the two that cannot be engineered around. `LiveSessionFeatureFlags.transportConfigured` stays false until they are answered. |
| 3 | **Donations and payments.** Campaigns are readable; nothing takes money. | **Deliberate.** See [`payment-compliance.md`](payment-compliance.md). Taking donations needs a payment provider, charity-registration checks, and a position on zakat eligibility that is a scholarly question rather than an engineering one. |
| 4 | **Youth participation and age assurance.** | **Deliberate.** See [`child-safety.md`](child-safety.md). It will not open until guardian consent, verified-organisation-only activities and background checks on the adults involved are all in place. Opening it sooner would put a feature ahead of children's safety. |
| 5 | **Document and image upload.** Verification evidence, qualification documents and profile photographs are all stored as references with no upload path and no storage bucket. | A storage bucket with per-object policies, an upload path, and a decision about scanning. The reference plumbing is in place at both ends. |
| 6 | **Nothing drives the scheduler.** `RunScheduledMaintenanceUseCase` exists, is tested and archives conversations and lapses introductions — but no cron, worker or edge function calls it. | A scheduled trigger in the deployment. Deliberately not the client: neither expiry should depend on a member opening the app. |
| 7 | **Campaign creation and donation screens.** | Follows item 3. The models and RLS policies exist. |
| 8 | **Organisation creation and verification.** Organisations are readable and are created only by the seed. | A use case and screens, plus a document-review path that follows item 5. |
| 9 | **No people-browsing surface.** | **Deliberate**, per the "no browse surface" principle — `SearchPeopleUseCase` exists and is reachable only from a profile you already have a reason to open. Recorded as a decision rather than an omission. |

## What is claimed but unverified

**Nothing in the Android application has ever been run.** It compiles — GitHub Actions
assembles a debug APK on every push to the branch, and that build is green — but compiling
is the whole of the evidence. No test opens a screen, and no human has used the app. That
single fact qualifies every row in this matrix whose `Screen` column names a screen.

What this means precisely:

- Every screen row in [§18](#18-screen-inventory) is marked **Implemented, untested**. A
  more exact reading is *"the file compiles and declares a `@Composable` the navigation
  graph reaches"*. Whether it renders correctly, or behaves correctly when it does, is
  unknown.
- The same qualification runs through every earlier section. Where a row's `Screen` column
  names a screen and its `Status` says **Implemented and tested**, the *test* is a JVM test
  over `:core:policy`, `:core:data` or `:core:auth`. It proves the rule. It proves nothing
  about the screen.
- There are **no Android tests of any kind** — no unit tests, no Compose UI tests, no
  instrumentation tests. `androidApp/app/src/test/java/org/fisabilillah/app/` is an empty
  directory tree.
- Every `viewModel(factory = …)` call, every `collectAsState()`, and every navigation
  argument parse type-checks and is otherwise unverified.
- **Authentication has never completed a real round trip from this repository.** `core:auth`
  is tested against a fake transport, 26 tests, covering the wire format and the refresh
  logic. The container this was built in cannot reach `*.supabase.co`, so no live sign-up,
  no live sign-in, and no live token refresh has been observed. The server half is verified
  the other way round — by SQL, against the live project and against a local PostgreSQL 16
  cluster.

What *is* verified:

- The 303 JVM tests over `:core:policy`, `:core:data` and `:core:auth` pass, and were read
  from `core/*/build/test-results/test/*.xml` for this document rather than taken on trust.
- The 130 database assertions pass against PostgreSQL 16 via
  `backend/supabase/run_local_tests.sh`, with the full migration set applied to a throwaway
  database.
- All 18 migrations are applied to the live Supabase project, and its advisors report no
  security findings beyond two pre-existing `extension_in_public` warnings for `citext` and
  `pg_trgm`.
- Everything in `core/` compiles, because its tests run. The Android module compiles,
  because CI assembles it.

One further caveat specific to §7. `core/domain/.../LiveSessionUseCases.kt` is tracked and
compiles, but nothing constructs it: it is not referenced by `CoreGraph`, no repository in
`:core:data` implements `LiveSessionRepository`, and no screen calls it. Rows citing it are
**Model and UI only** for that reason and not merely for want of media transport.

### The specification's own condition

> *"Do not begin visual polish until this matrix confirms that no original feature has been
> lost."*

This matrix now comes close to confirming it, and should not be read as confirming it. Of
the sixteen **Not implemented** rows, twelve are decisions recorded as decisions —
donations, youth participation, live-session media, people-browsing — and four are work
that follows from them. The condition that actually blocks polish is item 1 in [What is
genuinely missing](#what-is-genuinely-missing): the app reads from an in-memory store, so
the 162 row-level security policies protect a database the client never opens. Polishing
the spacing of a screen whose data layer is about to be replaced is work done twice.

---

## Related

- [`live-sessions.md`](live-sessions.md) — the live voice and video feature in full.
- [`safeguards.md`](safeguards.md) — the safeguard system, for a non-technical reader.
- [`wali-workflow.md`](wali-workflow.md) — the introduction process end to end.
- [`child-safety.md`](child-safety.md) — the adults-only position and its known gaps.
- [`privacy-model.md`](privacy-model.md) — what is collected, kept, and released.
- [`testing.md`](testing.md) — how to run the suites (its totals predate the live-session tests).
- [`backend/docs/rls-model.md`](../backend/docs/rls-model.md) — the per-table permission grid.
- [`backend/docs/data-model.md`](../backend/docs/data-model.md) — the entities named in every `Data entity` cell.
