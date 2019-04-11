/*
 *  Algorithm.scala
 *  (Wr_t_ng-M_ch_n_)
 *
 *  Copyright (c) 2017-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.wrtng
package sound

import de.sciss.lucre.synth.Txn

import scala.concurrent.Future
import scala.concurrent.stm.InTxn

object Algorithm {
  def apply(c: OSCClient, channel: Int): Algorithm = {
    require(channel == 0 || channel == 1)
    new impl.AlgorithmImpl(c, channel = channel)
  }
}
trait Algorithm {
  def init(): this.type

  def client: OSCClient

  def channel: Int

  def iterate(iterId: Int)(implicit tx: InTxn): Future[Unit]

  /** @return the duration in seconds of the snippet being played */
  def play()(implicit tx: Txn): Double

  def playAndIterate()(implicit tx: Txn): Future[Unit]

  def playLogic(relay: Boolean)(implicit tx: Txn): Future[Unit]
}