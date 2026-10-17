package s4y.evoting.core.crypto

import org.scalatest.funsuite.AnyFunSuite

class CryptoValuesSpec extends AnyFunSuite:
  test("hex round-trips"):
    val bytes = IArray[Byte](0, 1, 127, -128, -1)
    assert(Bytes.hex(bytes) == "00017f80ff")
    assert(Bytes.fromHex("00017f80ff").map(_.toSeq) == Some(bytes.toSeq))
    assert(Bytes.fromHex("0g").isEmpty)
    assert(Bytes.fromHex("abc").isEmpty)

  test("readable names are deterministic and use the first three bytes"):
    assert(ReadableName.of(IArray[Byte](0, 0, 42)) == "amber-falcon-42")
    assert(ReadableName.of(IArray[Byte](31, 31, -1)) == "humble-crane-55")
    assert(ReadableName.of(IArray[Byte](32, 32, 100)) == "amber-falcon-0")   // only 5 bits used per word

  test("the D7 tag is the first 6 hex characters of the signature"):
    assert(Signature(IArray.fill[Byte](64)(-85)).tag == "ababab")
