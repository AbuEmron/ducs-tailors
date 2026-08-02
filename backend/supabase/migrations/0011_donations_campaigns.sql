-- =====================================================================
-- Fi Sabilillah -- 0011_donations_campaigns.sql
--
-- NO PAYMENT PROCESSING LIVES HERE. `donations` records giving that was
-- settled elsewhere (bank transfer, in person, an external processor).
-- There are no card numbers, no tokens, no bank details in this schema.
--
-- ZAKAT ELIGIBILITY is a fiqh claim, not a marketing checkbox. It can be
-- set true only by app.set_campaign_zakat_eligible(), which requires:
--   * a verified owning organization, and
--   * a recorded campaign_verifications attestation of kind
--     'scholarly_zakat_attestation' or 'organization_zakat_attestation'.
-- A plain UPDATE is rejected by a trigger (0014), and a CHECK constraint
-- means the flag cannot even be inserted without an attestation row.
-- =====================================================================

create table public.campaigns (
  id                uuid primary key default gen_random_uuid(),
  organization_id   uuid not null references public.organizations(id) on delete cascade,
  project_id        uuid references public.projects(id) on delete set null,
  created_by        uuid not null references public.profiles(id) on delete cascade,
  slug              text not null unique,
  title             text not null,
  description       text not null,
  cause_note        text,
  status            public.campaign_status not null default 'draft',
  currency          char(3) not null default 'GBP',
  target_amount     numeric(14,2) check (target_amount is null or target_amount > 0),
  recorded_total    numeric(14,2) not null default 0 check (recorded_total >= 0),
  starts_on         date,
  ends_on           date,
  -- Fiqh flag. Guarded by CHECK + trigger + security-definer setter.
  zakat_eligible    boolean not null default false,
  zakat_attestation_id uuid,   -- FK added below (circular with campaign_verifications)
  -- Payments are intentionally disabled at the data layer.
  payments_enabled  boolean not null default false,
  external_giving_url text,
  is_public         boolean not null default false,
  deleted_at        timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  constraint campaigns_date_order check (ends_on is null or starts_on is null or ends_on >= starts_on),
  constraint campaigns_zakat_needs_attestation
    check (zakat_eligible = false or zakat_attestation_id is not null),
  constraint campaigns_payments_disabled check (payments_enabled = false)
);

comment on constraint campaigns_zakat_needs_attestation on public.campaigns is
  'zakat_eligible cannot be true without pointing at a recorded attestation row.';
comment on constraint campaigns_payments_disabled on public.campaigns is
  'This schema deliberately cannot represent an enabled payment flow.';

create table public.campaign_verifications (
  id              uuid primary key default gen_random_uuid(),
  campaign_id     uuid not null references public.campaigns(id) on delete cascade,
  kind            text not null
                    check (kind in ('organization_registration','financial_review',
                                    'scholarly_zakat_attestation','organization_zakat_attestation',
                                    'beneficiary_verification')),
  status          public.verification_status not null default 'pending',
  attested_by     uuid references public.profiles(id) on delete set null,
  attesting_organization_id uuid references public.organizations(id) on delete set null,
  attestation_note text,
  evidence_path   text,
  reviewed_at     timestamptz,
  expires_at      timestamptz,
  revoked_at      timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

alter table public.campaigns
  add constraint campaigns_zakat_attestation_fk
  foreign key (zakat_attestation_id) references public.campaign_verifications(id) on delete restrict;

alter table public.reports
  add constraint reports_campaign_fk
  foreign key (campaign_id) references public.campaigns(id) on delete set null;

alter table public.moderation_actions
  add constraint moderation_actions_campaign_fk
  foreign key (target_campaign_id) references public.campaigns(id) on delete set null;

create table public.donations (
  id                uuid primary key default gen_random_uuid(),
  campaign_id       uuid references public.campaigns(id) on delete set null,
  organization_id   uuid references public.organizations(id) on delete set null,
  donor_id          uuid references public.profiles(id) on delete set null,
  -- Sadaqah is best given quietly. Anonymous donations keep donor_id but
  -- hide it from the recipient organization via RLS + the public view.
  is_anonymous      boolean not null default true,
  status            public.donation_status not null default 'pledged',
  currency          char(3) not null default 'GBP',
  amount            numeric(14,2) not null check (amount > 0),
  intention_note    text,
  is_zakat          boolean not null default false,
  external_reference text,        -- opaque reference from the external processor
  settled_at        timestamptz,
  acknowledged_at   timestamptz,
  cancelled_at      timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  constraint donations_has_destination
    check (campaign_id is not null or organization_id is not null),
  constraint donations_zakat_needs_eligible_target check (is_zakat = false or campaign_id is not null)
);

comment on table public.donations is
  'A RECORD of giving settled outside this system. Contains no payment instrument data of any kind.';

select app.attach_updated_at('public.campaigns');
select app.attach_updated_at('public.campaign_verifications');
select app.attach_updated_at('public.donations');
