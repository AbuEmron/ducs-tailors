-- =====================================================================
-- Fi Sabilillah -- 0013_row_level_security.sql
--
-- RLS is enabled on EVERY table in the public schema. Nothing is left
-- open. Each policy below carries a one-line statement of the invariant
-- it exists to enforce, in plain language.
--
-- Conventions
--   * `to authenticated`  -- normal signed-in members.
--   * `to anon`           -- unauthenticated reads: lookup tables only.
--   * service_role bypasses RLS (Supabase) and is how server-side jobs
--     and SECURITY DEFINER functions do privileged work.
--   * APPEND-ONLY tables (audit_logs, report_evidence, moderation_actions,
--     message_redactions, wali_contact_disclosures) have no UPDATE or
--     DELETE policy AND have those privileges revoked, so an attempt
--     fails loudly rather than silently affecting zero rows.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Base grants. RLS does the real work; these just open the door.
-- ---------------------------------------------------------------------
grant usage on schema public to anon, authenticated, service_role;
grant select, insert, update, delete on all tables in schema public to authenticated;
grant all on all tables in schema public to service_role;
grant execute on all functions in schema app to authenticated, service_role;
grant execute on all functions in schema app to anon;

-- Lookup tables are world-readable (they are the platform's vocabulary).
grant select on public.roles, public.skills, public.service_categories,
                public.learning_subjects, public.community_rule_templates,
                public.qualification_types, public.safeguard_presets
  to anon;

-- Three public-facing directories are also readable signed-out. Their
-- `to anon` policies below still restrict WHICH rows are visible.
grant select on public.organizations, public.communities, public.campaigns to anon;

-- =====================================================================
-- SECTION 1 -- lookup tables
-- =====================================================================

alter table public.roles enable row level security;
-- Everyone may read the role vocabulary; only platform admins may change it.
create policy roles_select_all on public.roles
  for select to anon, authenticated using (true);
create policy roles_write_admin on public.roles
  for all to authenticated using (app.is_platform_admin()) with check (app.is_platform_admin());

alter table public.skills enable row level security;
-- Active skills are public; the catalogue is curated by platform admins.
create policy skills_select_all on public.skills
  for select to anon, authenticated using (is_active or app.is_platform_admin());
create policy skills_write_admin on public.skills
  for all to authenticated using (app.is_platform_admin()) with check (app.is_platform_admin());

alter table public.service_categories enable row level security;
-- Public vocabulary for mutual-aid requests; admin-curated.
create policy service_categories_select_all on public.service_categories
  for select to anon, authenticated using (is_active or app.is_platform_admin());
create policy service_categories_write_admin on public.service_categories
  for all to authenticated using (app.is_platform_admin()) with check (app.is_platform_admin());

alter table public.learning_subjects enable row level security;
-- Public vocabulary for learning offerings; admin-curated.
create policy learning_subjects_select_all on public.learning_subjects
  for select to anon, authenticated using (is_active or app.is_platform_admin());
create policy learning_subjects_write_admin on public.learning_subjects
  for all to authenticated using (app.is_platform_admin()) with check (app.is_platform_admin());

alter table public.community_rule_templates enable row level security;
-- Rule templates are published so communities can adopt them verbatim.
create policy community_rule_templates_select_all on public.community_rule_templates
  for select to anon, authenticated using (is_active or app.is_platform_admin());
create policy community_rule_templates_write_admin on public.community_rule_templates
  for all to authenticated using (app.is_platform_admin()) with check (app.is_platform_admin());

alter table public.qualification_types enable row level security;
-- Qualification vocabulary is public; only admins define new kinds.
create policy qualification_types_select_all on public.qualification_types
  for select to anon, authenticated using (is_active or app.is_platform_admin());
create policy qualification_types_write_admin on public.qualification_types
  for all to authenticated using (app.is_platform_admin()) with check (app.is_platform_admin());

alter table public.safeguard_presets enable row level security;
-- Members must be able to read a preset before adopting it.
create policy safeguard_presets_select_all on public.safeguard_presets
  for select to anon, authenticated using (is_active or app.is_platform_admin());
create policy safeguard_presets_write_admin on public.safeguard_presets
  for all to authenticated using (app.is_platform_admin()) with check (app.is_platform_admin());

-- =====================================================================
-- SECTION 2 -- identity, settings, safeguards, roles
-- =====================================================================

alter table public.profiles enable row level security;
-- A member sees their own profile always; other profiles only when the
-- account is live, searchable, and there is no block in either direction.
create policy profiles_select_visible on public.profiles
  for select to authenticated
  using (
    id = app.current_user_id()
    or app.is_moderator()
    or (
      deleted_at is null
      and is_searchable
      -- app.is_blocked_between() reads public.blocks with definer rights: the
      -- BLOCKED party cannot see the block row itself, so an inline subquery
      -- here would silently evaluate to "no block" for exactly the person we
      -- are trying to stop.
      and not app.is_blocked_between(profiles.id, app.current_user_id())
    )
  );
-- A member may only ever create the profile row that matches their auth id.
create policy profiles_insert_self on public.profiles
  for insert to authenticated
  with check (id = app.current_user_id());
-- A member edits only their own profile. verification_level changes are
-- additionally rejected by trg_profiles_guard_verification_level (0014).
create policy profiles_update_self on public.profiles
  for update to authenticated
  using (id = app.current_user_id())
  with check (id = app.current_user_id());
-- Moderators may edit a profile (e.g. clear an abusive display name).
create policy profiles_update_moderator on public.profiles
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());
-- No DELETE policy: accounts are soft-deleted via deleted_at so that
-- outstanding reports and evidence keep their subject.

alter table public.user_settings enable row level security;
alter table public.user_settings force row level security;
-- Preferences are private to their owner. No moderator read.
create policy user_settings_own on public.user_settings
  for all to authenticated
  using (user_id = app.current_user_id())
  with check (user_id = app.current_user_id());

alter table public.user_safeguards enable row level security;
alter table public.user_safeguards force row level security;
-- Safeguards belong to the member alone; loosening one is audited (0014).
create policy user_safeguards_own on public.user_safeguards
  for all to authenticated
  using (user_id = app.current_user_id())
  with check (user_id = app.current_user_id());

