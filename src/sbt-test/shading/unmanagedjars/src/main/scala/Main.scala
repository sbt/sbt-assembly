import org.apache.commons.io.filefilter.AgeFileFilter

object Main {
  def main(args: Array[String]): Unit = {
    val filter = new AgeFileFilter(0)
    println("hello " + filter.getClass.getName.toString)
  }
}
