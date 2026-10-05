package dev.contracteer.core.serde

import dev.contracteer.core.Result
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.UrlEncoding
import dev.contracteer.core.codec.DecodeView
import dev.contracteer.core.codec.ParameterCodec
import dev.contracteer.core.combineResults
import dev.contracteer.core.datatype.DataType
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * [Serde] for `application/x-www-form-urlencoded` request and response bodies.
 * Deserialization fails on a malformed percent-escape.
 *
 * Delegates to per-property [ParameterCodec]s for encoding/decoding individual properties
 * of the object.
 */
class FormUrlEncodedSerde internal constructor(
  internal val propertyEncodings: Map<String, PropertyEncoding>
): Serde() {

  override fun doSerialize(value: Any?): String {
    require(value is Map<*, *>) { "FormUrlEncodedSerde expects a Map but received ${value?.let { it::class.simpleName }}" }

    return value.entries
      .filter { (key, _) -> key.toString() in propertyEncodings }
      .flatMap { (key, propValue) ->
        val encoding = propertyEncodings.getValue(key.toString())
        encoding.codec.encode(propValue).map { it to encoding.allowReserved }
      }
      .joinToString("&") { (entry, allowReserved) ->
        val (key, propValue) = entry
        "${urlEncode(key)}=${UrlEncoding.encode(propValue, allowReserved)}"
      }
  }

  override fun doDeserialize(source: String?, targetDataType: DataType<out Any>): Result<Any?> {
    if (source == null) return success(null)

    return parseValues(source).flatMap { decodeProperties(it) }
  }

  private fun decodeProperties(values: Map<String, List<String>>): Result<Map<String, Any?>> =
    propertyEncodings.entries
      .map { (propName, encoding) ->
        encoding.codec
          .decode(values, encoding.view.source)
          .map { propName to it }
      }
      .combineResults()
      .map { pairs -> pairs.filter { it.second != null }.toMap() }

  private fun parseValues(source: String): Result<Map<String, List<String>>> =
    source
      .split("&")
      .filter { "=" in it }
      .map { decodeEntry(it) }
      .combineResults()
      .map { pairs -> pairs.groupBy({ it.first }, { it.second }) }

  private fun decodeEntry(entry: String): Result<Pair<String, String>> =
    try {
      success(urlDecode(entry.substringBefore("=")) to urlDecode(entry.substringAfter("=")))
    } catch (_: IllegalArgumentException) {
      failure("Malformed percent-escape in '$entry'")
    }
}

internal data class PropertyEncoding(val codec: ParameterCodec, val view: DecodeView, val allowReserved: Boolean = false)

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun urlDecode(value: String): String = URLDecoder.decode(value, "UTF-8")