package s4y.evoting.audit.app.usecases

import s4y.evoting.audit.domain.{AuditOperations, AuditReport, PublicRecord}
import s4y.evoting.core.app.{UseCase, UseCaseCommand}
import s4y.evoting.core.crypto.{Crypto, Party, Verifier}
import s4y.evoting.core.domain.InfraFailure
import s4y.evoting.voting.domain.vo.VotingKey

/** Reads the public record of one voting. The audit context has no store of its own, so this
  * port is implemented by the composition root over the other contexts (`VotingSystem`). */
trait PublicRecords:
  def of(voting: VotingKey): Either[InfraFailure, Option[PublicRecord]]

/** Runs the auditor over a voting's public record: the tally and the invariant checks. */
final case class AuditQuery(voting: VotingKey) extends UseCaseCommand:
  type E = InfraFailure
  type A = Option[(PublicRecord, AuditReport)]

/** No workflow and no write, so no atomic commit: the port read is one commit of its own. */
final class AuditUseCase(using records: PublicRecords, operator: Verifier[Party.Operator], issuer: Verifier[Party.Issuer], crypto: Crypto)
    extends UseCase[AuditQuery]:
  def apply(c: AuditQuery): Either[c.E, c.A] =
    records.of(c.voting).map(_.map(record => record -> AuditOperations.audit(record)))
