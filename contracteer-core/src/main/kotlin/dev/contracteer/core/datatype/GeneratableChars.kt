package dev.contracteer.core.datatype

/**
 * Finds the characters that a generated value can carry, for checks that restrict the
 * character set of a value on the wire (such as HTTP header values).
 *
 * Reads the schema rather than sampling generated values: `enum` values, `pattern` generators,
 * property names and injected discriminator values, walking into array items, object properties
 * and composition sub-types. It never misses a character these can carry, but may report one
 * that a generated value never carries (see [StringPattern.lowestCharOutside]). Characters that
 * Contracteer's own generators pick (strings without `pattern`, formats, numbers) are not
 * inspected.
 */
internal object GeneratableChars {

  /** Returns the lowest character a value of [dataType] can carry that [isAllowed] rejects, or `null` when there is none. */
  fun lowestOutside(dataType: DataType<out Any>, isAllowed: (Char) -> Boolean): Char? =
    GeneratableCharsWalk(isAllowed, CycleGuard()).lowestIn(dataType)
}

private class GeneratableCharsWalk(private val isAllowed: (Char) -> Boolean,
                                   private val guard: CycleGuard<Char?>) {

  fun lowestIn(dataType: DataType<out Any>): Char? {
    val allowedValues = dataType.allowedValues
    return when {
      dataType is ProxyDataType -> guard.visit(dataType, onCycle = { null }) { lowestIn(dataType.delegate) }
      allowedValues != null     -> allowedValues.asSequence().mapNotNull { lowestInValue(it) }.minOrNull()
      else                      -> lowestInStructure(dataType)
    }
  }

  private fun lowestInStructure(dataType: DataType<out Any>): Char? =
    when (dataType) {
      is StringDataType       -> dataType.pattern?.lowestCharOutside(isAllowed)
      is ArrayDataType        -> lowestIn(dataType.itemDataType)
      is ObjectDataType       -> lowestInObject(dataType)
      is CompositeDataType<*> -> listOfNotNull(lowestAmong(dataType.subTypes), lowestInDiscriminator(dataType)).minOrNull()
      else                    -> null
    }

  private fun lowestInObject(dataType: ObjectDataType): Char? =
    listOfNotNull(
      dataType.properties.keys.mapNotNull { lowestInText(it) }.minOrNull(),
      lowestAmong(dataType.properties.values),
      dataType.additionalPropertiesDataType?.let { lowestIn(it) },
      dataType.propertyNamesDataType?.let { lowestIn(it) }
    ).minOrNull()

  // Mirrors CompositeDataType's injection: allOf injects its own mapping name, oneOf and anyOf the picked sub-type's.
  private fun lowestInDiscriminator(dataType: CompositeDataType<*>): Char? =
    dataType.discriminator?.let { discriminator ->
      val injectedFor = if (dataType is AllOfDataType) listOf(dataType.name) else dataType.subTypes.map { it.name }
      injectedFor.mapNotNull { lowestInText(discriminator.getMappingName(it)) }.minOrNull()
    }

  private fun lowestAmong(dataTypes: Collection<DataType<out Any>>): Char? =
    dataTypes.mapNotNull { lowestIn(it) }.minOrNull()

  private fun lowestInValue(value: Any?): Char? =
    when (value) {
      null             -> null
      is Map<*, *>     -> lowestAmongValues(value.keys + value.values)
      is Collection<*> -> lowestAmongValues(value)
      else             -> lowestInText(value.toString())
    }

  private fun lowestAmongValues(values: Collection<Any?>): Char? =
    values.mapNotNull { lowestInValue(it) }.minOrNull()

  private fun lowestInText(text: String): Char? =
    text.filterNot(isAllowed).minOrNull()
}
