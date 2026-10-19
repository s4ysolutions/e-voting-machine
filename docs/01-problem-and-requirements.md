# 1. Problem and requirements

## The problem

Every voting system has to keep each vote **secret** while still letting people **check** that the
votes were counted correctly.

Offline voting manages this with anonymous paper ballots and with public observers at the count.
Electronic voting loses both. There is no booth to protect the voter, and no physical ballot box for
observers to watch.

The second problem is **coercion and vote buying**. In a booth, nobody can see how you voted. With
remote e-voting, someone can stand next to you while you vote, or demand proof afterwards.

This project's answer is **revocable votes**:

- A voter can vote again at any time while the voting is open, and only the last vote counts.
- The public record never shows whether a particular vote was later revoked.

So a coerced vote, and any receipt shown to a coercer, does not prove what the final vote is. Whether
a coercer can still *infer* a revocation from timing or rare options depends on how cancellations are
published (T17, [Q6](07-open-questions.md)).

## Requirements

| ID | Requirement | How the design meets it |
|----|-------------|-------------------------|
| R1 | **Eligibility.** Only registered voters can vote | Voters Registry plus a pluggable Identity Provider. A ballot token is issued only to an eligible voter |
| R2 | **One voter, one counted vote.** Each voter's *net* contribution to the tally is at most one ballot | One first-ballot token per voter, marked in the registry. Each re-vote ballot is matched by a cancellation |
| R3 | **No ballot stuffing.** The tally cannot exceed the ballots issued, and this can be checked publicly | Public counts of issued and spent tokens. Invariants I3–I6 in [3](03-concept-and-data-model.md#public-invariants) |
| R4 | **Ballot secrecy.** Nobody can link a voter to a choice | Ballot tokens carry no identity, and casting is anonymous. In v1 the issuer is trusted not to misuse its knowledge of who got which token FIX: he lost the knowledge on the second vote anyway (see [5](05-trust-model-and-roadmap.md)) |
| R5 | **Individual verifiability.** A voter can check that their cast was recorded as intended | Each cast has a receipt (`cast_id`) that the voter can look up in the public ledger |
| R6 | **Universal verifiability.** Anyone can recompute the tally and check that the ledger is consistent | Add-only, hash-chained, signed ledger. The tally is a plain sum of deltas |
| R7 | **Coercion resistance through re-voting.** A coerced voter can secretly override the coerced vote | Cancellations are not publicly linked to the cast they cancel |
| R8 | **Immutability.** Recorded entries are never edited or deleted | The ledger only accepts appends, and changes happen through compensating `-1` entries |

### Where the requirements pull against each other

- **R5 against R7.** If a receipt proved the vote still counts, it would also prove it to a coercer.
  So a receipt proves only that the cast was **recorded**, not that it is **final**.
- **R6 against R7.** If anyone could check which cast a cancellation cancels, the coercer could
  check too. In v1, the legitimacy of each cancellation is therefore **trusted** to the operator,
  limited only by public count invariants. Zero-knowledge proofs remove that trust later
  ([5](05-trust-model-and-roadmap.md)).

Writing these trade-offs down openly is part of the pitch. They are not hidden.

## Non-goals for the demo (v1)

- Real identity verification. The demo uses a mock identity provider; real ones come later as plugins.
- Removing trust in the operator for secrecy and cancellations ([5](05-trust-model-and-roadmap.md)).
- Persistence, scaling, availability, and running on a blockchain.
- Legal compliance for public elections. The target market is still open
  ([Q8](07-open-questions.md)).
