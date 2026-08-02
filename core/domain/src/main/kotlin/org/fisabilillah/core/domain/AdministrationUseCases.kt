package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.ConversationState
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.NotificationId
import org.fisabilillah.core.model.NotificationKind
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.UserId

/**
 * Appointing and removing staff.
 *
 * Until this existed, no moderator could be appointed except by somebody with direct
 * database access — which meant that a platform whose entire safety design rests on human
 * moderators had no way, inside itself, to have any.
 *
 * The rules here are the ones that stop an administrator quietly becoming the whole
 * platform. Nobody grants themselves a role. Every grant and every removal is written to
 * the append-only log with a reason. And the last platform administrator cannot be
 * removed, because an installation with nobody able to grant roles is an installation
 * where the only remaining fix is a database console.
 */
public class ManageRolesUseCase(
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    /**
     * Roles an administrator may hand out here.
     *
     * `COMMUNITY_MEMBER` is not on the list because everyone has it, and `WALI_CONTACT` is
     * not because it is conferred by somebody naming you their guardian rather than by an
     * administrator deciding.
     */
    public val grantable: List<AccountRole> = listOf(
        AccountRole.MODERATOR,
        AccountRole.SAFETY_ADMINISTRATOR,
        AccountRole.PLATFORM_ADMINISTRATOR,
        AccountRole.SCHOLAR,
        AccountRole.TEACHER,
        AccountRole.PROJECT_ORGANIZER,
    )

    public suspend fun grant(
        principal: Principal,
        userId: UserId,
        role: AccountRole,
        reason: String,
    ): Outcome<Profile> {
        val refusal = guard(principal, userId, role, reason)
        if (refusal != null) return refusal

        val profile = profiles.find(userId) ?: return Outcome.NotFound("that member")
        if (role in profile.roles) {
            return Outcome.refused("That member already holds ${role.displayName.lowercase()}.")
        }

        val now = clock.now()
        val updated = profiles.save(profile.copy(roles = profile.roles + role, updatedAt = now))
        record(principal, userId, role, reason, AuditAction.ROLE_GRANTED, now)

        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = userId,
                kind = NotificationKind.SAFETY_NOTICE,
                title = "You have been given a new role: ${role.displayName}",
                body = reason.trim(),
                createdAt = now,
            ),
        )
        return Outcome.Success(updated)
    }

    public suspend fun revoke(
        principal: Principal,
        userId: UserId,
        role: AccountRole,
        reason: String,
    ): Outcome<Profile> {
        val refusal = guard(principal, userId, role, reason)
        if (refusal != null) return refusal

        val profile = profiles.find(userId) ?: return Outcome.NotFound("that member")
        if (role !in profile.roles) {
            return Outcome.refused("That member does not hold ${role.displayName.lowercase()}.")
        }

        // An installation with no administrator cannot appoint one. Whoever is last out
        // has to hand over before they leave.
        if (role == AccountRole.PLATFORM_ADMINISTRATOR) {
            val others = profiles.all()
                .filter { it.id != userId && AccountRole.PLATFORM_ADMINISTRATOR in it.roles }
            if (others.isEmpty()) {
                return Outcome.refused(
                    "This is the last platform administrator. Appoint another one first — " +
                        "an installation with nobody able to grant roles cannot be repaired " +
                        "from inside the app.",
                )
            }
        }

        val now = clock.now()
        val updated = profiles.save(profile.copy(roles = profile.roles - role, updatedAt = now))
        record(principal, userId, role, reason, AuditAction.ROLE_REVOKED, now)

        notifications.add(
            Notification(
                id = NotificationId(ids.newId()),
                userId = userId,
                kind = NotificationKind.SAFETY_NOTICE,
                title = "A role was removed from your account: ${role.displayName}",
                body = reason.trim(),
                createdAt = now,
            ),
        )
        return Outcome.Success(updated)
    }

    /** Everyone currently holding a staff role, for the administration screen. */
    public suspend fun staff(principal: Principal): Outcome<List<Profile>> {
        if (!principal.isSafetyAdmin) {
            return Outcome.refused("Only a platform administrator can see the staff list.")
        }
        return Outcome.Success(profiles.all().filter { p -> p.roles.any { it.isStaff } })
    }

    private fun guard(
        principal: Principal,
        userId: UserId,
        role: AccountRole,
        reason: String,
    ): Outcome<Nothing>? = when {
        AccountRole.PLATFORM_ADMINISTRATOR !in principal.roles ->
            Outcome.refused("Only a platform administrator can change roles.")

        // Not even to remove one. An administrator who can edit their own roles is an
        // administrator whose account compromise is unrecoverable in one step.
        userId == principal.userId ->
            Outcome.refused("You cannot change the roles on your own account.")

        role !in grantable ->
            Outcome.refused("${role.displayName} is not a role that is granted this way.")

        reason.isBlank() ->
            Outcome.invalid("reason", "Say why. This is written to the permanent record.")

        else -> null
    }

    private suspend fun record(
        principal: Principal,
        userId: UserId,
        role: AccountRole,
        reason: String,
        action: AuditAction,
        now: org.fisabilillah.core.model.Timestamp,
    ) {
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = AccountRole.PLATFORM_ADMINISTRATOR,
                action = action,
                subjectType = "profile",
                subjectId = userId.value,
                summary = "${role.displayName}: ${reason.trim()}",
                occurredAt = now,
            ),
        )
    }
}

