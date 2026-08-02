-- =====================================================================
-- Fi Sabilillah -- 0016_auth_registration.sql
--
-- The join between Supabase Auth and this schema.
--
-- Everything from 0001 to 0015 was written against `auth.uid()`, and until
-- now nothing produced one: the application picked a seeded account out of a
-- list. This migration supplies the missing half.
--
-- Three things happen here.
--
-- 1. The structural vocabulary a live deployment cannot start without --
--    the platform roles and the safeguard presets -- is inserted here rather
--    than in seed/seed.sql. Roles are not demonstration data: app.is_moderator()
--    is a lookup against public.roles, so a database without those rows has a
--    safety team that cannot be appointed. seed/seed.sql keeps its copy for
--    local runs and both are written to tolerate the other having gone first.
--
-- 2. A profile is created through public.register_member(), a security-definer
--    entry point, and NOT by trusting the client to insert its own row.
--
-- 3. Confirming the address on file records a verification the ordinary way --
--    a row in user_verifications, synced onto the profile by the trigger from
--    0014 -- rather than writing a trust level directly.
--
-- CLOSING AN ESCALATION PATH
-- 0014 guarded public.profiles.verification_level BEFORE UPDATE only. That was
-- survivable while no one could sign up: the sole INSERT path was the seed,
-- running as superuser. With real registration it becomes exploitable -- the
-- RLS policy profiles_insert_self permits a member to insert the row whose id
-- matches their own auth.uid(), and an INSERT was never inspected, so a member
-- could have arrived already holding 'scholar_verified'. The guard below
-- covers INSERT as well, and takes gender with it: contact structure, gendered
-- spaces and the whole introduction workflow key off gender, so it is settled
-- once at registration and thereafter only a moderator can correct it.
-- =====================================================================

-- ---------------------------------------------------------------------
-- SECTION 1 -- structural vocabulary
-- ---------------------------------------------------------------------
insert into public.roles (key, display_name, description, is_privileged) values
  ('member',           'Member',            'An ordinary member of the community.',                  false),
  ('organizer',        'Organizer',         'May coordinate projects and opportunities.',            false),
  ('scholar',          'Scholar',           'Listed teacher: may attest qualifications and zakat.',  true),
  ('moderator',        'Moderator',         'Safety team: handles reports and moderation cases.',    true),
  ('platform_admin',   'Platform Admin',    'Full administrative authority, including role grants.', true),
  ('safeguarding_lead','Safeguarding Lead', 'Handles youth and vulnerable-adult incidents.',         true)
on conflict (key) do nothing;

insert into public.safeguard_presets (slug, name, description, settings, is_default) values
  ('balanced', 'Balanced',
     'Purpose-bound contact, no unsolicited messages, media blocked from strangers.',
     '{"require_purpose_for_contact":true,"allow_unsolicited_contact":false}'::jsonb, true),
  ('strict',   'Strict',
     'Only verified members may reach you, and only through an organization or community.',
     '{"min_counterparty_verification":"community_vouched"}'::jsonb, false),
  ('new-to-platform', 'New to the platform',
     'Extra protection for the first weeks: no direct contact except through a coordinator.',
     '{"allow_unsolicited_contact":false,"block_media_from_strangers":true}'::jsonb, false),
  ('youth-supervised', 'Supervised (under 18)',
     'A guardian is notified of every new contact; introductions are disabled entirely.',
     '{"is_minor_supervised":true,"require_wali_for_introductions":true}'::jsonb, false)
on conflict (slug) do nothing;

-- ---------------------------------------------------------------------
-- SECTION 2 -- the profile guard, now covering INSERT
--
-- Replaces trg_profiles_guard_verification_level from 0014. Same reasoning
-- about SECURITY INVOKER: the trigger has to see the real current_user so
-- that app.is_privileged_caller() can tell a definer function apart from a
-- member typing SQL at PostgREST.
-- ---------------------------------------------------------------------
drop trigger if exists trg_profiles_guard_verification_level on public.profiles;
drop function if exists app.guard_profile_verification_level();

create or replace function app.guard_profile_identity()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    -- Trust is granted, never claimed -- including on the very first write.
    if new.verification_level is distinct from 'unverified'::public.verification_level
       and not app.is_privileged_caller() and not app.is_moderator() then
      raise exception 'a new profile always starts unverified; verification is derived from user_verifications'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;

  if new.verification_level is distinct from old.verification_level
     and not app.is_privileged_caller() and not app.is_moderator() then
    raise exception 'verification_level is derived from user_verifications and cannot be set directly'
      using errcode = 'insufficient_privilege';
  end if;

  if new.id is distinct from old.id then
    raise exception 'profile id is immutable' using errcode = 'insufficient_privilege';
  end if;

  -- Gender decides who may open a conversation, which spaces are reachable,
  -- and which side of an introduction a person stands on. A member who could
  -- flip it at will could walk into a sisters-only circle between requests.
  if new.gender is distinct from old.gender
     and not app.is_privileged_caller() and not app.is_moderator() then
    raise exception 'gender is set at registration; ask the safety team to correct it'
      using errcode = 'insufficient_privilege';
  end if;

  return new;
