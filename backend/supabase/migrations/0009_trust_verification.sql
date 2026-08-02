-- =====================================================================
-- Fi Sabilillah -- 0009_trust_verification.sql
--
-- Trust is granted, never claimed. Nothing in this file may be
-- self-elevated: users may SUBMIT a verification request or a
-- qualification claim, but `status`, `verified_at` and `verified_by` are
-- writable only through security-definer functions gated on moderator or
-- scholar role (0014), and enforced by triggers.
-- =====================================================================

create table public.user_verifications (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references public.profiles(id) on delete cascade,
  level          public.verification_level not null,
  status         public.verification_status not null default 'pending',
  method         text not null default 'document_review'
                   check (method in ('email','phone','document_review','in_person',
                                     'organization_vouch','scholar_attestation','community_vouch')),
  -- Private storage path; never a public URL.
  evidence_path  text,
  evidence_note  text,
  submitted_at   timestamptz not null default now(),
  -- Reviewer fields: only app.decide_user_verification() may set these.
  reviewed_by    uuid references public.profiles(id) on delete set null,
  reviewed_at    timestamptz,
  verified_at    timestamptz,
  expires_at     timestamptz,
  revoked_at     timestamptz,
  revoked_by     uuid references public.profiles(id) on delete set null,
  decision_note  text,
  vouching_organization_id uuid references public.organizations(id) on delete set null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint user_verifications_verified_needs_reviewer
    check (status <> 'verified' or (reviewed_by is not null and verified_at is not null))
);

comment on constraint user_verifications_verified_needs_reviewer on public.user_verifications is
  'A row cannot claim verified status without naming a reviewer and a decision time.';

create unique index user_verifications_active_level_uk
  on public.user_verifications (user_id, level)
  where status in ('pending','under_review','verified');

create table public.qualifications (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references public.profiles(id) on delete cascade,
  qualification_type_id uuid not null references public.qualification_types(id) on delete restrict,
  title               text not null,
  issuing_body        text,
  issued_on           date,
  expires_on          date,
  reference_code      text,
  evidence_path       text,
  claim_note          text,
  -- Verification fields: never writable by the claimant.
  verified_at         timestamptz,
  verified_by         uuid references public.profiles(id) on delete set null,
  verification_note   text,
  revoked_at          timestamptz,
  is_public           boolean not null default true,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  constraint qualifications_verified_needs_verifier
    check (verified_at is null or verified_by is not null),
  constraint qualifications_date_order
    check (expires_on is null or issued_on is null or expires_on >= issued_on)
);

comment on column public.qualifications.verified_at is
  'Only app.verify_qualification() may set this. A direct UPDATE is rejected by a trigger.';

select app.attach_updated_at('public.user_verifications');
select app.attach_updated_at('public.qualifications');
