package de.sciss.wrtng
package radio
package impl

import java.io.{BufferedReader, InputStreamReader, PrintStream}
import java.net.Socket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import de.sciss.file._
import de.sciss.kollflitz.Vec
import de.sciss.synth.io.AudioFile

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Try}
import scala.util.control.NonFatal

object LiveImpl {
  private final case object Init
  private final case class Acquire(dur: Double, p: Promise[RadioSnippet])

  private final class ActorImpl(live: LiveImpl) extends Actor {
    import live._
    import context.become

    private var recs = knownRecordings()

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
            val startOk = startRes.toOption.contains(true)
            if (startOk) {
              val durMS = (dur * 1000).toLong
              Thread.sleep(durMS)
              val stopRes = remote.send(CMD_STOP_RECORD) {
                case SUCCESS  => true
                case FAILURE  => false
              }
              val stopOk = stopRes.toOption.contains(true)
              if (stopOk) {
                val now = knownRecordings()
                val fOpt = now.diff(recs).find(_.length() > 0)
                recs = now
                fOpt match {
                  case Some(f) =>
                    val res = Try {
                      val spec = AudioFile.readSpec(f)
                      RadioSnippet(file = f, offset = 0L, numFrames = spec.numFrames)
                    }
                    p.tryComplete(res)

                  case None => p.tryFailure(new Exception("Could not find recorded file"))
                }

              } else {
                p.tryFailure(new Exception("Could not stop gqrx recorder"))
              }

            } else {
              p.tryFailure(new Exception("Could not start gqrx recorder"))
            }
        }
    }
  }
}
final class LiveImpl(config: Config) extends Live {
  import LiveImpl._

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

  private final class Telnet {
    private[this] val socket    = new Socket("127.0.0.1", config.gqrxTCPPort)
    private[this] val out       = new PrintStream(socket.getOutputStream)
    private[this] val in        = new BufferedReader(new InputStreamReader(socket.getInputStream))
    private[this] var syncLine  = ""
    private[this] var syncCount = 0
    private[this] val sync      = new AnyRef

    /* private val receiver = */ new Thread {
      override def run(): Unit = {
        var ln = in.readLine()
        while (ln != null) {
          sync.synchronized {
            syncLine   = ln
            syncCount += 1
            sync.notify()
          }
          ln = in.readLine()
        }
      }
      start()
    }

    def send[A](command: String)(response: PartialFunction[String, A]): Try[A] =
      sync.synchronized {
        out.println(command)
        val cnt0  = syncCount
        val t0    = System.currentTimeMillis()
        do {
          sync.wait(200)
        } while (syncCount == cnt0 && System.currentTimeMillis() - t0 < 4000)

        if (syncCount == cnt0) Failure(new Exception(s"Gqrx remote response timeout for '$command'"))
        else Try(response(syncLine))
      }
  }

  private final val CMD_START_RECORD  = "U RECORD 1"
  private final val CMD_STOP_RECORD   = "U RECORD 0"
  private final val SUCCESS           = "RPRT 0"
  private final val FAILURE           = "RPRT 1"

  private def knownRecordings(): Vec[File] =
    config.gqrxRecDir.children { f =>
      f.name.startsWith("gqrx_") && f.name.endsWith(".wav")
    }

  def deleteRecordings(): Unit = {
    val seq = knownRecordings()
    seq.foreach(_.delete())
  }

  private[this] val system          = ActorSystem("radio-acquire")
  private[this] val actor: ActorRef = system.actorOf(Props[ActorImpl](new ActorImpl(this)))

  def init(): this.type = {
    deleteRecordings()
    import sys.process._
    Seq("gqrx", "-c", config.gqrxConfig).run()
    Thread.sleep(4000)
    Seq("xdotool", "search", "--onlyvisible", "--classname", "gqrx", "windowactivate").!
    Thread.sleep(1000)
    Seq("xdotool", "key", "ctrl+d").!   // start DSP
//    if (!config.isLaptop) {
//      Thread.sleep(1000)
//      // XXX TODO --- mousepress to activate TCP
//    }
    Thread.sleep(1000)
    actor ! Init
    this
  }

  def acquire(dur: Double): Future[RadioSnippet] = {
    val p = Promise[RadioSnippet]()
    actor ! Acquire(dur = dur, p = p)
    p.future
  }
}
