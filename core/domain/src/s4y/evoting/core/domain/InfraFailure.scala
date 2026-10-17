package s4y.evoting.core.domain

/** The one infrastructure error that may cross a port. Adapters wrap library failures in it. */
final case class InfraFailure(message: String) derives CanEqual

object InfraFailure:
  /** strictEquality: lets `Either[E | InfraFailure, A]` results be compared with `Left(e)`. */
  given unionCanEqual[E](using CanEqual[E, E]): CanEqual[E | InfraFailure, E | InfraFailure] =
    CanEqual.derived
