package s4y.evoting.core.identity.mint

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.core.identity.Identifier

class MintSpec extends AnyFunSuite:
  private sealed trait Voter

  test("minted IDs round-trip and compare by value"):
    val a: Identifier[Voter] = "p-1".identifier[Voter]
    assert(a.raw == "p-1")
    assert(a == "p-1".identifier[Voter])
    assert(a != "p-2".identifier[Voter])
    assert(Set(a, "p-1".identifier[Voter]).size == 1)
