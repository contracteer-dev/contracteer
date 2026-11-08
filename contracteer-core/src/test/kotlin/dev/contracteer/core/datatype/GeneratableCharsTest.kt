package dev.contracteer.core.datatype

import dev.contracteer.core.dsl.allOfType
import dev.contracteer.core.dsl.anyOfType
import dev.contracteer.core.dsl.arrayType
import dev.contracteer.core.dsl.cyclicObjectType
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.oneOfType
import dev.contracteer.core.dsl.stringType
import kotlin.test.Test

class GeneratableCharsTest {

  @Test
  fun `a string without pattern generates no char outside the allowed set`() {
    // given
    val dataType = stringType()

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == null) { "Expected null but got $lowest" }
  }

  @Test
  fun `a string with pattern reports chars its pattern generates`() {
    // given
    val dataType = stringType(pattern = "^[a\\x13]$")

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `an enum reports the lowest char among its values`() {
    // given
    val dataType = stringType(enum = listOf("café", "ok\u0013"))

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `an enum of arrays reports chars nested in its values`() {
    // given
    val dataType = arrayType(items = stringType(), enum = listOf(listOf("ok", "café")))

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == 'é') { "Expected U+00E9 but got $lowest" }
  }

  @Test
  fun `an array reports chars its items generate`() {
    // given
    val dataType = arrayType(items = stringType(pattern = "^[a\\x13]$"))

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `an object reports chars in its property names`() {
    // given
    val dataType = objectType {
      properties {
        "café" to stringType()
      }
    }

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == 'é') { "Expected U+00E9 but got $lowest" }
  }

  @Test
  fun `an object reports chars its property types generate`() {
    // given
    val dataType = objectType {
      properties {
        "tag" to stringType(pattern = "^[a\\x13]$")
      }
    }

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `an object reports chars its additional properties generate`() {
    // given
    val dataType = objectType(additionalPropertiesDataType = stringType(pattern = "^[a\\x13]$"))

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `an object reports chars its property names schema generates`() {
    // given
    val dataType = objectType(
      additionalPropertiesDataType = stringType(),
      propertyNames = stringType(pattern = "^[a\\x13]$"))

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `a composition reports chars its sub-types generate`() {
    // given
    val dataType = oneOfType {
      subType(integerType())
      subType(stringType(pattern = "^[a\\x13]$"))
    }

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `a oneOf reports chars in the discriminator values it injects`() {
    // given
    val dataType = oneOfType {
      subType(objectType(name = "Cat") { properties { "kind" to stringType() } })
      subType(objectType(name = "Dog") { properties { "kind" to stringType() } })
      discriminator("kind") { mapping("chaté", "Cat") }
    }

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == 'é') { "Expected U+00E9 but got $lowest" }
  }

  @Test
  fun `an allOf reports chars in the discriminator value it injects`() {
    // given
    val dataType = allOfType(name = "Cat") {
      subType(objectType { properties { "kind" to stringType() } })
      discriminator("kind") { mapping("chaté", "Cat") }
    }

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == 'é') { "Expected U+00E9 but got $lowest" }
  }

  @Test
  fun `the lowest char wins across branches`() {
    // given
    val dataType = anyOfType {
      subType(stringType(pattern = "^café$"))
      subType(stringType(pattern = "^[a\\x13]$"))
    }

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }

  @Test
  fun `a cyclic schema terminates and reports chars reachable through the cycle`() {
    // given
    val dataType = cyclicObjectType("Node") { self ->
      properties {
        "next" to self
        "tag" to stringType(pattern = "^[a\\x13]$")
      }
    }

    // when
    val lowest = GeneratableChars.lowestOutside(dataType, printableAscii)

    // then
    assert(lowest == '\u0013') { "Expected U+0013 but got $lowest" }
  }
}
