object Main {
  def main(args: Array[String]) {
    Option(getClass().getResource("foo.txt")) match {
      case Some(_) => println("foo.txt")
      case _ =>       println("hello")
    }
  }
}
