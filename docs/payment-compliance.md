# Payment compliance

## Payments are on, one campaign at a time

**This document was written when payments were off entirely, behind a single compile-time
flag. That flag is gone.** Stripe is wired up; the mechanism is described in
[`payments.md`](payments.md). Nothing below is thereby discharged — the checklist is what it
always was, and most of it is organisational rather than technical.

What changed is the shape of the switch, and the change was a narrowing rather than a
widening:

```kotlin
// Gone. A property of the build, false for every campaign at once.
public const val paymentsEnabled: Boolean = false

// What replaces it: a property of one campaign, set by an administrator,
// rejected by the database unless that campaign and its organisation are
// both currently verified.
val paymentsEnabled: Boolean = false   // on Campaign
```

A global flag was the wrong instrument. Flipping it would have switched on every campaign
simultaneously, including ones nobody had looked at, and the moment it was flipped the
verification state of any individual appeal stopped mattering. The per-campaign gate cannot
be flipped that way: `app.campaign_may_collect()` is evaluated at the moment of giving and
requires a current organisation verification *and* a current campaign financial review, so a
lapse stops the money the same day it happens.

The properties the old flag was chosen for are preserved, and one is added:

| Property | Old flag | Now |
| --- | --- | --- |
| Cannot be flipped by configuration | ✔ compile-time constant | ✔ database trigger, platform administrator only |
| Cannot be flipped by accident | ✔ code change and release | ✔ requires a written reason, written to the audit log |
| Cannot be flipped by an administrator alone | ✔ | ✔ verification must exist first |
| Stops automatically when verification lapses | ✘ nothing to lapse | ✔ checked at give-time |

`VerificationPolicy.canOfferDonations` no longer returns a preview state for everyone. It
returns `Unavailable` for an organisation whose registration has not been checked, and
`Available` for one whose has. The old preview copy — *"the platform is not authorised to
handle charitable funds"* — has been retired, because leaving it in place while money can in
fact move would have made it a lie.

---

## Why the gate is in the database rather than in a ticket

Handling other people's sadaqah and zakat is not something to improvise. The failure modes
are not bugs; they are a family's zakat going somewhere it should not have gone, a fraudulent
campaign running for three weeks before anyone notices, and a platform that has taken money
it is not registered to solicit.

The gate lives in a trigger and a `SECURITY DEFINER` function because those are the only
places a client cannot reach and an application bug cannot bypass. A check in the Kotlin
model is a courtesy to the screen; a check in `trg_campaigns_guard_payments` is the one that
holds when somebody calls the REST API directly.

---

## The checklist

**Every box must be ticked before a campaign is switched on for real donors.** Not most of them. Not
"we will do that in the next sprint". This is a gate, and the reason it is written as a gate
is that every item on it is easier to do before money moves than after.

Nothing on this list has been started.

### 1. Legal entity and charitable status

- [ ] A legal entity exists that can enter into contracts and hold liability.
- [ ] Its jurisdiction of incorporation is decided and documented.
- [ ] Its role is decided and documented: is the platform a **conduit** passing funds to
      registered charities, or does it **receive** funds itself and regrant them? These carry
      entirely different obligations and the answer determines most of what follows.
- [ ] If the platform receives funds: charity or nonprofit registration is complete in its
      home jurisdiction.
- [ ] Governing documents, trustees or directors, and a conflict-of-interest policy are in
      place.
- [ ] Professional indemnity and directors' liability cover is in force.
- [ ] Legal advice has been taken specifically on faith-based giving, which carries
      restrictions and expectations that generic charity advice does not cover.

### 2. Charitable-solicitation registration, per jurisdiction

Soliciting donations is regulated separately from being a charity, and separately in each
place you solicit. A platform reachable from anywhere solicits everywhere unless it
deliberately does not.

- [ ] The list of jurisdictions where donations will be solicited is decided and written
      down.
- [ ] Registration is complete in each. In the United States this means state-by-state
      charitable-solicitation registration, and it is not optional for a public appeal.
- [ ] In the United Kingdom, Charity Commission registration and the fundraising standards
      that go with it.
- [ ] The Fundraising Regulator's code, or the local equivalent, has been read and applied to
      the actual interface — not to an intention.
- [ ] Geographic restrictions are enforced in the product where registration does not cover a
      jurisdiction. A disclaimer is not an enforcement mechanism.
- [ ] Required disclosures appear at the point of donation: who the recipient is, their
      registration number, and where their accounts can be inspected.
