-- =====================================================================
-- Fi Sabilillah -- 0014_functions_triggers.sql
--
-- Guard triggers and the SECURITY DEFINER entry points that are the ONLY
-- legitimate way to perform privileged writes.
--
-- HOW THE GUARDS TELL "privileged" FROM "a user typing SQL"
-- app.is_privileged_caller() is a SECURITY INVOKER function, so it sees
-- the REAL current_user. Inside a SECURITY DEFINER function owned by the
-- migration role it reports that owner; in a direct request from a signed
-- in member it reports `authenticated`. Guard triggers are therefore also
-- SECURITY INVOKER. This is deliberately NOT a session GUC flag: a GUC
-- could simply be set by the attacker before their UPDATE.
-- =====================================================================

create or replace function app.is_privileged_caller()
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  -- current_user is an SQL keyword, not a schema-qualifiable function, so it
  -- resolves correctly even with an empty search_path.
  select current_user::text not in ('authenticated', 'anon');
$$;

comment on function app.is_privileged_caller() is
  'TRUE only inside a SECURITY DEFINER function owned by a privileged role, or for service_role. Never spoofable from a client session.';

-- =====================================================================
-- SECTION 1 -- guard triggers (SECURITY INVOKER on purpose)
-- =====================================================================

-- profiles.verification_level mirrors user_verifications; a member may
-- never simply write themselves a higher trust level.
create or replace function app.guard_profile_verification_level()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if new.verification_level is distinct from old.verification_level
     and not app.is_privileged_caller() and not app.is_moderator() then
    raise exception 'verification_level is derived from user_verifications and cannot be set directly'
      using errcode = 'insufficient_privilege';
  end if;
  if new.id is distinct from old.id then
    raise exception 'profile id is immutable' using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_profiles_guard_verification_level
  before update on public.profiles
  for each row execute function app.guard_profile_verification_level();

-- No self-service role grants, ever. Second line of defence behind the
-- user_roles RLS policies.
create or replace function app.guard_role_escalation()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_privileged boolean;
begin
  select r.is_privileged into v_privileged from public.roles r where r.id = new.role_id;

  if new.user_id = app.current_user_id() and coalesce(v_privileged, true) then
    raise exception 'a member may never grant themselves a privileged role'
      using errcode = 'insufficient_privilege';
  end if;

  if app.is_privileged_caller() then
    return new;
  end if;

  if not app.is_platform_admin() then
    raise exception 'only a platform admin may grant or change platform roles'
      using errcode = 'insufficient_privilege';
  end if;

  return new;
end;
$$;

create trigger trg_user_roles_no_escalation
  before insert or update on public.user_roles
  for each row execute function app.guard_role_escalation();

-- qualifications.verified_at / verified_by are a third-party judgement.
create or replace function app.guard_qualification_verification()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    if (new.verified_at is not null or new.verified_by is not null)
       and not app.is_privileged_caller() and not app.is_moderator() then
      raise exception 'a qualification cannot be created already verified'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;

  if (new.verified_at is distinct from old.verified_at
      or new.verified_by is distinct from old.verified_by
      or new.revoked_at is distinct from old.revoked_at)
     and not app.is_privileged_caller() and not app.is_moderator() then
    raise exception 'qualification verification is set only by app.verify_qualification()'
      using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_qualifications_guard_verification
  before insert or update on public.qualifications
  for each row execute function app.guard_qualification_verification();

-- user_verifications: members submit, moderators decide.
create or replace function app.guard_user_verification()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if app.is_privileged_caller() or app.is_moderator() then
    return new;
  end if;

  if tg_op = 'INSERT' then
    if new.status <> 'pending' or new.verified_at is not null or new.reviewed_by is not null then
      raise exception 'a verification request may only be submitted as pending'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;

  if new.status is distinct from old.status
     or new.verified_at is distinct from old.verified_at
     or new.reviewed_by is distinct from old.reviewed_by
     or new.reviewed_at is distinct from old.reviewed_at
     or new.revoked_at is distinct from old.revoked_at then
    raise exception 'verification outcomes are set only by app.decide_user_verification()'
      using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_user_verifications_guard
  before insert or update on public.user_verifications
  for each row execute function app.guard_user_verification();

