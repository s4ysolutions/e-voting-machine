package s4y.evoting.ballots.domain.vo

import s4y.evoting.core.crypto.*
import s4y.evoting.voting.domain.vo.VotingKey

/** The voter's secret `s`. Generated on the voter's device and never published. */
opaque type BallotSecret = IArray[Byte]
object BallotSecret:
  def from(bytes: IArray[Byte]): Either[BallotError, BallotSecret] =
    Either.cond(bytes.length == 32, bytes, BallotError.InvalidSecret)
  extension (s: BallotSecret) def bytes: IArray[Byte] = s

/** `token_id = H("token" ‖ s)`: derived, so its only constructor is the derivation. */
opaque type TokenId = Digest
object TokenId:
  def derive(secret: BallotSecret)(using crypto: Crypto): TokenId =
    crypto.sha256(Bytes.utf8("token") ++ secret)   // opaque types are transparent in their own file
  extension (t: TokenId)
    def hex: String  = Digest.hex(t)
    /** Display name for D7, e.g. `amber-falcon-42`. */
    def name: String = ReadableName.of(Digest.bytes(t))
  given CanEqual[TokenId, TokenId] = CanEqual.derived

enum BallotKind derives CanEqual:
  case First, Revote

/** What the issuer signs. Carries no time (design doc D7, threat T12). */
final case class BallotToken(voting: VotingKey, id: TokenId, kind: BallotKind) derives CanEqual
object BallotToken:
  given Canonical[BallotToken] = t =>
    val kind = t.kind match
      case BallotKind.First  => "first"
      case BallotKind.Revote => "revote"
    CanonicalText(s"ballot=${t.id.name} voting=${t.voting.value} kind=$kind id=${t.id.hex}")

/** Public issuance counts (invariants I4, I5). */
final case class IssuanceCounts(firstBallots: Int, revoteBallots: Int) derives CanEqual

enum BallotError derives CanEqual:
  case InvalidSecret
  case VotingNotOpen(voting: VotingKey)
  case NotEligible(voting: VotingKey)
  case AlreadyIssued(voting: VotingKey)
  case TokenAlreadySpent
