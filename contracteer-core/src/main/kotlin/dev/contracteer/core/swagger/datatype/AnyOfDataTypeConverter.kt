package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.datatype.AnyOfDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.Discriminator
import dev.contracteer.core.result
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.effectiveEnum
import dev.contracteer.core.swagger.isNullable

internal object AnyOfDataTypeConverter {

  fun convert(
    schema: Schema<*>,
    convertBranch: (Schema<*>, String) -> Result<DataType<out Any>>,
    discriminator: (Schema<*>) -> Discriminator?,
    warnings: LoadWarnings
  ): Result<DataType<out Any>> =
    if (schema.anyOf == null) failure("'anyOf' must be defined")
    else schema.anyOf
      .mapIndexed { index, sub -> convertBranch(sub, branchName("anyOf", index)) }
      .combineResults()
      .flatMap { branches -> create(schema, branches, discriminator(schema), warnings) }

  private fun create(schema: Schema<*>,
                     branches: List<DataType<out Any>>,
                     discriminator: Discriminator?,
                     warnings: LoadWarnings): Result<DataType<out Any>> {
    val anyValueIndices = branches.anyValueBranchIndices()
    val subTypes = branches.filter { it !is AnyDataType }
    warnings.warnRequiredIgnoredOnBranches(schema.anyOf, "anyOf", anyValueIndices)
    return when {
      subTypes.isEmpty() -> schema.readAsAnyValue("anyOf", discriminator)
      else               -> createAnyOf(schema, subTypes, discriminator)
                              .also { warnings.warnIgnoredBranches(schema, "anyOf", anyValueIndices) }
    }
  }

  private fun createAnyOf(schema: Schema<*>, subTypes: List<DataType<out Any>>, discriminator: Discriminator?) =
    result {
      schema.rejectNullBranchAgainstOuterType(subTypes, "anyOf").bind()
      val enum = schema.effectiveEnum().bind()
      AnyOfDataType.create(
        name = schema.name,
        subTypes = subTypes,
        discriminator = discriminator,
        outerIsNullable = schema.isNullable(),
        enum = enum
      ).bind()
    }
}
