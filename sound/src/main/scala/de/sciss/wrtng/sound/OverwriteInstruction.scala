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
  def printFormat: String = {
    val s = formatSpan(span)
    val p = if (span.length > 0) formatPercent(newLength.toDouble / span.length) else newLength.toString
    s"over($s -> $p)"
  }
}