package org.fisabilillah.core.domain

import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.Contactability
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.EngagementDuration
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.Page
import org.fisabilillah.core.model.PrivateImpactRecord
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.VisibleProfile
import org.fisabilillah.core.model.VisibleServiceRequest
import org.fisabilillah.core.model.VolunteerOpportunity
import org.fisabilillah.core.policy.ContactDecision
import org.fisabilillah.core.policy.ContactPolicy
import org.fisabilillah.core.policy.VisibilityPolicy
import org.fisabilillah.core.policy.Viewer

/**
 * The home screen's contents.
 *
 * Every section answers a question about the member's own obligations and opportunities:
 * what have I promised, what near me needs doing, what matches what I can actually do.
 * There is no section for what other people are up to, nothing ranked by popularity, and
 * nothing whose purpose is to keep someone scrolling.
 */
public data class HomeDigest(
    val greetingName: String,
    val upcomingCommitments: List<Commitment>,
    val nearbyOpportunities: List<VolunteerOpportunity>,
    val matchingRequests: List<ServiceRequest>,
    val learningCircles: List<LearningOffering>,
    val projectsNeedingHelp: List<Project>,
    val unreadNotifications: Int,
    val activeConversations: Int,
    val impact: PrivateImpactRecord,
    val safetyReminder: String,
)

public class HomeDigestUseCase(
    private val profiles: ProfileRepository,
    private val commitments: CommitmentRepository,
    private val opportunities: OpportunityRepository,
    private val requests: ServiceRequestRepository,
    private val learning: LearningRepository,
    private val projects: ProjectRepository,
    private val notifications: NotificationRepository,
    private val conversations: ConversationRepository,
    private val trust: TrustRepository,
    private val clock: AppClock,
) {

    public suspend operator fun invoke(principal: Principal): Outcome<HomeDigest> {
        val profile = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val now = clock.now()
        val city = profile.place.approximate.city

        val nearby = opportunities.search(
            OpportunitySearchCriteria(city = city, from = now, limit = 6),
        ).items

        // "Matching" here means the member said they can do this kind of thing. It is not
        // a recommendation engine and it does not learn from what they tap.
        val matching = requests.search(
            RequestSearchCriteria(categories = profile.areasWillingToHelp, city = city, limit = 6),
        ).items

        val circles = learning.search(
            LearningSearchCriteria(city = city, limit = 5),
        ).items

        return Outcome.Success(
            HomeDigest(
                greetingName = profile.displayName,
                upcomingCommitments = commitments.upcoming(principal.userId, now).take(5),
                nearbyOpportunities = nearby,
                matchingRequests = matching,
                learningCircles = circles,
                projectsNeedingHelp = projects.list().items
                    .filter { it.volunteersNeeded > 0 && it.isPubliclyListed }
                    .take(4),
                unreadNotifications = notifications.unreadCount(principal.userId),
                activeConversations = conversations.forUser(principal.userId).items
                    .count { it.state.acceptsNewMessages },
                impact = trust.impactFor(principal.userId),
                safetyReminder = SafetyReminders.forToday(now.epochSeconds),
            ),
        )
    }
}

/**
 * Short, plain reminders shown once a day.
 *
 * Written to be useful rather than pious. Nobody needs an app to tell them to fear Allah;
 * they may well benefit from being reminded that a conversation which has drifted is worth
 * closing, or that a guardian can be added to any thread with one tap.
 */
public object SafetyReminders {
    private val REMINDERS = listOf(
        "You can add your wali or a trusted contact to any conversation, at any point, " +
            "with one tap.",
        "Conversations here have a stated purpose. When the work is finished, it is fine — " +
            "and healthy — to close the thread.",
        "Meeting someone from the platform for the first time? A public place and a second " +
            "person are always reasonable to ask for.",
        "Your service record is private. Nobody can see it, and it is not ranked against " +
            "anyone else's.",
        "If someone asks you to continue a conversation on another app, that removes the " +
            "safeguards you chose here. You are entitled to say no.",
        "Verification confirms documents, not character. Take your own time with people " +
            "regardless of their badges.",
        "You can make your safeguards stricter at any time, and you never have to explain why.",
        "A peer helper is not a scholar, and neither is this app. For matters that affect " +
            "your religion, ask a qualified person who knows your circumstances.",
    )

