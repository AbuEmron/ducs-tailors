-- =====================================================================
-- Fi Sabilillah -- 0019_stripe_payments.sql
--
-- Real money.
--
-- Everything before this migration treated a donation as a RECORD of
-- giving that happened somewhere else. 0011 says so in a comment on the
-- table. That assumption is now false, and it was load-bearing: the
-- policies written under it let a member insert a donation naming
-- themselves for any amount, and update their own row afterwards. That
-- was harmless for a note saying "I gave £20 at the masjid". It is not
-- harmless when a row can move a campaign total, appear on a receipt,
-- and be reconciled against a bank account.
--
-- So the first thing this migration does is take those grants away.
--
-- WHO WRITES A DONATION
-- Nobody, from a client session. Not even the donor, and not even for
-- their own donation. Rows are created and advanced only by the two edge
-- functions in supabase/functions/, which hold the service-role key and
-- run where the donor cannot reach them:
--
--   donation-checkout  creates the row as 'awaiting_payment' and asks
--                      Stripe for a hosted checkout page.
--   stripe-webhook     advances it, and only on a signed event from
--                      Stripe that it has verified.
--
-- The consequence is the point: a donation becomes settled because a
-- payment processor said so, cryptographically, to a server. There is no
-- code path -- none -- by which a device can assert that money arrived.
--
-- WHAT IS STORED ABOUT THE PAYMENT
-- Two opaque Stripe identifiers, the fee, and the net. No card number,
-- no last four digits, no expiry, no cardholder name, no billing
-- address. The donor's card details are typed on Stripe's own page and
-- never touch this application, which is the whole reason for choosing
-- hosted checkout over an in-app card form.
--
-- WHICH CAMPAIGNS MAY COLLECT
-- 0011 carried `constraint campaigns_payments_disabled check
-- (payments_enabled = false)` -- a blunt instrument that was right while
-- nothing could take money. It is replaced here by a rule with an actual
-- opinion: payments may be enabled only on a verified campaign belonging
-- to an organisation whose registration has been checked, and only by a
-- platform administrator. An organisation administrator can edit their
-- campaign's words. They cannot decide it may take money.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- 1. States a donation can now be in
--
-- 'pledged' and 'recorded' are kept because rows created under the old
-- meaning still exist and must not be rewritten into something they were
-- not. Nothing new is ever created in those states.
-- ---------------------------------------------------------------------
alter type public.donation_status add value if not exists 'awaiting_payment';
alter type public.donation_status add value if not exists 'settled';
alter type public.donation_status add value if not exists 'failed';
alter type public.donation_status add value if not exists 'refunded';
alter type public.donation_status add value if not exists 'disputed';

commit;

-- A new enum value cannot be used in the same transaction that adds it.
begin;

-- ---------------------------------------------------------------------
-- 2. What a payment leaves behind
-- ---------------------------------------------------------------------
alter table public.donations
  add column if not exists provider            text,
  add column if not exists provider_session_id text,
  add column if not exists provider_payment_id text,
  add column if not exists fee_amount          numeric(14,2),
  add column if not exists net_amount          numeric(14,2),
  add column if not exists receipt_url         text,
  add column if not exists failure_reason      text;

comment on column public.donations.provider_session_id is
  'Stripe Checkout Session id. Opaque. Not a payment instrument.';
comment on column public.donations.net_amount is
  'What reached the campaign after the processor fee. Null until settled -- never estimated.';

-- One Stripe session settles one donation. Without this a webhook replayed
-- against a duplicated row would credit the same money twice.
create unique index if not exists donations_provider_session_uniq
  on public.donations (provider_session_id)
  where provider_session_id is not null;

alter table public.donations
  drop constraint if exists donations_net_not_more_than_gross;
alter table public.donations
  add constraint donations_net_not_more_than_gross
  check (net_amount is null or net_amount <= amount);

alter table public.donations
  drop constraint if exists donations_settled_has_reference;
alter table public.donations
  add constraint donations_settled_has_reference
  check (status <> 'settled' or provider_payment_id is not null or external_reference is not null);

