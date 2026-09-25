package dev.contracteer.cli

import dev.contracteer.core.Result
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.Ambiguous
import dev.contracteer.verifier.UnverifiedPrimaryResponse
import dev.contracteer.verifier.VerificationCaseFactory
import dev.contracteer.verifier.VerificationOutcome
import kotlin.test.Test

class ResultSummaryTest {

  @Test
  fun `celebrates and exits with 0 when every case passes`() {
    // Given
    val outcomes = listOf(passed(), passed())

    // When
    val summary = summarize(outcomes, unverifiedPrimaryResponses = emptyList())

    // Then
    assert(summary.lines == listOf("   🎉 All 2 verification cases passed!"))
    assert(summary.exitCode == 0)
  }

  @Test
  fun `counts errors and passed cases and exits with 1 when cases fail`() {
    // Given
    val outcomes = listOf(failed(), failed(), passed())

    // When
    val summary = summarize(outcomes, unverifiedPrimaryResponses = emptyList())

    // Then
    assert(summary.lines == listOf(
      "   ❌ @|yellow 2|@ errors found during verification.",
      "   ✅ @|yellow 1|@ verification case passed."
    ))
    assert(summary.exitCode == 1)
  }

  @Test
  fun `uses the singular for a single error and the plural for several passed cases`() {
    // Given
    val outcomes = listOf(failed(), passed(), passed())

    // When
    val summary = summarize(outcomes, unverifiedPrimaryResponses = emptyList())

    // Then
    assert(summary.lines == listOf(
      "   ❌ @|yellow 1|@ error found during verification.",
      "   ✅ @|yellow 2|@ verification cases passed."
    ))
  }

  @Test
  fun `lists unverified primary responses without celebrating and exits with 0 when every case passes`() {
    // Given
    val outcomes = listOf(passed(), passed())

    // When
    val summary = summarize(outcomes, listOf(unverifiedOrders()))

    // Then
    assert(summary.lines == listOf(
      "   ✅ @|yellow 2|@ verification cases passed.",
      "   ⚠️ POST /orders -> primary response not verified: 200 and 201 both qualify; declare a scenario for each of them"
    ))
    assert(summary.exitCode == 0)
  }

  @Test
  fun `lists unverified primary responses after the errors and exits with 1 when a case fails`() {
    // Given
    val outcomes = listOf(failed(), passed())

    // When
    val summary = summarize(outcomes, listOf(unverifiedOrders()))

    // Then
    assert(summary.lines == listOf(
      "   ❌ @|yellow 1|@ error found during verification.",
      "   ✅ @|yellow 1|@ verification case passed.",
      "   ⚠️ POST /orders -> primary response not verified: 200 and 201 both qualify; declare a scenario for each of them"
    ))
    assert(summary.exitCode == 1)
  }

  private fun passed() = VerificationOutcome(productsCase(), Result.success())

  private fun failed() = VerificationOutcome(productsCase(), Result.failure("Status code does not match."))

  private fun productsCase() =
    VerificationCaseFactory.create(apiOperation("GET", "/products") { response(200) {} }).single()

  private fun unverifiedOrders() =
    UnverifiedPrimaryResponse("POST", "/orders", Ambiguous(declared = listOf("200", "201"), candidates = listOf(200, 201)))
}