- [ ] A calendar exists for annual filings, with a named owner.

### 3. Tax

- [ ] The tax treatment of each fund type is determined: sadaqah, zakat, emergency aid,
      general operations, restricted project.
- [ ] **United Kingdom:** Gift Aid is either implemented properly — declaration capture,
      eligibility checks, HMRC claim process, record retention — or deliberately not offered.
      A half-implemented Gift Aid flow is a liability.
- [ ] **United States:** 501(c)(3) status is confirmed, and contemporaneous written
      acknowledgement is issued for donations of $250 or more with the wording the IRS
      requires.
- [ ] Receipts are issued for every donation, in a form the donor's tax authority accepts.
- [ ] Quid-pro-quo rules are handled where anything is given in return.
- [ ] Cross-border donations are handled correctly, including the cases where they are simply
      not deductible.
- [ ] Sales tax and VAT treatment of any platform fee is determined.
- [ ] A tax adviser has reviewed the actual implementation, not a description of it.

### 4. Payment provider

- [ ] A licensed payment provider is engaged, with a signed agreement.
- [ ] **The platform never holds funds directly.** Money moves from the donor to the provider
      to the receiving organisation. The platform is not a money transmitter, is not
      registered as one, and must not behave as one.
- [ ] Whether a money-transmitter or payment-institution licence is nonetheless required has
      been checked in every jurisdiction of operation, and the answer is documented.
- [ ] The provider's terms permit charitable fundraising on behalf of third parties. Many
      standard agreements do not.
- [ ] Settlement timing, reserves and holdbacks are understood and communicated to receiving
      organisations.
- [ ] Fee disclosure to donors is explicit: platform fee, provider fee, and exactly how much
      reaches the cause.
- [ ] Payouts go only to a verified bank account held in the receiving organisation's own
      name.
- [ ] Multi-currency handling and exchange-rate disclosure are settled.
- [ ] `Money` is used throughout, in minor units, so no donation total ever passes through a
      floating-point value. (Already true in the model.)

### 5. KYC and KYB on receiving organisations

- [ ] Every organisation that can receive funds has completed know-your-business
      verification: registration documents, jurisdiction, registration number, and expiry.
- [ ] Beneficial ownership and control are identified.
- [ ] Named signatories have completed know-your-customer identity verification.
- [ ] Bank account ownership is verified independently, not merely asserted.
- [ ] Re-verification runs on a schedule; the model already carries an expiry date on
      organisation verification.
- [ ] Verification is revoked automatically when documents lapse, and campaigns stop
      accepting funds when it is.
- [ ] What verification means is stated honestly to donors. The model is already careful
      here: "Registration documents were checked. It is not an audit of how funds are spent."
      Nothing in the payment flow may imply more than that.

### 6. Anti-fraud and sanctions

- [ ] Sanctions screening on every receiving organisation, its beneficial owners and its
      signatories, against the applicable lists — and re-screened on a schedule, because
      lists change.
- [ ] Sanctions screening on donors where the amount or the jurisdiction requires it.
- [ ] Anti-money-laundering procedures proportionate to volume, with a named responsible
      person.
- [ ] Suspicious-activity reporting route established, with the named person who owns it.
- [ ] Transaction monitoring for the patterns that matter: unusual velocity, structuring,
      card testing, a sudden change of payout account.
- [ ] Campaign review before publication. The model has the states
      (`CampaignVerification.PENDING` → `VERIFIED`); the human review process behind them does
      not exist.
- [ ] A route to freeze a campaign immediately on a fraud signal. `CAMPAIGN_SUSPENDED` exists
      as a moderation action requiring a safety administrator; the payment-side freeze does
      not.
- [ ] Chargeback and fraud-rate monitoring against the provider's thresholds.
- [ ] Individual help requests are considered separately. `FraudReviewState` exists on a
      service request, and a request for direct financial help is a materially different risk
      from a registered charity's campaign.

### 7. Refunds and disputes

- [ ] A written refund policy, published, stating when a donation can be refunded and when it
      cannot.
- [ ] The policy addresses the hard case honestly: money already disbursed to a beneficiary
      generally cannot be recovered, and a donor needs to know that before giving.
- [ ] A dispute and chargeback process, with the provider's timelines documented.
- [ ] A complaints procedure, and the external ombudsman or regulator a complainant can go to
      if the platform's own answer does not satisfy them.
- [ ] Recurring donations can be cancelled by the donor in one obvious step, without
      contacting anybody. `recurringEnabled` stays off until this is true.
