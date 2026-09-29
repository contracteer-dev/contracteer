package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.UriReferenceDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum

internal object UriReferenceDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<UriReferenceDataType> {
    if (schema.minLength != null || schema.maxLength != null)
      warnings.warn("Schema '${schema.name}': 'minLength'/'maxLength' ignored because 'format: uri-reference' takes precedence.")
    if (schema.pattern != null)
      warnings.warn("Schema '${schema.name}': 'pattern' ignored because 'format: uri-reference' takes precedence.")

    return schema
      .mapEnum {
        when (it) {
          is String -> success(it)
          else      -> failure("Schema '${schema.name}': enum value '$it' is not a valid uri-reference string")
        }
      }.flatMap { enum ->
        UriReferenceDataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          enum = enum,
        )
      }
  }
}