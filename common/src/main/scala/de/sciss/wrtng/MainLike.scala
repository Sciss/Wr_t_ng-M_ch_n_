/*
 *  MainLike.scala
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

import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import scala.annotation.elidable
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

trait MainLike {
  protected def pkgLast: String

  private def buildInfString(key: String): String = try {
    val clazz = Class.forName(s"de.sciss.wrtng.$pkgLast.BuildInfo")
    val m     = clazz.getMethod(key)
    m.invoke(null).toString
  } catch {
    case NonFatal(_) => "?"
  }

  final def name        : String = "Wr_t_ng M_ch_n_"
  final def namePkg     : String = s"$name ${pkgLast.capitalize}"

  final def version     : String = buildInfString("version")
  final def builtAt     : String = buildInfString("builtAtString")
  final def fullVersion : String = s"$pkgLast v$version, built $builtAt"

  final protected def parseSocket(s: String): Either[String, InetSocketAddress] = {
    val arr = s.split(':')
    if (arr.length != 2) Left(s"Must be of format <host>:<port>")
    else parseSocket(arr)
  }

  final protected def parseSocketDot(s: String): Either[String, (InetSocketAddress, Int)] = {
    val arr = s.split(':')
    if (arr.length != 3) Left(s"Must be of format <host>:<port>:<dot>")
    else {
      val dotS = arr(2)
      Try(dotS.toInt) match {
        case Success(dot) =>
          parseSocket(arr).map(socket => (socket,dot))
        case Failure(_) => Left(s"Invalid dot: $dotS - must be an integer")
      }
    }
  }

  private def parseSocket(arr: Array[String]): Either[String, InetSocketAddress] = {
    val host = arr(0)
    val port = arr(1)
    Try(new InetSocketAddress(host, port.toInt)) match {
      case Success(addr)  => Right(addr)
      case Failure(ex)    => Left(s"Invalid socket address: $host:$port - ${ex.getClass.getSimpleName}")
    }
  }

  final protected def validateSockets(vs: Seq[String], useDot: Boolean): Either[String, Unit] =
    ((Right(()): Either[String, Unit]) /: vs) { case (e, v) =>
      e.flatMap { _ =>
        val eth = if (useDot) parseSocketDot(v)
        else        parseSocket   (v)
        eth.map(_ => ()) }
    }

  final var showLog = false

  private[this] lazy val logHeader = new SimpleDateFormat(s"[HH:mm''ss.SSS] '$pkgLast' - ", Locale.US)

  @elidable(elidable.CONFIG) def log(what: => String): Unit =
    if (showLog) println(logHeader.format(new Date()) + what)
}
