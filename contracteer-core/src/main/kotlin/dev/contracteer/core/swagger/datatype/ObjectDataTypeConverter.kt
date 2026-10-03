package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.accumulate
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.ObjectDataType
import dev.contracteer.core.datatype.StringDataType
import dev.contracteer.core.result
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.effectiveEnum
import dev.contracteer.core.swagger.effectivePropertyNames
import dev.contracteer.core.swagger.effectiveType
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.safeProperties

internal object ObjectDataTypeConverter {

  fun convert(
    schema: Schema<*>,
    convert: (Schema<*>, String) -> Result<DataType<out Any>>,
    warnings: LoadWarnings,
    localRequiredOnly: Boolean = false): Result<ObjectDataType> {

    val booleanShorthand = additionalPropertiesAsBoolean(schema.additionalProperties)
    val (allowAdditionalProperties, additionalPropertiesSchema) = when {
      booleanShorthand != null                 -> booleanShorthand to null
      schema.additionalProperties is Schema<*> -> true to (schema.additionalProperties as Schema<*>)
      else                                     -> true to null
    }
    val additionalPropertiesDataTypeResult =
      if (additionalPropertiesSchema == null) success(null)
      else convert(additionalPropertiesSchema, "additionalProperties")

    return result {
      val properties = schema
        .safeProperties()
        .accumulate { (name, subSchema) -> convert(subSchema, name).forProperty(name) }
        .bind()
      val additionalPropertiesDataType = additionalPropertiesDataTypeResult.bind()
      val enum = schema.effectiveEnum().bind()
      val readOnlyProps = schema.safeProperties().filter { (_, propSchema) -> propSchema.readOnly == true }.keys
      val writeOnlyProps = schema.safeProperties().filter { (_, propSchema) -> propSchema.writeOnly == true }.keys
      val propertyNamesDataType = convertPropertyNames(schema, convert, warnings).bind()

      ObjectDataType.create(
        name = schema.name,
        properties = properties,
        requiredProperties = schema.requiredProperties(localRequiredOnly),
        readOnlyProperties = readOnlyProps,
        writeOnlyProperties = writeOnlyProps,
        allowAdditionalProperties = allowAdditionalProperties,
        additionalPropertiesDataType = additionalPropertiesDataType,
        isNullable = schema.isNullable(),
        enum = enum,
        minProperties = schema.minProperties,
        maxProperties = schema.maxProperties,
        propertyNamesDataType = propertyNamesDataType
      ).bind()
    }
  }

  private fun convertPropertyNames(
    schema: Schema<*>,
    convert: (Schema<*>, String) -> Result<DataType<out Any>>,
    warnings: LoadWarnings
  ): Result<StringDataType?> {
    val propertyNamesSchema = schema.effectivePropertyNames() ?: return success(null)
    propertyNamesSchema.name = "${schema.name}.propertyNames"
    // JSON Schema 2020-12: property names are always strings; type-less schemas default to string.
    val asDataType = if (propertyNamesSchema.`$ref` != null || propertyNamesSchema.effectiveType() != null)
      convert(propertyNamesSchema, propertyNamesSchema.name)
    else
      StringDataTypeConverter.convert(propertyNamesSchema, "string", warnings)
    return asDataType.flatMap { dataType ->
      when (dataType) {
        is StringDataType -> success(dataType)
        else              -> failure(
          "Schema '${schema.name}': propertyNames must be a string schema, but resolved to '${dataType.openApiType}'"
        )
      }
    }
  }

  fun convertSiblingObject(schema: Schema<*>,
                           convert: (Schema<*>, String) -> Result<DataType<out Any>>,
                           warnings: LoadWarnings,
                           localRequiredOnly: Boolean = false): Result<DataType<out Any>>? {
    val requiredTriggersConversion = schema.required != null && !localRequiredOnly
    return if (schema.properties != null || requiredTriggersConversion || schema.additionalProperties != null)
      convert(schema, convert, warnings, localRequiredOnly)
    else
      null
  }

  private fun Schema<*>.requiredProperties(localRequiredOnly: Boolean): Set<String> =
    required.orEmpty()
      .filter { !localRequiredOnly || it in safeProperties() }
      .toSet()

  private fun additionalPropertiesAsBoolean(value: Any?): Boolean? =
    when (value) {
      is Boolean   -> value
      is Schema<*> -> value.booleanSchemaValue
      else         -> null
    }
}
