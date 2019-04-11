/*
 *  SelectMatch.scala
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

import de.sciss.file.{File, file}
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph}
import de.sciss.kollflitz.Vec
import de.sciss.span.Span
import de.sciss.synth.io.AudioFile
import de.sciss.wrtng.sound.Main.log

import scala.concurrent.stm.InTxn
import scala.concurrent.{Future, Promise}
import scala.math.{max, min}
import scala.swing.Swing
import scala.util.{Failure, Success}

object SelectMatch {
  final case class Config(filePhIn: File = file("ph.aif"), fileDbIn: File = file("db.aif"),
                          instr: OverwriteInstruction = OverwriteInstruction(Span(0, 48000), -1L))

  def main(args: Array[String]): Unit = {
    val default = Config()
    val p = new scopt.OptionParser[Config]("SelectMatch") {
      opt[File]("phase-file")
        .required()
        .text("Phase sound file")
        .action { (f, c) => c.copy(filePhIn = f) }

      opt[File]("db-file")
        .required()
        .text("Database sound file")
        .action { (f, c) => c.copy(fileDbIn = f) }

      opt[Int]("start")
        .required()
        .text("Start frame")
        .action { (v, c) =>
          val w = c.instr.span.stop
          c.copy(instr = c.instr.copy(span = Span(min(v, w), max(v, w)))) }

      opt[Int]("stop")
        .required()
        .text("Stop frame")
        .action { (v, c) =>
          val w = c.instr.span.start
          c.copy(instr = c.instr.copy(span = Span(min(v, w), max(v, w)))) }

      opt[Int]("length")
        .text("Matching length in frames")
        .action { (v, c) => c.copy(instr = c.instr.copy(newLength = v)) }
    }

    p.parse(args, default).fold(sys.exit(1)) { config0 =>
      val config = if (config0.instr.newLength > 0L) config0 else
        config0.copy(instr = config0.instr.copy(newLength = config0.instr.span.length))

      run(config)
    }
  }

  def apply(filePhIn: File = file("ph.aif"), fileDbIn: File = file("db.aif"),
            instr: OverwriteInstruction, ctlCfg: Control.Config)(implicit tx: InTxn): Future[Span] = {
    val pSpan = Promise[Vec[Long]]()

    val g = Graph {
      val sel = selectPart(dbFile = fileDbIn, phFile = filePhIn, instr = instr)
      import de.sciss.fscape.graph._
      FutureLong(sel, pSpan)
    }

    val t0 = System.currentTimeMillis()

    render[Span](ctlCfg, g) { implicit tx =>
      val value = pSpan.future.value
      val t1 = System.currentTimeMillis()
      log(s"SelectMatch() - result value is $value - took ${(t1 - t0)/1000}s")
      val xs = value.get.get
      val offset = if (xs.isEmpty) 0L else xs.head
      Span(offset, offset + instr.newLength)
    }
  }

  def run(config: Config): Unit = {
    val pOffset = Promise[Vec[Long]]()

    val g = Graph {
      val sel = selectPart(dbFile = config.fileDbIn, phFile = config.filePhIn, instr = config.instr)
      import de.sciss.fscape.graph._
      FutureLong(sel, pOffset)
    }

    val cfg = Control.Config()
    cfg.useAsync = false
    val c = Control(cfg)
    c.run(g)

    Swing.onEDT {
      SimpleGUI(c)
    }

    //    g.renderAndWait().get
//    Await.ready(c.status, Duration.Inf)
//    val res = c.status.value.get
//    res.get
//    println("Done.")

    pOffset.future.onComplete {
      case Success(Vec(offset)) =>
        println(s"offset-frame =  $offset")
      case Success(other) => println(s"Huh? Other: $other")
      case Failure(ex) => ex.printStackTrace()
    }
  }

  private[this] val fftSize   : Int     = 1024 // 2048
  private[this] val stepDiv   : Int     = 4
  private[this] val numMel    : Int     = 42
  private[this] val numCoef   : Int     = 21
  private[this] val sideDur   : Double  = 0.25
  //  private[this] val spaceDur  : Double  = 1.5 // 0.5
  private[this] val minFreq   : Double  = 100
  private[this] val maxFreq   : Double  = 14000
  private[this] val maxDbDur  : Double  = 42.0     // limit, coz the Pi is too slow to run the entire 3 minutes in one rotation
  private[this] val maxDbLen  : Long    = (maxDbDur * SR).toLong

  def selectPart(dbFile: File, phFile: File, instr: OverwriteInstruction): GE = {
    import de.sciss.fscape.graph._
    val dbSpec  = AudioFile.readSpec(dbFile)
    val phSpec  = AudioFile.readSpec(phFile)

    require(dbSpec.numChannels == 1)
    require(phSpec.numChannels == 2) // left channel is sound signal, right channel is 'withering'

    def mkDbIn()    = AudioFileIn(dbFile, numChannels = dbSpec.numChannels)
    def mkPhIn()    = {
      val in = AudioFileIn(phFile, numChannels = phSpec.numChannels)
      // there is a bloody bug in fscape audio-file-in with the second channel dangling.
      // this seems to fix it
      Length(in).poll(0, "length-ph-in")
      in out 0
    }

//    // XXX TODO --- enabling this prevents some hanging. but why?
//    // if (Main.showLog) {
//    in.poll(0, "ovr-fsc")
//    // }

    val stepSize    = fftSize / stepDiv
    val sideFrames  = (SR * sideDur ).toInt
    val sideLen     = max(1, sideFrames / stepSize)
    val covSize     = numCoef * sideLen

//    - punch-in : start = max(0, min(phrase-length, instr.span.start) - sideFrames); stop = min(phrase-length, start + sideFrames)
//    - punch-out: start = max(0, min(phrase-length, instr.span.stop )); stop = min(phrase-length, start + sideFrames)
//    - from this we calculate the offset between the two taps into the db: delay-length = punch-out.start - punch-in.start
//    - from that we calculate the run length: db.length - sideFrames - delay-length; if that's <= 0, abort here
//      - from that we calculate the number of window steps (/repetitions)

    val phraseLen     = phSpec.numFrames
    val punchInStart  = max(0, min(phraseLen, instr.span.start) - sideFrames)
    val punchInStop   = min(phraseLen, punchInStart + sideFrames)

    val punchOutStart = max(0, min(phraseLen, instr.span.stop))
    val punchOutStop  = min(phraseLen, punchOutStart + sideFrames)

    val punchLen      = min(punchInStop - punchInStart, punchOutStop - punchOutStart)
    val punchIn       = Span(punchInStart , punchInStart  + punchLen)
    val punchOut      = Span(punchOutStart, punchOutStart + punchLen)

    val dbLen         = min(maxDbLen, dbSpec.numFrames)
    val dbDlyFrames   = max(0, instr.newLength  - sideFrames)
    val runFrames     = max(0, dbLen - sideFrames - dbDlyFrames)
    val runSteps      = max(1, runFrames / stepSize)

    val dbSpanIn      = Span(0L, runFrames)
    val dbSpanOut     = Span(dbDlyFrames, dbDlyFrames + runFrames)

    def mkMatrix(in: GE): GE = {
      val lap         = Sliding(in, fftSize, stepSize) * GenWindow(fftSize, GenWindow.Hann)
      val fft         = Real1FFT(lap, fftSize, mode = 2)
      val mag         = fft.complex.mag
      val mel         = MelFilter(mag, fftSize/2, bands = numMel,
        minFreq = minFreq, maxFreq = maxFreq, sampleRate = SR)
      val mfcc        = DCT_II(mel.log, numMel, numCoef, zero = 0 /* 1 */)
      mfcc
    }

    def mkPhaseSig(span: Span): GE = {
      val in0         = mkPhIn()
      val in1         = if (span.start == 0L) in0 else in0.drop(span.start)
      val in          = in1.take(span.length)
      val mfcc        = mkMatrix(in)
      RepeatWindow(mfcc, size = covSize, num = runSteps)
    }

    def mkDbSig(span: Span): GE = {
      val in0         = mkDbIn()
      val in1         = if (span.start == 0L) in0 else in0.drop(span.start)
      val in          = in1.take(span.length)
      val mfcc        = mkMatrix(in)
      val mfccSlid    = Sliding(mfcc, size = covSize, step = numCoef)
      mfccSlid
    }

    val sigPunchIn  = mkPhaseSig(punchIn )
    val sigPunchOut = mkPhaseSig(punchOut)

