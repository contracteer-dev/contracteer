package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.DiagnosticRule.UNKNOWN_FORMAT
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.datatype.NumberDataType
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

internal object NumberDataTypeConverter {
  fun convert(schema: Schema<*>, warnings: LoadWarnings): Result<NumberDataType> =
    formatRange(schema.name, schema.format, warnings).flatMap { formatRange ->
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
              else          -> failure("Schema '${schema.name}': enum value '$it' is not a valid number")
            }
          }.flatMap { enum ->
            NumberDataType.create(
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

  private fun formatRange(schemaName: String, format: String?, warnings: LoadWarnings): Result<Range> =
    when (format) {
      null     -> Range.create()
      "float"  -> Range.create(Float.MAX_VALUE.toBigDecimal().negate(), Float.MAX_VALUE.toBigDecimal())
      "double" -> Range.create(Double.MAX_VALUE.toBigDecimal().negate(), Double.MAX_VALUE.toBigDecimal())
      else     ->
        Range.create()
          .also { warnings.warn(UNKNOWN_FORMAT, "Schema '$schemaName': unknown format '$format' for number type is ignored.", "format") }
    }
}