alter table public.user_roles enable row level security;
-- A member can see which roles they hold, and moderators can see grants.
create policy user_roles_select_self_or_staff on public.user_roles
  for select to authenticated
  using (user_id = app.current_user_id() or app.is_moderator());
-- Only a platform admin may grant a role. This is the primary defence
-- against privilege escalation; trg_user_roles_no_escalation is the second.
create policy user_roles_insert_admin on public.user_roles
  for insert to authenticated
  with check (app.is_platform_admin());
create policy user_roles_update_admin on public.user_roles
  for update to authenticated
  using (app.is_platform_admin()) with check (app.is_platform_admin());
create policy user_roles_delete_admin on public.user_roles
  for delete to authenticated
  using (app.is_platform_admin());

alter table public.user_skills enable row level security;
-- Skills are shown publicly when the member marks them public.
create policy user_skills_select on public.user_skills
  for select to authenticated
  using (user_id = app.current_user_id() or app.is_moderator()
         or (is_public and app.is_account_active(user_id)));
-- A member curates only their own skills; endorsements are trigger-guarded.
create policy user_skills_write_own on public.user_skills
  for insert to authenticated with check (user_id = app.current_user_id());
create policy user_skills_update_own on public.user_skills
  for update to authenticated
  using (user_id = app.current_user_id()) with check (user_id = app.current_user_id());
create policy user_skills_delete_own on public.user_skills
  for delete to authenticated using (user_id = app.current_user_id());

-- =====================================================================
-- SECTION 3 -- organizations and communities
-- =====================================================================

alter table public.organizations enable row level security;
-- The organization directory is public; deleted orgs disappear.
create policy organizations_select_public on public.organizations
  for select to anon, authenticated
  using (deleted_at is null or app.is_moderator());
-- Anyone may register an organization, recorded as its creator.
create policy organizations_insert_self on public.organizations
  for insert to authenticated with check (created_by = app.current_user_id());
-- Only an admin OF THAT organization may edit it.
create policy organizations_update_admin on public.organizations
  for update to authenticated
  using (app.is_org_admin(id)) with check (app.is_org_admin(id));

alter table public.organization_members enable row level security;
-- The roster is private to the organization. An admin of org A gets
-- nothing at all from org B -- is_org_admin() is scoped to one org id.
create policy organization_members_select_same_org on public.organization_members
  for select to authenticated
  using (
    user_id = app.current_user_id()
    or app.is_org_member(organization_id)
    or app.is_moderator()
  );
-- A member may request to join; an org admin may add anyone.
create policy organization_members_insert on public.organization_members
  for insert to authenticated
  with check (
    (user_id = app.current_user_id() and status = 'pending' and org_role = 'member')
    or app.is_org_admin(organization_id)
  );
-- Only that organization's admins change roster rows (or a member leaving).
create policy organization_members_update on public.organization_members
  for update to authenticated
  using (app.is_org_admin(organization_id) or user_id = app.current_user_id())
  with check (app.is_org_admin(organization_id) or user_id = app.current_user_id());
create policy organization_members_delete_admin on public.organization_members
  for delete to authenticated using (app.is_org_admin(organization_id));

alter table public.organization_verifications enable row level security;
alter table public.organization_verifications force row level security;
-- Verification evidence never crosses an organization boundary.
create policy organization_verifications_select_own_org on public.organization_verifications
  for select to authenticated
  using (app.is_org_admin(organization_id) or app.is_moderator());
-- An org admin may submit evidence, but only in a pending, unreviewed state.
create policy organization_verifications_insert_own_org on public.organization_verifications
  for insert to authenticated
  with check (
    app.is_org_admin(organization_id)
    and status = 'pending' and reviewed_by is null and reviewed_at is null
  );
-- Only moderators decide verification outcomes.
create policy organization_verifications_update_moderator on public.organization_verifications
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());

alter table public.communities enable row level security;
-- Public communities are listed; private ones only to their members.
create policy communities_select on public.communities
  for select to authenticated
  using (
    (deleted_at is null and visibility in ('public','listed_private'))
    or app.is_community_member(id)
    or app.is_moderator()
  );
create policy communities_select_anon on public.communities
  for select to anon
  using (deleted_at is null and visibility = 'public');
create policy communities_insert_self on public.communities
  for insert to authenticated with check (created_by = app.current_user_id());
-- Only community moderators (or platform moderators) reshape a community.
create policy communities_update_moderator on public.communities
  for update to authenticated
  using (app.is_community_moderator(id)) with check (app.is_community_moderator(id));

alter table public.community_members enable row level security;
-- Membership lists are visible inside the community only.
create policy community_members_select on public.community_members
  for select to authenticated
  using (user_id = app.current_user_id()
         or app.is_community_member(community_id)
         or app.is_moderator());
create policy community_members_insert on public.community_members
  for insert to authenticated
  with check (
    (user_id = app.current_user_id() and status in ('pending','active') and is_moderator = false)
    or app.is_community_moderator(community_id)
  );
create policy community_members_update on public.community_members
  for update to authenticated
  using (app.is_community_moderator(community_id) or user_id = app.current_user_id())
  with check (app.is_community_moderator(community_id) or user_id = app.current_user_id());
create policy community_members_delete on public.community_members
  for delete to authenticated using (app.is_community_moderator(community_id));

alter table public.community_rules enable row level security;
-- Rules are readable by anyone who can see the community; editable by its moderators.
create policy community_rules_select on public.community_rules
  for select to authenticated
  using (
    exists (select 1 from public.communities c
             where c.id = community_rules.community_id
               and (c.visibility in ('public','listed_private') or app.is_community_member(c.id)))
    or app.is_moderator()
  );
create policy community_rules_write on public.community_rules
  for all to authenticated
  using (app.is_community_moderator(community_id))
  with check (app.is_community_moderator(community_id));

-- =====================================================================
-- SECTION 4 -- learning and volunteering
-- =====================================================================

alter table public.learning_offerings enable row level security;
-- Published offerings are discoverable; drafts stay with their teacher.
create policy learning_offerings_select on public.learning_offerings
  for select to authenticated
  using (
    (is_published and deleted_at is null)
    or teacher_id = app.current_user_id()
    or (organization_id is not null and app.is_org_admin(organization_id))
    or app.is_moderator()
  );
