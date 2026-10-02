package dev.contracteer.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test

class LintCliTest {

  private val json = ObjectMapper()

  @Test
  fun `json format prints the load report and exits with 0 when the document loads without a finding`() {
    // When
    val execution = execute("lint", ORDERS, "--format", "json")

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 0)
    assert(report["status"].asText() == "loaded")
    assert(report["diagnostics"].isEmpty)
  }

  @Test
  fun `json format exits with 0 when the document loads with a warning`() {
    // When
    val execution = execute("lint", ORDERS_WITH_XML_OPERATION, "--format", "json")

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 0)
    assert(report["status"].asText() == "loaded")
    assert(report.rules() == listOf("operation-excluded"))
  }

  @Test
  fun `fail on warning exits with 1 when the document loads with a warning`() {
    // When
    val execution = execute("lint", ORDERS_WITH_XML_OPERATION, "--format", "json", "--fail-on", "warning")

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 1)
    assert(report["status"].asText() == "loaded")
  }

  @Test
  fun `fail on accepts warn for warning`() {
    // When
    val execution = execute("lint", ORDERS_WITH_XML_OPERATION, "--format", "json", "--fail-on", "warn")

    // Then
    assert(execution.exitCode == 1)
  }

  @Test
  fun `fail on warning exits with 0 when the document loads without a finding`() {
    // When
    val execution = execute("lint", ORDERS, "--format", "json", "--fail-on", "warning")

    // Then
    assert(execution.exitCode == 0)
  }

  @Test
  fun `json format prints the load report and exits with 1 when the document does not load`() {
    // When
    val execution = execute("lint", ORDERS_WITH_UNSUPPORTED_KEYWORD, "--format", "json")

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 1)
    assert(report["status"].asText() == "failed")
    assert(report.rules() == listOf("unsupported"))
  }

  @Test
  fun `json format reports an execution error and exits with 1 when the document cannot be read`() {
    // When
    val execution = execute("lint", MISSING_DOCUMENT, "--format", "json")

    // Then
    val report = json.readTree(execution.stdout)
    assert(execution.exitCode == 1)
    assert(report["status"].asText() == "failed")
    assert(report["diagnostics"].map { it["category"].asText() } == listOf("execution-error"))
  }

  @Test
  fun `the loader logs no warning the report already holds`() {
    // When
    val execution = execute("lint", ORDERS_WITH_XML_OPERATION, "--format", "json")

    // Then
    assert(!execution.stderr.contains("Operation excluded"))
  }

  @Test
  fun `logs are written to stderr`() {
    // When
    val execution = execute("lint", ORDERS, "--format", "json")

    // Then
    assert(execution.stderr.contains("Found 1 valid operation(s)."))
  }

  @Test
  fun `an unknown fail on severity prints nothing on stdout and exits with 2`() {
    // When
    val execution = execute("lint", ORDERS, "--format", "json", "--fail-on", "info")

    // Then
    assert(execution.exitCode == 2)
    assert(execution.stdout.isEmpty())
  }

  @Test
  fun `the http traffic option is rejected and exits with 2`() {
    // When
    val execution = execute("lint", ORDERS, "--format", "json", "-t")

    // Then
    assert(execution.exitCode == 2)
    assert(execution.stdout.isEmpty())
  }

  @Test
  fun `text format states what ran when the document loads without a finding`() {
    // When
    val execution = execute("lint", ORDERS)

    // Then
    assert(execution.exitCode == 0)
    assert(execution.stdout.lines() == listOf(
      "OpenAPI document: $ORDERS",
      "",
      "The document loads: 1 operation declared, none excluded, no constraint ignored.",
      ""
    ))
  }

  @Test
  fun `text format lists each warning once with its rule and counts the excluded operations`() {
    // When
    val execution = execute("lint", ORDERS_WITH_XML_OPERATION)

    // Then
    assert(execution.exitCode == 0)
    assert(execution.stdout.lines() == listOf(
      "OpenAPI document: $ORDERS_WITH_XML_OPERATION",
      "",
      "Warnings (1)",
      "   POST /orders: Operation excluded: no supported request body content type. [operation-excluded]",
      "",
      "The document loads: 2 operations declared, 1 excluded. 0 errors, 1 warning.",
      ""
    ))
  }

  @Test
  fun `text format lists the errors and states that exclusions were not evaluated when the document does not load`() {
    // When
    val execution = execute("lint", ORDERS_WITH_UNSUPPORTED_KEYWORD)

    // Then
    assert(execution.exitCode == 1)
    assert(execution.stdout.lines() == listOf(
      "OpenAPI document: $ORDERS_WITH_UNSUPPORTED_KEYWORD",
      "",
      "Errors (1)",
      "   GET /orders: 'response[200].body.id': Schema 'id': 'not' is not supported in Contracteer. [unsupported]",
      "",
      "The document does not load: 1 error, 0 warnings. Exclusions were not evaluated.",
      ""
    ))
  }

  @Test
  fun `text format lists the warnings collected before the load stopped when the document does not load`() {
    // When
    val execution = execute("lint", ORDERS_WITH_UNSUPPORTED_KEYWORD_AND_IGNORED_CONSTRAINT)

    // Then
    assert(execution.exitCode == 1)
    assert(execution.stdout.lines() == listOf(
      "OpenAPI document: $ORDERS_WITH_UNSUPPORTED_KEYWORD_AND_IGNORED_CONSTRAINT",
      "",
      "Errors (1)",
      "   GET /orders: 'response[200].body.id': Schema 'id': 'not' is not supported in Contracteer. [unsupported]",
      "",
      "Warnings (1)",
      "   Schema 'reference': 'minLength' ignored because 'format: uuid' takes precedence. [conflicting-constraints]",
      "",
      "The document does not load: 1 error, 1 warning. Exclusions were not evaluated.",
      ""
    ))
  }

  @Test
  fun `text format exits with 1 on a warning when failing on warnings`() {
    // When
    val execution = execute("lint", ORDERS_WITH_XML_OPERATION, "--fail-on", "warning")

    // Then
    assert(execution.exitCode == 1)
  }

  @Test
  fun `text format prints a freeform finding without a rule`() {
    // When
    val execution = execute("lint", MISSING_DOCUMENT)

    // Then
    assert(execution.exitCode == 1)
    assert(execution.stdout.lines().drop(2).take(2) == listOf(
      "Errors (1)",
      "   Classpath resource not found: lint/missing.yaml"
    ))
  }

  @Test
  fun `text format states how many errors are not shown when the errors are truncated`() {
    // When
    val execution = execute("lint", ORDERS_WITH_THIRTY_UNSUPPORTED_KEYWORDS)

    // Then
    assert(execution.exitCode == 1)
    assert(execution.stdout.lines().dropLast(1).last() ==
           "The document does not load: 25 errors, 0 warnings. Exclusions were not evaluated. 5 more errors not shown.")
  }

  private fun JsonNode.rules() = this["diagnostics"].map { it["rule"].asText() }

  private companion object {
    const val ORDERS = "classpath:lint/orders.yaml"
    const val ORDERS_WITH_XML_OPERATION = "classpath:lint/orders_with_xml_operation.yaml"
    const val ORDERS_WITH_UNSUPPORTED_KEYWORD = "classpath:lint/orders_with_unsupported_keyword.yaml"
    const val ORDERS_WITH_UNSUPPORTED_KEYWORD_AND_IGNORED_CONSTRAINT =
      "classpath:lint/orders_with_unsupported_keyword_and_ignored_constraint.yaml"
    const val ORDERS_WITH_THIRTY_UNSUPPORTED_KEYWORDS = "classpath:lint/orders_with_thirty_unsupported_keywords.yaml"
    const val MISSING_DOCUMENT = "classpath:lint/missing.yaml"
  }
}
