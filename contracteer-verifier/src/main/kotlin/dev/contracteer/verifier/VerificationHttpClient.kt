package dev.contracteer.verifier

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.UrlEncoding
import dev.contracteer.core.datatype.GenerationContext
import dev.contracteer.core.operation.*
import dev.contracteer.core.operation.ParameterElement.*
import dev.contracteer.core.result
import dev.contracteer.core.serde.MultipartSerde
import dev.contracteer.core.serde.Serde
import dev.contracteer.verifier.VerificationCase.*

internal class VerificationHttpClient(serverUrl: String) {
  private val serverUrl = serverUrl.trimEnd('/')
  private val baseClient = JavaHttpClient()

  fun execute(case: VerificationCase): Result<Pair<Request, Response>> =
    buildRequest(case).map { request -> request to baseClient(request) }

  private fun buildRequest(case: VerificationCase): Result<Request> =
    when (case) {
      is ScenarioBased -> buildScenarioRequest(case)
      is SchemaBased   -> buildSchemaBasedRequest(case)
      is TypeMismatch  -> buildTypeMismatchRequest(case)
    }

  private fun buildScenarioRequest(case: ScenarioBased): Result<Request> =
    result {
      val scenario = case.scenario
      validateScenarioParameterElements(scenario.request.parameterValues, case.requestSchema).bind()

      val pathParams = buildPathParameters(scenario.request.parameterValues, case.requestSchema).bind()
      val bodySchema = case.requestContentType?.let { ct -> case.requestSchema.bodies.find { it.contentType == ct } }
      Request(
        method = Method.valueOf(scenario.method.uppercase()),
        uri = resolvePathUri(scenario.path, pathParams))
        .withScenarioParameters(scenario.request.parameterValues, case.requestSchema).bind()
        .withScenarioRequestBody(scenario.request.body, bodySchema).bind()
        .withAcceptHeader(scenario.response.body?.contentType)
    }

  private fun buildSchemaBasedRequest(case: SchemaBased): Result<Request> =
    result {
      val pathParams = generatePathParameters(case.requestSchema.pathParameters).bind()
      Request(
        method = Method.valueOf(case.method.uppercase()),
        uri = resolvePathUri(case.path, pathParams))
        .withGeneratedParameters(case.requestSchema).bind()
        .withGeneratedBody(case.requestSchema.bodies.find { it.contentType == case.requestContentType }).bind()
        .withAcceptHeader(case.responseContentType)
    }

  private fun buildTypeMismatchRequest(case: TypeMismatch): Result<Request> =
    result {
      val mutatedElement = case.mutatedElement
      val pathParams = case.requestSchema.pathParameters.associate { param ->
        val value = when {
          mutatedElement is MutatedElement.Parameter && mutatedElement.element == param.element ->
            case.mutatedValue
          else                                                                                  ->
            param.codec.encode(param.generate().bind()).single().second
        }
        param.element.name to value
      }

      Request(
        method = Method.valueOf(case.method.uppercase()),
        uri = resolvePathUri(case.path, pathParams))
        .withTypeMismatchParameters(case).bind()
        .withTypeMismatchBody(case).bind()
        .withAcceptHeader(case.responseContentType)
    }

  private fun Request.withScenarioRequestBody(body: ScenarioBody?, bodySchema: BodySchema?): Result<Request> =
    when {
      body != null       -> success(withScenarioBody(body, bodySchema?.serde))
      bodySchema != null -> withGeneratedBody(bodySchema)
      else               -> success(this)
    }

  private fun Request.withTypeMismatchParameters(case: TypeMismatch): Result<Request> = result {
    val mutatedElement = case.mutatedElement
    case.requestSchema.parameters.fold(this@withTypeMismatchParameters) { req, param ->
      when {
        param.element is PathParam                                                            ->
          req
        mutatedElement is MutatedElement.Parameter && mutatedElement.element == param.element ->
          req.placeRawValue(param, case.mutatedValue)
        else                                                                                  ->
          req.placeEncodedEntries(param, param.codec.encode(param.generate().bind()))
      }
    }
  }

  private fun Request.withTypeMismatchBody(case: TypeMismatch): Result<Request> =
    if (case.mutatedElement is MutatedElement.Body)
      success(case.requestContentType?.let { header("Content-Type", it.value).body(case.mutatedValue) } ?: this)
    else
      withGeneratedBody(case.requestSchema.bodies.find { it.contentType == case.requestContentType })

  private fun buildPathParameters(parameterValues: Map<ParameterElement, Any?>,
                                  requestSchema: RequestSchema): Result<Map<String, String>> = result {
    requestSchema.pathParameters.flatMap { param ->
      val value = if (parameterValues.containsKey(param.element)) parameterValues[param.element]
      else param.generate().bind()
      param.codec.encode(value)
    }.toMap()
  }

  private fun generatePathParameters(pathParameters: List<ParameterSchema>): Result<Map<String, String>> = result {
    pathParameters.flatMap { param -> param.codec.encode(param.generate().bind()) }.toMap()
  }

