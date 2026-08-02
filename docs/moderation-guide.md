# Moderation guide

*This is a handbook for a person, not a specification for a program. It assumes you have been
given moderator or safety-administrator access and no engineering background whatsoever.*

---

## What you are for

Two commitments run through everything below.

**Automated checks never decide anything.** They change the order of your queue. Nothing the
software produces removes content, restricts an account, or closes a case on its own. Every
one of those outcomes requires you.

**You are accountable, permanently.** Every consequential action you take writes a record
that you cannot alter or remove — and neither can a senior moderator, a safety administrator,
or a platform administrator. This is not distrust of you personally. On a platform like this
the hardest abuse to defend against is not the harasser but the moderator who decides they
know best, and the only structural protection that actually works against that is a trail
nobody can clean up.

---

## Report categories

A member choosing what to report from is choosing from this list. It is deliberately granular:
a single "inappropriate" bucket would force you to re-derive what happened from free text, and
would make it impossible to see that eleven separate people have reported the same person for
the same specific behaviour.

### Critical — reaches a human immediately

| Category | Group | Starts at |
| --- | --- | --- |
| Child safety concern | Safety | **Safety administrator** |
| Grooming behaviour | Safety | **Safety administrator** |
| Threats | Safety | **External referral** |
| Extremism or violence | Safety | **External referral** |
| Illegal content | Safety | **External referral** |
| Sexual content | Conduct | Senior moderator |
| Coercion or manipulation | Safety | Senior moderator |
| Fraud or scam | Integrity | Senior moderator |
| Misuse of donations | Integrity | Senior moderator |

### High — within 24 hours

| Category | Group |
| --- | --- |
| Flirtation or unwanted pursuit | Conduct |
| Going around the guardian process | Conduct |
| Harassment | Conduct |
| Sectarian abuse | Conduct |
| Impersonation | Integrity |
| Professional misconduct | Integrity |
| Unsafe volunteering | Safety |
| Privacy violation | Safety |

### Medium — within 3 days

| Category | Group |
| --- | --- |
| Presenting unqualified religious claims as authoritative | Integrity |

### Low — routine

| Category | Group |
| --- | --- |
| Spam | Integrity |

Two categories deserve a note.

**Going around the guardian process.** This is high severity, not a technicality. It covers a
sender who was declined or ignored and then approached the recipient another way: through an
ordinary service conversation, through a mutual acquaintance, through a family member, or off
the platform. The person accepted a written conduct rule saying they would not do this, and
the acceptance is timestamped against their account.

**Presenting unqualified religious claims as authoritative.** This is medium and belongs to
integrity rather than conduct, on purpose. It is not about which opinion someone holds. It is
about a person without the relevant standing presenting themselves as having it — most often
to somebody new to Islam, which is the population this most damages. The platform takes no
position on schools of thought. It insists only that a student knows what they are walking
into.

---

## Triage targets

| Severity | Target |
| --- | --- |
| Critical | **Immediately** |
| High | Within 24 hours |
| Medium | Within 3 days |
| Low | Routine |

"Immediately" is a staffing commitment, not a constant in a file. If the rota cannot meet it,
the honest response is to reduce the number of members joining, not to quietly redefine the
target.

A critical report does not wait for triage at all. The moment it is submitted, the platform
opens a case, sets its escalation level, and marks the report as triaged. Your queue will
already have it.

Your queue is ordered by severity, then by escalation level, then oldest first. It is not
ordered by how easy an item is to close.

---

## Escalation levels

| Level | Who handles it |
| --- | --- |
| Standard | Any moderator |
| Senior moderator | A moderator with more experience, per your team's own rota |
| Safety administrator | A safety administrator |
| External referral | A safety administrator, plus the named person who owns the relationship with law enforcement or child-protection authorities |

Child-safety and grooming matters **do not begin in the general queue.** They start at safety
administrator.

Threats, extremism or violence, and illegal content start at external referral — meaning the
question is not only what to do on the platform but whether an authority outside it needs to
be told.

---

## What you may do, and what needs a safety administrator

