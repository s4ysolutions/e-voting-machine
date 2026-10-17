package s4y.evoting.audit.ui.ledger.components

import com.raquo.laminar.api.L.*
import s4y.evoting.casting.domain.vo.LedgerEntry
import s4y.evoting.core.crypto.{Party, Verifier}
import s4y.evoting.core.ui.{SignedLine, SignerLabel}

/** Looks a receipt up on the public ledger (design doc Q7, F7). Whoever holds a receipt (the
  * voter, or a coercer who was shown it) sees its +1 entries. A cancellation names no receipt,
  * so the lookup looks the same whether or not the vote was later changed. */
object FindMyVote:
  def apply(entries: Signal[List[LedgerEntry]])(using Verifier[Party.Operator]): HtmlElement =
    val receipt = Var("")
    div(
      cls := "find-vote",
      label("Find a vote by its receipt", input(placeholder := "c-1a2b", controlled(value <-- receipt.signal, onInput.mapToValue --> receipt))),
      child <-- receipt.signal.combineWith(entries).map { (raw, all) =>
        val wanted = raw.trim.toLowerCase
        if wanted.isEmpty then emptyNode
        else
          val found = all.filter(_.value.castId.exists(_.name == wanted))
          if found.isEmpty then div(cls := "empty", s"No entry carries receipt $wanted.")
          else
            div(
              found.map(e => SignedLine(e, SignerLabel.Operator)),
              p(cls := "hint", "This is all anyone can find for a receipt. Cancellations carry no receipt, so this view does not change if the voter re-votes.")
            )
      }
    )
