-- =====================================================================
-- Fi Sabilillah -- 0008_wali_formal_introduction.sql
--
-- The marriage-introduction workflow. This is NOT a dating feature:
--   * no browsing of individuals for romantic interest without settings
--     explicitly opened,
--   * no private one-to-one channel before both walis approve,
--   * wali contact details are never a readable column for a counterparty
--     until the introduction is APPROVED AND FORWARDED, and even then only
--     through app.get_wali_contact(), which records the disclosure.
--
-- wali_profiles.contact_email / contact_phone are protected by:
--   1. RLS: only the owning user (the ward) may SELECT the row at all.
--   2. A redacted view (public.wali_profiles_redacted) for everyone else.
--   3. app.get_wali_contact(introduction_id) -- SECURITY DEFINER, checks
--      status = approved_and_forwarded / in_correspondence AND an
--      authorized formal_introduction_participants row for the caller,
--      then writes a wali_contact_disclosures audit row.
-- =====================================================================

-- ---------------------------------------------------------------------
-- trusted_contacts: the people a member has designated (wali, mahram,
-- mentor, emergency contact). Strictly private to the owning user.
-- ---------------------------------------------------------------------
create table public.trusted_contacts (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references public.profiles(id) on delete cascade,
  contact_user_id  uuid references public.profiles(id) on delete set null,
  relationship     public.trust_relationship not null,
  display_name     text not null,
  contact_email    citext,
  contact_phone    text,
  is_primary       boolean not null default false,
  can_be_notified  boolean not null default true,
  notes            text,
  confirmed_at     timestamptz,
  revoked_at       timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint trusted_contacts_not_self check (contact_user_id is null or contact_user_id <> user_id)
);

create unique index trusted_contacts_primary_uk
  on public.trusted_contacts (user_id, relationship)
  where is_primary and revoked_at is null;

comment on table public.trusted_contacts is
  'PRIVATE to the owning user. Not visible to moderators except via a documented legal-hold export.';

-- ---------------------------------------------------------------------
-- wali_profiles: the guardian who mediates on behalf of a member.
-- ---------------------------------------------------------------------
create table public.wali_profiles (
  id                 uuid primary key default gen_random_uuid(),
  -- The WARD: the member whose introductions this wali supervises.
  user_id            uuid not null references public.profiles(id) on delete cascade,
  -- The wali as a platform account, when they have one.
  wali_user_id       uuid references public.profiles(id) on delete set null,
  wali_display_name  text not null,
  relationship       text not null default 'father'
                       check (relationship in ('father','grandfather','brother','uncle','son',
                                               'appointed_guardian','imam_as_wali','other')),
  -- PROTECTED COLUMNS. See app.get_wali_contact().
  contact_email      citext,
  contact_phone      text,
  preferred_contact_method text not null default 'email'
                       check (preferred_contact_method in ('email','phone','in_person','via_masjid')),
  masjid_organization_id uuid references public.organizations(id) on delete set null,
  -- Wali identity confirmation is a moderator/scholar action, never self-set.
  confirmed_at       timestamptz,
  confirmed_by       uuid references public.profiles(id) on delete set null,
  confirmation_method text,
  is_active          boolean not null default true,
  revoked_at         timestamptz,
  deleted_at         timestamptz,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint wali_profiles_wali_not_ward check (wali_user_id is null or wali_user_id <> user_id),
  constraint wali_profiles_has_a_channel
    check (contact_email is not null or contact_phone is not null or wali_user_id is not null)
);

create unique index wali_profiles_active_uk
  on public.wali_profiles (user_id) where is_active and deleted_at is null;

comment on column public.wali_profiles.contact_email is
  'PROTECTED. Never exposed by any view. Disclosed only via app.get_wali_contact().';
comment on column public.wali_profiles.contact_phone is
  'PROTECTED. Never exposed by any view. Disclosed only via app.get_wali_contact().';

-- Redacted projection used for every general read. Contains no contact
-- data at all -- only enough to show "this member has a confirmed wali".
create view public.wali_profiles_redacted
with (security_invoker = true) as
select
  wp.id,
  wp.user_id,
  wp.wali_display_name,
  wp.relationship,
  wp.masjid_organization_id,
  (wp.confirmed_at is not null)                     as is_confirmed,
  -- NOTE: deliberately no projection of contact_email / contact_phone, not
  -- even as a boolean. The SELECT privilege on those columns is revoked in
  -- 0013, so referencing them here would break this view for every caller.
  wp.preferred_contact_method,
  wp.is_active,
  wp.created_at
from public.wali_profiles wp
where wp.deleted_at is null;

comment on view public.wali_profiles_redacted is
  'Contact-free projection of wali_profiles. security_invoker so the caller''s RLS still applies.';

