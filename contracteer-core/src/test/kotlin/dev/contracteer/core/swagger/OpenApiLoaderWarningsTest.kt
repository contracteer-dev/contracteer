package dev.contracteer.core.swagger

import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticCategory.SPEC
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
      Diagnostic("Operation excluded: no supported response content type.",
                 operation = OperationRef("GET", "/products"),
                 category = SPEC,
                 severity = WARNING)
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
      "Schema 'Date': 'minLength'/'maxLength' ignored because 'format: date' takes precedence.",
      "Schema 'Date': 'pattern' ignored because 'format: date' takes precedence.",
      "Schema 'DateTime': 'minLength'/'maxLength' ignored because 'format: date-time' takes precedence.",
      "Schema 'DateTime': 'pattern' ignored because 'format: date-time' takes precedence.",
      "Schema 'Email': 'pattern' ignored because 'format: email' takes precedence.",
      "Schema 'Hostname': 'minLength'/'maxLength' ignored because 'format: hostname' takes precedence.",
      "Schema 'Hostname': 'pattern' ignored because 'format: hostname' takes precedence.",
      "Schema 'Uri': 'minLength'/'maxLength' ignored because 'format: uri' takes precedence.",
      "Schema 'Uri': 'pattern' ignored because 'format: uri' takes precedence.",
      "Schema 'UriReference': 'minLength'/'maxLength' ignored because 'format: uri-reference' takes precedence.",
      "Schema 'UriReference': 'pattern' ignored because 'format: uri-reference' takes precedence.",
      "Schema 'Uuid': 'minLength'/'maxLength' ignored because 'format: uuid' takes precedence.",
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
      "Schema 'Code': 'minLength'/'maxLength' ignored because 'pattern' takes precedence.",
      "Schema 'Labels.propertyNames': 'minLength'/'maxLength' ignored because 'pattern' takes precedence."
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
      Diagnostic("Example key '404_missing' targets status code 404, but no response with that status code is defined. Key ignored.",
                 operation = OperationRef("GET", "/products/{id}"),
                 category = SPEC,
                 severity = WARNING)
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
             Diagnostic("Schema '': unknown format 'int128' for integer type is ignored.", category = SPEC, severity = WARNING)
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

  private fun assertSchemaWarnings(report: LoadReport): List<String> {
    val loaded = assertIs<LoadReport.Loaded>(report)
    assert(loaded.diagnostics.all { it.category == SPEC && it.severity == WARNING && it.operation == null }) {
      loaded.diagnostics.toString()
    }
    return loaded.diagnostics.map { it.message }
  }
}
