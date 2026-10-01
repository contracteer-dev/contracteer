package dev.contracteer.core

import kotlin.test.Test

class DiagnosticWireNameTest {

  @Test
  fun `every enum constant has a pinned kebab-case wire name`() {
    // when
    val rules = DiagnosticRule.entries.map { it.wireName() }
    val categories = DiagnosticCategory.entries.map { it.wireName() }
    val severities = Severity.entries.map { it.wireName() }

    // then
    assert(rules == listOf(
      "unsupported",
      "unserializable-content",
      "infinite-cycle",
      "pattern-uncertifiable",
      "operation-excluded",
      "body-excluded",
      "scenario-excluded",
      "conflicting-constraints",
      "unknown-format",
      "empty-schema",
      "unresolved-example-key"
    )) { rules.toString() }
    assert(categories == listOf("spec", "contract-violation", "execution-error")) { categories.toString() }
    assert(severities == listOf("error", "warning", "info")) { severities.toString() }
  }
}