| Action | Reversible | Ordinary moderator |
| --- | --- | --- |
| Add a note to a case | yes | yes |
| Issue a warning | yes | yes |
| Remove content | yes | yes |
| Freeze a conversation | yes | yes |
| Restrict a feature for a period | yes | yes |
| Lift a restriction | yes | yes |
| Decide an appeal | yes | yes |
| **Suspend an account** | yes | **no — safety administrator** |
| **Ban an account permanently** | **no** | **no — safety administrator** |
| **Revoke verification** | yes | **no — safety administrator** |
| **Suspend an organisation** | yes | **no — safety administrator** |
| **Suspend a campaign** | yes | **no — safety administrator** |
| **Refer externally** | **no** | **no — safety administrator** |

The dividing line is not arbitrary. A permanent ban and a revocation of verification are not
routine moderation: they require a safety administrator, which means at least two people have
been involved before anyone loses their account for good.

If you attempt an action above your level the platform refuses it and tells you plainly:
"This action requires a safety administrator." That is not a bug and it is not something to
work around by asking an administrator to click the button without reading the case.

**Every action requires a written rationale.** The record will not save without one. Write it
for a stranger reading it in two years who has none of your context: what happened, what you
concluded, and why this response rather than a lighter one.

### Restricting a capability

Restrictions are per-capability and time-bounded. You can take away starting conversations,
sending messages, creating listings, creating help requests, applying to opportunities,
submitting formal introductions, joining communities, uploading files, submitting reports,
receiving donations — or all activity.

Prefer the narrowest restriction that addresses the behaviour. Somebody spamming listings
does not need their messages taken away.

Restricting *submitting reports* deserves particular care. It silences a person's ability to
raise a safety concern, and if you get it wrong you have handed a harasser exactly what they
wanted. Do not do it on a hunch; do it on a documented pattern that a second person has
looked at.

---

## Appeals

A member can appeal any decision about their own account.

**The person who made the original decision cannot hear the appeal.** The platform enforces
this: if you took any action on the case, you are refused with "This appeal must be reviewed
by someone who was not involved in the original decision." An appeals process staffed by the
person being appealed against is worse than none at all, because it produces a record saying
the decision was reviewed.

A member also cannot review their own appeal, and only the safety team can review appeals at
all.

Outcomes are: upheld (the restriction is lifted), partly upheld, rejected, or withdrawn by
the member. Upholding an appeal automatically lifts the restrictions that came from that
case; you do not have to remember to.

Every appeal decision appends its own moderation action and its own audit entry, so the
appeal is as much a part of the permanent record as the original decision.

The enforcement notice a member receives already tells them they can appeal, and tells them
it will be heard by someone who was not involved. Do not undercut that in your own wording.

---

## The audit trail

Every action you take writes exactly one audit entry. The mapping from action to entry lives
in one place in the code, so no new action type can be added without somebody deciding how it
is logged.

| What you did | What is recorded |
| --- | --- |
| Suspended or banned an account | Account status changed |
| Revoked verification | Verification changed |
| Restricted a feature | Restriction imposed |
| Lifted a restriction | Restriction lifted |
| Decided an appeal | Appeal decided |
| Anything else | Moderation action |

Each entry records who acted, the role they held at the time, what they acted on, the
rationale in full, and when.

**There is no update path and no delete path for any of this.** Not in the interfaces the
application uses, not in the database's row-level security policies, and not for platform
administrators. A test asserts it structurally rather than behaviourally: the repository
offers `append` and nothing beginning with `update` or `delete`. In the database, the same
tables carry no UPDATE or DELETE policy and the privilege itself is revoked.

The audit entry is written **before** the state change is applied, so an action that fails
halfway still leaves a trace of having been attempted.

Refused attempts are logged too. When someone tries to open a conversation and the platform
refuses them, an audit entry records the precise reason even though nothing was created and
the recipient was never told. This is how a pattern of refused approaches to the same person
becomes visible to you.

---

## Evidence

Evidence is captured **at the moment of reporting**, not fetched when you get round to
looking. By the time you open a case the message may have been unsent, the listing taken
down, and the profile rewritten. A report with nothing behind it protects nobody.

When a member reports a conversation or a message, the platform automatically attaches:

- a snapshot of the conversation, including its stated purpose and the requirements that were
  in force
- a reference to every preserved copy of an unsent message in that thread

Evidence is write-once. It is never edited and never deleted, only added to.

### How unsend works

