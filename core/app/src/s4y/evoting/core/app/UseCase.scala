package s4y.evoting.core.app

/** A command describes an intent and carries its own error and result types. */
trait UseCaseCommand:
  type E
  type A

/** Handles one command. The ui depends on `UseCase[C]` only, never on the implementation. */
trait UseCase[C <: UseCaseCommand]:
  def apply(command: C): Either[command.E, command.A]

extension [C <: UseCaseCommand](command: C)
  /** Runs the command with the use case found among the `given`s. */
  def run(using useCase: UseCase[C]): Either[command.E, command.A] = useCase(command)

extension [A](result: Either[Nothing, A])
  /** Unwraps the result of a command whose `E` is `Nothing`. */
  def infallible: A = result.fold[A](nothing => nothing, a => a)
