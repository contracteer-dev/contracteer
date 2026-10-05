package dev.contracteer.mockserver

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.AfterEach
import dev.contracteer.core.assertSuccess
import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.form
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.oneOfType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.swagger.OpenApiLoader
import kotlin.test.Test

class RequestValidationTest {

  private lateinit var mockServer: MockServer

  @AfterEach
  fun tearDown() {
    mockServer.stop()
  }

  // --- Path parameters ---

  @Test
  fun `responds successfully when path parameter matches schema type`() {
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
      .get("/v1/users/123")
      .then()
      .assertThat()
      .statusCode(200)
      .body("id", notNullValue())
  }

  @Test
  fun `responds with 418 when path parameter does not match schema type`() {
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
  }

  // --- Query parameters ---

  @Test
  fun `responds successfully when required query parameter is present and matches schema type`() {
    // Given
    val operation = apiOperation("GET", "/v1/users") {
      request { queryParam("id", integerType(), isRequired = true) }
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
      .get("/v1/users?id=123")
      .then()
      .assertThat()
      .statusCode(200)
      .body("id", notNullValue())
  }

  @Test
  fun `responds with 418 when required query parameter is missing`() {
    // Given
    val operation = apiOperation("GET", "/v1/users") {
      request { queryParam("id", integerType(), isRequired = true) }
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
      .get("/v1/users")
      .then()
      .assertThat()
      .statusCode(418)
  }

  @Test
  fun `responds successfully when optional query parameter is absent`() {
    // Given
    val operation = apiOperation("GET", "/v1/users") {
      request { queryParam("id", integerType(), isRequired = false) }
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
      .get("/v1/users")
      .then()
      .assertThat()
      .statusCode(200)
      .body("id", notNullValue())
  }

  @Test
  fun `responds successfully when query parameter with allowReserved contains reserved characters`() {
    // Given
    val operation = apiOperation("GET", "/v1/search") {
      request {
        queryParam(
          "path",
          stringType(),
          isRequired = true,
          codec = form(allowReserved = true)
        )
      }
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
      .get("/v1/search?path=/users/123")
      .then()
      .assertThat()
      .statusCode(200)
      .body("id", notNullValue())
  }

  // --- Header parameters ---

  @Test
  fun `responds successfully when required header parameter is present and matches schema type`() {
    // Given
    val operation = apiOperation("GET", "/v1/users") {
      request { header("X-Request-Id", integerType(), isRequired = true) }
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
      .header("X-Request-Id", "123")
      .get("/v1/users")
      .then()
      .assertThat()
      .statusCode(200)
      .body("id", notNullValue())
  }

  @Test
  fun `responds with 418 when required header parameter is missing`() {
    // Given
    val operation = apiOperation("GET", "/v1/users") {
      request { header("X-Request-Id", integerType(), isRequired = true) }
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
      .get("/v1/users")
      .then()
      .assertThat()
      .statusCode(418)
  }

  // --- Cookie parameters ---

  @Test
  fun `responds successfully when required cookie is present and matches schema type`() {
    // Given
    val operation = apiOperation("GET", "/v1/users") {
      request { cookie("session", integerType(), isRequired = true) }
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
      .cookie("session", "123")
      .get("/v1/users")
      .then()
      .assertThat()
      .statusCode(200)
      .body("id", notNullValue())
  }

  @Test
  fun `responds with 418 when required cookie is missing`() {
    // Given
    val operation = apiOperation("GET", "/v1/users") {
      request { cookie("session", integerType(), isRequired = true) }
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
      .get("/v1/users")
      .then()
      .assertThat()
      .statusCode(418)
  }

  // --- Request body ---

  @Test
  fun `responds successfully when request body matches schema type`() {
    // Given
    val operation = apiOperation("POST", "/v1/users") {
      request {
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
      .contentType("application/json")
      .body("""{"id": 42}""")
      .post("/v1/users")
      .then()
      .assertThat()
      .statusCode(201)
      .body("id", notNullValue())
  }

  @Test
  fun `responds with 418 when request body does not match schema type`() {
    // Given
    val operation = apiOperation("POST", "/v1/users") {
      request {
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
      .contentType("application/json")
      .body("""{"id": "john"}""")
      .post("/v1/users")
      .then()
      .assertThat()
      .statusCode(418)
  }

  @Test
  fun `responds with 418 when request content type does not match body schema`() {
    // Given
    val operation = apiOperation("POST", "/v1/users") {
      request {
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
      .contentType("text/plain")
      .body("""{"id": 42}""")
      .post("/v1/users")
      .then()
      .assertThat()
      .statusCode(418)
  }

  @Test
  fun `responds with 418 when request body matches no oneOf variant`() {
    // Given
    val variantA = objectType {
      properties { "name" to stringType() }
      required("name")
    }
    val variantB = objectType {
      properties { "age" to integerType() }
      required("age")
    }
    val operation = apiOperation("POST", "/v1/users") {
      request {
        jsonBody(oneOfType { subType(variantA); subType(variantB) })
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
      .contentType("application/json")
      .body("""{"unknown": true}""")
      .post("/v1/users")
      .then()
      .assertThat()
      .statusCode(418)
  }

  @Test
  fun `responds with 418 when form-urlencoded request body has a malformed percent-escape`() {
    // Given
    val operations = OpenApiLoader.loadOperations("src/test/resources/form_urlencoded/form_urlencoded_body.yaml").assertSuccess()
    mockServer = MockServer(operations)
    mockServer.start()
    RestAssured.port = mockServer.port()

    // When / Then
    given()
      .contentType("application/x-www-form-urlencoded")
      .body("access_token=%ZZ")
      .post("/token")
      .then()
      .assertThat()
      .statusCode(418)
      .body(containsString("Malformed percent-escape in 'access_token=%ZZ'"))
  }

  @Test
  fun `responds with 418 when required request body is missing`() {
    // Given
    val operation = apiOperation("POST", "/v1/users") {
      request {
        jsonBody(objectType { properties { "id" to integerType() } }, isRequired = true)
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
      .post("/v1/users")
      .then()
      .assertThat()
      .statusCode(418)
  }
}
