---
name: usecase-command
description: Use when designing the boundary between the ui and app layers, when a Laminar component or state holder is growing business logic, when adding a command such as IssueBallot, Cast, Revote or CloseVoting, or when a use case is coupled to a specific adapter
metadata:
  tags: [architecture, scala3, framework-agnostic]
---

# Use Case / Command Pattern

**Scope:** Scala 3, synchronous `Either` results. Unlike the ZIO original there is one variant
only, and the same `UseCase[C]` is called by the Laminar UI (through a state holder, see
`usecase-airstream-bridge`) and by tests.

## Overview

A **command** is a plain data object describing intent, carrying its error and result types. A
**use case** handles one command. The UI depends only on `UseCase[C]`, never on the concrete
class, its transaction type or its ports.

## Structure

```scala
// core.app — no dependencies
package s4y.evoting.core.app

trait UseCaseCommand:
  type E          // domain error (enum, union, or Nothing)
  type A          // result

trait UseCase[C <: UseCaseCommand]:
  def apply(command: C): Either[command.E, command.A]   // path-dependent result

extension [C <: UseCaseCommand](command: C)
  def run(using useCase: UseCase[C]): Either[command.E, command.A] = useCase(command)

extension [A](result: Either[Nothing, A])
  def infallible: A = result.fold[A](nothing => nothing, a => a)   // for commands with E = Nothing
```

`command.run` reads as the intent and resolves the use case from `given`s
(`scala3-context-di`).

```scala
// casting.app.usecases
final case class CastCommand(
    ballot: Signed[Party.Issuer, BallotToken],
    castId: CastId,
    choices: Choices
) extends UseCaseCommand:
  type E = CastError | InfraFailure
  type A = Receipt

final class CastUseCase[TX <: Tx](using
    TransactionManager[TX], CastingPorts[TX], Verifier[Party.Issuer]
) extends UseCase[CastCommand]:
  def apply(c: CastCommand): Either[c.E, c.A] =
    CastWorkflows.cast(c.ballot, c.castId, c.choices)      // pass-through; the workflow owns atomicity
```

**A command that cannot fail** sets `type E = Nothing`. There is no effect wrapper to avoid, so
this is fine here (unlike the ZIO original).

**A use case with no dependencies** can be a `given` in the command's companion:

```scala
final case class TallyCommand(entries: Seq[LedgerEntry]) extends UseCaseCommand:
  type E = Nothing
  type A = Tally
object TallyCommand:
  given UseCase[TallyCommand] with
    def apply(c: TallyCommand): Either[Nothing, Tally] = Right(TallyOperations.tally(c.entries))
```

This is the one exception to "use-case givens live in the composition root": nothing in it can
be swapped.

## Key Properties

- **The command carries its result type**, so callers are statically typed to the command's
  error and result without knowing the use case.
- **The UI depends only on `UseCase[C]`.** The TX type and the ports are invisible above the
  app layer.
- **The workflow owns the atomic commit**, not the use case (rule owned by
  `domain-operations-and-workflows`).

## What Belongs in a Command

- Input fields, already validated VOs where possible (`CastId`, `Choices`), not raw strings
- The `E` and `A` type members

Not in a command: transaction contexts, ports, keys, UI state.

## Common Mistakes

**A state holder or component calling a workflow or a repository directly.** The UI calls
`command.run`; the use case calls the workflow.

**A use case returning UI types** (`Signal`, `HtmlElement`, a CSS class). The use case returns
domain values; the UI renders them.

**One use case handling many commands.** Each command gets its own use case.

**Raw strings in a command** (`CastCommand(castId: String, …)`). Parse at the UI boundary into
VOs; a parse error is shown to the user before any use case runs.
