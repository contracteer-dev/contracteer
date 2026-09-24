package dev.contracteer.conformance

import dev.contracteer.conformance.Invariant.HOLDS
import dev.contracteer.conformance.Invariant.REPORTED
import dev.contracteer.conformance.Invariant.VIOLATED
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.mockserver.MockServer
import dev.contracteer.verifier.OpenApiVerifier
import dev.contracteer.verifier.VerificationCaseFactory
import dev.contracteer.verifier.VerificationOutcome
import dev.contracteer.verifier.VerifierConfiguration
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.I_M_A_TEAPOT
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal

/**
 * Encodes the round-trip invariant: for any OpenAPI document Contracteer accepts, running the
 * verifier against the mock server started from that same document must pass.
 *
 * See the module README for why this lives in a module of its own and what each row asserts.
 */
class RoundTripInvariantTest {

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("conformanceMatrix")
  fun `verifier and mock server agree on the responses an operation declares`(row: ConformanceRow) {
    // when
    val observed = withMockServer(row.operation) { port -> observe(row, port) }

    // then
    assert(observed.caseCount == row.expectedCaseCount) {
      "Expected ${row.expectedCaseCount} verification cases but got ${observed.caseCount}"
    }
    assert(observed.failures.size == row.expectedFailingCases) {
      "Expected ${row.expectedFailingCases} failing cases but got ${observed.failures.size}: ${observed.describeFailures()}"
    }
    assert(observed.mockStatus == row.expectedMockStatus) {
      "Expected the mock server to answer ${row.expectedMockStatus} but got ${observed.mockStatus}"
    }
    assert(observed.unverifiedPrimaryResponse == row.expectedUnverifiedPrimaryResponse) {
      "Expected the verifier ${if (row.expectedUnverifiedPrimaryResponse) "to" else "not to"} report the primary response unverified"
    }
    assert(observed.invariant == row.invariant) {
      "Expected the round trip to be ${row.invariant} but it is ${observed.invariant}: ${observed.describeFailures()}"
    }
  }

  private fun observe(row: ConformanceRow, port: Int): Observation {
    val baseUrl = "http://localhost:$port"
    val plan = VerificationCaseFactory.plan(row.operation)
    val verifier = OpenApiVerifier(VerifierConfiguration(baseUrl))
    return Observation(
      caseCount = plan.cases.size,
      failures = plan.cases.map { verifier.verify(it) }.filter { it.result.isFailure() },
      unverifiedPrimaryResponse = plan.unverifiedPrimaryResponse != null,
      mockStatus = probeMock(baseUrl + row.probePath)
    )
  }

  private fun probeMock(url: String): Int = JavaHttpClient()(Request(GET, url)).status.code

  private fun <T> withMockServer(operation: ApiOperation, block: (Int) -> T): T {
    val mockServer = MockServer(listOf(operation))
    mockServer.start()
    return try {
      block(mockServer.port())
    } finally {
      mockServer.stop()
    }
  }

  private data class Observation(
    val caseCount: Int,
    val failures: List<VerificationOutcome>,
    val unverifiedPrimaryResponse: Boolean,
    val mockStatus: Int
  ) {
    val invariant: Invariant
      get() = when {
        failures.isNotEmpty()                                                -> VIOLATED
        !unverifiedPrimaryResponse && caseCount > 0 && !mockRefusedToRespond -> HOLDS
        unverifiedPrimaryResponse && mockRefusedToRespond                    -> REPORTED
        else                                                                 -> VIOLATED
      }

    private val mockRefusedToRespond get() = mockStatus == I_M_A_TEAPOT.code

    fun describeFailures(): String =
      failures.joinToString("; ") { "${it.case.displayName}: ${it.result.errors()}" }
        .ifEmpty { "no verification case failed" }
  }

