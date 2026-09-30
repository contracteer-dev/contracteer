package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.DateTimeDataType
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

internal object DateTimeDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<DateTimeDataType> {
    warnings.warnFormatPrecedence(schema, "date-time")

    return schema
      .mapEnum {
        when (it) {
          is String         -> success(it)
          is OffsetDateTime -> success(it.format(ISO_OFFSET_DATE_TIME))
          else              -> failure("Schema '${schema.name}': enum value '$it' is not a valid date-time string")
        }
      }.flatMap { enum ->
        DateTimeDataType.create(
          name = schema.name,
          isNullable = schema.isNullable(),
          enum = enum
        )
      }
  }
}