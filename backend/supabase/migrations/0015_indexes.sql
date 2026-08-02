-- =====================================================================
-- Fi Sabilillah -- 0015_indexes.sql
--
-- Indexes fall into three groups:
--  1. Foreign keys -- Postgres does not index the referencing side, and
--     every ON DELETE CASCADE does a sequential scan without these.
--  2. RLS predicates -- policies run on EVERY row touched, so the columns
--     they filter on (owner ids, membership pairs, blocks) must be indexed
--     or the whole database degrades under policy evaluation.
--  3. Product queries -- listings, inboxes, moderation queues.
-- =====================================================================

-- --- identity ---------------------------------------------------------
create index idx_profiles_searchable on public.profiles (is_searchable, verification_level)
  where deleted_at is null;
create index idx_profiles_display_name_trgm on public.profiles using gin (display_name gin_trgm_ops);
create index idx_profiles_city on public.profiles (country_code, city) where deleted_at is null;

create index idx_user_settings_user on public.user_settings (user_id);
create index idx_user_safeguards_user on public.user_safeguards (user_id);
create index idx_user_safeguards_preset on public.user_safeguards (preset_id);

-- Hot path: app.has_role() runs inside almost every policy.
create index idx_user_roles_user_active on public.user_roles (user_id, role_id)
  where revoked_at is null;
create index idx_user_roles_role on public.user_roles (role_id);
create index idx_user_roles_granted_by on public.user_roles (granted_by);

create index idx_user_skills_user on public.user_skills (user_id);
create index idx_user_skills_skill_public on public.user_skills (skill_id) where is_public and is_offered;
create index idx_user_skills_endorsed_by on public.user_skills (endorsed_by);

-- --- organizations and communities ------------------------------------
create index idx_organizations_created_by on public.organizations (created_by);
create index idx_organizations_directory on public.organizations (country_code, city, org_kind)
  where deleted_at is null;

-- app.is_org_admin() / app.is_org_member() lookup.
create index idx_org_members_user_org on public.organization_members (user_id, organization_id, status);
create index idx_org_members_org on public.organization_members (organization_id, status, org_role);
create index idx_org_members_invited_by on public.organization_members (invited_by);
create index idx_org_members_cleared_by on public.organization_members (safeguarding_cleared_by);

create index idx_org_verifications_org on public.organization_verifications (organization_id, status);
create index idx_org_verifications_reviewed_by on public.organization_verifications (reviewed_by);
create index idx_org_verifications_submitted_by on public.organization_verifications (submitted_by);

create index idx_communities_org on public.communities (organization_id);
create index idx_communities_visibility on public.communities (visibility, country_code, city)
  where deleted_at is null;
create index idx_communities_created_by on public.communities (created_by);

create index idx_community_members_user on public.community_members (user_id, community_id, status);
create index idx_community_members_community on public.community_members (community_id, status);
create index idx_community_members_invited_by on public.community_members (invited_by);

create index idx_community_rules_community on public.community_rules (community_id, position)
  where is_active;
create index idx_community_rules_template on public.community_rules (template_id);
create index idx_community_rules_created_by on public.community_rules (created_by);

-- --- learning and volunteering ----------------------------------------
create index idx_learning_offerings_teacher on public.learning_offerings (teacher_id);
create index idx_learning_offerings_subject on public.learning_offerings (subject_id)
  where is_published and deleted_at is null;
create index idx_learning_offerings_org on public.learning_offerings (organization_id);
create index idx_learning_offerings_community on public.learning_offerings (community_id);
create index idx_learning_offerings_starts on public.learning_offerings (starts_at)
  where is_published and deleted_at is null;

create index idx_learning_enrollments_student on public.learning_enrollments (student_id, status);
create index idx_learning_enrollments_offering on public.learning_enrollments (offering_id, status);
create index idx_learning_enrollments_consent on public.learning_enrollments (guardian_consent_record_id);

create index idx_volunteer_opportunities_org on public.volunteer_opportunities (organization_id)
  where deleted_at is null;
