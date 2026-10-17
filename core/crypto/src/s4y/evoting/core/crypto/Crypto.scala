package s4y.evoting.core.crypto

/** Pure hashing capability. Deterministic, so domain Operations may use it (skill:
  * domain-operations-and-workflows). Implemented by `NobleCrypto` (browser) and `JdkCrypto` (JVM). */
trait Crypto:
  def sha256(bytes: IArray[Byte]): Digest
