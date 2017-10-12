/*
 *  Task.scala
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

import java.util.TimerTask

import scala.concurrent.stm.{InTxn, Ref, Txn, atomic}

object Task {
  def apply(scheduler: java.util.Timer, delay: Long)(body: InTxn => Unit)(implicit tx: InTxn): Task = {
    val res = new Impl(body)
    Txn.afterCommit(_ => scheduler.schedule(res.peer, delay))
    res
  }

  private final class Impl(body: InTxn => Unit) extends Task {
    private[this] val cancelledRef = Ref(false)
    private[this] val executedRef  = Ref(false)

    private[this] val _peer = new TimerTask {
      def run(): Unit = atomic { implicit tx =>
        if (!cancelledRef() && !executedRef.swap(true)) body(tx)
      }
    }

    def peer: TimerTask = _peer

    def cancel()(implicit tx: InTxn): Unit =
      if (!cancelledRef.swap(true)) {
        Txn.afterCommit(_ => _peer.cancel())
      }

    def isCancelled(implicit tx: InTxn): Boolean = cancelledRef()
    def wasExecuted(implicit tx: InTxn): Boolean = executedRef()
  }
}
trait Task {
  def cancel()(implicit tx: InTxn): Unit

  def isCancelled(implicit tx: InTxn): Boolean
  def wasExecuted(implicit tx: InTxn): Boolean
}
