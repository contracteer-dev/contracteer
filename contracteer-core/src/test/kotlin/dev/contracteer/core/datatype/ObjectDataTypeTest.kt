package dev.contracteer.core.datatype

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import dev.contracteer.core.assertSuccess
import dev.contracteer.core.datatype.GenerationOutcome.Boundary
import dev.contracteer.core.datatype.GenerationOutcome.Reason
import dev.contracteer.core.dsl.arrayType
import dev.contracteer.core.dsl.booleanType
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType

class ObjectDataTypeTest {

  @Test
  fun `creation fails when a required property is not defined as a property`() {
    // when
    val result = ObjectDataType.create(name = "cat",
                                       properties = mapOf("hunts" to booleanType(),
                                                          "age" to integerType()),
                                       allowAdditionalProperties = false,
                                       isNullable = false,
                                       requiredProperties = setOf("hunts", "age", "type"))
    // then
    assert(result.isFailure())
    assert(result.errors().first().contains("'type'"))
  }

  @Test
  fun `validation succeeds for a null value when nullable`() {
    // given
    val objectDataType = objectType(isNullable = true) { properties { "prop" to integerType() } }

    // when
    val result = objectDataType.validate(null)

    // then
    assert(result.isSuccess())
  }

  @Test
  fun `validation fails for a null value when not nullable`() {
    // given
    val objectDataType = objectType(isNullable = false) { properties { "prop" to integerType() } }

    // when
    val result = objectDataType.validate(null)

    // then
    assert(result.isFailure())
  }

  @Test
  fun `validation fails when a value is not of type Map`() {
    // given
    val objectDataType = objectType { properties { "prop" to integerType() } }

    // when
    val result = objectDataType.validate(123)

    // then
    assert(result.isFailure())
  }

  @Test
  fun `validation succeeds when a value is of type Map`() {
    // given
    val objectDataType = objectType { properties { "prop" to integerType() } }

    // when
    val result = objectDataType.validate(mapOf("prop" to 1))

    // then
    assert(result.isSuccess())
  }

  @Test
  fun `validation succeeds when a non required property is not present`() {
    // given
    val objectDataType = objectType {
      properties {
        "prop" to integerType()
        "prop2" to integerType()
      }
      required("prop")
    }
    // when
    val result = objectDataType.validate(mapOf("prop" to 1))

    // then
    assert(result.isSuccess())
  }

  @Test
  fun `validation succeeds when a non required and non nullable property is not present`() {
    // given
    val objectDataType = objectType {
      properties {
        "prop" to integerType()
        "prop2" to integerType(isNullable = false)
      }
      required("prop")
    }
    // when
    val result = objectDataType.validate(mapOf("prop" to 1))

    // then
    assert(result.isSuccess())
  }

  @Test
  fun `validation fails when a property is not of the expected type`() {
    // given
    val objectDataType = objectType { properties { "prop" to integerType() } }

    // when
    val result = objectDataType.validate(mapOf("prop" to true))

    // then
    assert(result.isFailure())
  }

  @Test
  fun `validation fails when a non nullable property is null`() {
    // given
    val objectDataType = objectType {
      properties {
        "prop" to integerType(isNullable = false)
        "prop2" to booleanType()
      }
    }

    // when
    val result = objectDataType.validate(mapOf(
      "prop" to null,
      "prop2" to true))

    // then
    assert(result.isFailure())
  }

  @Test
  fun `validation fails when a required property is missing`() {
    // given
    val objectDataType = objectType {
      properties {
        "prop" to integerType()
        "prop2" to booleanType()
      }
      required("prop")
    }

    // when
    val result = objectDataType.validate(mapOf("prop2" to true))

    // then
    assert(result.isFailure())
    assert(result.errors().size == 1)
    assert(listOf("is required", "prop").all { result.errors().first().contains(it) })
  }

