package s4y.evoting.audit.domain

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.ballots.domain.vo.*
import s4y.evoting.casting.domain.operations.CastOperations
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.casting.domain.workflows.CastWorkflows
import s4y.evoting.casting.domain.workflows.ports.*
import s4y.evoting.casting.infra.memory.*
import s4y.evoting.core.crypto.*
import s4y.evoting.core.infra.jdk.{JdkCrypto, JdkKeys}
import s4y.evoting.core.infra.memory.{MemoryCell, MemoryTransactionManager, MemoryTx}
import s4y.evoting.core.tx.TransactionManager
import s4y.evoting.voting.domain.dsl.*
import s4y.evoting.voting.domain.vo.{VotingDefinition, VotingKey}

class AuditSpec extends AnyFunSuite:
  private given Crypto = JdkCrypto
  private val issuer   = JdkKeys.fromSeed[Party.Issuer](IArray.fill[Byte](32)(7))
  private val operator = JdkKeys.fromSeed[Party.Operator](IArray.fill[Byte](32)(8))
  private given Verifier[Party.Issuer]   = issuer.verifier
  private given Verifier[Party.Operator] = operator.verifier
  private val definition: VotingDefinition = voting("annual-2027", "Annual meeting 2027") {
    subject("budget", "Approve the 2027 budget?")("yes", "no")
    subject("board", "Who joins the board?")("alice", "bob")
  }.toEither.toOption.get

  private def secret(n: Int) = BallotSecret.from(IArray.fill[Byte](32)(n.toByte)).toOption.get

  /** Two voters cast, one of them re-votes: a realistic, honest public record. */
  private def honestRecord(): PublicRecord =
    val spent = MemoryCell(List.empty[Signed[Party.Issuer, BallotToken]])
    var revotes = 0
    given TransactionManager[MemoryTx] = MemoryTransactionManager()
    given CastingPorts[MemoryTx] = CastingPorts(
      MemoryLedger(),
      new SpentTokens[MemoryTx]:
        def contains(t: TokenId)(using MemoryTx) = spent.get.exists(_.value.id == t)
        def add(t: TokenId, b: Signed[Party.Issuer, BallotToken])(using MemoryTx) = spent.update(_ :+ b)
      ,
      MemoryCancelledCasts(),
      new OpenVotings[MemoryTx]:
        def requireOpen(v: VotingKey)(using MemoryTx) = Right(definition)
      ,
      new RevoteBallots[MemoryTx]:
        def issue(v: VotingKey, next: TokenId)(using MemoryTx) =
          revotes += 1
          Right(issuer.signer.sign(BallotToken(v, next, BallotKind.Revote)))
    )
    given Signer[Party.Operator] = operator.signer
    def first(n: Int) = issuer.signer.sign(BallotToken(definition.key, TokenId.derive(secret(n)), BallotKind.First))

    CastWorkflows.cast[MemoryTx](first(1), CastId.derive(secret(1)), Map("budget" -> "yes", "board" -> "bob"))
    CastWorkflows.cast[MemoryTx](first(2), CastId.derive(secret(2)), Map("budget" -> "no", "board" -> "bob"))
    val next = CastWorkflows.revote[MemoryTx](definition.key, secret(1), TokenId.derive(secret(3))).toOption.get
    CastWorkflows.cast[MemoryTx](next, CastId.derive(secret(3)), Map("budget" -> "no", "board" -> "alice"))
    val tm = summon[TransactionManager[MemoryTx]]
    PublicRecord(
      definition,
      CastWorkflows.ledger[MemoryTx].toOption.get,
      tm.atomically[Nothing, List[Signed[Party.Issuer, BallotToken]]]("read")(Right(spent.get)).toOption.get,
      IssuanceCounts(firstBallots = 2, revoteBallots = revotes),
      registrySize = 3,
      ballotsIssuedFlags = 2
    )

  private def check(report: AuditReport, invariant: Invariant): Check = report.checks.toMap.apply(invariant)

  test("an honest record passes every check and tallies only the final votes"):
    val report = AuditOperations.audit(honestRecord())
    assert(report.allPassed, report.checks)
    assert(check(report, Invariant.I7).isInstanceOf[Check.NotChecked])
    assert(report.tally == Map(("budget", "yes") -> 0, ("budget", "no") -> 2, ("board", "bob") -> 1, ("board", "alice") -> 1))

  test("I1: reordering the ledger is detected (F8 X9)"):
    val record = honestRecord()
    val swapped = record.copy(ledger = record.ledger.updated(0, record.ledger(1)).updated(1, record.ledger(0)))
    assert(check(AuditOperations.audit(swapped), Invariant.I1).isInstanceOf[Check.Failed])

  test("I5: a -1 entry without a re-vote ballot is detected, even when the operator signs it (F8 X10)"):
    val record = honestRecord()
    val (seq, prev) = (record.ledger.size.toLong, CastOperations.entryHash(record.ledger.last))
    val subject = definition.subjects.head
    val forged  = operator.signer.sign(EntryBody(seq + 1, definition.key, subject.key, subject.options.head, Delta.Minus, None, None, prev))
    val report  = AuditOperations.audit(record.copy(ledger = record.ledger :+ forged))
    assert(check(report, Invariant.I1) == Check.Passed)          // signed and chained correctly
    assert(check(report, Invariant.I5).isInstanceOf[Check.Failed])

  test("I4: issuance counts that disagree with the registry are detected"):
    val record = honestRecord()
    assert(check(AuditOperations.audit(record.copy(ballotsIssuedFlags = 1)), Invariant.I4).isInstanceOf[Check.Failed])
    assert(check(AuditOperations.audit(record.copy(registrySize = 1)), Invariant.I4).isInstanceOf[Check.Failed])
