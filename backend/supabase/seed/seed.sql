-- =====================================================================
-- Fi Sabilillah -- seed/seed.sql
--
-- ENTIRELY FICTIONAL sample data.
--   * No real people, organizations, addresses or phone numbers.
--   * Phone numbers use the 555-01xx reserved range.
--   * Email domains are all *.example.test (RFC 6761 reserved TLD).
--   * Payments are disabled everywhere; the campaign is a placeholder.
--
-- Run as the migration owner (superuser / service role). The whole file is
-- one transaction because a conversation may not be COMMITTED without a
-- row in conversation_purposes.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- auth.users. On Supabase these come from the auth service; locally they
-- come from tests/00_bootstrap.sql. Seeding them here keeps the FK from
-- public.profiles satisfied in both environments.
-- ---------------------------------------------------------------------
insert into auth.users (id, email, email_confirmed_at) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'admin.rahma@example.test',      now()),
  ('aaaaaaaa-0000-4000-8000-000000000002', 'mod.bilal@example.test',        now()),
  ('aaaaaaaa-0000-4000-8000-000000000003', 'ustadh.idris@example.test',     now()),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'amina.tutor@example.test',      now()),
  ('bbbbbbbb-0000-4000-8000-000000000002', 'hafsa.newmuslim@example.test',  now()),
  ('bbbbbbbb-0000-4000-8000-000000000003', 'maryam.careers@example.test',   now()),
  ('bbbbbbbb-0000-4000-8000-000000000004', 'khadija.elder@example.test',    now()),
  ('bbbbbbbb-0000-4000-8000-000000000005', 'nadia.relief@example.test',     now()),
  ('cccccccc-0000-4000-8000-000000000001', 'yusuf.sparks@example.test',     now()),
  ('cccccccc-0000-4000-8000-000000000002', 'tariq.volunteer@example.test',  now()),
  ('cccccccc-0000-4000-8000-000000000003', 'zayd.masjid@example.test',      now()),
  ('cccccccc-0000-4000-8000-000000000004', 'ibrahim.newcomer@example.test', now()),
  ('dddddddd-0000-4000-8000-000000000001', 'rashid.blocked@example.test',   now()),
  ('dddddddd-0000-4000-8000-000000000002', 'faisal.restricted@example.test',now()),
  ('dddddddd-0000-4000-8000-000000000003', 'anas.departed@example.test',    now()),
  ('eeeeeeee-0000-4000-8000-000000000001', 'wali.amir@example.test',        now()),
  ('eeeeeeee-0000-4000-8000-000000000002', 'wali.saleh@example.test',       now());

-- ---------------------------------------------------------------------
-- Lookup vocabulary
-- ---------------------------------------------------------------------
insert into public.roles (key, display_name, description, is_privileged) values
  ('member',         'Member',         'An ordinary member of the community.',                 false),
  ('organizer',      'Organizer',      'May coordinate projects and opportunities.',           false),
  ('scholar',        'Scholar',        'Listed teacher: may attest qualifications and zakat.', true),
  ('moderator',      'Moderator',      'Safety team: handles reports and moderation cases.',   true),
  ('platform_admin', 'Platform Admin', 'Full administrative authority, including role grants.',true),
  ('safeguarding_lead','Safeguarding Lead','Handles youth and vulnerable-adult incidents.',    true);

insert into public.skills (slug, name, category, description) values
  ('arabic-language',      'Arabic Language',        'teaching',   'Classical and conversational Arabic.'),
  ('quran-recitation',     'Quran Recitation',       'teaching',   'Tajweed and reading support.'),
  ('mentoring',            'Mentoring',              'pastoral',   'Walking alongside someone new.'),
  ('electrical-trade',     'Electrical Trade',       'trade',      'Domestic and light commercial electrics.'),
  ('resume-writing',       'CV and Resume Writing',  'employment', 'Helping people present their work.'),
  ('driving',              'Driving',                'practical',  'Licensed driver, own vehicle.'),
  ('it-support',           'IT Support',             'technical',  'Hardware refurbishment and setup.'),
  ('cooking-at-scale',     'Cooking at Scale',       'practical',  'Catering for community meals.'),
  ('bookkeeping',          'Bookkeeping',            'admin',      'Small-charity accounts.');

insert into public.service_categories (slug, name, description, requires_safeguarding) values
  ('transport',        'Transport and Lifts',    'Getting someone to an appointment or the masjid.', false),
  ('food-support',     'Food Support',           'Meals, parcels and distribution.',                 false),
  ('household-repair', 'Household Repair',       'Small practical repairs at home.',                 false),
  ('employment-help',  'Employment Help',        'CVs, applications, interview practice.',           false),
  ('new-muslim-support','New Muslim Support',    'Companionship and practical help for reverts.',    true),
  ('youth-support',    'Youth Support',          'Anything involving under-18s.',                    true);

insert into public.learning_subjects (slug, name, description, requires_scholarly_qualification) values
  ('arabic',            'Arabic',                'Language of the Quran.',                    false),
  ('quran-reading',     'Quran Reading',         'Tajweed and fluency support.',              false),
  ('fiqh-basics',       'Basics of Fiqh',        'Everyday rulings for practice.',            true),
  ('seerah',            'Seerah',                'The life of the Prophet (peace be upon him).', false),
  ('new-muslim-basics', 'New Muslim Essentials', 'Salah, wudu, and first steps.',             false);

insert into public.community_rule_templates (slug, title, body) values
  ('purpose-only',   'Keep it to the purpose',
     'Use each channel only for the purpose it was opened for. If the need changes, open a new one.'),
  ('adab',           'Speak with adab',
     'Assume the best of one another. No mockery, no backbiting, no raised voices in text.'),
  ('no-solicitation','No solicitation',
     'Do not use community channels to sell, recruit or fundraise without the organizers'' agreement.'),
  ('safeguarding',   'Protecting young people',
     'Adults do not contact under-18s privately. All youth activity happens in supervised, recorded settings.');

insert into public.qualification_types (slug, name, description) values
  ('ijazah',            'Ijazah',                 'A chain-of-transmission licence to teach.'),
  ('teaching-cert',     'Teaching Certificate',   'A formal teaching qualification.'),
  ('dbs-check',         'Background Check',       'A completed criminal-records check for youth work.'),
  ('trade-licence',     'Trade Licence',          'A licence to practise a regulated trade.'),
  ('first-aid',         'First Aid',              'Current first-aid certification.');

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
     '{"is_minor_supervised":true,"require_wali_for_introductions":true}'::jsonb, false);

