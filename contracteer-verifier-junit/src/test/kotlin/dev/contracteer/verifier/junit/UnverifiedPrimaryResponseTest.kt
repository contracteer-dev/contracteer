package dev.contracteer.verifier.junit

import org.junit.jupiter.api.Tag
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.testkit.engine.EngineExecutionResults
import org.junit.platform.testkit.engine.EngineTestKit
import kotlin.test.Test

class UnverifiedPrimaryResponseTest {

  @Test
  fun `does not fail when no operation has a verifiable primary response`() {
    // When
    val results = execute(NoVerifiablePrimaryResponse::class.java)

    // Then
    assert(results.containerEvents().failed().count() == 0L)
  }

  @Test
  fun `reports an unverified primary response as a skipped test explaining why`() {
    // When
    val results = execute(NoVerifiablePrimaryResponse::class.java)

    // Then
    val message = "POST /orders -> primary response not verified: 200 and 201 both qualify; " +
                  "declare a scenario for each of them"
    val skipped = results.testEvents().skipped().list()
    assert(skipped.map { it.testDescriptor.displayName } == listOf(message))
    assert(skipped.map { it.getPayload(String::class.java).orElse(null) } == listOf(message))
  }

  @Test
  fun `does not run the test body for an unverified primary response`() {
    // Given
    NoVerifiablePrimaryResponse.bodyRuns = 0

    // When
    execute(NoVerifiablePrimaryResponse::class.java)

    // Then
    assert(NoVerifiablePrimaryResponse.bodyRuns == 0)
  }

  @Test
  fun `reports an unverified primary response after the cases of its operation`() {
    // When
    val results = execute(VerifiedAndUnverifiedPrimaryResponses::class.java)

    // Then
    val registered = results.allEvents().dynamicallyRegistered().list().map { it.testDescriptor.displayName }
    assert(registered == listOf(
      "GET /orders/{id} -> 400 (auto: path 'id' type mismatch)",
      "GET /orders/{id} -> primary response not verified: 200 and 201 both qualify; " +
      "declare a scenario for each of them",
      "GET /products/{id} -> 200 (generated)"
    ))
  }

  private fun execute(testClass: Class<*>): EngineExecutionResults =
    EngineTestKit.engine("junit-jupiter")
      .selectors(selectClass(testClass))
      .execute()

  @Tag("engine-test-kit-target")
  class NoVerifiablePrimaryResponse {

    @ContracteerTest(openApiDoc = "src/test/resources/api_without_verifiable_primary_response.yaml")
    fun `verify contracts`() {
      bodyRuns++
    }

    companion object {
      var bodyRuns = 0
    }
  }

  @Tag("engine-test-kit-target")
  class VerifiedAndUnverifiedPrimaryResponses {

    @ContracteerTest(openApiDoc = "src/test/resources/api_with_verified_and_unverified_primary_responses.yaml")
    fun `verify contracts`() {
    }
  }
}
