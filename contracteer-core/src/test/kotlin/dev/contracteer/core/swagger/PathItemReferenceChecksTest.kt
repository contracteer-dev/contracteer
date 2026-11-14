package dev.contracteer.core.swagger

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import kotlin.test.Test

class PathItemReferenceChecksTest {

  @Test
  fun `accepts an extension beside a path item reference`() {
    // given
    val document = yaml("""
      paths:
        /products:
          ${'$'}ref: '#/components/pathItems/Products'
          x-owner: catalog
    """)

    // when
    val result = document.rejectUnsupportedPathItemReferences()

    // then
    assert(result.isSuccess()) { result.diagnostics().toString() }
  }

  @Test
  fun `ignores an extension of the paths object shaped like a path item reference`() {
    // given
    val document = yaml("""
      paths:
        x-shared:
          ${'$'}ref: '#/components/pathItems/Products'
          get: {}
    """)

    // when
    val result = document.rejectUnsupportedPathItemReferences()

    // then
    assert(result.isSuccess()) { result.diagnostics().toString() }
  }

  private fun yaml(content: String) = YAMLMapper().readTree(content.trimIndent())
}
