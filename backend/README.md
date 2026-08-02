# Fi Sabilillah — backend data layer

The PostgreSQL / Supabase schema for **Fi Sabilillah**, a Muslim
service-oriented community platform: volunteering, Islamic learning, mutual
aid, community projects, purpose-bound messaging, and a wali-centred marriage
introduction workflow.

**This is not a dating app and not a social network.** The schema is written so
that it *cannot* become one — see [`docs/data-model.md`](docs/data-model.md)
for the list of things it deliberately cannot represent.

```
backend/
  supabase/
    migrations/       0001 … 0015, applied in filename order
    seed/seed.sql     fictional sample data
    tests/
      00_bootstrap.sql  local-only auth.* shim (NEVER applied on Supabase)
      rls_tests.sql     100 assertion-based authorization tests
    run_local_tests.sh
  docs/
    data-model.md     tables, enums, relationships, helper functions
    rls-model.md      every table: who may select/insert/update/delete, and why
    threat-model.md   assets, actors, attack paths, mitigations, residual risk
```

## Running the tests

Postgres 16 must be installed. The script starts the cluster if it is down,
creates a throwaway database, applies the bootstrap shim, then every migration
in order, then the seed, then the tests.

```console
$ backend/supabase/run_local_tests.sh
```

```
==> checking the PostgreSQL cluster
    cluster is up: /var/run/postgresql:5432 - accepting connections
==> recreating the throwaway database 'fisabilillah_test'
==> applying tests/00_bootstrap.sql (local auth.* shim -- never applied on Supabase)
==> applying migrations
    0001_extensions_and_helpers.sql                     ok
    ...
    0015_indexes.sql                                    ok
==> applying seed/seed.sql
    seeded
==> running tests/rls_tests.sql
...
PASS: schema summary -- 58 tables, 162 policies
---------------------------------------------------------------
RESULT: PASS  (100 assertions passed)
```

Exit code is non-zero on any migration, seed or assertion failure.

Options: `KEEP_DB=1` to keep the database for inspection,
`PGDATABASE_TEST=<name>` to change its name, `PG_SUPERUSER=<role>` if your
database superuser is not `postgres`.

## Deploying to Supabase

```console
$ supabase db push          # applies supabase/migrations in order
$ psql "$SUPABASE_DB_URL" -f supabase/seed/seed.sql   # optional, non-production only
```

**Do not apply `supabase/tests/00_bootstrap.sql` to a Supabase project.** It
exists solely to recreate, on a plain PostgreSQL cluster, what Supabase already
provides: the `auth` schema, `auth.users`, `auth.uid()`, `auth.role()` and the
`anon` / `authenticated` / `service_role` database roles. The migrations
themselves contain no part of that shim, which is what makes them portable.

The migrations assume:

* the migration role can create schemas, extensions and roles' grants
  (Supabase's `postgres` role can, and has `BYPASSRLS`, which is what lets
  `security definer` helpers read past policies);
* `auth.uid()` returns the requesting user's id;
* Storage object *paths* are stored as plain text (`avatar_path`,
  `evidence_path`, `attachment_path`) — never public URLs. Bucket policies are
  configured separately in the Supabase dashboard and should mirror the RLS
  rules documented in `docs/rls-model.md`.

## Design commitments

| Commitment | How the schema keeps it |
| --- | --- |
| **Messaging is purpose-bound** | A conversation cannot commit without a `conversation_purposes` row (deferrable constraint trigger); a message cannot be inserted into a purposeless channel (BEFORE trigger + INSERT policy). |
| **Wali contact is sealed** | SELECT on `wali_profiles.contact_email` / `.contact_phone` is revoked from every application role — moderators included. Disclosure happens only through `app.get_wali_contact()`, only after `approved_and_forwarded`, only to an authorized participant, and is written to an append-only ledger. |
| **The doorstep rule** | Exact address, coordinates and access notes live in `service_request_private_details`, readable only by the requester, the *accepted* helper, and moderators. Everyone else sees `approximate_area`. |
| **Unsend never destroys evidence** | The original body is copied into the moderator-only, append-only `message_redactions` before the visible row is blanked. |
| **Trust is granted, never claimed** | Verification outcomes, qualification verification, role grants and skill endorsements are all guard-triggered and function-only. |
| **Moderators are accountable** | `audit_logs`, `moderation_actions`, `report_evidence`, `message_redactions` and `wali_contact_disclosures` have no UPDATE/DELETE policy and the privilege revoked. Every moderation action forces an audit row by trigger. |
| **Organizations are sealed from each other** | `app.is_org_admin(org)` is scoped to one organization id and never generalises. |
| **No payments, no leaderboards** | `check (payments_enabled = false)` on campaigns; `check (is_visible_to_self_only = true)` on private impact records. |
| **Zakat eligibility is a ruling, not a checkbox** | CHECK constraint + guard trigger + a `security definer` setter requiring a verified organization and a verified scholarly/organizational attestation. |

## RLS at a glance

58 tables, **RLS enabled on every one**, 162 policies, 24 tables additionally
carrying `force row level security`. Migration `0013` ends with an assertion
that fails the migration if any table in `public` has RLS off, and the test
suite re-asserts it.

Full matrix in [`docs/rls-model.md`](docs/rls-model.md).

## Working on this schema

Read [`docs/threat-model.md`](docs/threat-model.md) §5 before adding a table or
a policy. The three mistakes that are easy to make and hard to notice:

1. **A subquery inside a policy is itself subject to RLS.** An inline
   `not exists (select 1 from blocks …)` is blind to a block the current user
   is on the receiving end of — which is exactly the person it was written to
   stop. Route such checks through a `security definer` helper.
2. **Two tables whose policies reference each other** produce
   `infinite recursion detected in policy`. Break the cycle with a helper.
3. **A view without `with (security_invoker = true)`** runs as its owner and
   bypasses every underlying policy.

Add a test to `supabase/tests/rls_tests.sql` for every new invariant, and pair
each negative assertion with a positive control — otherwise a policy that
denies *everyone* passes by accident.

## Seed data

`supabase/seed/seed.sql` is entirely fictional: no real people, organizations
or addresses; phone numbers use the reserved `555-01xx` range and all email
domains are under the reserved `.example.test` TLD. It covers Arabic tutoring,
Quran-reading support, new-Muslim mentoring, an electrical trade mentorship, CV
assistance, food distribution, a masjid deep clean, an elder transport rota, a
laptop-refurbishment community project, a youth programme with organization
controls and safeguarding clearance, a verified nonprofit campaign placeholder
with payments disabled, and a wali-mediated introduction paused mid-flow at
`counterpart_wali_review`.
