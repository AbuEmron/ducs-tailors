# Fi Sabilillah — threat model (data layer)

Scope: the PostgreSQL/Supabase data layer in `backend/supabase`. Client apps,
the auth service, storage buckets and operational security are out of scope
except where the schema deliberately constrains them.

A note before the tables. **Software cannot guarantee a fitnah-free
environment.** No schema stops a person from being dishonest, from carrying a
conversation off-platform, or from misusing a legitimate meeting. What a
schema can do is refuse to *manufacture* the conditions for harm: no
open-ended DM surface, no browsable people directory, no leaderboard for
riyaa, no doorstep addresses handed to strangers, no way to quietly erase
evidence. Everything below is written in that spirit — reducing the surface,
making harm slower, noisier and traceable — not claiming to eliminate it.

---

## 1. Assets

| # | Asset | Why it matters | Where it lives |
| --- | --- | --- | --- |
| A1 | **Wali contact details** | The single most sensitive field set. Leaking it turns a supervised process into unsupervised contact and exposes a family. | `wali_profiles.contact_email`, `.contact_phone` |
| A2 | **Home addresses** | A mutual-aid request is a stranger asking to come to your door. Often an elderly or isolated person. | `service_request_private_details` |
| A3 | **Message content** | Contains pastoral, financial and family disclosures. | `messages`, `message_redactions` |
| A4 | **Safeguarding and incident records** | Concerns minors and vulnerable adults; leaking it can endanger a child or destroy a reputation. | `safety_incidents`, `moderation_cases`, `report_evidence` |
| A5 | **Reporter identity** | If the reported party learns who reported them, reporting stops. | `reports.reporter_id`, `reports.is_anonymous` |
| A6 | **Trust markers** | Verification levels, qualifications and roles are what makes a stranger acceptable to meet. Forging one is the entry point for most other attacks. | `user_verifications`, `qualifications`, `user_roles`, `profiles.verification_level` |
| A7 | **The audit trail** | The only thing that makes insider misuse discoverable. | `audit_logs`, `moderation_actions`, `wali_contact_disclosures` |
| A8 | **Giving records and the zakat claim** | Financial fraud and a false fiqh claim on top of it. | `campaigns`, `campaign_verifications`, `donations` |
| A9 | **Private worship/impact records** | Spiritually sensitive; publishing them is itself a harm (riyaa). | `private_impact_records` |
| A10 | **Guardian/consent receipts** | Evidence that a minor's participation was authorised. | `consent_records`, `trusted_contacts` |
| A11 | **Personal boundaries** | Safeguards are the user's stated limits; silently loosening them is an attack. | `user_safeguards`, `formal_introduction_settings` |

---

## 2. Actors

| Actor | Capability assumed | Motivation |
| --- | --- | --- |
| **Ordinary member** | Authenticated PostgREST access as `authenticated`, able to issue arbitrary requests against any table they have privileges on. | Normal use. |
| **Curious member** | The same, plus willingness to probe endpoints and read raw responses. | Finding out who is single, where someone lives, who reported them. |
| **Harasser** | An account (or several) that keeps contacting someone who does not want contact. | Persistence, control. |
| **Grooming actor** | Patient. Presents as a helper, tutor or mentor; targets a new Muslim, a minor, or an isolated elder; wants a private, unsupervised channel and off-platform contact. | The most serious threat this platform faces. |
| **Scammer** | Creates a plausible cause and a plausible organization; wants the "verified" and "zakat eligible" markers. | Money. |
| **Malicious org admin** | Legitimate admin of organization A. | Poaching, competitor intelligence, covering up an incident in their own org. |
| **Malicious insider / rogue moderator** | Holds `moderator` or `platform_admin` via `user_roles`. Legitimate read access to reports and cases. | Snooping on a specific person; protecting a friend by erasing a record. |
| **Compromised service key holder** | Holds `service_role`. **Bypasses RLS entirely.** | Anything. |
| **Compromised member account** | Full rights of that member. | Whatever the member had. |
| **Database operator** | Superuser on the cluster. | Out of the schema's reach. |

---

## 3. Attack paths and mitigations

### AP-1 — Harvest wali contact details (A1)

*Paths:* read the column directly; join through the redacted view; call the
disclosure function before approval; forge a participant row with
`contact_authorized_at` set; hand-write the introduction into
`approved_and_forwarded`; use a moderator role to read the column.

