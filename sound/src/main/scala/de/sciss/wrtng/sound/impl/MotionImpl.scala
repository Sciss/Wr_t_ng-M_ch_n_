/*
 *  MotionImpl.scala
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
package impl

import de.sciss.synth

import scala.concurrent.stm.{InTxn, Ref}
import scala.math.{exp, max, min}

object MotionImpl {
  
  def const(value: Double): Motion = Constant(value)

  def linRand(lo: Double, hi: Double)(implicit random: TxRnd): Motion = LinRand(lo, hi)

  def expRand(lo: Double, hi: Double)(implicit random: TxRnd): Motion = ExpRand(lo, hi)

  def sine(lo: Double, hi: Double, period: Int): Motion = Sine(lo, hi, period)

  def walk(lo: Double, hi: Double, maxStep: Double)(implicit random: TxRnd): Motion = Walk(lo, hi, maxStep)

  def linLin(in: Motion, inLo: Double, inHi: Double, outLo: Double, outHi: Double): Motion =
    LinLin(in, inLo, inHi, outLo, outHi)

  def linExp(in: Motion, inLo: Double, inHi: Double, outLo: Double, outHi: Double): Motion =
    LinExp(in, inLo, inHi, outLo, outHi)

  def coin(prob: Double, a: Motion, b: Motion)(implicit random: TxRnd): Motion = Coin(prob, a, b)

  private final case class Constant(value: Double) extends Motion {
    def step()(implicit tx: InTxn): Double = value
  }

  private final case class LinRand(lo: Double, hi: Double)(implicit random: TxRnd) 
    extends Motion {
   
    private[this] val range = hi - lo

    def step()(implicit tx: InTxn): Double = random.nextDouble() * range + lo
  }

  private final case class ExpRand(lo: Double, hi: Double)(implicit random: TxRnd)
    extends Motion {
   
    private[this] val factor = math.log(hi / lo)

    def step()(implicit tx: InTxn): Double = exp(random.nextDouble() * factor) * lo
  }

  private final case class Walk(lo: Double, hi: Double, maxStep: Double)(implicit random: TxRnd)
    extends Motion {
    
    private[this] val maxStep2 = maxStep * 2
    private[this] val current = Ref(Double.NaN)

    def step()(implicit tx: InTxn): Double = {
      val c = current()
      val r = random.nextDouble()
      val v = if (c.isNaN) {
        r * (hi - lo) + lo
      } else {
        max(lo, min(hi, c + (r * maxStep2 - maxStep)))
      }
      current.set(v)
      v
    }
  }

  private final case class Sine(lo: Double, hi: Double, period: Int) extends Motion {
    private[this] val phase   = Ref(0)
    private[this] val mul     = (hi - lo) / 2
    private[this] val add     = mul + lo
    private[this] val factor  = math.Pi * 2 / period

    def step()(implicit tx: InTxn): Double = {
      val p = phase()
      phase.set((p + 1) % period)
      math.sin(p * factor) * mul + add
    }
  }

  private final case class Coin(prob: Double, a: Motion, b: Motion)(implicit random: TxRnd)
    extends Motion {
    def step()(implicit tx: InTxn): Double = {
      val m = if (random.nextDouble() >= prob) a else b
      m.step()
    }
  }

  private final case class LinLin(in: Motion, inLo: Double, inHi: Double, outLo: Double, outHi: Double)
    extends Motion {
    
    def step()(implicit tx: InTxn): Double = {
      import synth._
      in.step().linLin(inLo, inHi, outLo, outHi)
    }
  }

  private final case class LinExp(in: Motion, inLo: Double, inHi: Double, outLo: Double, outHi: Double)
    extends Motion {
    def step()(implicit tx: InTxn): Double = {
      import synth._
      in.step().linLin(inLo, inHi, outLo, outHi)
    }
  }
}