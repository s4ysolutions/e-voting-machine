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
  * Keys come from fixed seeds so every run of the demo shows the same public keys. */
object Main:
  private def seed(n: Int): IArray[Byte] = IArray.tabulate[Byte](32)(i => ((n * 37 + i * 11) % 256).toByte)

  def main(args: Array[String]): Unit =
    val system = VotingSystem(NobleCrypto, BrowserRandomness, NobleKeys.fromSeed[Party.Issuer](seed(1)), NobleKeys.fromSeed[Party.Operator](seed(2)))
    import system.given
    given ChangeFeed = ChangeFeed()
    given Randomness = BrowserRandomness
    given Forger     = Forger(NobleKeys.fromSeed[Party.Issuer](seed(66)).signer)

    Seed.load()
    val mallory = VoterIdentity.from(Seed.Mallory).fold(e => sys.error(s"$e"), identity)
    renderOnDomContentLoaded(
      dom.document.getElementById("app"),
      DemoPage(OwnerSession(), RegistrySession(), VoterSession(), LedgerView(), CheatSession(mallory))
    )
