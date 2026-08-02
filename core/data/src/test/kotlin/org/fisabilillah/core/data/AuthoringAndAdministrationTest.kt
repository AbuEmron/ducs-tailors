package org.fisabilillah.core.data

import kotlinx.coroutines.test.runTest
import org.fisabilillah.core.domain.CreateCommunityUseCase
import org.fisabilillah.core.domain.CreateLearningOfferingUseCase
import org.fisabilillah.core.domain.CreateProjectUseCase
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.domain.Principal
import org.fisabilillah.core.domain.ProjectTaskUseCase
import org.fisabilillah.core.domain.StartConversationCommand
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.AccountStatus
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.CommunityKind
import org.fisabilillah.core.model.ConversationState
import org.fisabilillah.core.model.DeliveryFormat
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.LearningLevel
import org.fisabilillah.core.model.LearningSubject
import org.fisabilillah.core.model.MembershipPolicy
import org.fisabilillah.core.model.MembershipStatus
import org.fisabilillah.core.model.Methodology
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.SourceReference
import org.fisabilillah.core.model.TaskStatus
import org.fisabilillah.core.model.TeachingCapacity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

/**
 * The things the platform is for can now be made, and staff can now be appointed.
 *
 * Learning offerings, projects, tasks and communities were all readable and none creatable
 * — the app could show you a class somebody had seeded and give you no way to teach one.
 * And no moderator could be appointed except from a database console, on a platform whose
 * entire safety design rests on human moderators.
 */
@DisplayName("Authoring and administration")
class AuthoringAndAdministrationTest {

    private fun Harness.admin() =
        Principal(SeedData.safetyAdmin, setOf(AccountRole.PLATFORM_ADMINISTRATOR))

    // ── Learning ──────────────────────────────────────────────────────────────

    private fun arabicClass(
        capacity: TeachingCapacity = TeachingCapacity.PEER_HELPER,
        subject: LearningSubject = LearningSubject.ARABIC_READING,
        methodology: Methodology? = null,
        sources: List<SourceReference> = emptyList(),
    ) = CreateLearningOfferingUseCase.Command(
        title = "Beginners' Arabic, Saturday mornings",
        summary = "The alphabet, then reading simple sentences aloud.",
        subject = subject,
        level = LearningLevel.BEGINNER,
        capacity = capacity,
        format = DeliveryFormat.IN_PERSON,
        maxStudents = 12,
        methodology = methodology,
        sourceReferences = sources,
    )

    @Test
    @DisplayName("a member can publish a class, and it is listed under their own name")
    fun aMemberCanTeach() = runTest {
        val harness = Harness()
        val saved = harness.graph.createLearningOffering(
            harness.principal(SeedData.daniel),
            arabicClass(),
        ).expectSuccess()

        assertEquals(SeedData.daniel, saved.instructorId)
        assertNotNull(harness.graph.learning.find(saved.id))
    }

    @Test
    @DisplayName("the instructor badge is read off the profile, never off the form")
    fun theBadgeIsTheOneThePlatformGranted() = runTest {
        val harness = Harness()
        val expected = harness.graph.profiles.find(SeedData.daniel)!!.verificationLevel

        val saved = harness.graph.createLearningOffering(
            harness.principal(SeedData.daniel),
            arabicClass(),
        ).expectSuccess()

        assertEquals(expected, saved.instructorVerification)
    }

    @Test
    @DisplayName("a capacity that needs review cannot be self-assigned")
    fun capacityClaimsAreNotSelfService() = runTest {
        val harness = Harness()
        val invalid = harness.graph.createLearningOffering(
            harness.principal(SeedData.daniel),
            arabicClass(capacity = TeachingCapacity.VERIFIED_SCHOLAR),
        ) as Outcome.Invalid

        assertEquals("capacity", invalid.errors.single().field)
        assertTrue(invalid.errors.single().message.contains("qualification has been confirmed"))
    }

