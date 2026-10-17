---
name: ui-component-container-pattern
description: Use when a Laminar panel (Owner, Voter, Public ledger and auditor, Try to cheat) grows past one leaf component and needs an entry point, when deciding where related UI components belong, or when Main.scala or DemoPage accumulates inlined panel content that should be extracted
metadata:
  tags: [scala3, scalajs, laminar, frontend, ui]
---

# UI Component Container Pattern

**Scope:** Scala 3, Scala.js, Laminar, code under `features.<feature>.ui`.

## Overview

React marks a folder's public entry with `index.tsx`. Scala has no such file, so a container
object sitting next to its own leaves looks just like them. Fix this structurally: leaves go one
package down into `components/`, and the container stays alone at the package root. Position
marks the entry point.

## Layout

```
casting/ui/voter/
  VoterPanel.scala          <- container, alone at the root = entry
  components/
    BallotCard.scala
    ChoicesForm.scala
    ReceiptCard.scala
  state/
    VoterSession.scala      <- state holder (usecase-airstream-bridge)
```

```scala
package s4y.evoting.casting.ui.voter

import com.raquo.laminar.api.L.*
import s4y.evoting.casting.ui.voter.components.{BallotCard, ChoicesForm, ReceiptCard}
import s4y.evoting.casting.ui.voter.state.{VoterSession, VoterState}

object VoterPanel:
  def apply(session: VoterSession): HtmlElement =
    div(
      cls := "panel panel-voter",
      child <-- session.state.map {
        case VoterState.NoBallot        => BallotCard.request(session)
        case VoterState.HasBallot(b)    => ChoicesForm(session, b)
        case VoterState.Voted(receipt)  => ReceiptCard(receipt)
        case VoterState.Failed(message) => div(cls := "rejected", message)
      }
    )
```

Callers (`DemoPage`) import only `VoterPanel` and never reach into `components`.

## Styling

No component library. One plain stylesheet with CSS variables, including one colour per signer
for D7 (`--signer-issuer`, `--signer-operator`, `--signer-owner`). Components set `cls`, never
inline colours. The shared D7 line component (`SignedLine`) lives in `core.ui`.

## Rules

- **The container takes its state holder(s) as parameters**, nothing global. Then it can be
  dropped into another page, a test or a demo reset with one call.
- **Container name matches the package** (`VoterPanel` in `voter`).
- **Leaves live in `components/`.** If a leaf must be used from outside, it is a second container
  or a `core.ui` component.
- **Shared state the container does not own stays a parameter** (the `ChangeFeed`, another
  panel's holder).
- **One container per package.**

## When Not to Use

A panel with a single leaf and no local state. Extract a container once a section combines two
or more components.

## Common Mistakes

**Container and leaves flat in one package.** Nothing shows which file is the entry.

**A container reading a global `Var` or `Main.given`.** Breaks reuse; pass it in.

**A container taking the whole app's state.** If a parameter exists only for one deep leaf,
reconsider whether that leaf belongs in this panel.
