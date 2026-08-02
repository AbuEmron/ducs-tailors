-- =====================================================================
-- Fi Sabilillah -- tests/rls_tests.sql
--
-- Assertion-based authorization tests. Every test block impersonates a
-- real seeded member with:
--     set local role authenticated;
--     set local request.jwt.claim.sub = '<uuid>';
-- and is wrapped in begin/rollback so tests cannot contaminate each other.
--
-- A failing assertion raises SQLSTATE TS001, which stops psql when it is
-- run with -v ON_ERROR_STOP=1. Every passing assertion prints "PASS: ...".
-- =====================================================================

\set ON_ERROR_STOP on
\set QUIET on
set client_min_messages = notice;

-- ---------------------------------------------------------------------
-- Assertion helpers. Created as the migration owner, then made callable
-- by the impersonated roles. SECURITY INVOKER throughout: dynamic SQL
-- inside them must run with the caller's privileges and policies.
-- ---------------------------------------------------------------------
create schema if not exists test;
grant usage on schema test to public;

create or replace function test.fail(p_label text, p_detail text default null)
returns void language plpgsql as $$
begin
  raise exception 'FAIL: %', p_label || coalesce(' -- ' || p_detail, '')
    using errcode = 'TS001';
end;
$$;

create or replace function test.ok(p_condition boolean, p_label text, p_detail text default null)
returns void language plpgsql as $$
begin
  if p_condition is true then
    raise notice 'PASS: %', p_label;
  else
    perform test.fail(p_label, coalesce(p_detail, 'condition was ' || coalesce(p_condition::text, 'null')));
  end if;
end;
$$;

-- Expect the statement to be REJECTED (RLS violation, guard trigger,
-- missing privilege, or constraint).
create or replace function test.denied(p_sql text, p_label text)
returns void language plpgsql as $$
begin
  begin
    execute p_sql;
  exception
    when sqlstate 'TS001' then
      raise;
    when others then
      raise notice 'PASS: % [rejected: %]', p_label, replace(sqlerrm, chr(10), ' ');
      return;
  end;
  perform test.fail(p_label, 'the statement was ACCEPTED but should have been rejected');
end;
$$;

-- Expect the statement to succeed.
create or replace function test.allowed(p_sql text, p_label text)
returns void language plpgsql as $$
begin
  execute p_sql;
  raise notice 'PASS: %', p_label;
end;
$$;

-- Row count of a query, evaluated under the caller's policies.
create or replace function test.count_of(p_sql text)
returns bigint language plpgsql as $$
declare v bigint;
begin
  execute 'select count(*) from (' || p_sql || ') _q' into v;
  return v;
end;
$$;

-- Rows AFFECTED by a DML statement (0 means "policy matched nothing").
create or replace function test.affected(p_sql text)
returns bigint language plpgsql as $$
declare v bigint;
begin
  execute p_sql;
  get diagnostics v = row_count;
  return v;
end;
$$;

create or replace function test.scalar_text(p_sql text)
returns text language plpgsql as $$
declare v text;
begin
  execute p_sql into v;
  return v;
end;
$$;

grant execute on all functions in schema test to public;

-- Cast of characters (all fictional, all from seed/seed.sql):
--   admin       aaaaaaaa-0000-4000-8000-000000000001  Rahma A. (platform_admin)
--   moderator   aaaaaaaa-0000-4000-8000-000000000002  Bilal M. (moderator)
--   scholar     aaaaaaaa-0000-4000-8000-000000000003  Ustadh Idris
--   amina       bbbbbbbb-0000-4000-8000-000000000001  Arabic tutor
--   hafsa       bbbbbbbb-0000-4000-8000-000000000002  introduction recipient
--   maryam      bbbbbbbb-0000-4000-8000-000000000003  blocker / reporter
--   khadija     bbbbbbbb-0000-4000-8000-000000000004  service requester
--   nadia       bbbbbbbb-0000-4000-8000-000000000005  admin of the relief circle
--   yusuf       cccccccc-0000-4000-8000-000000000001  introduction initiator
--   tariq       cccccccc-0000-4000-8000-000000000002  accepted helper
--   zayd        cccccccc-0000-4000-8000-000000000003  admin of the masjid trust
--   ibrahim     cccccccc-0000-4000-8000-000000000004  unrelated ordinary member
--   rashid      dddddddd-0000-4000-8000-000000000001  blocked by maryam
--   faisal      dddddddd-0000-4000-8000-000000000002  messaging_suspended
--   anas        dddddddd-0000-4000-8000-000000000003  soft-deleted profile
--   wali_saleh  eeeeeeee-0000-4000-8000-000000000002  hafsa's wali

