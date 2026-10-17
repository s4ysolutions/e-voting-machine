package s4y.evoting.core.infra.noble

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.*
import s4y.evoting.core.crypto.*

/** Facades for the parts of @noble/ed25519 and @noble/hashes this project uses. */
private object Facade:
  @js.native @JSImport("@noble/ed25519", JSImport.Namespace)
  object Ed25519 extends js.Object:
    val hashes: js.Dynamic                                                 = js.native
    def getPublicKey(secretKey: Uint8Array): Uint8Array                    = js.native
    def sign(message: Uint8Array, secretKey: Uint8Array): Uint8Array       = js.native
    def verify(signature: Uint8Array, message: Uint8Array, publicKey: Uint8Array): Boolean = js.native

  @js.native @JSImport("@noble/hashes/sha2.js", JSImport.Namespace)
  object Sha2 extends js.Object:
    def sha256(message: Uint8Array): Uint8Array        = js.native
    val sha512: js.Function1[Uint8Array, Uint8Array]   = js.native

  /** noble/ed25519 needs a synchronous SHA-512 before its synchronous API can be used. */
  lazy val ready: Unit = Ed25519.hashes.updateDynamic("sha512")(Sha2.sha512)

  def toJs(bytes: IArray[Byte]): Uint8Array =
    val int8 = Bytes.copy(bytes).toTypedArray
    new Uint8Array(int8.buffer, int8.byteOffset, int8.length)

  def fromJs(bytes: Uint8Array): IArray[Byte] =
    IArray.unsafeFromArray(new Int8Array(bytes.buffer, bytes.byteOffset, bytes.length).toArray)

import Facade.*

/** SHA-256 through @noble/hashes. */
object NobleCrypto extends Crypto:
  def sha256(bytes: IArray[Byte]): Digest = Digest(fromJs(Sha2.sha256(toJs(bytes))))

/** Ed25519 keys through @noble/ed25519. Same seed, same keys and signatures as `JdkKeys`. */
object NobleKeys:
  def fromSeed[P <: Party](seed: IArray[Byte]): Keys[P] =
    require(seed.length == 32, "an Ed25519 seed is 32 bytes")
    ready
    val secret    = toJs(seed)
    val publicKey = PublicKey(fromJs(Ed25519.getPublicKey(secret)))
    new Keys[P]:
      val signer: Signer[P] = new SignerBase[P]:
        protected def signBytes(message: IArray[Byte]): Signature =
          Signature(fromJs(Ed25519.sign(toJs(message), secret)))
      val verifier: Verifier[P] = NobleVerifier[P](publicKey)

/** Verifies with a raw public key. */
final class NobleVerifier[P <: Party](val publicKey: PublicKey) extends Verifier[P]:
  def verifyText(text: String, signature: Signature): Boolean =
    ready
    try Ed25519.verify(toJs(signature.bytes), toJs(Bytes.utf8(text)), toJs(publicKey.bytes))
    catch case _: js.JavaScriptException => false
