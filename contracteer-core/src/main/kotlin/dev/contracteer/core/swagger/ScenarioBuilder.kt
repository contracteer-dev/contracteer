package dev.contracteer.core.swagger

import io.swagger.v3.oas.models.examples.Example
import dev.contracteer.core.OperationRef
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.combineResults
import dev.contracteer.core.operation.*
import dev.contracteer.core.operation.ParameterElement.*

private val STATUS_CODE_PREFIX = Regex("""^(\d{3})_\w+$""")

internal object ScenarioBuilder {

  /**
   * Builds the list of [Scenario]s implied by matching example keys across the request and response schemas.
   *
   * Non-400 scenario examples are validated against their schemas; a validation failure yields a [Result.Failure].
   * Status-code-prefixed keys (e.g. `404_NOT_FOUND`) that cannot be resolved are reported to [warnings] and ignored.
   */
  fun buildScenarios(method: String,
                     path: String,
                     request: ExtractedRequestSchema,
                     byStatusCode: Map<Int, ExtractedResponseSchema>,
                     byClass: Map<Int, ExtractedResponseSchema>,
                     default: ExtractedResponseSchema?,
                     warnings: LoadWarnings): Result<List<Scenario>> {

    if (request.exampleKeys().isEmpty()) return success(emptyList())
    warnUnresolvablePrefixedKeys(method, path, request, byStatusCode, byClass, default, warnings)

    return targetResponses(request, byStatusCode, byClass, default)
      .map { (statusCode, response) ->
        buildScenariosForResponse(method, path, request, statusCode, response)
      }
      .combineResults()
      .map { it.flatten() }
  }

  private fun responseFor(statusCode: Int,
                          byStatusCode: Map<Int, ExtractedResponseSchema>,
                          byClass: Map<Int, ExtractedResponseSchema>,
                          default: ExtractedResponseSchema?): ExtractedResponseSchema? =
    byStatusCode[statusCode] ?: byClass[statusCode / 100] ?: default

  private fun targetResponses(request: ExtractedRequestSchema,
                              byStatusCode: Map<Int, ExtractedResponseSchema>,
                              byClass: Map<Int, ExtractedResponseSchema>,
                              default: ExtractedResponseSchema?): Map<Int, ExtractedResponseSchema> {
    val allStatusCodes = byStatusCode.keys +
                         request.exampleKeys().mapNotNull { it.statusCodePrefix() }

    return allStatusCodes
      .mapNotNull { statusCode -> responseFor(statusCode, byStatusCode, byClass, default)?.let { statusCode to it } }
      .toMap()
  }

  private fun buildScenariosForResponse(method: String,
                                        path: String,
                                        request: ExtractedRequestSchema,
                                        statusCode: Int,
                                        response: ExtractedResponseSchema): Result<List<Scenario>> {
    val responseExampleKeys = response.exampleKeys()
    val prefixedKeys = request.exampleKeys().filter { it.statusCodePrefix() == statusCode }
    val nonPrefixedKeys = request.exampleKeys().filter { it.statusCodePrefix() == null }
    val scenarioKeys = (nonPrefixedKeys intersect responseExampleKeys) union prefixedKeys

    return when {
      scenarioKeys.isEmpty() -> success(emptyList())
      else                   ->
        scenarioKeys
          .map { key -> buildScenarioForKey(key, method, path, request, statusCode, response) }
          .combineResults()
          .map { it.flatten() }
    }
  }

  private fun buildScenarioForKey(key: String,
                                  method: String,
                                  path: String,
                                  request: ExtractedRequestSchema,
                                  statusCode: Int,
                                  response: ExtractedResponseSchema): Result<List<Scenario>> {
    val examples = ExampleValues(
      requestParams = request.parameterExamplesFor(key),
      requestBodies = request.bodyExamplesFor(key).ifEmpty { listOf(null) },
      responseHeaders = response.headerExamplesFor(key),
      responseBodies = response.bodyExamplesFor(key).ifEmpty { listOf(null) }
    )
    val scenarios = cartesianScenarios(method, path, key, statusCode, examples)

    return when (statusCode) {
      400  -> success(scenarios)
      else -> validateExamples(key, examples, request, response).map { scenarios }
    }
  }

  private fun cartesianScenarios(method: String,
                                 path: String,
                                 key: String,
                                 statusCode: Int,
                                 examples: ExampleValues): List<Scenario> =
    examples.requestBodies.flatMap { reqBody ->
      examples.responseBodies.map { resBody ->
        Scenario(
          path = path,
          method = method,
          key = key,
          statusCode = statusCode,
          request = ScenarioRequest(examples.requestParams, reqBody),
          response = ScenarioResponse(examples.responseHeaders, resBody)
        )
      }
    }

