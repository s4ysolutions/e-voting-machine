package s4y.evoting.core.ui

import com.raquo.laminar.api.L.*
import s4y.evoting.core.crypto.{Party, Signed, Verifier}

/** Who signed a value, as the audience sees it (decision D7). */
enum SignerLabel(val name: String, val css: String):
  case Issuer   extends SignerLabel("issuer", "signer-issuer")
  case Operator extends SignerLabel("operator", "signer-operator")
  case Unknown  extends SignerLabel("unknown signer", "signer-unknown")

/** A signed value shown as readable text plus the real signature tag (decision D7).
  * `editable` lets the audience change the text: the line turns red while the text no longer
  * matches the signature and green again once it is restored. */
object SignedLine:
  private val LongHex = "[0-9a-f]{32,}".r

  /** The text without its long hex values (hashes, full IDs), which stay in the tooltip. */
  def readable(text: String): String =
    val words = text.split(' ').toList
    val kept  = words.filterNot(w => LongHex.matches(w.dropWhile(_ != '=').drop(1)))
    if kept.sizeIs == words.size then text else kept.mkString(" ") + " …"

  def apply[P <: Party](signed: Signed[P, ?], signer: SignerLabel, editable: Boolean = false)(using
      verifier: Verifier[P]
  ): HtmlElement =
    val original = signed.text.value
    val text     = Var(original)
    val valid    = text.signal.map(t => verifier.verifyText(t, signed.signature))
    div(
      cls := "signed-line",
      cls("tampered") <-- valid.map(!_),
      if editable then
        input(
          cls := "signed-text",
          aria.label := "Signed text; edit it to see the signature break",
          spellCheck := false,
          controlled(value <-- text.signal, onInput.mapToValue --> text)
        )
      else span(cls := "signed-text", title := original, readable(original)),
      span(
        cls := s"signed-tag ${signer.css}",
        s"signed:${signer.name} #${signed.signature.tag}"
      ),
      span(
        cls := "signed-check",
        child.text <-- valid.map(ok => if ok then "✓ valid" else "✗ signature does not match")
      )
    )