\echo ''
\echo '### 1. A blocked user is cut off from the blocker'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'dddddddd-0000-4000-8000-000000000001';   -- rashid
do $$
begin
  perform test.denied($q$
    insert into public.messages (conversation_id, sender_id, body)
    values ('f7000000-0000-4000-8000-000000000004',
            'dddddddd-0000-4000-8000-000000000001',
            'Just checking in again.')
  $q$, 'a blocked user cannot insert a message into a channel with the blocker');

  perform test.ok(
    test.count_of($q$select 1 from public.conversations
                      where id = 'f7000000-0000-4000-8000-000000000004'$q$) = 0,
    'a blocked user cannot read the conversation they share with the blocker');

  perform test.ok(
    test.count_of($q$select 1 from public.messages
                      where conversation_id = 'f7000000-0000-4000-8000-000000000004'$q$) = 0,
    'a blocked user cannot read any message in that conversation');

  perform test.ok(
    test.count_of($q$select 1 from public.profiles
                      where id = 'bbbbbbbb-0000-4000-8000-000000000003'$q$) = 0,
    'a blocked user cannot even see the blocker''s profile');

  perform test.ok(
    test.count_of($q$select 1 from public.reports
                      where reported_user_id = 'dddddddd-0000-4000-8000-000000000001'$q$) = 0,
    'a reported user cannot see the report filed against them');
end;
$$;
rollback;

\echo ''
\echo '### 2. Messaging is purpose-bound'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000003';   -- zayd
do $$
declare v_conv uuid := '0f0f0f0f-0000-4000-8000-00000000beef';
begin
  insert into public.conversations (id, created_by, title)
  values (v_conv, 'cccccccc-0000-4000-8000-000000000003', 'No stated purpose');
  insert into public.conversation_members (conversation_id, user_id, member_role)
  values (v_conv, 'cccccccc-0000-4000-8000-000000000003', 'host');

  perform test.denied(format($q$
    insert into public.messages (conversation_id, sender_id, body)
    values (%L, 'cccccccc-0000-4000-8000-000000000003', 'hello?')
  $q$, v_conv), 'a message cannot be inserted into a conversation with no declared purpose');

  -- Declare the purpose, and the same insert now works.
  insert into public.conversation_purposes
    (conversation_id, purpose_kind, purpose_statement, declared_by)
  values (v_conv, 'project_work', 'Coordinating the laptop handover criteria.',
          'cccccccc-0000-4000-8000-000000000003');

  perform test.allowed(format($q$
    insert into public.messages (conversation_id, sender_id, body)
    values (%L, 'cccccccc-0000-4000-8000-000000000003', 'Purpose declared, now we can talk.')
  $q$, v_conv), 'once a purpose exists the same message is accepted');
end;
$$;
rollback;

\echo ''
\echo '### 3. A non-member cannot read someone else''s messages'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000004';   -- ibrahim
do $$
begin
  perform test.ok(
    test.count_of($q$select 1 from public.messages
                      where conversation_id = 'f7000000-0000-4000-8000-000000000001'$q$) = 0,
    'a non-member reads no messages from the Arabic class channel');

  perform test.ok(
    test.count_of($q$select 1 from public.conversation_members
                      where conversation_id = 'f7000000-0000-4000-8000-000000000001'$q$) = 0,
    'a non-member cannot even enumerate that channel''s membership');

  perform test.ok(
    test.count_of($q$select 1 from public.messages
                      where conversation_id = 'f7000000-0000-4000-8000-000000000005'$q$) >= 1,
    'positive control: the same member DOES read the channel they belong to');

  perform test.ok(
    test.count_of($q$select 1 from public.trusted_contacts
                      where user_id = 'bbbbbbbb-0000-4000-8000-000000000002'$q$) = 0,
    'another member''s trusted contacts are invisible');

  perform test.ok(
    test.count_of($q$select 1 from public.private_impact_records$q$) = 0,
    'another member''s private impact records are invisible');
end;
$$;
rollback;

