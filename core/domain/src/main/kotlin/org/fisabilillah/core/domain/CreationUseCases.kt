package org.fisabilillah.core.domain

import org.fisabilillah.core.model.ApproximateLocation
import org.fisabilillah.core.model.BeneficiaryType
import org.fisabilillah.core.model.DeliveryFormat
import org.fisabilillah.core.model.ExactLocation
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.Place
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.RequestUrgency
import org.fisabilillah.core.model.RequestVisibility
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.VolunteerOpportunity
import org.fisabilillah.core.policy.ValidationError
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Publishing an opportunity, and asking for help.
 *
 * ## Why these take fields rather than a finished object
 *
 * Both [VolunteerOpportunity] and [ServiceRequest] check their invariants in `init`.
 * That is right for the model — an opportunity with a blank title should not be
 * constructible — but it means that a use case which accepts an already-built object can
 * never validate anything the constructor also checks: the constructor throws first, in
 * the caller, before the use case is entered.
 *
 * That is not theoretical. Until this file existed, the application built a
 * `VolunteerOpportunity` straight from the form and handed it to the use case, so an
 * organiser who left the title blank got an `IllegalArgumentException` rather than the
 * sentence explaining what was missing — and `CreateOpportunityUseCase`'s carefully
 * written message about completion criteria was unreachable, because the constructor
 * rejected the blank field a line earlier. Service requests were worse still: they went
 * straight to the repository with no use case at all.
 *
 * So the commands below carry the form's fields as they were typed. Everything is checked
 * here, in one place, and the model is constructed only once it is known to be valid. The
 * same shape as `IntroductionPolicy.validateForm`, and for the same reason.
 */
