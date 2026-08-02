-- =====================================================================
-- Fi Sabilillah -- 0001_extensions_and_helpers.sql
-- Extensions, the `app` helper schema, and the security-definer helper
-- functions that every RLS policy in 0013 is built on.
--
-- NOTE: this migration deliberately contains NO shim for auth.users /
-- auth.uid(). On Supabase those are supplied by the platform. For local
-- plain-Postgres testing they are supplied by supabase/tests/00_bootstrap.sql.
-- =====================================================================

create extension if not exists pgcrypto;
create extension if not exists citext;
create extension if not exists pg_trgm;

-- ---------------------------------------------------------------------
-- Helper schema. Nothing user-facing lives here; only functions that
-- policies call. `app` is readable/executable by application roles but
-- not writable.
-- ---------------------------------------------------------------------
create schema if not exists app;

grant usage on schema app to public;

-- ---------------------------------------------------------------------
-- Shared updated_at maintenance trigger.
-- ---------------------------------------------------------------------
create or replace function app.set_updated_at()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

comment on function app.set_updated_at() is
  'Shared BEFORE UPDATE trigger keeping updated_at honest regardless of what the client sends.';

-- Convenience: attach the updated_at trigger to a table.
create or replace function app.attach_updated_at(p_table regclass)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_name text;
begin
  v_name := 'trg_' || replace(p_table::text, '.', '_') || '_updated_at';
  execute format(
    'create trigger %I before update on %s for each row execute function app.set_updated_at()',
    v_name, p_table::text);
end;
$$;

-- ---------------------------------------------------------------------
-- Identity helpers
-- ---------------------------------------------------------------------

-- The calling user's id, or NULL for anon / service contexts without a JWT.
create or replace function app.current_user_id()
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  return auth.uid();
exception
  when others then
    return null;
end;
$$;

comment on function app.current_user_id() is
  'Current authenticated user id (auth.uid()), NULL when unauthenticated.';

-- True when the request is running with the Supabase service key or as a
-- database superuser (migrations, back-office jobs, edge functions).
create or replace function app.is_service_role()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_role text;
begin
  begin
    v_role := auth.role();
  exception when others then
    v_role := null;
  end;
  return coalesce(v_role, '') = 'service_role'
      or pg_catalog.current_setting('is_superuser', true) = 'on';
end;
$$;

