package dev.contracteer.core.swagger

import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.DiagnosticRule
import dev.contracteer.core.DiagnosticRule.BODY_EXCLUDED
import dev.contracteer.core.DiagnosticRule.OPERATION_EXCLUDED
import dev.contracteer.core.DiagnosticRule.SCENARIO_EXCLUDED
import dev.contracteer.core.DiagnosticRule.UNKNOWN_FORMAT
import dev.contracteer.core.DiagnosticRule.UNRESOLVED_EXAMPLE_KEY
import dev.contracteer.core.OperationRef
import dev.contracteer.core.Severity.ERROR
import dev.contracteer.core.Severity.WARNING
import kotlin.test.Test
import kotlin.test.assertIs

class OpenApiLoaderWarningsTest {

  @Test
  fun `operation excluded for unsupported response content is reported as a spec warning carrying its operation`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/unsupported/xml_schema.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(OPERATION_EXCLUDED, "Operation excluded: no supported response content type.", "GET", "/products")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `operation excluded for parameter content without schema is reported as a spec warning carrying its operation`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/unsupported/schemaless_parameter_content.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics.filter { it.operation != null } == listOf(
      operationWarning(OPERATION_EXCLUDED, "Operation excluded: parameter content has no schema.", "GET", "/null-schema"),
      operationWarning(OPERATION_EXCLUDED, "Operation excluded: parameter content has no schema.", "GET", "/empty-schema")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `operation excluded for unsupported request body content is reported without a warning for each body`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/unsupported/schemaless_request_body.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics.filter { it.operation != null } == listOf(
      operationWarning(OPERATION_EXCLUDED, "Operation excluded: no supported request body content type.", "POST", "/null-schema"),
      operationWarning(OPERATION_EXCLUDED, "Operation excluded: no supported request body content type.", "POST", "/empty-schema")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `constraints ignored because a format takes precedence are reported as spec warnings`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/format_precedence.yaml")

    // then
    val warnings = assertSchemaWarnings(report)
    assert(warnings.sorted() == listOf(
      "Schema 'Binary': 'pattern' ignored because 'format: binary' takes precedence.",
      "Schema 'Byte': 'pattern' ignored because 'format: byte' takes precedence.",
      "Schema 'Date': 'maxLength' ignored because 'format: date' takes precedence.",
      "Schema 'Date': 'pattern' ignored because 'format: date' takes precedence.",
      "Schema 'DateTime': 'maxLength' ignored because 'format: date-time' takes precedence.",
      "Schema 'DateTime': 'pattern' ignored because 'format: date-time' takes precedence.",
      "Schema 'Email': 'pattern' ignored because 'format: email' takes precedence.",
      "Schema 'Hostname': 'maxLength' ignored because 'format: hostname' takes precedence.",
      "Schema 'Hostname': 'pattern' ignored because 'format: hostname' takes precedence.",
      "Schema 'Uri': 'maxLength' ignored because 'format: uri' takes precedence.",
      "Schema 'Uri': 'pattern' ignored because 'format: uri' takes precedence.",
      "Schema 'UriReference': 'maxLength' ignored because 'format: uri-reference' takes precedence.",
      "Schema 'UriReference': 'pattern' ignored because 'format: uri-reference' takes precedence.",
      "Schema 'Uuid': 'maxLength' ignored because 'format: uuid' takes precedence.",
      "Schema 'Uuid': 'minLength' ignored because 'format: uuid' takes precedence.",
      "Schema 'Uuid': 'pattern' ignored because 'format: uuid' takes precedence."
    )) { warnings.toString() }
  }

  @Test
  fun `unknown numeric formats are reported as spec warnings`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/unknown_format.yaml")

    // then
    val warnings = assertSchemaWarnings(report)
    assert(warnings.sorted() == listOf(
      "Schema 'Count': unknown format 'int128' for integer type is ignored.",
      "Schema 'Ratio': unknown format 'decimal128' for number type is ignored."
    )) { warnings.toString() }
  }

  @Test
  fun `length constraints ignored because a pattern takes precedence are reported as spec warnings`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/pattern_precedence.yaml")

