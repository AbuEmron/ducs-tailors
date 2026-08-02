-- =====================================================================
-- Fi Sabilillah -- 0004_organizations_communities.sql
-- Masjids, charities, study circles and local communities.
-- =====================================================================

create table public.organizations (
  id                 uuid primary key default gen_random_uuid(),
  slug               text not null unique,
  name               text not null check (length(btrim(name)) between 2 and 160),
  legal_name         text,
  org_kind           text not null default 'community_group'
                       check (org_kind in ('masjid','charity','school','community_group',
                                           'student_society','relief_agency','other')),
  description        text,
  public_email       citext,
  public_phone       text,
  website_url        text,
  city               text,
  country_code       char(2),
  -- Highest verification level currently held; maintained by trigger from
  -- organization_verifications, never directly writable.
  verification_level public.verification_level not null default 'unverified',
  is_accepting_volunteers boolean not null default true,
  safeguarding_policy_url text,
  created_by         uuid references public.profiles(id) on delete set null,
  deleted_at         timestamptz,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.organizations is
  'Public directory entry. Private operational data lives in organization_members / organization_verifications.';

create table public.organization_members (
  id               uuid primary key default gen_random_uuid(),
  organization_id  uuid not null references public.organizations(id) on delete cascade,
  user_id          uuid not null references public.profiles(id) on delete cascade,
  org_role         public.org_member_role not null default 'member',
  title            text,
  status           public.membership_status not null default 'pending',
  -- Safeguarding clearance recorded by an org admin for youth-facing work.
  safeguarding_cleared_at timestamptz,
  safeguarding_cleared_by uuid references public.profiles(id) on delete set null,
  invited_by       uuid references public.profiles(id) on delete set null,
  joined_at        timestamptz,
  removed_at       timestamptz,
  notes_internal   text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  unique (organization_id, user_id)
);

comment on table public.organization_members is
  'PRIVATE roster. Visible only within the owning organization (plus moderators).';

create table public.organization_verifications (
  id               uuid primary key default gen_random_uuid(),
  organization_id  uuid not null references public.organizations(id) on delete cascade,
  level            public.verification_level not null,
  status           public.verification_status not null default 'pending',
  method           text not null default 'document_review',
  evidence_path    text,                    -- storage object path, private bucket
  evidence_note    text,
  submitted_by     uuid references public.profiles(id) on delete set null,
  reviewed_by      uuid references public.profiles(id) on delete set null,
  reviewed_at      timestamptz,
  expires_at       timestamptz,
  revoked_at       timestamptz,
  revocation_reason text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.organization_verifications is
  'PRIVATE. Contains submitted evidence references; never cross-organization readable.';

create table public.communities (
  id                uuid primary key default gen_random_uuid(),
  slug              text not null unique,
  name              text not null,
  purpose           text not null,
  organization_id   uuid references public.organizations(id) on delete set null,
  visibility        public.community_visibility not null default 'public',
  gender_policy     public.gender_policy not null default 'any',
  city              text,
  country_code      char(2),
  requires_verification public.verification_level not null default 'unverified',
  is_youth_focused  boolean not null default false,
  created_by        uuid references public.profiles(id) on delete set null,
  deleted_at        timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

create table public.community_members (
  id            uuid primary key default gen_random_uuid(),
  community_id  uuid not null references public.communities(id) on delete cascade,
  user_id       uuid not null references public.profiles(id) on delete cascade,
  status        public.membership_status not null default 'pending',
  is_moderator  boolean not null default false,
  invited_by    uuid references public.profiles(id) on delete set null,
  joined_at     timestamptz,
  removed_at    timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  unique (community_id, user_id)
);

create table public.community_rules (
  id             uuid primary key default gen_random_uuid(),
  community_id   uuid not null references public.communities(id) on delete cascade,
  template_id    uuid references public.community_rule_templates(id) on delete set null,
  position       int not null default 0,
  title          text not null,
  body           text not null,
  is_active      boolean not null default true,
  created_by     uuid references public.profiles(id) on delete set null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (community_id, position)
);

select app.attach_updated_at('public.organizations');
select app.attach_updated_at('public.organization_members');
select app.attach_updated_at('public.organization_verifications');
select app.attach_updated_at('public.communities');
select app.attach_updated_at('public.community_members');
select app.attach_updated_at('public.community_rules');
