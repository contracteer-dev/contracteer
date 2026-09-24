package dev.contracteer.verifier

import dev.contracteer.core.datatype.AnyDataType
import dev.contracteer.core.datatype.NullDataType
import dev.contracteer.core.dsl.allOfType
import dev.contracteer.core.dsl.anyOfType
import dev.contracteer.core.dsl.arrayType
import dev.contracteer.core.dsl.base64Type
import dev.contracteer.core.dsl.binaryType
import dev.contracteer.core.dsl.booleanType
import dev.contracteer.core.dsl.dateTimeType
import dev.contracteer.core.dsl.dateType
import dev.contracteer.core.dsl.emailType
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.numberType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.oneOfType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.dsl.uuidType
import kotlin.test.Test

class TypeMismatchMutationTest {

  @Test
  fun `produces invalid value for IntegerDataType`() {
    assert(TypeMismatchMutation.mutate(integerType()) == "<<not-a-integer>>")
  }

  @Test
  fun `produces invalid value for NumberDataType`() {
    assert(TypeMismatchMutation.mutate(numberType()) == "<<not-a-number>>")
  }

  @Test
  fun `produces invalid value for BooleanDataType`() {
    assert(TypeMismatchMutation.mutate(booleanType()) == "<<not-a-boolean>>")
  }

  @Test
  fun `produces invalid value for DateDataType`() {
    assert(TypeMismatchMutation.mutate(dateType()) == "<<not-a-date>>")
  }

  @Test
  fun `produces invalid value for DateTimeDataType`() {
    assert(TypeMismatchMutation.mutate(dateTimeType()) == "<<not-a-date-time>>")
  }

  @Test
  fun `produces invalid value for UuidDataType`() {
    assert(TypeMismatchMutation.mutate(uuidType()) == "<<not-a-uuid>>")
  }

  @Test
  fun `produces invalid value for EmailDataType`() {
    assert(TypeMismatchMutation.mutate(emailType()) == "<<not-a-email>>")
  }

  @Test
  fun `produces invalid value for Base64DataType`() {
    assert(TypeMismatchMutation.mutate(base64Type()) == "<<not-a-byte>>")
  }

  @Test
  fun `produces invalid value for ObjectDataType`() {
    // Given
    val dataType = objectType { properties { "name" to stringType() } }

    // When
    val result = TypeMismatchMutation.mutate(dataType)

    // Then
    assert(result == "<<not-a-object>>")
  }

  @Test
  fun `produces invalid value for ArrayDataType with non-string items`() {
    assert(TypeMismatchMutation.mutate(arrayType(items = integerType())) == "<<not-a-array>>")
  }

  @Test
  fun `returns null for ArrayDataType with string items`() {
    // Given
    val dataType = arrayType(items = stringType())

    // When
    val result = TypeMismatchMutation.mutate(dataType)

    // Then
    assert(result == null)
  }

  @Test
  fun `produces invalid value for OneOfDataType`() {
    // Given
    val dataType = oneOfType {
      subType(integerType())
      subType(stringType())
    }

    // When
    val result = TypeMismatchMutation.mutate(dataType)

    // Then
    assert(result == "<<not-a-oneOf>>")
  }

  @Test
  fun `produces invalid value for AnyOfDataType`() {
    // Given
    val dataType = anyOfType {
      subType(integerType())
      subType(stringType())
    }

    // When
    val result = TypeMismatchMutation.mutate(dataType)

    // Then
    assert(result == "<<not-a-anyOf>>")
  }

  @Test
  fun `produces invalid value for AllOfDataType`() {
    // Given
    val dataType = allOfType {
      subType(objectType { properties { "a" to stringType() } })
      subType(objectType { properties { "b" to integerType() } })
    }

    // When
    val result = TypeMismatchMutation.mutate(dataType)

    // Then
    assert(result == "<<not-a-allOf>>")
  }

  @Test
  fun `returns null for StringDataType`() {
    assert(TypeMismatchMutation.mutate(stringType()) == null)
  }

  @Test
  fun `returns null for BinaryDataType`() {
    assert(TypeMismatchMutation.mutate(binaryType()) == null)
  }

  @Test
  fun `returns null for AnyDataType`() {
    assert(TypeMismatchMutation.mutate(AnyDataType) == null)
  }

  @Test
  fun `returns null for NullDataType`() {
    // when
    val result = TypeMismatchMutation.mutate(NullDataType)

    // then
    assert(result == null)
  }
}