  private fun Request.withScenarioParameters(parameterValues: Map<ParameterElement, Any?>,
                                             requestSchema: RequestSchema): Result<Request> = result {
    withParameters(requestSchema) { schema ->
      if (parameterValues.containsKey(schema.element)) parameterValues[schema.element]
      else schema.generate().bind()
    }
  }

  private fun Request.withGeneratedParameters(requestSchema: RequestSchema): Result<Request> = result {
    withParameters(requestSchema) { it.generate().bind() }
  }

  private fun Request.withParameters(requestSchema: RequestSchema, valueProvider: (ParameterSchema) -> Any?): Request =
    requestSchema.parameters.fold(this) { req, param ->
      when (param.element) {
        is PathParam -> req
        else         -> req.placeEncodedEntries(param, param.codec.encode(valueProvider(param)))
      }
    }

  private fun validateScenarioParameterElements(parameterValues: Map<ParameterElement, Any?>,
                                                requestSchema: RequestSchema): Result<Unit> {
    val allowedElements = requestSchema.parameters.map { it.element }.toSet()
    val unknownElements = parameterValues.keys.filterNot { it in allowedElements }
    return if (unknownElements.isNotEmpty())
      failure("No parameter schema found for elements: ${unknownElements.joinToString(", ")}")
    else
      success()
  }

  private fun Request.withScenarioBody(body: ScenarioBody?, serde: Serde?): Request {
    if (body == null || serde == null) return this
    return header("Content-Type", contentTypeHeaderValue(body.contentType, serde)).body(serde.serialize(body.value))
  }

  private fun Request.placeEncodedEntries(param: ParameterSchema, entries: List<Pair<String, String>>): Request =
    entries.fold(this) { request, (key, value) ->
      when (param.element) {
        is QueryParam if (param.codec.allowReserved) -> request.appendRawQueryEntry(key, value)
        is QueryParam                                -> request.query(key, value)
        is Header                                    -> request.header(key, HttpHeaderValue.requireValid(key, value))
        is Cookie                                    -> request.withCookie(key, value)
        else                                         -> request
      }
    }

  private fun Request.placeRawValue(param: ParameterSchema, value: String): Request =
    when (param.element) {
      is QueryParam if (param.codec.allowReserved) -> appendRawQueryEntry(param.element.name, value)
      is QueryParam                                -> query(param.element.name, value)
      is Header                                    -> header(param.element.name, HttpHeaderValue.requireValid(param.element.name, value))
      is Cookie                                    -> withCookie(param.element.name, value)
      else                                         -> this
    }

  // http4k's Request.cookie wraps the value in DQUOTEs, which RFC 6265bis treats as part of the value.
  private fun Request.withCookie(name: String, value: String): Request {
    val cookiePair = "$name=$value"
    return replaceHeader("Cookie", header("Cookie")?.let { "$it; $cookiePair" } ?: cookiePair)
  }

  private fun Request.appendRawQueryEntry(key: String, value: String): Request {
    val encodedEntry = "${UrlEncoding.encode(key, false)}=${UrlEncoding.encode(value, true)}"
    val currentQuery = uri.query
    val newQuery = if (currentQuery.isEmpty()) encodedEntry else "$currentQuery&$encodedEntry"
    return uri(uri.query(newQuery))
  }

  private fun Request.withGeneratedBody(bodySchema: BodySchema?): Result<Request> {
    if (bodySchema == null) return success(this)
    return bodySchema.generate().map { value ->
      header("Content-Type", contentTypeHeaderValue(bodySchema.contentType, bodySchema.serde))
        .body(bodySchema.serde.serialize(value))
    }
  }

  private fun ParameterSchema.generate(): Result<Any?> {
    val kind = when (element) {
      is PathParam  -> "path"
      is QueryParam -> "query"
      is Header     -> "header"
      is Cookie     -> "cookie"
    }
    return dataType.randomValue(GenerationContext.default())
      .toResult()
      .forKey(element.name)
      .forProperty(kind)
      .forProperty("request")
  }

  private fun BodySchema.generate(): Result<Any?> =
    dataType.randomValue(GenerationContext.default())
      .toResult()
      .forProperty("body")
      .forProperty("request")

  private fun contentTypeHeaderValue(contentType: ContentType, serde: Serde): String =
    if (serde is MultipartSerde) "${contentType.value}; boundary=${serde.boundary}" else contentType.value

  private fun Request.withAcceptHeader(contentType: ContentType?): Request {
    return contentType?.let { header("Accept", it.value) } ?: this
  }

  private fun resolvePathUri(path: String, pathParams: Map<String, String>): String =
    pathParams.entries.fold("$serverUrl$path") { uri, (name, value) ->
      uri.replace("{$name}", encodePathSegment(value))
    }

  // Path segments must use %20 for spaces; the underlying encoder produces `+` (form-encoding).
  private fun encodePathSegment(value: String): String =
    UrlEncoding.encode(value, false).replace("+", "%20")
}
