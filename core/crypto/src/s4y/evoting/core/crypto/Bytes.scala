package s4y.evoting.core.crypto

import java.nio.charset.StandardCharsets.UTF_8

/** Immutable byte helpers shared by every crypto value. */
object Bytes:
  def utf8(s: String): IArray[Byte] = IArray.unsafeFromArray(s.getBytes(UTF_8))

  /** A mutable copy, for APIs that take `Array[Byte]`. */
  def copy(bytes: IArray[Byte]): Array[Byte] = IArray.genericWrapArray(bytes).toArray

  def hex(bytes: IArray[Byte]): String = bytes.map(b => f"${b & 0xff}%02x").mkString

  def fromHex(hex: String): Option[IArray[Byte]] =
    if hex.length % 2 != 0 || !hex.forall(c => Character.digit(c, 16) >= 0) then None
    else Some(IArray.from(hex.grouped(2).map(h => Integer.parseInt(h, 16).toByte)))

/** SHA-256 output. */
opaque type Digest = IArray[Byte]
object Digest:
  def apply(bytes: IArray[Byte]): Digest = bytes
  extension (d: Digest)
    def bytes: IArray[Byte] = d
    def hex: String         = Bytes.hex(d)
  given CanEqual[Digest, Digest] = CanEqual.derived

/** A 64-byte Ed25519 signature. */
opaque type Signature = IArray[Byte]
object Signature:
  def apply(bytes: IArray[Byte]): Signature = bytes
  extension (s: Signature)
    def bytes: IArray[Byte] = s
    def hex: String         = Bytes.hex(s)
    /** The first 6 hex characters, shown as the D7 tag. */
    def tag: String         = hex.take(6)
  given CanEqual[Signature, Signature] = CanEqual.derived

/** A raw 32-byte Ed25519 public key. */
opaque type PublicKey = IArray[Byte]
object PublicKey:
  def apply(bytes: IArray[Byte]): PublicKey = bytes
  extension (k: PublicKey)
    def bytes: IArray[Byte] = k
    def hex: String         = Bytes.hex(k)
  given CanEqual[PublicKey, PublicKey] = CanEqual.derived
