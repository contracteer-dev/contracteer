package dev.contracteer.verifier

import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Response
import org.http4k.core.Status
import dev.contracteer.core.assertFailure
import dev.contracteer.core.dsl.*
import dev.contracteer.core.operation.ContentType
import dev.contracteer.core.operation.ResponseSchema
import dev.contracteer.core.serde.PlainTextSerde
import kotlin.test.Test

class ResponseValidatorTest {

  @Test
  fun `validates successfully when status codes match`() {
    // Given
    val target = schemaBasedCase(statusCode = 201)
    val response = mockResponse(Status.CREATED)

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess())
  }

  @Test
  fun `fails when status code does not match`() {
    // Given
    val target = schemaBasedCase(statusCode = 200)
    val response = mockResponse(Status.CREATED)

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isFailure())
    assert(result.errors().size == 1)
    assert(result.errors().first().contains("200"))
    assert(result.errors().first().contains("201"))
  }

  @Test
  fun `names the likely authoring cause when a 4xx or 5xx primary response does not match`() {
    // Given
    val forbidden = schemaBasedCase(statusCode = 403)
    val serverError = schemaBasedCase(statusCode = 500)
    val response = mockResponse(Status.OK)

    // When
    val forbiddenResult = ResponseValidator.validate(forbidden, response)
    val serverErrorResult = ResponseValidator.validate(serverError, response)

    // Then
    assert(forbiddenResult.errors().first().contains("403 is the only response this operation declares"))
    assert(serverErrorResult.errors().first().contains("500 is the only response this operation declares"))
  }

  @Test
  fun `does not name an authoring cause when a 2xx or 3xx primary response does not match`() {
    // Given
    val ok = schemaBasedCase(statusCode = 200)
    val found = schemaBasedCase(statusCode = 302)
    val response = mockResponse(Status.CREATED)

    // When
    val okResult = ResponseValidator.validate(ok, response)
    val foundResult = ResponseValidator.validate(found, response)

    // Then
    assert(okResult.errors().first() == "Status code does not match. Expected: 200, Actual: 201")
    assert(foundResult.errors().first() == "Status code does not match. Expected: 302, Actual: 201")
  }

  @Test
  fun `validates successfully with required headers using serde deserialization`() {
    // Given
    val target = schemaBasedCase(statusCode = 200) {
      response(200) {
        header("X-Count", integerType(), isRequired = true)
        header("X-Name", stringType(), isRequired = true)
      }
    }
    val response = mockResponse(
      status = Status.OK,
      headers = listOf("X-Count" to "42", "X-Name" to "test")
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess())
  }

  @Test
  fun `validates successfully with optional headers missing`() {
    // Given
    val target = schemaBasedCase(statusCode = 200) {
      response(200) {
        header("X-Optional", stringType(), isRequired = false)
      }
    }
    val response = mockResponse(Status.OK)

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess())
  }

  @Test
  fun `fails when required header is missing`() {
    // Given
    val target = schemaBasedCase(statusCode = 200) {
      response(200) {
        header("X-Required", stringType(), isRequired = true)
      }
    }
    val response = mockResponse(Status.OK)

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isFailure())
    assert(result.errors().size == 1)
    assert(result.errors().first() == "Response header 'X-Required' is missing")
  }

  @Test
  fun `fails when header value does not match datatype after deserialization`() {
    // Given
    val target = schemaBasedCase(statusCode = 200) {
      response(200) {
        header("X-Count", integerType(), isRequired = true)
      }
    }
    val response = mockResponse(
      status = Status.OK,
      headers = listOf("X-Count" to "not-a-number")
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isFailure())
    assert(result.errors().size == 1)
    assert(result.errors().first().contains("X-Count"))
  }

  @Test
  fun `validates successfully with matching JSON body`() {
    // Given
    val target = schemaBasedCase(
      statusCode = 200,
      responseContentType = ContentType("application/json")
    ) {
      response(200) {
        jsonBody(objectType {
          properties {
            "id" to integerType()
            "name" to stringType()
          }
        })
      }
    }
    val response = mockResponse(
      status = Status.OK,
      headers = listOf("Content-Type" to "application/json"),
      contentType = "application/json",
      body = """{"id": 123, "name": "John"}"""
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess())
  }

  @Test
  fun `validates successfully when no body expected and no body received`() {
    // Given
    val target = schemaBasedCase(method = "DELETE", statusCode = 204)
    val response = mockResponse(Status.NO_CONTENT)

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess())
  }

  @Test
  fun `fails when content-type does not match expected`() {
    // Given
    val target = schemaBasedCase(
      statusCode = 200,
      responseContentType = ContentType("application/json")
    ) {
      response(200) {
        jsonBody(objectType { properties { "name" to stringType() } })
      }
    }
    val response = mockResponse(
      status = Status.OK,
      headers = listOf("Content-Type" to "text/plain"),
      contentType = "text/plain",
      body = "plain text"
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isFailure())
    assert(result.errors().size == 1)
    assert(result.errors().first().contains("text/plain"))
    assert(result.errors().first().contains("application/json"))
  }

  @Test
  fun `fails when body is expected but content-type is missing`() {
    // Given
    val target = schemaBasedCase(
      statusCode = 200,
      responseContentType = ContentType("application/json")
    ) {
      response(200) {
        jsonBody(objectType { properties { "name" to stringType() } })
      }
    }
    val response = mockResponse(
      status = Status.OK,
      body = ""
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isFailure())
    assert(result.errors().size == 1)
    assert(result.errors().first().contains("Content-Type is missing"))
  }

  @Test
  fun `fails when no body expected but content-type is present`() {
    // Given
    val target = schemaBasedCase(method = "DELETE", statusCode = 204)
    val response = mockResponse(
      status = Status.NO_CONTENT,
      headers = listOf("Content-Type" to "application/json"),
      contentType = "application/json",
      body = "{}"
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isFailure())
    assert(result.errors().size == 1)
    assert(result.errors().first().contains("Expected no Content-Type"))
  }

  @Test
  fun `validates with multiple content types and finds matching schema`() {
    // Given
    val target = schemaBasedCase(
      statusCode = 200,
      responseContentType = ContentType("application/xml")
    ) {
      response(200) {
        jsonBody(objectType { properties { "name" to stringType() } })
        body("application/xml", stringType(), PlainTextSerde)
      }
    }
    val response = mockResponse(
      status = Status.OK,
      headers = listOf("Content-Type" to "application/xml"),
      contentType = "application/xml",
      body = "<user><name>John</name></user>"
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess())
  }

  @Test
  fun `does not validate body and headers against expected schema when status code does not match`() {
    // Given
    val target = schemaBasedCase(
      statusCode = 200,
      responseContentType = ContentType("application/json")
    ) {
      response(200) {
        header("X-Count", integerType(), isRequired = true)
        jsonBody(arrayType(stringType()))
      }
    }
    val response = mockResponse(
      status = Status.BAD_REQUEST,
      headers = listOf("Content-Type" to "application/json"),
      contentType = "application/json",
      body = """{"code": 400, "error": "bad"}"""
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    val errors = result.assertFailure()
    assert(errors.size == 1)
    assert(errors.single().contains("Status code does not match"))
  }

  @Test
  fun `full validation with status code headers and body`() {
    // Given
    val target = schemaBasedCase(
      method = "POST",
      statusCode = 201,
      requestContentType = ContentType("application/json"),
      responseContentType = ContentType("application/json")
    ) {
      request {
        jsonBody(objectType { properties { "email" to stringType() } })
      }
      response(201) {
        header("X-Total-Count", integerType(), isRequired = true)
        jsonBody(objectType {
          properties {
            "id" to integerType()
            "email" to stringType()
          }
          required("id", "email")
        })
      }
    }
    val response = mockResponse(
      status = Status.CREATED,
      headers = listOf(
        "X-Total-Count" to "42",
        "Content-Type" to "application/json"
      ),
      contentType = "application/json",
      body = """{"id": 999, "email": "test@example.com"}"""
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess())
  }

  @Test
  fun `validates type mismatch response against the schema of the status the server answered`() {
    // Given
    val target = typeMismatchCase(expectedStatusCodes = listOf(400, 422)) {
      response(400) {
        jsonBody(objectType {
          properties { "error" to stringType() }
          required("error")
        })
      }
      response(422) {
        jsonBody(objectType {
          properties { "detail" to stringType() }
          required("detail")
        })
      }
    }
    val response = mockResponse(
      status = Status.UNPROCESSABLE_ENTITY,
      headers = listOf("Content-Type" to "application/json"),
      contentType = "application/json",
      body = """{"detail": "invalid body"}"""
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    assert(result.isSuccess()) { "Expected success but got: ${result.errors()}" }
  }

  @Test
  fun `fails type mismatch when body does not match the schema of the status the server answered`() {
    // Given
    val target = typeMismatchCase(expectedStatusCodes = listOf(400, 422)) {
      response(400) {
        jsonBody(objectType {
          properties { "error" to stringType() }
          required("error")
        })
      }
      response(422) {
        jsonBody(objectType {
          properties { "detail" to stringType() }
          required("detail")
        })
      }
    }
    val response = mockResponse(
      status = Status.UNPROCESSABLE_ENTITY,
      headers = listOf("Content-Type" to "application/json"),
      contentType = "application/json",
      body = """{"error": "invalid body"}"""
    )

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    val errors = result.assertFailure()
    assert(errors.single().contains("detail")) { "Expected a failure on 'detail' but got: $errors" }
  }

  @Test
  fun `fails type mismatch when status is not expected and lists every expected status`() {
    // Given
    val target = typeMismatchCase(expectedStatusCodes = listOf(400, 422)) {
      classResponse(4) {}
    }
    val response = mockResponse(Status.UNAUTHORIZED)

    // When
    val result = ResponseValidator.validate(target, response)

    // Then
    val errors = result.assertFailure()
    assert(errors.single() == "Status code does not match. Expected: 400|422, Actual: 401")
  }

  // --- helpers ---

  private fun schemaBasedCase(method: String = "GET",
                              path: String = "/users",
                              statusCode: Int = 200,
                              requestContentType: ContentType? = null,
                              responseContentType: ContentType? = null,
                              block: ApiOperationBuilder.() -> Unit = {}): VerificationCase.SchemaBased {
    val op = apiOperation(method, path, block)
    return VerificationCase.SchemaBased(
      path = path,
      method = method,
      statusCode = statusCode,
      requestContentType = requestContentType,
      responseContentType = responseContentType,
      requestSchema = op.requestSchema,
      responseSchema = op.responseSchemas.responseFor(statusCode)
                       ?: ResponseSchema(headers = emptyList(), bodies = emptyList())
    )
  }

  private fun typeMismatchCase(expectedStatusCodes: List<Int>,
                               block: ApiOperationBuilder.() -> Unit): VerificationCase.TypeMismatch {
    val op = apiOperation("POST", "/users", block)
    return VerificationCase.TypeMismatch(
      path = "/users",
      method = "POST",
      requestContentType = null,
      responseContentType = null,
      requestSchema = op.requestSchema,
      expectedResponses = expectedStatusCodes.associateWith { op.responseSchemas.responseFor(it)!! },
      mutatedElement = MutatedElement.Body,
      mutatedValue = "<<not a object>>"
    )
  }

  private fun mockResponse(status: Status,
                           headers: List<Pair<String, String>> = emptyList(),
                           contentType: String? = null,
                           body: String? = null): Response {
    val response = mockk<Response>()
    every { response.status } returns status
    every { response.headers } returns headers
    every { response.header("Content-Type") } returns contentType
    if (body != null) every { response.bodyString() } returns body
    return response
  }
}
