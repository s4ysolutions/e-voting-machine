package s4y.evoting.core.ui

import com.raquo.laminar.api.L.*

/** Shared signal that some data changed; read models re-read on it. One per composition root. */
final class ChangeFeed:
  private val bus = EventBus[Unit]()
  val changes: EventStream[Unit] = bus.events
  def notifyChanged(): Unit = bus.writer.onNext(())
