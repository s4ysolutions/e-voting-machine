package s4y.evoting.core.domain

import org.scalatest.funsuite.AnyFunSuite
import Validated.*

class ValidatedSpec extends AnyFunSuite:
  private def positive(n: Int): Validated[String, Int] =
    if n > 0 then valid(n) else invalid(s"$n is not positive")

  test("zip keeps every error, in order"):
    val result = positive(-1).zip(positive(2)).zip(positive(-3))
    assert(result.toEither == Left(::("-1 is not positive", List("-3 is not positive"))))

  test("traverse collects values when all are valid"):
    assert(traverse(List(1, 2, 3))(positive).toEither == Right(List(1, 2, 3)))

  test("traverse collects every error"):
    assert(traverse(List(1, -2, -3))(positive).toEither.left.map(_.size) == Left(2))

  test("toValidated turns Left into a single error"):
    assert(Left("bad").toValidated.toEither == Left(::("bad", Nil)))
