package s4y.evoting.voters.infra.memory

import java.util.UUID
import s4y.evoting.core.identity.Identifier
import s4y.evoting.core.identity.mint.*
import s4y.evoting.core.infra.memory.{MemoryCell, MemoryTx}
import s4y.evoting.voters.domain.vo.*
import s4y.evoting.voters.domain.workflows.ports.VotersRepository
import s4y.evoting.voting.domain.vo.VotingKey

/** Pseudonyms are random, so nobody can recompute one from a guessed email (design doc, F1). */
final class MemoryVotersRepository(newPseudonym: () => String = () => s"p-${UUID.randomUUID()}")
    extends VotersRepository[MemoryTx]:
  private val cell = MemoryCell(Vector.empty[(Identifier[Voter], RegisteredVoter)])

  def insert(voter: RegisteredVoter)(using MemoryTx): Identifier[Voter] =
    val taken     = cell.get.iterator.map(_._1).toSet
    val pseudonym = Iterator.continually(newPseudonym().identifier[Voter]).find(!taken.contains(_)).get
    cell.update(_ :+ (pseudonym -> voter))
    pseudonym

  def findByIdentity(voting: VotingKey, identity: VoterIdentity)(using MemoryTx): Option[(Identifier[Voter], RegisteredVoter)] =
    cell.get.find((_, v) => v.voting == voting && v.identity == identity)

  def update(pseudonym: Identifier[Voter], voter: RegisteredVoter)(using MemoryTx): Unit =
    cell.update(_.map((id, v) => if id == pseudonym then id -> voter else id -> v))

  def publicView(voting: VotingKey)(using MemoryTx): List[(Identifier[Voter], PublicVoter)] =
    cell.get.collect { case (id, v) if v.voting == voting => id -> PublicVoter(v.ballotIssued) }.toList
