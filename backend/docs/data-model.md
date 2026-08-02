# Fi Sabilillah — data model

58 tables, 3 views, one `app` helper schema. Target is Supabase Postgres
(`auth.users`, `auth.uid()`, Storage object paths); the same migrations apply
unchanged to plain PostgreSQL 16 with the shim in
`supabase/tests/00_bootstrap.sql`.

## What this schema is, and is not

Fi Sabilillah is a **service-oriented community platform**: volunteering,
Islamic learning, mutual aid, community projects, purpose-bound messaging, and
a wali-centred marriage-introduction workflow.

The schema deliberately **cannot represent** several things a dating app or a
social network would need:

| Absent by design | Why |
| --- | --- |
| A follower / friend graph | There is no social graph to farm, and no feed to optimise for engagement. |
| A generic inbox or "message anyone" surface | Every conversation must have a row in `conversation_purposes`; a deferrable constraint trigger refuses to commit a conversation without one. |
| A public leaderboard of good deeds | `private_impact_records` has `check (is_visible_to_self_only = true)` and an owner-only policy. There is no path to publish it. |
| Enabled payments | `campaigns` carries `check (payments_enabled = false)`. No card, token, or bank field exists anywhere. |
| Browsable singles | `formal_introduction_settings.is_open_to_introductions` defaults to **false**, and `visible_to` defaults to `wali_referral_only`. |
| A hard-delete path for evidence | `audit_logs`, `report_evidence`, `moderation_actions`, `message_redactions` and `wali_contact_disclosures` have no UPDATE/DELETE policy and no UPDATE/DELETE grant. |

## Conventions applied to every table

* `id uuid primary key default gen_random_uuid()` — except `profiles`, whose
  primary key **is** `auth.users.id`.
* `created_at timestamptz not null default now()`
* `updated_at timestamptz not null default now()`, maintained by the shared
  `app.set_updated_at()` trigger (attached via `app.attach_updated_at()`).
  `audit_logs` is the one exception: it is append-only, so it carries no
  update trigger.
* `deleted_at timestamptz` on anything a member can retire without erasing
  history: `profiles`, `organizations`, `communities`, `learning_offerings`,
  `volunteer_opportunities`, `service_requests`, `projects`, `conversations`,
  `campaigns`, `wali_profiles`, `formal_introduction_requests`.
* Emails use `citext` (`profiles.contact_email`, `organizations.public_email`,
  `wali_profiles.contact_email`, `trusted_contacts.contact_email`,
  `user_safeguards.guardian_notify_email`).
* Ownership columns are always explicit: `user_id`, `requester_id`,
  `teacher_id`, `created_by`, `lead_id`, `sender_id`, `donor_id`, …

## Migrations

| File | Contents |
| --- | --- |
| `0001_extensions_and_helpers.sql` | `pgcrypto`, `citext`, `pg_trgm`; the `app` schema; every RLS helper function. |
| `0002_enums_and_lookups.sql` | 27 enums + 6 lookup tables. |
| `0003_identity_profiles_safeguards.sql` | `profiles`, `user_settings`, `safeguard_presets`, `user_safeguards`, `user_roles`. |
| `0004_organizations_communities.sql` | Organizations, their private roster and verifications; communities and rules. |
| `0005_skills_learning_volunteering.sql` | `user_skills`, learning offerings/enrolments, volunteer opportunities/applications. |
| `0006_requests_projects_commitments.sql` | Mutual aid (with the location split), projects, tasks, commitments. |
| `0007_messaging_purposes.sql` | Conversations, purposes, members, messages, `message_redactions`. |
| `0008_wali_formal_introduction.sql` | Trusted contacts, wali profiles, introduction settings/requests/participants, disclosure ledger. |
| `0009_trust_verification.sql` | `user_verifications`, `qualifications`. |
| `0010_moderation_reports_appeals.sql` | Reports, evidence, cases, actions, appeals, blocks, restrictions, safety incidents. |
| `0011_donations_campaigns.sql` | Campaigns, campaign verifications, donation records. |
| `0012_notifications_audit_consent.sql` | Notifications, private impact records, audit log, consent receipts. |
| `0013_row_level_security.sql` | RLS on every table, 162 policies, grants/revokes, the safe views. |
| `0014_functions_triggers.sql` | Guard triggers, evidence-preservation triggers, audit triggers, privileged entry points. |
| `0015_indexes.sql` | Foreign-key, RLS-predicate and product-query indexes. |

## Enums vs lookup tables

**Enums** are used where the value set is *structural*: policies, triggers and
application logic branch on specific values, so adding one is a schema change
plus a code change.

`gender`, `verification_level`, `verification_status`, `report_category`,
`report_status`, `moderation_case_status`, `moderation_action_type`,
`appeal_status`, `conversation_purpose_kind`, `introduction_status`,
`membership_status`, `org_member_role`, `application_status`,
`enrollment_status`, `request_status`, `commitment_status`, `restriction_type`,
`task_status`, `project_status`, `campaign_status`, `donation_status`,
`notification_channel`, `community_visibility`, `consent_type`,
`incident_severity`, `trust_relationship`, `offering_format`, `gender_policy`.

