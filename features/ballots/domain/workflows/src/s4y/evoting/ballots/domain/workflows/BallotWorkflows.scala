package s4y.evoting.ballots.domain.workflows

import s4y.evoting.ballots.domain.vo.*
import s4y.evoting.ballots.domain.workflows.ports.IssuerPorts
import s4y.evoting.core.crypto.{Party, Signed, Signer}
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.domain.vo.VotingKey

/** The Ballot Issuer (design doc F2). */
object BallotWorkflows:

  /** First ballot: check, flag, sign and count in one atomic commit. */
  def issueFirst[TX <: Tx](voting: VotingKey, identity: VoterIdentity, token: TokenId)(using
      tm: TransactionManager[TX],
      ports: IssuerPorts[TX],
      signer: Signer[Party.Issuer]
  ): Either[BallotError | InfraFailure, Signed[Party.Issuer, BallotToken]] =
    tm.atomically[BallotError, Signed[Party.Issuer, BallotToken]]("issue first ballot") {
      for
        _ <- ports.gate.requireOpen(voting)
        _ <- ports.eligibility.markFirstBallotIssued(voting, identity)
      yield
        ports.counter.countFirst(voting)
        signer.sign(BallotToken(voting, token, BallotKind.First))
    }

  /** Re-vote ballot. Runs inside the caller's atomic commit (the re-vote workflow, F4). */
  def issueRevote[TX <: Tx](voting: VotingKey, token: TokenId)(using
      ports: IssuerPorts[TX],
      signer: Signer[Party.Issuer],
      tx: TX
  ): Either[BallotError, Signed[Party.Issuer, BallotToken]] =
    ports.gate.requireOpen(voting).map { _ =>
      ports.counter.countRevote(voting)
      signer.sign(BallotToken(voting, token, BallotKind.Revote))
    }

  def counts[TX <: Tx](voting: VotingKey)(using
      tm: TransactionManager[TX],
      ports: IssuerPorts[TX]
  ): Either[InfraFailure, IssuanceCounts] =
    tm.atomically[Nothing, IssuanceCounts]("issuance counts")(Right(ports.counter.counts(voting)))
