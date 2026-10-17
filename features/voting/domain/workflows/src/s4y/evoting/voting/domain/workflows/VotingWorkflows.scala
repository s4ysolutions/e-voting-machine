package s4y.evoting.voting.domain.workflows

import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voting.domain.operations.VotingOperations
import s4y.evoting.voting.domain.vo.*
import s4y.evoting.voting.domain.workflows.ports.VotingRepository

/** Voting lifecycle. Each workflow is one atomic commit. */
object VotingWorkflows:

  def register[TX <: Tx](definition: VotingDefinition)(using
      tm: TransactionManager[TX],
      repo: VotingRepository[TX]
  ): Either[LifecycleError | InfraFailure, Voting[Status.Planned]] =
    tm.atomically[LifecycleError, Voting[Status.Planned]]("register voting") {
      if repo.find(definition.key).isDefined then Left(LifecycleError.AlreadyExists(definition.key))
      else
        val planned = Voting.planned(definition)
        repo.save(StoredVoting.Planned(planned))
        Right(planned)
    }

  def open[TX <: Tx](key: VotingKey)(using
      tm: TransactionManager[TX],
      repo: VotingRepository[TX]
  ): Either[LifecycleError | InfraFailure, Voting[Status.Open]] =
    tm.atomically[LifecycleError, Voting[Status.Open]]("open voting") {
      for
        stored  <- repo.find(key).toRight(LifecycleError.NotFound(key))
        planned <- VotingOperations.requirePlanned(stored)
        opened   = planned.open
        _        = repo.save(StoredVoting.Open(opened))
      yield opened
    }

  def close[TX <: Tx](key: VotingKey)(using
      tm: TransactionManager[TX],
      repo: VotingRepository[TX]
  ): Either[LifecycleError | InfraFailure, Voting[Status.Closed]] =
    tm.atomically[LifecycleError, Voting[Status.Closed]]("close voting") {
      for
        stored <- repo.find(key).toRight(LifecycleError.NotFound(key))
        open   <- VotingOperations.requireOpen(stored)
        closed  = open.close
        _       = repo.save(StoredVoting.Closed(closed))
      yield closed
    }

  def list[TX <: Tx](using
      tm: TransactionManager[TX],
      repo: VotingRepository[TX]
  ): Either[InfraFailure, List[StoredVoting]] =
    tm.atomically[Nothing, List[StoredVoting]]("list votings")(Right(repo.all))
