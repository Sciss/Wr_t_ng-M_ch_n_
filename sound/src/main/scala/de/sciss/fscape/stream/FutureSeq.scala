/*
 *  FutureSeq.scala
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

package de.sciss.fscape
package stream

import akka.stream.{Attributes, Inlet, Outlet, SinkShape}
import de.sciss.fscape.stream.impl.{NodeImpl, Sink1Impl, StageImpl}
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.Success

object FutureSeq {
  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], p: Promise[Vec[A]])
                                       (implicit b: Builder): Unit = {
    val stage0  = new Stage[A, E](b.layer, p)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "FutureSeq"

  private type Shape[E <: BufLike] = SinkShape[E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer, p: Promise[Vec[A]])
                                                       (implicit ctrl: Control)
    extends StageImpl[Shape[E]](name) {

    val shape = new SinkShape(
      in = Inlet[E](s"$name.in")
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer, p)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[E], layer: Layer,
                                                        p: Promise[Vec[A]])
                                                       (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with Sink1Impl[E] {

    private[this] val builder = Vector.newBuilder[A]

    protected override def stopped(): Unit = {
      super.stopped()
      val vec = builder.result()
      p.tryComplete(Success(vec))
    }

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in)) {
          logStream(s"completeStage() $this")
          completeStage()
        }
        return
      }

      logStream(s"process() $this")

      val stop0   = readIns()
      val b0      = bufIn0.buf
      if (stop0 > 0) {
        var i = 0
        while (i < stop0) {
          val x = b0(i)
          builder += x
          i += 1
        }
      }
    }
  }
}