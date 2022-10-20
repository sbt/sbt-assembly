package sbtassembly

import java.io.{ ByteArrayInputStream, InputStream }
import scala.collection.mutable

private[sbtassembly] object AssemblyUtils {

  /**
   * Checks if an [[InputStream]] ends with the given eof string and adds it if not
   *
   * @param is input stream to check
   * @param eof the terminating string to add at the end if needed
   */
  class AppendEofInputStream(is: InputStream, eof: String) extends InputStream {
    private val readBytesQ: mutable.Queue[Int] = mutable.Queue()
    private val eofBytes = eof.map(_.toByte).toArray
    private val maxQueueSize = eof.length
    private val eofStream = new ByteArrayInputStream(eofBytes)
    private var source = is

    private def enqueue(i: Int): Unit = {
      if (i > 0) readBytesQ.enqueue(i)
      if (readBytesQ.size > maxQueueSize) readBytesQ.dequeue()
    }

    override def read(): Int =
      readWithEnqueue(() => source.read(), enqueue)

    override def read(b: Array[Byte], off: Int, len: Int): Int =
      readWithEnqueue(() => source.read(b, off, len), _ => b.map(_.toInt).foreach(enqueue))

    override def close(): Unit = {
      is.close()
      eofStream.close()
    }

    private def readWithEnqueue(read: () => Int, enqueue: Int => Unit): Int = {
      val byte = read()
      if (byte != -1) {
        enqueue(byte)
        byte
      } else {
        val lastBytesStored = readBytesQ.dequeueAll(_ => true)
        if (lastBytesStored.toArray.map(_.toByte) sameElements eofBytes)
          -1
        else {
          source = eofStream
          val eofByte = read()
          enqueue(eofByte)
          eofByte
        }
      }
    }
  }

  object AppendEofInputStream {

    /**
     * Creates an [[AppendEofInputStream]]
     * @param is input stream to check
     * @param eof the terminating string to add at the end if needed
     * @return an [[AppendEofInputStream]] instance
     */
    def apply(is: InputStream, eof: String = "\n") = new AppendEofInputStream(is, eof)
  }
}