`gender` has exactly two values because the introduction workflow is built
around Islamic marriage rulings, which are gender-structural. It is used for
the `gender_policy` matching of offerings, opportunities and requests — not as
a profile decoration.

**Lookup tables** are used where admins and scholars are expected to add rows
over time and no policy branches on a specific row:

`roles`, `skills`, `service_categories`, `learning_subjects`,
`community_rule_templates`, `qualification_types`. (`safeguard_presets` is
admin-curated in the same spirit.)

## Domain areas

### Identity and safeguards

`profiles` is intentionally thin. `profiles.verification_level` is **derived**:
it is recomputed by `app.sync_profile_verification_level()` from
`user_verifications` and rejected by `app.guard_profile_verification_level()`
if anyone tries to write it directly.

`user_safeguards` holds the member's own boundaries — purpose requirement,
gender interaction policy, minimum counterparty verification, wali requirement
for introductions, guardian notification address. Every insert or update writes
an audit row, because *loosening* a safeguard is a safety-relevant event.

`user_roles` references the `roles` lookup. Rows flagged `is_privileged` can
never be self-granted: there is a `check (granted_by <> user_id)`, an
admin-only INSERT policy, and `app.guard_role_escalation()` on top.

### Organizations and communities

`organizations` is a public directory row. Everything operational —
`organization_members` (including safeguarding clearance) and
`organization_verifications` (including evidence paths) — is private to that
one organization. `app.is_org_admin(org uuid)` is scoped to a single
organization id and never yields cross-organization visibility.

`organizations.verification_level` is derived from
`organization_verifications` by trigger, exactly like the profile equivalent.

### Learning and volunteering

Two safeguarding invariants are enforced as table constraints, not as
application code:

```sql
constraint learning_offerings_youth_guardian
  check (not is_youth_offering or requires_guardian_consent)
constraint volunteer_opportunities_youth_clearance
  check (not is_youth_facing or requires_safeguarding_clearance)
constraint projects_youth_clearance
  check (not is_youth_involved or requires_safeguarding_clearance)
```

### Mutual aid and the location split

`service_requests` carries only `approximate_area` and coarse
`approximate_latitude/longitude`. The doorstep lives in a separate 1:1 table:

```
service_requests                          service_request_private_details
  approximate_area                          exact_address
  approximate_latitude/longitude            latitude / longitude
  assigned_helper_id                        access_notes
  helper_accepted_at                        contact_phone
                                            household_notes
```

`app.can_see_exact_location(request)` returns true only for the requester, the
helper whose offer was **accepted**, and moderators. The public view
`service_requests_public` cannot leak it: the columns are not in the view's
source table at all.

### Messaging

A conversation is created, then a `conversation_purposes` row is inserted in
the same transaction. Two independent mechanisms enforce this:

1. `trg_conversations_require_purpose` — a `deferrable initially deferred`
   constraint trigger, so a conversation can never be *committed* bare.
2. `trg_messages_require_purpose` — a non-deferred BEFORE INSERT trigger on
   `messages`, so a message can never be written into a purposeless channel
   even mid-transaction. The `messages` INSERT policy checks it a third time.

`conversation_members.member_role` includes `wali_observer`, constrained to
`can_write = false`: a guardian supervises an introduction channel without
posting in it.

**Unsend does not destroy evidence.** Setting `messages.unsent_at` fires
`app.preserve_message_on_redaction()`, which copies the original body and
attachment path into `message_redactions` (moderator-read, append-only) and
then nulls the participant-visible columns.

### Wali and formal introductions

```
profiles ──1:1──> formal_introduction_settings ──> wali_profiles
                                  │                     │
                                  ▼                     │
                  formal_introduction_requests <────────┘
                                  │
                                  ▼
                  formal_introduction_participants
                                  │
                                  ▼
                       wali_contact_disclosures   (append-only ledger)
```

`introduction_status` has exactly one state that unlocks contact disclosure:
`approved_and_forwarded` (and its successor `in_correspondence`). Reaching it
requires `app.forward_introduction()`, which checks that both walis exist and
have recorded a decision, that the caller is a wali of the introduction or a
moderator, and that the current status is `counterpart_wali_review`. A plain
UPDATE into that status is refused by `app.guard_introduction_status()`.

`wali_profiles.contact_email` and `contact_phone` are protected at three
levels — see `rls-model.md`.

### Trust and verification

`user_verifications` and `qualifications` are *claims until someone else says
otherwise*. Members may submit; only `app.decide_user_verification()` and
`app.verify_qualification()` may record an outcome, and guard triggers reject
direct writes to `status`, `verified_at`, `verified_by`, `reviewed_by`.

### Moderation

`reports` → `report_evidence` → `moderation_cases` → `moderation_actions` →
`restrictions` / `appeals`, with `safety_incidents` for anything escalated
beyond the platform.

