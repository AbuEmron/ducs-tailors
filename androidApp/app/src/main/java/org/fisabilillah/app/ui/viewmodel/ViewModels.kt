package org.fisabilillah.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fisabilillah.app.di.AppGraph
import org.fisabilillah.core.domain.HomeDigest
import org.fisabilillah.core.domain.IntroductionDecisionAction
import org.fisabilillah.core.domain.LearningSearchCriteria
import org.fisabilillah.core.domain.OpportunitySearchCriteria
import org.fisabilillah.core.domain.AppealQueueUseCase
import org.fisabilillah.core.domain.CreateCommunityUseCase
import org.fisabilillah.core.domain.CreateLearningOfferingUseCase
import org.fisabilillah.core.domain.CreateProjectUseCase
import org.fisabilillah.core.domain.ExportMyDataUseCase
import org.fisabilillah.core.domain.MyVerificationUseCase
import org.fisabilillah.core.domain.RequestAccountDeletionUseCase
import org.fisabilillah.core.domain.RequestVerificationUseCase
import org.fisabilillah.core.domain.SubmitQualificationUseCase
import org.fisabilillah.core.model.AccountRole
import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.CommitmentId
import org.fisabilillah.core.model.CommitmentSubject
import org.fisabilillah.core.model.DeviceSession
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.Qualification
import org.fisabilillah.core.model.QualificationId
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.VerificationLevel
import org.fisabilillah.core.model.VerificationMethod
import org.fisabilillah.core.model.VerificationRequest
import org.fisabilillah.core.model.VerificationRequestId
import org.fisabilillah.core.domain.MyModerationRecordUseCase
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.domain.PeopleSearchCriteria
import org.fisabilillah.core.domain.Principal
import org.fisabilillah.core.domain.RequestSearchCriteria
import org.fisabilillah.core.domain.StartConversationCommand
import org.fisabilillah.core.domain.SubmitReportUseCase
import org.fisabilillah.core.domain.TakeModerationActionUseCase
import org.fisabilillah.core.policy.ValidationError
import org.fisabilillah.core.model.Commitment
import org.fisabilillah.core.model.Community
import org.fisabilillah.core.model.ContactPurpose
import org.fisabilillah.core.model.ContactPurposeKind
import org.fisabilillah.core.model.Conversation
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.EngagementDuration
import org.fisabilillah.core.model.FormalIntroductionRequest
import org.fisabilillah.core.model.FormalIntroductionSettings
import org.fisabilillah.core.model.IntroductionForm
import org.fisabilillah.core.model.IntroductionId
import org.fisabilillah.core.model.LearningOffering
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.Message
import org.fisabilillah.core.model.Notification
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ProjectId
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.ReportCategory
import org.fisabilillah.core.model.ReportTarget
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.SafeguardPresetName
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.TrustedContact
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.model.UserSafeguards
import org.fisabilillah.core.model.VisibleProfile
import org.fisabilillah.core.model.VisibleServiceRequest

/**
 * View models.
 *
 * Every one of these is a thin adapter: it calls a use case, turns the [Outcome] into
 * screen state, and stops. No safety rule is implemented here. If a screen appears to be
 * enforcing a boundary, that is a bug — the boundary belongs in `core:policy`, where it can
 * be tested without a device and where the same rule protects a future iOS client.
 */

/** Shared shape for a screen that loads something and can be refused. */
internal data class ScreenState<T>(
    val loading: Boolean = true,
    val data: T? = null,
    val refusal: String? = null,
    val errors: List<ValidationError> = emptyList(),
) {
    val hasData: Boolean get() = data != null
}

/** Folds an [Outcome] into screen state, so no screen has to match on it. */
internal fun <T> Outcome<T>.toScreenState(): ScreenState<T> = when (this) {
    is Outcome.Success -> ScreenState(loading = false, data = value)
    is Outcome.Refused -> ScreenState(loading = false, refusal = message)
    is Outcome.Invalid -> ScreenState(loading = false, errors = errors)
    is Outcome.NotFound -> ScreenState(loading = false, refusal = "We could not find $what.")
}

// ── Home ─────────────────────────────────────────────────────────────────────

internal class HomeViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenState<HomeDigest>())
    val state: StateFlow<ScreenState<HomeDigest>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ScreenState(loading = true)
            _state.value = graph.core.homeDigest(principal).toScreenState()
        }
    }
}

// ── Serve, learn, requests, projects ─────────────────────────────────────────

internal data class ServeState(
    val opportunities: List<org.fisabilillah.core.model.VolunteerOpportunity> = emptyList(),
    val loading: Boolean = true,
    val city: String? = null,
    val hideBackgroundCheckRequired: Boolean = false,
)

