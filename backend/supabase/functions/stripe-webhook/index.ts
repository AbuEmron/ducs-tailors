// =====================================================================
// stripe-webhook
//
// The only thing in this system that can say a donation was paid.
//
// Everything else -- the app, the use cases, the other edge function --
// can create an intention to give. This endpoint, and nothing else,
// turns one into a settled record, and it does so only for an event
// whose signature it has verified against a secret that Stripe and this
// function share.
//
// SIGNATURE VERIFICATION IS THE WHOLE SECURITY MODEL
// This URL is public. Anyone can POST to it. The only thing standing
// between an attacker and a campaign total showing money that was never
// given is the check below, so it is written out in full rather than
// delegated, and it does three things that are each load-bearing:
//
//   1. It verifies HMAC-SHA256 over `${timestamp}.${rawBody}` -- the
//      RAW body, byte for byte. Re-serialising parsed JSON changes key
//      order and whitespace and produces a different, wrong signature.
//   2. It compares in constant time. A byte-by-byte early return leaks
//      the expected signature to anyone willing to measure.
//   3. It enforces a five-minute tolerance on the timestamp, so a
//      genuine event captured off the wire cannot be replayed a week
//      later.
//
// IDEMPOTENCY IS NOT OPTIONAL
// Stripe delivers at least once and retries on any non-2xx response.
// Duplicate deliveries are normal traffic, not errors. The insert into
// `payment_events` keyed on Stripe's own event id is the lock: if it
// conflicts, this event has been handled and we stop. Without it a
// retried `checkout.session.completed` would settle the same donation
// twice.
//
// AND IT MUST ANSWER 200 EVEN WHEN IT IGNORES THE EVENT
// A non-2xx tells Stripe to retry. An event we do not care about, or one
// for a donation that no longer exists, is not a failure -- returning
// anything but 200 for those turns a shrug into an infinite retry loop.
// The only 4xx here is a bad signature, which should retry never.
//
// SECRETS (set with `supabase secrets set`, never in this file)
//   STRIPE_WEBHOOK_SECRET        whsec_... from the endpoint's settings
//   SUPABASE_URL                 provided by the platform
//   SUPABASE_SERVICE_ROLE_KEY    provided by the platform
//
// This function must be deployed with JWT verification OFF
// (`verify_jwt = false`): Stripe does not carry a Supabase token, and
// its signature is the authentication.
// =====================================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const TOLERANCE_SECONDS = 300;

/** Constant-time comparison. Returns false for a length mismatch. */
function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}

function hexToBytes(hex: string): Uint8Array {
  if (hex.length % 2 !== 0) return new Uint8Array(0);
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) {
    const byte = Number.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    if (Number.isNaN(byte)) return new Uint8Array(0);
    out[i] = byte;
  }
  return out;
}

/**
 * Verifies a `Stripe-Signature` header.
 *
 * The header looks like `t=1690000000,v1=abc...,v1=def...`. More than one
 * v1 appears while a signing secret is being rotated, and any of them
 * matching is a valid event -- rejecting all but the first would break
 * every rotation.
 */
async function signatureIsValid(
  rawBody: string,
  header: string | null,
  secret: string,
): Promise<boolean> {
  if (!header) return false;

  let timestamp: string | null = null;
  const candidates: string[] = [];
  for (const part of header.split(",")) {
    const [key, value] = part.split("=", 2);
    if (key === "t") timestamp = value;
    if (key === "v1" && value) candidates.push(value);
  }
  if (!timestamp || candidates.length === 0) return false;

  const age = Math.abs(Math.floor(Date.now() / 1000) - Number(timestamp));
  if (!Number.isFinite(age) || age > TOLERANCE_SECONDS) return false;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const expected = new Uint8Array(
    await crypto.subtle.sign(
      "HMAC",
      key,
      new TextEncoder().encode(`${timestamp}.${rawBody}`),
    ),
  );

  return candidates.some((candidate) => equalBytes(expected, hexToBytes(candidate)));
}

