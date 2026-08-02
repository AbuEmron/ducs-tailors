-- =====================================================================
-- Fi Sabilillah -- 0007_messaging_purposes.sql
--
-- Messaging on this platform is PURPOSE-BOUND. There is no generic inbox
-- and no "message anyone" surface. A conversation may not exist without a
-- row in conversation_purposes -- enforced by a DEFERRABLE constraint
-- trigger (checked at COMMIT) and, independently, by the messages INSERT
-- policy in 0013.
--
-- UNSEND vs EVIDENCE
-- A participant may unsend a message; participants then see a tombstone.
-- The original text is moved to message_redactions, which only moderators
-- can read. Unsending must never destroy abuse evidence.
-- =====================================================================

create table public.conversations (
  id               uuid primary key default gen_random_uuid(),
  created_by       uuid not null references public.profiles(id) on delete cascade,
  title            text,
  is_group         boolean not null default false,
  organization_id  uuid references public.organizations(id) on delete set null,
  community_id     uuid references public.communities(id) on delete set null,
  -- Set by moderators; freezes all writes without deleting evidence.
  frozen_at        timestamptz,
  frozen_by        uuid references public.profiles(id) on delete set null,
  frozen_reason    text,
  last_message_at  timestamptz,
  closed_at        timestamptz,
  deleted_at       timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- conversation_purposes: the declared reason this channel exists.
-- One row per conversation (1:1). Deleting the purpose deletes nothing
-- else, but the constraint trigger below refuses to leave a conversation
-- purposeless at commit time.
-- ---------------------------------------------------------------------
create table public.conversation_purposes (
  id                 uuid primary key default gen_random_uuid(),
  conversation_id    uuid not null unique references public.conversations(id) on delete cascade,
  purpose_kind       public.conversation_purpose_kind not null,
  purpose_statement  text not null check (length(btrim(purpose_statement)) between 5 and 500),
  -- Optional anchor object that justifies the channel.
  opportunity_id     uuid references public.volunteer_opportunities(id) on delete set null,
  service_request_id uuid references public.service_requests(id) on delete set null,
  project_id         uuid references public.projects(id) on delete set null,
  offering_id        uuid references public.learning_offerings(id) on delete set null,
  introduction_id    uuid,   -- FK added in 0008 (forward reference)
  expires_at         timestamptz,
  declared_by        uuid not null references public.profiles(id) on delete cascade,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.conversation_purposes is
  'Every channel states why it exists. Off-purpose use is a reportable category.';

create table public.conversation_members (
  id               uuid primary key default gen_random_uuid(),
  conversation_id  uuid not null references public.conversations(id) on delete cascade,
  user_id          uuid not null references public.profiles(id) on delete cascade,
  member_role      text not null default 'participant'
                     check (member_role in ('participant','host','observer','wali_observer','moderator')),
  -- wali_observer: a guardian who can read but never write. Used by the
  -- formal introduction workflow so correspondence is never unsupervised.
  can_write        boolean not null default true,
  added_by         uuid references public.profiles(id) on delete set null,
  joined_at        timestamptz not null default now(),
  last_read_at     timestamptz,
  muted_until      timestamptz,
  left_at          timestamptz,
  removed_at       timestamptz,
  removed_by       uuid references public.profiles(id) on delete set null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  unique (conversation_id, user_id),
  constraint conversation_members_observer_readonly
    check (member_role <> 'wali_observer' or can_write = false)
);

comment on constraint conversation_members_observer_readonly on public.conversation_members is
  'A wali observer supervises; they never post as a participant.';

create table public.messages (
  id               uuid primary key default gen_random_uuid(),
  conversation_id  uuid not null references public.conversations(id) on delete cascade,
  sender_id        uuid not null references public.profiles(id) on delete cascade,
  body             text,
  attachment_path  text,          -- private storage object path
  reply_to_id      uuid references public.messages(id) on delete set null,
  -- Unsend tombstone. Body is nulled by trigger; original goes to
  -- message_redactions.
  unsent_at        timestamptz,
  unsent_by        uuid references public.profiles(id) on delete set null,
  -- Moderator removal (distinct from a user unsend).
  removed_at       timestamptz,
  removed_by       uuid references public.profiles(id) on delete set null,
  flagged_at       timestamptz,
  edited_at        timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint messages_have_content
    check (unsent_at is not null or removed_at is not null
           or body is not null or attachment_path is not null)
);

-- ---------------------------------------------------------------------
-- message_redactions: evidence preservation. Append-only, moderator-read.
-- No UPDATE or DELETE policy exists for this table anywhere.
-- ---------------------------------------------------------------------
create table public.message_redactions (
  id                uuid primary key default gen_random_uuid(),
  message_id        uuid not null references public.messages(id) on delete cascade,
  conversation_id   uuid not null references public.conversations(id) on delete cascade,
  sender_id         uuid not null references public.profiles(id) on delete cascade,
  original_body     text,
  original_attachment_path text,
  redaction_reason  text not null default 'user_unsend'
                      check (redaction_reason in ('user_unsend','moderator_removal','automated_filter')),
  redacted_by       uuid references public.profiles(id) on delete set null,
  original_created_at timestamptz not null,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.message_redactions is
  'Preserved original text of unsent/removed messages. Moderator SELECT only; never updatable or deletable.';

-- ---------------------------------------------------------------------
-- A conversation may not exist without a purpose. DEFERRABLE so that the
-- normal "insert conversation, insert purpose" pair works inside one
-- transaction, but a conversation can never be COMMITTED bare.
-- ---------------------------------------------------------------------
create or replace function app.assert_conversation_has_purpose()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not exists (select 1 from public.conversation_purposes cp
                  where cp.conversation_id = new.conversation_id) then
    raise exception
      'conversation % has no declared purpose; messaging is purpose-bound', new.conversation_id
      using errcode = 'check_violation';
  end if;
  return new;   -- BEFORE ROW trigger: must return NEW or the insert is silently dropped
end;
$$;

create or replace function app.assert_new_conversation_has_purpose()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not exists (select 1 from public.conversation_purposes cp
                  where cp.conversation_id = new.id) then
    raise exception
      'conversation % committed without a declared purpose', new.id
      using errcode = 'check_violation';
  end if;
  return null;
end;
$$;

create constraint trigger trg_conversations_require_purpose
  after insert on public.conversations
  deferrable initially deferred
  for each row execute function app.assert_new_conversation_has_purpose();

-- Non-deferred: a message may never be written into a purposeless channel,
-- even mid-transaction.
create trigger trg_messages_require_purpose
  before insert on public.messages
  for each row execute function app.assert_conversation_has_purpose();

select app.attach_updated_at('public.conversations');
select app.attach_updated_at('public.conversation_purposes');
select app.attach_updated_at('public.conversation_members');
select app.attach_updated_at('public.messages');
select app.attach_updated_at('public.message_redactions');
