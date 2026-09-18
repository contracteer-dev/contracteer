package dev.contracteer.conformance

import dev.contracteer.core.operation.ApiOperation

/**
 * Whether the verifier/mock round-trip invariant holds for an operation.
 *
 * `VIOLATED` marks behaviour that exists today and is known to be wrong: the document is accepted,
 * yet the verifier and the mock server do not agree on what the operation answers with.
 */
enum class Invariant { HOLDS, VIOLATED }

/**
 * One row of the conformance matrix: an operation, and what the verifier and the mock server are
 * expected to do with it.
 *
 * @param declaredResponses the response keys the operation declares, for test output
 * @param operation the fixture under test
 * @param expectedCaseCount how many verification cases the operation is expected to produce
 * @param expectedFailingCases how many of those cases are expected to fail against the mock server
 * @param expectedMockStatus the status the mock server is expected to answer a direct request with
 * @param invariant whether the round trip is expected to hold
 * @param probePath the path used for the direct mock request, including any required parameters
 */
data class ConformanceRow(
  val declaredResponses: String,
  val operation: ApiOperation,
  val expectedCaseCount: Int,
  val expectedFailingCases: Int = 0,
  val expectedMockStatus: Int,
  val invariant: Invariant,
  val probePath: String = operation.path
) {
  override fun toString(): String = "$declaredResponses -> $invariant"
}