//    val numSteps    = numFrames / stepSize
//    val numCov      = numSteps - (2 * sideLen)
//    val numCov1     = numSteps - sideLen - spaceLen

    val sigDbIn     = mkDbSig(dbSpanIn )
    val sigDbOut    = mkDbSig(dbSpanOut)
    val covIn       = Pearson(sigDbIn , sigPunchIn , covSize)
    val covOut      = Pearson(sigDbOut, sigPunchOut, covSize)

    val keys        = (covIn + covOut).take(runSteps)
    //    Length(BufferDisk(covNeg)).poll(0, "covNeg.length")
    //    Length(BufferDisk(wither)).poll(0, "wither.length")

//    println(s"runSteps: $runSteps")
//    Length(BufferDisk(keys)).poll(0, "key.length")

//    Frames(sigDbIn ).poll(Metro(48000), "sigDbIn ")
//    Frames(sigDbOut).poll(Metro(48000), "sigDbOut")

//    AudioFileOut(file("/data/temp/match.aif"), AudioFileSpec(numChannels = 1, sampleRate = SR), in = keys)

    val keysEl      = keys.elastic()
    val values      = Frames(keysEl) - 1
    val top         = PriorityQueue(keysEl, values, size = 1)    // highest covariances mapped to frames
    val startF      = top * stepSize
    startF.poll(0, "RESULT")
//    Length(startF).poll(0, "RESULT-LEN")
    startF
  }
}
