package s4y.evoting.core.infra.noble

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.core.crypto.*

/** Same seed and message as JdkCryptoSpec: the browser and the JVM must agree byte for byte. */
class NobleCryptoSpec extends AnyFunSuite:
  private val seed = IArray.fill[Byte](32)(7)
  private final case class Ballot(name: String)
  private given Canonical[Ballot] = b => CanonicalText(s"ballot=${b.name}")

  test("SHA-256 matches the reference"):
    assert(NobleCrypto.sha256(Bytes.utf8("ballot=x")).hex.startsWith("52834b817261"))

  test("keys and signatures match the reference"):
    val keys   = NobleKeys.fromSeed[Party.Issuer](seed)
    val signed = keys.signer.sign(Ballot("x"))
    assert(keys.verifier.publicKey.hex == "ea4a6c63e29c520abef5507b132ec5f9954776aebebe7b92421eea691446d22c")
    assert(signed.signature.hex.startsWith("c7bfa189043f"))

  test("a signature verifies, and any edit to the text breaks it"):
    val keys   = NobleKeys.fromSeed[Party.Issuer](seed)
    val signed = keys.signer.sign(Ballot("x"))
    assert(keys.verifier.verify(signed))
    assert(!keys.verifier.verifyText("ballot=y", signed.signature))

  test("a malformed signature is rejected, not thrown"):
    val keys = NobleKeys.fromSeed[Party.Issuer](seed)
    assert(!keys.verifier.verifyText("ballot=x", Signature(IArray.fill[Byte](3)(1))))