-- Skill endorsements come from someone else, not from the claimant.
create or replace function app.guard_skill_endorsement()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if app.is_privileged_caller() or app.is_moderator() then
    return new;
  end if;
  if new.endorsed_by = new.user_id then
    raise exception 'a member cannot endorse their own skill'
      using errcode = 'insufficient_privilege';
  end if;
  if tg_op = 'INSERT' then
    if new.endorsed_at is not null or new.endorsed_by is not null then
      raise exception 'skill endorsements are recorded by the endorser, not the claimant'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;
  if new.endorsed_at is distinct from old.endorsed_at
     or new.endorsed_by is distinct from old.endorsed_by then
    raise exception 'skill endorsements cannot be self-modified'
      using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_user_skills_guard_endorsement
  before insert or update on public.user_skills
  for each row execute function app.guard_skill_endorsement();

-- Introduction status: the two gate transitions are function-only.
create or replace function app.guard_introduction_status()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if app.is_privileged_caller() then
    return new;
  end if;

  if tg_op = 'INSERT' then
    if new.status not in ('draft','submitted') or new.approved_and_forwarded_at is not null then
      raise exception 'an introduction always starts as draft or submitted'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;

  if new.status is distinct from old.status
     and new.status in ('approved_and_forwarded','in_correspondence') then
    raise exception 'forwarding an introduction is done only by app.forward_introduction()'
      using errcode = 'insufficient_privilege';
  end if;

  if new.approved_and_forwarded_at is distinct from old.approved_and_forwarded_at then
    raise exception 'approved_and_forwarded_at is set only by app.forward_introduction()'
      using errcode = 'insufficient_privilege';
  end if;

  return new;
end;
$$;

create trigger trg_fir_guard_status
  before insert or update on public.formal_introduction_requests
  for each row execute function app.guard_introduction_status();

-- Contact authorization is the single most sensitive bit in the schema.
create or replace function app.guard_introduction_authorization()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if app.is_privileged_caller() then
    return new;
  end if;
  if new.contact_authorized_at is null then
    return new;
  end if;
  if tg_op = 'INSERT' or old.contact_authorized_at is null then
    raise exception 'contact authorization is granted only by app.forward_introduction()'
      using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_fip_guard_authorization
  before insert or update on public.formal_introduction_participants
  for each row execute function app.guard_introduction_authorization();

-- Zakat eligibility is a fiqh ruling, not an editable boolean.
create or replace function app.guard_campaign_zakat()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if app.is_privileged_caller() then
    return new;
  end if;
  if tg_op = 'INSERT' then
    if new.zakat_eligible then
      raise exception
        'zakat_eligible may only be set by app.set_campaign_zakat_eligible() with a recorded attestation'
        using errcode = 'insufficient_privilege';
    end if;
    return new;
  end if;
  if new.zakat_eligible and not old.zakat_eligible then
    raise exception
      'zakat_eligible may only be set by app.set_campaign_zakat_eligible() with a recorded attestation'
      using errcode = 'insufficient_privilege';
  end if;
  if new.zakat_attestation_id is distinct from old.zakat_attestation_id then
    raise exception 'the zakat attestation reference cannot be changed directly'
      using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_campaigns_guard_zakat
  before insert or update on public.campaigns
  for each row execute function app.guard_campaign_zakat();

-- A commitment is never confirmed by the person who made it.
create or replace function app.guard_self_confirmation()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if exists (select 1 from public.commitments c
              where c.id = new.commitment_id and c.user_id = new.confirmed_by) then
    raise exception 'a commitment cannot be confirmed by the person who made it'
      using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_commitment_confirmations_not_self
  before insert or update on public.commitment_confirmations
  for each row execute function app.guard_self_confirmation();