-- ---------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------
insert into public.profiles (id, display_name, contact_email, gender, city, country_code, bio, year_of_birth, accepted_covenant_at) values
  ('aaaaaaaa-0000-4000-8000-000000000001','Rahma A.','admin.rahma@example.test','female','Northgate','GB',
     'Platform administration. Contact through the safety desk.',1985, now()),
  ('aaaaaaaa-0000-4000-8000-000000000002','Bilal M.','mod.bilal@example.test','male','Northgate','GB',
     'Volunteer moderator on the safety team.',1988, now()),
  ('aaaaaaaa-0000-4000-8000-000000000003','Ustadh Idris','ustadh.idris@example.test','male','Northgate','GB',
     'Teaches Quran reading at the community trust. Listed scholar.',1972, now()),
  ('bbbbbbbb-0000-4000-8000-000000000001','Amina S.','amina.tutor@example.test','female','Northgate','GB',
     'Arabic tutor. Teaching sisters and children only.',1990, now()),
  ('bbbbbbbb-0000-4000-8000-000000000002','Hafsa R.','hafsa.newmuslim@example.test','female','Riverside','GB',
     'Two years in the deen, still learning. Grateful for the company.',1996, now()),
  ('bbbbbbbb-0000-4000-8000-000000000003','Maryam T.','maryam.careers@example.test','female','Northgate','GB',
     'Careers adviser. Happy to look over a CV for anyone job-hunting.',1991, now()),
  ('bbbbbbbb-0000-4000-8000-000000000004','Khadija bint N.','khadija.elder@example.test','female','Riverside','GB',
     'Retired. Grateful for lifts to appointments.',1948, now()),
  ('bbbbbbbb-0000-4000-8000-000000000005','Nadia H.','nadia.relief@example.test','female','Eastbrook','GB',
     'Coordinates the relief circle food runs.',1987, now()),
  ('cccccccc-0000-4000-8000-000000000001','Yusuf A.','yusuf.sparks@example.test','male','Northgate','GB',
     'Electrician. Takes on apprentices from the community.',1993, now()),
  ('cccccccc-0000-4000-8000-000000000002','Tariq D.','tariq.volunteer@example.test','male','Riverside','GB',
     'Drives, lifts, cooks. Whatever is needed on the day.',1994, now()),
  ('cccccccc-0000-4000-8000-000000000003','Zayd K.','zayd.masjid@example.test','male','Northgate','GB',
     'Youth and volunteering coordinator at the masjid trust.',1986, now()),
  ('cccccccc-0000-4000-8000-000000000004','Ibrahim Q.','ibrahim.newcomer@example.test','male','Eastbrook','GB',
     'New to the area, looking for a halaqa.',1999, now()),
  ('dddddddd-0000-4000-8000-000000000001','Rashid W.','rashid.blocked@example.test','male','Northgate','GB',
     'Account under review.',1992, now()),
  ('dddddddd-0000-4000-8000-000000000002','Faisal B.','faisal.restricted@example.test','male','Riverside','GB',
     'Account restricted following a moderation case.',1990, now()),
  ('eeeeeeee-0000-4000-8000-000000000001','Amir (wali)','wali.amir@example.test','male','Northgate','GB',
     'Father of Yusuf A. Registered as his wali.',1962, now()),
  ('eeeeeeee-0000-4000-8000-000000000002','Saleh (wali)','wali.saleh@example.test','male','Riverside','GB',
     'Appointed guardian for Hafsa R. through the masjid.',1965, now());

-- A member who has left. Soft-deleted: their evidence and audit trail stay.
insert into public.profiles (id, display_name, contact_email, gender, city, country_code, deleted_at) values
  ('dddddddd-0000-4000-8000-000000000003','Anas L.','anas.departed@example.test','male','Northgate','GB', now() - interval '3 days');

insert into public.user_settings (user_id, show_city_publicly, preferred_gender_policy)
select id, true, case when gender = 'female' then 'sisters_only'::public.gender_policy
                      else 'any'::public.gender_policy end
  from public.profiles;

insert into public.user_safeguards (user_id, preset_id, gender_interaction_policy, min_counterparty_verification)
select p.id,
       (select id from public.safeguard_presets where slug = 'balanced'),
       case when p.gender = 'female' then 'sisters_only'::public.gender_policy else 'any'::public.gender_policy end,
       'basic'::public.verification_level
  from public.profiles p;

-- ---------------------------------------------------------------------
-- Platform roles
-- ---------------------------------------------------------------------
insert into public.user_roles (user_id, role_id, granted_by, reason)
select 'aaaaaaaa-0000-4000-8000-000000000001', id,
       'aaaaaaaa-0000-4000-8000-000000000002', 'Founding administrator.'
  from public.roles where key = 'platform_admin';

insert into public.user_roles (user_id, role_id, granted_by, reason)
select 'aaaaaaaa-0000-4000-8000-000000000002', id,
       'aaaaaaaa-0000-4000-8000-000000000001', 'Safety team.'
  from public.roles where key = 'moderator';

insert into public.user_roles (user_id, role_id, granted_by, reason)
select 'aaaaaaaa-0000-4000-8000-000000000003', id,
       'aaaaaaaa-0000-4000-8000-000000000001', 'Listed teacher at the community trust.'
  from public.roles where key = 'scholar';

insert into public.user_roles (user_id, role_id, granted_by, reason)
select 'cccccccc-0000-4000-8000-000000000003', id,
       'aaaaaaaa-0000-4000-8000-000000000001', 'Coordinates masjid volunteering.'
  from public.roles where key = 'organizer';

-- ---------------------------------------------------------------------
-- Skills held
-- ---------------------------------------------------------------------
insert into public.user_skills (user_id, skill_id, proficiency, years_experience)
select 'bbbbbbbb-0000-4000-8000-000000000001', id, 'advanced', 8 from public.skills where slug='arabic-language';
insert into public.user_skills (user_id, skill_id, proficiency, years_experience)
select 'aaaaaaaa-0000-4000-8000-000000000003', id, 'professional', 25 from public.skills where slug='quran-recitation';
insert into public.user_skills (user_id, skill_id, proficiency, years_experience)
select 'cccccccc-0000-4000-8000-000000000001', id, 'professional', 12 from public.skills where slug='electrical-trade';
insert into public.user_skills (user_id, skill_id, proficiency, years_experience)
select 'bbbbbbbb-0000-4000-8000-000000000003', id, 'advanced', 9 from public.skills where slug='resume-writing';
insert into public.user_skills (user_id, skill_id, proficiency, years_experience)
select 'cccccccc-0000-4000-8000-000000000002', id, 'intermediate', 4 from public.skills where slug='driving';
insert into public.user_skills (user_id, skill_id, proficiency, years_experience)
select 'cccccccc-0000-4000-8000-000000000004', id, 'intermediate', 3 from public.skills where slug='it-support';
insert into public.user_skills (user_id, skill_id, proficiency, years_experience)
select 'bbbbbbbb-0000-4000-8000-000000000005', id, 'advanced', 6 from public.skills where slug='cooking-at-scale';

