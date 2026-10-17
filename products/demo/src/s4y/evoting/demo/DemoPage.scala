package s4y.evoting.demo

import com.raquo.laminar.api.L.*
import s4y.evoting.voting.ui.owner.OwnerPanel
import s4y.evoting.voting.ui.owner.state.OwnerSession

/** Page layout. Panels for the voter, the public ledger and "try to cheat" arrive in plan step 6. */
object DemoPage:
  def apply(owner: OwnerSession): HtmlElement =
    div(
      cls := "page",
      headerTag(h1("e-Voting Machine"), p(cls := "subtitle", "Investor demo — everything runs in this browser tab")),
      mainTag(cls := "panels", OwnerPanel(owner))
    )
