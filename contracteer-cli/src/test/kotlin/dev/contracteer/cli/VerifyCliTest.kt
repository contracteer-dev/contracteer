package dev.contracteer.cli

import com.fasterxml.jackson.databind.ObjectMapper
import dev.contracteer.core.Result.Success
import dev.contracteer.core.swagger.OpenApiLoader
import dev.contracteer.mockserver.MockServer
import org.junit.jupiter.api.Timeout
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.test.Test

class VerifyCliTest {

  private val json = ObjectMapper()

  @Test
  fun `json format prints one report and exits with 0 when every case passes`() {
    // When
    val execution = withMockServer(ORDERS) { baseUrl ->
      execute("verify", ORDERS, "-u", baseUrl, "--format", "json")
    }

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 0)
    assert(report["summary"]["cases"].asInt() > 0)
    assert(report["summary"]["failed"].asInt() == 0)
  }

  @Test
  fun `json format prints one report and exits with 1 when a case fails`() {
    // When
    val execution = withMockServer(ORDERS_WITH_TEXT_ID) { baseUrl ->
      execute("verify", ORDERS, "-u", baseUrl, "--format", "json")
    }

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 1)
    assert(report["summary"]["failed"].asInt() > 0)
    assert(report["cases"].any { it["status"].asText() == "failed" })
  }

  @Test
  fun `json format prints the load report and exits with 1 when the document does not load`() {
    // When
    val execution = execute("verify", MISSING_DOCUMENT, "--format", "json")

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 1)
    assert(report["status"].asText() == "failed")
    assert(report["source"].asText() == MISSING_DOCUMENT)
  }

  @Test
  fun `json format keeps the report alone on stdout and writes the http traffic to stderr`() {
    // When
    val execution = withMockServer(ORDERS) { baseUrl ->
      execute("verify", ORDERS, "-u", baseUrl, "--format", "json", "-t")
    }

    // Then
    val report = json.readTree(execution.stdout)
    assert(report["summary"]["failed"].asInt() == 0)
    assert(execution.stderr.contains(">> GET"))
  }

  @Test
  fun `an unknown format prints nothing on stdout and exits with 2`() {
    // When
    val execution = execute("verify", ORDERS, "--format", "xml")

    // Then
    assert(execution.exitCode == 2)
    assert(execution.stdout.isEmpty())
  }

  @Test
  @Timeout(10)
  fun `json format fails the case with a timeout failure when the server does not answer within the timeout`() {
    // When
    val (baseUrl, execution) = withServerNeverAnswering { port ->
      val baseUrl = "http://localhost:$port"
      baseUrl to execute("verify", ORDERS, "-u", baseUrl, "--format", "json", "--timeout", "1")
    }

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 1)
    assert(report["cases"].single()["diagnostics"].single()["message"].asText() ==
             "Request failed: no response from $baseUrl within 1s")
  }

  @Test
  fun `a timeout that is not positive prints nothing on stdout and exits with 2`() {
    // When
    val execution = execute("verify", ORDERS, "--timeout", "0")

    // Then
    assert(execution.exitCode == 2)
    assert(execution.stdout.isEmpty())
    assert(execution.stderr.contains("'0' is not a positive whole number of seconds"))
  }

  @Test
  fun `text format exits with 0 when every case passes`() {
    // When
    val execution = withMockServer(ORDERS) { baseUrl ->
      execute("verify", ORDERS, "-u", baseUrl)
    }

    // Then
    assert(execution.exitCode == 0)
  }

  @Test
  fun `text format exits with 1 when the document does not load`() {
    // When
    val execution = execute("verify", MISSING_DOCUMENT)

    // Then
    assert(execution.exitCode == 1)
  }

  private fun <T> withMockServer(document: String, block: (String) -> T): T {
    val operations = OpenApiLoader.loadOperations(document) as Success
    val mockServer = MockServer(operations.value)
    mockServer.start()
    return try {
      block("http://localhost:${mockServer.port()}")
    } finally {
      mockServer.stop()
    }
  }

  private fun <T> withServerNeverAnswering(block: (port: Int) -> T): T =
    ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { socket ->
      val connections = ConcurrentLinkedQueue<Socket>()
      thread(isDaemon = true) { runCatching { generateSequence { socket.accept() }.forEach { connections.add(it) } } }
      try {
        block(socket.localPort)
      } finally {
        connections.forEach { it.close() }
      }
    }

  private companion object {
    const val ORDERS = "classpath:verify/orders.yaml"
    const val ORDERS_WITH_TEXT_ID = "classpath:verify/orders_with_text_id.yaml"
    const val MISSING_DOCUMENT = "classpath:verify/missing.yaml"
  }
}