  private fun validateExamples(key: String,
                               examples: ExampleValues,
                               request: ExtractedRequestSchema,
                               response: ExtractedResponseSchema): Result<Any?> {
    val requestValidation = validateRequestExamples(key,
                                                    examples.requestParams,
                                                    examples.requestBodies,
                                                    request).forProperty("request")
    val responseValidation = validateResponseExamples(key,
                                                      examples.responseHeaders,
                                                      examples.responseBodies,
                                                      response).forProperty("response")

    return requestValidation combineWith responseValidation
  }

  private fun validateRequestExamples(key: String,
                                      params: Map<ParameterElement, Any?>,
                                      bodies: List<ScenarioBody?>,
                                      request: ExtractedRequestSchema): Result<Any?> {
    val schemasByElement = request.parameters.associate { it.schema.element to it.schema }
    val paramValidation = params.map { (element, value) ->
      when (val paramSchema = schemasByElement[element]) {
        null -> failure("Example '$key' has no schema for request parameter '${elementName(element)}'")
        else -> paramSchema.dataType.validate(value).forExample(key).forProperty(elementName(element))
      }
    }.combineResults()

    val bodyValidation = validateBodies(key, "request", bodies, request.bodies.map { it.schema })

    return paramValidation combineWith bodyValidation
  }

  private fun validateResponseExamples(key: String,
                                       headers: Map<Header, Any?>,
                                       bodies: List<ScenarioBody?>,
                                       response: ExtractedResponseSchema): Result<Any?> {
    val schemasByElement = response.headers.associate { it.schema.element to it.schema }
    val headerValidation = headers.map { (element, value) ->
      when (val headerSchema = schemasByElement[element]) {
        null -> failure("Example '$key' has no schema for response header '${elementName(element)}'")
        else -> headerSchema.dataType.validate(value).forExample(key).forProperty(elementName(element))
      }
    }.combineResults()

    val bodyValidation = validateBodies(key, "response", bodies, response.bodies.map { it.schema })

    return headerValidation combineWith bodyValidation
  }

  private fun validateBodies(key: String,
                             role: String,
                             bodies: List<ScenarioBody?>,
                             bodySchemas: List<BodySchema>): Result<Any?> {
    val multiContent = bodySchemas.size > 1
    return bodies.map { body ->
      if (body == null) success()
      else when (val bodySchema = bodySchemas.find { it.contentType.value == body.contentType.value }) {
        null -> failure("Example '$key' has no schema for $role body '${body.contentType.value}'")
        else -> bodySchema.dataType.validate(body.value)
          .forExample(key)
          .let { if (multiContent) it.forKey(body.contentType.value) else it }
          .forProperty("body")
      }
    }.combineResults()
  }

  private fun warnUnresolvablePrefixedKeys(method: String,
                                           path: String,
                                           request: ExtractedRequestSchema,
                                           byStatusCode: Map<Int, ExtractedResponseSchema>,
                                           byClass: Map<Int, ExtractedResponseSchema>,
                                           default: ExtractedResponseSchema?,
                                           warnings: LoadWarnings) {
    request.exampleKeys().forEach { key ->
      val statusCode = key.statusCodePrefix()
      if (statusCode != null && responseFor(statusCode, byStatusCode, byClass, default) == null) {
        warnings.warn(
          "Example key '$key' targets status code $statusCode, but no response with that status code is defined. Key ignored.",
          OperationRef(method, path)
        )
      }
    }
  }

  private fun <T> Result<T>.forExample(key: String): Result<T> =
    if (key == SINGLE_EXAMPLE_KEY) forProperty("example")
    else forKey(key).forProperty("examples")

  private fun elementName(element: ParameterElement) =
    when (element) {
      is PathParam  -> "path[${element.name}]"
      is QueryParam -> "query[${element.name}]"
      is Header     -> "header[${element.name}]"
      is Cookie     -> "cookie[${element.name}]"
    }

  private fun String.statusCodePrefix(): Int? =
    STATUS_CODE_PREFIX.matchEntire(this)?.groupValues?.get(1)?.toIntOrNull()

  private data class ExampleValues(
    val requestParams: Map<ParameterElement, Any?>,
    val requestBodies: List<ScenarioBody?>,
    val responseHeaders: Map<Header, Any?>,
    val responseBodies: List<ScenarioBody?>
  )
}
