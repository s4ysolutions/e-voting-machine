package s4y.evoting.audit.ui.ledger.components

import com.raquo.laminar.api.L.*
import s4y.evoting.voting.domain.vo.VotingDefinition

/** Totals recomputed from the public ledger alone. */
object TallyTable:
  def apply(definition: VotingDefinition, tally: Map[(String, String), Int]): HtmlElement =
    table(
      cls := "tally",
      thead(tr(th("Subject"), th("Option"), th("Votes"))),
      tbody(
        definition.subjects.flatMap(s =>
          s.options.map(o => tr(td(s.key.value), td(o.value), td(cls := "num", tally.getOrElse((s.key.value, o.value), 0).toString)))
        )
      )
    )
