package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.StringDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum

internal object StringDataTypeConverter {
  fun convert(schema: Schema<*>, openApiType: String, warnings: LoadWarnings): Result<StringDataType> =
    schema
      .mapEnum {
        when (it) {
          is String -> success(it)
          else      -> failure("Schema '${schema.name}': enum value '$it' is not a valid string")
        }
      }.flatMap { enum ->
        StringDataType.create(
          name = schema.name,
          openApiType,
          isNullable = schema.isNullable(),
          minLength = schema.minLength ?: 0,
          maxLength = schema.maxLength,
          pattern = schema.pattern,
          enum = enum
        )
      }.also { if (it.isSuccess()) warnIfLengthIgnored(schema, warnings) }

  private fun warnIfLengthIgnored(schema: Schema<*>, warnings: LoadWarnings) {
    if (schema.pattern != null)
      warnings.warnConflictingConstraints(schema, "pattern", "minLength" to schema.minLength?.takeIf { it > 0 }, "maxLength" to schema.maxLength)
  }
}