public class CreateOpportunityUseCase(
    private val opportunities: OpportunityRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val title: String,
        val summary: String,
        val category: ServiceCategory,
        val beneficiaryType: BeneficiaryType,
        val city: String,
        val countryCode: String,
        val format: DeliveryFormat,
        val volunteersNeeded: Int,
        val completionCriteria: String,
        val startsAt: Timestamp,
        val endsAt: Timestamp,
        val physicalRequirements: String? = null,
        val safetyNotes: String? = null,
        val backgroundCheckRequired: Boolean = false,
        val childSafeguardingRequired: Boolean = false,
        val genderArrangement: GenderArrangement = GenderArrangement.NOT_APPLICABLE,
        val expensesReimbursed: Boolean = false,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<VolunteerOpportunity> {
        val errors = validate(command)
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val now = clock.now()
        return Outcome.Success(
            opportunities.save(
                VolunteerOpportunity(
                    id = ListingId(ids.newId()),
                    title = command.title.trim(),
                    summary = command.summary.trim(),
                    category = command.category,
                    // Taken from the signed-in principal, never from the form. An organiser
                    // cannot publish work in somebody else's name.
                    organizerId = principal.userId,
                    beneficiaryType = command.beneficiaryType,
                    place = Place(
                        approximate = ApproximateLocation(
                            label = command.city.trim(),
                            city = command.city.trim(),
                            countryCode = command.countryCode,
                        ),
                    ),
                    format = command.format,
                    startsAt = command.startsAt,
                    endsAt = command.endsAt,
                    volunteersNeeded = command.volunteersNeeded,
                    physicalRequirements = command.physicalRequirements?.trim()?.ifBlank { null },
                    safetyNotes = command.safetyNotes?.trim()?.ifBlank { null },
                    // A safeguarding requirement can be added by the organiser but never
                    // removed by them: if the category demands it, it is on regardless of
                    // what the form said.
                    backgroundCheckRequired = command.backgroundCheckRequired ||
                        command.category.requiresBackgroundCheckByDefault,
                    genderArrangement = command.genderArrangement,
                    childSafeguardingRequired = command.childSafeguardingRequired ||
                        command.category.involvesMinors,
                    expensesReimbursed = command.expensesReimbursed,
                    completionCriteria = command.completionCriteria.trim(),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun validate(command: Command): List<ValidationError> = buildList {
        if (command.title.isBlank()) {
            add(ValidationError("title", "Give the opportunity a title."))
        }
        if (command.summary.isBlank()) {
            add(ValidationError("summary", "Say what a volunteer would actually be doing."))
        }
        if (command.city.isBlank()) {
            add(ValidationError("city", "Say roughly where this is. A town or area is enough."))
        }
        if (command.volunteersNeeded < 1) {
            add(ValidationError("volunteersNeeded", "At least one volunteer is needed."))
        }
        if (command.completionCriteria.isBlank()) {
            add(
                ValidationError(
                    "completionCriteria",
                    "Say how a volunteer will know the work is finished.",
                ),
            )
        }
        if (command.endsAt < command.startsAt) {
            add(ValidationError("endsAt", "The finish time cannot be before the start."))
        }
        // These two are stated rather than silently applied, so the organiser reads them.
        // The construction above enforces them anyway; the message is what makes the
        // requirement a thing the organiser has understood rather than a surprise.
        if (command.category.requiresBackgroundCheckByDefault && !command.backgroundCheckRequired) {
            add(
                ValidationError(
                    "backgroundCheckRequired",
                    "This kind of work involves people who need extra protection, so a " +
                        "background check must be required.",
                ),
            )
        }
        if (command.category.involvesMinors && !command.childSafeguardingRequired) {
            add(
                ValidationError(
                    "childSafeguardingRequired",
                    "Work with young people needs child safeguarding arrangements in place.",
                ),
            )
        }
    }
}

/**
 * Asking the community for help.
 *
 * The care here is about the requester rather than the helper. A request is somebody
 * saying publicly that they cannot manage something, and the two fields most able to hurt
 * them are the street address and the amount of money involved — so the address is stored
 * but never published (it is released to one named person through
 * [DiscloseExactLocationUseCase]), and totals are off unless deliberately switched on.
 */
public class CreateServiceRequestUseCase(
    private val requests: ServiceRequestRepository,
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    public data class Command(
        val title: String,
        val description: String,
        val category: ServiceCategory,
        val urgency: RequestUrgency,
        val visibility: RequestVisibility,
        val city: String,
        val countryCode: String,
        val exactAddress: String? = null,
        val peopleAffected: Int = 1,
        val showSupportTotals: Boolean = false,
    )

    public suspend operator fun invoke(
        principal: Principal,
        command: Command,
    ): Outcome<ServiceRequest> {
        val errors = validate(command)
        if (errors.isNotEmpty()) return Outcome.Invalid(errors)

        val now = clock.now()
        return Outcome.Success(
            requests.save(
                ServiceRequest(
                    id = RequestId(ids.newId()),
                    requesterId = principal.userId,
                    title = command.title.trim(),
                    description = command.description.trim(),
                    category = command.category,
                    urgency = command.urgency,
                    visibility = command.visibility,
                    place = Place(
                        approximate = ApproximateLocation(
                            label = command.city.trim(),
                            city = command.city.trim(),
                            countryCode = command.countryCode,
                        ),
                        // Stored, and withheld from every read path until the requester
                        // releases it to one named person.
                        exact = command.exactAddress?.trim()
                            ?.ifBlank { null }
                            ?.let { ExactLocation(addressLine1 = it) },
                    ),
                    peopleAffected = command.peopleAffected,
                    expiresAt = now + REQUEST_LIFETIME,
                    showSupportTotals = command.showSupportTotals,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun validate(command: Command): List<ValidationError> = buildList {
        if (command.title.isBlank()) {
            add(ValidationError("title", "Give your request a short title."))
        }
        if (command.description.isBlank()) {
            add(
                ValidationError(
                    "description",
                    "Say what you need. As much or as little detail as you are comfortable with.",
                ),
            )
        }
        if (command.city.isBlank()) {
            add(ValidationError("city", "Say roughly where you are. A town or area is enough."))
        }
        if (command.peopleAffected < 1) {
            add(ValidationError("peopleAffected", "This should be at least one."))
        }
        // Anonymity and a street address do not sit together. Somebody choosing to hide
        // their name and then attaching their address has almost certainly misunderstood
        // one of the two controls, and finding out afterwards is too late.
        if (command.visibility == RequestVisibility.ANONYMOUS_TO_MEMBERS &&
            !command.exactAddress.isNullOrBlank()
        ) {
            add(
                ValidationError(
                    "exactAddress",
                    "You have chosen not to show your name, but added a street address. " +
                        "The address stays private until you release it to one person — " +
                        "if you would rather not store it at all, clear this field.",
                ),
            )
        }
    }

    private companion object {
        /** Requests expire rather than lingering as an open statement of somebody's need. */
        val REQUEST_LIFETIME = 30.days
    }
}

/** Kept next to the command that uses them, so a screen's placeholder dates are visible. */
public object DefaultOpportunityTiming {
    public val LEAD_TIME: kotlin.time.Duration = 7.days
    public val DURATION: kotlin.time.Duration = 4.hours
}
