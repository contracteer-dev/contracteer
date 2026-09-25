package dev.contracteer.cli

import dev.contracteer.verifier.UnverifiedPrimaryResponse
import dev.contracteer.verifier.VerificationOutcome

/**
 * The closing summary of a verification run: the lines to print, in picocli markup, and the exit
 * code. Unverified primary responses are listed but never change the exit code.
 */
internal data class ResultSummary(val lines: List<String>, val exitCode: Int)

internal fun summarize(outcomes: List<VerificationOutcome>,
                       unverifiedPrimaryResponses: List<UnverifiedPrimaryResponse>): ResultSummary {
  val failureCount = outcomes.count { it.result.isFailure() }
  return ResultSummary(
    lines = caseLines(outcomes.size, failureCount, unverifiedPrimaryResponses.isEmpty()) +
            unverifiedPrimaryResponses.map { "   ⚠️ ${it.message}" },
    exitCode = if (failureCount > 0) 1 else 0
  )
}

private fun caseLines(caseCount: Int, failureCount: Int, everyPrimaryResponseVerified: Boolean): List<String> =
  when {
    failureCount > 0             -> listOf(
      "   ❌ @|yellow $failureCount|@ ${plural(failureCount, "error")} found during verification.",
      passedLine(caseCount - failureCount)
    )
    everyPrimaryResponseVerified -> listOf("   🎉 All $caseCount verification cases passed!")
    else                         -> listOf(passedLine(caseCount))
  }

private fun passedLine(passedCount: Int) =
  "   ✅ @|yellow $passedCount|@ ${plural(passedCount, "verification case")} passed."

private fun plural(count: Int, noun: String) = if (count == 1) noun else "${noun}s"
