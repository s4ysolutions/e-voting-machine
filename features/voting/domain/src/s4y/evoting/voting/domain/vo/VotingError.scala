package s4y.evoting.voting.domain.vo

/** Why a voting definition is invalid. Several can be reported at once (see `Validated`). */
enum VotingError derives CanEqual:
  case InvalidVotingKey(raw: String)
  case EmptyTitle
  case NoSubjects
  case InvalidSubjectKey(raw: String)
  case DuplicateSubject(key: String)
  case EmptyQuestion(subject: String)
  case TooFewOptions(subject: String)
  case InvalidOption(subject: String, raw: String)
  case DuplicateOption(subject: String, option: String)