internal class ServeViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(ServeState())
    val state: StateFlow<ServeState> = _state.asStateFlow()

    init {
        search()
    }

    fun setCity(city: String?) {
        _state.update { it.copy(city = city) }
        search()
    }

    fun setHideBackgroundCheckRequired(hide: Boolean) {
        _state.update { it.copy(hideBackgroundCheckRequired = hide) }
        search()
    }

    private fun search() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val current = _state.value
            val page = graph.core.opportunities.search(
                OpportunitySearchCriteria(
                    city = current.city,
                    excludeBackgroundCheckRequired = current.hideBackgroundCheckRequired,
                    limit = 50,
                ),
            )
            _state.update { it.copy(opportunities = page.items, loading = false) }
        }
    }

    fun apply(id: ListingId, message: String, onResult: (Outcome<*>) -> Unit) {
        viewModelScope.launch {
            onResult(graph.core.applyToOpportunity(principal, id, message))
        }
    }
}

internal data class LearnState(
    val offerings: List<LearningOffering> = emptyList(),
    val loading: Boolean = true,
    val freeOnly: Boolean = false,
)

internal class LearnViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(LearnState())
    val state: StateFlow<LearnState> = _state.asStateFlow()

    init {
        search()
    }

    fun setFreeOnly(freeOnly: Boolean) {
        _state.update { it.copy(freeOnly = freeOnly) }
        search()
    }

    private fun search() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val page = graph.core.learning.search(
                LearningSearchCriteria(freeOnly = _state.value.freeOnly, limit = 50),
            )
            _state.update { it.copy(offerings = page.items, loading = false) }
        }
    }

    fun enrol(id: ListingId, onResult: (Outcome<*>) -> Unit) {
        viewModelScope.launch { onResult(graph.core.enrollInLearning(principal, id)) }
    }

    suspend fun offering(id: ListingId): LearningOffering? = graph.core.learning.find(id)
}

internal class RequestsViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenState<List<VisibleServiceRequest>>())
    val state: StateFlow<ScreenState<List<VisibleServiceRequest>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ScreenState(loading = true)
            _state.value = graph.core.browseRequests(principal, RequestSearchCriteria(limit = 50))
                .map { it.items }
                .toScreenState()
        }
    }

    fun respond(id: RequestId, message: String, onResult: (Outcome<*>) -> Unit) {
        viewModelScope.launch { onResult(graph.core.respondToRequest(principal, id, message)) }
    }

    suspend fun detail(id: RequestId): VisibleServiceRequest? =
        graph.core.browseRequests.detail(principal, id).valueOrNull()

    suspend fun raw(id: RequestId): ServiceRequest? = graph.core.requests.find(id)

    fun discloseAddress(id: RequestId, to: UserId, onResult: (Outcome<*>) -> Unit) {
        viewModelScope.launch {
            onResult(graph.core.discloseExactLocation(principal, id, to))
        }
    }
}

internal class ProjectsViewModel(
    private val graph: AppGraph,
) : ViewModel() {

    private val _state = MutableStateFlow<List<Project>>(emptyList())
    val state: StateFlow<List<Project>> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = graph.core.projects.list().items }
    }

    suspend fun project(id: ProjectId): Project? = graph.core.projects.find(id)
    suspend fun tasks(id: ProjectId) = graph.core.projects.tasks(id)
}

// ── Messaging ────────────────────────────────────────────────────────────────

internal data class ConversationListState(
    val conversations: List<Conversation> = emptyList(),
    val loading: Boolean = true,
)

internal class MessagesViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationListState())
    val state: StateFlow<ConversationListState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ConversationListState(loading = true)
            _state.value = ConversationListState(
                conversations = graph.core.listConversations(principal).items,
                loading = false,
            )
        }
    }

    suspend fun displayName(userId: UserId): String =
        graph.core.profiles.find(userId)?.displayName ?: "Member"
}

internal data class ConversationState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val participantNames: Map<UserId, String> = emptyMap(),
    val loading: Boolean = true,
    val refusal: String? = null,
    val canSend: Boolean = true,
)

internal class ConversationViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
    private val conversationId: ConversationId,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val conversation = graph.core.conversations.find(conversationId)
            if (conversation == null) {
                _state.value = ConversationState(loading = false, refusal = "Conversation not found.")
                return@launch
            }
            val messages = graph.core.messages.forConversation(conversationId).items
            val names = conversation.members.associate { member ->
                member.userId to (graph.core.profiles.find(member.userId)?.displayName ?: "Member")
            }
            _state.value = ConversationState(
                conversation = conversation,
                messages = messages,
                participantNames = names,
                loading = false,
                canSend = conversation.state.acceptsNewMessages,
            )
        }
    }

    fun send(body: String, onRefused: (String) -> Unit) {
        viewModelScope.launch {
            when (val outcome = graph.core.sendMessage(principal, conversationId, body)) {
                is Outcome.Success -> refresh()
                is Outcome.Refused -> onRefused(outcome.message)
                is Outcome.Invalid -> onRefused(outcome.errors.first().message)
                is Outcome.NotFound -> onRefused("We could not find ${outcome.what}.")
            }
        }
    }

    fun unsend(messageId: org.fisabilillah.core.model.MessageId, onRefused: (String) -> Unit) {
        viewModelScope.launch {
            when (val outcome = graph.core.unsendMessage(principal, messageId)) {
                is Outcome.Success -> refresh()
                is Outcome.Refused -> onRefused(outcome.message)
                else -> onRefused("That message could not be unsent.")
            }
        }
    }

    fun addGuardian(onRefused: (String) -> Unit) = addOversight(
        org.fisabilillah.core.policy.OversightRequirement.RECIPIENT_GUARDIAN,
        onRefused,
    )

    fun addModerator(onRefused: (String) -> Unit) = addOversight(
        org.fisabilillah.core.policy.OversightRequirement.MODERATOR,
        onRefused,
    )

    private fun addOversight(
        kind: org.fisabilillah.core.policy.OversightRequirement,
        onRefused: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (val outcome = graph.core.addOversight(principal, conversationId, kind)) {
                is Outcome.Success -> refresh()
                is Outcome.Refused -> onRefused(outcome.message)
                else -> onRefused("That person could not be added.")
            }
        }
    }

    fun endConversation(onDone: () -> Unit) {
        viewModelScope.launch {
            graph.core.endConversation(principal, conversationId)
            refresh()
            onDone()
        }
    }

    fun block(userId: UserId, onDone: () -> Unit) {
        viewModelScope.launch {
            graph.core.blockUser(principal, userId)
            refresh()
            onDone()
        }
    }
}

