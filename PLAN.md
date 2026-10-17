# E-Voting Machine: Plan

**Goal:** a working investor demo. Every button shown in the demo works, and anything not built goes
on the roadmap slide instead of into the UI. This plan does not aim at a production-ready product.

Effort estimates are for one experienced developer.

---

- [ ] **1. Start the Mermaid design doc in `docs/`** (3–5 days)
  - [x] Problem statement and requirements: [`docs/01-problem-and-requirements.md`](docs/01-problem-and-requirements.md)
  - [x] Actors and threat model: [`docs/02-actors-and-threat-model.md`](docs/02-actors-and-threat-model.md)
  - [x] Concept, components and data model: [`docs/03-concept-and-data-model.md`](docs/03-concept-and-data-model.md)
  - [x] Flows as sequence diagrams: [`docs/04-flows.md`](docs/04-flows.md)
  - [x] Trust model and roadmap: [`docs/05-trust-model-and-roadmap.md`](docs/05-trust-model-and-roadmap.md)
  - [x] Prior art: [`docs/06-prior-art.md`](docs/06-prior-art.md)
  - [x] Open questions: [`docs/07-open-questions.md`](docs/07-open-questions.md)
  - [ ] Review pass by the author; correct anything that misstates the idea

- [ ] **2. Settle the v1 design**
  - [ ] Resolve every blocking item in [`docs/07-open-questions.md`](docs/07-open-questions.md)
  - [ ] Confirm or change the proposed decisions in [`docs/README.md`](docs/README.md#decision-log)
  - [ ] Freeze the v1 design (tag `design-v1`)

- [ ] **3. External sanity check**
  - [ ] Walk an e-voting or cryptography practitioner through the design and threat model
  - [ ] Record the feedback and any design changes it causes
  - [ ] Write the "how we differ from MACI, Estonia, Helios and JCJ" positioning

- [x] **4. Repository restructuring**
  - [x] Top-level `README.md`: what this is, current status, how to run the demo
  - [x] License: keep CC BY-NC-ND 4.0 (Q10)
  - [x] Archive the original Lua proof of concept: kept on branch `legacy/lua-poc`, removed from `main`
  - [x] Remove the unused Flutter template `ui/`
  - [x] Remove `web-ui/` (TypeScript prototype) and the ts-proto `package.json`
  - [x] Add a root `package.json` listing Vite and the `@noble/*` packages
  - [x] Move the pitch-deck PDF out of `out/`, which becomes Mill's build directory
  - [x] Set up the Mill build: core modules, one bounded context (`voting`), the demo product, Vite (skill `mill-module-layout`)
  - [x] Retire `protobuffers/` (Q9); kept on branch `legacy/lua-poc`
  - [x] CI on GitHub Actions: compile, tests, Scala.js link

- [ ] **5. Core domain in Scala 3 (pure and tested)** (4–6 days)
  - [x] `core`: `Validated`, crypto capabilities with noble and JDK adapters, `Identifier`, `atomically`, `UseCase[C]`
  - [x] Voting Registry (define, open, close) and Voters Registry (import, pseudonyms, ballot-issued flag)
  - [x] Ballot Issuer (Ed25519-signed ballot tokens)
  - [x] Cast Service: cast, cancel and re-vote, spent-token and cancelled-cast checks
  - [x] Append-only, hash-chained, signed Cast Ledger
  - [ ] Tally plus an auditor that checks the public invariants (I1–I7)
  - [ ] ScalaTest on the JVM for every flow and every rejection path, plus the scenario DSL

- [ ] **6. Demo UI** (5–8 days)
  - [ ] **Owner** panel: create voting, import voters, open and close
  - [ ] **Voter** panel: authenticate (mock), get ballot, vote, receipt, re-vote
  - [ ] **Public ledger and auditor** panel: entries, recompute tally, verify signatures and invariants
  - [ ] Readable signed text everywhere (decision D7 in `docs/README.md`), with an editable ledger line
        that turns red when tampered with and green again when restored
  - [ ] **"Try to cheat"** panel: double ballot, token replay, forged signature,
        cancel without the secret, double cancel. Each one is visibly rejected.

- [ ] **7. Demo packaging** (3–4 days)
  - [ ] Scripted scenario with seeded voters and simulated background traffic
  - [ ] Coercion walkthrough: the coercer sees the receipt, the voter re-votes, the coercer cannot tell
  - [ ] One-click reset with deterministic seed data
  - [ ] Static deploy (GitHub Pages) with no backend

- [ ] **8. Pitch deck update**
  - [ ] Align the slides with the design doc's terms and diagrams
  - [ ] Add prior art and positioning, and state the trust model honestly
  - [ ] Roadmap slide: everything deliberately left out of the demo

- [ ] **9. Rehearsal and freeze** (2–3 days)
  - [ ] Three full dry runs, including the "try to cheat" panel and Q&A on the trust model
  - [ ] Freeze the demo build and tag `demo-v1`

---

### Later, after investment (roadmap only, not part of this plan)

- Blind-signature ballot issuance, so the issuer cannot link a voter to a token
- Zero-knowledge cancellations, so the operator cannot fake a cancel (Semaphore-style nullifiers)
- Anchor the ledger head on a public blockchain or a transparency log
- Real identity providers (eID, bank ID, email with risk scoring)
- Persistence, scaling, multi-operator or threshold setup, and an external security audit
