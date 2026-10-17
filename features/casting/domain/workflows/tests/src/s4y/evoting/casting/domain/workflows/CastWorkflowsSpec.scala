package s4y.evoting.casting.domain.workflows

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.ballots.domain.vo.*
import s4y.evoting.casting.domain.operations.CastOperations
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.casting.domain.workflows.ports.*
import s4y.evoting.casting.infra.memory.*
import s4y.evoting.core.crypto.*
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.infra.jdk.{JdkCrypto, JdkKeys}
import s4y.evoting.core.infra.memory.{MemoryTransactionManager, MemoryTx}
import s4y.evoting.core.tx.TransactionManager
import s4y.evoting.voting.domain.dsl.*
import s4y.evoting.voting.domain.vo.{VotingDefinition, VotingKey}

class CastWorkflowsSpec extends AnyFunSuite:
  private given Crypto  = JdkCrypto
  private val issuer    = JdkKeys.fromSeed[Party.Issuer](IArray.fill[Byte](32)(7))
  private val operator  = JdkKeys.fromSeed[Party.Operator](IArray.fill[Byte](32)(8))
  private val definition: VotingDefinition = voting("annual-2027", "Annual meeting 2027") {
    subject("budget", "Approve the 2027 budget?")("yes", "no")
    subject("board", "Who joins the board?")("alice", "bob")
  }.toEither.toOption.get
  private val key = definition.key

  private def secret(n: Int): BallotSecret = BallotSecret.from(IArray.fill[Byte](32)(n.toByte)).toOption.get
  private def ballot(s: BallotSecret, kind: BallotKind = BallotKind.First) =
    issuer.signer.sign(BallotToken(key, TokenId.derive(s), kind))

  /** A fresh Cast Service with in-memory adapters; the voting can be closed with `open = false`. */
  private final class World(open: Boolean = true):
    given TransactionManager[MemoryTx] = MemoryTransactionManager()
    given CastingPorts[MemoryTx] = CastingPorts(
      MemoryLedger(), MemorySpentTokens(), MemoryCancelledCasts(),
      new OpenVotings[MemoryTx]:
        def requireOpen(v: VotingKey)(using MemoryTx) = Either.cond(open, definition, CastError.VotingNotOpen(v))
      ,
      new RevoteBallots[MemoryTx]:
        def issue(v: VotingKey, next: TokenId)(using MemoryTx) = Right(issuer.signer.sign(BallotToken(v, next, BallotKind.Revote)))
    )
    given Verifier[Party.Issuer]  = issuer.verifier
    given Signer[Party.Operator]  = operator.signer

    def cast(s: BallotSecret, choices: (String, String)*): Either[CastError | InfraFailure, Receipt] =
      CastWorkflows.cast[MemoryTx](ballot(s), CastId.derive(s), choices.toMap)
    def revote(s: BallotSecret, next: BallotSecret, in: VotingKey = key) =
      CastWorkflows.revote[MemoryTx](in, s, TokenId.derive(next))
    def entries: List[LedgerEntry] = CastWorkflows.ledger[MemoryTx].toOption.get
    def tally: Map[(String, String), Int] =
      entries.groupMapReduce(e => (e.value.subject.value, e.value.option.value))(_.value.delta.value)(_ + _)

  test("a cast appends one signed +1 entry per subject, hash-chained from genesis"):
    val w = World()
    val receipt = w.cast(secret(1), "budget" -> "yes", "board" -> "bob").toOption.get
    assert(receipt.entries.map(e => e.value.subject.value -> e.value.option.value) == List("budget" -> "yes", "board" -> "bob"))
    assert(receipt.entries.forall(operator.verifier.verify))
    assert(w.entries.head.value.prevHash == CastOperations.GenesisHash)
    assert(w.entries(1).value.prevHash == CastOperations.entryHash(w.entries.head))
    assert(w.entries.map(_.value.seq) == List(1L, 2L))

  test("X2: the same token cannot be cast twice"):
    val w = World()
    w.cast(secret(1), "budget" -> "yes", "board" -> "bob")
    import w.given
    val again = CastWorkflows.cast[MemoryTx](ballot(secret(1)), CastId.derive(secret(2)), Map("budget" -> "no", "board" -> "bob"))
    assert(again == Left(CastError.TokenAlreadySpent))
    assert(w.entries.size == 2)

  test("X3: a ballot signed by anyone but the issuer is rejected"):
    val w      = World()
    val forger = JdkKeys.fromSeed[Party.Issuer](IArray.fill[Byte](32)(66))
    val forged = forger.signer.sign(BallotToken(key, TokenId.derive(secret(1)), BallotKind.First))
    import w.given
    assert(CastWorkflows.cast[MemoryTx](forged, CastId.derive(secret(1)), Map("budget" -> "yes", "board" -> "bob")) ==
      Left(CastError.InvalidIssuerSignature))

  test("X4: every subject needs exactly one valid option"):
    val w = World()
    assert(w.cast(secret(1), "budget" -> "maybe", "board" -> "bob") ==
      Left(CastError.InvalidChoices(List("maybe is not an option of budget"))))
    assert(w.cast(secret(1), "budget" -> "yes") == Left(CastError.InvalidChoices(List("no option chosen for board"))))
    assert(w.entries.isEmpty)

  test("a re-vote cancels the old cast with unlinked -1 entries and issues a re-vote ballot"):
    val w = World()
    w.cast(secret(1), "budget" -> "yes", "board" -> "bob")
    val next = w.revote(secret(1), secret(2)).toOption.get
    assert(next.value.kind == BallotKind.Revote)
    val minus = w.entries.filter(_.value.delta == Delta.Minus)
    assert(minus.size == 2)
    assert(minus.forall(e => e.value.castId.isEmpty && e.value.token.isEmpty))   // nothing points at the old receipt
    import w.given
    CastWorkflows.cast[MemoryTx](next, CastId.derive(secret(2)), Map("budget" -> "no", "board" -> "alice"))
    assert(w.tally == Map(("budget", "yes") -> 0, ("budget", "no") -> 1, ("board", "bob") -> 0, ("board", "alice") -> 1))

  test("X5 and X6: a re-vote needs the secret of a real cast, and works only once"):
    val w = World()
    w.cast(secret(1), "budget" -> "yes", "board" -> "bob")
    assert(w.revote(secret(9), secret(2)) == Left(CastError.NoCastForSecret))
    assert(w.revote(secret(1), secret(2)).isRight)
    assert(w.revote(secret(1), secret(3)) == Left(CastError.CastAlreadyCancelled))

  test("a re-vote is refused for a cast in another voting, and nothing is cancelled"):
    val w     = World()
    val other = VotingKey.from("other-2027").toOption.get
    w.cast(secret(1), "budget" -> "yes", "board" -> "bob")
    assert(w.revote(secret(1), secret(2), in = other) == Left(CastError.WrongVoting(other)))
    assert(w.revote(secret(1), secret(2)).isRight)

  test("a re-vote is refused when the next token is already spent, and the cast stays live"):
    val w = World()
    w.cast(secret(1), "budget" -> "yes", "board" -> "bob")
    assert(w.revote(secret(1), secret(1)) == Left(CastError.TokenAlreadySpent))
    assert(w.entries.size == 2)
    assert(w.revote(secret(1), secret(2)).isRight)

  test("X7: nothing is accepted while the voting is not open"):
    val w = World(open = false)
    assert(w.cast(secret(1), "budget" -> "yes", "board" -> "bob") == Left(CastError.VotingNotOpen(key)))
