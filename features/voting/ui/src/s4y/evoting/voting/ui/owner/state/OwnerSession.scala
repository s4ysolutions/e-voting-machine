package s4y.evoting.voting.ui.owner.state

import com.raquo.laminar.api.L.*
import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voting.app.usecases.*
import s4y.evoting.voting.domain.dsl.{subject, voting}
import s4y.evoting.voting.domain.vo.{StoredVoting, VotingKey}
import s4y.evoting.voting.ui.Messages

/** Result of the owner's last action, shown under the panel title. */
enum Notice:
  case Done(text: String)
  case Rejected(text: String)

/** State holder for the voting owner's panel (skill: usecase-airstream-bridge). */
final class OwnerSession(using
    UseCase[RegisterVotingCommand],
    UseCase[ListVotingsQuery],
    UseCase[OpenVotingCommand],
    UseCase[CloseVotingCommand]
)(using feed: ChangeFeed):
  private val _notice = Var[Option[Notice]](None)
  val notice: Signal[Option[Notice]] = _notice.signal

  /** Read model: re-read whenever any panel changes data. */
  val votings: Signal[Either[String, List[StoredVoting]]] =
    feed.changes.startWith(()).map(_ => ListVotingsQuery().run.left.map(Messages.of))

  /** Defines a planned voting from the form. Each subject line reads `key | question | option, option`. */
  def create(key: String, title: String, subjectLines: String): Unit =
    val subjects = subjectLines.linesIterator.map(_.trim).filter(_.nonEmpty).toList.map { line =>
      line.split('|').map(_.trim).toList match
        case k :: q :: options :: Nil => (k, q, options.split(',').map(_.trim).filter(_.nonEmpty).toList)
        case k :: q :: Nil            => (k, q, Nil)
        case other                    => (other.headOption.getOrElse(""), "", Nil)
    }
    val definition = voting(key.trim, title.trim) {
      subjects.foreach((k, q, options) => subject(k, q)(options*))
    }
    definition.toEither match
      case Left(errors) =>
        _notice.set(Some(Notice.Rejected("Rejected: " + errors.map(Messages.of).mkString(" "))))
      case Right(valid) =>
        RegisterVotingCommand(valid).run match
          case Right(v) =>
            _notice.set(Some(Notice.Done(s"${v.definition.key.value} is defined and planned.")))
            feed.notifyChanged()
          case Left(error) =>
            _notice.set(Some(Notice.Rejected(Messages.of(error))))

  def open(key: VotingKey): Unit =
    OpenVotingCommand(key).run match
      case Right(v) =>
        _notice.set(Some(Notice.Done(s"${v.key.value} is open.")))
        feed.notifyChanged()
      case Left(error) =>
        _notice.set(Some(Notice.Rejected(Messages.of(error))))

  def close(key: VotingKey): Unit =
    CloseVotingCommand(key).run match
      case Right(v) =>
        _notice.set(Some(Notice.Done(s"${v.key.value} is closed.")))
        feed.notifyChanged()
      case Left(error) =>
        _notice.set(Some(Notice.Rejected(Messages.of(error))))
