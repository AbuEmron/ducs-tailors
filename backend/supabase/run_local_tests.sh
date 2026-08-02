#!/usr/bin/env bash
# =====================================================================
# Fi Sabilillah -- run_local_tests.sh
#
# Applies the whole Supabase migration set to a throwaway database on a
# plain PostgreSQL 16 cluster and runs the RLS/authorization tests.
#
#   ./run_local_tests.sh
#
# It will:
#   1. start the local PostgreSQL cluster if it is down,
#   2. drop and recreate a throwaway database,
#   3. apply tests/00_bootstrap.sql (the local auth.* shim),
#   4. apply every supabase/migrations/*.sql in filename order,
#   5. apply seed/seed.sql,
#   6. run tests/rls_tests.sql,
#   7. print a PASS/FAIL summary and exit non-zero on any failure.
#
# Environment overrides:
#   PGDATABASE_TEST   name of the throwaway database (default fisabilillah_test)
#   PG_SUPERUSER      OS/DB superuser to run as          (default postgres)
#   KEEP_DB=1         do not drop the database at the end
# =====================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_NAME="${PGDATABASE_TEST:-fisabilillah_test}"
PG_SUPERUSER="${PG_SUPERUSER:-postgres}"
PG_BIN="${PG_BIN:-/usr/lib/postgresql/16/bin}"
LOG_FILE="$(mktemp -t fisabilillah-tests-XXXXXX.log)"

RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
if [ ! -t 1 ]; then RED=""; GREEN=""; YELLOW=""; BOLD=""; OFF=""; fi

say()  { printf '%s\n' "$*"; }
step() { printf '%s==>%s %s\n' "$BOLD" "$OFF" "$*"; }
die()  { printf '%s[FAIL]%s %s\n' "$RED" "$OFF" "$*" >&2; exit 1; }

# ---------------------------------------------------------------------
# How do we reach the server? As root we drop to the database superuser;
# otherwise we assume the current user can already connect.
# ---------------------------------------------------------------------
if [ "$(id -u)" = "0" ]; then
  RUN_AS_SUPERUSER=1
else
  RUN_AS_SUPERUSER=0
fi

psql_run() {   # psql_run <db> <args...>
  local db="$1"; shift
  if [ "$RUN_AS_SUPERUSER" = "1" ]; then
    su "$PG_SUPERUSER" -c "psql -X -v ON_ERROR_STOP=1 -d $(printf %q "$db") $*"
  else
    psql -X -v ON_ERROR_STOP=1 -d "$db" "$@"
  fi
}

psql_file() {  # psql_file <db> <absolute path>
  local db="$1" file="$2"
  if [ "$RUN_AS_SUPERUSER" = "1" ]; then
    su "$PG_SUPERUSER" -c "psql -X -v ON_ERROR_STOP=1 -q -d $(printf %q "$db") -f $(printf %q "$file")"
  else
    psql -X -v ON_ERROR_STOP=1 -q -d "$db" -f "$file"
  fi
}

admin_sql() {  # admin_sql <statement> -- runs against the maintenance database
  if [ "$RUN_AS_SUPERUSER" = "1" ]; then
    su "$PG_SUPERUSER" -c "psql -X -v ON_ERROR_STOP=1 -q -d postgres -c $(printf %q "$1")"
  else
    psql -X -v ON_ERROR_STOP=1 -q -d postgres -c "$1"
  fi
}

# ---------------------------------------------------------------------
# 1. Make sure the cluster is up.
# ---------------------------------------------------------------------
step "checking the PostgreSQL cluster"
if ! "$PG_BIN/pg_isready" -q 2>/dev/null; then
  say "    cluster is down, starting it"
  if command -v pg_ctlcluster >/dev/null 2>&1 && [ "$(id -u)" = "0" ]; then
    pg_ctlcluster 16 main start || true
  elif command -v service >/dev/null 2>&1 && [ "$(id -u)" = "0" ]; then
    service postgresql start || true
  fi
  for _ in $(seq 1 30); do
    "$PG_BIN/pg_isready" -q 2>/dev/null && break
    sleep 1
  done
fi
"$PG_BIN/pg_isready" -q 2>/dev/null || die "PostgreSQL is not accepting connections"
say "    cluster is up: $("$PG_BIN/pg_isready" 2>/dev/null || true)"

