package org.fisabilillah.core.domain

import org.fisabilillah.core.model.Attestation
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.Community
import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.CommunityKind
import org.fisabilillah.core.model.CommunityMember
import org.fisabilillah.core.model.CommunityMemberRole
import org.fisabilillah.core.model.CommunityRule
import org.fisabilillah.core.model.DeliveryFormat
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.Language
import org.fisabilillah.core.model.LearningCost
import org.fisabilillah.core.model.LearningLevel
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.LearningSubject
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.MembershipPolicy
import org.fisabilillah.core.model.MembershipStatus
import org.fisabilillah.core.model.Methodology
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.Place
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ProjectId
import org.fisabilillah.core.model.ProjectMember
import org.fisabilillah.core.model.ProjectTask
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.SourceReference
import org.fisabilillah.core.model.TaskId
import org.fisabilillah.core.model.TaskStatus
import org.fisabilillah.core.model.TeachingCapacity
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.policy.ValidationError

/**
 * Making the things the platform is for.
 *
 * Learning offerings, projects, project tasks and communities were all readable and none
 * of them were creatable. The app could show you a class somebody had seeded and give you
 * no way to teach one, which is an odd shape for a platform whose stated purpose is
 * serving and learning.
 *
 * The care in this file is mostly about **what a person is allowed to say about
 * themselves**. A teacher declaring a capacity, a community declaring a safeguard floor
 * and an organiser declaring a gender arrangement are all statements other members will
 * rely on, so each one is checked here rather than trusted.
 */
public class CreateLearningOfferingUseCase(
    private val learning: LearningRepository,
    private val profiles: ProfileRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val title: String,
        val summary: String,
        val subject: LearningSubject,
        val level: LearningLevel,
        val capacity: TeachingCapacity,
        val format: DeliveryFormat,
        val maxStudents: Int,
        val language: Language = Language.ENGLISH,
        val methodology: Methodology? = null,
        val methodologyNotes: String? = null,
        val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
        val sameGenderStudentsOnly: Boolean = false,
        val organizationId: OrganizationId? = null,
        val communityId: CommunityId? = null,
        val place: Place? = null,
        val startsOn: Timestamp? = null,
        val cost: LearningCost = LearningCost.Free,
        val learningObjectives: List<String> = emptyList(),
        val sourceReferences: List<SourceReference> = emptyList(),
        val isPeerLearning: Boolean = false,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<LearningOffering> {
        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")

        val errors = buildList {
            if (command.title.isBlank()) add(ValidationError("title", "Give the class a title."))
            if (command.summary.isBlank()) {
                add(ValidationError("summary", "Say what students will actually study."))
            }
            if (command.maxStudents < 1) {
                add(ValidationError("maxStudents", "At least one place is needed."))
            }

            // A capacity is a claim about standing, and the ones the model marks as
            // needing review are not self-assignable. Somebody may teach what they know
            // without claiming to be a scholar; claiming to be one is a separate act with
            // its own review, and this is where the two are kept apart.
            //
            // The gate is the confirmed-qualification attestation rather than a role,
            // because that attestation is exactly what ReviewQualificationUseCase grants
            // and takes away — so a capacity claim follows the qualification rather than
            // outliving it.
            if (command.capacity.requiresQualificationReview &&
                Attestation.QUALIFICATION_VERIFIED !in profile.attestations
            ) {
                add(
                    ValidationError(
                        "capacity",
                        "Teaching as a ${command.capacity.displayName.lowercase()} is listed " +
                            "only for members whose qualification has been confirmed. You " +
                            "can offer this as a " +
                            "\"${TeachingCapacity.PEER_HELPER.displayName.lowercase()}\" " +
                            "meanwhile, and submit the qualification for review.",
                    ),
                )
            }

            // Anything in the religious subjects has to name what it follows and what it
            // rests on. A class on fiqh with no stated methodology and no sources is the
            // exact thing the platform's learning principles exist to prevent.
            if (command.subject.isReligiousInstruction) {
                if (command.methodology == null) {
                    add(
                        ValidationError(
                            "methodology",
                            "Say which approach you teach from. Students are entitled to " +
                                "know before they enrol, and this platform does not " +
                                "present one school as the default.",
                        ),
                    )
                }
                if (command.sourceReferences.isEmpty() && !command.isPeerLearning) {
                    add(
                        ValidationError(
                            "sourceReferences",
                            "Name at least one text or source this is taught from.",
                        ),
                    )
                }
            }
        }
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val now = clock.now()
        val saved = learning.save(
            LearningOffering(
                id = ListingId(ids.newId()),
                title = command.title.trim(),
                summary = command.summary.trim(),
                subject = command.subject,
                level = command.level,
                // From the principal. A teacher cannot list a class under somebody else.
                instructorId = principal.userId,
                instructorCapacity = command.capacity,
                // Read off the profile rather than accepted from the form: the badge next
                // to a teacher's name has to be the one the platform granted.
                instructorVerification = profile.verificationLevel,
                organizationId = command.organizationId,
                communityId = command.communityId,
                methodology = command.methodology,
                methodologyNotes = command.methodologyNotes?.trim()?.ifBlank { null },
                language = command.language,
                genderArrangement = command.genderArrangement,
                sameGenderStudentsOnly = command.sameGenderStudentsOnly,
                format = command.format,
                place = command.place,
                startsOn = command.startsOn,
                maxStudents = command.maxStudents,
                cost = command.cost,
                learningObjectives = command.learningObjectives.filter { it.isNotBlank() },
                sourceReferences = command.sourceReferences,
                isPeerLearning = command.isPeerLearning,
                createdAt = now,
                updatedAt = now,
            ),
        )

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.ACCOUNT_STATUS_CHANGED,
                subjectType = "learning_offering",
                subjectId = saved.id.value,
                summary = "Published \"${saved.title}\" as ${command.capacity.displayName}",
                occurredAt = now,
            ),
        )
        return Outcome.Success(saved)
    }
}

