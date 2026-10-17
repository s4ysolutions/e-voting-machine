package s4y.evoting.ballots.domain.workflows.ports

import s4y.evoting.ballots.domain.vo.{BallotError, IssuanceCounts, TokenId}
import s4y.evoting.core.tx.Tx
import s4y.evoting.voting.domain.vo.VotingKey
import s4y.evoting.voters.domain.vo.VoterIdentity

/** Is the voting open? Implemented by the composition root over the voting context. */
trait VotingGate[TX <: Tx]:
  def requireOpen(voting: VotingKey)(using TX): Either[BallotError, Unit]

/** Sets the voter's ballot-issued flag. Implemented by the composition root over the voters
  * context, inside the same atomic commit as the issuance. */
trait Eligibility[TX <: Tx]:
  def markFirstBallotIssued(voting: VotingKey, identity: VoterIdentity)(using TX): Either[BallotError, Unit]

/** Public issuance counters per voting. */
trait IssuanceCounter[TX <: Tx]:
  def countFirst(voting: VotingKey)(using TX): Unit
  def countRevote(voting: VotingKey)(using TX): Unit
  def counts(voting: VotingKey)(using TX): IssuanceCounts

/** Has this token already been spent? Implemented by the composition root over the casting
  * context. A secret reused across votings yields the same token. */
trait SpentTokenCheck[TX <: Tx]:
  def isSpent(token: TokenId)(using TX): Boolean

/** The ports the issuer always needs together. */
final case class IssuerPorts[TX <: Tx](
    gate: VotingGate[TX],
    eligibility: Eligibility[TX],
    counter: IssuanceCounter[TX],
    spent: SpentTokenCheck[TX]
)
