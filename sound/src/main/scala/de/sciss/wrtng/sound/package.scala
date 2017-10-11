/*
 *  package.scala
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

import de.sciss.fscape.Graph
import de.sciss.fscape.stream.Control

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

package object sound {
  def any2stringadd(x: Any): Unit = ()  // that bloody thing doesn't die

  implicit final class GraphOps(private val g: Graph) extends AnyVal {
    def renderAndWait(): Try[Unit] = {
      val cfg = Control.Config()
      cfg.useAsync = false
      val c = Control(cfg)
//      new Thread {
//        override def run(): Unit =
          c.run(g)
//
//        start()
//      }

      Await.ready(c.status, Duration.Inf)
      c.status.value.get
    }
  }
}
