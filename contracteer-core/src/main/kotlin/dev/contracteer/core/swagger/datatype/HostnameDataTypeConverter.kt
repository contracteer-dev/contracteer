package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.HostnameDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum

internal object HostnameDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<HostnameDataType> {
    if (schema.minLength != null || schema.maxLength != null)
      warnings.warn("Schema '${schema.name}': 'minLength'/'maxLength' ignored because 'format: hostname' takes precedence.")
    if (schema.pattern != null)
      warnings.warn("Schema '${schema.name}': 'pattern' ignored because 'format: hostname' takes precedence.")

    return schema
      .mapEnum {
        when (it) {
          is String -> success(it)
          else      -> failure("Schema '${schema.name}': enum value '$it' is not a valid hostname string")
        }
      }.flatMap { enum ->
        HostnameDataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          enum = enum,
        )
      }
  }
}