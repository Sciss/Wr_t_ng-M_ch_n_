/*
 *  UpdateDebTarget.scala
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

import java.net.SocketAddress

import de.sciss.equal.Implicits._
import de.sciss.file._

final class UpdateDebTarget(val uid: Int, protected val c: OSCClientLike, val sender: SocketAddress,
                            protected val size: Long)
  extends UpdateTarget(".deb") {

  protected def queryNext(): Unit =
    reply(Network.OscUpdateGet(uid = uid, offset = offset))

  protected def transferCompleted(f: File): Unit = {
    import sys.process._
    val resInfo = Seq("dpkg", "--info", f.path).!
    if (resInfo === 0) {
      val resInstall = sudo("dpkg", "--install", f.path)
      dispose()
      val m = if (resInstall === 0) {
        Network.OscUpdateSuccess(uid)
      } else {
        Network.OscUpdateError(uid, s"dpkg --install returned $resInstall")
      }
      reply(m)

    } else {
      reply(Network.OscUpdateError(uid, s"dpkg --info returned $resInfo"))
    }
  }
}