package s4y.evoting.casting.ui

import s4y.evoting.ballots.domain.vo.BallotError
import s4y.evoting.casting.domain.vo.CastError
import s4y.evoting.core.domain.InfraFailure

/** Ballot and casting errors as text for the audience. */
object Messages:
  def of(error: BallotError | CastError | InfraFailure): String = error match
    case e: BallotError        => ballot(e)
    case e: CastError          => cast(e)
    case InfraFailure(message) => s"Infrastructure failure: $message"

  private def ballot(error: BallotError): String = error match
    case BallotError.InvalidSecret             => "Rejected: the ballot secret is malformed."
    case BallotError.VotingNotOpen(v)          => s"Rejected: ${v.value} is not open."
    case BallotError.NotEligible(v)            => s"Rejected: this voter is not on the voter list of ${v.value}."
    case BallotError.AlreadyIssued(v)          => s"Rejected: this voter already received a ballot for ${v.value}."
    case BallotError.TokenAlreadySpent         => "Rejected: this token was already spent."

  private def cast(error: CastError): String = error match
    case CastError.VotingNotOpen(v)            => s"Rejected: ${v.value} is not open."
    case CastError.InvalidIssuerSignature      => "Rejected: the ballot is not signed by the issuer."
    case CastError.WrongVoting(v)              => s"Rejected: that cast does not belong to ${v.value}."
    case CastError.TokenAlreadySpent           => "Rejected: this ballot token was already spent."
    case CastError.CastIdAlreadyUsed           => "Rejected: this receipt is already on the ledger."
    case CastError.InvalidChoices(problems)    => "Rejected: " + problems.mkString("; ")
    case CastError.NoCastForSecret             => "Rejected: no cast on the ledger matches this secret."
    case CastError.CastAlreadyCancelled        => "Rejected: that cast was already cancelled."
