package dev.contracteer.verifier

import dev.contracteer.core.operation.PrimaryResponse.Unresolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.Ambiguous
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoPreferredResponse
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoResponsesDeclared
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoSelectableResponse

/**
 * Reports that an operation's primary response goes unverified: the declared responses resolve
 * no primary response, so no verification case asserts it.
 *
 * The operation may still produce verification cases, from its scenarios or a type mismatch.
 *
 * @param method the HTTP method of the operation
 * @param path the URL path pattern of the operation
 * @param reason why the declared responses resolve no primary response
 */
data class UnverifiedPrimaryResponse(
  val method: String,
  val path: String,
  val reason: Unresolved
) {
  /**
   * A human-readable explanation naming the operation and the reason, suitable for test output.
   * It never mentions scenarios: the verification cases already show what was run.
   */
  val message: String
    get() = "Primary response of ${method.uppercase()} $path not verified: ${explanation()}"

  private fun explanation(): String = when (reason) {
    is NoSelectableResponse -> "declares only ${reason.declared.joinAsProse()}; no exact status code a request can target"
    is NoPreferredResponse  -> "declares ${reason.declared.joinAsProse()}; no exact 2xx or 3xx a request can target"
    NoResponsesDeclared     -> "declares no response"
    is Ambiguous            ->
      "${reason.candidates.joinAsProse()} ${if (reason.candidates.size == 2) "both" else "all"} qualify; " +
      "declare a scenario for each of them"
  }

  private fun List<Any>.joinAsProse(): String =
    if (size < 2) joinToString()
    else "${dropLast(1).joinToString(", ")} and ${last()}"
}
