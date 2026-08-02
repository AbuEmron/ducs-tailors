# Local setup

Three parts, three sets of prerequisites. You do not need all three to work on any one of
them.

| Part | Needs | Time to first green |
| --- | --- | --- |
| Shared core (`core/`) | A JDK, 17 or newer | About a minute |
| Database (`backend/`) | PostgreSQL 16 and `psql` | A few minutes |
| Android client (`androidApp/`) | Android Studio, the Android SDK, network access to `dl.google.com` | Longer, and expect to fix compile errors — see below |

---

## 1. The shared core

This is where every safety decision lives, and it is the build to run before pushing
anything.

```console
$ git clone <repository>
$ cd ducs-tailors
$ ./gradlew build test
```

The Gradle wrapper pins 8.14.3 and downloads it on first run. The build declares
`mavenCentral()` and nothing else — there is no Android SDK requirement, no emulator, and no
dependency on Google's Maven repository.

Expected output ends with `BUILD SUCCESSFUL`. To see the test names:

```console
$ ./gradlew test --info | grep -E 'PASSED|FAILED'
```

HTML reports are written to `core/policy/build/reports/tests/test/index.html` and
`core/data/build/reports/tests/test/index.html`.

### Working on a single module

```console
$ ./gradlew :core:policy:test          # the decision layer, fast
$ ./gradlew :core:data:test            # end-to-end through the real use cases
$ ./gradlew :core:model:compileKotlin  # type changes only
```

### Opening it in an IDE

Open the **repository root** in IntelliJ IDEA (Community Edition is enough). It is a plain
Kotlin/JVM project; import the Gradle build when prompted. Android Studio will also open it,
but it will not offer you anything Android-related, which is correct and expected.

---

## 2. The database

Requires PostgreSQL 16 on the local machine. On Debian or Ubuntu:

```console
$ sudo apt-get install -y postgresql-16 postgresql-client-16
```

Then:

```console
$ backend/supabase/run_local_tests.sh
```

The script starts the cluster if it is down, drops and recreates a throwaway database
(`fisabilillah_test` by default), applies `tests/00_bootstrap.sql`, then every migration in
filename order, then the seed, then the assertion suite. It exits non-zero on any migration,
seed or assertion failure.

Expected tail:

```
PASS: schema summary -- 58 tables, 162 policies
---------------------------------------------------------------
RESULT: PASS  (100 assertions passed)
```

Useful overrides:

| Variable | Effect |
| --- | --- |
| `KEEP_DB=1` | Leave the database in place for inspection afterwards |
| `PGDATABASE_TEST=<name>` | Use a different throwaway database name |
| `PG_SUPERUSER=<role>` | Your database superuser is not `postgres` |
| `PG_BIN=<path>` | `pg_isready` is not at `/usr/lib/postgresql/16/bin` |

`tests/00_bootstrap.sql` recreates, on a plain PostgreSQL cluster, what Supabase already
provides: the `auth` schema, `auth.users`, `auth.uid()`, `auth.role()` and the `anon` /
`authenticated` / `service_role` roles. **Never apply it to a Supabase project.** The
migrations themselves contain no part of it, which is what makes them portable.

Details, including the three policy-authoring mistakes that are easy to make and hard to
notice, are in [`backend/README.md`](../backend/README.md) and
[`backend/docs/rls-model.md`](../backend/docs/rls-model.md).

---

## 3. The Android client

### Open `androidApp/`, not the repository root

This is the single most common way to lose an afternoon. `androidApp/` is a separate Gradle
build with its own wrapper, its own `settings.gradle.kts`, and its own repositories block.
The repository root is a plain Kotlin/JVM build; Android Studio will open it happily and
then offer you no run configuration, no emulator target, and no Compose preview.

```console
$ cd androidApp
$ ./gradlew assembleDebug
```

Or, in Android Studio: **File → Open →** select the `androidApp` directory.

The build includes the repository root as a composite build and substitutes the
`org.fisabilillah:core-*` coordinates for the sibling projects, so a change in `core/` is
picked up immediately with nothing published. You will see `:core:model`, `:core:policy`,
`:core:domain` and `:core:data` appear in the Gradle task list alongside `:app`.

### Requirements

