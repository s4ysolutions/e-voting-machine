package s4y.evoting.voting.domain.workflows

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.core.infra.memory.{MemoryTransactionManager, MemoryTx}
import s4y.evoting.core.tx.TransactionManager
import s4y.evoting.voting.domain.dsl.*
import s4y.evoting.voting.domain.vo.*
import s4y.evoting.voting.domain.workflows.ports.VotingRepository
import s4y.evoting.voting.infra.memory.MemoryVotingRepository

class VotingWorkflowsSpec extends AnyFunSuite:

  private val definition: VotingDefinition =
    voting("annual-2027", "Annual meeting 2027") {
      subject("budget", "Approve the 2027 budget?")("yes", "no")
    }.toEither.fold(errors => fail(s"unexpected: $errors"), identity)

  private def fresh(): (TransactionManager[MemoryTx], VotingRepository[MemoryTx]) =
    (MemoryTransactionManager(), MemoryVotingRepository())

  test("register, open and close a voting"):
    val (tm, repo) = fresh()
    given TransactionManager[MemoryTx] = tm
    given VotingRepository[MemoryTx]   = repo
    assert(VotingWorkflows.register(definition).isRight)
    assert(VotingWorkflows.open(definition.key).isRight)
    assert(VotingWorkflows.close(definition.key).isRight)
    assert(VotingWorkflows.list.map(_.map(_.status)) == Right(List(VotingStatus.Closed)))

  test("rejected steps say why and change nothing"):
    val (tm, repo) = fresh()
    given TransactionManager[MemoryTx] = tm
    given VotingRepository[MemoryTx]   = repo
    VotingWorkflows.register(definition)
    assert(VotingWorkflows.register(definition) == Left(LifecycleError.AlreadyExists(definition.key)))
    assert(VotingWorkflows.close(definition.key) == Left(LifecycleError.NotOpen(definition.key, VotingStatus.Planned)))
    VotingWorkflows.open(definition.key)
    assert(VotingWorkflows.open(definition.key) == Left(LifecycleError.NotPlanned(definition.key, VotingStatus.Open)))
    assert(VotingWorkflows.list.map(_.map(_.status)) == Right(List(VotingStatus.Open)))

  test("an unknown voting is reported"):
    val (tm, repo) = fresh()
    given TransactionManager[MemoryTx] = tm
    given VotingRepository[MemoryTx]   = repo
    val unknown = VotingKey.from("nope").toOption.get
    assert(VotingWorkflows.open(unknown) == Left(LifecycleError.NotFound(unknown)))