/** Stripe amounts are integer minor units; the column is numeric(14,2). */
function toDecimal(minorUnits: number | null | undefined): string | null {
  if (minorUnits === null || minorUnits === undefined) return null;
  return (minorUnits / 100).toFixed(2);
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const secret = Deno.env.get("STRIPE_WEBHOOK_SECRET");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");

  if (!secret || !supabaseUrl || !serviceKey) {
    console.error("stripe-webhook is not fully configured");
    // 500 so Stripe retries: this is our fault and is worth retrying.
    return new Response("not configured", { status: 500 });
  }

  const rawBody = await request.text();
  if (!(await signatureIsValid(rawBody, request.headers.get("Stripe-Signature"), secret))) {
    // The one case that must never be retried, and must never be logged
    // with its body: an unverified payload is attacker-controlled.
    console.warn("rejected an event with an invalid signature");
    return new Response("invalid signature", { status: 400 });
  }

  const event = JSON.parse(rawBody);
  const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

  // ── The idempotency lock ─────────────────────────────────────────────
  const { error: ledgerError } = await admin
    .from("payment_events")
    .insert({ id: event.id, type: event.type });

  if (ledgerError) {
    // 23505 is unique_violation: we have seen this event already. That is
    // an ordinary retry, so answer 200 and do nothing.
    if (ledgerError.code === "23505") {
      return new Response("already handled", { status: 200 });
    }
    console.error("could not write the event ledger", ledgerError.message);
    return new Response("ledger unavailable", { status: 500 });
  }

  const object = event.data?.object ?? {};
  const donationId: string | null =
    object.metadata?.donation_id ?? object.payment_intent?.metadata?.donation_id ?? null;

  let outcome = "ignored";
  let summary: string | null = null;

  switch (event.type) {
    case "checkout.session.completed": {
      if (!donationId) break;

      // `payment_status` matters: a completed session with an unpaid
      // status is a delayed method (a bank debit) that has not cleared.
      // Treating it as settled would credit a campaign for money that
      // may still fail days later.
      if (object.payment_status !== "paid") {
        await admin
          .from("donations")
          .update({ status: "pending" })
          .eq("id", donationId);
        outcome = "pending";
        summary = "checkout complete, payment not yet cleared";
        break;
      }

      // The fee is on the balance transaction rather than the session, so
      // it takes a second call. A donation that settles without it is
      // still correct -- net_amount simply stays null, which the model
      // treats as "not known" rather than "nothing was deducted".
      let fee: number | null = null;
      let net: number | null = null;
      let receiptUrl: string | null = null;
      const paymentIntentId: string | null =
        typeof object.payment_intent === "string" ? object.payment_intent : null;

      if (paymentIntentId && stripeKey) {
        const expanded = await fetch(
          `https://api.stripe.com/v1/payment_intents/${paymentIntentId}` +
            `?expand[]=latest_charge.balance_transaction`,
          { headers: { Authorization: `Bearer ${stripeKey}` } },
        );
        if (expanded.ok) {
          const intent = await expanded.json();
          const charge = intent.latest_charge;
          receiptUrl = charge?.receipt_url ?? null;
          const balance = charge?.balance_transaction;
          if (balance && typeof balance === "object") {
            fee = balance.fee ?? null;
            net = balance.net ?? null;
          }
        } else {
          console.error("could not read the balance transaction", expanded.status);
        }
      }

      const { error } = await admin
        .from("donations")
        .update({
          status: "settled",
          provider_payment_id: paymentIntentId,
          fee_amount: toDecimal(fee),
          net_amount: toDecimal(net),
          receipt_url: receiptUrl,
          settled_at: new Date().toISOString(),
        })
        .eq("id", donationId);

      if (error) {
        console.error("could not settle the donation", error.message);
        // Undo the ledger entry so the retry is allowed to try again --
        // otherwise the lock we took swallows the only delivery that
        // could have fixed this.
        await admin.from("payment_events").delete().eq("id", event.id);
        return new Response("could not settle", { status: 500 });
      }
      outcome = "settled";
      break;
    }

    case "checkout.session.expired": {
      if (!donationId) break;
      // Not a failure and not the donor's fault -- they closed the page
      // or changed their mind, which is theirs to do. Nothing chases them.
      await admin
        .from("donations")
        .update({ status: "failed", failure_reason: "checkout_expired" })
        .eq("id", donationId)
        .eq("status", "awaiting_payment");
      outcome = "expired";
      break;
    }

    case "payment_intent.payment_failed": {
      if (!donationId) break;
      await admin
        .from("donations")
        .update({
          status: "failed",
          // Stripe's own decline reason, which is a code rather than a
          // sentence. It is never shown to the donor as-is: a card
          // decline explained in the processor's vocabulary reads as an
          // accusation.
          failure_reason: object.last_payment_error?.code ?? "payment_failed",
        })
        .eq("id", donationId);
      outcome = "failed";
      break;
    }

    case "charge.refunded": {
      if (!donationId) break;
      await admin
        .from("donations")
        .update({ status: "refunded" })
        .eq("id", donationId);
      outcome = "refunded";
      break;
    }

    case "charge.dispute.created": {
      if (!donationId) break;
      // Left for a human. A dispute is somebody saying they did not
      // authorise this, which on a donation platform is either a stolen
      // card or a person who feels misled, and neither is a state
      // transition to be resolved automatically.
      await admin
        .from("donations")
        .update({ status: "disputed" })
        .eq("id", donationId);
      outcome = "disputed";
      summary = "needs a human";
      break;
    }

    default:
      break;
  }

  await admin
    .from("payment_events")
    .update({
      processed_at: new Date().toISOString(),
      outcome,
      summary,
      donation_id: donationId,
    })
    .eq("id", event.id);

  return new Response("ok", { status: 200 });
});