# ---------------------------------------------------------------------
# 2. Stage the SQL somewhere the database superuser can definitely read.
# ---------------------------------------------------------------------
WORK_DIR="$(mktemp -d -t fisabilillah-sql-XXXXXX)"
cleanup() {
  rm -rf "$WORK_DIR"
  if [ "${KEEP_DB:-0}" != "1" ]; then
    admin_sql "drop database if exists \"$DB_NAME\" with (force);" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

cp -r "$SCRIPT_DIR/migrations" "$SCRIPT_DIR/seed" "$SCRIPT_DIR/tests" "$WORK_DIR/"
chmod -R a+rX "$WORK_DIR"

# ---------------------------------------------------------------------
# 3. Throwaway database.
# ---------------------------------------------------------------------
step "recreating the throwaway database '$DB_NAME'"
admin_sql "drop database if exists \"$DB_NAME\" with (force);" >/dev/null 2>&1 || \
  admin_sql "drop database if exists \"$DB_NAME\";" >/dev/null
admin_sql "create database \"$DB_NAME\";" >/dev/null || die "could not create $DB_NAME"

# ---------------------------------------------------------------------
# 4. Bootstrap shim, then the migrations in order, then the seed.
# ---------------------------------------------------------------------
step "applying tests/00_bootstrap.sql (local auth.* shim -- never applied on Supabase)"
psql_file "$DB_NAME" "$WORK_DIR/tests/00_bootstrap.sql" >>"$LOG_FILE" 2>&1 \
  || { cat "$LOG_FILE"; die "bootstrap failed"; }

step "applying migrations"
shopt -s nullglob
MIGRATIONS=("$WORK_DIR"/migrations/*.sql)
shopt -u nullglob
[ "${#MIGRATIONS[@]}" -gt 0 ] || die "no migrations found under $SCRIPT_DIR/migrations"
for f in "${MIGRATIONS[@]}"; do
  printf '    %-52s' "$(basename "$f")"
  if psql_file "$DB_NAME" "$f" >>"$LOG_FILE" 2>&1; then
    printf '%sok%s\n' "$GREEN" "$OFF"
  else
    printf '%sfailed%s\n' "$RED" "$OFF"
    tail -n 40 "$LOG_FILE"
    die "migration $(basename "$f") failed"
  fi
done

step "applying seed/seed.sql"
if psql_file "$DB_NAME" "$WORK_DIR/seed/seed.sql" >>"$LOG_FILE" 2>&1; then
  say "    seeded"
else
  tail -n 40 "$LOG_FILE"
  die "seed failed"
fi

# ---------------------------------------------------------------------
# 5. The tests.
# ---------------------------------------------------------------------
step "running tests/rls_tests.sql"
TEST_OUT="$(mktemp -t fisabilillah-rls-XXXXXX.out)"
if [ "$RUN_AS_SUPERUSER" = "1" ]; then
  su "$PG_SUPERUSER" -c "psql -X -v ON_ERROR_STOP=1 -q -d $(printf %q "$DB_NAME") -f $(printf %q "$WORK_DIR/tests/rls_tests.sql")" \
    >"$TEST_OUT" 2>&1
else
  psql -X -v ON_ERROR_STOP=1 -q -d "$DB_NAME" -f "$WORK_DIR/tests/rls_tests.sql" >"$TEST_OUT" 2>&1
fi
TEST_STATUS=$?

# psql prints our RAISE NOTICE output on stderr, which we folded into
# TEST_OUT above. Strip the NOTICE prefix for readability, then count on
# the normalised text.
NORM_OUT="$(mktemp -t fisabilillah-rls-norm-XXXXXX.out)"
sed -e 's/^psql:[^ ]*: NOTICE:  //' -e 's/^NOTICE:  //' "$TEST_OUT" > "$NORM_OUT"
cat "$NORM_OUT"

PASS_COUNT="$(grep -c '^PASS: ' "$NORM_OUT" || true)"
FAIL_COUNT="$(grep -c 'FAIL: ' "$NORM_OUT" || true)"
rm -f "$NORM_OUT"

echo
echo "---------------------------------------------------------------"
if [ "$TEST_STATUS" -ne 0 ] || [ "$FAIL_COUNT" -ne 0 ] || [ "$PASS_COUNT" -eq 0 ]; then
  printf '%s%sRESULT: FAIL%s  (%s assertions passed, %s failed, psql exit %s)\n' \
    "$BOLD" "$RED" "$OFF" "$PASS_COUNT" "$FAIL_COUNT" "$TEST_STATUS"
  echo "full log: $LOG_FILE"
  echo "test output: $TEST_OUT"
  exit 1
fi

printf '%s%sRESULT: PASS%s  (%s assertions passed)\n' "$BOLD" "$GREEN" "$OFF" "$PASS_COUNT"
rm -f "$TEST_OUT" "$LOG_FILE"
exit 0
