package dev.contracteer.core.swagger

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.parameters.Parameter
import dev.contracteer.core.OperationRef
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.Result.Success
import dev.contracteer.core.combineResults
import dev.contracteer.core.operation.ApiOperation
import dev.contracteer.core.operation.ResponseSchemas
import dev.contracteer.core.swagger.datatype.DataTypeConverter

internal class ApiOperationExtractor(private val sharedComponents: SharedComponents,
                                     private val warnings: LoadWarnings) {

  private val logger = KotlinLogging.logger {}
  private val dataTypeConverter = DataTypeConverter(sharedComponents, warnings)
  private val schemaExtractor = SchemaExtractor(sharedComponents, dataTypeConverter)

  fun extract(openAPI: OpenAPI): Result<List<ApiOperation>> {
    val paths = openAPI.paths ?: emptyMap()
    val equivalentPathErrors = findEquivalentPaths(paths.keys)
    return when {
      equivalentPathErrors.isNotEmpty() -> failure(*equivalentPathErrors.toTypedArray())
      else                              ->
        paths
          .flatMap { it.toApiOperations() }
          .combineResults()
          .map {
            it.mapNotNull { operation -> filterUnsupportedOperation(operation, warnings) }
              .also { operations -> logExtractedOperations(operations) }
          }
    }
  }

  private fun Map.Entry<String, PathItem>.toApiOperations(): List<Result<ApiOperation>> {
    val resolvedPathParams = value.parameters.orEmpty()
      .map { sharedComponents.resolve(it) }
      .combineResults()
    return value.readOperationsMap().map { (method, operation) ->
      resolvedPathParams
        .flatMap { pathParams -> normalizeOperationParameters(operation, pathParams) }
        .flatMap { extractApiOperation(method.name, key, operation) }
        .forOperation(OperationRef(method.name, key))
    }
  }

  // OAS 3.0/3.1: path-level parameters apply to every operation on the path; operation-level
  // parameters with the same (name, in) override them. swagger-parser performs this merge for
  // 3.0 docs but not for 3.1 (issue #1950), and its isResolve setting also leaves operation-level
  // $ref parameters un-inlined for 3.1. We resolve operation-level refs, then merge in path-level
  // parameters, mutating operation.parameters in place so downstream extractors never see $refs.
  private fun normalizeOperationParameters(operation: Operation, resolvedPathParams: List<Parameter>): Result<Unit> =
    operation.safeParameters()
      .map { sharedComponents.resolve(it) }
      .combineResults()
      .map { resolvedOperationParams ->
        val operationKeys = resolvedOperationParams.map { it.name to it.`in` }.toSet()
        operation.parameters =
          resolvedPathParams.filterNot { (it.name to it.`in`) in operationKeys } + resolvedOperationParams
      }

  private fun extractApiOperation(method: String, path: String, operation: Operation): Result<ApiOperation> {
    val extractedRequest = schemaExtractor.extractRequestSchema(operation)
    val extractedByStatusCode = extractResponseSchemas(operation)
    val extractedByClass = extractClassResponses(operation)
    val extractedDefault = extractDefaultResponse(operation)

    if (!(extractedRequest is Success && extractedByStatusCode is Success && extractedByClass is Success && extractedDefault is Success))
      return (extractedRequest combineWith
          extractedByStatusCode combineWith
          extractedByClass combineWith extractedDefault).retypeError()

    val requestSchema = extractedRequest.value.toRequestSchema()
    val responseSchemas = ResponseSchemas(
      extractedByStatusCode.value.mapValues { it.value.toResponseSchema() },
      extractedByClass.value.mapValues { it.value.toResponseSchema() },
      extractedDefault.value?.toResponseSchema()
    )

    return validateHeadResponses(method, responseSchemas).flatMap {
      ScenarioBuilder
        .buildScenarios(method,
                        path,
                        extractedRequest.value,
                        extractedByStatusCode.value,
                        extractedByClass.value,
                        extractedDefault.value,
                        warnings)
        .map { scenarios -> ApiOperation(path, method, requestSchema, responseSchemas, scenarios) }
    }
  }

  private fun validateHeadResponses(method: String, responseSchemas: ResponseSchemas): Result<Unit> {
    if (!method.equals("head", ignoreCase = true)) return success()

    return when {
      responseSchemas.hasAnyBody() -> failure("HEAD responses MUST NOT include a message body (RFC 7231)")
      else                         -> success()
    }
  }

  private fun extractResponseSchemas(operation: Operation): Result<Map<Int, ExtractedResponseSchema>> =
    operation.responses
      .filter { (code, _) -> code != "default" && !isClassCode(code) }
      .map { (code, response) -> schemaExtractor.extractResponseSchema(code, response) }
      .combineResults()
      .map { list -> list.associate { it.first to it.second } }

  private fun extractClassResponses(operation: Operation): Result<Map<Int, ExtractedResponseSchema>> =
    operation.responses
      .filter { (code, _) -> isClassCode(code) }
      .map { (code, response) ->
        schemaExtractor.extractResponseSchema(response).map { code[0].digitToInt() to it }
      }
      .combineResults()
      .map { list -> list.associate { it.first to it.second } }

  private fun extractDefaultResponse(operation: Operation): Result<ExtractedResponseSchema?> =
    operation.responses["default"]
      ?.let { schemaExtractor.extractResponseSchema(it) }
    ?: success(null)

  private fun findEquivalentPaths(paths: Set<String>): List<String> =
    paths
      .groupBy { it.replace(PATH_PARAMETER_PATTERN, "{}") }
      .filterValues { it.size > 1 }
      .map { (_, equivalentPaths) ->
        "Equivalent paths found: ${equivalentPaths.joinToString(" and ") { "'$it'" }}. " +
        "These paths are identical after ignoring parameter names and are considered invalid by the OpenAPI Specification (OAS 3.0 §4.7.9)."
      }

  private fun logExtractedOperations(operations: List<ApiOperation>) {
    if (operations.isEmpty()) {
      logger.warn { "No valid operations were generated." }
    } else {
      logger.info { "Found ${operations.size} valid operation(s)." }
      logger.debug {
        operations.joinToString("${System.lineSeparator()}- ", "Operations:${System.lineSeparator()}- ") { operation ->
          "${operation.method.uppercase()} ${operation.path} -> [${operation.responseSchemas.summary()}]"
        }
      }
    }
  }

  companion object {
    private val PATH_PARAMETER_PATTERN = Regex("\\{[^}]+}")
  }
}