-- ---------------------------------------------------------------------
-- Organizations
-- ---------------------------------------------------------------------
insert into public.organizations (id, slug, name, legal_name, org_kind, description,
                                  public_email, public_phone, city, country_code, created_by,
                                  safeguarding_policy_url)
values
  ('f0000000-0000-4000-8000-000000000001','masjid-al-furqan-trust','Masjid Al-Furqan Community Trust',
   'Al-Furqan Community Trust (fictional)','masjid',
   'A neighbourhood masjid running weekend classes, a youth programme and a food run.',
   'salam@alfurqan.example.test','555-0110','Northgate','GB',
   'cccccccc-0000-4000-8000-000000000003','https://alfurqan.example.test/safeguarding'),
  ('f0000000-0000-4000-8000-000000000002','sabeel-relief-circle','Sabeel Relief Circle',
   'Sabeel Relief Circle (fictional)','relief_agency',
   'A small volunteer-run circle distributing food parcels twice a month.',
   'hello@sabeelrelief.example.test','555-0121','Eastbrook','GB',
   'bbbbbbbb-0000-4000-8000-000000000005', null);

insert into public.organization_members (organization_id, user_id, org_role, status, title, joined_at, safeguarding_cleared_at, safeguarding_cleared_by) values
  ('f0000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003','owner','active','Volunteering coordinator', now(), now(), 'aaaaaaaa-0000-4000-8000-000000000001'),
  ('f0000000-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000003','admin','active','Teacher', now(), now(), 'cccccccc-0000-4000-8000-000000000003'),
  ('f0000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000001','member','active','Arabic tutor', now(), now(), 'cccccccc-0000-4000-8000-000000000003'),
  ('f0000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000002','member','active','Volunteer driver', now(), null, null),
  ('f0000000-0000-4000-8000-000000000002','bbbbbbbb-0000-4000-8000-000000000005','owner','active','Coordinator', now(), null, null),
  ('f0000000-0000-4000-8000-000000000002','cccccccc-0000-4000-8000-000000000002','member','active','Volunteer', now(), null, null);

-- The masjid trust is verified; the relief circle has a pending submission.
insert into public.organization_verifications
  (organization_id, level, status, method, evidence_note, submitted_by, reviewed_by, reviewed_at)
values
  ('f0000000-0000-4000-8000-000000000001','org_verified','verified','document_review',
   'Registration documents and two community references reviewed at the safety desk.',
   'cccccccc-0000-4000-8000-000000000003','aaaaaaaa-0000-4000-8000-000000000002', now()),
  ('f0000000-0000-4000-8000-000000000002','basic','pending','document_review',
   'Awaiting a second community reference.',
   'bbbbbbbb-0000-4000-8000-000000000005', null, null);

-- ---------------------------------------------------------------------
-- Communities
-- ---------------------------------------------------------------------
insert into public.communities (id, slug, name, purpose, organization_id, visibility, city, country_code, created_by) values
  ('f1000000-0000-4000-8000-000000000001','northgate-neighbours','Northgate Neighbours',
   'Practical mutual aid between neighbours: lifts, meals, small repairs.',
   'f0000000-0000-4000-8000-000000000001','public','Northgate','GB','cccccccc-0000-4000-8000-000000000003'),
  ('f1000000-0000-4000-8000-000000000002','new-muslim-companions','New Muslim Companions',
   'Companionship and steady, unhurried learning for people new to Islam.',
   'f0000000-0000-4000-8000-000000000001','invite_only','Northgate','GB','aaaaaaaa-0000-4000-8000-000000000003');

insert into public.community_members (community_id, user_id, status, is_moderator, joined_at) values
  ('f1000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003','active', true,  now()),
  ('f1000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000002','active', false, now()),
  ('f1000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000004','active', false, now()),
  ('f1000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000003','active', false, now()),
  ('f1000000-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000003','active', true,  now()),
  ('f1000000-0000-4000-8000-000000000002','bbbbbbbb-0000-4000-8000-000000000002','active', false, now()),
  ('f1000000-0000-4000-8000-000000000002','bbbbbbbb-0000-4000-8000-000000000001','active', false, now());

insert into public.community_rules (community_id, template_id, position, title, body, created_by)
select 'f1000000-0000-4000-8000-000000000001', t.id, 1, t.title, t.body, 'cccccccc-0000-4000-8000-000000000003'
  from public.community_rule_templates t where t.slug = 'purpose-only';
insert into public.community_rules (community_id, template_id, position, title, body, created_by)
select 'f1000000-0000-4000-8000-000000000001', t.id, 2, t.title, t.body, 'cccccccc-0000-4000-8000-000000000003'
  from public.community_rule_templates t where t.slug = 'adab';
insert into public.community_rules (community_id, template_id, position, title, body, created_by)
select 'f1000000-0000-4000-8000-000000000002', t.id, 1, t.title, t.body, 'aaaaaaaa-0000-4000-8000-000000000003'
  from public.community_rule_templates t where t.slug = 'safeguarding';

-- ---------------------------------------------------------------------
-- Learning offerings
-- ---------------------------------------------------------------------
insert into public.learning_offerings
  (id, teacher_id, organization_id, community_id, subject_id, title, description, format,
   gender_policy, capacity, starts_at, recurrence_note, meeting_location, is_published)
select 'f2000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000001',
       'f0000000-0000-4000-8000-000000000001', null, s.id,
       'Arabic for sisters: reading from zero',
       'Eight weeks of alphabet, joining letters and simple sentences. Small group, sisters only.',
       'in_person','sisters_only',8, now() + interval '9 days',
       'Saturdays after Asr','Masjid Al-Furqan, upstairs classroom', true
  from public.learning_subjects s where s.slug = 'arabic';

insert into public.learning_offerings
  (id, teacher_id, organization_id, community_id, subject_id, title, description, format,
   gender_policy, capacity, starts_at, recurrence_note, is_published)
select 'f2000000-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000003',
       'f0000000-0000-4000-8000-000000000001', null, s.id,
       'Quran reading support (one-to-one, recorded)',
       'Twenty minutes a week to steady your recitation. Sessions are recorded and a coordinator has access.',
       'online','any', 12, now() + interval '4 days', 'Weekday evenings by arrangement', true
  from public.learning_subjects s where s.slug = 'quran-reading';

insert into public.learning_offerings
  (id, teacher_id, organization_id, community_id, subject_id, title, description, format,
   gender_policy, capacity, starts_at, is_published)
