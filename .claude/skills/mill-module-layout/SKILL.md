---
name: mill-module-layout
description: Use when setting up, extending or scaffolding the Mill build — adding a core or feature module, declaring moduleDeps, adding a ScalaTest sub-module, cross-compiling a module to Scala.js through a nested Js twin, or wiring the Scala.js demo product to Vite
metadata:
  tags: [mill, scala, scalajs, build-tool, architecture]
---

# Mill Module Layout

**Scope:** Mill 1.x, Scala 3, Scala.js, ScalaTest. Version numbers below are placeholders
(`x.y.z`); pin real ones when the build is created.

## Overview

Mill's nested `object`s map onto the `core / features / products` grouping from
`module-separation`. Each object is one build module, and `moduleDeps` is the only place
inter-module dependencies are declared, which makes it the compile-time enforcer of the layer
rules.

## Base Traits and Dependencies

```scala
//| mill-version: 1.x.y
package build

import mill.*, scalalib.*, scalajslib.*, scalajslib.api.*

object V:
  val scala   = "3.x.y"       // Scala 3 LTS
  val scalaJS = "1.x.y"

object D:
  val laminar   = mvn"com.raquo::laminar::x.y.z"     // brings Airstream and scala-js-dom
  val scalatest = mvn"org.scalatest::scalatest::x.y.z"

trait BaseModule extends ScalaModule:
  def scalaVersion = V.scala
  override def scalacOptions = super.scalacOptions() ++ Seq(
    "-deprecation", "-feature", "-unchecked",
    "-language:strictEquality",      // == needs CanEqual; see scala3-domain-patterns
    "-Wunused:all"
  )

trait BaseJsModule extends BaseModule with ScalaJSModule:
  def scalaJSVersion = V.scalaJS
  override def moduleKind = ModuleKind.ESModule   // Vite consumes ES modules
```

`BaseModule` is the JVM side of every cross-compiled module. `BaseJsModule` is used for JS twins
and for JS-only modules (`ui`, `core.infra.noble`, `products.demo`).

The `::` before the version makes Mill pick the platform artifact (`_sjs1_3` for Scala.js,
`_3` for the JVM) automatically, so the same `D.scalatest` works in both kinds of module.

## Grouping Objects

```scala
object core     extends Module { ... }
object features extends Module { ... }
object products extends Module { ... }
```

Grouping objects extend `Module`, not `ScalaModule`, so they have no compile target of their own
and no `mvnDeps`.

## Scala.js Twin (Cross-Compilation)

A cross-compiled module is a JVM `BaseModule` with a nested `*Js` twin that **reuses the
parent's sources**:

```scala
object domain extends BaseModule:
  override def moduleDeps = Seq(core.domain, core.crypto)

  object tests extends ScalaTests with TestModule.ScalaTest:
    override def mvnDeps = Seq(D.scalatest)

  object domainJs extends BaseJsModule:
    override def sources    = Task { domain.sources() ++ super.sources() }
    override def moduleDeps = Seq(core.domain.domainJs, core.crypto.cryptoJs)
```

Rules:
- The twin is nested inside the module it mirrors, never a sibling.
- It has no source files of its own: `sources` reuses the parent's. One set of files, two targets.
- Its `moduleDeps` point at the **other twins** (`core.domain.domainJs`), never at the JVM
  originals. Pointing at a JVM module compiles but fails at the Scala.js link step.
- Name it `<parent>Js` so every cross-compiled module can be found with one grep.

## Core Modules

```scala
object core extends Module:
  object domain extends BaseModule:                        // InfraFailure, Validated
    object domainJs extends BaseJsModule:
      override def sources = Task { domain.sources() ++ super.sources() }

  object crypto extends BaseModule:                        // Crypto, Signer[P], Signed[P, T]
    override def moduleDeps = Seq(core.domain)
    object cryptoJs extends BaseJsModule:
      override def sources    = Task { crypto.sources() ++ super.sources() }
      override def moduleDeps = Seq(core.domain.domainJs)

  object identity extends BaseModule:                      // Identifier[A], no mint
    object identityJs extends BaseJsModule:
      override def sources = Task { identity.sources() ++ super.sources() }
    object mint extends BaseModule:                        // constructs Identifier; adapters + ui only
      override def moduleDeps = Seq(identity)
      object mintJs extends BaseJsModule:
        override def sources    = Task { mint.sources() ++ super.sources() }
        override def moduleDeps = Seq(identity.identityJs)

  object tx extends BaseModule:                            // Tx, TransactionManager[TX]
    override def moduleDeps = Seq(core.domain)
    object txJs extends BaseJsModule:
      override def sources    = Task { tx.sources() ++ super.sources() }
      override def moduleDeps = Seq(core.domain.domainJs)

  object app extends BaseModule:                           // UseCaseCommand, UseCase[C]
    object appJs extends BaseJsModule:
      override def sources = Task { app.sources() ++ super.sources() }

  object infra extends Module:
    object memory extends BaseModule:                      // MemoryTx, MemoryCell
      override def moduleDeps = Seq(core.tx)
      object memoryJs extends BaseJsModule:
        override def sources    = Task { memory.sources() ++ super.sources() }
        override def moduleDeps = Seq(core.tx.txJs)

    object noble extends BaseJsModule:                     // JS only: @noble/* facades
      override def moduleDeps = Seq(core.crypto.cryptoJs)

    object jdk extends BaseModule:                         // JVM only: JDK Ed25519, SHA-256
      override def moduleDeps = Seq(core.crypto)

  object ui extends BaseJsModule:                          // shared Laminar pieces, SignedLine
    override def moduleDeps = Seq(core.crypto.cryptoJs)
    override def mvnDeps    = Seq(D.laminar)

  object tests extends BaseModule:                         // scenario DSL, fixtures
    override def moduleDeps = Seq(core.infra.memory, core.infra.jdk, core.app)
    override def mvnDeps    = Seq(D.scalatest)
```

