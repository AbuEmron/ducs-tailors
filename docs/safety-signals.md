# Safety signals

What the on-device content checks produce, where it goes, and the several things it
deliberately does not do.

Until recently this document would have been very short. `SendMessageUseCase` computed a
list of signals, returned them in `SentMessage`, and every caller discarded them. The
detection was carefully written, thoroughly tested, and completely inert: somebody could
send the same message to thirty people and the platform's own checks would notice all
thirty times and remember none of them — which is precisely the situation the checks exist
for.

---

## 1. Three layers

| Layer | Where | Question it answers |
| --- | --- | --- |
| `ContentSignals` | `core/policy/` | Did this message contain anything worth noticing? |
| `RecordedSignal` + `message_safety_signals` | `core/model/`, migration 0018 | What has this person actually done, over time? |
| `SignalEscalationPolicy` | `core/policy/` | Is that now enough for a person to look at? |

The split matters. One message almost never supports a conclusion about somebody. "You have
beautiful eyes" once is a misjudgement; the same sentence to a third person in a week is a
search. Only a stored history can tell those apart, and the first layer on its own cannot.

---

## 2. What is stored

A row names the message, the conversation, the sender, the kind of signal, the confidence,
and the phrase that matched.

**The message body is not stored.** A moderator who needs the surrounding conversation opens
the case and reads it there, which goes through the ordinary message policies and leaves an
audit trail. A table holding a copy of every flagged private message would be a second,
quieter store of exactly the material this platform is most careful about, readable by
anybody who could read the signals table.

The explanation is the check's own words — *"The message contains the phrase
\"beautiful\"."* — so a moderator can see that `brother` matched a keyword and dismiss it,
rather than being handed a score they cannot interrogate.

---

## 3. When a case is opened

`SignalEscalationPolicy`, over a seven-day window, counting only signals that have not
already contributed to a case:

| Situation | Result |
| --- | --- |
| One high-confidence sexual-content, isolation or financial signal | Case immediately |
| Three or more medium-or-better signals of one kind | Case |
| The same kind of signal across three different conversations | Case |
| Low-confidence signals only, at any volume | Nothing |
| Everything already attached to a case | Nothing |

The three "act on first occurrence" kinds are there because the cost of being slow about
them is measured in harm to a person, and the cost of being wrong is that a moderator reads
a conversation and closes the case.

A case assembled by counting is filed one notch below the category's headline severity. It
still reaches a human; it does not jump the queue ahead of somebody who actually asked for
help.

---

## 4. What escalation does not do

**It never restricts an account.** The only output is a case in the queue. Every
consequence on this platform is applied by a named moderator through
`TakeModerationActionUseCase`, written to an append-only audit log, and appealable. An
automated system that could mute someone would be a system that mutes the wrong someone,
and the person it silenced would have nobody to argue with.

**It never tells the sender.** No notification, and the sender cannot read their own
signals — the RLS policy is moderator-only and there is an assertion for exactly that.
Feedback would turn the checks into a puzzle to be solved by rewording, and the people most
motivated to solve it are the ones the checks exist for. The consent record for automated
safety processing says plainly that this happens; that is the honest disclosure, made once
and up front, rather than as a hint at the moment of detection.

**It does not swallow the message.** The message is still delivered. Detection is not
moderation.

**One open automated case per person.** Without that, every further message from somebody
already in the queue opens another case about the same behaviour, and the effect on a
moderator's screen is that the loudest problem hides the other nineteen.

---

## 5. Isolation is not flirtation

Phrases like *"our secret"*, *"delete this chat"* and *"your parents don't need to know"*
used to be recorded as `POSSIBLE_FLIRTATION`. Under that label a grooming pattern arrived
in the safety queue looking like clumsy romantic interest — the wrong thing for a moderator
to read first, and the wrong category to route by.

They now have their own kind, `POSSIBLE_ISOLATION_ATTEMPT`, and file as `GROOMING`.
Isolating a person from their family is the shape of grooming and of coercive control, and
neither is flirtation.

---

## 6. Who can write, who can read

| | `anon` | member | the sender | moderator | service role |
| --- | --- | --- | --- | --- | --- |
| Read signals | ✗ | ✗ | ✗ | ✓ | ✓ |
| Insert directly | ✗ | ✗ | ✗ | ✗ | ✓ |
| Via `record_message_signals()` | ✗ | own message only | ✓ | own message only | direct insert |
| Update or delete | ✗ | ✗ | ✗ | ✗ | ✓ |

### A bug worth recording

The first draft of `app.record_message_signals()` guarded its ownership check with:

```sql
if v_sender <> app.current_user_id() and not app.is_privileged_caller() then
```

`app.is_privileged_caller()` reads the real `current_user`, and inside a `SECURITY DEFINER`
function that is the function's *owner*. So the second clause was true for every caller,
the check never fired, and any member could have recorded fabricated signals against
anybody else's message — planting evidence on another person's account.

The assertion *"a member cannot record signals against somebody else's message"* is what
caught it. The bypass is gone; the test is `auth.uid()`-only, because `auth.uid()` reads the
request's JWT and gives the same answer wherever it is called. A back-office process that
genuinely needs to write these rows runs as a role that bypasses RLS and inserts directly.

---

## 7. Tests

**Kotlin** — `SignalEscalationPolicyTest` (12) and `SafetySignalsReachTheQueueTest` (5):

- one affectionate message is not a case; low confidence never escalates alone
- three of a kind, or the same kind across three conversations, becomes one
- serious kinds do not wait; isolation files as grooming
- signals older than the window, and signals already attached to a case, are not counted
- the summary states the evidence and reaches no conclusion about the person
- the message is delivered, no restriction is applied, and the sender is not notified
- a second flagged message does not open a second case
- a clean message records nothing at all

**SQL** — section 20 of `rls_tests.sql` (10 assertions), covering the table above.

---

## 8. What is still missing

- **The client does not call `record_message_signals()` yet.** The Kotlin path stores
  signals in the in-memory repository; the server table and its function exist and are
  tested, and will be wired up with the rest of the content layer.
- **`ContentSignals.conversationVolumeSignal` has no caller.** The high-volume-of-new-
  conversations signal is written and untested in situ.
- **Nothing ages signals out.** A row stays until its message is deleted. A retention
  window — signals that never became a case expiring after some months — is the right
  thing and is not built.
- **No moderator view of the signals on a case.** `signalsForCase` exists in the repository
  and in SQL; the moderation case screen does not yet show them.
