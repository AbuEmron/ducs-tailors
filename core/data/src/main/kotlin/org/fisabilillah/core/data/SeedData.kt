package org.fisabilillah.core.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.ApproximateLocation
import org.fisabilillah.core.model.Attestation
import org.fisabilillah.core.model.AudienceScope
import org.fisabilillah.core.model.Availability
import org.fisabilillah.core.model.AvailabilityWindow
import org.fisabilillah.core.model.BeneficiaryType
import org.fisabilillah.core.model.Campaign
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.CampaignStatus
import org.fisabilillah.core.model.CampaignVerification
import org.fisabilillah.core.model.Community
import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.CommunityKind
import org.fisabilillah.core.model.CommunityMember
import org.fisabilillah.core.model.CommunityMemberRole
import org.fisabilillah.core.model.CommunityRule
import org.fisabilillah.core.model.ContactMethod
import org.fisabilillah.core.model.CrossGenderConversationStructure
import org.fisabilillah.core.model.DeliveryFormat
import org.fisabilillah.core.model.ExactLocation
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.FundType
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.GuardianRelationship
import org.fisabilillah.core.model.GuardianVerificationState
import org.fisabilillah.core.model.Language
import org.fisabilillah.core.model.LearningCost
import org.fisabilillah.core.model.LearningLevel
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.LearningSubject
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.ListingVerification
import org.fisabilillah.core.model.MembershipPolicy
import org.fisabilillah.core.model.MembershipStatus
import org.fisabilillah.core.model.Methodology
import org.fisabilillah.core.model.ModeratorPresenceRule
import org.fisabilillah.core.model.Money
import org.fisabilillah.core.model.NameVisibility
import org.fisabilillah.core.model.Organization
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.OrganizationKind
import org.fisabilillah.core.model.OrganizationMember
import org.fisabilillah.core.model.OrganizationRole
import org.fisabilillah.core.model.OrganizationStatus
import org.fisabilillah.core.model.OrganizationVerification
import org.fisabilillah.core.model.Place
import org.fisabilillah.core.model.PrivateContactDetails
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.ProfileImageStyle
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ProjectId
import org.fisabilillah.core.model.ProjectStatus
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.RequestUrgency
import org.fisabilillah.core.model.RequestVisibility
import org.fisabilillah.core.model.SafeguardFloor
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.Skill
import org.fisabilillah.core.model.SkillCategory
import org.fisabilillah.core.model.SkillId
import org.fisabilillah.core.model.SkillProficiency
import org.fisabilillah.core.model.SourceReference
import org.fisabilillah.core.model.TeachingCapacity
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.TrustRecord
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactId
import org.fisabilillah.core.model.TrustedContactRole
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.UserSkill
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.VolunteerOpportunity
import org.fisabilillah.core.policy.SafeguardPresets
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Sample data for development and demonstration.
 *
 * Everybody here is invented. The addresses are made up, the phone numbers use the
 * reserved 555-01xx range, and the email addresses use `.test` domains that cannot resolve.
 * Nothing in this file corresponds to a real person, masjid, or charity — which matters
 * more than usual for a platform whose seed data would otherwise consist of people's
 * financial difficulties and family arrangements.
 *
 * The scenarios are chosen to exercise the parts of the product that are easy to get wrong:
 * an organisation that imposes a stricter floor than its members chose, a youth programme
 * that requires background checks, a request whose address stays hidden, a campaign that
 * cannot take money, and an introduction sitting with a wali.
 */
public object SeedData {

    // Fixed so that seeded data is identical on every run.
    public val EPOCH: Timestamp = Instant.parse("2026-03-01T09:00:00Z")

    // ── Places ────────────────────────────────────────────────────────────────
    private val northfield = Place(
        approximate = ApproximateLocation(
            label = "Northfield, Ashbourne",
            city = "Ashbourne",
            region = "Midshire",
            countryCode = "GB",
            coarseLatitude = 52.51,
            coarseLongitude = -1.89,
        ),
    )
    private val riverside = Place(
        approximate = ApproximateLocation(
            label = "Riverside, Ashbourne",
            city = "Ashbourne",
            region = "Midshire",
            countryCode = "GB",
            coarseLatitude = 52.47,
            coarseLongitude = -1.92,
        ),
    )
    private val eastgate = Place(
        approximate = ApproximateLocation(
            label = "Eastgate, Ashbourne",
            city = "Ashbourne",
            region = "Midshire",
            countryCode = "GB",
        ),
    )

    // ── Skills ────────────────────────────────────────────────────────────────
    public val arabicSkill: Skill =
        Skill(SkillId("skill-arabic"), "arabic", "Arabic language", SkillCategory.LANGUAGE)
    public val tajwidSkill: Skill =
        Skill(SkillId("skill-tajwid"), "tajwid", "Tajwid", SkillCategory.ISLAMIC_STUDIES)
    public val electricalSkill: Skill =
        Skill(SkillId("skill-electrical"), "electrical", "Electrical work", SkillCategory.TRADES)
    public val cvSkill: Skill =
        Skill(SkillId("skill-cv"), "cv-writing", "CV writing", SkillCategory.PROFESSIONAL)
    public val drivingSkill: Skill =
        Skill(SkillId("skill-driving"), "driving", "Driving", SkillCategory.LOGISTICS)
    public val softwareSkill: Skill =
        Skill(SkillId("skill-software"), "software", "Software development", SkillCategory.TECHNOLOGY)
    public val cateringSkill: Skill =
        Skill(SkillId("skill-catering"), "catering", "Cooking for groups", SkillCategory.CARE)

    public val allSkills: List<Skill> = listOf(
        arabicSkill, tajwidSkill, electricalSkill, cvSkill, drivingSkill, softwareSkill,
        cateringSkill,
    )

    // ── Identifiers ───────────────────────────────────────────────────────────
    public val yusuf: UserId = UserId("user-yusuf")
    public val aminah: UserId = UserId("user-aminah")
    public val ibrahim: UserId = UserId("user-ibrahim")
    public val khadija: UserId = UserId("user-khadija")
    public val daniel: UserId = UserId("user-daniel")
    public val fatima: UserId = UserId("user-fatima")
    public val musa: UserId = UserId("user-musa")
    public val hafsa: UserId = UserId("user-hafsa")
    public val abdullah: UserId = UserId("user-abdullah")
    public val zaynab: UserId = UserId("user-zaynab")
    public val moderator: UserId = UserId("user-moderator")
    public val safetyAdmin: UserId = UserId("user-safety-admin")

