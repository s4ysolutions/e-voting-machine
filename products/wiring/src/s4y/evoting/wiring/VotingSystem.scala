package s4y.evoting.wiring

import s4y.evoting.audit.domain.PublicRecord
import s4y.evoting.ballots.app.usecases.*
import s4y.evoting.ballots.domain.vo.{BallotError, TokenId}
import s4y.evoting.ballots.domain.workflows.BallotWorkflows
import s4y.evoting.ballots.domain.workflows.ports.*
import s4y.evoting.ballots.infra.memory.MemoryIssuanceCounter
import s4y.evoting.casting.app.usecases.*
import s4y.evoting.casting.domain.vo.CastError
import s4y.evoting.casting.domain.workflows.ports.*
import s4y.evoting.casting.infra.memory.*
import s4y.evoting.core.app.UseCase
import s4y.evoting.core.crypto.*
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.infra.memory.{MemoryTransactionManager, MemoryTx}
import s4y.evoting.core.tx.TransactionManager
import s4y.evoting.voters.app.usecases.*
import s4y.evoting.voters.domain.vo.{VoterIdentity, VotersError}
import s4y.evoting.voters.domain.workflows.VotersWorkflows
import s4y.evoting.voters.domain.workflows.ports.VotersRepository
import s4y.evoting.voters.infra.memory.MemoryVotersRepository
import s4y.evoting.voting.app.usecases.*
import s4y.evoting.voting.domain.operations.VotingOperations
import s4y.evoting.voting.domain.vo.VotingKey
import s4y.evoting.voting.domain.workflows.ports.VotingRepository
import s4y.evoting.voting.infra.memory.MemoryVotingRepository

/** The whole system wired with in-memory adapters (skill: composition-root). Shared by the
  * browser demo (noble crypto) and the JVM harness (JDK crypto); only products depend on it.
  * Each instance is independent, so a demo reset or a test simply builds a new one. */
final class VotingSystem(crypto: Crypto, issuerKeys: Keys[Party.Issuer], operatorKeys: Keys[Party.Operator]):
  type TXM = MemoryTx

  // 1. capabilities and ports
  given Crypto                       = crypto
  given Signer[Party.Issuer]         = issuerKeys.signer
  given Verifier[Party.Issuer]       = issuerKeys.verifier
  given Signer[Party.Operator]       = operatorKeys.signer
  given Verifier[Party.Operator]     = operatorKeys.verifier
  given TransactionManager[TXM]      = MemoryTransactionManager()
  given votings: VotingRepository[TXM] = MemoryVotingRepository()
  given voters: VotersRepository[TXM]  = MemoryVotersRepository()

  // 2. cross-context adapters: they translate, they decide nothing
  private val votingGate: VotingGate[TXM] = new VotingGate[TXM]:
    def requireOpen(voting: VotingKey)(using TXM): Either[BallotError, Unit] =
      votings.find(voting).toRight(BallotError.VotingNotOpen(voting))
        .flatMap(VotingOperations.requireOpen(_).left.map(_ => BallotError.VotingNotOpen(voting))).map(_ => ())

  private val eligibility: Eligibility[TXM] = new Eligibility[TXM]:
    def markFirstBallotIssued(voting: VotingKey, identity: VoterIdentity)(using TXM): Either[BallotError, Unit] =
      VotersWorkflows.markBallotIssued[TXM](voting, identity).left.map {
        case VotersError.BallotAlreadyIssued(_, _) => BallotError.AlreadyIssued(voting)
        case _                                     => BallotError.NotEligible(voting)
      }.map(_ => ())

  given IssuerPorts[TXM] = IssuerPorts(votingGate, eligibility, MemoryIssuanceCounter())

  private val openVotings: OpenVotings[TXM] = new OpenVotings[TXM]:
    def requireOpen(voting: VotingKey)(using TXM) =
      votings.find(voting).toRight(CastError.VotingNotOpen(voting))
        .flatMap(VotingOperations.requireOpen(_).left.map(_ => CastError.VotingNotOpen(voting))).map(_.definition)

  private val revoteBallots: RevoteBallots[TXM] = new RevoteBallots[TXM]:
    def issue(voting: VotingKey, next: TokenId)(using TXM) =
      BallotWorkflows.issueRevote[TXM](voting, next).left.map(e => InfraFailure(s"re-vote ballot: $e"))

  given CastingPorts[TXM] = CastingPorts(MemoryLedger(), MemorySpentTokens(), MemoryCancelledCasts(), openVotings, revoteBallots)

  // 3. use cases
  given UseCase[RegisterVotingCommand]   = RegisterVotingUseCase[TXM]()
  given UseCase[OpenVotingCommand]       = OpenVotingUseCase[TXM]()
  given UseCase[CloseVotingCommand]      = CloseVotingUseCase[TXM]()
  given UseCase[ListVotingsQuery]        = ListVotingsUseCase[TXM]()
  given UseCase[ImportVotersCommand]     = ImportVotersUseCase[TXM]()
  given UseCase[PublicRegistryQuery]     = PublicRegistryUseCase[TXM]()
  given UseCase[IssueFirstBallotCommand] = IssueFirstBallotUseCase[TXM]()
  given UseCase[IssuanceCountsQuery]     = IssuanceCountsUseCase[TXM]()
  given UseCase[CastCommand]             = CastUseCase[TXM]()
  given UseCase[RevoteCommand]           = RevoteUseCase[TXM]()
  given UseCase[LedgerQuery]             = LedgerUseCase[TXM]()

  /** Everything an outside auditor can read about one voting. */
  def publicRecord(voting: VotingKey): Either[InfraFailure, Option[PublicRecord]] =
    val ports = summon[CastingPorts[TXM]]
    summon[TransactionManager[TXM]].atomically[Nothing, Option[PublicRecord]]("public record") {
      Right(votings.find(voting).map { stored =>
        val registry = voters.publicView(voting)
        PublicRecord(
          stored.definition,
          ports.ledger.entries,
          ports.spent.all.filter(_.value.voting == voting),
          summon[IssuerPorts[TXM]].counter.counts(voting),
          registrySize = registry.size,
          ballotsIssuedFlags = registry.count(_._2.ballotIssued)
        )
      })
    }
