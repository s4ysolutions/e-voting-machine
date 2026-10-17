package s4y.evoting.core.domain

/** Accumulating validation: every error is reported, not only the first. `::[E]` is never empty. */
enum Validated[+E, +A]:
  case Valid(value: A)
  case Invalid(errors: ::[E])

object Validated:
  def valid[A](a: A): Validated[Nothing, A]   = Valid(a)
  def invalid[E](e: E): Validated[E, Nothing] = Invalid(::(e, Nil))

  extension [E, A](either: Either[E, A])
    def toValidated: Validated[E, A] = either match
      case Left(e)  => invalid(e)
      case Right(a) => valid(a)

  extension [E, A](va: Validated[E, A])
    def map[B](f: A => B): Validated[E, B] = va match
      case Valid(a)   => Valid(f(a))
      case Invalid(e) => Invalid(e)

    def zip[E2 >: E, B](vb: Validated[E2, B]): Validated[E2, (A, B)] = (va, vb) match
      case (Valid(a), Valid(b))       => Valid((a, b))
      case (Invalid(e1), Invalid(e2)) => Invalid(::(e1.head, e1.tail ++ e2))
      case (Invalid(e), _)            => Invalid(e)
      case (_, Invalid(e))            => Invalid(e)

    def toEither: Either[::[E], A] = va match
      case Valid(a)   => Right(a)
      case Invalid(e) => Left(e)

  def traverse[E, A, B](as: List[A])(f: A => Validated[E, B]): Validated[E, List[B]] =
    as.foldRight(valid(List.empty[B]): Validated[E, List[B]])((a, acc) => f(a).zip(acc).map(_ :: _))
