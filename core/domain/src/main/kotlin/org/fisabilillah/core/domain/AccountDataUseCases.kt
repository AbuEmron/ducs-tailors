package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AccountStatus
import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.Timestamp
import kotlin.time.Duration.Companion.days

/**
 * Your own data, and leaving.
 *
 * Both of these are things the product already promised. `AccountDataScreen` describes an
 * export and a deletion, `AuditAction` has `DATA_EXPORTED` and `DELETION_REQUESTED`, and
 * nothing produced either. A privacy page describing a right nobody has implemented is
 * worse than one that admits the gap.
 */
public class ExportMyDataUseCase(
    private val profiles: ProfileRepository,
    private val safeguards: SafeguardRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val commitments: CommitmentRepository,
    private val consents: ConsentRepository,
    private val trustedContacts: TrustedContactRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    /**
     * What comes back.
     *
     * Structured rather than a blob of JSON, because the point of an export is that a
     * person can *read* it. A file they have to be a developer to open satisfies a
     * regulation and helps nobody.
     */
    public data class Export(
        val generatedAt: Timestamp,
        val profile: Profile,
        val safeguardSummary: String,
        val conversationCount: Int,
        val messagesSent: Int,
        val commitmentCount: Int,
        val consents: List<ConsentLine>,
        val trustedContactCount: Int,
        /** Stated plainly rather than left for the reader to notice. */
        val whatIsNotIncluded: List<String>,
    )

    public data class ConsentLine(
        val kind: String,
        val granted: Boolean,
        val at: Timestamp,
        val documentVersion: String,
    )

    public suspend operator fun invoke(principal: Principal): Outcome<Export> {
        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val now = clock.now()

        val threads = conversations.forUser(principal.userId).items
        val sent = threads.sumOf { thread ->
            messages.forConversation(thread.id).items.count { it.senderId == principal.userId }
        }
        val safeguard = safeguards.forUser(principal.userId)

        val export = Export(
            generatedAt = now,
            profile = profile,
            safeguardSummary = safeguard?.let {
                "Contactable by ${it.contactableBy.displayName.lowercase()}; " +
                    "minimum verification to contact you: " +
                    it.minimumVerificationToContactMe.displayName.lowercase()
            } ?: "No safeguards recorded.",
            conversationCount = threads.size,
            messagesSent = sent,
            commitmentCount = commitments.forUser(principal.userId).size,
            consents = consents.forUser(principal.userId).map {
                ConsentLine(
                    kind = it.kind.displayName,
                    granted = it.granted,
                    at = it.recordedAt,
                    documentVersion = it.documentVersion,
                )
            },
            trustedContactCount = trustedContacts.forOwner(principal.userId).size,
            whatIsNotIncluded = listOf(
                "Other people's messages to you. They are their words, and an export of " +
                    "your account is not a way to obtain a copy of somebody else's.",
                "Reports made about you, and the notes moderators wrote on them. Releasing " +
                    "these would identify whoever reported you.",
                "Your guardian's contact details, which are held for the introduction " +
                    "workflow and are not yours to export.",
                "The automated safety signals recorded against your messages.",
            ),
        )

        // An export is a disclosure of personal data, even to its subject, so it is logged
        // like one. If somebody's account is compromised, the record of what was taken is
        // the thing that tells them what to worry about.
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.DATA_EXPORTED,
                subjectType = "profile",
                subjectId = principal.userId.value,
                summary = "Exported own account data",
                occurredAt = now,
            ),
        )
        return Outcome.Success(export)
    }
}

/**
 * Asking to be deleted.
 *
 * The account moves to [AccountStatus.DELETION_REQUESTED] immediately — it stops being
 * able to act, which is most of what someone wants when they ask — and the content is
 * removed on the schedule in the privacy policy rather than at the tap.
 *
 * ## Why anything survives at all
 *
 * Reports made *about* this account, and the moderation record attached to them, are not
 * deleted. A platform where closing your account erases what you did to somebody else is
 * a platform that rewards abusers for leaving and returning, and the person who reported
 * them would find their evidence gone. That is stated on the screen, in those words,
 * before the button.
 */
public class RequestAccountDeletionUseCase(
    private val profiles: ProfileRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Result(
        val profile: Profile,
        /** When the content is removed, so the screen can state a date rather than "soon". */
        val contentRemovedAfter: Timestamp,
        val whatIsRetained: List<String>,
    )

    public suspend operator fun invoke(principal: Principal, reason: String?): Outcome<Result> {
        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        if (profile.status == AccountStatus.DELETION_REQUESTED) {
            return Outcome.refused("Your account is already scheduled for deletion.")
        }

        val now = clock.now()
        val updated = profiles.save(
            profile.copy(status = AccountStatus.DELETION_REQUESTED, updatedAt = now),
        )

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.DELETION_REQUESTED,
                subjectType = "profile",
                subjectId = principal.userId.value,
                summary = reason?.trim()?.ifBlank { null } ?: "No reason given",
                occurredAt = now,
            ),
        )

        return Outcome.Success(
            Result(
                profile = updated,
                contentRemovedAfter = now + GRACE_PERIOD,
                whatIsRetained = listOf(
                    "Reports other members made about your account, and what moderators " +
                        "decided. Deleting these would erase somebody else's evidence.",
                    "Entries in the audit log naming your account. That log has no delete " +
                        "path for anybody, including administrators.",
                    "The fact that a commitment was completed, without your name attached, " +
                        "where an organiser relied on it.",
                ),
            ),
        )
    }

    /**
     * Changing your mind, inside the grace period.
     *
     * The whole reason there is a grace period rather than an immediate wipe. People
     * delete accounts angry, or frightened, or at three in the morning.
     */
    public suspend fun cancel(principal: Principal): Outcome<Profile> {
        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        if (profile.status != AccountStatus.DELETION_REQUESTED) {
            return Outcome.refused("Your account is not scheduled for deletion.")
        }
        val now = clock.now()
        val restored = profiles.save(profile.copy(status = AccountStatus.ACTIVE, updatedAt = now))

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.ACCOUNT_STATUS_CHANGED,
                subjectType = "profile",
                subjectId = principal.userId.value,
                summary = "Deletion request cancelled by the member",
                occurredAt = now,
            ),
        )
        return Outcome.Success(restored)
    }

    private companion object {
        /** Stated on the screen, and the reason [cancel] exists. */
        val GRACE_PERIOD = 30.days
    }
}
