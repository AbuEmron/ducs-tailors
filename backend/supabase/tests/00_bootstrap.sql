-- =====================================================================
-- Fi Sabilillah -- tests/00_bootstrap.sql
--
-- LOCAL ONLY. Never applied to Supabase.
--
-- Supabase supplies the `auth` schema, the `auth.users` table, the
-- `auth.uid()` / `auth.role()` helpers, and the anon / authenticated /
-- service_role database roles. This file recreates just enough of that
-- surface so the migrations in supabase/migrations/ apply unchanged on a
-- plain PostgreSQL 16 cluster and the RLS tests can impersonate users.
--
-- The migrations themselves contain NO part of this shim.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Application roles (Supabase creates these for you).
-- ---------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin noinherit;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin noinherit;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then
    create role service_role nologin noinherit bypassrls;
  end if;
end;
$$;

-- ---------------------------------------------------------------------
-- auth schema
-- ---------------------------------------------------------------------
create schema if not exists auth;

grant usage on schema auth to anon, authenticated, service_role;

create table if not exists auth.users (
  id                  uuid primary key default gen_random_uuid(),
  email               text unique,
  phone               text,
  encrypted_password  text,
  raw_user_meta_data  jsonb not null default '{}'::jsonb,
  email_confirmed_at  timestamptz,
  banned_until        timestamptz,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- auth.uid(): Supabase reads the `sub` claim of the request JWT. PostgREST
-- exposes it as the GUC request.jwt.claim.sub (legacy) and inside
-- request.jwt.claims (current). We honour both so tests can use either.
-- ---------------------------------------------------------------------
create or replace function auth.uid()
returns uuid
language plpgsql
stable
as $$
declare
  v_raw    text;
  v_claims text;
begin
  v_raw := nullif(current_setting('request.jwt.claim.sub', true), '');
  if v_raw is null then
    v_claims := nullif(current_setting('request.jwt.claims', true), '');
    if v_claims is not null then
      v_raw := nullif(v_claims::jsonb ->> 'sub', '');
    end if;
  end if;
  if v_raw is null then
    return null;
  end if;
  return v_raw::uuid;
exception
  when others then
    return null;
end;
$$;

-- ---------------------------------------------------------------------
-- auth.role(): the PostgREST role claim. Falls back to the session role so
-- that `set local role service_role` behaves like a service-key request.
-- ---------------------------------------------------------------------
create or replace function auth.role()
returns text
language plpgsql
stable
as $$
declare
  v_raw    text;
  v_claims text;
begin
  v_raw := nullif(current_setting('request.jwt.claim.role', true), '');
  if v_raw is null then
    v_claims := nullif(current_setting('request.jwt.claims', true), '');
    if v_claims is not null then
      v_raw := nullif(v_claims::jsonb ->> 'role', '');
    end if;
  end if;
  return coalesce(v_raw, current_user::text);
exception
  when others then
    return current_user::text;
end;
$$;

-- ---------------------------------------------------------------------
-- auth.email(): convenience mirror of the Supabase helper.
-- ---------------------------------------------------------------------
create or replace function auth.email()
returns text
language sql
stable
as $$
  select u.email from auth.users u where u.id = auth.uid();
$$;

grant execute on function auth.uid() to anon, authenticated, service_role;
grant execute on function auth.role() to anon, authenticated, service_role;
grant execute on function auth.email() to anon, authenticated, service_role;
grant select on auth.users to authenticated, service_role;