    @Test
    @DisplayName("a member whose qualification was confirmed may claim the capacity")
    fun aConfirmedQualificationUnlocksIt() = runTest {
        val harness = Harness()
        // Yusuf's seeded profile carries the confirmed-qualification attestation.
        val saved = harness.graph.createLearningOffering(
            harness.principal(SeedData.yusuf),
            arabicClass(capacity = TeachingCapacity.ARABIC_TUTOR),
        ).expectSuccess()

        assertEquals(TeachingCapacity.ARABIC_TUTOR, saved.instructorCapacity)
    }

    @Test
    @DisplayName("religious instruction must say what it follows and what it rests on")
    fun religiousSubjectsNeedAMethodologyAndSources() = runTest {
        val harness = Harness()
        val invalid = harness.graph.createLearningOffering(
            harness.principal(SeedData.daniel),
            arabicClass(subject = LearningSubject.FIQH),
        ) as Outcome.Invalid

        val fields = invalid.errors.map { it.field }.toSet()
        assertEquals(setOf("methodology", "sourceReferences"), fields)
    }

    @Test
    @DisplayName("with both stated, the same class is accepted")
    fun aWellDeclaredReligiousClassIsFine() = runTest {
        val harness = Harness()
        val saved = harness.graph.createLearningOffering(
            harness.principal(SeedData.daniel),
            arabicClass(
                subject = LearningSubject.FIQH,
                methodology = Methodology.HANAFI,
                sources = listOf(
                    SourceReference(title = "Nur al-Idah", author = "al-Shurunbulali"),
                ),
            ),
        ).expectSuccess()

        assertEquals(Methodology.HANAFI, saved.methodology)
    }

    // ── Projects and tasks ────────────────────────────────────────────────────

    @Test
    @DisplayName("creating a project makes the organiser a member of it")
    fun theOrganiserIsAMember() = runTest {
        val harness = Harness()
        val project = harness.graph.createProject(
            harness.principal(SeedData.daniel),
            CreateProjectUseCase.Command(
                title = "Winter coat collection",
                summary = "Collecting and sorting coats for the shelter before December.",
                category = ServiceCategory.FOOD_DISTRIBUTION,
            ),
        ).expectSuccess()

        val members = harness.graph.projects.members(project.id)
        assertEquals(1, members.size)
        assertEquals(SeedData.daniel, members.single().userId)
        assertTrue(members.single().role.canAssignTasks)
    }

    @Test
    @DisplayName("only an organiser adds tasks, anyone may claim an open one")
    fun tasksHaveAnOwner() = runTest {
        val harness = Harness()
        val organiser = harness.principal(SeedData.daniel)
        val project = harness.graph.createProject(
            organiser,
            CreateProjectUseCase.Command(
                title = "Winter coat collection",
                summary = "Collecting and sorting coats.",
                category = ServiceCategory.FOOD_DISTRIBUTION,
            ),
        ).expectSuccess()

        assertTrue(
            harness.graph.projectTasks.create(
                harness.principal(SeedData.abdullah),
                ProjectTaskUseCase.Command(project.id, "Book the van"),
            ).expectRefused().message.contains("organising this project"),
        )

        val task = harness.graph.projectTasks.create(
            organiser,
            ProjectTaskUseCase.Command(project.id, "Book the van"),
        ).expectSuccess()
        assertEquals(TaskStatus.OPEN, task.status)

        val claimed = harness.graph.projectTasks
            .claim(harness.principal(SeedData.abdullah), task.id)
            .expectSuccess()
        assertEquals(SeedData.abdullah, claimed.assigneeId)
        assertEquals(TaskStatus.CLAIMED, claimed.status)

        // And nobody else can take it over.
        assertTrue(
            harness.graph.projectTasks
                .claim(harness.principal(SeedData.yusuf), task.id)
                .expectRefused().message.contains("already working on that one"),
        )
    }

