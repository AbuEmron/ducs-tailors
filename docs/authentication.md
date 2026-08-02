# Authentication

How a person gets an account on this platform, how the app proves who they are, and what
this deliberately refuses to tell anyone.

Before this, `SessionManager` picked a seeded profile out of a list. Every one of the 162
row-level security policies in the database is written against `auth.uid()`, and nothing
produced one. That is now closed.

---

## 1. The pieces

| Piece | Where | What it does |
| --- | --- | --- |
| Supabase Auth (GoTrue) | project `mqrooikosbhwjcdssatf`, region `eu-west-2` | Holds the password, issues and refreshes tokens, sends the confirmation and reset messages |
| `public.register_member()` | `0016_auth_registration.sql` | The only supported way a profile comes into existence |
| `public.current_member()` | `0016_auth_registration.sql` | One round trip for "who am I and what may I do" |
| `core:auth` | `core/auth/` | Pure Kotlin. `AuthGateway`, `MemberDirectory`, `MemberSession` |
| `KeystoreSessionStore` | `androidApp/.../session/` | The refresh token at rest, encrypted under a hardware-backed key |
| `SessionManager` | `androidApp/.../di/AppGraph.kt` | Turns a verified identity into a `Principal` |

`core:auth` depends on `core:model`, coroutines, and nothing else. It talks HTTP through
`HttpURLConnection` and parses with `kotlinx.serialization`, so it adds no dependency to
either build and its whole surface is testable on a plain JVM with a fake transport.

---

## 2. The journey

```
Create account ──► GoTrue signup ──► confirmation email ──► sign in
                                                              │
                                                              ▼
                                                   register_member()
                                                              │
                                            profile + settings + safeguards
                                                   + the member role
                                                              │
                                                              ▼
                                                    current_member()
                                                              │
                                                    Principal(id, roles)
```

**Sign-up** takes an age declaration, a purpose declaration, an address and a password. The
declarations gate the credential fields — the order of the screen matches the order of the
decision. GoTrue creates the auth user; with email confirmation on there is no session yet,
and the screen says so rather than pretending.

**Confirming the address** does not write a trust level directly. It inserts a row in
`user_verifications` with `method = 'email'`, and the existing trigger from `0014` syncs the
profile up to `basic`. Verification stays one mechanism with one audit trail.

**Registration** is `register_member()`, a `SECURITY DEFINER` function. It reads the account
id and the email address off `auth.users` rather than from its arguments, so nobody can
register under an address they have not proved they hold. It grants `member` and no other
role. It sets the verification level to `unverified` and leaves it there. There is no
parameter in which a caller could assert a role, a verification level, or an account id —
not because they would be ignored, but because they do not exist.

**Every start-up** restores the session from the encrypted refresh token, then asks
`current_member()` again. Roles come from that answer, never from the JWT body: a token is
signed, but the claims inside it are a snapshot from issue time, and a moderator suspended
this morning has to stop being a moderator now rather than at the next refresh.

---

## 3. What this refuses to say

The membership list of this platform is itself sensitive. A woman using it to look for a
formal introduction has not necessarily told anyone, and "does this address have an account
here" is a question a stranger with a contact list should not get an answer to.

GoTrue is more candid than that. It will report `user_already_exists` on sign-up, and
`User not found` on password recovery. Those answers stop at `core:auth`:

| Situation | What GoTrue says | What the app says |
| --- | --- | --- |
| Sign up, new address | 200 | "Check your inbox…" |
| Sign up, address already registered | 422 `user_already_exists` | "Check your inbox…" — identical |
| Sign in, wrong password | 400 `invalid_grant` | "Those details do not match an account." |
| Sign in, no such address | 400 `invalid_grant` | The same sentence |
| Recovery, address exists | 200 | "If that address has an account here, a link is on its way." |
| Recovery, no such address | 4xx | The same sentence |

Someone who genuinely already has an account is told to check their inbox, finds no new
message, and their existing password still works. That is the correct outcome and it reveals
nothing to anybody else.

The one thing that *is* named plainly is an unconfirmed address on sign-in — because that
answer is only reachable by someone who has already produced the right password, so it gives
an attacker nothing, and withholding it would leave a real member stuck with no idea why.

`AuthResult.Failure` carries a `detail` alongside the user-facing `reason`, the same
user-facing/audit split used by `ContactPolicy` throughout the safety layer.

---

## 4. What the client is not trusted with

- **Roles.** Read from `user_roles` through `current_member()`. A role key this build has
  never heard of is dropped rather than guessed at.
- **Verification level.** Same route. An unrecognised level falls to `unverified` — always
  downward. Guessing upward would hand a stranger a trust marker nobody granted.
- **Gender.** Set once at registration. `trg_profiles_guard_identity` refuses a later change
  from anyone but a moderator, because contact structure, gendered spaces and the whole
  introduction workflow key off it, and a field a member could flip between requests is a way
  into a sisters-only circle.
- **The account id.** Always `auth.uid()`.

### An escalation path this work closed

`0014` guarded `profiles.verification_level` on `BEFORE UPDATE` only. That was survivable
while nobody could sign up — the only `INSERT` path was the seed, running as superuser. With
real registration it becomes live: the RLS policy `profiles_insert_self` lets a member insert
the row whose id matches their own `auth.uid()`, and an `INSERT` was never inspected. A
member could have arrived already holding `scholar_verified`.

