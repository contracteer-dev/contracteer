package dev.contracteer.verifier

import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.operation.ParameterElement.PathParam
import dev.contracteer.verifier.VerificationCase.TypeMismatch
import kotlin.test.Test

class TypeMismatchVerificationTest {

  @Test
  fun `verifies body type mismatch sends mutated value as raw body`() {
    // Given
    var capturedBody: String? = null
    var capturedContentType: String? = null

    val app = routes(
      "/users" bind POST to { request ->
        capturedBody = request.bodyString()
        capturedContentType = request.header("Content-Type")
        Response(BAD_REQUEST)
          .header("Content-Type", "application/json")
          .body("""{"error": "invalid body"}""")
      }
    )
    val server = app.asServer(SunHttp(0)).start()

    val apiOperation = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
      }

      response(200) {}

      response(400) {
        jsonBody(objectType {
          properties { "error" to stringType() }
        })
      }
    }

    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCase = cases.filterIsInstance<TypeMismatch>().first()
    val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:${server.port()}"))

    // When
    val outcome = verifier.verify(typeMismatchCase)

    // Then
    server.stop()
    assert(outcome.result.isSuccess())
    assert(capturedBody == "<<not-a-object>>")
    assert(capturedContentType?.contains("application/json") == true)
  }

  @Test
  fun `verifies parameter type mismatch sends mutated value for targeted parameter`() {
    // Given
    var capturedId: String? = null

    val app = routes(
      "/users/{id}" bind GET to { request ->
        capturedId = request.path("id")
        Response(BAD_REQUEST)
          .header("Content-Type", "application/json")
          .body("""{"error": "invalid id"}""")
      }
    )
    val server = app.asServer(SunHttp(0)).start()

    val apiOperation = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
      }

      response(200) {}

      response(400) {
        jsonBody(objectType {
          properties { "error" to stringType() }
        })
      }
    }

    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCase = cases.filterIsInstance<TypeMismatch>().first()
    val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:${server.port()}"))

    // When
    val outcome = verifier.verify(typeMismatchCase)

    // Then
    server.stop()
    assert(outcome.result.isSuccess())
    assert(capturedId == "<<not-a-integer>>")
  }

  @Test
  fun `sends valid values for non-mutated parameters alongside mutated one`() {
    // Given
    var capturedId: String? = null
    var capturedPage: String? = null

    val app = routes(
      "/users/{id}" bind GET to { request ->
        capturedId = request.path("id")
        capturedPage = request.query("page")
        Response(BAD_REQUEST)
          .header("Content-Type", "application/json")
          .body("""{"error": "invalid"}""")
      }
    )
    val server = app.asServer(SunHttp(0)).start()

    val apiOperation = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
        queryParam("page", integerType())
      }

      response(200) {}

      response(400) {
        jsonBody(objectType {
          properties { "error" to stringType() }
        })
      }
    }

    val cases = VerificationCaseFactory.create(apiOperation)
    // The factory generates 2 cases: one for path, one for query. Get the path one.
    val pathCase = cases
      .filterIsInstance<TypeMismatch>()
      .first { it.mutatedElement == MutatedElement.Parameter(PathParam("id")) }
    val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:${server.port()}"))

    // When
    val outcome = verifier.verify(pathCase)

    // Then
    server.stop()
    assert(outcome.result.isSuccess())
    assert(capturedId == "<<not-a-integer>>")
    // The non-mutated query param should have a valid integer value
    assert(capturedPage != null)
    assert(capturedPage!!.matches(Regex("-?\\d+")))
  }

  @Test
  fun `verification fails when server does not return 400`() {
    // Given
    val app = routes(
      "/users" bind POST to {
        Response(OK)
          .header("Content-Type", "application/json")
          .body("""{"id": 1}""")
      }
    )
    val server = app.asServer(SunHttp(0)).start()

    val apiOperation = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
      }

      response(200) {}

      response(400) {
        jsonBody(objectType {
          properties { "error" to stringType() }
        })
      }
    }

    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCase = cases.filterIsInstance<TypeMismatch>().first()
    val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:${server.port()}"))

    // When
    val outcome = verifier.verify(typeMismatchCase)

    // Then
    server.stop()
    assert(outcome.result.isFailure())
    assert(outcome.result.errors().any { it.contains("Status code") })
  }

  @Test
  fun `verification passes when server rejects type mismatch with a declared 422`() {
    // Given
    val app = routes(
      "/users" bind POST to {
        Response(UNPROCESSABLE_ENTITY)
          .header("Content-Type", "application/json")
          .body("""{"detail": "invalid body"}""")
      }
    )
    val server = app.asServer(SunHttp(0)).start()

    val apiOperation = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
      }

      response(200) {}

      response(400) {
        jsonBody(objectType {
          properties { "error" to stringType() }
          required("error")
        })
      }

      response(422) {
        jsonBody(objectType {
          properties { "detail" to stringType() }
          required("detail")
        })
      }
    }

    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCase = cases.filterIsInstance<TypeMismatch>().first()
    val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:${server.port()}"))

    // When
    val outcome = verifier.verify(typeMismatchCase)

    // Then
    server.stop()
    assert(outcome.result.isSuccess()) { "Expected success but got: ${outcome.result.errors()}" }
  }

  @Test
  fun `verification passes when server rejects path type mismatch with a declared bodyless 404`() {
    // Given
    val app = routes(
      "/users/{id}" bind GET to { Response(NOT_FOUND) }
    )
    val server = app.asServer(SunHttp(0)).start()

    val apiOperation = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
      }

      response(200) {}

      response(404) {}

      classResponse(4) {
        jsonBody(objectType {
          properties { "title" to stringType() }
          required("title")
        })
      }
    }

    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCase = cases.filterIsInstance<TypeMismatch>().first()
    val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:${server.port()}"))

    // When
    val outcome = verifier.verify(typeMismatchCase)

    // Then
    server.stop()
    assert(outcome.result.isSuccess()) { "Expected success but got: ${outcome.result.errors()}" }
  }

  @Test
  fun `verification fails when server answers type mismatch with a covered status that does not signal a rejected input`() {
    // Given
    val app = routes(
      "/users" bind POST to {
        Response(UNAUTHORIZED)
          .header("Content-Type", "application/json")
          .body("""{"title": "missing credentials"}""")
      }
    )
    val server = app.asServer(SunHttp(0)).start()

    val apiOperation = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
      }

      response(200) {}

      classResponse(4) {
        jsonBody(objectType {
          properties { "title" to stringType() }
        })
      }
    }

    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCase = cases.filterIsInstance<TypeMismatch>().first()
    val verifier = OpenApiVerifier(VerifierConfiguration("http://localhost:${server.port()}"))

    // When
    val outcome = verifier.verify(typeMismatchCase)

    // Then
    server.stop()
    assert(outcome.result.isFailure())
    assert(outcome.result.errors().any { it.contains("Status code") })
  }
}
