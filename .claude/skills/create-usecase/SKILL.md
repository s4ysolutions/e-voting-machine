---
name: create-usecase
description: Use when asked to create, scaffold or add a new use case, command, user action or demo feature (for example close a voting, re-vote, audit the ledger) as a full vertical slice from domain to Laminar UI
metadata:
  tags: [architecture, scala3, scaffold]
---

# Create a Use Case: Full Vertical Slice

**Scope:** Scala 3 / Scala.js / Laminar / Mill, no effect library.

## Order

```
1. Domain VOs and errors   (feature.domain)              if new concepts are needed
2. Domain operation(s)     (feature.domain)              pure rules
3. Port(s)                 (feature.domain.workflows.ports) if new storage or capability
4. Workflow                (feature.domain.workflows)    if a port is touched
5. Command + use case      (feature.app.usecases)
6. Adapter(s)              (feature.infra.memory)        for each new port
7. Composition root        (products.demo, products.harness) one given per new piece
8. UI                      (feature.ui)                  state holder + container
9. Tests                   JVM: operation, workflow; scenario DSL for the flow
```

Cross-references: `domain-operations-and-workflows` (Operation vs Workflow, error and transaction
rules), `scala3-domain-patterns` (types), `usecase-command`, `scala3-tx-parameterized-repository`,
`scala3-context-di`, `composition-root`, `usecase-airstream-bridge`,
`ui-component-container-pattern`.

## Worked Example: "Close a voting"

### 1–2. Domain: typestate and the rule

```scala
// voting.domain — the typestate already exists (scala3-domain-patterns § 5)
enum CloseError derives CanEqual:
  case NotFound(key: VotingKey)
  case NotOpen(key: VotingKey)

object VotingOperations:
  def requireOpen(s: StoredVoting): Either[CloseError, Voting[Status.Open]] = s match
    case StoredVoting.Open(v) => Right(v)
    case other                => Left(CloseError.NotOpen(other.key))
```

### 3. Port

```scala
// voting.domain.workflows.ports
trait VotingRepository[TX <: Tx]:
  def find(key: VotingKey)(using TX): Option[StoredVoting]
  def save(v: StoredVoting)(using TX): Unit
```

### 4. Workflow (owns the atomic commit)

```scala
// voting.domain.workflows
object VotingWorkflows:
  def close[TX <: Tx](key: VotingKey)(using
      tm: TransactionManager[TX],
      repo: VotingRepository[TX]
  ): Either[CloseError | InfraFailure, Voting[Status.Closed]] =
    tm.atomically[CloseError, Voting[Status.Closed]]("close voting") {
      for
        stored <- repo.find(key).toRight(CloseError.NotFound(key))
        open   <- VotingOperations.requireOpen(stored)
        closed  = open.close                         // compiles only on Voting[Status.Open]
        _       = repo.save(StoredVoting.Closed(closed))
      yield closed
    }
```

The design's close barrier (F5: drain accepted requests before publishing the final head) is a
second step in the casting feature, not part of this workflow.

### 5. Command and use case

```scala
// voting.app.usecases
final case class CloseVotingCommand(key: VotingKey) extends UseCaseCommand:
  type E = CloseError | InfraFailure
  type A = Voting[Status.Closed]

final class CloseVotingUseCase[TX <: Tx](using TransactionManager[TX], VotingRepository[TX])
    extends UseCase[CloseVotingCommand]:
  def apply(c: CloseVotingCommand): Either[c.E, c.A] = VotingWorkflows.close(c.key)
```

### 6. Adapter

```scala
// voting.infra.memory
final class MemoryVotingRepository extends VotingRepository[MemoryTx]:
  private val cell = MemoryCell(Map.empty[VotingKey, StoredVoting])
  def find(key: VotingKey)(using MemoryTx): Option[StoredVoting] = cell.get.get(key)
  def save(v: StoredVoting)(using MemoryTx): Unit = cell.update(_.updated(v.key, v))
```

### 7. Composition root

```scala
// products.demo Main — and the same two lines in products.harness
given VotingRepository[TXM]           = MemoryVotingRepository()
given UseCase[CloseVotingCommand]     = CloseVotingUseCase[TXM]()
```

### 8. UI

```scala
// voting.ui.owner.state
final class OwnerSession(using UseCase[CloseVotingCommand], feed: ChangeFeed):
  private val _state = Var[OwnerState](OwnerState.Idle)
  val state: Signal[OwnerState] = _state.signal

  def close(key: VotingKey): Unit =
    CloseVotingCommand(key).run match
      case Right(_)    => _state.set(OwnerState.Closed(key)); feed.notifyChanged()
      case Left(error) => _state.set(OwnerState.Failed(Messages.of(error)))
```

`OwnerPanel` (the container) renders a "Close voting" button wired to `session.close(key)`.

### 9. Tests

```scala
// voting.domain.tests (JVM)
test("only an open voting can be closed"):
  assert(VotingOperations.requireOpen(StoredVoting.Planned(Fixtures.planned)).isLeft)

// products.harness tests (JVM) — the whole flow, through the scenario DSL
scenario {
  closeVoting("annual-2027")
  expectRejected(voter("alice").casts("budget" -> "yes"), "Voting is not open")   // F8 X7
}
```

## Decision Rules

- **Pure rule only?** Operation, no workflow; the use case may call the Operation directly.
- **Touches a port?** Workflow in `domain.workflows`; it owns `atomically`.
- **Needs another feature's capability?** Declare a port in this feature; the composition root
  adapts the other feature's use case (`composition-root` § "Cross-Feature Adapters").
- **New storage?** New port + memory adapter + a `given` in both products.

## Checklist

- [ ] Command has `E` and `A`; `E` is a domain enum, `InfraFailure` or a union; fields are VOs
- [ ] Port methods take `(using TX)`; the workflow names ports, never adapters
- [ ] The workflow opens `atomically`; the use case is a pass-through
- [ ] Adapters only read and write; rules live in Operations
- [ ] Composition root gains one `given` per new piece, in `products.demo` and `products.harness`
- [ ] UI calls `command.run` from a state holder and folds every `Left` into visible state
- [ ] JVM tests for the Operation and the workflow; a scenario test for the user-visible flow
- [ ] If it is a rejection path, it appears in the "try to cheat" panel (design doc F8)