create policy learning_offerings_insert_teacher on public.learning_offerings
  for insert to authenticated
  with check (teacher_id = app.current_user_id()
              and app.is_account_active(app.current_user_id())
              and not app.is_restricted(app.current_user_id(), 'posting_suspended'));
create policy learning_offerings_update_owner on public.learning_offerings
  for update to authenticated
  using (teacher_id = app.current_user_id()
         or (organization_id is not null and app.is_org_admin(organization_id)))
  with check (teacher_id = app.current_user_id()
              or (organization_id is not null and app.is_org_admin(organization_id)));

alter table public.learning_enrollments enable row level security;
-- A student sees their own enrolment; the teacher sees their class.
create policy learning_enrollments_select on public.learning_enrollments
  for select to authenticated
  using (
    student_id = app.current_user_id()
    or exists (select 1 from public.learning_offerings o
                where o.id = learning_enrollments.offering_id
                  and (o.teacher_id = app.current_user_id()
                       or (o.organization_id is not null and app.is_org_admin(o.organization_id))))
    or app.is_moderator()
  );
create policy learning_enrollments_insert_self on public.learning_enrollments
  for insert to authenticated
  with check (student_id = app.current_user_id() and status = 'requested');
create policy learning_enrollments_update on public.learning_enrollments
  for update to authenticated
  using (student_id = app.current_user_id()
         or exists (select 1 from public.learning_offerings o
                     where o.id = learning_enrollments.offering_id
                       and o.teacher_id = app.current_user_id()))
  with check (student_id = app.current_user_id()
              or exists (select 1 from public.learning_offerings o
                          where o.id = learning_enrollments.offering_id
                            and o.teacher_id = app.current_user_id()));

alter table public.volunteer_opportunities enable row level security;
-- Published opportunities are public; unpublished ones belong to the org.
create policy volunteer_opportunities_select on public.volunteer_opportunities
  for select to authenticated
  using (
    (is_published and deleted_at is null)
    or created_by = app.current_user_id()
    or (organization_id is not null and app.is_org_member(organization_id))
    or app.is_moderator()
  );
-- Posting on behalf of an organization requires being its admin.
create policy volunteer_opportunities_insert on public.volunteer_opportunities
  for insert to authenticated
  with check (
    created_by = app.current_user_id()
    and app.is_account_active(app.current_user_id())
    and not app.is_restricted(app.current_user_id(), 'organizing_suspended')
    and (organization_id is null or app.is_org_admin(organization_id))
  );
create policy volunteer_opportunities_update on public.volunteer_opportunities
  for update to authenticated
  using (created_by = app.current_user_id()
         or (organization_id is not null and app.is_org_admin(organization_id)))
  with check (created_by = app.current_user_id()
              or (organization_id is not null and app.is_org_admin(organization_id)));

alter table public.volunteer_applications enable row level security;
-- An applicant sees their own application; the posting org sees its inbox.
create policy volunteer_applications_select on public.volunteer_applications
  for select to authenticated
  using (
    volunteer_id = app.current_user_id()
    or exists (select 1 from public.volunteer_opportunities o
                where o.id = volunteer_applications.opportunity_id
                  and (o.created_by = app.current_user_id()
                       or (o.organization_id is not null and app.is_org_admin(o.organization_id))))
    or app.is_moderator()
  );
create policy volunteer_applications_insert_self on public.volunteer_applications
  for insert to authenticated
  with check (volunteer_id = app.current_user_id()
              and app.is_account_active(app.current_user_id())
              and reviewed_by is null and decided_at is null);
create policy volunteer_applications_update on public.volunteer_applications
  for update to authenticated
  using (
    volunteer_id = app.current_user_id()
    or exists (select 1 from public.volunteer_opportunities o
                where o.id = volunteer_applications.opportunity_id
                  and (o.created_by = app.current_user_id()
                       or (o.organization_id is not null and app.is_org_admin(o.organization_id))))
  )
  with check (
    volunteer_id = app.current_user_id()
    or exists (select 1 from public.volunteer_opportunities o
                where o.id = volunteer_applications.opportunity_id
                  and (o.created_by = app.current_user_id()
                       or (o.organization_id is not null and app.is_org_admin(o.organization_id))))
  );

-- =====================================================================
-- SECTION 5 -- mutual aid, projects, commitments
-- =====================================================================

alter table public.service_requests enable row level security;
-- Open requests are visible to members who are not blocked by the
-- requester. The exact address is NOT in this table (see 0006).
create policy service_requests_select on public.service_requests
  for select to authenticated
  using (
    requester_id = app.current_user_id()
    or assigned_helper_id = app.current_user_id()
    or app.is_moderator()
    or (
      deleted_at is null
      and status in ('open','matched','in_progress')
      and not app.is_blocked_between(service_requests.requester_id, app.current_user_id())
    )
  );
create policy service_requests_insert_self on public.service_requests
  for insert to authenticated
  with check (requester_id = app.current_user_id()
              and app.is_account_active(app.current_user_id())
              and not app.is_restricted(app.current_user_id(), 'posting_suspended'));
create policy service_requests_update on public.service_requests
  for update to authenticated
  using (requester_id = app.current_user_id() or app.is_moderator())
  with check (requester_id = app.current_user_id() or app.is_moderator());

alter table public.service_request_private_details enable row level security;
alter table public.service_request_private_details force row level security;
-- THE DOORSTEP RULE. Exact address, coordinates and access notes are
-- readable only by the requester, the helper whose offer was ACCEPTED,
-- and moderators. Other responders -- even shortlisted ones -- see nothing.
create policy service_request_private_details_select on public.service_request_private_details
  for select to authenticated
  using (app.can_see_exact_location(request_id));
-- Only the requester can write their own address.
create policy service_request_private_details_insert on public.service_request_private_details
  for insert to authenticated
  with check (exists (select 1 from public.service_requests sr
                       where sr.id = service_request_private_details.request_id
                         and sr.requester_id = app.current_user_id()));
create policy service_request_private_details_update on public.service_request_private_details
  for update to authenticated
  using (exists (select 1 from public.service_requests sr
                  where sr.id = service_request_private_details.request_id
                    and sr.requester_id = app.current_user_id()))
  with check (exists (select 1 from public.service_requests sr
                       where sr.id = service_request_private_details.request_id
                         and sr.requester_id = app.current_user_id()));
