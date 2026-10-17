package s4y.evoting.ballots.infra.memory

import s4y.evoting.ballots.domain.vo.IssuanceCounts
import s4y.evoting.ballots.domain.workflows.ports.IssuanceCounter
import s4y.evoting.core.infra.memory.{MemoryCell, MemoryTx}
import s4y.evoting.voting.domain.vo.VotingKey

final class MemoryIssuanceCounter extends IssuanceCounter[MemoryTx]:
  private val cell = MemoryCell(Map.empty[VotingKey, IssuanceCounts])

  def countFirst(voting: VotingKey)(using MemoryTx): Unit =
    cell.update(m => m.updated(voting, current(m, voting).copy(firstBallots = current(m, voting).firstBallots + 1)))
  def countRevote(voting: VotingKey)(using MemoryTx): Unit =
    cell.update(m => m.updated(voting, current(m, voting).copy(revoteBallots = current(m, voting).revoteBallots + 1)))
  def counts(voting: VotingKey)(using MemoryTx): IssuanceCounts = current(cell.get, voting)

  private def current(m: Map[VotingKey, IssuanceCounts], voting: VotingKey) = m.getOrElse(voting, IssuanceCounts(0, 0))
