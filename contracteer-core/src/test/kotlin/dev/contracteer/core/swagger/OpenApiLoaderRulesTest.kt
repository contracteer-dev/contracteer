package dev.contracteer.core.swagger

import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticRule.BODY_EXCLUDED
import dev.contracteer.core.DiagnosticRule.CONFLICTING_CONSTRAINTS
import dev.contracteer.core.DiagnosticRule.EMPTY_SCHEMA
import dev.contracteer.core.DiagnosticRule.INFINITE_CYCLE
import dev.contracteer.core.DiagnosticRule.OPERATION_EXCLUDED
import dev.contracteer.core.DiagnosticRule.PATTERN_UNCERTIFIABLE
import dev.contracteer.core.DiagnosticRule.SCENARIO_EXCLUDED
import dev.contracteer.core.DiagnosticRule.UNKNOWN_FORMAT
import dev.contracteer.core.DiagnosticRule.UNRESOLVED_EXAMPLE_KEY
import dev.contracteer.core.DiagnosticRule.UNSERIALIZABLE_CONTENT
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Severity.ERROR
import dev.contracteer.core.Severity.WARNING
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertIs

class OpenApiLoaderRulesTest {

  @ParameterizedTest(name = "{0} is about keyword {1}")
  @MethodSource("unsupportedSchemaFixtures")
  fun `unsupported schema is reported as unsupported with the keyword it is about`(fixture: String, keyword: String?) {
    // when
    val report = OpenApiLoader.load("src/test/resources/datatype/$fixture")

    // then
    val errors = report.errors()
    assert(errors.map { it.rule to it.keyword } == listOf(UNSUPPORTED to keyword)) { report.diagnostics.toString() }
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = ["ref_into_unsupported_section.yaml", "ref_into_definitions.yaml"])
  fun `unsupported reference is reported as unsupported with the ref keyword`(fixture: String) {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/$fixture")

    // then
    val errors = report.errors()
    assert(errors.map { it.rule to it.keyword } == listOf(UNSUPPORTED to $$"$ref")) { report.diagnostics.toString() }
  }

  @Test
  fun `each unsupported sibling of a reference is reported as unsupported with its keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/datatype/3.1/ref_with_sibling_several_unsupported_keywords_error.yaml")

    // then
    val errors = report.errors()
    assert(errors.map { it.rule to it.keyword } == listOf(UNSUPPORTED to "not", UNSUPPORTED to "if")) { report.diagnostics.toString() }
    assert(errors.map { it.message } == listOf(
      $$"Schema 'Name': sibling 'not' on '$ref' is not supported.",
      $$"Schema 'Name': sibling 'if/then/else' on '$ref' is not supported."
    )) { report.diagnostics.toString() }
  }

  @Test
  fun `multi-type sibling of a reference is reported as unsupported with the type keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/datatype/3.1/ref_with_sibling_multi_type_error.yaml")

