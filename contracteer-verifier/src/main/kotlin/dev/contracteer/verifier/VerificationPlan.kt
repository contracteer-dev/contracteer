package dev.contracteer.verifier

/**
 * What verifying one operation involves: the cases to run, and whether its primary response goes
 * unverified.
 *
 * @param cases the verification cases generated for the operation, possibly none
 * @param unverifiedPrimaryResponse the report that no case asserts the operation's primary
 *   response, or `null` when one does or every competing status code has a scenario
 */
data class VerificationPlan(
  val cases: List<VerificationCase>,
  val unverifiedPrimaryResponse: UnverifiedPrimaryResponse?
)
