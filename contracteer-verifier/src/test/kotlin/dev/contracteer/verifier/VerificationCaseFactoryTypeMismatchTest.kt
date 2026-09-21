package dev.contracteer.verifier

import dev.contracteer.core.dsl.ApiOperationBuilder
import dev.contracteer.core.dsl.allOfType
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.deepObject
import dev.contracteer.core.dsl.form
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.operation.ParameterElement.Cookie
import dev.contracteer.core.operation.ParameterElement.Header
import dev.contracteer.core.operation.ParameterElement.PathParam
import dev.contracteer.core.operation.ParameterElement.QueryParam
import dev.contracteer.core.serde.JsonSerde
import dev.contracteer.verifier.VerificationCase.ScenarioBased
import dev.contracteer.verifier.VerificationCase.TypeMismatch
import kotlin.test.Test

class VerificationCaseFactoryTypeMismatchTest {

  @Test
  fun `generates type mismatch case for path parameter`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        pathParam("id", integerType())
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    val pathCase = typeMismatchCases.find { it.mutatedElement == MutatedElement.Parameter(PathParam("id")) }
    assert(pathCase != null)
    assert(pathCase!!.mutatedValue == "<<not a integer>>")
  }

  @Test
  fun `generates type mismatch case for query parameter`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        queryParam("page", integerType())
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    val queryCase = typeMismatchCases.find { it.mutatedElement == MutatedElement.Parameter(QueryParam("page")) }
    assert(queryCase != null)
    assert(queryCase!!.mutatedValue == "<<not a integer>>")
  }

  @Test
  fun `generates type mismatch case for header parameter`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        header("X-Request-Id", integerType(), isRequired = true)
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    val headerCase = typeMismatchCases.find { it.mutatedElement == MutatedElement.Parameter(Header("X-Request-Id")) }
    assert(headerCase != null)
    assert(headerCase!!.mutatedValue == "<<not a integer>>")
  }

  @Test
  fun `generates type mismatch case for cookie parameter`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        cookie("session_ttl", integerType())
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    val cookieCase = typeMismatchCases.find { it.mutatedElement == MutatedElement.Parameter(Cookie("session_ttl")) }
    assert(cookieCase != null)
    assert(cookieCase!!.mutatedValue == "<<not a integer>>")
  }

  @Test
  fun `generates one type mismatch case per element category`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        pathParam("id", integerType())
        queryParam("page", integerType())
        header("X-Correlation-Id", integerType(), isRequired = true)
        cookie("token", integerType())
        jsonBody(objectType { properties { "name" to stringType() } })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 5)
    assert(typeMismatchCases.count { it.mutatedElement is MutatedElement.Parameter && it.mutatedElement.element is PathParam } == 1)
    assert(typeMismatchCases.count { it.mutatedElement is MutatedElement.Parameter && it.mutatedElement.element is QueryParam } == 1)
    assert(typeMismatchCases.count { it.mutatedElement is MutatedElement.Parameter && it.mutatedElement.element is Header } == 1)
    assert(typeMismatchCases.count { it.mutatedElement is MutatedElement.Parameter && it.mutatedElement.element is Cookie } == 1)
    assert(typeMismatchCases.count { it.mutatedElement == MutatedElement.Body } == 1)
  }

  @Test
  fun `picks first mutable parameter when category has multiple parameters`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        queryParam("name", stringType())
        queryParam("page", integerType())
        queryParam("limit", integerType())
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Parameter(QueryParam("page")))
  }

  @Test
  fun `does not generate parameter type mismatch case when all parameters in category are non-mutable`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        queryParam("name", stringType())
        queryParam("filter", stringType())
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `generates type mismatch case for body when operation has 400 response and mutable request body`() {
    // Given
    val apiOperation = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(200) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
      response(400) {
        jsonBody(objectType { properties { "error" to stringType() } })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Body)
    assert(typeMismatchCases[0].mutatedValue == "<<not a object>>")
    assert(typeMismatchCases[0].requestContentType == ContentType("application/json"))
    assert(typeMismatchCases[0].expectedResponses == mapOf(400 to apiOperation.responseSchemas.badRequestResponse()))
    assert(typeMismatchCases[0].path == "/users")
    assert(typeMismatchCases[0].method == "POST")
  }

  @Test
  fun `does not generate type mismatch case when operation has no 400 response`() {
    // Given
    val apiOperation = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(200) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `does not generate type mismatch case for body when all request body data types are non-mutable`() {
    // Given
    val apiOperation = apiOperation("POST", "/users") {
      request {
        body("text/plain", stringType(), JsonSerde)
      }
      response(200) {}
      response(400) {
        jsonBody(objectType { properties { "error" to stringType() } })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `does not generate type mismatch case when operation has no request body and no mutable parameters`() {
    // Given
    val apiOperation = apiOperation("GET", "/users") {
      response(200) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
      response(400) {
        jsonBody(objectType { properties { "error" to stringType() } })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `generates type mismatch case for first mutable body when multiple content types exist`() {
    // Given
    val apiOperation = apiOperation("POST", "/data") {
      request {
        body("text/plain", stringType(), JsonSerde)
        jsonBody(objectType { properties { "value" to stringType() } })
      }
      response(200) {}
      response(400) {
        jsonBody(objectType { properties { "error" to stringType() } })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].requestContentType == ContentType("application/json"))
    assert(typeMismatchCases[0].mutatedValue == "<<not a object>>")
  }

  @Test
  fun `generates type mismatch case alongside explicit 400 scenarios`() {
    // Given
    val apiOperation = apiOperation("POST", "/users") {
      request {
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(201) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
      response(400) {
        jsonBody(objectType { properties { "error" to stringType() } })
      }
      scenario("invalidUser", status = 400) {
        request { jsonBody { "name" to "" } }
        response { jsonBody { "error" to "name is required" } }
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    val scenarioCases = cases.filterIsInstance<ScenarioBased>()
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()
    assert(scenarioCases.size == 1)
    assert(typeMismatchCases.size == 1)
    assert(scenarioCases[0].displayName.contains("invalidUser"))
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Body)
  }


  @Test
  fun `does not generate body type mismatch for form-urlencoded with all optional properties`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        // Intentional serde mismatch: application/x-www-form-urlencoded with JsonSerde (preserved from legacy).
        body(
          "application/x-www-form-urlencoded",
          objectType { properties { "name" to stringType() } },
          JsonSerde
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `generates body type mismatch for form-urlencoded with required properties`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        body(
          "application/x-www-form-urlencoded",
          objectType {
            properties { "name" to stringType() }
            required("name")
          },
          JsonSerde
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Body)
  }

  @Test
  fun `generates body type mismatch for form-urlencoded with additionalProperties false`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        body(
          "application/x-www-form-urlencoded",
          objectType(allowAdditionalProperties = false) {
            properties { "name" to stringType() }
          },
          JsonSerde
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Body)
  }

  @Test
  fun `does not generate body type mismatch for form-urlencoded allOf where every branch is all-optional`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        body(
          "application/x-www-form-urlencoded",
          allOfType {
            subType(objectType { properties { "name" to stringType() } })
            subType(objectType { properties { "size" to integerType() } })
          },
          JsonSerde
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `generates body type mismatch for form-urlencoded allOf where one branch has a required property`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        body(
          "application/x-www-form-urlencoded",
          allOfType {
            subType(objectType {
              properties { "name" to stringType() }
              required("name")
            })
            subType(objectType { properties { "size" to integerType() } })
          },
          JsonSerde
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Body)
  }

  @Test
  fun `generates body type mismatch for form-urlencoded allOf where one branch forbids additionalProperties`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        body(
          "application/x-www-form-urlencoded",
          allOfType {
            subType(objectType(allowAdditionalProperties = false) {
              properties { "name" to stringType() }
            })
            subType(objectType { properties { "size" to integerType() } })
          },
          JsonSerde
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Body)
  }

  @Test
  fun `does not generate parameter type mismatch for deepObject with all optional properties`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        queryParam(
          "filter",
          objectType { properties { "name" to stringType() } },
          codec = deepObject()
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `does not generate parameter type mismatch for form-explode object with no declared properties and additionalProperties true`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        queryParam(
          "params",
          objectType(allowAdditionalProperties = true) {},
          codec = form(explode = true)
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.none { it is TypeMismatch })
  }

  @Test
  fun `generates parameter type mismatch for form-no-explode object with no declared properties and additionalProperties true`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        queryParam(
          "params",
          objectType(allowAdditionalProperties = true) {},
          codec = form(explode = false)
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Parameter(QueryParam("params")))
  }

  @Test
  fun `generates parameter type mismatch for form-explode object with required property`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        queryParam(
          "filter",
          objectType {
            properties { "name" to stringType() }
            required("name")
          },
          codec = form(explode = true)
        )
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)
    val typeMismatchCases = cases.filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 1)
    assert(typeMismatchCases[0].mutatedElement == MutatedElement.Parameter(QueryParam("filter")))
  }

  @Test
  fun `expects only 400 when the document covers no other rejection status`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        pathParam("id", integerType())
      }
    }

    // When
    val typeMismatchCase = VerificationCaseFactory.create(apiOperation).filterIsInstance<TypeMismatch>().single()

    // Then
    assert(typeMismatchCase.expectedResponses.keys.toList() == listOf(400))
  }

  @Test
  fun `expects a covered 404 for path and query mutations`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        pathParam("id", integerType())
        queryParam("page", integerType())
      }
      response(404) {}
    }

    // When
    val typeMismatchCases = VerificationCaseFactory.create(apiOperation).filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 2)
    assert(typeMismatchCases.all { it.expectedResponses.keys.toList() == listOf(400, 404) })
    assert(typeMismatchCases.all { it.expectedResponses[404] == apiOperation.responseSchemas.responseFor(404) })
  }

  @Test
  fun `does not expect 404 for header cookie and body mutations`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        header("X-Count", integerType(), isRequired = true)
        cookie("session_ttl", integerType())
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(404) {}
    }

    // When
    val typeMismatchCases = VerificationCaseFactory.create(apiOperation).filterIsInstance<TypeMismatch>()

    // Then
    assert(typeMismatchCases.size == 3)
    assert(typeMismatchCases.all { it.expectedResponses.keys.toList() == listOf(400) })
  }

  @Test
  fun `expects a covered 422`() {
    // Given
    val apiOperation = apiOperationWith400 {
      request {
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(422) {
        jsonBody(objectType { properties { "detail" to stringType() } })
      }
    }

    // When
    val typeMismatchCase = VerificationCaseFactory.create(apiOperation).filterIsInstance<TypeMismatch>().single()

    // Then
    assert(typeMismatchCase.expectedResponses == mapOf(
      400 to apiOperation.responseSchemas.responseFor(400),
      422 to apiOperation.responseSchemas.responseFor(422)
    ))
  }

  @Test
  fun `expects every rejection status a class response covers in ascending order`() {
    // Given
    val apiOperation = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
      }
      response(200) {}
      classResponse(4) {
        jsonBody(objectType { properties { "title" to stringType() } })
      }
    }

    // When
    val typeMismatchCase = VerificationCaseFactory.create(apiOperation).filterIsInstance<TypeMismatch>().single()

    // Then
    assert(typeMismatchCase.expectedResponses.keys.toList() == listOf(400, 404, 422))
    assert(typeMismatchCase.expectedResponses.values.all { it == apiOperation.responseSchemas.responseFor(400) })
  }

  @Test
  fun `expects every rejection status a default response covers`() {
    // Given
    val apiOperation = apiOperation("GET", "/users") {
      request {
        queryParam("page", integerType())
      }
      response(200) {}
      defaultResponse {
        jsonBody(objectType { properties { "message" to stringType() } })
      }
    }

    // When
    val typeMismatchCase = VerificationCaseFactory.create(apiOperation).filterIsInstance<TypeMismatch>().single()

    // Then
    assert(typeMismatchCase.expectedResponses.keys.toList() == listOf(400, 404, 422))
  }

  private fun apiOperationWith400(
    path: String = "/users",
    method: String = "POST",
    block: ApiOperationBuilder.() -> Unit = {}
  ) = apiOperation(method, path) {
    block()
    response(200) {}
    response(400) {
      jsonBody(objectType { properties { "error" to stringType() } })
    }
  }
}