- Android Studio, a version recent enough for AGP 8.7.3 and Kotlin 2.0.21
- `compileSdk` 35, `minSdk` 26, `targetSdk` 35
- JDK 17 for the toolchain (the CI job uses 21; either works)
- Network access to `dl.google.com` for AGP, AndroidX and Compose artifacts

### Expect the first build to fail

The Compose UI in this repository was authored in an environment where `dl.google.com` is
blocked by egress policy. The Android SDK, AGP, and the AndroidX and Compose artifacts could
not be downloaded, so **the Android module has never been compiled — not once.** The shared
core and the database layer were both fully built and tested; the UI was not.

Anyone picking this up should plan for a session of fixing compile errors before anything
renders. What to expect, in rough order of likelihood:

1. **Missing or wrong imports** in the Compose sources, particularly for Material 3 and
   `material-icons-extended` symbols.
2. **Signature mismatches between a screen and its view model** — a screen calling a method
   that was renamed, or passing a parameter the view model does not take.
3. **Compose API drift** against the pinned BOM (`2024.12.01`): experimental annotations,
   moved `HorizontalDivider`, changed `Scaffold` padding semantics.
4. **Navigation wiring.** `MainActivity` calls `FiSabilillahNavHost` from
   `org.fisabilillah.app.ui.navigation`. Confirm it exists and that every route in
   `Routes.kt` has a composable behind it; some screen areas may still be placeholders.

None of this touches the logic those screens call, which is already tested. Treat it as
mechanical work and do not be tempted to "fix" a compile error by moving a decision out of
`:core:policy` and into a screen — see [`CONTRIBUTING.md`](../CONTRIBUTING.md).

### Signing in

Authentication is a development stand-in. The sign-in screen lists the seeded accounts from
`SessionManager.availableAccounts()`; choosing one sets the `Principal`. Useful accounts
from `SeedData`:

| Account | Why it is interesting |
| --- | --- |
| `user-abdullah` | An ordinary member, identity verified but not background checked |
| `user-khadija` | Accepts contact from sisters only — use to see the cross-gender refusal |
| `user-aminah` | On the family-and-wali-guided preset, with a linked wali (`user-zaynab`) |
| `user-yusuf` | Background checked, organiser of the youth programme |
| `user-ibrahim` | The only verified scholar in the fixture |
| `user-moderator` | Sees the moderation area but cannot ban |
| `user-safety-admin` | Can suspend, ban, and revoke verification |

Nothing persists. Restarting the process reseeds from `SeedData`.

---

## Common problems

**`./gradlew` fails to download Gradle.** Both wrappers pin 8.14.3 from
`services.gradle.org`. Behind a proxy, set `HTTPS_PROXY` or point `distributionUrl` at a
mirror.

**The root build tries to reach `dl.google.com`.** It should not — the root
`settings.gradle.kts` sets `RepositoriesMode.PREFER_SETTINGS` and declares only
`mavenCentral()` and `gradlePluginPortal()`. If you see a Google Maven request from the root
build, something has added an Android dependency to a `core` module. That is a bug; see
[`CONTRIBUTING.md`](../CONTRIBUTING.md).

**`androidApp` cannot resolve `org.fisabilillah:core-model`.** The substitution block in
`androidApp/settings.gradle.kts` did not run. Confirm you opened `androidApp/` rather than a
subdirectory of it, and that the relative path `includeBuild("..")` still resolves.

**Two Gradle daemons.** Each build has its own wrapper and will start its own daemon. On a
machine with limited memory, `./gradlew --stop` between builds helps. `org.gradle.jvmargs` is
`-Xmx2g` in `gradle.properties`.

**`run_local_tests.sh` cannot connect.** It calls `pg_isready` from `$PG_BIN`, defaulting to
`/usr/lib/postgresql/16/bin`. On macOS with Homebrew, set
`PG_BIN=$(brew --prefix postgresql@16)/bin`.

---

## What to run before pushing

```console
$ ./gradlew build test                    # required — must be green
$ backend/supabase/run_local_tests.sh     # required if you touched backend/
$ cd androidApp && ./gradlew assembleDebug   # if you have the SDK
```

CI runs all three as separate jobs (`.github/workflows/ci.yml`). The Android job is expected
to need fixing on its first successful run, and the workflow says so in a comment.
