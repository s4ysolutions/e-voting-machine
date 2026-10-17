package s4y.evoting.voting.domain.vo

import s4y.evoting.core.domain.Validated
import s4y.evoting.core.domain.Validated.*

/** One option a voter may pick for a subject (e.g. `yes`). Trimmed, 1–40 characters. */
opaque type OptionLabel = String
object OptionLabel:
  def from(subject: String, raw: String): Either[VotingError, OptionLabel] =
    val trimmed = raw.trim
    Either.cond(trimmed.nonEmpty && trimmed.length <= 40, trimmed, VotingError.InvalidOption(subject, raw))
  extension (o: OptionLabel) def value: String = o
  given CanEqual[OptionLabel, OptionLabel] = CanEqual.derived

/** Unvalidated input for a subject, as typed by the owner or written in the DSL. */
final case class RawSubject(key: String, question: String, options: List[String])

/** A question on the ballot with at least two distinct options. */
final case class Subject private (key: SubjectKey, question: String, options: List[OptionLabel])
    derives CanEqual

object Subject:
  def from(raw: RawSubject): Validated[VotingError, Subject] =
    val key      = SubjectKey.from(raw.key).toValidated
    val question = if raw.question.trim.nonEmpty then valid(raw.question.trim) else invalid(VotingError.EmptyQuestion(raw.key))
    val options  = traverse(raw.options)(o => OptionLabel.from(raw.key, o).toValidated).toEither match
      case Left(errors) => Validated.Invalid(errors)
      case Right(labels) =>
        val duplicates = labels.diff(labels.distinct).distinct
        if labels.sizeIs < 2 then invalid(VotingError.TooFewOptions(raw.key))
        else if duplicates.nonEmpty then invalid(VotingError.DuplicateOption(raw.key, duplicates.head.value))
        else valid(labels)
    key.zip(question).zip(options).map { case ((k, q), os) => Subject(k, q, os) }
