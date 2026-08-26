enum Color:
  case Red, Green

object Main:
  def main(args: Array[String]): Unit =
    println(if Color.Red.ordinal == 0 then "hello" else "bye")