-- An appeal is never reviewed by the moderator who took the action.
create or replace function app.guard_appeal_reviewer()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.reviewer_id is not null and new.action_id is not null
     and exists (select 1 from public.moderation_actions ma
                  where ma.id = new.action_id and ma.actor_id = new.reviewer_id) then
    raise exception 'an appeal cannot be reviewed by the moderator who took the action'
      using errcode = 'insufficient_privilege';
  end if;
  return new;
end;
$$;

create trigger trg_appeals_reviewer_distinct
  before insert or update on public.appeals
  for each row execute function app.guard_appeal_reviewer();

-- =====================================================================
-- SECTION 2 -- evidence preservation and derived state (SECURITY DEFINER)
-- =====================================================================

-- Unsend redacts for participants but never destroys the original.
create or replace function app.preserve_message_on_redaction()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_reason text;
begin
  if (new.unsent_at is not null and old.unsent_at is null)
     or (new.removed_at is not null and old.removed_at is null) then

    v_reason := case when new.removed_at is not null and old.removed_at is null
                     then 'moderator_removal' else 'user_unsend' end;

    insert into public.message_redactions (
      message_id, conversation_id, sender_id, original_body,
      original_attachment_path, redaction_reason, redacted_by, original_created_at)
    values (
      old.id, old.conversation_id, old.sender_id, old.body,
      old.attachment_path, v_reason, app.current_user_id(), old.created_at);

    new.body := null;
    new.attachment_path := null;
    if new.unsent_at is not null then
      new.unsent_by := coalesce(new.unsent_by, app.current_user_id());
    end if;
    if new.removed_at is not null then
      new.removed_by := coalesce(new.removed_by, app.current_user_id());
    end if;
  end if;
  return new;
end;
$$;

create trigger trg_messages_preserve_on_unsend
  before update on public.messages
  for each row execute function app.preserve_message_on_redaction();

create or replace function app.bump_conversation_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.conversations set last_message_at = new.created_at where id = new.conversation_id;
  return null;
end;
$$;

create trigger trg_messages_bump_conversation
  after insert on public.messages
  for each row execute function app.bump_conversation_activity();

-- profiles.verification_level is derived, never asserted.
create or replace function app.sync_profile_verification_level()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_level public.verification_level;
begin
  select uv.level into v_level
    from public.user_verifications uv
   where uv.user_id = new.user_id
     and uv.status = 'verified'
     and uv.revoked_at is null
     and (uv.expires_at is null or uv.expires_at > now())
   order by uv.level desc
   limit 1;

  update public.profiles
     set verification_level = coalesce(v_level, 'unverified')
   where id = new.user_id
     and verification_level is distinct from coalesce(v_level, 'unverified');
  return null;
end;
$$;

create trigger trg_user_verifications_sync_profile
  after insert or update on public.user_verifications
  for each row execute function app.sync_profile_verification_level();

create or replace function app.sync_organization_verification_level()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_level public.verification_level;
begin
  select ov.level into v_level
    from public.organization_verifications ov
   where ov.organization_id = new.organization_id
     and ov.status = 'verified'
     and ov.revoked_at is null
     and (ov.expires_at is null or ov.expires_at > now())
   order by ov.level desc
   limit 1;

  update public.organizations
     set verification_level = coalesce(v_level, 'unverified')
   where id = new.organization_id
     and verification_level is distinct from coalesce(v_level, 'unverified');
  return null;
end;
$$;

create trigger trg_org_verifications_sync
  after insert or update on public.organization_verifications
  for each row execute function app.sync_organization_verification_level();

create or replace function app.recalculate_campaign_total()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_campaign uuid;
begin
  if tg_op = 'DELETE' then
    v_campaign := old.campaign_id;
  else
    v_campaign := new.campaign_id;
  end if;
  if v_campaign is null then
    return null;
  end if;
  update public.campaigns c
     set recorded_total = coalesce((select sum(d.amount) from public.donations d
                                     where d.campaign_id = v_campaign
                                       and d.status in ('recorded','acknowledged')), 0)
   where c.id = v_campaign;
  return null;
