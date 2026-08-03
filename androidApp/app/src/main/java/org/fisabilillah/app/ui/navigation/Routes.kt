package org.fisabilillah.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the application.
 *
 * String routes rather than type-safe serializable ones, because the argument shapes here
 * are all single identifiers and the plain form keeps deep links (`fisabilillah://…`,
 * emitted by the notification use cases) trivially readable.
 */
internal object Routes {
    // Unauthenticated
    const val LANDING = "landing"
    const val MISSION = "mission"
    const val SIGN_IN = "sign-in"
    const val SIGN_UP = "sign-up"
    const val ACCOUNT_RECOVERY = "account-recovery"

    // Onboarding
    const val ONBOARDING_PROFILE = "onboarding/profile"
    const val ONBOARDING_SKILLS = "onboarding/skills"
    const val ONBOARDING_SAFEGUARDS = "onboarding/safeguards"
    const val ONBOARDING_CONSENT = "onboarding/consent"

    // Primary navigation
    const val HOME = "home"
    const val SERVE = "serve"
    const val LEARN = "learn"
    const val PROJECTS = "projects"
    const val REQUESTS = "requests"
    const val MESSAGES = "messages"
    const val COMMUNITY = "community"
    const val PROFILE = "profile"

    // Details
    const val OPPORTUNITY_DETAIL = "opportunity/{id}"
    const val LEARNING_DETAIL = "learning/{id}"
    const val REQUEST_DETAIL = "request/{id}"
    const val PROJECT_DETAIL = "project/{id}"
    const val COMMUNITY_DETAIL = "community/{id}"
    const val ORGANIZATION_DETAIL = "organization/{id}"
    const val MEMBER_PROFILE = "member/{id}"
    const val CONVERSATION = "conversation/{id}"
    const val INTRODUCTION_DETAIL = "introduction/{id}"

    // Creation and composition
    const val CREATE_LISTING = "create/listing"
    const val CREATE_REQUEST = "create/request"
    const val CREATE_CLASS = "create/class"
    const val CREATE_PROJECT = "create/project"
    const val CREATE_COMMUNITY = "create/community"
    const val COMPOSE_CONVERSATION = "compose/{recipientId}"
    const val SUBMIT_INTRODUCTION = "introduction/submit/{recipientId}"
    const val REPORT = "report/{targetType}/{targetId}"

    // Account and safety
    const val EDIT_PROFILE = "settings/profile"
    const val PRIVACY_CONTROLS = "settings/privacy"
    const val SAFEGUARD_SETTINGS = "settings/safeguards"
    const val TRUSTED_CONTACTS = "settings/trusted-contacts"
    const val WALI_SETTINGS = "settings/wali"
    const val NOTIFICATIONS = "notifications"
    const val SAFETY_CENTRE = "safety"
    const val MY_REPORTS = "safety/reports"

    /** What has been done to my account, and the form to argue with it. */
    const val MY_RESTRICTIONS = "safety/restrictions"
    const val SUBMIT_APPEAL = "safety/appeal/{caseId}"
    const val SERVICE_HISTORY = "profile/history"
    const val ACCOUNT_DATA = "settings/account-data"
    const val DEVICE_SESSIONS = "settings/devices"

    // Trust
    const val MY_VERIFICATION = "trust/verification"
    const val REQUEST_VERIFICATION = "trust/verification/request"
    const val MY_QUALIFICATIONS = "trust/qualifications"
    const val TRUST_REVIEW = "moderation/trust"

    // Giving
    const val CAMPAIGNS = "giving"
    const val DONATE = "giving/campaign/{id}"
    const val MY_GIVING = "giving/mine"

    // Commitments
    const val MY_COMMITMENTS = "commitments"
    const val ENDORSE = "commitments/endorse/{id}"

    // Staff
    const val MODERATOR_DASHBOARD = "moderation"
    const val MODERATION_CASE = "moderation/case/{id}"
    // Appeals are decided on the queue screen itself. A separate per-appeal route was
    // declared and never wired; a moderator reading one appeal in isolation loses the
    // thing that makes an appeal queue reviewable — the other appeals against the same
    // decision, sitting next to it.
    const val APPEAL_QUEUE = "moderation/appeals"
    const val ADMIN_DASHBOARD = "admin"
    const val ROLE_ADMINISTRATION = "admin/roles"

    // Static
    const val TERMS = "legal/terms"
    const val PRIVACY_POLICY = "legal/privacy"
    const val COMMUNITY_GUIDELINES = "legal/guidelines"
    const val GIVING_COMPLIANCE = "legal/giving"

    fun opportunity(id: String): String = "opportunity/$id"
    fun learning(id: String): String = "learning/$id"
    fun request(id: String): String = "request/$id"
    fun project(id: String): String = "project/$id"
    fun community(id: String): String = "community/$id"
    fun organization(id: String): String = "organization/$id"
    fun member(id: String): String = "member/$id"
    fun conversation(id: String): String = "conversation/$id"
    fun introduction(id: String): String = "introduction/$id"
    fun compose(recipientId: String): String = "compose/$recipientId"
    fun submitIntroduction(recipientId: String): String = "introduction/submit/$recipientId"
    fun report(targetType: String, targetId: String): String = "report/$targetType/$targetId"
    fun moderationCase(id: String): String = "moderation/case/$id"
    fun submitAppeal(caseId: String): String = "safety/appeal/$caseId"
    fun endorse(commitmentId: String): String = "commitments/endorse/$commitmentId"
    fun donate(campaignId: String): String = "giving/campaign/$campaignId"
}

/**
 * The bottom bar.
 *
 * Six destinations, all of them a thing to do rather than a thing to look at. There is no
 * "discover" or "explore" tab: browsing people is not an activity this product wants to
 * make easy, and a tab bar is the strongest statement a mobile app makes about what it is for.
 */
internal enum class PrimaryDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
) {
    HOME(Routes.HOME, "Home", Icons.Outlined.Home, "Home"),
    SERVE(Routes.SERVE, "Serve", Icons.Outlined.VolunteerActivism, "Volunteering opportunities"),
    LEARN(Routes.LEARN, "Learn", Icons.AutoMirrored.Outlined.MenuBook, "Classes and study circles"),
    REQUESTS(Routes.REQUESTS, "Requests", Icons.Outlined.Diversity3, "Requests for help"),
    MESSAGES(Routes.MESSAGES, "Messages", Icons.Outlined.Forum, "Your conversations"),
    PROFILE(Routes.PROFILE, "You", Icons.Outlined.Person, "Your profile and settings"),
}
