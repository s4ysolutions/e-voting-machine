package s4y.evoting.casting.domain.vo

import s4y.evoting.ballots.domain.vo.BallotSecret
import s4y.evoting.core.crypto.*
import s4y.evoting.voting.domain.vo.{OptionLabel, SubjectKey}

/** `cast_id = H("cast" ‖ s)`: the voter's receipt. Derived, so the derivation is its only constructor. */
opaque type CastId = Digest
object CastId:
  def derive(secret: BallotSecret)(using crypto: Crypto): CastId =
    crypto.sha256(Bytes.utf8("cast") ++ secret.bytes)
  extension (c: CastId)
    def hex: String  = Digest.hex(c)
    /** Display receipt for D7, e.g. `c-71ab`. */
    def name: String = "c-" + Digest.hex(c).take(4)
  given CanEqual[CastId, CastId] = CanEqual.derived

/** One option per subject, checked against the voting definition. */
opaque type Choices = Map[SubjectKey, OptionLabel]
object Choices:
  private[domain] def unsafe(map: Map[SubjectKey, OptionLabel]): Choices = map
  extension (c: Choices)
    def toMap: Map[SubjectKey, OptionLabel] = c
  given CanEqual[Choices, Choices] = CanEqual.derived

