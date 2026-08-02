-- =====================================================================
-- Fi Sabilillah -- 0017_auth_hardening.sql
--
-- Two things the Supabase security advisor caught once real sign-in
-- existed, both of which were invisible on a plain PostgreSQL cluster.
--
-- 1. GRANTS THAT ARRIVE BY THEMSELVES.
--    Supabase ships default privileges that grant EXECUTE on every new
--    function in `public` to `anon` and `authenticated`. `revoke ... from
--    public` in 0016 did not touch them, because they are grants to those
--    roles by name rather than to PUBLIC. Locally there are no such
--    defaults, so the test asserting anon cannot call register_member
--    passed on a cluster where the grant never existed in the first place.
--    Revoked explicitly here.
--
-- 2. AN AUDIT LOG ANYONE COULD WRITE TO.
--    audit_logs_insert_any allowed any signed-in member to append any row,
--    with any actor_id and any action text. Nobody could authenticate
--    before, so nobody could use it. Now they can, and a forgeable audit
--    log is worse than none: it is the record a moderator reads when
--    deciding whether someone did what they are accused of, and it would
--    have accepted a row naming an innocent member as the actor.
--
--    The table stays append-only and unchanged in every other respect. The
--    only way in is now app.write_audit(), which is SECURITY DEFINER and
--    owned by a role that bypasses RLS, and which stamps actor_id from
--    app.current_user_id() rather than from anything the caller passes.
-- =====================================================================

revoke all on function public.register_member(
  text, public.gender, text, text, text, int, text, char(2), boolean) from anon;
revoke all on function public.current_member() from anon;

drop policy if exists audit_logs_insert_any on public.audit_logs;

revoke insert on public.audit_logs from authenticated, anon;

comment on table public.audit_logs is
  'Append-only, and not writable from a client session at all: the sole entry '
  'point is app.write_audit(). A moderator can act, but cannot erase -- or '
  'fabricate -- the fact that they acted.';
