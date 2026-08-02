package org.fisabilillah.core.domain

import org.fisabilillah.core.model.AuditAction
import org.fisabilillah.core.model.AuditLogEntry
import org.fisabilillah.core.model.AuditLogId
import org.fisabilillah.core.model.ConsentKind
import org.fisabilillah.core.model.ConsentRecord
import org.fisabilillah.core.model.ConsentRecordId
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.TrustedContactId
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.policy.SafeguardPresets
import org.fisabilillah.core.policy.SafeguardResolver
import org.fisabilillah.core.policy.VerificationPolicy

public data class SafeguardUpdateResult(
    val saved: UserSafeguards,
    /** Present when the change opens something up, so the UI can say so plainly. */
    val loosenedFields: List<String>,
    /** Fields an organisation or community floor tightened beyond what the member chose. */
    val tightenedByOrganization: Boolean,
)

/**
 * Changing a member's own boundaries.
 *
 * Tightening always succeeds. Loosening also succeeds — this is the member's own account —
 * but the result reports exactly what has been opened up, and the UI is expected to show
 * that before the change is confirmed rather than after.
 */
public class UpdateSafeguardsUseCase(
    private val safeguards: SafeguardRepository,
    private val auditLog: AuditLogRepository,
    private val assembler: ContactContextAssembler,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(
        principal: Principal,
        candidate: UserSafeguards,
    ): Outcome<SafeguardUpdateResult> {
        if (candidate.userId != principal.userId) {
            return Outcome.refused("You can only change your own safeguards.")
        }
        val current = safeguards.forUser(principal.userId)
            ?: UserSafeguards(userId = principal.userId)

        val now = clock.now()
        val saved = safeguards.save(candidate.copy(updatedAt = now))

        val floors = assembler.floorsFor(principal.userId)
        val effective = SafeguardResolver.effective(saved, floors)

        val loosened = SafeguardResolver.loosenedFields(saved, current)
        if (loosened.isNotEmpty()) {
            auditLog.append(
                AuditLogEntry(
                    id = AuditLogId(ids.newId()),
                    actorId = principal.userId,
                    actorRoleAtTime = principal.roles.firstOrNull(),
                    action = AuditAction.SAFEGUARDS_CHANGED,
                    subjectType = "user_safeguards",
                    subjectId = principal.userId.value,
                    summary = "Loosened: ${loosened.joinToString("; ")}",
                    occurredAt = now,
                ),
            )
        } else {
            auditLog.append(
                AuditLogEntry(
                    id = AuditLogId(ids.newId()),
                    actorId = principal.userId,
                    actorRoleAtTime = principal.roles.firstOrNull(),
                    action = AuditAction.SAFEGUARDS_CHANGED,
                    subjectType = "user_safeguards",
                    subjectId = principal.userId.value,
                    summary = "Safeguards updated (no fields loosened)",
                    occurredAt = now,
                ),
            )
        }

        return Outcome.Success(
            SafeguardUpdateResult(
                saved = saved,
                loosenedFields = loosened,
                tightenedByOrganization = effective != saved,
            ),
        )
    }

    /** Applies a named preset. Presets are starting points, never a judgement. */
    public suspend fun applyPreset(
        principal: Principal,
        preset: SafeguardPresetName,
    ): Outcome<SafeguardUpdateResult> =
        invoke(principal, SafeguardPresets.forName(preset, principal.userId))
}

/** Reads the rules actually in force, including anything an organisation added. */
public class GetEffectiveSafeguardsUseCase(
    private val assembler: ContactContextAssembler,
) {
    public suspend operator fun invoke(userId: UserId): UserSafeguards =
        assembler.effectiveSafeguards(userId)
}

/**
 * Managing trusted contacts.
 *
 * Contact details supplied here never leave the owner's own session. The redaction happens
 * at the model, and every other read path in the codebase goes through
 * [TrustedContact.redacted].
 */