A sender can unsend their own message within **five minutes**. Participants then see "This
message was unsent by the sender."

**The original is preserved for you.** In the same operation that hides the body from
participants, the platform writes the original text to a moderator-only record. Neither half
is optional. An unsend that destroyed the evidence would hand every harasser a delete button
for their own abuse.

The window is short on purpose: unsend exists so somebody can retract a message sent in
haste, not so that abuse can be deleted before it is reported. After five minutes the message
stands.

When you look at a reported thread, you see the preserved originals. Participants do not.

### Freezing a conversation

Freezing stops new messages from everybody in the thread, including the person who reported
it. Both parties see "This conversation is on hold while the safety team reviews it."

History is preserved intact. Freezing is not deletion, and it is the correct action when you
need the exchange to stop while you read it.

---

## Reporting used as a weapon

Reporting is a safety tool, and like every safety tool it can be turned into a weapon. The
platform assesses this explicitly so that a pattern of retaliatory reports is itself visible
and actionable, rather than silently costing an innocent person their account.

You will see one of three flags alongside a report:

| Flag | What triggered it |
| --- | --- |
| **Normal** | Nothing unusual, or fewer than three recent reports |
| **Needs review** | Five or more reports about a *single* member, none upheld — or six or more reports of which at least half were dismissed |
| **Likely abusive** | This member has already had two or more reports formally assessed as retaliatory or knowingly false |

Read the wording of the first "needs review" note carefully, because it is doing real work:

> "Repeated reports about a single member, none of which were upheld. Check whether this is
> persistent harassment or a genuine pattern that earlier reviews missed."

Both readings are live. A person filing five reports about one member may be harassing them.
They may equally be a victim whose first four reports were wrongly dismissed. Getting this
wrong in either direction hurts somebody: too eager and a genuine victim is disbelieved, too
cautious and a harasser silences the person they are harassing.

**The flag is never an automatic sanction.** It is a prompt for you to look.

When you conclude a review, record an assessment on the report itself:

| Assessment | Meaning |
| --- | --- |
| Made in good faith | Whether or not it was upheld |
| Mistaken but sincere | Wrong, but honestly meant |
| Retaliatory | Filed to punish somebody, typically after they reported first |
| Knowingly false | Fabricated |

Only the last two count towards the "likely abusive" threshold. **Use them sparingly and only
on evidence.** A report that turned out to be wrong is not retaliatory. Marking a sincere
member as retaliatory is itself a serious act, and it is permanently on the record — theirs
and yours.

Where action against a reporter is warranted, the case outcome "Action taken against the
reporter" exists for exactly that, and it is visible as such.

---

## What moderators must not do

This list is not exhaustive and is not a substitute for judgement. Every item is something
the system either prevents outright or records permanently.

**Do not read messages without a reason.** Nobody reads a member's messages unless a report
has been made or a flag has been raised. This is what members are told, verbatim, before any
automated checks run. Browsing conversations out of curiosity breaks that promise, and every
access leaves a record.

**Do not look up somebody you know.** Not a relative, not a member of your masjid, not
somebody who was rude to you last week. If a case involves anyone you have a relationship
with, hand it to a colleague and say why. Your identity, your role at the time, and what you
touched are all recorded.

**Do not seek a guardian's contact details.** You cannot get them: the database revokes the
privilege from every application role, moderators explicitly included. Attempting it through
another route is a serious disciplinary matter, and every legitimate disclosure is written to
an append-only ledger, so an illegitimate one stands out.

**Do not disclose a member's exact address.** Only the requester can release it, to a person
they name, one at a time. You can see it for a safety review; you cannot pass it on.

**Do not act on a case you are a party to.** Not as the reporter, not as the reported, not as
the person who took the original decision when the appeal arrives.

**Do not use verification, restriction, or removal to settle a disagreement about religion.**
The platform takes no position on schools of thought. Sectarian abuse is a reportable
category; holding a different view is not.

**Do not tell a sender why they were refused.** Refusal messages are deliberately vague about
a recipient's settings. If a sender contacts you asking why they could not reach somebody,
the answer is that the member is not accepting contact — not which setting stopped them.

**Do not act without writing down why.** The system will not let you, but the deeper point is
that a rationale you would be embarrassed to have read back to you is a rationale that should
change your decision.

