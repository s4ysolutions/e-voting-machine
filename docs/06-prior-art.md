# 6. Prior art

The pitch decks call the conflict between secrecy and verification an "unsolvable contradiction".
Published work has addressed it for about twenty years. Naming that work up front makes the project
more credible: an investor's technical advisor will look these systems up anyway.

> Facts below are summarised from public sources and need checking before they go into the deck.

| System | Core idea | Coercion resistance | Who is trusted | Relation to this project |
|--------|-----------|---------------------|----------------|--------------------------|
| **Estonian i-voting** (national elections since 2005) | Votes are encrypted and signed with the national ID card. Re-voting is allowed, and only the last vote counts. Signatures are removed before decryption | **Re-voting**, plus a paper vote in person that overrides electronic votes | Election authority and its servers | Closest real-world use of "last vote counts". Our difference: a public add-only ledger with `±1` deltas, where Estonia keeps encrypted votes and drops the earlier ones before counting |
| **Helios** (Adida, 2008) and **Belenios** | Encrypted ballots on a public bulletin board. Homomorphic tallying or mixnets. Voters can audit how their ballot was encrypted | None: a voter can prove their vote | Trustees for decryption (threshold) | The standard for open-audit verifiability. We give up encryption-based secrecy in v1 for a simpler, explainable model |
| **JCJ** (Juels, Catalano, Jakobsson, 2005) and **Civitas** (Clarkson, Chong, Myers, 2008) | Voters can produce *fake credentials* that a coercer cannot tell from real ones. Votes cast with fake credentials are silently dropped at the tally | Strong, including the case where the coercer takes the credential (our T11) | Tally authorities (threshold) | The academic benchmark for coercion resistance. The tally is expensive and the system is hard to explain to users |
| **MACI** (Minimal Anti-Collusion Infrastructure, Ethereum Foundation / PSE, 2019→) | Voters can change their voting key and re-vote. A *coordinator* processes the messages and posts zk-SNARK proofs that the tally is correct | Re-voting and key changes. A briber cannot verify compliance | The coordinator, for privacy only. Correctness is proven | Closest in goal: both allow re-voting against bribery and coercion, and both trust a central party with vote privacy. The mechanisms differ: MACI uses encrypted messages and key changes with tally-correctness proofs, while we use public `±1` entries and cancellations. Unlike MACI, the v1 operator could also falsify cancellations (T14) |
| **Semaphore v4** (PSE, 2024) | Anonymous membership in a group with zero-knowledge proofs and nullifiers | n/a (a building block) | No trusted operator at runtime. Its Groth16 circuits rely on a one-time trusted-setup ceremony, which must have had at least one honest participant | Candidate building block for ZK cancellations (RM2) |

## Where this project can stand out

1. **Explainability.** A public ledger of `+1`/`-1` entries and a tally anyone can add up in a
   spreadsheet. That is easier to explain to non-experts than mixnets or homomorphic encryption.
2. **Re-voting as the main anti-coercion tool, without a separate tally step.** Estonia and JCJ remove
   earlier votes at tally time. Here, cancellation happens inside the add-only ledger itself.
3. **An honest, staged trust model.** The demo's v1 operator is trusted with vote privacy, as MACI's
   coordinator is, and also with the legitimacy of cancellations, which MACI does not need. The
   roadmap (RM1–RM4) gives a concrete path to remove both.
4. **Pluggable identity.** Organisations bring their own identity source, from email to eID.

## To read before the pitch

- Juels, Catalano, Jakobsson: *Coercion-Resistant Electronic Elections* (2005)
- Clarkson, Chong, Myers: *Civitas: Toward a Secure Voting System* (2008)
- Adida: *Helios: Web-based Open-Audit Voting* (2008)
- MACI documentation (maci.pse.dev) and Vitalik Buterin's original MACI post (2019)
- Estonian National Electoral Committee: i-voting documentation
- RFC 9474: RSA Blind Signatures