  @Nested
  inner class WithRequiredPropertiesAdded {

    @Test
    fun `adding a required property enforces it and leaves the original unchanged`() {
      // given
      val objectDataType = objectType {
        properties {
          "first_name" to stringType()
          "last_name" to stringType()
        }
      }

      // when
      val result = objectDataType.withRequiredProperties(setOf("first_name"))

      // then
      assert(result.assertSuccess().validate(mapOf("last_name" to "Lovelace")).isFailure())
      assert(objectDataType.validate(mapOf("last_name" to "Lovelace")).isSuccess())
    }

    @Test
    fun `adding required properties fails when they exceed maxProperties`() {
      // given
      val objectDataType = objectType(maxProperties = 1) {
        properties {
          "first_name" to stringType()
          "last_name" to stringType()
        }
        required("first_name")
      }

      // when
      val result = objectDataType.withRequiredProperties(setOf("last_name"))

      // then
      assert(result.isFailure())
    }

    @Test
    fun `adding a required property keeps the enum`() {
      // given
      val objectDataType = objectType(enum = listOf(mapOf("first_name" to "Ada"))) {
        properties { "first_name" to stringType() }
      }

      // when
      val result = objectDataType.withRequiredProperties(setOf("first_name"))

      // then
      assert(result.assertSuccess().validate(mapOf("first_name" to "Grace")).isFailure())
    }
  }

  @Nested
  inner class WithAdditionalProperties {

    @Test
    fun `validation fails when extra properties are provided and additionalProperties is disabled`() {
      // given
      val objectDataType =
        objectType(allowAdditionalProperties = false) { properties { "prop" to integerType() } }

      // when
      val result = objectDataType.validate(mapOf("prop" to 1, "prop2" to 2, "prop3" to 3))

      // then
      assert(result.isFailure())
    }

    @Test
    fun `validation fails when extra properties are not of the expected type`() {
      // given
      val objectDataType = objectType(
        allowAdditionalProperties = true,
        additionalPropertiesDataType = stringType()
      ) { properties { "prop" to integerType() } }

      // when
      val result = objectDataType.validate(mapOf("prop" to 1, "prop2" to true, "prop3" to 3.5))

      // then
      assert(result.isFailure())
    }

    @Test
    fun `validation succeeds when extra properties datatype is not specified`() {
      // given
      val objectDataType = objectType(allowAdditionalProperties = true) {
        properties { "prop" to integerType() }
      }

      // when
      val result = objectDataType.validate(mapOf("prop" to 1, "prop2" to true, "prop3" to 3.5))

      // then
      assert(result.isSuccess())
    }

    @Test
    fun `validation succeeds when allow additional properties is true but there is no extra properties`() {
      // given
      val objectDataType = objectType(allowAdditionalProperties = true) {
        properties { "prop" to integerType() }
      }

      // when
      val result = objectDataType.validate(mapOf("prop" to 1))

      // then
      assert(result.isSuccess())
    }

    @Test
    fun `validation succeeds when allow additional properties datatype is specified but there is no extra properties`() {
      // given
      val objectDataType = objectType(
        allowAdditionalProperties = true,
        additionalPropertiesDataType = stringType()
      ) { properties { "prop" to integerType() } }

      // when
      val result = objectDataType.validate(mapOf("prop" to 1))

      // then
      assert(result.isSuccess())
    }
  }


  @Test
  fun `asRequestType removes readOnly properties and adjusts requiredProperties`() {
    // given
    val dataType = objectType(readOnlyProperties = setOf("id")) {
      properties {
        "id" to integerType()
        "name" to stringType()
        "password" to stringType()
      }
      required("id", "name", "password")
    }

    // when
    val requestType = dataType.asRequestType() as ObjectDataType

    // then
    assert(!requestType.properties.containsKey("id"))
    assert(requestType.properties.containsKey("name"))
    assert(requestType.properties.containsKey("password"))
    assert(!requestType.requiredProperties.contains("id"))
    assert(requestType.requiredProperties.contains("name"))
  }

