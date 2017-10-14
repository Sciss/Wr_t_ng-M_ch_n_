/*
 *  Algorithm.scala
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

import scala.concurrent.Future
import scala.concurrent.stm.InTxn

object Algorithm {
  def apply(c: OSCClient): Algorithm = new impl.AlgorithmImpl(c)
}
trait Algorithm {
  def init(): this.type

  def iterate()(implicit tx: InTxn): Future[Unit]
}