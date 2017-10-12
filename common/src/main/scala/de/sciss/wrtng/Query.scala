/*
 *  Query.scala
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

import java.net.SocketAddress
import java.util.concurrent.TimeoutException

import de.sciss.kollflitz.Vec
import de.sciss.osc

import scala.concurrent.stm.{InTxn, Ref, TSet, Txn}
import scala.util.{Failure, Success, Try}

final class Query[A](c: OSCClientLike, sq: Vec[SocketAddress], mOut: osc.Message,
                     result: InTxn => Try[List[QueryResult[A]]] => Unit,
                     handler: PartialFunction[osc.Packet, A], extraDelay: Long,
                     tx0: InTxn) {

  private[this] val values    = Ref(List.empty[QueryResult[A]])
  private[this] val remaining = TSet(sq: _*)

  private[this] val timeOut = c.scheduleTxn(Network.TimeOutMillis + extraDelay) { implicit tx =>
    c.removeQuery(this)
    result(tx)(Failure(new TimeoutException(mOut.name)))
  } (tx0)

  def handle(sender: SocketAddress, pIn: osc.Packet)(implicit tx: InTxn): Boolean = {
    val senderOk = remaining.contains(sender)
    //    if (!senderOk) println(s"SENDER NOT OK ${sender} vs. ${remaining.toSet}")
    senderOk && handler.isDefinedAt(pIn) && {
      remaining.remove(sender)
      val value = handler(pIn)
      val dot   = c.socketToDotMap.getOrElse(sender, -1)
      val res   = QueryResult(dot, value)
      val vs    = values.transformAndGet(res :: _)
      remaining.isEmpty && {
        timeOut.cancel()
        result(tx)(Success(vs))
        true
      }
    }
  }

  Txn.afterCommit { _ =>
    sq.foreach { target =>
      c.transmitter.send(mOut, target)
    }
  } (tx0)
}