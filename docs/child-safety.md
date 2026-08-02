# Child safety

*Written for two readers: an engineer, and a community safeguarding lead. Nothing here
requires you to read code.*

---

## The position: adults only

Fi Sabilillah is for people aged 18 and over. Youth participation is a later phase, and it
does not ship until everything in [the requirements section](#before-any-youth-functionality-ships)
below is true.

This is not a disclaimer. It is enforced in four independent places:

1. **Onboarding refuses** anybody who does not declare they are an adult, with a message that
   says what is coming: *"This platform is currently for adults only. Youth participation is
   coming later, through verified organisations with guardian consent."*
2. **An age declaration is a required consent**, recorded with a version and a timestamp:
   *"This platform is for adults. Declaring an age you are not is a violation of the terms."*
3. **The contact gate refuses** any conversation where one party is an adult and the other is
   not.
4. **The introduction workflow refuses** unless both parties are adults: *"Formal
   introductions are only available between adults."*

The age threshold is a single constant, `Profile.ADULT_AGE = 18`, with a comment pointing at
this document.

---

## The age gate

A profile stores a **year of birth**, not a full date of birth. `ageAt(currentYear)` and
`isAdultIn(currentYear)` derive the only answers the system needs.

This is a privacy decision as much as a safety one. A full date of birth is a strong
identifier and the platform has no use for one. The cost is a small imprecision around
birthdays, which for an 18-year threshold is acceptable; the benefit is that a data breach
does not hand out a field that appears on identity documents.

The declaration is self-reported today, and self-reported age is weak. Nothing checks it.
Strengthening it means identity verification with an age attribute, and identity verification
is itself not yet wired to a provider — see [Known gaps](#known-gaps).

---

## Adult-to-minor direct messaging does not exist as a code path

This is the strongest statement in the document and it is meant literally.

`ContactPolicy.evaluate` compares the adulthood of both parties and refuses if they differ:

| What the sender sees | What the record says |
| --- | --- |
| "Direct messages between adults and under-18s are not available." | adult-to-minor direct contact is prohibited |

`DenialReason.MINOR_ADULT_DIRECT_CONTACT`.

Three properties of that check matter:

**There is no configuration that permits it.** Not a safeguard setting, not an organisation
floor, not an administrator override, not a support request. There is no parameter to change.
An engineer wanting to allow it would have to delete the check, which is a visible act in a
review.

**It is symmetrical.** An adult cannot message a minor and a minor cannot message an adult.
The check compares the two answers and refuses on any difference.

**It runs before any structural checks.** A guardian requirement, a moderator requirement, or
a group-context requirement cannot be offered as a way of satisfying it, because the refusal
happens first.

The consequence, and it is intentional: today, since every account is an adult account, no
adult-to-minor conversation exists on the platform at all. When youth accounts are introduced,
the check is already there and already refuses — the work is building the supervised
group-only alternative, not adding a prohibition.

---

## Work involving young people requires a background check

Some kinds of service routinely bring a volunteer into contact with minors, or into somebody's
home. Those categories carry a flag in the model, and the flag is enforced rather than
displayed.

| Category | Why it is flagged |
| --- | --- |
| Youth mentorship | Involves minors |
| Childcare assistance | Involves minors |
| Elder assistance | Involves home visits |
| Disability support | Involves home visits |

`ServiceCategory.requiresBackgroundCheckByDefault` is true when a category involves minors or
involves home visits.

### Publishing

`CreateOpportunityUseCase` refuses to publish a listing in one of these categories without a
background-check requirement:

> "This kind of work involves people who need extra protection, so a background check must be
> required."

And, for anything involving minors specifically, without safeguarding arrangements:

> "Work with young people needs child safeguarding arrangements in place."

Both are field-addressed errors so the organiser is told which field to fix, rather than being
given an exception. An organiser cannot waive either in the publishing flow.

### Applying

`ApplyToOpportunityUseCase` refuses an application from anybody who has not completed a
background check:

> "This activity involves people who need extra protection, so a completed background check is
> required before you can apply."

Not a warning shown to the organiser. Not a badge on the application. The application cannot
be submitted.

A test proves both sides: an identity-verified member without a background check is refused
from the youth programme, and a background-checked member is accepted with the identical
request.

### The community floor

The seeded youth programme carries the strictest safeguard floor on the platform, and it is
worth reading as a template for what a real youth community should look like:

- contactable only by people in the organisation
- **minimum verification: background checked**
- cross-gender conversations only inside a group thread
- a moderator present in every conversation, not only cross-gender ones
- group context required
- video calls: nobody
- one-to-one meetings: nobody
- meetings in public places
- formal introductions forbidden in this space entirely

Its published rules include "Two adults present — no session runs with a single adult" and
"Report concerns immediately — anything that worries you goes to the safeguarding lead the
same day".

Because it is a **community** floor rather than an organisation floor, it applies inside the
programme's space and does not follow its volunteers into unrelated conversations. The
reasoning is set out in [`safeguards.md`](safeguards.md); the short version is that a
volunteer who helps with Saturday football must still be reachable about a house move.

---

## Grooming: detection and response

### Report categories

Both are **critical** severity and both start at **safety administrator** escalation, not in
the general queue:

| Category | Group |
| --- | --- |
| Child safety concern | Safety |
| Grooming behaviour | Safety |

A critical report does not wait for triage. The moment it is submitted the platform opens a
case, sets the escalation level, and marks the report triaged. A test confirms a grooming
report lands at safety-administrator escalation immediately.

Alongside them, `EscalationLevel.EXTERNAL_REFERRAL` is reserved for matters that may need
reporting to authorities, and `CaseOutcome.REFERRED_EXTERNALLY` records that it happened.

### The on-device signals

`ContentSignals` matches a small list of phrases associated with isolating somebody from the
people around them:

> "don't tell anyone", "our secret", "keep this between us", "delete this chat", "your parents
> don't need", "you're mature for", "how old are you really"

A match produces a **high-confidence** signal carrying the phrase that triggered it, with the
explanation that it is "a phrase associated with isolating someone from the people around
them".

Related signals in the same pass: attempts to move a conversation off the platform (WhatsApp,
Telegram, Snapchat, "add me on", "my number"), and text that looks like a phone number or
email address.

Two things must be understood about these:

- **They are advisory.** They order a queue. Nothing they produce restricts an account,
  removes content, or closes a case.
- **They are keyword matching, not classification.** A determined adult will avoid the
  phrases. The signals catch carelessness and volume, which is genuinely worth catching, and
  they are honest about being nothing more.

The rules run on the device that typed the message; nothing is transmitted to produce a flag.
Members are told exactly this before any of it runs — see [`privacy-model.md`](privacy-model.md).

### The structural defences matter more

Keyword matching is the weakest of the protections here. The ones that actually constrain
behaviour are structural:

- Adult-to-minor direct messaging does not exist as a code path.
- Every conversation carries a stated purpose, and drifting from it produces a visible
  reminder in the thread.
- A recipient can require a guardian, a third party, or a moderator in every cross-gender
  thread, and the requirement is applied at creation and cannot be removed by the person who
  opened the thread.
- An organisation or community can require the same of everyone in its space.
- Unsending a message hides it from participants but preserves the original for the safety
  team, so an adult cannot delete what they wrote before it is reported.
- Moving a conversation off the platform is itself a flagged signal, and one of the daily
  safety reminders tells members plainly: *"If someone asks you to continue a conversation on
  another app, that removes the safeguards you chose here. You are entitled to say no."*

---

## Before any youth functionality ships

Every item. Not most of them.

### Legal and organisational

- [ ] Legal advice obtained on operating a service accessible to minors in each jurisdiction:
      COPPA in the United States, the Age Appropriate Design Code in the United Kingdom, GDPR
      Article 8 in the EU, and the equivalents elsewhere.
- [ ] A named designated safeguarding lead with the relevant qualification and a deputy.
- [ ] A written child-protection policy, published, reviewed annually.
- [ ] A documented reporting route to child-protection authorities and to law enforcement in
      each jurisdiction, with a named owner and a tested contact.
- [ ] Mandatory-reporting obligations identified per jurisdiction and built into the
      moderation procedure rather than left to a moderator's memory.
- [ ] Insurance that covers work with minors.
- [ ] A data-protection impact assessment specifically covering minors.

### Verified guardian consent

- [ ] A guardian consent mechanism that verifies the guardian is who they say they are —
      identity verification, not an email tick-box.
- [ ] Consent is per-activity, not a blanket permission for the platform.
- [ ] The guardian can withdraw consent at any time, with immediate effect.
- [ ] The guardian has visibility of what the young person is doing on the platform, and the
      young person is told plainly that they do.
- [ ] The consent record is stored with a version and a timestamp, alongside the existing
      consent records.
- [ ] What happens at the young person's 18th birthday is designed: what carries over, what is
      deleted, and what they are asked to re-consent to.

### Verified organisations only

- [ ] A minor can only participate through a verified organisation. There is no route to a
      general-purpose youth account.
- [ ] The organisation has a designated safeguarding lead of its own, named on the record.
- [ ] The organisation's own safeguarding policy has been seen and is on file.
- [ ] The organisation carries insurance covering work with minors.
- [ ] Organisation verification for youth work is a distinct, higher check than ordinary
      organisation verification, and it expires.

### Communication

- [ ] **Adult-to-minor direct messaging remains impossible.** The existing refusal stays.
- [ ] All adult-to-minor communication happens in a supervised group thread with at least two
      verified adults present.
- [ ] The two-adult rule is enforced in code, not left to the organisation's rota.
- [ ] The young person's guardian can be a member of any thread the young person is in.
- [ ] Voice and video calls between an adult and a minor are not available at all.
- [ ] One-to-one meetings between an adult and a minor cannot be arranged through the
      platform.
- [ ] Message retention for threads involving minors is longer and is not subject to the
      ordinary archiving schedule.
- [ ] Unsend is disabled entirely in threads involving minors.

### Background checks

- [ ] Enhanced background checks — DBS in the United Kingdom, the equivalent elsewhere — are
      verified through a real provider before any adult is in contact with a minor.
- [ ] Checks are re-verified on a schedule and expire; a lapsed check removes access
      automatically rather than raising a task.
- [ ] The barred-list check is included where the jurisdiction provides one.
- [ ] Checks are per-jurisdiction, and the limits are stated honestly. The model already says
      it: "Checks look backwards, not forwards, and they do not cover every jurisdiction."
- [ ] Somebody whose check lapses loses access to youth activities the same day.
- [ ] A dedicated adult-to-minor contact policy exists, replacing what
      either wired into `SafeguardResolver` or removed, so that what the data says and what
      the code enforces are the same thing.

### Age assurance

- [ ] Self-declared age is replaced by something meaningful for accounts claiming to be under
      18, and for adults seeking access to youth activities.
- [ ] The under-13 case is decided and handled. In most jurisdictions the answer is that they
      cannot have an account at all.
- [ ] Age-assurance failure has a defined outcome, and it is not "let them through and review
      later".

### Moderation

- [ ] A safety team member trained in child protection is on the rota at all times that youth
      activity is possible.
- [ ] The immediate-triage target for child-safety and grooming reports is met in practice,
      measured, and reported.
- [ ] A young person can report without an adult's involvement, and the route is obvious to
      them.
- [ ] A guardian can report on the young person's behalf.
- [ ] Evidence in a child-safety case is preserved for the legally required period and is
      exempt from the ordinary deletion schedule — including when the reported account
      requests deletion.
- [ ] Referral to authorities is a documented procedure with a named owner, rehearsed rather
      than written down and filed.

### Product

- [ ] A separate youth interface that does not include general discovery, general messaging,
      or the introduction workflow.
- [ ] The formal introduction workflow is unavailable to and about minors, enforced in code as
      it already is.
- [ ] Location precision for a minor is coarser than the adult default, with no exact-address
      disclosure path at all.
- [ ] Profile images for minors are not visible outside their verified organisation.
- [ ] Independent penetration testing focused specifically on whether an adult account can
      reach a minor account by any route.

---

## Known gaps

Stated plainly, because a safeguarding document that reads as though everything is handled is
worse than none.

- **Age is self-declared and nothing checks it.** An adult can claim to be an adult, which is
  currently all the platform asks. Once minors exist, this is the first thing that must change.
- **Identity verification is not wired to a provider.** `IDENTITY_VERIFIED` currently means
  nothing has been checked at all.
- **Background checks are not wired to a provider.** `BACKGROUND_CHECKED` is a level in an
  enum. The enforcement around it is real and tested; the underlying check is not performed by
  anybody.
- **There is no adult-to-minor contact policy, only a prohibition.** `DenialReason
  .MINOR_ADULT_DIRECT_CONTACT` refuses the contact outright, which is right for an
  adults-only release but is not a model for supervised youth work. Where a background check
  is enforced today it is through the minimum verification level on a community floor and
  through the service-category rules — not through any dedicated minor-contact flag.
- **There is no two-adult rule in code.** The seeded youth community states it as a published
  rule; nothing enforces it.
- **There is no youth interface**, no guardian-consent mechanism, and no supervised group-only
  communication model. These are design work, not configuration.
- **Nothing in the moderation tooling is specialised for child-protection cases** beyond the
  escalation level and the category.
- **No malware scanning on uploads.** A placeholder exists in `.env.example` and is not wired
  up.

---

## Related

- [`safeguards.md`](safeguards.md) — the floor model, and why community floors are scoped
- [`moderation-guide.md`](moderation-guide.md) — triage, escalation, evidence, external
  referral
- [`privacy-model.md`](privacy-model.md) — what is collected, and what deletion does not remove
- [`wali-workflow.md`](wali-workflow.md) — the adults-only introduction process
- [`backend/docs/threat-model.md`](../backend/docs/threat-model.md) AP-4 — grooming a minor or
  a new Muslim, as an explicit attack path
