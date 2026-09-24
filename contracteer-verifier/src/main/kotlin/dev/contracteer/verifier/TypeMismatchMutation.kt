package dev.contracteer.verifier

import dev.contracteer.core.datatype.*

internal object TypeMismatchMutation {

  fun mutate(dataType: DataType<out Any>): String? =
    when (dataType) {
      is StringDataType                                         -> null
      is BinaryDataType                                         -> null
      is AnyDataType                                            -> null
      is NullDataType                                           -> null
      is ArrayDataType if mutate(dataType.itemDataType) == null -> null
      else                                                      -> "<<not-a-${dataType.openApiType.substringAfter('/')}>>"
    }
}
