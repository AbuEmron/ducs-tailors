// =====================================================================
// donation-checkout
//
// Turns "I would like to give £25 to the winter fund" into a Stripe
// hosted checkout page, and records that the attempt happened.
//
// This function exists because the two things it does cannot be done on
// a phone. It holds the Stripe secret key, which must never leave a
// server, and it writes to `donations`, which no client session can
// write to at all since migration 0019.
//
// WHAT IT TRUSTS FROM THE CALLER
// Two things, and it validates both: a campaign id, and an amount. That
// is the entire attack surface. Everything else that matters -- the
// currency, the receiving organisation, whether that campaign is allowed
// to collect, the donor's identity -- is read from the database or from
// the verified JWT, never from the request body. A client that sends a
// currency, a fee, a net amount or somebody else's donor id will find
// all four ignored.
//
// WHAT IT DELIBERATELY DOES NOT DO
// It does not mark anything as paid. It cannot: the row it writes is
// `awaiting_payment` and the only thing that advances it is
// stripe-webhook, responding to a signed event. If this function is ever
// changed to set a donation to `settled`, the guarantee that a donation
// record corresponds to money that actually moved is gone.
//
// SECRETS (set with `supabase secrets set`, never in this file)
//   STRIPE_SECRET_KEY            sk_live_... or sk_test_...
//   SUPABASE_URL                 provided by the platform
//   SUPABASE_SERVICE_ROLE_KEY    provided by the platform
//   DONATION_RETURN_URL          where Stripe sends the donor afterwards
// =====================================================================

import { createClient } from "jsr:@supabase/supabase-js@2";

const STRIPE_API = "https://api.stripe.com/v1";

/**
 * Matches `GivingLimits` in core:model.
 *
 * Duplicated rather than shared, because these two copies protect
 * different things: the Kotlin one stops a donor being shown a form they
 * cannot submit, and this one stops anybody who skips the app entirely.
 * If they ever disagree, this one wins, and that is the right way round.
 */
const MINIMUM_MINOR_UNITS = 100;
const MAXIMUM_MINOR_UNITS = 500_000;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

/**
 * A refusal the donor can read.
 *
 * Deliberately vague about *why* a campaign cannot collect. "This
 * campaign is not currently able to accept donations" is all a donor
 * needs; the difference between "never verified", "verification expired"
 * and "suspended for fraud" is a moderation matter, and spelling it out
 * turns a refusal into an argument with the wrong person.
 */
