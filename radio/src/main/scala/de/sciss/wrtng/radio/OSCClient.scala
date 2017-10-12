/*
 *  OSCClient.scala
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

import java.net.{InetSocketAddress, SocketAddress}

import de.sciss.osc
import de.sciss.osc.UDP

import scala.concurrent.stm.InTxn
import scala.util.Try

object OSCClient {
  def apply(config: Config, localSocketAddress: InetSocketAddress)
           /* (implicit rnd: Random) */: OSCClient = {
    val c                 = UDP.Config()
    c.codec               = Network.oscCodec
    val dot               = Network.resolveDot(config, localSocketAddress)
    c.localSocketAddress  = localSocketAddress
    println(s"OSCClient local socket $localSocketAddress - dot $dot")
    val tx                = UDP.Transmitter(c)
    val rx                = UDP.Receiver(tx.channel, c)
    new OSCClient(config, dot, tx, rx)
  }
}
/** Undirected pair of transmitter and receiver, sharing the same datagram channel. */
final class OSCClient(override val config: Config, val dot: Int,
                      val transmitter : UDP.Transmitter .Undirected,
                      val receiver    : UDP.Receiver    .Undirected,
                     )/* (implicit rnd: Random) */
  extends OSCClientLike {

  override def main: Main.type = Main

//  private[this] val soundNodeMap: Map[Int, SocketAddress] = {
//    if (config.soundSockets.nonEmpty) config.soundSockets else {
//      (Network.soundDotSeq zip Network.soundSocketSeq).toMap
//    }
//  }
//
//  def soundNode(dot: Int): Option[SocketAddress] = soundNodeMap.get(dot)
//
//  override protected val socketSeqCtl: Vec[SocketAddress] =
//    if (config.otherVideoSockets.nonEmpty)
//      config.otherVideoSockets.valuesIterator.toVector
//    else
//      Network.socketSeqCtl
//
//  override val socketToDotMap: Map[SocketAddress, Int] =
//    if (config.otherVideoSockets.nonEmpty)
//      config.otherVideoSockets.map(_.swap)
//    else
//      Network.socketToDotMap

  def oscReceived(p: osc.Packet, sender: SocketAddress): Unit = p match {
    case Network.OscSetVolume(_) => // ignore volume

    case _ =>
      oscFallback(p, sender)
  }

//  def aliveVideos(): Vec[SocketAddress] = filterAlive(otherVideoNodes)
//
//  def queryVideos[A](m: osc.Message, extraDelay: Long = 0L)
//                    (handler: PartialFunction[osc.Packet, A])
//                    (result: InTxn => Try[List[QueryResult[A]]] => Unit)
//                    (implicit tx: InTxn): Unit = {
//    val sq  = aliveVideos()
//    val q   = new Query[A](this, sq, m, result, handler,
//      extraDelay = extraDelay, tx0 = tx)
//    addQuery(q)
//  }

  def queryTxn[A](target: SocketAddress, m: osc.Message, extraDelay: Long = 0L)
                 (handler: PartialFunction[osc.Packet, A])
                 (result: InTxn => Try[QueryResult[A]] => Unit)
                 (implicit tx: InTxn): Unit = {
    val sq  = Vector(target)
    val q   = new Query[A](this, sq, m, tx => seq => result(tx)(seq.map(_.head)), handler,
      extraDelay = extraDelay, tx0 = tx)
    addQuery(q)
  }

//  def sendVideos(m: osc.Message)(implicit tx: InTxn): Unit = {
//    val sq = aliveVideos()
//    Txn.afterCommit { _ =>
//      sq.foreach { target =>
//        transmitter.send(m, target)
//      }
//    }
//  }

  //  init()
}