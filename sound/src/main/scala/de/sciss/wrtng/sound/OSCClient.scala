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

import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.lucre.synth.Txn
import de.sciss.osc
import de.sciss.osc.UDP
import de.sciss.synth.io.AudioFile
import de.sciss.wrtng.sound.Main.log

import scala.concurrent.stm.{InTxn, Ref, Txn => STxn}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

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
    val radioSocket       = config.radioSocket.getOrElse(Network.radioSocket)
    new OSCClient(config, dot, tx, rx, radioSocket = radioSocket)
  }

//  private val DummyDoneFun: File => Unit = _ => ()
}
/** Undirected pair of transmitter and receiver, sharing the same datagram channel. */
final class OSCClient(override val config: Config, val dot: Int, val transmitter: UDP.Transmitter.Undirected,
                      val receiver: UDP.Receiver.Undirected,
                      val radioSocket: SocketAddress)
  extends OSCClientLike {

  val scene     : SoundScene  = new SoundScene(this)
  val algorithm1: Algorithm   = Algorithm(this, channel = 0)
  val algorithm2: Algorithm   = Algorithm(this, channel = 1)

  private[this] val algorithms = Array(algorithm1, algorithm2)

  override def main: Main.type = Main

//  private[this] var radioUpdateUID  = -1L
//  private[this] var radioUpdateDone: File => Unit = OSCClient.DummyDoneFun

//  private[this] val radioUpdater    = Ref(Option.empty[RadioUpdater])
  private[this] val radioUpdateTgt = Ref(Option.empty[UpdateRadioTarget])

  def queryRadio[A](m: osc.Message, extraDelay: Long = 0L)
                   (handler: PartialFunction[osc.Packet, A])
                   (result: InTxn => Try[QueryResult[A]] => Unit)
                   (implicit tx: InTxn): Unit = {
    val sq  = Vector(radioSocket)
    val q   = new Query[A](this, sq, m, tx => seq => result(tx)(seq.map(_.head)), handler,
      extraDelay = extraDelay, tx0 = tx)
    addQuery(q)
  }

  def oscReceived(p: osc.Packet, sender: SocketAddress): Unit = try {
    tryOscReceived(p, sender)
  } catch {
    case ex: Throwable =>
      log(s"oscReceived - exception: ${exceptionToOSC(ex)}")
      Thread.sleep(2000)
      // try another time
      try {
        tryOscReceived(p, sender)
      } catch {
        case _: Throwable =>
          log("Retry didn't help. Forcing reboot.")
          Util.reboot()
      }
  }

  private def tryOscReceived(p: osc.Packet, sender: SocketAddress): Unit = p match {
    case Network.OscRadioRecSet(uid, off, bytes) =>
      radioUpdateTgt.single.get.fold[Unit] {
        transmitter.send(Network.OscRadioRecError(uid, "missing /update-init"), sender)
      } { u =>
        if (u.uid === uid) {
          if (u.sender != sender) {
            sendNow(Network.OscRadioRecError(uid, "changed sender"), sender)
          } else {
            u.write(off, bytes)
          }
        } else {
          sendNow(Network.OscRadioRecError(uid, s"no updater for uid $uid"), sender)
        }
      }

    case Network.OscIterate(ch, relay) =>
      iterate(ch = ch, relay = relay, sender = sender)

    case Network.OscSetVolume(amp) =>
      scene.setMasterVolume(amp)

    case osc.Message("/server-info") =>
      try {
        val info = scene.serverInfo()
        sendNow(osc.Message("/done", "server-info", info), sender)
      } catch {
        case NonFatal(ex) =>
          sendNow(osc.Message("/fail", "server-info", exceptionToOSC(ex)), sender)
      }

    case osc.Message("/test-channel", ch: Int, sound: Int, rest @ _*) =>
      try {
//        relay.selectChannel(ch)
        val ok = (sound >= 0) && scene.testSound(ch /* / 6 */, tpe = sound, rest = rest)
        sendNow(osc.Message("/done", "test-channel", ch, ok), sender)
      } catch {
        case NonFatal(ex) =>
          sendNow(osc.Message("/fail", "test-channel", ch, exceptionToOSC(ex)), sender)
      }

    case osc.Message("/test_rec", id: Int, dur: Float) =>
      atomic { implicit tx =>
        val fut = queryRadioRec(dur)
        fut.onComplete {
          case Success(f) =>
            import sys.process._
            Seq("cp", f.f.path, (userHome / "Music" / "test.wav").path).!
            sendNow(osc.Message("/done", "test_rec", id), sender)

          case Failure(ex) =>
            sendNow(osc.Message("/error", "test_rec", id, exceptionToOSC(ex)), sender)
        }
      }

    case _ =>
      oscFallback(p, sender)
  }

  def iterate(ch: Int, relay: Boolean, sender: SocketAddress): Unit = {
    try {
      val algorithm = algorithms(ch)
      val fut = atomic { itx =>
        implicit val tx: Txn = Txn.wrap(itx)
        algorithm.playLogic(relay = relay)
      }
      fut.onComplete {
        case Success(_)   =>
          sendNow(osc.Message("/done" , Network.OscIterate.Name), sender)
        case Failure(ex)  =>
          log(s"iterate($ch) failed: ${exceptionToOSC(ex)}")
          sendNow(osc.Message("/error", Network.OscIterate.Name, exceptionToOSC(ex)), sender)
      }

    } catch {
      case NonFatal(ex) =>
        ex.printStackTrace()
        log(s"iterate($ch) ERROR: ${exceptionToOSC(ex)}")
        sendNow(osc.Message("/error", Network.OscIterate.Name, exceptionToOSC(ex)), sender)
    }
  }

  def relayIterate(thisCh: Int): Unit = {
    val nextCh = 1 - thisCh
    val target = if (thisCh == 0) {
      log(s"relayIterate($thisCh) -> $nextCh (self)")
      transmitter.localSocketAddress
    } else {
      val targets = filterAlive(Network.soundSocketSeq)
      log(s"relayIterate($thisCh) -> ${targets.size} candidates")
      if (targets.isEmpty) {
        log(s"relayIterate($thisCh) -> $nextCh (self)")
        transmitter.localSocketAddress
      }
      else {
        val dots    = targets.map(Network.socketToDotMap.getOrElse(_, -1))
        val myIdx   = dots.indexOf(dot)
        val nextIdx = (myIdx + 1) % targets.size
        val res     = targets(nextIdx)
        log(s"relayIterate($thisCh) -> $nextCh ($res)")
        res
      }

//        .sortBy { addr =>
//        Network.socketToDotMap.get(addr).map(Network.soundDotSeq.indexOf)
//      }
    }

    val m = Network.OscIterate(ch = nextCh, relay = true)
    sendNow(m, target)
    sendNow(m, radioSocket)   // signalise algorithm is up and healthy
  }

  def queryRadioRec(dur: Float)(implicit tx: InTxn): Future[AudioFileRef] = {
    val Uid = mkTxnId()
    val p   = Promise[AudioFileRef]()
    queryRadio[Long](Network.OscRadioRecBegin(uid = Uid, dur = dur), extraDelay = ((dur + 60) * 1000).toLong) {
      case Network.OscRadioRecDone(Uid, size) => size
    } { implicit tx => {
      case Success(QueryResult(_, size: Long)) =>
        val u = new UpdateRadioTarget(Uid, this, radioSocket, size, { f =>
          p.complete {
            Try {
              val spec = AudioFile.readSpec(f)
              new AudioFileRef(f, spec.numFrames)
            }
          }
        })
        radioUpdateTgt.swap(Some(u)).foreach(_.dispose())
        STxn.afterCommit(_ => u.begin())

      case Failure(ex) => p.failure(ex)
    }}

    p.future
  }

  override def init(): this.type = {
    super     .init()
    algorithm1.init()
    algorithm2.init()
    scene     .run()
    this
  }
}