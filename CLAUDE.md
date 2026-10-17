# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this repo is

An investor demo of an e-voting system built on revocable votes and a public, add-only ledger.
The design lives in [`docs/`](docs/README.md); the plan is [`PLAN.md`](PLAN.md).

The demo is one **Scala 3 + Scala.js** app that runs entirely in the browser:

| Concern | Choice |
|---------|--------|
| Language | Scala 3, Scala.js |
| Build | Mill 1.x; Vite serves the linked JS in development |
| UI | Laminar + Airstream, no component library, plain CSS |
| Effects | **None.** No ZIO, no cats-effect. Ports are synchronous and return `Either` |
| Dependency injection | `given` / `using` only, wired in one composition root |
| Crypto | `@noble/ed25519` + `@noble/hashes` through a Scala.js facade (browser); JDK Ed25519 (JVM tests) |
| Tests | ScalaTest |

Legacy code still in the tree (`lua-reference/`, `web-ui/`, `ui/`, `protobuffers/`) is not part of
the new stack; step 4 of `PLAN.md` archives or removes it.

## Skills

Project skills live in `.claude/skills/`. They are adapted from
[s4ysolutions/hexagonal-scala-zio-best-practice](https://github.com/s4ysolutions/hexagonal-scala-zio-best-practice),
with ZIO removed and Scala 3 `given`/`using`, context functions and opaque types in its place.

Layering spine:

```
hexagonal-feature-layout   ← logical layers: domain / app / infra / ui
  ↓ physical enforcement
module-separation          ← build-module split; the compiler enforces the layer rules
  ↓ decision logic
layers-to-modules          ← how many modules per layer; JVM + JS twins
  ↓ Mill syntax
mill-module-layout         ← build.mill idioms, Scala.js twins, Vite
```

Domain model spine:

```
domain-value-objects       ← VOs validate themselves; typed errors
  ↓ uses
scala3-domain-patterns     ← opaque types, Validated, union errors, phantom types, CanEqual
  ↓ specializes for identity
domain-identity-types      ← derived vs assigned IDs; who may mint one
  ↓ governs
domain-operations-and-workflows ← pure Operation vs Workflow that needs ports (via using)
```

Wiring spine:

```
scala3-context-di          ← given/using DI, capabilities, context-function DSLs
  ↓ assembled by
composition-root           ← the one place concrete adapters are named
  ↓ use-case boundary
usecase-command            ← Command + UseCase[C], Either results
  ↓ data access
scala3-tx-parameterized-repository ← Repository[TX] + atomically { … }
static-data-memory-adapter ← in-memory adapters (the demo's only storage)
```

Frontend spine:

```
usecase-airstream-bridge   ← state holder: UseCase[C] result → Var/Signal
ui-component-container-pattern ← container at the package root, leaves in components/
```

Scaffolding entry point: `create-usecase` walks all spines for one new use case.

## Critical invariants

The most commonly violated cross-skill rules. Check them on every change:

1. **Domain modules are platform-neutral and dependency-free.** No `scala.scalajs.*`, no
   `org.scalajs.dom`, no Laminar, no crypto library in `feature.domain` or
   `feature.domain.workflows`. They cross-compile to JVM and JS.
2. **Concrete adapters are named only in the composition root** (`products.demo`). Owned by
   `composition-root`.
3. **"Domain service" is a retired term.** Everything is an Operation (pure) or a Workflow
   (needs a port through `using`). Owned by `domain-operations-and-workflows`.
4. **DI is `given`/`using` only.** No service locator, no global mutable singleton, no
   `import Main.given` outside the composition root. Owned by `scala3-context-di`.
5. **Workflows own the atomic commit.** A use case never opens `atomically { … }` itself unless it
   has no workflow at all. Owned by `domain-operations-and-workflows`.
6. **No effect library.** Synchronous `Either[E, A]` everywhere; errors are sealed enums plus
   `InfraFailure`, never a raw `Throwable`. Owned by `domain-operations-and-workflows`.

## Rule ownership

Each cross-skill rule is stated in full by one owner skill; other skills link to it.

| Rule | Owner |
|------|-------|
| Module-by-module dependency lists | `module-separation` |
| domain / domain.workflows split decision, JVM + JS twins | `layers-to-modules` |
| Operation vs Workflow, error discipline, transaction ownership | `domain-operations-and-workflows` |
| `given`/`using` wiring, capability bundles, context-function DSLs | `scala3-context-di` |
| Concrete types only at the composition root | `composition-root` |
| Opaque types, `Validated`, `CanEqual`, phantom types | `scala3-domain-patterns` |
| ID shape and who may mint one | `domain-identity-types` |

## Editing skills

- The `description` must trigger on prompts people actually type.
- Cross-reference rules owned by another skill; don't restate them.
- Code examples must match Scala 3 / Scala.js / Mill 1.x / Laminar, with no ZIO.
- Keep examples in this project's domain (votings, ballots, ledger, audit) under the
  `s4y.evoting` package root.