## Feature Modules

```scala
object features extends Module:
  object casting extends Module:

    object domain extends BaseModule:
      override def moduleDeps = Seq(core.domain, core.crypto, features.ballots.domain)
      object tests extends ScalaTests with TestModule.ScalaTest:
        override def mvnDeps = Seq(D.scalatest)
      object domainJs extends BaseJsModule:
        override def sources    = Task { domain.sources() ++ super.sources() }
        override def moduleDeps = Seq(core.domain.domainJs, core.crypto.cryptoJs,
                                      features.ballots.domain.domainJs)

      object workflows extends BaseModule:
        override def moduleDeps = Seq(domain, core.tx)
        object tests extends ScalaTests with TestModule.ScalaTest:
          override def moduleDeps = super.moduleDeps ++ Seq(casting.infra.memory, core.tests)
          override def mvnDeps    = Seq(D.scalatest)
        object workflowsJs extends BaseJsModule:
          override def sources    = Task { workflows.sources() ++ super.sources() }
          override def moduleDeps = Seq(domain.domainJs, core.tx.txJs)

    object app extends BaseModule:
      override def moduleDeps = Seq(core.app, domain, domain.workflows)
      object appJs extends BaseJsModule:
        override def sources    = Task { app.sources() ++ super.sources() }
        override def moduleDeps = Seq(core.app.appJs, domain.domainJs, domain.workflows.workflowsJs)

    object infra extends Module:
      object memory extends BaseModule:
        override def moduleDeps = Seq(domain.workflows, core.infra.memory)
        object memoryJs extends BaseJsModule:
          override def sources    = Task { memory.sources() ++ super.sources() }
          override def moduleDeps = Seq(domain.workflows.workflowsJs, core.infra.memory.memoryJs)

    object ui extends BaseJsModule:
      override def moduleDeps = Seq(domain.domainJs, app.appJs, core.ui)
```

`ui` depends on `app.appJs` and `domain.domainJs`, never on `domain.workflows` or `infra`.

## Product (Composition Root)

```scala
object products extends Module:
  object demo extends BaseJsModule:
    override def mainClass  = Some("s4y.evoting.demo.Main")
    override def moduleDeps = Seq(
      core.infra.memory.memoryJs,
      core.infra.noble,
      features.casting.infra.memory.memoryJs,
      features.casting.ui,
      // ... every other feature's infra.memory twin and ui
    )
    object tests extends ScalaJSTests with TestModule.ScalaTest:   // smoke tests with real noble crypto
      override def mvnDeps = Seq(D.scalatest)
```

This is the only module that sees every concrete adapter. Its sources are the composition root
(see `composition-root`).

## Vite and npm

Mill links the app; Vite serves it and resolves the npm imports the noble facades emit.

- `package.json` at the repo root lists `@noble/ed25519`, `@noble/hashes` and `vite`.
- `products/demo/index.html` loads `products/demo/main.js`, which imports the linked output:
  `import "../../out/products/demo/fastLinkJS.dest/main.js";`
- Development loop, in two terminals:

```bash
./mill -w products.demo.fastLinkJS     # relink on every change
npx vite products/demo                  # serve with hot reload
```

`@scala-js/vite-plugin-scalajs` drives sbt, so it is not used here.

Mill writes all build output to `out/`. Nothing else may live there.

## Test Sub-Modules

| Test target | Mixin | Runs on |
|-------------|-------|---------|
| Domain VOs and operations | `ScalaTests with TestModule.ScalaTest` | JVM |
| Workflows and use cases (memory adapters + JDK crypto) | `ScalaTests with TestModule.ScalaTest` | JVM |
| noble adapter, end-to-end smoke | `ScalaJSTests with TestModule.ScalaTest` | Node |
| Laminar components (optional) | `ScalaJSTests` + `override def jsEnvConfig = JsEnvConfig.JsDom()` | Node + jsdom |

Test modules are nested inside the module they test.

```bash
./mill __.test                          # every test module
./mill features.casting.__.test         # one feature
./mill features.casting.domain.compile  # compile check only
```

## Common Mistakes

**A grouping object extending `ScalaModule`.** Adds an empty compile target. Use `Module`.

**A JS twin depending on a JVM module.** `domainJs` must depend on `core.domain.domainJs`, not
`core.domain`. It may compile and then fail to link.

**A twin with its own source directory.** Invites drift. Reuse the parent's `sources`.

**Laminar in a cross-compiled module's `mvnDeps`.** The JVM side fails to resolve it. Laminar
belongs only in `ui` modules.

**Running domain tests on Scala.js by default.** Slower and needs Node. Domain and app tests run
on the JVM; JS tests only cover JS-only code.
