package dev.contracteer.core.operation

import dev.contracteer.core.codec.ParameterCodec
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.operation.PrimaryResponse.Resolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.Ambiguous
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoPreferredResponse
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoResponsesDeclared
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoSelectableResponse
import dev.contracteer.core.serde.Serde

/**
 * Represents a single API operation (path + HTTP method) extracted from an OpenAPI document.
 *
 * Contains the structural schemas describing what the operation accepts and returns,
 * and the scenarios providing named example-based request/response pairings.
 *
 * @param path the URL path pattern (e.g. `/products/{id}`)
 * @param method the HTTP method (e.g. `get`, `post`)
 * @param requestSchema the structural definition of what this operation accepts
 * @param responseSchemas the response schemas grouped by status code, class, and default
 * @param scenarios named example-based pairings of request and response values
 */
data class ApiOperation(
  val path: String,
  val method: String,
  val requestSchema: RequestSchema,
  val responseSchemas: ResponseSchemas,
  val scenarios: List<Scenario>
)

/**
 * Structural definition of what an API operation accepts: parameters and request bodies.
 */
data class RequestSchema(
  val parameters: List<ParameterSchema>,
  val bodies: List<BodySchema>
) {
  val pathParameters get() = parameters.filter { it.element is ParameterElement.PathParam }
  val queryParameters get() = parameters.filter { it.element is ParameterElement.QueryParam }
  val headers get() = parameters.filter { it.element is ParameterElement.Header }
  val cookies get() = parameters.filter { it.element is ParameterElement.Cookie }
}

/**
 * Groups the three response schema sources: explicit status codes, class responses (e.g. 4XX),
 * and the default response. Provides a unified fallback chain for response lookup.
 */
data class ResponseSchemas(
  private val byStatusCode: Map<Int, ResponseSchema> = emptyMap(),
  private val byClass: Map<Int, ResponseSchema> = emptyMap(),
  private val defaultResponse: ResponseSchema? = null
) {
  fun responseFor(statusCode: Int): ResponseSchema? =
    byStatusCode[statusCode] ?: byClass[statusCode / 100] ?: defaultResponse

  fun badRequestResponse(): ResponseSchema? = responseFor(400)

  /**
   * Resolves the one response Contracteer generates against and asserts when no scenario applies.
   *
   * A lone declared response is the primary whatever its status code, as the Responses Object
   * says a single declared response is expected to be the successful one. Otherwise the single
   * exact `2xx` wins, or — when the document declares no exact `2xx` at all — the single exact
   * `3xx`. Class responses such as `4XX` and `default` never resolve a primary, and neither do
   * `1xx` and `304`, which no unconditional request can elicit.
   */
  fun primaryResponse(): PrimaryResponse {
    val elicitable = byStatusCode.filterKeys { it.isElicitable() }
    return when {
      !hasResponses()              -> NoResponsesDeclared
      elicitable.isEmpty()         -> NoSelectableResponse(declaredResponses())
      declaresExactlyOneResponse() -> elicitable.entries.first().toResolved()
      else                         -> resolveAmong(preferredCandidates(elicitable))
    }
  }

  private fun declaresExactlyOneResponse(): Boolean =
    byStatusCode.size == 1 && byClass.isEmpty() && defaultResponse == null

  /** The exact `2xx`, or — when no exact `2xx` is declared — the elicitable `3xx`. */
  private fun preferredCandidates(elicitable: Map<Int, ResponseSchema>): Map<Int, ResponseSchema> =
    elicitable.filterKeys { it in 200..299 }
      .ifEmpty { elicitable.filterKeys { it in 300..399 } }

  private fun resolveAmong(candidates: Map<Int, ResponseSchema>): PrimaryResponse =
    when (candidates.size) {
      0    -> NoPreferredResponse(declaredResponses())
      1    -> candidates.entries.single().toResolved()
      else -> Ambiguous(declaredResponses(), candidates.keys.sorted())
    }

  private fun declaredResponses(): List<String> =
    byStatusCode.keys.sorted().map { it.toString() } +
    byClass.keys.sorted().map { "${it}XX" } +
    listOfNotNull(defaultResponse?.let { "default" })

  internal fun hasResponses(): Boolean =
    byStatusCode.isNotEmpty() || byClass.isNotEmpty() || defaultResponse != null

  internal fun summary(): String = declaredResponses().joinToString(", ")

  internal fun hasAnyBody(): Boolean =
    byStatusCode.values.any { it.bodies.isNotEmpty() }
    || byClass.values.any { it.bodies.isNotEmpty() }
    || (defaultResponse?.bodies?.isNotEmpty() == true)

  internal fun mapSchemas(transform: (ResponseSchema) -> ResponseSchema?): ResponseSchemas {
    fun Map<Int, ResponseSchema>.transformValues() =
      mapValues { (_, schema) -> transform(schema) }
        .filterValues { it != null }
        .mapValues { it.value!! }

    return ResponseSchemas(
      byStatusCode.transformValues(),
      byClass.transformValues(),
      defaultResponse?.let { transform(it) }
    )
  }
}

/**
 * A `1xx` is an interim response a client never observes as the final one, and a `304` is
 * answered only to a conditional request whose validator still matches. Neither can be elicited
 * by a request built from the schema alone.
 */
private fun Int.isElicitable(): Boolean = this !in 100..199 && this != 304

private fun Map.Entry<Int, ResponseSchema>.toResolved() = Resolved(key, value)

/**
 * Structural definition of what an API operation returns for a given status code:
 * response headers and response bodies.
 */
data class ResponseSchema(
  val headers: List<ParameterSchema>,
  val bodies: List<BodySchema>
)

/**
 * Schema for a single request parameter or response header.
 *
 * @param element identifies the parameter location and name
 * @param dataType the expected data type and constraints
 * @param isRequired whether the parameter must be present
 * @param codec the style codec for encoding/decoding this parameter's wire format
 */
data class ParameterSchema(
  val element: ParameterElement,
  val dataType: DataType<out Any>,
  val isRequired: Boolean,
  val codec: ParameterCodec
)

/**
 * Schema for a request or response body.
 *
 * @param contentType the media type (e.g. `application/json`)
 * @param dataType the expected data type and constraints
 * @param isRequired whether the body must be present
 * @param serde the serializer/deserializer for this body's wire format
 */
data class BodySchema(
  val contentType: ContentType,
  val dataType: DataType<out Any>,
  val isRequired: Boolean,
  val serde: Serde
)