end;
$$;

create trigger trg_donations_recalculate_total
  after insert or update or delete on public.donations
  for each row execute function app.recalculate_campaign_total();

-- =====================================================================
-- SECTION 3 -- audit triggers
-- Requirement: every moderation action, verification change, role grant,
-- safeguard change and introduction status change leaves an audit row.
-- =====================================================================

-- A moderation action ALWAYS produces an audit_logs row. There is no code
-- path that inserts into moderation_actions without this firing.
create or replace function app.audit_moderation_action()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform app.write_audit(
    'moderation_action.' || new.action_type::text,
    'moderation_actions',
    new.id,
    jsonb_build_object(
      'case_id', new.case_id,
      'report_id', new.report_id,
      'action_type', new.action_type,
      'target_user_id', new.target_user_id,
      'target_message_id', new.target_message_id,
      'target_conversation_id', new.target_conversation_id,
      'effective_at', new.effective_at,
      'rationale_present', (new.rationale is not null)),
    new.actor_id,
    new.target_user_id);
  return null;
end;
$$;

create trigger trg_moderation_actions_audit
  after insert on public.moderation_actions
  for each row execute function app.audit_moderation_action();

create or replace function app.audit_role_grant()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_key text;
  v_action text;
begin
  select r.key into v_key from public.roles r where r.id = new.role_id;
  if tg_op = 'INSERT' then
    v_action := 'role.granted';
  elsif new.revoked_at is not null and old.revoked_at is null then
    v_action := 'role.revoked';
  else
    v_action := 'role.updated';
  end if;
  perform app.write_audit(
    v_action,
    'user_roles', new.id,
    jsonb_build_object('role_key', v_key, 'expires_at', new.expires_at),
    coalesce(new.granted_by, app.current_user_id()), new.user_id);
  return null;
end;
$$;

create trigger trg_user_roles_audit
  after insert or update on public.user_roles
  for each row execute function app.audit_role_grant();

create or replace function app.audit_verification_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' then
    if new.status is not distinct from old.status
       and new.revoked_at is not distinct from old.revoked_at then
      return null;
    end if;
  end if;
  perform app.write_audit(
    'verification.' || new.status::text, 'user_verifications', new.id,
    jsonb_build_object('level', new.level, 'method', new.method,
                       'reviewed_by', new.reviewed_by),
    coalesce(new.reviewed_by, app.current_user_id()), new.user_id);
  return null;
end;
$$;

create trigger trg_user_verifications_audit
  after insert or update on public.user_verifications
  for each row execute function app.audit_verification_change();

create or replace function app.audit_org_verification_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform app.write_audit(
    'organization_verification.' || new.status::text, 'organization_verifications', new.id,
    jsonb_build_object('organization_id', new.organization_id, 'level', new.level),
    coalesce(new.reviewed_by, app.current_user_id()), null);
  return null;
end;
$$;

create trigger trg_org_verifications_audit
  after insert or update on public.organization_verifications
  for each row execute function app.audit_org_verification_change();

-- Loosening a safeguard is a safety-relevant event and is recorded.
create or replace function app.audit_safeguard_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform app.write_audit(
    case when tg_op = 'INSERT' then 'safeguards.created' else 'safeguards.updated' end,
    'user_safeguards', new.id,
    jsonb_build_object(
      'require_purpose_for_contact', new.require_purpose_for_contact,
      'allow_unsolicited_contact', new.allow_unsolicited_contact,
      'require_wali_for_introductions', new.require_wali_for_introductions,
      'gender_interaction_policy', new.gender_interaction_policy,
      'min_counterparty_verification', new.min_counterparty_verification),
    new.user_id, new.user_id);
  return null;
end;
$$;

create trigger trg_user_safeguards_audit
  after insert or update on public.user_safeguards
  for each row execute function app.audit_safeguard_change();

