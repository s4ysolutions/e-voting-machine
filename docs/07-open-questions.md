# 7. Open questions

**Blocking** means step 2 of [`PLAN.md`](../PLAN.md) (freeze the v1 design) needs an answer first.

| ID | Question | Options | Proposal | Blocking |
|----|----------|---------|----------|:--------:|
| Q1 | Is the v1 trust model (D4, operator trusted for secrecy and cancellations) acceptable for the demo? | (a) yes, and say so openly. (b) build blind signatures (RM1) into the demo. (c) build ZK cancellations (RM2) into the demo | (a) and show RM1/RM2 on the roadmap slide. (b) is a possible stretch goal, about 2–3 days | ✅ |
| Q2 | Are subjects only yes/no, or multiple options per subject? | (a) yes/no only. (b) N options, pick one. (c) ranked or approval voting | (b): it covers yes/no and keeps the tally a simple sum | ✅ |
| Q3 | Does a re-vote cancel the whole ballot, or a single subject? | (a) whole ballot (D5). (b) per subject | (a): fewer events that could link a voter's actions. Cancellation entries are still published per subject to avoid option-pattern fingerprints. With RM2, enforcing "all subjects cancelled" without linking the per-subject records is still open | ✅ |
| Q4 | How do we handle last-minute coercion (T10)? | (a) accept it as a limit. (b) allow an in-person or out-of-band override after close, like Estonia's paper vote. (c) a grace period with secret re-vote | (a) for the demo, and name it on the roadmap | |
| Q5 | What happens if a coercer takes the ballot secret `s` (T11)? | (a) accept it as a limit. (b) let the voter recover through the IdP, which issues a "revoke all my casts" request. That is hard without linking. (c) JCJ-style fake secrets | (a) for the demo. Research (c) for later | |
| Q6 | How large is the anonymity set for cancellations, and do we use batching (T17)? | (a) publish immediately. (b) publish `-1` entries in shuffled batches every K minutes, or once at least M cancellations of the same subject and option are waiting. (c) operator dummy pairs (`+1`/`-1` with the same option and net zero) | (b) in the demo, since it is easy to show and to explain | |
| Q7 | Is a receipt shown to the voter as a QR code or ID, and can the voter later prove their cast to a third party? | Proving a cast is fine, because it does not prove finality | Show `cast_id` and a "find my vote" link | |
| Q8 | Which first market? | (a) associations and unions. (b) shareholder votes. (c) universities. (d) DAOs and Web3. (e) public elections | Not decided. It shapes the IdP plugins, the trust story and the deck | ✅ |
| Q9 | What happens to the existing `protobuffers/` API? | (a) rewrite to match this design. (b) drop it for the demo, since everything runs in the browser, and re-introduce it with the backend | (b) | |
| Q10 | Code license | (a) keep CC BY-NC-ND. (b) source-available, e.g. BSL. (c) open source, e.g. Apache-2.0 or AGPL-3.0 | Not decided. Auditing needs at least the right to publish patches | |
| Q11 | Do we keep the term "smart contract" in the pitch? | (a) yes, explained as a "published deterministic rule". (b) no, use "ledger rule" | (a) for continuity with the deck, with an explicit note that no blockchain is needed | |