    public fun forToday(epochSeconds: Long): String =
        REMINDERS[((epochSeconds / 86_400L) % REMINDERS.size).toInt()]
}

/**
 * Finding people who can help, or who want help.
 *
 * The result set is filtered twice: once by what the searcher asked for, and again by
 * whether each person has agreed to be discoverable by someone like the searcher. The
 * second filter happens here rather than in the query so that it cannot be forgotten by a
 * future caller who writes their own SQL.
 */
public class SearchPeopleUseCase(
    private val profiles: ProfileRepository,
    private val safeguards: SafeguardRepository,
    private val blocks: BlockRepository,
    private val trust: TrustRepository,
    private val assembler: ContactContextAssembler,
) {

    public suspend operator fun invoke(
        principal: Principal,
        criteria: PeopleSearchCriteria,
        cursor: String? = null,
    ): Outcome<Page<VisibleProfile>> {
        val searcher = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val viewer = Viewer(
            id = searcher.id,
            gender = searcher.gender,
            verificationLevel = searcher.verificationLevel,
            organizationIds = searcher.organizationIds,
            isStaff = principal.isStaff,
        )
        val blockedEitherWay = blocks.blocking(searcher.id) + blocks.blockedBy(searcher.id)

        val candidates = profiles.search(criteria, cursor)
        val visible = mutableListOf<VisibleProfile>()

        for (candidate in candidates.items) {
            if (candidate.id == searcher.id) continue
            if (candidate.id in blockedEitherWay) continue

            val candidateSafeguards = assembler.effectiveSafeguards(candidate.id)
            val sharedOrganizations = assembler.sharedOrganizations(searcher.id, candidate.id)

            val discoverable = VisibilityPolicy.isDiscoverable(
                subject = candidate,
                safeguards = candidateSafeguards,
                viewer = viewer,
                sharedOrganizationIds = sharedOrganizations,
            )
            if (!discoverable) continue

            visible += VisibilityPolicy.visibleProfile(
                subject = candidate,
                safeguards = candidateSafeguards,
                trust = trust.recordFor(candidate.id),
                viewer = viewer,
                sharedOrganizationIds = sharedOrganizations,
                contactability = contactability(principal, searcher, candidate),
            )
        }

        return Outcome.Success(Page(visible, candidates.nextCursor, visible.size))
    }

    /**
     * A dry run of the contact gate, so the profile screen can tell someone up front what
     * they would need in order to make contact — rather than letting them write a long
     * message and then refusing it.
     */
    private suspend fun contactability(
        principal: Principal,
        searcher: Profile,
        candidate: Profile,
    ): Contactability {
        val probe = ContactPurpose(
            kind = ContactPurposeKind.ORGANIZATION_INQUIRY,
            reasonForContact = "Checking whether contact is possible before writing.",
            requestedAction = "No action requested; this is a capability probe.",
            expectedDuration = EngagementDuration.ONE_OFF,
        )
        val context = assembler.assemble(principal, searcher, candidate, probe)
        val safeguards = context.recipientEffectiveSafeguards

        return when (val decision = ContactPolicy.evaluate(context)) {
            is ContactDecision.Allowed -> Contactability(
                canInitiate = true,
                requirements = decision.requirements.map { it.displayName },
                acceptedPurposes = ContactPurposeKind.selectable.toSet() -
                    safeguards.declinedPurposes,
            )

            is ContactDecision.Denied -> Contactability(
                canInitiate = false,
                reasonIfNot = decision.userFacingMessage,
                acceptedPurposes = emptySet(),
            )
        }
    }

    public suspend fun viewProfile(
        principal: Principal,
        subjectId: UserId,
    ): Outcome<VisibleProfile> {
        val searcher = profiles.find(principal.userId) ?: return Outcome.NotFound("your profile")
        val subject = profiles.find(subjectId) ?: return Outcome.NotFound("that member")

        if (blocks.isBlocked(subjectId, principal.userId) && !principal.isStaff) {
            return Outcome.NotFound("that member")
        }

        val viewer = Viewer(
            id = searcher.id,
            gender = searcher.gender,
            verificationLevel = searcher.verificationLevel,
            organizationIds = searcher.organizationIds,
            isStaff = principal.isStaff,
        )
        val subjectSafeguards = assembler.effectiveSafeguards(subjectId)
        val sharedOrganizations = assembler.sharedOrganizations(searcher.id, subjectId)

        if (!VisibilityPolicy.isDiscoverable(
                subject = subject,
                safeguards = subjectSafeguards,
                viewer = viewer,
                sharedOrganizationIds = sharedOrganizations,
            ) && searcher.id != subjectId
        ) {
            return Outcome.NotFound("that member")
        }

        return Outcome.Success(
            VisibilityPolicy.visibleProfile(
                subject = subject,
                safeguards = subjectSafeguards,
                trust = trust.recordFor(subjectId),
                viewer = viewer,
                sharedOrganizationIds = sharedOrganizations,
                contactability = contactability(principal, searcher, subject),
            ),
        )
    }
}

