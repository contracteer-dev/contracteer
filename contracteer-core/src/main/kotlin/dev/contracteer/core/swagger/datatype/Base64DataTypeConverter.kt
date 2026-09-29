package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.Base64DataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum
import java.util.Base64

internal object Base64DataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<Base64DataType> {
    if (schema.pattern != null) warnings.warn("Schema '${schema.name}': 'pattern' ignored because 'format: byte' takes precedence.")

    return schema
      .mapEnum {
        when (it) {
          is String    -> success(it)
          is ByteArray -> success(Base64.getEncoder().encodeToString(it))
          else         -> failure("Schema '${schema.name}': enum value '$it' is not a valid base64 string")
        }
      }.flatMap { enum ->
        Base64DataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          minLength = schema.minLength,
          maxLength = schema.maxLength,
          enum = enum
        )
      }
  }
}