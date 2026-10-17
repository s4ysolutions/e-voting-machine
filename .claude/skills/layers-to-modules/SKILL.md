---
name: layers-to-modules
description: Use when deciding how many build modules a hexagonal layer maps to, when creating or naming a domain sub-module (domain, domain.workflows), when deciding which modules cross-compile to JVM and Scala.js, or when a domain module picks up a port, a Scala.js or a Laminar dependency and you want to know whether that is correct
metadata:
  tags: [architecture, language-agnostic, build-system-agnostic, scalajs]
---

# Mapping Hexagonal Layers to Build Modules

**Scope:** build-system-agnostic decision rules; Mill syntax in `mill-module-layout`.

## Core Principle

**Split a layer into several modules only when the parts have different dependency surfaces
that matter**: one part may see something the other must not, and that restriction buys
something concrete (enforced purity, platform independence, faster tests). Splitting only to
make folders look tidy is not enough; use packages inside one module for that.

## Two Axes in This Project

1. **Purity axis.** Can this code reach a port? Operations must not; workflows must.
2. **Platform axis.** Must this code run on the JVM as well as in the browser? Everything in
   `domain`, `domain.workflows`, `app` and `core.{domain,crypto,tx,app}` does: it is tested on
   the JVM (fast, no Node) and reused by a future JVM backend. Laminar UI and npm facades are
   JS-only; JDK crypto is JVM-only.

## Layer-by-Layer

### `domain`: two modules, split on the purity axis

| Concern | Module | Sees |
|---------|--------|------|
| VOs, errors, operations | `feature.domain` | `core.domain`, `core.crypto`, other pure domains |
| Port traits, workflows | `feature.domain.workflows` | `feature.domain`, `core.tx` |

The split is the default. `feature.domain` cannot see any port trait, so an Operation cannot
call one: the compiler enforces the Operation/Workflow rule from
`domain-operations-and-workflows`. Both modules are cross-compiled (a JVM module plus a nested
`*Js` twin, see `mill-module-layout`).

Unlike the ZIO original, there is no need for a third `domain.vo` module: nothing in
`feature.domain` is JVM-only, so the whole module cross-compiles.

### `app`: one module

`feature.app` depends on `feature.domain.workflows` and `core.app`. It is cross-compiled because
use cases are plain Scala. Split it only if use-case groups need genuinely different libraries
(rare).

### `infra`: one module per technology, one platform each

```
feature.infra.memory     ← in-memory adapters; cross-compiled (tests use it on the JVM)
core.infra.noble         ← @noble/* facades; JS only
core.infra.jdk           ← JDK Ed25519 + SHA-256; JVM only
```

Infra modules never depend on each other. A feature only needs its own `infra.memory`; crypto
adapters are shared in `core.infra`.

### `ui`: one module per feature, JS only

`feature.ui` holds that feature's Laminar components and state holders. Do not split by screen
or component group; use packages (`ui-component-container-pattern`).

## Summary

| Layer | Modules | Platforms |
|-------|---------|-----------|
| `domain` | `feature.domain` + `feature.domain.workflows` | JVM + JS |
| `app` | `feature.app` | JVM + JS |
| `infra` | one per technology | the technology's platform |
| `ui` | `feature.ui` | JS |
| composition root | `products.demo` | JS |

## Naming

| What | Name |
|------|------|
| Pure domain | `feature.domain` |
| Port-using domain | `feature.domain.workflows` |
| Infra | `feature.infra.<technology>` (`memory`), `core.infra.<technology>` (`noble`, `jdk`) |
| UI | `feature.ui` |
| JS twin of a cross-compiled module | `<parent>Js` (`domainJs`, `workflowsJs`, `appJs`) |

Avoid `feature.domain.core` or `feature.domain.pure` ("domain" is already the pure one), and
generic infra names like `feature.infra.storage` (name the technology).

## Common Mistakes

**A port trait in `feature.domain`.** Move it to `feature.domain.workflows/ports`; otherwise
Operations can call it.

**A Scala.js import in a domain module.** The JVM build fails. Put the platform code in
`core.infra.noble` behind a capability trait from `core.crypto`.

**Making `app` JS-only "because the demo is a browser app".** Then use cases cannot be tested on
the JVM and cannot move to a backend. Keep `app` cross-compiled; only `ui` and npm-backed infra
are JS-only.

**One `core.infra` module holding both noble and JDK crypto.** They are different platforms;
neither one would link.