/**
 * Composing the first message.
 *
 * The screen collects a purpose and an opening message and hands both to the use case.
 * It does not pre-check anything: the refusal text a person sees is the one the contact
 * gate produced, so what the UI says and what the system does cannot drift apart.
 */
internal data class ComposeState(
    val recipient: VisibleProfile? = null,
    val purposeKind: ContactPurposeKind = ContactPurposeKind.VOLUNTEER_OPPORTUNITY,
    val subject: PurposeSubject? = null,
    val subjectLabel: String? = null,
    val reason: String = "",
    val action: String = "",
    val duration: EngagementDuration = EngagementDuration.ONE_OFF,
    val requestGuardian: Boolean = false,
    val requestModerator: Boolean = false,
    val openingMessage: String = "",
    val submitting: Boolean = false,
    val errors: List<ValidationError> = emptyList(),
    val refusal: String? = null,
) {
    fun errorFor(field: String): String? = errors.firstOrNull { it.field == field }?.message
}

internal class ComposeConversationViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
    private val recipientId: UserId,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeState())
    val state: StateFlow<ComposeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recipient = graph.core.searchPeople.viewProfile(principal, recipientId).valueOrNull()
            _state.update { it.copy(recipient = recipient) }
        }
    }

    fun setPurposeKind(kind: ContactPurposeKind) = _state.update { it.copy(purposeKind = kind) }
    fun setReason(value: String) = _state.update { it.copy(reason = value) }
    fun setAction(value: String) = _state.update { it.copy(action = value) }
    fun setDuration(value: EngagementDuration) = _state.update { it.copy(duration = value) }
    fun setOpeningMessage(value: String) = _state.update { it.copy(openingMessage = value) }
    fun setRequestGuardian(value: Boolean) = _state.update { it.copy(requestGuardian = value) }
    fun setRequestModerator(value: Boolean) = _state.update { it.copy(requestModerator = value) }

    fun setSubject(subject: PurposeSubject?, label: String?) =
        _state.update { it.copy(subject = subject, subjectLabel = label) }

    fun submit(onOpened: (ConversationId) -> Unit) {
        val current = _state.value
        _state.update { it.copy(submitting = true, errors = emptyList(), refusal = null) }

        viewModelScope.launch {
            val outcome = graph.core.startConversation(
                principal,
                StartConversationCommand(
                    recipientId = recipientId,
                    purpose = ContactPurpose(
                        kind = current.purposeKind,
                        subject = current.subject,
                        reasonForContact = current.reason,
                        requestedAction = current.action,
                        expectedDuration = current.duration,
                        requestGuardianPresent = current.requestGuardian,
                        requestModeratorPresent = current.requestModerator,
                    ),
                    openingMessage = current.openingMessage,
                ),
            )
            when (outcome) {
                is Outcome.Success -> {
                    _state.update { it.copy(submitting = false) }
                    onOpened(outcome.value.conversation.id)
                }
                is Outcome.Invalid ->
                    _state.update { it.copy(submitting = false, errors = outcome.errors) }
                is Outcome.Refused ->
                    _state.update { it.copy(submitting = false, refusal = outcome.message) }
                is Outcome.NotFound ->
                    _state.update {
                        it.copy(submitting = false, refusal = "We could not find ${outcome.what}.")
                    }
            }
        }
    }

    /** Listings the sender could attach, so the purpose points at something real. */
    suspend fun availableSubjects(): List<Pair<PurposeSubject, String>> = buildList {
        graph.core.opportunities.search(OpportunitySearchCriteria(limit = 20)).items.forEach {
            add(PurposeSubject.Opportunity(it.id) to it.title)
        }
        graph.core.learning.search(LearningSearchCriteria(limit = 20)).items.forEach {
            add(PurposeSubject.LearningOffer(it.id) to it.title)
        }
        graph.core.projects.list().items.forEach {
            add(PurposeSubject.Project(it.id) to it.title)
        }
    }
}

