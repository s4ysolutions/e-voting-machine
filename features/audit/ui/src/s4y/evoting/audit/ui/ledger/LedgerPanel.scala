package s4y.evoting.audit.ui.ledger

import com.raquo.laminar.api.L.*
import s4y.evoting.audit.ui.ledger.components.{AuditChecks, TallyTable}
import s4y.evoting.audit.ui.ledger.state.LedgerView
import s4y.evoting.core.crypto.{Party, Verifier}
import s4y.evoting.core.ui.{SignedLine, SignerLabel}
import s4y.evoting.voting.domain.vo.VotingKey

/** Entry point of the public ledger and auditor panel (skill: ui-component-container-pattern). */
object LedgerPanel:
  def apply(view: LedgerView)(using Verifier[Party.Operator]): HtmlElement =
    div(
      cls := "panel panel-ledger",
      h2("Public ledger and auditor"),
      p(cls := "hint", "Anyone can read this and re-run the checks. Edit any line: its signature breaks; restore the text and it is valid again."),
      div(
        cls := "ledger-bar",
        label(
          "Voting ",
          select(
            children <-- view.votings.combineWith(view.selected).map((all, chosen) =>
              all.map((k, t) => option(value := k.value, selected := chosen.contains(k), t))
            ),
            onChange.mapToValue --> (raw => VotingKey.from(raw).foreach(view.select))
          )
        ),
        button("Run audit", onClick --> (_ => view.runAudit())),
        span(cls := "hint", child.text <-- view.runs.map(n => if n == 0 then "" else s"run #$n"))
      ),
      child <-- view.audit.map {
        case Left(message)  => div(cls := "notice rejected", s"Infrastructure failure: $message")
        case Right(None)    => div(cls := "empty", "No voting selected.")
        case Right(Some((record, report))) =>
          div(
            h3("Tally"),
            TallyTable(record.definition, report.tally),
            h3("Checks"),
            div(cls := s"verdict ${if report.allPassed then "passed" else "failed"}",
              if report.allPassed then "All public checks pass." else "A public check failed."),
            AuditChecks(report),
            h3(s"Ledger entries (${record.own.size})"),
            p(cls := "hint", s"Issued: ${record.counts.firstBallots} first ballots, ${record.counts.revoteBallots} re-vote ballots; ${record.ballotsIssuedFlags} of ${record.registrySize} registered voters marked."),
            if record.own.isEmpty then div(cls := "empty", "No entries yet.")
            else div(cls := "entries", record.own.map(e => SignedLine(e, SignerLabel.Operator, editable = true)))
          )
      }
    )
