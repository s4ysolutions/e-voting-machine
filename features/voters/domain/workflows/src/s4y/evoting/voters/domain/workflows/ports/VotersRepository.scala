package s4y.evoting.voters.domain.workflows.ports

import s4y.evoting.core.identity.Identifier
import s4y.evoting.core.tx.Tx
import s4y.evoting.voters.domain.vo.*
import s4y.evoting.voting.domain.vo.VotingKey

/** Driven port: the Voters Registry. The adapter assigns a random pseudonym on insert. */
trait VotersRepository[TX <: Tx]:
  def insert(voter: RegisteredVoter)(using TX): Identifier[Voter]
  def findByIdentity(voting: VotingKey, identity: VoterIdentity)(using TX): Option[(Identifier[Voter], RegisteredVoter)]
  def update(pseudonym: Identifier[Voter], voter: RegisteredVoter)(using TX): Unit
  def publicView(voting: VotingKey)(using TX): List[(Identifier[Voter], PublicVoter)]
