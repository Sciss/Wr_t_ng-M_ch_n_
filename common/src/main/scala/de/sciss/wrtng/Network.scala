/*
 *  Network.scala
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

import java.io.FileOutputStream
import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer

import de.sciss.file.file
import de.sciss.kollflitz.Vec
import de.sciss.osc

object Network {
  /** Maps MAC addresses to IP addresses */
  final val macToIPMap: Map[String, String] = Map(
    // raspberry pi
    "b8:27:eb:71:d5:56" -> "192.168.0.11",
    "b8:27:eb:55:1b:32" -> "192.168.0.12",
    "b8:27:eb:76:1c:85" -> "192.168.0.13",
    "b8:27:eb:37:83:bc" -> "192.168.0.14",
    "b8:27:eb:42:00:49" -> "192.168.0.15",
    "b8:27:eb:72:d1:70" -> "192.168.0.16",
    "b8:27:eb:d9:a5:b9" -> "192.168.0.17",
    "b8:27:eb:c5:19:a6" -> "192.168.0.18",
    "b8:27:eb:36:2e:72" -> "192.168.0.19",
    "b8:27:eb:36:50:58" -> "192.168.0.22",
    "b8:27:eb:73:29:49" -> "192.168.0.23",
    "b8:27:eb:85:e5:30" -> "192.168.0.24",
    "b8:27:eb:61:90:b9" -> "192.168.0.25"
  )

  final val soundDotSeq: Vec[Int] =
    Vector(14, 15, 17, 18, 19, 25)

  final val radioDot: Int = 23

  final val dotSeqCtl: Vec[Int] = (soundDotSeq :+ radioDot) :+ 77

  final val ClientPort = 57120

  final val KeypadDot = 23

  private def mkSocket(dot: Int): InetSocketAddress = {
    val addr = InetAddress.getByAddress(Array(192.toByte, 168.toByte, 0.toByte, dot.toByte))
    new InetSocketAddress(addr, ClientPort)
  }

  final val soundSocketSeq  : Vec[SocketAddress]   = soundDotSeq .map(mkSocket)
  final val radioSocket     :     SocketAddress    = mkSocket(radioDot)
  final val socketSeqCtl    : Vec[SocketAddress]   = dotSeqCtl   .map(mkSocket)

  final val dotToSocketMap  : Map[Int, SocketAddress] = (dotSeqCtl zip socketSeqCtl).toMap
  final val socketToDotMap  : Map[SocketAddress, Int] = dotToSocketMap.map(_.swap)

  def initConfig(config: ConfigLike, main: MainLike): InetSocketAddress = {
    val res = config.ownSocket.getOrElse {
      val host = Network.thisIP()
      if (!config.isLaptop) {
        Network.compareIP(host)
      }
      new InetSocketAddress(host, Network.ClientPort)
    }
    checkConfig(config)
    if (config.log) main.showLog = true
    res
  }

  def resolveDot(config: ConfigLike, localSocketAddress: InetSocketAddress): Int = {
    if (config.dot >= 0) config.dot else {
      val dot0 = Network.socketToDotMap.getOrElse(localSocketAddress, -1)
      val res = if (dot0 >= 0) dot0 else {
        localSocketAddress.getAddress.getAddress.last.toInt
      }
      if (dot0 < 0) println(s"Warning - could not determine 'dot' for host $localSocketAddress - assuming $res")
      res
    }
  }

  def checkConfig(config: ConfigLike): Unit = {
    if (config.disableEnergySaving && !config.isLaptop) {
      import sys.process._
      Seq("xset", "s", "off").!
      Seq("xset", "-dpms").!
    }
  }

  def thisIP(): String = {
    import sys.process._
    // cf. https://unix.stackexchange.com/questions/384135/
    val ifConfig    = Seq("ip", "a", "show", "eth0").!!
    val ifConfigPat = "inet "
    val line        = ifConfig.split("\n").map(_.trim).find(_.startsWith(ifConfigPat)).getOrElse("")
    val i0          = line.indexOf(ifConfigPat)
    val i1          = if (i0 < 0) 0 else i0 + ifConfigPat.length
    val i2          = line.indexOf("/", i1)
    if (i0 < 0 || i2 < 0) {
      val local = InetAddress.getLocalHost.getHostAddress
      Console.err.println(s"No assigned IP4 found in eth0! Falling back to $local")
      local
    } else {
      line.substring(i1, i2)
    }
  }

  /** Verifies IP according to `ipMap` and
    * MAC address. If IP doesn't match, tries
    * to edit `/etc/dhcpcd.conf` and reboot.
    * This way, we can clone the Raspberry Pi
    * image, and each machine can configure
    * itself from the identical clone.
    */
  def compareIP(host: String): Unit = {
    import sys.process._
    val macAddress  = Seq("cat", "/sys/class/net/eth0/address").!!.trim
    macToIPMap.get(macAddress).fold[String] {
      Console.err.println(s"Unknown MAC address: $macAddress - not trying to match IP.")
      host
    } { desiredIP =>
      println(s"This computer has MAC address $macAddress and IP $host")
      if (desiredIP != host && host != "127.0.0.1" && host != "127.0.1.1") {
        val confPath = "/etc/dhcpcd.conf"
        println(s"Designated IP is $desiredIP. Updating /etc/dhcpcd.conf...")
        val header = "interface eth0"
        Seq("cp", confPath, s"$confPath.BAK").!
        val src   = scala.io.Source.fromFile(file(confPath))
        val init  = try {
          src.getLines().toList
            .takeWhile(ln => ln.trim() != header).mkString("\n")
        } finally {
          src.close()
        }
        val tail =
          s"""$header
             |
             |static ip_address=$desiredIP/24
             |static routers=192.168.0.1
             |static domain_name_servers=192.168.0.1
             |""".stripMargin
        val contents = s"$init\n$tail"

        val fOut = new FileOutputStream(confPath)
        fOut.write(contents.getBytes("UTF-8"))
        fOut.close()
        println("Rebooting...")
        Seq("sudo", "reboot", "now").!
      }
      host
    }
  }

  final val oscCodec: osc.PacketCodec =
    osc.PacketCodec().doublePrecision().booleans().packetsAsBlobs()

  final val OscQueryVersion: osc.Message =
    osc.Message("/query", "version")

  object OscReplyVersion {
    private[this] val Name = "/info"
    private[this] val Tpe  = "version"

    def apply(s: String): osc.Message = osc.Message(Name, Tpe, s)

    def unapply(p: osc.Packet): Option[String] = p match {
      case osc.Message(Name, Tpe, s: String) => Some(s)
      case _ => None
    }
  }

  object OscUpdateInit {
    private[this] val Name = "/update-init"

    def apply(uid: Long, size: Long): osc.Message = osc.Message(Name, uid, size)

    def unapply(p: osc.Packet): Option[(Long, Long)] = p match {
      case osc.Message(Name, uid: Long, size: Long) => Some((uid, size))
      case _ => None
    }
  }

  object OscUpdateGet {
    private[this] val Name = "/update-get"

    def apply(uid: Long, offset: Long): osc.Message = osc.Message(Name, uid, offset)

    def unapply(p: osc.Packet): Option[(Long, Long)] = p match {
      case osc.Message(Name, uid: Long, offset: Long) => Some((uid, offset))
      case _ => None
    }
  }

  object OscUpdateSet {
    private[this] val Name = "/update-set"

    def apply(uid: Long, offset: Long, bytes: ByteBuffer): osc.Message =
      osc.Message(Name, uid,offset, bytes)

    def unapply(p: osc.Packet): Option[(Long, Long, ByteBuffer)] = p match {
      case osc.Message(Name, uid: Long, offset: Long, bytes: ByteBuffer) => Some((uid, offset, bytes))
      case _ => None
    }
  }

  object OscUpdateError {
    private[this] val Name = "/error"
    private[this] val Tpe  = "update"

    def apply(uid: Long, s: String): osc.Message = osc.Message(Name, Tpe, uid, s)

    def unapply(p: osc.Packet): Option[(Long, String)] = p match {
      case osc.Message(Name, Tpe, uid: Long, s: String) => Some((uid, s))
      case _ => None
    }
  }

  object OscUpdateSuccess {
    private[this] val Name = "/update-done"

    def apply(uid: Long): osc.Message =
      osc.Message(Name, uid)

    def unapply(p: osc.Packet): Option[Long] = p match {
      case osc.Message(Name, uid: Long) => Some(uid)
      case _ => None
    }
  }

  object OscSetVolume {
    private[this] val Name = "/set-volume"

    def apply(amp: Float): osc.Message = osc.Message(Name, amp)

    def unapply(p: osc.Packet): Option[Float] = p match {
      case osc.Message(Name, amp: Float) => Some(amp)
      case _ => None
    }
  }

  object OscShell {
    private[this] val Name = "/shell"

    def apply(cmd: Seq[String]): osc.Message = osc.Message(Name, cmd: _*)

    def unapply(p: osc.Packet): Option[Seq[String]] = p match {
      case osc.Message(Name, cmd @ _*) => Some(cmd.map(_.toString))
      case _ => None
    }
  }

  object OscLogEnable {
    private[this] val Name = "/log-enable"

    def apply(onOff: Boolean): osc.Message = osc.Message(Name, onOff)

    def unapply(p: osc.Packet): Option[Boolean] = p match {
      case osc.Message(Name, onOff: Boolean) => Some(onOff)
      case _ => None
    }
  }

  object OscLog {
    private[this] val Name = "/log"

    def apply(text: String): osc.Message = osc.Message(Name, text)

    def unapply(p: osc.Packet): Option[String] = p match {
      case osc.Message(Name, text: String) => Some(text)
      case _ => None
    }
  }

  final val OscShutdown : osc.Message = osc.Message("/shutdown" )
  final val OscReboot   : osc.Message = osc.Message("/reboot"   )
  final val OscHeart    : osc.Message = osc.Message("/heart"    )

  final val oscDumpFilter: osc.Dump.Filter = { p =>
    p.encodedSize(oscCodec) < 1024
  }

  final val TimeOutSeconds: Float = 2.0f
  final val TimeOutMillis : Long  = (TimeOutSeconds * 1000).toLong

  final val HeartPeriodSeconds: Float = 15f
  final val DeathPeriodSeconds: Float = HeartPeriodSeconds * 2.5f

  final val DeathPeriodMillis: Long = (DeathPeriodSeconds * 1000).toLong

  ////////////////////
  // piece-specific //
  ////////////////////

  /** Sent from sound to radio to initiate recording */
  object OscRadioRecBegin {
    private[this] val Name = "/radio-rec-begin"

    def apply(uid: Long, dur: Float) =
      osc.Message(Name, uid, dur)

    def unapply(m: osc.Message): Option[(Long, Float)] = m match {
      case osc.Message(Name, uid: Long, dur: Float) =>
        Some((uid, dur))
      case _ => None
    }
  }

  /** Sent from radio to sound to signalise recording is done */
  object OscRadioRecDone {
    private[this] val Name = "/radio-rec-done"

    def apply(uid: Long, size: Long): osc.Message = osc.Message(Name, uid, size)

    def unapply(p: osc.Packet): Option[(Long, Long)] = p match {
      case osc.Message(Name, uid: Long, size: Long) => Some((uid, size))
      case _ => None
    }
  }

  /** Sent from sound to radio to query next recording bit */
  object OscRadioRecGet {
    private[this] val Name = "/radio-rec-get"

    def apply(uid: Long, offset: Long): osc.Message = osc.Message(Name, uid, offset)

    def unapply(p: osc.Packet): Option[(Long, Long)] = p match {
      case osc.Message(Name, uid: Long, offset: Long) => Some((uid, offset))
      case _ => None
    }
  }

  /** Sent from radio to sound to transmit next recording bit */
  object OscRadioRecSet {
    private[this] val Name = "/radio-rec-set"

    def apply(uid: Long, offset: Long, bytes: ByteBuffer): osc.Message =
      osc.Message(Name, uid,offset, bytes)

    def unapply(p: osc.Packet): Option[(Long, Long, ByteBuffer)] = p match {
      case osc.Message(Name, uid: Long, offset: Long, bytes: ByteBuffer) => Some((uid, offset, bytes))
      case _ => None
    }
  }

  object OscRadioRecError {
    private[this] val Name = "/radio-rec-error"

    def apply(uid: Long, s: String): osc.Message = osc.Message(Name, uid, s)

    def unapply(p: osc.Packet): Option[(Long, String)] = p match {
      case osc.Message(Name, uid: Long, s: String) => Some((uid, s))
      case _ => None
    }
  }

  /** Sent from sound to radio to signalise transmission complete, file can be discarded */
  object OscRadioRecDispose {
    private[this] val Name = "/radio-rec-dispose"

    def apply(uid: Long): osc.Message =
      osc.Message(Name, uid)

    def unapply(p: osc.Packet): Option[Long] = p match {
      case osc.Message(Name, uid: Long) => Some(uid)
      case _ => None
    }
  }

  //////

  // final val OscIterate : osc.Message = osc.Message("/iterate")

  object OscIterate {
    private[this] val _Name = "/iterate"

    def Name: String = _Name

    def apply(ch: Int, relay: Boolean): osc.Message = osc.Message(_Name, ch, relay)

    def unapply(p: osc.Packet): Option[(Int, Boolean)] = p match {
      case osc.Message(`_Name`, ch: Int, relay: Boolean) => Some((ch, relay))
      case _ => None
    }
  }
}