    // then
    val warnings = assertSchemaWarnings(report)
    assert(warnings.sorted() == listOf(
      "Schema 'Code': 'minLength' ignored because 'pattern' takes precedence.",
      "Schema 'Labels.propertyNames': 'maxLength' ignored because 'pattern' takes precedence."
    )) { warnings.toString() }
  }

  @Test
  fun `empty schema read as accepting any type is reported as a spec warning`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/empty_schema.yaml")

    // then
    val warnings = assertSchemaWarnings(report)
    assert(warnings == listOf(
      "Schema 'Payload' is empty (anyType) and will be interpreted as accepting any type."
    )) { warnings.toString() }
  }

  @Test
  fun `example key targeting an undefined status code is reported as a spec warning carrying its operation`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/unresolvable_example_key.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(
        UNRESOLVED_EXAMPLE_KEY,
        "Example key '404_missing' targets status code 404, but no response with that status code is defined. Key ignored.",
        "GET",
        "/products/{id}",
        keyword = "examples"
      )
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `failed load reports its warnings after its errors`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/failed_load_with_warning.yaml")

    // then
    val failed = assertIs<LoadReport.Failed>(report)
    val severities = failed.diagnostics.map { it.severity }
    assert(severities.first() == ERROR) { failed.diagnostics.toString() }
    assert(failed.diagnostics.last() ==
             Diagnostic("Schema '': unknown format 'int128' for integer type is ignored.", keyword = "format", rule = UNKNOWN_FORMAT, category = SPEC, severity = WARNING)
    ) { failed.diagnostics.toString() }
    assert(severities.count { it == WARNING } == 1) { failed.diagnostics.toString() }
  }

  @Test
  fun `identical warnings from different schemas are reported once`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/identical_warnings.yaml")

    // then
    val warnings = assertSchemaWarnings(report)
    assert(warnings == listOf(
      "Schema '': unknown format 'int128' for integer type is ignored."
    )) { warnings.toString() }
  }

  @Test
  fun `body excluded among supported ones and the scenario using it are reported as spec warnings carrying their operation`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/unsupported/xml_scenarios.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(BODY_EXCLUDED, "Response 200 body 'application/xml' excluded: XML content is not supported.", "GET", "/products"),
      operationWarning(SCENARIO_EXCLUDED, "Scenario 'xml_scenario' excluded: its response body 'application/xml' is excluded.", "GET", "/products")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `body without schema excluded among supported ones is reported as a spec warning carrying its operation`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/excluded_body_without_schema.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(BODY_EXCLUDED, "Response 200 body 'application/json' excluded: its schema is empty or missing.", "GET", "/products")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `response excluded for having no supported body and the scenario targeting it are reported as spec warnings`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/operation/unsupported/schemaless_response_scenario.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics.filter { it.operation != null } == listOf(
      operationWarning(BODY_EXCLUDED, "Response 201 excluded: no supported body.", "GET", "/test"),
      operationWarning(SCENARIO_EXCLUDED, "Scenario 'bad_scenario' excluded: response 201 is excluded.", "GET", "/test")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `request body excluded among supported ones is reported as a spec warning carrying its operation`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/excluded_request_body.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(BODY_EXCLUDED, "Request body 'application/xml' excluded: XML content is not supported.", "POST", "/products")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `class and default responses excluded for having no supported body are reported as spec warnings`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/excluded_class_and_default_responses.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(BODY_EXCLUDED, "Response 4XX excluded: no supported body.", "GET", "/products"),
      operationWarning(BODY_EXCLUDED, "Default response excluded: no supported body.", "GET", "/products")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `scenarios excluded under an example key shared across content types name the excluded body`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/same_example_key_across_content_types.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(BODY_EXCLUDED, "Request body 'application/xml' excluded: XML content is not supported.", "POST", "/products"),
      operationWarning(BODY_EXCLUDED, "Response 200 body 'application/xml' excluded: XML content is not supported.", "POST", "/products"),
      operationWarning(SCENARIO_EXCLUDED, "Scenario 'widget' excluded: its response body 'application/xml' is excluded.", "POST", "/products"),
      operationWarning(SCENARIO_EXCLUDED, "Scenario 'widget' excluded: its request body 'application/xml' is excluded.", "POST", "/products")
    )) { loaded.diagnostics.toString() }
  }

  @Test
  fun `scenarios targeting an excluded response name the declared response serving their status code`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/warning/scenarios_of_excluded_responses.yaml")

    // then
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics == listOf(
      operationWarning(BODY_EXCLUDED, "Response 404 excluded: no supported body.", "GET", "/products"),
      operationWarning(BODY_EXCLUDED, "Response 4XX excluded: no supported body.", "GET", "/products"),
      operationWarning(BODY_EXCLUDED, "Default response excluded: no supported body.", "GET", "/products"),
      operationWarning(SCENARIO_EXCLUDED, "Scenario '404_MISSING' excluded: response 404 is excluded.", "GET", "/products"),
      operationWarning(SCENARIO_EXCLUDED, "Scenario '418_TEAPOT' excluded: response 4XX is excluded.", "GET", "/products"),
      operationWarning(SCENARIO_EXCLUDED, "Scenario '503_DOWN' excluded: the default response is excluded.", "GET", "/products")
    )) { loaded.diagnostics.toString() }
  }

  private fun operationWarning(rule: DiagnosticRule, message: String, method: String, path: String, keyword: String? = null) =
    Diagnostic(message, keyword = keyword, rule = rule, operation = OperationRef(method, path), category = SPEC, severity = WARNING)

  private fun assertSchemaWarnings(report: LoadReport): List<String> {
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics.all { it.category == SPEC && it.severity == WARNING && it.operation == null }) {
      loaded.diagnostics.toString()
    }
    return loaded.diagnostics.map { it.message }
  }
}
