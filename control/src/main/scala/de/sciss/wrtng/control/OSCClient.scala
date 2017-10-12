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
package control

import java.net.{InetSocketAddress, SocketAddress}

import de.sciss.file.File
import de.sciss.model.impl.ModelImpl
import de.sciss.osc
import de.sciss.osc.UDP

import scala.collection.immutable.{Seq => ISeq}

object OSCClient {
  final val Port = 57110

  def apply(config: Config, host: String): OSCClient = {
    val c                 = UDP.Config()
    c.codec               = Network.oscCodec
    val localSocket       = new InetSocketAddress(host, Port)
    c.localSocketAddress  = localSocket
    c.bufferSize          = 32768   // only higher for sending SynthDefs
    println(s"OSCClient local socket $localSocket")
    val tx                = UDP.Transmitter(c)
    val rx                = UDP.Receiver(tx.channel, c)
    new OSCClient(config, tx, rx)
  }

  sealed trait Update
  final case class Added  (status: Status) extends Update
  final case class Removed(status: Status) extends Update
  final case class Changed(status: Status) extends Update
}
/** Undirected pair of transmitter and receiver, sharing the same datagram channel. */
final class OSCClient(config: Config, val tx: UDP.Transmitter.Undirected, val rx: UDP.Receiver.Undirected)
  extends ModelImpl[OSCClient.Update] {

  private[this] val sync        = new AnyRef
  private[this] var _instances  = Vector.empty[Status]

  def instances: Vector[Status] = sync.synchronized(_instances)

  /** Sends to all possible targets, including laptop itself. */
  def ! (p: osc.Packet): Unit =
    Network.socketSeqCtl.foreach { target =>
      tx.send(p, target)
    }

  def dumpOSC(): Unit = {
    tx.dump(filter = Network.oscDumpFilter)
    rx.dump(filter = Network.oscDumpFilter)
  }

  def beginUpdates(debFile: File, instances: ISeq[Status]): Unit = mapUIDToUpdaterSync.synchronized {
    val map0 = mapUIDToUpdater
    val map2 = instances.foldLeft(map0) { (mapT, status) =>
      val uid = Util.nextUniqueID()
      val u   = new UpdateSource(uid, config, this, status, debFile)
      u.begin()
      mapT + (uid -> u)
    }
    mapUIDToUpdater = map2
  }

  private[this] val mapUIDToUpdaterSync = new AnyRef
  private[this] var mapUIDToUpdater     = Map.empty[Int, UpdateSource]

  def getDot(sender: SocketAddress): Int = sender match {
    case in: InetSocketAddress =>
      val arr = in.getAddress.getAddress
      if (arr.length == 4) arr(3) else -1
    case _ => -1
  }

  def oscReceived(p: osc.Packet, sender: SocketAddress): Unit = p match {
    case Network.OscUpdateGet(uid, off) =>
      mapUIDToUpdaterSync.synchronized {
        mapUIDToUpdater.get(uid).fold[Unit] {
          println(s"Warning: update request for unknown uid $uid")
        } { u =>
          u.sendNext(off)
          val statusOld = u.instance
          val dot       = statusOld.dot
          val idx       = _instances.indexWhere(_.dot == dot)
          if (idx >= 0) {
            val progress  = off.toDouble / u.size
            val statusNew = statusOld.copyWithUpdate(progress)
            _instances    = _instances.updated(idx, statusNew)
            dispatch(OSCClient.Changed(statusNew))
          }
        }
      }

    case Network.OscUpdateError(uid, text) =>
      val base = s"Update error $uid - $text"
      val msg = mapUIDToUpdaterSync.synchronized {
        mapUIDToUpdater.get(uid).fold(s"$base - no updater found") { u =>
          mapUIDToUpdater -= uid
          u.dispose()
          s"$base - instance ${u.instance.dot} - file ${u.debFile}"
        }
      }
      println(msg)

    case Network.OscUpdateSuccess(uid) =>
      mapUIDToUpdaterSync.synchronized {
        mapUIDToUpdater.get(uid).foreach { u =>
          mapUIDToUpdater -= uid
          u.dispose()
          val statusOld = u.instance
          val dot       = statusOld.dot
          val idx       = _instances.indexWhere(_.dot == dot)
          if (idx >= 0) {
            val statusNew = statusOld.copyWithUpdate(1.0)
            _instances    = _instances.updated(idx, statusNew)
            dispatch(OSCClient.Changed(statusNew))
          }
        }
      }

    case Network.OscReplyVersion(v) =>
      val dot = getDot(sender)
      if (dot >= 0) {
        val idx = _instances.indexWhere(_.dot == dot)
        if (idx < 0) {
          val status = Status(dot)(version = v)()
          _instances :+= status
          dispatch(OSCClient.Added(status))
        } else {
          val statusOld = _instances(idx)
          if (statusOld.version != v) {
            val statusNew = statusOld.copyWithVersion(v)
            _instances = _instances.updated(idx, statusNew)
            dispatch(OSCClient.Changed(statusNew))
          }
        }
      }

    case Network.OscHeart =>

    case osc.Message("/done", "test-channel", _, success: Boolean) =>
      if (!success) println("Failed to test channel!")

    case _ =>
      Console.err.println(s"Ignoring unknown OSC packet $p")
    // N.B.: Do _not_ reply, because we may create an infinite growing loop
    // tx.send(osc.Message("/error", "unknown packet", p), sender)
  }

  rx.action = oscReceived
  if (config.dumpOSC) dumpOSC()
  tx.connect()
  rx.connect()
}