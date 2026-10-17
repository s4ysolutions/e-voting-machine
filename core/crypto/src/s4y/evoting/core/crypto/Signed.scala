package s4y.evoting.core.crypto

/** Who signed something. Used as a phantom type: `Signed[Party.Issuer, T]`. */
sealed trait Party
object Party:
  sealed trait Issuer   extends Party
  sealed trait Operator extends Party
  sealed trait Owner    extends Party

/** The exact text a signature covers (decision D7). */
opaque type CanonicalText = String
object CanonicalText:
  def apply(text: String): CanonicalText = text
  extension (t: CanonicalText) def value: String = t
  given CanEqual[CanonicalText, CanonicalText] = CanEqual.derived

/** How a value is written as signed text. Instances live in the value's companion. */
trait Canonical[T]:
  def text(value: T): CanonicalText

/** A value with the signature of party `P` over its canonical text. Only a `SignerBase` creates one. */
final case class Signed[P <: Party, +T] private[crypto] (value: T, text: CanonicalText, signature: Signature)

trait Signer[P <: Party]:
  def sign[T](value: T)(using Canonical[T]): Signed[P, T]

trait Verifier[P <: Party]:
  def publicKey: PublicKey
  def verifyText(text: String, signature: Signature): Boolean
  final def verify(signed: Signed[P, ?]): Boolean = verifyText(signed.text.value, signed.signature)

/** Base for signer adapters: they supply the raw signature; this class builds the `Signed`. */
abstract class SignerBase[P <: Party] extends Signer[P]:
  protected def signBytes(message: IArray[Byte]): Signature

  final def sign[T](value: T)(using canonical: Canonical[T]): Signed[P, T] =
    val text = canonical.text(value)
    Signed(value, text, signBytes(Bytes.utf8(text.value)))

/** A signing key pair for party `P`, as created by an adapter (`NobleKeys`, `JdkKeys`). */
trait Keys[P <: Party]:
  def signer: Signer[P]
  def verifier: Verifier[P]
