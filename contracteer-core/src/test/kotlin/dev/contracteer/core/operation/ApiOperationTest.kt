package dev.contracteer.core.operation

import org.junit.jupiter.api.Test

class ApiOperationTest {

  private val successResponse = ResponseSchema(headers = emptyList(), bodies = emptyList())
  private val badRequestResponse = ResponseSchema(headers = emptyList(), bodies = emptyList())
  private val classResponse4xx = ResponseSchema(headers = emptyList(), bodies = emptyList())
  private val defaultResponse = ResponseSchema(headers = emptyList(), bodies = emptyList())

  @Test
  fun `responseFor returns explicit response when defined`() {
    // given
    val operation = apiOperation(responses = mapOf(200 to successResponse, 400 to badRequestResponse))

    // then
    assert(operation.responseSchemas.responseFor(200) === successResponse)
    assert(operation.responseSchemas.responseFor(400) === badRequestResponse)
  }

  @Test
  fun `responseFor falls back to default when status code is not explicitly defined`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse),
      defaultResponse = defaultResponse
    )

    // then
    assert(operation.responseSchemas.responseFor(404) === defaultResponse)
    assert(operation.responseSchemas.responseFor(500) === defaultResponse)
  }

  @Test
  fun `responseFor returns explicit response over default`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse),
      defaultResponse = defaultResponse
    )

    // then
    assert(operation.responseSchemas.responseFor(200) === successResponse)
    assert(operation.responseSchemas.responseFor(200) !== defaultResponse)
  }

  @Test
  fun `responseFor returns null when no explicit response and no default`() {
    // given
    val operation = apiOperation(responses = mapOf(200 to successResponse))

    // then
    assert(operation.responseSchemas.responseFor(404) == null)
  }

  @Test
  fun `badRequestResponse returns explicit 400 over default`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse, 400 to badRequestResponse),
      defaultResponse = defaultResponse
    )

    // then
    assert(operation.responseSchemas.badRequestResponse() === badRequestResponse)
  }

  @Test
  fun `badRequestResponse falls back to default when no explicit 400`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse),
      defaultResponse = defaultResponse
    )

    // then
    assert(operation.responseSchemas.badRequestResponse() === defaultResponse)
  }

  @Test
  fun `badRequestResponse returns null when no explicit 400 and no default`() {
    // given
    val operation = apiOperation(responses = mapOf(200 to successResponse))

    // then
    assert(operation.responseSchemas.badRequestResponse() == null)
  }

  @Test
  fun `responseFor falls back to class response when no exact match`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse),
      classResponses = mapOf(4 to classResponse4xx)
    )

    // then
    assert(operation.responseSchemas.responseFor(404) === classResponse4xx)
    assert(operation.responseSchemas.responseFor(403) === classResponse4xx)
  }

  @Test
  fun `responseFor returns exact response over class response`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse, 400 to badRequestResponse),
      classResponses = mapOf(4 to classResponse4xx)
    )

    // then
    assert(operation.responseSchemas.responseFor(400) === badRequestResponse)
  }

  @Test
  fun `responseFor falls back to default when no exact and no class match`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse),
      classResponses = mapOf(4 to classResponse4xx),
      defaultResponse = defaultResponse
    )

    // then
    assert(operation.responseSchemas.responseFor(500) === defaultResponse)
  }

  @Test
  fun `badRequestResponse returns class response over default`() {
    // given
    val operation = apiOperation(
      responses = mapOf(200 to successResponse),
      classResponses = mapOf(4 to classResponse4xx),
      defaultResponse = defaultResponse
    )

    // then
    assert(operation.responseSchemas.badRequestResponse() === classResponse4xx)
  }

  private fun apiOperation(
    responses: Map<Int, ResponseSchema> = emptyMap(),
    classResponses: Map<Int, ResponseSchema> = emptyMap(),
    defaultResponse: ResponseSchema? = null
  ) = ApiOperation(
    path = "/test",
    method = "get",
    requestSchema = RequestSchema(parameters = emptyList(), bodies = emptyList()),
    responseSchemas = ResponseSchemas(responses, classResponses, defaultResponse),
    scenarios = emptyList()
  )
}
