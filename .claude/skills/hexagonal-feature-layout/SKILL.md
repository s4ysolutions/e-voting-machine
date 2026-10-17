---
name: hexagonal-feature-layout
description: Use when structuring a bounded context (voting, voters, ballots, casting, audit), when deciding where a class or function belongs — domain, app, infra or ui — including where a pure helper used by the Laminar UI goes, or when reviewing for layer violations such as Laminar or Scala.js imports in domain code or adapter types in use cases
metadata:
  tags: [architecture, language-agnostic, framework-agnostic]
---

# Hexagonal Feature Layout

**Scope:** language-agnostic rules, Scala 3 / Scala.js examples. Adapted from
`s4ysolutions/hexagonal-scala-zio-best-practice`; there is no effect library here.

## Overview

Each bounded context has four layers. Dependencies point inward only:
ui → app → domain ← infra. The domain never knows about any adapter.

In this project the whole system runs in one browser tab, but the parties of the design
(Voting Registry, Voters Registry, Ballot Issuer, Cast Service, Auditor) are still separate
bounded contexts. Keeping them apart is what lets a real backend replace one of them later.

## Layer Map

The domain is split into two build modules (see `layers-to-modules` for the decision,
`module-separation` for the dependency rules, `mill-module-layout` for the syntax):

```
domain/                  ← module: feature.domain (pure, platform-neutral)
  vo/                    Value objects with internal validation, domain errors
  operations/            Pure domain logic: no ports, no I/O, no mutable state
domain/workflows/        ← module: feature.domain.workflows (needs ports, still platform-neutral)
  ports/                 Driven port traits + parameter objects grouping them
  workflows/             Domain logic that needs a port, received through `using`
app/
  usecases/              Commands + UseCase[C] implementations; build params, call one workflow
infra/
  memory/                Driven adapter: in-memory storage (the demo's only storage)
  noble/                 Driven adapter: crypto through the @noble/* npm packages (JS only)
  jdk/                   Driven adapter: JDK crypto (JVM tests, future backend)
ui/
  laminar/               Driving adapter: Laminar components + state holders
```

`ui` is this project's name for the presentation layer.

## Dependency Rules

Logical view only. The authoritative module-by-module lists are owned by `module-separation`.

| Layer (module) | May import | Must NOT import |
|----------------|-----------|-----------------|
| `domain` | other domain types, `core.domain`, `core.crypto` (capability traits only) | app, infra, ui, `scala.scalajs.*`, Laminar, any npm facade |
| `domain.workflows` | `domain`, `core.tx`, port traits | app, infra, ui, concrete adapter types |
| `app` | `domain.workflows`, `domain`, `core.app` | infra, ui |
| `infra` | `domain.workflows` (implements ports), matching `core.infra.*` | app, ui, other features' infra |
| `ui` | `app` (as `UseCase[C]`), `domain` | infra, `domain.workflows` |
| composition root | everything | nothing: it exists to wire, not to contain logic |

Concrete adapters are named **only** in the composition root (rule owned by `composition-root`).
A workflow names a port trait in its `using` clause, never the adapter that implements it.

## What Goes Where

| Thing | Layer | Reason |
|-------|-------|--------|
| Field validation (`SubjectKey.from`) | domain/vo | The VO knows its own invariants |
| Tally, invariants I1–I7 | domain/operations | Pure functions over public data |
| Atomic commit boundary (`atomically { … }`) | domain.workflows | Only the workflow knows how many atomic units it needs (see `domain-operations-and-workflows`) |
| Readable signed text (D7) for a VO | domain (`Canonical[T]` given) | It *is* the signed message, so it is domain data, not styling |
| Colours, badges, "turns red" | ui | Rendering of the signed text |
| Mapping a domain error to a message | ui | Presentation concern |
| npm facades (`@noble/ed25519`) | infra/noble | Platform-specific library |
| Parameter object grouping driven ports | domain.workflows | References port traits |

## Parameter Objects for Driven Ports

When a workflow needs several ports that always travel together, group them in one parameter
object instead of a long `using` list (a standard refactoring; Fowler, *Refactoring*):

```scala
// casting.domain.workflows.ports — here because it references port traits
final case class CastingPorts[TX <: Tx](
    ledger: LedgerRepository[TX],
    spent: SpentTokens[TX],
    cancelled: CancelledCasts[TX]
)
```

It is not a VO: it holds port references, not domain data. See `scala3-context-di` for how to
receive it as a `given`.

## Adapter Types in Public Signatures

**A concrete adapter type may appear only in the composition root.** Everywhere else, every
type in a public signature (constructor params, `using` params, return types, public vals) must
be declared in `domain`, `domain.workflows` or a `core` capability module.

```scala
// FORBIDDEN — adapter type as a dependency
final class CastUseCase(using ledger: MemoryLedgerRepository)

// ALLOWED — port trait; the composition root supplies the adapter as a given
final class CastUseCase[TX <: Tx](using ledger: LedgerRepository[TX])
```

**Check:** resolve the declaring package of every type in a public signature. `*.infra.*` is
legal only in `products.demo`.

## Common Mistakes

**Transport or library errors in the domain.** `NobleError`, `JsException` and `DomException`
are infra details. Domain errors express intent (`TokenAlreadySpent`, `InvalidIssuerSignature`).
Adapters wrap library failures in `InfraFailure` (from `core.domain`) before returning.

**A bespoke error enum that every caller collapses into `InfraFailure` anyway.** If nothing
branches on the cases, delete the enum and return `InfraFailure`.

**Port result types as free-floating files in `ports/`.** A type describing what one port
returns belongs in that port's companion (`SpentTokens.Spent`), not as a sibling file.

**Laminar `Var` in a domain or app class.** Reactive state is a ui concern. The app layer returns
values; the ui state holder turns them into a `Signal` (see `usecase-airstream-bridge`).

**Calling anything a "domain service".** Classify it as an Operation or a Workflow; see
`domain-operations-and-workflows`.

**A use case that opens `atomically { … }` and inlines repository calls.** That skips the
workflow and takes the transaction boundary away from the code that knows it.
