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
package sound

import java.net.{InetSocketAddress, SocketAddress}

import de.sciss.osc
import de.sciss.osc.UDP

import scala.util.control.NonFatal

object OSCClient {
  def apply(config: Config, localSocketAddress: InetSocketAddress): OSCClient = {
    val c                 = UDP.Config()
    c.codec               = Network.oscCodec
    val dot               = Network.resolveDot(config, localSocketAddress)
    c.localSocketAddress  = localSocketAddress
    c.bufferSize          = 32768   // only higher for sending SynthDefs
    println(s"OSCClient local socket $localSocketAddress - dot $dot")
    val tx                = UDP.Transmitter(c)
    val rx                = UDP.Receiver(tx.channel, c)
    new OSCClient(config, dot, tx, rx)
  }

}
/** Undirected pair of transmitter and receiver, sharing the same datagram channel. */
final class OSCClient(override val config: Config, val dot: Int, val transmitter: UDP.Transmitter.Undirected,
                      val receiver: UDP.Receiver.Undirected) extends OSCClientLike {

//  val relay: RelayPins  = RelayPins.map(dot)
  val scene: SoundScene = new SoundScene(this)

  override def main: Main.type = Main

  def oscReceived(p: osc.Packet, sender: SocketAddress): Unit = p match {
    case Network.OscSetVolume(amp) =>
      scene.setMasterVolume(amp)

//    case Network.OscAmpChanVolume(amps) =>
//      val arr = scene.chanAmps.volumes
//      amps.iterator.take(arr.length).zipWithIndex.foreach { case (value, idx) =>
//        arr(idx) = value
//      }
//
//    case Network.OscSaveAmpChan() =>
//      scene.chanAmps.save()

    case osc.Message("/server-info") =>
      try {
        val info = scene.serverInfo()
        transmitter.send(osc.Message("/done", "server-info", info), sender)
      } catch {
        case NonFatal(ex) =>
          transmitter.send(osc.Message("/fail", "server-info", ex.toString), sender)
      }

    case osc.Message("/test-channel", ch: Int, sound: Int, rest @ _*) =>
      try {
//        relay.selectChannel(ch)
        val ok = (sound >= 0) && scene.testSound(ch /* / 6 */, tpe = sound, rest = rest)
        transmitter.send(osc.Message("/done", "test-channel", ch, ok), sender)
      } catch {
        case NonFatal(ex) =>
          val msg = Util.formatException(ex)
          transmitter.send(osc.Message("/fail", "test-channel", ch, msg), sender)
      }

    case _ =>
      oscFallback(p, sender)
  }

  override def init(): this.type = {
    super.init()
    scene.run()
    this
  }
}