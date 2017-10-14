/*
 *  Motion.scala
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

import impl.{MotionImpl => Impl}

import scala.concurrent.stm.InTxn

object Motion {
  def const(value: Double): Motion = Impl.const(value)

  def linrand(lo: Double, hi: Double)(implicit random: TxRnd): Motion = Impl.linrand(lo, hi)

  def exprand(lo: Double, hi: Double)(implicit random: TxRnd): Motion = Impl.exprand(lo, hi)

  def sine(lo: Double, hi: Double, period: Int): Motion = Impl.sine(lo, hi, period)

  def walk(lo: Double, hi: Double, maxStep: Double)(implicit random: TxRnd): Motion = Impl.walk(lo, hi, maxStep)

  def linlin(in: Motion, inLo: Double, inHi: Double, outLo: Double, outHi: Double): Motion =
    Impl.linlin(in, inLo, inHi, outLo, outHi)

  def linexp(in: Motion, inLo: Double, inHi: Double, outLo: Double, outHi: Double): Motion =
    Impl.linexp(in, inLo, inHi, outLo, outHi)

  def coin(prob: Double, a: Motion, b: Motion)(implicit random: TxRnd): Motion = Impl.coin(prob, a, b)
}

trait Motion {
  def step()(implicit tx: InTxn): Double
}