package keep

import org.apache.commons.lang3.tuple.ImmutablePair

class Keeped {
  def main(args: Array[String]): Unit = {
    val myUsedObject = new ImmutablePair[String, String]("Foo", "Bar")
    println(myUsedObject)
  }
}