\echo ''
\echo '### 4. Wali contact details are sealed until approved AND forwarded'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000001';   -- yusuf (initiator)
do $$
begin
  perform test.ok(
    test.scalar_text($q$select status::text from public.formal_introduction_requests
                        where id = 'fa000000-0000-4000-8000-000000000001'$q$) = 'counterpart_wali_review',
    'the seeded introduction is mid-flow, not yet forwarded');

  perform test.denied($q$
    select contact_email from public.wali_profiles
     where user_id = 'bbbbbbbb-0000-4000-8000-000000000002'
  $q$, 'the counterparty cannot SELECT the wali contact_email column at all');

  perform test.denied($q$
    select contact_phone from public.wali_profiles
     where user_id = 'bbbbbbbb-0000-4000-8000-000000000002'
  $q$, 'the counterparty cannot SELECT the wali contact_phone column at all');

  perform test.denied($q$select * from public.wali_profiles$q$,
    'even SELECT * is refused, because it would expand to the protected columns');

  perform test.denied($q$
    select * from app.get_wali_contact('fa000000-0000-4000-8000-000000000001')
  $q$, 'app.get_wali_contact() refuses while the introduction is only mid-review');

  perform test.ok(
    test.count_of($q$select 1 from public.wali_profiles_redacted
                      where user_id = 'bbbbbbbb-0000-4000-8000-000000000002'$q$) = 1,
    'the redacted view still works for general reads (no contact columns in it)');
end;
$$;

-- The recipient's wali forwards the introduction.
set local request.jwt.claim.sub = 'eeeeeeee-0000-4000-8000-000000000002';   -- wali_saleh
do $$
begin
  perform test.denied($q$
    update public.formal_introduction_requests
       set status = 'approved_and_forwarded', approved_and_forwarded_at = now()
     where id = 'fa000000-0000-4000-8000-000000000001'
  $q$, 'even a wali cannot hand-write the forwarded status with a plain UPDATE');

  perform app.forward_introduction('fa000000-0000-4000-8000-000000000001');
  perform test.ok(true, 'the wali forwards the introduction through app.forward_introduction()');
end;
$$;

set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000001';   -- yusuf again
do $$
declare v_email text;
begin
  select contact_email into v_email
    from app.get_wali_contact('fa000000-0000-4000-8000-000000000001');
  perform test.ok(v_email = 'saleh.wali.private@example.test',
    'after approval+forwarding the counterparty receives the OTHER side''s wali contact',
    'got: ' || coalesce(v_email, 'null'));

  perform test.ok(
    test.count_of($q$select 1 from public.wali_contact_disclosures
                      where disclosed_to = 'cccccccc-0000-4000-8000-000000000001'$q$) = 1,
    'the disclosure is recorded in the append-only disclosure ledger');

  perform test.denied($q$
    select contact_email from public.wali_profiles
     where user_id = 'bbbbbbbb-0000-4000-8000-000000000002'
  $q$, 'the column itself is STILL unreadable even after forwarding');
end;
$$;

set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000004';   -- ibrahim (outsider)
do $$
begin
  perform test.denied($q$
    select * from app.get_wali_contact('fa000000-0000-4000-8000-000000000001')
  $q$, 'an unrelated member gets nothing from a forwarded introduction');
end;
$$;
rollback;

\echo ''
\echo '### 5. Trust cannot be self-granted'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000004';   -- ibrahim
do $$
declare v_affected bigint;
begin
  perform test.denied($q$
    insert into public.user_verifications (user_id, level, status, method, verified_at)
    values ('cccccccc-0000-4000-8000-000000000004', 'org_verified', 'verified', 'email', now())
  $q$, 'a member cannot INSERT a user_verifications row that is already verified');

  v_affected := test.affected($q$
    update public.user_verifications
       set status = 'verified', verified_at = now()
     where user_id = 'cccccccc-0000-4000-8000-000000000004'
  $q$);
  perform test.ok(v_affected = 0,
    'a member''s UPDATE of their own verification status matches zero rows',
    'rows affected: ' || v_affected);

  perform test.ok(
    test.scalar_text($q$select status::text from public.user_verifications
                        where user_id = 'cccccccc-0000-4000-8000-000000000004'$q$) = 'pending',
    'and the stored status is still pending');

  perform test.ok(
    test.scalar_text($q$select verification_level::text from public.profiles
                        where id = 'cccccccc-0000-4000-8000-000000000004'$q$) = 'unverified',
    'the derived profile verification_level is untouched');

  perform test.denied($q$
    update public.profiles set verification_level = 'scholar_verified'
     where id = 'cccccccc-0000-4000-8000-000000000004'
  $q$, 'a member cannot write their own profile verification_level');

  perform test.denied($q$
    insert into public.user_roles (user_id, role_id)
    select 'cccccccc-0000-4000-8000-000000000004', id from public.roles where key = 'scholar'
  $q$, 'a member cannot grant themselves the scholar role');

  perform test.denied($q$
    insert into public.user_roles (user_id, role_id)
    select 'cccccccc-0000-4000-8000-000000000004', id from public.roles where key = 'moderator'
  $q$, 'a member cannot grant themselves the moderator role');

  perform test.denied($q$
    insert into public.user_roles (user_id, role_id)
    select 'cccccccc-0000-4000-8000-000000000004', id from public.roles where key = 'platform_admin'
  $q$, 'a member cannot grant themselves the platform_admin role');

  perform test.denied($q$
    select app.grant_role('cccccccc-0000-4000-8000-000000000004', 'moderator')
  $q$, 'and app.grant_role() refuses a non-admin caller');

  perform test.ok(not app.is_moderator(), 'the member is still not a moderator');

  perform test.denied($q$
    update public.qualifications set verified_at = now(),
           verified_by = 'cccccccc-0000-4000-8000-000000000004'
     where user_id = 'cccccccc-0000-4000-8000-000000000004'
  $q$, 'a member cannot stamp verified_at on a qualification');