// ── People and profile ───────────────────────────────────────────────────────

internal class PeopleViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenState<List<VisibleProfile>>())
    val state: StateFlow<ScreenState<List<VisibleProfile>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        search()
    }

    fun setQuery(value: String) {
        _query.value = value
        search()
    }

    private fun search() {
        viewModelScope.launch {
            _state.value = ScreenState(loading = true)
            _state.value = graph.core.searchPeople(
                principal,
                PeopleSearchCriteria(skillQuery = _query.value.ifBlank { null }, limit = 50),
            ).map { it.items }.toScreenState()
        }
    }

    suspend fun profile(userId: UserId): VisibleProfile? =
        graph.core.searchPeople.viewProfile(principal, userId).valueOrNull()
}

internal data class MyProfileState(
    val profile: Profile? = null,
    val safeguards: UserSafeguards? = null,
    val effectiveSafeguards: UserSafeguards? = null,
    val commitments: List<Commitment> = emptyList(),
    val impact: org.fisabilillah.core.model.PrivateImpactRecord? = null,
    val trustedContacts: List<TrustedContact> = emptyList(),
    val introductionSettings: FormalIntroductionSettings? = null,
    val loading: Boolean = true,
)

internal class ProfileViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(MyProfileState())
    val state: StateFlow<MyProfileState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = MyProfileState(
                profile = graph.core.profiles.find(principal.userId),
                safeguards = graph.core.safeguards.forUser(principal.userId),
                effectiveSafeguards = graph.core.effectiveSafeguards(principal.userId),
                commitments = graph.core.commitments.forUser(principal.userId),
                impact = graph.core.privateImpact(principal),
                trustedContacts = graph.core.manageTrustedContacts.list(principal),
                introductionSettings = graph.core.introductions.settingsFor(principal.userId),
                loading = false,
            )
        }
    }

    fun saveProfile(profile: Profile, onDone: () -> Unit) {
        viewModelScope.launch {
            graph.core.profiles.save(profile)
            refresh()
            onDone()
        }
    }
}

// ── Safeguards ───────────────────────────────────────────────────────────────

internal data class SafeguardEditorState(
    val draft: UserSafeguards? = null,
    val saved: UserSafeguards? = null,
    val effective: UserSafeguards? = null,
    val loosenedFields: List<String> = emptyList(),
    val tightenedByOrganization: Boolean = false,
    val saving: Boolean = false,
    val refusal: String? = null,
) {
    val hasUnsavedChanges: Boolean get() = draft != null && draft != saved
}

internal class SafeguardViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(SafeguardEditorState())
    val state: StateFlow<SafeguardEditorState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val saved = graph.core.safeguards.forUser(principal.userId)
                ?: UserSafeguards(userId = principal.userId)
            val effective = graph.core.effectiveSafeguards(principal.userId)
            _state.value = SafeguardEditorState(
                draft = saved,
                saved = saved,
                effective = effective,
                tightenedByOrganization = effective != saved,
            )
        }
    }

    fun edit(transform: (UserSafeguards) -> UserSafeguards) {
        _state.update { current ->
            val draft = current.draft ?: return@update current
            val updated = transform(draft)
            current.copy(
                draft = updated,
                loosenedFields = current.saved?.let {
                    org.fisabilillah.core.policy.SafeguardResolver.loosenedFields(updated, it)
                } ?: emptyList(),
            )
        }
    }

    fun applyPreset(preset: SafeguardPresetName) {
        edit { org.fisabilillah.core.policy.SafeguardPresets.forName(preset, principal.userId) }
    }

    fun save(onDone: () -> Unit) {
        val draft = _state.value.draft ?: return
        _state.update { it.copy(saving = true, refusal = null) }
        viewModelScope.launch {
            when (val outcome = graph.core.updateSafeguards(principal, draft)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            saved = outcome.value.saved,
                            draft = outcome.value.saved,
                            loosenedFields = outcome.value.loosenedFields,
                            tightenedByOrganization = outcome.value.tightenedByOrganization,
                        )
                    }
                    onDone()
                }
                is Outcome.Refused ->
                    _state.update { it.copy(saving = false, refusal = outcome.message) }
                is Outcome.Invalid ->
                    _state.update {
                        it.copy(saving = false, refusal = outcome.errors.first().message)
                    }
                is Outcome.NotFound ->
                    _state.update { it.copy(saving = false, refusal = "Not found.") }
            }
        }
    }
}

// ── Trusted contacts and the introduction workflow ───────────────────────────

internal data class TrustedContactsState(
    val contacts: List<TrustedContact> = emptyList(),
    val settings: FormalIntroductionSettings? = null,
    val incoming: List<FormalIntroductionRequest> = emptyList(),
    val outgoing: List<FormalIntroductionRequest> = emptyList(),
    val loading: Boolean = true,
    val refusal: String? = null,
)

