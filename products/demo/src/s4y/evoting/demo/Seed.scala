package s4y.evoting.demo

import s4y.evoting.core.app.{UseCase, run}
import s4y.evoting.voting.app.usecases.{OpenVotingCommand, RegisterVotingCommand}
import s4y.evoting.voting.domain.dsl.*
import s4y.evoting.voting.domain.vo.VotingDefinition

/** Demo data, written through the normal use cases so it passes the same validation. */
object Seed:
  private def define(result: s4y.evoting.core.domain.Validated[?, VotingDefinition]): VotingDefinition =
    result.toEither.fold(errors => sys.error(s"invalid seed voting: $errors"), identity)

  val annualMeeting: VotingDefinition = define(
    voting("annual-2027", "Annual meeting 2027") {
      subject("budget", "Approve the 2027 budget?")("yes", "no")
      subject("board", "Who joins the board?")("alice", "bob", "carol")
    }
  )

  val boardElection: VotingDefinition = define(
    voting("chair-2027", "Chair election 2027") {
      subject("chair", "Who chairs the association?")("dana", "eli")
    }
  )

  def load()(using UseCase[RegisterVotingCommand], UseCase[OpenVotingCommand]): Unit =
    val seeded =
      for
        _ <- RegisterVotingCommand(annualMeeting).run
        _ <- RegisterVotingCommand(boardElection).run
        _ <- OpenVotingCommand(boardElection.key).run
      yield ()
    seeded.left.foreach(error => sys.error(s"seeding the demo failed: $error"))
