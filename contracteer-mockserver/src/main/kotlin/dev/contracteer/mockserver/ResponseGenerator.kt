package dev.contracteer.mockserver

import org.http4k.core.Response
import org.http4k.core.Status
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.GenerationContext
import dev.contracteer.core.operation.*

internal object ResponseGenerator {

  fun fromScenario(scenario: Scenario, responseSchema: ResponseSchema, fallbackBodySchema: BodySchema?): Result<Response> =
    Response(Status.fromCode(scenario.statusCode)!!)
      .withScenarioHeaders(responseSchema.headers, scenario.response.headers)
      .flatMap { it.withScenarioBody(scenario.response.body, responseSchema, fallbackBodySchema) }

  fun fromSchema(statusCode: Int, headers: List<ParameterSchema>, bodySchema: BodySchema?): Result<Response> {
    val responseWithHeaders = Response(Status.fromCode(statusCode)!!).withGeneratedHeaders(headers)
    return when {
      bodySchema != null -> responseWithHeaders.flatMap { it.withGeneratedBody(bodySchema) }
      else               -> responseWithHeaders
    }
  }

  private fun Response.withGeneratedBody(bodySchema: BodySchema): Result<Response> =
    generateBody(bodySchema).map { value ->
      header("Content-Type", bodySchema.contentType.value)
        .body(bodySchema.serde.serialize(value))
    }

  private fun Response.withScenarioBody(scenarioBody: ScenarioBody?,
                                        responseSchema: ResponseSchema,
                                        fallbackBodySchema: BodySchema?): Result<Response> =
    when {
      scenarioBody != null       -> success(withExampleBody(scenarioBody, responseSchema))
      fallbackBodySchema != null -> withGeneratedBody(fallbackBodySchema)
      else                       -> success(this)
    }

  private fun Response.withExampleBody(scenarioBody: ScenarioBody, responseSchema: ResponseSchema): Response {
    val bodySchema = responseSchema.bodies.find { it.contentType == scenarioBody.contentType } ?: return this
    return header("Content-Type", scenarioBody.contentType.value)
      .body(bodySchema.serde.serialize(scenarioBody.value))
  }

  private fun Response.withGeneratedHeaders(headerSchemas: List<ParameterSchema>): Result<Response> =
    withScenarioHeaders(headerSchemas, emptyMap())

  private fun Response.withScenarioHeaders(headerSchemas: List<ParameterSchema>,
                                           scenarioHeaders: Map<ParameterElement.Header, Any?>): Result<Response> =
    headerSchemas.fold(success(this)) { acc, schema ->
      acc.flatMap { it.applyHeader(schema, scenarioHeaders) }
    }

  private fun Response.applyHeader(schema: ParameterSchema,
                                   scenarioHeaders: Map<ParameterElement.Header, Any?>): Result<Response> {
    val header = requireHeaderElement(schema)
    return resolveHeaderValue(schema, header, scenarioHeaders)
      .map { value ->
        schema.codec.encode(value).fold(this) { resp, (_, headerValue) ->
          resp.header(header.name, HttpHeaderValue.requireValid(header.name, headerValue))
        }
      }
  }

  private fun resolveHeaderValue(schema: ParameterSchema,
                                 header: ParameterElement.Header,
                                 scenarioHeaders: Map<ParameterElement.Header, Any?>): Result<Any?> =
    if (header in scenarioHeaders) success(scenarioHeaders[header])
    else generateHeader(schema)

  private fun requireHeaderElement(schema: ParameterSchema): ParameterElement.Header =
    requireNotNull(schema.element as? ParameterElement.Header) {
      "ResponseGenerator header pipeline received a non-header schema (element=${schema.element::class.simpleName})"
    }

  private fun generateBody(bodySchema: BodySchema): Result<Any?> =
    bodySchema.dataType
      .randomValue(GenerationContext.default())
      .toResult()
      .forProperty("body")

  private fun generateHeader(schema: ParameterSchema): Result<Any?> =
    schema.dataType
      .randomValue(GenerationContext.default())
      .toResult()
      .forKey(schema.element.name)
      .forProperty("header")
}