Every `moderation_actions` insert fires `app.audit_moderation_action()`, which
writes an `audit_logs` row. There is no code path that records an action
without an audit entry.

`moderation_cases.second_reviewer_id` supports four-eyes sign-off, and
`app.guard_appeal_reviewer()` refuses to let the moderator who took an action
review the appeal against it.

### Giving

`donations` records giving settled **elsewhere**. There is no payment
instrument column anywhere in this schema, and `campaigns.payments_enabled` is
pinned to false by a CHECK constraint.

`campaigns.zakat_eligible` is guarded by three independent mechanisms:

1. `check (zakat_eligible = false or zakat_attestation_id is not null)` — the
   flag cannot exist without pointing at a `campaign_verifications` row;
2. `app.guard_campaign_zakat()` — rejects any INSERT or UPDATE that raises the
   flag from an ordinary session;
3. `app.set_campaign_zakat_eligible()` — requires a moderator or listed
   scholar, a verified owning organization, and a *verified* attestation of
   kind `scholarly_zakat_attestation` or `organization_zakat_attestation`.

## Views

| View | Purpose |
| --- | --- |
| `service_requests_public` | Approximate location only; the exact-address table is not joined and cannot be reached from here. |
| `volunteer_opportunities_public` | Listing without `exact_location_note`. |
| `wali_profiles_redacted` | Contact-free projection of `wali_profiles`; deliberately does not even project a "has an email" boolean, because the SELECT privilege on those columns is revoked. |

All three are `security_invoker = true`, so the caller's own RLS still applies
— a view is never a privilege-escalation hole here.

## Helper functions (`app` schema)

All are `security definer` with `set search_path = ''` and fully qualified
names, and are `stable` where correct.

| Function | Volatility | Purpose |
| --- | --- | --- |
| `app.current_user_id()` | stable | `auth.uid()`, NULL when unauthenticated. |
| `app.is_service_role()` | stable | service key or superuser context. |
| `app.has_role(text)` | stable | live, unexpired, unrevoked role grant. |
| `app.is_moderator()` / `app.is_platform_admin()` / `app.is_scholar()` | stable | staff predicates. |
| `app.is_account_active(uuid)` | stable | profile exists, not soft-deleted, not fully suspended. |
| `app.is_restricted(uuid, text)` | stable | live restriction of a kind (or a full suspension). |
| `app.is_blocked_between(uuid, uuid)` | stable | symmetric block test. |
| `app.is_conversation_member(uuid)` | stable | active membership. |
| `app.conversation_has_purpose(uuid)` | stable | purpose exists. |
| `app.conversation_blocked_for(uuid, uuid)` | stable | any block with any other member. |
| `app.can_open_conversation(uuid, text)` | stable | full pre-flight, including the introduction rule. |
| `app.is_org_member(uuid)` / `app.is_org_admin(uuid)` | stable | single-organization scope. |
| `app.is_community_member(uuid)` / `app.is_community_moderator(uuid)` | stable | community scope. |
| `app.is_project_member(uuid)` / `app.is_project_lead(uuid)` | stable | breaks the projects ↔ project_members policy cycle. |
| `app.is_commitment_owner(uuid)` / `app.has_confirmed_commitment(uuid)` | stable | breaks the commitments ↔ confirmations cycle. |
| `app.is_open_to_introductions(uuid)` | stable | reads a private settings row on behalf of a policy. |
| `app.is_introduction_principal/participant/…(uuid)` | stable | breaks the introduction ↔ wali policy cycle. |
| `app.can_view_wali_profile(uuid)` | stable | row visibility only; contact columns stay revoked. |
| `app.can_see_exact_location(uuid)` | stable | the doorstep rule. |
| `app.is_privileged_caller()` | stable, **invoker** | true only inside a definer function or for service_role. |
| `app.write_audit(...)` | volatile | append-only audit write. |
| `app.grant_role(...)` | volatile | admin-only role grant. |
| `app.decide_user_verification(...)` / `app.verify_qualification(...)` | volatile | the only writers of verification outcomes. |
| `app.record_moderation_action(...)` | volatile | moderator-only; forces an audit row. |
| `app.set_campaign_zakat_eligible(...)` | volatile | the only writer of the zakat flag. |
| `app.forward_introduction(uuid)` | volatile | the only path to contact authorization. |
| `app.get_wali_contact(uuid)` / `app.get_my_wali_contact()` | volatile / stable | the only readers of wali contact details. |
| `app.notify(...)` | volatile | the only writer of `notifications`. |

### Why `app.is_privileged_caller()` is SECURITY INVOKER

Guard triggers need to distinguish "a privileged function is doing this" from
"a member typed this UPDATE". A session GUC flag would be worthless — the
attacker can simply `set_config()` it before their statement. Instead
`app.is_privileged_caller()` is a **security invoker** function reading the
real `current_user`, which is the definer function's owner inside a privileged
call and `authenticated` in a direct client request. All guard triggers are
therefore security invoker too.
