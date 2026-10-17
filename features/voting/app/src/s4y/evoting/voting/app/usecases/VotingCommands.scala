package s4y.evoting.voting.app.usecases

import s4y.evoting.core.app.{UseCase, UseCaseCommand}
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voting.domain.vo.*
import s4y.evoting.voting.domain.workflows.VotingWorkflows
import s4y.evoting.voting.domain.workflows.ports.VotingRepository

final case class RegisterVotingCommand(definition: VotingDefinition) extends UseCaseCommand:
  type E = LifecycleError | InfraFailure
  type A = Voting[Status.Planned]

final case class OpenVotingCommand(key: VotingKey) extends UseCaseCommand:
  type E = LifecycleError | InfraFailure
  type A = Voting[Status.Open]

final case class CloseVotingCommand(key: VotingKey) extends UseCaseCommand:
  type E = LifecycleError | InfraFailure
  type A = Voting[Status.Closed]

final case class ListVotingsQuery() extends UseCaseCommand:
  type E = InfraFailure
  type A = List[StoredVoting]

// Use cases are pass-throughs: the workflows own the atomic commits.

final class RegisterVotingUseCase[TX <: Tx](using TransactionManager[TX], VotingRepository[TX])
    extends UseCase[RegisterVotingCommand]:
  def apply(c: RegisterVotingCommand): Either[c.E, c.A] = VotingWorkflows.register(c.definition)

final class OpenVotingUseCase[TX <: Tx](using TransactionManager[TX], VotingRepository[TX])
    extends UseCase[OpenVotingCommand]:
  def apply(c: OpenVotingCommand): Either[c.E, c.A] = VotingWorkflows.open(c.key)

final class CloseVotingUseCase[TX <: Tx](using TransactionManager[TX], VotingRepository[TX])
    extends UseCase[CloseVotingCommand]:
  def apply(c: CloseVotingCommand): Either[c.E, c.A] = VotingWorkflows.close(c.key)

final class ListVotingsUseCase[TX <: Tx](using TransactionManager[TX], VotingRepository[TX])
    extends UseCase[ListVotingsQuery]:
  def apply(c: ListVotingsQuery): Either[c.E, c.A] = VotingWorkflows.list
