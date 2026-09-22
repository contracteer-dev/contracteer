package dev.contracteer.verifier

import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.serde.JsonSerde
import dev.contracteer.core.serde.PlainTextSerde
import dev.contracteer.verifier.VerificationCase.ScenarioBased
import dev.contracteer.verifier.VerificationCase.SchemaBased
import kotlin.test.Test

class VerificationCaseFactoryTest {

  @Test
  fun `creates scenario based cases from operation scenarios`() {
    // Given
    val apiOperation = apiOperation("GET", "/users/{id}") {
      request {
        pathParam("id", integerType())
      }

      response(200) {
        jsonBody(objectType {
          properties {
            "id" to integerType()
            "name" to stringType()
          }
        })
      }

      response(404) {}

      scenario("validUser", status = 200) {
        request { pathParam["id"] = 1 }
        response { jsonBody { "id" to 1; "name" to "John" } }
      }
      scenario("notFound", status = 404) {
        request { pathParam["id"] = 999 }
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 2)
    assert(cases.all { it is ScenarioBased })
    assert(cases[0].displayName.contains("validUser"))
    assert(cases[1].displayName.contains("notFound"))
  }

  @Test
  fun `generates schema based case when no scenario targets the primary response`() {
    // Given
    val apiOperation = apiOperation("GET", "/products") {
      response(200) {
        jsonBody(objectType {
          properties { "items" to stringType() }
        })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 1)
    assert(cases[0] is SchemaBased)
    assert(cases[0].displayName.contains("(generated)"))
  }

  @Test
  fun `creates cartesian product for schema based cases with multiple content types`() {
    // Given
    val apiOperation = apiOperation("POST", "/data") {
      request {
        jsonBody(objectType {
          properties { "value" to stringType() }
        })
        body("application/xml", stringType(), JsonSerde)
      }

      response(201) {
        jsonBody(objectType {
          properties { "result" to stringType() }
        })
        body("application/xml", stringType(), JsonSerde)
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 4)
    assert(cases.all { it is SchemaBased })
    assert(cases.all { it.displayName.contains("(generated)") })
  }

  @Test
  fun `does not generate schema based case when a scenario targets the primary response`() {
    // Given
    val apiOperation = apiOperation("POST", "/orders") {
      request {
        jsonBody(objectType {
          properties { "product" to stringType() }
        })
      }
      response(201) {
        jsonBody(objectType {
          properties { "orderId" to stringType() }
        })
      }
      scenario("success", status = 201) {
        request { jsonBody { "product" to "laptop" } }
        response { jsonBody { "orderId" to "12345" } }
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 1)
    assert(cases[0] is ScenarioBased)
    assert(cases[0].displayName.contains("success"))
    assert(!cases[0].displayName.contains("(generated)"))
  }

  @Test
  fun `fans out scenario based cases per required content type when scenario has no body example`() {
    // Given a scenario with no body example and a required request body with two content types
    val apiOperation = apiOperation("POST", "/upload") {
      request {
        jsonBody(objectType {
          properties { "name" to stringType() }
        })
        body(
          "application/xml",
          objectType { properties { "name" to stringType() } },
          PlainTextSerde
        )
      }
      response(200) {}
      scenario("K1", status = 200) {}
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 2)
    assert(cases.all { it is ScenarioBased })
    val names = cases.map { it.displayName }
    assert(names.any { it.contains("application/json") && it.contains("K1") })
    assert(names.any { it.contains("application/xml") && it.contains("K1") })
  }

  @Test
  fun `marks request content type when scenario has no body example and single required content type`() {
    // Given a scenario with no body example and a required request body with a single content type
    val apiOperation = apiOperation("POST", "/upload") {
      request {
        body(
          "multipart/form-data",
          objectType { properties { "file" to stringType() } },
          JsonSerde
        )
      }
      response(200) {}
      scenario("K1", status = 200) {}
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 1)
    assert(cases[0] is ScenarioBased)
    assert(cases[0].displayName.contains("multipart/form-data"))
    assert(cases[0].displayName.contains("K1"))
  }

  @Test
  fun `does not fan out scenario based cases when request body is optional`() {
    // Given a scenario with no body example and an optional request body
    val apiOperation = apiOperation("POST", "/search") {
      request {
        jsonBody(objectType {
          properties { "q" to stringType() }
        },
                 isRequired = false
        )
        body(
          "application/xml",
          objectType {
            properties { "q" to stringType() }
          },
          JsonSerde,
          isRequired = false
        )
      }

      response(200) {}

      scenario("K1", status = 200) {}
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 1)
    assert(cases[0] is ScenarioBased)
    assert(!cases[0].displayName.contains("application/json"))
    assert(!cases[0].displayName.contains("application/xml"))
  }

  @Test
  fun `does not generate schema based case when several declared responses compete`() {
    // Given
    val apiOperation = apiOperation("GET", "/resources") {
      response(200) {
        jsonBody(objectType {
          properties { "data" to stringType() }
        })
      }
      response(202) {
        jsonBody(objectType {
          properties { "status" to stringType() }
        })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.isEmpty())
  }

  @Test
  fun `generates schema based case for the only declared response whatever its status code`() {
    // Given
    val redirect = apiOperation("GET", "/artifacts/{id}") {
      request { pathParam("id", integerType()) }
      response(302) {
        jsonBody(objectType { properties { "location" to stringType() } })
      }
    }
    val forbidden = apiOperation("GET", "/admin") {
      response(403) {
        jsonBody(objectType { properties { "message" to stringType() } })
      }
    }

    // When
    val redirectCase = VerificationCaseFactory.create(redirect).single()
    val forbiddenCase = VerificationCaseFactory.create(forbidden).single()

    // Then
    assert(redirectCase is SchemaBased && redirectCase.statusCode == 302)
    assert(forbiddenCase is SchemaBased && forbiddenCase.statusCode == 403)
  }

  @Test
  fun `generates schema based case for a single 3xx when no 2xx is declared`() {
    // Given
    val apiOperation = apiOperation("GET", "/downloads/{id}") {
      request { pathParam("id", integerType()) }
      response(302) {
        jsonBody(objectType { properties { "location" to stringType() } })
      }
      response(404) {}
    }

    // When
    val case = VerificationCaseFactory.create(apiOperation).single()

    // Then
    assert(case is SchemaBased && case.statusCode == 302)
  }

  @Test
  fun `does not generate schema based case when no declared response can be targeted`() {
    // Given
    val apiOperation = apiOperation("GET", "/reports") {
      classResponse(2) {
        jsonBody(objectType { properties { "data" to stringType() } })
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.isEmpty())
  }

  @Test
  fun `does not generate schema based case when a scenario targets a non 2xx primary response`() {
    // Given
    val apiOperation = apiOperation("GET", "/artifacts/{id}") {
      request { pathParam("id", integerType()) }
      response(302) {
        jsonBody(objectType { properties { "location" to stringType() } })
      }
      scenario("redirects", status = 302) {
        request { pathParam["id"] = 1 }
        response { jsonBody { "location" to "/elsewhere" } }
      }
    }

    // When
    val case = VerificationCaseFactory.create(apiOperation).single()

    // Then
    assert(case is ScenarioBased)
  }

  @Test
  fun `generates schema based case when a scenario targets a response other than the primary`() {
    // Given
    val apiOperation = apiOperation("GET", "/users/{id}") {
      request { pathParam("id", integerType()) }
      response(200) {
        jsonBody(objectType { properties { "name" to stringType() } })
      }
      response(404) {}
      scenario("notFound", status = 404) {
        request { pathParam["id"] = 999 }
      }
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases.size == 2)
    assert(cases.any { it is ScenarioBased })
    assert(cases.any { it is SchemaBased && it.statusCode == 200 })
  }
}
