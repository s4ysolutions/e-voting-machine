package s4y.evoting.voting.domain.dsl

import s4y.evoting.core.domain.Validated
import s4y.evoting.voting.domain.vo.{RawSubject, VotingDefinition, VotingError}

/** Collects subjects inside a `voting { … }` block. Never escapes the block. */
final class VotingBuilder private[dsl] ():
  private[dsl] val subjects = List.newBuilder[RawSubject]

/** Builds a validated voting definition:
  * {{{
  * voting("annual-2027", "Annual meeting 2027") {
  *   subject("budget", "Approve the 2027 budget?")("yes", "no")
  * }
  * }}}
  */
def voting(key: String, title: String)(body: VotingBuilder ?=> Unit): Validated[VotingError, VotingDefinition] =
  val builder = VotingBuilder()
  body(using builder)
  VotingDefinition.from(key, title, builder.subjects.result())

def subject(key: String, question: String)(options: String*)(using builder: VotingBuilder): Unit =
  builder.subjects += RawSubject(key, question, options.toList)
