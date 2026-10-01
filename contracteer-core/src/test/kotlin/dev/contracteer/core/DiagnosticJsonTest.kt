package dev.contracteer.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.contracteer.core.DiagnosticCategory.SPEC
import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.Severity.ERROR
import kotlin.test.Test

class DiagnosticJsonTest {

  private val json = ObjectMapper()

  @Test
  fun `diagnostic serializes every property with kebab-case enums`() {
    // given
    val diagnostic = Diagnostic(
      message = "Schema 'Item': 'prefixItems' is not supported.",
      location = "response[200].body[application/json]",
      keyword = "prefixItems",
      rule = UNSUPPORTED,
      operation = OperationRef("GET", "/items"),
      category = SPEC,
      severity = ERROR
    )

    // when
    val actual = json.valueToTree<JsonNode>(diagnostic.toJsonMap())

    // then
    val expected = json.readTree("""
      {
        "message": "Schema 'Item': 'prefixItems' is not supported.",
        "location": "response[200].body[application/json]",
        "keyword": "prefixItems",
        "rule": "unsupported",
        "operation": { "method": "GET", "path": "/items" },
        "category": "spec",
        "severity": "error"
      }
    """)
    assert(actual == expected) { actual.toPrettyString() }
  }
}
