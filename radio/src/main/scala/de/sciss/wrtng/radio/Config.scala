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
package radio

import java.net.InetSocketAddress

/**
  * @param dumpOSC              if `true`, print incoming and outgoing OSC packets
  * @param isLaptop             if `true`, assume a test run from laptop, no GPIO etc.
  * @param disableEnergySaving  if `true`, disable screen blanking and power saving on the RPi
  * @param ownSocket            optional socket to bind local OSC client to (useful when debugging from laptop)
  * @param dot                  override 'dot' transaction id (useful when debugging from laptop)
  * @param log                  enable log message printing
  */
final case class Config(
                         dumpOSC            : Boolean       = false,
                         isLaptop           : Boolean       = false,
                         disableEnergySaving: Boolean       = true,
                         ownSocket          : Option[InetSocketAddress] = None,
                         dot                : Int           = -1,
                         log                : Boolean       = false,
                         gqrxConfig         : String        = "wrtng.conf",
                         gqrxTCPPort        : Int           = 7356
                       )
  extends ConfigLike