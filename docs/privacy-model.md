# Privacy model

*Written for two readers: an engineer, and a community safeguarding lead. Nothing here
requires you to read code.*

This describes what the software actually does. It is not a privacy notice — a published
notice is one of the things that must exist before a single real member joins, and it must be
consistent with this document.

---

## The position

The most sensitive information this platform holds is not passwords. It is the fact that a
particular family is short of food this week, the street address of a woman asking for a lift
to hospital, and the phone number of somebody's father.

Three commitments follow, and each is enforced rather than promised:

1. **The default is the least revealing form.** An approximate area rather than an address, a
   display name rather than a legal name, initials rather than a photograph.
2. **Moving from the least revealing form to a more revealing one is an act by the person it
   belongs to**, aimed at a named recipient, and it is recorded.
3. **Nobody reads a member's messages unless a report has been made or a flag has been
   raised.** Members are told this in those words before any automated checks run.

---

## What is collected, and why

| What | Why it is needed | Who can see it by default |
| --- | --- | --- |
| Display name | To address somebody | Anyone who can see the profile |
| Legal name (optional) | So an organiser can know who is turning up to a shift | Nobody — the default is display name only |
| Gender | Every safeguard about unrelated men and women depends on it | Anyone who can see the profile |
| Year of birth | The adults-only age gate, and nothing else | Nobody; only the derived adult/not-adult answer is used |
| Approximate location | To show opportunities near somebody | At the precision the member chose, defaulting to city |
| Exact location | Only where a request needs somebody to come to a door | Nobody until the member releases it, one named person at a time |
| Languages, skills, availability | The whole point of the platform: matching help to need | Anyone who can see the profile |
| Categories they can help with, or need help with | The same | Anyone who can see the profile |
| A short contribution statement | So a stranger can judge whether to ask | Anyone who can see the profile |
| Verification level | So a member can require it of people contacting them | Anyone who can see the profile, with a statement of what it does not mean |
| Trusted contacts, including a wali | The guardian-led introduction workflow, and supervised conversations | The owner only; a counterparty ever sees a redacted form |
| Conversations and their stated purpose | The service itself, and the record if something goes wrong | The participants, plus any oversight the recipient's safeguards required |
| Commitments and attendance | So an organiser knows who turned up, and a member has their own record | The member and the relevant organiser |
| Reports, cases, evidence | Safety | The safety team |
| Consent records | Proof of what somebody agreed to, and when | The member and the safety team |
| Audit entries | Accountability, particularly for staff | The safety team |

Note the year of birth rather than the full date. `Profile.dateOfBirthYear` is an integer, and
`isAdultIn(currentYear)` derives the only answer the system needs. A full date of birth is a
strong identifier and the platform has no use for one.

### What is deliberately not collected

- **No appearance data of any kind.** No height, no build, no complexion, no photograph
  requirement. The profile type has no field for any of it.
- **No follower graph, no likes, no reactions, no view counts.** A test enumerates a list of
  vanity terms and fails if a field on any publicly visible type contains one.
- **No behavioural profile.** The home digest filters by what a member said they can do and
  where they live. It does not learn from what they tap, and the code says so.
- **No analytics SDK, no crash reporter, no attribution library.** None is present today, and
  adding one is a privacy decision rather than a tooling decision — see the constraints in
  [`deployment.md`](deployment.md).
- **No advertising identifiers.**
- **No contact-list upload.**
- **No precise device location.** Coordinates are stored rounded to roughly a neighbourhood,
  for a distance filter.

---

## The doorstep rule

A place is stored at two precisions. The approximate part carries a label, a city, a region,
a country code and coordinates rounded to roughly a neighbourhood. The exact part carries a
street address and precise coordinates, and it is never returned in a list response.

A person who needs food this week should be able to say so without their neighbours seeing
it, without their address being public, and without their difficulty becoming content for
anyone to browse through.

For a help request, three things follow and all three are enforced:

- The address starts undisclosed and only becomes visible through an explicit decision.
- The requester can hide their identity from everyone except moderators, or hand the request
  to an organisation so that to everybody else it is the organisation's request.
- No response count, no view count and no support total is exposed unless the requester turns
  it on. Nobody's difficulty becomes a fundraising thermometer by accident.

### The one disclosure path

