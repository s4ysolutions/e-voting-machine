package s4y.evoting.demo

import com.raquo.laminar.api.L.*
import scala.util.Random
import s4y.evoting.ballots.app.usecases.IssueFirstBallotCommand
import s4y.evoting.ballots.domain.vo.{BallotSecret, BallotToken, TokenId}
import s4y.evoting.casting.app.usecases.{CastCommand, RevoteCommand}
import s4y.evoting.casting.domain.vo.CastId
import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.core.crypto.{Crypto, Party, Randomness, Signed}
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.app.usecases.ListVotingsQuery
import s4y.evoting.voting.domain.vo.{StoredVoting, VotingDefinition, VotingStatus}

/** Simulated background voters, so the ledger fills up and cancellations of the same option hide
  * among others (design doc T17). Every action goes through the real use cases. Demo-only, so it
  * lives in the product, not in a feature. */
final class Traffic(voters: List[VoterIdentity])(using
    UseCase[ListVotingsQuery],
    UseCase[IssueFirstBallotCommand],
    UseCase[CastCommand],
    UseCase[RevoteCommand]
)(using feed: ChangeFeed, crypto: Crypto, randomness: Randomness):
  private final case class Device(secret: BallotSecret, ballot: Signed[Party.Issuer, BallotToken], cast: Boolean)

  private val random  = Random(2027)   // fixed: the same sequence of choices after every reset
  private var devices = Map.empty[(String, VoterIdentity), Device]

  val enabled: Var[Boolean] = Var(false)
  private val _actions = Var(0)
  /** How many simulated actions were recorded. */
  val actions: Signal[Int] = _actions.signal

  private def newSecret(): BallotSecret = BallotSecret.from(randomness.bytes(32)).fold(e => sys.error(s"$e"), identity)

  private def choices(v: VotingDefinition): Map[String, String] =
    v.subjects.map(s => s.key.value -> s.options(random.nextInt(s.options.size)).value).toMap

  /** One step. Mostly a voter holding a ballot casts it; sometimes a voter who voted changes the
    * vote; otherwise a new voter gets a ballot. A rejected step is simply skipped. */
  def tick(): Unit =
    val open = ListVotingsQuery().run.fold(_ => Nil, _.collect { case s: StoredVoting if s.status == VotingStatus.Open => s.definition })
    if open.nonEmpty && voters.nonEmpty then
      val v       = open(random.nextInt(open.size))
      val here    = devices.filter(_._1._1 == v.key.value)
      val holding = here.filter(!_._2.cast).toList
      val voted   = here.filter(_._2.cast).toList
      val fresh   = voters.filterNot(w => here.contains((v.key.value, w)))
      val roll    = random.nextInt(10)
      val done =
        if holding.nonEmpty && roll < 5 then
          val (key, d) = holding(random.nextInt(holding.size))
          CastCommand(d.ballot, CastId.derive(d.secret), choices(v)).run.toOption.map(_ => devices += key -> d.copy(cast = true))
        else if voted.nonEmpty && (roll < 7 || fresh.isEmpty) then
          val (key, d) = voted(random.nextInt(voted.size))
          val next     = newSecret()
          RevoteCommand(v.key, d.secret, TokenId.derive(next)).run.toOption.map(b => devices += key -> Device(next, b, cast = false))
        else if fresh.nonEmpty then
          val who = fresh(random.nextInt(fresh.size))
          val s   = newSecret()
          IssueFirstBallotCommand(v.key, who, TokenId.derive(s)).run.toOption.map(b => devices += (v.key.value, who) -> Device(s, b, cast = false))
        else None
      if done.isDefined then
        _actions.update(_ + 1)
        feed.notifyChanged()

  /** Ticks every `periodMs` while enabled; stops when the element is unmounted (on reset). */
  def driver(periodMs: Int): Modifier[HtmlElement] =
    EventStream.periodic(periodMs).filter(_ => enabled.now()) --> (_ => tick())
