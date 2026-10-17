---
name: static-data-memory-adapter
description: Use when implementing an in-memory adapter for a port (the demo's only storage), when seeding demo data such as votings, voters and keys, when a test needs a fast store, or when a hand-maintained list of records may contain duplicate keys
metadata:
  tags: [architecture, testing, language-agnostic]
---

# In-Memory Adapters

**Scope:** language-agnostic rules, Scala 3 examples. In this project in-memory adapters are not
a test convenience: they are the demo's storage. Atomicity is owned by
`scala3-tx-parameterized-repository`.

## Pattern

```
LedgerRepository[TX]            ← port (casting.domain.workflows.ports)
  ↑ implemented by
MemoryLedgerRepository          ← casting.infra.memory, over a MemoryCell
(future) PgLedgerRepository     ← a real database, same port
```

The port contract is identical; only the composition root's choice differs.

## Adapters Are Thin Shells

An adapter records and looks up. Decisions stay in the domain:

```scala
final class MemorySpentTokens extends SpentTokens[MemoryTx]:
  private val cell = MemoryCell(Set.empty[TokenId])

  def contains(token: TokenId)(using MemoryTx): Boolean = cell.get.contains(token)

  def markSpent(token: TokenId)(using MemoryTx): Either[CastError, Unit] =
    SpentOperations.markSpent(cell.get, token).map(next => cell.update(_ => next))
```

`SpentOperations.markSpent(current, token): Either[CastError, Set[TokenId]]` is a pure Operation
holding the rule ("already spent" means `TokenAlreadySpent`); the adapter only reads and writes
the cell.

## Seed Data

Demo seed data (the annual-meeting voting, its eligible voters, signing keys) is built in the
composition root with the builder DSL (`scala3-context-di`) and written through the normal use
cases, not poked into the cells. Seeding then exercises the same validation as the live demo,
and a one-click reset is simply "build a fresh graph and seed again".

## Testing Benefit

The same adapters back the JVM tests: no database, no fakes, deterministic.

## Common Mistakes

**Logic in the adapter.** A memory adapter that decides whether a cancel is legitimate is a
domain rule hidden in infra. Extract it into an Operation.

**Seeding by writing to cells directly.** Bypasses validation, so the demo can show states the
real system could never reach.

**Duplicate keys in a hand-written list.** A `List` of seed voters with the same pseudonym twice
silently keeps both, and lookups return whichever comes first. Build seed collections as a `Map`
keyed by the identity, or add a test asserting the keys are distinct:

```scala
test("seed voters have distinct identities"):
  val ids = Seed.voters.map(_.identity)
  assert(ids.distinct.size == ids.size)
```

**Sharing one adapter instance between two composition roots.** Each `scenario`, test and demo
reset builds its own adapters.
