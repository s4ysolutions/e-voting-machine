package s4y.evoting.voters.domain.vo

import s4y.evoting.core.identity.Identifier
import s4y.evoting.voting.domain.vo.VotingKey

/** Who a voter is, as asserted by the identity provider (an email in the demo). Private to the operator. */
opaque type VoterIdentity = String
object VoterIdentity:
  def from(raw: String): Either[VotersError, VoterIdentity] =
    // ASCII-only lowercasing: the same on every platform and locale (java.util.Locale is not
    // available in Scala.js, and a Turkish default locale would turn "I" into a dotless "ı").
    val trimmed = raw.trim.map(c => if c >= 'A' && c <= 'Z' then (c + ('a' - 'A')).toChar else c)
    Either.cond(trimmed.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"), trimmed, VotersError.InvalidIdentity(raw))
  extension (v: VoterIdentity) def value: String = v
  given CanEqual[VoterIdentity, VoterIdentity] = CanEqual.derived

/** The concept. Both projections below share one ID type, `Identifier[Voter]` (the pseudonym). */
sealed trait Voter

/** The operator's private record. */
final case class RegisteredVoter(voting: VotingKey, identity: VoterIdentity, ballotIssued: Boolean)
    extends Voter derives CanEqual

/** What the public registry shows: a pseudonym and whether a ballot was issued. */
final case class PublicVoter(ballotIssued: Boolean) extends Voter derives CanEqual

enum VotersError derives CanEqual:
  case InvalidIdentity(raw: String)
  case AlreadyRegistered(voting: VotingKey, identity: VoterIdentity)
  case NotEligible(voting: VotingKey, identity: VoterIdentity)
  case BallotAlreadyIssued(voting: VotingKey, pseudonym: Identifier[Voter])
