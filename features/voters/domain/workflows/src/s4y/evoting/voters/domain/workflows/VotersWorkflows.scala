package s4y.evoting.voters.domain.workflows

import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.identity.Identifier
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voters.domain.vo.*
import s4y.evoting.voters.domain.workflows.ports.VotersRepository
import s4y.evoting.voting.domain.vo.VotingKey

object VotersWorkflows:

  /** Adds eligible voters; all or none (F1). */
  def importVoters[TX <: Tx](voting: VotingKey, identities: List[VoterIdentity])(using
      tm: TransactionManager[TX],
      repo: VotersRepository[TX]
  ): Either[VotersError | InfraFailure, List[Identifier[Voter]]] =
    tm.atomically[VotersError, List[Identifier[Voter]]]("import voters") {
      identities.foldLeft[Either[VotersError, List[Identifier[Voter]]]](Right(Nil)) { (acc, identity) =>
        acc.flatMap { ids =>
          if repo.findByIdentity(voting, identity).isDefined then Left(VotersError.AlreadyRegistered(voting, identity))
          else Right(ids :+ repo.insert(RegisteredVoter(voting, identity, ballotIssued = false)))
        }
      }
    }

  /** Marks the voter's first ballot as issued (F2). Fails if not eligible or already issued. */
  def markBallotIssued[TX <: Tx](voting: VotingKey, identity: VoterIdentity)(using
      repo: VotersRepository[TX],
      tx: TX
  ): Either[VotersError, Identifier[Voter]] =
    repo.findByIdentity(voting, identity) match
      case None                                   => Left(VotersError.NotEligible(voting, identity))
      case Some((pseudonym, voter)) if voter.ballotIssued => Left(VotersError.BallotAlreadyIssued(voting, pseudonym))
      case Some((pseudonym, voter)) =>
        repo.update(pseudonym, voter.copy(ballotIssued = true))
        Right(pseudonym)

  def publicRegistry[TX <: Tx](voting: VotingKey)(using
      tm: TransactionManager[TX],
      repo: VotersRepository[TX]
  ): Either[InfraFailure, List[(Identifier[Voter], PublicVoter)]] =
    tm.atomically[Nothing, List[(Identifier[Voter], PublicVoter)]]("public registry")(Right(repo.publicView(voting)))
