package dev.contracteer.core.operation

/**
 * Validates that a string may be used as an HTTP header field-value per RFC 7230 §3.2.6.
 *
 * Allowed chars: horizontal tab (0x09), space (0x20), and visible ASCII (0x21-0x7E).
 * Disallowed: NUL, CR, LF, other controls, DEL (0x7F), and non-ASCII (0x80+).
 */
object HttpHeaderValue {

  fun isValid(value: String): Boolean =
    value.all { isFieldValueChar(it) }

  fun firstInvalidChar(value: String): Char? =
    value.firstOrNull { !isFieldValueChar(it) }

  /**
   * Returns [value] if it contains only RFC 7230 §3.2.6 field-value characters.
   *
   * Throws [IllegalStateException] otherwise, naming [headerName], the offending code point,
   * and the RFC rule. Used as a runtime safety net should a generated header value escape
   * the load-time check.
   */
  fun requireValid(headerName: String, value: String): String {
    val invalid = firstInvalidChar(value) ?: return value
    val codePoint = "U+%04X".format(invalid.code)
    error("Header '$headerName' contains character $codePoint not valid in HTTP header values per RFC 7230 §3.2.6. " +
      "Tighten the schema pattern or provide explicit example values.")
  }

  internal fun isFieldValueChar(c: Char): Boolean =
    c == '\t' || c.code in 0x20..0x7E
}
