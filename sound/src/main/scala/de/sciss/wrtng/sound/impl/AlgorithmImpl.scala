/*
 *  AlgorithmImpl.scala
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
package impl

import akka.actor.ActorSystem
import de.sciss.file._
import de.sciss.fscape.{GE, Graph}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.confluent.TxnRandom
import de.sciss.lucre.stm.TxnLike
import de.sciss.lucre.synth.Txn
import de.sciss.numbers
import de.sciss.span.Span
import de.sciss.synth.io.AudioFileType.AIFF
import de.sciss.synth.io.SampleFormat.Int16
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import de.sciss.wrtng.sound.Main.log

import scala.concurrent.Future
import scala.concurrent.stm.{InTxn, Ref, atomic}
import scala.math.{max, min}
import scala.util.control.NonFatal

final class AlgorithmImpl(val client: OSCClient, val channel: Int) extends Algorithm {
  import client.config

  def init(): this.type = {
    dbInit()
    phInit()
    this
  }

  def playAndIterate()(implicit tx: Txn): Future[Unit] = {
    import TxnLike.peer

    val ph0 = phFile()
    if (ph0.numFrames > 4800) {
      val fadeIn  = 0.1f
      import numbers.Implicits._
      val fadeOut = random.nextFloat().linlin(0, 1, 0.1f, 0.5f)
      val start   = 0L
      val stop    = ph0.numFrames
      client.scene.play(ph0, ch = channel, start = start, stop = stop, fadeIn = fadeIn, fadeOut = fadeOut)
    }
    iterate()
  }

  def iterate()(implicit tx: InTxn): Future[Unit] = {
    log("iterate()")

    for {
      _     <- dbFill()
      instr <- atomic { implicit tx => phSelectOverwrite  ()            }
      mat   <- atomic { implicit tx => dbFindMatch        (instr)       }
      _     <- atomic { implicit tx => performOverwrite   (instr, mat)  }
    } yield {

      log("iterate() - done")
      ()
    }
  }

  private[this] implicit val random: TxRnd = TxnRandom.plain()

  ////////////////
  //  Database  //
  ////////////////

  private[this] val dbDir         = config.baseDir / s"database${channel + 1}"
  private[this] val dbPattern     = "db%06d.aif"
  private[this] val dbAfSpec      = AudioFileSpec(AIFF, Int16, numChannels = 1, sampleRate = SR)

  private[this] val dbCount       = Ref.make[Int ]()
  private[this] val dbFile        = Ref.make[AudioFileRef]()

  private[this] val dbTargetLen   = (SR * 180).toLong
  private[this] val maxCaptureLen = (SR *  20).toLong
  private[this] val minCaptureLen = (SR *   4).toLong

  private[this] val ctlCfg: Control.Config = {
    val c = Control.Config()
    c.actorSystem = ActorSystem(s"algorithm-${channel + 1}")
    c.useAsync    = false
    c.build
  }

  private def mkDbFile(i: Int): File = dbDir / dbPattern.format(i)

  private def dbInit(): Unit = {
    dbDir.mkdirs()

    val soundFiles  = dbDir.children(f => f.isFile && f.name.startsWith("db") && f.extL == "aif")
    val candidate   = soundFiles.filter(_.length() > 0L).sorted(File.NameOrdering).lastOption
    val toDelete    = soundFiles.toSet -- candidate
    toDelete.foreach(_.delete())
    val db0 = candidate.getOrElse {
      val f   = mkDbFile(0)
      val af  = AudioFile.openWrite(f, dbAfSpec)
      af.close()
      f
    }
    atomic { implicit tx =>
      dbCount() = db0.base.substring(2).toInt
      val spec  = AudioFile.readSpec(db0)
      dbFile () = new AudioFileRef(db0, spec.numFrames)
    }
  }

  def dbFill()(implicit tx: InTxn): Future[Long] = {
    val db0     = dbFile()
    val len0    = db0.numFrames
    val captLen = min(maxCaptureLen, dbTargetLen - len0)
    if (captLen < minCaptureLen) txFutureSuccessful(len0)
    else {
      val captSec     = (captLen / SR).toFloat
      log(f"dbFill() - capture dur $captSec%g sec")
      val futFileApp  = client.queryRadioRec(captSec)
      futFileApp.flatMap { fileApp =>
        val numFrames = min(fileApp.numFrames, captLen)
        atomic { implicit tx =>
          dbAppend(fileApp = fileApp.f, numFrames = numFrames).andThen {
            case _ => atomic { implicit tx => fileApp.release() }
          }
        }
      }
    }
  }

  def dbAppend(fileApp: File, offset: Long = 0L, numFrames: Long)(implicit tx: InTxn): Future[Long] = {
    val cnt   = dbCount.transformAndGet(_ + 1)
    val db0   = dbFile()
    val len0  = db0.numFrames
    val fdLen = min(len0, min(numFrames, (SR * 0.1).toInt)).toInt
    val db1F  = mkDbFile(cnt)

    log(s"dbAppend(numFrames = $numFrames); len0 = $len0, fdLen = $fdLen")

    val g = Graph {
      import de.sciss.fscape.graph._
      val inDb    = AudioFileIn(db0.f  , numChannels = 1)
      val inApp0  = AudioFileIn(fileApp, numChannels = 1)

      // adjust to target for 72 phon
      val loudWin = SR.toInt
      val inAppSl = Sliding(inApp0, size = loudWin, step = loudWin/2)
      val loud    = Loudness(in = inAppSl, sampleRate = SR, size = loudWin)
//      val loudAvg = {
//        val sum = RunningSum(loud)
//        val num = Length(sum)
//        sum.last / num
//      }
      val loudMax = {
        RunningMax(loud).last
      }
      val loudCorr = {
        val tgt   = 72.0
        val dif   = tgt - loudMax
        ((dif.abs.pow(0.85) * dif.signum) * 1.28).dbamp.min(24)
      }
      val inApp   = (AudioFileIn(fileApp, numChannels = 1) * loudCorr).clip2(1.0)

      val cat     = if (fdLen == 0) {
        inDb ++ inApp
      } else {
        val preLen  = len0 - fdLen
        val pre     = inDb .take(preLen)
        val cross0  = inDb .drop(preLen) * Line(1, 0, fdLen).sqrt
        val cross1  = inApp.take(fdLen ) * Line(0, 1, fdLen).sqrt
        val cross   = (cross0 + cross1).clip2(1.0)
        val post    = inApp.drop(fdLen)
        pre ++ cross ++ post
      }
      AudioFileOut(db1F, dbAfSpec, in = cat)
    }

    render[Long](ctlCfg, g) { implicit tx =>
      if (dbCount() == cnt) {
        val spec    = AudioFile.readSpec(db1F)
        val db1     = new AudioFileRef(db1F, spec.numFrames)
        val dbOld   = dbFile.swap(db1)
        dbOld.release()
        spec.numFrames
      } else {
        dbFile().numFrames
      }
    }
  }

  def dbFindMatch(instr: OverwriteInstruction)(implicit tx: InTxn): Future[Span] = {
    val len0 = dbFile().numFrames
    // XXX TODO
    txFutureSuccessful {
      val span = Span(0L, min(len0, instr.newLength))
      log(s"dbFindMatch($instr) yields $span")
      span
    }
  }

  ////////////////
  //  Ph(r)ase  //
  ////////////////

  private[this] val phDir         = config.baseDir / s"phase${channel + 1}"
  private[this] val phPattern     = "ph%06d.aif"
  private[this] val phAfSpec      = AudioFileSpec(AIFF, Int16, numChannels = 2, sampleRate = SR)

  private[this] val phCount       = Ref.make[Int]()
  private[this] val phFile        = Ref.make[AudioFileRef]()

  private def mkPhFile(i: Int): File = phDir / phPattern.format(i)

  private def phInit(): Unit = {
    phDir.mkdirs()

    val soundFiles  = phDir.children(f => f.isFile && f.name.startsWith("ph") && f.extL == "aif")
    val candidate   = soundFiles.filter { f =>
      try {
        val spec = AudioFile.readSpec(f)
        spec.numFrames > 4800 && spec.numChannels == 2
      } catch {
        case NonFatal(_) => false
      }
    } .sorted(File.NameOrdering).lastOption
    val toDelete    = soundFiles.toSet -- candidate
    toDelete.foreach(_.delete())
    val ph0 = candidate.getOrElse {
      val f   = mkPhFile(0)
      val af  = AudioFile.openWrite(f, phAfSpec)
      af.close()
      f
    }
    atomic { implicit tx =>
      phCount() = ph0.base.substring(2).toInt
      val spec  = AudioFile.readSpec(ph0)
      phFile () = new AudioFileRef(ph0, spec.numFrames)
    }
  }

  private[this] val stretchStable   : Motion = Motion.linexp(Motion.walk(0, 1, 0.1), 0, 1, 1.0 / 1.1, 1.1)
  private[this] val stretchGrow     : Motion = Motion.walk(1.2, 2.0, 0.2)
  private[this] val stretchShrink   : Motion = Motion.walk(0.6, 0.95, 0.2)

  private[this] val stretchMotion = Ref(stretchStable)

  private[this] val minPhaseDur =   3.0
  private[this] val minPhInsDur =   3.0
  private[this] val maxPhaseDur =  30.0 // 150.0
  private[this] val minPhaseLen = (SR * minPhaseDur).toLong
  private[this] val minPhInsLen = (SR * minPhInsDur).toLong
  private[this] val maxPhaseLen = (SR * maxPhaseDur).toLong

  private[this] val minStabDur      : Double =  10.0
  private[this] val stableDurProb   : Double =   3.0 / 100
  private[this] val ovrBoundaryProb : Double =   6.0 / 100

  def phSelectOverwrite()(implicit tx: InTxn): Future[OverwriteInstruction] = {
    log("phSelectOverwrite()")

    val ph0   = phFile()
    val len0  = ph0.numFrames

    val pDur = framesToSeconds(len0)
    val mStretch = if (pDur <= minPhaseDur) {
      stretchMotion.set(stretchGrow)
      // if (verbose) println(s"---pDur = ${formatSeconds(pDur)} -> grow")
      stretchGrow
    } else if (pDur >= maxPhaseDur) {
      stretchMotion.set(stretchShrink)
      // if (verbose) println(s"---pDur = ${formatSeconds(pDur)} -> shrink")
      stretchShrink
    } else if (pDur > minStabDur && random.nextDouble() < stableDurProb) {
      stretchMotion.set(stretchStable)
      // if (verbose) println(s"---pDur = ${formatSeconds(pDur)} -> stable")
      stretchStable
    } else {
      stretchMotion()
    }

    val fStretch  = mStretch.step()
    val useBound  = random.nextDouble() <= ovrBoundaryProb
    val boundEnd  = useBound && random.nextDouble() > 0.667
    val jitAmt    = random.nextDouble()

    val fut = if (len0 > minPhaseLen) SelectOverwrite(ph0.f, ctlCfg) else txFutureSuccessful(Span(0L, 0L))
    fut.map { span0 =>
      val span1       = if (!useBound) span0 else {
        if (boundEnd) Span(len0 - span0.length, len0)
        else          Span(0L, span0.length)
      }
      val span        = jitter(span1, r = jitAmt, secs = 0.2f, minStart = 0L, maxStop = len0)
      val newLength0  = max(minPhInsLen, (span.length * fStretch + 0.5).toLong)
      val newDiff0    = newLength0 - span.length
      val len1        = max(minPhaseLen, min(maxPhaseLen, len0 + newDiff0))
      val newDiff1    = len1 - len0
      val newLength   = newDiff1 + span.length
      val instr       = OverwriteInstruction(span, newLength = newLength)
      log(s"phSelectOverwrite() yields $instr")
      instr
    }
  }

  private def jitter(in: Span, r: Double, secs: Float, minStart: Long, maxStop: Long): Span = {
    val secs2   = secs * 2
    val dFrames = (r * secs2 - secs * SR).toLong
    val d       = max(minStart - in.start, min(maxStop - in.stop, dFrames))
    Span(in.start + d, in.stop + d)
  }

  def performOverwrite(instr: OverwriteInstruction, dbSpan: Span)(implicit tx: InTxn): Future[Unit] = {
    val dbPos     = dbSpan    .start
    val phPos     = instr.span.start
    val spliceLen = min(dbSpan.length, instr.newLength)
    
    require(instr.newLength == dbSpan.length)
    
    val dbCnt     = dbCount.transformAndGet(_ + 1)
    val db0       = dbFile()
//    val dbLen0    = dbLen()
    val db1F      = mkDbFile(dbCnt)
  
    val phCnt     = phCount.transformAndGet(_ + 1)
    val ph0       = phFile()
//    val phLen0    = phLen()
    val ph1F      = mkPhFile(phCnt)

    val insFdLen  = min(instr.span.length/2, min(spliceLen/2, (SR * 0.1).toInt)).toInt
    val remFdLen  =                          min(spliceLen/2, (SR * 0.1).toInt) .toInt

    log(s"performOverwrite(); dbPos $dbPos, phPos $phPos, spliceLen $spliceLen, insFdLen $insFdLen, remFdLen $remFdLen")

    val g = Graph {
      import de.sciss.fscape.graph._
      val inDb    = AudioFileIn(db0.f, numChannels = 1)
      val inDbW   = inDb  * (Seq[GE](1.0, 0.0): GE)
      val inPh0   = AudioFileIn(ph0.f, numChannels = 2)
      val inPh    = inPh0 + (Seq[GE](0.0, WitheringConstant): GE) // withering

      val insPre  = inPh.take(phPos)
      val insPost = inPh.drop(instr.span.stop)
      val insMid  = inDbW.drop(dbPos).take(spliceLen)

      val insCat  = if (insFdLen == 0) {
        insPre ++ insMid ++ insPost

      } else {
        // we could use .sqrt for left channel and linear for right channel;
        // for simplicity, just use linear for both
        val preOut  = inPh.drop(phPos).take(insFdLen) * Line(1, 0, insFdLen) // .sqrt
        val midIn   = insMid          .take(insFdLen) * Line(0, 1, insFdLen) // .sqrt
        val cross0  = preOut + midIn
        val cross1  = insMid.drop(insFdLen).take(spliceLen - 2*insFdLen)
        val midOut  = insMid.drop(spliceLen       - insFdLen)                * Line(1, 0, insFdLen) // .sqrt
        val postIn  = inPh  .drop(instr.span.stop - insFdLen).take(insFdLen) * Line(0, 1, insFdLen) // .sqrt
        val cross2  = midOut + postIn
        val cross   = cross0 ++ cross1 ++ cross2

        insPre ++ cross ++ insPost
      }
      AudioFileOut(ph1F, phAfSpec, in = insCat.clip2(1.0))

      val remPre  = inDb.take(dbPos)
      val remPost = inDb.drop(dbSpan.start + spliceLen)

      val remCat  = if (true /* XXX TODO remFdLen == 0 */) {
        remPre ++ remPost

      } else {
        ???
      }
      AudioFileOut(db1F, dbAfSpec, in = remCat)
    }

    render[Unit](ctlCfg, g) { implicit tx =>
      if (dbCount() == dbCnt) {
        val spec    = AudioFile.readSpec(db1F)
        val db1     = new AudioFileRef(db1F, spec.numFrames)
        val dbOld   = dbFile.swap(db1)
        dbOld.release()
      } else {
        log("performOverwrite() - not replacing db, has moved on")
      }

      if (phCount() == phCnt) {
        val spec    = AudioFile.readSpec(ph1F)
        val ph1     = new AudioFileRef(ph1F, spec.numFrames)
        val phOld   = phFile.swap(ph1)
        phOld.release()
      } else {
        log("performOverwrite() - not replacing ph, has moved on")
      }
    }
  }
}
