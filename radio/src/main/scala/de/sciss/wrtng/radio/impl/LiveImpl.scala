package de.sciss.wrtng
package radio
package impl

import java.io.{BufferedReader, InputStreamReader, PrintStream}
import java.net.Socket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

final class LiveImpl(config: Config) extends Live {
  /*

   Gqrx 2.6 remote control (TCP telnet-style) commands:

  f - Get frequency [Hz]
  F - Set frequency [Hz]
  m - Get demodulator mode
  M - Set demodulator mode (OFF, RAW, AM, FM, WFM, WFM_ST,
      WFM_ST_OIRT, LSB, USB, CW, CWL, CWU)
  l STRENGTH - Get signal strength [dBFS]
  l SQL - Get squelch threshold [dBFS]
  L SQL <sql> - Set squelch threshold to <sql> [dBFS]
  u RECORD - Get status of audio recorder
  U RECORD <status> - Set status of audio recorder to <status = 0|1>
  c - Close connection
  AOS - Acquisition of signal (AOS) event, start audio recording
  LOS - Loss of signal (LOS) event, stop audio recording
  \dump_state - Dump state (only usable for compatibility)

  Gqrx replies with:

  RPRT 0 - Command successful
  RPRT 1 - Command failed

  */

  private final case object Init
  private final case class Acquire(dur: Double, p: Promise[RadioSnippet])

  private final class Telnet {
    private[this] val socket  = new Socket("127.0.0.1", config.gqrxTCPPort)
    private[this] val out     = new PrintStream(socket.getOutputStream)
    private[this] val in      = new BufferedReader(new InputStreamReader(socket.getInputStream))

    def send[A](command: String)(response: PartialFunction[String, A]): Try[A] = {
      out.println(command)
      ???
    }
  }

  private final val CMD_START_RECORD  = "U RECORD 1"
  private final val CMD_STOP_RECORD   = "U RECORD 0"
  private final val SUCCESS           = "RPRT 0"
  private final val FAILURE           = "RPRT 1"

  private final class MyActor extends Actor {
    import context.become

    def receive: Actor.Receive = {
      case Init =>
        def connect(): Telnet = {
          var res: Telnet = null
          while (res == null) {
            try {
              res = new Telnet
            } catch {
              case NonFatal(ex) =>
                ex.printStackTrace()
                Thread.sleep(1000)
            }
          }
          res
        }

        val remote = connect()
        become {
          case Acquire(dur, p) =>
            val startRes = remote.send(CMD_START_RECORD) {
              case SUCCESS  => true
              case FAILURE  => false
            }
            ???
        }
    }
  }

  private[this] val system          = ActorSystem("radio-acquire")
  private[this] val actor: ActorRef = system.actorOf(Props[MyActor])

  def init(): this.type = {
    import sys.process._
    Seq("gqrx", "-c", config.gqrxConfig).run()
    Thread.sleep(4000)
    Seq("xdotool", "search", "--onlyvisible", "--classname", "gqrx", "windowactivate")
    Thread.sleep(1000)
    Seq("xdotool", "key", "ctrl+d")   // start DSP
    if (!config.isLaptop) {
      Thread.sleep(1000)
      ??? // XXX TODO --- mousepress to activate TCP
    }
    Thread.sleep(1000)
    actor ! Init
    this
  }

  def acquire(dur: Double): Future[RadioSnippet] = {
    ???
  }
}