There is exactly one place in the codebase where a precise location becomes visible to
somebody else: `DiscloseExactLocationUseCase` in
`core/domain/src/main/kotlin/org/fisabilillah/core/domain/ParticipationUseCases.kt`.

It requires all of the following:

- **The requester must act.** The use case refuses anybody else: "Only the person who posted
  this can share its address."
- **It names one recipient.** The disclosure is to a specific member, added to a set. It is
  not a switch that opens the address to everyone.
- **It writes an audit entry** recording who released the address and to whom.
- **It notifies the recipient**, telling them the address is now visible to them and asking
  them to treat it carefully.

Everybody else — including other volunteers who responded to the same request — continues to
see the neighbourhood. Moderators can see the exact address for a safety review; they cannot
pass it on.

A test walks the whole thing: browse and see "Eastgate, Ashbourne" with no address and no
requester name; disclose to one volunteer; that volunteer sees "14 Eastgate Rise"; a different
volunteer still sees the neighbourhood; and an audit entry exists.

In the database the same rule is enforced independently. Exact address, coordinates and access
notes live in `service_request_private_details`, readable only by the requester, the
*accepted* helper, and moderators.

---

## Guardian contact details

Covered in full in [`wali-workflow.md`](wali-workflow.md). The summary:

- Contact details live in their own named type, `PrivateContactDetails`, so that a reviewer
  can search for it and find every place they are handled.
- Only `TrustedContact.redacted()` crosses the boundary to a counterparty, carrying a name, a
  relationship, a role, a preferred contact method, and a confirmation state.
- One use case can return it, it refuses non-participants, it refuses before the introduction
  has reached the guardian, and it writes an audit entry every time it succeeds.
- The database revokes SELECT on the contact columns from every application role, moderators
  included.

When an introduction is forwarded, the platform contacts the guardian. It does not hand out
their number.

---

## The service record is private

A member's own record of what they have done — commitments completed, hours given, people
helped, the categories they worked in — is private by default, and permanently private if
they want it that way. The type carries the reason in a field the interface displays
alongside the summary:

> "This summary is private to you. It is not shown to anyone else and it is not ranked
> against anyone."

The only alternative setting is to list selected completed projects on a profile, with no
numbers. There is no third option, and there is no aggregation into a public ranking.

Behind that sits a private behavioural record: commitments completed and cancelled late, no
shows, punctuality, endorsements, upheld and dismissed reports, active restrictions. It is
never returned to another member — not the numbers, not a derived score, not a percentile.
It exists so an organiser can be shown a plain-language summary when deciding whether to
accept somebody onto a sensitive activity, and so the safety team has something to reason
about.

What a member *does* see about somebody else is a small set of labels: identity verified,
background checked, completed five or twenty commitments, qualification confirmed,
organisation representative, reliable organiser, no unresolved safety restrictions, new
member. Each carries an explanation of what it does not prove. There is no method anywhere
that turns them into a number, and a test asserts that there is nothing to rank people by.

The organiser summary is shown only to that organiser, only for that decision, and is never
stored on the volunteer's profile.

---

## Consent

Consent is recorded per kind, with the document version, whether it was granted, when, and
whether it has since been withdrawn. Onboarding will not complete without the required ones.

| Consent | Required | What the member is told |
| --- | --- | --- |
| Terms of use | yes | The rules for using this platform. |
| Privacy policy | yes | What information we hold, why, and for how long. |
| Community guidelines | yes | How members are expected to treat one another here. |
| **Automated safety checks** | yes | See below — quoted in full. |
| Age declaration | yes | This platform is for adults. Declaring an age you are not is a violation of the terms. |
| Location | no | Your approximate area is used to show you nearby opportunities. Your exact location is never shared without a separate decision by you. |
| Notifications | no | Reminders about commitments you made and messages in your conversations. |

The automated-safety-processing consent is separate rather than buried in a terms document,
because telling people plainly what is scanned and why is the only version of this that
respects them. Its exact wording:

> "Messages are checked automatically for a narrow set of safety signals — sexual content,
> grooming patterns, scam patterns, and attempts to move a conversation off the platform. The
> checks flag threads for a human to look at. They never restrict an account on their own,
> and no person reads your messages unless a report is made or a flag is raised."

---

## What the automated checks actually do

