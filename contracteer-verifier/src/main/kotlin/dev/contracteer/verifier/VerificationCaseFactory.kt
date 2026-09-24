package dev.contracteer.verifier

import io.github.oshai.kotlinlogging.KotlinLogging
import dev.contracteer.core.datatype.AllOfDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.ObjectDataType
import dev.contracteer.core.operation.*
import dev.contracteer.core.operation.PrimaryResponse.Resolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.Ambiguous
import dev.contracteer.verifier.VerificationCase.*

/**
 * Plans the verification of an [ApiOperation]: generates its [VerificationCase] instances and
 * reports an [UnverifiedPrimaryResponse] when no case can assert its primary response.
 *
 * Produces three kinds of verification cases:
 * - [VerificationCase.ScenarioBased]: one per scenario defined in the operation
 * - [VerificationCase.SchemaBased]: generated from the schema when no scenario targets the operation's primary response
 * - [VerificationCase.TypeMismatch]: generated when the document covers status `400`, exactly or through `4XX` or `default`
 */
object VerificationCaseFactory {
  private val logger = KotlinLogging.logger {}

  /**
   * Plans the verification of [apiOperation]: the cases to run and, when the operation's primary
   * response cannot be resolved, the report that it goes unverified.
   *
   * An ambiguous primary response is not reported when every competing status code has a
   * scenario: each of them is then asserted.
   */
  @JvmStatic
  fun plan(apiOperation: ApiOperation): VerificationPlan {
    val primaryResponse = apiOperation.responseSchemas.primaryResponse()
    return VerificationPlan(
      cases = createScenarioBasedCases(apiOperation) +
              createSchemaBasedCasesIfNeeded(apiOperation, primaryResponse) +
              createTypeMismatchCases(apiOperation),
      unverifiedPrimaryResponse = unverifiedPrimaryResponse(apiOperation, primaryResponse)
    )
  }

  /** Creates all verification cases for the given [apiOperation]: the cases of its [plan]. */
  @JvmStatic
  fun create(apiOperation: ApiOperation): List<VerificationCase> = plan(apiOperation).cases

  private fun unverifiedPrimaryResponse(apiOperation: ApiOperation,
                                        primaryResponse: PrimaryResponse): UnverifiedPrimaryResponse? =
    when (primaryResponse) {
      is Resolved                                                              -> null
      is Ambiguous if everyCandidateHasScenario(apiOperation, primaryResponse) -> null
      is Unresolved                                                            ->
        UnverifiedPrimaryResponse(apiOperation.method, apiOperation.path, primaryResponse)
    }

  private fun everyCandidateHasScenario(apiOperation: ApiOperation, ambiguous: Ambiguous): Boolean =
    ambiguous.candidates.all { apiOperation.hasScenarioFor(it) }

  private fun ApiOperation.hasScenarioFor(statusCode: Int): Boolean =
    scenarios.any { it.statusCode == statusCode }

  private fun createScenarioBasedCases(apiOperation: ApiOperation): List<ScenarioBased> {
    return apiOperation.scenarios.flatMap { scenario ->
      val responseSchema = apiOperation.responseSchemas.responseFor(scenario.statusCode)
                           ?: error("No response schema found for status code ${scenario.statusCode} in operation ${apiOperation.method} ${apiOperation.path}")

      requestContentTypesFor(scenario, apiOperation.requestSchema).map { contentType ->
        ScenarioBased(
          scenario = scenario,
          requestSchema = apiOperation.requestSchema,
          responseSchema = responseSchema,
          requestContentType = contentType
        )
      }
    }
  }

  private fun requestContentTypesFor(scenario: Scenario, requestSchema: RequestSchema): List<ContentType?> {
    scenario.request.body?.let { return listOf(it.contentType) }
    val requiredBodies = requestSchema.bodies.filter { it.isRequired }
    return if (requiredBodies.isEmpty()) listOf(null) else requiredBodies.map { it.contentType }
  }

  private fun createSchemaBasedCasesIfNeeded(apiOperation: ApiOperation,
                                             primaryResponse: PrimaryResponse): List<SchemaBased> =
    if (primaryResponse is Resolved) createSchemaBasedCasesUnlessScenarioCovers(apiOperation, primaryResponse)
    else emptyList()

  private fun createSchemaBasedCasesUnlessScenarioCovers(apiOperation: ApiOperation,
                                                         primaryResponse: Resolved): List<SchemaBased> =
    if (apiOperation.hasScenarioFor(primaryResponse.statusCode)) emptyList()
    else createSchemaBasedCases(apiOperation, primaryResponse.statusCode, primaryResponse.schema)

