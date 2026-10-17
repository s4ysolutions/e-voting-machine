package s4y.evoting.casting.infra.memory

import s4y.evoting.ballots.domain.vo.{BallotToken, TokenId}
import s4y.evoting.casting.domain.operations.CastOperations
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.casting.domain.workflows.ports.*
import s4y.evoting.core.crypto.{Crypto, Digest, Party, Signed}
import s4y.evoting.core.infra.memory.{MemoryCell, MemoryTx}

final class MemoryLedger(using crypto: Crypto) extends LedgerRepository[MemoryTx]:
  private val cell = MemoryCell(Vector.empty[LedgerEntry])

  def head(using MemoryTx): (Long, Digest) =
    cell.get.lastOption.fold((0L, CastOperations.GenesisHash))(e => (e.value.seq, CastOperations.entryHash(e)))
  def append(entries: List[LedgerEntry])(using MemoryTx): Unit = cell.update(_ ++ entries)
  def entries(using MemoryTx): List[LedgerEntry]               = cell.get.toList
  def byCast(castId: CastId)(using MemoryTx): List[LedgerEntry] =
    cell.get.filter(_.value.castId.contains(castId)).toList

final class MemorySpentTokens extends SpentTokens[MemoryTx]:
  private val cell = MemoryCell(Map.empty[TokenId, Signed[Party.Issuer, BallotToken]])
  def contains(token: TokenId)(using MemoryTx): Boolean = cell.get.contains(token)
  def add(token: TokenId, ballot: Signed[Party.Issuer, BallotToken])(using MemoryTx): Unit =
    cell.update(_.updated(token, ballot))

final class MemoryCancelledCasts extends CancelledCasts[MemoryTx]:
  private val cell = MemoryCell(Set.empty[CastId])
  def contains(castId: CastId)(using MemoryTx): Boolean = cell.get.contains(castId)
  def add(castId: CastId)(using MemoryTx): Unit         = cell.update(_ + castId)
