-- =====================================================================
-- Fi Sabilillah -- 0010_moderation_reports_appeals.sql
--
-- Evidence-preserving moderation. Three tables here are APPEND-ONLY by
-- design and have no UPDATE or DELETE policy anywhere in 0013:
--   * report_evidence
--   * moderation_actions
-- (plus audit_logs in 0012). A malicious insider with a moderator role
-- can still act, but cannot quietly erase the record of having acted.
-- =====================================================================

create table public.reports (
  id                uuid primary key default gen_random_uuid(),
  reporter_id       uuid references public.profiles(id) on delete set null,
  is_anonymous      boolean not null default false,
  category          public.report_category not null,
  status            public.report_status not null default 'submitted',
  severity          public.incident_severity not null default 'moderate',
  summary           text not null check (length(btrim(summary)) between 5 and 4000),
  -- Subject of the report: at most one target object, plus an optional user.
  reported_user_id  uuid references public.profiles(id) on delete set null,
  message_id        uuid references public.messages(id) on delete set null,
  conversation_id   uuid references public.conversations(id) on delete set null,
  organization_id   uuid references public.organizations(id) on delete set null,
  community_id      uuid references public.communities(id) on delete set null,
  service_request_id uuid references public.service_requests(id) on delete set null,
  introduction_id   uuid references public.formal_introduction_requests(id) on delete set null,
  campaign_id       uuid,  -- FK added in 0011
  triaged_at        timestamptz,
  triaged_by        uuid references public.profiles(id) on delete set null,
  resolved_at       timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  constraint reports_not_self check (reported_user_id is null or reported_user_id <> reporter_id),
  constraint reports_has_a_subject check (
    reported_user_id is not null or message_id is not null or conversation_id is not null
    or organization_id is not null or community_id is not null
    or service_request_id is not null or introduction_id is not null or campaign_id is not null
  )
);

comment on table public.reports is
  'A reporter can always see their own report. The reported user cannot -- retaliation surface.';

