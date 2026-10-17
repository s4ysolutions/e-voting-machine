package s4y.evoting.core.infra.memory

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.core.domain.InfraFailure

class MemorySpec extends AnyFunSuite:
  private val tm = MemoryTransactionManager()

  private def read(cell: MemoryCell[Int]): Int =
    tm.atomically[Nothing, Int]("read")(Right(cell.get)).getOrElse(-1)

  test("a Right commits the writes"):
    val cell = MemoryCell(0)
    assert(tm.atomically[String, Unit]("inc") { cell.update(_ + 1); Right(()) } == Right(()))
    assert(read(cell) == 1)

  test("a Left rolls back every write in the block"):
    val cell = MemoryCell(0)
    val result = tm.atomically[String, Unit]("inc twice then fail") {
      cell.update(_ + 1)
      cell.update(_ + 1)
      Left("nope")
    }
    assert(result == Left("nope"))
    assert(read(cell) == 0)

  test("an exception becomes an InfraFailure and rolls back"):
    val cell = MemoryCell(0)
    val result = tm.atomically[String, Unit]("boom") {
      cell.update(_ + 1)
      throw IllegalStateException("disk on fire")
    }
    assert(result == Left(InfraFailure("boom: disk on fire")))
    assert(read(cell) == 0)
