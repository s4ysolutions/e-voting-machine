package s4y.evoting.voters.ui.registry.state

import com.raquo.laminar.api.L.*
import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.core.identity.Identifier
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voters.app.usecases.*
import s4y.evoting.voters.domain.vo.{PublicVoter, Voter, VoterIdentity}
import s4y.evoting.voters.ui.Messages
import s4y.evoting.voting.app.usecases.ListVotingsQuery
import s4y.evoting.voting.domain.vo.{VotingKey, VotingStatus}

enum RegistryNotice:
  case Done(text: String)
  case Rejected(text: String)

/** State holder for the voter registry: import eligible voters, show the public view (pseudonyms
  * and ballot-issued flags, never identities). */
final class RegistrySession(using
    UseCase[ImportVotersCommand],
    UseCase[PublicRegistryQuery],
    UseCase[ListVotingsQuery]
)(using feed: ChangeFeed):
  private val _notice = Var[Option[RegistryNotice]](None)
  val notice: Signal[Option[RegistryNotice]] = _notice.signal

  private val _selected = Var[Option[VotingKey]](None)

  private val stored = feed.changes.startWith(()).map(_ => ListVotingsQuery().run.fold(_ => Nil, identity))

  val votings: Signal[List[VotingKey]] = stored.map(_.map(_.definition.key))

  /** The chosen voting; while nothing is chosen, the first open one, else the first one. */
  val selected: Signal[Option[VotingKey]] =
    _selected.signal.combineWith(stored).map { (chosen, all) =>
      val keys = all.map(_.definition.key)
      chosen.filter(keys.contains)
        .orElse(all.find(_.status == VotingStatus.Open).map(_.definition.key))
        .orElse(keys.headOption)
    }

  def select(key: VotingKey): Unit = _selected.set(Some(key))

  val registry: Signal[List[(Identifier[Voter], PublicVoter)]] =
    feed.changes.startWith(()).combineWith(selected).map { key =>
      key.fold(Nil)(k => PublicRegistryQuery(k).run.fold(_ => Nil, identity))
    }

  /** Imports one email per line, all or nothing. */
  def importVoters(voting: VotingKey, emails: String): Unit =
    val lines = emails.linesIterator.map(_.trim).filter(_.nonEmpty).toList
    val parsed = lines.map(VoterIdentity.from)
    parsed.collectFirst { case Left(e) => e } match
      case Some(error) => _notice.set(Some(RegistryNotice.Rejected(Messages.of(error))))
      case None =>
        ImportVotersCommand(voting, parsed.collect { case Right(id) => id }).run match
          case Right(ids) =>
            _notice.set(Some(RegistryNotice.Done(s"${ids.size} voters imported into ${voting.value}.")))
            feed.notifyChanged()
          case Left(error) =>
            _notice.set(Some(RegistryNotice.Rejected(Messages.of(error))))