create index idx_volunteer_opportunities_created_by on public.volunteer_opportunities (created_by);
create index idx_volunteer_opportunities_community on public.volunteer_opportunities (community_id);
create index idx_volunteer_opportunities_category on public.volunteer_opportunities (category_id);
create index idx_volunteer_opportunities_open on public.volunteer_opportunities (starts_at)
  where is_published and closed_at is null and deleted_at is null;

create index idx_volunteer_applications_volunteer on public.volunteer_applications (volunteer_id, status);
create index idx_volunteer_applications_opportunity on public.volunteer_applications (opportunity_id, status);
create index idx_volunteer_applications_reviewed_by on public.volunteer_applications (reviewed_by);

-- --- mutual aid, projects, commitments --------------------------------
create index idx_service_requests_requester on public.service_requests (requester_id, status);
create index idx_service_requests_open on public.service_requests (status, urgency, needed_by)
  where deleted_at is null;
create index idx_service_requests_helper on public.service_requests (assigned_helper_id);
create index idx_service_requests_category on public.service_requests (category_id);
create index idx_service_requests_community on public.service_requests (community_id);
create index idx_service_requests_organization on public.service_requests (organization_id);

create index idx_request_responses_request on public.request_responses (request_id, status);
create index idx_request_responses_responder on public.request_responses (responder_id);

create index idx_projects_lead on public.projects (lead_id);
create index idx_projects_org on public.projects (organization_id);
create index idx_projects_community on public.projects (community_id);
create index idx_projects_public on public.projects (status) where is_public and deleted_at is null;

create index idx_project_members_project on public.project_members (project_id, status);
create index idx_project_members_user on public.project_members (user_id, status);

create index idx_project_tasks_project on public.project_tasks (project_id, status, position);
create index idx_project_tasks_assignee on public.project_tasks (assignee_id) where status <> 'done';
create index idx_project_tasks_created_by on public.project_tasks (created_by);

create index idx_commitments_user on public.commitments (user_id, status);
create index idx_commitments_opportunity on public.commitments (opportunity_id);
create index idx_commitments_request on public.commitments (service_request_id);
create index idx_commitments_project on public.commitments (project_id);
create index idx_commitments_offering on public.commitments (offering_id);

create index idx_commitment_confirmations_commitment on public.commitment_confirmations (commitment_id);
create index idx_commitment_confirmations_by on public.commitment_confirmations (confirmed_by);
create index idx_commitment_confirmations_org on public.commitment_confirmations (organization_id);

create index idx_srpd_request on public.service_request_private_details (request_id);

-- --- messaging --------------------------------------------------------
create index idx_conversations_created_by on public.conversations (created_by);
create index idx_conversations_recent on public.conversations (last_message_at desc)
  where deleted_at is null;
create index idx_conversations_org on public.conversations (organization_id);
create index idx_conversations_community on public.conversations (community_id);

-- app.is_conversation_member() is the single hottest policy helper.
create index idx_conversation_members_user on public.conversation_members (user_id, conversation_id)
  where removed_at is null and left_at is null;
create index idx_conversation_members_conversation on public.conversation_members (conversation_id)
  where removed_at is null;
create index idx_conversation_members_added_by on public.conversation_members (added_by);

create index idx_conversation_purposes_conversation on public.conversation_purposes (conversation_id);
create index idx_conversation_purposes_kind on public.conversation_purposes (purpose_kind);
create index idx_conversation_purposes_introduction on public.conversation_purposes (introduction_id);
create index idx_conversation_purposes_declared_by on public.conversation_purposes (declared_by);

create index idx_messages_conversation_time on public.messages (conversation_id, created_at desc);
create index idx_messages_sender on public.messages (sender_id, created_at desc);
create index idx_messages_flagged on public.messages (flagged_at) where flagged_at is not null;
create index idx_messages_reply_to on public.messages (reply_to_id);

create index idx_message_redactions_message on public.message_redactions (message_id);
create index idx_message_redactions_conversation on public.message_redactions (conversation_id, created_at desc);
create index idx_message_redactions_sender on public.message_redactions (sender_id);

