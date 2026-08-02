-- =====================================================================
-- Fi Sabilillah -- 0003_identity_profiles_safeguards.sql
-- Profiles, settings, personal safeguards, and platform role grants.
-- =====================================================================

-- ---------------------------------------------------------------------
-- profiles: one row per auth.users row. Deliberately thin on personal
-- data; anything sensitive lives in a narrower table with tighter RLS.
-- ---------------------------------------------------------------------
create table public.profiles (
  id                    uuid primary key references auth.users(id) on delete cascade,
  display_name          text not null check (length(btrim(display_name)) between 2 and 80),
  kunya                 text,
  contact_email         citext not null,
  locale                text not null default 'en',
  timezone              text not null default 'UTC',
  gender                public.gender not null,
  year_of_birth         int check (year_of_birth between 1900 and 2100),
  bio                   text check (bio is null or length(bio) <= 2000),
  city                  text,
  country_code          char(2),
  avatar_path           text,          -- storage object path, not a public URL
  -- The highest verification level currently held. Maintained ONLY by the
  -- trigger in 0014 from user_verifications; never writable by the user.
  verification_level    public.verification_level not null default 'unverified',
  is_available_for_service boolean not null default true,
  is_searchable         boolean not null default true,
  accepted_covenant_at  timestamptz,
  last_active_at        timestamptz,
  deleted_at            timestamptz,
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now(),
  constraint profiles_contact_email_format check (contact_email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

create unique index profiles_contact_email_uk
  on public.profiles (contact_email) where deleted_at is null;

comment on table public.profiles is
  'Public-facing member record. Not a social profile: no follower graph, no feed.';

-- ---------------------------------------------------------------------
-- user_settings: 1:1 preferences.
-- ---------------------------------------------------------------------
create table public.user_settings (
  id                        uuid primary key default gen_random_uuid(),
  user_id                   uuid not null unique references public.profiles(id) on delete cascade,
  notification_email        boolean not null default true,
  notification_push         boolean not null default false,
  digest_frequency          text not null default 'weekly'
                              check (digest_frequency in ('never','daily','weekly','monthly')),
  quiet_hours_start         time,
  quiet_hours_end           time,
  show_city_publicly        boolean not null default false,
  allow_service_requests    boolean not null default true,
  allow_learning_invitations boolean not null default true,
  preferred_gender_policy   public.gender_policy not null default 'any',
  data_processing_region    text not null default 'unspecified',
  created_at                timestamptz not null default now(),
  updated_at                timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- safeguard_presets: curated bundles of protective settings that a user
-- can adopt (e.g. "strict", "youth", "new-to-platform"). Lookup-like but
-- structural enough to keep as a table admins maintain.
-- ---------------------------------------------------------------------
create table public.safeguard_presets (
  id            uuid primary key default gen_random_uuid(),
  slug          text not null unique,
  name          text not null,
  description   text not null,
  settings      jsonb not null default '{}'::jsonb,
  is_default    boolean not null default false,
  is_active     boolean not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.safeguard_presets is
  'Admin-curated protective defaults. Users adopt one; adopting never lowers an existing safeguard silently.';

-- ---------------------------------------------------------------------
-- user_safeguards: the user's own boundaries. Enforced by policy and by
-- application code. Changes are audited (0014) because loosening a
-- safeguard is a safety-relevant event.
-- ---------------------------------------------------------------------
create table public.user_safeguards (
  id                          uuid primary key default gen_random_uuid(),
  user_id                     uuid not null unique references public.profiles(id) on delete cascade,
  preset_id                   uuid references public.safeguard_presets(id) on delete set null,
  require_purpose_for_contact boolean not null default true,
  allow_unsolicited_contact   boolean not null default false,
  gender_interaction_policy   public.gender_policy not null default 'any',
  require_wali_for_introductions boolean not null default true,
  block_media_from_strangers  boolean not null default true,
  auto_flag_keywords          text[] not null default '{}',
  min_counterparty_verification public.verification_level not null default 'basic',
  guardian_notify_email       citext,
  is_minor_supervised         boolean not null default false,
  created_at                  timestamptz not null default now(),
  updated_at                  timestamptz not null default now()
);

comment on column public.user_safeguards.require_wali_for_introductions is
  'Default TRUE. Turning this off does not bypass the counterparty''s own wali requirement.';

-- ---------------------------------------------------------------------
-- user_roles: platform role grants. NEVER self-service.
-- ---------------------------------------------------------------------
create table public.user_roles (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references public.profiles(id) on delete cascade,
  role_id      uuid not null references public.roles(id) on delete restrict,
  granted_by   uuid references public.profiles(id) on delete set null,
  granted_at   timestamptz not null default now(),
  expires_at   timestamptz,
  revoked_at   timestamptz,
  revoked_by   uuid references public.profiles(id) on delete set null,
  reason       text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint user_roles_no_self_grant check (granted_by is null or granted_by <> user_id)
);

create unique index user_roles_active_uk
  on public.user_roles (user_id, role_id) where revoked_at is null;

comment on constraint user_roles_no_self_grant on public.user_roles is
  'Belt-and-braces: a role grant can never record the recipient as the grantor.';

select app.attach_updated_at('public.profiles');
select app.attach_updated_at('public.user_settings');
select app.attach_updated_at('public.safeguard_presets');
select app.attach_updated_at('public.user_safeguards');
select app.attach_updated_at('public.user_roles');
