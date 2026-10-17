package s4y.evoting.casting.domain.workflows

import s4y.evoting.ballots.domain.vo.{BallotSecret, BallotToken, TokenId}
import s4y.evoting.casting.domain.operations.CastOperations
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.casting.domain.workflows.ports.CastingPorts
import s4y.evoting.core.crypto.*
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.{TransactionManager, Tx}
import s4y.evoting.voting.domain.vo.VotingKey

/** The Cast Service (design doc F3, F4). Each call is one atomic commit. */
object CastWorkflows:

  type Casting[TX <: Tx, A] =
    (TransactionManager[TX], CastingPorts[TX], Verifier[Party.Issuer], Signer[Party.Operator], Crypto) ?=> A

  /** F3: check the ballot and the choices, spend the token, append one +1 entry per subject. */
  def cast[TX <: Tx](ballot: Signed[Party.Issuer, BallotToken], castId: CastId, choices: Map[String, String])
      : Casting[TX, Either[CastError | InfraFailure, Receipt]] =
    val ports = summon[CastingPorts[TX]]
    summon[TransactionManager[TX]].atomically[CastError, Receipt]("cast") {
      for
        token      <- CastOperations.checkBallot(ballot)
        definition <- ports.votings.requireOpen(token.voting)
        valid      <- CastOperations.checkChoices(definition, choices)
        _          <- Either.cond(!ports.spent.contains(token.id), (), CastError.TokenAlreadySpent)
        _          <- Either.cond(ports.ledger.byCast(castId).isEmpty, (), CastError.CastIdAlreadyUsed)
      yield
        ports.spent.add(token.id, ballot)
        val entries = CastOperations.chain(ports.ledger.head, definition, valid.toMap, Delta.Plus, Some(castId), Some(token.id))
        ports.ledger.append(entries)
        Receipt(castId, entries)
    }

  /** F4: prove the earlier cast with its secret, cancel it with unlinked -1 entries, issue a new ballot. */
  def revote[TX <: Tx](voting: VotingKey, secret: BallotSecret, next: TokenId)
      : Casting[TX, Either[CastError | InfraFailure, Signed[Party.Issuer, BallotToken]]] =
    val ports = summon[CastingPorts[TX]]
    val token  = TokenId.derive(secret)
    val castId = CastId.derive(secret)
    summon[TransactionManager[TX]].atomically[CastError | InfraFailure, Signed[Party.Issuer, BallotToken]]("re-vote") {
      for
        definition <- ports.votings.requireOpen(voting)
        _          <- Either.cond(ports.spent.contains(token), (), CastError.NoCastForSecret)
        cast        = ports.ledger.byCast(castId)
        _          <- Either.cond(cast.nonEmpty, (), CastError.NoCastForSecret)
        _          <- Either.cond(cast.forall(_.value.voting == voting), (), CastError.WrongVoting(voting))
        _          <- Either.cond(!ports.cancelled.contains(castId), (), CastError.CastAlreadyCancelled)
        _          <- Either.cond(!ports.spent.contains(next), (), CastError.TokenAlreadySpent)
        _           = ports.cancelled.add(castId)
        options     = cast.map(e => e.value.subject -> e.value.option).toMap
        _           = ports.ledger.append(CastOperations.chain(ports.ledger.head, definition, options, Delta.Minus, None, None))
        ballot     <- ports.revoteBallots.issue(voting, next)
      yield ballot
    }

  def ledger[TX <: Tx](using tm: TransactionManager[TX], ports: CastingPorts[TX]): Either[InfraFailure, List[LedgerEntry]] =
    tm.atomically[Nothing, List[LedgerEntry]]("read ledger")(Right(ports.ledger.entries))