-- --- blocks: read by app.is_blocked_between() on nearly every policy ---
create index idx_blocks_blocker on public.blocks (blocker_id, blocked_id) where lifted_at is null;
create index idx_blocks_blocked on public.blocks (blocked_id, blocker_id) where lifted_at is null;

-- --- wali and introductions -------------------------------------------
create index idx_trusted_contacts_user on public.trusted_contacts (user_id, relationship)
  where revoked_at is null;
create index idx_trusted_contacts_contact_user on public.trusted_contacts (contact_user_id);

create index idx_wali_profiles_user on public.wali_profiles (user_id) where deleted_at is null;
create index idx_wali_profiles_wali_user on public.wali_profiles (wali_user_id);
create index idx_wali_profiles_masjid on public.wali_profiles (masjid_organization_id);

create index idx_fis_user on public.formal_introduction_settings (user_id);
create index idx_fis_open on public.formal_introduction_settings (is_open_to_introductions, visible_to)
  where is_open_to_introductions;
create index idx_fis_wali_profile on public.formal_introduction_settings (wali_profile_id);

create index idx_fir_initiator on public.formal_introduction_requests (initiator_id, status);
create index idx_fir_recipient on public.formal_introduction_requests (recipient_id, status);
create index idx_fir_initiator_wali on public.formal_introduction_requests (initiator_wali_profile_id);
create index idx_fir_recipient_wali on public.formal_introduction_requests (recipient_wali_profile_id);
create index idx_fir_case on public.formal_introduction_requests (moderator_case_id);
create index idx_fir_org on public.formal_introduction_requests (referred_by_organization_id);

create index idx_fip_introduction on public.formal_introduction_participants (introduction_id)
  where revoked_at is null;
create index idx_fip_user on public.formal_introduction_participants (user_id) where revoked_at is null;
create index idx_fip_authorized_wali on public.formal_introduction_participants (authorized_for_wali_profile_id);
create index idx_fip_authorized_by on public.formal_introduction_participants (authorized_by);

create index idx_wcd_introduction on public.wali_contact_disclosures (introduction_id);
create index idx_wcd_wali_profile on public.wali_contact_disclosures (wali_profile_id, disclosed_at desc);
create index idx_wcd_disclosed_to on public.wali_contact_disclosures (disclosed_to);

-- --- verification -----------------------------------------------------
create index idx_user_verifications_user on public.user_verifications (user_id, status);
create index idx_user_verifications_queue on public.user_verifications (submitted_at)
  where status in ('pending','under_review');
create index idx_user_verifications_reviewed_by on public.user_verifications (reviewed_by);
create index idx_user_verifications_revoked_by on public.user_verifications (revoked_by);
create index idx_user_verifications_org on public.user_verifications (vouching_organization_id);

create index idx_qualifications_user on public.qualifications (user_id);
create index idx_qualifications_type on public.qualifications (qualification_type_id);
create index idx_qualifications_verified on public.qualifications (verified_at)
  where verified_at is not null and revoked_at is null;
create index idx_qualifications_verified_by on public.qualifications (verified_by);

-- --- moderation -------------------------------------------------------
create index idx_reports_status on public.reports (status, severity, created_at desc);
create index idx_reports_reporter on public.reports (reporter_id);
create index idx_reports_reported_user on public.reports (reported_user_id);
create index idx_reports_case on public.reports (case_id);
create index idx_reports_message on public.reports (message_id);
create index idx_reports_conversation on public.reports (conversation_id);
create index idx_reports_organization on public.reports (organization_id);
create index idx_reports_community on public.reports (community_id);
create index idx_reports_service_request on public.reports (service_request_id);
create index idx_reports_introduction on public.reports (introduction_id);
create index idx_reports_campaign on public.reports (campaign_id);
create index idx_reports_triaged_by on public.reports (triaged_by);

create index idx_report_evidence_report on public.report_evidence (report_id);
create index idx_report_evidence_submitted_by on public.report_evidence (submitted_by);
create index idx_report_evidence_message on public.report_evidence (message_id);