internal class WaliViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(TrustedContactsState())
    val state: StateFlow<TrustedContactsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = TrustedContactsState(
                contacts = graph.core.manageTrustedContacts.list(principal),
                settings = graph.core.introductions.settingsFor(principal.userId)
                    ?: FormalIntroductionSettings(userId = principal.userId),
                incoming = graph.core.introductions.incomingFor(principal.userId),
                outgoing = graph.core.introductions.outgoingFrom(principal.userId),
                loading = false,
            )
        }
    }

    fun saveContact(contact: TrustedContact, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val outcome = graph.core.manageTrustedContacts.save(principal, contact)) {
                is Outcome.Success -> {
                    refresh()
                    onDone()
                }
                is Outcome.Refused -> _state.update { it.copy(refusal = outcome.message) }
                is Outcome.Invalid ->
                    _state.update { it.copy(refusal = outcome.errors.first().message) }
                is Outcome.NotFound -> _state.update { it.copy(refusal = "Not found.") }
            }
        }
    }

    fun saveSettings(settings: FormalIntroductionSettings) {
        viewModelScope.launch {
            when (val outcome = graph.core.updateIntroductionSettings(principal, settings)) {
                is Outcome.Success -> refresh()
                is Outcome.Refused -> _state.update { it.copy(refusal = outcome.message) }
                else -> _state.update { it.copy(refusal = "That change could not be saved.") }
            }
        }
    }

    fun decide(
        id: IntroductionId,
        action: IntroductionDecisionAction,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            graph.core.decideIntroduction(principal, id, action)
            refresh()
            onDone()
        }
    }

    fun openGuardianConversation(
        id: IntroductionId,
        message: String,
        onOpened: (ConversationId) -> Unit,
        onRefused: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (
                val outcome =
                    graph.core.openIntroductionConversation(principal, id, message)
            ) {
                is Outcome.Success -> onOpened(outcome.value.id)
                is Outcome.Refused -> onRefused(outcome.message)
                else -> onRefused("That conversation could not be opened.")
            }
        }
    }

    suspend fun introduction(id: IntroductionId): FormalIntroductionRequest? =
        graph.core.introductions.find(id)

    /** Only ever the redacted form. Contact details never reach this layer. */
    suspend fun guardianSummary(id: IntroductionId) =
        graph.core.viewIntroductionGuardian(principal, id).valueOrNull()
}

internal data class IntroductionFormState(
    val recipient: VisibleProfile? = null,
    val statedIntention: String = "",
    val aboutSelf: String = "",
    val familyContext: String = "",
    val practiceAndPriorities: String = "",
    val livingSituationAndPlans: String = "",
    val guardianName: String = "",
    val guardianRelationship: org.fisabilillah.core.model.GuardianRelationship =
        org.fisabilillah.core.model.GuardianRelationship.FATHER,
    val confirmsMarriage: Boolean = false,
    val confirmsConduct: Boolean = false,
    val submitting: Boolean = false,
    val errors: List<ValidationError> = emptyList(),
    val refusal: String? = null,
    val submitted: Boolean = false,
) {
    fun errorFor(field: String): String? = errors.firstOrNull { it.field == field }?.message
}

internal class SubmitIntroductionViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
    private val recipientId: UserId,
) : ViewModel() {

    private val _state = MutableStateFlow(IntroductionFormState())
    val state: StateFlow<IntroductionFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    recipient = graph.core.searchPeople
                        .viewProfile(principal, recipientId).valueOrNull(),
                )
            }
        }
    }

    fun update(transform: (IntroductionFormState) -> IntroductionFormState) =
        _state.update(transform)

    fun submit() {
        val current = _state.value
        _state.update { it.copy(submitting = true, errors = emptyList(), refusal = null) }

        viewModelScope.launch {
            val outcome = graph.core.submitIntroduction(
                principal,
                recipientId,
                IntroductionForm(
                    statedIntention = current.statedIntention,
                    aboutSelf = current.aboutSelf,
                    familyContext = current.familyContext,
                    practiceAndPriorities = current.practiceAndPriorities,
                    livingSituationAndPlans = current.livingSituationAndPlans,
                    guardianOrRepresentativeName = current.guardianName,
                    guardianOrRepresentativeRelationship = current.guardianRelationship,
                    confirmsMarriageConsideration = current.confirmsMarriage,
                    confirmsConductRules = current.confirmsConduct,
                ),
            )
            when (outcome) {
                is Outcome.Success -> _state.update {
                    it.copy(submitting = false, submitted = true)
                }
                is Outcome.Invalid -> _state.update {
                    it.copy(submitting = false, errors = outcome.errors)
                }
                is Outcome.Refused -> _state.update {
                    it.copy(submitting = false, refusal = outcome.message)
                }
                is Outcome.NotFound -> _state.update {
                    it.copy(submitting = false, refusal = "We could not find that member.")
                }
            }
        }
    }
}

// ── Safety and moderation ────────────────────────────────────────────────────

internal data class ReportState(
    val category: ReportCategory? = null,
    val description: String = "",
    val includeEvidence: Boolean = true,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val refusal: String? = null,
)

