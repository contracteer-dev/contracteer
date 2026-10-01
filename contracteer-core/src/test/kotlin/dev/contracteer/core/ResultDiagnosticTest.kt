package dev.contracteer.core

import dev.contracteer.core.DiagnosticCategory.CONTRACT_VIOLATION
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.Result.Companion.failure
import dev.contracteer.core.Result.Companion.success
import dev.contracteer.core.Severity.ERROR
import dev.contracteer.core.Severity.WARNING
import org.junit.jupiter.api.Test

class ResultDiagnosticTest {

  @Test
  fun `failure with a message produces a freeform diagnostic`() {
    // when
    val diagnostics = failure<Any>("Wrong Type").diagnostics()

    // then
    assert(diagnostics == listOf(Diagnostic("Wrong Type")))
  }

  @Test
  fun `failure with a diagnostic keeps it and renders its message`() {
    // given
    val diagnostic = Diagnostic("Array has 5 items but maxItems is 3", keyword = "maxItems")

    // when
    val result = failure<Any>(diagnostic)

    // then
    assert(result.diagnostics() == listOf(diagnostic))
    assert(result.errors() == listOf("Array has 5 items but maxItems is 3"))
  }

  @Test
  fun `diagnostics is empty on success`() {
    // when
    val diagnostics = success(1).diagnostics()

    // then
    assert(diagnostics.isEmpty())
  }

  @Test
  fun `property path prefixes locate the diagnostic`() {
    // given
    val diagnostic = Diagnostic("Array has 5 items but maxItems is 3", keyword = "maxItems")

    // when
    val result = failure<Any>(diagnostic).forKey("tags").forIndex(0).forProperty("items")

    // then
    assert(result.diagnostics() == listOf(diagnostic.copy(location = "items[0][tags]")))
    assert(result.errors() == listOf("'items[0][tags]': Array has 5 items but maxItems is 3"))
  }

  @Test
  fun `forOperation sets the operation on every diagnostic and prefixes the rendered errors`() {
    // given
    val failure = failure<Any>("error 1") combineWith failure<Any>("name", "error 2")

    // when
    val result = failure.forOperation(OperationRef("GET", "/items"))

    // then
    assert(result.diagnostics().map { it.operation } == listOf(OperationRef("GET", "/items"), OperationRef("GET", "/items")))
    assert(result.errors() == listOf("GET /items: error 1", "GET /items: 'name': error 2"))
  }

  @Test
  fun `withDefaults sets the category and severity a diagnostic lacks and keeps the assigned ones`() {
    // given
    val unassigned = Diagnostic("error 1")
    val assigned = Diagnostic("error 2", category = SPEC, severity = WARNING)
    val categoryOnly = Diagnostic("error 3", category = SPEC)
    val failure = failure<Any>(unassigned) combineWith failure<Any>(assigned) combineWith failure<Any>(categoryOnly)

    // when
    val result = failure.withDefaults(CONTRACT_VIOLATION, ERROR)

    // then
    assert(result.diagnostics() == listOf(
      unassigned.copy(category = CONTRACT_VIOLATION, severity = ERROR),
      assigned,
      categoryOnly.copy(severity = ERROR)
    ))
  }

  @Test
  fun `mapErrors keeps the operation`() {
    // given
    val failure = failure<Any>("error").forOperation(OperationRef("GET", "/items"))

    // when
    val result = failure.mapErrors { "Request body: $it" }

    // then
    assert(result.diagnostics().single().operation == OperationRef("GET", "/items"))
    assert(result.errors() == listOf("GET /items: Request body: error"))
  }
}
