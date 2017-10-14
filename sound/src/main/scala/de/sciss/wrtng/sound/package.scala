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
import de.sciss.lucre.confluent.TxnRandom
import de.sciss.span.Span

import scala.concurrent.duration.Duration
import scala.concurrent.stm.{InTxn, Txn, atomic}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

package object sound {
  def any2stringadd(x: Any): Unit = ()  // that bloody thing doesn't die

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  final val SR: Double = 48000.0

  def secondsToFrames(secs: Double): Long = (secs * SR + 0.5).toLong

  def framesToSeconds(frames: Long): Double = frames / SR

  def formatSeconds(seconds: Double): String = {
    val millisR = (seconds * 1000).toInt
    val sb      = new StringBuilder(10)
    val secsR   = millisR / 1000
    val millis  = millisR % 1000
    val mins    = secsR / 60
    val secs    = secsR % 60
    if (mins > 0) {
      sb.append(mins)
      sb.append(':')
      if (secs < 10) {
        sb.append('0')
      }
    }
    sb.append(secs)
    sb.append('.')
    if (millis < 10) {
      sb.append('0')
    }
    if (millis < 100) {
      sb.append('0')
    }
    sb.append(millis)
    sb.append('s')
    sb.toString()
  }

  def formatSpan(span: Span): String = {
    val sb = new StringBuilder(24)
    sb.append('[')
    sb.append(formatSeconds(framesToSeconds(span.start)))
    sb.append('-')
    sb.append(formatSeconds(framesToSeconds(span.stop)))
    sb.append(']')
    sb.toString()
  }

  def formatPercent(d: Double): String = {
    val pm = (d * 1000).toInt
    val post = pm % 10
    val pre = pm / 10
    val sb = new StringBuilder(8)
    sb.append(pre)
    sb.append('.')
    sb.append(post)
    sb.append('%')
    sb.toString()
  }

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

  def txFutureSuccessful[A](value: => A)(implicit tx: InTxn): Future[A] = {
    val p = Promise[A]()
    Txn.afterCommit(_ => p.complete(Try(value)))
    p.future
  }

  type TxRnd = TxnRandom[InTxn]

  def render[A](ctlCfg: Control.Config, g: Graph)(done: InTxn => A)(implicit tx: InTxn): Future[A] = {
    val p = Promise[A]()
    Txn.afterCommit { _ =>
      try {
        val ctl = Control(ctlCfg)
        ctl.run(g)
        val futF = ctl.status.map { _ =>
          atomic(done)
        }
        p.completeWith(futF)
      } catch {
        case NonFatal(ex) => p.failure(ex)
      }
    }

    p.future
  }
}
