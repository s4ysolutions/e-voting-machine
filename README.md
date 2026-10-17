# e-Voting Machine

An investor demo of an e-voting system built on **revocable votes** and a **public, add-only
ledger**. A voter may vote again at any time while a voting is open; the earlier vote is cancelled
by a `-1` entry that is not publicly linked to it, so a receipt shown to a coercer does not prove
the vote still counts.

- Design: [`docs/`](docs/README.md) — requirements, threat model, data model, flows, trust model
- Plan: [`PLAN.md`](PLAN.md) — the steps from design to a rehearsed demo
- Architecture rules: [`CLAUDE.md`](CLAUDE.md) and the project skills in `.claude/skills/`

## Status

The core domain is done and tested on the JVM: votings, the voter registry, ballot issuance,
casting and re-voting on a signed, hash-chained ledger, and an auditor that checks the public
invariants. The demo page has every panel: the organiser (define, import voters, open, close),
the voter's device (ballot, vote, receipt, re-vote), the public ledger and auditor (tally, checks,
editable signed lines that turn red when tampered with) and "try to cheat". Demo packaging
(scripted scenario, reset, static deploy) is plan step 7.

## Stack

Scala 3 + Scala.js, built with Mill; Laminar + Airstream for the UI; ScalaTest; Vite to serve the
page in development. Everything runs in the browser — there is no backend.

## Run

Prerequisites: JDK 21+ and Node 20.19+ or 22.12+ (required by Vite 8).

```bash
npm install                              # Vite and the npm packages the app imports

./mill -w products.demo.fastLinkJS       # terminal 1: compile and relink on every change
npm run dev                              # terminal 2: serve http://localhost:5173 with hot reload
```

Tests (run `npm install` first: the Scala.js tests load the `@noble` packages):

```bash
./mill __.testForked                     # every test module
./mill features.voting.__.testForked     # one bounded context
```

The repository includes the Mill launcher script (`./mill`); it downloads Mill 1.3.0 on first use.

## Layout

```
core/            shared kernel: Validated, UseCase[C], atomically, in-memory storage, ChangeFeed
features/        one bounded context each: voting, voters, ballots, casting, audit
                 (domain, workflows, app, infra/memory, ui)
products/wiring/ shared wiring of every context (VotingSystem)
products/harness/ JVM scenario DSL and end-to-end tests
products/demo/   composition root, page, stylesheet and Vite config
docs/            design
doc/             pitch decks (LaTeX)
```

Each bounded context is split into one Mill module per layer, so the compiler enforces the
hexagonal layer rules (see `.claude/skills/module-separation`).

## License

[CC BY-NC-ND 4.0](LICENSE): the code may be read, audited and shared non-commercially with
attribution; modified versions may not be distributed without permission.

## History

The original Lua proof of concept, an early TypeScript prototype, a Flutter template and the
protobuf API are kept on the branch [`legacy/lua-poc`](../../tree/legacy/lua-poc).
