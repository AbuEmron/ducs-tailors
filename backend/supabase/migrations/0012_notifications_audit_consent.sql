-- =====================================================================
-- Fi Sabilillah -- 0012_notifications_audit_consent.sql
-- Notifications, the append-only audit log, private impact records, and
-- consent receipts.
-- =====================================================================

create table public.notifications (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles(id) on delete cascade,
  channel       public.notification_channel not null default 'in_app',
  kind          text not null,
  title         text not null,
  body          text,
  -- Loose reference so notifications never leak a joinable secret.
  entity_type   text,
  entity_id     uuid,
  action_path   text,
  is_urgent     boolean not null default false,
  read_at       timestamptz,
  dismissed_at  timestamptz,
  sent_at       timestamptz,
  expires_at    timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.notifications is
  'Strictly per-user. Bodies must not embed protected data (wali contact, exact address).';

-- ---------------------------------------------------------------------
-- private_impact_records: a member's own record of what they did, kept
-- deliberately private. Nobody else can read it -- not other members, not
-- organizations, not moderators. Riyaa (showing off) is a spiritual
-- hazard; the schema refuses to build a leaderboard.
-- ---------------------------------------------------------------------
create table public.private_impact_records (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references public.profiles(id) on delete cascade,
  occurred_on    date not null default current_date,
  category       text not null default 'service'
                   check (category in ('service','teaching','learning','giving','dua','other')),
  summary        text not null,
  reflection     text,
  hours          numeric(6,2) check (hours is null or hours >= 0),
  commitment_id  uuid references public.commitments(id) on delete set null,
  is_visible_to_self_only boolean not null default true,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint private_impact_records_always_private check (is_visible_to_self_only = true)
);

comment on constraint private_impact_records_always_private on public.private_impact_records is
  'There is intentionally no way to publish this table. No leaderboards, ever.';

-- ---------------------------------------------------------------------
-- audit_logs: APPEND-ONLY. There is an INSERT policy and a narrow SELECT
-- policy. There is deliberately NO update or delete policy, and UPDATE /
-- DELETE privileges are revoked from every application role in 0013.
-- ---------------------------------------------------------------------
create table public.audit_logs (
  id           uuid primary key default gen_random_uuid(),
  actor_id     uuid references public.profiles(id) on delete set null,
  subject_id   uuid references public.profiles(id) on delete set null,
  action       text not null,
  entity_type  text not null,
  entity_id    uuid,
  metadata     jsonb not null default '{}'::jsonb,
  occurred_at  timestamptz not null default now(),
  request_id   text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.audit_logs is
  'Append-only. A moderator can act, but cannot erase the fact that they acted.';

-- audit_logs is append-only, so it must NOT carry the updated_at trigger.

-- ---------------------------------------------------------------------
-- consent_records: a receipt for every consent given or withdrawn.
-- ---------------------------------------------------------------------
create table public.consent_records (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references public.profiles(id) on delete cascade,
  consent_type   public.consent_type not null,
  document_version text not null,
  granted        boolean not null default true,
  granted_at     timestamptz not null default now(),
  withdrawn_at   timestamptz,
  -- For minors: the guardian who consented on their behalf.
  granted_by_guardian uuid references public.profiles(id) on delete set null,
  guardian_relationship text,
  scope_note     text,
  evidence_hash  text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create unique index consent_records_live_uk
  on public.consent_records (user_id, consent_type, document_version)
  where withdrawn_at is null;

-- Close the forward reference from learning_enrollments.
alter table public.learning_enrollments
  add constraint learning_enrollments_guardian_consent_fk
  foreign key (guardian_consent_record_id) references public.consent_records(id) on delete set null;

select app.attach_updated_at('public.notifications');
select app.attach_updated_at('public.private_impact_records');
select app.attach_updated_at('public.consent_records');
