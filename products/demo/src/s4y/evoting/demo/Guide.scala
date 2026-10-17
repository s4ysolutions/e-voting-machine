package s4y.evoting.demo

import com.raquo.laminar.api.L.*

/** The presenter's script (plan step 7), collapsible above the panels. */
object Guide:
  def apply(): HtmlElement =
    detailsTag(
      cls := "guide",
      summaryTag("Demo script"),
      div(
        cls := "guide-body",
        div(
          h3("1. The basics"),
          ol(
            li("Organiser: Chair election 2027 is open; five voters are registered. The registry shows pseudonyms only."),
            li("Voter: sign in as alice@x.org and get a ballot. It is signed by the issuer; the secret stays on the phone."),
            li("Pick an option and cast. The receipt and its ledger entries appear; the auditor's tally updates and every check passes."),
            li("Ledger: edit any entry — it turns red because the signature no longer matches. Restore it — green again.")
          )
        ),
        div(
          h3("2. Coercion"),
          ol(
            li("A coercer watches alice vote for dana and asks for the receipt."),
            li("Ledger, \"Find a vote\": type the receipt. The coercer sees +1 dana, recorded."),
            li("Later, alone, alice presses \"Change my vote\" and votes eli."),
            li("Look the old receipt up again: still +1 dana, unchanged. The −1 entry carries no receipt."),
            li("Turn on background traffic first, so other voters' cancellations of the same option hide alice's.")
          )
        ),
        div(
          h3("3. Try to cheat"),
          ol(
            li("Press each red button. Mallory asks for a second ballot, replays a spent token, forges the issuer's signature, cancels a vote without its secret, cancels twice."),
            li("Each attempt is rejected with the reason; the auditor still passes every check."),
            li("Reset demo starts again from the same seed data.")
          )
        )
      )
    )