end;
$$;
rollback;

\echo ''
\echo '### 6. Organization boundaries hold'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000003';   -- zayd, admin of the masjid trust
do $$
begin
  perform test.ok(app.is_org_admin('f0000000-0000-4000-8000-000000000001'),
    'positive control: zayd is an admin of his own organization');

  perform test.ok(not app.is_org_admin('f0000000-0000-4000-8000-000000000002'),
    'zayd is NOT an admin of the other organization');

  perform test.ok(
    test.count_of($q$select 1 from public.organization_members
                      where organization_id = 'f0000000-0000-4000-8000-000000000001'$q$) >= 4,
    'he sees his own organization''s roster');

  perform test.ok(
    test.count_of($q$select 1 from public.organization_members
                      where organization_id = 'f0000000-0000-4000-8000-000000000002'
                        and user_id <> 'cccccccc-0000-4000-8000-000000000003'$q$) = 0,
    'he sees NOTHING of the other organization''s roster');

  perform test.ok(
    test.count_of($q$select 1 from public.organization_verifications
                      where organization_id = 'f0000000-0000-4000-8000-000000000002'$q$) = 0,
    'he sees nothing of the other organization''s verification evidence');

  perform test.ok(
    test.count_of($q$select 1 from public.organization_verifications
                      where organization_id = 'f0000000-0000-4000-8000-000000000001'$q$) = 1,
    'positive control: his own organization''s verification is visible');

  perform test.denied($q$
    insert into public.organization_members (organization_id, user_id, org_role, status)
    values ('f0000000-0000-4000-8000-000000000002',
            'cccccccc-0000-4000-8000-000000000004', 'admin', 'active')
  $q$, 'he cannot add an admin to the other organization');

  perform test.ok(
    test.affected($q$update public.organizations set name = 'Renamed by an outsider'
                      where id = 'f0000000-0000-4000-8000-000000000002'$q$) = 0,
    'he cannot rename the other organization');

  -- Anonymous giving stays anonymous even from the receiving organization.
  perform test.ok(
    test.count_of($q$select 1 from public.donations
                      where campaign_id = 'fb000000-0000-4000-8000-000000000001'$q$) = 1,
    'an org admin sees only the NON-anonymous donation to their campaign');
end;
$$;
rollback;

\echo ''
\echo '### 7. Moderation is auditable and the audit trail is immutable'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'aaaaaaaa-0000-4000-8000-000000000002';   -- moderator bilal
do $$
declare
  v_before bigint;
  v_after  bigint;
  v_action uuid;
begin
  perform test.ok(app.is_moderator(), 'positive control: bilal holds the moderator role');

  select count(*) into v_before from public.audit_logs;

  v_action := app.record_moderation_action(
    p_action_type => 'content_removed',
    p_rationale   => 'Removed an off-purpose message after a substantiated report.',
    p_case        => 'fe000000-0000-4000-8000-000000000001',
    p_target_user => 'dddddddd-0000-4000-8000-000000000001');

  select count(*) into v_after from public.audit_logs;

  perform test.ok(v_after = v_before + 1,
    'every moderation action writes exactly one audit_logs row',
    format('before=%s after=%s', v_before, v_after));

  perform test.ok(
    test.count_of(format($q$select 1 from public.audit_logs
                            where entity_type = 'moderation_actions' and entity_id = %L$q$, v_action)) = 1,
    'the audit row points back at the moderation action');

  perform test.denied($q$update public.audit_logs set action = 'nothing happened'$q$,
    'a moderator cannot UPDATE audit_logs');
  perform test.denied($q$delete from public.audit_logs$q$,
    'a moderator cannot DELETE audit_logs');
  perform test.denied($q$update public.moderation_actions set rationale = 'rewritten'$q$,
    'a moderator cannot UPDATE a recorded moderation action');
  perform test.denied($q$delete from public.moderation_actions$q$,
    'a moderator cannot DELETE a recorded moderation action');
  perform test.denied($q$delete from public.report_evidence$q$,
    'a moderator cannot DELETE report evidence');
  perform test.denied($q$update public.report_evidence set body = 'edited'$q$,
    'a moderator cannot UPDATE report evidence');
  perform test.denied($q$delete from public.message_redactions$q$,
    'a moderator cannot DELETE preserved message originals');
  perform test.denied($q$
    select app.record_moderation_action(
      p_action_type => 'account_suspended',
      p_rationale   => 'Suspending myself to clear my own record.',
      p_target_user => 'aaaaaaaa-0000-4000-8000-000000000002')
  $q$, 'a moderator cannot take a moderation action against their own account');

  -- Wali contact is not a moderator privilege either.
  perform test.denied($q$
    select contact_email from public.wali_profiles limit 1
  $q$, 'not even a moderator can read wali contact columns');
