package s4y.evoting.voting.domain.vo

/** Phantom lifecycle states. A `Voting[Status.Open]` is proof that the voting is open. */
object Status:
  sealed trait Planned
  sealed trait Open
  sealed trait Closed

final case class Voting[S] private (definition: VotingDefinition):
  def key: VotingKey = definition.key

object Voting:
  def planned(definition: VotingDefinition): Voting[Status.Planned] = Voting(definition)

  extension (v: Voting[Status.Planned]) def open: Voting[Status.Open]    = Voting(v.definition)
  extension (v: Voting[Status.Open])    def close: Voting[Status.Closed] = Voting(v.definition)

/** A voting as stored: its state is only known at runtime. Match on it to get typed evidence. */
enum StoredVoting:
  case Planned(voting: Voting[Status.Planned])
  case Open(voting: Voting[Status.Open])
  case Closed(voting: Voting[Status.Closed])

  def definition: VotingDefinition = this match
    case Planned(v) => v.definition
    case Open(v)    => v.definition
    case Closed(v)  => v.definition

  def key: VotingKey = definition.key

  def status: VotingStatus = this match
    case Planned(_) => VotingStatus.Planned
    case Open(_)    => VotingStatus.Open
    case Closed(_)  => VotingStatus.Closed

/** Plain status value, for display and error messages. */
enum VotingStatus derives CanEqual:
  case Planned, Open, Closed

/** Why a lifecycle step is not allowed. */
enum LifecycleError derives CanEqual:
  case NotFound(key: VotingKey)
  case AlreadyExists(key: VotingKey)
  case NotPlanned(key: VotingKey, current: VotingStatus)
  case NotOpen(key: VotingKey, current: VotingStatus)
