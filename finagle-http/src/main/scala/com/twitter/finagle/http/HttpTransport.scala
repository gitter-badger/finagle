package com.twitter.finagle.http

import com.twitter.finagle
import com.twitter.finagle.transport.Transport
import com.twitter.finagle.http.codec.ConnectionManager
import com.twitter.util.{Future, Time}
import java.net.SocketAddress
import scala.util.control.NonFatal

/**
 * A Transport with close behavior managed by ConnectionManager.
 */
class HttpTransport(self: Transport[Any, Any], manager: ConnectionManager)
  extends Transport[Any, Any] {

  def this(self: Transport[Any, Any]) = this(self, new ConnectionManager)

  def close(deadline: Time): Future[Unit] = self.close(deadline)

  def read(): Future[Any] =
    self.read() onSuccess { m =>
      manager.observeMessage(m)
      if (manager.shouldClose)
        self.close()
    }

  def write(m: Any): Future[Unit] =
    try {
      manager.observeMessage(m)
      val f = self.write(m)
      if (manager.shouldClose) f before self.close()
      else f
    } catch {
      case NonFatal(e) => Future.exception(e)
    }

  def status: finagle.Status = 
    if (manager.shouldClose) finagle.Status.Closed
    else self.status

  def localAddress: SocketAddress = self.localAddress

  def remoteAddress: SocketAddress = self.remoteAddress

  private[finagle] def peerCertificate = self.peerCertificate

  val onClose: Future[Throwable] = self.onClose
}