*Mitigations:*
* SELECT privilege on `contact_email`/`contact_phone` is **revoked from every
  application role**, so no SQL path reaches them — moderators included.
  `select *` fails too.
* `wali_profiles_redacted` does not project the columns at all.
* `app.get_wali_contact()` requires status ∈ {`approved_and_forwarded`,
  `in_correspondence`} **and** an authorized, unrevoked participant row, and
  writes a `wali_contact_disclosures` entry before returning.
* `app.guard_introduction_authorization()` rejects any write of
  `contact_authorized_at` outside `app.forward_introduction()`.
* `app.guard_introduction_status()` rejects any manual move into the
  forwarded states.
* `app.forward_introduction()` requires both walis recorded, both decisions
  recorded, and the caller to be a wali of that introduction or a moderator.

*Residual:* the counterparty, once legitimately given the details, can do
anything with them. The ledger records that they were given, not what was done
with them.

### AP-2 — Obtain a member's home address (A2)

*Paths:* read `service_request_private_details` as a responder; respond and
assume acceptance grants access; scrape coordinates from the public listing.

*Mitigations:*
* Exact address, coordinates, access notes and household notes are in a
  separate table gated by `app.can_see_exact_location()`: requester, the
  helper whose offer was **accepted**, moderators. A merely shortlisted
  responder gets nothing.
* `service_requests` carries only `approximate_area` and 4-decimal coarse
  coordinates. `service_requests_public` cannot leak the fine ones — the
  columns are not in its source table.
* `min_helper_verification` and `gender_policy` let a requester require a
  verification level and a gender policy before matching.

*Residual:* an accepted helper legitimately learns the address. Acceptance is
the trust decision; the schema records it (`helper_accepted_at`) but cannot
make it wise.

### AP-3 — Unsolicited or coercive contact (A3, harasser/grooming actor)

*Mitigations:*
* **Purpose-bound messaging.** A conversation cannot commit without a
  `conversation_purposes` row (deferrable constraint trigger), a message
  cannot be inserted into a purposeless channel (BEFORE trigger + policy).
  There is no "message anyone" surface to begin with.
* Blocking is **symmetric in effect**: `app.conversation_blocked_for()` hides
  the channel and its messages from both parties and refuses new inserts.
* The blocked party cannot see the `blocks` row, so they cannot confirm they
  were blocked.
* `restrictions` with `messaging_suspended` / `full_suspension` are checked in
  the INSERT policy of `messages`, `conversations`, `service_requests`,
  `projects` and `learning_offerings`.
* `off_purpose_contact` is a first-class `report_category`.
* `user_safeguards` records the member's own limits, and every change to them
  is audited.

*Residual:* a determined harasser can create new accounts. Mitigating that is
an identity/rate-limiting problem at the auth layer, not a schema problem.

### AP-4 — Grooming a minor or a new Muslim (A4, A10)

*Mitigations:*
* Youth-facing work **cannot be posted** without safeguarding clearance:
  three CHECK constraints (`volunteer_opportunities_youth_clearance`,
  `learning_offerings_youth_guardian`, `projects_youth_clearance`).
* `organization_members.safeguarding_cleared_at/by` records who cleared whom.
* `consent_records` with `granted_by_guardian` gives a guardian receipt, and
  `learning_enrollments.guardian_consent_record_id` ties an enrolment to it.
* `user_safeguards.is_minor_supervised` and `guardian_notify_email` support a
  supervised account.
* `conversation_members.member_role = 'wali_observer'` is constrained to
  `can_write = false`, so a guardian can supervise a channel without
  participating.
* Learning offerings default to `is_published = false` and carry an explicit
  `gender_policy`.

*Residual:* **this is the honest limit of the design.** A patient adult who
passes a background check, behaves correctly on-platform, and then moves the
relationship elsewhere is not detectable by a database. Clearance records and
supervision reduce opportunity and improve after-the-fact investigation; they
do not prevent the harm. Human safeguarding process is the real control here,
and the schema exists to support it, not replace it.

### AP-5 — Manufacture trust (A6)

*Paths:* insert a `verified` verification for yourself; update your own
verification; stamp `verified_at` on your own qualification; write
`profiles.verification_level`; grant yourself `scholar`/`moderator`/
`platform_admin`; endorse your own skill.

*Mitigations:*
* `user_verifications` INSERT policy forces `status = 'pending'` with no
  reviewer fields; there is **no ordinary-user UPDATE policy**;
  `app.guard_user_verification()` rejects field changes; only
  `app.decide_user_verification()` (moderator) records an outcome.
