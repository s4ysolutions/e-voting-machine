package s4y.evoting.demo

import com.raquo.laminar.api.L.*
import s4y.evoting.audit.ui.ledger.LedgerPanel
import s4y.evoting.audit.ui.ledger.state.LedgerView
import s4y.evoting.casting.ui.cheat.CheatPanel
import s4y.evoting.casting.ui.cheat.state.CheatSession
import s4y.evoting.casting.ui.voter.VoterPanel
import s4y.evoting.casting.ui.voter.state.VoterSession
import s4y.evoting.core.crypto.{Party, Verifier}
import s4y.evoting.voters.ui.registry.RegistryPanel
import s4y.evoting.voters.ui.registry.state.RegistrySession
import s4y.evoting.voting.ui.owner.OwnerPanel
import s4y.evoting.voting.ui.owner.state.OwnerSession

/** Page layout: who runs the voting on the left, the voter in the middle, the public on the right. */
object DemoPage:
  def apply(owner: OwnerSession, registry: RegistrySession, voter: VoterSession, ledger: LedgerView, cheat: CheatSession)(using
      Verifier[Party.Issuer],
      Verifier[Party.Operator]
  ): HtmlElement =
    div(
      cls := "page",
      headerTag(h1("e-Voting Machine"), p(cls := "subtitle", "Investor demo — everything runs in this browser tab")),
      mainTag(
        cls := "columns",
        sectionTag(cls := "column", aria.label := "Organiser", OwnerPanel(owner), RegistryPanel(registry)),
        sectionTag(cls := "column", aria.label := "Voter", VoterPanel(voter), CheatPanel(cheat)),
        sectionTag(cls := "column column-wide", aria.label := "Public", LedgerPanel(ledger))
      )
    )
