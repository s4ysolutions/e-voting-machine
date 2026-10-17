---
name: scala3-context-di
description: Use when wiring dependencies with given/using, when a class or function needs a port, a capability or a use case, when several dependencies always travel together (capability bundle), when shortening signatures with context-function type aliases, when building a DSL with context functions (atomically { … }, voting(…) { subject(…) }, test scenarios), or when tests need to swap an adapter
metadata:
  tags: [scala3, dependency-injection, context-functions, dsl]
---

# Dependency Injection and DSLs with `given` / `using`

**Scope:** Scala 3 only. No DI framework, no effect-system layers. Owns the wiring rules;
`composition-root` owns *where* the givens are defined.

## Overview

- A **dependency** is declared as a `using` parameter: on a constructor (classes) or on a method
  (functions in objects).
- A **provider** is a `given` definition. Providers for ports, capabilities and use cases exist in
  exactly two places: the composition root (`products.demo`) and test scopes.
- The compiler resolves the graph. A missing dependency is a compile error at the composition
  root; an ambiguous one (two givens of one type) is too.

## What May Be a `given`, Where

| Kind | Example | Where the `given` lives |
|------|---------|-------------------------|
| Port | `LedgerRepository[TX]`, `TransactionManager[TX]` | composition root, tests |
| Pure capability | `Crypto`, `Signer[Party.Issuer]`, `Verifier[Party.Issuer]` | composition root, tests |
| Use case | `UseCase[CastCommand]` | composition root, tests |
| UI plumbing | `ChangeFeed` (see `usecase-airstream-bridge`) | composition root |
| Typeclass instance | `Canonical[BallotToken]`, `CanEqual[SeqNo, SeqNo]`, `Ordering[SeqNo]` | the type's companion (not a dependency, it is part of the type) |

A port or capability `given` in a feature's companion object is a mistake: it is in implicit
scope everywhere and can no longer be swapped at the composition root.

## Receiving Dependencies

```scala
// Shape B class (see domain-operations-and-workflows): dependencies in the constructor
final class CastUseCase[TX <: Tx](using
    TransactionManager[TX],
    CastingPorts[TX],
    Verifier[Party.Issuer]
) extends UseCase[CastCommand]:
  def apply(c: CastCommand): Either[c.E, c.A] = CastWorkflows.cast(c.ballot, c.castId, c.choices)
```

Anonymous `using` parameters are fine when the body only passes them on. Name a parameter
(`using tm: TransactionManager[TX]`) when the body calls it directly.

## Capability Bundles

When ports always travel together, give them one parameter object
(`hexagonal-feature-layout` § "Parameter Objects") and provide the bundle as a single `given`:

```scala
final case class CastingPorts[TX <: Tx](
    ledger: LedgerRepository[TX],
    spent: SpentTokens[TX],
    cancelled: CancelledCasts[TX]
)
```

Keep a bundle to one bounded context's ports. An app-wide `AppContext` holding everything is a
service locator under another name.

## Context-Function Type Aliases

A context function type `A ?=> B` is a function whose argument is supplied from `given`s. Aliases
turn long `using` lists into a readable vocabulary:

```scala
type Casting[TX <: Tx, A] = (TransactionManager[TX], CastingPorts[TX], Verifier[Party.Issuer]) ?=> A

object CastWorkflows:
  def cast[TX <: Tx](ballot: Signed[Party.Issuer, BallotToken], castId: CastId, choices: Choices)
      : Casting[TX, Either[CastError | InfraFailure, Receipt]] =
    summon[TransactionManager[TX]].atomically[CastError, Receipt]("cast") { ... }
```

Use an alias when the same set appears on three or more signatures; below that, a plain `using`
clause is clearer.

## DSLs with Context Functions

A context-function parameter (`body: B ?=> R`) lets a block of code call functions that need
`B` without mentioning it. Three DSLs in this project use the pattern.

