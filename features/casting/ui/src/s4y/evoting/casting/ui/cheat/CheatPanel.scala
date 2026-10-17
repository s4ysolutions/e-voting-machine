package s4y.evoting.casting.ui.cheat

import com.raquo.laminar.api.L.*
import s4y.evoting.casting.ui.cheat.state.{Cheat, CheatOutcome, CheatSession}

/** Entry point of the "try to cheat" panel (skill: ui-component-container-pattern). */
object CheatPanel:
  def apply(session: CheatSession): HtmlElement =
    div(
      cls := "panel panel-cheat",
      h2("Try to cheat"),
      p(cls := "hint", "Mallory is a registered voter who wants more than one vote. Each button runs a real attack against the open voting."),
      div(
        cls := "cheats",
        Cheat.values.toList.map(c => button(title := c.attempt, c.title, onClick --> (_ => session.attempt(c))))
      ),
      div(
        cls := "outcomes",
        role := "log",
        children <-- session.outcomes.map(_.map(render))
      )
    )

  private def render(o: CheatOutcome): HtmlElement =
    div(
      cls := "outcome",
      cls(if o.rejected then "rejected" else "accepted"),
      div(cls := "outcome-title", o.cheat.title),
      div(cls := "outcome-attempt", o.cheat.attempt),
      o.steps.map(s => div(cls := "outcome-step", s)),
      div(cls := "outcome-result", (if o.rejected then "✗ " else "⚠ ") + o.result)
    )
