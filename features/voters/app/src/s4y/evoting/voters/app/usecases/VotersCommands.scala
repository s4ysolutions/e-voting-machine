package s4y.evoting.voters.app.usecases

import s4y.evoting.core.app.{UseCase, UseCaseCommand}
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.identity.Identifier
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voters.domain.vo.*
import s4y.evoting.voters.domain.workflows.VotersWorkflows
import s4y.evoting.voters.domain.workflows.ports.VotersRepository
import s4y.evoting.voting.domain.vo.VotingKey

final case class ImportVotersCommand(voting: VotingKey, identities: List[VoterIdentity]) extends UseCaseCommand:
  type E = VotersError | InfraFailure
  type A = List[Identifier[Voter]]

final case class PublicRegistryQuery(voting: VotingKey) extends UseCaseCommand:
  type E = InfraFailure
  type A = List[(Identifier[Voter], PublicVoter)]

final class ImportVotersUseCase[TX <: Tx](using TransactionManager[TX], VotersRepository[TX])
    extends UseCase[ImportVotersCommand]:
  def apply(c: ImportVotersCommand): Either[c.E, c.A] = VotersWorkflows.importVoters(c.voting, c.identities)

final class PublicRegistryUseCase[TX <: Tx](using TransactionManager[TX], VotersRepository[TX])
    extends UseCase[PublicRegistryQuery]:
  def apply(c: PublicRegistryQuery): Either[c.E, c.A] = VotersWorkflows.publicRegistry(c.voting)
