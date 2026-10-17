package s4y.evoting.audit.ui.ledger.state

import com.raquo.laminar.api.L.*
import s4y.evoting.audit.app.usecases.AuditQuery
import s4y.evoting.audit.domain.{AuditReport, PublicRecord}
import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voting.app.usecases.ListVotingsQuery
import s4y.evoting.voting.domain.vo.{VotingKey, VotingStatus}

/** Read model for the public ledger and the auditor (skill: usecase-airstream-bridge, shape B).
  * Re-reads after every change anywhere, and on demand. */
final class LedgerView(using UseCase[ListVotingsQuery], UseCase[AuditQuery])(using feed: ChangeFeed):
  private val _selected = Var[Option[VotingKey]](None)
  private val rerun     = EventBus[Unit]()
  private val _runs     = Var(0)

  private val stored = feed.changes.startWith(()).map(_ => ListVotingsQuery().run.fold(_ => Nil, identity))

  val votings: Signal[List[(VotingKey, String)]] = stored.map(_.map(s => s.definition.key -> s.definition.title))

  /** The chosen voting; while nothing is chosen, the first open one, else the first one. */
  val selected: Signal[Option[VotingKey]] =
    _selected.signal.combineWith(stored).map { (chosen, all) =>
      val keys = all.map(_.definition.key)
      chosen.filter(keys.contains)
        .orElse(all.find(_.status == VotingStatus.Open).map(_.definition.key))
        .orElse(keys.headOption)
    }

  def select(key: VotingKey): Unit = _selected.set(Some(key))

  /** How many times the auditor ran; shown so a manual re-run is visibly new. */
  val runs: Signal[Int] = _runs.signal

  def runAudit(): Unit =
    _runs.update(_ + 1)
    rerun.writer.onNext(())

  val audit: Signal[Either[String, Option[(PublicRecord, AuditReport)]]] =
    EventStream.merge(feed.changes, rerun.events).startWith(()).combineWith(selected).map { key =>
      key.fold(Right(None))(k => AuditQuery(k).run.left.map(_.message))
    }
