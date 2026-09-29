package dev.contracteer.core.swagger

import dev.contracteer.core.OperationRef
import dev.contracteer.core.codec.ContentCodec
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.operation.BodySchema
import dev.contracteer.core.operation.ResponseSchema
import dev.contracteer.core.operation.ResponseSchemas
import dev.contracteer.core.operation.Scenario
import dev.contracteer.core.operation.ScenarioBody

internal fun filterUnsupportedOperation(operation: ApiOperation, warnings: LoadWarnings): ApiOperation? {
  val operationRef = OperationRef(operation.method, operation.path)
  val supported = operation.withoutUnsupportedContent()
  val exclusionReason = operation.exclusionReason(supported)
  if (exclusionReason != null) {
    warnings.warn("Operation excluded: $exclusionReason.", operationRef)
    return null
  }
  operation.exclusionWarnings(supported.responseSchemas).forEach { warnings.warn(it, operationRef) }
  return supported
}

private fun ApiOperation.withoutUnsupportedContent(): ApiOperation {
  val supportedResponses = responseSchemas.mapSchemas { filterUnsupportedBodies(it) }
  return copy(
    requestSchema = requestSchema.copy(bodies = requestSchema.bodies.filterNot { it.isUnsupported() }),
    responseSchemas = supportedResponses,
    scenarios = scenarios.filter { exclusionReason(it, supportedResponses) == null }
  )
}

private fun ApiOperation.exclusionReason(supported: ApiOperation): String? =
  when {
    requestSchema.parameters.any { it.dataType is AnyDataType && it.codec is ContentCodec } ->
      "parameter content has no schema"
    requestSchema.bodies.isNotEmpty() && supported.requestSchema.bodies.isEmpty()           ->
      "no supported request body content type"
    responseSchemas.hasResponses() && !supported.responseSchemas.hasResponses()             ->
      "no supported response content type"
    else                                                                                    -> null
  }

private fun ApiOperation.exclusionWarnings(supportedResponses: ResponseSchemas): List<String> =
  requestSchema.bodies.exclusionWarnings("Request body") +
  responseSchemas.responsesByLabel().flatMap { (label, schema) -> schema.exclusionWarnings(responseName(label)) } +
  scenarios.mapNotNull { scenario ->
    exclusionReason(scenario, supportedResponses)?.let { "Scenario '${scenario.key}' excluded: $it." }
  }

private fun ResponseSchema.exclusionWarnings(name: String): List<String> =
  if (filterUnsupportedBodies(this) == null) listOf("$name excluded: no supported body.")
  else bodies.exclusionWarnings("$name body")

private fun List<BodySchema>.exclusionWarnings(name: String): List<String> =
  filter { it.isUnsupported() }
    .map { "$name '${it.contentType.value}' excluded: ${it.unsupportedReason()}." }

private fun responseName(label: String) =
  if (label == "default") "Default response" else "Response $label"

private fun filterUnsupportedBodies(schema: ResponseSchema): ResponseSchema? {
  val filtered = schema.copy(bodies = schema.bodies.filterNot { it.isUnsupported() })
  return if (schema.bodies.isNotEmpty() && filtered.bodies.isEmpty()) null else filtered
}

private fun ApiOperation.exclusionReason(scenario: Scenario, supportedResponses: ResponseSchemas): String? =
  if (supportedResponses.responseFor(scenario.statusCode) == null) "${declaredResponse(scenario.statusCode)} is excluded"
  else scenario.xmlBody()?.let { "its $it is excluded" }

private fun ApiOperation.declaredResponse(statusCode: Int): String {
  val label = responseSchemas.labelFor(statusCode) ?: statusCode.toString()
  return if (label == "default") "the default response" else "response $label"
}

private fun Scenario.xmlBody(): String? =
  request.body.ifXml("request body") ?: response.body.ifXml("response body")

private fun ScenarioBody?.ifXml(name: String): String? =
  this?.contentType?.takeIf { it.isXml() }?.let { "$name '${it.value}'" }

private fun BodySchema.isUnsupported() = contentType.isXml() || dataType is AnyDataType

private fun BodySchema.unsupportedReason() =
  if (contentType.isXml()) "XML content is not supported" else "its schema is empty or missing"
