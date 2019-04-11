/*
 *  SelectOverwrite.scala
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

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph}
import de.sciss.span.Span
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.io.AudioFile
import de.sciss.wrtng.sound.Main.log

import scala.concurrent.stm.InTxn
import scala.concurrent.{Future, Promise}
import scala.swing.Swing
import scala.util.{Failure, Success}

object SelectOverwrite {
  def main(args: Array[String]): Unit = {
    val pathIn  = args.headOption.getOrElse(
      "/data/IEM/SoSe2017/DigitaleVerfahren2017/support/Session02_170315/beyond_the_wall_of_sleep_s1.aif"
    )
    println(s"Path: $pathIn")
    val fileIn  = file(pathIn)
    // val fileOut = file("/data/temp/corr.aif")
    run(fileIn = fileIn /* , fileOut = fileOut */)
  }

  final case class SelectPart(startFrame: GE, stopFrame: GE)

  private[this] val fftSize   : Int     = 1024 // 2048
  private[this] val stepDiv   : Int     = 4
  private[this] val numMel    : Int     = 42
  private[this] val numCoef   : Int     = 21
  private[this] val sideDur   : Double  = 0.25
//  private[this] val spaceDur  : Double  = 1.5 // 0.5
  private[this] val minFreq   : Double  = 100
  private[this] val maxFreq   : Double  = 14000
  private[this] val witherTgt : Double  = 0.0012 / 30   // a threshold of 0.0012 in 30 iterations

  def selectPart(fileIn: File, spaceDur: Double): SelectPart = {
    import de.sciss.fscape.graph._
    val specIn      = AudioFile.readSpec(fileIn)
    import specIn.{numChannels, numFrames, sampleRate}
    require(numChannels == 2) // left channel is sound signal, right channel is 'withering'

    def mkIn()      = AudioFileIn(fileIn, numChannels = numChannels)
    val in          = mkIn() out 0
    val inWither    = mkIn() out 1    // separate UGen so we don't run into trouble wrt to buffering

    // XXX TODO --- enabling this prevents some hanging. but why?
    // if (Main.showLog) {
      in.poll(0, "ovr-fsc")
    // }

    val stepSize    = fftSize / stepDiv
    val sideFrames  = (sampleRate * sideDur ).toInt
    val spaceFrames = (sampleRate * spaceDur).toInt
    val spaceLen    = spaceFrames / stepSize
    val sideLen     = math.max(1, sideFrames / stepSize) // 24
    val covSize     = numCoef * sideLen
    val numSteps    = numFrames / stepSize
    val numCov      = numSteps - (2 * sideLen)
    val numCov1     = numSteps - sideLen - spaceLen

//    val inMono      = if (numChannels == 1) in else ChannelProxy(in, 0) + ChannelProxy(in, 1)
    val lap         = Sliding (in       , fftSize, stepSize) * GenWindow(fftSize, GenWindow.Hann)
    val fft         = Real1FFT(lap, fftSize, mode = 2)
    val mag         = fft.complex.mag
    val mel         = MelFilter(mag, fftSize/2, bands = numMel,
      minFreq = minFreq, maxFreq = maxFreq, sampleRate = sampleRate)
    val mfcc        = DCT_II(mel.log, numMel, numCoef, zero = 1 /* 0 */)

    // reconstruction of what strugatzki's 'segmentation' is doing (first step)
    val mfccSlid    = Sliding(mfcc, size = covSize, step = numCoef)
    val mfccSlidT   = mfccSlid.drop(covSize)
    val el          = BufferMemory(mfccSlid, size = covSize)
    val cov         = Pearson(el, mfccSlidT, covSize)

    val covNeg      = -cov + (1: GE)  // N.B. not `1 - cov` because binary-op-ugen stops when first input stops
    val wither0     = ResizeWindow(inWither, size = stepSize, start = 0, stop = -(stepSize - 1))
    val wither      = wither0 * (witherTgt / WitheringConstant)

    val key         = (covNeg + wither).take(numCov1)
//    Length(BufferDisk(covNeg)).poll(0, "covNeg.length")
//    Length(BufferDisk(wither)).poll(0, "wither.length")
//    Length(BufferDisk(key   )).poll(0, "key   .length")

    val covMin0     = DetectLocalMax(key, size = spaceLen)
    val covMin      = covMin0.take(numCov)  // XXX TODO --- bug in DetectLocalMax?

    val keysEl      = key.elastic()
    val values      = Frames(keysEl) - 1
    val keysG       = FilterSeq(keysEl, covMin)
    val valuesG     = FilterSeq(values, covMin)

//    RunningMax(keysG).last.poll(0, "MAX SCHNUCK")

    val top         = PriorityQueue(keysG, valuesG, size = 1)    // lowest covariances mapped to frames
    val startF      = top * stepSize
    val valuesGE    = BufferMemory(valuesG, numCov1)
    val stopF       = (valuesG.dropWhile(valuesGE <= top) :+ numCov /* numSteps */).take(1) * stepSize

    SelectPart(startFrame = startF, stopFrame = stopF)
  }

  def apply(fileIn: File, ctlCfg: Control.Config, spaceDur: Double)(implicit tx: InTxn): Future[Span] = {
    val pSpan = Promise[Vec[Long]]()

    val g = Graph {
      val sel = selectPart(fileIn, spaceDur = spaceDur)
      import de.sciss.fscape.graph._
      FutureLong(sel.startFrame ++ sel.stopFrame, pSpan)
    }

    render[Span](ctlCfg, g) { implicit tx =>
      val value = pSpan.future.value
      log(s"SelectOverwrite() - result value is $value")
      val xs = value.get.get
      val start = if (xs.size > 0) xs(0) else 0L
      val stop  = if (xs.size > 1) xs(1) else (start + (4 * SR)).toLong
//      val Vec(start, stop) = value.get.get
      Span(start, stop)
    }
  }

  def run(fileIn: File /* , fileOut: File */): Unit = {
//    /**
//      * Length of correlation in seconds
//      */
//    val correlationMotion = Motion.linexp(Motion.walk(0, 1, 0.1), 0, 1, 0.2, 2.0)

    val pSpan = Promise[Vec[Long]]()

    val g = Graph {
      val sel = selectPart(fileIn, spaceDur = 0.5)
      // sel.startFrame.poll(0, "start-frame")
      // sel.stopFrame .poll(0, "stop-frame" )

      import de.sciss.fscape.graph._
      FutureLong(sel.startFrame ++ sel.stopFrame, pSpan)
    }

    val cfg = Control.Config()
    cfg.useAsync = false
    val c = Control(cfg)
    c.run(g)

    Swing.onEDT {
      SimpleGUI(c)
    }

    pSpan.future.onComplete {
      case Success(Vec(start, stop)) =>
        println(s"start-frame =  $start, stop-frame = $stop")
      case Success(other) => println(s"Huh? Other: $other")
      case Failure(ex) => ex.printStackTrace()
    }
  }
}