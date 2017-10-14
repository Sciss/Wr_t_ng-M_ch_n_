/*
 *  MainFrame.scala
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
package control

import java.util.Comparator
import javax.swing.table.{AbstractTableModel, DefaultTableCellRenderer, TableCellRenderer, TableRowSorter}
import javax.swing.{ButtonGroup, Icon, JTable, SwingConstants}

import de.sciss.desktop.{FileDialog, OptionPane}
import de.sciss.file._
import de.sciss.osc
import de.sciss.swingplus.Table

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Table.AutoResizeMode
import scala.swing.event.{ButtonClicked, TableRowsSelected, ValueChanged}
import scala.swing.{BorderPanel, BoxPanel, Button, Component, FlowPanel, Frame, Label, Orientation, ScrollPane, Slider, Swing, ToggleButton}

class MainFrame(c: OSCClient) {
  private case class Column(idx: Int, name: String, minWidth: Int, prefWidth: Int, maxWidth: Int,
                            extract: Status => Any, cellRenderer: Option[TableCellRenderer] = None,
                            sorter: Option[Comparator[_]] = None, headerRenderer: Option[TableCellRenderer] = None)

  private[this] val RightAlignedRenderer = {
    val res = new DefaultTableCellRenderer
    res.setHorizontalAlignment(SwingConstants.TRAILING)
    res
  }

  private object AmountRenderer extends DefaultTableCellRenderer with Icon {
    setIcon(this)

    private[this] var amount = 0.0

    override def setValue(value: AnyRef): Unit = {
      amount = if (value == null) 0.0 else value match {
        case i: java.lang.Double  => i.doubleValue()
        case _                    => 0.0
      }
    }

    def getIconWidth  = 21
    def getIconHeight = 16

    def paintIcon(c: java.awt.Component, g: java.awt.Graphics, x: Int, y: Int): Unit = {
      g.setColor(getForeground)
      var xi = x + 1
      val y0 = y + 2
      val y1 = y + 14
      val xn = (x + amount * 70).toInt // 2
      while (xi < xn) {
        g.drawLine(xi, y0, xi, y1)
        xi += 2
      }
    }
  }

  private val columns: Array[Column] = Array(
    Column( 0, "Pos"    , 64,  64,  64, _.pos     , Some(RightAlignedRenderer), Some(Ordering.Int)),
    Column( 1, "Id"     , 64,  64,  64, _.dot     , Some(RightAlignedRenderer), Some(Ordering.Int)),
    Column( 2, "Version", 64, 360, 360, _.version , None, None),
    Column( 3, "Update" , 74,  74,  74, _.update  , Some(AmountRenderer)      , Some(Ordering.Double))
  )

  private object model extends AbstractTableModel {
    private[this] var _instances = Vector.empty[Status]

    def getRowCount   : Int = _instances.size
    def getColumnCount: Int = columns.length

    def instances: Vec[Status] = _instances

    override def getColumnName(colIdx: Int): String = columns(colIdx).name

    def clear(): Unit = {
      val sz = _instances.size
      if (sz > 0) {
        _instances = Vector.empty
        fireTableRowsDeleted(0, sz - 1)
      }
    }

    def += (status: Status): Unit = {
      val row = _instances.size
      _instances :+= status
      fireTableRowsInserted(row, row)
    }

    def -= (status: Status): Unit = {
      val row = _instances.indexOf(status)
      if (row < 0) throw new IllegalArgumentException(s"Status $status was not in table")
      _instances = _instances.patch(row, Nil, 1)
      fireTableRowsDeleted(row, row)
    }

    def update(status: Status): Unit = {
      val row = _instances.indexWhere(_.dot == status.dot)
      if (row < 0) throw new IllegalArgumentException(s"Dot ${status.dot} was not occupied")
      _instances = _instances.updated(row, status)
      fireTableRowsUpdated(row, row)
    }

    def getValueAt(rowIdx: Int, colIdx: Int): AnyRef = {
      val status  = _instances(rowIdx)
      val col     = columns(colIdx)
      col.extract(status).asInstanceOf[AnyRef]
    }
  }

  c.addListener {
    case OSCClient.Added  (status) => Swing.onEDT(model += status)
    case OSCClient.Removed(status) => Swing.onEDT(model -= status)
    case OSCClient.Changed(status) => Swing.onEDT(model.update(status))
  }

  c.instances.foreach(model += _)

  private[this] val table: Table = {
    val res = new Table {
      // https://github.com/scala/scala-swing/issues/47
      override lazy val peer: JTable = new JTable with SuperMixin
    }
    res.model   = model
    val resJ    = res.peer
    val cm      = resJ.getColumnModel
    val sorter  = new TableRowSorter(model)
    columns.foreach { col =>
      val tc = cm.getColumn(col.idx)
      col.sorter.foreach(sorter.setComparator(col.idx, _))
      tc.setMinWidth      (col.minWidth )
      tc.setMaxWidth      (col.maxWidth )
      tc.setPreferredWidth(col.prefWidth)
      col.cellRenderer  .foreach(tc.setCellRenderer  )
      col.headerRenderer.foreach(tc.setHeaderRenderer)
    }
    // cm.setColumnMargin(6)
    resJ.setRowSorter(sorter)
    // cf. http://stackoverflow.com/questions/5968355/horizontal-bar-on-jscrollpane/5970400
    res.autoResizeMode = AutoResizeMode.Off
    // resJ.setPreferredScrollableViewportSize(resJ.getPreferredSize)

    res.listenTo(res.selection)
    //    res.selection.elementMode = ...
    res.reactions += {
      case TableRowsSelected(_, _, false) => selectedChanged()
    }

    res
  }

  private def selection: Vec[Status] = {
    val xs      = model.instances
    val rows    = table.selection.rows
    val res     = rows.iterator.map { vi =>
      val mi = table.viewToModelRow(vi)
      xs(mi)
    } .toIndexedSeq
    res
  }

  private[this] val ggRefresh = Button("Refresh List") {
    // XXX TODO --- restart time-out timer that removes instances which do not respond
    //    model.clear()
    c ! Network.OscQueryVersion
  }

  private[this] var lastUpdate = Option.empty[File]

  private[this] val ggUpdate = Button("Update Software...") {
    val instances = selection
    if (instances.nonEmpty) {
      val isSound = instances.head.version.contains("sound")
      val dir = lastUpdate.flatMap(_.parentOption).getOrElse {
        userHome / "Documents" / "devel" / "Wr_t_ng-M_ch_n_" / (if (isSound) "sound" else "radio") / "target"
      }
      val candidates  = dir.children(_.ext == "deb")
      // this also works for `-SNAPSHOT_all.deb` vs. `_all.deb`
      val sorted      = candidates.sorted(File.NameOrdering)
      val init        = sorted.lastOption.orElse(if (dir.isDirectory) Some(dir) else None)
      val dlg         = FileDialog.open(init = init, title = "Select .deb file")
      dlg.show(None).foreach { debFile =>
        lastUpdate = Some(debFile)
        c.beginUpdates(debFile, instances)
      }
    }
  }

  private[this] val ggReboot = Button("Reboot") {
    selection.foreach { instance =>
      c.sendNow(Network.OscReboot, instance.socketAddress)
    }
  }

  private[this] val ggShutdown = Button("Shutdown") {
    selection.foreach { instance =>
      c.sendNow(Network.OscShutdown, instance.socketAddress)
    }
  }

  private[this] val ggTestRec = Button("Test Rec") {
    selection.foreach { instance =>
      c.sendNow(osc.Message("/test_rec", Util.nextUniqueID(): Int, 4f), instance.socketAddress)
    }
  }

  private[this] val ggServerInfo = Button("Server Info") {
    selection.foreach { instance =>
      c.sendNow(osc.Message("/server-info"), instance.socketAddress)
    }
  }

  private[this] val ggIterate = Button("Iterate") {
    selection.foreach { instance =>
      c.sendNow(Network.OscIterate, instance.socketAddress)
    }
  }

  private[this] var shellString = "df"

  private[this] val ggShell = Button("Shell...") {
    val sel = selection
    if (sel.nonEmpty) {
      val opt = OptionPane.textInput("Shell command", initial = shellString)
      opt.show(None).foreach { cmdS =>
        shellString = cmdS
        val cmd = cmdS.split(" ")
        sel.foreach { instance =>
          c.sendNow(Network.OscShell(cmd), instance.socketAddress)
        }
      }
    }
  }

  private def ToggleButton(title: String, init: Boolean)(fun: Boolean => Unit): ToggleButton =
    new ToggleButton(title) {
      if (init) selected = true
      listenTo(this)
      reactions += {
        case ButtonClicked(_) => fun(selected)
      }
    }

  private[this] val ggSoundOff    = new ToggleButton("Off"  )
  private[this] val ggSoundPing   = new ToggleButton("Ping" )
  private[this] val ggSoundNoise  = new ToggleButton("Noise")

  private[this] val gSound = new ButtonGroup
  gSound.add(ggSoundOff     .peer)
  gSound.add(ggSoundPing    .peer)
  gSound.add(ggSoundNoise   .peer)

  private[this] val ggBees = ToggleButton("Bees", init = true) { onOff =>
    selection.foreach { instance =>
      c.sendNow(osc.Message("/bees", onOff), instance.socketAddress)
    }
  }

  private def selectedChanged(): Unit = {
    val hasSelection    = selection.nonEmpty
    ggUpdate  .enabled  = hasSelection
    ggReboot  .enabled  = hasSelection
    ggShutdown.enabled  = hasSelection
  }

  selectedChanged()

  private[this] val ggAmp = new Slider {
    min   = -60
    max   =   0
    value = -12
    listenTo(this)
    reactions += {
      case ValueChanged(_) =>
        import de.sciss.numbers.Implicits._
        val amp = if (value == min) 0f else value.dbamp
        c ! Network.OscSetVolume(amp)
    }
  }

  private[this] val ggRepeat      = new ToggleButton("Repeat")

  private[this] var lastChan      = -1
  private[this] val timRepeat     = new javax.swing.Timer(1500, Swing.ActionListener { _ =>
    if (lastChan >= 0) mkTest(lastChan)
  })
  timRepeat.setRepeats(false)

  private[this] val pButtons1 = new FlowPanel(ggRefresh, ggUpdate, ggReboot, ggShutdown, ggTestRec,
    ggShell, ggServerInfo, ggIterate)
  private[this] val pButtons2 = new FlowPanel(
    ggBees, new Label("Sound:"), ggSoundOff, ggSoundPing, ggSoundNoise, ggRepeat)
  private[this] val pChannels = new FlowPanel(Seq.tabulate(2 /* 12 */) { ch =>
    Button((ch + 1).toString) {
      mkTest(ch)
    }
  }: _*)

  private def mkTest(ch: Int): Unit = {
    lastChan = ch
    selection.foreach { instance =>
      val tpe = if (ggSoundPing.selected) 0 else if (ggSoundNoise.selected) 1 else -1
      c.sendNow(osc.Message("/test-channel", ch, tpe), instance.socketAddress)

      if (ggRepeat.selected) {
        timRepeat.restart()
      }
    }
  }

