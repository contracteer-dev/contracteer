package dev.contracteer.core.swagger

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.SpecVersion.V31
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.NullDataType
import dev.contracteer.core.joinWithQuotes
import dev.contracteer.core.operation.ContentType
import java.math.BigDecimal

internal const val SINGLE_EXAMPLE_KEY = "_example"

internal fun MediaType.safeExamples() =
  examples ?: example?.let(::singleExampleMap) ?: emptyMap()

internal fun Parameter.safeExamples(): Map<String, Example> =
  examples
    ?: example?.let(::singleExampleMap)
    ?: content?.values?.firstOrNull()?.safeExamples()
    ?: emptyMap()

internal fun Parameter.safeIsRequired() =
  required ?: false

internal fun Parameter.safeAllowReserved() =
  allowReserved ?: false

internal fun Header.safeExamples() =
  examples ?: example?.let(::singleExampleMap) ?: emptyMap()

internal fun Header.safeIsRequired() =
  required ?: false

internal fun ApiResponse.safeHeaders() =
  headers ?: emptyMap()

internal fun Operation.safeParameters() =
  parameters ?: emptyList()

internal fun Schema<*>.safeEnum(): List<Any?> =
  enum ?: emptyList()

internal fun Schema<*>.effectiveEnum(): Result<List<Any?>> {
  val enum = safeEnum()
  val const = effectiveConst() ?: return success(enum)
  return if (enum.all { it == const }) success(listOf(const))
  else failure("Schema '$name': const value '$const' conflicts with enum [${enum.joinWithQuotes()}]")
}

internal fun <T> Schema<*>.mapEnum(transform: (Any) -> Result<T?>): Result<List<T?>> =
  effectiveEnum().flatMap { values ->
    values
      .map { value -> if (value == null) success(null) else transform(value) }
      .combineResults()
  }

internal fun Schema<*>.effectiveType(): String? =
  if (specVersion == V31) types?.singleOrNull { it != "null" }
  else type

internal fun Schema<*>.isNullable(): Boolean =
  if (specVersion == V31) "null" in types.orEmpty()
  else nullable ?: false

internal fun Schema<*>.effectiveLowerBound(): NumericBound =
  if (specVersion == V31) pickMostRestrictive(minimum, exclusiveMinimumValue) { excl, incl -> excl >= incl }
  else NumericBound(minimum, exclusiveMinimum ?: false)

internal fun Schema<*>.effectiveUpperBound(): NumericBound =
  if (specVersion == V31) pickMostRestrictive(maximum, exclusiveMaximumValue) { excl, incl -> excl <= incl }
  else NumericBound(maximum, exclusiveMaximum ?: false)

internal fun Schema<*>.effectiveMinimum(): BigDecimal? = effectiveLowerBound().value
internal fun Schema<*>.effectiveMaximum(): BigDecimal? = effectiveUpperBound().value
internal fun Schema<*>.effectiveExclusiveMinimum(): Boolean = effectiveLowerBound().isExclusive
internal fun Schema<*>.effectiveExclusiveMaximum(): Boolean = effectiveUpperBound().isExclusive

private fun pickMostRestrictive(inclusive: BigDecimal?,
                                exclusive: BigDecimal?,
                                exclusiveWinsOver: (excl: BigDecimal, incl: BigDecimal) -> Boolean): NumericBound =
  when {
    inclusive == null && exclusive == null  -> NumericBound(null, false)
    inclusive == null                       -> NumericBound(exclusive, true)
    exclusive == null                       -> NumericBound(inclusive, false)
    exclusiveWinsOver(exclusive, inclusive) -> NumericBound(exclusive, true)
    else                                    -> NumericBound(inclusive, false)
  }

internal fun Schema<*>.effectiveConst(): Any? =
  if (specVersion == V31) const else null

internal fun Schema<*>.effectiveContentEncoding(): String? =
  if (specVersion == V31) contentEncoding?.lowercase() else null

internal fun Schema<*>.effectiveContentMediaType(): String? =
  if (specVersion == V31) contentMediaType else null

internal fun Schema<*>.effectivePropertyNames(): Schema<*>? =
  if (specVersion == V31) propertyNames else null

internal fun Schema<*>.hasComposition(): Boolean =
  allOf != null || anyOf != null || oneOf != null

internal fun Schema<*>.hasNonNullableMultiType(): Boolean =
  (types?.count { it != "null" } ?: 0) > 1

internal fun Schema<*>.hasPrefixItems(): Boolean =
  !prefixItems.isNullOrEmpty()

internal fun Schema<*>.hasContains(): Boolean =
  contains != null || minContains != null || maxContains != null

internal fun Schema<*>.hasConditional(): Boolean =
  `if` != null || then != null || `else` != null

internal fun Schema<*>.hasNot(): Boolean =
  not != null

internal fun Schema<*>.hasUnevaluatedProperties(): Boolean =
  unevaluatedProperties != null

internal fun Schema<*>.hasUnevaluatedItems(): Boolean =
  unevaluatedItems != null

internal fun Schema<*>.hasPatternProperties(): Boolean =
  !patternProperties.isNullOrEmpty()

internal fun Schema<*>.hasDependentRequired(): Boolean =
  !dependentRequired.isNullOrEmpty()

internal fun Schema<*>.hasDependentSchemas(): Boolean =
  !dependentSchemas.isNullOrEmpty()