  @Test
  fun `asResponseType removes writeOnly properties and adjusts requiredProperties`() {
    // given
    val dataType = objectType(writeOnlyProperties = setOf("password")) {
      properties {
        "id" to integerType()
        "name" to stringType()
        "password" to stringType()
      }
      required("id", "name", "password")
    }

    // when
    val responseType = dataType.asResponseType() as ObjectDataType

    // then
    assert(responseType.properties.containsKey("id"))
    assert(responseType.properties.containsKey("name"))
    assert(!responseType.properties.containsKey("password"))
    assert(!responseType.requiredProperties.contains("password"))
    assert(responseType.requiredProperties.contains("id"))
  }

  @Test
  fun `asRequestType and asResponseType return this when no readOnly or writeOnly properties`() {
    // given
    val dataType = objectType { properties { "name" to stringType() } }

    // then
    assert(dataType.asRequestType() === dataType)
    assert(dataType.asResponseType() === dataType)
  }

  @Test
  fun `request variant randomValue excludes readOnly and response variant excludes writeOnly`() {
    // given
    val dataType = objectType(
      readOnlyProperties = setOf("id"),
      writeOnlyProperties = setOf("password")
    ) {
      properties {
        "id" to integerType()
        "name" to stringType()
        "password" to stringType()
      }
    }

    // when
    val requestValue = dataType.asRequestType().randomValue()!!
    val responseValue = dataType.asResponseType().randomValue()!!

    // then
    assert(!requestValue.containsKey("id") && requestValue.containsKey("name") && requestValue.containsKey("password"))
    assert(responseValue.containsKey("id") && responseValue.containsKey("name") && !responseValue.containsKey("password"))
  }

  @Test
  fun `request variant validation succeeds without readOnly required field`() {
    // given
    val requestType = objectType(readOnlyProperties = setOf("id")) {
      properties {
        "id" to integerType()
        "name" to stringType()
      }
      required("id", "name")
    }.asRequestType()

    // when
    val result = requestType.validate(mapOf("name" to "Athos"))

    // then
    assert(result.isSuccess())
  }

  @Test
  fun `response variant validation succeeds without writeOnly required field`() {
    // given
    val responseType = objectType(writeOnlyProperties = setOf("password")) {
      properties {
        "password" to stringType()
        "name" to stringType()
      }
      required("password", "name")
    }.asResponseType()

    // when
    val result = responseType.validate(mapOf("name" to "Athos"))

    // then
    assert(result.isSuccess())
  }

  @Test
  fun `asRequestType transforms nested objects recursively`() {
    // given
    val address = objectType(name = "address", readOnlyProperties = setOf("id")) {
      properties {
        "id" to integerType()
        "street" to stringType()
      }
    }
    val user = objectType {
      properties {
        "address" to address
        "name" to stringType()
      }
    }

    // when
    val requestType = user.asRequestType() as ObjectDataType
    val addressRequestType = requestType.properties["address"] as ObjectDataType

    // then
    assert(!addressRequestType.properties.containsKey("id"))
    assert(addressRequestType.properties.containsKey("street"))
  }

  @Test
  fun `asRequestType transforms objects nested inside arrays`() {
    // given
    val item = objectType(name = "item", readOnlyProperties = setOf("id")) {
      properties {
        "id" to integerType()
        "name" to stringType()
      }
    }
    val parent = objectType {
      properties {
        "items" to arrayType(item)
        "label" to stringType()
      }
    }

    // when
    val requestType = parent.asRequestType() as ObjectDataType
    val arrayType = requestType.properties["items"] as ArrayDataType
    val itemRequestType = arrayType.itemDataType as ObjectDataType

    // then
    assert(!itemRequestType.properties.containsKey("id"))
    assert(itemRequestType.properties.containsKey("name"))
  }

