/*
 *  Offline.scala
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

import de.sciss.synth.io.AudioFile

object Offline {
  def apply(config: Config): Offline = {
    val f = config.offline.getOrElse(
      throw new IllegalArgumentException("Must have a config with a specific 'offline' file"))
    val spec = AudioFile.readSpec(f)
    require(spec.numChannels == 1, s"File '$f' has ${spec.numChannels} channels, mono required")
    new impl.OfflineImpl(f, spec)
  }
}
trait Offline extends Source