comment on table public.donations is
  'A donation. Rows created before 0019 are records of giving settled elsewhere; rows '
  'created since are payments taken through a processor. Contains no payment instrument '
  'data of any kind, in either case.';

-- ---------------------------------------------------------------------
-- 3. The event ledger
--
-- Stripe delivers at least once, retries on any non-2xx, and can deliver
-- out of order. Every one of those is normal traffic rather than an
-- error, so the webhook must be safe to run twice on the same event. The
-- primary key is Stripe's own event id and the insert is the lock: if it
-- conflicts, this event has already been handled and the handler stops.
-- ---------------------------------------------------------------------
create table if not exists public.payment_events (
  id            text primary key,          -- Stripe's evt_... id
  type          text not null,
  donation_id   uuid references public.donations(id) on delete set null,
  received_at   timestamptz not null default now(),
  processed_at  timestamptz,
  outcome       text,
  -- Deliberately not the raw event body. A Stripe event carries the
  -- donor's email, billing name and address; storing every one of them
  -- forever would build a second copy of exactly the personal data this
  -- platform is careful about, in a table nobody thinks of as personal.
  summary       text
);

comment on table public.payment_events is
  'Idempotency ledger for processor webhooks. Service role only; no client may read it.';

alter table public.payment_events enable row level security;
alter table public.payment_events force row level security;

-- RLS with no policy already denies everyone, so this policy grants
-- nothing that was not already denied. It is here to say so out loud:
-- the repository's own schema assertion requires every table to carry an
-- explicit policy, precisely so that "no policy" can never be confused
-- with "nobody got round to writing one". The service role bypasses RLS,
-- and it is the only thing that should ever read this table.
drop policy if exists payment_events_nobody on public.payment_events;
create policy payment_events_nobody on public.payment_events
  for all to authenticated, anon
  using (false)
  with check (false);

revoke all on public.payment_events from authenticated, anon;

-- ---------------------------------------------------------------------
-- 4. Take the client's write access to donations away
--
-- This is the security change in this migration. Both policies below
-- were correct for a self-reported record of offline giving and are
-- dangerous now.
--
--   donations_insert_self  let a member create a donation for any amount
--                          in status 'recorded'.
--   donations_update       let a donor update their own donation --
--                          including its amount and its status.
--
-- Together they meant a member could have claimed to have given £10,000
-- and moved a campaign's total by saying so.
-- ---------------------------------------------------------------------
drop policy if exists donations_insert_self on public.donations;
drop policy if exists donations_update on public.donations;
revoke insert, update, delete on public.donations from authenticated, anon;

-- Selection is unchanged and still applies: a donor sees their own,
-- moderators see all, and a receiving organisation sees only the
-- non-anonymous ones. Quiet sadaqah stays quiet.

-- ---------------------------------------------------------------------
-- 5. Which campaigns may collect
-- ---------------------------------------------------------------------
alter table public.campaigns drop constraint if exists campaigns_payments_disabled;

