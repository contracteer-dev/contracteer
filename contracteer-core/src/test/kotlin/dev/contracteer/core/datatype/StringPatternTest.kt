package dev.contracteer.core.datatype

import dev.contracteer.core.assertFailure
import dev.contracteer.core.assertSuccess
import kotlin.test.Test

internal val printableAscii: (Char) -> Boolean = { it.code in 0x20..0x7E }

class StringPatternTest {

  // --- Happy path ---

  @Test
  fun `create succeeds for a simple pattern`() {
    // when
    val result = StringPattern.create("[a-zA-Z]+")

    // then
    result.assertSuccess()
  }

  @Test
  fun `validate accepts a matching value`() {
    // given
    val pattern = StringPattern.create("[a-zA-Z]+").assertSuccess()

    // when
    val result = pattern.validate("hello")

    // then
    assert(result.assertSuccess() == "hello")
  }

  @Test
  fun `validate rejects a non matching value with a clear error`() {
    // given
    val pattern = StringPattern.create("[0-9]+").assertSuccess()

    // when
    val errors = pattern.validate("abc").assertFailure()

    // then
    assert(errors.any { it.contains("abc") && it.contains("[0-9]+") })
  }

  @Test
  fun `randomValue produces values that match the pattern`() {
    // given
    val pattern = StringPattern.create("[a-z]{5,10}").assertSuccess()
    val compiled = Regex("[a-z]{5,10}")

    // when / then
    repeat(20) {
      val value = pattern.randomValue()
      assert(compiled.containsMatchIn(value)) { "Generated value '$value' does not match pattern" }
    }
  }

  // --- Failure paths ---

  @Test
  fun `create fails when pattern is not a valid Java regex`() {
    // when
    val errors = StringPattern.create("[unclosed").assertFailure()

    // then
    assert(errors.any { it.contains("not a valid regular expression") })
  }

  @Test
  fun `create fails when RgxGen cannot parse the pattern`() {
    // given — anchors inside alternation; RgxGen parse exception, no safe rewrite
    val errors = StringPattern.create("(?:^foo\$)|(?:^bar\$)").assertFailure()

    // then
    assert(errors.any { it.contains("not supported for value generation") })
  }

  @Test
  fun `create fails when the generator produces values that do not match`() {
    // given — known silent-corruption pattern from a real-world batch
    val errors = StringPattern.create("^[0-9a-z\\.\\-]*(?<!\\.)\$").assertFailure()

    // then
    assert(errors.any { it.contains("not supported for value generation") })
  }

  @Test
  fun `create fails gracefully when pattern causes StackOverflowError during validation`() {
    // given — deeply nested quantifiers can overflow Java's recursive regex matcher
    val result = StringPattern.create(
      "abc:[a-z0-9-\\.]{1,63}:[a-z0-9-\\.]{0,63}:[a-z0-9-\\.]{0,63}:[a-z0-9-\\.]{0,63}:[^/].{0,1023}")

    // then — returns a Result (success or failure), never crashes with StackOverflowError
    assert(result.isSuccess() || result.isFailure())
  }

  // --- Rewrite integration (end-to-end) ---

  @Test
  fun `create succeeds for Java Is alias via rewrite`() {
    // given — \p{IsLetter} currently crashes RgxGen but is equivalent to \p{L}
    val pattern = StringPattern.create("\\p{IsLetter}+").assertSuccess()
    val compiled = Regex("\\p{IsLetter}+")

    // when / then
    repeat(20) {
      val value = pattern.randomValue()
      assert(compiled.containsMatchIn(value)) { "Generated value '$value' does not match pattern" }
    }
  }

  @Test
  fun `create succeeds for POSIX class via rewrite`() {
    // given — \p{Print} currently crashes RgxGen; ASCII subset is a safe lossy rewrite
    val pattern = StringPattern.create("\\p{Print}+").assertSuccess()
    val compiled = Regex("\\p{Print}+")

    // when / then
    repeat(20) {
      val value = pattern.randomValue()
      assert(compiled.containsMatchIn(value)) { "Generated value '$value' does not match pattern" }
    }
  }

