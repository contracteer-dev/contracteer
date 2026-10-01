package dev.contracteer.verifier

import org.http4k.routing.RoutingHttpHandler
import org.http4k.server.SunHttp
import org.http4k.server.asServer

internal fun <T> withHttpServer(routes: RoutingHttpHandler, block: (port: Int) -> T): T {
  val server = routes.asServer(SunHttp(0)).start()
  try {
    return block(server.port())
  } finally {
    server.stop()
  }
}
