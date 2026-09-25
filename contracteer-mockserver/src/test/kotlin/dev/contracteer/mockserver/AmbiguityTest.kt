package dev.contracteer.mockserver

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.AfterEach
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import java.math.BigDecimal
import kotlin.test.Test

class AmbiguityTest {

  private lateinit var mockServer: MockServer

  @AfterEach
  fun tearDown() {
    mockServer.stop()
  }

  @Test
  fun `responds with 418 when multiple scenarios match`() {
    // Given
    val operation = apiOperation("GET", "/v1/users/{id}") {
      request { pathParam("id", integerType()) }
      response(200) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
      scenario("scenario1", status = 200) {
        request { pathParam["id"] = BigDecimal(42) }
        response { jsonBody { "id" to 42 } }
      }
      scenario("scenario2", status = 200) {
        request { pathParam["id"] = BigDecimal(42) }
        response { jsonBody { "id" to 42 } }
      }
    }
    mockServer = MockServer(listOf(operation))
    mockServer.start()
    RestAssured.port = mockServer.port()

    // When / Then
    given()
      .accept("application/json")
      .get("/v1/users/42")
      .then()
      .assertThat()
      .statusCode(418)
      .body(containsString("Ambiguous"))
      .body(containsString("scenario1"))
      .body(containsString("scenario2"))
  }

  @Test
  fun `responds with 418 naming the competing status codes when multiple 2xx exist and no scenario matches`() {
    // Given
    val operation = apiOperation("GET", "/v1/users/{id}") {
      request { pathParam("id", integerType()) }
      response(200) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
      response(201) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
    }
    mockServer = MockServer(listOf(operation))
    mockServer.start()
    RestAssured.port = mockServer.port()

    // When / Then
    given()
      .accept("application/json")
      .get("/v1/users/123")
      .then()
      .assertThat()
      .statusCode(418)
      .body(equalTo("GET /v1/users/{id} -> matches no scenario and has no primary response: 200 and 201 both qualify"))
  }

  @Test
  fun `responds with 418 explaining why no declared response can be the primary response`() {
    // Given
    val operation = apiOperation("GET", "/v1/users/{id}") {
      request { pathParam("id", integerType()) }
      response(404) {}
      classResponse(2) {}
    }
    mockServer = MockServer(listOf(operation))
    mockServer.start()
    RestAssured.port = mockServer.port()

    // When / Then
    given()
      .get("/v1/users/123")
      .then()
      .assertThat()
      .statusCode(418)
      .body(equalTo("GET /v1/users/{id} -> matches no scenario and has no primary response: " +
                    "declares 404 and 2XX; no exact 2xx or 3xx a request can target"))
  }

  @Test
  fun `responds with 418 and diagnostic message when request validation fails`() {
    // Given
    val operation = apiOperation("GET", "/v1/users/{id}") {
      request { pathParam("id", integerType()) }
      response(200) {
        jsonBody(objectType { properties { "id" to integerType() } })
      }
    }
    mockServer = MockServer(listOf(operation))
    mockServer.start()
    RestAssured.port = mockServer.port()

    // When / Then
    given()
      .accept("application/json")
      .get("/v1/users/abc")
      .then()
      .assertThat()
      .statusCode(418)
      .body(containsString("Request validation failed"))
  }
}
