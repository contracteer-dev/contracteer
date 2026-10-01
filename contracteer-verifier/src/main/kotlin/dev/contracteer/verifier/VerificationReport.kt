package dev.contracteer.verifier

/**
 * Report of verifying a list of [VerificationPlan]s with [OpenApiVerifier]: what every verification case found,
 * and the primary responses that no case asserts.
 *
 * The shape is experimental and may change until a consumer freezes it.
 *
 * @property outcomes one outcome per verification case, in the order of the plans, then of each plan's cases.
 * @property unverifiedPrimaryResponses the unverified primary responses the plans carry, in the order of the plans.
 */
class VerificationReport internal constructor(
  val outcomes: List<VerificationOutcome>,
  val unverifiedPrimaryResponses: List<UnverifiedPrimaryResponse>
)
