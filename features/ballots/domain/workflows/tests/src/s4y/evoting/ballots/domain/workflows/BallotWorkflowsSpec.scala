package s4y.evoting.ballots.domain.workflows

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.ballots.domain.vo.*
import s4y.evoting.ballots.domain.workflows.ports.*
import s4y.evoting.ballots.infra.memory.MemoryIssuanceCounter
import s4y.evoting.core.crypto.*
import s4y.evoting.core.infra.jdk.{JdkCrypto, JdkKeys}
import s4y.evoting.core.infra.memory.{MemoryCell, MemoryTransactionManager, MemoryTx}
import s4y.evoting.core.tx.TransactionManager
import s4y.evoting.voters.domain.vo.VoterIdentity
import s4y.evoting.voting.domain.vo.VotingKey

class BallotWorkflowsSpec extends AnyFunSuite:
  private given Crypto = JdkCrypto
  private val voting   = VotingKey.from("annual-2027").toOption.get
  private val alice    = VoterIdentity.from("alice@x.org").toOption.get
  private val issuer   = JdkKeys.fromSeed[Party.Issuer](IArray.fill[Byte](32)(7))
  private def token(n: Byte) = TokenId.derive(BallotSecret.from(IArray.fill[Byte](32)(n)).toOption.get)

  /** Fakes for the cross-context ports: an open/closed switch and a set of flagged voters. */
  private final class Fakes(open: Boolean):
    val flagged = MemoryCell(Set.empty[VoterIdentity])
    val gate: VotingGate[MemoryTx] = new VotingGate[MemoryTx]:
      def requireOpen(v: VotingKey)(using MemoryTx) = Either.cond(open, (), BallotError.VotingNotOpen(v))
    val spentTokens = MemoryCell(Set.empty[TokenId])
    val spent: SpentTokenCheck[MemoryTx] = new SpentTokenCheck[MemoryTx]:
      def isSpent(t: TokenId)(using MemoryTx) = spentTokens.get.contains(t)
    val eligibility: Eligibility[MemoryTx] = new Eligibility[MemoryTx]:
      def markFirstBallotIssued(v: VotingKey, who: VoterIdentity)(using MemoryTx) =
        if flagged.get.contains(who) then Left(BallotError.AlreadyIssued(v))
        else Right(flagged.update(_ + who))

  private def issuing(open: Boolean, spent: Set[TokenId] = Set.empty)(test: (TransactionManager[MemoryTx], IssuerPorts[MemoryTx], Signer[Party.Issuer]) ?=> Unit): Unit =
    val fakes = Fakes(open)
    given TransactionManager[MemoryTx] = MemoryTransactionManager()
    if spent.nonEmpty then summon[TransactionManager[MemoryTx]].atomically[Nothing, Unit]("seed")(Right(fakes.spentTokens.update(_ ++ spent)))
    given IssuerPorts[MemoryTx]        = IssuerPorts(fakes.gate, fakes.eligibility, MemoryIssuanceCounter(), fakes.spent)
    given Signer[Party.Issuer]         = issuer.signer
    test

  test("a first ballot is signed by the issuer and counted"):
    issuing(open = true) {
      val ballot = BallotWorkflows.issueFirst(voting, alice, token(1))
      assert(ballot.exists(issuer.verifier.verify))
      assert(ballot.map(_.value.kind) == Right(BallotKind.First))
      assert(BallotWorkflows.counts(voting) == Right(IssuanceCounts(1, 0)))
    }

  test("a second first ballot is rejected and nothing is counted (F8 X1)"):
    issuing(open = true) {
      BallotWorkflows.issueFirst(voting, alice, token(1))
      assert(BallotWorkflows.issueFirst(voting, alice, token(2)) == Left(BallotError.AlreadyIssued(voting)))
      assert(BallotWorkflows.counts(voting) == Right(IssuanceCounts(1, 0)))
    }

  test("no ballot while the voting is not open"):
    issuing(open = false) {
      assert(BallotWorkflows.issueFirst(voting, alice, token(1)) == Left(BallotError.VotingNotOpen(voting)))
      assert(BallotWorkflows.counts(voting) == Right(IssuanceCounts(0, 0)))
    }

  test("a spent token is refused before the voter is flagged or anything counted"):
    issuing(open = true, spent = Set(token(1))) {
      assert(BallotWorkflows.issueFirst(voting, alice, token(1)) == Left(BallotError.TokenAlreadySpent))
      assert(BallotWorkflows.issueFirst(voting, alice, token(2)).isRight)
      assert(BallotWorkflows.counts(voting) == Right(IssuanceCounts(1, 0)))
    }
