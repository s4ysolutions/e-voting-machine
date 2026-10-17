package s4y.evoting.core.identity.mint

import s4y.evoting.core.identity.Identifier

/** The only way to construct an `Identifier`, and to read its raw value. For adapters (minting on
  * insert) and the ui boundary (parsing input). */
extension (raw: String) def identifier[A]: Identifier[A] = Identifier.unsafe[A](raw)
extension [A](id: Identifier[A]) def raw: String = id.value
