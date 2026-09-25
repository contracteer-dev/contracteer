package dev.contracteer.verifier.junit

import org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import dev.contracteer.verifier.UnverifiedPrimaryResponse

/**
 * A skipped invocation reporting that an operation's primary response goes unverified.
 *
 * Its condition disables the invocation before it starts, so the test method body never runs.
 */
internal class UnverifiedPrimaryResponseInvocationContext(
  private val unverifiedPrimaryResponse: UnverifiedPrimaryResponse
): TestTemplateInvocationContext {

  override fun getDisplayName(invocationIndex: Int) =
    unverifiedPrimaryResponse.message

  override fun getAdditionalExtensions(): List<Extension> =
    listOf(ExecutionCondition { disabled(unverifiedPrimaryResponse.message) })
}
