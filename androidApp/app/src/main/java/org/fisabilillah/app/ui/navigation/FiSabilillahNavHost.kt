package org.fisabilillah.app.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import kotlinx.coroutines.launch
import org.fisabilillah.app.di.AppGraph
import org.fisabilillah.app.di.SignInResult
import org.fisabilillah.app.di.SignUpResult
import org.fisabilillah.app.di.anonymousViewModelFactory
import org.fisabilillah.app.di.viewModelFactory
import org.fisabilillah.app.ui.screens.community.CommunityDetailScreen
import org.fisabilillah.app.ui.screens.community.CommunityScreen
import org.fisabilillah.app.ui.screens.community.MemberProfileScreen
import org.fisabilillah.app.ui.screens.community.OrganizationDetailScreen
import org.fisabilillah.app.ui.screens.giving.CampaignsScreen
import org.fisabilillah.app.ui.screens.giving.DonateScreen
import org.fisabilillah.app.ui.screens.giving.MyGivingScreen
import org.fisabilillah.app.ui.screens.home.HomeScreen
import org.fisabilillah.app.ui.screens.learn.LearnScreen
import org.fisabilillah.app.ui.screens.learn.LearningDetailScreen
import org.fisabilillah.app.ui.screens.legal.CommunityGuidelinesScreen
import org.fisabilillah.app.ui.screens.legal.GivingComplianceScreen
import org.fisabilillah.app.ui.screens.legal.PrivacyPolicyScreen
import org.fisabilillah.app.ui.screens.legal.TermsScreen
import org.fisabilillah.app.ui.screens.messages.ComposeConversationScreen
import org.fisabilillah.app.ui.screens.messages.ConversationScreen
import org.fisabilillah.app.ui.screens.messages.MessagesScreen
import org.fisabilillah.app.ui.screens.moderation.AdminDashboardScreen
import org.fisabilillah.app.ui.screens.moderation.ModerationCaseScreen
import org.fisabilillah.app.ui.screens.moderation.ModeratorDashboardScreen
import org.fisabilillah.app.ui.screens.onboarding.AccountRecoveryScreen
import org.fisabilillah.app.ui.screens.onboarding.EmailConfirmedScreen
import org.fisabilillah.app.ui.screens.onboarding.LandingScreen
import org.fisabilillah.app.ui.screens.onboarding.MissionScreen
import org.fisabilillah.app.ui.screens.onboarding.OnboardingConsentScreen
import org.fisabilillah.app.ui.screens.onboarding.OnboardingProfileScreen
import org.fisabilillah.app.ui.screens.onboarding.OnboardingSafeguardsScreen
import org.fisabilillah.app.ui.screens.onboarding.OnboardingSkillsScreen
import org.fisabilillah.app.ui.screens.onboarding.OnboardingState
import org.fisabilillah.app.ui.screens.onboarding.SignInScreen
import org.fisabilillah.app.ui.screens.onboarding.SignUpMessage
import org.fisabilillah.app.ui.screens.onboarding.SignUpScreen
import org.fisabilillah.app.ui.screens.profile.AccountDataScreen
import org.fisabilillah.app.ui.screens.profile.EditProfileScreen
import org.fisabilillah.app.ui.screens.profile.ProfileScreen
import org.fisabilillah.app.ui.screens.profile.ServiceHistoryScreen
import org.fisabilillah.app.ui.screens.projects.ProjectDetailScreen
import org.fisabilillah.app.ui.screens.projects.ProjectsScreen
import org.fisabilillah.app.ui.screens.requests.CreateRequestScreen
import org.fisabilillah.app.ui.screens.requests.RequestDetailScreen
import org.fisabilillah.app.ui.screens.requests.RequestsScreen
import org.fisabilillah.app.ui.screens.safety.IntroductionDetailScreen
import org.fisabilillah.app.ui.screens.create.CreateClassScreen
import org.fisabilillah.app.ui.screens.create.CreateCommunityScreen
import org.fisabilillah.app.ui.screens.create.CreateProjectScreen
import org.fisabilillah.app.ui.screens.moderation.DeviceSessionsScreen
import org.fisabilillah.app.ui.screens.moderation.RoleAdministrationScreen
import org.fisabilillah.app.ui.screens.profile.EndorseScreen
import org.fisabilillah.app.ui.screens.profile.MyCommitmentsScreen
import org.fisabilillah.app.ui.screens.safety.AppealQueueScreen
import org.fisabilillah.app.ui.screens.trust.MyQualificationsScreen
import org.fisabilillah.app.ui.screens.trust.MyVerificationScreen
import org.fisabilillah.app.ui.screens.trust.RequestVerificationScreen
import org.fisabilillah.app.ui.screens.trust.TrustReviewScreen
import org.fisabilillah.app.ui.screens.safety.MyReportsScreen
import org.fisabilillah.app.ui.screens.safety.MyRestrictionsScreen
import org.fisabilillah.app.ui.screens.safety.SubmitAppealScreen
import org.fisabilillah.app.ui.screens.safety.NotificationsScreen
import org.fisabilillah.app.ui.screens.safety.PrivacyControlsScreen
import org.fisabilillah.app.ui.screens.safety.ReportScreen
import org.fisabilillah.app.ui.screens.safety.SafeguardSettingsScreen
import org.fisabilillah.app.ui.screens.safety.SafetyCentreScreen
import org.fisabilillah.app.ui.screens.safety.SubmitIntroductionScreen
import org.fisabilillah.app.ui.screens.safety.TrustedContactsScreen
import org.fisabilillah.app.ui.screens.safety.WaliSettingsScreen
import org.fisabilillah.app.ui.screens.serve.CreateListingScreen
import org.fisabilillah.app.ui.screens.serve.OpportunityDetailScreen
import org.fisabilillah.app.ui.screens.serve.ServeScreen
import org.fisabilillah.app.ui.viewmodel.ComposeConversationViewModel
import org.fisabilillah.app.ui.viewmodel.ConversationViewModel
import org.fisabilillah.app.ui.viewmodel.CommunityViewModel
import org.fisabilillah.app.ui.viewmodel.GivingViewModel
import org.fisabilillah.app.ui.viewmodel.HomeViewModel
import org.fisabilillah.app.ui.viewmodel.LearnViewModel
import org.fisabilillah.app.ui.viewmodel.MessagesViewModel
import org.fisabilillah.app.ui.viewmodel.AccountDataViewModel
import org.fisabilillah.app.ui.viewmodel.AdministrationViewModel
import org.fisabilillah.app.ui.viewmodel.AppealQueueViewModel
import org.fisabilillah.app.ui.viewmodel.AuthoringViewModel
import org.fisabilillah.app.ui.viewmodel.CommitmentsViewModel
import org.fisabilillah.app.ui.viewmodel.TrustViewModel
import org.fisabilillah.app.ui.viewmodel.ModerationViewModel
import org.fisabilillah.app.ui.viewmodel.MyModerationRecordViewModel
import org.fisabilillah.app.ui.viewmodel.NotificationsViewModel
import org.fisabilillah.app.ui.viewmodel.PeopleViewModel
import org.fisabilillah.app.ui.viewmodel.ProfileViewModel
import org.fisabilillah.app.ui.viewmodel.ProjectsViewModel
import org.fisabilillah.app.ui.viewmodel.ReportViewModel
import org.fisabilillah.app.ui.viewmodel.RequestsViewModel
import org.fisabilillah.app.ui.viewmodel.SafeguardViewModel
import org.fisabilillah.app.ui.viewmodel.ServeViewModel
import org.fisabilillah.app.ui.viewmodel.SubmitIntroductionViewModel
import org.fisabilillah.app.ui.viewmodel.WaliViewModel
import org.fisabilillah.core.auth.MemberRegistration
import org.fisabilillah.core.domain.CompleteOnboardingUseCase
import org.fisabilillah.core.domain.CreateCommunityUseCase
import org.fisabilillah.core.domain.CreateLearningOfferingUseCase
import org.fisabilillah.core.domain.CreateOpportunityUseCase
import org.fisabilillah.core.domain.CreateProjectUseCase
import org.fisabilillah.core.domain.CreateServiceRequestUseCase
import org.fisabilillah.core.domain.DefaultOpportunityTiming
import org.fisabilillah.core.domain.IntroductionDecisionAction
import org.fisabilillah.core.domain.Outcome
import org.fisabilillah.core.domain.TakeModerationActionUseCase
import org.fisabilillah.core.model.ApproximateLocation
import org.fisabilillah.core.model.Availability
import org.fisabilillah.core.model.CampaignId
import org.fisabilillah.core.model.CommitmentId
import org.fisabilillah.core.model.CommunityId
import org.fisabilillah.core.model.ConversationId
import org.fisabilillah.core.model.ExactLocation
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.IntroductionId
import org.fisabilillah.core.model.Language
import org.fisabilillah.core.model.ListingId
import org.fisabilillah.core.model.MessageId
import org.fisabilillah.core.model.ModerationCaseId
import org.fisabilillah.core.model.OrganizationId
import org.fisabilillah.core.model.Place
import org.fisabilillah.core.model.Profile
import org.fisabilillah.core.model.ProjectId
import org.fisabilillah.core.model.PurposeSubject
import org.fisabilillah.core.model.ReportTarget
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.ServiceCategory
import org.fisabilillah.core.model.ServiceRequest
import org.fisabilillah.core.model.UserId
import org.fisabilillah.core.policy.ValidationError
import org.fisabilillah.core.model.VolunteerOpportunity
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The navigation graph.
 *
 * One place where every screen is wired to the use cases that feed it. Screens themselves
 * take plain data and callbacks — no screen reaches for the graph, and none of them decide
 * anything a safety rule depends on. That keeps the boundary honest: if a refusal message
 * appears on screen, it came from `core:policy`, not from a `when` block in a composable.
 */
