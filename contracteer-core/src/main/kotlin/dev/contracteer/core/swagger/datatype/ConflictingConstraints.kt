package dev.contracteer.core.swagger.datatype

import dev.contracteer.core.DiagnosticRule.CONFLICTING_CONSTRAINTS
import dev.contracteer.core.swagger.LoadWarnings
import io.swagger.v3.oas.models.media.Schema

/**
 * Warns, for each constraint of [ignored] that is declared on [schema], that the [winner] takes precedence over it.
 * Each pair holds the keyword and the value it holds on the schema; `null` means it is not declared.
 */
internal fun LoadWarnings.warnConflictingConstraints(schema: Schema<*>, winner: String, vararg ignored: Pair<String, Any?>) =
  ignored
    .filter { (_, value) -> value != null }
    .forEach { (keyword, _) ->
      warn(CONFLICTING_CONSTRAINTS, "Schema '${schema.name}': '$keyword' ignored because '$winner' takes precedence.", keyword)
    }

/** Warns that the `pattern`, `minLength` and `maxLength` declared on [schema] are ignored because the schema's `format` ([format]) takes precedence. */
internal fun LoadWarnings.warnFormatPrecedence(schema: Schema<*>, format: String) =
  warnConflictingConstraints(
    schema,
    "format: $format",
    "pattern" to schema.pattern,
    "minLength" to schema.minLength,
    "maxLength" to schema.maxLength
  )
