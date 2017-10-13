/*
 *  UpdateSource.scala
 *  (Wr_t_ng-M_ch_n_)
 *
 *  Copyright (c) 2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.wrtng

import java.io.RandomAccessFile
import java.net.SocketAddress
import java.nio.ByteBuffer

import de.sciss.file._
import de.sciss.osc

abstract class UpdateSource(val c: OSCClientLike, val target: SocketAddress, val file: File) {
  // ---- abstract ----

  def begin(): Unit

  protected def sendData(offset: Long, bytes: ByteBuffer): Unit

  // ---- impl ----

  private[this] val raf         = new RandomAccessFile(file, "r")
  private[this] val _size       = raf.length()
  private[this] val ch          = raf.getChannel
  private[this] val buf         = ByteBuffer.allocate(6 * 1024)

  final def size: Long = _size

  protected final def reply(p: osc.Packet): Unit =
    c.sendNow(p, target)

  final def sendNext(offset: Long): Unit = {
    val bytes: ByteBuffer = ch.synchronized {
      if (ch.position != offset) ch.position(offset)
      buf.clear()
      val chunk = math.min(buf.capacity(), _size - offset).toInt
      buf.limit(chunk)
      ch.read(buf)
      buf.flip()
      buf
    }
    sendData(offset = offset, bytes = bytes)
  }

  def dispose(): Unit = {
    ch.synchronized(ch.close())
  }
}