-- ---------------------------------------------------------------------
-- formal_introduction_settings: whether a member participates at all.
-- Default is CLOSED. Nobody is browsable by default.
-- ---------------------------------------------------------------------
create table public.formal_introduction_settings (
  id                       uuid primary key default gen_random_uuid(),
  user_id                  uuid not null unique references public.profiles(id) on delete cascade,
  is_open_to_introductions boolean not null default false,
  wali_profile_id          uuid references public.wali_profiles(id) on delete set null,
  require_wali_approval    boolean not null default true,
  require_chaperone        boolean not null default true,
  min_counterparty_verification public.verification_level not null default 'community_vouched',
  visible_to               text not null default 'wali_referral_only'
                             check (visible_to in ('nobody','wali_referral_only',
                                                   'verified_members','community_members')),
  intention_statement      text,
  criteria                 jsonb not null default '{}'::jsonb,
  max_open_introductions   int not null default 1 check (max_open_introductions between 0 and 5),
  paused_until             timestamptz,
  created_at               timestamptz not null default now(),
  updated_at               timestamptz not null default now(),
  constraint fis_open_requires_wali
    check (not is_open_to_introductions or wali_profile_id is not null)
);

comment on constraint fis_open_requires_wali on public.formal_introduction_settings is
  'A member cannot be open to introductions without a recorded wali.';

-- ---------------------------------------------------------------------
-- formal_introduction_requests: one per (initiator, recipient) attempt.
-- ---------------------------------------------------------------------
create table public.formal_introduction_requests (
  id                    uuid primary key default gen_random_uuid(),
  initiator_id          uuid not null references public.profiles(id) on delete cascade,
  recipient_id          uuid not null references public.profiles(id) on delete cascade,
  initiator_wali_profile_id uuid references public.wali_profiles(id) on delete set null,
  recipient_wali_profile_id uuid references public.wali_profiles(id) on delete set null,
  status                public.introduction_status not null default 'draft',
  intention_statement   text not null check (length(btrim(intention_statement)) >= 20),
  referred_by_organization_id uuid references public.organizations(id) on delete set null,
  submitted_at          timestamptz,
  initiator_wali_decided_at timestamptz,
  recipient_wali_decided_at timestamptz,
  -- The single gate that unlocks contact disclosure.
  approved_and_forwarded_at timestamptz,
  correspondence_started_at timestamptz,
  concluded_at          timestamptz,
  conclusion_note       text,
  cancelled_by          uuid references public.profiles(id) on delete set null,
  moderator_case_id     uuid,   -- FK added in 0010
  deleted_at            timestamptz,
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now(),
  constraint fir_distinct_parties check (initiator_id <> recipient_id),
  constraint fir_forward_requires_status check (
    approved_and_forwarded_at is null
    or status in ('approved_and_forwarded','in_correspondence',
                  'concluded_positively','concluded_declined','withdrawn','cancelled_by_moderator')
  )
);

create unique index fir_open_pair_uk
  on public.formal_introduction_requests (initiator_id, recipient_id)
  where status not in ('concluded_positively','concluded_declined',
                       'withdrawn','cancelled_by_moderator','counterpart_declined','wali_declined');

-- Now that formal_introduction_requests exists, close the forward reference
-- from conversation_purposes.
alter table public.conversation_purposes
  add constraint conversation_purposes_introduction_fk
  foreign key (introduction_id) references public.formal_introduction_requests(id) on delete set null;

-- ---------------------------------------------------------------------
-- formal_introduction_participants: exactly who is inside an introduction
-- and, critically, who is AUTHORIZED to receive the other side's wali
-- contact details.
-- ---------------------------------------------------------------------
create table public.formal_introduction_participants (
  id               uuid primary key default gen_random_uuid(),
  introduction_id  uuid not null references public.formal_introduction_requests(id) on delete cascade,
  user_id          uuid not null references public.profiles(id) on delete cascade,
  participant_role text not null
                     check (participant_role in ('initiator','recipient','initiator_wali',
                                                 'recipient_wali','chaperone','moderator')),
  -- Which side's wali contact this participant may be shown.
  authorized_for_wali_profile_id uuid references public.wali_profiles(id) on delete cascade,
  -- Set ONLY by app.forward_introduction(). A plain UPDATE is blocked by a
  -- trigger in 0014.
  contact_authorized_at timestamptz,
  authorized_by    uuid references public.profiles(id) on delete set null,
  revoked_at       timestamptz,
  revocation_reason text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  unique (introduction_id, user_id, participant_role)
);

comment on column public.formal_introduction_participants.contact_authorized_at is
  'Non-null ONLY after both walis approved and the introduction was forwarded.';

-- ---------------------------------------------------------------------
-- wali_contact_disclosures: an append-only ledger of every time contact
-- details were revealed, to whom, and under which introduction.
-- ---------------------------------------------------------------------
create table public.wali_contact_disclosures (
  id               uuid primary key default gen_random_uuid(),
  introduction_id  uuid not null references public.formal_introduction_requests(id) on delete cascade,
  wali_profile_id  uuid not null references public.wali_profiles(id) on delete cascade,
  disclosed_to     uuid not null references public.profiles(id) on delete cascade,
  disclosed_fields text[] not null default '{}',
  disclosed_at     timestamptz not null default now(),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.wali_contact_disclosures is
  'Append-only. The ward can always see who was given their wali''s details.';

select app.attach_updated_at('public.trusted_contacts');
select app.attach_updated_at('public.wali_profiles');
select app.attach_updated_at('public.formal_introduction_settings');
select app.attach_updated_at('public.formal_introduction_requests');
select app.attach_updated_at('public.formal_introduction_participants');
select app.attach_updated_at('public.wali_contact_disclosures');