internal class ReportViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
    private val target: ReportTarget,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    fun setCategory(category: ReportCategory) = _state.update { it.copy(category = category) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }
    fun setIncludeEvidence(value: Boolean) = _state.update { it.copy(includeEvidence = value) }

    fun submit() {
        val current = _state.value
        val category = current.category ?: return
        _state.update { it.copy(submitting = true, refusal = null) }

        viewModelScope.launch {
            val outcome = graph.core.submitReport(
                principal,
                SubmitReportUseCase.Command(
                    target = target,
                    category = category,
                    description = current.description,
                    includeConversationEvidence = current.includeEvidence,
                ),
            )
            when (outcome) {
                is Outcome.Success -> _state.update { it.copy(submitting = false, submitted = true) }
                is Outcome.Refused -> _state.update {
                    it.copy(submitting = false, refusal = outcome.message)
                }
                is Outcome.Invalid -> _state.update {
                    it.copy(submitting = false, refusal = outcome.errors.first().message)
                }
                is Outcome.NotFound -> _state.update {
                    it.copy(submitting = false, refusal = "That could not be reported.")
                }
            }
        }
    }
}

/**
 * What the platform has done to this member, and their right to argue with it.
 *
 * Deliberately a screen a member can reach without being invited to. The alternative --
 * showing the appeal route only in the notification that announced the restriction -- means
 * that the person who was asleep, or who cleared the notification, or who came back a week
 * later, has no way in at all.
 */
internal class MyModerationRecordViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScreenState<List<MyModerationRecordUseCase.RestrictionRecord>>(),
    )
    val state: StateFlow<ScreenState<List<MyModerationRecordUseCase.RestrictionRecord>>> =
        _state.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = graph.core.myModerationRecord(principal).toScreenState()
        }
    }

    fun appeal(
        caseId: org.fisabilillah.core.model.ModerationCaseId,
        statement: String,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            _submitting.value = true
            val outcome = graph.core.reviewAppeal.submit(principal, caseId, statement)
            _submitting.value = false
            refresh()
            onResult(
                when (outcome) {
                    is Outcome.Success -> null
                    is Outcome.Refused -> outcome.message
                    is Outcome.Invalid -> outcome.errors.first().message
                    is Outcome.NotFound -> "We could not find ${outcome.what}."
                },
            )
        }
    }
}

/** The safety team's side: appeals waiting to be read. */
internal class AppealQueueViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenState<List<AppealQueueUseCase.QueueItem>>())
    val state: StateFlow<ScreenState<List<AppealQueueUseCase.QueueItem>>> = _state.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = graph.core.appealQueue(principal).toScreenState()
        }
    }

    fun decide(
        appealId: org.fisabilillah.core.model.AppealId,
        state: org.fisabilillah.core.model.AppealState,
        note: String,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            _submitting.value = true
            val outcome = graph.core.reviewAppeal.decide(principal, appealId, state, note)
            _submitting.value = false
            refresh()
            onResult(
                when (outcome) {
                    is Outcome.Success -> null
                    is Outcome.Refused -> outcome.message
                    is Outcome.Invalid -> outcome.errors.first().message
                    is Outcome.NotFound -> "We could not find ${outcome.what}."
                },
            )
        }
    }
}

internal class ModerationViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state =
        MutableStateFlow(ScreenState<List<org.fisabilillah.core.domain.ModerationQueueUseCase.QueueItem>>())
    val state: StateFlow<ScreenState<List<org.fisabilillah.core.domain.ModerationQueueUseCase.QueueItem>>> =
        _state.asStateFlow()

    private val _auditTrail = MutableStateFlow<List<org.fisabilillah.core.model.AuditLogEntry>>(emptyList())
    val auditTrail: StateFlow<List<org.fisabilillah.core.model.AuditLogEntry>> =
        _auditTrail.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ScreenState(loading = true)
            _state.value = graph.core.moderationQueue(principal).toScreenState()
            if (principal.isSafetyAdmin) {
                _auditTrail.value = graph.core.auditLog.recent(50)
            }
        }
    }

    fun act(command: TakeModerationActionUseCase.Command, onResult: (Outcome<*>) -> Unit) {
        viewModelScope.launch {
            onResult(graph.core.takeModerationAction(principal, command))
            refresh()
        }
    }
}

// ── Notifications and communities ────────────────────────────────────────────

internal class NotificationsViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _state = MutableStateFlow<List<Notification>>(emptyList())
    val state: StateFlow<List<Notification>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = graph.core.notifications.forUser(principal.userId)
        }
    }

    fun markRead(id: org.fisabilillah.core.model.NotificationId) {
        viewModelScope.launch {
            graph.core.notifications.markRead(id, graph.core.clock.now())
            refresh()
        }
    }
}

internal class CommunityViewModel(
    private val graph: AppGraph,
) : ViewModel() {

    private val _state = MutableStateFlow<List<Community>>(emptyList())
    val state: StateFlow<List<Community>> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = graph.core.communities.list().items }
    }

    suspend fun community(id: org.fisabilillah.core.model.CommunityId): Community? =
        graph.core.communities.find(id)

    suspend fun members(id: org.fisabilillah.core.model.CommunityId) =
        graph.core.communities.members(id)

    suspend fun organization(id: org.fisabilillah.core.model.OrganizationId) =
        graph.core.organizations.find(id)
}

