/*
 *  Main.scala
 *  (Wr_t_ng-M_ch_n_)
 *
 *  Copyright (c) 2017-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.wrtng
package sound

import java.net.InetSocketAddress

import de.sciss.file._

import scala.util.control.NonFatal

object Main extends MainLike {
  protected val pkgLast = "sound"

  def main(args: Array[String]): Unit = {
    println(s"-- $name $fullVersion --")
    val default = Config()
    val p = new scopt.OptionParser[Config](namePkg) {
      opt[File]("base-dir")
        .text (s"Base directory (default: ${default.baseDir})")
        .validate { f => if (f.isDirectory) success else failure(s"Not a directory: $f") }
        .action { (f, c) => c.copy(baseDir = f) }

      opt[Unit] ('d', "dump-osc")
        .text (s"Enable OSC dump (default ${default.dumpOSC})")
        .action { (_, c) => c.copy(dumpOSC = true) }

      opt[Unit] ("laptop")
        .text (s"Instance is laptop (default ${default.isLaptop})")
        .action { (_, c) => c.copy(isLaptop = true) }

      opt[Unit] ("keep-phase")
        .text (s"Keep working from existing phase file (default ${default.keepPhase})")
        .action { (_, c) => c.copy(keepPhase = true) }

      opt[Unit] ("keep-energy")
        .text ("Do not turn off energy saving")
        .action { (_, c) => c.copy(disableEnergySaving = false) }

      opt[Unit] ("no-qjackctl")
        .text ("Do not launch QJackCtl")
        .action { (_, c) => c.copy(qjLaunch = false) }

      opt[String]("qjackctl-preset")
        .text (s"QJackCtl preset name (default: ${default.qjPreset})")
        .action { (f, c) => c.copy(qjPreset = f) }

      opt[File]("qjackctl-patchbay")
        .text (s"QJackCtl patchbay path (default: ${default.qjPatchBay})")
        .action { (f, c) => c.copy(qjPatchBay = f) }

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

      opt[String] ("radio-socket")
        .text (s"Override radio IP address and port; must be <host>:<port> ")
        .validate { v =>
          parseSocket(v).map(_ => ())
        }
        .action { (v, c) =>
          val addr = parseSocket(v).right.get
          c.copy(radioSocket = Some(addr))
        }

      opt[Int] ("dot")
        .text ("Explicit 'dot' (normally the last element of the IP address). Used for transaction ids.")
        .validate { v => if (v >= -1 && v <= 255) success else failure("Must be -1, or 0 to 255") }
        .action { (v, c) => c.copy(dot = v) }

      opt[Int] ("alsa-device-index")
        .text (s"ALSA device index, for setting volume (-1 for no volume set; default: ${default.alsaDeviceIndex})")
        .action { (v, c) => c.copy(alsaDeviceIndex = v) }

      opt[String] ("alsa-volume-name")
        .text (s"ALSA device's volume control name (${default.alsaVolumeName})")
        .action { (v, c) => c.copy(alsaVolumeName = v) }

      opt[String] ("alsa-volume-value")
        .text (s"ALSA device's volume control value (${default.alsaVolumeValue})")
        .action { (v, c) => c.copy(alsaVolumeValue = v) }

      opt[Unit] ("buttons")
        .text ("Enable RPi button control for shutdown (29) and reboot (30)")
        .action { (_, c) => c.copy(buttonCtrl = true) }

      opt[Unit] ("simple-match")
        .text ("Enable simple replacement matching")
        .action { (_, c) => c.copy(simpleMatch = true) }

      //      opt[Double] ("bee-amp")
//        .text (s"Amplitude (decibels) for bees (default ${default.beeAmp}")
//        .validate { v => if (v >= -30 && v <= 30) success else failure("Must be >= -30 and <= 30") }
//        .action { (v, c) => c.copy(beeAmp = v.toFloat) }
//
//      opt[Double] ("text-amp")
//        .text (s"Amplitude (decibels) for text (default ${default.textAmp}")
//        .validate { v => if (v >= -30 && v <= 30) success else failure("Must be >= -30 and <= 30") }
//        .action { (v, c) => c.copy(textAmp = v.toFloat) }
//
//      opt[Unit] ("keypad")
//        .text ("Enable keypad window")
//        .action { (_, c) => c.copy(keypad = true) }
    }
    p.parse(args, default).fold(sys.exit(1)) { config =>
      val localSocketAddress = Network.initConfig(config, this)

//      if (!config.isLaptop) {
//        // cf. https://github.com/Pi4J/pi4j/issues/238
//        println("Setting up GPIO...")
//        try {
//          GpioUtil.enableNonPrivilegedAccess()
//        } catch {
//          case NonFatal(ex) =>
//            Console.err.println("Could not enable GPIO access")
//            ex.printStackTrace()
//        }
//      }

      if (config.alsaDeviceIndex >=0) {
        val cmd = Seq("amixer", "-c", config.alsaDeviceIndex.toString,
          "set", config.alsaVolumeName, config.alsaVolumeValue)
        println(cmd.mkString(" "))
        import sys.process._
        try {
          cmd.!
        } catch {
          case NonFatal(ex) =>
            Console.err.println("Could not set ALSA volume control")
            ex.printStackTrace()
        }
      }

      if (!config.isLaptop && config.qjLaunch) {
        // -p preset, -a active patch bay, -s start server
        val cmd = Seq("qjackctl", "-p", config.qjPreset, "-a", config.qjPatchBay.path, "-s")
        println(cmd.mkString(" "))
        import sys.process._
        try {
          cmd.run()
        } catch {
          case NonFatal(ex) =>
            Console.err.println("Could not start QJackCtl")
            ex.printStackTrace()
        }
      }

      if (config.buttonCtrl) {
        import sys.process._
        val cmd = Seq("sudo", "imperfect-raspikeys",
          "--ip", localSocketAddress.getHostString, "--port", localSocketAddress.getPort.toString,
          "--button-shutdown", 29.toString, "--button-reboot", 30.toString)
        try {
          cmd.run()
        } catch {
          case NonFatal(ex) =>
            println("Could not start button control:")
            ex.printStackTrace()
        }
      }

      run(localSocketAddress, config)
    }
  }

  def run(localSocketAddress: InetSocketAddress, config: Config): Unit = {
    val c = OSCClient(config, localSocketAddress)
    c.init()
    new Heartbeat(c)
//    if (!config.isLaptop) {
//      try {
//        c.relay.bothPins  // lazy, now initialises them
//      } catch {
//        case NonFatal(ex) =>
//          Console.err.println("Could not enable GPIO access")
//          ex.printStackTrace()
//      }
//    }
//    if (c.dot == Network.KeypadDot || config.keypad) Swing.onEDT(new KeypadWindow(c))
  }
}