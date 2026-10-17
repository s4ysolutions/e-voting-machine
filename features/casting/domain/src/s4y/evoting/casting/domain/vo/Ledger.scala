package s4y.evoting.casting.domain.vo

import s4y.evoting.ballots.domain.vo.TokenId
import s4y.evoting.core.crypto.*
import s4y.evoting.voting.domain.vo.{OptionLabel, SubjectKey, VotingKey}

/** +1 when a cast is recorded, -1 when a cast is cancelled. */
enum Delta derives CanEqual:
  case Plus, Minus
  def value: Int = this match
    case Plus  => 1
    case Minus => -1

/** What the operator signs for one ledger entry (design doc 3, LEDGER_ENTRY). A cancellation
  * carries no cast ID and no token, so nothing public links it to the cast it cancels. */
final case class EntryBody(
    seq: Long,
    voting: VotingKey,
    subject: SubjectKey,
    option: OptionLabel,
    delta: Delta,
    castId: Option[CastId],
    token: Option[TokenId],
    prevHash: Digest
) derives CanEqual

object EntryBody:
  given Canonical[EntryBody] = e =>
    val sign    = if e.delta == Delta.Plus then "+1" else "-1"
    val receipt = e.castId.map(c => s" receipt=${c.name}").getOrElse("")
    val token   = e.token.map(t => s" token=${t.name}").getOrElse("")
    val ids     = e.castId.map(c => s" cast=${c.hex}").getOrElse("") + e.token.map(t => s" tokenid=${t.hex}").getOrElse("")
    CanonicalText(
      s"$sign ${e.subject.value}=${e.option.value} voting=${e.voting.value}$receipt$token seq=${e.seq} prev=${e.prevHash.hex}$ids"
    )

type LedgerEntry = Signed[s4y.evoting.core.crypto.Party.Operator, EntryBody]

/** What the voter keeps: the receipt and the entries it can find in the public ledger. */
final case class Receipt(castId: CastId, entries: List[LedgerEntry])

enum CastError derives CanEqual:
  case VotingNotOpen(voting: VotingKey)
  case InvalidIssuerSignature
  case WrongVoting(expected: VotingKey)
  case TokenAlreadySpent
  case CastIdAlreadyUsed
  case InvalidChoices(problems: List[String])
  case NoCastForSecret
  case CastAlreadyCancelled