-- Does the current user hold the named platform role?
create or replace function app.has_role(p_key text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null then
    return false;
  end if;
  return exists (
    select 1
      from public.user_roles ur
      join public.roles r on r.id = ur.role_id
     where ur.user_id = v_uid
       and r.key = p_key
       and ur.revoked_at is null
       and (ur.expires_at is null or ur.expires_at > now())
  );
end;
$$;

create or replace function app.is_moderator()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  return app.is_service_role()
      or app.has_role('moderator')
      or app.has_role('platform_admin');
end;
$$;

comment on function app.is_moderator() is
  'Safety team. Can see reports, evidence, redactions and moderation cases.';

create or replace function app.is_platform_admin()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  return app.is_service_role() or app.has_role('platform_admin');
end;
$$;

create or replace function app.is_scholar()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  return app.has_role('scholar');
end;
$$;

-- ---------------------------------------------------------------------
-- Account state helpers
-- ---------------------------------------------------------------------

-- An account is "active" when its profile exists, is not soft-deleted, and
-- is not under a full suspension.
create or replace function app.is_account_active(p_user uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if p_user is null then
    return false;
  end if;
  if not exists (select 1 from public.profiles p
                  where p.id = p_user and p.deleted_at is null) then
    return false;
  end if;
  return not exists (
    select 1 from public.restrictions r
     where r.user_id = p_user
       and r.restriction_type = 'full_suspension'::public.restriction_type
       and r.lifted_at is null
       and r.starts_at <= now()
       and (r.expires_at is null or r.expires_at > now())
  );
end;
$$;

create or replace function app.is_restricted(p_user uuid, p_kind text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if p_user is null then
    return true;
  end if;
  return exists (
    select 1 from public.restrictions r
     where r.user_id = p_user
       and r.restriction_type::text in (p_kind, 'full_suspension')
       and r.lifted_at is null
       and r.starts_at <= now()
       and (r.expires_at is null or r.expires_at > now())
  );
end;
$$;

-- ---------------------------------------------------------------------
-- Blocking
-- ---------------------------------------------------------------------

-- Symmetric: if either party blocked the other, they are blocked.
create or replace function app.is_blocked_between(a uuid, b uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if a is null or b is null or a = b then
    return false;
  end if;
  return exists (
    select 1 from public.blocks bl
     where bl.lifted_at is null
       and ((bl.blocker_id = a and bl.blocked_id = b)
         or (bl.blocker_id = b and bl.blocked_id = a))
  );
end;
$$;

comment on function app.is_blocked_between(uuid, uuid) is
  'Blocking is symmetric in effect: neither side may reach the other once a block exists.';

-- ---------------------------------------------------------------------
-- Conversation helpers
-- ---------------------------------------------------------------------

create or replace function app.is_conversation_member(conv uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or conv is null then
    return false;
  end if;
  return exists (
    select 1 from public.conversation_members cm
     where cm.conversation_id = conv
       and cm.user_id = v_uid
       and cm.removed_at is null
       and cm.left_at is null
  );
end;
$$;

-- Every conversation must carry a declared purpose. Messaging is
-- purpose-bound by design: no open-ended "slide into DMs" surface.
create or replace function app.conversation_has_purpose(conv uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  return exists (select 1 from public.conversation_purposes cp
                  where cp.conversation_id = conv);
end;
$$;

-- True when the viewer is blocked with ANY other active member of the
-- conversation. Used to make blocked pairs mutually invisible.
create or replace function app.conversation_blocked_for(conv uuid, viewer uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if conv is null or viewer is null then
    return false;
  end if;
  return exists (
    select 1
      from public.conversation_members cm
      join public.blocks bl
        on bl.lifted_at is null
       and ((bl.blocker_id = viewer and bl.blocked_id = cm.user_id)
         or (bl.blocked_id = viewer and bl.blocker_id = cm.user_id))
     where cm.conversation_id = conv
       and cm.user_id <> viewer
       and cm.removed_at is null
  );
end;
$$;

-- Can the current user open a conversation with `target` for `purpose`?
-- Enforces: both accounts active, no block, messaging not suspended, and
-- for formal introductions that a wali-mediated introduction is live.
create or replace function app.can_open_conversation(
  target uuid,
  purpose text
)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or target is null then
    return false;
  end if;
  if not app.is_account_active(v_uid) or not app.is_account_active(target) then
    return false;
  end if;
  if app.is_blocked_between(v_uid, target) then
    return false;
  end if;
  if app.is_restricted(v_uid, 'messaging_suspended') then
    return false;
  end if;

  if purpose = 'formal_introduction' then
    -- Direct correspondence only exists inside an approved, forwarded,
    -- wali-supervised introduction.
    return exists (
      select 1
        from public.formal_introduction_requests fir
        join public.formal_introduction_participants p1
          on p1.introduction_id = fir.id and p1.user_id = v_uid
        join public.formal_introduction_participants p2
          on p2.introduction_id = fir.id and p2.user_id = target
       where fir.status in (
               'approved_and_forwarded'::public.introduction_status,
               'in_correspondence'::public.introduction_status)
         and p1.revoked_at is null
         and p2.revoked_at is null
    );
  end if;

  return true;
end;
$$;

-- Is this member currently accepting formal introductions? Their
-- formal_introduction_settings row is private, so policies must ask
-- through this definer helper rather than reading the table.
create or replace function app.is_open_to_introductions(p_user uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if p_user is null then
    return false;
  end if;
  return exists (
    select 1 from public.formal_introduction_settings s
     where s.user_id = p_user
       and s.is_open_to_introductions
       and s.wali_profile_id is not null
       and (s.paused_until is null or s.paused_until < now())
  );
end;
$$;

-- ---------------------------------------------------------------------
-- Organization / community helpers
-- ---------------------------------------------------------------------

create or replace function app.is_org_member(org uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or org is null then
    return false;
  end if;
  return exists (
    select 1 from public.organization_members om
     where om.organization_id = org
       and om.user_id = v_uid
       and om.status = 'active'::public.membership_status
  );
end;
$$;

create or replace function app.is_org_admin(org uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if org is null then
    return false;
  end if;
  if app.is_platform_admin() then
    return true;
  end if;
  v_uid := app.current_user_id();
  if v_uid is null then
    return false;
  end if;
  return exists (
    select 1 from public.organization_members om
     where om.organization_id = org
       and om.user_id = v_uid
       and om.status = 'active'::public.membership_status
       and om.org_role in ('admin'::public.org_member_role, 'owner'::public.org_member_role)
  );
end;
$$;

comment on function app.is_org_admin(uuid) is
  'Scoped to ONE organization. Never grants cross-organization visibility.';

create or replace function app.is_community_member(comm uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or comm is null then
    return false;
  end if;
  return exists (
    select 1 from public.community_members cm
     where cm.community_id = comm
       and cm.user_id = v_uid
       and cm.status = 'active'::public.membership_status
  );
end;
$$;

create or replace function app.is_community_moderator(comm uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or comm is null then
    return false;
  end if;
  if app.is_moderator() then
    return true;
  end if;
  return exists (
    select 1 from public.community_members cm
     where cm.community_id = comm
       and cm.user_id = v_uid
       and cm.status = 'active'::public.membership_status
       and cm.is_moderator
  );
end;
$$;

-- ---------------------------------------------------------------------
-- Project helpers
--
-- These exist to BREAK POLICY RECURSION. `projects` needs to ask about
-- `project_members` and `project_members` needs to ask about `projects`;
-- expressed as inline subqueries PostgreSQL rejects that with "infinite
-- recursion detected in policy". A SECURITY DEFINER helper reads the table
-- without triggering its policies, so the cycle disappears.
-- ---------------------------------------------------------------------

create or replace function app.is_project_member(p_project uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_project is null then
    return false;
  end if;
  return exists (
    select 1 from public.project_members pm
     where pm.project_id = p_project
       and pm.user_id = v_uid
       and pm.status = 'active'::public.membership_status
  );
end;
$$;

create or replace function app.is_project_lead(p_project uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_project is null then
    return false;
  end if;
  return exists (select 1 from public.projects p
                  where p.id = p_project and p.lead_id = v_uid);
end;
$$;

-- ---------------------------------------------------------------------
-- Commitment helpers (same recursion-breaking reason)
-- ---------------------------------------------------------------------

create or replace function app.is_commitment_owner(p_commitment uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_commitment is null then
    return false;
  end if;
  return exists (select 1 from public.commitments c
                  where c.id = p_commitment and c.user_id = v_uid);
end;
$$;

create or replace function app.has_confirmed_commitment(p_commitment uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_commitment is null then
    return false;
  end if;
  return exists (select 1 from public.commitment_confirmations cc
                  where cc.commitment_id = p_commitment and cc.confirmed_by = v_uid);
end;
$$;

-- ---------------------------------------------------------------------
-- Introduction / wali helpers
--
-- formal_introduction_requests, formal_introduction_participants and
-- wali_profiles all need to ask about each other. Every one of those
-- questions goes through a definer helper so the policy graph stays acyclic
-- AND so a policy never accidentally depends on a row the caller cannot see.
-- ---------------------------------------------------------------------

create or replace function app.is_introduction_participant(p_introduction uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_introduction is null then
    return false;
  end if;
  return exists (
    select 1 from public.formal_introduction_participants fip
     where fip.introduction_id = p_introduction
       and fip.user_id = v_uid
       and fip.revoked_at is null
  );
end;
$$;

-- Is the current user one of the two people the introduction is about?
create or replace function app.is_introduction_principal(p_introduction uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_introduction is null then
    return false;
  end if;
  return exists (
    select 1 from public.formal_introduction_requests fir
     where fir.id = p_introduction
       and (fir.initiator_id = v_uid or fir.recipient_id = v_uid)
  );
end;
$$;

create or replace function app.is_wali_in_introduction(p_introduction uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_introduction is null then
    return false;
  end if;
  return exists (
    select 1
      from public.formal_introduction_requests fir
      join public.wali_profiles wp
        on wp.id in (fir.initiator_wali_profile_id, fir.recipient_wali_profile_id)
     where fir.id = p_introduction
       and wp.wali_user_id = v_uid
  );
end;
$$;

-- Is the current user the wali named on this wali_profile row?
create or replace function app.is_wali_of_profile(p_wali_profile uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_wali_profile is null then
    return false;
  end if;
  return exists (select 1 from public.wali_profiles wp
                  where wp.id = p_wali_profile and wp.wali_user_id = v_uid);
end;
$$;

-- Is the current user the WARD (the member the wali acts for)?
create or replace function app.owns_wali_profile(p_wali_profile uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_wali_profile is null then
    return false;
  end if;
  return exists (select 1 from public.wali_profiles wp
                  where wp.id = p_wali_profile and wp.user_id = v_uid);
end;
$$;

-- Row-level (NOT column-level) visibility of a wali_profile. The contact
-- columns remain unreadable through SQL regardless of what this returns.
create or replace function app.can_view_wali_profile(p_wali_profile uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_wali_profile is null then
    return false;
  end if;
  if app.is_moderator() then
    return true;
  end if;
  if exists (select 1 from public.wali_profiles wp
              where wp.id = p_wali_profile
                and (wp.user_id = v_uid or wp.wali_user_id = v_uid)) then
    return true;
  end if;
  return exists (
    select 1
      from public.formal_introduction_participants fip
      join public.formal_introduction_requests fir on fir.id = fip.introduction_id
     where fip.user_id = v_uid
       and fip.revoked_at is null
       and p_wali_profile in (fir.initiator_wali_profile_id, fir.recipient_wali_profile_id)
  );
end;
$$;

-- ---------------------------------------------------------------------
-- Service request helpers
-- ---------------------------------------------------------------------

-- Exact address disclosure rule: requester, the ACCEPTED helper, and
-- moderators. Nobody else -- not even other responders.
create or replace function app.can_see_exact_location(p_request uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null or p_request is null then
    return false;
  end if;
  if app.is_moderator() then
    return true;
  end if;
  if exists (select 1 from public.service_requests sr
              where sr.id = p_request and sr.requester_id = v_uid) then
    return true;
  end if;
  return exists (
    select 1 from public.service_requests sr
     where sr.id = p_request
       and sr.assigned_helper_id = v_uid
       and sr.helper_accepted_at is not null
  );
end;
$$;

-- ---------------------------------------------------------------------
-- Audit
-- ---------------------------------------------------------------------

create or replace function app.write_audit(
  p_action        text,
  p_entity_type   text,
  p_entity_id     uuid,
  p_metadata      jsonb default '{}'::jsonb,
  p_actor         uuid  default null,
  p_subject       uuid  default null
)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_id uuid;
begin
  insert into public.audit_logs (actor_id, subject_id, action, entity_type, entity_id, metadata)
  values (coalesce(p_actor, app.current_user_id()), p_subject, p_action, p_entity_type,
          p_entity_id, coalesce(p_metadata, '{}'::jsonb))
  returning id into v_id;
  return v_id;
end;
$$;

comment on function app.write_audit(text, text, uuid, jsonb, uuid, uuid) is
  'Append-only audit write. audit_logs has no UPDATE/DELETE policy anywhere.';
