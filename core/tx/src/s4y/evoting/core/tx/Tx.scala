package s4y.evoting.core.tx

import s4y.evoting.core.domain.InfraFailure

/** Capability: proof that code runs inside an atomic commit. */
trait Tx

trait TransactionManager[TX <: Tx]:
  /** Runs `body` as one atomic commit. A `Left`, or an exception, rolls back every write in it. */
  def atomically[E, A](label: String)(body: TX ?=> Either[E, A]): Either[E | InfraFailure, A]