select 'f2000000-0000-4000-8000-000000000003','aaaaaaaa-0000-4000-8000-000000000003',
       'f0000000-0000-4000-8000-000000000001','f1000000-0000-4000-8000-000000000002', s.id,
       'New Muslim essentials: the first forty days',
       'Wudu, salah, and what to say when people ask. No question is too basic. Companions are paired same-gender.',
       'hybrid','any', 10, now() + interval '2 days', true
  from public.learning_subjects s where s.slug = 'new-muslim-basics';

insert into public.learning_enrollments (offering_id, student_id, status, motivation) values
  ('f2000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000002','enrolled',
   'I want to read the Quran without transliteration.'),
  ('f2000000-0000-4000-8000-000000000003','bbbbbbbb-0000-4000-8000-000000000002','enrolled',
   'Still nervous about salah in company.'),
  ('f2000000-0000-4000-8000-000000000002','cccccccc-0000-4000-8000-000000000004','requested',
   'My recitation has got rusty.');

-- ---------------------------------------------------------------------
-- Volunteering
-- ---------------------------------------------------------------------
insert into public.volunteer_opportunities
  (id, organization_id, community_id, created_by, category_id, title, description, gender_policy,
   is_youth_facing, requires_safeguarding_clearance, min_verification, volunteers_needed,
   starts_at, approximate_location, exact_location_note, is_published)
select 'f3000000-0000-4000-8000-000000000001','f0000000-0000-4000-8000-000000000002',
       null,'bbbbbbbb-0000-4000-8000-000000000005', c.id,
       'Food parcel packing and distribution',
       'Two hours packing, two hours delivering. Bring a trolley if you have one.',
       'any', false, false, 'basic', 8, now() + interval '6 days',
       'Eastbrook, near the community hall', 'Unit 4, rear entrance. Ask for the duty coordinator.', true
  from public.service_categories c where c.slug = 'food-support';

insert into public.volunteer_opportunities
  (id, organization_id, created_by, category_id, title, description, gender_policy,
   min_verification, volunteers_needed, starts_at, approximate_location, is_published)
select 'f3000000-0000-4000-8000-000000000002','f0000000-0000-4000-8000-000000000001',
       'cccccccc-0000-4000-8000-000000000003', null,
       'Monthly masjid deep clean',
       'Carpets, wudu area, shoe racks and windows. Refreshments provided afterwards.',
       'any','basic', 12, now() + interval '11 days', 'Masjid Al-Furqan, Northgate', true;

insert into public.volunteer_opportunities
  (id, organization_id, created_by, category_id, title, description, gender_policy,
   min_verification, volunteers_needed, starts_at, approximate_location, is_published)
select 'f3000000-0000-4000-8000-000000000003','f0000000-0000-4000-8000-000000000001',
       'cccccccc-0000-4000-8000-000000000003', c.id,
       'Transport rota for elders',
       'Driving elders to medical appointments and to Jumuah. Own vehicle and insurance required.',
       'any','community_vouched', 6, now() + interval '3 days', 'Riverside and Northgate', true
  from public.service_categories c where c.slug = 'transport';

-- Youth-facing work: the schema refuses to publish this without
-- requires_safeguarding_clearance = true.
insert into public.volunteer_opportunities
  (id, organization_id, created_by, category_id, title, description, gender_policy,
   is_youth_facing, requires_safeguarding_clearance, min_verification, volunteers_needed,
   starts_at, approximate_location, is_published)
select 'f3000000-0000-4000-8000-000000000004','f0000000-0000-4000-8000-000000000001',
       'cccccccc-0000-4000-8000-000000000003', c.id,
       'Saturday youth programme helpers (cleared adults only)',
       'Sports, homework club and a short halaqa for 11-15s. Two cleared adults present at all times; '
       || 'no private contact with participants, ever.',
       'any', true, true, 'org_verified', 4, now() + interval '5 days',
       'Masjid Al-Furqan, community hall', true
  from public.service_categories c where c.slug = 'youth-support';

insert into public.volunteer_applications (opportunity_id, volunteer_id, status, message, reviewed_by, reviewed_at, decided_at) values
  ('f3000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000002','accepted',
   'I can do both shifts and I have a van.','bbbbbbbb-0000-4000-8000-000000000005', now(), now()),
  ('f3000000-0000-4000-8000-000000000003','cccccccc-0000-4000-8000-000000000002','accepted',
   'Happy to take the Riverside runs.','cccccccc-0000-4000-8000-000000000003', now(), now()),
  ('f3000000-0000-4000-8000-000000000002','cccccccc-0000-4000-8000-000000000004','submitted',
   'New to the area, would like to help and meet people.', null, null, null);

-- ---------------------------------------------------------------------
-- Mutual aid. Note the location split: the request carries only an
-- approximate area; the doorstep lives in the protected child table.
-- ---------------------------------------------------------------------
insert into public.service_requests
  (id, requester_id, category_id, community_id, title, description, status, urgency,
   gender_policy, approximate_area, approximate_latitude, approximate_longitude,
   min_helper_verification, needed_by, assigned_helper_id, helper_accepted_at)
select 'f4000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000004', c.id,
       'f1000000-0000-4000-8000-000000000001',
       'Lift to a hospital appointment on Tuesday morning',
       'Appointment at 09:40, back home by about 11:30. I use a walking frame.',
       'matched','normal','any','Riverside, near the old library', 53.4100, -2.9800,
       'community_vouched', now() + interval '5 days',
       'cccccccc-0000-4000-8000-000000000002', now() - interval '1 day'
  from public.service_categories c where c.slug = 'transport';

insert into public.service_request_private_details
  (request_id, exact_address, latitude, longitude, access_notes, contact_phone, household_notes)
values
  ('f4000000-0000-4000-8000-000000000001',
   'Flat 3B, 14 Willowmead Court, Riverside RV1 9ZZ (fictional address)',
   53.412345, -2.981234,
   'Buzzer is broken. Please knock loudly on the ground-floor window.',
   '555-0133',
   'Elderly resident, hard of hearing. Please allow extra time.');

insert into public.service_requests
  (id, requester_id, category_id, community_id, title, description, status, urgency,
   gender_policy, approximate_area, min_helper_verification, needed_by)
select 'f4000000-0000-4000-8000-000000000002','bbbbbbbb-0000-4000-8000-000000000002', c.id,
       'f1000000-0000-4000-8000-000000000001',
       'Help rewriting my CV after a career break',
       'I have been out of work for three years and I do not know how to explain the gap.',
       'open','normal','sisters_only','Riverside','basic', now() + interval '14 days'
  from public.service_categories c where c.slug = 'employment-help';

