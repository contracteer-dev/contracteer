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
   * No response can be targeted without a scenario.
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
     * Several exact status codes of the preferred class compete and nothing chooses between them:
     * several exact `2xx`, or — when no exact `2xx` is declared — several targetable exact `3xx`.
     *
     * @param declared every declared response key, in the order [ResponseSchemas] lists them
     * @param candidates the competing status codes, in ascending order
     */
    data class Ambiguous(val declared: List<String>, val candidates: List<Int>): Unresolved

    /**
     * The operation declares several responses and none is an exact `2xx` or a targetable exact
     * `3xx`, such as `400` and `404`, or `404` and `2XX`. With more than one response declared,
     * none is presumed to be the successful one.
     *
     * @param declared every declared response key, in the order [ResponseSchemas] lists them
     */
    data class NoPreferredResponse(val declared: List<String>): Unresolved
  }
}