  @Test
  fun `randomValue returns Boundary with Reason DEPTH when nested objects exceed maxDepth`() {
    // given — two required-non-nullable nesting levels; budget allows depth 1 only
    val inner = objectType(name = "Inner") {
      properties { "value" to stringType() }
      required("value")
    }
    val outer = objectType(name = "Outer") {
      properties { "inner" to inner }
      required("inner")
    }
    val ctx = GenerationContext.withBudget(maxDepth = 1, maxNodes = 1000)

    // when
    val result = outer.randomValue(ctx)

    // then
    assert(result is Boundary)
    assert((result as Boundary).reason == Reason.DEPTH)
  }

  @Test
  fun `randomValue succeeds for required enum-typed properties under tight budget`() {
    // given
    val enumValues = listOf("a", "b", "c")
    val container = objectType(name = "Container") {
      properties {
        "p1" to stringType(enum = enumValues)
        "p2" to stringType(enum = enumValues)
        "p3" to stringType(enum = enumValues)
        "p4" to stringType(enum = enumValues)
        "p5" to stringType(enum = enumValues)
      }
      required("p1", "p2", "p3", "p4", "p5")
    }
    val ctx = GenerationContext.withBudget(maxDepth = 10, maxNodes = 1)

    // when
    val result = container.randomValue(ctx)

    // then
    assert(result is GenerationOutcome.Value)
    val entries = (result as GenerationOutcome.Value).value as Map<*, *>
    assert(entries.size == 5)
  }

  @Nested
  inner class WithEnum {

    @Test
    fun `creation fails when enum contains a value that does not match any provided sub datatype`() {
      // when
      val result = ObjectDataType.create(name = "object",
                                         properties = mapOf("prop" to integerType(), "prop2" to integerType()),
                                         requiredProperties = setOf("prop2"),
                                         allowAdditionalProperties = true,
                                         isNullable = false,
                                         enum = listOf(mapOf("prop" to 1, "prop2" to "2"), mapOf("prop" to 2)))

      // then
      assert(result.isFailure())
    }

    @Test
    fun `validation succeeds when the value is included in the enum`() {
      // given
      val objectDataType = objectType(enum = listOf(mapOf("prop" to 1), mapOf("prop" to 2))) {
        properties { "prop" to integerType() }
      }

      // when
      val result = objectDataType.validate(mapOf("prop" to 1))

      // then
      assert(result.isSuccess())
    }

    @Test
    fun `validation fails when the value is not included in the enum`() {
      // given
      val objectDataType = objectType(enum = listOf(mapOf("prop" to 1), mapOf("prop" to 2))) {
        properties { "prop" to integerType() }
      }

      // when
      val result = objectDataType.validate(mapOf("john" to 5))

      // then
      assert(result.isFailure())
    }

    @Test
    fun `generates valid random value that matches one of the enumerated values`() {
      // given
      val enum = listOf(mapOf("prop" to "value1"), mapOf("prop" to "value2"))
      val objectDataType = objectType(enum = enum) { properties { "prop" to stringType() } }

      // when
      val result = objectDataType.randomValue()!!

      // then
      assert(enum.contains(result))
    }
  }

  @Nested
  inner class WithMinProperties {

    @Test
    fun `creation fails when minProperties is negative`() {
      // when
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("a" to stringType()),
        allowAdditionalProperties = true,
        isNullable = false,
        minProperties = -1
      )

      // then
      assert(result.isFailure())
    }

    @Test
    fun `creation fails when minProperties exceeds declared properties and no additionalProperties schema`() {
      // when
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("a" to stringType(), "b" to stringType()),
        allowAdditionalProperties = true,
        isNullable = false,
        minProperties = 3
      )

