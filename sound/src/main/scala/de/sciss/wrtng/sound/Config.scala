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
package sound

import java.net.InetSocketAddress

import de.sciss.file._

final case class Config(
                         baseDir             : File          = userHome/"Documents"/"projects"/"WrtngMchn",
                         dumpOSC             : Boolean       = false,
                         isLaptop            : Boolean       = false,
                         disableEnergySaving : Boolean       = true,
                         qjLaunch            : Boolean       = true,
                         qjPreset            : String        = "Goobay",
                         qjPatchBay          : File          = userHome/"Documents"/"jack-defaults.xml",
                         ownSocket           : Option[InetSocketAddress] = None,
                         radioSocket         : Option[InetSocketAddress] = None,
                         dot                 : Int           = -1,
                         log                 : Boolean       = false
//                         keypad              : Boolean       = false
                       )
  extends ConfigLike