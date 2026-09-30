package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.BinaryDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum

internal object BinaryDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<BinaryDataType> {
    warnings.warnConflictingConstraints(schema, "format: binary", "pattern" to schema.pattern)

    return schema
      .mapEnum {
        when (it) {
          is String    -> success(it)
          is ByteArray -> success(String(it))
          else         -> failure("Schema '${schema.name}': enum value '$it' is not a valid binary string")
        }
      }.flatMap { enum ->
        BinaryDataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          minLength = schema.minLength,
          maxLength = schema.maxLength,
          enum = enum
        )
      }
  }
}