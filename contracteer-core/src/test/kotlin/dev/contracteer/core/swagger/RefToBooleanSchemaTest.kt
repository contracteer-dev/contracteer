package dev.contracteer.core.swagger

import dev.contracteer.core.Diagnostic
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Severity.ERROR
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs

// OAS 3.1 allows a boolean schema as a $ref target, but swagger-parser cannot read one: Contracteer
// rejects the reference before parsing, with a readable diagnostic instead of the parser's failure.
class RefToBooleanSchemaTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("booleanTargets")
  fun `rejects a reference to a boolean schema and names the reference`(fixture: String, ref: String, value: Boolean) {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/$fixture")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(unsupportedBooleanRef(ref, value))) { report.diagnostics.toString() }
  }

  @Test
  fun `reports each distinct reference to a boolean schema once and in order`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/ref_to_boolean_several_31.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(
      unsupportedBooleanRef("#/components/schemas/Foo/properties/alpha", true),
      unsupportedBooleanRef("#/components/schemas/Foo/properties/zeta", false)
    )) { report.diagnostics.toString() }
  }

  @Test
  fun `rejects a reference to a boolean schema under a name spelled like a literal keyword`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/ref_to_boolean_under_keyword_like_names_31.yaml")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(
      unsupportedBooleanRef("#/components/schemas/Foo/properties/a", false),
      unsupportedBooleanRef("#/components/schemas/Foo/properties/b", false),
      unsupportedBooleanRef("#/components/schemas/Foo/properties/c", false)
    )) { report.diagnostics.toString() }
  }

  @Test
  fun `rejects a reference to a boolean schema in a document larger than 3 MB`(@TempDir dir: Path) {
    // given
    val path = dir.largeCopyOf("error/ref_to_boolean_property_31.yaml")

    // when
    val report = OpenApiLoader.load(path)

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(unsupportedBooleanRef("#/components/schemas/Foo/properties/forbidden", false))) { report.diagnostics.toString() }
  }

  @Test
  fun `rejects a reference to a boolean schema in a JSON document that escapes a slash`() {
    // when
    val report = OpenApiLoader.load("src/test/resources/error/ref_to_boolean_property_escaped_slash_31.json")

    // then
    assertIs<LoadReport.Failed>(report)
    assert(report.diagnostics == listOf(unsupportedBooleanRef("#/components/schemas/Foo/properties/forbidden", false))) { report.diagnostics.toString() }
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = ["ref_to_boolean_in_literal_values.yaml", "ref_to_boolean_in_unresolved_positions.yaml"])
  fun `loads a document whose references to a boolean schema are never resolved`(fixture: String) {
    // when
    val report = OpenApiLoader.load("src/test/resources/datatype/3.1/$fixture")

    // then
    assertIs<LoadReport.Loaded>(report, report.diagnostics.toString())
  }

  private fun unsupportedBooleanRef(ref: String, value: Boolean) =
    Diagnostic(
      message = $$"$ref '$$ref': boolean schema '$$value' is not supported in Contracteer. Use a schema object instead.",
      rule = UNSUPPORTED,
      category = SPEC,
      severity = ERROR
    )

  companion object {
    @JvmStatic
    fun booleanTargets() = listOf(
      Arguments.of("ref_to_boolean_property_31.yaml",                    "#/components/schemas/Foo/properties/forbidden", false),
      Arguments.of("ref_to_true_property_31.yaml",                       "#/components/schemas/Foo/properties/anything",  true),
      Arguments.of("ref_to_boolean_additional_properties_31.yaml",       "#/components/schemas/Foo/additionalProperties", false),
      Arguments.of("ref_to_boolean_defs_31.yaml",                        $$"#/components/schemas/Foo/$defs/never",        false),
      Arguments.of("ref_to_boolean_component_31.yaml",                   "#/components/schemas/Never",                    false),
      Arguments.of("ref_to_boolean_parameter_schema_31.yaml",            "#/components/schemas/Foo/properties/forbidden", false),
      Arguments.of("ref_to_boolean_header_named_like_extension_31.yaml", "#/components/schemas/Foo/properties/forbidden", false)
    )
  }
}
