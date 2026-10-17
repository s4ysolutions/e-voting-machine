package s4y.evoting.voting.domain.vo

import s4y.evoting.core.domain.Validated
import s4y.evoting.core.domain.Validated.*

/** What is being voted on. Construction reports every problem at once. */
final case class VotingDefinition private (key: VotingKey, title: String, subjects: List[Subject])
    derives CanEqual

object VotingDefinition:
  def from(key: String, title: String, subjects: List[RawSubject]): Validated[VotingError, VotingDefinition] =
    val validKey   = VotingKey.from(key).toValidated
    val validTitle = if title.trim.nonEmpty then valid(title.trim) else invalid(VotingError.EmptyTitle)
    val validSubjects = traverse(subjects)(Subject.from).toEither match
      case Left(errors) => Validated.Invalid(errors)
      case Right(Nil)   => invalid(VotingError.NoSubjects)
      case Right(list) =>
        val keys       = list.map(_.key)
        val duplicates = keys.diff(keys.distinct).distinct
        if duplicates.nonEmpty then invalid(VotingError.DuplicateSubject(duplicates.head.value))
        else valid(list)
    validKey.zip(validTitle).zip(validSubjects).map { case ((k, t), ss) => VotingDefinition(k, t, ss) }
