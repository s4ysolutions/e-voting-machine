package s4y.evoting.voting.domain.operations

import s4y.evoting.voting.domain.vo.*

/** Pure lifecycle rules. "Parse, don't validate": each check returns typed evidence. */
object VotingOperations:
  def requirePlanned(stored: StoredVoting): Either[LifecycleError, Voting[Status.Planned]] = stored match
    case StoredVoting.Planned(v) => Right(v)
    case other                   => Left(LifecycleError.NotPlanned(other.key, other.status))

  def requireOpen(stored: StoredVoting): Either[LifecycleError, Voting[Status.Open]] = stored match
    case StoredVoting.Open(v) => Right(v)
    case other                => Left(LifecycleError.NotOpen(other.key, other.status))
