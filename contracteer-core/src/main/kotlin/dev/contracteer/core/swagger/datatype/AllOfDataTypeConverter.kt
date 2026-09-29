package dev.contracteer.core.swagger.datatype

import io.swagger.v3.oas.models.media.Schema
import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.AllOfDataType
import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.datatype.CompositeDataType
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.datatype.Discriminator
import dev.contracteer.core.datatype.ObjectDataType
import dev.contracteer.core.datatype.ProxyDataType
import dev.contracteer.core.joinWithQuotes
import dev.contracteer.core.result
import dev.contracteer.core.swagger.LoadWarnings
import dev.contracteer.core.swagger.effectiveEnum
import dev.contracteer.core.swagger.hasComposition
import dev.contracteer.core.swagger.isAnyType
import dev.contracteer.core.swagger.isNullable
import dev.contracteer.core.swagger.safeProperties

/**
 * A `required` name that an inline branch (or the object beside `allOf`) does not declare itself is
 * moved into the sibling branches that declare it: under the cross-branch visibility model, only the
 * declaring branch sees that property, so only it can enforce the requirement.
 */
internal object AllOfDataTypeConverter {

  fun convert(schema: Schema<*>,
              convert: (Schema<*>, String) -> Result<DataType<out Any>>,
              convertInlineBranch: (Schema<*>, String) -> Result<DataType<out Any>>,
              discriminator: (Schema<*>) -> Discriminator?,
              warnings: LoadWarnings): Result<AllOfDataType> {
    if (schema.allOf == null) return failure("'allOf' must be defined.")

    val subTypeResults = schema.allOf
      .withIndex()
      .filterNot { (_, subSchema) -> subSchema.isRequiredOnly() }
      .map { (index, subSchema) ->
        if (subSchema.isInline()) convertInlineBranch(subSchema, "allOf #$index")
        else convert(subSchema, "allOf #$index")
      }

    val siblingResult = ObjectDataTypeConverter.convertSiblingObject(schema, convert, warnings, localRequiredOnly = true)

    return result {
      val subDataTypes = (subTypeResults + listOfNotNull(siblingResult))
        .combineResults()
        .bind()
        .filter { it !is AnyDataType }
      schema.rejectAllOfNullBranch(subDataTypes).bind()
      val requiredSubDataTypes = subDataTypes.withRequiredProperties(schema.foreignRequiredProperties()).bind()
      val enum = schema.effectiveEnum().bind()
      val discriminators = schema.allOf.mapNotNull { discriminator(it) }
      when {
        discriminators.size > 1 -> failure<AllOfDataType>("Only 1 discriminator is allowed in 'allOf'.").bind()
        else                    ->
          AllOfDataType.create(
            name = schema.name,
            subTypes = requiredSubDataTypes,
            outerIsNullable = schema.isNullable(),
            discriminator = discriminators.firstOrNull(),
            enum = enum
          ).bind()
      }
    }
  }

  private fun Schema<*>.isInline(): Boolean =
    `$ref` == null && !hasComposition()

  private fun Schema<*>.isRequiredOnly(): Boolean =
    isInline() && isAnyType() && !required.isNullOrEmpty()

  private fun Schema<*>.foreignRequiredProperties(): Set<String> =
    (allOf.filter { it.isInline() } + this)
      .flatMap { it.required.orEmpty() - it.safeProperties().keys }
      .toSet()

  private fun List<DataType<out Any>>.withRequiredProperties(names: Set<String>): Result<List<DataType<out Any>>> =
    rejectUndeclared(names - flatMap { it.declaredProperties() }.toSet())
      .flatMap { map { it.withRequiredProperties(names intersect it.declaredProperties()) }.combineResults() }

  private fun DataType<out Any>.withRequiredProperties(names: Set<String>): Result<DataType<out Any>> =
    when {
      names.isEmpty()        -> success(this)
      this is ObjectDataType -> withRequiredProperties(names)
      this is AllOfDataType  -> subTypes.withRequiredProperties(names).flatMap { withSubTypes(it) }
      this is ProxyDataType  -> delegate.withRequiredProperties(names)
      else                   -> success(this)
    }

  private fun DataType<out Any>.declaredProperties(): Set<String> =
    compositionLeaves { it is AllOfDataType }.propertyNames()

  private fun List<DataType<out Any>>.rejectUndeclared(names: Set<String>): Result<Unit> {
    val insideOneOfOrAnyOf = names intersect flatMap { it.reachableProperties() }.toSet()
    val circularBranches = flatMap { it.circularBranches() }.map { it.name }.distinct()
    return rejectInsideOneOfOrAnyOf(insideOneOfOrAnyOf) combineWith
        rejectNotDeclared(names - insideOneOfOrAnyOf, circularBranches)
  }

  private fun rejectInsideOneOfOrAnyOf(names: Set<String>): Result<Unit> =
    if (names.isEmpty()) success()
    else failure("The following required properties are declared only inside 'oneOf' or 'anyOf', where 'required' cannot be enforced: ${names.joinWithQuotes()}. Declare them in an 'allOf' branch.")

  private fun rejectNotDeclared(names: Set<String>, circularBranches: List<String>): Result<Unit> {
    val notInspectable = "The following required properties are not declared by any 'allOf' branch that can be inspected: ${names.joinWithQuotes()}."
    return when {
      names.isEmpty()            -> success()
      circularBranches.isEmpty() -> failure("The following required properties are not defined in the schema: ${names.joinWithQuotes()}")
      circularBranches.size == 1 -> failure("$notInspectable Branch ${circularBranches.joinWithQuotes()} is a circular reference.")
      else                       -> failure("$notInspectable Branches ${circularBranches.joinWithQuotes()} are circular references.")
    }
  }

  private fun DataType<out Any>.reachableProperties(): Set<String> =
    compositionLeaves { true }.propertyNames()

  private fun DataType<out Any>.circularBranches(): List<ProxyDataType> =
    compositionLeaves { true }.filterIsInstance<ProxyDataType>().filterNot { it.isResolved }

  private fun DataType<out Any>.compositionLeaves(visited: Set<ProxyDataType> = emptySet(),
                                               follows: (CompositeDataType<*>) -> Boolean): List<DataType<out Any>> =
    when (this) {
      is CompositeDataType<*> if follows(this)           -> subTypes.flatMap { it.compositionLeaves(visited, follows) }
      is ProxyDataType if isResolved && this !in visited -> delegate.compositionLeaves(visited + this, follows)
      else                                               -> listOf(this)
    }

  private fun List<DataType<out Any>>.propertyNames(): Set<String> =
    filterIsInstance<ObjectDataType>().flatMapTo(mutableSetOf()) { it.properties.keys }
}
