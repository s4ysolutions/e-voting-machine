package s4y.evoting.casting.ui.voter

import com.raquo.laminar.api.L.*
import s4y.evoting.casting.ui.voter.components.BallotForm
import s4y.evoting.casting.ui.voter.state.{Device, VoterSession}
import s4y.evoting.core.crypto.{Party, Verifier}
import s4y.evoting.core.ui.{SignedLine, SignerLabel}

/** Entry point of the voter's device (skill: ui-component-container-pattern). */
object VoterPanel:
  def apply(session: VoterSession)(using Verifier[Party.Issuer], Verifier[Party.Operator]): HtmlElement =
    val email  = Var("alice@x.org")
    val chosen = Var(Option.empty[String])
    div(
      cls := "panel panel-voter",
      h2("Voter"),
      p(cls := "hint", "A voter's phone. Sign-in is mocked: type an email from the voter list."),
      div(
        role := "status",
        child.maybe <-- session.message.map(_.map {
          case Right(text) => div(cls := "notice done", text)
          case Left(text)  => div(cls := "notice rejected", text)
        })
      ),
      child <-- session.device.map {
        case Device.Empty =>
          div(
            label(
              "Voting ",
              select(
                children <-- session.openVotings.map(_.map(v => option(value := v.key.value, v.title))),
                onChange.mapToValue --> (k => chosen.set(Some(k)))
              )
            ),
            label("Email ", input(controlled(value <-- email.signal, onInput.mapToValue --> email))),
            div(
              cls := "actions",
              button(
                "Get ballot",
                onClick.compose(_.sample(session.openVotings, chosen.signal)) --> { (open, key) =>
                  key.flatMap(k => open.find(_.key.value == k)).orElse(open.headOption) match
                    case Some(v) => session.requestBallot(v, email.now())
                    case None    => ()
                }
              )
            ),
            child.maybe <-- session.openVotings.map(open => Option.when(open.isEmpty)(p(cls := "empty", "No voting is open.")))
          )
        case Device.HasBallot(voting, _, ballot) =>
          div(
            h3(voting.title),
            p(cls := "label", "Your ballot, signed by the issuer:"),
            SignedLine(ballot, SignerLabel.Issuer),
            BallotForm(voting, session.cast)
          )
        case Device.Voted(voting, _, _, receipt) =>
          div(
            h3(voting.title),
            p(cls := "label", s"Receipt ${receipt.castId.name}. Find these entries in the public ledger:"),
            receipt.entries.map(e => SignedLine(e, SignerLabel.Operator)),
            p(cls := "hint", "Pressured to vote a certain way? Change your vote any time while the voting is open; the old receipt stays on the ledger, but nobody can see that it was cancelled."),
            div(cls := "actions", button("Change my vote", onClick --> (_ => session.revote())))
          )
      },
      child.maybe <-- session.history.map(h =>
        Option.when(h.sizeIs > 1)(p(cls := "hint", "Receipts this device received: " + h.map(_.castId.name).mkString(", ")))
      ),
      div(cls := "actions secondary", button("Leave device", onClick --> (_ => session.leave())))
    )
