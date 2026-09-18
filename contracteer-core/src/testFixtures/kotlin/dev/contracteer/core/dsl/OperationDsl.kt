package dev.contracteer.core.dsl

import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.operation.BodySchema
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.operation.ParameterElement
import dev.contracteer.core.operation.ParameterSchema
import dev.contracteer.core.operation.RequestSchema
import dev.contracteer.core.operation.ResponseSchema
import dev.contracteer.core.operation.ResponseSchemas
import dev.contracteer.core.operation.Scenario
import dev.contracteer.core.serde.JsonSerde
import dev.contracteer.core.serde.PlainTextSerde
import dev.contracteer.core.serde.Serde

fun apiOperation(
  method: String,
  path: String,
  block: ApiOperationBuilder.() -> Unit = {}
): ApiOperation = ApiOperationBuilder(method, path).apply(block).build()

@TestBuilder
class ApiOperationBuilder internal constructor(val method: String, val path: String) {
  private var requestBuilder: RequestBuilder? = null
  private val responses = mutableMapOf<Int, ResponseSchema>()
  private val classResponses = mutableMapOf<Int, ResponseSchema>()
  private var defaultSchema: ResponseSchema? = null
  private val scenarios = mutableListOf<Scenario>()

  fun request(block: RequestBuilder.() -> Unit) {
    requestBuilder = RequestBuilder().apply(block)
  }

  fun response(statusCode: Int, block: ResponseBuilder.() -> Unit = {}) {
    responses[statusCode] = ResponseBuilder().apply(block).build()
  }

  /** Declares a class response such as `4XX`, keyed by its leading digit. */
  fun classResponse(statusClass: Int, block: ResponseBuilder.() -> Unit = {}) {
    require(statusClass in 1..5) { "Status class must be 1..5 (for 1XX..5XX) but was $statusClass" }
    classResponses[statusClass] = ResponseBuilder().apply(block).build()
  }

  fun defaultResponse(block: ResponseBuilder.() -> Unit = {}) {
    defaultSchema = ResponseBuilder().apply(block).build()
  }

  fun scenario(key: String, status: Int, block: ScenarioBuilder.() -> Unit = {}) {
    scenarios += ScenarioBuilder(path, method, key, status).apply(block).build()
  }

  internal fun build(): ApiOperation = ApiOperation(
    path = path,
    method = method,
    requestSchema = (requestBuilder ?: RequestBuilder()).build(),
    responseSchemas = ResponseSchemas(
      byStatusCode = responses.toMap(),
      byClass = classResponses.toMap(),
      defaultResponse = defaultSchema
    ),
    scenarios = scenarios.toList()
  )
}

@TestBuilder
class RequestBuilder internal constructor() {
  private val parameters = mutableListOf<ParameterSchema>()
  private val bodies = mutableListOf<BodySchema>()

  fun pathParam(name: String, dataType: DataType<out Any>, isRequired: Boolean = true, codec: CodecFactory = simple()) {
    parameters += ParameterSchema(ParameterElement.PathParam(name), dataType, isRequired, codec(name))
  }

  fun queryParam(name: String, dataType: DataType<out Any>, isRequired: Boolean = false, codec: CodecFactory = form()) {
    parameters += ParameterSchema(ParameterElement.QueryParam(name), dataType, isRequired, codec(name))
  }

  fun header(name: String, dataType: DataType<out Any>, isRequired: Boolean = false, codec: CodecFactory = simple()) {
    parameters += ParameterSchema(ParameterElement.Header(name), dataType, isRequired, codec(name))
  }

  fun cookie(name: String, dataType: DataType<out Any>, isRequired: Boolean = false, codec: CodecFactory = form(explode = false)) {
    parameters += ParameterSchema(ParameterElement.Cookie(name), dataType, isRequired, codec(name))
  }

  fun jsonBody(dataType: DataType<out Any>, isRequired: Boolean = true) {
    bodies += BodySchema(ContentType("application/json"), dataType, isRequired, JsonSerde)
  }

  fun plainTextBody(dataType: DataType<out Any>, isRequired: Boolean = true) {
    bodies += BodySchema(ContentType("text/plain"), dataType, isRequired, PlainTextSerde)
  }

  fun body(contentType: String, dataType: DataType<out Any>, serde: Serde, isRequired: Boolean = true) {
    bodies += BodySchema(ContentType(contentType), dataType, isRequired, serde)
  }

  internal fun build(): RequestSchema = RequestSchema(parameters.toList(), bodies.toList())
}

@TestBuilder
class ResponseBuilder internal constructor() {
  private val headers = mutableListOf<ParameterSchema>()
  private val bodies = mutableListOf<BodySchema>()

  fun header(name: String, dataType: DataType<out Any>, isRequired: Boolean = false, codec: CodecFactory = simple()) {
    headers += ParameterSchema(ParameterElement.Header(name), dataType, isRequired, codec(name))
  }

  fun jsonBody(dataType: DataType<out Any>, isRequired: Boolean = true) {
    bodies += BodySchema(ContentType("application/json"), dataType, isRequired, JsonSerde)
  }

  fun plainTextBody(dataType: DataType<out Any>, isRequired: Boolean = true) {
    bodies += BodySchema(ContentType("text/plain"), dataType, isRequired, PlainTextSerde)
  }

  fun body(contentType: String, dataType: DataType<out Any>, serde: Serde, isRequired: Boolean = true) {
    bodies += BodySchema(ContentType(contentType), dataType, isRequired, serde)
  }

  internal fun build(): ResponseSchema = ResponseSchema(headers.toList(), bodies.toList())
}
