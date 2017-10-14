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
import java.nio.channels.FileChannel

import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.osc

abstract class UpdateTarget(suffix: String) {
  // ---- abstract ----

  def uid   : Long
  def c     : OSCClientLike
  def sender: SocketAddress
  def size  : Long

  protected def queryNext(): Unit
  protected def transferCompleted(f: File): Unit

  // ---- impl ----

  protected def createFile(): File = File.createTemp(suffix = suffix)

  protected val deleteFileOnDisposal: Boolean = true

  private[this] var _offset : Long        = 0L
  private[this] var f       : File        = _
  private[this] var ch      : FileChannel = _

  final protected def offset: Long = _offset

  final protected def reply(p: osc.Packet): Unit =
    c.transmitter.send(p, sender)

  final def begin(): Unit = {
    require(_offset === 0L)
    f       = createFile()
    val raf = new RandomAccessFile(f, "rw")
    ch      = raf.getChannel
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
      else {
        try {
          ch.close()
          transferCompleted(f)
        } finally {
          dispose()
        }
      }
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
    if (ch != null) ch.close()
    if (deleteFileOnDisposal && f != null) f.delete()
  }
}