create policy service_request_private_details_delete on public.service_request_private_details
  for delete to authenticated
  using (exists (select 1 from public.service_requests sr
                  where sr.id = service_request_private_details.request_id
                    and sr.requester_id = app.current_user_id()));

alter table public.request_responses enable row level security;
-- A responder sees their own offer; the requester sees all offers.
create policy request_responses_select on public.request_responses
  for select to authenticated
  using (
    responder_id = app.current_user_id()
    or exists (select 1 from public.service_requests sr
                where sr.id = request_responses.request_id
                  and sr.requester_id = app.current_user_id())
    or app.is_moderator()
  );
-- Offering help requires an active account and no block with the requester.
create policy request_responses_insert_self on public.request_responses
  for insert to authenticated
  with check (
    responder_id = app.current_user_id()
    and app.is_account_active(app.current_user_id())
    and exists (select 1 from public.service_requests sr
                 where sr.id = request_responses.request_id
                   and sr.status = 'open'
                   and sr.requester_id <> app.current_user_id()
                   and not app.is_blocked_between(sr.requester_id, app.current_user_id()))
  );
create policy request_responses_update on public.request_responses
  for update to authenticated
  using (responder_id = app.current_user_id()
         or exists (select 1 from public.service_requests sr
                     where sr.id = request_responses.request_id
                       and sr.requester_id = app.current_user_id()))
  with check (responder_id = app.current_user_id()
              or exists (select 1 from public.service_requests sr
                          where sr.id = request_responses.request_id
                            and sr.requester_id = app.current_user_id()));

alter table public.projects enable row level security;
-- Public projects are browsable; private ones are for their members.
create policy projects_select on public.projects
  for select to authenticated
  using (
    (is_public and deleted_at is null)
    or lead_id = app.current_user_id()
    -- definer helper: projects <-> project_members would otherwise recurse
    or app.is_project_member(id)
    or app.is_moderator()
  );
create policy projects_insert_lead on public.projects
  for insert to authenticated
  with check (lead_id = app.current_user_id()
              and app.is_account_active(app.current_user_id())
              and not app.is_restricted(app.current_user_id(), 'organizing_suspended'));
create policy projects_update_lead on public.projects
  for update to authenticated
  using (lead_id = app.current_user_id()
         or (organization_id is not null and app.is_org_admin(organization_id)))
  with check (lead_id = app.current_user_id()
              or (organization_id is not null and app.is_org_admin(organization_id)));

alter table public.project_members enable row level security;
-- The team roster is visible to the team.
create policy project_members_select on public.project_members
  for select to authenticated
  using (
    user_id = app.current_user_id()
    or app.is_project_lead(project_id)
    or app.is_project_member(project_id)
    or app.is_moderator()
  );
create policy project_members_insert on public.project_members
  for insert to authenticated
  with check (
    app.is_project_lead(project_id)
    or (user_id = app.current_user_id() and project_role = 'contributor')
  );
create policy project_members_update on public.project_members
  for update to authenticated
  using (user_id = app.current_user_id() or app.is_project_lead(project_id))
  with check (user_id = app.current_user_id() or app.is_project_lead(project_id));
create policy project_members_delete_lead on public.project_members
  for delete to authenticated
  using (app.is_project_lead(project_id));

alter table public.project_tasks enable row level security;
-- Tasks live inside the team; no outside visibility.
create policy project_tasks_select on public.project_tasks
  for select to authenticated
  using (
    app.is_project_member(project_id)
    or app.is_project_lead(project_id)
    or app.is_moderator()
  );
create policy project_tasks_write on public.project_tasks
  for insert to authenticated
  with check (
    created_by = app.current_user_id()
    and (app.is_project_member(project_id) or app.is_project_lead(project_id))
  );
create policy project_tasks_update on public.project_tasks
  for update to authenticated
  using (app.is_project_member(project_id) or app.is_project_lead(project_id))
  with check (app.is_project_member(project_id) or app.is_project_lead(project_id));

alter table public.commitments enable row level security;
-- A commitment is the member's own promise; confirmers may also see it.
create policy commitments_select on public.commitments
  for select to authenticated
  using (
    user_id = app.current_user_id()
    or app.is_moderator()
    -- definer helper: commitments <-> commitment_confirmations would recurse
    or app.has_confirmed_commitment(id)
  );
create policy commitments_insert_self on public.commitments
  for insert to authenticated
  with check (user_id = app.current_user_id());
create policy commitments_update_self on public.commitments
  for update to authenticated
  using (user_id = app.current_user_id()) with check (user_id = app.current_user_id());

alter table public.commitment_confirmations enable row level security;
-- Both sides see the confirmation; nobody may confirm their own commitment
-- (trg_commitment_confirmations_not_self, 0014).
create policy commitment_confirmations_select on public.commitment_confirmations
  for select to authenticated
  using (
    confirmed_by = app.current_user_id()
    or app.is_commitment_owner(commitment_id)
    or app.is_moderator()
  );
create policy commitment_confirmations_insert on public.commitment_confirmations
  for insert to authenticated
  with check (confirmed_by = app.current_user_id());
create policy commitment_confirmations_update on public.commitment_confirmations
  for update to authenticated
  using (confirmed_by = app.current_user_id()) with check (confirmed_by = app.current_user_id());

-- =====================================================================
-- SECTION 6 -- messaging
-- =====================================================================

alter table public.conversations enable row level security;
-- Only active members see a conversation, and never if a block exists
-- with any other member of it.
create policy conversations_select_member on public.conversations
  for select to authenticated
  using (
    (
      -- The creator counts even before their own membership row exists,
      -- otherwise "create channel then add members" is impossible.
      (app.is_conversation_member(id) or created_by = app.current_user_id())
      and not app.conversation_blocked_for(id, app.current_user_id())
    )
    or app.is_moderator()
  );
-- Opening a channel requires an active, unrestricted account.
create policy conversations_insert_self on public.conversations
  for insert to authenticated
  with check (
    created_by = app.current_user_id()
    and app.is_account_active(app.current_user_id())
    and not app.is_restricted(app.current_user_id(), 'messaging_suspended')
  );
-- Members may touch conversation metadata; moderators may freeze it.
create policy conversations_update on public.conversations
  for update to authenticated
  using (created_by = app.current_user_id() or app.is_moderator())
  with check (created_by = app.current_user_id() or app.is_moderator());

