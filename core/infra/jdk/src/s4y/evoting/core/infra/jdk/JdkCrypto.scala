package s4y.evoting.core.infra.jdk

import java.security.{KeyFactory, KeyPairGenerator, MessageDigest, PrivateKey, SecureRandom}
import java.security.spec.{NamedParameterSpec, X509EncodedKeySpec}
import s4y.evoting.core.crypto.*

/** SHA-256 through the JDK. For JVM tests and a future backend. */
object JdkCrypto extends Crypto:
  def sha256(bytes: IArray[Byte]): Digest =
    Digest(IArray.unsafeFromArray(MessageDigest.getInstance("SHA-256").digest(Bytes.copy(bytes))))

/** Ed25519 keys through the JDK (Java 15+). Same seed, same keys and signatures as `NobleKeys`. */
object JdkKeys:
  // DER prefix of an X.509 SubjectPublicKeyInfo for Ed25519; the raw 32-byte key follows it.
  private val X509Prefix = Bytes.fromHex("302a300506032b6570032100").get

  def fromSeed[P <: Party](seed: IArray[Byte]): Keys[P] =
    require(seed.length == 32, "an Ed25519 seed is 32 bytes")
    val generator = KeyPairGenerator.getInstance("Ed25519")
    generator.initialize(NamedParameterSpec.ED25519, FixedRandom(seed))
    val pair     = generator.generateKeyPair()
    val encoded  = IArray.unsafeFromArray(pair.getPublic.getEncoded)
    val rawPublic = PublicKey(encoded.drop(encoded.length - 32))
    JdkKeyPair[P](pair.getPrivate, rawPublic)

  private final class JdkKeyPair[P <: Party](privateKey: PrivateKey, val publicKey: PublicKey) extends Keys[P]:
    val signer: Signer[P] = new SignerBase[P]:
      protected def signBytes(message: IArray[Byte]): Signature =
        val s = java.security.Signature.getInstance("Ed25519")
        s.initSign(privateKey)
        s.update(Bytes.copy(message))
        Signature(IArray.unsafeFromArray(s.sign()))

    val verifier: Verifier[P] = JdkVerifier[P](publicKey)

  /** Verifies with a raw public key, as published by the issuer or the operator. */
  final class JdkVerifier[P <: Party](val publicKey: PublicKey) extends Verifier[P]:
    private val key = KeyFactory.getInstance("Ed25519")
      .generatePublic(X509EncodedKeySpec(Bytes.copy(X509Prefix ++ publicKey.bytes)))

    def verifyText(text: String, signature: Signature): Boolean =
      val s = java.security.Signature.getInstance("Ed25519")
      s.initVerify(key)
      s.update(Bytes.copy(Bytes.utf8(text)))
      try s.verify(Bytes.copy(signature.bytes))
      catch case _: java.security.SignatureException => false

  /** Hands the key generator exactly the seed, so keys are reproducible. */
  private final class FixedRandom(seed: IArray[Byte]) extends SecureRandom:
    override def nextBytes(bytes: Array[Byte]): Unit =
      require(bytes.length == seed.length, s"expected a ${seed.length}-byte request")
      seed.copyToArray(bytes)
