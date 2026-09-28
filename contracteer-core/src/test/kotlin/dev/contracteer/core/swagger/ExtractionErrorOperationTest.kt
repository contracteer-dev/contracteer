package dev.contracteer.core.swagger

import dev.contracteer.core.OperationRef
import dev.contracteer.core.assertFailure
import kotlin.test.Test

class ExtractionErrorOperationTest {

  @Test
  fun `parameter resolution error carries its operation`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/cyclic_operation_parameter_ref_31.yaml")

    // then
    result.assertFailure()
    assert(result.diagnostics().map { it.operation }.distinct() == listOf(OperationRef("GET", "/products"))) { result.diagnostics().toString() }
  }

  @Test
  fun `schema extraction error carries its operation`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/blank_header_parameter_name.yaml")

    // then
    result.assertFailure()
    assert(result.diagnostics().map { it.operation }.distinct() == listOf(OperationRef("GET", "/v1/pages/{id}"))) { result.diagnostics().toString() }
  }

  @Test
  fun `HEAD response validation error carries its operation`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/head_with_response_body.yaml")

    // then
    result.assertFailure()
    assert(result.diagnostics().map { it.operation }.distinct() == listOf(OperationRef("HEAD", "/resources/{id}"))) { result.diagnostics().toString() }
  }

  @Test
  fun `scenario error carries its operation and renders the operation before the property path`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/single_example_invalid.yaml")

    // then
    val errors = result.assertFailure()
    assert(result.diagnostics().map { it.operation }.distinct() == listOf(OperationRef("GET", "/items/{id}"))) { result.diagnostics().toString() }
    assert(errors.all { it.startsWith("GET /items/{id}: 'request.path[id].example': ") }) { errors.toString() }
  }
}
