package s4y.evoting.casting.ui.voter.components

import com.raquo.laminar.api.L.*
import s4y.evoting.voting.domain.vo.VotingDefinition

/** One radio group per subject. Nothing is preselected: an incomplete ballot is sent as is,
  * so the audience sees the Cast Service reject it. */
object BallotForm:
  def apply(voting: VotingDefinition, onCast: Map[String, String] => Unit): HtmlElement =
    val choices = Var(Map.empty[String, String])
    form(
      cls := "ballot-form",
      onSubmit.preventDefault --> (_ => onCast(choices.now())),
      voting.subjects.map { s =>
        fieldSet(
          legend(s.question),
          s.options.map { o =>
            label(
              cls := "choice",
              input(
                tpe := "radio",
                nameAttr := s"${voting.key.value}-${s.key.value}",
                value := o.value,
                onChange.mapTo(o.value) --> (v => choices.update(_.updated(s.key.value, v)))
              ),
              o.value
            )
          }
        )
      },
      div(cls := "actions", button(tpe := "submit", "Cast vote"))
    )