insert into public.request_responses (request_id, responder_id, message, status, accepted_at) values
  ('f4000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000002',
   'I can take you and wait. My sister will travel with us.','accepted', now() - interval '1 day'),
  ('f4000000-0000-4000-8000-000000000002','bbbbbbbb-0000-4000-8000-000000000003',
   'Send it over whenever you are ready. A career break is not a problem to explain.','submitted', null);

-- ---------------------------------------------------------------------
-- A community technology project
-- ---------------------------------------------------------------------
insert into public.projects
  (id, organization_id, community_id, lead_id, slug, name, summary, intention_note, status,
   starts_on, target_end_on, is_public)
values
  ('f5000000-0000-4000-8000-000000000001','f0000000-0000-4000-8000-000000000001',
   'f1000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003',
   'refurbished-laptops','Refurbished laptops for students',
   'Collect donated laptops, wipe and rebuild them, and pass them to families who need one for schoolwork.',
   'So that no child in the community falls behind for want of a machine.',
   'active', current_date - 20, current_date + 60, true);

insert into public.project_members (project_id, user_id, project_role, status) values
  ('f5000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003','lead','active'),
  ('f5000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000004','contributor','active'),
  ('f5000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000002','contributor','active'),
  ('f5000000-0000-4000-8000-000000000001','dddddddd-0000-4000-8000-000000000002','contributor','active'),
  ('f5000000-0000-4000-8000-000000000001','dddddddd-0000-4000-8000-000000000003','contributor','removed');

insert into public.project_tasks (project_id, created_by, assignee_id, title, detail, status, due_on, position) values
  ('f5000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003','cccccccc-0000-4000-8000-000000000004',
   'Wipe and reimage the first eight machines','Certified wipe, then a clean install and a basic office suite.','in_progress', current_date + 7, 1),
  ('f5000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003','cccccccc-0000-4000-8000-000000000002',
   'Collect four donations from Eastbrook','Two are ready now; the others need a call first.','todo', current_date + 10, 2),
  ('f5000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003', null,
   'Agree the handover criteria with the school liaison','Written criteria so allocation is not ad hoc.','todo', current_date + 14, 3);

-- ---------------------------------------------------------------------
-- Commitments (confirmed by someone else, never self-declared)
-- ---------------------------------------------------------------------
insert into public.commitments (id, user_id, status, summary, opportunity_id, hours_pledged, hours_delivered, due_at, fulfilled_at) values
  ('f6000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000002','fulfilled',
   'Packing and delivering food parcels, Eastbrook run.',
   'f3000000-0000-4000-8000-000000000001', 4, 4.5, now() - interval '7 days', now() - interval '7 days');

insert into public.commitments (id, user_id, status, summary, project_id, hours_pledged, due_at) values
  ('f6000000-0000-4000-8000-000000000002','cccccccc-0000-4000-8000-000000000004','in_progress',
   'Reimaging laptops for the student project.',
   'f5000000-0000-4000-8000-000000000001', 10, now() + interval '10 days');

insert into public.commitment_confirmations (commitment_id, confirmed_by, organization_id, hours_confirmed, note) values
  ('f6000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000005',
   'f0000000-0000-4000-8000-000000000002', 4.5, 'Stayed to finish the last three drops. Jazak Allahu khayran.');

insert into public.private_impact_records (user_id, occurred_on, category, summary, reflection, hours, commitment_id) values
  ('cccccccc-0000-4000-8000-000000000002', current_date - 7, 'service',
   'Food run, Eastbrook.','Kept it between me and Allah. Nobody needs to see this.', 4.5,
   'f6000000-0000-4000-8000-000000000001');

-- ---------------------------------------------------------------------
-- Conversations. Every one has a declared purpose -- the schema will not
-- commit a conversation without one.
-- ---------------------------------------------------------------------
insert into public.conversations (id, created_by, title, is_group, organization_id) values
  ('f7000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000001','Arabic class logistics', true, 'f0000000-0000-4000-8000-000000000001'),
  ('f7000000-0000-4000-8000-000000000002','cccccccc-0000-4000-8000-000000000003','Transport rota', true, 'f0000000-0000-4000-8000-000000000001'),
  ('f7000000-0000-4000-8000-000000000003','bbbbbbbb-0000-4000-8000-000000000004','Tuesday lift', false, null),
  ('f7000000-0000-4000-8000-000000000004','bbbbbbbb-0000-4000-8000-000000000003','CV help offer', false, null),
  ('f7000000-0000-4000-8000-000000000005','cccccccc-0000-4000-8000-000000000003','Laptop project', true, 'f0000000-0000-4000-8000-000000000001');

insert into public.conversation_purposes
  (conversation_id, purpose_kind, purpose_statement, offering_id, service_request_id, opportunity_id, project_id, declared_by)
values
  ('f7000000-0000-4000-8000-000000000001','learning_session',
   'Coordinating the Saturday Arabic class for sisters.',
   'f2000000-0000-4000-8000-000000000001', null, null, null,'bbbbbbbb-0000-4000-8000-000000000001'),
  ('f7000000-0000-4000-8000-000000000002','volunteering_coordination',
   'Organising the weekly transport rota for elders.',
   null, null,'f3000000-0000-4000-8000-000000000003', null,'cccccccc-0000-4000-8000-000000000003'),
  ('f7000000-0000-4000-8000-000000000003','service_request',
   'Arranging the hospital lift on Tuesday morning.',
   null,'f4000000-0000-4000-8000-000000000001', null, null,'bbbbbbbb-0000-4000-8000-000000000004'),
  ('f7000000-0000-4000-8000-000000000004','mutual_aid',
   'Offering CV help in response to a posted request.',
   null,'f4000000-0000-4000-8000-000000000002', null, null,'bbbbbbbb-0000-4000-8000-000000000003'),
  ('f7000000-0000-4000-8000-000000000005','project_work',
   'Working channel for the refurbished laptops project.',
   null, null, null,'f5000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000003');

insert into public.conversation_members (conversation_id, user_id, member_role, can_write) values
  ('f7000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000001','host', true),
  ('f7000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000002','participant', true),
  ('f7000000-0000-4000-8000-000000000002','cccccccc-0000-4000-8000-000000000003','host', true),
  ('f7000000-0000-4000-8000-000000000002','cccccccc-0000-4000-8000-000000000002','participant', true),
  ('f7000000-0000-4000-8000-000000000003','bbbbbbbb-0000-4000-8000-000000000004','host', true),
  ('f7000000-0000-4000-8000-000000000003','cccccccc-0000-4000-8000-000000000002','participant', true),
  -- A channel that predates a block: Maryam later blocked Rashid.
  ('f7000000-0000-4000-8000-000000000004','bbbbbbbb-0000-4000-8000-000000000003','host', true),
  ('f7000000-0000-4000-8000-000000000004','dddddddd-0000-4000-8000-000000000001','participant', true),
  ('f7000000-0000-4000-8000-000000000005','cccccccc-0000-4000-8000-000000000003','host', true),
  ('f7000000-0000-4000-8000-000000000005','cccccccc-0000-4000-8000-000000000004','participant', true),
  ('f7000000-0000-4000-8000-000000000005','cccccccc-0000-4000-8000-000000000002','participant', true),
  ('f7000000-0000-4000-8000-000000000005','dddddddd-0000-4000-8000-000000000002','participant', true),
  ('f7000000-0000-4000-8000-000000000005','dddddddd-0000-4000-8000-000000000003','participant', true);