  companion object {
    private fun responseBody() = objectType { properties { "id" to integerType() } }

    @JvmStatic
    fun conformanceMatrix(): List<ConformanceRow> = listOf(
      ConformanceRow(
        declaredResponses = "200",
        operation = apiOperation("get", "/single-200") {
          response(200) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 1,
        expectedMockStatus = 200,
        invariant = HOLDS
      ),
      ConformanceRow(
        declaredResponses = "204",
        operation = apiOperation("get", "/single-204") {
          response(204)
        },
        expectedCaseCount = 1,
        expectedMockStatus = 204,
        invariant = HOLDS
      ),
      ConformanceRow(
        declaredResponses = "200 + 404",
        operation = apiOperation("get", "/success-and-not-found") {
          response(200) { jsonBody(responseBody()) }
          response(404) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 1,
        expectedMockStatus = 200,
        invariant = HOLDS
      ),
      ConformanceRow(
        declaredResponses = "302",
        operation = apiOperation("get", "/single-302") {
          response(302) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 1,
        expectedMockStatus = 302,
        invariant = HOLDS
      ),
      ConformanceRow(
        declaredResponses = "302 + 404",
        operation = apiOperation("get", "/redirect-and-not-found") {
          response(302) { jsonBody(responseBody()) }
          response(404) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 1,
        expectedMockStatus = 302,
        invariant = HOLDS
      ),
      ConformanceRow(
        declaredResponses = "2XX",
        operation = apiOperation("get", "/class-2xx") {
          classResponse(2) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 0,
        expectedMockStatus = I_M_A_TEAPOT.code,
        expectedUnverifiedPrimaryResponse = true,
        invariant = REPORTED
      ),
      ConformanceRow(
        declaredResponses = "4XX",
        operation = apiOperation("get", "/class-4xx") {
          classResponse(4) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 0,
        expectedMockStatus = I_M_A_TEAPOT.code,
        expectedUnverifiedPrimaryResponse = true,
        invariant = REPORTED
      ),
      ConformanceRow(
        declaredResponses = "default",
        operation = apiOperation("get", "/default-only") {
          defaultResponse { jsonBody(responseBody()) }
        },
        expectedCaseCount = 0,
        expectedMockStatus = I_M_A_TEAPOT.code,
        expectedUnverifiedPrimaryResponse = true,
        invariant = REPORTED
      ),
      ConformanceRow(
        declaredResponses = "200 + 201",
        operation = apiOperation("get", "/two-success-responses") {
          response(200) { jsonBody(responseBody()) }
          response(201) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 0,
        expectedMockStatus = I_M_A_TEAPOT.code,
        expectedUnverifiedPrimaryResponse = true,
        invariant = REPORTED
      ),
      ConformanceRow(
        declaredResponses = "200 + 201 with a scenario on each",
        operation = apiOperation("get", "/two-success-responses-with-scenarios/{id}") {
          request { pathParam("id", integerType()) }
          response(200) { jsonBody(responseBody()) }
          response(201) { jsonBody(responseBody()) }
          scenario("ok", status = 200) {
            request { pathParam["id"] = BigDecimal(1) }
            response { jsonBody { "id" to 1 } }
          }
          scenario("created", status = 201) {
            request { pathParam["id"] = BigDecimal(2) }
            response { jsonBody { "id" to 2 } }
          }
        },
        expectedCaseCount = 2,
        expectedMockStatus = 200,
        invariant = HOLDS,
        probePath = "/two-success-responses-with-scenarios/1"
      ),
      ConformanceRow(
        declaredResponses = "4XX with a query parameter",
        operation = apiOperation("get", "/class-4xx-with-parameter") {
          request { queryParam("limit", integerType(), isRequired = true) }
          classResponse(4) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 1,
        expectedMockStatus = I_M_A_TEAPOT.code,
        expectedUnverifiedPrimaryResponse = true,
        invariant = REPORTED,
        probePath = "/class-4xx-with-parameter?limit=1"
      ),
      ConformanceRow(
        declaredResponses = "200 + 4XX with a query parameter",
        operation = apiOperation("get", "/success-and-class-4xx-with-parameter") {
          request { queryParam("limit", integerType(), isRequired = true) }
          response(200) { jsonBody(responseBody()) }
          classResponse(4) { jsonBody(responseBody()) }
        },
        expectedCaseCount = 2,
        expectedMockStatus = 200,
        invariant = HOLDS,
        probePath = "/success-and-class-4xx-with-parameter?limit=1"
      )
    )
  }
}
