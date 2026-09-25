package dev.contracteer.mockserver

import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.I_M_A_TEAPOT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import dev.contracteer.core.operation.ApiOperation

/**
 * An HTTP mock server that serves responses derived from OpenAPI [ApiOperation] definitions.
 *
 * The mock server validates incoming requests against the operation's request schema,
 * matches scenarios for deterministic responses, and falls back to schema-generated
 * random values when no scenario matches.
 *
 * @param operations the API operations to serve
 * @param port the port to listen on, or 0 for a random available port
 */
class MockServer @JvmOverloads constructor(private val operations: List<ApiOperation>,
                                           private val port: Int = 0) {

  private lateinit var http4kServer: Http4kServer
  private val logger = KotlinLogging.logger {}
  private val httpLogger = KotlinLogging.logger("dev.contracteer.http")

  /** Starts the mock server. */
  fun start() {
    val routeHandlers = operations
      .sortedByDescending { literalSegmentLength(it.path) }
      .onEach { logger.info { "Registering route: [${it.method.uppercase()}] ${it.path}" } }
      .map { createRouteHandler(it) }
    logStartupReport()

    logger.info { "Starting Contracteer mock server" }
    http4kServer = routes(*routeHandlers.toTypedArray()).asServer(SunHttp(port)).start()
    logger.info { "Contracteer mock server started on port ${this.port()}" }
  }

  private fun logStartupReport() {
    val report = startupReport(operations)
    report.warnings.forEach { line -> logger.warn { line } }
    report.details.forEach { line -> logger.debug { line } }
  }

  // Sort routes by literal-segment length so concrete paths win over template paths sharing
  // a common prefix (e.g. `/products/special` registers before `/products/{id}`).
  private fun literalSegmentLength(path: String): Int = path.replace(PATH_PARAM_PATTERN, "").length

  /** Stops the mock server. */
  fun stop() {
    if (::http4kServer.isInitialized) {
      logger.info { "Stopping Contracteer mock server" }
      http4kServer.stop()
      logger.info { "Stopped Contracteer mock server" }
    }
  }

  /** Returns the port the server is listening on. Must be called after [start]. */
  fun port(): Int {
    check(::http4kServer.isInitialized) { "Contracteer mock server is not started yet." }
    return http4kServer.port()
  }

  private fun createRouteHandler(operation: ApiOperation): RoutingHttpHandler {
    return StrictPathRouter(operation.path, Method.valueOf(operation.method.uppercase())) { request ->
      handle(request, operation)
    } bind { _ -> Response(NOT_FOUND) }
  }

  private fun handle(request: Request, operation: ApiOperation): Response {
    httpLogger.debug { formatRequest(request) }

    val response = RequestHandler.handle(request, operation)
    httpLogger.debug { formatResponse(response) }

    if (response.status == I_M_A_TEAPOT) {
      httpLogger.warn {
        "Request handling failed for [${operation.method.uppercase()}] ${operation.path}\n${
          formatRequest(request)
        }\n${formatResponse(response)}"
      }
      httpLogger.warn { "Enable DEBUG logging for 'dev.contracteer.http' to see all HTTP traffic" }
    }

    return response
  }

  private fun formatRequest(request: Request): String {
    val headers = request.headers.joinToString("\n") { (name, value) -> ">> $name: $value" }
    val body = request.bodyString().ifEmpty { "(none)" }
    return buildString {
      append(">> ${request.method} ${request.uri}")
      if (headers.isNotEmpty()) append("\n$headers")
      append("\n>> Body: $body")
    }
  }

  private fun formatResponse(response: Response): String {
    val status = response.status
    val statusLine = if (status.description.isNotBlank()) "${status.code} ${status.description}" else "${status.code}"
    val headers = response.headers.joinToString("\n") { (name, value) -> "<< $name: $value" }
    val body = response.bodyString().ifEmpty { "(none)" }
    return buildString {
      append("<< $statusLine")
      if (headers.isNotEmpty()) append("\n$headers")
      append("\n<< Body: $body")
    }
  }

  companion object {
    private val PATH_PARAM_PATTERN = Regex("\\{[^}]+}")
  }
}