insert into public.messages (id, conversation_id, sender_id, body) values
  ('f8000000-0000-4000-8000-000000000001','f7000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000001',
   'Assalamu alaikum. We start Saturday after Asr, upstairs. Bring a notebook.'),
  ('f8000000-0000-4000-8000-000000000002','f7000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000002',
   'Wa alaikum assalam, jazak Allahu khayran. I will be there in sha Allah.'),
  ('f8000000-0000-4000-8000-000000000003','f7000000-0000-4000-8000-000000000003','bbbbbbbb-0000-4000-8000-000000000004',
   'The appointment is 09:40. Would 09:00 be too early for you?'),
  ('f8000000-0000-4000-8000-000000000004','f7000000-0000-4000-8000-000000000003','cccccccc-0000-4000-8000-000000000002',
   'Nine is fine. My sister will travel with us so you are not on your own with me.'),
  ('f8000000-0000-4000-8000-000000000005','f7000000-0000-4000-8000-000000000004','bbbbbbbb-0000-4000-8000-000000000003',
   'Happy to look over your CV whenever you are ready.'),
  ('f8000000-0000-4000-8000-000000000006','f7000000-0000-4000-8000-000000000005','cccccccc-0000-4000-8000-000000000003',
   'Eight machines are in the store room. Ibrahim, can you start on those this week?');

-- ---------------------------------------------------------------------
-- A block and a restriction
-- ---------------------------------------------------------------------
insert into public.blocks (blocker_id, blocked_id, reason_note) values
  ('bbbbbbbb-0000-4000-8000-000000000003','dddddddd-0000-4000-8000-000000000001',
   'Kept steering an offer of help towards personal questions.');

