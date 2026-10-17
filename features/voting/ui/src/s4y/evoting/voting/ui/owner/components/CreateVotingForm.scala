package s4y.evoting.voting.ui.owner.components

import com.raquo.laminar.api.L.*
import s4y.evoting.voting.ui.owner.state.OwnerSession

/** Defines a new voting. Invalid input is not blocked in the form: the domain rejects it, and
  * the rejection is shown, so the audience sees the validation work. */
object CreateVotingForm:
  def apply(session: OwnerSession): HtmlElement =
    val key      = Var("agm-2028")
    val title    = Var("General meeting 2028")
    val subjects = Var("dues | Raise the yearly dues? | yes, no\nvenue | Where do we meet? | online, hall")
    detailsTag(
      cls := "create-voting",
      summaryTag("Define a new voting"),
      form(
        onSubmit.preventDefault --> (_ => session.create(key.now(), title.now(), subjects.now())),
        label("Key", input(controlled(value <-- key.signal, onInput.mapToValue --> key))),
        label("Title", input(controlled(value <-- title.signal, onInput.mapToValue --> title))),
        label(
          "Subjects, one per line: key | question | option, option",
          textArea(rows := 3, controlled(value <-- subjects.signal, onInput.mapToValue --> subjects))
        ),
        div(cls := "actions", button(tpe := "submit", "Define voting"))
      )
    )