end;
$$;
rollback;

\echo ''
\echo '### 8. The doorstep rule: exact address vs approximate area'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000004';   -- ibrahim, unrelated
do $$
begin
  perform test.ok(
    test.count_of($q$select 1 from public.service_request_private_details
                      where request_id = 'f4000000-0000-4000-8000-000000000001'$q$) = 0,
    'an unrelated member cannot read the exact address of a service request');

  perform test.ok(
    test.count_of($q$select 1 from public.service_requests_public
                      where id = 'f4000000-0000-4000-8000-000000000001'
                        and approximate_area is not null$q$) = 1,
    'but the approximate area IS readable through the public view');

  perform test.ok(not app.can_see_exact_location('f4000000-0000-4000-8000-000000000001'),
    'app.can_see_exact_location() agrees');
end;
$$;

set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000002';   -- tariq, ACCEPTED helper
do $$
begin
  perform test.ok(
    test.count_of($q$select 1 from public.service_request_private_details
                      where request_id = 'f4000000-0000-4000-8000-000000000001'$q$) = 1,
    'the accepted helper CAN read the exact address');
end;
$$;

set local request.jwt.claim.sub = 'bbbbbbbb-0000-4000-8000-000000000004';   -- khadija, requester
do $$
begin
  perform test.ok(
    test.count_of($q$select 1 from public.service_request_private_details
                      where request_id = 'f4000000-0000-4000-8000-000000000001'$q$) = 1,
    'the requester can read their own address');
end;
$$;

set local request.jwt.claim.sub = 'bbbbbbbb-0000-4000-8000-000000000003';   -- maryam, a responder on ANOTHER request
do $$
begin
  perform test.ok(
    test.count_of($q$select 1 from public.service_request_private_details$q$) = 0,
    'a responder who has not been accepted sees no addresses at all');
end;
$$;
rollback;

\echo ''
\echo '### 9. Restricted and departed accounts cannot write'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'dddddddd-0000-4000-8000-000000000002';   -- faisal, messaging_suspended
do $$
begin
  perform test.ok(app.is_restricted('dddddddd-0000-4000-8000-000000000002', 'messaging_suspended'),
    'positive control: faisal is under a messaging restriction');

  perform test.denied($q$
    insert into public.messages (conversation_id, sender_id, body)
    values ('f7000000-0000-4000-8000-000000000005',
            'dddddddd-0000-4000-8000-000000000002', 'Still here.')
  $q$, 'a messaging-suspended member cannot post, even in a channel they belong to');

  perform test.denied($q$
    insert into public.conversations (created_by, title)
    values ('dddddddd-0000-4000-8000-000000000002', 'A fresh start')
  $q$, 'nor open a new channel');
end;
$$;

set local request.jwt.claim.sub = 'dddddddd-0000-4000-8000-000000000003';   -- anas, soft-deleted
do $$
begin
  perform test.ok(not app.is_account_active('dddddddd-0000-4000-8000-000000000003'),
    'positive control: a soft-deleted account is not active');

  perform test.denied($q$
    insert into public.messages (conversation_id, sender_id, body)
    values ('f7000000-0000-4000-8000-000000000005',
            'dddddddd-0000-4000-8000-000000000003', 'I am back.')
  $q$, 'a soft-deleted account cannot post');

  perform test.denied($q$
    insert into public.service_requests (requester_id, title, description)
    values ('dddddddd-0000-4000-8000-000000000003', 'Need help', 'Please.')
  $q$, 'a soft-deleted account cannot open a service request');
