package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{BufL, StreamIn}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Promise

/** Hackish solution to capture values for future use.
  * Hackish because the graph can only be used once.
  */
final case class FutureLong(in: GE, p: Promise[Vec[Long]]) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in) = args
    stream.FutureSeq[Long, BufL](in = in.toLong, p)
  }
}
