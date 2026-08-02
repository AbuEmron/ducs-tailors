-- =====================================================================
-- Fi Sabilillah -- 0002_enums_and_lookups.sql
--
-- Design rule used throughout:
--   * PG ENUM  -> the value set is STRUCTURAL. Application logic, RLS
--                 policies and safety guarantees branch on these values.
--                 Adding a value is a schema change and a code change.
--   * LOOKUP TABLE -> the value set is EDITORIAL. Admins / scholars are
--                 expected to add rows over time (skills, subjects,
--                 service categories, rule templates, roles) and no policy
--                 branches on a specific row.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Structural enums
-- ---------------------------------------------------------------------

-- Only two values by design: the platform's introduction workflow is
-- built around Islamic marriage rulings, which are gender-structural.
create type public.gender as enum ('male', 'female');

create type public.verification_level as enum (
  'unverified',
  'basic',              -- email / phone confirmed
  'community_vouched',  -- vouched by N trusted members
  'org_verified',       -- verified by a verified organization
  'scholar_verified'    -- verified by a listed scholar/imam
);

create type public.verification_status as enum (
  'pending', 'under_review', 'verified', 'rejected', 'revoked'
);

create type public.report_category as enum (
  'harassment',
  'inappropriate_content',
  'scam_or_fraud',
  'impersonation',
  'safety_concern',
  'child_safety',
  'hate_speech',
  'spam',
  'off_purpose_contact',   -- using a purpose-bound channel for something else
  'other'
);

create type public.report_status as enum (
  'submitted', 'triaged', 'investigating', 'actioned', 'dismissed', 'withdrawn'
);

create type public.moderation_case_status as enum (
  'open', 'investigating', 'awaiting_appeal', 'resolved', 'closed'
);

create type public.moderation_action_type as enum (
  'warning_issued',
  'content_removed',
  'message_redacted',
  'conversation_frozen',
  'account_restricted',
  'account_suspended',
  'account_reinstated',
  'verification_revoked',
  'introduction_cancelled',
  'report_dismissed',
  'case_escalated',
  'referred_to_authorities'
);

create type public.appeal_status as enum (
  'submitted', 'under_review', 'upheld', 'overturned', 'partially_overturned', 'withdrawn'
);

-- Messaging is purpose-bound. There is no "just chatting" value.
create type public.conversation_purpose_kind as enum (
  'volunteering_coordination',
  'learning_session',
  'service_request',
  'mutual_aid',
  'project_work',
  'organization_admin',
  'community_announcement',
  'formal_introduction',
  'moderation_support'
);

-- The wali-centred introduction workflow. Every transition is audited.
create type public.introduction_status as enum (
  'draft',
  'submitted',
  'wali_review',                -- initiator's wali reviewing
  'wali_approved',
  'wali_declined',
  'counterpart_wali_review',    -- recipient's wali reviewing
  'counterpart_declined',
  'approved_and_forwarded',     -- ONLY state in which contact may be disclosed
  'in_correspondence',
  'concluded_positively',
  'concluded_declined',
  'withdrawn',
  'cancelled_by_moderator'
);

create type public.membership_status as enum (
  'invited', 'pending', 'active', 'paused', 'left', 'removed', 'banned'
);

create type public.org_member_role as enum ('member', 'coordinator', 'admin', 'owner');

create type public.application_status as enum (
  'draft', 'submitted', 'shortlisted', 'accepted', 'declined',
  'withdrawn', 'completed', 'no_show'
);

create type public.enrollment_status as enum (
  'requested', 'enrolled', 'waitlisted', 'completed', 'withdrawn', 'removed'
);

create type public.request_status as enum (
  'draft', 'open', 'matched', 'in_progress', 'fulfilled', 'closed', 'expired', 'cancelled'
);

create type public.commitment_status as enum (
  'pledged', 'confirmed', 'in_progress', 'fulfilled', 'partially_fulfilled', 'missed', 'released'
);

create type public.restriction_type as enum (
  'messaging_suspended',
  'posting_suspended',
  'introductions_suspended',
  'organizing_suspended',
  'full_suspension'
);

create type public.task_status as enum (
  'todo', 'in_progress', 'blocked', 'review', 'done', 'cancelled'
);

create type public.project_status as enum (
  'proposed', 'planning', 'active', 'paused', 'completed', 'archived', 'cancelled'
);

create type public.campaign_status as enum (
  'draft', 'pending_verification', 'active', 'paused', 'completed', 'cancelled'
);

-- NOTE: no payment processing exists in this schema. Donations are
-- RECORDS of offline/externally-settled giving only.
create type public.donation_status as enum (
  'pledged', 'recorded', 'acknowledged', 'cancelled'
);

create type public.notification_channel as enum ('in_app', 'email', 'push');

create type public.community_visibility as enum (
  'public', 'listed_private', 'invite_only', 'hidden'
);

create type public.consent_type as enum (
  'terms_of_service',
  'privacy_policy',
  'community_covenant',
  'safeguarding_policy',
  'wali_mediation_terms',
  'contact_disclosure',
  'data_export',
  'account_deletion'
);

create type public.incident_severity as enum ('low', 'moderate', 'high', 'critical');

create type public.trust_relationship as enum (
  'wali', 'mahram', 'mentor', 'vouching_elder', 'emergency_contact', 'community_reference'
);

create type public.offering_format as enum ('in_person', 'online', 'hybrid', 'self_paced');

create type public.gender_policy as enum (
  'any', 'brothers_only', 'sisters_only', 'family_friendly'
);

-- ---------------------------------------------------------------------
-- Lookup tables (admin-extensible)
-- ---------------------------------------------------------------------

-- Platform roles. Rows are seeded; new roles may be added by admins.
-- RLS in 0013 makes this table read-only for everyone but platform admins.
create table public.roles (
  id            uuid primary key default gen_random_uuid(),
  key           text not null unique,
  display_name  text not null,
  description   text,
  is_privileged boolean not null default false,  -- true => never self-grantable
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.roles is 'Lookup: platform roles. is_privileged rows can only be granted by a platform admin.';

create table public.skills (
  id           uuid primary key default gen_random_uuid(),
  slug         text not null unique,
  name         text not null,
  category     text not null default 'general',
  description  text,
  is_active    boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table public.service_categories (
  id           uuid primary key default gen_random_uuid(),
  slug         text not null unique,
  name         text not null,
  description  text,
  requires_safeguarding boolean not null default false,
  is_active    boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table public.learning_subjects (
  id           uuid primary key default gen_random_uuid(),
  slug         text not null unique,
  name         text not null,
  description  text,
  requires_scholarly_qualification boolean not null default false,
  is_active    boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table public.community_rule_templates (
  id           uuid primary key default gen_random_uuid(),
  slug         text not null unique,
  title        text not null,
  body         text not null,
  is_active    boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table public.qualification_types (
  id           uuid primary key default gen_random_uuid(),
  slug         text not null unique,
  name         text not null,
  description  text,
  requires_evidence boolean not null default true,
  is_active    boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

select app.attach_updated_at('public.roles');
select app.attach_updated_at('public.skills');
select app.attach_updated_at('public.service_categories');
select app.attach_updated_at('public.learning_subjects');
select app.attach_updated_at('public.community_rule_templates');
select app.attach_updated_at('public.qualification_types');