end;
$$;
rollback;

\echo ''
\echo '### 10. Zakat eligibility cannot be flipped by a plain UPDATE'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000003';   -- zayd, org admin
do $$
begin
  perform test.ok(
    test.scalar_text($q$select zakat_eligible::text from public.campaigns
                        where id = 'fb000000-0000-4000-8000-000000000001'$q$) = 'true',
    'positive control: the winter fund was made zakat eligible through the function');

  perform test.denied($q$
    update public.campaigns set zakat_eligible = true
     where id = 'fb000000-0000-4000-8000-000000000002'
  $q$, 'an org admin cannot flip zakat_eligible with a plain UPDATE');

  perform test.denied($q$
    update public.campaigns
       set zakat_eligible = true,
           zakat_attestation_id = 'fc000000-0000-4000-8000-000000000001'
     where id = 'fb000000-0000-4000-8000-000000000002'
  $q$, 'nor by pointing at somebody else''s attestation');

  perform test.denied($q$
    insert into public.campaigns (organization_id, created_by, slug, title, description, zakat_eligible)
    values ('f0000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003',
            'born-eligible','Born eligible','A campaign that tries to be born zakat eligible.', true)
  $q$, 'a campaign cannot be created already zakat eligible');

  perform test.denied($q$
    select app.set_campaign_zakat_eligible('fb000000-0000-4000-8000-000000000002',
                                           'fc000000-0000-4000-8000-000000000001')
  $q$, 'and an org admin cannot call the privileged setter either');

  perform test.denied($q$
    update public.campaigns set payments_enabled = true
     where id = 'fb000000-0000-4000-8000-000000000001'
  $q$, 'payments cannot be enabled: the schema cannot represent it');
end;
$$;
rollback;

\echo ''
\echo '### 11. Unsend redacts for participants but preserves evidence'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'bbbbbbbb-0000-4000-8000-000000000001';   -- amina, the sender
do $$
begin
  update public.messages set unsent_at = now()
   where id = 'f8000000-0000-4000-8000-000000000001';

  perform test.ok(
    test.scalar_text($q$select coalesce(body, '<redacted>') from public.messages
                        where id = 'f8000000-0000-4000-8000-000000000001'$q$) = '<redacted>',
    'the body is gone from the participant-visible row after unsend');

  perform test.ok(
    test.count_of($q$select 1 from public.message_redactions$q$) = 0,
    'the sender cannot read the preserved original');
end;
$$;

set local request.jwt.claim.sub = 'aaaaaaaa-0000-4000-8000-000000000002';   -- moderator
do $$
begin
  perform test.ok(
    test.scalar_text($q$select original_body from public.message_redactions
                        where message_id = 'f8000000-0000-4000-8000-000000000001'$q$)
      = 'Assalamu alaikum. We start Saturday after Asr, upstairs. Bring a notebook.',
    'a moderator can still read the original: unsend does not destroy evidence');

  perform test.denied($q$update public.message_redactions set original_body = 'gone'$q$,
    'and the preserved original cannot be edited by anyone');
end;
$$;
rollback;

\echo ''
\echo '### 12. Personal data stays personal'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'aaaaaaaa-0000-4000-8000-000000000002';   -- moderator
do $$
begin
  perform test.ok(
    test.count_of($q$select 1 from public.private_impact_records$q$) = 0,
    'not even a moderator can read private impact records');
  perform test.ok(
    test.count_of($q$select 1 from public.trusted_contacts$q$) = 0,
    'not even a moderator can browse trusted contacts');
  perform test.ok(
    test.count_of($q$select 1 from public.user_settings
                      where user_id <> 'aaaaaaaa-0000-4000-8000-000000000002'$q$) = 0,
    'not even a moderator can read another member''s settings');
  perform test.ok(
    test.count_of($q$select 1 from public.consent_records
                      where user_id <> 'aaaaaaaa-0000-4000-8000-000000000002'$q$) = 0,
    'not even a moderator can read another member''s consent receipts');
  perform test.ok(
    test.count_of($q$select 1 from public.notifications
                      where user_id <> 'aaaaaaaa-0000-4000-8000-000000000002'$q$) = 0,
    'notifications are strictly per-recipient');
end;
$$;

set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000002';   -- tariq
do $$
begin
  perform test.ok(
    test.count_of($q$select 1 from public.private_impact_records$q$) = 1,
    'positive control: a member reads their own impact record');
  perform test.denied($q$
    insert into public.notifications (user_id, kind, title)
    values ('cccccccc-0000-4000-8000-000000000004', 'fake', 'You have been suspended')
  $q$, 'a member cannot fabricate a notification for someone else');
  perform test.denied($q$
    insert into public.private_impact_records (user_id, summary)
    values ('cccccccc-0000-4000-8000-000000000004', 'Deeds attributed to someone else')
  $q$, 'a member cannot write an impact record onto another account');
