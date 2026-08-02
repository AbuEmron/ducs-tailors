-- =====================================================================
-- Fi Sabilillah -- 0005_skills_learning_volunteering.sql
-- What people can offer, what they can learn, and where they can serve.
-- =====================================================================

create table public.user_skills (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references public.profiles(id) on delete cascade,
  skill_id       uuid not null references public.skills(id) on delete cascade,
  proficiency    text not null default 'intermediate'
                   check (proficiency in ('beginner','intermediate','advanced','professional')),
  years_experience int check (years_experience between 0 and 80),
  is_offered     boolean not null default true,   -- willing to serve with it
  is_public      boolean not null default true,
  -- Verified by a third party (org / scholar). Never self-settable: see 0014.
  endorsed_by    uuid references public.profiles(id) on delete set null,
  endorsed_at    timestamptz,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (user_id, skill_id)
);

-- ---------------------------------------------------------------------
-- Learning: halaqas, tutoring, Quran reading support, new-Muslim mentoring.
-- ---------------------------------------------------------------------
create table public.learning_offerings (
  id                 uuid primary key default gen_random_uuid(),
  teacher_id         uuid not null references public.profiles(id) on delete cascade,
  organization_id    uuid references public.organizations(id) on delete set null,
  community_id       uuid references public.communities(id) on delete set null,
  subject_id         uuid not null references public.learning_subjects(id) on delete restrict,
  title              text not null check (length(btrim(title)) between 3 and 160),
  description        text not null,
  format             public.offering_format not null default 'online',
  gender_policy      public.gender_policy not null default 'any',
  is_youth_offering  boolean not null default false,
  requires_guardian_consent boolean not null default false,
  capacity           int check (capacity is null or capacity > 0),
  starts_at          timestamptz,
  ends_at            timestamptz,
  recurrence_note    text,
  meeting_location   text,          -- approximate / venue name only
  is_free            boolean not null default true,
  suggested_contribution_note text,
  is_published       boolean not null default false,
  deleted_at         timestamptz,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint learning_offerings_time_order check (ends_at is null or starts_at is null or ends_at >= starts_at),
  constraint learning_offerings_youth_guardian
    check (not is_youth_offering or requires_guardian_consent)
);

comment on constraint learning_offerings_youth_guardian on public.learning_offerings is
  'Youth-facing teaching always requires recorded guardian consent. Non-negotiable at the schema level.';

create table public.learning_enrollments (
  id             uuid primary key default gen_random_uuid(),
  offering_id    uuid not null references public.learning_offerings(id) on delete cascade,
  student_id     uuid not null references public.profiles(id) on delete cascade,
  status         public.enrollment_status not null default 'requested',
  motivation     text,
  guardian_consent_record_id uuid,   -- FK added in 0012 once consent_records exists
  attended_sessions int not null default 0 check (attended_sessions >= 0),
  completed_at   timestamptz,
  withdrawn_at   timestamptz,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (offering_id, student_id)
);

-- ---------------------------------------------------------------------
-- Volunteering
-- ---------------------------------------------------------------------
create table public.volunteer_opportunities (
  id                 uuid primary key default gen_random_uuid(),
  organization_id    uuid references public.organizations(id) on delete cascade,
  community_id       uuid references public.communities(id) on delete set null,
  created_by         uuid not null references public.profiles(id) on delete cascade,
  category_id        uuid references public.service_categories(id) on delete set null,
  title              text not null,
  description        text not null,
  gender_policy      public.gender_policy not null default 'any',
  is_youth_facing    boolean not null default false,
  requires_safeguarding_clearance boolean not null default false,
  min_verification   public.verification_level not null default 'basic',
  volunteers_needed  int not null default 1 check (volunteers_needed > 0),
  starts_at          timestamptz,
  ends_at            timestamptz,
  approximate_location text,     -- e.g. "North side, near the community centre"
  exact_location_note  text,     -- disclosed to accepted volunteers only (see RLS)
  is_published       boolean not null default false,
  closed_at          timestamptz,
  deleted_at         timestamptz,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint volunteer_opportunities_youth_clearance
    check (not is_youth_facing or requires_safeguarding_clearance)
);

comment on constraint volunteer_opportunities_youth_clearance on public.volunteer_opportunities is
  'Youth-facing volunteering cannot be posted without requiring safeguarding clearance.';

create table public.volunteer_applications (
  id               uuid primary key default gen_random_uuid(),
  opportunity_id   uuid not null references public.volunteer_opportunities(id) on delete cascade,
  volunteer_id     uuid not null references public.profiles(id) on delete cascade,
  status           public.application_status not null default 'submitted',
  message          text,
  availability_note text,
  reviewed_by      uuid references public.profiles(id) on delete set null,
  reviewed_at      timestamptz,
  decided_at       timestamptz,
  withdrawal_reason text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  unique (opportunity_id, volunteer_id)
);

select app.attach_updated_at('public.user_skills');
select app.attach_updated_at('public.learning_offerings');
select app.attach_updated_at('public.learning_enrollments');
select app.attach_updated_at('public.volunteer_opportunities');
select app.attach_updated_at('public.volunteer_applications');
