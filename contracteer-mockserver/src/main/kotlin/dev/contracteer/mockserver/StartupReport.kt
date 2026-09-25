package dev.contracteer.mockserver

import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.operation.PrimaryResponse.Resolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved

/**
 * What the mock server reports when it starts: one line per operation that answers a valid
 * request matching no scenario with 418, or with an error status because that is the only
 * response declared.
 *
 * Up to ten lines are [warnings]. Beyond that, the warnings end with a count and the full list
 * moves to [details], so a large document does not flood the log.
 */
internal data class StartupReport(val warnings: List<String>, val details: List<String>)

private const val WARNING_LIMIT = 10

internal fun startupReport(operations: List<ApiOperation>): StartupReport {
  val lines = operations.mapNotNull { it.startupLine() }
  return when {
    lines.size <= WARNING_LIMIT -> StartupReport(warnings = lines, details = emptyList())
    else                        -> StartupReport(
      warnings = lines.take(WARNING_LIMIT) + overflowLine(lines.size - WARNING_LIMIT),
      details = lines
    )
  }
}

private fun ApiOperation.startupLine(): String? =
  when (val primaryResponse = responseSchemas.primaryResponse()) {
    is Unresolved -> answerLine(418, primaryResponse.explanation())
    is Resolved   -> primaryResponse.statusCode
      .takeIf { it >= 400 }
      ?.let { answerLine(it, "$it is the only response declared") }
  }

private fun ApiOperation.answerLine(statusCode: Int, reason: String) =
  "${describe()} -> answers $statusCode to any valid request that matches no scenario: $reason"

private fun overflowLine(hiddenCount: Int) =
  "...and $hiddenCount more; enable DEBUG logging for 'dev.contracteer.mockserver' to list them"
