package s4y.evoting.voting.ui

import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.voting.domain.vo.{LifecycleError, VotingError, VotingStatus}

/** Domain errors as text for the audience. */
object Messages:
  def of(error: LifecycleError | InfraFailure): String = error match
    case LifecycleError.NotFound(key)            => s"There is no voting ${key.value}."
    case LifecycleError.AlreadyExists(key)       => s"A voting ${key.value} already exists."
    case LifecycleError.NotPlanned(key, current) => s"Rejected: ${key.value} is ${status(current)}, so it cannot be opened."
    case LifecycleError.NotOpen(key, current)    => s"Rejected: ${key.value} is ${status(current)}, so it cannot be closed."
    case InfraFailure(message)                   => s"Infrastructure failure: $message"

  def of(error: VotingError): String = error match
    case VotingError.InvalidVotingKey(raw)        => s"The key '$raw' must start with a letter and use only lowercase letters, digits or dashes (up to 48)."
    case VotingError.EmptyTitle                   => "The title is empty."
    case VotingError.NoSubjects                   => "Add at least one subject."
    case VotingError.InvalidSubjectKey(raw)       => s"The subject key '$raw' must be lowercase letters, digits or dashes."
    case VotingError.DuplicateSubject(key)        => s"The subject '$key' appears twice."
    case VotingError.EmptyQuestion(subject)       => s"The subject '$subject' has no question."
    case VotingError.TooFewOptions(subject)       => s"The subject '$subject' needs at least two options."
    case VotingError.InvalidOption(subject, raw)  => s"The option '$raw' of '$subject' must be lowercase letters, digits or dashes."
    case VotingError.DuplicateOption(subject, op) => s"The option '$op' appears twice in '$subject'."

  def status(s: VotingStatus): String = s match
    case VotingStatus.Planned => "planned"
    case VotingStatus.Open    => "open"
    case VotingStatus.Closed  => "closed"
