package dev.contracteer.core.serde

import dev.contracteer.core.assertFailure
import dev.contracteer.core.assertSuccess
import dev.contracteer.core.codec.DecodeView
import dev.contracteer.core.codec.FormParameterCodec
import dev.contracteer.core.codec.PipeDelimitedParameterCodec
import dev.contracteer.core.datatype.DataType
import dev.contracteer.core.dsl.arrayType
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import kotlin.test.Test

class FormUrlEncodedSerdeTest {

  @Test
  fun `serialize object with primitive properties`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType(), "age" to integerType())

    // when
    val result = serde.serialize(mapOf("name" to "John", "age" to 30))

    // then
    assert(result == "name=John&age=30")
  }

  @Test
  fun `serialize object with array property using default encoding`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType(), "colors" to arrayType(items = stringType()))

    // when
    val result = serde.serialize(mapOf("name" to "John", "colors" to listOf("blue", "black")))

    // then
    assert(result == "name=John&colors=blue&colors=black")
  }

  @Test
  fun `serialize object with custom encoding`() {
    // given
    val serde = FormUrlEncodedSerde(mapOf(
      "name" to PropertyEncoding(FormParameterCodec("name", explode = true), DecodeView.of(stringType())),
      "colors" to PropertyEncoding(PipeDelimitedParameterCodec("colors"), DecodeView.of(arrayType(items = stringType())))
    ))

    // when
    val result = serde.serialize(mapOf("name" to "John", "colors" to listOf("blue", "black")))

    // then
    assert(result == "name=John&colors=blue%7Cblack")
  }

  @Test
  fun `serialize URL-encodes special characters in values`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType(), "city" to stringType())

    // when
    val result = serde.serialize(mapOf("name" to "John Doe", "city" to "New York"))

    // then
    assert(result == "name=John+Doe&city=New+York")
  }


  @Test
  fun `deserialize object with primitive properties`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType(), "age" to integerType())
    val dataType = objectType {
      properties {
        "name" to stringType()
        "age" to integerType()
      }
    }

    // when
    val result = serde.deserialize("name=John&age=30", dataType)

    // then
    val obj = result.assertSuccess() as Map<*, *>
    assert(obj["name"] == "John")
    assert(obj["age"] == 30.toBigDecimal())
  }

  @Test
  fun `deserialize object with array property using default encoding`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType(), "colors" to arrayType(items = stringType()))
    val dataType = objectType {
      properties {
        "name" to stringType()
        "colors" to arrayType(items = stringType())
      }
    }

    // when
    val result = serde.deserialize("name=John&colors=blue&colors=black", dataType)

    // then
    val obj = result.assertSuccess() as Map<*, *>
    assert(obj["name"] == "John")
    assert(obj["colors"] == listOf("blue", "black"))
  }

  @Test
  fun `deserialize object with custom encoding`() {
    // given
    val serde = FormUrlEncodedSerde(mapOf(
      "name" to PropertyEncoding(FormParameterCodec("name", explode = true), DecodeView.of(stringType())),
      "colors" to PropertyEncoding(PipeDelimitedParameterCodec("colors"), DecodeView.of(arrayType(items = stringType())))
    ))
    val dataType = objectType {
      properties {
        "name" to stringType()
        "colors" to arrayType(items = stringType())
      }
    }

    // when
    val result = serde.deserialize("name=John&colors=blue|black", dataType)

    // then
    val obj = result.assertSuccess() as Map<*, *>
    assert(obj["name"] == "John")
    assert(obj["colors"] == listOf("blue", "black"))
  }

  @Test
  fun `deserialize URL-decodes special characters in values`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType(), "city" to stringType())
    val dataType = objectType {
      properties {
        "name" to stringType()
        "city" to stringType()
      }
    }

    // when
    val result = serde.deserialize("name=John+Doe&city=New+York", dataType)

    // then
    val obj = result.assertSuccess() as Map<*, *>
    assert(obj["name"] == "John Doe")
    assert(obj["city"] == "New York")
  }

  @Test
  fun `serialize preserves reserved characters when allowReserved is true`() {
    // given
    val serde = FormUrlEncodedSerde(mapOf(
      "callback" to PropertyEncoding(FormParameterCodec("callback", explode = true), DecodeView.of(stringType()), allowReserved = true),
      "name" to PropertyEncoding(FormParameterCodec("name", explode = true), DecodeView.of(stringType()))
    ))

    // when
    val result = serde.serialize(mapOf("callback" to "https://example.com/callback?token=abc", "name" to "John Doe"))

    // then
    assert(result == "callback=https://example.com/callback?token=abc&name=John+Doe")
  }

  @Test
  fun `serialize encodes reserved characters when allowReserved is false`() {
    // given
    val serde = FormUrlEncodedSerde(mapOf(
      "callback" to PropertyEncoding(FormParameterCodec("callback", explode = true), DecodeView.of(stringType()), allowReserved = false),
      "name" to PropertyEncoding(FormParameterCodec("name", explode = true), DecodeView.of(stringType()))
    ))

    // when
    val result = serde.serialize(mapOf("callback" to "https://example.com/callback?token=abc", "name" to "John Doe"))

    // then
    assert(result == "callback=https%3A%2F%2Fexample.com%2Fcallback%3Ftoken%3Dabc&name=John+Doe")
  }

  @Test
  fun `deserialize fails when a value holds a malformed percent-escape`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType())
    val dataType = objectType { properties { "name" to stringType() } }

    // when
    val result = serde.deserialize("name=%ZZ", dataType)

    // then
    assert(result.assertFailure() == listOf("Malformed percent-escape in 'name=%ZZ'"))
  }

  @Test
  fun `deserialize fails when a value ends with an incomplete percent-escape`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType())
    val dataType = objectType { properties { "name" to stringType() } }

    // when
    val result = serde.deserialize("name=John%2", dataType)

    // then
    assert(result.assertFailure() == listOf("Malformed percent-escape in 'name=John%2'"))
  }

  @Test
  fun `deserialize fails when a key holds a malformed percent-escape`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType())
    val dataType = objectType { properties { "name" to stringType() } }

    // when
    val result = serde.deserialize("na%ZZme=John", dataType)

    // then
    assert(result.assertFailure() == listOf("Malformed percent-escape in 'na%ZZme=John'"))
  }

  @Test
  fun `deserialize reports every entry holding a malformed percent-escape`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType(), "city" to stringType())
    val dataType = objectType {
      properties {
        "name" to stringType()
        "city" to stringType()
      }
    }

    // when
    val result = serde.deserialize("name=%ZZ&city=%2", dataType)

    // then
    assert(result.assertFailure() == listOf(
      "Malformed percent-escape in 'name=%ZZ'",
      "Malformed percent-escape in 'city=%2'"
    ))
  }

  @Test
  fun `deserialize returns null for null source`() {
    // given
    val serde = formUrlEncodedSerde("name" to stringType())
    val dataType = objectType { properties { "name" to stringType() } }

    // when
    val result = serde.deserialize(null, dataType)

    // then
    assert(result.assertSuccess() == null)
  }
}

/** Creates a FormUrlEncodedSerde with default encoding (form, explode=true) for the given properties. */
private fun formUrlEncodedSerde(vararg properties: Pair<String, DataType<out Any>>) =
  FormUrlEncodedSerde(properties.associate { (name, type) ->
    name to PropertyEncoding(FormParameterCodec(name, explode = true), DecodeView.of(type))
  })