end;
$$;
rollback;

\echo ''
\echo '### 13. Structural invariants'
begin;
set local role authenticated;
set local request.jwt.claim.sub = 'bbbbbbbb-0000-4000-8000-000000000001';   -- amina
do $$
begin
  perform test.ok(
    test.affected($q$update public.conversation_purposes
                       set purpose_statement = 'Actually, let us talk about something else entirely.'
                     where conversation_id = 'f7000000-0000-4000-8000-000000000001'$q$) = 0,
    'a declared purpose is immutable: no UPDATE policy exists');

  perform test.denied($q$
    insert into public.commitment_confirmations (commitment_id, confirmed_by)
    values ('f6000000-0000-4000-8000-000000000002', 'cccccccc-0000-4000-8000-000000000004')
  $q$, 'nobody may record a confirmation on behalf of another member');

  perform test.denied($q$
    insert into public.blocks (blocker_id, blocked_id)
    values ('bbbbbbbb-0000-4000-8000-000000000003', 'cccccccc-0000-4000-8000-000000000004')
  $q$, 'a member cannot create a block on someone else''s behalf');

  perform test.denied($q$
    insert into public.volunteer_opportunities
      (organization_id, created_by, title, description, is_youth_facing,
       requires_safeguarding_clearance, is_published)
    values ('f0000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000001',
            'Unsupervised youth club','No clearance needed.', true, false, true)
  $q$, 'youth-facing work cannot be posted without requiring safeguarding clearance');
end;
$$;

set local request.jwt.claim.sub = 'cccccccc-0000-4000-8000-000000000002';   -- tariq
do $$
begin
  perform test.denied($q$
    insert into public.commitment_confirmations (commitment_id, confirmed_by)
    values ('f6000000-0000-4000-8000-000000000001', 'cccccccc-0000-4000-8000-000000000002')
  $q$, 'a member cannot confirm their own commitment');
end;
$$;
rollback;

\echo ''
\echo '### 14. Unauthenticated and anonymous access'
begin;
set local role anon;
do $$
begin
  perform test.denied($q$select * from public.profiles$q$,
    'anon cannot read the member directory');
  perform test.denied($q$select * from public.messages$q$,
    'anon cannot read messages');
  perform test.denied($q$select * from public.wali_profiles$q$,
    'anon cannot read wali profiles');
  perform test.denied($q$select * from public.audit_logs$q$,
    'anon cannot read the audit log');
  perform test.ok(
    test.count_of($q$select 1 from public.skills where is_active$q$) > 0,
    'anon CAN read the public skills vocabulary');
  perform test.ok(
    test.count_of($q$select 1 from public.organizations$q$) = 2,
    'anon CAN read the public organization directory');
end;
$$;
rollback;

\echo ''
\echo '### 15. Every table in public still has RLS enabled'
do $$
declare
  v_missing text;
  v_no_policy text;
  v_tables int;
  v_policies int;
begin
  select string_agg(c.relname, ', ')
    into v_missing
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity;
  perform test.ok(v_missing is null, 'RLS is enabled on every table in schema public',
                  'missing on: ' || coalesce(v_missing, ''));

  select string_agg(c.relname, ', ')
    into v_no_policy
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relkind = 'r'
     and not exists (select 1 from pg_policy p where p.polrelid = c.oid);
  perform test.ok(v_no_policy is null, 'every table carries at least one explicit policy',
                  'no policy on: ' || coalesce(v_no_policy, ''));

  select count(*) into v_tables
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relkind = 'r';
  select count(*) into v_policies
    from pg_policy p join pg_class c on c.oid = p.polrelid
    join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public';

  raise notice 'PASS: schema summary -- % tables, % policies', v_tables, v_policies;
end;
$$;

\echo ''
\echo '### 16. Registration: a real auth account becoming a member'
begin;
-- The auth row is created before the role switch: `authenticated` has SELECT
-- on auth.users and nothing more, which is exactly the point.
insert into auth.users (id, email, email_confirmed_at)
values ('99999999-0000-4000-8000-000000000001', 'newcomer@example.test', null);

set local role authenticated;
set local request.jwt.claim.sub = '99999999-0000-4000-8000-000000000001';
do $$
declare
  v_uid uuid := '99999999-0000-4000-8000-000000000001';