function refuse(message: string, status = 400): Response {
  return json({ error: message }, status);
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (request.method !== "POST") return refuse("Method not allowed", 405);

  const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const returnUrl = Deno.env.get("DONATION_RETURN_URL");

  if (!stripeKey || !supabaseUrl || !serviceKey || !returnUrl) {
    // Never say which one is missing. This response reaches the public.
    console.error("donation-checkout is not fully configured");
    return refuse("Giving is temporarily unavailable.", 503);
  }

  // ── Who is asking ────────────────────────────────────────────────────
  //
  // The token is verified by Supabase rather than parsed here. A JWT that
  // is merely decoded is not authentication, and hand-rolling the check
  // is the classic way to accept an unsigned one.
  const authorization = request.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) {
    return refuse("Sign in to give.", 401);
  }

  const asCaller = createClient(supabaseUrl, Deno.env.get("SUPABASE_ANON_KEY") ?? "", {
    global: { headers: { Authorization: authorization } },
  });
  const { data: userData, error: userError } = await asCaller.auth.getUser();
  if (userError || !userData?.user) {
    return refuse("Sign in to give.", 401);
  }
  const donorId = userData.user.id;

  // ── What they asked for ──────────────────────────────────────────────
  let body: { campaignId?: unknown; amountMinorUnits?: unknown; anonymous?: unknown };
  try {
    body = await request.json();
  } catch {
    return refuse("Malformed request.");
  }

  const campaignId = typeof body.campaignId === "string" ? body.campaignId : null;
  const amount = typeof body.amountMinorUnits === "number" ? body.amountMinorUnits : null;
  const anonymous = body.anonymous === true;

  if (!campaignId) return refuse("No campaign was named.");
  if (amount === null || !Number.isInteger(amount)) {
    return refuse("The amount must be a whole number of pence.");
  }
  if (amount < MINIMUM_MINOR_UNITS) {
    return refuse("That is below the smallest donation this platform can process.");
  }
  if (amount > MAXIMUM_MINOR_UNITS) {
    return refuse(
      "For a gift that size, please contact the organisation directly. Amounts above " +
        "the limit need checks this platform cannot do automatically.",
    );
  }

  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false },
  });

  // ── May this campaign collect at all ─────────────────────────────────
  //
  // Asked of the database rather than reconstructed here. The same
  // function backs the trigger that guards the flag, so there is one
  // definition of "may collect" and not two that can drift apart.
  const { data: mayCollect, error: gateError } = await admin.rpc("campaign_may_collect", {
    p_campaign: campaignId,
  });
  if (gateError) {
    console.error("campaign_may_collect failed", gateError.message);
    return refuse("Giving is temporarily unavailable.", 503);
  }
  if (mayCollect !== true) {
    return refuse("This campaign is not currently able to accept donations.", 409);
  }

  const { data: campaign, error: campaignError } = await admin
    .from("campaigns")
    .select("id, title, currency, organization_id")
    .eq("id", campaignId)
    .single();
  if (campaignError || !campaign) {
    return refuse("That campaign could not be found.", 404);
  }

  // ── Record the attempt ───────────────────────────────────────────────
  //
  // Written before Stripe is called, not after. If the call fails or the
  // donor closes the page, an abandoned `awaiting_payment` row is left
  // behind, and that is the outcome we want: a pattern of started-and-
  // abandoned attempts against one campaign is a fraud signal, and it is
  // invisible if rows are only written once money arrives.
  const { data: donation, error: insertError } = await admin
    .from("donations")
    .insert({
      campaign_id: campaign.id,
      organization_id: campaign.organization_id,
      donor_id: donorId,
      is_anonymous: anonymous,
      status: "awaiting_payment",
      currency: campaign.currency,
      amount: (amount / 100).toFixed(2),
      provider: "stripe",
    })
    .select("id")
    .single();

  if (insertError || !donation) {
    console.error("could not record the donation", insertError?.message);
    return refuse("Giving is temporarily unavailable.", 503);
  }

  // ── Ask Stripe for a page ────────────────────────────────────────────
  const form = new URLSearchParams({
    mode: "payment",
    success_url: `${returnUrl}?donation=${donation.id}&outcome=complete`,
    cancel_url: `${returnUrl}?donation=${donation.id}&outcome=cancelled`,
    "line_items[0][quantity]": "1",
    "line_items[0][price_data][currency]": String(campaign.currency).toLowerCase(),
    "line_items[0][price_data][unit_amount]": String(amount),
    "line_items[0][price_data][product_data][name]": String(campaign.title),
    // The donation id travels with the payment and comes back on the
    // webhook. Without it a settled payment has no way to say what it
    // settled, and the money arrives with nobody's name on it.
    "metadata[donation_id]": donation.id,
    "metadata[campaign_id]": campaign.id,
    "payment_intent_data[metadata][donation_id]": donation.id,
    // Stripe emails its own receipt. This platform does not send one,
    // because a second receipt for the same payment, issued by a body
    // that is not the charity, is a gift-aid and tax-record problem
    // rather than a courtesy.
    submit_type: "donate",
  });

  // An idempotency key derived from the donation id: a double-tapped
  // button produces one session, not two.
  const stripeResponse = await fetch(`${STRIPE_API}/checkout/sessions`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${stripeKey}`,
      "content-type": "application/x-www-form-urlencoded",
      "Idempotency-Key": `donation-${donation.id}`,
    },
    body: form,
  });

  if (!stripeResponse.ok) {
    const detail = await stripeResponse.text();
    console.error("stripe rejected the session", stripeResponse.status, detail);
    await admin
      .from("donations")
      .update({ status: "failed", failure_reason: "checkout_not_created" })
      .eq("id", donation.id);
    return refuse(
      "The payment page could not be opened. Nothing has been charged.",
      502,
    );
  }

  const session = await stripeResponse.json();

  await admin
    .from("donations")
    .update({ provider_session_id: session.id })
    .eq("id", donation.id);

  return json({
    donationId: donation.id,
    providerReference: session.id,
    url: session.url,
    expiresAt: new Date((session.expires_at ?? 0) * 1000).toISOString(),
  });
});
