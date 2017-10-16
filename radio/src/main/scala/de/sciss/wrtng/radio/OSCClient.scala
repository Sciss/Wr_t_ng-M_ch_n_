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

import de.sciss.file._
import de.sciss.osc
import de.sciss.osc.UDP
import de.sciss.wrtng.radio.Main.log

import scala.concurrent.stm.{InTxn, Ref, TMap, atomic}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object OSCClient {
  final val RadioUpdateTimeout = 60000L

  def apply(config: Config, localSocketAddress: InetSocketAddress, source: Source)
           /* (implicit rnd: Random) */: OSCClient = {
    val c                 = UDP.Config()
    c.codec               = Network.oscCodec
    val dot               = Network.resolveDot(config, localSocketAddress)
    c.localSocketAddress  = localSocketAddress
    println(s"OSCClient local socket $localSocketAddress - dot $dot")
    val tx                = UDP.Transmitter(c)
    val rx                = UDP.Receiver(tx.channel, c)
    new OSCClient(config, dot, tx, rx, source)
  }
}
/** Undirected pair of transmitter and receiver, sharing the same datagram channel. */
final class OSCClient(override val config: Config, val dot: Int,
                      val transmitter : UDP.Transmitter .Undirected,
                      val receiver    : UDP.Receiver    .Undirected,
                      val source      : Source
                     )/* (implicit rnd: Random) */
  extends OSCClientLike {

  override def main: Main.type = Main

  private[this] val mapUIDToRadioRecUpdate = TMap.empty[Long, UpdateRadioSource]

  private def disposeRadioUpdate(uid: Long)(implicit tx: InTxn): Unit = {
    val opt = mapUIDToRadioRecUpdate.get(uid)
    opt.foreach(_.disposeTxn())
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  def oscReceived(p: osc.Packet, sender: SocketAddress): Unit = p match {
    case Network.OscRadioRecBegin(uid, dur) =>
      if (source == null) {
        sendNow(Network.OscRadioRecError(uid, "no source"), sender)
      } else {
        val fut = source.acquire(dur = dur)
        fut.onComplete {
          case Success(snippet) =>
            // XXX TODO --- ugly mix of txn and non-txn
            val upd = new UpdateRadioSource(uid = uid, c = this, target = sender, file = snippet.file)
            try {
              atomic { implicit tx =>
                mapUIDToRadioRecUpdate += uid -> upd
                val to = scheduleTxn(OSCClient.RadioUpdateTimeout)(tx => disposeRadioUpdate(uid)(tx))
                upd.timeout = to
              }
              upd.begin()
            } catch {
              case NonFatal(ex) =>
                ex.printStackTrace()
                atomic { implicit tx =>
                  upd.disposeTxn()
                  sendTxn(sender, Network.OscRadioRecError(uid, exceptionToOSC(ex)))
                }
            }

          case Failure(ex) =>
            sendNow(Network.OscRadioRecError(uid, exceptionToOSC(ex)), sender)
        }
      }

    case Network.OscRadioRecGet(uid, off) =>
      val opt = atomic { implicit tx =>
        mapUIDToRadioRecUpdate.get(uid)
      }
      opt.fold[Unit] {
        val message = s"update request for unknown uid $uid"
        println(s"Warning: $message")
        sendNow(Network.OscRadioRecError(uid, message), sender)
      } { u =>
        u.sendNext(off)
      }

    case Network.OscRadioRecError(uid, text) =>
      val base = s"Update error $uid - $text"
      val msg = atomic { implicit tx =>
        val opt = mapUIDToRadioRecUpdate.remove(uid)
        opt.fold(s"$base - no updater found") { u =>
          u.disposeTxn()
          s"$base - instance ${u.target} - file ${u.file}"
        }
      }
      println(msg)

    case Network.OscRadioRecDispose(uid) =>
      atomic { implicit tx =>
        disposeRadioUpdate(uid)
      }

    case Network.OscIterate(_, _) =>
      atomic { implicit tx =>
        resetSoundTimeout()
      }

    case Network.OscSetVolume(_) => // ignore volume

    case osc.Message("/test_rec", id: Int, dur: Float) =>
      if (source == null) {
        sendNow(osc.Message("/error", "/test_rec", id, "no source"), sender)
      } else {
        val fut = source.acquire(dur = dur)
        fut.onComplete {
          case Success(snippet) =>
            sendNow(osc.Message("/done", "/test_rec", id, snippet.file.path, snippet.offset, snippet.numFrames), sender)
          case Failure(ex) =>
            sendNow(osc.Message("/error", "/test_rec", id, exceptionToOSC(ex)), sender)
        }
      }

    case _ =>
      oscFallback(p, sender)
  }

  private[this] var checkAutoStart = config.autoStart

  override protected def aliveUpdated(): Unit =
    if (checkAutoStart)
      atomic { implicit tx =>
        autoStartOne()
      }

  private def autoStartOne()(implicit tx: InTxn): Unit = {
    val aliveSoundNodes = filterAlive(Network.soundSocketSeq)
    if (aliveSoundNodes.nonEmpty && aliveSoundNodes.size >= config.minSoundNodes) {
      val idx = (math.random() * aliveSoundNodes.size).toInt
      val target = aliveSoundNodes(idx)
      log(s"auto-start: $target")
      sendTxn(target, Network.OscIterate(ch = 0, relay = true))
      checkAutoStart = false
    }
    resetSoundTimeout()
  }

  private[this] val timeoutRef = Ref(Option.empty[Task])
  private[this] val timeoutCnt = Ref(0)

  private def resetSoundTimeout()(implicit tx: InTxn): Unit = {
    val task = scheduleTxn(120000L) { implicit tx =>
      log("timeout! we've been waiting too long for a sound to come around and say 'hi'")
      val cnt = timeoutCnt.transformAndGet(_ + 1)
      if (cnt < 3) {
        autoStartOne()
      } else {
        rebootAll()
      }
    }

    timeoutRef.swap(Some(task)).foreach(_.cancel())
  }

  private def rebootAll()(implicit tx: InTxn): Unit = {
    log("ran out of patience; rebooting all sound nodes!")
    Network.soundSocketSeq.foreach { target =>
      sendTxn(target, Network.OscReboot)
    }
    Util.reboot()
  }
}