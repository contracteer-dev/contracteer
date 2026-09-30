package dev.contracteer.core.swagger

import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import kotlin.test.Test

class SharedComponentsTest {

  @Test
  fun `external reference is reported as unsupported with the ref keyword`() {
    // given
    val sharedComponents = SharedComponents(
      schemas = emptyMap(),
      parameters = emptyMap(),
      requestBodies = emptyMap(),
      headers = emptyMap(),
      examples = emptyMap(),
      responses = emptyMap()
    )

    // when
    val result = sharedComponents.dereference("other.yaml#/components/schemas/Name")

    // then
    assert(result.diagnostics().map { it.rule to it.keyword } == listOf(UNSUPPORTED to $$"$ref")) { result.diagnostics().toString() }
  }
}
