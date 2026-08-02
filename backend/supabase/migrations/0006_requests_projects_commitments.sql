-- =====================================================================
-- Fi Sabilillah -- 0006_requests_projects_commitments.sql
-- Mutual aid requests, community projects, and the commitments people
-- make to each other.
--
-- LOCATION SPLIT
-- service_requests holds only APPROXIMATE location. The exact address,
-- latitude/longitude and access notes live in a 1:1 child table,
-- service_request_private_details, which is readable only by the
-- requester, the ACCEPTED helper, and moderators (see 0013). This is a
-- column-level split rather than a column privilege because a helper's
-- access must be able to appear and disappear per row.
-- =====================================================================

create table public.service_requests (
  id                  uuid primary key default gen_random_uuid(),
  requester_id        uuid not null references public.profiles(id) on delete cascade,
  category_id         uuid references public.service_categories(id) on delete set null,
  community_id        uuid references public.communities(id) on delete set null,
  organization_id     uuid references public.organizations(id) on delete set null,
  title               text not null check (length(btrim(title)) between 3 and 160),
  description         text not null,
  status              public.request_status not null default 'open',
  urgency             text not null default 'normal'
                        check (urgency in ('low','normal','high','urgent')),
  gender_policy       public.gender_policy not null default 'any',
  -- Coarse, publishable location only.
  approximate_area    text,
  approximate_latitude   numeric(8,4),
  approximate_longitude  numeric(8,4),
  min_helper_verification public.verification_level not null default 'basic',
  needed_by           timestamptz,
  is_anonymous        boolean not null default false,
  assigned_helper_id  uuid references public.profiles(id) on delete set null,
  helper_accepted_at  timestamptz,
  fulfilled_at        timestamptz,
  closed_at           timestamptz,
  deleted_at          timestamptz,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  constraint service_requests_helper_accept_consistency
    check ((assigned_helper_id is null) = (helper_accepted_at is null)),
  constraint service_requests_no_self_help
    check (assigned_helper_id is null or assigned_helper_id <> requester_id)
);

comment on column public.service_requests.approximate_area is
  'Coarse area shown to everyone who can see the request. Never the doorstep.';

create table public.service_request_private_details (
  id                uuid primary key default gen_random_uuid(),
  request_id        uuid not null unique references public.service_requests(id) on delete cascade,
  exact_address     text not null,
  latitude          numeric(9,6),
  longitude         numeric(9,6),
  access_notes      text,       -- "gate code", "ask for the caretaker"
  contact_phone     text,
  household_notes   text,       -- e.g. "elderly resident, please knock loudly"
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.service_request_private_details is
  'PROTECTED. Requester + accepted helper + moderators only. Never joined into any public view.';

create table public.request_responses (
  id            uuid primary key default gen_random_uuid(),
  request_id    uuid not null references public.service_requests(id) on delete cascade,
  responder_id  uuid not null references public.profiles(id) on delete cascade,
  message       text not null,
  status        public.application_status not null default 'submitted',
  can_start_at  timestamptz,
  accepted_at   timestamptz,
  declined_at   timestamptz,
  withdrawn_at  timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  unique (request_id, responder_id)
);

-- ---------------------------------------------------------------------
-- Projects
-- ---------------------------------------------------------------------
create table public.projects (
  id               uuid primary key default gen_random_uuid(),
  organization_id  uuid references public.organizations(id) on delete set null,
  community_id     uuid references public.communities(id) on delete set null,
  lead_id          uuid not null references public.profiles(id) on delete cascade,
  slug             text not null unique,
  name             text not null,
  summary          text not null,
  intention_note   text,      -- the niyyah / why this serves the community
  status           public.project_status not null default 'proposed',
  gender_policy    public.gender_policy not null default 'any',
  is_youth_involved boolean not null default false,
  requires_safeguarding_clearance boolean not null default false,
  starts_on        date,
  target_end_on    date,
  is_public        boolean not null default true,
  deleted_at       timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint projects_youth_clearance
    check (not is_youth_involved or requires_safeguarding_clearance),
  constraint projects_date_order
    check (target_end_on is null or starts_on is null or target_end_on >= starts_on)
);

create table public.project_members (
  id           uuid primary key default gen_random_uuid(),
  project_id   uuid not null references public.projects(id) on delete cascade,
  user_id      uuid not null references public.profiles(id) on delete cascade,
  project_role text not null default 'contributor'
                 check (project_role in ('contributor','coordinator','lead','advisor','observer')),
  status       public.membership_status not null default 'active',
  joined_at    timestamptz not null default now(),
  removed_at   timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  unique (project_id, user_id)
);

create table public.project_tasks (
  id            uuid primary key default gen_random_uuid(),
  project_id    uuid not null references public.projects(id) on delete cascade,
  created_by    uuid not null references public.profiles(id) on delete cascade,
  assignee_id   uuid references public.profiles(id) on delete set null,
  title         text not null,
  detail        text,
  status        public.task_status not null default 'todo',
  due_on        date,
  completed_at  timestamptz,
  position      int not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- Commitments: a promise of service. Confirmed by the beneficiary, not
-- self-declared, so that "impact" cannot be manufactured.
-- ---------------------------------------------------------------------
create table public.commitments (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references public.profiles(id) on delete cascade,
  status           public.commitment_status not null default 'pledged',
  summary          text not null,
  -- Exactly one subject: an opportunity, a request, a project, or an offering.
  opportunity_id   uuid references public.volunteer_opportunities(id) on delete cascade,
  service_request_id uuid references public.service_requests(id) on delete cascade,
  project_id       uuid references public.projects(id) on delete cascade,
  offering_id      uuid references public.learning_offerings(id) on delete cascade,
  hours_pledged    numeric(6,2) check (hours_pledged is null or hours_pledged >= 0),
  hours_delivered  numeric(6,2) check (hours_delivered is null or hours_delivered >= 0),
  due_at           timestamptz,
  fulfilled_at     timestamptz,
  released_at      timestamptz,
  release_reason   text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint commitments_exactly_one_subject check (
    (opportunity_id is not null)::int
  + (service_request_id is not null)::int
  + (project_id is not null)::int
  + (offering_id is not null)::int = 1
  )
);

create table public.commitment_confirmations (
  id             uuid primary key default gen_random_uuid(),
  commitment_id  uuid not null references public.commitments(id) on delete cascade,
  confirmed_by   uuid not null references public.profiles(id) on delete cascade,
  organization_id uuid references public.organizations(id) on delete set null,
  confirmed_at   timestamptz not null default now(),
  hours_confirmed numeric(6,2) check (hours_confirmed is null or hours_confirmed >= 0),
  note           text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (commitment_id, confirmed_by)
);

comment on table public.commitment_confirmations is
  'A commitment is only "fulfilled" when someone other than the volunteer says so.';

select app.attach_updated_at('public.service_requests');
select app.attach_updated_at('public.service_request_private_details');
select app.attach_updated_at('public.request_responses');
select app.attach_updated_at('public.projects');
select app.attach_updated_at('public.project_members');
select app.attach_updated_at('public.project_tasks');
select app.attach_updated_at('public.commitments');
select app.attach_updated_at('public.commitment_confirmations');
