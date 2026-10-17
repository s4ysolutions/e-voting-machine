package s4y.evoting.voting.ui

import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.voting.domain.vo.{LifecycleError, VotingStatus}

/** Domain errors as text for the audience. */
object Messages:
  def of(error: LifecycleError | InfraFailure): String = error match
    case LifecycleError.NotFound(key)            => s"There is no voting ${key.value}."
    case LifecycleError.AlreadyExists(key)       => s"A voting ${key.value} already exists."
    case LifecycleError.NotPlanned(key, current) => s"Rejected: ${key.value} is ${status(current)}, so it cannot be opened."
    case LifecycleError.NotOpen(key, current)    => s"Rejected: ${key.value} is ${status(current)}, so it cannot be closed."
    case InfraFailure(message)                   => s"Infrastructure failure: $message"

  def status(s: VotingStatus): String = s match
    case VotingStatus.Planned => "planned"
    case VotingStatus.Open    => "open"
    case VotingStatus.Closed  => "closed"
