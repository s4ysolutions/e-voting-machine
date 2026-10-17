package s4y.evoting.core.infra.jdk

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.core.crypto.*

/** Reference values were produced with @noble/ed25519 3.2.0 and @noble/hashes 2.4.0 for the same
  * seed and message; NobleCryptoSpec checks the browser side against the same values. */
class JdkCryptoSpec extends AnyFunSuite:
  private val seed = IArray.fill[Byte](32)(7)
  private final case class Ballot(name: String)
  private given Canonical[Ballot] = b => CanonicalText(s"ballot=${b.name}")

  test("SHA-256 matches the reference"):
    assert(JdkCrypto.sha256(Bytes.utf8("ballot=x")).hex.startsWith("52834b817261"))

  test("keys and signatures match the noble reference"):
    val keys   = JdkKeys.fromSeed[Party.Issuer](seed)
    val signed = keys.signer.sign(Ballot("x"))
    assert(keys.verifier.publicKey.hex == "ea4a6c63e29c520abef5507b132ec5f9954776aebebe7b92421eea691446d22c")
    assert(signed.text.value == "ballot=x")
    assert(signed.signature.tag == "c7bfa1")
    assert(signed.signature.hex.startsWith("c7bfa189043f"))

  test("a signature verifies, and any edit to the text breaks it"):
    val keys   = JdkKeys.fromSeed[Party.Issuer](seed)
    val signed = keys.signer.sign(Ballot("x"))
    assert(keys.verifier.verify(signed))
    assert(!keys.verifier.verifyText("ballot=y", signed.signature))
    assert(keys.verifier.verifyText("ballot=x", signed.signature))

  test("another party's key does not verify"):
    val issuer   = JdkKeys.fromSeed[Party.Issuer](seed)
    val operator = JdkKeys.fromSeed[Party.Operator](IArray.fill[Byte](32)(9))
    val signed   = issuer.signer.sign(Ballot("x"))
    assert(!operator.verifier.verifyText(signed.text.value, signed.signature))
