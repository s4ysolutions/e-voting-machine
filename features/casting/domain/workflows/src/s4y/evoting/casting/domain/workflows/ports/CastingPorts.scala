package s4y.evoting.casting.domain.workflows.ports

import s4y.evoting.ballots.domain.vo.{BallotToken, TokenId}
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.core.crypto.{Digest, Party, Signed}
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.Tx
import s4y.evoting.voting.domain.vo.{VotingDefinition, VotingKey}

/** The add-only, hash-chained public ledger. */
trait LedgerRepository[TX <: Tx]:
  /** Last sequence number and the hash the next entry must chain from. */
  def head(using TX): (Long, Digest)
  def append(entries: List[LedgerEntry])(using TX): Unit
  def entries(using TX): List[LedgerEntry]
  def byCast(castId: CastId)(using TX): List[LedgerEntry]

/** Public set of spent tokens (invariant I3). */
trait SpentTokens[TX <: Tx]:
  def contains(token: TokenId)(using TX): Boolean
  def add(token: TokenId, ballot: Signed[Party.Issuer, BallotToken])(using TX): Unit
  /** The spent ballots with their issuer signatures, for the public record. */
  def all(using TX): List[Signed[Party.Issuer, BallotToken]]

/** Private to the operator: which casts were cancelled (T7). Never published. */
trait CancelledCasts[TX <: Tx]:
  def contains(castId: CastId)(using TX): Boolean
  def add(castId: CastId)(using TX): Unit

/** Is the voting open, and what does it ask? Adapted by the composition root over the voting context. */
trait OpenVotings[TX <: Tx]:
  def requireOpen(voting: VotingKey)(using TX): Either[CastError, VotingDefinition]

/** Issues a re-vote ballot inside the re-vote's atomic commit. Adapted over the ballots context. */
trait RevoteBallots[TX <: Tx]:
  def issue(voting: VotingKey, next: TokenId)(using TX): Either[InfraFailure, Signed[Party.Issuer, BallotToken]]

final case class CastingPorts[TX <: Tx](
    ledger: LedgerRepository[TX],
    spent: SpentTokens[TX],
    cancelled: CancelledCasts[TX],
    votings: OpenVotings[TX],
    revoteBallots: RevoteBallots[TX]
)
