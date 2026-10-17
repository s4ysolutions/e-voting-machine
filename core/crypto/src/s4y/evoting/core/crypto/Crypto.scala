package s4y.evoting.core.crypto

/** Pure hashing capability. Deterministic, so domain Operations may use it (skill:
  * domain-operations-and-workflows). Implemented by `NobleCrypto` (browser) and `JdkCrypto` (JVM). */
trait Crypto:
  def sha256(bytes: IArray[Byte]): Digest

/** Source of secret random bytes, e.g. for a ballot secret on the voter's device. Not
  * deterministic, so only Workflows and the ui may use it, never an Operation. Implemented by
  * `BrowserRandomness` (browser) and `JdkRandomness` (JVM). */
trait Randomness:
  def bytes(count: Int): IArray[Byte]
