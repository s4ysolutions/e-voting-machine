package s4y.evoting.casting.ui.cheat.state

import com.raquo.laminar.api.L.*
import s4y.evoting.ballots.app.usecases.IssueFirstBallotCommand
import s4y.evoting.ballots.domain.vo.{BallotKind, BallotSecret, BallotToken, TokenId}
import s4y.evoting.casting.app.usecases.{CastCommand, RevoteCommand}
import s4y.evoting.casting.domain.vo.CastId
import s4y.evoting.casting.ui.Messages
import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.core.crypto.*
import s4y.evoting.core.ui.ChangeFeed
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.app.usecases.ListVotingsQuery
import s4y.evoting.voting.domain.vo.{StoredVoting, VotingDefinition, VotingStatus}

/** A signing key that is not the issuer's, for the forged-ballot attempt. Built by the composition root. */
final case class Forger(signer: Signer[Party.Issuer])

/** The cheats from design doc F8. */
enum Cheat(val title: String, val attempt: String) derives CanEqual:
  case DoubleBallot extends Cheat("Second ballot", "Mallory asks the issuer for a second first ballot.")
  case TokenReplay  extends Cheat("Token replay", "Mallory casts her spent ballot again under a new receipt.")
  case Forged       extends Cheat("Forged ballot", "Mallory signs her own ballot with a key that is not the issuer's.")
  case NoSecret     extends Cheat("Cancel someone's vote", "Mallory tries to cancel a vote without its secret.")
  case DoubleCancel extends Cheat("Cancel twice", "Mallory re-votes, then replays her old secret to cancel again.")

/** What happened when a cheat was tried. `steps` are the honest preparations (shown in grey). */
final case class CheatOutcome(cheat: Cheat, steps: List[String], rejected: Boolean, result: String)

/** State holder for the "try to cheat" panel. Every attempt runs the real use cases; nothing is mocked. */
final class CheatSession(mallory: VoterIdentity)(using
    UseCase[ListVotingsQuery],
    UseCase[IssueFirstBallotCommand],
    UseCase[CastCommand],
    UseCase[RevoteCommand]
)(using feed: ChangeFeed, crypto: Crypto, randomness: Randomness, forger: Forger):
  private val _outcomes = Var[List[CheatOutcome]](Nil)
  /** The latest attempt first. */
  val outcomes: Signal[List[CheatOutcome]] = _outcomes.signal

  // Mallory's own device: her current secret, ballot, and whether that ballot was cast.
  private var secret: Option[BallotSecret]                      = None
  private var ballot: Option[Signed[Party.Issuer, BallotToken]] = None
  private var cast                                              = false

  private def newSecret(): BallotSecret = BallotSecret.from(randomness.bytes(32)).fold(e => sys.error(s"$e"), identity)

  private def openVoting(): Option[VotingDefinition] =
    ListVotingsQuery().run.toOption.flatMap(_.collectFirst {
      case s: StoredVoting if s.status == VotingStatus.Open => s.definition
    })

  private def firstChoices(v: VotingDefinition): Map[String, String] =
    v.subjects.map(s => s.key.value -> s.options.head.value).toMap

  /** Honest preparation: Mallory holds a ballot and has cast it. Returns the steps taken. */
  private def ensureCast(v: VotingDefinition): Either[String, List[String]] =
    val issued =
      if ballot.isDefined then Right(Nil)
      else
        val s = newSecret()
        IssueFirstBallotCommand(v.key, mallory, TokenId.derive(s)).run match
          case Right(b) => secret = Some(s); ballot = Some(b); Right(List(s"Mallory gets her ballot ${b.value.id.name}."))
          case Left(e)  => Left(Messages.of(e))
    issued.flatMap { steps =>
      if cast then Right(steps)
      else
        CastCommand(ballot.get, CastId.derive(secret.get), firstChoices(v)).run match
          case Right(r) => cast = true; Right(steps :+ s"Mallory votes honestly, receipt ${r.castId.name}.")
          case Left(e)  => Left(Messages.of(e))
    }

  def attempt(cheat: Cheat): Unit =
    val outcome = openVoting() match
      case None    => CheatOutcome(cheat, Nil, rejected = true, "Open a voting first.")
      case Some(v) => run(cheat, v)
    _outcomes.update(outcome :: _)
    feed.notifyChanged()

  private def run(cheat: Cheat, v: VotingDefinition): CheatOutcome =
    def outcome(steps: List[String], result: Either[String, String]) =
      CheatOutcome(cheat, steps, result.isLeft, result.fold(identity, ok => s"ACCEPTED — this should never happen: $ok"))
    def prepared(f: List[String] => CheatOutcome) =
      ensureCast(v).fold(e => CheatOutcome(cheat, Nil, rejected = true, s"Preparation failed: $e"), f)

    cheat match
      case Cheat.DoubleBallot =>
        prepared { steps =>
          val attempt = IssueFirstBallotCommand(v.key, mallory, TokenId.derive(newSecret())).run
          outcome(steps, attempt.left.map(Messages.of).map(b => s"second ballot ${b.value.id.name}"))
        }
      case Cheat.TokenReplay =>
        prepared { steps =>
          val attempt = CastCommand(ballot.get, CastId.derive(newSecret()), firstChoices(v)).run
          outcome(steps, attempt.left.map(Messages.of).map(r => s"receipt ${r.castId.name}"))
        }
      case Cheat.Forged =>
        val s       = newSecret()
        val fake    = forger.signer.sign(BallotToken(v.key, TokenId.derive(s), BallotKind.First))
        val attempt = CastCommand(fake, CastId.derive(s), firstChoices(v)).run
        outcome(List(s"Mallory signs ballot ${fake.value.id.name} with her own key, tag #${fake.signature.tag}."),
          attempt.left.map(Messages.of).map(r => s"receipt ${r.castId.name}"))
      case Cheat.NoSecret =>
        val attempt = RevoteCommand(v.key, newSecret(), TokenId.derive(newSecret())).run
        outcome(List("Mallory guesses a secret."), attempt.left.map(Messages.of).map(b => s"re-vote ballot ${b.value.id.name}"))
      case Cheat.DoubleCancel =>
        prepared { steps =>
          val old  = secret.get
          val next = newSecret()
          RevoteCommand(v.key, old, TokenId.derive(next)).run match
            case Left(e) => CheatOutcome(cheat, steps, rejected = true, s"Preparation failed: ${Messages.of(e)}")
            case Right(b) =>
              secret = Some(next); ballot = Some(b); cast = false
              val again = RevoteCommand(v.key, old, TokenId.derive(newSecret())).run
              outcome(steps :+ s"Mallory re-votes honestly and gets ballot ${b.value.id.name}.",
                again.left.map(Messages.of).map(b2 => s"another ballot ${b2.value.id.name}"))
        }
