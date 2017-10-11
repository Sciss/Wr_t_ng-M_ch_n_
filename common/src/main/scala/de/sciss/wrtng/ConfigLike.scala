/*
 *  ConfigLike.scala
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

import java.net.InetSocketAddress

trait ConfigLike {
  def isLaptop            : Boolean
  def dumpOSC             : Boolean
  def disableEnergySaving : Boolean
  def ownSocket           : Option[InetSocketAddress]
  def dot                 : Int
  def log                 : Boolean
}