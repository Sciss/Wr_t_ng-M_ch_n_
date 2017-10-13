package de.sciss.wrtng

import java.net.SocketAddress

trait Node {
  def dot: Int

  def socketAddress: SocketAddress
}