`ContentSignals` in `core/policy/src/main/kotlin/org/fisabilillah/core/policy/ContentSignals.kt`.

The disclosure shown to members, verbatim from the constant that sits next to the code it
describes, so the two cannot drift apart:

> "Messages are checked automatically for a narrow set of safety signals: sexual content,
> patterns associated with grooming, scam patterns, attempts to move the conversation off the
> platform, and drift away from a conversation's stated purpose. These checks run on your
> device. They flag a conversation for a human to look at; they never restrict an account,
> remove content, or decide anything on their own. No one reads your messages unless a report
> is made or a flag is raised."

### What it is

Keyword and phrase matching, plus one regular expression for something that looks like a
phone number or an email address, plus a simple word-overlap check for whether a thread has
wandered from what it was opened for. That is the whole mechanism.

Each signal carries **the phrase that triggered it**. A moderator sees that a message matched
a specific term and can dismiss it immediately, rather than being handed a score they cannot
interrogate.

Cross-gender context raises the weight of a flirtation signal rather than creating one. The
same sentence between two brothers arranging a food delivery and between a man and a woman in
a tutoring thread does not carry the same risk, and pretending otherwise produces either
noise or blindness.

### What it is not

Four things, stated in the source: it is not a classifier, it is not a censor, it does not
act, and it does not run on anything a member has not been told about.

Every signal it produces is advisory. There is no code path from a signal to a restriction, a
removal, or a case closure. All of those require a person.

### Where it runs

On the device that typed the message. Nothing is sent anywhere to produce a flag.

There is an honest cost: the rules are visible to anyone who decompiles the app, so a
determined person can work out which phrases to avoid. The source acknowledges this and takes
the trade, because the alternative is transmitting every message to a server for scanning.

### Purpose drift

Where a thread has moved away from what it was opened for, the platform posts a **visible
reminder in the thread** rather than filing a report:

> "A reminder that this conversation was opened about [purpose]: '[the requested action]'. If
> you need to talk about something else, it is usually better to start a conversation for
> that instead."

Most drift is innocent — two people arranging a masjid clean-up end up talking about a job
opening — and the right response is a note, not a moderator. The reminder is rate-limited so
that a thread which has genuinely moved on does not get one after every message, which would
teach people to ignore it.

---

## Who can see what

| Reader | Sees |
| --- | --- |
| A signed-out visitor | The least of anyone |
| An ordinary member | Whatever the subject's safeguards admit them to: discoverability, name visibility, location precision, image visibility, each decided separately |
| Somebody in a shared organisation | Additionally, whatever a `MY_ORGANIZATIONS_ONLY` scope admits |
| An organiser of something the member committed to | Additionally, the real name if the member chose "real name only to organisers I commit to" |
| A conversation participant | The thread, its stated purpose, and the terms attached to it |
| An oversight participant | The same, and they are visibly labelled as oversight rather than present silently |
| The safety team | Everything needed for a case — but not guardian contact details, ever |
| The member themselves | Everything about themselves |

The decision is made in exactly one place, `VisibilityPolicy`, which builds a `VisibleProfile`
from the subject and the viewer. A screen that receives one cannot accidentally render a real
name or a precise location, because those fields are simply absent unless the policy put them
there.

---

## Retention

There is no implemented retention schedule. This is a gap and it is named as one in
[`deployment.md`](deployment.md): a retention schedule, implemented and matching what the
privacy notice says, is a prerequisite before real members join.

What the model already supports:

- **Conversations archive themselves** when the work is done, on the owner's schedule
  (defaulting to fourteen days after completion). Archiving hides the thread and stops new
  messages; it never destroys evidence needed for a report.
- **Soft deletion** is uniform: every auditable record carries a `deletedAt`, so removal from
  view and removal from storage are separable.
- **Introductions lapse** after 21 days without an answer.
- **Restrictions expire** on a set date unless they are permanent.
- **Device sessions** are visible to the member and individually revocable.

What has to be decided and then built:

- How long a completed conversation is kept before deletion rather than archiving.
- How long a closed moderation case and its evidence are kept.
- How long audit entries are kept. They are append-only, which makes retention a matter of
  bulk expiry rather than selective deletion, and that expiry has to be designed rather than
  improvised.
- How long a banned account's records are kept for evidential purposes.

---

## Export