// ── Trust, authoring and administration ──────────────────────────────────────

/**
 * One view model for both sides of trust, because a member and a reviewer are looking at
 * the same data from different ends and keeping two would mean two places to forget a
 * refresh.
 */
internal class TrustViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _mine = MutableStateFlow(ScreenState<MyVerificationUseCase.State>())
    val mine: StateFlow<ScreenState<MyVerificationUseCase.State>> = _mine.asStateFlow()

    private val _qualifications = MutableStateFlow<List<Qualification>>(emptyList())
    val qualifications: StateFlow<List<Qualification>> = _qualifications.asStateFlow()

    private val _verificationQueue = MutableStateFlow<List<VerificationRequest>>(emptyList())
    val verificationQueue: StateFlow<List<VerificationRequest>> = _verificationQueue.asStateFlow()

    private val _qualificationQueue = MutableStateFlow<List<Qualification>>(emptyList())
    val qualificationQueue: StateFlow<List<Qualification>> = _qualificationQueue.asStateFlow()

    private val _errors = MutableStateFlow<List<ValidationError>>(emptyList())
    val errors: StateFlow<List<ValidationError>> = _errors.asStateFlow()

    private val _refusal = MutableStateFlow<String?>(null)
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _mine.value = graph.core.myVerification(principal).toScreenState()
            _qualifications.value = graph.core.reviewQualification.mine(principal).valueOr(emptyList())
            _verificationQueue.value =
                graph.core.decideVerification.queue(principal).valueOr(emptyList())
            _qualificationQueue.value =
                graph.core.reviewQualification.queue(principal).valueOr(emptyList())
        }
    }

    fun requestVerification(
        level: VerificationLevel,
        method: VerificationMethod,
        evidence: List<String>,
        note: String?,
        onDone: (Boolean) -> Unit,
    ) = act(onDone) {
        graph.core.requestVerification(
            principal,
            RequestVerificationUseCase.Command(level, method, evidence, note),
        )
    }

    fun submitQualification(title: String, issuingBody: String, year: Int?, onDone: (Boolean) -> Unit) =
        act(onDone) {
            graph.core.submitQualification(
                principal,
                SubmitQualificationUseCase.Command(title, issuingBody, year),
            )
        }

    fun decideVerification(id: VerificationRequestId, approve: Boolean, note: String) =
        act({}) {
            if (approve) {
                graph.core.decideVerification.approve(principal, id, note)
            } else {
                graph.core.decideVerification.reject(principal, id, note)
            }
        }

    fun decideQualification(id: QualificationId, verified: Boolean, note: String) =
        act({}) { graph.core.reviewQualification.decide(principal, id, verified, note) }

    private fun act(onDone: (Boolean) -> Unit, block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            _submitting.value = true
            _errors.value = emptyList()
            _refusal.value = null
            val outcome = block()
            _submitting.value = false
            when (outcome) {
                is Outcome.Success -> Unit
                is Outcome.Invalid -> _errors.value = outcome.errors
                is Outcome.Refused -> _refusal.value = outcome.message
                is Outcome.NotFound -> _refusal.value = "We could not find ${outcome.what}."
            }
            refresh()
            onDone(outcome is Outcome.Success)
        }
    }
}

/** Creating a class, a project or a community. One view model; the screens differ. */
internal class AuthoringViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _errors = MutableStateFlow<List<ValidationError>>(emptyList())
    val errors: StateFlow<List<ValidationError>> = _errors.asStateFlow()

    private val _refusal = MutableStateFlow<String?>(null)
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    fun createClass(command: CreateLearningOfferingUseCase.Command, onDone: (Boolean) -> Unit) =
        act(onDone) { graph.core.createLearningOffering(principal, command) }

    fun createProject(command: CreateProjectUseCase.Command, onDone: (Boolean) -> Unit) =
        act(onDone) { graph.core.createProject(principal, command) }

    fun createCommunity(command: CreateCommunityUseCase.Command, onDone: (Boolean) -> Unit) =
        act(onDone) { graph.core.createCommunity(principal, command) }

    private fun act(onDone: (Boolean) -> Unit, block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            _submitting.value = true
            _errors.value = emptyList()
            _refusal.value = null
            val outcome = block()
            _submitting.value = false
            when (outcome) {
                is Outcome.Success -> Unit
                is Outcome.Invalid -> _errors.value = outcome.errors
                is Outcome.Refused -> _refusal.value = outcome.message
                is Outcome.NotFound -> _refusal.value = "We could not find ${outcome.what}."
            }
            onDone(outcome is Outcome.Success)
        }
    }
}

