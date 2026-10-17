package s4y.evoting.voting.domain.vo

private val KeyPattern = "[a-z][a-z0-9-]{0,47}".r   // 1–48 characters

/** Business key of a voting, chosen by its owner (e.g. `annual-2027`). */
opaque type VotingKey = String
object VotingKey:
  def from(raw: String): Either[VotingError, VotingKey] =
    Either.cond(KeyPattern.matches(raw), raw, VotingError.InvalidVotingKey(raw))
  extension (k: VotingKey) def value: String = k
  given CanEqual[VotingKey, VotingKey] = CanEqual.derived
  given Ordering[VotingKey]            = Ordering.String

/** Business key of a subject within a voting (e.g. `budget`). */
opaque type SubjectKey = String
object SubjectKey:
  def from(raw: String): Either[VotingError, SubjectKey] =
    Either.cond(KeyPattern.matches(raw), raw, VotingError.InvalidSubjectKey(raw))
  extension (k: SubjectKey) def value: String = k
  given CanEqual[SubjectKey, SubjectKey] = CanEqual.derived
