/*
 *  OfflineImpl.scala
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

package de.sciss.wrtng.radio
package impl

import de.sciss.file.File
import de.sciss.synth.io.AudioFileSpec

import scala.concurrent.Future

final class OfflineImpl(file: File, spec: AudioFileSpec) extends Offline {
  private[this] var offset = 0L

  def acquire(dur: Double): Future[RadioSnippet] = {
    require(dur > 0.0)
    val numFrames = math.min(spec.numFrames, (dur * spec.sampleRate + 0.5).toLong)
    val off0      = offset
    val stop      = off0 + numFrames
    val snippet   = if (stop <= spec.numFrames) {
      offset = stop
      RadioSnippet(file, offset = off0, numFrames = numFrames)
    } else {
      offset = numFrames
      RadioSnippet(file, offset = 0L, numFrames = numFrames)
    }
    Future.successful(snippet)
  }
}