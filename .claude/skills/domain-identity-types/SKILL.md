---
name: domain-identity-types
description: Use when introducing an ID into the domain (token_id, cast_id, pseudonym, entry_id, seq), when deciding whether an ID is derived from a secret, assigned by a store, or business-assigned, when deciding who may construct one, when the same record has a private and a public projection that must share one ID, or when showing an ID as a readable name (D7)
metadata:
  tags: [scala3, architecture, domain-modeling]
---

# Domain Identity Types

**Scope:** Scala 3. Adapted from the original skill to this design's three kinds of identifier.

## Rule 0: Who creates the ID, and when

| Kind | Examples | Created by | Modelled as |
|------|----------|-----------|-------------|
| **Derived** from a secret | `TokenId = H("token" ‖ s)`, `CastId = H("cast" ‖ s)` | one pure derivation function | opaque VO with a single derivation constructor |
| **Assigned** by a store | `Pseudonym` (Voters Registry), `EntryId`, `SeqNo` (ledger) | the adapter, at insert time | `Identifier[A]`: domain may carry, never mint |
| **Business-assigned** | `VotingKey`, `SubjectKey` | the voting owner, validated | plain VO (`domain-value-objects`); name it `Key`, not `Id` |

Name only store-assigned surrogates `Id`/`Identifier`. A `Key` the owner types in is a VO.

## Derived IDs

A derived ID has exactly one constructor: the derivation. Anything else (a public `apply(String)`)
would let code invent a token that no secret produced.

```scala
opaque type TokenId = Digest
object TokenId:
  def derive(secret: BallotSecret)(using crypto: Crypto): TokenId =
    crypto.sha256(Bytes.utf8("token") ++ secret.bytes)
  given CanEqual[TokenId, TokenId] = CanEqual.derived
```

The UI may need to *parse* one (the auditor pastes a token ID). That parser lives at the ui
boundary and returns `Either`; it is the only other way in.

## Store-Assigned IDs: `Identifier[A]`

```scala
// core.identity — types only; domain-visible; no mint, no raw accessor
package s4y.evoting.core.identity

final class Identifier[A] private (private[identity] val value: String):
  override def equals(that: Any): Boolean = that match
    case other: Identifier[?] => value == other.value
    case _                    => false
  override def hashCode: Int = value.hashCode
object Identifier:
  private[identity] def unsafe[A](value: String): Identifier[A] = new Identifier(value)
  given [A]: CanEqual[Identifier[A], Identifier[A]] = CanEqual.derived
```

```scala
// core.identity.mint — separate module; only adapters and the ui boundary depend on it
package s4y.evoting.core.identity.mint

extension (raw: String) def identifier[A]: Identifier[A] = Identifier.unsafe[A](raw)
extension [A](id: Identifier[A]) def raw: String = id.value
```

The module graph is the wall: domain modules do not depend on `core.identity.mint`, so they can
carry, compare and store an `Identifier[Voter]` but cannot fabricate one.

Not a `case class`: `apply`, `copy` and `unapply` would all be public mints.

## Parameterize on the Concept, Not the Projection

The Voters Registry has a private record (identity, pseudonym, flag) and a public view
(pseudonym, flag). Both are the same voter, so both share one ID type:

```scala
sealed trait Voter                                              // the concept: shapeless
final case class RegisteredVoter(identity: VoterIdentity, ballotIssued: Boolean) extends Voter
final case class PublicVoter(ballotIssued: Boolean) extends Voter

def publicView(id: Identifier[Voter]): Option[PublicVoter]      // not Identifier[PublicVoter]
```

Parameterizing on a projection (`Identifier[PublicVoter]`) would give one row two incompatible ID
types. No `VoterId` marker trait and no `type VoterId = Identifier[Voter]` alias: write
`Identifier[Voter]`.

## Carry, Resolve, Construct

- **Carry**: fields, map keys, pass-through, equality. Fine anywhere.
- **Resolve**: looking an ID up is a port call, so it is a Workflow. An Operation taking an ID in
  order to look something up is mistyped.
- **Construct**: derived IDs through their derivation, store-assigned IDs only in adapters (with
  `core.identity.mint`), parsed IDs only at the ui boundary.

## Readable Names (D7)

`amber-falcon-42` and `c-71ab` are **display**, derived deterministically from the ID's bytes by
a pure function (`ReadableName.of(tokenId)`). They are never parsed back into an ID and never used
as a key: two IDs could share a name.

## No Ordering on Surrogates

Sorting by a store-assigned ID sorts by insertion accident. The ledger's order is `SeqNo`, which
has an `Ordering` because "ledger order" is a domain concept; `EntryId` has none.

## Checklist

- [ ] Each ID is classified as derived, store-assigned or business-assigned
- [ ] Derived IDs have exactly one constructor (the derivation) plus at most a ui-boundary parser
- [ ] Store-assigned IDs are `Identifier[Concept]`, minted only through `core.identity.mint`
- [ ] No raw accessor on `Identifier` outside the mint module
- [ ] Projections share the concept's ID type
- [ ] Readable names are display only

## Common Mistakes

**`TokenId.apply(hex: String)` in the companion.** A public mint for a derived ID. Use the
derivation, and a separate boundary parser.

**A `Key` called `Id`.** `VotingId` that the owner types in is a business key. Calling it `Id`
invites applying the surrogate rules to it.

**Looking up by readable name.** Names collide; look up by ID.
