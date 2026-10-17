---
name: usecase-airstream-bridge
description: Use when a Laminar UI needs the result of a UseCase[C] as a Signal or EventStream, when writing a state holder (VoterSession, LedgerView, AuditPanel state), when one panel must refresh after another panel's action, when deriving live checks such as the D7 tamper verification from a Var, or when a state holder starts growing business logic
metadata:
  tags: [scala3, scalajs, laminar, airstream, frontend]
---

# Use Case → Airstream Bridge

**Scope:** Scala.js, Laminar, Airstream, synchronous `UseCase[C]` (`usecase-command`).
Adapted from the ZIO original: with no effect runtime, there is no fiber launch, no
`Runtime[R]`, no `ExecutionContext` and no write-ordering queue. A use case returns an `Either`
immediately; the bridge only turns it into reactive state.

## The State Holder

A **state holder** is a ui-layer class that owns one piece of UI state, calls use cases, and
publishes a read-only `Signal`. It lives in the feature's `ui` module, in a `state/` package next
to the container that uses it (`ui-component-container-pattern`), receives its dependencies through
`using` (`scala3-context-di`), and is constructed by the composition root or a container
component.

```scala
package s4y.evoting.casting.ui.voter.state

enum VoterState:
  case NoBallot
  case HasBallot(ballot: Signed[Party.Issuer, BallotToken])
  case Voted(receipt: Receipt)
  case Failed(message: String)

final class VoterSession(using
    UseCase[IssueBallotCommand],
    UseCase[CastCommand]
)(using feed: ChangeFeed):
  private val _state = Var[VoterState](VoterState.NoBallot)
  val state: Signal[VoterState] = _state.signal

  def requestBallot(cmd: IssueBallotCommand): Unit =
    _state.set(cmd.run.fold(e => VoterState.Failed(Messages.of(e)), VoterState.HasBallot(_)))

  def castVote(cmd: CastCommand): Unit =
    cmd.run match
      case Right(receipt) =>
        _state.set(VoterState.Voted(receipt))
        feed.notifyChanged()                       // other panels re-read
      case Left(error) =>
        _state.set(VoterState.Failed(Messages.of(error)))
```

The error channel is folded into a state case **inside** the holder. Components never see an
`Either`.

## Three Shapes

### A: `Var` + direct call (default)

As above. A method per user action, `_state.set(...)` from the use case result. Use it for
anything the user does.

### B: read model derived from a `ChangeFeed`

A panel that shows shared data (the public ledger, the tally) re-reads whenever any action
changed it:

```scala
// core.ui
final class ChangeFeed:
  private val bus = EventBus[Unit]()
  val changes: EventStream[Unit] = bus.events
  def notifyChanged(): Unit = bus.writer.onNext(())

// casting.ui.state
final class LedgerView(using UseCase[LedgerQuery])(using feed: ChangeFeed):
  val entries: Signal[Seq[LedgerEntry]] =
    feed.changes.startWith(()).map(_ => LedgerQuery().run.infallible)
```

`infallible` (in `core.app`) unwraps an `Either[Nothing, A]`. One `ChangeFeed` given, created by
the composition root, is shared by every holder. Do not let holders call each other.

### C: live derivation from local input (the D7 tamper check)

When a value depends only on what the user is typing, derive it with pure functions:

```scala
final class TamperLine[P <: Party](original: Signed[P, ?])(using verifier: Verifier[P]):
  val text: Var[String] = Var(original.text.value)
  val valid: Signal[Boolean] = text.signal.map(t => verifier.verifyText(t, original.signature))
```

No use case is involved: verification is a pure capability. The signal turns red or green as the
text changes.

## Decision Table

| Shape | Use when |
|-------|----------|
| A: `Var` + direct call | A user action changes state |
| B: `ChangeFeed` read model | A panel shows data other panels change |
| C: derived from local `Var` | The value depends only on local input and pure functions |

## State Holder Checklist

- [ ] A `State` enum covering every case the UI shows, including failure
- [ ] `_state: Var[State]` private, `state: Signal[State]` public
- [ ] One method per user action; each calls `command.run` and folds the `Either`
- [ ] Calls `feed.notifyChanged()` after a successful change to shared data
- [ ] Every helper with no `Var`, `Signal` or `UseCase[C]` in its signature lives in a domain
      module, not in this file

## Common Mistakes

**A singleton `object` state holder.** It becomes global mutable state and cannot be reset or
tested in isolation. Use a class, constructed with `using` and rebuilt on demo reset.

**Calling a workflow or a repository from a holder.** The holder calls `command.run`. Anything
else bypasses the app layer.

**Business logic in a holder** (computing the tally, checking invariants). That is a domain
Operation; the holder calls a use case or a pure function from the domain.

**Holders updating each other's `Var`s.** Publish a change on the `ChangeFeed`; let each holder
re-read.

**Folding errors to `Unit` and dropping them.** Every `Left` becomes a visible state, otherwise a
"try to cheat" rejection would show nothing.