@Composable
internal fun FiSabilillahNavHost(
    navController: NavHostController,
    graph: AppGraph,
    contentPadding: PaddingValues,
    showModeration: Boolean,
    /** Decided once, from the restored session. See MainActivity for why it is not a navigate(). */
    startDestination: String = Routes.LANDING,
) {
    val scope = rememberCoroutineScope()
    val principal by graph.session.principal.collectAsState()

    // Onboarding spans four screens; the answers live here so that going back does not
    // discard them.
    var onboarding by remember { mutableStateOf(OnboardingState()) }
    var onboardingError by remember { mutableStateOf<String?>(null) }

    fun goHome() {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.LANDING) { inclusive = true }
            launchSingleTop = true
        }
    }

    // A signed-in account with no profile has nowhere else to be. The landing screen is
    // popped so that "back" from the first onboarding step does not return to a sign-in
    // form for an account that is already signed in.
    fun goOnboarding() {
        navController.navigate(Routes.ONBOARDING_PROFILE) {
            popUpTo(Routes.LANDING) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {

        // ── Unauthenticated ───────────────────────────────────────────────────
        composable(Routes.LANDING) {
            LandingScreen(
                onSignIn = { navController.navigate(Routes.SIGN_IN) },
                onSignUp = { navController.navigate(Routes.SIGN_UP) },
                onMission = { navController.navigate(Routes.MISSION) },
            )
        }

        composable(Routes.MISSION) {
            MissionScreen(onBack = { navController.popBackStack() })
        }

        // Entered from a browser after GoTrue has verified the confirmation link. The
        // deep link has to be declared here as well as in the manifest: the manifest gets
        // the app opened, and this is what decides where in it the person lands.
        composable(
            route = Routes.EMAIL_CONFIRMED,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.EMAIL_CONFIRMED_URI }),
        ) {
            EmailConfirmedScreen(
                onContinue = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.EMAIL_CONFIRMED) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SIGN_IN) {
            SignInScreen(
                // Returns the message to show, or null when the screen is done with. The
                // decision about where to go next is made from the session state, not from
                // anything this screen knows.
                onSignIn = { email, password ->
                    when (val result = graph.session.signIn(email, password)) {
                        is SignInResult.Ready -> { goHome(); null }
                        is SignInResult.NeedsOnboarding -> { goOnboarding(); null }
                        is SignInResult.SignedOut ->
                            "That account could not be opened. Try again in a moment."
                        is SignInResult.Failed -> result.message
                    }
                },
                onSignUp = { navController.navigate(Routes.SIGN_UP) },
                onRecover = { navController.navigate(Routes.ACCOUNT_RECOVERY) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onCreateAccount = { email, password ->
                    when (val result = graph.session.signUp(email, password)) {
                        is SignUpResult.CheckYourEmail -> SignUpMessage.CheckYourEmail(
                            "Check your inbox. If that address can be registered, a " +
                                "confirmation message is on its way. Follow the link in it, " +
                                "then come back and sign in.",
                        )
                        is SignUpResult.SignedIn -> {
                            goOnboarding()
                            SignUpMessage.Continue
                        }
                        is SignUpResult.Failed -> SignUpMessage.Failed(result.message)
                    }
                },
                onSignIn = { navController.navigate(Routes.SIGN_IN) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ACCOUNT_RECOVERY) {
            AccountRecoveryScreen(
                onSendResetLink = { email -> graph.session.sendRecoveryEmail(email) },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Onboarding ────────────────────────────────────────────────────────
        composable(Routes.ONBOARDING_PROFILE) {
            OnboardingProfileScreen(
                state = onboarding,
                onUpdate = { transform -> onboarding = transform(onboarding) },
                onContinue = { navController.navigate(Routes.ONBOARDING_SKILLS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ONBOARDING_SKILLS) {
            OnboardingSkillsScreen(
                state = onboarding,
                onUpdate = { transform -> onboarding = transform(onboarding) },
                onContinue = { navController.navigate(Routes.ONBOARDING_SAFEGUARDS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ONBOARDING_SAFEGUARDS) {
            OnboardingSafeguardsScreen(
                state = onboarding,
                onUpdate = { transform -> onboarding = transform(onboarding) },
                onContinue = { navController.navigate(Routes.ONBOARDING_CONSENT) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ONBOARDING_CONSENT) {
            OnboardingConsentScreen(
                state = onboarding,
                onUpdate = { transform -> onboarding = transform(onboarding) },
                errorMessage = onboardingError,
                onFinish = {
                    scope.launch {
                        onboardingError = completeOnboarding(graph, onboarding)
                        if (onboardingError == null) goHome()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Home ──────────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> HomeViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            HomeScreen(
                state = state,
                contentPadding = contentPadding,
                onOpenOpportunity = { navController.navigate(Routes.opportunity(it.value)) },
                onOpenRequest = { navController.navigate(Routes.request(it.value)) },
                onOpenLearning = { navController.navigate(Routes.learning(it.value)) },
                onOpenProject = { navController.navigate(Routes.project(it.value)) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenServiceHistory = { navController.navigate(Routes.SERVICE_HISTORY) },
                onSeeAllServe = { navController.navigate(Routes.SERVE) },
                onSeeAllRequests = { navController.navigate(Routes.REQUESTS) },
                onSeeAllLearn = { navController.navigate(Routes.LEARN) },
            )
        }

        // ── Serve ─────────────────────────────────────────────────────────────
        composable(Routes.SERVE) {
            val viewModel: ServeViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ServeViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            ServeScreen(
                state = state,
                onSelect = { navController.navigate(Routes.opportunity(it.value)) },
                onToggleHideBackgroundCheck = viewModel::setHideBackgroundCheckRequired,
                onCreate = { navController.navigate(Routes.CREATE_LISTING) },
            )
        }

        composable(Routes.OPPORTUNITY_DETAIL) { entry ->
            val id = ListingId(entry.arguments?.getString("id").orEmpty())
            val viewModel: ServeViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ServeViewModel(g, p) },
            )
            var applying by remember { mutableStateOf(false) }
            var refusal by remember { mutableStateOf<String?>(null) }

            val opportunity by produceState<VolunteerOpportunity?>(null, id) {
                value = graph.core.opportunities.find(id)
            }
            val organiserName by produceState("", opportunity) {
                value = opportunity?.organizerId
                    ?.let { graph.core.profiles.find(it)?.displayName }
                    .orEmpty()
            }
            val organisationName by produceState<String?>(null, opportunity) {
                value = opportunity?.organizationId?.let { graph.core.organizations.find(it)?.name }
            }

            OpportunityDetailScreen(
                opportunity = opportunity,
                organiserName = organiserName,
                organisationName = organisationName,
                applying = applying,
                refusal = refusal,
                onApply = { message ->
                    applying = true
                    refusal = null
                    viewModel.apply(id, message) { outcome ->
                        applying = false
                        refusal = outcome.refusalMessage()
                    }
                },
                onMessageOrganiser = {
                    opportunity?.organizerId?.let {
                        navController.navigate(Routes.compose(it.value))
                    }
                },
                onReport = { navController.navigate(Routes.report("listing", id.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CREATE_LISTING) {
            var submitting by remember { mutableStateOf(false) }
            var errors by remember { mutableStateOf<List<ValidationError>>(emptyList()) }
            var refusal by remember { mutableStateOf<String?>(null) }

            CreateListingScreen(
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onSubmit = { draft ->
                    submitting = true
                    errors = emptyList()
                    refusal = null
                    scope.launch {
                        // The screen only closes on success. Popping the back stack
                        // regardless -- which is what this did -- threw away both the
                        // errors and everything the organiser had typed.
                        when (val outcome = createOpportunity(graph, draft)) {
                            is Outcome.Success -> navController.popBackStack()
                            is Outcome.Invalid -> errors = outcome.errors
                            is Outcome.Refused -> refusal = outcome.message
                            is Outcome.NotFound -> refusal = "We could not find ${outcome.what}."
                        }
                        submitting = false
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Learn ─────────────────────────────────────────────────────────────
        composable(Routes.LEARN) {
            val viewModel: LearnViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> LearnViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            LearnScreen(
                state = state,
                onSelect = { navController.navigate(Routes.learning(it.value)) },
                onToggleFreeOnly = viewModel::setFreeOnly,
            )
        }

        composable(Routes.LEARNING_DETAIL) { entry ->
            val id = ListingId(entry.arguments?.getString("id").orEmpty())
            val viewModel: LearnViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> LearnViewModel(g, p) },
            )
            var enrolling by remember { mutableStateOf(false) }
            var refusal by remember { mutableStateOf<String?>(null) }

            val offering by produceState<org.fisabilillah.core.model.LearningOffering?>(null, id) {
                value = graph.core.learning.find(id)
            }
            val instructorName by produceState("", offering) {
                value = offering?.instructorId
                    ?.let { graph.core.profiles.find(it)?.displayName }
                    .orEmpty()
            }

            LearningDetailScreen(
                offering = offering,
                instructorName = instructorName,
                enrolling = enrolling,
                refusal = refusal,
                onEnrol = {
                    enrolling = true
                    refusal = null
                    viewModel.enrol(id) { outcome ->
                        enrolling = false
                        refusal = outcome.refusalMessage()
                    }
                },
                onMessageInstructor = {
                    offering?.instructorId?.let {
                        navController.navigate(Routes.compose(it.value))
                    }
                },
                onReport = { navController.navigate(Routes.report("listing", id.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Requests ──────────────────────────────────────────────────────────
        composable(Routes.REQUESTS) {
            val viewModel: RequestsViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> RequestsViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            RequestsScreen(
                state = state,
                onSelect = { navController.navigate(Routes.request(it.value)) },
                onCreate = { navController.navigate(Routes.CREATE_REQUEST) },
            )
        }

        composable(Routes.REQUEST_DETAIL) { entry ->
            val id = RequestId(entry.arguments?.getString("id").orEmpty())
            val viewModel: RequestsViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> RequestsViewModel(g, p) },
            )
            var responding by remember { mutableStateOf(false) }
            var refusal by remember { mutableStateOf<String?>(null) }

            val request by produceState<org.fisabilillah.core.model.VisibleServiceRequest?>(null, id) {
                value = viewModel.detail(id)
            }

            RequestDetailScreen(
                request = request,
                responding = responding,
                refusal = refusal,
                onRespond = { message ->
                    responding = true
                    refusal = null
                    viewModel.respond(id, message) { outcome ->
                        responding = false
                        refusal = outcome.refusalMessage()
                    }
                },
                onReport = { navController.navigate(Routes.report("request", id.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CREATE_REQUEST) {
            var submitting by remember { mutableStateOf(false) }
            var errors by remember { mutableStateOf<List<ValidationError>>(emptyList()) }
            var refusal by remember { mutableStateOf<String?>(null) }

            CreateRequestScreen(
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onSubmit = { draft ->
                    submitting = true
                    errors = emptyList()
                    refusal = null
                    scope.launch {
                        when (val outcome = createRequest(graph, draft)) {
                            is Outcome.Success -> navController.popBackStack()
                            is Outcome.Invalid -> errors = outcome.errors
                            is Outcome.Refused -> refusal = outcome.message
                            is Outcome.NotFound -> refusal = "We could not find ${outcome.what}."
                        }
                        submitting = false
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Projects ──────────────────────────────────────────────────────────
        composable(Routes.PROJECTS) {
            val viewModel: ProjectsViewModel = viewModel(
                factory = anonymousViewModelFactory(graph) { g -> ProjectsViewModel(g) },
            )
            val projects by viewModel.state.collectAsState()

            ProjectsScreen(
                projects = projects,
                onSelect = { navController.navigate(Routes.project(it.value)) },
            )
        }

        composable(Routes.PROJECT_DETAIL) { entry ->
            val id = ProjectId(entry.arguments?.getString("id").orEmpty())
            val viewModel: ProjectsViewModel = viewModel(
                factory = anonymousViewModelFactory(graph) { g -> ProjectsViewModel(g) },
            )
            val project by produceState<org.fisabilillah.core.model.Project?>(null, id) {
                value = viewModel.project(id)
            }
            val tasks by produceState<List<org.fisabilillah.core.model.ProjectTask>>(emptyList(), id) {
                value = viewModel.tasks(id)
            }
            val organiserName by produceState("", project) {
                value = project?.organizerId
                    ?.let { graph.core.profiles.find(it)?.displayName }
                    .orEmpty()
            }

            ProjectDetailScreen(
                project = project,
                tasks = tasks,
                organiserName = organiserName,
                onMessageOrganiser = {
                    project?.organizerId?.let { navController.navigate(Routes.compose(it.value)) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Community ─────────────────────────────────────────────────────────
        composable(Routes.COMMUNITY) {
            val viewModel: CommunityViewModel = viewModel(
                factory = anonymousViewModelFactory(graph) { g -> CommunityViewModel(g) },
            )
            val communities by viewModel.state.collectAsState()

            CommunityScreen(
                communities = communities,
                onSelect = { navController.navigate(Routes.community(it.value)) },
            )
        }

        composable(Routes.COMMUNITY_DETAIL) { entry ->
            val id = CommunityId(entry.arguments?.getString("id").orEmpty())
            val viewModel: CommunityViewModel = viewModel(
                factory = anonymousViewModelFactory(graph) { g -> CommunityViewModel(g) },
            )
            val community by produceState<org.fisabilillah.core.model.Community?>(null, id) {
                value = viewModel.community(id)
            }
            val memberCount by produceState(0, id) { value = viewModel.members(id).size }
            val organisationName by produceState<String?>(null, community) {
                value = community?.organizationId?.let { viewModel.organization(it)?.name }
            }

            CommunityDetailScreen(
                community = community,
                memberCount = memberCount,
                organisationName = organisationName,
                onJoin = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ORGANIZATION_DETAIL) { entry ->
            val id = OrganizationId(entry.arguments?.getString("id").orEmpty())
            val organisation by produceState<org.fisabilillah.core.model.Organization?>(null, id) {
                value = graph.core.organizations.find(id)
            }
            OrganizationDetailScreen(
                organisation = organisation,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MEMBER_PROFILE) { entry ->
            val id = UserId(entry.arguments?.getString("id").orEmpty())
            val viewModel: PeopleViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> PeopleViewModel(g, p) },
            )
            val profile by produceState<org.fisabilillah.core.model.VisibleProfile?>(null, id) {
                value = viewModel.profile(id)
            }

            MemberProfileScreen(
                profile = profile,
                onMessage = { navController.navigate(Routes.compose(id.value)) },
                onFormalIntroduction = {
                    navController.navigate(Routes.submitIntroduction(id.value))
                },
                onBlock = {
                    scope.launch {
                        principal?.let { graph.core.blockUser(it, id) }
                        navController.popBackStack()
                    }
                },
                onReport = { navController.navigate(Routes.report("user", id.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Messaging ─────────────────────────────────────────────────────────
        composable(Routes.MESSAGES) {
            val viewModel: MessagesViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> MessagesViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()
            val names = remember(state.conversations) { mutableStateOf(mapOf<UserId, String>()) }

            MessagesScreen(
                state = state,
                currentUserId = principal?.userId ?: UserId(""),
                nameFor = { userId ->
                    names.value[userId] ?: graph.core.store.profiles[userId]?.displayName ?: "Member"
                },
                onOpen = { navController.navigate(Routes.conversation(it.value)) },
            )
        }

        composable(Routes.CONVERSATION) { entry ->
            val id = ConversationId(entry.arguments?.getString("id").orEmpty())
            val viewModel: ConversationViewModel = viewModel(
                key = id.value,
                factory = viewModelFactory(graph) { g, p -> ConversationViewModel(g, p, id) },
            )
            val state by viewModel.state.collectAsState()
            var transientError by remember { mutableStateOf<String?>(null) }

            ConversationScreen(
                state = state,
                currentUserId = principal?.userId ?: UserId(""),
                transientError = transientError,
                onSend = { body -> viewModel.send(body) { transientError = it } },
                onUnsend = { messageId: MessageId ->
                    viewModel.unsend(messageId) { transientError = it }
                },
                onAddGuardian = { viewModel.addGuardian { transientError = it } },
                onAddModerator = { viewModel.addModerator { transientError = it } },
                onEndConversation = { viewModel.endConversation { navController.popBackStack() } },
                onBlock = { userId -> viewModel.block(userId) { navController.popBackStack() } },
                onReport = { navController.navigate(Routes.report("conversation", id.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.COMPOSE_CONVERSATION) { entry ->
            val recipientId = UserId(entry.arguments?.getString("recipientId").orEmpty())
            val viewModel: ComposeConversationViewModel = viewModel(
                key = recipientId.value,
                factory = viewModelFactory(graph) { g, p ->
                    ComposeConversationViewModel(g, p, recipientId)
                },
            )
            val state by viewModel.state.collectAsState()
            val subjects by produceState<List<Pair<PurposeSubject, String>>>(emptyList()) {
                value = viewModel.availableSubjects()
            }

            ComposeConversationScreen(
                state = state,
                subjects = subjects,
                onUpdatePurposeKind = viewModel::setPurposeKind,
                onUpdateSubject = viewModel::setSubject,
                onUpdateReason = viewModel::setReason,
                onUpdateAction = viewModel::setAction,
                onUpdateDuration = viewModel::setDuration,
                onToggleGuardian = viewModel::setRequestGuardian,
                onToggleModerator = viewModel::setRequestModerator,
                onUpdateOpening = viewModel::setOpeningMessage,
                onSubmit = {
                    viewModel.submit { conversationId ->
                        navController.navigate(Routes.conversation(conversationId.value)) {
                            popUpTo(Routes.COMPOSE_CONVERSATION) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Profile and settings ──────────────────────────────────────────────
        composable(Routes.PROFILE) {
            val viewModel: ProfileViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ProfileViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            ProfileScreen(
                state = state,
                showModeration = showModeration,
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onSafeguards = { navController.navigate(Routes.SAFEGUARD_SETTINGS) },
                onPrivacy = { navController.navigate(Routes.PRIVACY_CONTROLS) },
                onTrustedContacts = { navController.navigate(Routes.TRUSTED_CONTACTS) },
                onWaliSettings = { navController.navigate(Routes.WALI_SETTINGS) },
                onServiceHistory = { navController.navigate(Routes.SERVICE_HISTORY) },
                onGiving = { navController.navigate(Routes.CAMPAIGNS) },
                onCommitments = { navController.navigate(Routes.MY_COMMITMENTS) },
                onVerification = { navController.navigate(Routes.MY_VERIFICATION) },
                onQualifications = { navController.navigate(Routes.MY_QUALIFICATIONS) },
                onCreateClass = { navController.navigate(Routes.CREATE_CLASS) },
                onCreateProject = { navController.navigate(Routes.CREATE_PROJECT) },
                onCreateCommunity = { navController.navigate(Routes.CREATE_COMMUNITY) },
                onDevices = { navController.navigate(Routes.DEVICE_SESSIONS) },
                onRoles = { navController.navigate(Routes.ROLE_ADMINISTRATION) },
                onSafetyCentre = { navController.navigate(Routes.SAFETY_CENTRE) },
                onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onAccountData = { navController.navigate(Routes.ACCOUNT_DATA) },
                onModeration = { navController.navigate(Routes.MODERATOR_DASHBOARD) },
                onSignOut = {
                    scope.launch {
                        // Signing out clears this device first and revokes server-side
                        // afterwards, so a tap on a train still means what it says.
                        graph.session.signOut()
                        navController.navigate(Routes.LANDING) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.EDIT_PROFILE) {
            val viewModel: ProfileViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ProfileViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()
            var saving by remember { mutableStateOf(false) }

            EditProfileScreen(
                profile = state.profile,
                saving = saving,
                onSave = { profile: Profile ->
                    saving = true
                    viewModel.saveProfile(profile) {
                        saving = false
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SERVICE_HISTORY) {
            val viewModel: ProfileViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ProfileViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            ServiceHistoryScreen(
                commitments = state.commitments,
                impact = state.impact,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ACCOUNT_DATA) {
            val viewModel: AccountDataViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> AccountDataViewModel(g, p) },
            )
            AccountDataScreen(
                onExport = viewModel::exportData,
                onRequestDeletion = viewModel::requestDeletion,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DEVICE_SESSIONS) {
            val viewModel: AdministrationViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> AdministrationViewModel(g, p) },
            )
            val sessions by viewModel.sessions.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            DeviceSessionsScreen(
                sessions = sessions,
                currentSessionId = null,
                refusal = refusal,
                submitting = submitting,
                onRevoke = viewModel::endSession,
                onRevokeAllOthers = {
                    sessions.firstOrNull()?.let { viewModel.endOtherSessions(it.id) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Trust ─────────────────────────────────────────────────────────────
        composable(Routes.MY_VERIFICATION) {
            val viewModel: TrustViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> TrustViewModel(g, p) },
            )
            val mine by viewModel.mine.collectAsState()

            MyVerificationScreen(
                state = mine.data,
                loading = mine.loading,
                refusal = mine.refusal,
                onRequest = { navController.navigate(Routes.REQUEST_VERIFICATION) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.REQUEST_VERIFICATION) {
            val viewModel: TrustViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> TrustViewModel(g, p) },
            )
            val errors by viewModel.errors.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            RequestVerificationScreen(
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onSubmit = { level, method, evidence, note ->
                    viewModel.requestVerification(level, method, evidence, note) { ok ->
                        if (ok) navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MY_QUALIFICATIONS) {
            val viewModel: TrustViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> TrustViewModel(g, p) },
            )
            val qualifications by viewModel.qualifications.collectAsState()
            val errors by viewModel.errors.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            MyQualificationsScreen(
                qualifications = qualifications,
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onSubmit = { title, body, year ->
                    viewModel.submitQualification(title, body, year) { }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TRUST_REVIEW) {
            val viewModel: TrustViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> TrustViewModel(g, p) },
            )
            val verifications by viewModel.verificationQueue.collectAsState()
            val qualifications by viewModel.qualificationQueue.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            TrustReviewScreen(
                verifications = verifications,
                qualifications = qualifications,
                canReviewVerifications = principal?.isModerator == true,
                refusal = refusal,
                submitting = submitting,
                onDecideVerification = viewModel::decideVerification,
                onDecideQualification = viewModel::decideQualification,
                onBack = { navController.popBackStack() },
            )
        }

        // ── Commitments ───────────────────────────────────────────────────────
        composable(Routes.MY_COMMITMENTS) {
            val viewModel: CommitmentsViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> CommitmentsViewModel(g, p) },
            )
            val mine by viewModel.mine.collectAsState()
            val awaiting by viewModel.awaiting.collectAsState()
            val loading by viewModel.loading.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            MyCommitmentsScreen(
                commitments = mine,
                awaitingMyConfirmation = awaiting,
                loading = loading,
                refusal = refusal,
                submitting = submitting,
                onCheckIn = viewModel::checkIn,
                onCheckOut = viewModel::checkOut,
                onConfirm = viewModel::confirm,
                onEndorse = { navController.navigate(Routes.endorse(it.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ENDORSE) { entry ->
            val commitmentId = CommitmentId(entry.arguments?.getString("id").orEmpty())
            val viewModel: CommitmentsViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> CommitmentsViewModel(g, p) },
            )
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            EndorseScreen(
                submitting = submitting,
                refusal = refusal,
                onSubmit = { note ->
                    viewModel.endorse(
                        commitmentId,
                        ServiceCategory.COMMUNITY_CLEANUP,
                        note,
                    ) { ok -> if (ok) navController.popBackStack() }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Creating things ───────────────────────────────────────────────────
        composable(Routes.CREATE_CLASS) {
            val viewModel: AuthoringViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> AuthoringViewModel(g, p) },
            )
            val errors by viewModel.errors.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            CreateClassScreen(
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onSubmit = { draft ->
                    viewModel.createClass(
                        CreateLearningOfferingUseCase.Command(
                            title = draft.title,
                            summary = draft.summary,
                            subject = draft.subject,
                            level = draft.level,
                            capacity = draft.capacity,
                            format = draft.format,
                            maxStudents = draft.maxStudents,
                            methodology = draft.methodology,
                            genderArrangement = draft.genderArrangement,
                            sameGenderStudentsOnly = draft.sameGenderStudentsOnly,
                            sourceReferences = draft.sources,
                            isPeerLearning = draft.isPeerLearning,
                        ),
                    ) { ok -> if (ok) navController.popBackStack() }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CREATE_PROJECT) {
            val viewModel: AuthoringViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> AuthoringViewModel(g, p) },
            )
            val errors by viewModel.errors.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            CreateProjectScreen(
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onSubmit = { draft ->
                    viewModel.createProject(
                        CreateProjectUseCase.Command(
                            title = draft.title,
                            summary = draft.summary,
                            category = draft.category,
                            volunteersNeeded = draft.volunteersNeeded,
                            genderArrangement = draft.genderArrangement,
                            isPubliclyListed = draft.isPubliclyListed,
                        ),
                    ) { ok -> if (ok) navController.popBackStack() }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CREATE_COMMUNITY) {
            val viewModel: AuthoringViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> AuthoringViewModel(g, p) },
            )
            val errors by viewModel.errors.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            CreateCommunityScreen(
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onSubmit = { draft ->
                    viewModel.createCommunity(
                        CreateCommunityUseCase.Command(
                            name = draft.name,
                            kind = draft.kind,
                            summary = draft.summary,
                            membershipPolicy = draft.membershipPolicy,
                            genderArrangement = draft.genderArrangement,
                        ),
                    ) { ok -> if (ok) navController.popBackStack() }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ROLE_ADMINISTRATION) {
            val viewModel: AdministrationViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> AdministrationViewModel(g, p) },
            )
            val staff by viewModel.staff.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()

            RoleAdministrationScreen(
                staff = staff,
                grantable = viewModel.grantable,
                refusal = refusal,
                submitting = submitting,
                onGrant = viewModel::grant,
                onRevoke = viewModel::revoke,
                onBack = { navController.popBackStack() },
            )
        }

        // ── Safeguards and privacy ────────────────────────────────────────────
        composable(Routes.SAFEGUARD_SETTINGS) {
            val viewModel: SafeguardViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> SafeguardViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            SafeguardSettingsScreen(
                state = state,
                onEdit = viewModel::edit,
                onApplyPreset = viewModel::applyPreset,
                onSave = { viewModel.save { } },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PRIVACY_CONTROLS) {
            val viewModel: SafeguardViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> SafeguardViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            PrivacyControlsScreen(
                state = state,
                onEdit = viewModel::edit,
                onSave = { viewModel.save { } },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Trusted contacts and the introduction workflow ────────────────────
        composable(Routes.TRUSTED_CONTACTS) {
            val viewModel: WaliViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> WaliViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            TrustedContactsScreen(
                state = state,
                currentUserId = principal?.userId ?: UserId(""),
                onSave = { contact -> viewModel.saveContact(contact) { } },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.WALI_SETTINGS) {
            val viewModel: WaliViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> WaliViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            WaliSettingsScreen(
                state = state,
                onSaveSettings = viewModel::saveSettings,
                onOpenIntroduction = { navController.navigate(Routes.introduction(it.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SUBMIT_INTRODUCTION) { entry ->
            val recipientId = UserId(entry.arguments?.getString("recipientId").orEmpty())
            val viewModel: SubmitIntroductionViewModel = viewModel(
                key = recipientId.value,
                factory = viewModelFactory(graph) { g, p ->
                    SubmitIntroductionViewModel(g, p, recipientId)
                },
            )
            val state by viewModel.state.collectAsState()

            SubmitIntroductionScreen(
                state = state,
                onUpdate = viewModel::update,
                onSubmit = viewModel::submit,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.INTRODUCTION_DETAIL) { entry ->
            val id = IntroductionId(entry.arguments?.getString("id").orEmpty())
            val viewModel: WaliViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> WaliViewModel(g, p) },
            )
            val request by produceState<org.fisabilillah.core.model.FormalIntroductionRequest?>(null, id) {
                value = viewModel.introduction(id)
            }
            // Only ever the redacted form. Guardian contact details do not exist at this layer.
            val guardian by produceState<org.fisabilillah.core.model.RedactedTrustedContact?>(null, request) {
                value = viewModel.guardianSummary(id)
            }
            val me = principal?.userId
            val viewerIsRecipient = me != null && request?.recipientId == me
            val viewerIsGuardian by produceState(false, request, me) {
                val contactId = request?.guardianContactId
                value = me != null && contactId != null &&
                    graph.core.trustedContacts.find(contactId)?.linkedUserId == me
            }

            IntroductionDetailScreen(
                request = request,
                guardian = guardian,
                viewerIsRecipient = viewerIsRecipient,
                viewerIsGuardian = viewerIsGuardian,
                onApprove = {
                    viewModel.decide(id, IntroductionDecisionAction.APPROVE_AND_FORWARD) { }
                },
                onDecline = { viewModel.decide(id, IntroductionDecisionAction.DECLINE) { } },
                onClosePermanently = {
                    viewModel.decide(id, IntroductionDecisionAction.BLOCK_PERMANENTLY) {
                        navController.popBackStack()
                    }
                },
                onOpenGuardianConversation = { message ->
                    viewModel.openGuardianConversation(
                        id = id,
                        message = message,
                        onOpened = { navController.navigate(Routes.conversation(it.value)) },
                        onRefused = { },
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Safety ────────────────────────────────────────────────────────────
        composable(Routes.SAFETY_CENTRE) {
            SafetyCentreScreen(
                onMyReports = { navController.navigate(Routes.MY_REPORTS) },
                onMyRestrictions = { navController.navigate(Routes.MY_RESTRICTIONS) },
                onGuidelines = { navController.navigate(Routes.COMMUNITY_GUIDELINES) },
                onPrivacyControls = { navController.navigate(Routes.PRIVACY_CONTROLS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MY_REPORTS) {
            MyReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MY_RESTRICTIONS) {
            val viewModel: MyModerationRecordViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> MyModerationRecordViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()

            MyRestrictionsScreen(
                records = state.data.orEmpty(),
                loading = state.loading,
                refusal = state.refusal,
                onAppeal = { caseId -> navController.navigate(Routes.submitAppeal(caseId.value)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SUBMIT_APPEAL) { entry ->
            val caseId = ModerationCaseId(entry.arguments?.getString("caseId").orEmpty())
            val viewModel: MyModerationRecordViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> MyModerationRecordViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()
            val submitting by viewModel.submitting.collectAsState()
            var error by remember { mutableStateOf<String?>(null) }

            val summary = state.data
                ?.firstOrNull { it.case?.id == caseId }
                ?.case
                ?.summary

            SubmitAppealScreen(
                caseSummary = summary,
                submitting = submitting,
                error = error,
                onSubmit = { statement ->
                    viewModel.appeal(caseId, statement) { message ->
                        error = message
                        if (message == null) navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.REPORT) { entry ->
            val targetType = entry.arguments?.getString("targetType").orEmpty()
            val targetId = entry.arguments?.getString("targetId").orEmpty()
            val target = reportTargetFor(targetType, targetId)

            val viewModel: ReportViewModel = viewModel(
                key = "$targetType-$targetId",
                factory = viewModelFactory(graph) { g, p -> ReportViewModel(g, p, target) },
            )
            val state by viewModel.state.collectAsState()

            ReportScreen(
                state = state,
                onSetCategory = viewModel::setCategory,
                onSetDescription = viewModel::setDescription,
                onSetIncludeEvidence = viewModel::setIncludeEvidence,
                onSubmit = viewModel::submit,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.NOTIFICATIONS) {
            val viewModel: NotificationsViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> NotificationsViewModel(g, p) },
            )
            val notifications by viewModel.state.collectAsState()

            NotificationsScreen(
                notifications = notifications,
                onMarkRead = viewModel::markRead,
                onOpen = { deepLink -> navigateToDeepLink(navController, deepLink) },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Moderation ────────────────────────────────────────────────────────
        composable(Routes.APPEAL_QUEUE) {
            val viewModel: AppealQueueViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> AppealQueueViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()
            val submitting by viewModel.submitting.collectAsState()
            var error by remember { mutableStateOf<String?>(null) }

            AppealQueueScreen(
                items = state.data.orEmpty(),
                loading = state.loading,
                refusal = error ?: state.refusal,
                submitting = submitting,
                onDecide = { appealId, decision, note ->
                    viewModel.decide(appealId, decision, note) { message -> error = message }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MODERATOR_DASHBOARD) {
            val viewModel: ModerationViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ModerationViewModel(g, p) },
            )
            val state by viewModel.state.collectAsState()
            val auditTrail by viewModel.auditTrail.collectAsState()

            ModeratorDashboardScreen(
                state = state,
                auditTrail = auditTrail,
                isSafetyAdmin = principal?.isSafetyAdmin == true,
                onOpenCase = { navController.navigate(Routes.moderationCase(it.value)) },
                onAppeals = { navController.navigate(Routes.APPEAL_QUEUE) },
                onTrustReview = { navController.navigate(Routes.TRUST_REVIEW) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MODERATION_CASE) { entry ->
            val id = ModerationCaseId(entry.arguments?.getString("id").orEmpty())
            val viewModel: ModerationViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ModerationViewModel(g, p) },
            )
            var refusal by remember { mutableStateOf<String?>(null) }

            val case by produceState<org.fisabilillah.core.model.ModerationCase?>(null, id) {
                value = graph.core.moderation.findCase(id)
            }
            val actions by produceState<List<org.fisabilillah.core.model.ModerationAction>>(
                emptyList(),
                id,
            ) {
                value = graph.core.moderation.actionsFor(id)
            }

            ModerationCaseScreen(
                case = case,
                actions = actions,
                canTakeSeniorActions = principal?.isSafetyAdmin == true,
                refusal = refusal,
                onAct = { type, rationale ->
                    viewModel.act(
                        TakeModerationActionUseCase.Command(
                            caseId = id,
                            type = type,
                            targetUserId = case?.subjectUserId,
                            rationale = rationale,
                        ),
                    ) { outcome -> refusal = outcome.refusalMessage() }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ADMIN_DASHBOARD) {
            val viewModel: ModerationViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> ModerationViewModel(g, p) },
            )
            val auditTrail by viewModel.auditTrail.collectAsState()

            AdminDashboardScreen(
                auditTrail = auditTrail,
                onBack = { navController.popBackStack() },
            )
        }

        // ── Static ────────────────────────────────────────────────────────────
        composable(Routes.TERMS) { TermsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMMUNITY_GUIDELINES) {
            CommunityGuidelinesScreen(onBack = { navController.popBackStack() })
        }
        // ── Giving ───────────────────────────────────────────────────────
        composable(Routes.CAMPAIGNS) {
            val viewModel: GivingViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> GivingViewModel(g, p) },
            )
            val campaigns by viewModel.campaigns.collectAsState()
            val loading by viewModel.loading.collectAsState()

            CampaignsScreen(
                campaigns = campaigns,
                loading = loading,
                onOpen = { navController.navigate(Routes.donate(it.value)) },
                onMyGiving = { navController.navigate(Routes.MY_GIVING) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DONATE) { entry ->
            val id = CampaignId(entry.arguments?.getString("id").orEmpty())
            val viewModel: GivingViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> GivingViewModel(g, p) },
            )
            val errors by viewModel.errors.collectAsState()
            val refusal by viewModel.refusal.collectAsState()
            val submitting by viewModel.submitting.collectAsState()
            val checkoutUrl by viewModel.checkoutUrl.collectAsState()

            val campaign by produceState<org.fisabilillah.core.model.Campaign?>(null, id) {
                value = viewModel.campaign(id)
            }
            val organizationName by produceState<String?>(null, campaign) {
                value = campaign?.let { viewModel.organizationName(it) }
            }

            // The payment page opens in the browser rather than a web view. A donor can
            // then see the address bar and the padlock and satisfy themselves about who
            // they are paying, which is exactly what a web view takes away and exactly
            // what a fake giving flow imitates.
            val context = LocalContext.current
            LaunchedEffect(checkoutUrl) {
                val url = checkoutUrl ?: return@LaunchedEffect
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                viewModel.checkoutOpened()
            }

            DonateScreen(
                campaign = campaign,
                organizationName = organizationName,
                errors = errors,
                refusal = refusal,
                submitting = submitting,
                onGive = { amount, anonymous -> viewModel.give(id, amount, anonymous) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MY_GIVING) {
            val viewModel: GivingViewModel = viewModel(
                factory = viewModelFactory(graph) { g, p -> GivingViewModel(g, p) },
            )
            val mine by viewModel.mine.collectAsState()
            val loading by viewModel.loading.collectAsState()

            MyGivingScreen(
                lines = mine,
                loading = loading,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.GIVING_COMPLIANCE) {
            GivingComplianceScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** The refusal text an outcome carries, if it carries one. */
private fun Outcome<*>.refusalMessage(): String? = when (this) {
    is Outcome.Refused -> message
    is Outcome.Invalid -> errors.firstOrNull()?.message
    is Outcome.NotFound -> "We could not find $what."
    is Outcome.Success -> null
}

private fun reportTargetFor(targetType: String, targetId: String): ReportTarget =
    when (targetType) {
        "user" -> ReportTarget.User(UserId(targetId))
        "conversation" -> ReportTarget.ConversationTarget(ConversationId(targetId))
        "listing" -> ReportTarget.Listing(ListingId(targetId))
        "request" -> ReportTarget.Request(RequestId(targetId))
        "campaign" -> ReportTarget.Campaign(CampaignId(targetId))
        "organisation" -> ReportTarget.Organization(OrganizationId(targetId))
        "introduction" -> ReportTarget.Introduction(IntroductionId(targetId))
        else -> ReportTarget.User(UserId(targetId))
    }

/** Resolves a `fisabilillah://…` link from a notification to a route. */
private fun navigateToDeepLink(navController: NavHostController, deepLink: String) {
    val path = deepLink.removePrefix("fisabilillah://")
    val parts = path.split("/")
    if (parts.size < 2) return
    val id = parts[1]
    val route = when (parts[0]) {
        "conversation" -> Routes.conversation(id)
        "opportunity" -> Routes.opportunity(id)
        "learning" -> Routes.learning(id)
        "request" -> Routes.request(id)
        "introduction" -> Routes.introduction(id)
        else -> return
    }
    navController.navigate(route)
}

/**
 * Completes onboarding.
 *
 * Note what is *not* passed through: no verification level, no attestations, and no roles
 * beyond what the member is entitled to grant themselves. The use case sanitises all three
 * again regardless, and the database rejects them a third time — but sending them from here
 * in the first place would be the kind of convenience that turns into a vulnerability.
 */
private suspend fun completeOnboarding(graph: AppGraph, state: OnboardingState): String? {
    val principal = graph.session.principal.value
        ?: return "You need to be signed in to finish setting up."

    val gender: Gender = state.gender
        ?: return "Please choose how you would like to be addressed."
    if (state.displayName.isBlank()) return "Please choose a display name."
    if (state.birthYear <= 0) return "Please give your year of birth."

    val profile = Profile(
        id = principal.userId,
        displayName = state.displayName.trim(),
        realName = state.realName.trim().ifBlank { null },
        gender = gender,
        dateOfBirthYear = state.birthYear,
        place = Place(
            approximate = ApproximateLocation(
                label = state.city.trim(),
                city = state.city.trim(),
                countryCode = "GB",
            ),
        ),
        languages = Language.common.filter { it.tag in state.languages },
        areasWillingToHelp = state.areasWillingToHelp,
        areasSeekingHelp = state.areasSeekingHelp,
        availability = Availability(timeZoneId = "Europe/London"),
    )

    // The database first. That row is what every row-level security policy will check,
    // and creating the local copy without it would leave a member who looks set up on
    // this phone and does not exist to the server. register_member decides the role and
    // the verification level itself; nothing privileged is sent from here.
    val serverError = graph.session.completeServerRegistration(
        MemberRegistration(
            displayName = state.displayName.trim(),
            gender = gender,
            acceptedCovenant = state.acceptedConsents.isNotEmpty(),
            timezone = "Europe/London",
            yearOfBirth = state.birthYear,
            city = state.city.trim().ifBlank { null },
            countryCode = "GB",
        ),
    )
    if (serverError != null) return serverError

    val outcome = graph.core.completeOnboarding(
        principal,
        CompleteOnboardingUseCase.Command(
            profile = profile,
            preset = state.safeguardPreset,
            acceptedConsents = state.acceptedConsents,
            documentVersion = CONSENT_DOCUMENT_VERSION,
            declaredAdult = true,
        ),
    )

    return when (outcome) {
        is Outcome.Success -> {
            graph.session.refresh()
            null
        }
        is Outcome.Refused -> outcome.message
        is Outcome.Invalid -> outcome.errors.firstOrNull()?.message
        is Outcome.NotFound -> "We could not find ${outcome.what}."
    }
}

private suspend fun createOpportunity(
    graph: AppGraph,
    draft: org.fisabilillah.app.ui.screens.serve.CreateListingDraft,
): Outcome<VolunteerOpportunity> {
    val principal = graph.session.principal.value
        ?: return Outcome.refused("You need to be signed in to publish an opportunity.")
    val now = graph.core.clock.now()

    return graph.core.createOpportunity(
        principal,
        CreateOpportunityUseCase.Command(
            title = draft.title,
            summary = draft.summary,
            category = draft.category,
            beneficiaryType = draft.beneficiaryType,
            city = draft.city,
            countryCode = "GB",
            format = draft.format,
            volunteersNeeded = draft.volunteersNeeded,
            completionCriteria = draft.completionCriteria,
            // A date and time picker is a known gap in this release: an organiser cannot
            // yet choose when the work happens, so it is provisionally set a week out.
            // See the status section of the README.
            startsAt = now + DefaultOpportunityTiming.LEAD_TIME,
            endsAt = now + DefaultOpportunityTiming.LEAD_TIME + DefaultOpportunityTiming.DURATION,
            physicalRequirements = draft.physicalRequirements,
            safetyNotes = draft.safetyNotes,
            backgroundCheckRequired = draft.backgroundCheckRequired,
            childSafeguardingRequired = draft.childSafeguardingRequired,
            genderArrangement = draft.genderArrangement,
            expensesReimbursed = draft.expensesReimbursed,
        ),
    )
}

private suspend fun createRequest(
    graph: AppGraph,
    draft: org.fisabilillah.app.ui.screens.requests.CreateRequestDraft,
): Outcome<ServiceRequest> {
    val principal = graph.session.principal.value
        ?: return Outcome.refused("You need to be signed in to ask for help.")

    return graph.core.createServiceRequest(
        principal,
        CreateServiceRequestUseCase.Command(
            title = draft.title,
            description = draft.description,
            category = draft.category,
            urgency = draft.urgency,
            visibility = draft.visibility,
            city = draft.city,
            countryCode = "GB",
            exactAddress = draft.exactAddress,
            peopleAffected = draft.peopleAffected,
            showSupportTotals = draft.showSupportTotals,
        ),
    )
}

/** Bumped whenever the terms, privacy policy, or guidelines change materially. */
private const val CONSENT_DOCUMENT_VERSION = "2026-03-01"
