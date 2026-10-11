package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.accumulate
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.Discriminator
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.effectiveConst
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.safeEnum

/** The positions of the composition branches read as accepting any value, which the composition drops. */
internal fun List<DataType<out Any>>.anyValueBranchIndices(): List<Int> =
  withIndex()
    .filter { (_, branch) -> branch is AnyDataType }
    .map { (index, _) -> index }

internal fun branchName(compositionKeyword: String, index: Int) = "$compositionKeyword #$index"

/** Dropping a branch read as accepting any value leaves the composition accepting only its other branches. */
internal fun LoadWarnings.warnIgnoredBranches(schema: Schema<*>, compositionKeyword: String, anyValueIndices: List<Int>) =
  anyValueIndices
    .map { branchName(compositionKeyword, it) }
    .forEach { warn(UNSUPPORTED, "Schema '${schema.name}': '$it' accepts any value and is ignored; '${schema.name}' accepts only its other branches.", compositionKeyword) }

/** Unlike an `allOf` branch, an `anyOf` or `oneOf` branch declaring only `required` is not applied. */
internal fun LoadWarnings.warnRequiredIgnoredOnBranches(branchSchemas: List<Schema<*>>, compositionKeyword: String, anyValueIndices: List<Int>) =
  anyValueIndices
    .filter { branchSchemas[it].`$ref` == null && branchSchemas[it].declaresRequired() }
    .forEach { warn(UNSUPPORTED, "Schema '${branchName(compositionKeyword, it)}': 'required' is ignored because the schema declares no type.", "required") }

internal fun Schema<*>.declaresRequired() = !required.isNullOrEmpty()

/** A composition with no branch left reads as accepting any value, which cannot carry an outer `enum`, `const`, nullability or `discriminator`. */
internal fun Schema<*>.readAsAnyValue(compositionKeyword: String, discriminator: Discriminator?): Result<DataType<out Any>> =
  listOfNotNull(
    "enum".takeIf { safeEnum().isNotEmpty() },
    "const".takeIf { effectiveConst() != null },
    nullabilityKeyword(),
    "discriminator".takeIf { discriminator != null })
    .accumulate { failure<Unit>(Diagnostic(cannotBeApplied(it, compositionKeyword), keyword = it, rule = UNSUPPORTED)) }
    .map { AnyDataType }

/** OAS 3.1 declares nullability in the `type` array, OAS 3.0 with `nullable`. */
private fun Schema<*>.nullabilityKeyword(): String? =
  when {
    !isNullable()              -> null
    "null" in types.orEmpty()  -> "type"
    else                       -> "nullable"
  }

private fun Schema<*>.cannotBeApplied(keyword: String, compositionKeyword: String) =
  "Schema '$name': '$keyword' cannot be applied because every '$compositionKeyword' branch is read as accepting any value."
