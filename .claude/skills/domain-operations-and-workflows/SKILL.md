---
name: domain-operations-and-workflows
description: Use when deciding whether domain logic is pure (Operation) or needs a port (Workflow), when it needs a fake or stub to test, when a workflow's using clause names an adapter type instead of a port, when choosing error types (enum, union, InfraFailure), or when deciding who opens atomically { … }
metadata:
  tags: [architecture, domain-modeling, scala3]
---

# Domain Operations and Workflows

**Scope:** Scala 3, no effect library. Adapted from the ZIO original: the "effect" of a
Workflow is the port it receives through `using`, not a `ZIO` return type.

## Overview

"Domain service" is retired. It covered pure rules and port-touching logic alike. Split it:

- **Operation.** Pure. Inputs are values, the output is a value or a domain error
  (`Either[E, A]`). It may take *pure capabilities* such as `Crypto` or `Signer[P]` through
  `using`: deterministic functions with no state.
- **Workflow.** Needs at least one **port** (a repository, `TransactionManager`, another
  feature's capability) through `using`, and composes Operations around it.

The module split enforces this: port traits live in `feature.domain.workflows`, which
`feature.domain` cannot see (`layers-to-modules`).

## Naming

| Object | Bucket | Signal |
|--------|--------|--------|
| `LedgerOperations`, `TallyOperations` | Operation | no port in any signature |
| `CastWorkflows`, `RevoteWorkflows` | Workflow | a port in a `using` clause |

`grep Workflows` finds every piece of port-touching domain logic.

## Core Rules

**An Operation's dependencies are pure values.** Policies, lookup tables and pure capabilities
arrive as parameters: explicit, or `using` when they are ambient (see `scala3-context-di`).
Never a repository, never a mutable cell.

```scala
object CastOperations:
  // pure: validates a ballot against the voting definition
  def checkChoices(voting: VotingDefinition, choices: Choices): Either[CastError, Choices] = ...

  // pure capability through using: Verifier is a deterministic function
  def checkBallot(ballot: Signed[Party.Issuer, BallotToken])
      (using verifier: Verifier[Party.Issuer]): Either[CastError, BallotToken] =
    Either.cond(verifier.verify(ballot), ballot.value, CastError.InvalidIssuerSignature)
```

**A Workflow names ports, never adapters.** Its `using` clause lists port traits from
`domain.workflows.ports` (or `core.tx`). Concrete adapters are supplied only by the composition
root.

```scala
object CastWorkflows:
  def cast[TX <: Tx](ballot: Signed[Party.Issuer, BallotToken], castId: CastId, choices: Choices)(
      using tm: TransactionManager[TX],
            ports: CastingPorts[TX],
            verifier: Verifier[Party.Issuer]
  ): Either[CastError | InfraFailure, Receipt] =
    tm.atomically[CastError, Receipt]("cast") {
      for
        token   <- CastOperations.checkBallot(ballot)
        _       <- ports.spent.markSpent(token.id)             // Left(TokenAlreadySpent) if reused
        entries  = ports.ledger.appendCast(castId, token.id, choices)  // cannot fail with a domain error
      yield Receipt(castId, entries)
    }
```

Give `atomically` its type arguments when the body mixes error types; Scala 3 then checks each
step against `CastError` instead of inferring a least upper bound.

**Errors are typed.** `E` is a sealed domain enum, `InfraFailure` (from `core.domain`), or a union
of both (`CastError | InfraFailure`). A raw `Throwable` never crosses a port: adapters catch and
wrap. Port methods return `Either[E, A]` (or a plain `A` when they cannot fail).

**No effect library.** Everything is synchronous. In-memory adapters and noble crypto are
synchronous, so `Either` is enough. If a real backend ever needs async ports, that change is
confined to port signatures and workflows. Do not add `Future` or an effect type "just in case".

**Testing litmus.** If testing it needs a fake port, it is a Workflow. An Operation is tested
with literal inputs and, where it takes a pure capability, the real one (JDK crypto on the JVM).
A "pure" helper that needs a fake to test has a port smuggled into it: move it.

## Transactions

`atomically` is an **atomicity boundary**: everything in the body commits together or not at all
(design doc § "Consistency and retries"). A decision that must be consistent with a following
write belongs inside the same `atomically` block; moving the read outside opens a
time-of-check/time-of-use gap.

```scala
trait TransactionManager[TX <: Tx]:
  def atomically[E, A](label: String)(body: TX ?=> Either[E, A]): Either[E | InfraFailure, A]
```

A `Left` from the body rolls back every write made in it. `InfraFailure` comes from the manager
itself. The body is a **context function** (`TX ?=> …`), so repository calls inside it receive
the transaction implicitly (`scala3-tx-parameterized-repository`).

**Ownership lives in the Workflow.** Only the workflow knows whether it needs zero, one or several
atomic units. The use case's `apply` is a pass-through that builds parameters and calls the
workflow. Exception: a use case with no business logic at all (one repository call, nothing to
derive) may call `atomically` directly. The moment there is a second call, an Operation over the
result, or a domain error to raise, extract a workflow.

## Choosing the Workflow Shape

**Shape A: function in an object, ports through `using` (default).** Every call site needs the
givens in scope, which in practice means the use case that holds them.

**Shape B: class taking its ports through a `using` constructor.** Use when a workflow is called
from several use cases and its port list keeps growing: callers then depend on the one class
instead of every port.

```scala
final class RevoteWorkflows[TX <: Tx](using TransactionManager[TX], CastingPorts[TX], RevoteBallotIssuer[TX]):
  def revote(secret: BallotSecret, next: TokenId): Either[RevoteError | InfraFailure, Signed[Party.Issuer, BallotToken]] = ...
```

| Situation | Shape |
|-----------|-------|
| One caller, stable ports | A |
| Several callers, or ports likely to grow | B |

Decide before writing; switching later touches every caller.

## Checklist

- [ ] No port trait and no mutable state in anything called an Operation
- [ ] Pure capabilities (`Crypto`, `Signer[P]`, `Verifier[P]`) are the only `using` deps an Operation has
- [ ] Every Workflow's `using` clause names ports, never adapters
- [ ] Every error is a sealed domain enum, `InfraFailure` or a union of them; no `Throwable`
- [ ] Each atomic unit is one `atomically` call, opened by the workflow
- [ ] No `Future`, no effect library
- [ ] Nothing named "domain service" remains

## Common Mistakes

**A port "just for now" in an Operation.** Passing `LedgerRepository` to `TallyOperations` so it
can read entries. Pass the entries instead; reading them is the Workflow's job.

**Wrapping a deterministic library in a port.** Ed25519 and SHA-256 will never become network
calls, so they are pure capabilities (`core.crypto`), not ports. A port is for something that is
a stand-in for future I/O or holds mutable state.

**Decision logic buried in an adapter.** The in-memory spent-token store should only record and
look up. "Is this token already spent, and what error results" is a domain decision: keep the
rule in an Operation and let the adapter be a thin shell over its cell.

**A workflow re-reading through a port what its caller already has.** Derive it with an Operation
over the value you hold.

**Throwing instead of returning `Left`.** An exception escapes `atomically` as an
`InfraFailure` and hides a domain error. Domain failures are `Left` values.
