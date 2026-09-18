package dev.contracteer.core.swagger

import dev.contracteer.core.assertFailure
import kotlin.test.Test

class ExtractionErrorTest {

  @Test
  fun `fails when file does not exist`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/not_found.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.first().contains("file not found"))
  }

  // Characterization: OAS 3.1 does not require `responses` on the Operation Object, but
  // swagger-parser reports it missing and Contracteer treats every parser message as fatal.
  @Test
  fun `fails when a 3 1 operation declares no responses`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/no_responses_31.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("responses is missing") }) { "Expected error about missing responses but got: $errors" }
  }

  @Test
  fun `fails when path parameter is not required`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/path_parameter_required_error.yaml")

    // then
    result.assertFailure()
  }

  @Test
  fun `fails when response 204 declares a body`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/bodyless_204_with_body.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("204") && it.contains("MUST NOT") }) { "Expected error about 204 bodyless but got: $errors" }
  }

  @Test
  fun `fails when response 205 declares a body`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/bodyless_205_with_body.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("205") && it.contains("MUST NOT") }) { "Expected error about 205 bodyless but got: $errors" }
  }

  @Test
  fun `fails when response 304 declares a body`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/bodyless_304_with_body.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("304") && it.contains("MUST NOT") }) { "Expected error about 304 bodyless but got: $errors" }
  }

  @Test
  fun `fails when 1xx response declares a body`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/bodyless_1xx_with_body.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("100") && it.contains("MUST NOT") }) { "Expected error about 1xx bodyless but got: $errors" }
  }

  @Test
  fun `fails when HEAD response declares a body`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/head_with_response_body.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("HEAD") && it.contains("MUST NOT") }) { "Expected error about HEAD bodyless but got: $errors" }
  }

  @Test
  fun `fails when paths are equivalent (differ only in parameter names)`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/equivalent_paths.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("/resources/{resourceId}/items") && it.contains("/resources/{parentId}/items")
    })
  }

  @Test
  fun `fails when request header parameter has blank name`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/blank_header_parameter_name.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("blank name") && it.contains("header") })
  }

  @Test
  fun `fails when query parameter has blank name`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/blank_query_parameter_name.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("blank name") && it.contains("query") })
  }

  @Test
  fun `fails when path parameter has blank name`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/blank_path_parameter_name.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("blank name") && it.contains("path") })
  }

  @Test
  fun `fails when cookie parameter has blank name`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/blank_cookie_parameter_name.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("blank name") && it.contains("cookie") })
  }

  @Test
  fun `fails when response header has blank name`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/blank_response_header_name.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("blank name") && it.contains("Response header") })
  }

  @Test
  fun `fails when non 400 scenario example violates schema`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/invalid_non_400_examples.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.size == 5) { "Expected 5 validation errors but got ${errors.size}: $errors" }
    assert(errors.any { it.contains("'request.body.examples[CREATE_PRODUCT].id'") }) { errors.toString() }
    assert(errors.any { it.contains("'request.path[id].examples[NOT_FOUND]'") }) { errors.toString() }
    assert(errors.any { it.contains("'response.body.examples[GET_DETAILS].quantity'") }) { errors.toString() }
    assert(errors.any { it.contains("'response.header[location].examples[ASYNC]'") }) { errors.toString() }
    assert(errors.any { it.contains("'response.body.examples[NOT_FOUND].error'") }) { errors.toString() }
  }

  @Test
  fun `uses the singular example keyword in the error path and does not leak the internal key`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/single_example_invalid.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("'request.path[id].example'") }) { errors.toString() }
    assert(errors.none { it.contains("_example") }) { errors.toString() }
  }

  @Test
  fun `fails when request header pattern admits HTTP-invalid chars`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/request_header_pattern_with_invalid_chars.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("X-Custom-Attributes") && it.contains("RFC 7230") })
  }

  @Test
  fun `fails when response header pattern admits HTTP-invalid chars`() {
    // when
    val result = OpenApiLoader.loadOperations(
      "src/test/resources/error/response_header_pattern_with_invalid_chars.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("X-Custom-Attributes") && it.contains("RFC 7230") })
  }

  @Test
  fun `fails when header example contains HTTP-invalid chars`() {
    // when
    val result = OpenApiLoader.loadOperations(
      "src/test/resources/error/request_header_example_with_invalid_chars.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("X-Custom-Attributes") && it.contains("RFC 7230") })
  }

  @Test
  fun `fails when header enum value contains HTTP-invalid chars`() {
    // when
    val result = OpenApiLoader.loadOperations(
      "src/test/resources/error/request_header_enum_with_invalid_chars.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("X-Custom-Attributes") && it.contains("RFC 7230") })
  }

  @Test
  fun `fails when request body schema is standalone type null`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/null_request_body.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("standalone 'type: null'") && it.contains("anyOf") }) {
      "Expected standalone null type error but got: $errors"
    }
  }

  @Test
  fun `fails when query parameter schema is standalone type null`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/null_query_parameter.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("standalone 'type: null'") && it.contains("anyOf") }) {
      "Expected standalone null type error but got: $errors"
    }
  }

  @Test
  fun `fails when request header parameter schema is standalone type null`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/null_request_header.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("standalone 'type: null'") && it.contains("in: header") }) {
      "Expected standalone null type error mentioning 'in: header' but got: $errors"
    }
  }

  @Test
  fun `fails when query parameter content schema is standalone type null`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/null_query_parameter_content.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("standalone 'type: null'") && it.contains("anyOf") }) {
      "Expected standalone null type error but got: $errors"
    }
  }

  @Test
  fun `fails when response header schema is standalone type null`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/null_response_header.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it.contains("standalone 'type: null'") && it.contains("anyOf") }) {
      "Expected standalone null type error but got: $errors"
    }
  }

  @Test
  fun `fails when anyOf outer type constrains non-null but a branch declares type null`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/anyof_outer_type_conflicts_with_null_branch.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("outer 'type: [object]'") &&
      it.contains("'anyOf' branch '#1'") &&
      it.contains("Either add 'null' to the outer type array or remove the null branch")
    }) { "Expected anyOf outer-type conflict error but got: $errors" }
  }

  @Test
  fun `fails when oneOf outer type constrains non-null but a branch declares type null`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/oneof_outer_type_conflicts_with_null_branch.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("outer 'type: [string]'") &&
      it.contains("'oneOf' branch '#1'") &&
      it.contains("Either add 'null' to the outer type array or remove the null branch")
    }) { "Expected oneOf outer-type conflict error but got: $errors" }
  }

  @Test
  fun `fails when allOf includes a type null branch`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/allof_with_null_branch.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("'allOf' includes a 'type: null' branch (#1)") &&
      it.contains("unsatisfiable")
    }) { "Expected allOf null-branch unsatisfiable error but got: $errors" }
  }

  @Test
  fun `fails with a clear message when a JSON Pointer segment names an unknown Schema field`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/ref_unknown_segment.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("\$ref '#/components/schemas/Person/foo': cannot resolve JSON Pointer") &&
      it.contains("Schema has no field 'foo'")
    })
  }

  @Test
  fun `fails with a clear message when a JSON Pointer targets a missing property`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/ref_missing_property_target.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("\$ref '#/components/schemas/Person/properties/missing': cannot resolve JSON Pointer") &&
      it.contains("no 'properties' entry 'missing'")
    })
  }

  @Test
  fun `fails with a clear message when a JSON Pointer targets a non-Schema location`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/ref_target_not_a_schema.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("\$ref '#/components/parameters/queryFilter': cannot resolve JSON Pointer") &&
      it.contains("target is not a Schema")
    })
  }

  @Test
  fun `fails with a clear message when a JSON Pointer targets a boolean additionalProperties`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/ref_into_boolean_additional_properties.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("\$ref '#/components/schemas/OpenBag/additionalProperties': cannot resolve JSON Pointer") &&
      it.contains("'additionalProperties' is a boolean, not a sub-schema")
    })
  }

  @Test
  fun `fails with a clear message when a JSON Pointer descends into 'definitions'`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/ref_into_definitions.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains("\$ref '#/components/schemas/Container/definitions/Inner': cannot resolve JSON Pointer") &&
      it.contains("segment 'definitions' is not supported in Contracteer")
    })
  }

  @Test
  fun `fails with a clear message when a JSON Pointer targets an unsupported components section`() {
    // when
    val result = OpenApiLoader.loadOperations("src/test/resources/error/ref_into_unsupported_section.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any {
      it.contains($$"$ref '#/components/requestBodies/Foo/content/application~1json/schema': cannot resolve JSON Pointer") &&
      it.contains("section 'requestBodies' is not supported in Contracteer")
    })
  }
}
