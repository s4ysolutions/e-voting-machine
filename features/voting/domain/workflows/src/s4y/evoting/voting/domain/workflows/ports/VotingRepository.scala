package s4y.evoting.voting.domain.workflows.ports

import s4y.evoting.core.tx.Tx
import s4y.evoting.voting.domain.vo.{StoredVoting, VotingKey}

/** Driven port: where votings are stored. */
trait VotingRepository[TX <: Tx]:
  def find(key: VotingKey)(using TX): Option[StoredVoting]
  def save(voting: StoredVoting)(using TX): Unit
  def all(using TX): List[StoredVoting]
