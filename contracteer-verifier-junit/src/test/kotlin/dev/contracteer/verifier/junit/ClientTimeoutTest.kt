package dev.contracteer.verifier.junit

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.testkit.engine.EngineTestKit
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.test.Test

class ClientTimeoutTest {

  @Test
  @Timeout(10)
  fun `fails the case with a timeout failure when the server does not answer within the timeout`() {
    // When
    val results = EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(ServerNeverAnswering::class.java))
      .execute()

    // Then
    val failure = results.testEvents().failed().list().single()
    val message = failure.getRequiredPayload(TestExecutionResult::class.java).throwable.get().message?.trim()
    assert(message == "Request failed: no response from http://localhost:${ServerNeverAnswering.port} within 1s") {
      "Expected a timeout failure but got: $message"
    }
  }

  @Tag("engine-test-kit-target")
  class ServerNeverAnswering {

    @ContracteerTest(openApiDoc = "src/test/resources/api_with_one_operation.yaml", timeoutSeconds = 1)
    fun `verify contracts`() {
    }

    companion object {
      private lateinit var socket: ServerSocket
      private val connections = ConcurrentLinkedQueue<Socket>()

      @field:ContracteerServerPort
      var port: Int = 0

      @JvmStatic
      @BeforeAll
      fun startServer() {
        socket = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
        port = socket.localPort
        thread(isDaemon = true) { runCatching { generateSequence { socket.accept() }.forEach { connections.add(it) } } }
      }

      @JvmStatic
      @AfterAll
      fun stopServer() {
        connections.forEach { it.close() }
        socket.close()
      }
    }
  }
}
