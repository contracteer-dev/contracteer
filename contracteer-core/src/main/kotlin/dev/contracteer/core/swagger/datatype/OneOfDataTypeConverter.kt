package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.Discriminator
import dev.contracteer.core.datatype.OneOfDataType
import dev.contracteer.core.joinWithQuotes
import dev.contracteer.core.result
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.effectiveEnum
import dev.contracteer.core.swagger.isNullable

internal object OneOfDataTypeConverter {
  fun convert(
    schema: Schema<*>,
    convertBranch: (Schema<*>, String) -> Result<DataType<out Any>>,
    discriminator: (Schema<*>) -> Discriminator?,
    warnings: LoadWarnings
  ): Result<DataType<out Any>> =
    if (schema.oneOf == null) failure("'oneOf' must be defined")
    else schema.oneOf
      .mapIndexed { index, sub -> convertBranch(sub, branchName("oneOf", index)) }
      .combineResults()
      .flatMap { branches -> create(schema, branches, discriminator(schema), warnings) }

  private fun create(schema: Schema<*>,
                     branches: List<DataType<out Any>>,
                     discriminator: Discriminator?,
                     warnings: LoadWarnings): Result<DataType<out Any>> {
    val anyValueIndices = branches.anyValueBranchIndices()
    val unconstrainedIndices = anyValueIndices.unconstrained(schema)
    val subTypes = branches.filter { it !is AnyDataType }
    warnings.warnRequiredIgnoredOnBranches(schema.oneOf, "oneOf", anyValueIndices)
    return when {
      unconstrainedIndices.size > 1                       -> noValueMatchesExactlyOneBranch(schema, unconstrainedIndices)
      unconstrainedIndices.size == 1 && branches.size > 1 -> onlyValuesMatchingNoOtherBranch(schema, unconstrainedIndices.single())
      subTypes.isEmpty()                                  -> schema.readAsAnyValue("oneOf", discriminator)
      else                                                -> createOneOf(schema, subTypes, discriminator)
                                                               .also { warnings.warnIgnoredBranches(schema, "oneOf", anyValueIndices) }
    }
  }

  /** A branch declaring `required` constrains the value under the specification, even though Contracteer does not apply it. */
  private fun List<Int>.unconstrained(schema: Schema<*>): List<Int> =
    filterNot { schema.oneOf[it].declaresRequired() }

  private fun createOneOf(schema: Schema<*>, subTypes: List<DataType<out Any>>, discriminator: Discriminator?) =
    result {
      schema.rejectNullBranchAgainstOuterType(subTypes, "oneOf").bind()
      val enum = schema.effectiveEnum().bind()
      OneOfDataType.create(
        name = schema.name,
        subTypes = subTypes,
        discriminator = discriminator,
        outerIsNullable = schema.isNullable(),
        enum = enum
      ).bind()
    }

  /** Every value matches each unconstrained branch read as accepting any value, so with two of them none matches exactly one branch. */
  private fun noValueMatchesExactlyOneBranch(schema: Schema<*>, anyValueIndices: List<Int>): Result<DataType<out Any>> =
    failure(Diagnostic(
      "Schema '${schema.name}': ${anyValueIndices.map { branchName("oneOf", it) }.joinWithQuotes()} are read as accepting any value, so no value can match exactly one branch.",
      keyword = "oneOf",
      rule = UNSUPPORTED))

  /** Every value matches the unconstrained branch read as accepting any value, so the `oneOf` accepts only the values that match no other branch. */
  private fun onlyValuesMatchingNoOtherBranch(schema: Schema<*>, anyValueIndex: Int): Result<DataType<out Any>> =
    failure(Diagnostic(
      "Schema '${schema.name}': '${branchName("oneOf", anyValueIndex)}' is read as accepting any value, so '${schema.name}' accepts only the values that match no other branch, which Contracteer cannot express.",
      keyword = "oneOf",
      rule = UNSUPPORTED))
}