alter table public.conversation_purposes enable row level security;
-- The purpose is visible to the channel's members. It is IMMUTABLE:
-- there is deliberately no user UPDATE or DELETE policy.
create policy conversation_purposes_select on public.conversation_purposes
  for select to authenticated
  using (app.is_conversation_member(conversation_id) or app.is_moderator());
create policy conversation_purposes_insert on public.conversation_purposes
  for insert to authenticated
  with check (
    declared_by = app.current_user_id()
    and exists (select 1 from public.conversations c
                 where c.id = conversation_purposes.conversation_id
                   and c.created_by = app.current_user_id())
  );

alter table public.conversation_members enable row level security;
-- Membership of a channel is visible to that channel's members only.
create policy conversation_members_select on public.conversation_members
  for select to authenticated
  using (user_id = app.current_user_id()
         or app.is_conversation_member(conversation_id)
         or app.is_moderator());
-- You may add someone only if you own the channel and no block exists.
create policy conversation_members_insert on public.conversation_members
  for insert to authenticated
  with check (
    exists (select 1 from public.conversations c
             where c.id = conversation_members.conversation_id
               and c.created_by = app.current_user_id())
    and not app.is_blocked_between(conversation_members.user_id, app.current_user_id())
  );
-- A member updates their own read state; the owner may remove people.
create policy conversation_members_update on public.conversation_members
  for update to authenticated
  using (user_id = app.current_user_id()
         or exists (select 1 from public.conversations c
                     where c.id = conversation_members.conversation_id
                       and c.created_by = app.current_user_id())
         or app.is_moderator())
  with check (user_id = app.current_user_id()
              or exists (select 1 from public.conversations c
                          where c.id = conversation_members.conversation_id
                            and c.created_by = app.current_user_id())
              or app.is_moderator());

alter table public.messages enable row level security;
alter table public.messages force row level security;
-- Reading: active membership, no block with any other member. Moderators
-- may read for investigation (and that read is expected to be audited by
-- the application layer).
create policy messages_select_member on public.messages
  for select to authenticated
  using (
    app.is_moderator()
    or (
      app.is_conversation_member(conversation_id)
      -- app.conversation_blocked_for() reads public.blocks with definer
      -- rights; an inline subquery would be blind to a block the current
      -- user is on the receiving end of.
      and not app.conversation_blocked_for(conversation_id, app.current_user_id())
    )
  );
-- Writing requires ALL of: sender is self, account live and not
-- messaging-suspended, active write-enabled membership, the channel has a
-- declared purpose, the channel is not frozen/closed, and no block exists
-- with any other member.
create policy messages_insert_purposeful on public.messages
  for insert to authenticated
  with check (
    sender_id = app.current_user_id()
    and app.is_account_active(app.current_user_id())
    and not app.is_restricted(app.current_user_id(), 'messaging_suspended')
    and app.conversation_has_purpose(conversation_id)
    and exists (
      select 1 from public.conversation_members cm
       where cm.conversation_id = messages.conversation_id
         and cm.user_id = app.current_user_id()
         and cm.can_write
         and cm.removed_at is null
         and cm.left_at is null
    )
    and not exists (
      select 1 from public.conversations c
       where c.id = messages.conversation_id
         and (c.frozen_at is not null or c.closed_at is not null or c.deleted_at is not null)
    )
    and not app.conversation_blocked_for(conversation_id, app.current_user_id())
  );
-- The sender may unsend their own message; the original text is copied to
-- message_redactions first (trg_messages_preserve_on_unsend, 0014).
create policy messages_update_sender on public.messages
  for update to authenticated
  using (sender_id = app.current_user_id() or app.is_moderator())
  with check (sender_id = app.current_user_id() or app.is_moderator());
-- No DELETE policy anywhere: messages are never destroyed, only redacted.

alter table public.message_redactions enable row level security;
alter table public.message_redactions force row level security;
-- Preserved originals are moderator-only. Not even the sender can read
-- back (or reach) what they unsent.
create policy message_redactions_select_moderator on public.message_redactions
  for select to authenticated using (app.is_moderator());
create policy message_redactions_insert_moderator on public.message_redactions
  for insert to authenticated with check (app.is_moderator());
-- No UPDATE / DELETE policy: evidence preservation.
revoke update, delete on public.message_redactions from authenticated, anon;

-- =====================================================================
-- SECTION 7 -- trusted contacts, wali, formal introductions
-- =====================================================================

alter table public.trusted_contacts enable row level security;
alter table public.trusted_contacts force row level security;
-- Entirely private to the member. Moderators cannot browse this table.
create policy trusted_contacts_own on public.trusted_contacts
  for all to authenticated
  using (user_id = app.current_user_id())
  with check (user_id = app.current_user_id());

alter table public.wali_profiles enable row level security;
-- Row visibility: the ward, the wali's own account, participants in a
-- live introduction (so the redacted view is useful), and moderators.
-- COLUMN visibility for contact_email / contact_phone is removed below by
-- revoking the column privilege, so NO ONE reads them through SQL --
-- disclosure happens only via app.get_wali_contact() / app.get_my_wali_contact().
create policy wali_profiles_select on public.wali_profiles
  for select to authenticated
  -- Row visibility only. The contact columns stay unreadable either way
  -- because the SELECT privilege on them is revoked below.
  using (app.can_view_wali_profile(id));
create policy wali_profiles_insert_own on public.wali_profiles
  for insert to authenticated with check (user_id = app.current_user_id());
create policy wali_profiles_update_own on public.wali_profiles
  for update to authenticated
  using (user_id = app.current_user_id()) with check (user_id = app.current_user_id());
create policy wali_profiles_delete_own on public.wali_profiles
  for delete to authenticated using (user_id = app.current_user_id());

-- Column-level lockdown of the two protected columns.
revoke select on public.wali_profiles from authenticated, anon;
grant select (
  id, user_id, wali_user_id, wali_display_name, relationship,
  preferred_contact_method, masjid_organization_id, confirmed_at, confirmed_by,
  confirmation_method, is_active, revoked_at, deleted_at, created_at, updated_at
) on public.wali_profiles to authenticated;

alter table public.wali_contact_disclosures enable row level security;
alter table public.wali_contact_disclosures force row level security;
-- The ward can always audit who was given their wali's details.
create policy wali_contact_disclosures_select on public.wali_contact_disclosures
  for select to authenticated
  using (
    disclosed_to = app.current_user_id()
    or app.owns_wali_profile(wali_profile_id)
    or app.is_wali_of_profile(wali_profile_id)
    or app.is_moderator()
  );
