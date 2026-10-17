package s4y.evoting.core.crypto

import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.immutable.ArraySeq

/** Immutable byte helpers shared by every crypto value. */
object Bytes:
  def utf8(s: String): IArray[Byte] = IArray.unsafeFromArray(s.getBytes(UTF_8))

  /** A mutable copy, for APIs that take `Array[Byte]`. */
  def copy(bytes: IArray[Byte]): Array[Byte] = IArray.genericWrapArray(bytes).toArray

  // Hashes, signatures and keys are backed by ArraySeq, which compares by value: two equal digests
  // are `==` and work as Set and Map keys. An IArray would compare by reference.
  private[crypto] def seq(bytes: IArray[Byte]): ArraySeq[Byte]   = ArraySeq.unsafeWrapArray(copy(bytes))
  private[crypto] def iarray(seq: ArraySeq[Byte]): IArray[Byte]  = IArray.unsafeFromArray(seq.toArray)

  def hex(bytes: IArray[Byte]): String = bytes.map(b => f"${b & 0xff}%02x").mkString

  def fromHex(hex: String): Option[IArray[Byte]] =
    if hex.length % 2 != 0 || !hex.forall(c => Character.digit(c, 16) >= 0) then None
    else Some(IArray.from(hex.grouped(2).map(h => Integer.parseInt(h, 16).toByte)))

/** SHA-256 output. */
opaque type Digest = ArraySeq[Byte]
object Digest:
  def apply(bytes: IArray[Byte]): Digest = Bytes.seq(bytes)
  extension (d: Digest)
    def bytes: IArray[Byte] = Bytes.iarray(d)
    def hex: String         = Bytes.hex(Bytes.iarray(d))
  given CanEqual[Digest, Digest] = CanEqual.derived

/** A 64-byte Ed25519 signature. */
opaque type Signature = ArraySeq[Byte]
object Signature:
  def apply(bytes: IArray[Byte]): Signature = Bytes.seq(bytes)
  extension (s: Signature)
    def bytes: IArray[Byte] = Bytes.iarray(s)
    def hex: String         = Bytes.hex(Bytes.iarray(s))
    /** The first 6 hex characters, shown as the D7 tag. */
    def tag: String         = hex.take(6)
  given CanEqual[Signature, Signature] = CanEqual.derived

/** A raw 32-byte Ed25519 public key. */
opaque type PublicKey = ArraySeq[Byte]
object PublicKey:
  def apply(bytes: IArray[Byte]): PublicKey = Bytes.seq(bytes)
  extension (k: PublicKey)
    def bytes: IArray[Byte] = Bytes.iarray(k)
    def hex: String         = Bytes.hex(Bytes.iarray(k))
  given CanEqual[PublicKey, PublicKey] = CanEqual.derived
