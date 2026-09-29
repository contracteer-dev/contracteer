package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.DateDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.Date

internal object DateDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<DateDataType> {
    if (schema.pattern != null) warnings.warn("Schema '${schema.name}': 'pattern' ignored because 'format: date' takes precedence.")
    if (schema.minLength != null || schema.maxLength != null) warnings.warn("Schema '${schema.name}': 'minLength'/'maxLength' ignored because 'format: date' takes precedence.")

    return schema
      .mapEnum {
        when (it) {
          is String -> success(it)
          is Date   -> success(it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(ISO_LOCAL_DATE))
          else      -> failure("Schema '${schema.name}': enum value '$it' is not a valid date string")
        }
      }.flatMap { enum ->
        DateDataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          enum = enum
        )
      }
  }
}