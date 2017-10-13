/*
 *  Config.scala
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

import java.net.InetSocketAddress

final case class Config(
                         dumpOSC: Boolean = false,
                         ownSocket          : Option[InetSocketAddress] = None,
                         dot                : Int           = -1,
                         log                : Boolean       = false,
                       )
  extends ConfigLike {

  def isLaptop            = true
  def disableEnergySaving = false
}