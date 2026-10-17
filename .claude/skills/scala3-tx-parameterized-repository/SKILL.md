---
name: scala3-tx-parameterized-repository
description: Use when designing a repository or store port that must stay swappable (in-memory now, a real database later), when implementing atomically { … } with rollback, when a port method needs to run inside an atomic commit, or when an adapter's concrete transaction type is leaking into domain or app code
metadata:
  tags: [scala3, architecture, context-functions, transactions]
---

# TX-Parameterized Repository

**Scope:** Scala 3 context parameters and context functions; no effect library.

## Overview

Port traits carry the transaction context as a **type parameter** and receive it as a **context
parameter**. Workflows open an atomic commit with `atomically { … }`, whose body is a context
function, so every repository call inside it gets the transaction without mentioning it. The
concrete TX type is chosen once, at the composition root (`type TXM = MemoryTx`).

The demo has only an in-memory store. The parameter costs almost nothing and keeps the door open
for a real database: only adapters and the root would change.

## Core Pattern

```scala
// core.tx — pure, cross-compiled
trait Tx

trait TransactionManager[TX <: Tx]:
  def atomically[E, A](label: String)(body: TX ?=> Either[E, A]): Either[E | InfraFailure, A]

// casting.domain.workflows.ports
trait SpentTokens[TX <: Tx]:
  def markSpent(token: TokenId)(using TX): Either[CastError, Unit]   // Left(TokenAlreadySpent)
  def contains(token: TokenId)(using TX): Boolean

trait LedgerRepository[TX <: Tx]:
  def appendCast(castId: CastId, token: TokenId, choices: Choices)(using TX): Seq[LedgerEntry]
  def entries(using TX): Seq[LedgerEntry]
```

```scala
// workflow: the transaction flows from atomically to every call in the block
tm.atomically[CastError, Receipt]("cast") {
  for
    _       <- spent.markSpent(token.id)
    entries  = ledger.appendCast(castId, token.id, choices)
  yield Receipt(castId, entries)
}
```

## In-Memory Implementation with Rollback

The atomic-commit guarantee from the design (§ "Consistency and retries") must hold even in
memory: a `Left` or an exception rolls back every write in the block.

```scala
// core.infra.memory
final class MemoryTx private[memory] () extends Tx:
  private[memory] val undo = scala.collection.mutable.ListBuffer.empty[() => Unit]

final class MemoryTransactionManager extends TransactionManager[MemoryTx]:
  def atomically[E, A](label: String)(body: MemoryTx ?=> Either[E, A]): Either[E | InfraFailure, A] =
    val tx = MemoryTx()
    val result: Either[E | InfraFailure, A] =
      try body(using tx)
      catch case scala.util.control.NonFatal(t) => Left(InfraFailure(s"$label: ${t.getMessage}"))
    if result.isLeft then tx.undo.reverseIterator.foreach(_())
    result

/** One piece of state; every write registers its undo with the current transaction. */
final class MemoryCell[S](initial: S):
  private var state: S = initial
  def get(using @scala.annotation.unused tx: MemoryTx): S = state   // tx is a capability: proof of being inside atomically
  def update(f: S => S)(using tx: MemoryTx): Unit =
    val before = state
    tx.undo += (() => state = before)
    state = f(state)
```

Writes are only possible with a `MemoryTx` in scope, so a write outside `atomically` does not
compile. Reads also require it, which keeps every read inside the same consistent unit as the
writes that depend on it.

## Rules

- `TX` appears only in port traits, their adapters and `TransactionManager`.
- Every port method that reads or writes state takes `(using TX)`.
- ID parameters are typed (`TokenId`, not `String`); see `domain-identity-types`.
- `MemoryTx` is named only in `core.infra.memory`, feature `infra.memory` adapters and the
  composition root.
- No nested `atomically`. A workflow that needs two atomic units calls it twice, one after the
  other.

## Common Mistakes

**`MemoryTx` in a use case or workflow signature.** Use a generic `TX <: Tx`; the root picks the
type.

**Forgetting `(using TX)` on a port method.** The method then runs outside the atomic unit and its
write is never rolled back.

**Reading outside the block and writing inside it.** The check and the write must be in the same
`atomically` call, or a second request can slip in between (in a real backend; in the demo it
hides the bug until then).

**Mixing two TX types in one workflow.** One workflow, one transaction type. If two stores need
different transaction types, split the work into two workflows.