  @Test
  fun `create succeeds for Unicode Other negation via rewrite`() {
    // given — \P{C}* currently crashes RgxGen; rewrite to complement union
    val pattern = StringPattern.create("\\P{C}+").assertSuccess()
    val compiled = Regex("\\P{C}+")

    // when / then
    repeat(20) {
      val value = pattern.randomValue()
      assert(compiled.containsMatchIn(value)) { "Generated value '$value' does not match pattern" }
    }
  }

  @Test
  fun `create succeeds for dash position in char class via rewrite`() {
    // given — RgxGen parses [0-9-\s] as a malformed range; reorder fixes it
    val pattern = StringPattern.create("[0-9-\\s]+").assertSuccess()
    val compiled = Regex("[0-9-\\s]+")

    // when / then
    repeat(20) {
      val value = pattern.randomValue()
      assert(compiled.containsMatchIn(value)) { "Generated value '$value' does not match pattern" }
    }
  }

  @Test
  fun `randomValue caps generated length when pattern declares an absurd upper bound`() {
    // given — protobuf-derived specs declare patterns like ^.{1,2097152} (2 MiB strings)
    val pattern = StringPattern.create("^.{1,2097152}").assertSuccess()
    val compiled = Regex("^.{1,2097152}")

    // when / then — generation is capped at 1024 chars while still matching the original
    repeat(20) {
      val value = pattern.randomValue()
      assert(value.length <= 1024) { "Generated value has length ${value.length}, expected <= 1024" }
      assert(compiled.containsMatchIn(value)) { "Generated value does not match original pattern" }
    }
  }

  @Test
  fun `validate accepts values longer than the generation cap`() {
    // given — capping applies to generation only; validation must still accept the full
    // range the spec author declared
    val pattern = StringPattern.create("^.{1,2097152}").assertSuccess()
    val longValue = "x".repeat(50_000)

    // when
    val result = pattern.validate(longValue)

    // then
    assert(result.assertSuccess() == longValue)
  }

  // --- Generatable characters ---

  @Test
  fun `lowestCharOutside returns the lowest generatable char outside the allowed set`() {
    // given
    val pattern = StringPattern.create("^[a-z\\x13\\x01]$").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside(printableAscii)

    // then
    assert(lowest == '\u0001') { "Expected U+0001 but got $lowest" }
  }

  @Test
  fun `lowestCharOutside returns null when every generatable char is allowed`() {
    // given
    val pattern = StringPattern.create("^[a-z]+ [A-Z0-9]{2}$").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside(printableAscii)

    // then
    assert(lowest == null) { "Expected null but got $lowest" }
  }

  @Test
  fun `lowestCharOutside sees literals inside groups, alternations and repetitions`() {
    // given
    val pattern = StringPattern.create("^(ab|café)+$").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside(printableAscii)

    // then
    assert(lowest == 'é') { "Expected U+00E9 but got $lowest" }
  }

  @Test
  fun `lowestCharOutside sees characters admitted by a rewritten POSIX class`() {
    // given
    val pattern = StringPattern.create("\\p{ASCII}*").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside(printableAscii)

    // then
    assert(lowest == '\u0000') { "Expected U+0000 but got $lowest" }
  }

  @Test
  fun `lowestCharOutside draws a negated class from printable ASCII`() {
    // given
    val pattern = StringPattern.create("^[^a]{3}$").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside { it == 'b' }

    // then
    assert(lowest == ' ') { "Expected U+0020 but got $lowest" }
  }

  @Test
  fun `lowestCharOutside draws a literal under a negative lookahead from printable ASCII`() {
    // given
    val pattern = StringPattern.create("(?!\\x01)b").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside { it == 'b' }

    // then
    assert(lowest == ' ') { "Expected U+0020 but got $lowest" }
  }

  @Test
  fun `lowestCharOutside sees literals under a negative lookahead nested in another`() {
    // given
    val pattern = StringPattern.create("(?!a(?!\\x01))b").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside(printableAscii)

    // then
    assert(lowest == '\u0001') { "Expected U+0001 but got $lowest" }
  }

  @Test
  fun `lowestCharOutside draws a back-reference under a negative lookahead from printable ASCII`() {
    // given
    val pattern = StringPattern.create("(?!([^b])\\1)b").assertSuccess()

    // when
    val lowest = pattern.lowestCharOutside { it == 'b' }

    // then
    assert(lowest == ' ') { "Expected U+0020 but got $lowest" }
  }
}