package dev.contracteer.verifier

import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.loadedReport
import kotlin.test.Test

class VerificationReportTest {

  @Test
  fun `reports every outcome in operation order and the unverified primary responses and the load report`() {
    // Given
    val users = apiOperation("GET", "/users/{id}") {
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
    val orders = apiOperation("POST", "/orders") {
      response(200) {}
      response(201) {}

      scenario("ok", status = 200)
    }
    val loaded = loadedReport(listOf(users, orders))

    val app = routes(
      "/users/{id}" bind GET to {
        Response(OK).header("Content-Type", "application/json").body("""{"id": 1, "name": "John"}""")
      },
      "/orders" bind POST to { Response(OK) }
    )

    // When
    val report = withHttpServer(app) { port ->
      OpenApiVerifier(VerifierConfiguration("http://localhost:$port")).verify(loaded)
    }

    // Then
    val plans = listOf(users, orders).map { VerificationCaseFactory.plan(it) }
    assert(report.outcomes.map { it.case } == plans.flatMap { it.cases })
    assert(report.outcomes.map { it.result.isSuccess() } == listOf(true, false, true))
    assert(report.unverifiedPrimaryResponses == listOf(plans[1].unverifiedPrimaryResponse))
    assert(report.loadReport === loaded)
  }
}
