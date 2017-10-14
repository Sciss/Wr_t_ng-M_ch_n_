/*
 *  OverwriteInstruction.scala
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

package de.sciss.wrtng.sound

import de.sciss.span.Span

final case class OverwriteInstruction(span: Span, newLength: Long) {
  override def toString: String = {
    val s = formatSpan(span)
    val a = s"$productPrefix($s, newLength = $newLength)"
    if (span.length > 0) s"$a - ${formatPercent(newLength.toDouble / span.length)}" else a
  }
}