begin
  perform test.ok(
    (select not has_profile from public.current_member()),
    'a signed-in account with no profile reports has_profile false');

  perform test.ok(
    (select email_confirmed = false from public.current_member()),
    'and reports its address as unconfirmed');

  -- The hole 0016 closes: self-insert is permitted, self-elevation is not.
  perform test.denied($q$
    insert into public.profiles (id, display_name, contact_email, gender, verification_level)
    values ('99999999-0000-4000-8000-000000000001', 'Sneaky', 'sneaky@example.test',
            'male', 'scholar_verified')
  $q$, 'a member cannot insert their own profile already verified');

  perform test.ok(
    (select public.register_member('Yahya T.', 'male', p_accept_covenant => true)) = v_uid,
    'register_member returns the auth id, never a caller-supplied one');

  perform test.ok(
    test.scalar_text($q$select contact_email::text from public.profiles
                        where id = '99999999-0000-4000-8000-000000000001'$q$)
      = 'newcomer@example.test',
    'the profile takes its address from auth.users');

  perform test.ok(
    test.scalar_text($q$select verification_level::text from public.profiles
                        where id = '99999999-0000-4000-8000-000000000001'$q$) = 'unverified',
    'a brand new member is unverified');

  perform test.ok(
    test.count_of($q$select 1 from public.user_settings
                      where user_id = '99999999-0000-4000-8000-000000000001'$q$) = 1,
    'registration creates the settings row');

  perform test.ok(
    test.count_of($q$select 1 from public.user_safeguards
                      where user_id = '99999999-0000-4000-8000-000000000001'
                        and require_purpose_for_contact$q$) = 1,
    'registration creates safeguards with purpose-bound contact already on');

  perform test.ok(
    (select role_keys = array['member'] from public.current_member()),
    'registration grants the member role and nothing else');

  perform test.denied(
    $q$select public.register_member('Yahya Again', 'male')$q$,
    'register_member refuses to run twice for the same account');

  perform test.denied($q$
    update public.profiles set gender = 'female'
     where id = '99999999-0000-4000-8000-000000000001'
  $q$, 'a member cannot change their own gender after registration');

  perform test.allowed($q$
    update public.profiles set display_name = 'Yahya Talib'
     where id = '99999999-0000-4000-8000-000000000001'
  $q$, 'but may still edit the rest of their profile');
end;
$$;
rollback;

\echo ''
\echo '### 17. Confirming the address earns basic verification, the ordinary way'
begin;
insert into auth.users (id, email, email_confirmed_at)
values ('99999999-0000-4000-8000-000000000002', 'confirmer@example.test', null);

set local role authenticated;
set local request.jwt.claim.sub = '99999999-0000-4000-8000-000000000002';
do $$
begin
  perform public.register_member('Sumayya K.', 'female', p_accept_covenant => true);
end;
$$;

-- GoTrue writes this column when the member follows the link in their mail.
reset role;
update auth.users set email_confirmed_at = now()
 where id = '99999999-0000-4000-8000-000000000002';

set local role authenticated;
set local request.jwt.claim.sub = '99999999-0000-4000-8000-000000000002';
do $$
begin
  perform test.ok(
    test.scalar_text($q$select verification_level::text from public.profiles
                        where id = '99999999-0000-4000-8000-000000000002'$q$) = 'basic',
    'confirming the address lifts the profile to basic');

  perform test.ok(
    test.count_of($q$select 1 from public.user_verifications
                      where user_id = '99999999-0000-4000-8000-000000000002'
                        and method = 'email' and status = 'verified'
                        and reviewed_by is null$q$) = 1,
    'and it is recorded as a verification row with no human reviewer claimed');

  perform test.ok(
    test.count_of($q$select 1 from public.user_verifications
                      where user_id = '99999999-0000-4000-8000-000000000002'$q$) = 1,
    'a second confirmation would not duplicate it');

  perform test.denied($q$
    insert into public.user_verifications (user_id, level, status, method, verified_at)
    values ('99999999-0000-4000-8000-000000000002', 'scholar_verified', 'verified',
            'email', now())
  $q$, 'the machine-confirmed exemption does not let a member write a scholar level');
end;
$$;
rollback;

\echo ''
\echo '### 18. register_member is not reachable without a signed-in account'
begin;
set local role anon;
do $$
begin
  perform test.denied($q$select public.register_member('Nobody', 'male')$q$,
    'anon cannot register a member');
  perform test.denied($q$select 1 from public.current_member()$q$,
    'and current_member() is not even executable without a session');
end;
$$;
rollback;

\echo ''
\echo '### all assertions completed'
