package s4y.evoting.demo

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import s4y.evoting.audit.ui.ledger.state.LedgerView
import s4y.evoting.casting.ui.cheat.state.{CheatSession, Forger}
import s4y.evoting.casting.ui.voter.state.VoterSession
import s4y.evoting.core.crypto.{Party, Randomness}
import s4y.evoting.core.infra.noble.{BrowserRandomness, NobleCrypto, NobleKeys}
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voters.ui.registry.state.RegistrySession
import s4y.evoting.voting.ui.owner.state.OwnerSession
import s4y.evoting.wiring.VotingSystem

/** Composition root: the only place that names concrete adapters (skill: composition-root).
  * Keys come from fixed seeds, so every run and every reset shows the same public keys. */
object Main:
  private def seed(n: Int): IArray[Byte] = IArray.tabulate[Byte](32)(i => ((n * 37 + i * 11) % 256).toByte)

  private def identity(raw: String): VoterIdentity = VoterIdentity.from(raw).fold(e => sys.error(s"$e"), v => v)

  /** A whole fresh demo: new system, seed data, state holders and page. Called again on reset. */
  private def build(reset: () => Unit): HtmlElement =
    val system = VotingSystem(
      NobleCrypto, BrowserRandomness,
      NobleKeys.fromSeed[Party.Issuer](seed(1)), NobleKeys.fromSeed[Party.Operator](seed(2))
    )
    import system.given
    given ChangeFeed = ChangeFeed()
    given Randomness = BrowserRandomness
    given Forger     = Forger(NobleKeys.fromSeed[Party.Issuer](seed(66)).signer)

    Seed.load()
    DemoPage(
      OwnerSession(), RegistrySession(), VoterSession(), LedgerView(), CheatSession(identity(Seed.Mallory)),
      Traffic(Seed.Background.map(identity)), reset
    )

  def main(args: Array[String]): Unit =
    val generation = Var(0)
    val reset      = () => generation.update(_ + 1)
    renderOnDomContentLoaded(dom.document.getElementById("app"), div(child <-- generation.signal.map(_ => build(reset))))
