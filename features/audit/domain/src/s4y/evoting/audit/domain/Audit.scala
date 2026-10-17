package s4y.evoting.audit.domain

import s4y.evoting.ballots.domain.vo.{BallotToken, IssuanceCounts}
import s4y.evoting.casting.domain.operations.CastOperations
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.core.crypto.*
import s4y.evoting.voting.domain.vo.VotingDefinition

/** Everything an outside auditor can read (design doc F6). */
final case class PublicRecord(
    definition: VotingDefinition,
    ledger: List[LedgerEntry],
    spentBallots: List[Signed[Party.Issuer, BallotToken]],
    counts: IssuanceCounts,
    registrySize: Int,
    ballotsIssuedFlags: Int
)

enum Invariant derives CanEqual:
  case I1, I2, I3, I4, I5, I6, I7

/** The outcome of one check. `Failed` names the first problem found, `NotChecked` says why it was skipped. */
enum Check derives CanEqual:
  case Passed
  case Failed(problem: String)
  case NotChecked(reason: String)

final case class AuditReport(checks: List[(Invariant, Check)], tally: Map[(String, String), Int]):
  def allPassed: Boolean = checks.forall((_, c) => c == Check.Passed || c.isInstanceOf[Check.NotChecked])

/** The auditor: pure functions anyone can run over the public record (design doc 3, "Public invariants"). */
object AuditOperations:

  def audit(record: PublicRecord)(using operator: Verifier[Party.Operator], issuer: Verifier[Party.Issuer], crypto: Crypto): AuditReport =
    AuditReport(
      List(
        Invariant.I1 -> i1Chain(record.ledger),
        Invariant.I2 -> i2NeverNegative(record.ledger),
        Invariant.I3 -> i3CastsBacked(record),
        Invariant.I4 -> i4FirstBallots(record),
        Invariant.I5 -> i5Revotes(record),
        Invariant.I6 -> i6NetCasts(record),
        Invariant.I7 -> Check.NotChecked("entries carry no timestamps yet")
      ),
      tally(record.ledger)
    )

  /** Sum of deltas per (subject, option). */
  def tally(ledger: List[LedgerEntry]): Map[(String, String), Int] =
    ledger.groupMapReduce(e => (e.value.subject.value, e.value.option.value))(_.value.delta.value)(_ + _)

  /** I1: every entry is signed by the operator and chained to the one before it. */
  def i1Chain(ledger: List[LedgerEntry])(using operator: Verifier[Party.Operator], crypto: Crypto): Check =
    val problems = ledger.iterator.zipWithIndex.flatMap { (entry, i) =>
      val expectedPrev = if i == 0 then CastOperations.GenesisHash else CastOperations.entryHash(ledger(i - 1))
      if !operator.verify(entry) then Some(s"entry seq ${entry.value.seq}: signature does not match")
      else if entry.value.seq != i + 1 then Some(s"entry seq ${entry.value.seq}: expected seq ${i + 1}")
      else if entry.value.prevHash != expectedPrev then Some(s"entry seq ${entry.value.seq}: not chained to the previous entry")
      else None
    }
    problems.nextOption().fold(Check.Passed)(Check.Failed(_))

  /** I2: no (subject, option) total is ever negative, at any point of the ledger. */
  def i2NeverNegative(ledger: List[LedgerEntry]): Check =
    val firstNegative = ledger.iterator
      .scanLeft((Map.empty[(String, String), Int], Option.empty[LedgerEntry])) { case ((sums, _), e) =>
        val key = (e.value.subject.value, e.value.option.value)
        (sums.updated(key, sums.getOrElse(key, 0) + e.value.delta.value), Some(e))
      }
      .collectFirst { case (sums, Some(e)) if sums.values.exists(_ < 0) => e }
    firstNegative.fold(Check.Passed)(e => Check.Failed(s"entry seq ${e.value.seq}: a total went below zero"))

  /** I3: each receipt has one +1 per subject from one token; that token was issued and spent once. */
  def i3CastsBacked(record: PublicRecord)(using issuer: Verifier[Party.Issuer]): Check =
    val subjects = record.definition.subjects.map(_.key.value).toSet
    val casts    = record.ledger.filter(_.value.delta == Delta.Plus).groupBy(_.value.castId)
    val spent    = record.spentBallots.map(b => b.value.id -> b).toMap
    val problems =
      casts.toList.flatMap { (castId, entries) =>
        val name   = castId.fold("?")(_.name)
        val tokens = entries.flatMap(_.value.token).distinct
        if castId.isEmpty then List("a +1 entry has no receipt")
        else if entries.map(_.value.subject.value).sorted != subjects.toList.sorted then List(s"receipt $name does not cover every subject once")
        else if tokens.sizeIs != 1 then List(s"receipt $name is not backed by exactly one token")
        else if !spent.get(tokens.head).exists(issuer.verify) then List(s"receipt $name: token was not issued by the issuer")
        else Nil
      } ++ {
        val tokensUsed = casts.values.flatMap(_.flatMap(_.value.token).distinct).toList
        if tokensUsed.distinct.sizeIs != tokensUsed.size then List("a token backs more than one receipt") else Nil
      }
    problems.headOption.fold(Check.Passed)(Check.Failed(_))

  /** I4: first ballots issued = voters flagged, and no more than the registry holds. */
  def i4FirstBallots(record: PublicRecord): Check =
    if record.counts.firstBallots != record.ballotsIssuedFlags then
      Check.Failed(s"${record.counts.firstBallots} first ballots issued, but ${record.ballotsIssuedFlags} voters flagged")
    else if record.ballotsIssuedFlags > record.registrySize then Check.Failed("more ballots than registered voters")
    else Check.Passed

  /** I5: re-vote ballots = cancellations, and every subject was cancelled the same number of times. */
  def i5Revotes(record: PublicRecord): Check =
    val perSubject = record.ledger.filter(_.value.delta == Delta.Minus).groupMapReduce(_.value.subject.value)(_ => 1)(_ + _)
    val counts     = record.definition.subjects.map(s => perSubject.getOrElse(s.key.value, 0)).distinct
    if counts.sizeIs > 1 then Check.Failed("subjects were cancelled a different number of times")
    else if counts.headOption.getOrElse(0) != record.counts.revoteBallots then
      Check.Failed(s"${record.counts.revoteBallots} re-vote ballots issued, but ${counts.headOption.getOrElse(0)} cancellations")
    else Check.Passed

  /** I6: net casts (casts − cancellations) never exceed the first ballots issued. */
  def i6NetCasts(record: PublicRecord): Check =
    val casts         = record.ledger.flatMap(_.value.castId).distinct.size
    val cancellations = record.counts.revoteBallots
    if casts - cancellations > record.counts.firstBallots then
      Check.Failed(s"$casts casts − $cancellations cancellations exceed ${record.counts.firstBallots} first ballots")
    else Check.Passed