-- Written only by app.get_wali_contact(). No user INSERT/UPDATE/DELETE.
revoke insert, update, delete on public.wali_contact_disclosures from authenticated, anon;

alter table public.formal_introduction_settings enable row level security;
alter table public.formal_introduction_settings force row level security;
-- Introduction settings are private to the member and their wali.
create policy formal_introduction_settings_select on public.formal_introduction_settings
  for select to authenticated
  using (
    user_id = app.current_user_id()
    or app.is_moderator()
    or app.is_wali_of_profile(wali_profile_id)
  );
create policy formal_introduction_settings_write on public.formal_introduction_settings
  for insert to authenticated with check (user_id = app.current_user_id());
create policy formal_introduction_settings_update on public.formal_introduction_settings
  for update to authenticated
  using (user_id = app.current_user_id()) with check (user_id = app.current_user_id());

alter table public.formal_introduction_requests enable row level security;
-- Visible only to the two members, the two walis, and moderators.
create policy formal_introduction_requests_select on public.formal_introduction_requests
  for select to authenticated
  using (
    initiator_id = app.current_user_id()
    or recipient_id = app.current_user_id()
    or app.is_moderator()
    or app.is_wali_of_profile(initiator_wali_profile_id)
    or app.is_wali_of_profile(recipient_wali_profile_id)
    or app.is_introduction_participant(id)
  );
-- Starting an introduction: the initiator must have a wali on file, the
-- recipient must be open to introductions, and no block may exist. It can
-- only be created in draft/submitted state and never pre-approved.
create policy formal_introduction_requests_insert on public.formal_introduction_requests
  for insert to authenticated
  with check (
    initiator_id = app.current_user_id()
    and status in ('draft','submitted')
    and approved_and_forwarded_at is null
    and app.is_account_active(app.current_user_id())
    and not app.is_restricted(app.current_user_id(), 'introductions_suspended')
    and not app.is_blocked_between(initiator_id, recipient_id)
    -- The recipient's settings row is private, so this must go through a
    -- definer helper rather than an inline subquery.
    and app.is_open_to_introductions(recipient_id)
    and exists (select 1 from public.formal_introduction_settings s2
                 where s2.user_id = app.current_user_id()
                   and s2.wali_profile_id is not null)
  );
-- Members may withdraw; walis may record their own side's decision.
-- Advancing to approved_and_forwarded is blocked by
-- trg_fir_guard_status (0014) unless app.forward_introduction() did it.
create policy formal_introduction_requests_update on public.formal_introduction_requests
  for update to authenticated
  using (
    initiator_id = app.current_user_id()
    or recipient_id = app.current_user_id()
    or app.is_moderator()
    or app.is_wali_of_profile(initiator_wali_profile_id)
    or app.is_wali_of_profile(recipient_wali_profile_id)
  )
  with check (
    initiator_id = app.current_user_id()
    or recipient_id = app.current_user_id()
    or app.is_moderator()
    or app.is_wali_of_profile(initiator_wali_profile_id)
    or app.is_wali_of_profile(recipient_wali_profile_id)
  );

alter table public.formal_introduction_participants enable row level security;
alter table public.formal_introduction_participants force row level security;
-- Participants see each other; nobody outside the introduction does.
create policy formal_introduction_participants_select on public.formal_introduction_participants
  for select to authenticated
  using (
    user_id = app.current_user_id()
    or app.is_moderator()
    -- definer helper: a policy on this table cannot query this table
    or app.is_introduction_participant(introduction_id)
  );
-- The initiator may attach their own wali/chaperone, never pre-authorized
-- for contact. contact_authorized_at is trigger-guarded (0014).
create policy formal_introduction_participants_insert on public.formal_introduction_participants
  for insert to authenticated
  with check (
    contact_authorized_at is null
    and app.is_introduction_principal(introduction_id)
  );
-- Revoking your own participation is allowed; authorization is not.
create policy formal_introduction_participants_update on public.formal_introduction_participants
  for update to authenticated
  using (user_id = app.current_user_id() or app.is_moderator())
  with check (user_id = app.current_user_id() or app.is_moderator());

-- =====================================================================
-- SECTION 8 -- verification and qualifications
-- =====================================================================

alter table public.user_verifications enable row level security;
alter table public.user_verifications force row level security;
-- A member sees their own verification history; moderators see all.
create policy user_verifications_select on public.user_verifications
  for select to authenticated
  using (user_id = app.current_user_id() or app.is_moderator());
-- A member may SUBMIT a request only, and only as pending with no
-- reviewer fields set. Self-declaring 'verified' violates this WITH CHECK.
create policy user_verifications_insert_request on public.user_verifications
  for insert to authenticated
  with check (
    user_id = app.current_user_id()
    and status = 'pending'
    and reviewed_by is null and reviewed_at is null
    and verified_at is null and revoked_at is null
  );
-- Only moderators decide. There is deliberately NO ordinary-user UPDATE
-- policy, so a self-promotion UPDATE matches zero rows.
create policy user_verifications_update_moderator on public.user_verifications
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());

alter table public.qualifications enable row level security;
alter table public.qualifications force row level security;
-- Verified public qualifications are visible; unverified claims are not
-- advertised to the community.
create policy qualifications_select on public.qualifications
  for select to authenticated
  using (
    user_id = app.current_user_id()
    or app.is_moderator()
    or (is_public and verified_at is not null and revoked_at is null)
  );
-- A member may claim a qualification but never mark it verified.
create policy qualifications_insert_self on public.qualifications
  for insert to authenticated
  with check (user_id = app.current_user_id()
              and verified_at is null and verified_by is null);
-- Editing your own claim is allowed; trg_qualifications_guard_verification
-- rejects any attempt to move verified_at / verified_by.
create policy qualifications_update_self on public.qualifications
  for update to authenticated
  using (user_id = app.current_user_id() or app.is_moderator())
  with check (user_id = app.current_user_id() or app.is_moderator());
create policy qualifications_delete_self on public.qualifications
  for delete to authenticated using (user_id = app.current_user_id());

-- =====================================================================
-- SECTION 9 -- moderation
-- =====================================================================

