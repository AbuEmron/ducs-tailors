# Fi Sabilillah — row level security model

**58 tables, 162 policies, RLS enabled on every single table.** Migration
`0013_row_level_security.sql` ends with a `DO` block that raises if any table
in `public` still has `relrowsecurity = false`, and
`tests/rls_tests.sql` re-asserts it (plus "every table has at least one
policy") on every run.

## Roles

| Role | Meaning |
| --- | --- |
| `anon` | Signed out. Reads the lookup vocabulary and three public directories. Nothing else. |
| `authenticated` | A signed-in member. `auth.uid()` identifies them; every policy is written from their point of view. |
| `service_role` | Server-side jobs and edge functions. Bypasses RLS (Supabase) — treat the key as a production secret. |
| moderator / platform_admin / scholar | **Not** database roles. They are rows in `user_roles`, tested by `app.is_moderator()` etc. inside policies. |

## Three defence layers

1. **Privileges** — `revoke`/`grant` decide whether the statement is even
   allowed to be attempted. Used for append-only tables and for the two
   protected `wali_profiles` columns. Violations raise `42501`.
2. **Policies** — decide which rows are visible/writable. A missing policy
   means "no rows", which is silent; that is why layer 1 backs it up wherever
   silence would be dangerous.
3. **Guard triggers** — catch privileged-field writes that a policy cannot
   express (a field-level change by someone who legitimately owns the row).

## Policy table

Legend: **self** = the owning user; **mod** = moderator or platform admin;
**org admin** = admin/owner of *that one* organization; **—** = no policy for
ordinary roles; **fn** = only through a `security definer` function.

| Table | SELECT | INSERT | UPDATE | DELETE | Invariant enforced |
| --- | --- | --- | --- | --- | --- |
| `roles` | anyone | admin | admin | admin | Role vocabulary is public knowledge; only admins extend it. |
| `skills` | anyone (active) | admin | admin | admin | Curated catalogue. |
| `service_categories` | anyone (active) | admin | admin | admin | Curated catalogue. |
| `learning_subjects` | anyone (active) | admin | admin | admin | Curated catalogue. |
| `community_rule_templates` | anyone (active) | admin | admin | admin | Communities can adopt a template verbatim. |
| `qualification_types` | anyone (active) | admin | admin | admin | Curated catalogue. |
| `safeguard_presets` | anyone (active) | admin | admin | admin | A member must be able to read a preset before adopting it. |
| `profiles` | self, mod, or live+searchable+**not blocked either way** | self only (`id = auth.uid()`) | self, mod | — (soft delete) | You are one profile, and a block makes both parties invisible to each other. |
| `user_settings` | self | self | self | self | Preferences are private; **not even moderators** read them. FORCE. |
| `user_safeguards` | self | self | self | self | Your boundaries are yours. Every change is audited. FORCE. |
| `user_roles` | self, mod | **admin only** | admin only | admin only | No self-service privilege. Backed by `trg_user_roles_no_escalation`. |
| `user_skills` | self, mod, public-flagged | self | self | self | Endorsement fields are trigger-guarded against self-endorsement. |
| `organizations` | anyone (not deleted) | self as `created_by` | org admin | — | Directory is public; editing is scoped to one org. |
| `organization_members` | self, same-org members, mod | self (pending, `member`) or org admin | org admin, or self leaving | org admin | **The roster never crosses an organization boundary.** |
| `organization_verifications` | org admin (that org), mod | org admin, pending + unreviewed | **mod only** | — | Evidence never crosses an org boundary; orgs never verify themselves. FORCE. |
| `communities` | public/listed, members, mod (`anon`: public only) | self as `created_by` | community mod | — | Private communities are invisible to non-members. |
| `community_members` | self, fellow members, mod | self (never as moderator) or community mod | community mod, or self | community mod | You cannot make yourself a community moderator. |
| `community_rules` | anyone who can see the community | community mod | community mod | community mod | Rules are visible wherever the community is. |
| `learning_offerings` | published, teacher, org admin, mod | teacher = self, active + not posting-suspended | teacher, org admin | — | Drafts stay private; restricted accounts cannot publish. |
| `learning_enrollments` | student, teacher, org admin, mod | student = self, status `requested` | student, teacher | — | You enrol yourself; the teacher decides. |
| `volunteer_opportunities` | published, creator, org members, mod | self, and org admin if org-scoped | creator, org admin | — | You cannot post in an organization's name without being its admin. |
| `volunteer_applications` | applicant, posting org, mod | applicant = self, unreviewed | applicant, posting org | — | An applicant cannot pre-approve themselves. |
| `service_requests` | requester, assigned helper, mod, or live+**not blocked** | requester = self, active + not posting-suspended | requester, mod | — | Requests carry approximate location only. |
| `service_request_private_details` | **`app.can_see_exact_location()`** only | requester | requester | requester | **The doorstep rule**: requester, *accepted* helper, moderators. FORCE. |
| `request_responses` | responder, requester, mod | responder = self, request open, **no block** | responder, requester | — | You cannot offer help to someone who blocked you. |
| `projects` | public, lead, active members, mod | lead = self, not organizing-suspended | lead, org admin | — | Private projects are team-only. |
| `project_members` | self, lead, members, mod | lead, or self as contributor | self, lead | lead | You cannot promote yourself to lead. |
| `project_tasks` | active members, lead, mod | member, `created_by = self` | members | — | Tasks are internal to the team. |
| `commitments` | self, mod, confirmers | self | self | — | Your promises are yours to record. |
| `commitment_confirmations` | confirmer, committer, mod | confirmer = self | confirmer | — | **Nobody confirms their own commitment** (`trg_commitment_confirmations_not_self`). |
| `conversations` | active member or creator, **and no block**, or mod | self as `created_by`, active + not messaging-suspended | creator, mod (freeze) | — | A block makes a channel disappear for both sides. |
| `conversation_purposes` | channel members, mod | channel creator, `declared_by = self` | **— (immutable)** | — | A channel's stated purpose can never be rewritten. |
| `conversation_members` | self, fellow members, mod | channel creator, **and no block** with the invitee | self (read state), creator, mod | — | You cannot pull a blocked person into a room. |
| `messages` | member **and no block** with any other member, or mod | self + active + not suspended + **member with `can_write`** + **channel has a purpose** + not frozen + **no block** | sender (unsend), mod | **— (never deleted)** | Purpose-bound, block-aware, restriction-aware writing. FORCE. |
| `message_redactions` | **mod only** | mod | **— revoked** | **— revoked** | Unsend redacts for participants; it never destroys abuse evidence. FORCE. |
| `trusted_contacts` | self | self | self | self | Entirely private — **not even moderators**. FORCE. |
| `wali_profiles` | `app.can_view_wali_profile()` (ward, wali, introduction participants, mod) — **but `contact_email` / `contact_phone` SELECT is revoked from every role** | ward | ward | ward | See "The wali contact rule" below. |
| `wali_contact_disclosures` | recipient, ward, wali, mod | **— revoked (fn only)** | **— revoked** | **— revoked** | The ward can always audit who was given their wali's details. FORCE. |
| `formal_introduction_settings` | self, their wali, mod | self | self | — | Nobody is browsable by default (`is_open_to_introductions` = false). FORCE. |
| `formal_introduction_requests` | the two members, either wali, participants, mod | initiator = self, draft/submitted, recipient open, no block, not introductions-suspended | the members, their walis, mod — **but not into a forwarded state** | — | An introduction cannot be pre-approved or self-forwarded. |
| `formal_introduction_participants` | fellow participants, mod | a principal, `contact_authorized_at` must be NULL | self (revoke), mod | — | Contact authorization is `app.forward_introduction()`-only. FORCE. |
| `user_verifications` | self, mod | self, **status must be `pending`**, no reviewer fields | **mod only** | — | Trust is granted, never claimed. FORCE. |
| `qualifications` | self, mod, or public+verified | self, `verified_at`/`verified_by` must be NULL | self (but `verified_*` trigger-guarded), mod | self | An unverified claim is not advertised. FORCE. |
| `reports` | reporter (non-anonymous), mod | anyone signed in, status `submitted` | **mod only** | — | The reported user can never see the report. FORCE. |
| `report_evidence` | submitter, mod | reporter of that report, or mod | **— revoked** | **— revoked** | Evidence outlives both the accused and the moderator. FORCE. |
| `moderation_cases` | mod | mod | mod | — | Case files are staff-only. FORCE. |
| `moderation_actions` | mod, **and the target user** | mod, `actor_id = self` | **— revoked** | **— revoked** | Moderation is not secret, and it is not erasable. FORCE. |
| `appeals` | appellant, mod | appellant = self, `submitted` | mod | — | The moderator who acted may not review the appeal. FORCE. |
| `blocks` | **blocker only**, mod | blocker = self | blocker (lift) | — | The blocked person is never told they were blocked. |
| `restrictions` | self, mod | mod | mod | — | You can always see why you are restricted. |
| `safety_incidents` | mod | mod | mod | — | Highest sensitivity; the subject never reads it. FORCE. |
| `campaigns` | public+active, org members, mod (`anon`: public+active) | org admin, `zakat_eligible` must be false | org admin, mod — **zakat flag trigger-guarded** | — | Fiqh claims are not editable booleans. |
| `campaign_verifications` | org members of that campaign, mod | org admin (pending) or mod | mod or scholar | — | An organization cannot attest to itself. FORCE. |
| `donations` | donor, mod, or org admin **for non-anonymous rows only** | donor = self | donor, org admin, mod | — | Quiet sadaqah stays quiet, even from the recipient. FORCE. |
| `notifications` | self | **— revoked (fn only)** | self | self | Nobody can fabricate a notification. FORCE. |
| `private_impact_records` | self | self | self | self | Private to the member. **No moderator read, no aggregate, no leaderboard.** FORCE. |
| `audit_logs` | mod, or the subject | anyone signed in (append) | **— revoked** | **— revoked** | Append-only, absolutely. FORCE. |
| `consent_records` | self, or the consenting guardian | self / guardian | self / guardian (withdraw) | **— revoked** | A consent receipt is never destroyed. FORCE. |

`FORCE` marks the 24 tables that additionally carry
`alter table … force row level security`.

## The wali contact rule

`wali_profiles.contact_email` and `wali_profiles.contact_phone` are protected
by four independent mechanisms:

1. **Column privilege.** `0013` does
   `revoke select on public.wali_profiles from authenticated, anon;` and then
   re-grants SELECT on the *other* columns only. Reading either protected
   column — or `select *`, which expands to them — raises
   `permission denied for table wali_profiles`. This applies to **moderators
   and platform admins too**; there is no SQL path to those columns.
2. **Row policy.** `app.can_view_wali_profile()` limits who can see the row at
   all: the ward, the wali's own account, participants in a related
   introduction, and moderators.
3. **Redacted view.** `wali_profiles_redacted` is what general reads use. It
   does not project the protected columns, not even as a "has an email"
   boolean.
4. **Disclosure function.** `app.get_wali_contact(introduction_id)` is the only
   read path for a counterparty. It requires:
   * the introduction's status to be `approved_and_forwarded` or
     `in_correspondence`, **and**
   * a `formal_introduction_participants` row for the caller with
     `revoked_at is null`, `contact_authorized_at is not null` and a non-null
     `authorized_for_wali_profile_id`.

   It then writes a `wali_contact_disclosures` row before returning. The ward
   can always see who received their wali's details.

   `app.get_my_wali_contact()` gives a member back the details they recorded
   themselves.

`contact_authorized_at` can only be set by `app.forward_introduction()`;
`app.guard_introduction_authorization()` rejects any other write, and
`app.guard_introduction_status()` rejects any manual move into
`approved_and_forwarded`.

## Append-only tables

Five tables have **no UPDATE or DELETE policy for any role, and the privilege
revoked** so an attempt fails loudly instead of silently affecting zero rows:

| Table | Why |
| --- | --- |
| `audit_logs` | A moderator can act; they cannot erase having acted. |
| `report_evidence` | Evidence must outlive both the accused and the investigator. |
| `moderation_actions` | The record of a decision is not editable after the fact. |
| `message_redactions` | Unsend must not be an evidence-destruction primitive. |
| `wali_contact_disclosures` | The disclosure ledger is the ward's audit trail. |

`consent_records` and `notifications` have DELETE / INSERT revoked
respectively, for the same "no silent zero-row" reason.

## Policy authoring rules learned the hard way

* **Never inline a subquery over an RLS-protected table in a policy that must
  see rows the caller cannot.** The `blocks` table is the canonical trap: only
  the *blocker* can see the block row, so an inline
  `not exists (select 1 from blocks …)` evaluates to "no block" for exactly the
  person you are trying to stop. Every block check therefore goes through
  `app.is_blocked_between()` / `app.conversation_blocked_for()`, which are
  `security definer`. The same applies to
  `app.is_open_to_introductions()` reading a private settings row.
* **Never let two tables' policies reference each other.** PostgreSQL raises
  `infinite recursion detected in policy`. `projects` ↔ `project_members`,
  `commitments` ↔ `commitment_confirmations`,
  `formal_introduction_requests` ↔ `wali_profiles`, and
  `formal_introduction_participants` referencing itself are all resolved with
  definer helpers.
* **Views must be `security_invoker = true`**, otherwise they run as the view
  owner and quietly bypass every policy on the underlying tables.
* **A missing policy is silent.** If "denied" must be observable, revoke the
  privilege as well.

## Test coverage

`supabase/tests/rls_tests.sql` — 100 assertions, 15 sections. Every invariant
listed in this document that can be expressed as "user X may/may not do Y" has
at least one assertion, together with a positive control so a policy that
denies *everyone* cannot pass by accident.
