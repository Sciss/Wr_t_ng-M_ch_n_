/*
 *  UpdateDebSource.scala
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

import java.nio.ByteBuffer

import de.sciss.file._

final class UpdateDebSource(val uid: Long, c: OSCClient, val instance: Status, file: File)
  extends UpdateSource(c, Network.dotToSocketMap(instance.dot), file) {

  def begin(): Unit =
    reply(Network.OscUpdateInit(uid = uid, size = size))

  protected def sendData(offset: Long, bytes: ByteBuffer): Unit =
    reply(Network.OscUpdateSet(uid = uid, offset = offset, bytes = bytes))
}