    public val northfieldMasjid: OrganizationId = OrganizationId("org-northfield-masjid")
    public val ashbourneRelief: OrganizationId = OrganizationId("org-ashbourne-relief")

    public val youthCommunity: CommunityId = CommunityId("community-youth")
    public val arabicCircle: CommunityId = CommunityId("community-arabic-circle")
    public val newMuslimCircle: CommunityId = CommunityId("community-new-muslims")

    /**
     * Populates [store] with the full sample set.
     *
     * Safe to call on a fresh store only; it does not attempt to merge with existing data.
     */
    public fun populate(store: InMemoryStore) {
        allSkills.forEach { store.skills[it.id] = it }
        seedOrganizations(store)
        seedProfiles(store)
        seedCommunities(store)
        seedTrustedContactsAndIntroductions(store)
        seedLearning(store)
        seedOpportunities(store)
        seedRequests(store)
        seedProjects(store)
        seedCampaign(store)
        store.touch()
    }

    private fun seedOrganizations(store: InMemoryStore) {
        store.organizations[northfieldMasjid] = Organization(
            id = northfieldMasjid,
            name = "Northfield Masjid and Community Centre",
            kind = OrganizationKind.MASJID,
            summary = "A neighbourhood masjid running weekend classes, a food store, and a " +
                "youth programme.",
            place = northfield,
            websiteUrl = "https://northfield-masjid.example.test",
            contactEmail = "office@northfield-masjid.example.test",
            verification = OrganizationVerification(
                registrationChecked = true,
                registrationNumber = "CH-000000-EXAMPLE",
                registrationJurisdiction = "GB",
                checkedAt = EPOCH - 90.days,
                checkedBy = safetyAdmin,
            ),
            // The masjid requires oversight on cross-gender threads inside its own spaces.
            // It cannot loosen anything a member chose — only add to it.
            safeguardFloor = SafeguardFloor(
                crossGenderStructure = CrossGenderConversationStructure.GROUP_CONTEXT_ONLY,
                moderatorPresence = ModeratorPresenceRule.CROSS_GENDER_ONLY,
                meetingsMustBeInPublicPlaces = true,
            ),
            status = OrganizationStatus.ACTIVE,
            createdAt = EPOCH - 400.days,
            updatedAt = EPOCH - 90.days,
        )

        store.organizations[ashbourneRelief] = Organization(
            id = ashbourneRelief,
            name = "Ashbourne Relief Trust",
            kind = OrganizationKind.CHARITY,
            summary = "Local food and clothing distribution, and emergency household support.",
            place = riverside,
            contactEmail = "help@ashbourne-relief.example.test",
            verification = OrganizationVerification(
                registrationChecked = true,
                registrationNumber = "CH-111111-EXAMPLE",
                registrationJurisdiction = "GB",
                checkedAt = EPOCH - 200.days,
                checkedBy = safetyAdmin,
            ),
            status = OrganizationStatus.ACTIVE,
            createdAt = EPOCH - 600.days,
            updatedAt = EPOCH - 200.days,
        )

        store.organizationMembers += listOf(
            OrganizationMember(northfieldMasjid, ibrahim, OrganizationRole.ADMINISTRATOR, "Imam", joinedAt = EPOCH - 300.days),
            OrganizationMember(northfieldMasjid, yusuf, OrganizationRole.COORDINATOR, "Youth lead", joinedAt = EPOCH - 200.days),
            OrganizationMember(northfieldMasjid, aminah, OrganizationRole.MEMBER, joinedAt = EPOCH - 150.days),
            OrganizationMember(northfieldMasjid, khadija, OrganizationRole.COORDINATOR, "Sisters' classes", joinedAt = EPOCH - 180.days),
            OrganizationMember(northfieldMasjid, abdullah, OrganizationRole.MEMBER, joinedAt = EPOCH - 85.days),
            OrganizationMember(northfieldMasjid, zaynab, OrganizationRole.MEMBER, joinedAt = EPOCH - 140.days),
            OrganizationMember(ashbourneRelief, hafsa, OrganizationRole.ADMINISTRATOR, "Coordinator", joinedAt = EPOCH - 250.days),
            OrganizationMember(ashbourneRelief, musa, OrganizationRole.MEMBER, joinedAt = EPOCH - 100.days),
        )
    }

    private fun seedProfiles(store: InMemoryStore) {
        fun add(profile: Profile, safeguards: UserSafeguards, trust: TrustRecord = TrustRecord(profile.id)) {
            store.profiles[profile.id] = profile
            store.safeguards[profile.id] = safeguards
            store.trustRecords[profile.id] = trust
        }

        add(
            Profile(
                id = yusuf,
                displayName = "Yusuf A.",
                realName = "Yusuf Adeyemi",
                gender = Gender.MALE,
                dateOfBirthYear = 1992,
                place = northfield,
                languages = listOf(Language.ENGLISH, Language.ARABIC),
                skills = listOf(
                    UserSkill(arabicSkill, SkillProficiency.EXPERIENCED, willingToTeach = true),
                    UserSkill(softwareSkill, SkillProficiency.PROFESSIONAL, willingToTeach = true),
                ),
                areasWillingToHelp = setOf(
                    ServiceCategory.ARABIC_TUTORING,
                    ServiceCategory.TECHNOLOGY_SUPPORT,
                    ServiceCategory.YOUTH_MENTORSHIP,
                ),
                availability = Availability(
                    windows = listOf(
                        AvailabilityWindow(DayOfWeek.SATURDAY, LocalTime(10, 0), LocalTime(13, 0)),
                        AvailabilityWindow(DayOfWeek.WEDNESDAY, LocalTime(19, 0), LocalTime(21, 0)),
                    ),
                    hoursPerWeek = 5,
                    timeZoneId = "Europe/London",
                ),
                teachingCapacity = TeachingCapacity.ARABIC_TUTOR,
                verificationLevel = VerificationLevel.BACKGROUND_CHECKED,
                attestations = setOf(Attestation.MASJID_AFFILIATED, Attestation.QUALIFICATION_VERIFIED),
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.VOLUNTEER, AccountRole.TEACHER),
                organizationIds = setOf(northfieldMasjid),
                contributionStatement = "Teaching Arabic at the masjid on Saturdays and helping " +
                    "with the youth programme. Happy to help anyone struggling with a laptop.",
                completedCommitments = 24,
                createdAt = EPOCH - 300.days,
                updatedAt = EPOCH - 10.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, yusuf),
            TrustRecord(
                userId = yusuf,
                commitmentsCompleted = 24,
                organizerConfirmations = 24,
                onTimeArrivals = 22,
                activitiesOrganized = 6,
                activitiesOrganizedCompleted = 5,
                qualificationsVerified = 1,
            ),
        )

