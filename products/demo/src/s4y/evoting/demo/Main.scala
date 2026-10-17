package s4y.evoting.demo

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import s4y.evoting.core.app.UseCase
import s4y.evoting.core.infra.memory.{MemoryTransactionManager, MemoryTx}
import s4y.evoting.core.tx.TransactionManager
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voting.app.usecases.*
import s4y.evoting.voting.domain.workflows.ports.VotingRepository
import s4y.evoting.voting.infra.memory.MemoryVotingRepository
import s4y.evoting.voting.ui.owner.state.OwnerSession

/** Composition root: the only place that names concrete adapters (skill: composition-root). */
object Main:
  type TXM = MemoryTx

  // 1. ports
  given TransactionManager[TXM] = MemoryTransactionManager()
  given VotingRepository[TXM]   = MemoryVotingRepository()

  // 2. use cases
  given UseCase[RegisterVotingCommand] = RegisterVotingUseCase[TXM]()
  given UseCase[OpenVotingCommand]     = OpenVotingUseCase[TXM]()
  given UseCase[CloseVotingCommand]    = CloseVotingUseCase[TXM]()
  given UseCase[ListVotingsQuery]      = ListVotingsUseCase[TXM]()

  // 3. ui plumbing
  given ChangeFeed = ChangeFeed()

  def main(args: Array[String]): Unit =
    Seed.load()
    renderOnDomContentLoaded(dom.document.getElementById("app"), DemoPage(OwnerSession()))
