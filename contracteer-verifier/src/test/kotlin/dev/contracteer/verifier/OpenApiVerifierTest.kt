package dev.contracteer.verifier

import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import dev.contracteer.core.datatype.GenerationOutcome
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.cyclicObjectType
import dev.contracteer.core.dsl.form
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import java.net.ServerSocket
import kotlin.test.Test

class OpenApiVerifierTest {

  @Test
  fun `verifies scenario based case successfully`() {
    // Given
    val apiOperation = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
      }

      response(200) {
        jsonBody(objectType {
          properties {
            "id" to integerType()
            "name" to stringType()
          }
        })
      }
      response(404) {}

      scenario("validUser", status = 200) {
        request { pathParam["id"] = 1 }
        response { jsonBody { "id" to 1; "name" to "John" } }
      }
      scenario("notFound", status = 404) {
        request { pathParam["id"] = 999 }
      }
    }

    val app = routes(
      "/users/1" bind GET to {
        Response(OK).header("Content-Type", "application/json").body("""{"id": 1, "name": "John"}""")
      },
      "/users/999" bind GET to {
        Response(NOT_FOUND)
      }
    )

    // When
    val results = withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.map { verifier.verify(it) }
    }

    // Then
    assert(results.size == 2)
    assert(results.all { it.result.isSuccess() })
  }

  @Test
  fun `generates missing request parameter values for scenario based case`() {
    // Given
    val apiOperation = apiOperation("GET", "/users/{userId}/orders/{orderId}") {
      request {
        pathParam("userId", integerType())
        pathParam("orderId", integerType())
      }

      response(200) {
        jsonBody(objectType {
          properties {
            "id" to integerType()
            "name" to stringType()
          }
        })
      }

      scenario("validOrder", status = 200) {
        request { pathParam["userId"] = 1 }
        response { jsonBody { "id" to 1; "name" to "Order" } }
      }
    }

    val app = routes(
      "/users/{userId}/orders/{orderId}" bind GET to { request ->
        val orderId = request.uri.path.split("/").last()
        if (orderId.matches(Regex("-?\\d+")))
          Response(OK).header("Content-Type", "application/json").body("""{"id": 1, "name": "Order"}""")
        else
          Response(NOT_FOUND)
      }
    )

    // When
    val results = withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.map { verifier.verify(it) }
    }

    // Then
    assert(results.size == 1)
    assert(results.all { it.result.isSuccess() })
  }

  @Test
  fun `returns failure when verification fails`() {
    // Given
    val apiOperation = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
      }

      response(200) {
        jsonBody(objectType {
          properties {
            "id" to integerType()
            "name" to stringType()
          }
        })
      }

      scenario("validUser", status = 200) {
        request { pathParam["id"] = 1 }
        response { jsonBody { "id" to 1; "name" to "John" } }
      }
    }

    val app = routes(
      "/users/{id}" bind GET to {
        Response(OK).header("Content-Type", "application/json").body("""{"id": "invalid", "name": "John"}""")
      }
    )

    // When
    val results = withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.map { verifier.verify(it) }
    }

    // Then
    assert(results.size == 1)
    assert(results[0].result.isFailure())
    assert(results[0].result.errors().isNotEmpty())
  }

  @Test
  fun `sends query parameter with reserved characters unencoded when allowReserved is true`() {
    // Given
    var capturedRawQuery: String? = null

    val app = routes(
      "/search" bind GET to { request ->
        capturedRawQuery = request.uri.query
        Response(OK).header("Content-Type", "application/json").body("""{"id": 1}""")
      }
    )

    val apiOperation = apiOperation("GET", "/search") {
      request {
        queryParam("callback", stringType(), isRequired = true, codec = form(allowReserved = true))
      }

      response(200) {
        jsonBody(objectType {
          properties { "id" to integerType() }
        })
      }

      scenario("withCallback", status = 200) {
        request { queryParam["callback"] = "https://example.com/cb?token=abc" }
        response { jsonBody { "id" to 1 } }
      }
    }

    // When
    withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.forEach { verifier.verify(it) }
    }

    // Then
    assert(capturedRawQuery != null)
    // Reserved characters (:, /, ?) should NOT be percent-encoded
    assert(capturedRawQuery!!.contains("https://example.com/cb?token=abc")) {
      "Expected reserved characters to be unencoded, but got: $capturedRawQuery"
    }
  }

  @Test
  fun `url-encodes path parameter values containing URI-illegal characters`() {
    // Given
    var capturedId: String? = null

    // Pattern guarantees at least one URI-illegal character (|, \, or >)
    val apiOperation = apiOperation("GET", "/resources/{id}") {
      request {
        pathParam("id", stringType(name = "id", pattern = """[a-z]+[|\\>][a-z]+"""))
      }

      response(200) {
        jsonBody(objectType {
          properties { "id" to integerType() }
        })
      }
    }

    val app = routes(
      "/resources/{id}" bind GET to { request ->
        capturedId = request.path("id")
        Response(OK).header("Content-Type", "application/json").body("""{"id": 1}""")
      }
    )

    // When
    val results = withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.map { verifier.verify(it) }
    }

    // Then
    assert(results.isNotEmpty())
    assert(capturedId != null) { "Server should have received the request with encoded path" }
    assert(capturedId!!.contains(Regex("""[|\\>]""")))
  }

  @Test
  fun `generates random body when scenario has no body example and request body is required`() {
    // Given
    var capturedBody: String? = null
    var capturedContentType: String? = null

    val apiOperation = apiOperation("POST", "/predictions") {
      request {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
      }

      response(200) {
        jsonBody(objectType {
          properties { "id" to integerType() }
        })
      }

      scenario("successfulPrediction", status = 200) {
        response { jsonBody { "id" to 1 } }
      }
    }

    val app = routes(
      "/predictions" bind POST to { request ->
        capturedBody = request.bodyString()
        capturedContentType = request.header("Content-Type")
        Response(OK).header("Content-Type", "application/json").body("""{"id": 1}""")
      }
    )

    // When
    val results = withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.map { verifier.verify(it) }
    }

    // Then
    assert(results.size == 1)
    assert(results.all { it.result.isSuccess() }) { "Expected success but got: ${results.map { it.result.errors() }}" }
    assert(capturedContentType == "application/json") { "Expected application/json but got: $capturedContentType" }
    assert(!capturedBody.isNullOrEmpty()) { "Expected non-empty body but got: $capturedBody" }
  }

  @Test
  fun `succeeds when the request body cycle is absorbed via a nullable property`() {
    // given — Person is nullable, so the inner cycle re-entry produces Boundary which
    // the outer object absorbs as null instead of propagating; generation succeeds.
    val person = cyclicObjectType("Person", isNullable = true) { proxy ->
      properties {
        "name" to stringType()
        "friend" to proxy
      }
      required("name", "friend")
    }
    val apiOperation = apiOperation("POST", "/persons") {
      request { jsonBody(person) }
      response(200) { jsonBody(objectType { properties { "id" to integerType() } }) }
    }
    val app = routes(
      "/persons" bind POST to {
        Response(OK).header("Content-Type", "application/json").body("""{"id": 1}""")
      }
    )

    // when
    val results = withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.map { verifier.verify(it) }
    }

    // then
    val schemaBased = results.single { it.case is VerificationCase.SchemaBased }
    assert(schemaBased.result.isSuccess()) { "Expected success but got: ${schemaBased.result.errors()}" }
  }

  @Test
  fun `reports diagnostic when request body cannot be generated due to a cyclic schema`() {
    // given
    val person = cyclicObjectType("Person") { proxy ->
      properties {
        "name" to stringType()
        "friend" to proxy
      }
      required("name", "friend")
    }
    val apiOperation = apiOperation("POST", "/persons") {
      request { jsonBody(person) }
      response(200) { jsonBody(objectType { properties { "id" to integerType() } }) }
    }
    val app = routes(
      "/persons" bind POST to {
        Response(OK).header("Content-Type", "application/json").body("""{"id": 1}""")
      }
    )

    // when
    val results = withHttpServer(app) { port ->
      val cases = VerificationCaseFactory.create(apiOperation)
      val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:$port"))
      cases.map { verifier.verify(it) }
    }

    // then
    val schemaBased = results.single { it.case is VerificationCase.SchemaBased }
    assert(schemaBased.result.isFailure())
    val errorMessage = schemaBased.result.errors().single()
    assert(errorMessage.contains("request.body.friend.friend"))
    assert(errorMessage.contains(GenerationOutcome.Reason.CYCLE.explanation()))
  }

  @Test
  fun `fails the case with a connection failure when the server cannot be reached`() {
    // given
    val apiOperation = apiOperation("GET", "/maintenance") {
      response(503) {}
    }
    val unreachableUrl = "http://localhost:${releasedPort()}"
    val verifier = OpenApiVerifier(VerifierConfiguration(unreachableUrl))

    // when
    val outcome = verifier.verify(VerificationCaseFactory.create(apiOperation).single())

    // then
    assert(outcome.result.errors() == listOf("Request failed: could not connect to $unreachableUrl")) {
      "Expected a connection failure but got: ${outcome.result}"
    }
  }

  // --- helpers ---

  private fun releasedPort(): Int = ServerSocket(0).use { it.localPort }

  private fun <T> withHttpServer(routes: RoutingHttpHandler, block: (port: Int) -> T): T {
    val server = routes.asServer(SunHttp(0)).start()
    try {
      return block(server.port())
    } finally {
      server.stop()
    }
  }
}
