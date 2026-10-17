package s4y.evoting.ballots.app.usecases

import s4y.evoting.ballots.domain.vo.*
import s4y.evoting.ballots.domain.workflows.BallotWorkflows
import s4y.evoting.ballots.domain.workflows.ports.IssuerPorts
import s4y.evoting.core.app.{UseCase, UseCaseCommand}
import s4y.evoting.core.crypto.{Party, Signed, Signer}
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.domain.vo.VotingKey

final case class IssueFirstBallotCommand(voting: VotingKey, identity: VoterIdentity, token: TokenId)
    extends UseCaseCommand:
  type E = BallotError | InfraFailure
  type A = Signed[Party.Issuer, BallotToken]

final case class IssuanceCountsQuery(voting: VotingKey) extends UseCaseCommand:
  type E = InfraFailure
  type A = IssuanceCounts

final class IssueFirstBallotUseCase[TX <: Tx](using TransactionManager[TX], IssuerPorts[TX], Signer[Party.Issuer])
    extends UseCase[IssueFirstBallotCommand]:
  def apply(c: IssueFirstBallotCommand): Either[c.E, c.A] = BallotWorkflows.issueFirst(c.voting, c.identity, c.token)

final class IssuanceCountsUseCase[TX <: Tx](using TransactionManager[TX], IssuerPorts[TX])
    extends UseCase[IssuanceCountsQuery]:
  def apply(c: IssuanceCountsQuery): Either[c.E, c.A] = BallotWorkflows.counts(c.voting)
