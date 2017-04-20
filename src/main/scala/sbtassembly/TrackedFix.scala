package sbtassembly

import java.io.File
import sbt._
import CacheImplicits._
import sjsonnew.JsonFormat
import sbt.util.{ SingletonCache, CacheStore }
import scala.util.{ Failure, Try, Success }

// This is a workaround for https://github.com/sbt/util/issues/79
object TrackedFix {
  def outputChanged[I: JsonFormat: SingletonCache, O](cacheFile: File)(f: (Boolean, I) => O): (() => I) => O =
    outputChanged[I, O](CacheStore(cacheFile))(f)

  def outputChanged[I: JsonFormat: SingletonCache, O](store: CacheStore)(f: (Boolean, I) => O): (() => I) => O = in =>
    {
      val cache: SingletonCache[I] = implicitly
      val initial = in()
      val help = new CacheHelp(cache)
      val changed = help.changed(store, initial)
      val result = f(changed, initial)
      if (changed) {
        help.save(store, initial)
      }
      result
    }

  private final class CacheHelp[I: JsonFormat](val sc: SingletonCache[I]) {
    def save(store: CacheStore, value: I): Unit = {
      store.write(value)
    }

    def changed(store: CacheStore, value: I): Boolean =
      Try { store.read[I] } match {
        case Success(prev) => !sc.equiv.equiv(value, prev)
        case Failure(_)    => true
      }
  }
}
