package dev.contracteer.verifier

import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.operation.ParameterElement.*
import dev.contracteer.core.operation.RequestSchema
import dev.contracteer.core.operation.ResponseSchema
import dev.contracteer.core.operation.Scenario

/**
 * A test case for verifying a server implementation against an OpenAPI contract.
 *
 * Each subtype represents a different verification strategy:
 * - [ScenarioBased]: driven by a named scenario from the OpenAPI document
 * - [SchemaBased]: generated from the schema when no scenario targets the operation's primary response
 * - [TypeMismatch]: sends an intentionally malformed request to verify the server rejects it
 */
sealed class VerificationCase {
  /** A human-readable description of this verification case, suitable for test output. */
  abstract val displayName: String

  /**
   * A verification case driven by a named [Scenario] from the OpenAPI document.
   *
   * [requestContentType] is the content type used for the request body. When the scenario carries
   * an explicit body example, it equals `scenario.request.body.contentType`. When the scenario has
   * no body example but the request schema declares a required body, it is the content type chosen
   * for schema-driven body generation at request time. When the request carries no body, it is
   * `null`.
   */
  data class ScenarioBased(
    val scenario: Scenario,
    val requestSchema: RequestSchema,
    val responseSchema: ResponseSchema,
    val requestContentType: ContentType?
  ): VerificationCase() {
    override val displayName: String
      get() {
        val requestCT = requestContentType?.let { " (${it.value})" } ?: ""
        val responseCT = scenario.response.body?.contentType?.let { " (${it.value})" } ?: ""
        return "${scenario.method.uppercase()} ${scenario.path}$requestCT -> ${scenario.statusCode}$responseCT with scenario '${scenario.key}'"
      }
  }

  /**
   * A verification case generated from the schema with random values, used when no scenario
   * targets the operation's primary response.
   *
   * [statusCode] is the primary response's status code, which is not necessarily a 2xx: an
   * operation declaring a lone `302` or `403` is verified against that code.
   */
  data class SchemaBased(
    val path: String,
    val method: String,
    val statusCode: Int,
    val requestContentType: ContentType?,
    val responseContentType: ContentType?,
    val requestSchema: RequestSchema,
    val responseSchema: ResponseSchema
  ): VerificationCase() {
    override val displayName: String
      get() {
        val requestCT = requestContentType?.let { " (${it.value})" } ?: ""
        val responseCT = responseContentType?.let { " (${it.value})" } ?: ""
        return "${method.uppercase()} $path$requestCT -> $statusCode$responseCT (generated)"
      }
  }

  /**
   * A verification case that sends a type-mismatched value to verify the server rejects it.
   *
   * [expectedResponses] maps each status code accepted as a rejection to the response schema the
   * document declares for it, in ascending order. The candidates are `400` and `422`, plus `404` when
   * the mutated element is a path or query parameter, since both are part of the target URI. A candidate
   * is kept only when the document covers it: exactly, through a class response such as `4XX`, or
   * through `default`. The factory always includes `400`.
   */
  data class TypeMismatch(
    val path: String,
    val method: String,
    val requestContentType: ContentType?,
    val responseContentType: ContentType?,
    val requestSchema: RequestSchema,
    val expectedResponses: Map<Int, ResponseSchema>,
    val mutatedElement: MutatedElement,
    val mutatedValue: String
  ): VerificationCase() {
    override val displayName: String
      get() {
        val elementLabel = when (val element = mutatedElement) {
          is MutatedElement.Parameter -> {
            val category = when (element.element) {
              is PathParam  -> "path"
              is QueryParam -> "query"
              is Header     -> "header"
              is Cookie     -> "cookie"
            }
            "$category '${element.element.name}'"
          }
          is MutatedElement.Body      -> "body"
        }
        return "${method.uppercase()} $path -> ${expectedResponses.keys.joinToString("|")} (auto: $elementLabel type mismatch)"
      }
  }
}
