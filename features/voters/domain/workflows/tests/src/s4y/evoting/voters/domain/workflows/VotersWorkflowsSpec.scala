package s4y.evoting.voters.domain.workflows

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.core.infra.memory.{MemoryTransactionManager, MemoryTx}
import s4y.evoting.core.tx.TransactionManager
import s4y.evoting.voters.domain.vo.*
import s4y.evoting.voters.domain.workflows.ports.VotersRepository
import s4y.evoting.voters.infra.memory.MemoryVotersRepository
import s4y.evoting.voting.domain.vo.VotingKey

class VotersWorkflowsSpec extends AnyFunSuite:
  private val voting = VotingKey.from("annual-2027").toOption.get
  private def id(raw: String): VoterIdentity = VoterIdentity.from(raw).toOption.get

  private def withRegistry(test: (TransactionManager[MemoryTx], VotersRepository[MemoryTx]) ?=> Unit): Unit =
    var n = 0
    given TransactionManager[MemoryTx] = MemoryTransactionManager()
    given VotersRepository[MemoryTx]   = MemoryVotersRepository(() => { n += 1; s"p-$n" })
    test

  test("identities are validated and normalised"):
    assert(VoterIdentity.from(" Alice@Example.org ").map(_.value) == Right("alice@example.org"))
    assert(VoterIdentity.from("not an email").isLeft)

  test("import is all or nothing"):
    withRegistry {
      assert(VotersWorkflows.importVoters(voting, List(id("a@x.org"), id("b@x.org"))).map(_.size) == Right(2))
      val again = VotersWorkflows.importVoters(voting, List(id("c@x.org"), id("a@x.org")))
      assert(again == Left(VotersError.AlreadyRegistered(voting, id("a@x.org"))))
      assert(VotersWorkflows.publicRegistry(voting).map(_.size) == Right(2))   // c@x.org rolled back
    }

  test("a ballot is issued once per eligible voter, and the public view shows only the flag"):
    withRegistry {
      VotersWorkflows.importVoters(voting, List(id("a@x.org")))
      val tm = summon[TransactionManager[MemoryTx]]
      def issue(who: String) = tm.atomically[VotersError, Unit]("issue")(VotersWorkflows.markBallotIssued(voting, id(who)).map(_ => ()))
      assert(issue("a@x.org") == Right(()))
      assert(issue("a@x.org").left.toOption.exists(_.isInstanceOf[VotersError.BallotAlreadyIssued]))
      assert(issue("z@x.org") == Left(VotersError.NotEligible(voting, id("z@x.org"))))
      assert(VotersWorkflows.publicRegistry(voting).map(_.map(_._2)) == Right(List(PublicVoter(ballotIssued = true))))
    }