create index idx_moderation_cases_status on public.moderation_cases (status, severity, opened_at desc);
create index idx_moderation_cases_subject on public.moderation_cases (subject_user_id);
create index idx_moderation_cases_assigned on public.moderation_cases (assigned_to) where closed_at is null;
create index idx_moderation_cases_org on public.moderation_cases (organization_id);
create index idx_moderation_cases_opened_by on public.moderation_cases (opened_by);
create index idx_moderation_cases_second_reviewer on public.moderation_cases (second_reviewer_id);

create index idx_moderation_actions_case on public.moderation_actions (case_id, created_at desc);
create index idx_moderation_actions_actor on public.moderation_actions (actor_id, created_at desc);
create index idx_moderation_actions_target_user on public.moderation_actions (target_user_id);
create index idx_moderation_actions_report on public.moderation_actions (report_id);
create index idx_moderation_actions_message on public.moderation_actions (target_message_id);
create index idx_moderation_actions_conversation on public.moderation_actions (target_conversation_id);
create index idx_moderation_actions_org on public.moderation_actions (target_organization_id);
create index idx_moderation_actions_campaign on public.moderation_actions (target_campaign_id);
create index idx_moderation_actions_introduction on public.moderation_actions (target_introduction_id);

create index idx_appeals_appellant on public.appeals (appellant_id, status);
create index idx_appeals_action on public.appeals (action_id);
create index idx_appeals_case on public.appeals (case_id);
create index idx_appeals_reviewer on public.appeals (reviewer_id);

-- app.is_restricted() / app.is_account_active() run on every write policy.
create index idx_restrictions_user_live on public.restrictions (user_id, restriction_type)
  where lifted_at is null;
create index idx_restrictions_case on public.restrictions (case_id);
create index idx_restrictions_action on public.restrictions (action_id);
create index idx_restrictions_imposed_by on public.restrictions (imposed_by);
create index idx_restrictions_lifted_by on public.restrictions (lifted_by);

create index idx_safety_incidents_case on public.safety_incidents (case_id);
create index idx_safety_incidents_severity on public.safety_incidents (severity, occurred_at desc)
  where closed_at is null;
create index idx_safety_incidents_involved on public.safety_incidents (involved_user_id);
create index idx_safety_incidents_org on public.safety_incidents (organization_id);
create index idx_safety_incidents_reported_by on public.safety_incidents (reported_by);

-- --- giving -----------------------------------------------------------
create index idx_campaigns_org on public.campaigns (organization_id, status);
create index idx_campaigns_public on public.campaigns (status, ends_on) where is_public and deleted_at is null;
create index idx_campaigns_project on public.campaigns (project_id);
create index idx_campaigns_created_by on public.campaigns (created_by);
create index idx_campaigns_zakat_attestation on public.campaigns (zakat_attestation_id);

create index idx_campaign_verifications_campaign on public.campaign_verifications (campaign_id, kind, status);
create index idx_campaign_verifications_attested_by on public.campaign_verifications (attested_by);
create index idx_campaign_verifications_org on public.campaign_verifications (attesting_organization_id);

create index idx_donations_donor on public.donations (donor_id, created_at desc);
create index idx_donations_campaign on public.donations (campaign_id, status);
create index idx_donations_org on public.donations (organization_id, status);

-- --- notifications, impact, audit, consent ----------------------------
create index idx_notifications_user_unread on public.notifications (user_id, created_at desc)
  where read_at is null and dismissed_at is null;
create index idx_notifications_user on public.notifications (user_id, created_at desc);

create index idx_private_impact_user on public.private_impact_records (user_id, occurred_on desc);
create index idx_private_impact_commitment on public.private_impact_records (commitment_id);

create index idx_audit_logs_entity on public.audit_logs (entity_type, entity_id, occurred_at desc);
create index idx_audit_logs_actor on public.audit_logs (actor_id, occurred_at desc);
create index idx_audit_logs_subject on public.audit_logs (subject_id, occurred_at desc);
create index idx_audit_logs_action on public.audit_logs (action, occurred_at desc);

create index idx_consent_records_user on public.consent_records (user_id, consent_type);
create index idx_consent_records_guardian on public.consent_records (granted_by_guardian);
