package dev.contracteer.core.swagger

import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.JsonSchema
import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.accumulate
import dev.contracteer.core.joinWithQuotes

internal fun mergeSchemaAndSiblings(target: Schema<*>,
                                    sibling: Schema<*>,
                                    displayName: String): Result<Schema<*>> {
  val merged = cloneAsV31(target)
  val steps = listOf(
    { mergeType(target, sibling, displayName) },
    { mergeMinLength(target, sibling, merged) },
    { mergeMaxLength(target, sibling, merged) },
    { mergeMinimum(target, sibling, merged) },
    { mergeMaximum(target, sibling, merged) },
    { mergeExclusiveMinimum(target, sibling, merged) },
    { mergeExclusiveMaximum(target, sibling, merged) },
    { mergeMinProperties(target, sibling, merged) },
    { mergeMaxProperties(target, sibling, merged) },
    { mergeMinItems(target, sibling, merged) },
    { mergeMaxItems(target, sibling, merged) },
    { mergeUniqueItems(target, sibling, merged) },
    { mergeMultipleOf(target, sibling, merged, displayName) },
    { mergePattern(target, sibling, merged, displayName) },
    { mergeItems(target, sibling, merged, displayName) },
    { mergeAdditionalProperties(target, sibling, merged, displayName) },
    { mergeConst(target, sibling, merged, displayName) },
    { mergeEnum(target, sibling, merged, displayName) },
    { mergeRequired(target, sibling, merged) },
    { mergeProperties(target, sibling, merged, displayName) },
  )
  return steps
    .fold(success(emptySet<String>())) { acc, step -> acc.flatMap { handled -> step().map { handled + it } } }
    .flatMap { handled -> rejectUnhandledSiblings(sibling.effectiveNonAnnotationSiblings(), displayName, handled) }
    .map { merged }
}

private fun mergeType(target: Schema<*>, sibling: Schema<*>, name: String): Result<Set<String>> {
  val siblingType = sibling.effectiveType() ?: return success(emptySet())
  val targetType = target.effectiveType()
  return when {
    targetType == siblingType        -> success(setOf("type", "types"))
    target.hasNonNullableMultiType() -> failure("Schema '$name': sibling 'type: $siblingType' cannot narrow a target declaring multiple types (${target.types}). Multi-type targets with sibling 'type' are not yet supported.")
    else                             -> failure("Schema '$name': sibling 'type: $siblingType' conflicts with referenced target type '${targetType ?: "(none)"}'."
    )
  }
}

