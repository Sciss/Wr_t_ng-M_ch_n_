/*
 *  UpdateRadioTarget.scala
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
package sound

import java.net.SocketAddress

import de.sciss.file._

import scala.util.{Failure, Success, Try}

final class UpdateRadioTarget(val uid: Long, val c: OSCClientLike, val sender: SocketAddress,
                              val size: Long, done: File => Unit)
  extends UpdateTarget(".wav") {

  protected def queryNext(): Unit =
    reply(Network.OscRadioRecGet(uid = uid, offset = offset))

  override protected val deleteFileOnDisposal = false

  protected def transferCompleted(f: File): Unit = {
    val m = Try(done(f)) match {
      case Success(_)  => Network.OscRadioRecDispose(uid)
      case Failure(ex) => Network.OscRadioRecError  (uid, ex.toString.take(200))
    }
    reply(m)
  }
}