`0016` extends the guard to `INSERT`. There is a test for it:

> `a member cannot insert their own profile already verified`

### And one the advisor caught

Supabase ships default privileges granting `EXECUTE` on every new function in `public` to
`anon` and `authenticated`. `revoke ... from public` in `0016` did not touch them, because
they are grants to those roles by name. `register_member` and `current_member` were therefore
callable without signing in. `0017` revokes from `anon` explicitly.

The same migration closes `audit_logs_insert_any`, which let any signed-in member append an
audit row with any `actor_id` and any action text — unreachable while nobody could
authenticate, live the moment they could. A forgeable audit log is worse than none: it is
what a moderator reads when deciding whether someone did what they are accused of, and it
would have accepted a row naming an innocent member as the actor. The only way in is now
`app.write_audit()`, which stamps the actor from `app.current_user_id()`.

---

## 5. Tokens on the device

- The **access token** is never written to disk. It is short-lived and always obtainable
  again from the refresh token; storing it widens what a stolen phone gives away for nothing.
- The **refresh token** is encrypted with AES-256-GCM under a key generated inside the
  Android keystore, which never leaves the device. Preferences alone are readable by anything
  that gets root or an ADB backup on an unlocked handset — not hypothetical on shared or
  second-hand phones.
- The key does **not** require user authentication to use. Demanding a fingerprint on every
  cold start would be the wrong trade for an app people open to check whether a lift was
  arranged. The ciphertext is still worthless off the device it was written on.
- `MemberSession.accessToken()` refreshes when the token is within a minute of expiry, under
  a mutex so that several screens waking at once produce one refresh rather than four
  rejected ones.
- **Signing out** clears the device first and revokes server-side afterwards, and does the
  first regardless of whether the second succeeds. A sign-out that fails because the train
  went into a tunnel must still sign the person out of the phone in their hand.

---

## 6. Configuration

`androidApp/app/build.gradle.kts` puts two values into `BuildConfig`:

| Field | Default | Override |
| --- | --- | --- |
| `SUPABASE_URL` | `https://mqrooikosbhwjcdssatf.supabase.co` | `-PsupabaseUrl=` or `SUPABASE_URL` |
| `SUPABASE_PUBLISHABLE_KEY` | `sb_publishable_…` | `-PsupabaseKey=` or `SUPABASE_PUBLISHABLE_KEY` |

Both are safe to ship. The publishable key grants nothing on its own — every table is behind
row-level security keyed on `auth.uid()`, and the two RPCs are revoked from `anon`.

**The service-role key is not here and must never be.** It bypasses every policy in the
database, and a key inside an APK is a key anybody can read out of it. `SupabaseConfig`
refuses one at construction:

```kotlin
require(!publishableKey.contains("service_role") && !publishableKey.startsWith("sb_secret_")) {
    "That looks like a service-role key. It must never be given to a client."
}
```

---

## 7. Tests

**Kotlin** — 26 assertions in `core/auth/src/test/`, all against a fake transport, no network:

- sign-up looks identical for a new and an existing address
- a wrong password and an unknown address give one message
- recovery is silent about existence but still reports a rate limit
- a rejected refresh token ends the session rather than retrying
- an expiring token refreshes once, not per caller
- signing out clears the device even when the server call fails
- registration sends no role, no verification level, no account id, no email address
- unknown role keys are dropped; unknown verification levels fall to unverified
- a service-role key cannot be put into `SupabaseConfig` at all

**SQL** — sections 16 to 19 of `backend/supabase/tests/rls_tests.sql`, 20 assertions:

- a member cannot insert their own profile already verified
- `register_member` returns `auth.uid()`, never a caller-supplied id
- it grants the member role and nothing else
- it refuses to run twice
- gender cannot be changed afterwards
- confirming the address lifts the profile to `basic`, recorded as a verification row with no
  human reviewer claimed, and a second confirmation does not duplicate it
- the machine-confirmed exemption does not let a member write a scholar level
- `anon` can call neither RPC
- a member cannot forge an audit row, naming a moderator or themselves

120 SQL assertions and 216 Kotlin tests pass in total.

---

## 8. What is still missing

- **The content layer is still the in-memory fixture.** Authentication is real; opportunities,
  requests, conversations and communities are not yet read from Supabase. A signed-in member
  gets a profile in the local fixture so the app works, and it does not survive a restart.
  Closing this means Supabase-backed implementations of the repository interfaces in
  `core/domain/.../Repositories.kt` — no use case, view model or screen changes when that
  happens.
- **No OAuth or magic-link sign-in.** Email and password only.
- **No device-session list**, so a member cannot see or revoke other devices. `DeviceSession`
  exists as a model with no path that reaches it.
- **No re-authentication before sensitive actions.** Changing an address or deleting an
  account should ask for the password again.
- **Verification above `basic` has no in-app path.** The identity-verification gap in
  `implementation-matrix.md` is unchanged by this work: every safeguard requiring
  `IDENTITY_VERIFIED`, including the whole introduction feature, remains unreachable for a
  real member until a reviewer flow exists.
- **Two Supabase advisor warnings remain open**, both pre-existing: `citext` and `pg_trgm` are
  installed in `public`. Moving them means rewriting the column types that reference them and
  is not worth doing incidentally.
