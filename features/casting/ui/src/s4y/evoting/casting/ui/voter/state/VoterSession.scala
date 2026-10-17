package s4y.evoting.casting.ui.voter.state

import com.raquo.laminar.api.L.*
import s4y.evoting.ballots.app.usecases.IssueFirstBallotCommand
import s4y.evoting.ballots.domain.vo.{BallotSecret, BallotToken, TokenId}
import s4y.evoting.casting.app.usecases.{CastCommand, RevoteCommand}
import s4y.evoting.casting.domain.vo.{CastId, Receipt}
import s4y.evoting.casting.ui.Messages
import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.core.crypto.{Crypto, Party, Randomness, Signed}
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.app.usecases.ListVotingsQuery
import s4y.evoting.voting.domain.vo.{StoredVoting, VotingDefinition, VotingStatus}

/** What the voter's device holds. The secret never leaves it. */
enum Device derives CanEqual:
  case Empty
  case HasBallot(voting: VotingDefinition, secret: BallotSecret, ballot: Signed[Party.Issuer, BallotToken])
  case Voted(voting: VotingDefinition, secret: BallotSecret, ballot: Signed[Party.Issuer, BallotToken], receipt: Receipt)

/** State holder for one voter's device (skill: usecase-airstream-bridge). */
final class VoterSession(using
    UseCase[ListVotingsQuery],
    UseCase[IssueFirstBallotCommand],
    UseCase[CastCommand],
    UseCase[RevoteCommand]
)(using feed: ChangeFeed, crypto: Crypto, randomness: Randomness):
  private val _device  = Var[Device](Device.Empty)
  private val _message = Var[Option[Either[String, String]]](None)
  private val _history = Var[List[Receipt]](Nil)

  val device: Signal[Device]                      = _device.signal
  /** Left: a rejection, Right: a confirmation. */
  val message: Signal[Option[Either[String, String]]] = _message.signal
  /** Every receipt this device was given, oldest first: what a coercer could be shown. */
  val history: Signal[List[Receipt]]              = _history.signal

  val openVotings: Signal[List[VotingDefinition]] =
    feed.changes.startWith(()).map(_ =>
      ListVotingsQuery().run.fold(_ => Nil, _.collect { case s: StoredVoting if s.status == VotingStatus.Open => s.definition })
    )

  private def newSecret(): BallotSecret =
    BallotSecret.from(randomness.bytes(32)).fold(e => sys.error(s"randomness: $e"), identity)

  def requestBallot(voting: VotingDefinition, email: String): Unit =
    VoterIdentity.from(email) match
      case Left(_) => _message.set(Some(Left(s"Rejected: '$email' is not an email address.")))
      case Right(identity) =>
        val secret = newSecret()
        IssueFirstBallotCommand(voting.key, identity, TokenId.derive(secret)).run match
          case Right(ballot) =>
            _device.set(Device.HasBallot(voting, secret, ballot))
            _message.set(Some(Right(s"Ballot ${ballot.value.id.name} received. Its secret stays on this device.")))
            feed.notifyChanged()
          case Left(error) => _message.set(Some(Left(Messages.of(error))))

  def cast(choices: Map[String, String]): Unit = _device.now() match
    case Device.HasBallot(voting, secret, ballot) =>
      CastCommand(ballot, CastId.derive(secret), choices).run match
        case Right(receipt) =>
          _device.set(Device.Voted(voting, secret, ballot, receipt))
          _history.update(_ :+ receipt)
          _message.set(Some(Right(s"Vote recorded. Receipt ${receipt.castId.name}.")))
          feed.notifyChanged()
        case Left(error) => _message.set(Some(Left(Messages.of(error))))
    case _ => _message.set(Some(Left("Get a ballot first.")))

  /** Cancels the current cast and receives a fresh ballot under a new secret (F4). */
  def revote(): Unit = _device.now() match
    case Device.Voted(voting, secret, _, _) =>
      val next = newSecret()
      RevoteCommand(voting.key, secret, TokenId.derive(next)).run match
        case Right(ballot) =>
          _device.set(Device.HasBallot(voting, next, ballot))
          _message.set(Some(Right(s"Earlier vote cancelled. New ballot ${ballot.value.id.name}: vote again.")))
          feed.notifyChanged()
        case Left(error) => _message.set(Some(Left(Messages.of(error))))
    case _ => _message.set(Some(Left("There is no recorded vote to change.")))

  /** Forgets everything on this device, as if the voter put the phone away. */
  def leave(): Unit =
    _device.set(Device.Empty)
    _history.set(Nil)
    _message.set(None)