### `atomically { … }`

The transaction is a context function parameter; repository calls inside the block receive it
implicitly. Owned by `scala3-tx-parameterized-repository`.

### Builder DSL for voting definitions (seed data, tests)

```scala
package s4y.evoting.voting.domain.dsl

final class VotingBuilder private[dsl] ():
  private[dsl] val subjects = List.newBuilder[RawSubject]

def voting(key: String, title: String)(body: VotingBuilder ?=> Unit): Validated[VotingError, VotingDefinition] =
  val builder = VotingBuilder()
  body(using builder)
  VotingDefinition.from(key, title, builder.subjects.result())

def subject(key: String, question: String)(options: String*)(using b: VotingBuilder): Unit =
  b.subjects += RawSubject(key, question, options.toList)
```

```scala
val annualMeeting = voting("annual-2027", "Annual meeting 2027") {
  subject("budget", "Approve the 2027 budget?")("yes", "no")
  subject("board", "Who joins the board?")("alice", "bob", "carol")
}   // Validated[VotingError, VotingDefinition]: every invalid key reported at once
```

The builder is mutable but never escapes `voting(…)`, and the result still goes through the
validating constructor, so the DSL cannot produce an invalid definition. `subject` outside a
`voting` block does not compile: there is no `VotingBuilder` in scope.

### Scenario DSL (tests and the scripted demo)

```scala
scenario {
  val alice = voter("alice")
  alice.getsBallot()
  val r1 = alice.casts("budget" -> "yes")
  alice.revotes("budget" -> "no")
  expectTally("budget")("yes" -> 0, "no" -> 1)
  expectListed(r1)                 // the coercer's view: the old receipt is still there
}
```

`scenario` creates a fresh `Scenario` (its own memory adapters and keys) and runs the block with
it as a `given`. `voter`, `expectTally` and the handle's methods take `(using Scenario)` and call
use cases through it. It crosses every feature, so it lives with a composition root: a JVM test
harness product (`products.harness`), never in `core` or a feature.

## Testing

Tests provide their own givens in a local scope. No mocking library is needed:

```scala
class CastWorkflowsSpec extends AnyFunSuite:
  test("a spent token is rejected"):
    given TransactionManager[MemoryTx] = MemoryTransactionManager()
    given CastingPorts[MemoryTx]       = MemoryCastingPorts()
    given Verifier[Party.Issuer]       = JdkKeys.issuer.verifier
    val ballot = JdkKeys.issuer.signer.sign(Fixtures.token)
    CastWorkflows.cast(ballot, Fixtures.castId1, Fixtures.choices)
    assert(CastWorkflows.cast(ballot, Fixtures.castId2, Fixtures.choices) == Left(CastError.TokenAlreadySpent))
```

Use real pure capabilities (JDK crypto) rather than fakes; fake only ports.

## Checklist

- [ ] Every dependency is a `using` parameter; nothing is fetched from a global object
- [ ] Port, capability and use-case givens exist only in the composition root and in tests
- [ ] Typeclass instances live in companions
- [ ] Bundles group one bounded context's ports, no more
- [ ] DSL builders never escape their block and finish through a validating constructor

## Common Mistakes

**`import Main.given` in a feature or ui module.** That makes the composition root a service
locator. Pass dependencies down through `using` instead.

**A port `given` in a feature companion** (`object LedgerRepository: given LedgerRepository[MemoryTx] = …`).
It names an adapter outside the composition root and cannot be swapped.

**`summon[LedgerRepository[TX]]` inside an Operation.** If it needs a port, it is a Workflow;
declare the port in its signature.

**Implicit conversions to make wiring "just work".** `given Conversion[…]` hides data flow. Use an
explicit adapter.

**A cycle between givens.** Given aliases are initialised lazily; a cycle shows up as a stack
overflow at startup. Keep the graph acyclic: ports and capabilities first, then use cases, then UI.
