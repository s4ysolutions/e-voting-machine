---
name: scala3-domain-patterns
description: Use when wrapping a primitive in a domain type (opaque type, refined subtype), when building a value object or command from several fields and choosing between Either and accumulating Validated, when modelling errors as enums or union types, when encoding a state or a signer in the type (phantom types, typestate, Signed[P, T]), when == fails to compile under strictEquality, or when adding a typeclass such as Canonical[T]
metadata:
  tags: [scala3, domain-modeling, adt, type-level]
---

# Scala 3 Domain Patterns

**Scope:** Scala 3 standard library only (no zio-prelude, no cats). Replaces the original
`zio-prelude-domain-patterns`. Everything here is pure, so it is allowed inside Operations
(`domain-operations-and-workflows`).

## 1. Opaque Types: Newtypes and Refined Subtypes

**Newtype: a distinct type, every value legal.** Use an opaque type with no bound. Outside the
defining file it is not a `String`: passing a raw string, or comparing with one, fails to compile.

```scala
opaque type OptionLabel = String
object OptionLabel:
  def apply(raw: String): OptionLabel = raw
  extension (o: OptionLabel) def value: String = o
  given CanEqual[OptionLabel, OptionLabel] = CanEqual.derived
```

**Refined subtype: a primitive that is never invalid.** Add an upper bound when callers may
safely *read* it as the primitive, and validate in the only constructor:

```scala
opaque type SeqNo <: Long = Long          // reads as a Long, but only from(…) creates one
object SeqNo:
  def from(n: Long): Either[LedgerError.NegativeSeq, SeqNo] =
    Either.cond(n >= 0, n, LedgerError.NegativeSeq(n))
  given CanEqual[SeqNo, SeqNo] = CanEqual.derived
  given Ordering[SeqNo] = Ordering.Long
```

**Parsing, not refining:** when the input shape differs from the stored shape (hex string to
digest bytes), hand-roll the smart constructor and return `Either`.

Define the opaque type and its companion at the top level of one file; the type is transparent
only inside that file.

## 2. Strict Equality

The build enables `-language:strictEquality` (`mill-module-layout`). `a == b` compiles only with
a `CanEqual` for the two types, so `optionLabel == "yes"` is a compile error, as intended.

- Case classes and enums: `derives CanEqual`.
- Opaque types: `given CanEqual[X, X] = CanEqual.derived` in the companion.
- Never `given CanEqual[X, String]`; that reopens the hole.
- **Union error types need one more given.** A use case returns `Either[CastError | InfraFailure, A]`,
  and a test compares it with `Left(CastError.TokenAlreadySpent)`. That needs a `CanEqual` for the
  union, which nothing derives. `InfraFailure`'s companion provides it once for every `E` with its
  own `CanEqual` (`domain-value-objects`). Because `CanEqual` is contravariant, the union instance
  also covers comparing against a single case. Without it the comparison fails to compile.

Pattern matching on enum cases or types needs no `CanEqual`.

## 3. `Either` for One Invariant, `Validated` for Many

`Either` stops at the first failure: right for one value with one invariant. When a constructor
combines several validated pieces (a voting with a key, a title and many subjects), the owner
should see **every** problem at once. `core.domain` provides a small accumulating `Validated`;
`::[E]` (non-empty `List`) guarantees at least one error:

```scala
package s4y.evoting.core.domain

enum Validated[+E, +A]:
  case Valid(value: A)
  case Invalid(errors: ::[E])

object Validated:
  def valid[A](a: A): Validated[Nothing, A]   = Valid(a)
  def invalid[E](e: E): Validated[E, Nothing] = Invalid(::(e, Nil))

  extension [E, A](either: Either[E, A])
    def toValidated: Validated[E, A] = either match
      case Left(e)  => invalid(e)
      case Right(a) => valid(a)

  extension [E, A](va: Validated[E, A])
    def map[B](f: A => B): Validated[E, B] = va match
      case Valid(a)   => Valid(f(a))
      case Invalid(e) => Invalid(e)
    def zip[E2 >: E, B](vb: Validated[E2, B]): Validated[E2, (A, B)] = (va, vb) match
      case (Valid(a), Valid(b))       => Valid((a, b))
      case (Invalid(e1), Invalid(e2)) => Invalid(::(e1.head, e1.tail ++ e2))
      case (Invalid(e), _)            => Invalid(e)
      case (_, Invalid(e))            => Invalid(e)
    def toEither: Either[::[E], A] = va match
      case Valid(a)   => Right(a)
      case Invalid(e) => Left(e)

  def traverse[E, A, B](as: List[A])(f: A => Validated[E, B]): Validated[E, List[B]] =
    as.foldRight(valid(List.empty[B]): Validated[E, List[B]])((a, acc) => f(a).zip(acc).map(_ :: _))
```

