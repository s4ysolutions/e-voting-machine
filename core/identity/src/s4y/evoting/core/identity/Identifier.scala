package s4y.evoting.core.identity

/** An ID assigned by a store (a voter pseudonym, a ledger entry ID). The domain may carry and
  * compare one but cannot create one: construction lives in `core.identity.mint`, which domain
  * modules do not depend on (skill: domain-identity-types). */
final class Identifier[A] private (private[identity] val value: String):
  override def equals(that: Any): Boolean = that match
    case other: Identifier[?] => value == other.value
    case _                    => false
  override def hashCode: Int    = value.hashCode
  override def toString: String = s"Identifier($value)"

object Identifier:
  private[identity] def unsafe[A](value: String): Identifier[A] = new Identifier(value)
  given [A]: CanEqual[Identifier[A], Identifier[A]] = CanEqual.derived
