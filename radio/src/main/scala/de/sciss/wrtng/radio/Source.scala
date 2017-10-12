package de.sciss.wrtng
package radio

import de.sciss.file.File

import scala.concurrent.Future

final case class RadioSnippet(file: File, offset: Long, numFrames: Long)

trait Source {
  def acquire(dur: Double): Future[RadioSnippet]
}
