package de.sciss.wrtng
package radio

import java.net.SocketAddress
import java.nio.ByteBuffer

import de.sciss.file.File

import scala.concurrent.stm.{InTxn, Txn}

final class UpdateRadioSource(val uid: Int, c: OSCClient, target: SocketAddress, file: File)
  extends UpdateSource(c, target, file) {

  @volatile
  var timeout: Task = _

  def begin(): Unit =
    reply(Network.OscRadioRecDone(uid = uid, size = size))

  protected def sendData(offset: Long, bytes: ByteBuffer): Unit =
    reply(Network.OscRadioRecSet(uid = uid, offset = offset, bytes = bytes))

  override def dispose(): Unit = {
    super.dispose()
    file.delete()
  }

  def disposeTxn()(implicit tx: InTxn): Unit = {
    val to = timeout
    if (to != null) timeout.cancel()
    Txn.afterCommit(_ => dispose())
  }
}
