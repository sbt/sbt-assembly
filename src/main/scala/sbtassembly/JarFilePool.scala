package sbtassembly

import java.io.{ File, FilterInputStream, InputStream }
import java.util.jar.JarFile

/**
 * A bounded, reference-counted pool of open [[JarFile]] handles.
 *
 * Only up to `maxOpen` jars are kept open at a time; the least recently used unreferenced handle is
 * closed to make room. A handle is never closed while one of its streams is still being read.
 */
private[sbtassembly] final class JarFilePool(maxOpen: Int) extends AutoCloseable {
  private final class Handle(val jar: JarFile) {
    var refs: Int = 0
  }

  private val open = new java.util.LinkedHashMap[String, Handle](16, 0.75f, true)
  private var closed = false
  private var opens = 0L
  private var reuses = 0L

  private def acquire(file: File): (String, Handle) = synchronized {
    if (closed) sys.error("JarFilePool is closed")
    val key = file.getAbsolutePath
    val handle = open.get(key) match {
      case null =>
        opens += 1
        val h = new Handle(new JarFile(file))
        open.put(key, h)
        h
      case h =>
        reuses += 1
        h
    }
    handle.refs += 1
    evict()
    key -> handle
  }

  private def release(handle: Handle): Unit = synchronized {
    handle.refs -= 1
    evict()
  }

  private def evict(): Unit =
    if (open.size > maxOpen) {
      val it = open.entrySet().iterator()
      while (it.hasNext && open.size > maxOpen) {
        val handle = it.next().getValue
        if (handle.refs == 0) {
          handle.jar.close()
          it.remove()
        }
      }
    }

  /** Runs `f` with the jar open, keeping it open for the duration of the call */
  def withJar[A](file: File)(f: JarFile => A): A = {
    val (_, handle) = acquire(file)
    try f(handle.jar)
    finally release(handle)
  }

  /** Opens a stream for `entryName`, opening (or reusing) the jar and releasing it on close */
  def stream(file: File, entryName: String): InputStream = {
    val (key, handle) = acquire(file)
    val entry = handle.jar.getEntry(entryName)
    if (entry == null) {
      release(handle)
      sys.error(s"Entry $entryName disappeared from $key")
    }
    new FilterInputStream(handle.jar.getInputStream(entry)) {
      private var released = false
      override def close(): Unit = {
        try super.close()
        finally
          synchronized {
            if (!released) {
              released = true
              release(handle)
            }
          }
      }
    }
  }

  def stats: String = synchronized(s"jar handles opened = $opens, reused = $reuses, still open = ${open.size}")

  override def close(): Unit = synchronized {
    closed = true
    val it = open.entrySet().iterator()
    while (it.hasNext) {
      it.next().getValue.jar.close()
      it.remove()
    }
  }
}

private[sbtassembly] object JarFilePool {
  private val maxOpenJarsProperty = "sbtassembly.maxOpenJars"
  private val absoluteMaxOpen = 512
  private val minimumMaxOpen = 16

  /** Descriptors left for the rest of the build, which holds its own classpath open */
  private val reservedFileDescriptors = 256

  /** The number of jars to keep open, kept well under the file descriptor limit of the process */
  def defaultMaxOpen: Int =
    sys.props
      .get(maxOpenJarsProperty)
      .flatMap(value => scala.util.Try(value.toInt).toOption)
      .getOrElse {
        val fileDescriptorLimit =
          try
            java.lang.management.ManagementFactory.getOperatingSystemMXBean match {
              case os: com.sun.management.UnixOperatingSystemMXBean => os.getMaxFileDescriptorCount
              case _                                                => -1L
            }
          catch { case _: Throwable => -1L }
        if (fileDescriptorLimit <= 0) absoluteMaxOpen
        else
          math.max(
            minimumMaxOpen,
            math.min(absoluteMaxOpen, ((fileDescriptorLimit - reservedFileDescriptors) / 2).toInt)
          )
      }

  def apply(): JarFilePool = new JarFilePool(math.max(1, defaultMaxOpen))
}
