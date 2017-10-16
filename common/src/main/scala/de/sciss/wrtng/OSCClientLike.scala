/*
 *  OSCClientLike.scala
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

import de.sciss.equal.Implicits._
import de.sciss.kollflitz.Vec
import de.sciss.osc
import de.sciss.osc.UDP

import scala.concurrent.stm.{InTxn, Ref, Txn}
import scala.util.Try
import scala.util.control.NonFatal

abstract class OSCClientLike {
  // ---- abstract ----

  def transmitter : UDP.Transmitter .Undirected
  def receiver    : UDP.Receiver    .Undirected

  def config: ConfigLike
  def main  : MainLike
  def dot   : Int

  protected def oscReceived(p: osc.Packet, sender: SocketAddress): Unit

  // ---- impl ----

  private[this] var updater = Option.empty[UpdateDebTarget]

  final val timer = new java.util.Timer("osc-timer")

  private[this] val queries = Ref(List.empty[Query[_]])

  @volatile
  private[this] var alive = Map.empty[SocketAddress, Long]

  final def filterAlive(in: Vec[SocketAddress]): Vec[SocketAddress] = {
    val deadline = System.currentTimeMillis() - 30000L
    in.filter(alive.getOrElse(_, 0L) > deadline)
  }

  final def scheduleTxn(delay: Long)(body: InTxn => Unit)(implicit tx: InTxn): Task =
    Task(timer, delay)(body)

  final def removeQuery[A](q: Query[A])(implicit tx: InTxn): Unit =
    queries.transform(_.filterNot(_ == /* ===  */ q))

  protected final def addQuery[A](q: Query[A])(implicit tx: InTxn): Unit =
    queries.transform(q :: _)

  def queryTxn[A](target: SocketAddress, m: osc.Message, extraDelay: Long = 0L)
                 (handler: PartialFunction[osc.Packet, A])
                 (result: InTxn => Try[QueryResult[A]] => Unit)
                 (implicit tx: InTxn): Unit = {
    val sq  = Vector(target)
    val q   = new Query[A](this, sq, m, tx => seq => result(tx)(seq.map(_.head)), handler,
      extraDelay = extraDelay, tx0 = tx)
    addQuery(q)
  }

  final protected def oscFallback(p: osc.Packet, sender: SocketAddress): Unit = {
    val wasHandled = Util.atomic(main) { implicit tx =>
      val qsIn      = queries()
      val qOpt      = qsIn.find(_.handle(sender, p))
      val _handled  = qOpt.isDefined
      // println(s"for $p looked through ${qsIn.size} queries - handled? ${_handled}")
      qOpt.foreach(removeQuery(_))
      _handled
    }

    if (!wasHandled) p match {
      case Network.OscHeart =>
        val now   = System.currentTimeMillis()
        val death = now + Network.DeathPeriodMillis
        alive = alive.filter(_._2 < death) + (sender -> now)

      case Network.OscUpdateSet (uid, off, bytes) =>
        updater.fold[Unit] {
          transmitter.send(Network.OscUpdateError(uid, "missing /update-init"), sender)
        } { u =>
          if (u.uid === uid) {
            if (u.sender != sender) {
              transmitter.send(Network.OscUpdateError(uid, "changed sender"), sender)
            } else {
              u.write(off, bytes)
            }
          } else {
            transmitter.send(Network.OscUpdateError(uid, s"no updater for uid $uid"), sender)
          }
        }

      case Network.OscUpdateInit(uid, size) =>
        val u = new UpdateDebTarget(uid, this, sender, size)
        updater.foreach(_.dispose())
        updater = Some(u)
        u.begin()

      case Network.OscShell(cmd) =>
        println("Executing shell command:")
        println(cmd.mkString(" "))
        import sys.process._
        val result = Try(cmd.!!).toOption.getOrElse("ERROR")
        transmitter.send(osc.Message("/shell_reply", result), sender)

      case Network.OscShutdown =>
        if (config.isLaptop)
          println("(laptop) ignoring /shutdown")
        else
          Util.shutdown()

      case Network.OscReboot =>
        if (config.isLaptop)
          println("(laptop) ignoring /reboot")
        else
          Util.reboot()

      case Network.OscQueryVersion =>
        transmitter.send(Network.OscReplyVersion(main.fullVersion), sender)

      case Network.OscLogEnable(onOff) =>
        main.showLog = onOff
        main.remoteLogging(if (onOff) Some(sendRemoteLog(sender, _)) else None)

      case osc.Message("/error"        , _ @ _*) =>
      case osc.Message("/inject-abort" , _ @ _*) =>
      case osc.Message("/inject-commit", _ @ _*) =>

      case _ =>
        Console.err.println(s"Ignoring unknown OSC $p")
        val args = p match {
          case m: osc.Message => m.name +: m.args
          case _: osc.Bundle  => "osc.Bundle" :: Nil
        }
        transmitter.send(osc.Message("/error", "unknown packet" +: args: _*), sender)
    }
  }

  private def sendRemoteLog(target: SocketAddress, text: String): Unit =
    sendNow(Network.OscLog(text), target)

  /** Sub-classes may override this */
  protected def socketSeqCtl: Vec[SocketAddress] = Network.socketSeqCtl

  /** Sub-classes may override this */
  def socketToDotMap: Map[SocketAddress, Int] = Network.socketToDotMap

  final def sendNow(p: osc.Packet, target: SocketAddress): Unit =
    try {
      transmitter.send(p, target)
    } catch {
      case NonFatal(ex) =>
        Console.err.println(s"Failed to send ${p.name} to $target - ${ex.getClass.getSimpleName}")
    }

  /** Sends to all possible targets. */
  final def ! (p: osc.Packet): Unit =
    socketSeqCtl.foreach { target =>
      sendNow(p, target)
    }

  final def sendTxn(target: SocketAddress, p: osc.Packet)(implicit tx: InTxn): Unit =
    Txn.afterCommit { _ =>
      sendNow(p, target)
    }

  final def dumpOSC(onOff: Boolean): Unit = {
    val mode = if (onOff) osc.Dump.Text else osc.Dump.Off
    transmitter.dump(mode, filter = Network.oscDumpFilter)
    receiver   .dump(mode, filter = Network.oscDumpFilter)
  }

  def init(): this.type = {
    receiver.action = oscReceived
    if (config.dumpOSC) dumpOSC(onOff = true)
    transmitter .connect()
    receiver    .connect()
    this
  }

  private[this] val txnCount = Ref(0)

  final def mkTxnId()(implicit tx: InTxn): Long = {
    val i = txnCount.getAndTransform(_ + 1)
    (i.toLong * 1000) + dot
  }

  final def exceptionToOSC(ex: Throwable): String = {
    val s = Util.formatException(ex)
    if (s.length < 512) s else s.substring(0, 512)
  }
}