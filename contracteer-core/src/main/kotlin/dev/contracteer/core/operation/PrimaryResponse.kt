package dev.contracteer.core.operation

/**
 * The outcome of resolving an operation's primary response: the one response Contracteer
 * generates against and asserts when no scenario applies.
 *
 * OpenAPI has no such concept. Contracteer derives it from the declared responses alone, so the
 * same decision drives the mock server and the verifier.
 *
 * @see ResponseSchemas.primaryResponse
 */
sealed interface PrimaryResponse {

  /**
   * Exactly one declared response can be targeted.
   *
   * @param statusCode the status code the mock answers with and the verifier expects
   * @param schema the response schema the document declares for [statusCode]
   */
  data class Resolved(val statusCode: Int, val schema: ResponseSchema): PrimaryResponse

  /**
   * No response can be targeted. Every case is fixed the same way: declare a scenario.
   */
  sealed interface Unresolved: PrimaryResponse {

    /** The operation declares no response at all. */
    data object NoResponsesDeclared: Unresolved

    /**
     * The operation declares responses, but none a request could be aimed at: only class
     * responses such as `4XX`, only `default`, or only responses no unconditional request can
     * elicit (`1xx` and `304`).
     *
     * @param declared every declared response key, in the order [ResponseSchemas] lists them
     */
    data class NoSelectableResponse(val declared: List<String>): Unresolved

    /**
     * Several declared responses could be targeted and nothing chooses between them.
     *
     * @param declared every declared response key, in the order [ResponseSchemas] lists them
     */
    data class Ambiguous(val declared: List<String>): Unresolved
  }
}
