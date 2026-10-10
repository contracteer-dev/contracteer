package dev.contracteer.verifier

import dev.contracteer.core.operation.*
import dev.contracteer.core.serde.JsonSerde
import dev.contracteer.core.dsl.ApiOperationBuilder
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import kotlin.test.Test

class VerificationCaseTest {

  @Test
  fun `display name for scenario based with request and response bodies`() {
    // Given
    val case = scenarioBasedCase("GET", "/users/{id}", statusCode = 200, key = "validUser",
      requestContentType = ContentType("application/json"),
      responseContentType = ContentType("application/json")) {
      request {
        pathParam("id", integerType())
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(200) {
        jsonBody(objectType { properties { "id" to integerType(); "name" to stringType() } })
      }
      scenario("validUser", status = 200) {
        request {
          pathParam["id"] = 123
          jsonBody { "name" to "John" }
        }
        response {
          jsonBody { "id" to 123; "name" to "John" }
        }
      }
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "GET /users/{id} (application/json) -> 200 (application/json) with scenario 'validUser'")
  }

  @Test
  fun `display name for scenario based without request body`() {
    // Given
    val case = scenarioBasedCase("GET", "/users/{id}", statusCode = 404, key = "notFound") {
      request {
        pathParam("id", integerType())
      }
      response(404)
      scenario("notFound", status = 404) {
        request {
          pathParam["id"] = 999
        }
        response {}
      }
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "GET /users/{id} -> 404 with scenario 'notFound'")
  }

  @Test
  fun `display name for scenario based without response body example`() {
    // Given
    val case = scenarioBasedCase("GET", "/users/{id}", statusCode = 404, key = "404_UNKNOWN",
      responseContentType = ContentType("text/plain")) {
      request {
        pathParam("id", integerType())
      }
      response(404) {
        jsonBody(objectType { properties { "code" to stringType() } })
        plainTextBody(stringType())
      }
      scenario("404_UNKNOWN", status = 404) {
        request {
          pathParam["id"] = 999
        }
      }
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "GET /users/{id} -> 404 (text/plain) with scenario '404_UNKNOWN'")
  }

  @Test
  fun `display name for scenario based with POST method`() {
    // Given
    val case = scenarioBasedCase("POST", "/orders", statusCode = 201, key = "success",
      requestContentType = ContentType("application/json"),
      responseContentType = ContentType("application/json")) {
      request {
        jsonBody(objectType {
          properties {
            "product" to stringType()
            "quantity" to integerType()
          }
        })
      }
      response(201) {
        jsonBody(objectType { properties { "orderId" to stringType() } })
      }
      scenario("success", status = 201) {
        request {
          jsonBody { "product" to "laptop"; "quantity" to 1 }
        }
        response {
          jsonBody { "orderId" to "12345" }
        }
      }
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "POST /orders (application/json) -> 201 (application/json) with scenario 'success'")
  }

  @Test
  fun `display name for schema only with request and response bodies`() {
    // Given
    val case = schemaBasedCase("GET", "/products", statusCode = 200,
      requestContentType = ContentType("application/json"),
      responseContentType = ContentType("application/json")) {
      request {
        pathParam("id", integerType())
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(200) {
        jsonBody(objectType { properties { "id" to integerType(); "name" to stringType() } })
      }
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "GET /products (application/json) -> 200 (application/json) (generated)")
  }

  @Test
  fun `display name for schema only without request body`() {
    // Given
    val case = schemaBasedCase("GET", "/products", statusCode = 200,
      responseContentType = ContentType("application/json")) {
      response(200) {
        jsonBody(objectType { properties { "items" to stringType() } })
      }
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "GET /products -> 200 (application/json) (generated)")
  }

  @Test
  fun `display name for schema only with XML content types`() {
    // Given
    val case = schemaBasedCase("POST", "/legacy/endpoint", statusCode = 201,
      requestContentType = ContentType("application/xml"),
      responseContentType = ContentType("application/xml")) {
      request {
        body("application/xml", objectType { properties { "name" to stringType() } }, JsonSerde)
      }
      response(201) {
        body("application/xml", objectType { properties { "result" to stringType() } }, JsonSerde)
      }
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "POST /legacy/endpoint (application/xml) -> 201 (application/xml) (generated)")
  }

  @Test
  fun `display name for schema only without any bodies`() {
    // Given
    val case = schemaBasedCase("GET", "/health", statusCode = 204) {
      response(204)
    }

    // When
    val displayName = case.displayName

    // Then
    assert(displayName == "GET /health -> 204 (generated)")
  }

  // --- helpers ---

  private fun scenarioBasedCase(
    method: String, path: String, statusCode: Int, key: String,
    requestContentType: ContentType? = null,
    responseContentType: ContentType? = null,
    block: ApiOperationBuilder.() -> Unit
  ): VerificationCase.ScenarioBased {
    val op = apiOperation(method, path, block)
    return VerificationCase.ScenarioBased(
      op.scenarios.first { it.key == key },
      op.requestSchema,
      op.responseSchemas.responseFor(statusCode)!!,
      requestContentType,
      responseContentType
    )
  }

  private fun schemaBasedCase(
    method: String, path: String, statusCode: Int,
    requestContentType: ContentType? = null,
    responseContentType: ContentType? = null,
    block: ApiOperationBuilder.() -> Unit
  ): VerificationCase.SchemaBased {
    val op = apiOperation(method, path, block)
    return VerificationCase.SchemaBased(
      path = path,
      method = method,
      statusCode = statusCode,
      requestContentType = requestContentType,
      responseContentType = responseContentType,
      requestSchema = op.requestSchema,
      responseSchema = op.responseSchemas.responseFor(statusCode)!!
    )
  }
}
