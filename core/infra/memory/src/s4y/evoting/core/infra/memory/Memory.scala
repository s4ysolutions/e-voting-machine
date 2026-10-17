package s4y.evoting.core.infra.memory

import scala.annotation.unused
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.core.tx.{TransactionManager, Tx}

final class MemoryTx private[memory] () extends Tx:
  private[memory] val undo = ListBuffer.empty[() => Unit]

final class MemoryTransactionManager extends TransactionManager[MemoryTx]:
  def atomically[E, A](label: String)(body: MemoryTx ?=> Either[E, A]): Either[E | InfraFailure, A] =
    val tx = MemoryTx()
    val result: Either[E | InfraFailure, A] =
      try body(using tx)
      catch case NonFatal(t) => Left(InfraFailure(s"$label: ${t.getMessage}"))
    if result.isLeft then tx.undo.reverseIterator.foreach(_())
    result

/** One piece of in-memory state. Every write registers its undo with the current transaction. */
final class MemoryCell[S](initial: S):
  private var state: S = initial

  def get(using @unused tx: MemoryTx): S = state

  def update(f: S => S)(using tx: MemoryTx): Unit =
    val before = state
    tx.undo += (() => state = before)
    state = f(state)