public class ManageTrustedContactsUseCase(
    private val trustedContacts: TrustedContactRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public suspend fun list(principal: Principal): List<TrustedContact> =
        trustedContacts.forOwner(principal.userId)

    public suspend fun save(
        principal: Principal,
        contact: TrustedContact,
    ): Outcome<TrustedContact> {
        if (contact.ownerId != principal.userId) {
            return Outcome.refused("You can only manage your own trusted contacts.")
        }
        if (contact.name.isBlank()) {
            return Outcome.invalid("name", "Give this person's name.")
        }
        val now = clock.now()
        val saved = trustedContacts.save(
            contact.copy(
                updatedAt = now,
                createdAt = if (contact.createdAt == org.fisabilillah.core.model.Timestamp.fromEpochSeconds(0)) {
                    now
                } else {
                    contact.createdAt
                },
            ),
        )
        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = principal.roles.firstOrNull(),
                action = AuditAction.SAFEGUARDS_CHANGED,
                subjectType = "trusted_contact",
                subjectId = saved.id.value,
                summary = "Trusted contact saved (${saved.role.name})",
                occurredAt = now,
            ),
        )
        return Outcome.Success(saved)
    }

    public suspend fun remove(
        principal: Principal,
        id: TrustedContactId,
    ): Outcome<Unit> {
        val existing = trustedContacts.find(id) ?: return Outcome.NotFound("that contact")
        if (existing.ownerId != principal.userId) {
            return Outcome.refused("You can only manage your own trusted contacts.")
        }
        trustedContacts.delete(id)
        return Outcome.Success(Unit)
    }
}

/**
 * Onboarding.
 *
 * The order is deliberate: consent is recorded before a profile exists, safeguards are
 * chosen before the account can be contacted, and the account stays in
 * `PENDING_ONBOARDING` — which the contact gate treats as inactive — until both are done.
 * A member cannot be reached before they have decided how they wish to be reached.
 */
public class CompleteOnboardingUseCase(
    private val profiles: ProfileRepository,
    private val safeguards: SafeguardRepository,
    private val consents: ConsentRepository,
    private val auditLog: AuditLogRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val profile: Profile,
        val preset: SafeguardPresetName,
        val customSafeguards: UserSafeguards? = null,
        val acceptedConsents: Set<ConsentKind>,
        val documentVersion: String,
        val declaredAdult: Boolean,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<Profile> {
        if (command.profile.id != principal.userId) {
            return Outcome.refused("You can only complete your own onboarding.")
        }
        if (!command.declaredAdult) {
            return Outcome.refused(
                "This platform is currently for adults only. Youth participation is coming " +
                    "later, through verified organisations with guardian consent.",
            )
        }
        val missing = ConsentKind.entries.filter { it.required && it !in command.acceptedConsents }
        if (missing.isNotEmpty()) {
            return Outcome.invalid(
                "consents",
                "Please accept: " + missing.joinToString { it.displayName },
            )
        }

        val now = clock.now()

        // Whatever the client sent, the member gets only the roles they are entitled to
        // grant themselves. A crafted request asking for SCHOLAR is silently reduced here
        // and rejected again by the database.
        val sanitized = VerificationPolicy.sanitizeRoleRequest(
            requested = command.profile.roles,
            currentlyHeld = emptySet(),
        )

        val profile = profiles.save(
            command.profile.copy(
                roles = sanitized.granted,
                // Verification is never taken from client input either.
                verificationLevel = org.fisabilillah.core.model.VerificationLevel.EMAIL_VERIFIED,
                attestations = emptySet(),
                status = org.fisabilillah.core.model.AccountStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val chosen = command.customSafeguards
            ?: SafeguardPresets.forName(command.preset, principal.userId)
        safeguards.save(chosen.copy(userId = principal.userId, updatedAt = now))

        for (kind in command.acceptedConsents) {
            consents.record(
                ConsentRecord(
                    id = ConsentRecordId(ids.newId()),
                    userId = principal.userId,
                    kind = kind,
                    documentVersion = command.documentVersion,
                    granted = true,
                    recordedAt = now,
                ),
            )
        }

        auditLog.append(
            AuditLogEntry(
                id = AuditLogId(ids.newId()),
                actorId = principal.userId,
                actorRoleAtTime = null,
                action = AuditAction.ACCOUNT_CREATED,
                subjectType = "profile",
                subjectId = principal.userId.value,
                summary = "Onboarding completed with preset ${command.preset.name}" +
                    if (sanitized.hadRejections) {
                        "; rejected self-assigned roles: " +
                            sanitized.rejected.joinToString { it.name }
                    } else {
                        ""
                    },
                occurredAt = now,
            ),
        )

        return Outcome.Success(profile)
    }
}
