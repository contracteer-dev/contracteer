package dev.contracteer.core.swagger

import dev.contracteer.core.DiagnosticRule.UNSUPPORTED
import dev.contracteer.core.assertSuccess
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import kotlin.test.Test

class SharedComponentsTest {

  @Test
  fun `external reference is reported as unsupported with the ref keyword`() {
    // given
    val sharedComponents = sharedComponents()

    // when
    val result = sharedComponents.dereference("other.yaml#/components/schemas/Name")

    // then
    assert(result.diagnostics().map { it.rule to it.keyword } == listOf(UNSUPPORTED to $$"$ref")) { result.diagnostics().toString() }
  }

  @Test
  fun `resolves a path item that references a path item component`() {
    // given
    val products = PathItem().get(Operation())
    val sharedComponents = sharedComponents(pathItems = mapOf("Products" to products))

    // when
    val result = sharedComponents.resolve(PathItem().`$ref`("#/components/pathItems/Products"))

    // then
    assert(result.assertSuccess() == products)
  }

  @Test
  fun `resolves a path item through a chain of path item component references`() {
    // given
    val products = PathItem().get(Operation())
    val sharedComponents = sharedComponents(pathItems = mapOf(
      "Alias" to PathItem().`$ref`("#/components/pathItems/Products"),
      "Products" to products
    ))

    // when
    val result = sharedComponents.resolve(PathItem().`$ref`("#/components/pathItems/Alias"))

    // then
    assert(result.assertSuccess() == products)
  }

  @Test
  fun `path item reference outside the path item components is reported as unsupported with the ref keyword`() {
    // given
    val sharedComponents = sharedComponents()

    // when
    val result = sharedComponents.resolve(PathItem().`$ref`("#/x-shared/products"))

    // then
    assert(result.diagnostics().map { it.rule to it.keyword } == listOf(UNSUPPORTED to $$"$ref")) { result.diagnostics().toString() }
  }

  private fun sharedComponents(pathItems: Map<String, PathItem> = emptyMap()) = SharedComponents(
    schemas = emptyMap(),
    parameters = emptyMap(),
    requestBodies = emptyMap(),
    headers = emptyMap(),
    examples = emptyMap(),
    responses = emptyMap(),
    pathItems = pathItems
  )
}
