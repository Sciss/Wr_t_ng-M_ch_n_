/*
 *  Main.scala
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

import java.net.InetSocketAddress

import de.sciss.file.File
import de.sciss.synth.io.AudioFile

import scala.util.control.NonFatal

object Main extends MainLike {
  protected val pkgLast = "radio"

  def main(args: Array[String]): Unit = {
    println(s"-- $name $fullVersion --")
    val default = Config()
    val p = new scopt.OptionParser[Config](namePkg) {
      opt[Unit] ('d', "dump-osc")
        .text (s"Enable OSC dump (default: ${default.dumpOSC})")
        .action { (_, c) => c.copy(dumpOSC = true) }

      opt[Unit] ("laptop")
        .text (s"Instance is laptop (default: ${default.isLaptop})")
        .action { (_, c) => c.copy(isLaptop = true) }

      opt[Unit] ("no-auto-start")
        .text ("Turn off auto-start.")
        .action { (_, c) => c.copy(autoStart = false) }

      opt[Unit] ("keep-energy")
        .text ("Do not turn off energy saving")
        .action { (_, c) => c.copy(disableEnergySaving = false) }

      opt[Unit] ("log")
        .text ("Enable logging")
        .action { (_, c) => c.copy(log = true) }

      opt[String] ("own-socket")
        .text (s"Override own IP address and port; must be <host>:<port> ")
        .validate { v =>
          parseSocket(v).map(_ => ())
        }
        .action { (v, c) =>
          val addr = parseSocket(v).right.get
          c.copy(ownSocket = Some(addr))
        }

      opt[Int] ("dot")
        .text ("Explicit 'dot' (normally the last element of the IP address). Used for transaction ids.")
        .validate { v => if (v >= -1 && v <= 255) success else failure("Must be -1, or 0 to 255") }
        .action { (v, c) => c.copy(dot = v) }

      opt[String] ("gqrx-config")
        .text (s"Config file for gqrx (default: ${default.gqrxConfig})")
        .action { (v, c) => c.copy(gqrxConfig = v) }

      opt[Int] ("gqrx-port")
        .text (s"TCP port of gqrx remote control (default: ${default.gqrxTCPPort})")
        .action { (v, c) => c.copy(gqrxTCPPort = v) }

      opt[File] ('f', "file")
        .text (s"Use pre-captured radio file instead of real-time capture through gqrx.")
        .validate { v =>
          try {
            val spec = AudioFile.readSpec(v)
            if (spec.numChannels == 1) success else failure(s"Must be monophonic: $v")
          } catch {
            case NonFatal(ex) => failure(s"Cannot identify file $v: ${ex.getMessage}")
          }
        }
        .action { (v, c) => c.copy(offline = Some(v)) }

      opt[File] ('r', "rec-dir")
        .text (s"Directory for sounds recorded by gqrx (default: ${default.gqrxRecDir})")
        .validate { v => if (v.isDirectory) success else failure(s"Not a directory: $v") }
        .action { (v, c) => c.copy(gqrxRecDir = v) }

      opt[Int] ("min-sound-nodes")
        .text (s"Minimum number of sound nodes for auto-start (default: ${default.minSoundNodes})")
        .action { (v, c) => c.copy(minSoundNodes = v) }

      opt[Int] ("key-shutdown")
        .text (s"Keypad key to trigger shutdown (1 to 9; default ${default.keyShutdown})")
        .validate(i => if (i >= 1 && i <= 9) Right(()) else Left("Must be 1 to 9") )
        .action { (v, c) => c.copy(keyShutdown = (v + '0').toChar) }

      opt[Int] ("key-reboot")
        .text (s"Keypad key to trigger reboot (1 to 9; default ${default.keyReboot})")
        .validate(i => if (i >= 1 && i <= 9) Right(()) else Left("Must be 1 to 9") )
        .action { (v, c) => c.copy(keyReboot = (v + '0').toChar) }

      opt[Int] ("button-shutdown")
        .text (s"Button 8-bit integer to trigger shutdown (default ${default.keyShutdown})")
        .validate(i => if (i >= 1 && i <= 255) Right(()) else Left("Must be 1 to 255") )
        .action { (v, c) => c.copy(buttonShutdown = v) }

      opt[Int] ("button-reboot")
        .text (s"Button 8-bit integer to trigger reboot (default ${default.keyShutdown})")
        .validate(i => if (i >= 1 && i <= 255) Right(()) else Left("Must be 1 to 255") )
        .action { (v, c) => c.copy(buttonReboot = v) }
    }
    p.parse(args, default).fold(sys.exit(1)) { config =>
      val localSocketAddress = Network.initConfig(config, this)
      run(localSocketAddress, config)
    }
  }

  def run(localSocketAddress: InetSocketAddress, config: Config): Unit = {
    val source = try {
      if (config.isLive) {
        Live(config)
      } else {
        Offline(config)
      }
    } catch {
      case NonFatal(ex) =>
        println("Could not create source:")
        ex.printStackTrace()
        null
    }
    val c = OSCClient(config, localSocketAddress, source)
    c.init()
    new Heartbeat(c)

    if (config.hasKeys || config.hasButtons) {
      import sys.process._
      val tpeArgs = if (config.hasKeys) {
        Seq("--key-shutdown", config.keyShutdown.toString, "--key-reboot", config.keyReboot.toString)
      } else {
        Seq("--button-shutdown", config.buttonShutdown.toString, "--button-reboot", config.buttonReboot.toString)
      }

      val cmd = Seq("sudo", "imperfect-raspikeys", "--ip", localSocketAddress.getHostString, "--port",
        localSocketAddress.getPort.toString) ++ tpeArgs
      cmd.run()
    }
  }
}