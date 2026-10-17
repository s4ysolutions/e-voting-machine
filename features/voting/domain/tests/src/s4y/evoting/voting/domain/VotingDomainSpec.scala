package s4y.evoting.voting.domain

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.core.domain.Validated
import s4y.evoting.voting.domain.dsl.*
import s4y.evoting.voting.domain.operations.VotingOperations
import s4y.evoting.voting.domain.vo.*

class VotingDomainSpec extends AnyFunSuite:

  private def annualMeeting: VotingDefinition =
    voting("annual-2027", "Annual meeting 2027") {
      subject("budget", "Approve the 2027 budget?")("yes", "no")
      subject("board", "Who joins the board?")("alice", "bob", "carol")
    }.toEither.fold(errors => fail(s"unexpected: $errors"), identity)

  test("the DSL builds a valid definition"):
    val d = annualMeeting
    assert(d.key.value == "annual-2027")
    assert(d.subjects.map(_.key.value) == List("budget", "board"))
    assert(d.subjects.head.options.map(_.value) == List("yes", "no"))

  test("the DSL reports every problem at once"):
    val result = voting("Bad Key", " ") {
      subject("budget", "Approve?")("yes")
      subject("Also Bad", "")("a", "a")
    }
    result match
      case Validated.Invalid(errors) =>
        assert(errors.toList == List(
          VotingError.InvalidVotingKey("Bad Key"),
          VotingError.EmptyTitle,
          VotingError.TooFewOptions("budget"),
          VotingError.InvalidSubjectKey("Also Bad"),
          VotingError.EmptyQuestion("Also Bad"),
          VotingError.DuplicateOption("Also Bad", "a")
        ))
      case Validated.Valid(_) => fail("expected errors")

  test("a voting needs at least one subject, with distinct keys"):
    assert(VotingDefinition.from("v-1", "t", Nil).toEither == Left(::(VotingError.NoSubjects, Nil)))
    val twice = List(RawSubject("q", "?", List("a", "b")), RawSubject("q", "?", List("a", "b")))
    assert(VotingDefinition.from("v-1", "t", twice).toEither == Left(::(VotingError.DuplicateSubject("q"), Nil)))

  test("lifecycle: only a planned voting can be opened, only an open one closed"):
    val planned = Voting.planned(annualMeeting)
    val open    = planned.open
    val closed  = open.close
    assert(VotingOperations.requirePlanned(StoredVoting.Planned(planned)).isRight)
    assert(VotingOperations.requireOpen(StoredVoting.Open(open)).isRight)
    assert(VotingOperations.requireOpen(StoredVoting.Planned(planned)) ==
      Left(LifecycleError.NotOpen(planned.key, VotingStatus.Planned)))
    assert(VotingOperations.requirePlanned(StoredVoting.Closed(closed)) ==
      Left(LifecycleError.NotPlanned(closed.key, VotingStatus.Closed)))
