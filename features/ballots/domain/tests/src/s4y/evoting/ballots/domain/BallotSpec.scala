package s4y.evoting.ballots.domain

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.ballots.domain.vo.*
import s4y.evoting.core.crypto.*
import s4y.evoting.core.infra.jdk.JdkCrypto
import s4y.evoting.voting.domain.vo.VotingKey

class BallotSpec extends AnyFunSuite:
  private given Crypto = JdkCrypto
  private val secret   = BallotSecret.from(IArray.fill[Byte](32)(1)).toOption.get

  test("a secret is exactly 32 bytes"):
    assert(BallotSecret.from(IArray.fill[Byte](31)(1)) == Left(BallotError.InvalidSecret))

  test("the token id is derived deterministically from the secret"):
    assert(TokenId.derive(secret) == TokenId.derive(secret))
    val other = BallotSecret.from(IArray.fill[Byte](32)(2)).toOption.get
    assert(TokenId.derive(secret) != TokenId.derive(other))

  test("the signed text names the ballot, the voting, the kind and the full id"):
    val id    = TokenId.derive(secret)
    val text  = summon[Canonical[BallotToken]].text(BallotToken(VotingKey.from("annual-2027").toOption.get, id, BallotKind.First))
    assert(text.value == s"ballot=${id.name} voting=annual-2027 kind=first id=${id.hex}")
