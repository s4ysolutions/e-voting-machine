---
name: composition-root
description: Use when wiring adapters together, when deciding where to instantiate a concrete adapter (memory store, noble or JDK crypto, signing keys), when adding a use case or a port that needs a given, or when a module imports a concrete adapter class and it feels wrong
metadata:
  tags: [architecture, language-agnostic, framework-agnostic]
---

# Composition Root

**Scope:** language-agnostic rules; Scala 3 `given` syntax. The `given`/`using` mechanics are
owned by `scala3-context-di`.

## Overview

One place in each runnable product decides which concrete adapter implements each port and
assembles the dependency graph. Everything else depends on abstractions.

Products in this project:
- `products.demo`: the Scala.js app (memory adapters, noble crypto, Laminar).
- `products.harness`: a JVM test product for cross-feature scenarios (memory adapters, JDK crypto).

## Rules

1. **Only the composition root names concrete adapter types.** No other module imports
   `MemoryLedgerRepository`, `NobleCrypto` or `MemoryTransactionManager`.
2. **It contains no business logic.** It wires; it does not decide.
3. **Infrastructure choice is explicit and searchable**, through type aliases:

```scala
type TXM = MemoryTx      // the demo's only transaction context
```

## Shape

```scala
package s4y.evoting.demo

object Main:
  type TXM = MemoryTx

  // 1. capabilities and ports: the only place adapters are named
  given Crypto                          = NobleCrypto
  given issuerKeys: Keys[Party.Issuer]  = NobleKeys.generate[Party.Issuer]()
  given operatorKeys: Keys[Party.Operator] = NobleKeys.generate[Party.Operator]()
  given Signer[Party.Issuer]            = issuerKeys.signer
  given Verifier[Party.Issuer]          = issuerKeys.verifier
  given Signer[Party.Operator]          = operatorKeys.signer
  given Verifier[Party.Operator]        = operatorKeys.verifier
  given TransactionManager[TXM]         = MemoryTransactionManager()
  given CastingPorts[TXM]               = MemoryCastingPorts()
  given RevoteBallotIssuer[TXM]         = RevoteViaBallots[TXM]()   // cross-feature adapter, below

  // 2. use cases
  given UseCase[IssueBallotCommand]     = IssueBallotUseCase[TXM]()
  given UseCase[CastCommand]            = CastUseCase[TXM]()
  given UseCase[RevoteCommand]          = RevoteUseCase[TXM]()

  // 3. ui plumbing
  given ChangeFeed                      = ChangeFeed()

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), DemoPage())
```

Order: capabilities and ports, then use cases, then UI. Given aliases are lazy, so the order in
the file does not matter at runtime, but keeping this order keeps the graph readable and
acyclic.

## Cross-Feature Adapters

When a workflow in one feature needs another feature's capability (re-voting in `casting` must
issue a ballot from `ballots`), the needing feature declares a port, and the composition root
supplies a thin adapter that calls the other feature's use case:

```scala
// products.demo — allowed here and only here
final class RevoteViaBallots[TX <: Tx](using issue: UseCase[IssueRevoteBallotCommand])
    extends RevoteBallotIssuer[TX]:
  def issue(next: TokenId)(using TX): Either[InfraFailure, Signed[Party.Issuer, BallotToken]] =
    IssueRevoteBallotCommand(next).run.left.map(e => InfraFailure(s"re-vote ballot: $e"))
```

The other feature's error becomes `InfraFailure` at this boundary: from `casting`'s point of view,
a failing ballot issuer is an infrastructure failure, and it rolls back the surrounding
`atomically` block like any other `Left`.

The adapter translates, nothing more. If it starts deciding things, that logic belongs in a
feature.

## Wiring Checklist

- [ ] Each port has exactly one adapter chosen here
- [ ] Type aliases document the infrastructure choice
- [ ] No feature module imports another feature's infra module
- [ ] Cross-feature adapters only translate
- [ ] Nothing outside this module imports `Main.given`

## Common Mistakes

**A use case constructing its own store or keys.** It then owns the lifecycle and cannot be
tested with other adapters.

**Business logic in the root.** `if demoMode then … else …` belongs in a configuration value that
the root reads and passes on, not in a decision tree here.

**One composition root per feature.** One per product. Several per feature means feature
boundaries are leaking.