* `check (status <> 'verified' or (reviewed_by is not null and verified_at is
  not null))` means a verified row must name a reviewer.
* `profiles.verification_level` is derived by trigger and rejected by
  `app.guard_profile_verification_level()` on direct write.
* `user_roles`: admin-only policies, `check (granted_by <> user_id)`, and
  `app.guard_role_escalation()` which refuses any self-grant of an
  `is_privileged` role even from a privileged context.
* `app.guard_skill_endorsement()` refuses self-endorsement.

*Residual:* a corrupt verifier can verify a liar. Every such decision is
audited with the reviewer's id.

### AP-6 — Charity fraud and a false zakat claim (A8)

*Mitigations:*
* `campaigns.payments_enabled` is pinned false by CHECK — the schema cannot
  represent a live payment flow, so there is nothing to divert.
* `donations` holds **no payment instrument data whatsoever**: no card, token,
  account number or sort code column exists.
* `zakat_eligible` requires all three of: a CHECK-enforced non-null
  `zakat_attestation_id`, a guard trigger that refuses ordinary INSERT/UPDATE,
  and `app.set_campaign_zakat_eligible()` which demands a moderator/scholar
  caller, a **verified owning organization**, and a *verified* attestation of
  the right kind for that exact campaign.
* `campaign_verifications` INSERT by an org admin is forced to `pending`;
  outcomes are moderator/scholar only.
* `organizations.verification_level` is derived from
  `organization_verifications`, never self-written.

*Residual:* a scammer who convinces a real scholar and a real verified
organization defeats every technical control. The attestation row records who
was convinced.

### AP-7 — Cross-organization data theft (A4, org admin)

*Mitigations:*
* `app.is_org_admin(org uuid)` takes **one** organization id and never
  generalises. Every organization-scoped policy calls it with the row's own
  `organization_id`.
* `organization_members` and `organization_verifications` SELECT policies
  admit only same-org members plus moderators.
* Tested directly: an admin of org A reads zero rows of org B's roster and
  verifications and cannot insert an admin into org B.

*Residual:* a person who is genuinely an admin of both organizations sees
both. That is correct behaviour.

### AP-8 — Insider abuse by a moderator (A1, A3, A4, A7)

*Mitigations:*
* **Wali contact is not a moderator privilege.** The column revoke applies to
  every application role.
* `private_impact_records`, `trusted_contacts`, `user_settings`,
  `user_safeguards` and other members' `consent_records` are unreadable by
  moderators.
* `audit_logs`, `moderation_actions`, `report_evidence`,
  `message_redactions` and `wali_contact_disclosures` have **no UPDATE or
  DELETE policy and the privilege revoked**, so tampering raises an error
  rather than silently doing nothing.
* Every `moderation_actions` insert forces an `audit_logs` row by trigger —
  there is no code path that acts without a record.
* `app.record_moderation_action()` refuses to let a moderator act on their own
  account.
* `app.guard_appeal_reviewer()` refuses to let the acting moderator review the
  appeal against their own action.
* `moderation_cases.second_reviewer_id` supports four-eyes sign-off and is
  constrained to differ from `assigned_to`.
* `moderation_actions` is readable by its **target**, so a member can see what
  was done to them.

*Residual:* a moderator can still *read* messages and reports within their
remit; that access is what the role is for. The schema records the action but
cannot record every read — read-auditing is an application-layer
responsibility. And a moderator who convinces a platform admin to
delete-and-rebuild the database defeats everything here.

### AP-9 — Destroying evidence via "unsend" (A3, A7)

*Mitigations:*
* `app.preserve_message_on_redaction()` copies the original body and
  attachment path into `message_redactions` *before* nulling the visible
  columns.
* `message_redactions` is moderator-read and append-only.
* `messages` has **no DELETE policy for anyone**; removal is a state change
  (`removed_at`), not a deletion.
* Account departure is `profiles.deleted_at`, not a row delete, so reports and
  evidence keep their subject.

*Residual:* a message never sent through the platform was never captured.

### AP-10 — Retaliation against a reporter (A5)

*Mitigations:*
* `reports` SELECT admits the reporter and moderators only; the reported user
  has no read path.
* Anonymous reports keep `reporter_id` NULL and are hidden even from the
  reporter's own listing.
* `blocks` is visible only to the blocker.