A member is entitled to a copy of their own data. The model has the audit action for it
(`DATA_EXPORTED`) and the app has a route for an account-data screen. **The export itself is
not implemented.**

When it is, it must include: the profile, safeguards and their history, trusted contacts
*including* the private contact details the member entered, conversations and messages the
member is a party to, commitments, applications and enrolments, help requests, consent
records, notifications, and the member's own private impact record.

It must exclude anything that would reveal a third party: another member's contact details,
another member's safeguards, the identity of somebody who reported them, and any moderator
note about a third party.

---

## Deletion

A member can request deletion. The account status becomes `DELETION_REQUESTED`, which the
contact gate treats as inactive immediately: the account can no longer start or continue
conversations, and cannot be contacted.

### What deletion removes

The profile, the safeguards, the trusted contacts and their private details, the member's own
content — listings, requests, projects they own — and their private impact record.

### What deletion does not remove

**Evidence on an open safety case.** This is the one exception, and it needs to be stated
plainly rather than buried.

If a member is the subject of an open moderation case, or is a participant in a conversation
that is evidence in somebody else's open case, that material is retained until the case is
resolved. This includes preserved copies of messages they unsent.

The reason is direct: a platform where deleting your account destroys the evidence of what you
did to somebody is a platform that has made deletion a tool for abusers. Somebody who
harassed a member and then deleted their account would take the proof with them, and the
person who was harassed would have nothing.

Also retained, and for narrower reasons:

- **Audit entries.** These record what *the platform and its staff* did, not what the member
  did. They are append-only by design, including for platform administrators. Removing them
  selectively is not possible without removing the accountability property they exist to
  provide.
- **Moderation actions and their rationales**, for the same reason.
- **Consent records**, as proof of what was agreed and when.
- **The other side of a conversation.** A message another member received is part of their
  record too. The deleting member's identity is removed from what remains.
- **Aggregate figures already recorded** against a commitment, such as an organiser's
  confirmation that somebody attended.

None of this is retained indefinitely by design; it is retained for as long as the case or the
obligation lasts. The schedule that says how long is one of the things that must be written
before launch.

---

## Security properties worth knowing

- **Typed identifiers.** Every entity has its own identifier type, so a user id cannot be
  passed where an organisation id is expected. On a platform where a mix-up means showing one
  person's private records to another, this is a meaningful protection rather than a style
  preference.
- **Repositories are stores, not gatekeepers.** Authorisation is decided in the use case
  against the signed-in principal, and enforced again independently in the database by
  row-level security. A bug in one layer is caught by the other.
- **IP and device identifiers are stored hashed**, where they are stored at all.
- **Storage paths, not URLs.** Object paths are stored as plain text; public URLs are never
  stored. Bucket policies are configured separately and must mirror the row-level rules.
- **The service-role key bypasses everything.** It must never reach a client. See
  [`deployment.md`](deployment.md) and `backend/docs/threat-model.md` AP-13.

---

## Known gaps

Stated so that nobody mistakes the design for the implementation:

- **No retention schedule is implemented.**
- **No data export is implemented.**
- **Deletion is modelled but the erasure job does not exist.**
- **Identity verification is not wired to a provider.** `IDENTITY_VERIFIED` currently means
  nothing has been checked, and a badge that overstates what was checked is worse than no
  badge.
- **No malware scanning on upload.** A placeholder exists in `.env.example`.
- **No end-to-end encryption of message bodies.** This is a deliberate tension rather than an
  oversight: a thread that a guardian or a moderator can be a member of, and whose content can
  be preserved as evidence when somebody unsends it, is not a thread the platform can be
  unable to read. Anyone reconsidering this must reconcile it with
  [`moderation-guide.md`](moderation-guide.md) first.

---

## Related

- [`safeguards.md`](safeguards.md) — the settings that govern who sees what
- [`wali-workflow.md`](wali-workflow.md) — guardian contact details in full
- [`moderation-guide.md`](moderation-guide.md) — when a member's messages are read, and by whom
- [`child-safety.md`](child-safety.md) — the age gate
- [`backend/docs/rls-model.md`](../backend/docs/rls-model.md) — the database's enforcement
- [`backend/docs/threat-model.md`](../backend/docs/threat-model.md) — AP-1 wali contact,
  AP-2 home address
