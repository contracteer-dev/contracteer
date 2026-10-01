package dev.contracteer.verifier

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.DiagnosticRule.EMPTY_SCHEMA
import dev.contracteer.core.Severity.WARNING
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.loadedReport
import dev.contracteer.core.swagger.LoadReport
import dev.contracteer.core.swagger.OpenApiLoader
import kotlin.test.Test
import kotlin.test.assertIs

class VerificationReportJsonTest {

  private val json = ObjectMapper()
  private val noRoute = routes("/" bind GET to { Response(OK) })

  @Test
  fun `report serializes six top-level keys and its version and its source and a summary counting the cases`() {
    // Given
    val orders = apiOperation("GET", "/orders") {
      response(200) {}
      response(404) {}

      scenario("found", status = 200)
      scenario("missing", status = 404)
    }
    val app = routes("/orders" bind GET to { Response(OK) })

    // When
    val actual = verify(loadedReport(listOf(orders), source = "orders.yaml"), app)

    // Then
    val expected = json.readTree("""
      {
        "version": 1,
        "source": "orders.yaml",
        "summary": { "cases": 2, "passed": 1, "failed": 1 }
      }
    """)
    assert(actual.fieldNames().asSequence().toSet() == setOf("version", "source", "summary", "load", "operations", "cases"))
    assert(actual.retain("version", "source", "summary") == expected) { actual.toPrettyString() }
  }

  @Test
  fun `report serializes each case with its kind and its operation and its status and what it found`() {
    // Given
    val getUser = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
      }

      response(200) {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
      }
      response(400) {}

      scenario("john", status = 200) {
        request { pathParam["id"] = 1 }
        response { jsonBody { "name" to "John" } }
      }
    }
    val createUser = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
      }

      response(201) {}
      response(400) {}
    }
    val app = routes(
      "/users/{id}" bind GET to { request ->
        if (request.path("id")?.toIntOrNull() == null) Response(BAD_REQUEST)
        else Response(OK).header("Content-Type", "application/json").body("""{"name": 42}""")
      },
      "/users" bind POST to { request ->
        if (request.bodyString().startsWith("{")) Response(CREATED) else Response(BAD_REQUEST)
      }
    )

    // When
    val actual = verify(loadedReport(listOf(getUser, createUser)), app)

    // Then
    val expected = json.readTree("""
      [
        {
          "name": "GET /users/{id} -> 200 (application/json) with scenario 'john'",
          "kind": "scenario-based",
          "operation": { "method": "GET", "path": "/users/{id}" },
          "status": "failed",
          "mutatedElement": null,
          "diagnostics": [
            {
              "message": "Type mismatch, expected type 'string'",
              "location": "name",
              "keyword": null,
              "rule": null,
              "operation": null,
              "category": "contract-violation",
              "severity": "error"
            }
          ],
          "truncated": 0
        },
        {
          "name": "GET /users/{id} -> 400 (auto: path 'id' type mismatch)",
          "kind": "type-mismatch",
          "operation": { "method": "GET", "path": "/users/{id}" },
          "status": "passed",
          "mutatedElement": { "in": "path", "name": "id" },
          "diagnostics": [],
          "truncated": 0
        },
        {
          "name": "POST /users (application/json) -> 201 (generated)",
          "kind": "schema-based",
          "operation": { "method": "POST", "path": "/users" },
          "status": "passed",
          "mutatedElement": null,
          "diagnostics": [],
          "truncated": 0
        },
        {
          "name": "POST /users -> 400 (auto: body type mismatch)",
          "kind": "type-mismatch",
          "operation": { "method": "POST", "path": "/users" },
          "status": "passed",
          "mutatedElement": { "in": "body", "name": null },
          "diagnostics": [],
          "truncated": 0
        }
      ]
    """)
    assert(actual["cases"] == expected) { actual["cases"]?.toPrettyString() ?: "no 'cases' key" }
  }

  @Test
  fun `report serializes the diagnostics of the load`() {
    // Given
    val warning = Diagnostic(
      message = "Schema 'Anything': empty schema accepts any value.",
      rule = EMPTY_SCHEMA,
      category = SPEC,
      severity = WARNING
    )

    // When
    val actual = verify(loadedReport(emptyList(), diagnostics = listOf(warning)), noRoute)

    // Then
    val expected = json.readTree("""
      {
        "diagnostics": [
          {
            "message": "Schema 'Anything': empty schema accepts any value.",
            "location": null,
            "keyword": null,
            "rule": "empty-schema",
            "operation": null,
            "category": "spec",
            "severity": "warning"
          }
        ]
      }
    """)
    assert(actual["load"] == expected) { actual["load"]?.toPrettyString() ?: "no 'load' key" }
  }

  @Test
  fun `report serializes the declared operation count and a gap for each operation excluded at load or with an unverified primary response`() {
    // Given
    val loaded = assertIs<LoadReport.Loaded>(OpenApiLoader.load("src/test/resources/report/operation_gaps.yaml"))
    val app = routes("/orders" bind GET to { Response(OK) })

    // When
    val actual = verify(loaded, app)

    // Then
    val expected = json.readTree("""
      {
        "declared": 3,
        "gaps": [
          {
            "operation": { "method": "GET", "path": "/products" },
            "reason": "excluded-at-load",
            "message": "Operation excluded: no supported response content type."
          },
          {
            "operation": { "method": "GET", "path": "/status" },
            "reason": "primary-response-unverified",
            "message": "declares only 4XX; no exact status code a request can target"
          }
        ]
      }
    """)
    assert(actual["operations"] == expected) { actual["operations"]?.toPrettyString() ?: "no 'operations' key" }
  }

  private fun verify(loaded: LoadReport.Loaded, app: RoutingHttpHandler): ObjectNode =
    withHttpServer(app) { port ->
      json.readTree(OpenApiVerifier(VerifierConfiguration("http://localhost:$port")).verify(loaded).toJson()) as ObjectNode
    }
}
