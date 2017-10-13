/*
 *  UpdateTarget.scala
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

import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.osc

abstract class UpdateTarget(suffix: String) {
  // ---- abstract ----

  protected def uid   : Int
  protected def c     : OSCClientLike
  protected def sender: SocketAddress
  protected def size  : Long

  protected def queryNext(): Unit
  protected def transferCompleted(f: File): Unit

  // ---- impl ----

  private[this] var _offset = 0L
  private[this] val f       = File.createTemp(suffix = suffix)
  private[this] val raf     = new RandomAccessFile(f, "rw")
  private[this] val ch      = raf.getChannel

  final protected def offset: Long = _offset

  final protected def reply(p: osc.Packet): Unit =
    c.transmitter.send(p, sender)

  final def begin(): Unit = {
    require(_offset === 0L)
    queryNext()
  }

  final def write(off: Long, bytes: ByteBuffer): Unit = {
    if (off != _offset) {
      reply(Network.OscUpdateError(uid, s"expected offset ${_offset} but got $off"))
      queryNext()
    } else {
      val plus = bytes.remaining()
      ch.write(bytes)
      _offset += plus
      if (_offset < size) queryNext()
      else transferCompleted(f)
    }
  }

  final protected def sudo(cmd: String*): Int = {
    import sys.process._
    if (c.config.isLaptop) {
      Process("sudo" +:"-A" +: cmd, None, "SUDO_ASKPASS" -> "/usr/bin/ssh-askpass").!
    } else {
      Process("sudo" +: cmd).!
    }
  }

  def dispose(): Unit = {
    ch.close()
    f.delete()
  }
}