/**
 * The work nobody opens the app to do.
 *
 * Two things in this product are written to happen after a period of time and, until now,
 * had nothing that made them happen. A conversation carries an auto-archive deadline that
 * no code read. An introduction lapses if the guardian does not respond, and
 * `LapseIntroductionsUseCase` was never called by anything.
 *
 * Both matter for the same reason: they are promises the interface makes to somebody who
 * is not looking. A woman told that an unanswered introduction expires after fourteen days
 * is owed that expiry whether or not she opens the app on the fourteenth.
 *
 * This runs them. It is deliberately a plain use case rather than a scheduler, so it can
 * be driven by whatever the deployment actually has — a cron job, an edge function, a
 * platform worker — and so it can be tested by moving a clock instead of waiting.
 */
public class RunScheduledMaintenanceUseCase(
    private val conversations: ConversationRepository,
    private val profiles: ProfileRepository,
    private val lapseIntroductions: LapseIntroductionsUseCase,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Report(
        val conversationsArchived: Int,
        val introductionsLapsed: Int,
    ) {
        public val didAnything: Boolean
            get() = conversationsArchived > 0 || introductionsLapsed > 0
    }

    /**
     * [principal] must be a service principal or a platform administrator. Maintenance
     * acts on everybody's data, so it is not something an ordinary session can trigger —
     * not because it is dangerous, but because "why did my conversation archive itself"
     * should have exactly one answer.
     */
    public suspend operator fun invoke(principal: Principal): Outcome<Report> {
        if (AccountRole.PLATFORM_ADMINISTRATOR !in principal.roles) {
            return Outcome.refused("Scheduled maintenance runs as the platform, not as a member.")
        }

        val now = clock.now()
        var archived = 0

        for (conversation in conversations.needingArchive(now)) {
            conversations.save(
                conversation.copy(
                    state = ConversationState.ARCHIVED,
                    updatedAt = now,
                ),
            )
            archived++
        }

        // Lapsing is per-person, so the sweep is over everybody. That is the honest
        // shape: an introduction lapses because a guardian did not answer, and there is no
        // index of "introductions somebody is waiting on" that does not start here.
        var lapsed = 0
        for (profile in profiles.all()) {
            lapsed += lapseIntroductions(profile.id)
        }

        if (archived > 0 || lapsed > 0) {
            auditLog.append(
                AuditLogEntry(
                    id = AuditLogId(ids.newId()),
                    actorId = null,
                    actorRoleAtTime = null,
                    action = AuditAction.ACCOUNT_STATUS_CHANGED,
                    subjectType = "maintenance",
                    subjectId = "scheduled",
                    summary = "Archived $archived conversations, lapsed $lapsed introductions",
                    occurredAt = now,
                ),
            )
        }
        return Outcome.Success(Report(archived, lapsed))
    }
}