alter table public.reports enable row level security;
alter table public.reports force row level security;
-- A reporter sees their own report. The REPORTED user never does --
-- otherwise reporting becomes a retaliation trigger.
create policy reports_select_reporter_or_staff on public.reports
  for select to authenticated
  using ((reporter_id = app.current_user_id() and not is_anonymous) or app.is_moderator());
-- Anyone signed in may report, as themselves or anonymously.
create policy reports_insert_self on public.reports
  for insert to authenticated
  with check (
    (reporter_id = app.current_user_id() or (is_anonymous and reporter_id is null))
    and status = 'submitted'
    and triaged_at is null and resolved_at is null
  );
-- Only moderators move a report through triage.
create policy reports_update_moderator on public.reports
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());

alter table public.report_evidence enable row level security;
alter table public.report_evidence force row level security;
-- Evidence is moderator-readable, plus whoever submitted it.
create policy report_evidence_select on public.report_evidence
  for select to authenticated
  using (submitted_by = app.current_user_id() or app.is_moderator());
-- The reporter may attach evidence to their own report; moderators to any.
create policy report_evidence_insert on public.report_evidence
  for insert to authenticated
  with check (
    app.is_moderator()
    or (submitted_by = app.current_user_id()
        and exists (select 1 from public.reports r
                     where r.id = report_evidence.report_id
                       and r.reporter_id = app.current_user_id()))
  );
-- APPEND-ONLY: no UPDATE / DELETE policy, and the privilege is revoked so
-- an attempt raises instead of silently doing nothing.
revoke update, delete on public.report_evidence from authenticated, anon;

alter table public.moderation_cases enable row level security;
alter table public.moderation_cases force row level security;
-- Case files are staff-only.
create policy moderation_cases_select_moderator on public.moderation_cases
  for select to authenticated using (app.is_moderator());
create policy moderation_cases_insert_moderator on public.moderation_cases
  for insert to authenticated with check (app.is_moderator());
create policy moderation_cases_update_moderator on public.moderation_cases
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());

alter table public.moderation_actions enable row level security;
alter table public.moderation_actions force row level security;
-- A member can see what was done TO them: moderation is not secret.
create policy moderation_actions_select on public.moderation_actions
  for select to authenticated
  using (app.is_moderator() or target_user_id = app.current_user_id());
-- Only a moderator acts, and only under their own name.
create policy moderation_actions_insert_moderator on public.moderation_actions
  for insert to authenticated
  with check (app.is_moderator() and actor_id = app.current_user_id());
-- APPEND-ONLY: an action, once taken, is part of the permanent record.
revoke update, delete on public.moderation_actions from authenticated, anon;

alter table public.appeals enable row level security;
alter table public.appeals force row level security;
-- The appellant follows their own appeal; moderators review it.
create policy appeals_select on public.appeals
  for select to authenticated
  using (appellant_id = app.current_user_id() or app.is_moderator());
create policy appeals_insert_self on public.appeals
  for insert to authenticated
  with check (appellant_id = app.current_user_id()
              and status = 'submitted' and reviewer_id is null);
-- Only moderators (never the one who acted -- trg_appeals_reviewer_distinct)
-- may decide an appeal.
create policy appeals_update_moderator on public.appeals
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());

alter table public.blocks enable row level security;
-- Only the blocker sees the block. The blocked person is never told.
create policy blocks_select_blocker on public.blocks
  for select to authenticated
  using (blocker_id = app.current_user_id() or app.is_moderator());
create policy blocks_insert_self on public.blocks
  for insert to authenticated
  with check (blocker_id = app.current_user_id());
-- Lifting a block is the blocker's decision alone.
create policy blocks_update_blocker on public.blocks
  for update to authenticated
  using (blocker_id = app.current_user_id()) with check (blocker_id = app.current_user_id());

alter table public.restrictions enable row level security;
-- A member must be able to see why they are restricted.
create policy restrictions_select on public.restrictions
  for select to authenticated
  using (user_id = app.current_user_id() or app.is_moderator());
-- Restrictions are imposed by moderators only.
create policy restrictions_insert_moderator on public.restrictions
  for insert to authenticated with check (app.is_moderator());
create policy restrictions_update_moderator on public.restrictions
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());

alter table public.safety_incidents enable row level security;
alter table public.safety_incidents force row level security;
-- The most sensitive table on the platform. Staff only; the subject of an
-- incident record can never read it.
create policy safety_incidents_select_moderator on public.safety_incidents
  for select to authenticated using (app.is_moderator());
create policy safety_incidents_insert_moderator on public.safety_incidents
  for insert to authenticated with check (app.is_moderator());
create policy safety_incidents_update_moderator on public.safety_incidents
  for update to authenticated
  using (app.is_moderator()) with check (app.is_moderator());

-- =====================================================================
-- SECTION 10 -- giving
-- =====================================================================

alter table public.campaigns enable row level security;
-- Public campaigns are browsable; drafts belong to the organization.
create policy campaigns_select on public.campaigns
  for select to authenticated
  using (
    (is_public and deleted_at is null and status in ('active','paused','completed'))
    or app.is_org_member(organization_id)
    or app.is_moderator()
  );
create policy campaigns_select_anon on public.campaigns
  for select to anon
  using (is_public and deleted_at is null and status = 'active');
-- Only an admin of the owning organization may create a campaign, and it
-- can never be born zakat-eligible.
create policy campaigns_insert_org_admin on public.campaigns
  for insert to authenticated
  with check (app.is_org_admin(organization_id)
              and created_by = app.current_user_id()
              and zakat_eligible = false);
-- Editing is org-admin only. Flipping zakat_eligible through this path is
-- rejected by trg_campaigns_guard_zakat (0014).
create policy campaigns_update_org_admin on public.campaigns
  for update to authenticated
  using (app.is_org_admin(organization_id) or app.is_moderator())
  with check (app.is_org_admin(organization_id) or app.is_moderator());

alter table public.campaign_verifications enable row level security;
alter table public.campaign_verifications force row level security;
-- Attestations are visible to the owning org and to moderators; the
-- decision itself is never made by the org.
create policy campaign_verifications_select on public.campaign_verifications
  for select to authenticated
  using (
    app.is_moderator()
    or exists (select 1 from public.campaigns c
                where c.id = campaign_verifications.campaign_id
                  and app.is_org_member(c.organization_id))
  );