end;
$$;

comment on function app.guard_profile_identity() is
  'Rejects self-granted verification on INSERT or UPDATE, and freezes gender and id after registration.';

create trigger trg_profiles_guard_identity
  before insert or update on public.profiles
  for each row execute function app.guard_profile_identity();

-- ---------------------------------------------------------------------
-- SECTION 3 -- machine-confirmed verification methods
--
-- user_verifications_verified_needs_reviewer demanded a named human reviewer
-- for every verified row. Email and phone confirmation have no human in the
-- loop -- the mail or SMS provider is the evidence -- so the constraint is
-- widened for exactly those two methods and left untouched for the ones that
-- represent a judgement someone has to be accountable for.
-- ---------------------------------------------------------------------
alter table public.user_verifications
  drop constraint user_verifications_verified_needs_reviewer;

alter table public.user_verifications
  add constraint user_verifications_verified_needs_reviewer
  check (
    status <> 'verified'
    or (verified_at is not null
        and (reviewed_by is not null or method in ('email', 'phone')))
  );

comment on constraint user_verifications_verified_needs_reviewer on public.user_verifications is
  'A verified row names a reviewer, unless the method is a machine-confirmed channel (email, phone).';

-- ---------------------------------------------------------------------
-- SECTION 4 -- registration
-- ---------------------------------------------------------------------

-- Records the 'basic' level earned by confirming the address on file.
-- Idempotent: a second call, or a second confirmation, changes nothing.
create or replace function app.record_channel_verification(
  p_user   uuid,
  p_method text)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if p_method not in ('email', 'phone') then
    raise exception 'app.record_channel_verification only handles machine-confirmed channels'
      using errcode = 'invalid_parameter_value';
  end if;

  if not exists (select 1 from public.profiles p where p.id = p_user) then
    return;   -- registration has not happened yet; the caller will retry then
  end if;

  insert into public.user_verifications
         (user_id, level, status, method, verified_at, decision_note)
  values (p_user, 'basic', 'verified', p_method, now(),
          'Confirmed automatically by the ' || p_method || ' provider.')
  on conflict do nothing;
end;
$$;

comment on function app.record_channel_verification(uuid, text) is
  'Records the basic verification earned by confirming an email address or phone number. Never raises a level beyond basic.';

-- The one legitimate way a profile comes into existence.
--
-- Lives in public because PostgREST only exposes public; app.* is deliberately
-- unreachable from a client. Everything privileged is decided here rather than
-- read off the request: the role granted is always 'member', the verification
-- level is always whatever user_verifications can justify, and the profile id
-- is always auth.uid() -- never a value the caller supplied.
create or replace function public.register_member(
  p_display_name  text,
  p_gender        public.gender,
  p_contact_email text default null,
  p_locale        text default 'en',
  p_timezone      text default 'UTC',
  p_year_of_birth int default null,
  p_city          text default null,
  p_country_code  char(2) default null,
  p_accept_covenant boolean default false)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid       uuid := app.current_user_id();
  -- text, not citext: these functions run with an empty search_path, and the
  -- extension's schema differs between a plain cluster and Supabase. The
  -- column is citext and casts on the way in, which is where it matters.
  v_email     text;
  v_confirmed timestamptz;
  v_member    uuid;
  v_preset    uuid;
  v_name      text := btrim(coalesce(p_display_name, ''));
