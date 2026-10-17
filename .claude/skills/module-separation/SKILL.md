---
name: module-separation
description: Use when splitting the project into build modules, when a layer violation is caught at runtime or in review instead of at compile time, when one feature's domain is accidentally importable by another feature's infra, or when adding a new bounded context or core module
metadata:
  tags: [architecture, language-agnostic, build-system-agnostic]
---

# Module Separation for Hexagonal Architecture

**Scope:** language-agnostic, build-system-agnostic. Mill syntax is in `mill-module-layout`.

## Overview

Physical build modules are the only reliable enforcement of hexagonal layer rules. Rules in a
README or a linter can be skipped or forgotten; a missing module dependency makes the violation
a compile error. Goal: **if it compiles, the architecture is correct.**

See `hexagonal-feature-layout` for what belongs in each layer.

## Top-Level Grouping

```
core/        ← shared kernel: abstractions every feature uses
features/    ← one sub-group per bounded context
products/    ← one runnable artifact per entry point; the composition root
```

`core` and `features` have no entry point. `products` is the only place that produces a runnable
artifact.

## Core Modules

```
core/
  domain/          ← InfraFailure, Validated, NonEmpty helpers; pure, cross-compiled
  crypto/          ← capability traits: Crypto, Signer[P], Verifier[P], Signed[P, T], Canonical[T]
  identity/        ← Identifier[A] for store-assigned IDs; no mint (see domain-identity-types)
    mint/          ← the only way to construct an Identifier; adapters and ui depend on it
  tx/              ← Tx marker, TransactionManager[TX]; pure, cross-compiled
  app/             ← UseCaseCommand + UseCase[C]; pure, cross-compiled
  infra/
    memory/        ← MemoryTx, MemoryTransactionManager, MemoryCell
    noble/         ← Crypto/Signer/Verifier over @noble/* (JS only)
    jdk/           ← Crypto/Signer/Verifier over JDK Ed25519 + SHA-256 (JVM only)
  ui/              ← shared Laminar pieces: layout, SignedLine (D7) (JS only)
  tests/           ← shared test helpers and the scenario DSL
```

Rules for core modules:
- A core module may depend on other core modules, never on a feature module.
- Only `core/infra/*` and `core/ui` name external libraries (npm facades, Laminar).
- `core/app` holds only the abstract `UseCase[C]` shape, never a feature's commands.

## Feature Modules

Each bounded context (`voting`, `voters`, `ballots`, `casting`, `audit`) has the same shape:

```
features/<name>/
  domain/            ← VOs, errors, operations
  domain/workflows/  ← port traits (ports/), workflows
  app/               ← commands + use cases
  infra/memory/      ← in-memory adapters implementing this feature's ports
  ui/                ← Laminar components + state holders for this feature
```

**Allowed dependencies (enforced through module deps):**

| Module | May depend on |
|--------|--------------|
| `feature.domain` | `core.domain`, `core.crypto`, `core.identity`, other features' **pure** `domain` |
| `feature.domain.workflows` | `feature.domain`, `core.domain`, `core.tx` |
| `feature.app` | `core.app`, `feature.domain`, `feature.domain.workflows` |
| `feature.infra.memory` | `feature.domain.workflows`, `core.infra.memory`, `core.identity.mint` |
| `feature.ui` | `feature.domain`, `feature.app`, `core.ui`, `core.identity.mint` (parsing only) |

`feature.app` never depends on `feature.infra.*` or `feature.ui`.

**Cross-feature dependencies.** A feature's pure `domain` may depend on another feature's pure
`domain` (for example `casting.domain` reusing `ballots.domain`'s `BallotToken`). A feature never
depends on another feature's `domain.workflows`, `app`, `infra` or `ui`.

When one feature's workflow needs another feature's capability (the re-vote workflow in
`casting` must issue a ballot from `ballots`), `casting` declares its own port
(`RevoteBallotIssuer`). The composition root implements it with a thin adapter that calls the
`ballots` use case. Cross-feature wiring goes through the composition root only.

## Test Sub-Modules

Each module with tests owns its test sub-module as a child:

```
feature.domain/
  tests/       ← ScalaTest on the JVM: VOs, operations
feature.domain.workflows/
  tests/       ← ScalaTest on the JVM, with core.infra.memory + core.infra.jdk as test deps
products.demo/
  tests/       ← ScalaTest on Scala.js (Node): smoke tests with the real noble crypto
```

Test modules may add test-only dependencies without changing the production module's
dependency surface. Shared helpers live in `core/tests`, never in a production module.

## Products (Composition Root)

```
products/
  demo/        ← Scala.js entry point, composition root, Vite index.html
```

It depends on every concrete infra and ui module, declares the TX alias
(`type TXM = MemoryTx`), defines every `given`, and contains no business logic. Rules owned by
`composition-root`.

## What the Compiler Enforces

| Violation | How it's caught |
|-----------|----------------|
| Laminar or `scala.scalajs` imported in a domain module | domain modules have no such dependency, and their JVM build fails |
| Domain imports an adapter type | domain modules have no dep on `*.infra.*` |
| App imports ui | `feature.app` has no dep on `feature.ui` |
| Feature A infra imports feature B infra | no cross-feature infra dep |
| Composition root bypassed | only `products.demo` depends on every concrete module |

## Incremental Refactoring

When a domain type changes, only modules above it in the graph break. Land the domain change
first, then fix the app, ui and composition root, so the compiler bounds each step.

## Common Mistakes

**One module per feature.** A single `ballots` module holding domain, app and infra compiles but
enforces nothing.

**`core/app` growing feature use cases.** Feature commands and use cases live in
`features/<name>/app`.

**Feature infra depending on another feature's infra.** Share through ports and the composition
root.

**`products.demo` growing business logic.** If a decision appears there, move it to the right
feature layer.

**Test helpers in production modules.** They belong in `core/tests`.
