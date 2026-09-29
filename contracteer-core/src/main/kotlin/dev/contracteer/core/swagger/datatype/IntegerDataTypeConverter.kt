package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.IntegerDataType
import dev.contracteer.core.datatype.Range
import dev.contracteer.core.normalize
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.effectiveExclusiveMaximum
import dev.contracteer.core.swagger.effectiveExclusiveMinimum
import dev.contracteer.core.swagger.effectiveMaximum
import dev.contracteer.core.swagger.effectiveMinimum
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.mapEnum
import java.math.BigDecimal

internal object IntegerDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<IntegerDataType> =
    formatRange(schema.name, schema.format, warnings)
      .flatMap { formatRange ->
        val minimum = schema.effectiveMinimum()
        val maximum = schema.effectiveMaximum()
        when {
          minimum != null && formatRange.contains(minimum).isFailure() ->
            failure("minimum ($minimum) is out of range for format '${schema.format}'")

          maximum != null && formatRange.contains(maximum).isFailure() ->
            failure("maximum ($maximum) is out of range for format '${schema.format}'")

          else                                                          ->
            schema.mapEnum {
              when (val normalized = it.normalize()) {
                is BigDecimal -> success(normalized)
                else          -> failure("Schema '${schema.name}': enum value '$it' is not a valid integer")
              }
            }.flatMap { enum ->
              IntegerDataType.create(
                name = schema.name,
                isNullable = schema.isNullable(),
                minimum = minimum ?: formatRange.minimum,
                maximum = maximum ?: formatRange.maximum,
                exclusiveMinimum = schema.effectiveExclusiveMinimum(),
                exclusiveMaximum = schema.effectiveExclusiveMaximum(),
                enum = enum,
                multipleOf = schema.multipleOf
              )
            }
        }
      }

  private fun formatRange(schemaName: String, format: String?, warnings: LoadWarnings): Result<Range> = when (format) {
    null    -> Range.create()
    "int32" -> Range.create(Int.MIN_VALUE.toBigDecimal(), Int.MAX_VALUE.toBigDecimal())
    "int64" -> Range.create(Long.MIN_VALUE.toBigDecimal(), Long.MAX_VALUE.toBigDecimal())
    else    ->
      Range.create()
        .also { warnings.warn("Schema '$schemaName': unknown format '$format' for integer type is ignored.") }
  }
}
