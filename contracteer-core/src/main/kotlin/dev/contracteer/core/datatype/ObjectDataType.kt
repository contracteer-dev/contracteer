package dev.contracteer.core.datatype

import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.accumulate
import dev.contracteer.core.datatype.GenerationOutcome.Boundary
import dev.contracteer.core.datatype.GenerationOutcome.Reason
import dev.contracteer.core.datatype.GenerationOutcome.Value
import dev.contracteer.core.joinWithQuotes

/** OpenAPI `object` type, with named properties, required property constraints, and optional additional properties. */
class ObjectDataType private constructor(name: String,
                                         val properties: Map<String, DataType<out Any>>,
                                         val requiredProperties: Set<String> = emptySet(),
                                         val readOnlyProperties: Set<String> = emptySet(),
                                         val writeOnlyProperties: Set<String> = emptySet(),
                                         val allowAdditionalProperties: Boolean,
                                         val additionalPropertiesDataType: DataType<out Any>?,
                                         isNullable: Boolean,
                                         val minProperties: Int?,
                                         val maxProperties: Int?,
                                         val propertyNamesDataType: StringDataType? = null,
                                         allowedValues: AllowedValues? = null):
    ResolvedDataType<Map<String, Any?>>(name,
                                        "object",
                                        isNullable,
                                        MAP_CLASS,
                                        allowedValues) {

  override fun doValidate(value: Map<String, Any?>): Result<Map<String, Any?>> =
    validatePropertyCount(value) andThen
        { validatePropertyNames(value) } andThen
        { validateProperties(value) } andThen
        { validateAdditionalProperties(value) }

  private fun validatePropertyNames(value: Map<String, Any?>): Result<Map<String, Any?>> =
    if (propertyNamesDataType == null) success(value)
    else value.keys.accumulate { key -> propertyNamesDataType.validate(key).forProperty(key) }.map { value }

  private fun validatePropertyCount(value: Map<String, Any?>): Result<Map<String, Any?>> = when {
    minProperties != null && value.size < minProperties -> failure("Object has ${value.size} properties but minProperties is $minProperties")
    maxProperties != null && value.size > maxProperties -> failure("Object has ${value.size} properties but maxProperties is $maxProperties")
    else                                                -> success(value)
  }

  override fun doRandomValue(ctx: GenerationContext): GenerationOutcome<Map<String, Any?>> {
    val entries = mutableMapOf<String, Any?>()
    for ((key, type) in selectProperties()) {
      when (val result = type.randomValue(ctx).forProperty(key)) {
        is Value                                  -> entries[key] = result.value
        is Boundary if type.isNullable            -> entries[key] = null
        is Boundary if key !in requiredProperties -> Unit
        is Boundary                               -> return result
      }
    }
    return synthesizeAdditional(entries, ctx)
  }

  private fun synthesizeAdditional(entries: MutableMap<String, Any?>, ctx: GenerationContext): GenerationOutcome<Map<String, Any?>> {
    val additional = additionalPropertiesDataType
    val needed = (minProperties ?: 0) - entries.size
    if (additional == null || needed <= 0) return Value(entries)

    repeat(needed) {
      val syntheticKey = generateUniqueKey(entries.keys, ctx) ?: return Boundary(Reason.NAMES)
      when (val result = additional.randomValue(ctx).forProperty(syntheticKey)) {
        is Value                             -> entries[syntheticKey] = result.value
        is Boundary if additional.isNullable -> entries[syntheticKey] = null
        is Boundary                          -> return result
      }
    }
    return Value(entries)
  }

  private fun selectProperties(): Map<String, DataType<out Any>> {
    if (maxProperties == null || maxProperties >= properties.size) return properties
    val required = properties.filterKeys { it in requiredProperties }
    val optional = properties
      .filterKeys { it !in requiredProperties }
      .entries
      .shuffled()
      .take(maxProperties - required.size)
      .associate { it.key to it.value }
    return required + optional
  }

  private fun generateUniqueKey(existingKeys: Set<String>, ctx: GenerationContext): String? =
    if (propertyNamesDataType == null) defaultUniqueKey(existingKeys)
    else generateUniqueKeyFromDataType(propertyNamesDataType, existingKeys, ctx)

  private fun defaultUniqueKey(existingKeys: Set<String>): String =
    generateSequence(existingKeys.size + 1) { it + 1 }
      .map { "contracteer_key_$it" }
      .first { it !in existingKeys }

  private fun generateUniqueKeyFromDataType(dataType: StringDataType,
                                            existingKeys: Set<String>,
                                            ctx: GenerationContext): String? =
    (1..MAX_KEY_GENERATION_ATTEMPTS)
      .asSequence()
      .mapNotNull { (dataType.randomValue(ctx) as? Value)?.value }
      .firstOrNull { it !in existingKeys }

  override fun asRequestType(): DataType<Map<String, Any?>> =
    withoutProperties(readOnlyProperties) { it.asRequestType() }

  override fun asResponseType(): DataType<Map<String, Any?>> =
    withoutProperties(writeOnlyProperties) { it.asResponseType() }

  private fun withoutProperties(excluded: Set<String>,
                                view: (DataType<out Any>) -> DataType<out Any>): DataType<Map<String, Any?>> {
    val transformedProperties = properties.minus(excluded).mapValues { (_, v) -> view(v) }
    return if (excluded.isEmpty() && transformedProperties.all { (k, v) -> v === properties[k] }) this
    else ObjectDataType(name = name,
                        properties = transformedProperties,
                        requiredProperties = requiredProperties - excluded,
                        allowAdditionalProperties = allowAdditionalProperties,
                        additionalPropertiesDataType = additionalPropertiesDataType,
                        isNullable = isNullable,
                        minProperties = minProperties,
                        maxProperties = maxProperties,
                        propertyNamesDataType = propertyNamesDataType,
                        allowedValues = allowedValues)
  }

  internal fun withRequiredProperties(names: Set<String>): Result<ObjectDataType> =
    create(name = name,
           properties = properties,
           requiredProperties = requiredProperties + names,
           readOnlyProperties = readOnlyProperties,
           writeOnlyProperties = writeOnlyProperties,
           allowAdditionalProperties = allowAdditionalProperties,
           additionalPropertiesDataType = additionalPropertiesDataType,
           isNullable = isNullable,
           enum = allowedValues?.asSequence()?.toList().orEmpty(),
           minProperties = minProperties,
           maxProperties = maxProperties,
           propertyNamesDataType = propertyNamesDataType)

  private fun validateProperties(value: Map<String, Any?>): Result<Map<String, Any?>> =
    properties.accumulate { (property, dataType) ->
      when {
        !value.containsKey(property) && !requiredProperties.contains(property) -> success(value)
        !value.containsKey(property)                                           -> failure(property, "is required")
        else                                                                   ->
          dataType.validate(value[property]).forProperty(property)
      }
    }

  private fun validateAdditionalProperties(value: Map<String, Any?>): Result<Map<String, Any?>> {
    val extraProperties = value.keys - properties.keys
    return when {
      extraProperties.isNotEmpty() && !allowAdditionalProperties -> failure("Additional properties are not allowed. Unexpected properties: " + extraProperties.joinWithQuotes())
      additionalPropertiesDataType == null                       -> success(value)
      else                                                       ->
        extraProperties.accumulate { additionalPropertiesDataType.validate(value[it]).forProperty(it) }.map { value }
    }
  }

  companion object {
    private const val MAX_KEY_GENERATION_ATTEMPTS = 50

    // Generic erasure forces an unchecked cast to express the value-type parameter.
    @Suppress("UNCHECKED_CAST")
    private val MAP_CLASS: Class<Map<String, Any?>> = Map::class.java as Class<Map<String, Any?>>

    @JvmStatic
    @JvmOverloads
    fun create(
      name: String,
      properties: Map<String, DataType<out Any>>,
      requiredProperties: Set<String> = emptySet(),
      readOnlyProperties: Set<String> = emptySet(),
      writeOnlyProperties: Set<String> = emptySet(),
      allowAdditionalProperties: Boolean,
      additionalPropertiesDataType: DataType<out Any>? = null,
      isNullable: Boolean,
      enum: List<Any?> = emptyList(),
      minProperties: Int? = null,
      maxProperties: Int? = null,
      propertyNamesDataType: StringDataType? = null
    ): Result<ObjectDataType> {
      val undefinedProperties = requiredProperties - properties.keys

      if (undefinedProperties.isNotEmpty())
        return failure("The following required properties are not defined in the schema: " + undefinedProperties.joinWithQuotes())
      if (minProperties != null && minProperties < 0)
        return failure("minProperties must be non-negative")
      if (maxProperties != null && maxProperties < 0)
        return failure("maxProperties must be non-negative")
      if (minProperties != null && maxProperties != null && minProperties > maxProperties)
        return failure("minProperties ($minProperties) must be less than or equal to maxProperties ($maxProperties)")
      if (maxProperties != null && maxProperties < requiredProperties.size)
        return failure("maxProperties ($maxProperties) is less than the number of required properties (${requiredProperties.size})")
      if (minProperties != null && minProperties > properties.size && additionalPropertiesDataType == null)
        return failure("minProperties ($minProperties) exceeds the number of declared properties (${properties.size}) and no additionalProperties schema is available")
      if ((minProperties != null || maxProperties != null) && readOnlyProperties.isNotEmpty())
        return failure("minProperties/maxProperties cannot be combined with readOnly properties")
      if ((minProperties != null || maxProperties != null) && writeOnlyProperties.isNotEmpty())
        return failure("minProperties/maxProperties cannot be combined with writeOnly properties")

      propertyNamesDataType?.let { dataType ->
        val invalidNames = properties.keys.filter { dataType.validate(it).isFailure() }
        if (invalidNames.isNotEmpty())
          return failure("Declared property names violate propertyNames schema: " + invalidNames.joinWithQuotes())
      }

      val default = ObjectDataType(
        name = name,
        properties = properties,
        requiredProperties = requiredProperties,
        readOnlyProperties = readOnlyProperties,
        writeOnlyProperties = writeOnlyProperties,
        allowAdditionalProperties = allowAdditionalProperties,
        additionalPropertiesDataType = additionalPropertiesDataType,
        isNullable = isNullable,
        minProperties = minProperties,
        maxProperties = maxProperties,
        propertyNamesDataType = propertyNamesDataType
      )
      return if (enum.isEmpty())
        success(default)
      else
        AllowedValues
          .create(enum, default)
          .map {
            ObjectDataType(name = name,
                           properties = properties,
                           requiredProperties = requiredProperties,
                           readOnlyProperties = readOnlyProperties,
                           writeOnlyProperties = writeOnlyProperties,
                           allowAdditionalProperties = allowAdditionalProperties,
                           additionalPropertiesDataType = additionalPropertiesDataType,
                           isNullable = isNullable,
                           minProperties = minProperties,
                           maxProperties = maxProperties,
                           propertyNamesDataType = propertyNamesDataType,
                           allowedValues = it)
          }
    }
  }
}