    @Test
    @DisplayName("a bystander cannot mark somebody else's task done")
    fun onlyTheHolderOrAnOrganiserMovesATask() = runTest {
        val harness = Harness()
        val organiser = harness.principal(SeedData.daniel)
        val project = harness.graph.createProject(
            organiser,
            CreateProjectUseCase.Command(
                title = "Winter coat collection",
                summary = "Collecting and sorting coats.",
                category = ServiceCategory.FOOD_DISTRIBUTION,
            ),
        ).expectSuccess()
        val task = harness.graph.projectTasks
            .create(organiser, ProjectTaskUseCase.Command(project.id, "Book the van"))
            .expectSuccess()
        harness.graph.projectTasks.claim(harness.principal(SeedData.abdullah), task.id)
            .expectSuccess()

        assertTrue(
            harness.graph.projectTasks
                .setStatus(harness.principal(SeedData.yusuf), task.id, TaskStatus.DONE)
                .expectRefused().message.contains("person doing this task or an organiser"),
        )
        harness.graph.projectTasks
            .setStatus(harness.principal(SeedData.abdullah), task.id, TaskStatus.DONE)
            .expectSuccess()
    }

    // ── Communities ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("founding a community makes the founder its moderator and first member")
    fun theFounderModerates() = runTest {
        val harness = Harness()
        val community = harness.graph.createCommunity(
            harness.principal(SeedData.daniel),
            CreateCommunityUseCase.Command(
                name = "Northfield new Muslims",
                kind = CommunityKind.NEW_MUSLIM_SUPPORT,
                summary = "A place to ask the questions you feel silly asking.",
            ),
        ).expectSuccess()

        assertTrue(SeedData.daniel in community.moderatorIds)
        assertEquals(1, community.memberCount)
        assertEquals(
            MembershipStatus.ACTIVE,
            harness.graph.communities.members(community.id).single().status,
        )
    }

    @Test
    @DisplayName("you cannot found a space you would not be able to enter")
    fun aSingleGenderSpaceNeedsAMatchingFounder() = runTest {
        val harness = Harness()
        // Daniel is male; a sisters-only space is not his to set up.
        val invalid = harness.graph.createCommunity(
            harness.principal(SeedData.daniel),
            CreateCommunityUseCase.Command(
                name = "Sisters' circle",
                kind = CommunityKind.LEARNING_CIRCLE,
                summary = "A weekly halaqa.",
                genderArrangement = GenderArrangement.SISTERS_ONLY,
            ),
        ) as Outcome.Invalid

        assertEquals("genderArrangement", invalid.errors.single().field)
    }

    @Test
    @DisplayName("an open community admits at once; an approval one waits and tells the moderators")
    fun joiningFollowsThePolicy() = runTest {
        val harness = Harness()
        val founder = harness.principal(SeedData.daniel)

        val open = harness.graph.createCommunity(
            founder,
            CreateCommunityUseCase.Command(
                name = "Northfield volunteers",
                kind = CommunityKind.VOLUNTEER_TEAM,
                summary = "Anyone who wants to help out.",
                membershipPolicy = MembershipPolicy.OPEN,
            ),
        ).expectSuccess()

        val joined = harness.graph.communityMembership
            .join(harness.principal(SeedData.abdullah), open.id)
            .expectSuccess()
        assertEquals(MembershipStatus.ACTIVE, joined.status)

        val gated = harness.graph.createCommunity(
            founder,
            CreateCommunityUseCase.Command(
                name = "Northfield mentors",
                kind = CommunityKind.VOLUNTEER_TEAM,
                summary = "Mentors working with newcomers.",
                membershipPolicy = MembershipPolicy.APPROVAL_REQUIRED,
            ),
        ).expectSuccess()

        val pending = harness.graph.communityMembership
            .join(harness.principal(SeedData.abdullah), gated.id)
            .expectSuccess()
        assertEquals(MembershipStatus.PENDING, pending.status)
        assertTrue(
            harness.store.notifications.any {
                it.userId == SeedData.daniel && it.title.contains("asked to join")
            },
        )

        val approved = harness.graph.communityMembership
            .decide(founder, gated.id, SeedData.abdullah, approved = true)
            .expectSuccess()
        assertEquals(MembershipStatus.ACTIVE, approved.status)
    }

