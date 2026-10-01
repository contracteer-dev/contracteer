package dev.contracteer.verifier

import com.fasterxml.jackson.databind.ObjectMapper
import dev.contracteer.core.swagger.LoadReport

/**
 * Report of verifying a loaded OpenAPI document with [OpenApiVerifier]: what every verification case found,
 * and the primary responses that no case asserts.
 *
 * The shape is experimental and may change until a consumer freezes it.
 *
 * @property loadReport the load report of the verified document, as given to [OpenApiVerifier.verify].
 * @property outcomes one outcome per verification case, in the order of the operations, then of each operation's cases.
 * @property unverifiedPrimaryResponses the primary responses no case asserts, in the order of the operations.
 */
class VerificationReport internal constructor(
  val loadReport: LoadReport.Loaded,
  val outcomes: List<VerificationOutcome>,
  val unverifiedPrimaryResponses: List<UnverifiedPrimaryResponse>
) {

  /** Renders this report as JSON. The shape is experimental and may change. */
  fun toJson(): String = jsonMapper.writeValueAsString(toJsonMap())

  private companion object {
    val jsonMapper = ObjectMapper()
  }
}
