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
    }
    p.parse(args, default).fold(sys.exit(1)) { config =>
      val localSocketAddress = Network.initConfig(config, this)
      run(localSocketAddress, config)
    }
  }

  def run(localSocketAddress: InetSocketAddress, config: Config): Unit = {
    val c = OSCClient(config, localSocketAddress)
    try {
      val source = if (config.isLive) {
        Live(config)
      } else {
        Offline(config)
      }

    } finally {
      c.init()
    }

    new Heartbeat(c)
  }
}