```scala
object VotingDefinition:
  def from(key: String, title: String, subjects: List[RawSubject]): Validated[VotingError, VotingDefinition] =
    (VotingKey.from(key).toValidated
      zip VotingTitle.from(title).toValidated
      zip Validated.traverse(subjects)(Subject.from))
      .map { case ((k, t), ss) => VotingDefinition(k, t, ss) }
```

Do not use `Validated` for a single field; that is ceremony `Either` already covers.

## 4. ADTs and Error Types

- **Enums for closed sets**: statuses, error cases, ledger entry kinds. Exhaustiveness checking
  catches a missed case at compile time.
- **One error enum per workflow or port**, named after it (`CastError`, `RevoteError`).
- **Union types to combine**: `Either[CastError | InfraFailure, Receipt]`. Do not wrap the union
  in yet another enum unless a caller genuinely branches on the wrapper.
- When a `for` over `Either` mixes error types, give the enclosing call its type arguments
  (`atomically[CastError, Receipt]`) or annotate the result; otherwise Scala infers a useless
  least upper bound.

## 5. Phantom Types

### Who signed it: `Signed[P, T]`

Decision D7 shows who signed each object. Put the signer in the type, so an operator-signed
entry can never be passed where an issuer-signed ballot is required:

```scala
package s4y.evoting.core.crypto

sealed trait Party
object Party:
  sealed trait Issuer   extends Party
  sealed trait Operator extends Party
  sealed trait Owner    extends Party

final case class Signed[P <: Party, +T] private[crypto] (value: T, text: CanonicalText, signature: Signature)

trait Signer[P <: Party]:                          // only a Signer can create a Signed
  def sign[T](value: T)(using Canonical[T]): Signed[P, T]

trait Verifier[P <: Party]:
  def verify(s: Signed[P, ?]): Boolean
  def verifyText(text: String, signature: Signature): Boolean   // the D7 tamper demo
```

```scala
def cast(ballot: Signed[Party.Issuer, BallotToken], ...)        // Signed[Party.Operator, …] won't compile
```

The `private[crypto]` constructor means domain code cannot fabricate a `Signed` value; only a
`Signer[P]` adapter creates one.

### What state it is in: typestate with evidence

Stored state is dynamic, but a workflow can demand proof that it was checked. Parse, don't
validate:

```scala
object Status:
  sealed trait Planned; sealed trait Open; sealed trait Closed

final case class Voting[S] private (definition: VotingDefinition)
object Voting:
  def planned(d: VotingDefinition): Voting[Status.Planned] = Voting(d)
  extension (v: Voting[Status.Planned]) def open: Voting[Status.Open]  = Voting(v.definition)
  extension (v: Voting[Status.Open])    def close: Voting[Status.Closed] = Voting(v.definition)

enum StoredVoting:                                 // what a repository returns
  case Planned(v: Voting[Status.Planned])
  case Open(v: Voting[Status.Open])
  case Closed(v: Voting[Status.Closed])

object VotingOperations:
  def requireOpen(s: StoredVoting): Either[CastError, Voting[Status.Open]] = s match
    case StoredVoting.Open(v) => Right(v)
    case _                    => Left(CastError.VotingNotOpen)
```

`CastWorkflows.cast` takes `Voting[Status.Open]`: the type proves the check happened. Closing a
planned voting, or opening a closed one, does not compile.

## 6. Typeclasses Through `given`

`Canonical[T]` produces the exact text a signature covers (D7). Instances live in the VO's
companion so they are found without imports:

```scala
trait Canonical[T]:
  def text(t: T): CanonicalText

object BallotToken:
  given Canonical[BallotToken] = t => CanonicalText(s"ballot=${t.name}")
```

Prefer `derives` where the compiler can derive (`CanEqual`), and a hand-written `given` in the
companion otherwise. Add `Ordering` only for a stated domain reason (ledger order by `SeqNo`).

## Checklist

- [ ] Every domain primitive is an opaque type, not `type X = String`
- [ ] A refined subtype (`<: Long`) only where reading as the primitive is safe
- [ ] Every compared type has `CanEqual`; no `CanEqual` against a raw primitive
- [ ] `Either` for one invariant, `Validated` for multi-field construction
- [ ] Errors are enums; combinations are unions
- [ ] Signer-sensitive values are `Signed[P, T]`; status-sensitive workflows take typestate evidence

## Common Mistakes

**Fake VO through a transparent alias and a `Conversion`.** `type Key = String` plus
`given Conversion[String, Key]` checks nothing. Grep for `given Conversion[`; it is the smell.

**`.value` on everything, immediately.** If every caller unwraps on the first line, the type is
not doing work. Put the logic on the VO.

**A public `Signed` constructor.** Anyone could then attach a signature to arbitrary text.
Construction belongs to `Signer[P]`.

**Typestate for everything.** Use phantom states only where a workflow must not run without the
check (casting needs an open voting). For plain display, the `StoredVoting` enum is enough.
