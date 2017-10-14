package de.sciss.wrtng.sound

import de.sciss.file.File

import scala.concurrent.stm.{InTxn, Ref, Txn}

/** Use count is initially one. */
final class AudioFileRef(val f: File, val numFrames: Long) {
  private[this] val useCount = Ref(1)

  def isValid(implicit tx: InTxn): Boolean = useCount() > 0

  def isDisposed(implicit tx: InTxn): Boolean = !isValid

  def acquire()(implicit tx: InTxn): Unit = {
    val before = useCount.getAndTransform(_ + 1)
    require (before > 0, "Phrase was already released")
  }

  def release()(implicit tx: InTxn): Unit = {
    val now = useCount.transformAndGet(_ - 1)
    require (now >= 0, "Phrase was already released")
    if (now == 0) Txn.afterCommit(_ => f.delete())
  }
}