create or replace function app.audit_introduction_status()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_previous text;
begin
  if tg_op = 'UPDATE' then
    if new.status is not distinct from old.status then
      return null;
    end if;
    v_previous := old.status::text;
  end if;
  perform app.write_audit(
    'introduction.' || new.status::text, 'formal_introduction_requests', new.id,
    jsonb_build_object(
      'initiator_id', new.initiator_id,
      'recipient_id', new.recipient_id,
      'previous_status', v_previous),
    app.current_user_id(), new.recipient_id);
  return null;
end;
$$;

create trigger trg_fir_audit_status
  after insert or update on public.formal_introduction_requests
  for each row execute function app.audit_introduction_status();

-- =====================================================================
-- SECTION 4 -- privileged entry points
-- =====================================================================

-- --- roles ------------------------------------------------------------
create or replace function app.grant_role(p_user uuid, p_role_key text, p_reason text default null)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_role uuid;
  v_id   uuid;
begin
  if not app.is_platform_admin() then
    raise exception 'only a platform admin may grant roles' using errcode = 'insufficient_privilege';
  end if;
  if p_user = app.current_user_id() then
    raise exception 'a platform admin may not grant a role to themselves'
      using errcode = 'insufficient_privilege';
  end if;
  select r.id into v_role from public.roles r where r.key = p_role_key;
  if v_role is null then
    raise exception 'unknown role %', p_role_key using errcode = 'no_data_found';
  end if;
  insert into public.user_roles (user_id, role_id, granted_by, reason)
  values (p_user, v_role, app.current_user_id(), p_reason)
  returning id into v_id;
  return v_id;
end;
$$;

-- --- verification -----------------------------------------------------
create or replace function app.decide_user_verification(
  p_verification uuid,
  p_status public.verification_status,
  p_note text default null)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if not app.is_moderator() then
    raise exception 'only a moderator may decide a verification'
      using errcode = 'insufficient_privilege';
  end if;
  update public.user_verifications
     set status        = p_status,
         reviewed_by   = app.current_user_id(),
         reviewed_at   = now(),
         verified_at   = case when p_status = 'verified' then now() else null end,
         revoked_at    = case when p_status = 'revoked' then now() else revoked_at end,
         decision_note = coalesce(p_note, decision_note)
   where id = p_verification;
  if not found then
    raise exception 'verification % not found', p_verification using errcode = 'no_data_found';
  end if;
end;
$$;

create or replace function app.verify_qualification(p_qualification uuid, p_note text default null)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if not (app.is_moderator() or app.is_scholar()) then
    raise exception 'only a moderator or a listed scholar may verify a qualification'
      using errcode = 'insufficient_privilege';
  end if;
  update public.qualifications
     set verified_at = now(),
         verified_by = app.current_user_id(),
         verification_note = coalesce(p_note, verification_note)
   where id = p_qualification;
  if not found then
    raise exception 'qualification % not found', p_qualification using errcode = 'no_data_found';
  end if;
  perform app.write_audit('qualification.verified', 'qualifications', p_qualification,
                          jsonb_build_object('note_present', p_note is not null));
end;
$$;

-- --- moderation -------------------------------------------------------
create or replace function app.record_moderation_action(
  p_action_type public.moderation_action_type,
  p_rationale   text,
  p_case        uuid default null,
  p_report      uuid default null,
  p_target_user uuid default null,
  p_target_message uuid default null,
  p_target_conversation uuid default null)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_id uuid;
begin
  if not app.is_moderator() then
    raise exception 'only a moderator may record a moderation action'
      using errcode = 'insufficient_privilege';
  end if;
  if p_target_user is not null and p_target_user = app.current_user_id() then
    raise exception 'a moderator may not act on their own account'
      using errcode = 'insufficient_privilege';
  end if;
  insert into public.moderation_actions (
    case_id, report_id, actor_id, action_type, target_user_id,
    target_message_id, target_conversation_id, rationale)
  values (p_case, p_report, app.current_user_id(), p_action_type, p_target_user,
          p_target_message, p_target_conversation, p_rationale)
  returning id into v_id;
  return v_id;
