package dev.contracteer.core.swagger

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test

class LoadReportJsonTest {

  private val json = ObjectMapper()

  @Test
  fun `failed load serializes as a versioned report with its diagnostics`() {
    // given
    val report = OpenApiLoader.load("src/test/resources/error/single_example_invalid.yaml")

    // when
    val actual = json.readTree(report.toJson())

    // then
    val expected = json.readTree("""
      {
        "version": 1,
        "source": "src/test/resources/error/single_example_invalid.yaml",
        "status": "failed",
        "operationCount": null,
        "diagnostics": [
          {
            "message": "Type mismatch, expected type 'integer'",
            "location": "request.path[id].example",
            "keyword": null,
            "rule": null,
            "operation": { "method": "GET", "path": "/items/{id}" },
            "category": "spec",
            "severity": "error"
          }
        ],
        "truncated": 0
      }
    """)
    assert(actual == expected) { actual.toPrettyString() }
  }

  @Test
  fun `loaded document serializes with its operation count and its warnings`() {
    // given
    val report = OpenApiLoader.load("src/test/resources/warning/pattern_precedence.yaml")

    // when
    val actual = json.readTree(report.toJson())

    // then
    val expected = json.readTree("""
      {
        "version": 1,
        "source": "src/test/resources/warning/pattern_precedence.yaml",
        "status": "loaded",
        "operationCount": 1,
        "diagnostics": [
          {
            "message": "Schema 'Code': 'minLength' ignored because 'pattern' takes precedence.",
            "location": null,
            "keyword": "minLength",
            "rule": "conflicting-constraints",
            "operation": null,
            "category": "spec",
            "severity": "warning"
          },
          {
            "message": "Schema 'Labels.propertyNames': 'maxLength' ignored because 'pattern' takes precedence.",
            "location": null,
            "keyword": "maxLength",
            "rule": "conflicting-constraints",
            "operation": null,
            "category": "spec",
            "severity": "warning"
          }
        ],
        "truncated": 0
      }
    """)
    assert(actual == expected) { actual.toPrettyString() }
  }
}
