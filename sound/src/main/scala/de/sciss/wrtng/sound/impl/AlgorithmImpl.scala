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
import de.sciss.fscape.Graph
import de.sciss.fscape.stream.Control
import de.sciss.synth.io.AudioFileType.AIFF
import de.sciss.synth.io.SampleFormat.Int16
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.concurrent.{Future, Promise}
import scala.concurrent.stm.{InTxn, Ref, Txn, atomic}
import scala.util.control.NonFatal

import Main.log

final class AlgorithmImpl(client: OSCClient) extends Algorithm {
  import client.config

  /*

   */

  private[this] val dbDir     = config.baseDir / "database"
  private[this] val dbPattern = "db%06d.aif"
  private[this] val afSpec    = AudioFileSpec(AIFF, Int16, numChannels = 1, sampleRate = SR)

  private[this] val dbCount   = Ref.make[Int]()
  private[this] val dbLen     = Ref.make[Long]()
  private[this] val dbFile    = Ref.make[File]()

  private[this] val dbTargetLen   = (SR * 180).toLong
  private[this] val maxCaptureLen = (SR *  20).toLong
  private[this] val minCaptureLen = (SR *   4).toLong

  private[this] val ctlCfg    = {
    val c = Control.Config()
    c.actorSystem = ActorSystem("algorithm")
    c.useAsync    = false
    c.build
  }

  private def mkDbFile(i: Int): File = dbDir / dbPattern.format(i)

  def init(): this.type = {
    dbDir.mkdirs()
    val soundFiles  = dbDir.children(f => f.isFile && f.name.startsWith("db") && f.extL == "aif")
    val candidate   = soundFiles.filter(_.length() > 0L).sorted(File.NameOrdering).lastOption
    val toDelete    = soundFiles.toSet -- candidate
    toDelete.foreach(_.delete())
    val db0 = candidate.getOrElse {
      val f   = mkDbFile(0)
      val af  = AudioFile.openWrite(f, afSpec)
      af.close()
      f
    }
    atomic { implicit tx =>
      dbFile () = db0
      dbCount() = db0.base.substring(2).toInt
      val spec  = AudioFile.readSpec(db0)
      dbLen  () = spec.numFrames
    }
    this
  }

  def iterate()(implicit tx: InTxn): Future[Unit] = {
    log("iterate()")
    val futFill = dbFill()
    futFill.map(_ => ())
  }

  def dbFill()(implicit tx: InTxn): Future[Long] = {
    val len0    = dbLen()
    val captLen = math.min(maxCaptureLen, dbTargetLen - len0)
    if (captLen < minCaptureLen) Future.successful(len0)
    else {
      val captSec     = (captLen / SR).toFloat
      log(f"dbFill() - capture dur $captSec%g sec")
      val futFileApp  = client.queryRadioRec(captSec)
      futFileApp.flatMap { fileApp =>
        val spec      = AudioFile.readSpec(fileApp)
        val numFrames = math.min(spec.numFrames, captLen)
        atomic { implicit tx =>
          dbAppend(fileApp = fileApp, numFrames = numFrames).andThen {
            case _ => fileApp.delete()
          }
        }
      }
    }
  }

  def dbAppend(fileApp: File, offset: Long = 0L, numFrames: Long)(implicit tx: InTxn): Future[Long] = {
    val cnt   = dbCount.transformAndGet(_ + 1)
    val db0   = dbFile()
    val len0  = dbLen()
    val fdLen = math.min(len0, math.min(numFrames, (SR * 0.1).toInt)).toInt
    val db1   = mkDbFile(cnt)

    val g = Graph {
      import de.sciss.fscape.graph._
      val inDb    = AudioFileIn(db0    , numChannels = 1)
      val inApp   = AudioFileIn(fileApp, numChannels = 1)
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
      AudioFileOut(db1, afSpec, in = cat)
    }

    val p = Promise[Long]()
    Txn.afterCommit { _ =>
      try {
        val ctl = Control(ctlCfg)
        ctl.run(g)
        val futF = ctl.status.map { _ =>
          atomic { implicit tx =>
            if (dbCount() == cnt) {
              val dbOld   = dbFile.swap(db1)
              val spec    = AudioFile.readSpec(db1)
              dbLen()     = spec.numFrames
              Txn.afterCommit(_ => dbOld.delete())
              spec.numFrames
            } else {
              dbLen()
            }
          }
        }
        p.completeWith(futF)
      } catch {
        case NonFatal(ex) => p.failure(ex)
      }
    }

    p.future
  }
}