end;
$$;

-- --- zakat ------------------------------------------------------------
create or replace function app.set_campaign_zakat_eligible(p_campaign uuid, p_attestation uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_org        uuid;
  v_org_level  public.verification_level;
begin
  if not (app.is_moderator() or app.is_scholar()) then
    raise exception 'zakat eligibility is recorded by a moderator or a listed scholar only'
      using errcode = 'insufficient_privilege';
  end if;

  select c.organization_id into v_org from public.campaigns c where c.id = p_campaign;
  if v_org is null then
    raise exception 'campaign % not found', p_campaign using errcode = 'no_data_found';
  end if;

  select o.verification_level into v_org_level
    from public.organizations o where o.id = v_org;
  if v_org_level not in ('org_verified','scholar_verified') then
    raise exception 'the owning organization must be verified before a campaign can be zakat eligible'
      using errcode = 'insufficient_privilege';
  end if;

  if not exists (
    select 1 from public.campaign_verifications cv
     where cv.id = p_attestation
       and cv.campaign_id = p_campaign
       and cv.kind in ('scholarly_zakat_attestation','organization_zakat_attestation')
       and cv.status = 'verified'
       and cv.revoked_at is null
  ) then
    raise exception 'a verified zakat attestation for this campaign is required'
      using errcode = 'insufficient_privilege';
  end if;

  update public.campaigns
     set zakat_eligible = true, zakat_attestation_id = p_attestation
   where id = p_campaign;

  perform app.write_audit('campaign.zakat_eligible_set', 'campaigns', p_campaign,
                          jsonb_build_object('attestation_id', p_attestation));
end;
$$;

-- --- introductions ----------------------------------------------------
-- The ONLY path from "both walis approved" to "contact may be disclosed".
create or replace function app.forward_introduction(p_introduction uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_fir  public.formal_introduction_requests%rowtype;
  v_uid  uuid := app.current_user_id();
  v_allowed boolean;
begin
  select * into v_fir from public.formal_introduction_requests where id = p_introduction;
  if not found then
    raise exception 'introduction % not found', p_introduction using errcode = 'no_data_found';
  end if;

  -- Only a wali of either side, or a moderator, may forward.
  select app.is_moderator()
      or exists (select 1 from public.wali_profiles wp
                  where wp.id in (v_fir.initiator_wali_profile_id, v_fir.recipient_wali_profile_id)
                    and wp.wali_user_id = v_uid)
    into v_allowed;
  if not v_allowed then
    raise exception 'only a wali of this introduction or a moderator may forward it'
      using errcode = 'insufficient_privilege';
  end if;

  if v_fir.initiator_wali_profile_id is null or v_fir.recipient_wali_profile_id is null then
    raise exception 'both sides must have a recorded wali before forwarding'
      using errcode = 'insufficient_privilege';
  end if;
  if v_fir.initiator_wali_decided_at is null or v_fir.recipient_wali_decided_at is null then
    raise exception 'both walis must have recorded a decision before forwarding'
      using errcode = 'insufficient_privilege';
  end if;
  if v_fir.status <> 'counterpart_wali_review' then
    raise exception 'introduction % is not awaiting forwarding (status %)', p_introduction, v_fir.status
      using errcode = 'invalid_parameter_value';
  end if;

  update public.formal_introduction_requests
     set status = 'approved_and_forwarded', approved_and_forwarded_at = now()
   where id = p_introduction;

  -- Each side becomes authorized for the OTHER side's wali contact only.
  update public.formal_introduction_participants
     set contact_authorized_at = now(),
         authorized_by = v_uid,
         authorized_for_wali_profile_id = v_fir.recipient_wali_profile_id
   where introduction_id = p_introduction
     and user_id = v_fir.initiator_id
     and participant_role = 'initiator';

  update public.formal_introduction_participants
     set contact_authorized_at = now(),
         authorized_by = v_uid,
         authorized_for_wali_profile_id = v_fir.initiator_wali_profile_id
   where introduction_id = p_introduction
     and user_id = v_fir.recipient_id
     and participant_role = 'recipient';

  perform app.write_audit('introduction.forwarded', 'formal_introduction_requests',
                          p_introduction, '{}'::jsonb, v_uid, v_fir.recipient_id);
end;
$$;

-- The ONLY read path to a wali's contact details for a counterparty.
create or replace function app.get_wali_contact(p_introduction uuid)
returns table (
  wali_profile_id   uuid,
  wali_display_name text,
  relationship      text,
  contact_email     text,
  contact_phone     text,
  preferred_contact_method text
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid    uuid := app.current_user_id();
  v_status public.introduction_status;
  v_wali   uuid;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;

  select fir.status into v_status
    from public.formal_introduction_requests fir where fir.id = p_introduction;
  if v_status is null then
    raise exception 'introduction % not found', p_introduction using errcode = 'no_data_found';
  end if;

  if v_status not in ('approved_and_forwarded','in_correspondence') then
    raise exception
      'wali contact details are not disclosed before an introduction is approved and forwarded (status %)',
      v_status using errcode = 'insufficient_privilege';
  end if;

  select fip.authorized_for_wali_profile_id into v_wali
    from public.formal_introduction_participants fip
   where fip.introduction_id = p_introduction
     and fip.user_id = v_uid
     and fip.revoked_at is null
     and fip.contact_authorized_at is not null
     and fip.authorized_for_wali_profile_id is not null
   limit 1;

  if v_wali is null then
    raise exception 'you are not an authorized participant for contact disclosure in introduction %',
      p_introduction using errcode = 'insufficient_privilege';
  end if;

  insert into public.wali_contact_disclosures (
    introduction_id, wali_profile_id, disclosed_to, disclosed_fields)
  values (p_introduction, v_wali, v_uid, array['contact_email','contact_phone']);

  return query
    select wp.id, wp.wali_display_name, wp.relationship,
           wp.contact_email::text, wp.contact_phone, wp.preferred_contact_method
      from public.wali_profiles wp
     where wp.id = v_wali;
end;
$$;

comment on function app.get_wali_contact(uuid) is
  'Discloses a wali contact ONLY for an approved+forwarded introduction, to an authorized participant, and records the disclosure.';

-- A member can always retrieve the wali details they themselves recorded.
create or replace function app.get_my_wali_contact()
returns table (
  wali_profile_id   uuid,
  wali_display_name text,
  contact_email     text,
  contact_phone     text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := app.current_user_id();
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  return query
    select wp.id, wp.wali_display_name, wp.contact_email::text, wp.contact_phone
      from public.wali_profiles wp
     where wp.user_id = v_uid and wp.deleted_at is null and wp.is_active;
end;
$$;

-- --- notifications ----------------------------------------------------
create or replace function app.notify(
  p_user uuid, p_kind text, p_title text,
  p_body text default null, p_entity_type text default null,
  p_entity_id uuid default null, p_urgent boolean default false)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_id uuid;
begin
  insert into public.notifications (user_id, kind, title, body, entity_type, entity_id, is_urgent, sent_at)
  values (p_user, p_kind, p_title, p_body, p_entity_type, p_entity_id, p_urgent, now())
  returning id into v_id;
  return v_id;
end;
$$;

-- =====================================================================
-- SECTION 5 -- execute grants for everything defined in this file
-- =====================================================================
grant execute on all functions in schema app to authenticated, service_role;
revoke execute on function app.grant_role(uuid, text, text) from anon;
revoke execute on function app.forward_introduction(uuid) from anon;
revoke execute on function app.get_wali_contact(uuid) from anon;
revoke execute on function app.get_my_wali_contact() from anon;
revoke execute on function app.set_campaign_zakat_eligible(uuid, uuid) from anon;
revoke execute on function app.record_moderation_action(
  public.moderation_action_type, text, uuid, uuid, uuid, uuid, uuid) from anon;