      // then
      assert(result.isFailure())
    }

    @Test
    fun `creation succeeds when minProperties exceeds declared properties but additionalProperties schema is available`() {
      // when
      val result = ObjectDataType.create(
        name = "Tags",
        properties = emptyMap(),
        allowAdditionalProperties = true,
        additionalPropertiesDataType = stringType(),
        isNullable = false,
        minProperties = 1
      )

      // then
      val dataType = result.assertSuccess()
      val generated = dataType.randomValue()!!
      assert(generated.isNotEmpty())
      assert(generated.values.all { it is String })
    }

    @Test
    fun `validation fails when object has fewer properties than minProperties`() {
      // given
      val objectDataType = objectType(minProperties = 2) {
        properties {
          "a" to stringType()
          "b" to stringType()
        }
      }

      // when
      val result = objectDataType.validate(mapOf("a" to "hello"))

      // then
      assert(result.isFailure())
    }

    @Test
    fun `validation succeeds when object has exactly minProperties`() {
      // given
      val objectDataType = objectType(minProperties = 2) {
        properties {
          "a" to stringType()
          "b" to stringType()
        }
      }

      // when
      val result = objectDataType.validate(mapOf("a" to "hello", "b" to "world"))

      // then
      assert(result.isSuccess())
    }

    @Test
    fun `randomValue returns Boundary when minProperties cannot be met via additionalProperties synthesis`() {
      // given
      val proxy = ProxyDataType("Recursive")
      val recursive = objectType(name = "Recursive") {
        properties { "self" to proxy }
        required("self")
      }
      proxy.delegate = recursive
      val container = objectType(
        name = "Container",
        additionalPropertiesDataType = proxy,
        minProperties = 5
      ) {
        properties {
          "name" to stringType()
          "age" to integerType()
        }
      }
      val ctx = GenerationContext.default()

      // when
      val result = container.randomValue(ctx)

      // then
      assert(result is Boundary && result.reason == Reason.CYCLE)
    }
  }

  @Nested
  inner class WithMaxProperties {

    @Test
    fun `creation fails when maxProperties is negative`() {
      // when
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("a" to stringType()),
        allowAdditionalProperties = true,
        isNullable = false,
        maxProperties = -1
      )

      // then
      assert(result.isFailure())
    }

    @Test
    fun `creation fails when maxProperties is less than required properties count`() {
      // when
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("a" to stringType(), "b" to stringType(), "c" to stringType()),
        requiredProperties = setOf("a", "b", "c"),
        allowAdditionalProperties = true,
        isNullable = false,
        maxProperties = 2
      )

      // then
      assert(result.isFailure())
    }

    @Test
    fun `validation fails when object has more properties than maxProperties`() {
      // given
      val objectDataType = objectType(allowAdditionalProperties = true, maxProperties = 1) {
        properties {
          "a" to stringType()
          "b" to stringType()
        }
      }

      // when
      val result = objectDataType.validate(mapOf("a" to "hello", "b" to "world"))

      // then
      assert(result.isFailure())
    }

    @Test
    fun `validation succeeds when object has exactly maxProperties`() {
      // given
      val objectDataType = objectType(maxProperties = 2) {
        properties {
          "a" to stringType()
          "b" to stringType()
        }
      }

      // when
      val result = objectDataType.validate(mapOf("a" to "hello", "b" to "world"))

      // then
      assert(result.isSuccess())
    }

    @Test
    fun `generates object with at most maxProperties including required`() {
      // given
      val objectDataType = objectType(maxProperties = 2) {
        properties {
          "a" to stringType()
          "b" to stringType()
          "c" to stringType()
        }
        required("a")
      }

      // when
      val result = objectDataType.randomValue()!!

      // then
      assert(result.size <= 2)
      assert(result.containsKey("a"))
    }
  }

  @Nested
  inner class WithPropertyNames {

    @Test
    fun `validation fails when a property name does not match the propertyNames pattern`() {
      // Given
      val objectDataType = objectType(propertyNames = stringType(pattern = "^[a-z]+$")) {
        properties { "foo" to integerType() }
      }

      // When
      val result = objectDataType.validate(mapOf("foo" to 1, "Bar" to 2))

      // Then
      assert(result.isFailure())
      assert(result.errors().any { it.contains("Bar") })
    }

    @Test
    fun `validation succeeds when every property name matches the propertyNames pattern`() {
      // Given
      val objectDataType = objectType(propertyNames = stringType(pattern = "^[a-z]+$")) {
        properties { "foo" to integerType() }
      }

      // When
      val result = objectDataType.validate(mapOf("foo" to 1, "bar" to 2))

      // Then
      assert(result.isSuccess())
    }

    @Test
    fun `validation fails when an additional property name violates the propertyNames length constraint`() {
      // Given
      val objectDataType = objectType(
        allowAdditionalProperties = true,
        additionalPropertiesDataType = integerType(),
        propertyNames = stringType(maxLength = 3)
      ) { properties { "foo" to integerType() } }

      // When
      val result = objectDataType.validate(mapOf("foo" to 1, "barbaz" to 2))

      // Then
      assert(result.isFailure())
      assert(result.errors().any { it.contains("barbaz") })
    }

    @Test
    fun `creation fails when a declared property name does not satisfy the propertyNames schema`() {
      // Given
      val propertyNames = stringType(pattern = "^[a-z]+$")

      // When
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("Foo" to integerType()),
        allowAdditionalProperties = true,
        isNullable = false,
        propertyNamesDataType = propertyNames
      )

      // Then
      assert(result.isFailure())
      assert(result.errors().any { it.contains("Foo") && it.contains("propertyNames") })
    }

    @Test
    fun `random generation synthesizes additional property names that satisfy the propertyNames pattern`() {
      // Given a constraint admitting only lowercase keys, with minProperties forcing synthesis
      val objectDataType = objectType(
        additionalPropertiesDataType = integerType(),
        minProperties = 3,
        propertyNames = stringType(pattern = "^[a-z]{3,8}$")
      )

      // When
      val generated = objectDataType.randomValue()!!

      // Then
      assert(generated.size >= 3)
      assert(generated.keys.all { it.matches(Regex("^[a-z]{3,8}$")) }) {
        "Generated keys ${generated.keys} do not all match the pattern"
      }
    }

    @Test
    fun `random generation reports a NAMES boundary when propertyNames admits too few unique keys`() {
      // Given a propertyNames schema admitting only one value, with minProperties forcing two synthesized keys
      val objectDataType = objectType(
        additionalPropertiesDataType = integerType(),
        minProperties = 2,
        propertyNames = stringType(pattern = "^a$")
      )

      // When
      val outcome = objectDataType.randomValue(GenerationContext.default())

      // Then
      assert(outcome is Boundary && outcome.reason == Reason.NAMES)
    }
  }

  @Nested
  inner class WithMinAndMaxProperties {

    @Test
    fun `creation fails when minProperties is combined with readOnly properties`() {
      // when
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("a" to stringType(), "b" to stringType()),
        readOnlyProperties = setOf("a"),
        allowAdditionalProperties = true,
        isNullable = false,
        minProperties = 1
      )

      // then
      assert(result.isFailure())
    }

    @Test
    fun `creation fails when maxProperties is combined with writeOnly properties`() {
      // when
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("a" to stringType(), "b" to stringType()),
        writeOnlyProperties = setOf("a"),
        allowAdditionalProperties = true,
        isNullable = false,
        maxProperties = 2
      )

      // then
      assert(result.isFailure())
    }

    @Test
    fun `creation fails when minProperties is greater than maxProperties`() {
      // when
      val result = ObjectDataType.create(
        name = "object",
        properties = mapOf("a" to stringType(), "b" to stringType(), "c" to stringType()),
        allowAdditionalProperties = true,
        isNullable = false,
        minProperties = 3,
        maxProperties = 1
      )

      // then
      assert(result.isFailure())
    }
  }
}
