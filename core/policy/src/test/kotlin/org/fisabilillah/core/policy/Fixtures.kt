package org.fisabilillah.core.policy

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import org.fisabilillah.core.model.ApproximateLocation
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.EngagementDuration
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.Place
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VerificationLevel

/** Shared builders so each test states only the thing it is actually about. */
internal object Fixtures {

    val NOW: Timestamp = Instant.parse("2026-06-01T12:00:00Z")
    const val YEAR: Int = 2026

    val place: Place = Place(
        approximate = ApproximateLocation(
            label = "Northfield",
            city = "Ashbourne",
            region = "Midshire",
            countryCode = "GB",
        ),
    )

    fun profile(
        id: String,
        gender: Gender = Gender.MALE,
        verification: VerificationLevel = VerificationLevel.IDENTITY_VERIFIED,
        birthYear: Int = 1995,
        status: org.fisabilillah.core.model.AccountStatus =
            org.fisabilillah.core.model.AccountStatus.ACTIVE,
        deletedAt: Timestamp? = null,
    ): Profile = Profile(
        id = UserId(id),
        displayName = id.replaceFirstChar { it.uppercase() },
        gender = gender,
        dateOfBirthYear = birthYear,
        place = place,
        verificationLevel = verification,
        status = status,
        deletedAt = deletedAt,
        createdAt = NOW,
        updatedAt = NOW,
    )

    fun safeguards(id: String): UserSafeguards = UserSafeguards(userId = UserId(id))

    fun purpose(
        kind: ContactPurposeKind = ContactPurposeKind.VOLUNTEER_OPPORTUNITY,
        subject: PurposeSubject? = PurposeSubject.Opportunity(ListingId("listing-1")),
    ): ContactPurpose = ContactPurpose(
        kind = kind,
        subject = subject,
        reasonForContact = "I saw your Saturday food distribution listing and I have a van " +
            "available that morning.",
        requestedAction = "Please let me know where to bring the van.",
        expectedDuration = EngagementDuration.ONE_OFF,
    )

    fun context(
        initiator: Profile = profile("initiator"),
        recipient: Profile = profile("recipient", gender = Gender.MALE),
        recipientSafeguards: UserSafeguards = safeguards(recipient.id.value),
        purpose: ContactPurpose = purpose(),
        localTime: LocalTime = LocalTime(14, 0),
        build: ContactContext.() -> ContactContext = { this },
    ): ContactContext = ContactContext(
        initiator = initiator,
        initiatorSafeguards = safeguards(initiator.id.value),
        recipient = recipient,
        recipientEffectiveSafeguards = recipientSafeguards,
        purpose = purpose,
        recipientLocalTime = localTime,
        now = NOW,
        currentYear = YEAR,
    ).build()
}
