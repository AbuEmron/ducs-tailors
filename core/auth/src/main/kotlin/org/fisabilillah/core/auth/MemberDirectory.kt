package org.fisabilillah.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.fisabilillah.core.model.Gender
import org.fisabilillah.core.model.UserId

/**
 * Who the signed-in account is, according to the database.
 *
 * Separate from [AuthGateway] on purpose. A session proves *who is calling*; it says
 * nothing about what they may do here. Roles, verification level and whether onboarding is
 * finished are all read back over an authenticated request, because they can change
 * between one screen and the next — a moderator suspended this morning must stop being a
 * moderator now, not at the next token refresh.
 */
public interface MemberDirectory {

    /** The `current_member()` RPC. Returns an identity with `hasProfile = false` before onboarding. */
    public suspend fun currentMember(accessToken: String): AuthResult<MemberIdentity>

    /** The `register_member()` RPC: creates the profile, settings, safeguards and member role. */
    public suspend fun register(
        accessToken: String,
        registration: MemberRegistration,
    ): AuthResult<MemberIdentity>
}

public class SupabaseMemberDirectory(
    private val config: SupabaseConfig,
    private val transport: HttpTransport = JdkHttpTransport(),
) : MemberDirectory {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    override suspend fun currentMember(accessToken: String): AuthResult<MemberIdentity> {
        val response = rpc("current_member", JsonObject(emptyMap()), accessToken)
            ?: return AuthResult.Failure(AuthFailure.NETWORK, "current_member: transport failed")

        if (!response.isSuccess) return failure(response, "current_member")

        // current_member() returns a set, and it is empty when the token names an auth
        // account the database has never seen -- a user deleted server-side while the
        // phone still held a token, for instance. That is a dead session, not an error
        // to retry.
        val rows = decodeRows(response.body)
            ?: return AuthResult.Failure(AuthFailure.SERVER, "current_member: unreadable response")
        val row = rows.firstOrNull()
            ?: return AuthResult.Failure(AuthFailure.SESSION_EXPIRED, "current_member: no row for this token")

        return AuthResult.Success(row.toIdentity())
    }

    override suspend fun register(
        accessToken: String,
        registration: MemberRegistration,
    ): AuthResult<MemberIdentity> {
        val response = rpc(
            function = "register_member",
            body = buildJsonObject {
                put("p_display_name", registration.displayName.trim())
                put("p_gender", registration.gender.wireName)
                put("p_locale", registration.locale)
                put("p_timezone", registration.timezone)
                put("p_accept_covenant", registration.acceptedCovenant)
                registration.yearOfBirth?.let { put("p_year_of_birth", it) }
                registration.city?.let { put("p_city", it) }
                registration.countryCode?.let { put("p_country_code", it.uppercase()) }
                // No p_contact_email: the function reads the address off auth.users, so
                // nobody can register under an address they have not proved they hold.
            },
            accessToken = accessToken,
        ) ?: return AuthResult.Failure(AuthFailure.NETWORK, "register_member: transport failed")

        if (!response.isSuccess) {
            val error = decode<ErrorResponse>(response.body)
            val text = listOfNotNull(error?.message, error?.bestDescription).joinToString(" ").lowercase()
            if (text.contains("already has a profile")) {
                return AuthResult.Failure(AuthFailure.ALREADY_REGISTERED, error?.bestDescription)
            }
            return failure(response, "register_member")
        }

        // The RPC returns the new account's id; the identity itself is read back rather
        // than assumed, so what the app shows is what the database actually stored --
        // including the role it granted and the verification level it did not.
        return currentMember(accessToken)
    }

    private suspend fun rpc(
        function: String,
        body: JsonObject,
        accessToken: String,
    ): HttpResponse? = try {
        transport.send(
            HttpRequest(
                method = "POST",
                url = "${config.restUrl}/rpc/$function",
                headers = mapOf(
                    "apikey" to config.publishableKey,
                    "Authorization" to "Bearer $accessToken",
                    "Content-Type" to "application/json",
                    // Ask PostgREST for the whole set rather than a single object, so a
                    // function that legitimately returns no rows is an empty array and
                    // not a 406.
                    "Accept" to "application/json",
                ),
                body = json.encodeToString(JsonObject.serializer(), body),
            ),
        )
    } catch (e: HttpTransportException) {
        null
    }

    private fun failure(response: HttpResponse, what: String): AuthResult.Failure {
        val error = decode<ErrorResponse>(response.body)
        val reason = when {
            response.status == 401 || response.status == 403 -> AuthFailure.SESSION_EXPIRED
            response.status == 429 -> AuthFailure.RATE_LIMITED
            else -> AuthFailure.SERVER
        }
        return AuthResult.Failure(reason, "$what: ${response.status} ${error?.bestDescription.orEmpty()}".trim())
    }

    private fun decodeRows(body: String): List<CurrentMemberRow>? = try {
        if (body.isBlank()) emptyList() else json.decodeFromString<List<CurrentMemberRow>>(body)
    } catch (e: Exception) {
        // A function declared `returns table` normally yields an array, but a single
        // object is worth accepting rather than failing a sign-in over a shape.
        try {
            listOf(json.decodeFromString<CurrentMemberRow>(body))
        } catch (e2: Exception) {
            null
        }
    }

    private inline fun <reified T> decode(body: String): T? = try {
        if (body.isBlank()) null else json.decodeFromString<T>(body)
    } catch (e: Exception) {
        null
    }
}

internal fun CurrentMemberRow.toIdentity(): MemberIdentity = MemberIdentity(
    userId = UserId(user_id.orEmpty()),
    hasProfile = has_profile,
    displayName = display_name,
    email = contact_email,
    gender = genderFromWire(gender),
    locale = locale,
    timezone = timezone,
    verificationLevel = VerificationLevel.fromWire(verification_level),
    roles = RoleKeys.toRoles(role_keys),
    covenantAccepted = covenant_accepted,
    emailConfirmed = email_confirmed,
)

/** The `public.gender` enum has exactly two values, and an unknown string is not one of them. */
internal fun genderFromWire(value: String?): Gender? = when (value) {
    "male" -> Gender.MALE
    "female" -> Gender.FEMALE
    else -> null
}

internal val Gender.wireName: String get() = if (this == Gender.MALE) "male" else "female"