-- ---------------------------------------------------------------------
-- Verification and qualifications
-- ---------------------------------------------------------------------
insert into public.user_verifications (user_id, level, status, method, reviewed_by, reviewed_at, verified_at, decision_note) values
  ('bbbbbbbb-0000-4000-8000-000000000001','community_vouched','verified','community_vouch',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Two vouches from the masjid trust.'),
  ('aaaaaaaa-0000-4000-8000-000000000003','scholar_verified','verified','scholar_attestation',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Ijazah verified with the issuing teacher.'),
  ('cccccccc-0000-4000-8000-000000000002','community_vouched','verified','organization_vouch',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Vouched by the relief circle coordinator.'),
  ('cccccccc-0000-4000-8000-000000000003','org_verified','verified','organization_vouch',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Named officer of a verified organization.'),
  ('bbbbbbbb-0000-4000-8000-000000000002','basic','verified','email',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Email confirmed.'),
  ('cccccccc-0000-4000-8000-000000000001','basic','verified','email',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Email confirmed.'),
  ('bbbbbbbb-0000-4000-8000-000000000003','basic','verified','email',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Email confirmed.'),
  ('bbbbbbbb-0000-4000-8000-000000000004','basic','verified','phone',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Phone confirmed by a volunteer visit.'),
  ('bbbbbbbb-0000-4000-8000-000000000005','basic','verified','email',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Email confirmed.'),
  ('eeeeeeee-0000-4000-8000-000000000001','basic','verified','in_person',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Met at the masjid office.'),
  ('eeeeeeee-0000-4000-8000-000000000002','basic','verified','in_person',
   'aaaaaaaa-0000-4000-8000-000000000002', now(), now(),'Met at the masjid office.'),
  -- Ibrahim's request is still pending: nothing about him is verified yet.
  ('cccccccc-0000-4000-8000-000000000004','basic','pending','email', null, null, null, null);

insert into public.qualifications (user_id, qualification_type_id, title, issuing_body, issued_on, verified_at, verified_by, is_public)
select 'aaaaaaaa-0000-4000-8000-000000000003', qt.id, 'Ijazah in Hafs an Asim',
       'Fictional Institute of Recitation', current_date - 4000, now(),
       'aaaaaaaa-0000-4000-8000-000000000002', true
  from public.qualification_types qt where qt.slug = 'ijazah';

insert into public.qualifications (user_id, qualification_type_id, title, issuing_body, issued_on, is_public)
select 'bbbbbbbb-0000-4000-8000-000000000001', qt.id, 'Certificate in Teaching Arabic as a Foreign Language',
       'Fictional Language College', current_date - 1200, true
  from public.qualification_types qt where qt.slug = 'teaching-cert';

insert into public.qualifications (user_id, qualification_type_id, title, issuing_body, issued_on, verified_at, verified_by, is_public)
select 'cccccccc-0000-4000-8000-000000000003', qt.id, 'Youth work background check',
       'Fictional Records Office', current_date - 300, now(),
       'aaaaaaaa-0000-4000-8000-000000000002', false
  from public.qualification_types qt where qt.slug = 'dbs-check';

insert into public.qualifications (user_id, qualification_type_id, title, issuing_body, issued_on, is_public)
select 'cccccccc-0000-4000-8000-000000000001', qt.id, 'Approved electrician registration (fictional)',
       'Fictional Trade Board', current_date - 2200, true
  from public.qualification_types qt where qt.slug = 'trade-licence';

-- An UNVERIFIED claim: nothing about it is trusted until someone else says so.
insert into public.qualifications (user_id, qualification_type_id, title, issuing_body, issued_on, is_public)
select 'cccccccc-0000-4000-8000-000000000004', qt.id, 'First aid at work (fictional)',
       'Fictional Training Centre', current_date - 100, true
  from public.qualification_types qt where qt.slug = 'first-aid';

-- ---------------------------------------------------------------------
-- Trusted contacts and the wali-mediated introduction workflow
-- ---------------------------------------------------------------------
insert into public.trusted_contacts (user_id, contact_user_id, relationship, display_name, contact_email, contact_phone, is_primary, confirmed_at) values
  ('cccccccc-0000-4000-8000-000000000001','eeeeeeee-0000-4000-8000-000000000001','wali','Amir (father)',
   'wali.amir@example.test','555-0142', true, now()),
  ('bbbbbbbb-0000-4000-8000-000000000002','eeeeeeee-0000-4000-8000-000000000002','wali','Saleh (appointed guardian)',
   'wali.saleh@example.test','555-0177', true, now()),
  ('bbbbbbbb-0000-4000-8000-000000000002', null,'emergency_contact','Aunt Ruqayya',
   'ruqayya.family@example.test','555-0188', true, now());

insert into public.wali_profiles
  (id, user_id, wali_user_id, wali_display_name, relationship, contact_email, contact_phone,
   preferred_contact_method, masjid_organization_id, confirmed_at, confirmed_by, confirmation_method)
values
  ('f9000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000001',
   'eeeeeeee-0000-4000-8000-000000000001','Amir (father of Yusuf A.)','father',
   'amir.wali.private@example.test','555-0142','email','f0000000-0000-4000-8000-000000000001',
   now(),'aaaaaaaa-0000-4000-8000-000000000002','Met in person at the masjid office.'),
  ('f9000000-0000-4000-8000-000000000002','bbbbbbbb-0000-4000-8000-000000000002',
   'eeeeeeee-0000-4000-8000-000000000002','Saleh (appointed guardian of Hafsa R.)','appointed_guardian',
   'saleh.wali.private@example.test','555-0177','phone','f0000000-0000-4000-8000-000000000001',
   now(),'aaaaaaaa-0000-4000-8000-000000000002','Appointed through the masjid, witnessed by two members.');

insert into public.formal_introduction_settings
  (user_id, is_open_to_introductions, wali_profile_id, require_wali_approval, require_chaperone,
   min_counterparty_verification, visible_to, intention_statement)
values
  ('cccccccc-0000-4000-8000-000000000001', true,'f9000000-0000-4000-8000-000000000001', true, true,
   'basic','wali_referral_only',
   'Seeking marriage. My father handles the first contact and I would like it kept brief and serious.'),
  ('bbbbbbbb-0000-4000-8000-000000000002', true,'f9000000-0000-4000-8000-000000000002', true, true,
   'basic','wali_referral_only',
   'Open to a wali-mediated introduction. I do not want private messaging before my guardian is satisfied.');

-- An introduction MID-FLOW: the initiator's wali has approved, the
-- recipient's wali is still reviewing. Nothing has been forwarded, so no
-- contact details may be disclosed to anyone yet.
insert into public.formal_introduction_requests
  (id, initiator_id, recipient_id, initiator_wali_profile_id, recipient_wali_profile_id,
   status, intention_statement, referred_by_organization_id,
   submitted_at, initiator_wali_decided_at, recipient_wali_decided_at)
values
  ('fa000000-0000-4000-8000-000000000001',
   'cccccccc-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000002',
   'f9000000-0000-4000-8000-000000000001','f9000000-0000-4000-8000-000000000002',
   'counterpart_wali_review',
   'I am seeking marriage and would like to proceed formally through both of our walis, with a chaperone present.',
   'f0000000-0000-4000-8000-000000000001',
   now() - interval '6 days', now() - interval '4 days', now() - interval '1 day');

insert into public.formal_introduction_participants
  (introduction_id, user_id, participant_role, authorized_for_wali_profile_id) values
  ('fa000000-0000-4000-8000-000000000001','cccccccc-0000-4000-8000-000000000001','initiator', null),
  ('fa000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000002','recipient', null),
  ('fa000000-0000-4000-8000-000000000001','eeeeeeee-0000-4000-8000-000000000001','initiator_wali', null),
  ('fa000000-0000-4000-8000-000000000001','eeeeeeee-0000-4000-8000-000000000002','recipient_wali', null),
  ('fa000000-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000003','chaperone', null);

-- ---------------------------------------------------------------------
-- A campaign. Payments are disabled at the schema level; this is a
-- placeholder record pointing at an external, verified giving route.
-- ---------------------------------------------------------------------
insert into public.campaigns
  (id, organization_id, project_id, created_by, slug, title, description, cause_note,
   status, currency, target_amount, starts_on, ends_on, external_giving_url, is_public)
values
  ('fb000000-0000-4000-8000-000000000001','f0000000-0000-4000-8000-000000000001',
   null,'cccccccc-0000-4000-8000-000000000003','winter-warmth-fund','Winter Warmth Fund',
   'Heating support and warm meals for households in Northgate and Riverside through the winter months.',
   'Distributed as direct assistance to households identified by the trust''s welfare committee.',
   'active','GBP', 15000, current_date - 10, current_date + 80,
   'https://alfurqan.example.test/give/winter-warmth', true),
  ('fb000000-0000-4000-8000-000000000002','f0000000-0000-4000-8000-000000000001',
   null,'cccccccc-0000-4000-8000-000000000003','roof-repair','Masjid roof repair',
   'Replacing failed flashing above the main prayer hall before the winter rains.',
   'General sadaqah. This is a building cost and is NOT zakat eligible.',
   'active','GBP', 6000, current_date - 3, current_date + 120,
   'https://alfurqan.example.test/give/roof', true);

insert into public.campaign_verifications
  (id, campaign_id, kind, status, attested_by, attesting_organization_id, attestation_note, reviewed_at)
values
  ('fc000000-0000-4000-8000-000000000001','fb000000-0000-4000-8000-000000000001',
   'scholarly_zakat_attestation','verified','aaaaaaaa-0000-4000-8000-000000000003',
   'f0000000-0000-4000-8000-000000000001',
   'Funds are distributed only to households in the categories of the poor and the needy, '
   || 'and are not used for building or administration.', now()),
  ('fc000000-0000-4000-8000-000000000002','fb000000-0000-4000-8000-000000000001',
   'financial_review','verified','aaaaaaaa-0000-4000-8000-000000000002',
   'f0000000-0000-4000-8000-000000000001','Ring-fenced account, two signatories.', now());

-- The ONLY supported way to make a campaign zakat eligible.
select app.set_campaign_zakat_eligible(
  'fb000000-0000-4000-8000-000000000001','fc000000-0000-4000-8000-000000000001');

insert into public.donations (campaign_id, organization_id, donor_id, is_anonymous, status, amount, is_zakat, intention_note, settled_at) values
  ('fb000000-0000-4000-8000-000000000001', null,'bbbbbbbb-0000-4000-8000-000000000003', true,'recorded', 250.00, true,
   'Zakat, for the winter fund.', now() - interval '2 days'),
  ('fb000000-0000-4000-8000-000000000001', null,'cccccccc-0000-4000-8000-000000000001', false,'recorded', 100.00, false,
   'Sadaqah.', now() - interval '1 day'),
  ('fb000000-0000-4000-8000-000000000002', null,'cccccccc-0000-4000-8000-000000000002', true,'pledged', 50.00, false,
   'Will pay at Jumuah.', null);

-- ---------------------------------------------------------------------
-- Moderation: a live case with preserved evidence
-- ---------------------------------------------------------------------
insert into public.reports (id, reporter_id, category, status, severity, summary, reported_user_id, conversation_id, triaged_at, triaged_by) values
  ('fd000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000003','off_purpose_contact',
   'investigating','moderate',
   'Offered CV help and then kept asking personal questions unrelated to the request. I have blocked him.',
   'dddddddd-0000-4000-8000-000000000001','f7000000-0000-4000-8000-000000000004',
   now() - interval '2 days','aaaaaaaa-0000-4000-8000-000000000002');

insert into public.report_evidence (report_id, submitted_by, evidence_kind, body, message_id) values
  ('fd000000-0000-4000-8000-000000000001','bbbbbbbb-0000-4000-8000-000000000003','text',
   'He asked three times about my living arrangements after I said I only wanted CV feedback.', null);

insert into public.moderation_cases (id, status, severity, subject_user_id, opened_by, assigned_to, title, narrative, second_reviewer_id) values
  ('fe000000-0000-4000-8000-000000000001','investigating','moderate','dddddddd-0000-4000-8000-000000000001',
   'aaaaaaaa-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000002',
   'Off-purpose contact in a mutual-aid channel',
   'Pattern of steering a purpose-bound channel towards personal contact. First recorded incident.',
   'aaaaaaaa-0000-4000-8000-000000000001');

update public.reports set case_id = 'fe000000-0000-4000-8000-000000000001'
 where id = 'fd000000-0000-4000-8000-000000000001';

insert into public.moderation_actions (case_id, report_id, actor_id, action_type, target_user_id, rationale, policy_reference) values
  ('fe000000-0000-4000-8000-000000000001','fd000000-0000-4000-8000-000000000001',
   'aaaaaaaa-0000-4000-8000-000000000002','warning_issued','dddddddd-0000-4000-8000-000000000001',
   'First incident of off-purpose contact. Written warning issued with a link to the community covenant.',
   'covenant:purpose-only');

-- A separate, older case: a full messaging restriction still in force.
insert into public.moderation_cases (id, status, severity, subject_user_id, opened_by, assigned_to, title, narrative) values
  ('fe000000-0000-4000-8000-000000000002','resolved','high','dddddddd-0000-4000-8000-000000000002',
   'aaaaaaaa-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000002',
   'Repeated unsolicited contact','Three upheld reports over two months.');

insert into public.moderation_actions (id, case_id, actor_id, action_type, target_user_id, rationale, expires_at) values
  ('ff000000-0000-4000-8000-000000000001','fe000000-0000-4000-8000-000000000002',
   'aaaaaaaa-0000-4000-8000-000000000002','account_restricted','dddddddd-0000-4000-8000-000000000002',
   'Messaging suspended for thirty days after three upheld reports of unsolicited contact.',
   now() + interval '23 days');

insert into public.restrictions (user_id, restriction_type, case_id, action_id, imposed_by, reason, expires_at) values
  ('dddddddd-0000-4000-8000-000000000002','messaging_suspended','fe000000-0000-4000-8000-000000000002',
   'ff000000-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000002',
   'Three upheld reports of unsolicited contact.', now() + interval '23 days');

insert into public.appeals (action_id, case_id, appellant_id, status, grounds) values
  ('ff000000-0000-4000-8000-000000000001','fe000000-0000-4000-8000-000000000002',
   'dddddddd-0000-4000-8000-000000000002','submitted',
   'I believe two of the three reports referred to the same exchange and were counted twice.');

insert into public.safety_incidents (case_id, reported_by, severity, category, occurred_at, location_note, organization_id, involved_user_id, narrative, safeguarding_lead_notified_at) values
  ('fe000000-0000-4000-8000-000000000002','aaaaaaaa-0000-4000-8000-000000000002','high','safety_concern',
   now() - interval '20 days','Reported through the platform, no in-person element.',
   'f0000000-0000-4000-8000-000000000001','dddddddd-0000-4000-8000-000000000002',
   'Escalated to the safeguarding lead because one of the recipients was newly arrived and isolated.',
   now() - interval '19 days');

-- ---------------------------------------------------------------------
-- Consent receipts and notifications
-- ---------------------------------------------------------------------
insert into public.consent_records (user_id, consent_type, document_version, granted)
select id, 'community_covenant', '2026-01', true from public.profiles where deleted_at is null;

insert into public.consent_records (user_id, consent_type, document_version, granted)
select id, 'safeguarding_policy', '2026-01', true from public.profiles
 where id in ('cccccccc-0000-4000-8000-000000000003','aaaaaaaa-0000-4000-8000-000000000003');

insert into public.consent_records (user_id, consent_type, document_version, granted, scope_note) values
  ('cccccccc-0000-4000-8000-000000000001','wali_mediation_terms','2026-01', true,
   'Consents to his wali receiving and handling first contact on his behalf.'),
  ('bbbbbbbb-0000-4000-8000-000000000002','wali_mediation_terms','2026-01', true,
   'Consents to her appointed guardian receiving and handling first contact on her behalf.');

select app.notify('cccccccc-0000-4000-8000-000000000002','commitment.confirmed',
                  'Your food-run hours were confirmed',
                  'Nadia H. confirmed 4.5 hours on the Eastbrook run.',
                  'commitments','f6000000-0000-4000-8000-000000000001', false);
select app.notify('bbbbbbbb-0000-4000-8000-000000000002','introduction.awaiting_wali',
                  'Your guardian is reviewing an introduction',
                  'No contact details have been shared with anyone.',
                  'formal_introduction_requests','fa000000-0000-4000-8000-000000000001', false);
select app.notify('dddddddd-0000-4000-8000-000000000001','moderation.warning',
                  'A warning has been recorded on your account',
                  'Please read the community covenant section on purpose-bound contact.',
                  'moderation_actions', null, true);

commit;

-- ---------------------------------------------------------------------
-- Post-seed sanity: the fixtures the RLS tests depend on must exist.
-- ---------------------------------------------------------------------
do $$
begin
  if (select count(*) from public.profiles) < 17 then
    raise exception 'seed: expected at least 17 profiles';
  end if;
  if not exists (select 1 from public.campaigns
                  where id = 'fb000000-0000-4000-8000-000000000001' and zakat_eligible) then
    raise exception 'seed: the winter fund should be zakat eligible via app.set_campaign_zakat_eligible()';
  end if;
  if exists (select 1 from public.conversations c
              where not exists (select 1 from public.conversation_purposes p
                                 where p.conversation_id = c.id)) then
    raise exception 'seed: a conversation was created without a purpose';
  end if;
  if not exists (select 1 from public.audit_logs where action like 'moderation_action.%') then
    raise exception 'seed: moderation actions did not produce audit rows';
  end if;
end;
$$;
