package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.EmailDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum

internal object EmailDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<EmailDataType> {
    if (schema.pattern != null) warnings.warn("Schema '${schema.name}': 'pattern' ignored because 'format: email' takes precedence.")

    return schema
      .mapEnum {
        when (it) {
          is String -> success(it)
          else      -> failure("Schema '${schema.name}': enum value '$it' is not a valid email string")
        }
      }.flatMap { enum ->
        EmailDataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          minLength = schema.minLength,
          maxLength = schema.maxLength,
          enum = enum,
        )
      }
  }
}