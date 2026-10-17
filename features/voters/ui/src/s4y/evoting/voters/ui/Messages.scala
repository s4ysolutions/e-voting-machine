package s4y.evoting.voters.ui

import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.voters.domain.vo.VotersError

/** Voter-registry errors as text for the audience. */
object Messages:
  def of(error: VotersError | InfraFailure): String = error match
    case VotersError.InvalidIdentity(raw)          => s"Rejected: '$raw' is not an email address."
    case VotersError.AlreadyRegistered(_, who)     => s"Rejected: ${who.value} is already registered, so nothing was imported."
    case VotersError.NotEligible(_, who)           => s"Rejected: ${who.value} is not on the voter list."
    case VotersError.BallotAlreadyIssued(_, _)     => "Rejected: this voter already has a ballot."
    case InfraFailure(message)                     => s"Infrastructure failure: $message"
