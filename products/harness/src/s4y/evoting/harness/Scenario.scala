package s4y.evoting.harness

import s4y.evoting.audit.domain.{AuditOperations, AuditReport}
import s4y.evoting.ballots.app.usecases.IssueFirstBallotCommand
import s4y.evoting.ballots.domain.vo.*
import s4y.evoting.casting.app.usecases.{CastCommand, RevoteCommand}
import s4y.evoting.casting.domain.vo.{CastId, Receipt}
import s4y.evoting.core.app.run
import s4y.evoting.core.crypto.*
import s4y.evoting.core.infra.jdk.{JdkCrypto, JdkKeys}
import s4y.evoting.voters.app.usecases.ImportVotersCommand
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.app.usecases.{CloseVotingCommand, OpenVotingCommand, RegisterVotingCommand}
import s4y.evoting.voting.domain.dsl.*
import s4y.evoting.voting.domain.vo.VotingKey
import s4y.evoting.wiring.VotingSystem

/** A fresh system with one open voting and three eligible voters (skill: scala3-context-di,
  * "Scenario DSL"). JVM only: JDK crypto, deterministic keys and secrets. */
final class Scenario private[harness] (val system: VotingSystem, val voting: VotingKey):
  private var nextSecret = 0
  private[harness] def newSecret(): BallotSecret =
    nextSecret += 1
    BallotSecret.from(IArray.tabulate[Byte](32)(i => ((nextSecret * 31 + i) % 251).toByte)).toOption.get

/** Runs `body` against a freshly wired system. */
def scenario(body: Scenario ?=> Unit): Unit =
  val system = VotingSystem(
    JdkCrypto,
    JdkKeys.fromSeed[Party.Issuer](IArray.fill[Byte](32)(1)),
    JdkKeys.fromSeed[Party.Operator](IArray.fill[Byte](32)(2))
  )
  import system.given
  val definition = voting("annual-2027", "Annual meeting 2027") {
    subject("budget", "Approve the 2027 budget?")("yes", "no")
    subject("board", "Who joins the board?")("alice", "bob")
  }.toEither.fold(e => sys.error(s"invalid scenario voting: $e"), identity)
  val eligible = List("alice@x.org", "bob@x.org", "carol@x.org").map(VoterIdentity.from(_).toOption.get)
  val setUp =
    for
      _ <- RegisterVotingCommand(definition).run
      _ <- ImportVotersCommand(definition.key, eligible).run
      _ <- OpenVotingCommand(definition.key).run
    yield ()
  setUp.left.foreach(e => sys.error(s"scenario set-up failed: $e"))
  body(using Scenario(system, definition.key))

/** One voter's device: holds the current secret and ballot, as the voter app would. */
final class VoterHandle private[harness] (identity: VoterIdentity)(using sc: Scenario):
  import sc.system.given
  private var secret: BallotSecret = sc.newSecret()
  private var ballot: Option[Signed[Party.Issuer, BallotToken]] = None

  def getsBallot(): Either[Any, Signed[Party.Issuer, BallotToken]] =
    IssueFirstBallotCommand(sc.voting, identity, TokenId.derive(secret)).run.map { b => ballot = Some(b); b }

  def casts(choices: (String, String)*): Either[Any, Receipt] =
    ballot.toRight("no ballot").flatMap(b => CastCommand(b, CastId.derive(secret), choices.toMap).run)

  /** Cancels the current cast and keeps the new ballot (F4). */
  def revotes(): Either[Any, Signed[Party.Issuer, BallotToken]] =
    val next = sc.newSecret()
    RevoteCommand(sc.voting, secret, TokenId.derive(next)).run.map { b =>
      secret = next
      ballot = Some(b)
      b
    }

def voter(email: String)(using Scenario): VoterHandle = VoterHandle(VoterIdentity.from(email).toOption.get)

def closeVoting()(using sc: Scenario): Unit =
  import sc.system.given
  CloseVotingCommand(sc.voting).run.left.foreach(e => sys.error(s"close failed: $e"))

def audit()(using sc: Scenario): AuditReport =
  import sc.system.given
  val record = sc.system.publicRecord(sc.voting).toOption.flatten.getOrElse(sys.error("no public record"))
  AuditOperations.audit(record)

/** The tally for one subject, e.g. `tally("budget")` → `Map("yes" -> 1, "no" -> 0)`. */
def tally(subject: String)(using Scenario): Map[String, Int] =
  audit().tally.collect { case ((s, option), n) if s == subject => option -> n }
