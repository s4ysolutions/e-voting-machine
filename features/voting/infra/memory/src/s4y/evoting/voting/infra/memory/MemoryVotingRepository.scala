package s4y.evoting.voting.infra.memory

import s4y.evoting.core.infra.memory.{MemoryCell, MemoryTx}
import s4y.evoting.voting.domain.vo.{StoredVoting, VotingKey}
import s4y.evoting.voting.domain.workflows.ports.VotingRepository

final class MemoryVotingRepository extends VotingRepository[MemoryTx]:
  private val cell = MemoryCell(Map.empty[VotingKey, StoredVoting])

  def find(key: VotingKey)(using MemoryTx): Option[StoredVoting] = cell.get.get(key)
  def save(voting: StoredVoting)(using MemoryTx): Unit         = cell.update(_.updated(voting.key, voting))
  def all(using MemoryTx): List[StoredVoting]                  = cell.get.values.toList.sortBy(_.key)
