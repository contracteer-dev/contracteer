package dev.contracteer.core.swagger

import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Severity.ERROR
import dev.contracteer.core.operation.ParameterElement.QueryParam
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertIs

class PathItemReferenceTest {

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = ["component_ref_31.yaml", "component_ref_chain_31.yaml", "ref_with_allowed_siblings_31.yaml"])
  fun `loads the operations of a path item that references a path item component`(fixture: String) {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/path_item_ref/$fixture")

    // then
    assertIs<LoadReport.Loaded>(report, report.diagnostics.toString())
    assert(report.operations.map { it.method to it.path } == listOf("GET" to "/products")) { report.operations.toString() }
    assert(report.diagnostics.isEmpty()) { report.diagnostics.toString() }
  }

  @Test
  fun `loads the operations of a path item that references another path in OpenAPI 3 0`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/path_item_ref/paths_ref_30.yaml")

    // then
    assertIs<LoadReport.Loaded>(report, report.diagnostics.toString())
    val operations = report.operations.map { it.method to it.path }
    assert(operations == listOf("GET" to "/products", "GET" to "/items")) { operations.toString() }
  }

  @Test
  fun `loads each path that references the same path item component with the component parameters`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/path_item_ref/component_shared_by_two_paths_31.yaml")

    // then
    assertIs<LoadReport.Loaded>(report, report.diagnostics.toString())
    val queryParameters = report.operations.associate { it.path to it.requestSchema.queryParameters.map { parameter -> parameter.element } }
    assert(queryParameters == mapOf(
      "/products" to listOf(QueryParam("q"), QueryParam("limit")),
      "/items" to listOf(QueryParam("q"), QueryParam("limit"))
    )) { queryParameters.toString() }
  }

  @Test
  fun `rejects a path item reference the parser leaves unresolved and names the path`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/path_item_ref/pointer_outside_paths_30.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(Diagnostic(
      message = $$"$ref '#/x-shared/products': cannot resolve JSON Pointer — a path item reference must point to '#/paths/<path>' or, in OpenAPI 3.1, to '#/components/pathItems/<name>'",
      location = "/products",
      keyword = $$"$ref",
      rule = UNSUPPORTED,
      category = SPEC,
      severity = ERROR
    ))) { report.diagnostics.toString() }
  }

  @Test
  fun `rejects each field beside a path item reference in OpenAPI 3 1 except summary description and servers`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/path_item_ref/siblings_31.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(siblingOfReference("/products", "parameters"), siblingOfReference("/products", "post"))) {
      report.diagnostics.toString()
    }
  }

  @Test
  fun `rejects an operation beside a path item reference in OpenAPI 3 0`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/path_item_ref/siblings_30.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(siblingOfReference("/items", "post"))) { report.diagnostics.toString() }
  }

  @Test
  fun `rejects a field beside a reference inside a path item component`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/path_item_ref/component_siblings_31.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(siblingOfReference("components/pathItems/Alias", "post"))) {
      report.diagnostics.toString()
    }
  }

  @Test
  fun `rejects a path item reference that is not a string`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/path_item_ref/non_string_ref_30.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(Diagnostic(
      message = $$"A path item '$ref' must be a string.",
      location = "/products",
      keyword = $$"$ref",
      rule = UNSUPPORTED,
      category = SPEC,
      severity = ERROR
    ))) { report.diagnostics.toString() }
  }

  private fun siblingOfReference(path: String, field: String) =
    Diagnostic(
      message = $$"'$$field' beside a path item '$ref' is not supported in Contracteer. Declare it in the referenced path item instead.",
      location = path,
      keyword = field,
      rule = UNSUPPORTED,
      category = SPEC,
      severity = ERROR
    )
}
