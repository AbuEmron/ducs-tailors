-- =====================================================================
-- Fi Sabilillah -- 0018_message_safety_signals.sql
--
-- Where the automated checks put what they saw.
--
-- The Kotlin core has computed these signals since the first release and
-- thrown every one of them away at the end of the call. That made the
-- detection worthless in the one situation it exists for: a person doing
-- the same thing to a succession of different people, where no single
-- message is damning and the pattern is the whole evidence.
--
-- WHAT IS AND IS NOT STORED
-- A row names the message, the conversation, the sender, the kind of
-- signal, and the phrase that matched. It does NOT contain the message
-- body. A moderator who needs the surrounding conversation opens the case
-- and reads it there, which goes through the ordinary message policies and
-- leaves an audit trail. A table holding a copy of every flagged private
-- message would be a second, quieter store of exactly the material this
-- platform is most careful about, and it would be readable by anyone who
-- could read this table.
--
-- WHO CAN SEE IT
-- Moderators. Not the sender, not the recipient, not the organisation.
-- The sender in particular must not be able to read their own signals:
-- that would turn the checks into a puzzle -- reword until the row stops
-- appearing -- and the people most motivated to solve it are the people
-- the checks exist for.
--
-- WHO CAN WRITE IT
-- Nobody, from a client session. Rows arrive through
-- app.record_message_signals(), a definer function, so a member cannot
-- fabricate signals against somebody else and a moderator cannot quietly
-- add evidence to a case they are deciding.
-- =====================================================================

create type public.safety_signal_kind as enum (
  'possible_flirtation',
  'possible_sexual_content',
  'possible_isolation_attempt',
  'possible_contact_detail_sharing',
  'possible_off_platform_move',
  'possible_financial_solicitation',
  'possible_purpose_drift',
  'repeated_contact_after_decline',
  'high_volume_new_conversations'
);

create type public.signal_confidence as enum ('low', 'medium', 'high');

create table public.message_safety_signals (
  id              uuid primary key default gen_random_uuid(),
  message_id      uuid not null references public.messages(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  -- Who sent the message the signal fired on. Never the recipient: being
  -- messaged something is not evidence about you.
  sender_id       uuid not null references public.profiles(id) on delete cascade,
  kind            public.safety_signal_kind not null,
  confidence      public.signal_confidence not null,
  -- The phrase that matched, in the words the check itself produced, so a
  -- moderator can see that "brother" tripped a keyword and dismiss it
  -- rather than being handed a score they cannot interrogate.
  explanation     text not null,
  -- Set once the signal has contributed to a case. Null means it is still
  -- loose evidence that has not yet amounted to anything.
  case_id         uuid references public.moderation_cases(id) on delete set null,
  observed_at     timestamptz not null default now(),
  created_at      timestamptz not null default now()
);

comment on table public.message_safety_signals is
  'Output of the on-device content checks. Advisory only: nothing here restricts an account.';

create index message_safety_signals_sender_idx
  on public.message_safety_signals (sender_id, observed_at desc);
create index message_safety_signals_case_idx
  on public.message_safety_signals (case_id) where case_id is not null;
create index message_safety_signals_open_idx
  on public.message_safety_signals (sender_id, kind) where case_id is null;

alter table public.message_safety_signals enable row level security;
alter table public.message_safety_signals force row level security;

create policy message_safety_signals_select_moderator on public.message_safety_signals
  for select to authenticated using (app.is_moderator());

-- 0013 granted the four verbs on every table that existed then; this table
-- came later, so its grants are stated here rather than inherited. SELECT is
-- granted and then narrowed to moderators by the policy above -- the same
-- arrangement every other table uses.
grant select on public.message_safety_signals to authenticated;

-- No insert, update or delete policy for any role, and the grants are
-- withheld too, so an attempt fails loudly rather than silently affecting
-- zero rows. The only writer is the definer function below.
revoke insert, update, delete on public.message_safety_signals from authenticated, anon;

-- ---------------------------------------------------------------------
-- The only way in.
-- ---------------------------------------------------------------------
create or replace function app.record_message_signals(
  p_message uuid,
  p_signals jsonb)
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_conversation uuid;
  v_sender       uuid;
  v_count        integer := 0;
begin
  select m.conversation_id, m.sender_id into v_conversation, v_sender
    from public.messages m where m.id = p_message;

  if v_sender is null then
    -- Either the message does not exist, or it is a system message with no
    -- sender. Neither is something to attribute a signal to.
    return 0;
  end if;

  -- A member may only cause signals to be recorded against their own message.
  -- Nobody can plant evidence on someone else's account.
  --
  -- The test is app.current_user_id() ALONE, with no privileged-caller escape
  -- hatch, and that is deliberate. app.is_privileged_caller() reads the real
  -- current_user, which inside a SECURITY DEFINER function is the function's
  -- owner -- so an `or app.is_privileged_caller()` here reports true for every
  -- caller and disables the check completely. An earlier draft of this
  -- migration had exactly that, and the test asserting a member cannot record
  -- signals against somebody else's message is what caught it.
  --
  -- auth.uid() has no such problem: it reads the request's JWT and gives the
  -- same answer wherever it is called. A back-office process that genuinely
  -- needs to write these rows runs as a role that bypasses RLS and inserts
  -- into the table directly; it does not need a bypass in here.
  if v_sender <> app.current_user_id() then
    raise exception 'signals may only be recorded for your own message'
      using errcode = 'insufficient_privilege';
  end if;

  insert into public.message_safety_signals
         (message_id, conversation_id, sender_id, kind, confidence, explanation, observed_at)
  select p_message,
         v_conversation,
         v_sender,
         (s ->> 'kind')::public.safety_signal_kind,
         (s ->> 'confidence')::public.signal_confidence,
         s ->> 'explanation',
         now()
    from jsonb_array_elements(coalesce(p_signals, '[]'::jsonb)) as s;

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

comment on function app.record_message_signals(uuid, jsonb) is
  'Records automated content-check output for one message. The sender is read from the message, never from the argument.';

revoke all on function app.record_message_signals(uuid, jsonb) from public;

-- Exposed through public so PostgREST can reach it; app.* is not an exposed schema.
create or replace function public.record_message_signals(
  p_message uuid,
  p_signals jsonb)
returns integer
language sql
volatile
security definer
set search_path = ''
as $$
  select app.record_message_signals(p_message, p_signals);
$$;

revoke all on function public.record_message_signals(uuid, jsonb) from public, anon;
grant execute on function public.record_message_signals(uuid, jsonb) to authenticated;

-- ---------------------------------------------------------------------
-- Attaching signals to a case. Moderator-only, and one-way: a signal
-- already resting under one case is never moved to another, because moving
-- it would quietly hollow out whatever was decided on the strength of it.
-- ---------------------------------------------------------------------
create or replace function app.attach_signals_to_case(
  p_signals uuid[],
  p_case uuid)
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_count integer := 0;
begin
  if not app.is_moderator() then
    raise exception 'only a moderator may attach signals to a case'
      using errcode = 'insufficient_privilege';
  end if;

  update public.message_safety_signals
     set case_id = p_case
   where id = any(p_signals)
     and case_id is null;

  get diagnostics v_count = row_count;

  perform app.write_audit('signals.attached_to_case', 'moderation_cases', p_case,
                          jsonb_build_object('signal_count', v_count));
  return v_count;
end;
$$;

revoke all on function app.attach_signals_to_case(uuid[], uuid) from public;
