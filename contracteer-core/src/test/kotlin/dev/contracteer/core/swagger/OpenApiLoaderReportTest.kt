package dev.contracteer.core.swagger

import dev.contracteer.core.DiagnosticCategory.EXECUTION_ERROR
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.OperationRef
import dev.contracteer.core.Severity.ERROR
import kotlin.test.Test
import kotlin.test.assertIs

class OpenApiLoaderReportTest {

  @Test
  fun `valid document loads with its operations and no diagnostics`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/default_response.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.source == "src/test/resources/operation/default_response.yaml")
    assert(loaded.operations.map { it.path } == listOf("/products/{id}"))
    assert(loaded.diagnostics.isEmpty()) { loaded.diagnostics.toString() }
    assert(loaded.truncated == 0)
  }

  @Test
  fun `extraction error fails the load as a spec error carrying its operation`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/blank_header_parameter_name.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics.isNotEmpty())
    assert(report.diagnostics.all { it.category == SPEC && it.severity == ERROR }) { report.diagnostics.toString() }
    assert(report.diagnostics.all { it.operation == OperationRef("GET", "/v1/pages/{id}") }) { report.diagnostics.toString() }
  }

  @Test
  fun `unsupported OpenAPI version fails the load as a spec error`() {
    // when
    val report = OpenApiLoader.load("classpath:error/openapi_32_unsupported.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics.map { it.category to it.severity } == listOf(SPEC to ERROR)) { report.diagnostics.toString() }
  }

  @Test
  fun `missing document fails the load as an execution error`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/not_found.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics.map { it.category to it.severity } == listOf(EXECUTION_ERROR to ERROR)) { report.diagnostics.toString() }
  }

  @Test
  fun `failed load keeps 25 diagnostics and reports how many were truncated`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/thirty_blank_query_parameter_names.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics.size == 25) { report.diagnostics.size.toString() }
    assert(report.truncated == 5)
  }
}
