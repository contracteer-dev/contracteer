package dev.contracteer.verifier

import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.Request
import org.http4k.core.Response
import dev.contracteer.core.DiagnosticCategory.CONTRACT_VIOLATION
import dev.contracteer.core.DiagnosticCategory.EXECUTION_ERROR
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Failure
import dev.contracteer.core.Result.Success
import dev.contracteer.core.Severity.ERROR
import dev.contracteer.core.swagger.LoadReport

/**
 * Verifies a real server implementation against OpenAPI contract expectations.
 *
 * Sends requests derived from [VerificationCase] instances and validates
 * the server's responses against the expected response schema.
 */
class OpenApiVerifier(configuration: VerifierConfiguration) {
  private val client = VerificationHttpClient(configuration.baseUrl)
  private val httpLogger = KotlinLogging.logger("dev.contracteer.http")

  /**
   * Sends a request for the given [case] and validates the response.
   *
   * A failed outcome's diagnostics carry a category: a response that breaks the contract is a
   * contract violation; a request that could not be built or sent is an execution error.
   *
   * @return a [VerificationOutcome] containing the case and its validation result
   */
  fun verify(case: VerificationCase): VerificationOutcome =
    runCatching { client.execute(case) }.fold(
      onSuccess = { handleExecutionResult(case, it) },
      onFailure = { e ->
        VerificationOutcome(
          case,
          failure<Unit>("Request failed: ${e::class.simpleName}: ${e.message ?: "<no message>"}")
            .withDefaults(EXECUTION_ERROR, ERROR)
        )
      }
    )

  /**
   * Verifies every operation of the given [loaded] document and reports what each case found.
   *
   * Every case of every operation runs: a failing case does not stop the run. To run a subset of the
   * cases, verify them one by one.
   * This entry point is experimental and may change until a consumer freezes it.
   *
   * @return a [VerificationReport] with one outcome per case, the primary responses no case asserts, and [loaded]
   */
  fun verify(loaded: LoadReport.Loaded): VerificationReport {
    val plans = loaded.operations.map { VerificationCaseFactory.plan(it) }
    return VerificationReport(
      loadReport = loaded,
      outcomes = plans.flatMap { it.cases }.map { verify(it) },
      unverifiedPrimaryResponses = plans.mapNotNull { it.unverifiedPrimaryResponse }
    )
  }

  private fun handleExecutionResult(case: VerificationCase,
                                    executionResult: Result<Pair<Request, Response>>): VerificationOutcome =
    when (executionResult) {
      is Success -> validateRequestResponse(case, executionResult.value)
      is Failure -> VerificationOutcome(case, executionResult.retypeError<Unit>().withDefaults(EXECUTION_ERROR, ERROR))
    }

  private fun validateRequestResponse(case: VerificationCase,
                                      requestResponse: Pair<Request, Response>): VerificationOutcome {
    val (request, response) = requestResponse
    httpLogger.debug { formatRequest(request) }
    httpLogger.debug { formatResponse(response) }
    val validationResult = ResponseValidator.validate(case, response).withDefaults(CONTRACT_VIOLATION, ERROR)
    if (validationResult.isFailure()) {
      httpLogger.warn {
        "Verification failed: ${case.displayName}\n${formatRequest(request)}\n${
          formatResponse(response)
        }"
      }
      httpLogger.warn { "Enable DEBUG logging for 'dev.contracteer.http' to see all HTTP traffic" }
    }
    return VerificationOutcome(case, validationResult)
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
}
