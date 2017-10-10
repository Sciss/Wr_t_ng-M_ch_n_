package de.sciss

import de.sciss.fscape.Graph
import de.sciss.fscape.stream.Control

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

package object wrtng {
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