- [ ] The `REFUNDED` and `DISPUTED` donation states are actually reachable in the
      implementation, not merely present in the enum.

### 8. Restricted-fund accounting

- [ ] Restricted funds are tracked separately from general funds, per the recorded intent.
      `FundType.RESTRICTED_PROJECT` and the restricted-fund notes on a campaign exist for
      this.
- [ ] Zakat funds are segregated from sadaqah, because they are not interchangeable and
      treating them as such is a serious matter rather than an accounting inconvenience.
- [ ] The over-funding case is decided in advance and disclosed to donors before they give:
      what happens when a campaign raises more than its goal.
- [ ] The under-funding case is decided and disclosed: what happens when a campaign closes
      short.
- [ ] The cancelled-project case is decided and disclosed.
- [ ] Donor-intent records are retained for as long as the funds are held.
- [ ] Reconciliation between the platform's records and the provider's runs on a schedule,
      with a named owner and a documented procedure for a discrepancy.

### 9. Financial transparency

- [ ] Receiving organisations are required to report on the use of restricted funds, and the
      requirement is contractual rather than aspirational.
- [ ] Campaign updates are published to donors. `CampaignUpdate` exists in the model.
- [ ] Aggregate figures — raised, disbursed, outstanding — are published per campaign.
- [ ] The platform's own fees are disclosed prominently, not in a footnote.
- [ ] Annual accounts are published if the platform's status requires it.
- [ ] An independent audit or examination is arranged at whatever threshold applies.
- [ ] There is a route for a donor to ask where their money went and get a real answer.

### 10. PCI scope

- [ ] Card details **never touch the platform's servers or the client application**. The
      integration is hosted fields, a redirect, or the provider's own SDK.
- [ ] The applicable Self-Assessment Questionnaire is identified and completed — SAQ A where
      the integration genuinely keeps the platform out of scope.
- [ ] No card data appears in logs, error reports, crash reports, or analytics.
- [ ] TLS configuration meets the current requirement.
- [ ] Scope is re-assessed whenever the integration changes. A change from a redirect to an
      in-app form changes the questionnaire.
- [ ] Provider webhooks are signature-verified, and replay is handled.

### 11. Zakat

This one is short and non-negotiable.

- [ ] **The platform never asserts zakat eligibility.** Not by default, not by inference, not
      by an organisation ticking a box about its own campaign.
- [ ] Eligibility is recorded only as a `ZakatAttestation` from a named qualified body,
      carrying who attested, their statement, an optional document reference, when, and who
      recorded it.
- [ ] The model constraint holds: a campaign cannot be marked zakat eligible without a
      recorded attestation. This is already enforced in the type's `init` block, by a CHECK
      constraint in the database, by a guard trigger, and by a `security definer` setter
      requiring a verified organisation.
- [ ] The disclaimer is displayed wherever eligibility is shown, in these words:

      > "Zakat eligibility was stated by the named body, not by this platform. If your
      > circumstances are unusual, ask a qualified scholar who knows them."

- [ ] Zakat funds are segregated in accounting (see §8).
- [ ] Attestation documents are retained and re-verified when a campaign is renewed.
- [ ] Nothing in the interface implies the platform has reviewed, endorsed, or agreed with the
      ruling. It has recorded it.

The principle behind this is the same one that runs through the rest of the product: the
platform verifies documents and states what it verified. It does not adjudicate religion.

---

## Before flipping the flag

- [ ] Every box above is ticked, with evidence, and a named person has signed off on each
      section.
- [ ] A penetration test covering the payment flow has been completed and its findings
      closed.
- [ ] A dry run has been completed in the provider's sandbox, end to end, including a refund
      and a chargeback.
- [ ] The `SANDBOX_RECORDED` donation state has been retired or is clearly distinguished from
      real donations in every view.
- [ ] Rollback is understood: turning the flag back off stops new donations, and the plan for
      in-flight ones is written down.
- [ ] Support is staffed and briefed. A donation question is a trust question, and an
      unanswered one costs more than the donation.

---

## Related

- [`deployment.md`](deployment.md) — where this sits in the release sequence
- [`moderation-guide.md`](moderation-guide.md) — the fraud and donation-misuse report
  categories, both critical severity
- [`backend/docs/threat-model.md`](../backend/docs/threat-model.md) AP-6 — charity fraud and
  a false zakat claim, as an explicit attack path
- [`backend/README.md`](../backend/README.md) — the schema-level guarantees on campaigns