internal fun Schema<*>.hasContentSchema(): Boolean =
  contentSchema != null

internal fun Schema<*>.effectiveNonAnnotationSiblings(): List<String> =
  if (specVersion == V31) presentSchemaKeywords() else emptyList()

private fun Schema<*>.presentSchemaKeywords(): List<String> =
  buildList {
    if (type != null) add("type")
    if (!types.isNullOrEmpty()) add("types")
    if (properties != null) add("properties")
    if (additionalProperties != null) add("additionalProperties")
    if (required != null) add("required")
    if (minProperties != null) add("minProperties")
    if (maxProperties != null) add("maxProperties")
    if (items != null) add("items")
    if (minItems != null) add("minItems")
    if (maxItems != null) add("maxItems")
    if (uniqueItems != null) add("uniqueItems")
    if (pattern != null) add("pattern")
    if (minLength != null) add("minLength")
    if (maxLength != null) add("maxLength")
    if (minimum != null) add("minimum")
    if (maximum != null) add("maximum")
    if (exclusiveMinimumValue != null) add("exclusiveMinimum")
    if (exclusiveMaximumValue != null) add("exclusiveMaximum")
    if (multipleOf != null) add("multipleOf")
    if (!enum.isNullOrEmpty()) add("enum")
    if (const != null) add("const")
    if (format != null) add("format")
    if (contentEncoding != null) add("contentEncoding")
    if (contentMediaType != null) add("contentMediaType")
    if (propertyNames != null) add("propertyNames")
    if (not != null) add("not")
    if (hasPrefixItems()) add("prefixItems")
    if (hasContains()) add("contains")
    if (hasConditional()) add("if/then/else")
    if (hasUnevaluatedProperties()) add("unevaluatedProperties")
    if (hasUnevaluatedItems()) add("unevaluatedItems")
    if (hasPatternProperties()) add("patternProperties")
    if (hasDependentRequired()) add("dependentRequired")
    if (hasDependentSchemas()) add("dependentSchemas")
    if (hasContentSchema()) add("contentSchema")
  }

internal fun Schema<*>.isObjectLike(): Boolean =
  effectiveType() == "object" || properties != null || additionalProperties != null

internal fun Schema<*>.isArrayLike(): Boolean =
  effectiveType() == "array" || items != null

internal fun Schema<*>.hasStructuredTextContent(): Boolean =
  effectiveType() == "string" && effectiveContentMediaType()?.let(::ContentType)?.isStructuredText() == true

internal fun Schema<*>.isAnyType() =
  effectiveType() == null &&
  !isNullOnly() &&
  properties.isNullOrEmpty() &&
  additionalProperties == null &&
  format == null &&
  maximum == null &&
  minimum == null &&
  exclusiveMaximum == null &&
  exclusiveMinimum == null &&
  pattern == null &&
  minLength == null &&
  maxLength == null &&
  multipleOf == null &&
  default == null &&
  example == null &&
  `enum`.isNullOrEmpty() &&
  effectiveConst() == null

internal fun Schema<*>.isNullOnly(): Boolean =
  specVersion == V31 && effectiveType() == null && "null" in types.orEmpty()

internal fun DataType<out Any>.ensureNotStandaloneNull(location: String): Result<Unit> =
  if (this is NullDataType)
    failure(
      "$location: standalone 'type: null' is not a meaningful schema. " +
      "For a nullable value, use 'anyOf: [Type, {type: null}]'. " +
      "For an empty response, omit the 'content' field and use status code 204.")
  else success()

internal fun Schema<*>.safeProperties() =
  properties ?: emptyMap()

internal fun Components?.safeSchemas() =
  this?.schemas ?: emptyMap()

internal fun Components?.safeParameters() =
  this?.parameters ?: emptyMap()

internal fun Components?.safeRequestBodies() =
  this?.requestBodies ?: emptyMap()

internal fun Components?.safeExamples() =
  this?.examples ?: emptyMap()

internal fun Components?.safeResponses() =
  this?.responses ?: emptyMap()

internal fun Components?.safeHeaders() =
  this?.headers ?: emptyMap()

internal fun Discriminator.safeMapping() =
  mapping ?: emptyMap()

internal fun RequestBody.safeRequired() =
  required ?: false

internal fun Parameter.shortRef() =
  this.`$ref`?.replace("#/components/parameters/", "")

internal fun RequestBody.shortRef() =
  this.`$ref`?.replace("#/components/requestBodies/", "")

internal fun Header.shortRef() =
  this.`$ref`?.replace("#/components/headers/", "")

internal fun Example.shortRef() =
  this.`$ref`?.replace("#/components/examples/", "")

internal fun ApiResponse.shortRef() =
  this.`$ref`?.replace("#/components/responses/", "")

internal fun singleExampleMap(exampleValue: Any) =
  mapOf("_example" to Example().apply { value = exampleValue })

internal fun isClassCode(code: String): Boolean =
  code.length == 3 && code[0].isDigit() && code.substring(1).uppercase() == "XX"

internal fun isBodylessStatusCode(statusCode: Int): Boolean =
  statusCode in 100..199 || statusCode == 204 || statusCode == 205 || statusCode == 304

internal data class NumericBound(val value: BigDecimal?, val isExclusive: Boolean)
