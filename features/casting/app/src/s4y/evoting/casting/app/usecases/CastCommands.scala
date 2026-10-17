package s4y.evoting.casting.app.usecases

import s4y.evoting.ballots.domain.vo.{BallotSecret, BallotToken, TokenId}
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.casting.domain.workflows.CastWorkflows
import s4y.evoting.casting.domain.workflows.ports.CastingPorts
import s4y.evoting.core.app.{UseCase, UseCaseCommand}
import s4y.evoting.core.crypto.*
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voting.domain.vo.VotingKey

final case class CastCommand(ballot: Signed[Party.Issuer, BallotToken], castId: CastId, choices: Map[String, String])
    extends UseCaseCommand:
  type E = CastError | InfraFailure
  type A = Receipt

final case class RevoteCommand(voting: VotingKey, secret: BallotSecret, next: TokenId) extends UseCaseCommand:
  type E = CastError | InfraFailure
  type A = Signed[Party.Issuer, BallotToken]

final case class LedgerQuery() extends UseCaseCommand:
  type E = InfraFailure
  type A = List[LedgerEntry]

final class CastUseCase[TX <: Tx](using
    TransactionManager[TX], CastingPorts[TX], Verifier[Party.Issuer], Signer[Party.Operator], Crypto
) extends UseCase[CastCommand]:
  def apply(c: CastCommand): Either[c.E, c.A] = CastWorkflows.cast[TX](c.ballot, c.castId, c.choices)

final class RevoteUseCase[TX <: Tx](using
    TransactionManager[TX], CastingPorts[TX], Verifier[Party.Issuer], Signer[Party.Operator], Crypto
) extends UseCase[RevoteCommand]:
  def apply(c: RevoteCommand): Either[c.E, c.A] = CastWorkflows.revote[TX](c.voting, c.secret, c.next)

final class LedgerUseCase[TX <: Tx](using TransactionManager[TX], CastingPorts[TX]) extends UseCase[LedgerQuery]:
  def apply(c: LedgerQuery): Either[c.E, c.A] = CastWorkflows.ledger
