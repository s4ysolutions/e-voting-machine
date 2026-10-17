package s4y.evoting.casting.domain.operations

import s4y.evoting.ballots.domain.vo.BallotToken
import s4y.evoting.casting.domain.vo.*
import s4y.evoting.core.crypto.*
import s4y.evoting.voting.domain.vo.{OptionLabel, SubjectKey, VotingDefinition}

/** Pure casting rules. */
object CastOperations:

  def checkBallot(ballot: Signed[Party.Issuer, BallotToken])(using verifier: Verifier[Party.Issuer]): Either[CastError, BallotToken] =
    Either.cond(verifier.verify(ballot), ballot.value, CastError.InvalidIssuerSignature)

  /** Exactly one valid option for every subject of the voting, nothing else (F8 X4). */
  def checkChoices(definition: VotingDefinition, raw: Map[String, String]): Either[CastError, Choices] =
    val subjects = definition.subjects.map(s => s.key.value -> s).toMap
    val unknown  = raw.keySet.diff(subjects.keySet).toList.sorted.map(k => s"unknown subject $k")
    val missing  = subjects.keySet.diff(raw.keySet).toList.sorted.map(k => s"no option chosen for $k")
    val invalid = raw.toList.sorted.flatMap { (k, o) =>
      subjects.get(k).filterNot(_.options.exists(_.value == o)).map(_ => s"$o is not an option of $k")
    }
    val problems = unknown ++ missing ++ invalid
    if problems.nonEmpty then Left(CastError.InvalidChoices(problems))
    else
      Right(Choices.unsafe(definition.subjects.map { s =>
        s.key -> s.options.find(_.value == raw(s.key.value)).get
      }.toMap))

  /** Entry bodies for a run of +1 or -1 entries, chained from `head`, in subject order. */
  def chain(
      head: (Long, Digest),
      definition: VotingDefinition,
      options: Map[SubjectKey, OptionLabel],
      delta: Delta,
      castId: Option[CastId],
      token: Option[s4y.evoting.ballots.domain.vo.TokenId]
  )(using signer: Signer[Party.Operator], crypto: Crypto): List[LedgerEntry] =
    val (_, entries) = definition.subjects.foldLeft((head, List.empty[LedgerEntry])) { case (((seq, prev), acc), subject) =>
      val body  = EntryBody(seq + 1, definition.key, subject.key, options(subject.key), delta, castId, token, prev)
      val entry = signer.sign(body)
      ((seq + 1, entryHash(entry)), entry :: acc)
    }
    entries.reverse

  /** Hash that the next entry's `prev` refers to: covers the signed text and the signature. */
  def entryHash(entry: LedgerEntry)(using crypto: Crypto): Digest =
    crypto.sha256(Bytes.utf8(entry.text.value) ++ entry.signature.bytes)

  val GenesisHash: Digest = Digest(IArray.fill[Byte](32)(0))
