package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.UuidDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum
import java.util.UUID

internal object UuidDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<UuidDataType> {
    warnings.warnFormatPrecedence(schema, "uuid")
    return schema
      .mapEnum {
        when (it) {
          is String -> success(it)
          is UUID   -> success(it.toString())
          else      -> failure("Schema '${schema.name}': enum value '$it' is not a valid UUID string")
        }
      }.flatMap { enum ->
        UuidDataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          enum = enum
        )
      }
  }
}