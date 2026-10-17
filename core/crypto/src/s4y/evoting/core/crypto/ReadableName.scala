package s4y.evoting.core.crypto

/** Short, memorable names for hashes (decision D7): `amber-falcon-42`. Display only — names can
  * collide, so never look anything up by one. */
object ReadableName:
  private val adjectives = IArray(
    "amber", "brave", "calm", "dusty", "eager", "fancy", "gentle", "happy",
    "icy", "jolly", "keen", "lucky", "merry", "noble", "olive", "proud",
    "quiet", "rapid", "silver", "tidy", "ultra", "vivid", "witty", "young",
    "zesty", "bold", "crisp", "deep", "early", "fresh", "grand", "humble"
  )
  private val animals = IArray(
    "falcon", "otter", "lynx", "heron", "bison", "koala", "panda", "raven",
    "tiger", "zebra", "whale", "gecko", "moose", "camel", "eagle", "fox",
    "hare", "ibis", "jaguar", "lemur", "mole", "newt", "owl", "puma",
    "quail", "robin", "seal", "toad", "viper", "wolf", "yak", "crane"
  )

  def of(bytes: IArray[Byte]): String =
    require(bytes.length >= 3, "need at least 3 bytes")
    val adjective = adjectives(bytes(0) & 31)
    val animal    = animals(bytes(1) & 31)
    val number    = (bytes(2) & 0xff) % 100
    s"$adjective-$animal-$number"
  // No `of(digest: Digest)` overload: an opaque type erases to its underlying array. Use `of(d.bytes)`.