private fun mergeMinLength(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("minLength", target.minLength, sibling.minLength, ::maxOf) { merged.minLength = it }

private fun mergeMaxLength(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("maxLength", target.maxLength, sibling.maxLength, ::minOf) { merged.maxLength = it }

private fun mergeMinimum(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("minimum", target.minimum, sibling.minimum, ::maxOf) { merged.minimum = it }

private fun mergeMaximum(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("maximum", target.maximum, sibling.maximum, ::minOf) { merged.maximum = it }

private fun mergeExclusiveMinimum(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("exclusiveMinimum",
               target.exclusiveMinimumValue,
               sibling.exclusiveMinimumValue,
               ::maxOf) { merged.exclusiveMinimumValue = it }

private fun mergeExclusiveMaximum(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("exclusiveMaximum",
               target.exclusiveMaximumValue,
               sibling.exclusiveMaximumValue,
               ::minOf) { merged.exclusiveMaximumValue = it }

private fun mergeMinProperties(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("minProperties", target.minProperties, sibling.minProperties, ::maxOf) { merged.minProperties = it }

private fun mergeMaxProperties(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("maxProperties", target.maxProperties, sibling.maxProperties, ::minOf) { merged.maxProperties = it }

private fun mergeMinItems(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("minItems", target.minItems, sibling.minItems, ::maxOf) { merged.minItems = it }

private fun mergeMaxItems(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> =
  mergeTighter("maxItems", target.maxItems, sibling.maxItems, ::minOf) { merged.maxItems = it }

private fun mergeUniqueItems(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> {
  val siblingUnique = sibling.uniqueItems ?: return success(emptySet())
  merged.uniqueItems = siblingUnique || (target.uniqueItems ?: false)
  return success(setOf("uniqueItems"))
}

private fun mergeMultipleOf(target: Schema<*>,
                            sibling: Schema<*>,
                            merged: JsonSchema,
                            name: String): Result<Set<String>> {
  val siblingMultipleOf = sibling.multipleOf ?: return success(emptySet())
  val targetMultipleOf = target.multipleOf
  if (targetMultipleOf != null && targetMultipleOf != siblingMultipleOf) return failure(
    "Schema '$name': sibling 'multipleOf: $siblingMultipleOf' conflicts with target 'multipleOf: $targetMultipleOf'."
  )
  merged.multipleOf = siblingMultipleOf
  return success(setOf("multipleOf"))
}

private fun mergePattern(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema, name: String): Result<Set<String>> {
  val siblingPattern = sibling.pattern ?: return success(emptySet())
  if (target.pattern != null) return failure(
    "Schema '$name': sibling 'pattern' conflicts with target 'pattern'. Merging multiple patterns is not supported."
  )
  merged.pattern = siblingPattern
  return success(setOf("pattern"))
}

private fun mergeItems(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema, name: String): Result<Set<String>> =
  mergeNoOverlap("items", target.items, sibling.items, name) { merged.items = it }

private fun mergeAdditionalProperties(target: Schema<*>,
                                      sibling: Schema<*>,
                                      merged: JsonSchema,
                                      name: String): Result<Set<String>> =
  mergeNoOverlap("additionalProperties",
                 target.additionalProperties,
                 sibling.additionalProperties,
                 name) { merged.additionalProperties = it }

private fun mergeConst(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema, name: String): Result<Set<String>> {
  val siblingConst = sibling.effectiveConst() ?: return success(emptySet())
  val targetConst = target.effectiveConst()
  val targetEnum = target.enum.orEmpty()
  if (targetConst != null && targetConst != siblingConst) return failure(
    "Schema '$name': sibling 'const: $siblingConst' conflicts with target 'const: $targetConst'."
  )
  if (targetConst == null && targetEnum.isNotEmpty() && siblingConst !in targetEnum) return failure(
    "Schema '$name': sibling 'const: $siblingConst' is not in target 'enum' (${targetEnum.joinWithQuotes()})."
  )
  merged.const = siblingConst
  merged.enum = null
  return success(setOf("const"))
}

private fun <T: Any> mergeNoOverlap(keyword: String,
                                    targetValue: T?,
                                    siblingValue: T?,
                                    name: String,
                                    applyTo: (T) -> Unit): Result<Set<String>> {
  return when {
    siblingValue == null -> success(emptySet())
    targetValue != null  -> failure("Schema '$name': sibling '$keyword' conflicts with target '$keyword'. Define it in only one of the two.")
    else                 -> {
      applyTo(siblingValue)
      success(setOf(keyword))
    }
  }
}

private fun <T: Comparable<T>> mergeTighter(keyword: String,
                                            targetValue: T?,
                                            siblingValue: T?,
                                            pick: (T, T) -> T,
                                            applyTo: (T) -> Unit): Result<Set<String>> {
  if (siblingValue == null) return success(emptySet())
  applyTo(targetValue?.let { pick(siblingValue, it) } ?: siblingValue)
  return success(setOf(keyword))
}

private fun mergeEnum(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema, name: String): Result<Set<String>> {
  val siblingEnum = sibling.enum.orEmpty()
  if (siblingEnum.isEmpty()) return success(emptySet())
  val targetEnum = target.enum.orEmpty()
  val extras = if (targetEnum.isEmpty()) emptyList() else siblingEnum.filterNot { it in targetEnum }
  if (extras.isNotEmpty()) return failure(
    "Schema '$name': sibling 'enum' contains values not in target 'enum' (extras: ${extras.joinWithQuotes()}). Implicit-allOf cannot loosen the target."
  )
  merged.enum = siblingEnum
  return success(setOf("enum"))
}

private fun mergeRequired(target: Schema<*>, sibling: Schema<*>, merged: JsonSchema): Result<Set<String>> {
  val siblingRequired = sibling.required.orEmpty()
  if (siblingRequired.isEmpty()) return success(emptySet())
  merged.required = (target.required.orEmpty() + siblingRequired).distinct()
  return success(setOf("required"))
}

private fun mergeProperties(target: Schema<*>,
                            sibling: Schema<*>,
                            merged: JsonSchema,
                            name: String): Result<Set<String>> {
  val siblingProps = sibling.properties.orEmpty()
  if (siblingProps.isEmpty()) return success(emptySet())
  val targetProps = target.properties.orEmpty()
  val overlap = siblingProps.keys intersect targetProps.keys
  if (overlap.isNotEmpty()) return failure(
    "Schema '$name': sibling 'properties' overlaps target on ${overlap.joinWithQuotes()}. Define each property in only one of the two."
  )
  merged.properties = targetProps + siblingProps
  return success(setOf("properties"))
}

private fun rejectUnhandledSiblings(siblingKeywords: List<String>,
                                    name: String,
                                    handled: Set<String>): Result<Unit> =
  siblingKeywords
    .filterNot { it in handled }
    .accumulate { unsupportedSibling(it, name) }

private fun unsupportedSibling(sibling: String, name: String): Result<Unit> =
  failure(Diagnostic($$"Schema '$$name': sibling '$$sibling' on '$ref' is not supported.", keyword = keywordOf(sibling), rule = UNSUPPORTED))

/** Sibling names are keywords, except a keyword group, reported under its first keyword, and the multi-type form of `type`. */
private fun keywordOf(sibling: String) = when (sibling) {
  "if/then/else" -> "if"
  "types"        -> "type"
  else           -> sibling
}

private fun cloneAsV31(target: Schema<*>): JsonSchema = JsonSchema().apply {
  specVersion = SpecVersion.V31
  `$ref` = target.`$ref`
  types = target.types
  type = target.type
  format = target.format
  properties = target.properties
  additionalProperties = target.additionalProperties
  required = target.required
  items = target.items
  pattern = target.pattern
  minLength = target.minLength
  maxLength = target.maxLength
  minimum = target.minimum
  maximum = target.maximum
  exclusiveMinimumValue = target.exclusiveMinimumValue
  exclusiveMaximumValue = target.exclusiveMaximumValue
  multipleOf = target.multipleOf
  minItems = target.minItems
  maxItems = target.maxItems
  uniqueItems = target.uniqueItems
  minProperties = target.minProperties
  maxProperties = target.maxProperties
  enum = target.enum
  const = target.const
  not = target.not
  allOf = target.allOf
  anyOf = target.anyOf
  oneOf = target.oneOf
  discriminator = target.discriminator
  contentEncoding = target.contentEncoding
  contentMediaType = target.contentMediaType
  contentSchema = target.contentSchema
  propertyNames = target.propertyNames
  readOnly = target.readOnly
  writeOnly = target.writeOnly
  nullable = target.nullable
  prefixItems = target.prefixItems
  contains = target.contains
  minContains = target.minContains
  maxContains = target.maxContains
  `if` = target.`if`
  then = target.then
  `else` = target.`else`
  unevaluatedProperties = target.unevaluatedProperties
  unevaluatedItems = target.unevaluatedItems
  patternProperties = target.patternProperties
  dependentRequired = target.dependentRequired
  dependentSchemas = target.dependentSchemas
}