/**
 * Reading help requests.
 *
 * The street address is stripped here, for everyone except the requester, the helper they
 * accepted, and the safety team. A volunteer browsing a list of needs sees a
 * neighbourhood, and learns the door number only once they have been let in.
 */
public class BrowseRequestsUseCase(
    private val requests: ServiceRequestRepository,
    private val profiles: ProfileRepository,
    private val organizations: OrganizationRepository,
) {

    public suspend operator fun invoke(
        principal: Principal,
        criteria: RequestSearchCriteria,
        cursor: String? = null,
    ): Outcome<Page<VisibleServiceRequest>> {
        val viewer = Viewer(
            id = principal.userId,
            gender = profiles.find(principal.userId)?.gender,
            isStaff = principal.isStaff,
        )

        val page = requests.search(criteria, cursor)
        val visible = page.items.map { request -> toVisible(request, viewer) }
        return Outcome.Success(Page(visible, page.nextCursor, page.totalKnown))
    }

    public suspend fun detail(
        principal: Principal,
        id: org.fisabilillah.core.model.RequestId,
    ): Outcome<VisibleServiceRequest> {
        val request = requests.find(id) ?: return Outcome.NotFound("that request")
        val viewer = Viewer(
            id = principal.userId,
            gender = profiles.find(principal.userId)?.gender,
            isStaff = principal.isStaff,
        )
        return Outcome.Success(toVisible(request, viewer))
    }

    private suspend fun toVisible(
        request: ServiceRequest,
        viewer: Viewer,
    ): VisibleServiceRequest {
        val requesterName = when (request.visibility) {
            org.fisabilillah.core.model.RequestVisibility.IDENTIFIED ->
                profiles.find(request.requesterId)?.displayName

            else -> if (viewer.isStaff) {
                profiles.find(request.requesterId)?.displayName
            } else {
                null
            }
        }
        val organizationName = request.mediatingOrganizationId
            ?.let { organizations.find(it)?.name }

        return VisibleServiceRequest(
            id = request.id,
            title = request.title,
            description = request.description,
            category = request.category,
            urgency = request.urgency,
            locationLabel = request.place.approximate.label,
            exactAddress = VisibilityPolicy.exactAddressFor(request, viewer),
            requesterDisplayName = requesterName,
            mediatingOrganizationName = organizationName,
            expiresAt = request.expiresAt,
            status = request.status,
            canRespond = request.status == org.fisabilillah.core.model.RequestStatus.OPEN &&
                viewer.id != request.requesterId,
            // Support totals stay hidden unless the requester chose otherwise. Nobody's
            // difficulty becomes a progress bar without their say-so.
            supportTotal = if (request.showSupportTotals) request.maximumAssistance else null,
        )
    }
}

/** Conversations a member is part of, newest activity first. */
public class ListConversationsUseCase(
    private val conversations: ConversationRepository,
) {
    public suspend operator fun invoke(
        principal: Principal,
        cursor: String? = null,
    ): Page<Conversation> = conversations.forUser(principal.userId, cursor)
}
