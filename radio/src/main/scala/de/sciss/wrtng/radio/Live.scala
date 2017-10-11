/*
 *  Live.scala
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
package radio

object Live {
/*

  Gqrx 2.6 remote control (TCP telnet-style) commands:

 f - Get frequency [Hz]
 F - Set frequency [Hz]
 m - Get demodulator mode
 M - Set demodulator mode (OFF, RAW, AM, FM, WFM, WFM_ST,
     WFM_ST_OIRT, LSB, USB, CW, CWL, CWU)
 l STRENGTH - Get signal strength [dBFS]
 l SQL - Get squelch threshold [dBFS]
 L SQL <sql> - Set squelch threshold to <sql> [dBFS]
 u RECORD - Get status of audio recorder
 U RECORD <status> - Set status of audio recorder to <status = 0|1>
 c - Close connection
 AOS - Acquisition of signal (AOS) event, start audio recording
 LOS - Loss of signal (LOS) event, stop audio recording
 \dump_state - Dump state (only usable for compatibility)

 Gqrx replies with:

 RPRT 0 - Command successful
 RPRT 1 - Command failed

 */

  def apply(config: Config): Live = ???
}
trait Live extends Source