  private fun createTypeMismatchCases(apiOperation: ApiOperation): List<TypeMismatch> {
    val badRequestResponse = apiOperation.responseSchemas.badRequestResponse() ?: return emptyList()
    val responseContentType = badRequestResponse.bodies.firstOrNull()?.contentType
    val requestSchema = apiOperation.requestSchema

    val cases = listOfNotNull(
      createParameterTypeMismatch(apiOperation, responseContentType, requestSchema.pathParameters),
      createParameterTypeMismatch(apiOperation, responseContentType, requestSchema.queryParameters),
      createParameterTypeMismatch(apiOperation, responseContentType, requestSchema.headers),
      createParameterTypeMismatch(apiOperation, responseContentType, requestSchema.cookies),
      createBodyTypeMismatch(apiOperation, responseContentType)
    )

    if (cases.isEmpty()) {
      logger.warn {
        "Operation ${apiOperation.method} ${apiOperation.path} defines a 400 response but no type mismatch " +
        "verification case could be generated (all request elements are non-mutable types such as string)."
      }
    }

    return cases
  }

  private fun createParameterTypeMismatch(
    apiOperation: ApiOperation,
    responseContentType: ContentType?,
    parameters: List<ParameterSchema>
  ): TypeMismatch? {
    val (param, mutatedValue) = findFirstMutableParameter(parameters) ?: return null
    val mutatedElement = MutatedElement.Parameter(param.element)

    return TypeMismatch(
      path = apiOperation.path,
      method = apiOperation.method,
      requestContentType = null,
      responseContentType = responseContentType,
      requestSchema = apiOperation.requestSchema,
      expectedResponses = expectedResponsesFor(mutatedElement, apiOperation.responseSchemas),
      mutatedElement = mutatedElement,
      mutatedValue = mutatedValue
    )
  }

  private fun findFirstMutableParameter(parameters: List<ParameterSchema>): Pair<ParameterSchema, String>? =
    parameters
      .filter { it.codec.supportsTypeMismatchMutation(it.dataType) }
      .firstNotNullOfOrNull { param ->
        TypeMismatchMutation.mutate(param.dataType)?.let { mutated -> param to mutated }
      }

  private fun createBodyTypeMismatch(
    apiOperation: ApiOperation,
    responseContentType: ContentType?
  ): TypeMismatch? {
    val mutableBody = findFirstMutableBody(apiOperation.requestSchema.bodies) ?: return null

    return TypeMismatch(
      path = apiOperation.path,
      method = apiOperation.method,
      requestContentType = mutableBody.first.contentType,
      responseContentType = responseContentType,
      requestSchema = apiOperation.requestSchema,
      expectedResponses = expectedResponsesFor(MutatedElement.Body, apiOperation.responseSchemas),
      mutatedElement = MutatedElement.Body,
      mutatedValue = mutableBody.second
    )
  }

  private fun expectedResponsesFor(mutatedElement: MutatedElement,
                                   responseSchemas: ResponseSchemas): Map<Int, ResponseSchema> =
    rejectionStatusCodesFor(mutatedElement)
      .mapNotNull { statusCode -> responseSchemas.responseFor(statusCode)?.let { statusCode to it } }
      .toMap()

  private fun rejectionStatusCodesFor(mutatedElement: MutatedElement): List<Int> =
    if (mutatedElement.isPartOfTargetUri()) listOf(400, 404, 422) else listOf(400, 422)

  private fun MutatedElement.isPartOfTargetUri(): Boolean =
    this is MutatedElement.Parameter && (element is ParameterElement.PathParam || element is ParameterElement.QueryParam)

  private fun findFirstMutableBody(bodies: List<BodySchema>): Pair<BodySchema, String>? =
    bodies
      .filter { !it.isFormWithAllOptionalProperties() }
      .firstNotNullOfOrNull { body ->
        TypeMismatchMutation.mutate(body.dataType)?.let { mutated -> body to mutated }
      }

  private fun BodySchema.isFormWithAllOptionalProperties(): Boolean {
    if (!contentType.isFormUrlEncoded()) return false
    return dataType.isStructurallyPermissive()
  }

  private fun DataType<out Any>.isStructurallyPermissive(): Boolean = when (this) {
    is ObjectDataType -> requiredProperties.isEmpty() && allowAdditionalProperties
    is AllOfDataType  -> subTypes.isNotEmpty() && subTypes.all { it.isStructurallyPermissive() }
    else              -> false
  }

  private fun createSchemaBasedCases(
    apiOperation: ApiOperation,
    statusCode: Int,
    responseSchema: ResponseSchema
  ): List<SchemaBased> {
    val requestBodies = apiOperation.requestSchema.bodies.ifEmpty { listOf(null) }
    val responseBodies = responseSchema.bodies.ifEmpty { listOf(null) }

    return requestBodies.flatMap { requestBody ->
      responseBodies.map { responseBody ->
        SchemaBased(
          path = apiOperation.path,
          method = apiOperation.method,
          statusCode = statusCode,
          requestContentType = requestBody?.contentType,
          responseContentType = responseBody?.contentType,
          requestSchema = apiOperation.requestSchema,
          responseSchema = responseSchema
        )
      }
    }
  }
}
