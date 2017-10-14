/*
 *  SelectOverwrite.scala
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
package sound

import de.sciss.file._
import de.sciss.fscape.{GE, Graph}
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.io.AudioFile

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object SelectOverwrite {
  def main(args: Array[String]): Unit = {
    val pathIn  = args.headOption.getOrElse(
      "/data/IEM/SoSe2017/DigitaleVerfahren2017/support/Session02_170315/beyond_the_wall_of_sleep_s1.aif"
    )
    val fileIn  = file(pathIn)
    // val fileOut = file("/data/temp/corr.aif")
    run(fileIn = fileIn /* , fileOut = fileOut */)
  }

  final case class SelectPart(startFrame: GE, stopFrame: GE)

  def selectPart(fileIn: File): SelectPart = {

    val fftSize   : Int     = 1024 // 2048
    val stepDiv   : Int     = 4
    val numMel    : Int     = 42
    val numCoef   : Int     = 21
    val sideDur   : Double  = 0.25
    val spaceDur  : Double  = 1.5 // 0.5
    val minFreq   : Double  = 100
    val maxFreq   : Double  = 14000

    import de.sciss.fscape.graph._
    val specIn      = AudioFile.readSpec(fileIn)
    import specIn.{numChannels, numFrames, sampleRate}
    def mkIn()      = AudioFileIn(fileIn, numChannels = numChannels)
    val in          = mkIn()
    val stepSize    = fftSize / stepDiv
    val sideFrames  = (sampleRate * sideDur ).toInt
    val spaceFrames = (sampleRate * spaceDur).toInt
    val spaceLen    = spaceFrames / stepSize
    val sideLen     = math.max(1, sideFrames / stepSize) // 24
    val covSize     = numCoef * sideLen
    val numSteps    = numFrames / stepSize
    val numCov      = numSteps - (2 * sideLen)
    val numCov1     = numSteps - sideLen - spaceLen

    val inMono      = if (numChannels == 1) in else ChannelProxy(in, 0) + ChannelProxy(in, 1) // XXX TODO --- missing Mix
    val lap         = Sliding (inMono, fftSize, stepSize) * GenWindow(fftSize, GenWindow.Hann)
    val fft         = Real1FFT(lap, fftSize, mode = 2)
    val mag         = fft.complex.mag
    val mel         = MelFilter(mag, fftSize/2, bands = numMel,
      minFreq = minFreq, maxFreq = maxFreq, sampleRate = sampleRate)
    val mfcc        = DCT_II(mel.log, numMel, numCoef, zero = 1 /* 0 */)

    // reconstruction of what strugatzki's 'segmentation' is doing (first step)
    val mfccSlid    = Sliding(mfcc, size = covSize, step = numCoef)
    val mfccSlidT   = mfccSlid.drop(covSize)
    val el          = BufferMemory(mfccSlid, size = covSize)
    val cov0        = Pearson(el, mfccSlidT, covSize)
    val cov         = cov0.take(numCov1)

    val covNeg      = -cov + (1: GE)  // N.B. not `1 - cov` because binary-op-ugen stops when first input stops
    val covMin0     = DetectLocalMax(covNeg, size = spaceLen)
    //      Length(covMin0).poll(0, "covMin0.length")
    //      println(s"numCov: $numCov")
    val covMin      = covMin0.take(numCov)  // XXX TODO --- bug in DetectLocalMax?

    val keysEl      = covNeg.elastic()
    val values      = Frames(keysEl) - 1
    val keysG       = FilterSeq(keysEl, covMin)
    val valuesG     = FilterSeq(values, covMin)

    val top         = PriorityQueue(keysG, valuesG, size = 1)    // lowest covariances mapped to frames
    val startF      = top * stepSize
    val valuesGE    = BufferMemory(valuesG, numCov1)
    val stopF       = valuesGE.dropWhile(valuesGE <= top).take(1) * stepSize

    SelectPart(startFrame = startF, stopFrame = stopF)
  }

  def run(fileIn: File /* , fileOut: File */): Unit = {
//    /**
//      * Length of correlation in seconds
//      */
//    val correlationMotion = Motion.linexp(Motion.walk(0, 1, 0.1), 0, 1, 0.2, 2.0)

    val pSpan = Promise[Vec[Long]]()

    val g = Graph {
      val sel = selectPart(fileIn)
      // sel.startFrame.poll(0, "start-frame")
      // sel.stopFrame .poll(0, "stop-frame" )

      import de.sciss.fscape.graph._
      FutureLong(sel.startFrame ++ sel.stopFrame, pSpan)
    }

    g.renderAndWait().get
    println("Done.")

    pSpan.future.onComplete {
      case Success(Vec(start, stop)) =>
        println(s"start-frame =  $start, stop-frame = $stop")
      case Success(other) => println(s"Huh? Other: $other")
      case Failure(ex) => ex.printStackTrace()
    }
  }
}