package dev.contracteer.mockserver

import org.http4k.core.ContentType.Companion.TEXT_PLAIN
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.I_M_A_TEAPOT
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.Result.Failure
import dev.contracteer.core.Result.Success
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.operation.BodySchema
import dev.contracteer.core.operation.PrimaryResponse.Resolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved
import dev.contracteer.core.operation.ResponseSchema
import dev.contracteer.core.operation.Scenario
import dev.contracteer.mockserver.ScenarioMatchResult.*

internal object RequestHandler {

  fun handle(request: Request, operation: ApiOperation): Response {
    val validationResult = operation.requestSchema.validate(request)
    if (validationResult.isFailure()) return respondToInvalidRequest(operation, validationResult.errors())

    return when (val matchResult = ScenarioMatcher.match(request, operation.scenarios, operation.requestSchema)) {
      is SingleMatch -> handleScenarioResponse(request, matchResult.scenario, operation)
      is NoMatch     -> handleSchemaOnlyResponse(request, operation)
      is Ambiguous   ->
        teapotResponse(
          "Ambiguous: multiple scenarios (${matchResult.scenarios.joinToString(", ") { it.key }}) " +
          "matched the request for ${operation.describe()}")
    }
  }

  private fun respondToInvalidRequest(operation: ApiOperation, errors: List<String>): Response {
    val badRequestSchema = operation.responseSchemas.badRequestResponse()
                           ?: return validationErrorResponse(operation, errors)
    return ResponseGenerator
      .fromSchema(400, badRequestSchema.headers, badRequestSchema.bodies.firstOrNull())
      .orTeapot()
  }

  private fun handleScenarioResponse(request: Request, scenario: Scenario, operation: ApiOperation): Response {
    val responseSchema = operation.responseSchemas.responseFor(scenario.statusCode)
                         ?: return teapotResponse("No response schema for status ${scenario.statusCode}")

    val acceptResult = verifyAcceptHeader(request.header("Accept"), responseSchema)
    if (acceptResult.isFailure()) return teapotResponse(acceptResult.errors().first())
    return ResponseGenerator.fromScenario(scenario, responseSchema).orTeapot()
  }

  private fun handleSchemaOnlyResponse(request: Request, operation: ApiOperation): Response {
    val primaryResult = findPrimaryResponse(operation)
    if (primaryResult !is Success) return teapotResponse(primaryResult.errors().first())

    val (statusCode, responseSchema) = primaryResult.value
    val acceptResult = verifyAcceptHeader(request.header("Accept"), responseSchema)
    if (acceptResult.isFailure()) return teapotResponse(acceptResult.errors().first())

    val bodyResult = selectResponseBody(request.header("Accept"), responseSchema, operation)
    if (bodyResult !is Success) return teapotResponse(bodyResult.errors().first())

    return ResponseGenerator.fromSchema(statusCode, responseSchema.headers, bodyResult.value).orTeapot()
  }

  private fun Result<Response>.orTeapot(): Response = when (this) {
    is Success -> value
    is Failure -> teapotResponse(errors().joinToString(System.lineSeparator()))
  }

  private fun findPrimaryResponse(operation: ApiOperation): Result<Pair<Int, ResponseSchema>> =
    when (val primaryResponse = operation.responseSchemas.primaryResponse()) {
      is Resolved   -> success(primaryResponse.statusCode to primaryResponse.schema)
      is Unresolved ->
        failure("${operation.describe()} -> matches no scenario and has no primary response: ${primaryResponse.explanation()}")
    }

  private fun verifyAcceptHeader(acceptHeader: String?, responseSchema: ResponseSchema): Result<Unit> {
    val accept = AcceptHeader.parse(acceptHeader)
    if (accept.acceptsAny()) return success()
    if (responseSchema.bodies.isEmpty()) return success()

    if (accept.bestMatch(responseSchema.bodies.map { it.contentType }) == null)
      return failure(
        "Accept header '$acceptHeader' does not match any response content type: " +
        responseSchema.bodies.joinToString(", ") { it.contentType.value })

    return success()
  }

  private fun selectResponseBody(acceptHeader: String?,
                                 responseSchema: ResponseSchema,
                                 operation: ApiOperation): Result<BodySchema?> {
    if (responseSchema.bodies.isEmpty()) return success(null)
    if (responseSchema.bodies.size == 1) return success(responseSchema.bodies.first())

    val accept = AcceptHeader.parse(acceptHeader)
    if (accept.acceptsAny())
      return failure("Multiple response content types for ${operation.describe()}. " +
                     "Use Accept header to disambiguate: ${responseSchema.bodies.joinToString(", ") { it.contentType.value }}")

    val bestMatch = accept.bestMatch(responseSchema.bodies.map { it.contentType })
    return when {
      bestMatch != null -> success(responseSchema.bodies.find { it.contentType == bestMatch })
      else              -> failure("Accept header '$acceptHeader' does not match any response content type: " +
                                   responseSchema.bodies.joinToString(", ") { it.contentType.value })
    }
  }

  private fun validationErrorResponse(operation: ApiOperation, errors: List<String>): Response =
    teapotResponse(
      "Request validation failed for ${operation.describe()}:${System.lineSeparator()}" +
      errors.joinToString(System.lineSeparator()) { "  * $it" })

  private fun teapotResponse(message: String): Response =
    Response(I_M_A_TEAPOT)
      .header("Content-Type", TEXT_PLAIN.value)
      .body(message)
}
