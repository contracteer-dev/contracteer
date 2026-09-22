package dev.contracteer.core.swagger

import dev.contracteer.core.assertSuccess
import kotlin.test.Test

class UnsupportedOperationFilterTest {

  @Test
  fun `excludes operation with XML-only responses`() {
    // when
    val result = loadResult("xml_schema.yaml")

    // then
    val operations = result.assertSuccess()
    assert(operations.isEmpty())
  }

  @Test
  fun `filters scenarios with XML content types`() {
    // when
    val result = loadResult("xml_scenarios.yaml")

    // then
    val operations = result.assertSuccess()
    val scenarios = operations.first().scenarios
    assert(operations.size == 1)
    assert(scenarios.all { it.response.body?.contentType?.isXml() != true })
    assert(scenarios.isNotEmpty())
  }

  @Test
  fun `excludes operations with null or empty schema on response body`() {
    // when
    val result = loadResult("schemaless_response_body.yaml")

    // then
    val operations = result.assertSuccess()
    assert(operations.isEmpty())
  }

  @Test
  fun `excludes operations with null or empty schema on request body`() {
    // when
    val result = loadResult("schemaless_request_body.yaml")

    // then
    val operations = result.assertSuccess()
    assert(operations.isEmpty())
  }

  @Test
  fun `filters scenarios targeting responses with unsupported body schemas`() {
    // when
    val result = loadResult("schemaless_response_scenario.yaml")

    // then
    val operations = result.assertSuccess()
    assert(operations.size == 1)
    assert(operations.first().scenarios.size == 1)
    assert(operations.first().scenarios.first().key == "good_scenario")
  }

  @Test
  fun `excludes operation whose only class response has an XML-only body`() {
    // when
    val result = loadResult("xml_class_response.yaml")

    // then
    val operations = result.assertSuccess()
    assert(operations.isEmpty())
  }

  @Test
  fun `excludes operations with null or empty schema on parameter content`() {
    // when
    val result = loadResult("schemaless_parameter_content.yaml")

    // then
    val operations = result.assertSuccess()
    assert(operations.isEmpty())
  }

  // --- Helpers ---

  private fun loadResult(yamlFile: String) =
    OpenApiLoader.loadOperations("src/test/resources/operation/unsupported/$yamlFile")
}
