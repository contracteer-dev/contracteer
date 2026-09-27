package dev.contracteer.core.datatype

import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.GenerationOutcome.Boundary
import dev.contracteer.core.datatype.GenerationOutcome.Value
import dev.contracteer.core.joinWithQuotes
import java.lang.System.lineSeparator

/** OpenAPI `allOf` composition. The value must match all sub-schemas simultaneously. */
class AllOfDataType private constructor(name: String,
                                        subTypes: List<DataType<out Any>>,
                                        outerIsNullable: Boolean,
                                        override val discriminator: Discriminator?,
                                        allowedValues: AllowedValues? = null):
    CompositeDataType<Any>(name,
                           "allOf",
                           outerIsNullable,
                           subTypes,
                           Any::class.java,
                           allowedValues) {

  override fun computeNullable(guard: CycleGuard<Boolean>): Boolean =
    outerIsNullable || subTypes.all { isNullableBranch(it, guard) }

  override fun asRequestType(): DataType<Any> =
    subTypes
      .map { it.asRequestType() }
      .let { transformed ->
        if (transformed.zip(subTypes).all { (a, b) -> a === b }) this
        else AllOfDataType(name, transformed, outerIsNullable, discriminator, allowedValues)
      }

  override fun asResponseType(): DataType<Any> =
    subTypes
      .map { it.asResponseType() }
      .let { transformed ->
        if (transformed.zip(subTypes).all { (a, b) -> a === b }) this
        else AllOfDataType(name, transformed, outerIsNullable, discriminator, allowedValues)
      }

  internal fun withSubTypes(subTypes: List<DataType<out Any>>): Result<AllOfDataType> =
    create(name, subTypes, outerIsNullable, discriminator, allowedValues?.asSequence()?.toList().orEmpty())

  override fun doValidate(value: Any): Result<Any> {
    if (discriminator != null) {
      val discriminatorResult = validateDiscriminator(value)
      if (discriminatorResult.isFailure()) return discriminatorResult
    }
    val dataTypeErrors = subTypes
      .associateWith { it.validate(withoutSiblingProperties(value, it)) }
      .filterValues { it.isFailure() }

    return if (dataTypeErrors.isNotEmpty())
      buildNoMatchError(dataTypeErrors)
    else
      success(value)
  }

  @Suppress("UNCHECKED_CAST")
  private fun withoutSiblingProperties(value: Any, subType: DataType<out Any>): Any {
    if (value !is Map<*, *>) return value
    val siblingProperties = subTypes
                              .filter { it !== subType }
                              .flatMap { it.propertyNames() }
                              .toSet() - subType.propertyNames()

    return when {
      siblingProperties.isEmpty() -> value
      else                        -> (value as Map<String, Any?>).filterKeys { it !in siblingProperties }
    }
  }

  private fun DataType<out Any>.propertyNames(): Set<String> =
    when (this) {
      is ObjectDataType       -> properties.keys
      is CompositeDataType<*> -> subTypes.flatMapTo(mutableSetOf()) { it.propertyNames() }
      is ProxyDataType        -> emptySet()
      else                    -> emptySet()
    }

  override fun doRandomValue(ctx: GenerationContext): GenerationOutcome<Any> =
    if (subTypes.size == 1) generateFromSingleSubType(ctx)
    else generateFromMergedSubTypes(ctx)

  private fun generateFromSingleSubType(ctx: GenerationContext): GenerationOutcome<Any> {
    val singleSubType = subTypes.single()
    return when (val result = singleSubType.randomValue(ctx).forProperty(singleSubType.name)) {
      is Boundary -> result
      is Value    -> Value(injectDiscriminator(result.value, name))
    }
  }

  private fun generateFromMergedSubTypes(ctx: GenerationContext): GenerationOutcome<Any> {
    val mergedProperties = mutableMapOf<String, Any?>()
    for (subType in subTypes) {
      when (val subTypeResult = subType.randomValue(ctx).forProperty(subType.name)) {
        is Boundary -> return subTypeResult
        is Value    -> mergeMapInto(subTypeResult.value, mergedProperties)
      }
    }
    return Value(injectDiscriminator(mergedProperties, name))
  }

  // Create-time validation rejects multi-subtype allOf containing non-structured subtypes, so
  // every Value produced here must be a Map. Failing that invariant at runtime is a programmer error.
  @Suppress("UNCHECKED_CAST")
  private fun mergeMapInto(value: Any?, target: MutableMap<String, Any?>) {
    require(value is Map<*, *>) {
      "allOf '$name' produced a non-map value from a structured sub-schema; this violates the create-time validation invariant."
    }
    target.putAll(value as Map<String, Any?>)
  }

  private fun validateDiscriminator(value: Any): Result<Any> {
    val discriminatorValue = (value as? Map<*, *>)?.get(discriminator!!.propertyName)
    return when {
      discriminatorValue !is String                                -> success(value)
      discriminator.getDataTypeNameFor(discriminatorValue) != name -> failure(
        "Invalid value for discriminator property '${discriminator.propertyName}'. " +
        "Expected '${discriminator.getMappingName(name)}', but found '$discriminatorValue'.")
      else                                                         -> success(value)
    }
  }

  private fun buildNoMatchError(dataTypeErrors: Map<DataType<out Any>, Result<Any?>>): Result<Any> {
    val schemaNames = dataTypeErrors.keys.map { it.name }.joinWithQuotes()
    val detailedErrors = dataTypeErrors.map { (dataType, result) ->
      "Schema '${dataType.name}':" + result.errors().joinToString(
        prefix = "${lineSeparator()}\t\t\t- ",
        separator = "${lineSeparator()}\t\t\t- "
      )
    }.joinToString(
      prefix = "\t\t- ",
      separator = "${lineSeparator()}\t\t- "
    )
    return failure(
      "No matching schema found. The provided value did not match any of the candidate schemas ($schemaNames):${lineSeparator()}$detailedErrors"
    )
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(name: String,
               subTypes: List<DataType<out Any>>,
               outerIsNullable: Boolean = false,
               discriminator: Discriminator? = null,
               enum: List<Any?> = emptyList()) =
      subTypes.validate(discriminator)
        .flatMap {
          val defaultDataType = AllOfDataType(name, subTypes, outerIsNullable, discriminator)
          if (enum.isEmpty()) {
            success(defaultDataType)
          } else {
            AllowedValues
              .create(enum, defaultDataType)
              .map { AllOfDataType(name, subTypes, outerIsNullable, discriminator, it) }
          }
        }

    private fun List<DataType<out Any>>.validate(discriminator: Discriminator?): Result<Discriminator?> {
      if (size > 1 && any { !it.canBeAllOfSubtype() }) return failure("Only structured schemas (object, allOf, anyOf, oneOf) are supported for multi-element 'allOf'.")
      if (discriminator == null) return success(null)

      val results = map { discriminator.validate(it).forProperty(it.name) }
      val successes = results.count { it.isSuccess() }
      return when {
        successes == 1 -> success(discriminator)
        successes > 1  -> failure("Ambiguous discriminator. The discriminator property '${discriminator.propertyName}' is present in multiple 'allOf' sub-schemas.")
        else           -> results.combineResults().retypeError()
      }
    }

    private fun DataType<out Any>.canBeAllOfSubtype(): Boolean = when (this) {
      is ObjectDataType       -> true
      is CompositeDataType<*> -> subTypes.all { it.canBeAllOfSubtype() }
      is ProxyDataType        -> !isResolved || delegate.canBeAllOfSubtype()
      else                    -> false
    }
  }
}