        add(
            Profile(
                id = aminah,
                displayName = "Aminah S.",
                realName = "Aminah Siddiqui",
                gender = Gender.FEMALE,
                dateOfBirthYear = 1998,
                imageStyle = ProfileImageStyle.INITIALS,
                place = northfield,
                languages = listOf(Language.ENGLISH, Language.URDU),
                skills = listOf(UserSkill(cvSkill, SkillProficiency.PROFESSIONAL, willingToTeach = true)),
                areasWillingToHelp = setOf(
                    ServiceCategory.RESUME_ASSISTANCE,
                    ServiceCategory.CAREER_GUIDANCE,
                ),
                areasSeekingHelp = setOf(ServiceCategory.ARABIC_TUTORING),
                availability = Availability(hoursPerWeek = 3, timeZoneId = "Europe/London"),
                verificationLevel = VerificationLevel.IDENTITY_VERIFIED,
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.VOLUNTEER, AccountRole.LEARNER),
                organizationIds = setOf(northfieldMasjid),
                contributionStatement = "I review CVs and do mock interviews for sisters looking " +
                    "for work. Learning Arabic myself.",
                completedCommitments = 7,
                createdAt = EPOCH - 150.days,
                updatedAt = EPOCH - 5.days,
            ),
            // Chose the wali-guided preset, and additionally turned introductions on.
            SafeguardPresets.forName(SafeguardPresetName.FAMILY_AND_WALI_GUIDED, aminah).copy(
                acceptFormalIntroductions = true,
                introductionsGoDirectlyToGuardian = false,
                nameVisibility = NameVisibility.REAL_NAME_TO_ORGANIZERS,
            ),
            TrustRecord(userId = aminah, commitmentsCompleted = 7, organizerConfirmations = 7, onTimeArrivals = 7),
        )

        add(
            Profile(
                id = ibrahim,
                displayName = "Imam Ibrahim",
                realName = "Ibrahim Toure",
                gender = Gender.MALE,
                dateOfBirthYear = 1975,
                place = northfield,
                languages = listOf(Language.ENGLISH, Language.ARABIC, Language.FRENCH),
                skills = listOf(UserSkill(tajwidSkill, SkillProficiency.PROFESSIONAL, willingToTeach = true)),
                areasWillingToHelp = setOf(ServiceCategory.QURAN_SUPPORT, ServiceCategory.NEW_MUSLIM_SUPPORT),
                teachingCapacity = TeachingCapacity.VERIFIED_SCHOLAR,
                verificationLevel = VerificationLevel.BACKGROUND_CHECKED,
                attestations = setOf(
                    Attestation.MASJID_AFFILIATED,
                    Attestation.QUALIFICATION_VERIFIED,
                    Attestation.ORGANIZATION_VERIFIED,
                ),
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.SCHOLAR, AccountRole.TEACHER),
                organizationIds = setOf(northfieldMasjid),
                contributionStatement = "Imam at Northfield. Available for Qur'an classes and " +
                    "for new Muslims finding their feet.",
                completedCommitments = 60,
                createdAt = EPOCH - 500.days,
                updatedAt = EPOCH - 20.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, ibrahim).copy(
                contactableBy = AudienceScope.EVERYONE,
                minimumVerificationToContactMe = VerificationLevel.EMAIL_VERIFIED,
            ),
            TrustRecord(
                userId = ibrahim,
                commitmentsCompleted = 60,
                organizerConfirmations = 60,
                activitiesOrganized = 20,
                activitiesOrganizedCompleted = 19,
                qualificationsVerified = 3,
            ),
        )

        add(
            Profile(
                id = khadija,
                displayName = "Khadija R.",
                realName = "Khadija Rahman",
                gender = Gender.FEMALE,
                dateOfBirthYear = 1985,
                place = northfield,
                languages = listOf(Language.ENGLISH, Language.BENGALI),
                skills = listOf(UserSkill(arabicSkill, SkillProficiency.EXPERIENCED, willingToTeach = true)),
                areasWillingToHelp = setOf(ServiceCategory.ARABIC_TUTORING, ServiceCategory.NEW_MUSLIM_SUPPORT),
                teachingCapacity = TeachingCapacity.ARABIC_TUTOR,
                verificationLevel = VerificationLevel.BACKGROUND_CHECKED,
                attestations = setOf(Attestation.MASJID_AFFILIATED, Attestation.QUALIFICATION_VERIFIED),
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.TEACHER, AccountRole.VOLUNTEER),
                organizationIds = setOf(northfieldMasjid),
                contributionStatement = "Arabic reading for sisters, beginners welcome. I teach " +
                    "sisters only.",
                completedCommitments = 31,
                createdAt = EPOCH - 280.days,
                updatedAt = EPOCH - 15.days,
            ),
            // Tightened beyond the preset: only sisters may contact her at all.
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, khadija).copy(
                contactableBy = AudienceScope.SAME_GENDER_ONLY,
                profileDiscoverableBy = AudienceScope.EVERYONE,
                profileImageVisibleTo = AudienceScope.SAME_GENDER_ONLY,
            ),
            TrustRecord(
                userId = khadija,
                commitmentsCompleted = 31,
                organizerConfirmations = 31,
                activitiesOrganized = 9,
                activitiesOrganizedCompleted = 9,
                qualificationsVerified = 2,
            ),
        )

        add(
            Profile(
                id = daniel,
                displayName = "Daniel (Dawud)",
                gender = Gender.MALE,
                dateOfBirthYear = 2000,
                place = riverside,
                languages = listOf(Language.ENGLISH),
                areasSeekingHelp = setOf(
                    ServiceCategory.NEW_MUSLIM_SUPPORT,
                    ServiceCategory.ARABIC_TUTORING,
                    ServiceCategory.QURAN_SUPPORT,
                ),
                verificationLevel = VerificationLevel.EMAIL_VERIFIED,
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.LEARNER),
                contributionStatement = "Took my shahada four months ago. Looking for someone " +
                    "patient to help me learn to pray properly and read Arabic.",
                createdAt = EPOCH - 60.days,
                updatedAt = EPOCH - 2.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.LEARNING_ONLY, daniel),
        )

        add(
            Profile(
                id = fatima,
                displayName = "Fatima B.",
                gender = Gender.FEMALE,
                dateOfBirthYear = 1963,
                place = eastgate,
                languages = listOf(Language.ENGLISH, Language.SOMALI),
                areasSeekingHelp = setOf(ServiceCategory.TRANSPORTATION, ServiceCategory.ELDER_ASSISTANCE),
                verificationLevel = VerificationLevel.PHONE_VERIFIED,
                roles = setOf(AccountRole.COMMUNITY_MEMBER),
                createdAt = EPOCH - 120.days,
                updatedAt = EPOCH - 30.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.MAXIMUM_PRIVACY, fatima).copy(
                // Opened up just enough to be reachable about a lift to the hospital.
                contactableBy = AudienceScope.VERIFIED_ONLY,
                profileDiscoverableBy = AudienceScope.VERIFIED_ONLY,
            ),
        )

        add(
            Profile(
                id = musa,
                displayName = "Musa K.",
                realName = "Musa Karim",
                gender = Gender.MALE,
                dateOfBirthYear = 1979,
                place = riverside,
                languages = listOf(Language.ENGLISH, Language.URDU),
                skills = listOf(
                    UserSkill(electricalSkill, SkillProficiency.PROFESSIONAL, willingToTeach = true, yearsOfExperience = 18),
                    UserSkill(drivingSkill, SkillProficiency.COMPETENT),
                ),
                areasWillingToHelp = setOf(
                    ServiceCategory.TRADE_SKILLS_MENTORING,
                    ServiceCategory.TRANSPORTATION,
                    ServiceCategory.MASJID_MAINTENANCE,
                ),
                teachingCapacity = TeachingCapacity.LICENSED_PROFESSIONAL,
                verificationLevel = VerificationLevel.IDENTITY_VERIFIED,
                attestations = setOf(Attestation.QUALIFICATION_VERIFIED),
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.VOLUNTEER),
                organizationIds = setOf(ashbourneRelief),
                contributionStatement = "Qualified electrician, eighteen years. Happy to take on " +
                    "a young brother who wants to learn the trade properly.",
                completedCommitments = 12,
                createdAt = EPOCH - 220.days,
                updatedAt = EPOCH - 8.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, musa),
            TrustRecord(userId = musa, commitmentsCompleted = 12, organizerConfirmations = 12, qualificationsVerified = 1),
        )

        add(
            Profile(
                id = hafsa,
                displayName = "Hafsa N.",
                realName = "Hafsa Noor",
                gender = Gender.FEMALE,
                dateOfBirthYear = 1990,
                place = riverside,
                languages = listOf(Language.ENGLISH, Language.SOMALI),
                skills = listOf(UserSkill(cateringSkill, SkillProficiency.EXPERIENCED)),
                areasWillingToHelp = setOf(
                    ServiceCategory.FOOD_DISTRIBUTION,
                    ServiceCategory.MEAL_PREPARATION,
                    ServiceCategory.CLOTHING_DISTRIBUTION,
                ),
                verificationLevel = VerificationLevel.IDENTITY_VERIFIED,
                attestations = setOf(Attestation.ORGANIZATION_VERIFIED),
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.PROJECT_ORGANIZER, AccountRole.VOLUNTEER),
                organizationIds = setOf(ashbourneRelief),
                contributionStatement = "I coordinate the Saturday food distribution for " +
                    "Ashbourne Relief.",
                completedCommitments = 45,
                createdAt = EPOCH - 250.days,
                updatedAt = EPOCH - 3.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.ORGANIZATION_MANAGED, hafsa),
            TrustRecord(
                userId = hafsa,
                commitmentsCompleted = 45,
                organizerConfirmations = 45,
                activitiesOrganized = 30,
                activitiesOrganizedCompleted = 29,
            ),
        )

        add(
            Profile(
                id = abdullah,
                displayName = "Abdullah H.",
                realName = "Abdullah Hakim",
                gender = Gender.MALE,
                dateOfBirthYear = 1996,
                place = riverside,
                languages = listOf(Language.ENGLISH, Language.ARABIC),
                areasWillingToHelp = setOf(ServiceCategory.COMMUNITY_CLEANUP),
                verificationLevel = VerificationLevel.IDENTITY_VERIFIED,
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.VOLUNTEER),
                organizationIds = setOf(northfieldMasjid),
                contributionStatement = "Working in logistics. Free most weekends for community work.",
                completedCommitments = 4,
                createdAt = EPOCH - 90.days,
                updatedAt = EPOCH - 1.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, abdullah),
            TrustRecord(userId = abdullah, commitmentsCompleted = 4, organizerConfirmations = 4),
        )

        // Aminah's father, who holds the wali role for her.
        add(
            Profile(
                id = zaynab,
                displayName = "Sulayman S.",
                realName = "Sulayman Siddiqui",
                gender = Gender.MALE,
                dateOfBirthYear = 1966,
                place = northfield,
                languages = listOf(Language.ENGLISH, Language.URDU),
                verificationLevel = VerificationLevel.IDENTITY_VERIFIED,
                attestations = setOf(Attestation.WALI_CONTACT_VERIFIED),
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.WALI_CONTACT),
                organizationIds = setOf(northfieldMasjid),
                createdAt = EPOCH - 140.days,
                updatedAt = EPOCH - 140.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, zaynab),
        )

        add(
            Profile(
                id = moderator,
                displayName = "Community Safety Team",
                gender = Gender.FEMALE,
                dateOfBirthYear = 1988,
                place = northfield,
                verificationLevel = VerificationLevel.BACKGROUND_CHECKED,
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.MODERATOR),
                createdAt = EPOCH - 400.days,
                updatedAt = EPOCH - 400.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, moderator),
        )

        add(
            Profile(
                id = safetyAdmin,
                displayName = "Safety Administration",
                gender = Gender.MALE,
                dateOfBirthYear = 1980,
                place = northfield,
                verificationLevel = VerificationLevel.BACKGROUND_CHECKED,
                roles = setOf(AccountRole.COMMUNITY_MEMBER, AccountRole.SAFETY_ADMINISTRATOR),
                createdAt = EPOCH - 400.days,
                updatedAt = EPOCH - 400.days,
            ),
            SafeguardPresets.forName(SafeguardPresetName.COMMUNITY_SERVICE, safetyAdmin),
        )
    }

    private fun seedCommunities(store: InMemoryStore) {
        store.communities[youthCommunity] = Community(
            id = youthCommunity,
            name = "Northfield Youth Programme",
            kind = CommunityKind.COMMUNITY_PROJECT,
            summary = "Saturday programme for 11–16s: sport, study support, and a weekly halaqa. " +
                "Run under the masjid's safeguarding policy.",
            organizationId = northfieldMasjid,
            place = northfield,
            membershipPolicy = MembershipPolicy.ORGANIZATION_MEMBERS_ONLY,
            rules = listOf(
                CommunityRule(1, "Background checks are mandatory", "Every adult volunteer holds a current check before any contact with young people."),
                CommunityRule(2, "No private messages with participants", "All communication with young people happens in the group, in view of the coordinators."),
                CommunityRule(3, "Two adults present", "No session runs with a single adult."),
                CommunityRule(4, "Report concerns immediately", "Anything that worries you goes to the safeguarding lead the same day."),
            ),
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            // Strictest floor on the platform, and deliberately so.
            safeguardFloor = SafeguardFloor(
                contactableBy = AudienceScope.MY_ORGANIZATIONS_ONLY,
                minimumVerificationToContact = VerificationLevel.BACKGROUND_CHECKED,
                crossGenderStructure = CrossGenderConversationStructure.GROUP_CONTEXT_ONLY,
                moderatorPresence = ModeratorPresenceRule.ALWAYS,
                requireGroupContext = true,
                videoCallsAllowedFrom = AudienceScope.NOBODY,
                oneToOneMeetingsAllowedFrom = AudienceScope.NOBODY,
                meetingsMustBeInPublicPlaces = true,
                meetingsRequireThirdParty = true,
                forbidFormalIntroductions = true,
            ),
            moderatorIds = setOf(yusuf, moderator),
            memberCount = 3,
            createdAt = EPOCH - 200.days,
            updatedAt = EPOCH - 30.days,
        )

        store.communities[arabicCircle] = Community(
            id = arabicCircle,
            name = "Ashbourne Arabic Circle",
            kind = CommunityKind.LEARNING_CIRCLE,
            summary = "Weekly Arabic study, separate sessions for brothers and sisters.",
            organizationId = northfieldMasjid,
            place = northfield,
            membershipPolicy = MembershipPolicy.APPROVAL_REQUIRED,
            rules = listOf(
                CommunityRule(1, "Come prepared", "Do the week's reading before the session."),
                CommunityRule(2, "Questions in the group", "Ask in the circle so everyone benefits."),
            ),
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            moderatorIds = setOf(khadija),
            memberCount = 4,
            createdAt = EPOCH - 180.days,
            updatedAt = EPOCH - 20.days,
        )

        store.communities[newMuslimCircle] = Community(
            id = newMuslimCircle,
            name = "New Muslim Support — Ashbourne",
            kind = CommunityKind.NEW_MUSLIM_SUPPORT,
            summary = "A patient, unhurried space for people in their first years of Islam.",
            organizationId = northfieldMasjid,
            place = northfield,
            membershipPolicy = MembershipPolicy.APPROVAL_REQUIRED,
            rules = listOf(
                CommunityRule(1, "No arguing over differences", "This is not the place for madhhab debates."),
                CommunityRule(2, "Refer, don't guess", "If you don't know, say so and point to someone qualified."),
            ),
            moderatorIds = setOf(ibrahim),
            memberCount = 3,
            createdAt = EPOCH - 160.days,
            updatedAt = EPOCH - 12.days,
        )

        store.communityMembers += listOf(
            CommunityMember(youthCommunity, yusuf, CommunityMemberRole.MODERATOR, MembershipStatus.ACTIVE, EPOCH - 200.days),
            CommunityMember(youthCommunity, abdullah, CommunityMemberRole.MEMBER, MembershipStatus.PENDING, EPOCH - 10.days),
            CommunityMember(youthCommunity, moderator, CommunityMemberRole.MODERATOR, MembershipStatus.ACTIVE, EPOCH - 200.days),
            CommunityMember(arabicCircle, khadija, CommunityMemberRole.MODERATOR, MembershipStatus.ACTIVE, EPOCH - 180.days),
            CommunityMember(arabicCircle, aminah, CommunityMemberRole.MEMBER, MembershipStatus.ACTIVE, EPOCH - 100.days),
            CommunityMember(arabicCircle, yusuf, CommunityMemberRole.ORGANIZER, MembershipStatus.ACTIVE, EPOCH - 170.days),
            CommunityMember(arabicCircle, daniel, CommunityMemberRole.MEMBER, MembershipStatus.ACTIVE, EPOCH - 40.days),
            CommunityMember(newMuslimCircle, ibrahim, CommunityMemberRole.MODERATOR, MembershipStatus.ACTIVE, EPOCH - 160.days),
            CommunityMember(newMuslimCircle, daniel, CommunityMemberRole.MEMBER, MembershipStatus.ACTIVE, EPOCH - 55.days),
            CommunityMember(newMuslimCircle, yusuf, CommunityMemberRole.MEMBER, MembershipStatus.ACTIVE, EPOCH - 150.days),
        )
    }

    /**
     * A wali-mediated introduction, mid-flow.
     *
     * Abdullah submitted a structured form; Aminah chose to screen requests herself before
     * her father sees them, so it is sitting with her. Note that Abdullah has learned
     * nothing at all: not whether she has read it, not whether she has a wali, not even
     * whether the feature is switched on.
     */
    private fun seedTrustedContactsAndIntroductions(store: InMemoryStore) {
        val waliId = TrustedContactId("contact-aminah-wali")
        store.trustedContacts[waliId] = TrustedContact(
            id = waliId,
            ownerId = aminah,
            name = "Sulayman Siddiqui",
            relationship = GuardianRelationship.FATHER,
            role = TrustedContactRole.WALI,
            // Never leaves Aminah's own session. Everything a counterparty sees comes from
            // TrustedContact.redacted().
            privateContact = PrivateContactDetails(
                email = "s.siddiqui@example.test",
                phone = "+44 20 7946 0155",
                notes = "Prefers a call in the evening, after Isha.",
            ),
            preferredContactMethod = ContactMethod.IN_APP,
            verificationState = GuardianVerificationState.CONFIRMED,
            verifiedAt = EPOCH - 120.days,
            linkedUserId = zaynab,
            createdAt = EPOCH - 140.days,
            updatedAt = EPOCH - 120.days,
        )

        store.introductionSettings[aminah] = FormalIntroductionSettings(
            userId = aminah,
            enabled = true,
            guardianContactId = waliId,
            recipientReviewsFirst = true,
            guardianMustBeIncludedThroughout = true,
            allowIntermediaryInsteadOfWali = false,
            acceptRequestsFrom = AudienceScope.VERIFIED_ONLY,
            minimumVerification = VerificationLevel.IDENTITY_VERIFIED,
            requireSharedOrganization = true,
            intentionsStatement = "Looking to marry someone settled in their deen and their " +
                "work, who wants to stay near family in Ashbourne.",
            updatedAt = EPOCH - 30.days,
        )

        // Khadija nominated the imam as an intermediary rather than a family wali.
        val intermediaryId = TrustedContactId("contact-khadija-intermediary")
        store.trustedContacts[intermediaryId] = TrustedContact(
            id = intermediaryId,
            ownerId = khadija,
            name = "Imam Ibrahim Toure",
            relationship = GuardianRelationship.IMAM,
            role = TrustedContactRole.INTERMEDIARY,
            privateContact = PrivateContactDetails(email = "imam@northfield-masjid.example.test"),
            preferredContactMethod = ContactMethod.THROUGH_MASJID,
            verificationState = GuardianVerificationState.CONFIRMED,
            verifiedAt = EPOCH - 60.days,
            linkedUserId = ibrahim,
            createdAt = EPOCH - 70.days,
            updatedAt = EPOCH - 60.days,
        )
        store.introductionSettings[khadija] = FormalIntroductionSettings(
            userId = khadija,
            enabled = false,
            guardianContactId = intermediaryId,
            allowIntermediaryInsteadOfWali = true,
            updatedAt = EPOCH - 60.days,
        )
    }

    private fun seedLearning(store: InMemoryStore) {
        store.learningOfferings[ListingId("learn-arabic-beginners")] = LearningOffering(
            id = ListingId("learn-arabic-beginners"),
            title = "Arabic reading from zero — sisters",
            summary = "Eight weeks from the letters to reading short surahs with confidence. " +
                "No prior knowledge assumed and no one is put on the spot.",
            subject = LearningSubject.ARABIC_READING,
            level = LearningLevel.ABSOLUTE_BEGINNER,
            instructorId = khadija,
            instructorCapacity = TeachingCapacity.ARABIC_TUTOR,
            instructorVerification = VerificationLevel.BACKGROUND_CHECKED,
            organizationId = northfieldMasjid,
            communityId = arabicCircle,
            language = Language.ENGLISH,
            genderArrangement = GenderArrangement.SISTERS_ONLY,
            sameGenderStudentsOnly = true,
            format = DeliveryFormat.IN_PERSON,
            place = northfield,
            schedule = listOf(AvailabilityWindow(DayOfWeek.SUNDAY, LocalTime(11, 0), LocalTime(12, 30))),
            startsOn = EPOCH + 14.days,
            maxStudents = 12,
            enrolledCount = 7,
            cost = LearningCost.Free,
            requiredMaterials = listOf("Qaida (provided)", "A notebook"),
            learningObjectives = listOf(
                "Recognise and pronounce every letter in its forms",
                "Read short words with the harakat correctly",
                "Read Surah al-Fatiha from the mushaf",
            ),
            createdAt = EPOCH - 20.days,
            updatedAt = EPOCH - 20.days,
        )

        store.learningOfferings[ListingId("learn-tajwid")] = LearningOffering(
            id = ListingId("learn-tajwid"),
            title = "Tajwid foundations",
            summary = "The rules of recitation, taught slowly, with individual correction each week.",
            subject = LearningSubject.TAJWID,
            level = LearningLevel.BEGINNER,
            instructorId = ibrahim,
            instructorCapacity = TeachingCapacity.VERIFIED_SCHOLAR,
            instructorVerification = VerificationLevel.BACKGROUND_CHECKED,
            organizationId = northfieldMasjid,
            methodology = Methodology.NOT_MADHHAB_SPECIFIC,
            methodologyNotes = "Hafs 'an 'Asim.",
            language = Language.ENGLISH,
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            format = DeliveryFormat.HYBRID,
            place = northfield,
            schedule = listOf(AvailabilityWindow(DayOfWeek.TUESDAY, LocalTime(19, 30), LocalTime(21, 0))),
            maxStudents = 20,
            enrolledCount = 11,
            cost = LearningCost.SuggestedDonation(Money(500, "GBP")),
            learningObjectives = listOf(
                "Apply the rules of nun sakinah and tanwin",
                "Observe the madd correctly",
                "Recite Juz 'Amma with the rules applied",
            ),
            sourceReferences = listOf(
                SourceReference(
                    title = "Tuhfat al-Atfal",
                    author = "Sulayman al-Jamzuri",
                    note = "Memorised alongside the class, a few lines per week.",
                ),
            ),
            createdAt = EPOCH - 45.days,
            updatedAt = EPOCH - 6.days,
        )

        // Peer learning, labelled plainly as such.
        store.learningOfferings[ListingId("learn-new-muslim")] = LearningOffering(
            id = ListingId("learn-new-muslim"),
            title = "Finding your feet — new Muslim study partners",
            summary = "Members who took shahada in the last few years, sitting together weekly " +
                "and working through the basics. Questions beyond us go to the imam.",
            subject = LearningSubject.NEW_MUSLIM_FOUNDATIONS,
            level = LearningLevel.ABSOLUTE_BEGINNER,
            instructorId = yusuf,
            instructorCapacity = TeachingCapacity.PEER_HELPER,
            instructorVerification = VerificationLevel.BACKGROUND_CHECKED,
            organizationId = northfieldMasjid,
            communityId = newMuslimCircle,
            methodology = Methodology.NOT_MADHHAB_SPECIFIC,
            language = Language.ENGLISH,
            genderArrangement = GenderArrangement.BROTHERS_ONLY,
            format = DeliveryFormat.IN_PERSON,
            place = northfield,
            maxStudents = 8,
            enrolledCount = 3,
            cost = LearningCost.Free,
            isPeerLearning = true,
            learningObjectives = listOf(
                "Pray the five prayers with confidence",
                "Understand wudu and ghusl",
                "Know where to take a question you cannot answer",
            ),
            createdAt = EPOCH - 30.days,
            updatedAt = EPOCH - 4.days,
        )

        store.learningOfferings[ListingId("learn-electrical")] = LearningOffering(
            id = ListingId("learn-electrical"),
            title = "Getting into the electrical trade",
            summary = "What an apprenticeship actually involves, which qualifications matter, " +
                "and site days shadowing real work.",
            subject = LearningSubject.CAREER_SKILLS,
            level = LearningLevel.BEGINNER,
            instructorId = musa,
            instructorCapacity = TeachingCapacity.LICENSED_PROFESSIONAL,
            instructorVerification = VerificationLevel.IDENTITY_VERIFIED,
            language = Language.ENGLISH,
            genderArrangement = GenderArrangement.BROTHERS_ONLY,
            format = DeliveryFormat.IN_PERSON,
            place = riverside,
            maxStudents = 3,
            enrolledCount = 1,
            cost = LearningCost.Free,
            learningObjectives = listOf(
                "Understand the route to a Level 3 qualification",
                "Know what tools to buy first and what to wait on",
                "Complete two supervised site days",
            ),
            createdAt = EPOCH - 25.days,
            updatedAt = EPOCH - 25.days,
        )
    }

    private fun seedOpportunities(store: InMemoryStore) {
        store.opportunities[ListingId("opp-food-distribution")] = VolunteerOpportunity(
            id = ListingId("opp-food-distribution"),
            title = "Saturday food distribution",
            summary = "Packing and handing out food parcels to forty households. Steady work, " +
                "good company.",
            category = ServiceCategory.FOOD_DISTRIBUTION,
            organizerId = hafsa,
            organizationId = ashbourneRelief,
            beneficiaryType = BeneficiaryType.NEIGHBOURHOOD,
            place = riverside,
            format = DeliveryFormat.IN_PERSON,
            startsAt = EPOCH + 5.days,
            endsAt = EPOCH + 5.days + 4.hours,
            neededSkills = listOf(cateringSkill),
            volunteersNeeded = 10,
            volunteersConfirmed = 6,
            physicalRequirements = "Lifting boxes up to 15kg and standing for three hours.",
            safetyNotes = "Loading bay in use — high-visibility vests provided and required.",
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            transportProvided = false,
            expensesReimbursed = true,
            completionCriteria = "All forty parcels delivered and the hall left clear.",
            verificationStatus = ListingVerification.ORGANIZATION_BACKED,
            createdAt = EPOCH - 12.days,
            updatedAt = EPOCH - 1.days,
        )

        store.opportunities[ListingId("opp-masjid-cleanup")] = VolunteerOpportunity(
            id = ListingId("opp-masjid-cleanup"),
            title = "Masjid deep clean before Ramadan",
            summary = "Carpets, windows, wudu area and the store room. Bring gloves if you have them.",
            category = ServiceCategory.MASJID_MAINTENANCE,
            organizerId = yusuf,
            organizationId = northfieldMasjid,
            beneficiaryType = BeneficiaryType.MASJID,
            place = northfield,
            format = DeliveryFormat.IN_PERSON,
            startsAt = EPOCH + 9.days,
            endsAt = EPOCH + 9.days + 5.hours,
            volunteersNeeded = 15,
            volunteersConfirmed = 4,
            physicalRequirements = "Kneeling, lifting, and working at height on a step ladder.",
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            expensesReimbursed = false,
            completionCriteria = "Every room cleaned and the caretaker's checklist signed off.",
            verificationStatus = ListingVerification.ORGANIZATION_BACKED,
            createdAt = EPOCH - 8.days,
            updatedAt = EPOCH - 8.days,
        )

        store.opportunities[ListingId("opp-elder-transport")] = VolunteerOpportunity(
            id = ListingId("opp-elder-transport"),
            title = "Hospital transport for an elderly sister",
            summary = "A regular Wednesday lift to a hospital appointment and back. Sisters only, " +
                "arranged through the masjid.",
            category = ServiceCategory.TRANSPORTATION,
            organizerId = khadija,
            organizationId = northfieldMasjid,
            beneficiaryType = BeneficiaryType.INDIVIDUAL,
            place = eastgate,
            format = DeliveryFormat.IN_PERSON,
            startsAt = EPOCH + 3.days,
            endsAt = EPOCH + 3.days + 3.hours,
            neededSkills = listOf(drivingSkill),
            volunteersNeeded = 2,
            volunteersConfirmed = 0,
            physicalRequirements = "Helping someone in and out of a car.",
            safetyNotes = "Full licence and valid insurance required. The exact address is " +
                "shared only once you are confirmed.",
            backgroundCheckRequired = true,
            genderArrangement = GenderArrangement.SISTERS_ONLY,
            transportProvided = false,
            expensesReimbursed = true,
            completionCriteria = "Passenger collected, taken to the appointment, and returned home.",
            verificationStatus = ListingVerification.ORGANIZATION_BACKED,
            createdAt = EPOCH - 4.days,
            updatedAt = EPOCH - 4.days,
        )

        // The youth programme: mandatory checks, and the community floor on top.
        store.opportunities[ListingId("opp-youth-programme")] = VolunteerOpportunity(
            id = ListingId("opp-youth-programme"),
            title = "Saturday youth programme helper",
            summary = "Supporting the 11–16 programme: study help, sport, and setting up. " +
                "Two adults present at all times.",
            category = ServiceCategory.YOUTH_MENTORSHIP,
            organizerId = yusuf,
            organizationId = northfieldMasjid,
            communityId = youthCommunity,
            beneficiaryType = BeneficiaryType.ORGANIZATION,
            place = northfield,
            format = DeliveryFormat.IN_PERSON,
            startsAt = EPOCH + 5.days,
            endsAt = EPOCH + 5.days + 4.hours,
            volunteersNeeded = 6,
            volunteersConfirmed = 3,
            physicalRequirements = "Being on your feet, and joining in with sport if you want to.",
            safetyNotes = "Under the masjid's safeguarding policy. No one-to-one contact with " +
                "participants, and no private messaging.",
            backgroundCheckRequired = true,
            childSafeguardingRequired = true,
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            expensesReimbursed = true,
            completionCriteria = "Session run, register completed, hall reset.",
            verificationStatus = ListingVerification.ORGANIZATION_BACKED,
            createdAt = EPOCH - 15.days,
            updatedAt = EPOCH - 2.days,
        )

        store.opportunities[ListingId("opp-cv-clinic")] = VolunteerOpportunity(
            id = ListingId("opp-cv-clinic"),
            title = "CV and interview clinic",
            summary = "One-to-one half-hour sessions helping people rewrite a CV and practise " +
                "answering the obvious questions.",
            category = ServiceCategory.RESUME_ASSISTANCE,
            organizerId = aminah,
            organizationId = northfieldMasjid,
            beneficiaryType = BeneficiaryType.INDIVIDUAL,
            place = northfield,
            format = DeliveryFormat.HYBRID,
            startsAt = EPOCH + 7.days,
            endsAt = EPOCH + 7.days + 3.hours,
            neededSkills = listOf(cvSkill),
            volunteersNeeded = 4,
            volunteersConfirmed = 1,
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            expensesReimbursed = false,
            completionCriteria = "Each attendee leaves with a revised CV they can send out.",
            verificationStatus = ListingVerification.ORGANIZER_IDENTITY_VERIFIED,
            createdAt = EPOCH - 6.days,
            updatedAt = EPOCH - 6.days,
        )
    }

    private fun seedRequests(store: InMemoryStore) {
        // The address is present in the record and withheld from every read path until the
        // requester releases it to a specific person.
        store.serviceRequests[RequestId("req-transport-hospital")] = ServiceRequest(
            id = RequestId("req-transport-hospital"),
            requesterId = fatima,
            title = "Lift to a hospital appointment",
            description = "I have a regular appointment on Wednesday mornings and cannot manage " +
                "the buses any more. A sister to take me and bring me back would be a great help.",
            category = ServiceCategory.TRANSPORTATION,
            urgency = RequestUrgency.SOON,
            visibility = RequestVisibility.ANONYMOUS_TO_MEMBERS,
            place = eastgate.copy(
                exact = ExactLocation(
                    addressLine1 = "14 Eastgate Rise",
                    postalCode = "AS4 2QF",
                    latitude = 52.4655,
                    longitude = -1.9012,
                ),
            ),
            exactLocationDisclosed = false,
            mediatingOrganizationId = northfieldMasjid,
            peopleAffected = 1,
            expiresAt = EPOCH + 45.days,
            createdAt = EPOCH - 5.days,
            updatedAt = EPOCH - 5.days,
        )

        store.serviceRequests[RequestId("req-quran-reading")] = ServiceRequest(
            id = RequestId("req-quran-reading"),
            requesterId = daniel,
            title = "Someone patient to help me read Qur'an",
            description = "I became Muslim four months ago. I know the letters but I read very " +
                "slowly and I am embarrassed to try in front of people. Half an hour a week " +
                "would change things for me.",
            category = ServiceCategory.QURAN_SUPPORT,
            urgency = RequestUrgency.STANDARD,
            visibility = RequestVisibility.IDENTIFIED,
            place = riverside,
            expiresAt = EPOCH + 60.days,
            createdAt = EPOCH - 10.days,
            updatedAt = EPOCH - 10.days,
        )

        store.serviceRequests[RequestId("req-electrical-mentor")] = ServiceRequest(
            id = RequestId("req-electrical-mentor"),
            requesterId = abdullah,
            title = "Looking for an electrician to learn from",
            description = "I want to leave warehouse work and get into the electrical trade. " +
                "I have started a Level 2 but I need someone who will let me shadow real jobs " +
                "and tell me honestly whether I am any good at it.",
            category = ServiceCategory.TRADE_SKILLS_MENTORING,
            urgency = RequestUrgency.STANDARD,
            visibility = RequestVisibility.IDENTIFIED,
            place = riverside,
            expiresAt = EPOCH + 90.days,
            createdAt = EPOCH - 18.days,
            updatedAt = EPOCH - 18.days,
        )

        store.serviceRequests[RequestId("req-food-support")] = ServiceRequest(
            id = RequestId("req-food-support"),
            requesterId = daniel,
            title = "Food support for a few weeks",
            description = "My hours were cut with no notice and I am short until I find " +
                "something else. I would rather not say more than that.",
            category = ServiceCategory.FOOD_DISTRIBUTION,
            urgency = RequestUrgency.URGENT,
            visibility = RequestVisibility.ORGANIZATION_MEDIATED,
            place = riverside,
            mediatingOrganizationId = ashbourneRelief,
            peopleAffected = 1,
            maximumAssistance = Money(15_000, "GBP"),
            fraudReviewState = org.fisabilillah.core.model.FraudReviewState.CLEARED,
            expiresAt = EPOCH + 21.days,
            // Off, so that nobody's difficulty becomes a public fundraising thermometer.
            showSupportTotals = false,
            createdAt = EPOCH - 3.days,
            updatedAt = EPOCH - 2.days,
        )
    }

    private fun seedProjects(store: InMemoryStore) {
        store.projects[ProjectId("project-masjid-booking")] = Project(
            id = ProjectId("project-masjid-booking"),
            title = "Room booking system for the masjid",
            summary = "The current whiteboard causes double bookings every week. Building " +
                "something simple that the office can actually run.",
            category = ServiceCategory.TECHNOLOGY_SUPPORT,
            organizerId = yusuf,
            organizationId = northfieldMasjid,
            place = northfield,
            format = DeliveryFormat.HYBRID,
            startsAt = EPOCH - 30.days,
            targetCompletionAt = EPOCH + 60.days,
            neededSkills = listOf(softwareSkill),
            volunteersNeeded = 3,
            status = ProjectStatus.RECRUITING,
            createdAt = EPOCH - 35.days,
            updatedAt = EPOCH - 7.days,
        )

        store.projects[ProjectId("project-winter-clothing")] = Project(
            id = ProjectId("project-winter-clothing"),
            title = "Winter clothing drive",
            summary = "Collecting, sorting and distributing warm clothing before the cold sets in.",
            category = ServiceCategory.CLOTHING_DISTRIBUTION,
            organizerId = hafsa,
            organizationId = ashbourneRelief,
            place = riverside,
            format = DeliveryFormat.IN_PERSON,
            startsAt = EPOCH + 20.days,
            targetCompletionAt = EPOCH + 80.days,
            volunteersNeeded = 12,
            genderArrangement = GenderArrangement.SEPARATE_SESSIONS,
            status = ProjectStatus.PLANNING,
            createdAt = EPOCH - 14.days,
            updatedAt = EPOCH - 14.days,
        )
    }

    /**
     * A campaign that cannot take a penny.
     *
     * Verified organisation, verified campaign, and still no payment path — because
     * `DonationFeatureFlags.paymentsEnabled` is false until the compliance work in
     * `docs/payment-compliance.md` is done. Note also that zakat eligibility is absent
     * rather than assumed: no attestation, no claim.
     */
    private fun seedCampaign(store: InMemoryStore) {
        store.campaigns[CampaignId("campaign-winter-fund")] = Campaign(
            id = CampaignId("campaign-winter-fund"),
            organizationId = ashbourneRelief,
            title = "Winter hardship fund",
            summary = "Emergency support for households facing a cold winter without heating.",
            fundType = FundType.EMERGENCY_AID,
            goal = Money(1_500_000, "GBP"),
            raised = Money(0, "GBP"),
            currencyCode = "GBP",
            zakatEligible = false,
            zakatAttestation = null,
            restrictedFundNotes = "Funds are restricted to fuel vouchers, heaters, and warm " +
                "bedding. They cannot be spent on running costs.",
            verification = CampaignVerification.VERIFIED,
            allowsRecurring = false,
            allowsAnonymous = true,
            startsAt = EPOCH - 10.days,
            endsAt = EPOCH + 120.days,
            status = CampaignStatus.ACTIVE,
            createdAt = EPOCH - 15.days,
            updatedAt = EPOCH - 10.days,
        )
    }
}