    // then
    val errors = report.errors()
    assert(errors.map { it.rule to it.keyword } == listOf(UNSUPPORTED to "type")) { report.diagnostics.toString() }
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = [
    "text_plain_composite_array_body.yaml",
    "form_urlencoded/form_urlencoded_invalid_schema.yaml",
    "form_urlencoded/form_urlencoded_nested_types.yaml",
    "multipart/multipart_invalid_schema.yaml",
    "multipart/multipart_kind_divergent_no_ct.yaml"
  ])
  fun `content type that cannot serialize its schema is reported as unserializable content without a keyword`(fixture: String) {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/$fixture")

    // then
    val errors = report.errors()
    assert(errors.map { it.rule to it.keyword }.distinct() == listOf(UNSERIALIZABLE_CONTENT to null)) { report.diagnostics.toString() }
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = ["circular_reference_infinite.yaml", "circular_reference_infinite_allof.yaml"])
  fun `circular reference without an exit point is reported as an infinite cycle with the ref keyword`(fixture: String) {
    // when
    val report = OpenApiLoader.load("src/test/resources/datatype/3.0/$fixture")

    // then
    val errors = report.errors()
    assert(errors.map { it.rule to it.keyword } == listOf(INFINITE_CYCLE to $$"$ref")) { report.diagnostics.toString() }
  }

  @Test
  fun `pattern without a value generator is reported as uncertifiable with the pattern keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/datatype/3.0/string_pattern_uncertifiable_error.yaml")

    // then
    val errors = report.errors()
    assert(errors.map { it.rule to it.keyword } == listOf(PATTERN_UNCERTIFIABLE to "pattern")) { report.diagnostics.toString() }
  }

  @Test
  fun `operation excluded by the filter is reported as operation excluded without a keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/unsupported/xml_schema.yaml")

    // then
    val warnings = report.warnings()
    assert(warnings.map { it.rule to it.keyword } == listOf(OPERATION_EXCLUDED to null)) { report.diagnostics.toString() }
  }

  @Test
  fun `body and scenario excluded by the filter are reported as body excluded and scenario excluded without a keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/unsupported/xml_scenarios.yaml")

    // then
    val warnings = report.warnings()
    assert(warnings.map { it.rule to it.keyword } == listOf(BODY_EXCLUDED to null, SCENARIO_EXCLUDED to null)) { report.diagnostics.toString() }
  }

  @Test
  fun `response excluded by the filter is reported as body excluded without a keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/excluded_class_and_default_responses.yaml")

    // then
    val warnings = report.warnings()
    assert(warnings.map { it.rule to it.keyword }.distinct() == listOf(BODY_EXCLUDED to null)) { report.diagnostics.toString() }
  }

  @Test
  fun `constraints a format takes precedence over are reported as conflicting constraints with their own keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/format_precedence.yaml")

    // then
    val warnings = report.warnings().filter { it.message.startsWith("Schema 'Uuid'") }
    assert(warnings.map { it.rule to it.keyword } == listOf(
      CONFLICTING_CONSTRAINTS to "pattern",
      CONFLICTING_CONSTRAINTS to "minLength",
      CONFLICTING_CONSTRAINTS to "maxLength"
    )) { report.diagnostics.toString() }
  }

  @Test
  fun `length constraints a pattern takes precedence over are reported as conflicting constraints with their own keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/pattern_precedence.yaml")

    // then
    val warnings = report.warnings()
    assert(warnings.map { it.rule to it.keyword } == listOf(
      CONFLICTING_CONSTRAINTS to "minLength",
      CONFLICTING_CONSTRAINTS to "maxLength"
    )) { report.diagnostics.toString() }
  }

  @Test
  fun `unknown numeric format is reported as unknown format with the format keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/unknown_format.yaml")

    // then
    val warnings = report.warnings()
    assert(warnings.map { it.rule to it.keyword }.distinct() == listOf(UNKNOWN_FORMAT to "format")) { report.diagnostics.toString() }
  }

  @Test
  fun `empty schema is reported as empty schema without a keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/empty_schema.yaml")

    // then
    val warnings = report.warnings()
    assert(warnings.map { it.rule to it.keyword } == listOf(EMPTY_SCHEMA to null)) { report.diagnostics.toString() }
  }

  @Test
  fun `example key targeting an undefined status code is reported as unresolved example key with the examples keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/unresolvable_example_key.yaml")

    // then
    val warnings = report.warnings()
    assert(warnings.map { it.rule to it.keyword } == listOf(UNRESOLVED_EXAMPLE_KEY to "examples")) { report.diagnostics.toString() }
  }

  private fun LoadReport.errors(): List<Diagnostic> {
    assertIs<LoadReport.Failed>(this)
    return diagnostics.filter { it.severity == ERROR }
  }

  private fun LoadReport.warnings(): List<Diagnostic> {
    assertIs<LoadReport.Loaded>(this)
    return diagnostics.filter { it.severity == WARNING }
  }

  companion object {
    @JvmStatic
    fun unsupportedSchemaFixtures() = listOf(
      Arguments.of("3.1/prefix_items_error.yaml",                         "prefixItems"),
      Arguments.of("3.1/not_error.yaml",                                  "not"),
      Arguments.of("3.1/not_alone_error.yaml",                            "not"),
      Arguments.of("3.0/not_error.yaml",                                  "not"),
      Arguments.of("3.1/unevaluated_properties_error.yaml",               "unevaluatedProperties"),
      Arguments.of("3.1/unevaluated_items_error.yaml",                    "unevaluatedItems"),
      Arguments.of("3.1/pattern_properties_error.yaml",                   "patternProperties"),
      Arguments.of("3.1/dependent_required_error.yaml",                   "dependentRequired"),
      Arguments.of("3.1/dependent_schemas_error.yaml",                    "dependentSchemas"),
      Arguments.of("3.1/content_schema_error.yaml",                       "contentSchema"),
      Arguments.of("3.1/contains_error.yaml",                             "contains"),
      Arguments.of("3.1/if_then_else_error.yaml",                         "if"),
      Arguments.of("3.1/multi_type_non_nullable_error.yaml",              "type"),
      Arguments.of("3.1/boolean_true_schema_error.yaml",                  null),
      Arguments.of("3.1/non_base64_content_encoding_error.yaml",          "contentEncoding"),
      Arguments.of("3.1/structured_text_content_media_type_error.yaml",   "contentMediaType"),
    )
  }
}