/** Commitments: the member's own, and the ones waiting on them as an organiser. */
internal class CommitmentsViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _mine = MutableStateFlow<List<Commitment>>(emptyList())
    val mine: StateFlow<List<Commitment>> = _mine.asStateFlow()

    private val _awaiting = MutableStateFlow<List<Commitment>>(emptyList())
    val awaiting: StateFlow<List<Commitment>> = _awaiting.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refusal = MutableStateFlow<String?>(null)
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _mine.value = graph.core.commitments.forUser(principal.userId)

            // Commitments on listings this member organises. Read here rather than in a
            // repository query because "am I the organiser" is a fact about the listing,
            // and the commitment does not carry it.
            val myListings = graph.core.store.opportunities.values
                .filter { it.organizerId == principal.userId }
                .map { it.id }
                .toSet()
            _awaiting.value = graph.core.store.commitments.values.filter { commitment ->
                val subject = commitment.subject
                subject is CommitmentSubject.Opportunity && subject.id in myListings
            }
            _loading.value = false
        }
    }

    fun checkIn(id: CommitmentId) = act { graph.core.commitmentActions.checkIn(principal, id) }
    fun checkOut(id: CommitmentId) = act { graph.core.commitmentActions.checkOut(principal, id) }
    fun confirm(id: CommitmentId, attended: Boolean) =
        act { graph.core.commitmentActions.confirmByOrganizer(principal, id, attended) }

    fun endorse(id: CommitmentId, category: ServiceCategory, note: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _submitting.value = true
            val outcome = graph.core.endorseTask(principal, id, category, note)
            _submitting.value = false
            if (outcome is Outcome.Refused) _refusal.value = outcome.message
            refresh()
            onDone(outcome is Outcome.Success)
        }
    }

    private fun act(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            _submitting.value = true
            _refusal.value = null
            val outcome = block()
            _submitting.value = false
            if (outcome is Outcome.Refused) _refusal.value = outcome.message
            refresh()
        }
    }
}

/** Role administration and device sessions. */
internal class AdministrationViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _staff = MutableStateFlow<List<Profile>>(emptyList())
    val staff: StateFlow<List<Profile>> = _staff.asStateFlow()

    private val _sessions = MutableStateFlow<List<DeviceSession>>(emptyList())
    val sessions: StateFlow<List<DeviceSession>> = _sessions.asStateFlow()

    private val _refusal = MutableStateFlow<String?>(null)
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    val grantable: List<AccountRole> get() = graph.core.manageRoles.grantable

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _staff.value = graph.core.manageRoles.staff(principal).valueOr(emptyList())
            _sessions.value = graph.core.devices.mine(principal).valueOr(emptyList())
        }
    }

    fun grant(userId: UserId, role: AccountRole, reason: String) =
        act { graph.core.manageRoles.grant(principal, userId, role, reason) }

    fun revoke(userId: UserId, role: AccountRole, reason: String) =
        act { graph.core.manageRoles.revoke(principal, userId, role, reason) }

    fun endSession(id: String) = act { graph.core.devices.revoke(principal, id) }
    fun endOtherSessions(keep: String) = act { graph.core.devices.revokeAllOthers(principal, keep) }

    private fun act(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            _submitting.value = true
            _refusal.value = null
            val outcome = block()
            _submitting.value = false
            when (outcome) {
                is Outcome.Refused -> _refusal.value = outcome.message
                is Outcome.Invalid -> _refusal.value = outcome.errors.first().message
                is Outcome.NotFound -> _refusal.value = "We could not find ${outcome.what}."
                is Outcome.Success -> Unit
            }
            refresh()
        }
    }
}

/** Your own data, and leaving. */
internal class AccountDataViewModel(
    private val graph: AppGraph,
    private val principal: Principal,
) : ViewModel() {

    private val _export = MutableStateFlow<ExportMyDataUseCase.Export?>(null)
    val export: StateFlow<ExportMyDataUseCase.Export?> = _export.asStateFlow()

    private val _deletion = MutableStateFlow<RequestAccountDeletionUseCase.Result?>(null)
    val deletion: StateFlow<RequestAccountDeletionUseCase.Result?> = _deletion.asStateFlow()

    private val _refusal = MutableStateFlow<String?>(null)
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    fun exportData() {
        viewModelScope.launch {
            when (val outcome = graph.core.exportMyData(principal)) {
                is Outcome.Success -> _export.value = outcome.value
                is Outcome.Refused -> _refusal.value = outcome.message
                is Outcome.NotFound -> _refusal.value = "We could not find ${outcome.what}."
                is Outcome.Invalid -> _refusal.value = outcome.errors.first().message
            }
        }
    }

    fun requestDeletion() {
        viewModelScope.launch {
            when (val outcome = graph.core.requestAccountDeletion(principal, null)) {
                is Outcome.Success -> _deletion.value = outcome.value
                is Outcome.Refused -> _refusal.value = outcome.message
                is Outcome.NotFound -> _refusal.value = "We could not find ${outcome.what}."
                is Outcome.Invalid -> _refusal.value = outcome.errors.first().message
            }
        }
    }
}

/** Folds an outcome to its value or a fallback, for the read-only list loads above. */
private fun <T> Outcome<T>.valueOr(fallback: T): T =
    if (this is Outcome.Success) value else fallback
