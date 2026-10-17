package s4y.evoting.demo

import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.voters.app.usecases.ImportVotersCommand
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.app.usecases.{OpenVotingCommand, RegisterVotingCommand}
import s4y.evoting.voting.domain.dsl.*
import s4y.evoting.voting.domain.vo.VotingDefinition

/** Demo data, written through the normal use cases so it passes the same validation. */
object Seed:
  val Mallory = "mallory@x.org"
  val Voters: List[String] = List("alice@x.org", "bob@x.org", "carol@x.org", "dave@x.org", Mallory)
  /** Voters the background traffic acts for; the presenter's voters are not among them. */
  val Background: List[String] = (1 to 40).toList.map(n => f"voter-$n%02d@x.org")

  private def define(result: s4y.evoting.core.domain.Validated[?, VotingDefinition]): VotingDefinition =
    result.toEither.fold(errors => sys.error(s"invalid seed voting: $errors"), identity)

  val chairElection: VotingDefinition = define(
    voting("chair-2027", "Chair election 2027") {
      subject("chair", "Who chairs the association?")("dana", "eli")
    }
  )

  val annualMeeting: VotingDefinition = define(
    voting("annual-2027", "Annual meeting 2027") {
      subject("budget", "Approve the 2027 budget?")("yes", "no")
      subject("board", "Who joins the board?")("alice", "bob", "carol")
    }
  )

  def load()(using UseCase[RegisterVotingCommand], UseCase[OpenVotingCommand], UseCase[ImportVotersCommand]): Unit =
    val voters = (Voters ++ Background).map(v => VoterIdentity.from(v).fold(e => sys.error(s"invalid seed voter: $e"), identity))
    val seeded =
      for
        _ <- RegisterVotingCommand(chairElection).run
        _ <- RegisterVotingCommand(annualMeeting).run
        _ <- ImportVotersCommand(chairElection.key, voters).run
        _ <- ImportVotersCommand(annualMeeting.key, voters).run
        _ <- OpenVotingCommand(chairElection.key).run
      yield ()
    seeded.left.foreach(error => sys.error(s"seeding the demo failed: $error"))
