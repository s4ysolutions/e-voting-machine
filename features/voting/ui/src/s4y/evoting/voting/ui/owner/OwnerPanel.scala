package s4y.evoting.voting.ui.owner

import com.raquo.laminar.api.L.*
import s4y.evoting.voting.ui.owner.components.{CreateVotingForm, VotingCard}
import s4y.evoting.voting.ui.owner.state.{Notice, OwnerSession}

/** Entry point of the voting owner's panel (skill: ui-component-container-pattern). */
object OwnerPanel:
  def apply(session: OwnerSession): HtmlElement =
    div(
      cls := "panel panel-owner",
      h2("Voting owner"),
      div(
        role := "status",
        child.maybe <-- session.notice.map(_.map {
          case Notice.Done(text)     => div(cls := "notice done", text)
          case Notice.Rejected(text) => div(cls := "notice rejected", text)
        })
      ),
      CreateVotingForm(session),
      child <-- session.votings.map {
        case Left(message) => div(cls := "notice rejected", message)
        case Right(Nil)    => div(cls := "empty", "No votings yet.")
        case Right(list)   => div(cls := "cards", list.map(VotingCard(_, session)))
      }
    )