//  private[this] val amps = new Amp(userHome / "Documents")
//
//  private[this] val pChanAmps = Vector.tabulate(Amp.numChannels) { ch =>
//    import de.sciss.numbers.Implicits._
//    new Slider {
//      min               = -60
//      max               =   0
//      value             = (amps.volumes(ch).ampdb * 0.5).toInt
//      paintTicks        = true
//      paintLabels       = true
//      majorTickSpacing  = 6
//      orientation       = Orientation.Vertical
//      reactions += {
//        case ValueChanged(_) =>
//          import de.sciss.numbers.Implicits._
//          val amp = if (value == min) 0f else value.dbamp
//          amps.volumes(ch) = amp
//      }
//    }
//  }
//
//  private[this] val ggSendChanAmps = Button("Send Amps") {
//    selection.foreach { instance =>
//      c.tx.send(Network.OscAmpChanVolume(amps.volumes), instance.socketAddress)
//    }
//  }
//
//  private[this] val ggSaveChanAmps = Button("Save Amps") {
//    amps.save()
//    selection.foreach { instance =>
//      c.tx.send(Network.OscSaveAmpChan(), instance.socketAddress)
//    }
//  }

  private[this] val pBottom = new BoxPanel(Orientation.Vertical) {
    contents += pButtons1
    contents += pButtons2
    contents += pChannels
    contents += new FlowPanel(new Label("Main Vol."), ggAmp)
//    contents += new FlowPanel(pChanAmps.zipWithIndex.map { case (slider, ch) =>
//      new BoxPanel(Orientation.Vertical) {
//        contents += slider
//        contents += new Label((ch + 1).toString)
//      }
//    }: _*)
//    contents += new FlowPanel(ggSendChanAmps, ggSaveChanAmps)
  }

  private[this] val component: Component = {
    val scroll = new ScrollPane(table)
    scroll.peer.putClientProperty("styleId", "undecorated")
    scroll.preferredSize = {
      val d = scroll.preferredSize
      d.width = math.min(540, table.preferredSize.width)
      d
    }
    new BorderPanel {
      add(scroll , BorderPanel.Position.Center )
      add(pBottom, BorderPanel.Position.South  )
    }
  }


  /* private[this] val frame = */ new Frame {
    override def closeOperation(): Unit =
      sys.exit(0)

    title = Main.namePkg
    contents = component
    pack().centerOnScreen()
    open()
  }
}