create table public.report_evidence (
  id            uuid primary key default gen_random_uuid(),
  report_id     uuid not null references public.reports(id) on delete cascade,
  submitted_by  uuid references public.profiles(id) on delete set null,
  evidence_kind text not null default 'text'
                  check (evidence_kind in ('text','screenshot','message_ref','file','link','system_snapshot')),
  body          text,
  storage_path  text,
  message_id    uuid references public.messages(id) on delete set null,
  content_hash  text,
  captured_at   timestamptz not null default now(),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.report_evidence is
  'APPEND-ONLY. No UPDATE/DELETE policy and no UPDATE/DELETE grant. Evidence survives the accused AND the moderator.';

create table public.moderation_cases (
  id                uuid primary key default gen_random_uuid(),
  case_number       bigint generated always as identity unique,
  status            public.moderation_case_status not null default 'open',
  severity          public.incident_severity not null default 'moderate',
  subject_user_id   uuid references public.profiles(id) on delete set null,
  organization_id   uuid references public.organizations(id) on delete set null,
  opened_by         uuid references public.profiles(id) on delete set null,
  assigned_to       uuid references public.profiles(id) on delete set null,
  title             text not null,
  narrative         text,
  -- A second moderator must sign off before severe actions. Enforced in
  -- app.record_moderation_action() for suspensions.
  second_reviewer_id uuid references public.profiles(id) on delete set null,
  second_reviewed_at timestamptz,
  opened_at         timestamptz not null default now(),
  closed_at         timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  constraint moderation_cases_second_reviewer_distinct
    check (second_reviewer_id is null or second_reviewer_id <> assigned_to)
);

alter table public.reports
  add column case_id uuid references public.moderation_cases(id) on delete set null;

alter table public.formal_introduction_requests
  add constraint fir_moderator_case_fk
  foreign key (moderator_case_id) references public.moderation_cases(id) on delete set null;

create table public.moderation_actions (
  id              uuid primary key default gen_random_uuid(),
  case_id         uuid references public.moderation_cases(id) on delete set null,
  report_id       uuid references public.reports(id) on delete set null,
  actor_id        uuid not null references public.profiles(id) on delete restrict,
  action_type     public.moderation_action_type not null,
  target_user_id  uuid references public.profiles(id) on delete set null,
  target_message_id uuid references public.messages(id) on delete set null,
  target_conversation_id uuid references public.conversations(id) on delete set null,
  target_organization_id uuid references public.organizations(id) on delete set null,
  target_campaign_id uuid,   -- FK added in 0011
  target_introduction_id uuid references public.formal_introduction_requests(id) on delete set null,
  rationale       text not null check (length(btrim(rationale)) >= 10),
  policy_reference text,
  effective_at    timestamptz not null default now(),
  expires_at      timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

comment on table public.moderation_actions is
  'APPEND-ONLY. Every insert forces an audit_logs row via trigger (0014). No UPDATE/DELETE anywhere.';

create table public.appeals (
  id              uuid primary key default gen_random_uuid(),
  action_id       uuid references public.moderation_actions(id) on delete set null,
  case_id         uuid references public.moderation_cases(id) on delete set null,
  appellant_id    uuid not null references public.profiles(id) on delete cascade,
  status          public.appeal_status not null default 'submitted',
  grounds         text not null check (length(btrim(grounds)) >= 10),
  -- The reviewer must differ from the moderator who acted. Enforced in 0014.
  reviewer_id     uuid references public.profiles(id) on delete set null,
  reviewed_at     timestamptz,
  outcome_note    text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

create table public.blocks (
  id           uuid primary key default gen_random_uuid(),
  blocker_id   uuid not null references public.profiles(id) on delete cascade,
  blocked_id   uuid not null references public.profiles(id) on delete cascade,
  reason_note  text,
  lifted_at    timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint blocks_not_self check (blocker_id <> blocked_id)
);

create unique index blocks_active_uk
  on public.blocks (blocker_id, blocked_id) where lifted_at is null;

comment on table public.blocks is
  'A block is one-directional to create but symmetric in effect: see app.is_blocked_between().';

create table public.restrictions (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references public.profiles(id) on delete cascade,
  restriction_type public.restriction_type not null,
  case_id          uuid references public.moderation_cases(id) on delete set null,
  action_id        uuid references public.moderation_actions(id) on delete set null,
  imposed_by       uuid references public.profiles(id) on delete set null,
  reason           text not null,
  starts_at        timestamptz not null default now(),
  expires_at       timestamptz,
  lifted_at        timestamptz,
  lifted_by        uuid references public.profiles(id) on delete set null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint restrictions_window check (expires_at is null or expires_at > starts_at),
  constraint restrictions_not_self_imposed check (imposed_by is null or imposed_by <> user_id)
);

create table public.safety_incidents (
  id                uuid primary key default gen_random_uuid(),
  case_id           uuid references public.moderation_cases(id) on delete set null,
  reported_by       uuid references public.profiles(id) on delete set null,
  severity          public.incident_severity not null default 'high',
  category          public.report_category not null default 'safety_concern',
  occurred_at       timestamptz,
  location_note     text,
  organization_id   uuid references public.organizations(id) on delete set null,
  involved_user_id  uuid references public.profiles(id) on delete set null,
  narrative         text not null,
  -- Set when the matter was escalated outside the platform.
  referred_externally_at timestamptz,
  referred_to       text,
  safeguarding_lead_notified_at timestamptz,
  closed_at         timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.safety_incidents is
  'Highest-sensitivity table. Moderators + platform admins only; never visible to the subject.';

select app.attach_updated_at('public.reports');
select app.attach_updated_at('public.report_evidence');
select app.attach_updated_at('public.moderation_cases');
select app.attach_updated_at('public.moderation_actions');
select app.attach_updated_at('public.appeals');
select app.attach_updated_at('public.blocks');
select app.attach_updated_at('public.restrictions');
select app.attach_updated_at('public.safety_incidents');
