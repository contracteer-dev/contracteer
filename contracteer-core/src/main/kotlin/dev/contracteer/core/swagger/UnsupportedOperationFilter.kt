package dev.contracteer.core.swagger

import dev.contracteer.core.OperationRef
import dev.contracteer.core.codec.ContentCodec
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.operation.BodySchema
import dev.contracteer.core.operation.ResponseSchema
import dev.contracteer.core.operation.Scenario

internal fun filterUnsupportedOperation(operation: ApiOperation, warnings: LoadWarnings): ApiOperation? {
  val operationRef = OperationRef(operation.method, operation.path)
  if (operation.requestSchema.parameters.any { it.dataType is AnyDataType && it.codec is ContentCodec }) {
    warnings.warn("Operation excluded: parameter content has no schema.", operationRef)
    return null
  }

  val requestBodies = operation.requestSchema.bodies.filterNot { it.isUnsupported() }
  val filteredResponseSchemas = operation.responseSchemas.mapSchemas { filterUnsupportedBodies(it) }
  val scenarios = operation.scenarios
    .filterNot { it.hasXmlContentType() }
    .filter { filteredResponseSchemas.responseFor(it.statusCode) != null }

  if (operation.requestSchema.bodies.isNotEmpty() && requestBodies.isEmpty()) {
    warnings.warn("Operation excluded: no supported request body content type.", operationRef)
    return null
  }

  if (operation.responseSchemas.hasResponses() && !filteredResponseSchemas.hasResponses()) {
    warnings.warn("Operation excluded: no supported response content type.", operationRef)
    return null
  }

  return operation.copy(
    requestSchema = operation.requestSchema.copy(bodies = requestBodies),
    responseSchemas = filteredResponseSchemas,
    scenarios = scenarios
  )
}

private fun filterUnsupportedBodies(schema: ResponseSchema): ResponseSchema? {
  val filtered = schema.copy(bodies = schema.bodies.filterNot { it.isUnsupported() })
  return if (schema.bodies.isNotEmpty() && filtered.bodies.isEmpty()) null else filtered
}

private fun Scenario.hasXmlContentType() =
  request.body?.contentType?.isXml() == true || response.body?.contentType?.isXml() == true

private fun BodySchema.isUnsupported() = contentType.isXml() || dataType is AnyDataType