create policy campaign_verifications_insert on public.campaign_verifications
  for insert to authenticated
  with check (
    app.is_moderator()
    or (status = 'pending'
        and exists (select 1 from public.campaigns c
                     where c.id = campaign_verifications.campaign_id
                       and app.is_org_admin(c.organization_id)))
  );
-- Only moderators / scholars record an outcome.
create policy campaign_verifications_update_moderator on public.campaign_verifications
  for update to authenticated
  using (app.is_moderator() or app.is_scholar())
  with check (app.is_moderator() or app.is_scholar());

alter table public.donations enable row level security;
alter table public.donations force row level security;
-- A donor always sees their own record. The receiving organization sees
-- only NON-anonymous records: quiet sadaqah stays quiet.
create policy donations_select on public.donations
  for select to authenticated
  using (
    donor_id = app.current_user_id()
    or app.is_moderator()
    or (is_anonymous = false and (
          (organization_id is not null and app.is_org_admin(organization_id))
          or exists (select 1 from public.campaigns c
                      where c.id = donations.campaign_id
                        and app.is_org_admin(c.organization_id))))
  );
create policy donations_insert_self on public.donations
  for insert to authenticated
  with check (donor_id = app.current_user_id() and status in ('pledged','recorded'));
create policy donations_update on public.donations
  for update to authenticated
  using (
    donor_id = app.current_user_id()
    or app.is_moderator()
    or (organization_id is not null and app.is_org_admin(organization_id))
  )
  with check (
    donor_id = app.current_user_id()
    or app.is_moderator()
    or (organization_id is not null and app.is_org_admin(organization_id))
  );

-- =====================================================================
-- SECTION 11 -- notifications, impact, audit, consent
-- =====================================================================

alter table public.notifications enable row level security;
alter table public.notifications force row level security;
-- Strictly the recipient's own.
create policy notifications_select_own on public.notifications
  for select to authenticated using (user_id = app.current_user_id());
create policy notifications_update_own on public.notifications
  for update to authenticated
  using (user_id = app.current_user_id()) with check (user_id = app.current_user_id());
create policy notifications_delete_own on public.notifications
  for delete to authenticated using (user_id = app.current_user_id());
-- Notifications are produced server-side (SECURITY DEFINER / service_role);
-- members cannot fabricate one for themselves or anyone else.
revoke insert on public.notifications from authenticated, anon;

alter table public.private_impact_records enable row level security;
alter table public.private_impact_records force row level security;
-- Private to the member. Not readable by moderators, organizations, or
-- anyone else. There is no aggregate, no ranking, no export to others.
create policy private_impact_records_own on public.private_impact_records
  for all to authenticated
  using (user_id = app.current_user_id())
  with check (user_id = app.current_user_id());

alter table public.audit_logs enable row level security;
alter table public.audit_logs force row level security;
-- Anyone signed in may APPEND an audit row (the app writes them through
-- app.write_audit()). Reading is limited to staff and to the subject.
create policy audit_logs_insert_any on public.audit_logs
  for insert to authenticated with check (true);
create policy audit_logs_select_staff_or_subject on public.audit_logs
  for select to authenticated
  using (app.is_moderator() or subject_id = app.current_user_id());
-- APPEND-ONLY, ABSOLUTELY: no UPDATE or DELETE policy exists on this table
-- for any role, and the privileges are revoked so tampering raises an
-- error instead of silently affecting zero rows. This is what stops a
-- rogue moderator quietly rewriting history.
revoke update, delete on public.audit_logs from authenticated, anon;

alter table public.consent_records enable row level security;
alter table public.consent_records force row level security;
-- A consent receipt belongs to the person who gave it (or their guardian).
create policy consent_records_select on public.consent_records
  for select to authenticated
  using (user_id = app.current_user_id() or granted_by_guardian = app.current_user_id());
create policy consent_records_insert on public.consent_records
  for insert to authenticated
  with check (user_id = app.current_user_id() or granted_by_guardian = app.current_user_id());
-- Withdrawal is an update; the receipt itself is never deleted.
create policy consent_records_update on public.consent_records
  for update to authenticated
  using (user_id = app.current_user_id() or granted_by_guardian = app.current_user_id())
  with check (user_id = app.current_user_id() or granted_by_guardian = app.current_user_id());
revoke delete on public.consent_records from authenticated, anon;

-- =====================================================================
-- SECTION 12 -- safe public views
-- =====================================================================

-- Everything a non-involved member may know about a mutual-aid request.
-- The exact address lives in service_request_private_details and is not
-- reachable from here at all.
create view public.service_requests_public
with (security_invoker = true) as
select
  sr.id,
  sr.requester_id,
  sr.category_id,
  sr.community_id,
  sr.organization_id,
  sr.title,
  sr.description,
  sr.status,
  sr.urgency,
  sr.gender_policy,
  sr.approximate_area,
  sr.approximate_latitude,
  sr.approximate_longitude,
  sr.min_helper_verification,
  sr.needed_by,
  sr.is_anonymous,
  sr.created_at
from public.service_requests sr
where sr.deleted_at is null;

comment on view public.service_requests_public is
  'Approximate location only. There is no column here that could leak a doorstep.';

-- Opportunity listing without the precise meeting-point note.
create view public.volunteer_opportunities_public
with (security_invoker = true) as
select
  o.id, o.organization_id, o.community_id, o.created_by, o.category_id,
  o.title, o.description, o.gender_policy, o.is_youth_facing,
  o.requires_safeguarding_clearance, o.min_verification, o.volunteers_needed,
  o.starts_at, o.ends_at, o.approximate_location, o.is_published, o.created_at
from public.volunteer_opportunities o
where o.deleted_at is null;

grant select on public.service_requests_public to authenticated;
grant select on public.volunteer_opportunities_public to authenticated;
grant select on public.wali_profiles_redacted to authenticated;

-- =====================================================================
-- SECTION 13 -- assertion: no table in `public` may be left with RLS off.
-- =====================================================================
do $$
declare
  v_missing text;
begin
  select string_agg(c.relname, ', ')
    into v_missing
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind = 'r'
     and not c.relrowsecurity;
  if v_missing is not null then
    raise exception 'RLS is not enabled on: %', v_missing;
  end if;
end;
$$;
