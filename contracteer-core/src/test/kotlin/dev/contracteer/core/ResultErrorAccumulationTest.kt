package dev.contracteer.core

import org.junit.jupiter.api.Test
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success

class ResultErrorAccumulationTest {

  @Test
  fun `andThen returns failure when first succeeds and next fails`() {
    // when
    val result = success(1) andThen { failure<Any>("Wrong Type") }

    // then
    val errors = result.assertFailure()
    assert(errors.size == 1)
    assert(errors.first() == "Wrong Type")
  }

  @Test
  fun `andThen returns first errors when first fails and next succeeds`() {
    // when
    val result = failure<Int>("first error") andThen { success("John") }

    // then
    val errors = result.assertFailure()
    assert(errors == listOf("first error"))
  }

  @Test
  fun `andThen accumulates errors from both when first and next fail`() {
    // when
    val result = failure<Int>("first error") andThen { failure<String>("second error") }

    // then
    val errors = result.assertFailure()
    assert(errors == listOf("first error", "second error"))
  }

  @Test
  fun `combineWith succeeds when both results succeed`() {
    // when
    val result = success(1) combineWith success(2)

    // then
    assert(result.isSuccess())
  }

  @Test
  fun `combineWith returns errors from second when first succeeds`() {
    // when
    val result = success(1) combineWith failure<Any>("second error")

    // then
    val errors = result.assertFailure()
    assert(errors == listOf("second error"))
  }

  @Test
  fun `combineWith returns errors from first when second succeeds`() {
    // when
    val result = failure<Int>("first error") combineWith success(1)

    // then
    val errors = result.assertFailure()
    assert(errors == listOf("first error"))
  }

  @Test
  fun `combineWith accumulates errors from both results when both fail`() {
    // when
    val result = failure<Int>("first error") combineWith failure<Any>("second error")

    // then
    val errors = result.assertFailure()
    assert(errors == listOf("first error", "second error"))
  }

  // -- Error accumulation cap --

  @Test
  fun `accumulate keeps 25 errors and reports how many were truncated`() {
    // when
    val result = (1..30).toList().accumulate { failure<Int>("error $it") }

    // then
    val errors = result.assertFailure()
    assert(errors.size == 26)
    assert(errors[24] == "error 25")
    assert(errors.last() == "5 additional errors were truncated")
  }

  @Test
  fun `accumulate reports a single truncated error in the singular`() {
    // when
    val result = (1..26).toList().accumulate { failure<Int>("error $it") }

    // then
    val errors = result.assertFailure()
    assert(errors.last() == "1 additional error was truncated")
  }

  @Test
  fun `accumulate preserves all errors when under the cap`() {
    // when
    val result = (1..24).toList().accumulate { failure<Int>("error $it") }

    // then
    val errors = result.assertFailure()
    assert(errors.size == 24)
  }

  @Test
  fun `andThen keeps 25 errors and reports how many were truncated`() {
    // given
    val first = (1..15).toList().accumulate { failure<Int>("error $it") }

    // when
    val result = first andThen { (16..40).toList().accumulate { failure<Int>("error $it") } }

    // then
    val errors = result.assertFailure()
    assert(errors.size == 26)
    assert(errors.last() == "15 additional errors were truncated")
  }

  @Test
  fun `combineWith sums the truncated counts of both results`() {
    // given
    val first = (1..30).toList().accumulate { failure<Int>("first $it") }
    val second = (1..30).toList().accumulate { failure<Int>("second $it") }

    // when
    val result = first combineWith second

    // then
    val errors = result.assertFailure()
    assert(errors.size == 26)
    assert(errors.last() == "35 additional errors were truncated")
  }

  @Test
  fun `forProperty keeps the truncated count and renders it once without a path`() {
    // given
    val truncated = (1..30).toList().accumulate { failure<Int>("error $it") }

    // when
    val result = truncated.forProperty("items")

    // then
    val errors = result.assertFailure()
    assert(errors.size == 26)
    assert(errors.first() == "'items': error 1")
    assert(errors.last() == "5 additional errors were truncated")
  }

  @Test
  fun `mapErrors keeps the truncated count and does not transform the truncation line`() {
    // given
    val truncated = (1..30).toList().accumulate { failure<Int>("error $it") }

    // when
    val result = truncated.mapErrors { "GET /items: $it" }

    // then
    val errors = result.assertFailure()
    assert(errors.size == 26)
    assert(errors.first() == "GET /items: error 1")
    assert(errors.last() == "5 additional errors were truncated")
  }

  @Test
  fun `forOperation keeps the truncated count and renders the truncation line without the operation`() {
    // given
    val truncated = (1..30).toList().accumulate { failure<Int>("error $it") }

    // when
    val result = truncated.forOperation(OperationRef("GET", "/items"))

    // then
    val errors = result.assertFailure()
    assert(errors.size == 26)
    assert(errors.first() == "GET /items: error 1")
    assert(errors.last() == "5 additional errors were truncated")
  }
}
