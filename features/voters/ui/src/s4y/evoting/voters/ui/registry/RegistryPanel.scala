package s4y.evoting.voters.ui.registry

import com.raquo.laminar.api.L.*
import s4y.evoting.core.identity.mint.raw
import s4y.evoting.voters.ui.registry.state.{RegistryNotice, RegistrySession}
import s4y.evoting.voting.domain.vo.VotingKey

/** Entry point of the voter registry (skill: ui-component-container-pattern). */
object RegistryPanel:
  def apply(session: RegistrySession): HtmlElement =
    val emails = Var("erin@x.org\nfrank@x.org")
    div(
      cls := "panel panel-registry",
      h2("Voter registry"),
      p(cls := "hint", "The operator keeps who is eligible. The public sees only random pseudonyms and whether each one got a ballot."),
      div(
        role := "status",
        child.maybe <-- session.notice.map(_.map {
          case RegistryNotice.Done(text)     => div(cls := "notice done", text)
          case RegistryNotice.Rejected(text) => div(cls := "notice rejected", text)
        })
      ),
      label(
        "Voting ",
        select(
          children <-- session.votings.combineWith(session.selected).map((all, chosen) =>
            all.map(k => option(value := k.value, selected := chosen.contains(k), k.value))
          ),
          onChange.mapToValue --> (raw => VotingKey.from(raw).foreach(session.select))
        )
      ),
      label("Eligible voters, one email per line", textArea(rows := 2, controlled(value <-- emails.signal, onInput.mapToValue --> emails))),
      div(
        cls := "actions",
        button(
          "Import",
          onClick.compose(_.sample(session.selected)) --> (_.foreach(k => session.importVoters(k, emails.now())))
        )
      ),
      table(
        cls := "registry",
        thead(tr(th("Pseudonym"), th("Ballot issued"))),
        tbody(
          children <-- session.registry.map(_.map((id, voter) =>
            tr(td(cls := "mono", id.raw.take(10) + "…"), td(if voter.ballotIssued then "yes" else "—"))
          ))
        )
      )
    )
