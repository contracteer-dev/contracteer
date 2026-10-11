package dev.contracteer.core.swagger

import dev.contracteer.core.assertFailure
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test

class OpenApiLoaderVersionTest {

  @Test
  fun `rejects OpenAPI 3 2 as unsupported`() {
    // when
    val result = OpenApiLoader.loadOperations("classpath:error/openapi_32_unsupported.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it == "OpenAPI version '3.2.0' is not supported. Contracteer currently supports OpenAPI 3.0.x and 3.1.x." })
  }

  @Test
  fun `rejects OpenAPI 3 2 as unsupported in a document larger than 3 MB`(@TempDir dir: Path) {
    // given
    val path = dir.largeCopyOf("error/openapi_32_unsupported.yaml")

    // when
    val result = OpenApiLoader.loadOperations(path)

    // then
    val errors = result.assertFailure()
    assert(errors.any { it == "OpenAPI version '3.2.0' is not supported. Contracteer currently supports OpenAPI 3.0.x and 3.1.x." })
  }

  @Test
  fun `fails to parse a version for which swagger-parser returns no model and no message`() {
    // when
    val result = OpenApiLoader.parse(File("src/test/resources/error/openapi_32_unsupported.yaml").readText())

    // then
    val errors = result.assertFailure()
    assert(errors == listOf("Failed to parse OpenAPI 3 Document")) { errors.toString() }
  }

  @Test
  fun `rejects OpenAPI 4 x as unsupported`() {
    // when
    val result = OpenApiLoader.loadOperations("classpath:error/openapi_4x_unsupported.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it == "OpenAPI version '4.0.0' is not supported. Contracteer currently supports OpenAPI 3.0.x and 3.1.x." })
  }

  @Test
  fun `rejects OpenAPI 2 x as unsupported`() {
    // when
    val result = OpenApiLoader.loadOperations("classpath:error/openapi_2x_unsupported.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it == "OpenAPI version '2.0' is not supported. Contracteer currently supports OpenAPI 3.0.x and 3.1.x." })
  }

  @Test
  fun `rejects document with missing openapi version field`() {
    // when
    val result = OpenApiLoader.loadOperations("classpath:error/openapi_missing_version.yaml")

    // then
    val errors = result.assertFailure()
    assert(errors.any { it == "OpenAPI document does not declare a version. Contracteer currently supports OpenAPI 3.0.x and 3.1.x." })
  }
}