begin
  if v_uid is null then
    raise exception 'register_member requires a signed-in account'
      using errcode = 'insufficient_privilege';
  end if;

  if exists (select 1 from public.profiles p where p.id = v_uid) then
    raise exception 'this account already has a profile' using errcode = 'unique_violation';
  end if;

  -- The address is read from the auth record, not from the argument, so that a
  -- caller cannot register under an address they have not proved they hold.
  select u.email, u.email_confirmed_at into v_email, v_confirmed
    from auth.users u where u.id = v_uid;

  v_email := coalesce(v_email, p_contact_email);
  if v_email is null then
    raise exception 'no email address on this account' using errcode = 'not_null_violation';
  end if;

  if length(v_name) < 2 then
    raise exception 'a display name of at least two characters is required'
      using errcode = 'check_violation';
  end if;

  insert into public.profiles
         (id, display_name, contact_email, locale, timezone, gender,
          year_of_birth, city, country_code,
          verification_level, accepted_covenant_at)
  values (v_uid, v_name, v_email, coalesce(p_locale, 'en'), coalesce(p_timezone, 'UTC'),
          p_gender, p_year_of_birth, p_city, p_country_code,
          'unverified',
          case when p_accept_covenant then now() else null end);

  insert into public.user_settings (user_id) values (v_uid);

  -- Safeguards start at the balanced preset. A member may tighten them
  -- immediately; nothing here silently loosens anything.
  select sp.id into v_preset from public.safeguard_presets sp
   where sp.slug = 'balanced' and sp.is_active;

  insert into public.user_safeguards (user_id, preset_id, gender_interaction_policy)
  values (v_uid, v_preset,
          case when p_gender = 'female' then 'sisters_only'::public.gender_policy
               else 'any'::public.gender_policy end);

  -- The member role, and only the member role. app.grant_role() is refused
  -- here on purpose: it requires a platform admin and forbids self-grants,
  -- which is exactly right for every role except this one.
  select r.id into v_member from public.roles r where r.key = 'member';
  if v_member is null then
    raise exception 'the member role is missing from public.roles' using errcode = 'no_data_found';
  end if;
  insert into public.user_roles (user_id, role_id, reason)
  values (v_uid, v_member, 'Granted on registration.');

  if v_confirmed is not null then
    perform app.record_channel_verification(v_uid, 'email');
  end if;

  perform app.write_audit('account.registered', 'profiles', v_uid,
                          jsonb_build_object('email_confirmed', v_confirmed is not null),
                          v_uid, v_uid);

  return v_uid;
end;
$$;

comment on function public.register_member is
  'Creates the profile, settings, safeguards and member role for the signed-in auth account. The only supported way a profile is created.';

revoke all on function public.register_member(
  text, public.gender, text, text, text, int, text, char(2), boolean) from public;
grant execute on function public.register_member(
  text, public.gender, text, text, text, int, text, char(2), boolean) to authenticated;

-- ---------------------------------------------------------------------
-- SECTION 5 -- confirmation arriving after registration
--
-- GoTrue writes email_confirmed_at when the member follows the link in their
-- mail, which is usually after register_member has run. Both orders are
-- covered: this trigger for confirm-after-register, and the call inside
-- register_member for register-after-confirm.
-- ---------------------------------------------------------------------
create or replace function app.on_auth_user_confirmed()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.email_confirmed_at is not null
     and (tg_op = 'INSERT' or old.email_confirmed_at is null) then
    perform app.record_channel_verification(new.id, 'email');
  end if;
  return null;
end;
$$;

drop trigger if exists trg_auth_users_confirmed on auth.users;
create trigger trg_auth_users_confirmed
  after insert or update of email_confirmed_at on auth.users
  for each row execute function app.on_auth_user_confirmed();

-- ---------------------------------------------------------------------
-- SECTION 6 -- who am I?
--
-- One round trip for the shape the application needs on every cold start:
-- does this auth account have a profile yet, what is it allowed to do, and
-- what does it still owe (a covenant it has not accepted, an address it has
-- not confirmed). Returning a row of nulls rather than raising, because "no
-- profile yet" is an ordinary state on the way through onboarding.
-- ---------------------------------------------------------------------
create or replace function public.current_member()
returns table (
  user_id            uuid,
  display_name       text,
  contact_email      text,
  gender             public.gender,
  locale             text,
  timezone           text,
  verification_level public.verification_level,
  role_keys          text[],
  has_profile        boolean,
  covenant_accepted  boolean,
  email_confirmed    boolean
)
language sql
stable
security definer
set search_path = ''
as $$
  select
    u.id,
    p.display_name,
    p.contact_email::text,
    p.gender,
    p.locale,
    p.timezone,
    p.verification_level,
    coalesce(
      (select array_agg(r.key order by r.key)
         from public.user_roles ur
         join public.roles r on r.id = ur.role_id
        where ur.user_id = u.id
          and ur.revoked_at is null
          and (ur.expires_at is null or ur.expires_at > now())),
      '{}'::text[]),
    p.id is not null,
    p.accepted_covenant_at is not null,
    u.email_confirmed_at is not null
  from auth.users u
  left join public.profiles p on p.id = u.id and p.deleted_at is null
  where u.id = app.current_user_id();
$$;

comment on function public.current_member() is
  'The signed-in account as the application needs it on start-up. has_profile false means onboarding is unfinished.';

revoke all on function public.current_member() from public;
grant execute on function public.current_member() to authenticated;