    @Test
    @DisplayName("a single-gender community is enforced on joining, not left to the moderators")
    fun genderIsEnforcedAtTheDoor() = runTest {
        val harness = Harness()
        val community = harness.graph.createCommunity(
            harness.principal(SeedData.khadija),
            CreateCommunityUseCase.Command(
                name = "Sisters' circle",
                kind = CommunityKind.LEARNING_CIRCLE,
                summary = "A weekly halaqa.",
                genderArrangement = GenderArrangement.SISTERS_ONLY,
                membershipPolicy = MembershipPolicy.OPEN,
            ),
        ).expectSuccess()

        assertTrue(
            harness.graph.communityMembership
                .join(harness.principal(SeedData.abdullah), community.id)
                .expectRefused().message.contains("not open to you"),
        )
    }

    @Test
    @DisplayName("leaving is always allowed and needs nobody's agreement")
    fun leavingIsUnconditional() = runTest {
        val harness = Harness()
        val community = harness.graph.createCommunity(
            harness.principal(SeedData.daniel),
            CreateCommunityUseCase.Command(
                name = "Northfield volunteers",
                kind = CommunityKind.VOLUNTEER_TEAM,
                summary = "Anyone who wants to help out.",
                membershipPolicy = MembershipPolicy.OPEN,
            ),
        ).expectSuccess()
        harness.graph.communityMembership.join(harness.principal(SeedData.abdullah), community.id)
            .expectSuccess()

        harness.graph.communityMembership.leave(harness.principal(SeedData.abdullah), community.id)
            .expectSuccess()

        assertEquals(
            MembershipStatus.LEFT,
            harness.graph.communities.members(community.id)
                .single { it.userId == SeedData.abdullah }.status,
        )
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an administrator can appoint a moderator, and it is recorded")
    fun aModeratorCanBeAppointed() = runTest {
        val harness = Harness()
        val updated = harness.graph.manageRoles.grant(
            harness.admin(),
            SeedData.daniel,
            AccountRole.MODERATOR,
            "Joining the safety rota from Monday.",
        ).expectSuccess()

        assertTrue(AccountRole.MODERATOR in updated.roles)
        assertTrue(
            harness.graph.auditLog.recent(50).any { it.action == AuditAction.ROLE_GRANTED },
        )
        assertTrue(
            harness.store.notifications.any {
                it.userId == SeedData.daniel && it.title.contains("Moderator")
            },
        )
    }

    @Test
    @DisplayName("nobody edits the roles on their own account")
    fun noSelfPromotion() = runTest {
        val harness = Harness()
        val admin = harness.admin()

        assertTrue(
            harness.graph.manageRoles
                .grant(admin, admin.userId, AccountRole.SCHOLAR, "I am one")
                .expectRefused().message.contains("your own account"),
        )
        assertTrue(
            harness.graph.manageRoles
                .revoke(admin, admin.userId, AccountRole.PLATFORM_ADMINISTRATOR, "Stepping back")
                .expectRefused().message.contains("your own account"),
        )
    }

    @Test
    @DisplayName("an ordinary member cannot grant roles, and a reason is always required")
    fun grantsAreAdministratorOnlyAndReasoned() = runTest {
        val harness = Harness()

        assertTrue(
            harness.graph.manageRoles
                .grant(
                    harness.principal(SeedData.daniel),
                    SeedData.abdullah,
                    AccountRole.MODERATOR,
                    "Because",
                )
                .expectRefused().message.contains("platform administrator"),
        )

        val invalid = harness.graph.manageRoles
            .grant(harness.admin(), SeedData.daniel, AccountRole.MODERATOR, "   ") as Outcome.Invalid
        assertEquals("reason", invalid.errors.single().field)
    }

    @Test
    @DisplayName("the last platform administrator cannot be removed")
    fun theLastAdministratorStays() = runTest {
        val harness = Harness()
        val admin = harness.admin()

        // Nobody in the seed holds the role, so the test appoints the first one.
        harness.graph.manageRoles.grant(
            admin,
            SeedData.daniel,
            AccountRole.PLATFORM_ADMINISTRATOR,
            "First administrator on this installation.",
        ).expectSuccess()

        // Daniel is now the only holder, and removing him would leave nobody able to
        // appoint anybody — a state only a database console could get out of.
        val refusal = harness.graph.manageRoles.revoke(
            admin,
            SeedData.daniel,
            AccountRole.PLATFORM_ADMINISTRATOR,
            "Clearing out.",
        ).expectRefused()
        assertTrue(refusal.message.contains("last platform administrator"))

        // With cover appointed, the same removal goes through.
        harness.graph.manageRoles.grant(
            admin,
            SeedData.abdullah,
            AccountRole.PLATFORM_ADMINISTRATOR,
            "Second administrator so there is cover.",
        ).expectSuccess()
        harness.graph.manageRoles.revoke(
            admin,
            SeedData.daniel,
            AccountRole.PLATFORM_ADMINISTRATOR,
            "Handing over.",
        ).expectSuccess()
    }

    // ── Account data ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("an export says what it contains and what it deliberately does not")
    fun theExportIsHonestAboutItsGaps() = runTest {
        val harness = Harness()
        val export = harness.graph.exportMyData(harness.principal(SeedData.aminah))
            .expectSuccess()

        assertEquals(SeedData.aminah, export.profile.id)
        assertTrue(export.whatIsNotIncluded.any { it.contains("Reports made about you") })
        assertTrue(export.whatIsNotIncluded.any { it.contains("guardian's contact details") })
        assertTrue(
            harness.graph.auditLog.recent(50).any { it.action == AuditAction.DATA_EXPORTED },
            "an export is a disclosure and is logged like one",
        )
    }

    @Test
    @DisplayName("deletion is requested, states what survives, and can be cancelled")
    fun deletionHasAGracePeriod() = runTest {
        val harness = Harness()
        val member = harness.principal(SeedData.daniel)

        val result = harness.graph.requestAccountDeletion(member, "Taking a break.")
            .expectSuccess()

        assertEquals(AccountStatus.DELETION_REQUESTED, result.profile.status)
        assertTrue(result.contentRemovedAfter > harness.clock.now())
        assertTrue(result.whatIsRetained.any { it.contains("erase somebody else's evidence") })

        assertTrue(
            harness.graph.requestAccountDeletion(member, null)
                .expectRefused().message.contains("already scheduled"),
        )

        val restored = harness.graph.requestAccountDeletion.cancel(member).expectSuccess()
        assertEquals(AccountStatus.ACTIVE, restored.status)
    }

    // ── Scheduled maintenance ─────────────────────────────────────────────────

    @Test
    @DisplayName("maintenance runs as the platform, not as a member")
    fun maintenanceIsNotAMemberAction() = runTest {
        val harness = Harness()
        assertTrue(
            harness.graph.scheduledMaintenance(harness.principal(SeedData.daniel))
                .expectRefused().message.contains("as the platform"),
        )
    }

    @Test
    @DisplayName("a conversation past its archive deadline is archived, and only then")
    fun theArchiveDeadlineIsFinallyRead() = runTest {
        val harness = Harness()
        val admin = harness.admin()

        val opened = harness.graph.startConversation(
            harness.principal(SeedData.abdullah),
            StartConversationCommand(
                recipientId = SeedData.yusuf,
                purpose = harness.purpose(),
                openingMessage = "I can bring ladders on Saturday.",
            ),
        ).expectSuccess().conversation

        // The deadline has been written on conversations since the first release and read
        // by nothing, so the test sets one and then checks it is finally honoured.
        val thread = harness.graph.conversations.save(
            opened.copy(archiveAfter = harness.clock.now() + 30.days),
        )

        val before = harness.graph.scheduledMaintenance(admin).expectSuccess()
        assertFalse(before.didAnything, "the deadline has not passed yet")

        harness.clock.advance(31.days)
        val after = harness.graph.scheduledMaintenance(admin).expectSuccess()

        assertEquals(1, after.conversationsArchived)
        assertEquals(
            ConversationState.ARCHIVED,
            harness.store.conversations.getValue(thread.id).state,
        )
        assertTrue(
            harness.graph.auditLog.recent(50).any { it.subjectType == "maintenance" },
        )
    }
}