create or replace function app.organization_registration_current(p_org uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  -- `organizations.verification_level` is a maintained summary column and is not enough
  -- on its own: it says what was once granted, not whether it still holds. Expiry and
  -- revocation live on the verification rows, and money is exactly the decision that has
  -- to read them rather than the summary.
  select exists (
    select 1
      from public.organization_verifications v
     where v.organization_id = p_org
       and v.status = 'verified'
       and v.revoked_at is null
       and (v.expires_at is null or v.expires_at > now())
  );
$$;

comment on function app.organization_registration_current(uuid) is
  'Whether this organisation holds a verification that has neither expired nor been revoked.';

create or replace function app.campaign_may_collect(p_campaign uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
      from public.campaigns c
      join public.organizations o on o.id = c.organization_id
     where c.id = p_campaign
       and c.status = 'active'
       and c.payments_enabled
       and c.deleted_at is null
       and app.organization_registration_current(o.id)
       -- The campaign's own financial review, distinct from the check on
       -- the organisation above. Both are required. A trustworthy charity
       -- can still run an appeal whose money has nowhere ring-fenced to
       -- land, and that is the failure this half catches.
       and exists (
         select 1 from public.campaign_verifications v
          where v.campaign_id = c.id
            and v.kind = 'financial_review'
            and v.status = 'verified'
            and v.revoked_at is null
            and (v.expires_at is null or v.expires_at > now())
       )
  );
$$;

comment on function app.campaign_may_collect(uuid) is
  'Every condition for taking money, evaluated at the moment of asking. Verification '
  'expires; a campaign approved in March by an organisation whose registration lapsed in '
  'June must stop collecting in June without anyone remembering to switch it off.';

-- Only a platform administrator flips the switch, and only onto a
-- campaign that has actually been verified.
create or replace function app.guard_campaign_payments()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    if new.payments_enabled and not app.is_platform_admin() then
      raise exception 'a campaign cannot be created with payments already enabled'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;

  if new.payments_enabled is distinct from old.payments_enabled then
    if not app.is_platform_admin() then
      raise exception 'only a platform administrator decides whether a campaign may collect'
        using errcode = 'insufficient_privilege';
    end if;

    if new.payments_enabled then
      if not exists (
        select 1 from public.campaign_verifications v
         where v.campaign_id = new.id
           and v.kind = 'financial_review'
           and v.status = 'verified'
           and v.revoked_at is null
      ) then
        raise exception 'this campaign has had no verified financial review'
          using errcode = 'check_violation';
      end if;

      if not app.organization_registration_current(new.organization_id) then
        raise exception 'this campaign''s organisation has not completed registration checks'
          using errcode = 'check_violation';
      end if;
    end if;
  end if;

  return new;
end;
$$;

drop trigger if exists trg_campaigns_guard_payments on public.campaigns;
create trigger trg_campaigns_guard_payments
  before insert or update on public.campaigns
  for each row execute function app.guard_campaign_payments();

-- PostgREST only exposes `public`, so an edge function cannot call
-- app.campaign_may_collect directly -- the RPC would 404 at runtime and
-- the failure would look like "giving is unavailable" rather than like a
-- missing function. This wrapper is the callable surface, and it is
-- granted to the service role alone: the answer to "may this campaign
-- collect" is not a thing a client needs to ask, and a client that could
-- ask it could enumerate which campaigns are about to go live.
create or replace function public.campaign_may_collect(p_campaign uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select app.campaign_may_collect(p_campaign);
$$;

revoke all on function public.campaign_may_collect(uuid) from public, anon, authenticated;
grant execute on function public.campaign_may_collect(uuid) to service_role;

-- ---------------------------------------------------------------------
-- 6. A campaign's total counts settled money only
--
-- Recomputed from the rows rather than incremented, because an increment
-- is wrong the first time a refund happens and stays wrong forever.
-- ---------------------------------------------------------------------
create or replace function app.recount_campaign_total()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_campaign uuid := coalesce(new.campaign_id, old.campaign_id);
begin
  if v_campaign is null then
    return coalesce(new, old);
  end if;

  update public.campaigns
     set recorded_total = coalesce((
           select sum(d.amount)
             from public.donations d
            where d.campaign_id = v_campaign
              and d.status in ('settled', 'recorded', 'acknowledged')
         ), 0)
   where id = v_campaign;

  return coalesce(new, old);
end;
$$;

drop trigger if exists trg_donations_recount on public.donations;
create trigger trg_donations_recount
  after insert or update or delete on public.donations
  for each row execute function app.recount_campaign_total();

-- ---------------------------------------------------------------------
-- 7. What a donor is allowed to read about their own payment
--
-- A view rather than wider column access, so that adding a column to
-- `donations` later cannot silently widen what a client can see.
-- ---------------------------------------------------------------------
create or replace view public.my_donations
with (security_invoker = true)
as
  select d.id,
         d.campaign_id,
         c.title            as campaign_title,
         d.amount,
         d.net_amount,
         d.currency,
         d.status,
         d.is_anonymous,
         d.is_zakat,
         d.receipt_url,
         d.failure_reason,
         d.settled_at,
         d.created_at
    from public.donations d
    left join public.campaigns c on c.id = d.campaign_id
   where d.donor_id = app.current_user_id();

grant select on public.my_donations to authenticated;

comment on view public.my_donations is
  'A donor''s own giving. security_invoker, so the donations policies still apply and this '
  'view cannot become a way around them.';

commit;