/** Starting a piece of work several people will do together. */
public class CreateProjectUseCase(
    private val projects: ProjectRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val title: String,
        val summary: String,
        val category: ServiceCategory,
        val format: DeliveryFormat = DeliveryFormat.HYBRID,
        val place: Place? = null,
        val organizationId: OrganizationId? = null,
        val communityId: CommunityId? = null,
        val startsAt: Timestamp? = null,
        val targetCompletionAt: Timestamp? = null,
        val volunteersNeeded: Int = 0,
        val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
        val isPubliclyListed: Boolean = true,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<Project> {
        val errors = buildList {
            if (command.title.isBlank()) add(ValidationError("title", "Give the project a name."))
            if (command.summary.isBlank()) {
                add(ValidationError("summary", "Say what the project is trying to achieve."))
            }
            if (command.volunteersNeeded < 0) {
                add(ValidationError("volunteersNeeded", "That cannot be negative."))
            }
            val start = command.startsAt
            val end = command.targetCompletionAt
            if (start != null && end != null && end < start) {
                add(ValidationError("targetCompletionAt", "The target date is before the start."))
            }
        }
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val now = clock.now()
        val saved = projects.save(
            Project(
                id = ProjectId(ids.newId()),
                title = command.title.trim(),
                summary = command.summary.trim(),
                category = command.category,
                organizerId = principal.userId,
                organizationId = command.organizationId,
                communityId = command.communityId,
                place = command.place,
                format = command.format,
                startsAt = command.startsAt,
                targetCompletionAt = command.targetCompletionAt,
                volunteersNeeded = command.volunteersNeeded,
                genderArrangement = command.genderArrangement,
                isPubliclyListed = command.isPubliclyListed,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // The organiser is a member of their own project. Without this the project has
        // tasks nobody can be assigned to and a members list that omits the one person
        // certainly involved.
        projects.saveMember(
            ProjectMember(
                projectId = saved.id,
                userId = principal.userId,
                role = org.fisabilillah.core.model.ProjectRole.LEAD,
                joinedAt = now,
            ),
        )
        return Outcome.Success(saved)
    }
}

/** Tasks inside a project, and claiming one. */
public class ProjectTaskUseCase(
    private val projects: ProjectRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val projectId: ProjectId,
        val title: String,
        val detail: String? = null,
        val dueAt: Timestamp? = null,
        val assigneeId: UserId? = null,
    )

    public suspend fun create(principal: Principal, command: Command): Outcome<ProjectTask> {
        val project = projects.find(command.projectId) ?: return Outcome.NotFound("that project")
        if (!isOrganiser(project, principal)) {
            return Outcome.refused("Only the people organising this project can add tasks.")
        }
        if (command.title.isBlank()) {
            return Outcome.invalid("title", "Say what needs doing.")
        }

        val now = clock.now()
        val task = projects.saveTask(
            ProjectTask(
                id = TaskId(ids.newId()),
                projectId = command.projectId,
                title = command.title.trim(),
                detail = command.detail?.trim()?.ifBlank { null },
                assigneeId = command.assigneeId,
                dueAt = command.dueAt,
                status = if (command.assigneeId != null) TaskStatus.CLAIMED else TaskStatus.OPEN,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (command.assigneeId != null) {
            notifications.add(
                Notification(
                    id = NotificationId(ids.newId()),
                    userId = command.assigneeId,
                    kind = NotificationKind.PROJECT_UPDATE,
                    title = "A task was assigned to you",
                    body = "${project.title}: ${task.title}",
                    deepLink = "fisabilillah://project/${project.id.value}",
                    createdAt = now,
                ),
            )
        }
        return Outcome.Success(task)
    }

    /** A member taking a task on. Open tasks only: claiming is not a way to take one over. */
    public suspend fun claim(principal: Principal, taskId: TaskId): Outcome<ProjectTask> {
        val task = projects.findTask(taskId) ?: return Outcome.NotFound("that task")
        if (task.status != TaskStatus.OPEN) {
            return Outcome.refused("Somebody is already working on that one.")
        }
        val now = clock.now()
        return Outcome.Success(
            projects.saveTask(
                task.copy(
                    assigneeId = principal.userId,
                    status = TaskStatus.CLAIMED,
                    updatedAt = now,
                ),
            ),
        )
    }

    /**
     * Moving a task along.
     *
     * The person holding it, or an organiser. Not anybody who happens to be looking at the
     * project: marking somebody else's work done is a way to make a project look finished
     * when it is not, and the people who would notice are the ones relying on it.
     */
    public suspend fun setStatus(
        principal: Principal,
        taskId: TaskId,
        status: TaskStatus,
    ): Outcome<ProjectTask> {
        val task = projects.findTask(taskId) ?: return Outcome.NotFound("that task")
        val project = projects.find(task.projectId) ?: return Outcome.NotFound("that project")
        val mine = task.assigneeId == principal.userId
        if (!mine && !isOrganiser(project, principal)) {
            return Outcome.refused("Only the person doing this task or an organiser can change it.")
        }
        val now = clock.now()
        return Outcome.Success(
            projects.saveTask(task.copy(status = status, updatedAt = now)),
        )
    }

    public suspend fun tasksFor(projectId: ProjectId): List<ProjectTask> = projects.tasks(projectId)

    private suspend fun isOrganiser(project: Project, principal: Principal): Boolean =
        project.organizerId == principal.userId ||
            projects.members(project.id).any {
                it.userId == principal.userId && it.role.canAssignTasks
            }
}

/**
 * Starting a community, and joining one.
 *
 * A community carries a [org.fisabilillah.core.model.SafeguardFloor], which is the one
 * thing on this screen that reaches into other people's settings: a floor tightens what
 * members may do inside that space. It can therefore only ever *raise* protection, and the
 * founder is told so rather than discovering it.
 */
public class CreateCommunityUseCase(
    private val communities: CommunityRepository,
    private val profiles: ProfileRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val name: String,
        val kind: CommunityKind,
        val summary: String,
        val membershipPolicy: MembershipPolicy = MembershipPolicy.APPROVAL_REQUIRED,
        val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
        val organizationId: OrganizationId? = null,
        val place: Place? = null,
        val rules: List<CommunityRule> = emptyList(),
        val safeguardFloor: org.fisabilillah.core.model.SafeguardFloor =
            org.fisabilillah.core.model.SafeguardFloor.NONE,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<Community> {
        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")

        val errors = buildList {
            if (command.name.isBlank()) add(ValidationError("name", "Give the community a name."))
            if (command.summary.isBlank()) {
                add(ValidationError("summary", "Say who this space is for and what happens in it."))
            }
            if (command.rules.any { it.title.isBlank() }) {
                add(ValidationError("rules", "Every rule needs a heading."))
            }
            // A single-gender space founded by somebody who could not themselves be in it
            // is almost always a mistake, and occasionally something worse.
            val single = command.genderArrangement == GenderArrangement.SISTERS_ONLY ||
                command.genderArrangement == GenderArrangement.BROTHERS_ONLY
            if (single) {
                val matches = when (command.genderArrangement) {
                    GenderArrangement.SISTERS_ONLY -> profile.gender == Gender.FEMALE
                    GenderArrangement.BROTHERS_ONLY -> profile.gender == Gender.MALE
                    else -> true
                }
                if (!matches) {
                    add(
                        ValidationError(
                            "genderArrangement",
                            "You cannot found a space you would not be able to enter. Ask " +
                                "somebody who can to set it up, or choose a different " +
                                "arrangement.",
                        ),
                    )
                }
            }
        }
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val now = clock.now()
        val saved = communities.save(
            Community(
                id = CommunityId(ids.newId()),
                name = command.name.trim(),
                kind = command.kind,
                summary = command.summary.trim(),
                organizationId = command.organizationId,
                place = command.place,
                membershipPolicy = command.membershipPolicy,
                rules = command.rules,
                genderArrangement = command.genderArrangement,
                safeguardFloor = command.safeguardFloor,
                moderatorIds = setOf(principal.userId),
                memberCount = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        communities.saveMember(
            CommunityMember(
                communityId = saved.id,
                userId = principal.userId,
                role = CommunityMemberRole.MODERATOR,
                status = MembershipStatus.ACTIVE,
                joinedAt = now,
            ),
        )

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.ACCOUNT_STATUS_CHANGED,
                subjectType = "community",
                subjectId = saved.id.value,
                summary = "Founded \"${saved.name}\"",
                occurredAt = now,
            ),
        )
        return Outcome.Success(saved)
    }
}

/** Asking to join a community, and the moderators deciding. */
public class CommunityMembershipUseCase(
    private val communities: CommunityRepository,
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend fun join(principal: Principal, communityId: CommunityId): Outcome<CommunityMember> {
        val community = communities.find(communityId) ?: return Outcome.NotFound("that community")
        if (community.isArchived) return Outcome.refused("That community is closed.")

        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")

        // The gender arrangement is checked here rather than left to the moderators. A
        // single-gender space that relies on a human noticing is a single-gender space
        // that will eventually not be one.
        val allowed = when (community.genderArrangement) {
            GenderArrangement.SISTERS_ONLY -> profile.gender == Gender.FEMALE
            GenderArrangement.BROTHERS_ONLY -> profile.gender == Gender.MALE
            else -> true
        }
        if (!allowed) {
            return Outcome.refused("This space is not open to you.")
        }

        val existing = communities.members(communityId).firstOrNull { it.userId == principal.userId }
        if (existing != null && existing.status == MembershipStatus.ACTIVE) {
            return Outcome.refused("You are already a member.")
        }
        if (existing != null && existing.status == MembershipStatus.REMOVED) {
            // Somebody removed by a moderator does not re-join by tapping a button.
            return Outcome.refused("You cannot rejoin this community.")
        }
        if (community.membershipPolicy == MembershipPolicy.INVITE_ONLY) {
            return Outcome.refused("This community is by invitation.")
        }

        val now = clock.now()
        val member = CommunityMember(
            communityId = communityId,
            userId = principal.userId,
            role = CommunityMemberRole.MEMBER,
            status = if (community.membershipPolicy == MembershipPolicy.OPEN) {
                MembershipStatus.ACTIVE
            } else {
                MembershipStatus.PENDING
            },
            joinedAt = now,
        )
        communities.saveMember(member)

        if (member.status == MembershipStatus.PENDING) {
            for (moderator in community.moderatorIds) {
                notifications.add(
                    Notification(
                        id = NotificationId(ids.newId()),
                        userId = moderator,
                        kind = NotificationKind.COMMUNITY_ANNOUNCEMENT,
                        title = "Someone asked to join ${community.name}",
                        body = profile.displayName,
                        deepLink = "fisabilillah://community/${communityId.value}",
                        createdAt = now,
                    ),
                )
            }
        } else {
            communities.save(community.copy(memberCount = community.memberCount + 1, updatedAt = now))
        }
        return Outcome.Success(member)
    }

    public suspend fun decide(
        principal: Principal,
        communityId: CommunityId,
        applicantId: UserId,
        approved: Boolean,
    ): Outcome<CommunityMember> {
        val community = communities.find(communityId) ?: return Outcome.NotFound("that community")
        if (principal.userId !in community.moderatorIds && !principal.isModerator) {
            return Outcome.refused("Only this community's moderators can decide who joins.")
        }
        val member = communities.members(communityId).firstOrNull { it.userId == applicantId }
            ?: return Outcome.NotFound("that request")
        if (member.status != MembershipStatus.PENDING) {
            return Outcome.refused("That request has already been decided.")
        }

        val now = clock.now()
        val updated = member.copy(
            status = if (approved) MembershipStatus.ACTIVE else MembershipStatus.DECLINED,
            joinedAt = if (approved) now else member.joinedAt,
        )
        communities.saveMember(updated)
        if (approved) {
            communities.save(community.copy(memberCount = community.memberCount + 1, updatedAt = now))
        }
        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = applicantId,
                kind = NotificationKind.COMMUNITY_ANNOUNCEMENT,
                title = if (approved) {
                    "You have joined ${community.name}"
                } else {
                    "Your request to join ${community.name} was not approved"
                },
                body = community.summary,
                deepLink = "fisabilillah://community/${communityId.value}",
                createdAt = now,
            ),
        )
        return Outcome.Success(updated)
    }

    /** Leaving. Always permitted, and never requires anybody's agreement. */
    public suspend fun leave(principal: Principal, communityId: CommunityId): Outcome<Unit> {
        val community = communities.find(communityId) ?: return Outcome.NotFound("that community")
        val member = communities.members(communityId).firstOrNull { it.userId == principal.userId }
            ?: return Outcome.refused("You are not a member of that community.")

        val now = clock.now()
        communities.saveMember(member.copy(status = MembershipStatus.LEFT, removedAt = now))
        if (member.status == MembershipStatus.ACTIVE) {
            communities.save(
                community.copy(
                    memberCount = (community.memberCount - 1).coerceAtLeast(0),
                    updatedAt = now,
                ),
            )
        }
        return Outcome.Success(Unit)
    }
}