*Residual:* content can be self-identifying ("you were the only person there
on Tuesday"). No schema fixes that.

### AP-11 — Privilege escalation through a `security definer` function

*Mitigations:*
* Every `app.*` function sets `search_path = ''` and fully schema-qualifies
  every reference, so no `search_path` shadowing attack works.
* `app.is_privileged_caller()` is **security invoker** and reads the real
  `current_user`. It is deliberately *not* a session GUC flag, because a
  client could simply `set_config()` a GUC before their UPDATE. All guard
  triggers are security invoker for the same reason.
* Privileged entry points re-check authorization inside the function
  (`app.grant_role`, `app.decide_user_verification`,
  `app.set_campaign_zakat_eligible`, `app.forward_introduction`,
  `app.record_moderation_action`).
* `execute` on the sensitive functions is revoked from `anon`.

*Residual:* a bug in one of these ~30 functions is a real escalation risk.
They are the highest-value review target in the codebase.

### AP-12 — RLS bypass through policy authoring mistakes

Three specific traps, all of which were hit and fixed during development:

1. **A policy subquery is itself subject to RLS.** An inline
   `not exists (select 1 from blocks …)` in the `messages` policy evaluated to
   "no block" for the blocked user, because only the blocker can see the block
   row — it silently permitted exactly what it was written to stop. Fixed by
   routing every block test through `security definer` helpers.
2. **Mutually referencing policies recurse.** PostgreSQL raises `infinite
   recursion detected in policy`; fixed with definer helpers for
   `projects` ↔ `project_members`, `commitments` ↔ `commitment_confirmations`,
   and the introduction/wali cluster.
3. **A view without `security_invoker` runs as its owner** and bypasses every
   policy underneath. All three views set it explicitly.

*Mitigation going forward:* `0013` ends with an assertion that no table has
RLS off, `rls_tests.sql` re-asserts that plus "every table has a policy", and
every negative assertion is paired with a positive control so a policy that
denies everyone cannot pass by accident.

### AP-13 — Compromised `service_role` key

`service_role` bypasses RLS completely. There is no schema-level mitigation.
Treat it as a production secret: never ship it to a client, scope edge
functions narrowly, rotate on suspicion, and prefer `app.*` definer functions
called as `authenticated` over service-key access wherever possible.

### AP-14 — Riyaa / social pressure around good deeds (A9)

*Mitigations:* `private_impact_records` is owner-only with FORCE RLS, carries
`check (is_visible_to_self_only = true)`, and has no aggregate, ranking or
export path. Recognition instead flows through
`commitment_confirmations`, which requires **someone else** to confirm and
refuses self-confirmation. Donations default to `is_anonymous = true` and
anonymous ones are invisible even to the receiving organization.

*Residual:* people will still compare themselves to each other. The schema
just refuses to help.

---

## 4. Residual risk summary

| Risk | Status |
| --- | --- |
| A patient grooming actor who behaves correctly on-platform and moves off it | **Not mitigated by the data layer.** Clearance records, supervision roles and audit trails support human safeguarding; they do not replace it. |
| A verifier, scholar or organization that vouches dishonestly | Not preventable. Every decision names its decision-maker. |
| Off-platform contact after a legitimate disclosure | Not preventable. The disclosure ledger records that it happened. |
| An accepted helper misusing an address | Not preventable after acceptance. |
| Compromised `service_role` key | Full bypass. Operational control only. |
| Superuser / database operator | Out of scope. |
| Repeat-offender account creation | Auth-layer problem (identity, rate limiting, device signals). |
| A bug in an `app.*` definer function | Real. These are the priority review target. |
| Self-identifying report content | Not preventable. |

## 5. Review checklist for future changes

1. Does the new table have RLS enabled **and** at least one explicit policy?
   (`0013`'s trailing assertion and test §15 will fail if not.)
2. Does any new policy inline a subquery over an RLS-protected table? If the
   caller might not be able to see those rows, use a definer helper.
3. Does the new policy create a reference cycle with another table's policy?
4. Is any new view `security_invoker = true`?
5. Does the change add a way to write a privileged field (verification,
   role, zakat flag, contact authorization)? If so, it needs a guard trigger,
   not just a policy.
6. Does the change create a hard-delete path for anything in the evidence set?
7. Does the change create a way to publish `private_impact_records`, or a
   ranking of members? If so, reconsider it.
8. Does the change add a column that could carry a home address, a phone
   number or a wali contact into a public view?
