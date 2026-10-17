package s4y.evoting.voting.ui.owner.components

import com.raquo.laminar.api.L.*
import s4y.evoting.voting.domain.vo.StoredVoting
import s4y.evoting.voting.ui.Messages
import s4y.evoting.voting.ui.owner.state.OwnerSession

/** One voting with its subjects and lifecycle buttons. Both buttons are always shown, so a
  * forbidden step (closing a planned voting) can be tried and is visibly rejected. */
object VotingCard:
  def apply(stored: StoredVoting, session: OwnerSession): HtmlElement =
    val definition = stored.definition
    val status     = Messages.status(stored.status)
    div(
      cls := "card",
      div(
        cls := "card-head",
        span(cls := "card-title", definition.title),
        span(cls := s"status status-$status", status)
      ),
      div(cls := "card-key", definition.key.value),
      ul(
        cls := "subjects",
        definition.subjects.map(s =>
          li(span(cls := "question", s.question), " ", span(cls := "options", s.options.map(_.value).mkString(" / ")))
        )
      ),
      div(
        cls := "actions",
        button("Open", onClick --> (_ => session.open(definition.key))),
        button("Close", onClick --> (_ => session.close(definition.key)))
      )
    )
