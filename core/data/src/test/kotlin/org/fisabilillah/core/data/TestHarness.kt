package org.fisabilillah.core.data

import kotlinx.datetime.Instant
import org.fisabilillah.core.domain.Principal
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.EngagementDuration
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.Timestamp
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.domain.Outcome

/**
 * A fully wired application, seeded, with a clock the test controls.
 *
 * The integration tests below run through the same use cases the Android app calls. That
 * matters: a rule enforced only in a policy object is a rule that a future screen can
 * bypass by talking to a repository directly, and these tests are what demonstrate the
 * rules survive the whole call path.
 */
internal class Harness(
    now: Timestamp = SeedData.EPOCH,
    payments: org.fisabilillah.core.domain.PaymentGateway =
        org.fisabilillah.core.domain.NoPaymentProcessor,
) {
    val clock: FixedClock = FixedClock(now, year = 2026)
    val graph: CoreGraph = CoreGraph(
        store = InMemoryStore(),
        clock = clock,
        ids = SequentialIdGenerator("t"),
        payments = payments,
    )
    val store: InMemoryStore get() = graph.store

    init {
        SeedData.populate(store)
    }

    fun principal(userId: UserId): Principal {
        val roles = store.profiles[userId]?.roles ?: setOf(AccountRole.COMMUNITY_MEMBER)
        return Principal(userId, roles)
    }

    fun purpose(
        kind: ContactPurposeKind = ContactPurposeKind.VOLUNTEER_OPPORTUNITY,
        subject: PurposeSubject? = PurposeSubject.Opportunity(ListingId("opp-masjid-cleanup")),
        reason: String = "I saw the masjid deep clean listing and I am free that morning with " +
            "a set of ladders.",
        action: String = "Please let me know what time to arrive.",
        build: ContactPurpose.() -> ContactPurpose = { this },
    ): ContactPurpose = ContactPurpose(
        kind = kind,
        subject = subject,
        reasonForContact = reason,
        requestedAction = action,
        expectedDuration = EngagementDuration.ONE_OFF,
    ).build()
}

internal fun <T> Outcome<T>.expectSuccess(): T {
    if (this !is Outcome.Success) {
        throw AssertionError("expected success but was $this")
    }
    return value
}

internal fun <T> Outcome<T>.expectRefused(): Outcome.Refused {
    if (this !is Outcome.Refused) {
        throw AssertionError("expected refusal but was $this")
    }
    return this
}

internal fun <T> Outcome<T>.expectInvalid(): Outcome.Invalid {
    if (this !is Outcome.Invalid) {
        throw AssertionError("expected invalid but was $this")
    }
    return this
}

internal fun <T> Outcome<T>.expectNotFound(): Outcome.NotFound {
    if (this !is Outcome.NotFound) {
        throw AssertionError("expected not found but was $this")
    }
    return this
}

internal val TEST_EPOCH: Timestamp = Instant.parse("2026-03-01T09:00:00Z")