**Do not delete or alter a record.** You cannot, and neither can anybody else. If a record is
wrong, append a correction.

**Do not warn a member that they are under investigation** unless the case calls for it. In a
grooming or coercion case, tipping somebody off gives them time to destroy what evidence is
still within their control.

---

## How misuse is detected

Assume this is checked, because it is checkable.

- **Every action you take names you** and the role you held at that moment, permanently.
- **Every access to guardian contact details is written to an append-only ledger**, and
  should be near zero.
- **Every exact-address disclosure is recorded** with who released it and to whom.
- **Refused conversation attempts are logged**, so an account that is repeatedly refused —
  including a staff account — is visible.
- **The audit trail can be read by actor**, so "everything this moderator did" is one query.
- **You cannot hear an appeal against your own decision**, so a wrong decision reaches a
  second pair of eyes by construction.
- **Permanent bans and verification revocations need a safety administrator**, so at least
  two people are involved in the irreversible ones.
- **A moderator who is a member of a conversation is visibly an oversight participant** —
  their presence is announced in the thread, not silent.

The threat model treats insider abuse by a moderator as its own attack path
(`backend/docs/threat-model.md` AP-8), and the mitigations there are structural rather than
procedural, precisely because procedure alone does not survive a determined insider.

If you believe a colleague has misused their access, raise it with a safety administrator who
is not involved. If it is a safety administrator, raise it with the named data controller.

---

## Working a case

A rough order that fits most cases.

1. **Read the report and its category.** The category tells you the severity, the triage
   target, and where the case starts.
2. **Read the preserved evidence before the live thread.** The snapshot is what existed when
   the reporter saw it.
3. **Check the reporter's pattern flag.** Not to discount them — to know whether you are also
   looking at a second problem.
4. **Check whether the accused account has a history.** Upheld reports, active restrictions,
   prior boundary violations.
5. **Decide the narrowest action that addresses the behaviour**, and check whether it needs a
   safety administrator.
6. **Write the rationale for a stranger in two years.**
7. **Record the abuse assessment on the report** — good faith, mistaken but sincere,
   retaliatory, or knowingly false.
8. **Close the case with an outcome**, and let the member be notified. The notice tells them
   what was done, why, until when, and that they may appeal.

---

## Related

- [`safeguards.md`](safeguards.md) — what members chose, and what "circumvention" means
- [`wali-workflow.md`](wali-workflow.md) — the introduction process and how it is bypassed
- [`child-safety.md`](child-safety.md) — the adults-only position and grooming indicators
- [`privacy-model.md`](privacy-model.md) — what deletion does not remove, and why
- [`backend/docs/threat-model.md`](../backend/docs/threat-model.md) — AP-8 insider abuse,
  AP-9 destroying evidence via unsend, AP-10 retaliation against a reporter

---

## Appeals

Every restriction is appealable, and since the appeal screens exist that is now true in the
product rather than only in this document.

A restricted member finds their restrictions in the safety centre under **Restrictions on
your account** — reachable at any time, not only from the notification that announced the
restriction. The reason you recorded is shown to them **verbatim**. Write it accordingly:
a member told only that they are "restricted from starting conversations" cannot form an
argument, and the appeal that follows will be a guess at what they are accused of, which
wastes your time as much as theirs.

Appeals arrive in **Appeals waiting to be read**, linked from the top of the moderation
queue. Two rules the software enforces rather than trusting you to remember:

- You cannot review an appeal against a decision you took. The queue shows you the appeal
  and says why the buttons are absent, so you know somebody else has to pick it up.
- Your reasons are shown to the member in full. Write them as though they are reading
  them, because they are.

Upholding an appeal lifts the restrictions attached to that case automatically, notifies
the member, and writes an `APPEAL_DECIDED` entry to the append-only audit log. Rejecting it
does everything except the lifting.

## Cases nobody reported

Some cases in the queue have no reporter. Those come from the automated content checks —
see [`safety-signals.md`](safety-signals.md) — and they read differently from a case
raised by a person:

- Nothing has been done to the account. The checks never restrict.
- The member has not been told. Do not assume they know they are being looked at.
- The summary states what was observed and nothing more. It is written not to editorialise,
  so the absence of a conclusion is not an omission — it is the point. Read the
  conversation before deciding.
