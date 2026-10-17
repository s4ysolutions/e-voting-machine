---
name: domain-value-objects
description: Use when modelling a domain concept (voting key, subject, option, choices, ballot token, receipt), when deciding where validation logic lives, when a domain type picks up a Laminar, JSON or Scala.js dependency, or when a raw exception escapes from an adapter
metadata:
  tags: [architecture, domain-modeling, language-agnostic]
---

# Domain Value Objects

**Scope:** language-agnostic rules, Scala 3 examples. Mechanics (opaque types, `Validated`,
`CanEqual`) are in `scala3-domain-patterns`.

## Overview

Value objects (VOs) are immutable domain types that own their invariants. Adapter errors are
wrapped in typed containers. Rendering, serialization and platform concerns never enter the
domain.

## Core Rules

**Validation inside the VO.** Construction either succeeds with a valid value or fails with a
domain error. Validating after construction is too late; validating at every call site is
fragile.

```scala
opaque type SubjectKey = String
object SubjectKey:
  private val Pattern = "[a-z][a-z0-9-]{0,31}".r
  def from(raw: String): Either[VotingError.InvalidSubjectKey, SubjectKey] =
    Either.cond(Pattern.matches(raw), raw, VotingError.InvalidSubjectKey(raw))
```

For one VO with one invariant, `Either` is right. When a constructor combines several validated
pieces (a voting definition with many subjects), accumulate errors with `Validated`
(`scala3-domain-patterns`) so the owner sees every problem at once.

**Typed error boundary.** A raw `Throwable` never reaches domain or app code. Adapters wrap:

```scala
// core.domain
final case class InfraFailure(message: String) derives CanEqual
object InfraFailure:
  /** strictEquality: lets `Either[E | InfraFailure, A]` results be compared with `Left(e)` */
  given unionCanEqual[E](using CanEqual[E, E]): CanEqual[E | InfraFailure, E | InfraFailure] = CanEqual.derived
```

**Layer-pure types.** A domain VO imports nothing from:
- Laminar, Airstream, `org.scalajs.dom`, `scala.scalajs.js`
- JSON or other serialization libraries
- npm facades

**The readable signed text (decision D7) is domain data.** The canonical text a signature covers
is part of what the VO *means*, so its `Canonical[T]` given lives with the VO. Colours, badges and
layout live in `ui`.

```scala
given Canonical[BallotToken] = t => s"ballot=${t.name}"     // what the issuer signs
```

**Domain errors are sealed hierarchies:**

```scala
enum CastError derives CanEqual:
  case InvalidIssuerSignature
  case TokenAlreadySpent
  case VotingNotOpen
  case InvalidChoices(problems: List[String])
```

## Checklist

- [ ] Immutable
- [ ] Validates on construction; no public constructor that skips validation
- [ ] Imports only other domain types and `core` capability traits
- [ ] Has a typed error, not an exception
- [ ] `derives CanEqual` (or a `CanEqual` given) because `-language:strictEquality` is on
- [ ] No rendering or serialization concerns; `Canonical[T]` is the one exception (D7)

## Common Mistakes

**JSON codec on the VO.** Codecs belong at the boundary that needs them, with explicit mapping.

**A domain enum case wrapping `Throwable`** (`case Failed(cause: Throwable)`). Map it to a
semantic error or `InfraFailure` in the adapter.

**Anemic VO and fat use case.** If logic naturally belongs to the value (`Choices.optionFor(subject)`),
put it on the VO.

**Mutable VO for performance.** Immutable by default; optimise only when measured.
