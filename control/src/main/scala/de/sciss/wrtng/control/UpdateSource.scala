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
package control

import java.io.RandomAccessFile
import java.nio.ByteBuffer

import de.sciss.file._
import de.sciss.osc

final class UpdateSource(val uid: Int, config: Config, c: OSCClient, val instance: Status, val debFile: File) {
  private[this] val raf         = new RandomAccessFile(debFile, "r")
  val               size: Long  = raf.length()
  private[this] val ch          = raf.getChannel
  //  private[this] val promise     = Promise[Unit]
  private[this] val target      = Network.dotToSocketMap(instance.dot)
  private[this] val buf         = ByteBuffer.allocate(6 * 1024)

  //  def result: Future[Unit] = promise.future

  private def reply(p: osc.Packet): Unit =
    c.tx.send(p, target)

  def begin(): Unit = {
    reply(Network.OscUpdateInit(uid = uid, size = size))
  }

  def sendNext(offset: Long): Unit = {
    val bytes: ByteBuffer = ch.synchronized {
      if (ch.position != offset) ch.position(offset)
      buf.clear()
      val chunk = math.min(buf.capacity(), size - offset).toInt
      buf.limit(chunk)
      ch.read(buf)
      buf.flip()
      buf
    }
    reply(Network.OscUpdateSet(uid = uid, offset = offset, bytes = bytes))
  }

  def dispose(): Unit = {
    ch.synchronized(ch.close())
  }
}
