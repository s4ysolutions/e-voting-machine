package s4y.evoting.audit.ui.ledger.components

import com.raquo.laminar.api.L.*
import s4y.evoting.audit.domain.{AuditReport, Check, Invariant}

/** The auditor's verdict per public invariant (design doc 3, "Public invariants"). */
object AuditChecks:
  private def describe(i: Invariant): String = i match
    case Invariant.I1 => "Every entry is signed by the operator and chained to the previous one"
    case Invariant.I2 => "No total ever goes below zero"
    case Invariant.I3 => "Every vote is backed by one issued ballot, spent once"
    case Invariant.I4 => "First ballots issued = voters marked as having one"
    case Invariant.I5 => "Re-vote ballots = cancellations, for every subject"
    case Invariant.I6 => "Votes counted never exceed first ballots issued"
    case Invariant.I7 => "Nothing was recorded after the voting closed"

  def apply(report: AuditReport): HtmlElement =
    ul(
      cls := "checks",
      report.checks.map { (invariant, check) =>
        val (css, mark, detail) = check match
          case Check.Passed            => ("passed", "✓", "")
          case Check.Failed(problem)   => ("failed", "✗", problem)
          case Check.NotChecked(why)   => ("skipped", "–", why)
        li(cls := s"check $css", span(cls := "mark", mark), b(invariant.toString), " ", describe(invariant),
          Option.when(detail.nonEmpty)(div(cls := "check-detail", detail)))
      }
    )
