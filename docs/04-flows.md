# 4. Flows

Each flow has a sequence diagram. Every rejection drawn here is one the demo must actually show.
Flows F1–F7 are the normal use cases. F8 lists the cheating attempts for the demo's "try to cheat" panel.

- [F1. Set up a voting](#f1-set-up-a-voting)
- [F2. Get a ballot](#f2-get-a-ballot)
- [F3. Cast](#f3-cast)
- [F4. Re-vote](#f4-re-vote)
- [F5. Close and tally](#f5-close-and-tally)
- [F6. Audit](#f6-audit)
- [F7. Coercion scenario](#f7-coercion-scenario)
- [F8. Cheating attempts](#f8-cheating-attempts)

---

## F1. Set up a voting

```mermaid
sequenceDiagram
    autonumber
    actor O as Voting Owner
    participant VR as Voting Registry
    participant VoR as Voters Registry

    O->>VR: create voting(title, subjects, options), signed by owner
    VR->>VR: validate IDs and options (plain data, no code)
    VR-->>O: voting_id, status PLANNED
    O->>VoR: import eligible voters(voting_id, identities)
    VoR->>VoR: pseudonym = fresh random value for each voter in this voting
    VoR-->>O: public registry view (pseudonyms, ballot_issued = false)
    O->>VR: open(voting_id), signed by owner
    VR-->>O: status OPEN, opened_at
```

## F2. Get a ballot

```mermaid
sequenceDiagram
    autonumber
    actor V as Voter App
    participant IdP as Identity Provider
    participant BI as Ballot Issuer
    participant VR as Voting Registry
    participant VoR as Voters Registry

    V->>IdP: authenticate
    IdP-->>V: identity assertion
    V->>V: s = 32 random bytes, token_id = H("token" ‖ s)
    V->>BI: request first ballot(voting_id, assertion, token_id)
    BI->>VR: is the voting OPEN?
    BI->>VoR: eligible and ballot_issued = false?
    alt same voter retries with the same token_id
        BI-->>V: the same issuer_sig again (idempotent, nothing changes)
    else not eligible, already issued for another token_id, or voting not open
        BI-->>V: rejected (reason)
    else ok
        rect rgba(127, 127, 127, 0.15)
            Note over BI,VoR: one atomic commit
            BI->>BI: issuer_sig = Sig_issuer(voting_id ‖ token_id)
            BI->>VoR: set ballot_issued = true, remember token_id for retries
            BI->>BI: first_ballots + 1
        end
        BI-->>V: issuer_sig
        V->>V: store s and issuer_sig on the device
    end
    Note over BI: v1 trusts the issuer not to misuse the (voter, token_id) link it keeps for retries.<br/>Roadmap: blind signature, so the issuer only ever sees a blinded value.
```

## F3. Cast

```mermaid
sequenceDiagram
    autonumber
    actor V as Voter App
    participant CS as Cast Service
    participant VR as Voting Registry
    participant ST as Spent Tokens
    participant CL as Cast Ledger

    V->>V: cast_id = H("cast" ‖ s)
    V->>CS: cast(voting_id, token_id, issuer_sig, cast_id, one option per subject)
    CS->>VR: voting OPEN? every subject answered with a valid option?
    CS->>CS: verify issuer_sig on token_id
    CS->>ST: token_id already spent?
    CS->>CL: cast_id already used?
    alt retry of an already committed cast (same token_id, cast_id and options)
        CS-->>V: the existing receipt (idempotent, nothing appended)
    else any check fails
        CS-->>V: rejected (reason)
    else ok
        rect rgba(127, 127, 127, 0.15)
            Note over CS,CL: one atomic commit, all or nothing
            CS->>ST: add (token_id, issuer_sig)
            CS->>CL: append one +1 entry per subject, all with cast_id and token_id
            CL-->>CS: signed entries (seq, prev_hash, operator_sig)
        end
        CS-->>V: receipt = cast_id plus the signed entries
        V->>CL: look up cast_id
        CL-->>V: entries match the chosen options (recorded as intended)
    end
```

`cast_id` is computed by the voter. If a voter sends something that is not `H("cast" ‖ s)`, the only
effect is that they cannot re-vote later. A duplicate `cast_id` is rejected.

## F4. Re-vote

```mermaid
sequenceDiagram
    autonumber
    actor V as Voter App
    participant CS as Cast Service
    participant CL as Cast Ledger
    participant BI as Ballot Issuer

    V->>V: s2 = 32 random bytes, token_id2 = H("token" ‖ s2)
    V->>CS: revote(voting_id, s, token_id2), sent privately
    CS->>CS: token_id = H("token" ‖ s), cast_id = H("cast" ‖ s)
    CS->>CS: voting OPEN, token_id spent, cast_id in ledger, cast_id not cancelled?
    alt retry of an already committed re-vote (same s and token_id2)
        CS-->>V: the same issuer_sig2 again (idempotent, nothing appended)
    else any check fails
        CS-->>V: rejected (reason)
    else ok
        rect rgba(127, 127, 127, 0.15)
            Note over CS,BI: one atomic commit, all or nothing
            CS->>CS: add cast_id to cancelled casts with token_id2 (private)
            CS->>CL: append one -1 entry per subject for the options of cast_id<br/>(fresh entry_ids, no cast_id)
            CS->>BI: issue re-vote ballot(token_id2)
            BI->>BI: issuer_sig2 = Sig_issuer(voting_id ‖ token_id2), revote_ballots + 1
            BI-->>CS: issuer_sig2
        end
        CS-->>V: issuer_sig2
        V->>V: replace s with s2
        Note over V: The voter now casts the new ballot (F3).<br/>They may also leave it unused, which means abstaining.
    end
```

The voter's old receipt stays in the ledger unchanged. Whether `-1` entries are published right away or
in shuffled batches is [Q6](07-open-questions.md).

### Consistency and retries (F2–F4)

- **Every state change is one atomic commit.** Nothing is partly applied: a ballot is not marked issued
  without its signature and count, a token is not marked spent without all of its `+1` entries, and a
  cast is not cancelled without its `-1` entries and its new ballot. The demo runs all services in one
  process, so each commit is a single in-memory transaction. A real backend needs one database
  transaction, or a write-ahead log across the services.
- **A failure before the commit changes nothing**, so the voter can simply try again.
- **Retries are idempotent**, keyed by values the voter already holds: `token_id` in F2,
  `token_id` + `cast_id` in F3, and `s` + `token_id2` in F4. If a response is lost after the commit,
  repeating the request returns the same result instead of an error, so no ballot is ever stranded.
- **The cost of F2 retries:** to recognise a retry, the issuer keeps `token_id` next to the voter's
  pseudonym. That is exactly the link T12 trusts the issuer not to misuse. With blind signatures (RM1)
  it keeps only the blinded value, which reveals nothing.

## F5. Close and tally

```mermaid
sequenceDiagram
    autonumber
    actor O as Voting Owner
    participant VR as Voting Registry
    participant CS as Cast Service
    participant CL as Cast Ledger
    actor A as Anyone

    O->>VR: close(voting_id), signed by owner
    VR->>CS: stop accepting new casts and re-votes
    CS->>CS: wait until every request accepted before close has committed or failed
    CS->>CL: publish final head hash, signed by operator (nothing is appended after it)
    VR-->>O: status CLOSED, closed_at
    A->>CL: read all entries
    A->>A: tally[subject][option] = sum of deltas
    A-->>A: result, tied to the final head hash
```

## F6. Audit

```mermaid
sequenceDiagram
    autonumber
    actor A as Auditor
    participant CL as Cast Ledger
    participant ST as Spent Tokens
    participant VoR as Voters Registry
    participant BI as Ballot Issuer
    participant VR as Voting Registry

    A->>VR: voting definition, open and close events, owner signatures
    A->>CL: all entries and final head hash
    A->>A: I1 hash chain and signatures
    A->>A: I2 running sums never negative
    A->>ST: spent tokens with issuer signatures
    A->>A: I3 every cast backed by its own valid spent token, one +1 per subject
    A->>VoR: public registry view
    A->>BI: issuance counts
    A->>A: I4 first ballots match flags, I5 re-vote ballots match cancellations, I6 net casts capped
    A->>A: I7 all entries inside the open and close window
    A->>A: recompute tally
    alt every check passes
        A-->>A: published result confirmed
    else a check fails
        A-->>A: report the violated invariant and the entry seq
    end
```

## F7. Coercion scenario

```mermaid
sequenceDiagram
    autonumber
    actor C as Coercer
    actor V as Voter
    participant CS as Cast Service
    participant L as Public Ledger

    C->>V: vote "yes" while I watch and show me the receipt
    V->>CS: cast yes (F3)
    CS->>L: +1 yes, receipt r1
    V-->>C: shows r1
    C->>L: look up r1
    L-->>C: r1 = +1 yes, recorded
    Note over V: Later, alone
    V->>CS: re-vote (F4), sent privately with s
    CS->>L: -1 yes, with no receipt, in a later batch
    V->>CS: cast no (F3) with the new ballot
    CS->>L: +1 no, receipt r2
    C->>L: look up r1 again
    L-->>C: r1 still +1 yes, unchanged
    Note over C,L: No -1 entry refers to r1. If enough other voters cancelled "yes" on the same subject in the same batch,<br/>the coercer cannot tell whether r1 still counts.
```

This assumes the coercer does not stay with the voter until the voting closes (T10) and does not take
`s` (T11), and that enough other cancellations of the **same subject and option** are published in the
same window, so neither timing nor a rare option singles out this one (T17). See
[2](02-actors-and-threat-model.md).

## F8. Cheating attempts

Each of these is a button in the demo's "try to cheat" panel. Each must be rejected or detected
visibly, with a reason that refers to the design.

| # | Attempt | Where it is stopped | Reason shown |
|---|---------|---------------------|--------------|
| X1 | Request a second first ballot | Ballot Issuer (F2) | "Ballot already issued to this voter" (T2) |
| X2 | Cast the same token twice | Cast Service (F3) | "Token already spent" (T3) |
| X3 | Cast with a forged or altered issuer signature | Cast Service (F3) | "Invalid issuer signature" (T4) |
| X4 | Cast with two options for one subject, or skip a subject | Cast Service (F3) | "Invalid ballot" (T5) |
| X5 | Re-vote with a made-up secret | Cast Service (F4) | "No cast for this secret" (T6) |
| X6 | Re-vote twice with the same secret | Cast Service (F4) | "Cast already cancelled" (T7) |
| X7 | Cast or re-vote after the voting closes | Cast Service (F3/F4) | "Voting is not open" (I7) |
| X8 | Create a subject whose ID contains code or quotes | Voting Registry (F1) | "Invalid identifier" (T16) |
| X9 | Edit an entry in a copy of the ledger | Auditor (F6) | "I1 failed at entry seq N" (T15) |
| X10 | Add a `-1` entry without a cancellation | Auditor (F6) | "I5 failed: re-vote ballots ≠ cancellations" |

```mermaid
sequenceDiagram
    autonumber
    actor X as Cheating voter
    participant BI as Ballot Issuer
    participant CS as Cast Service

    X->>BI: X1 request a second first ballot
    BI-->>X: rejected: ballot already issued
    X->>CS: X2 cast an already spent token
    CS-->>X: rejected: token already spent
    X->>CS: X3 cast with a forged issuer_sig
    CS-->>X: rejected: invalid issuer signature
    X->>CS: X5 revote with a made-up secret
    CS-->>X: rejected: no cast for this secret
    X->>CS: X6 revote twice with the same secret
    CS-->>X: rejected: cast already cancelled
```
