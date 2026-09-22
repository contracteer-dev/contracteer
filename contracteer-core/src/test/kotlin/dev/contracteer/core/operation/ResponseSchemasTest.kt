package dev.contracteer.core.operation

import org.junit.jupiter.api.Test

class ResponseSchemasTest {

  private val ok = ResponseSchema(headers = emptyList(), bodies = emptyList())
  private val badRequest = ResponseSchema(headers = emptyList(), bodies = emptyList())
  private val class4xx = ResponseSchema(headers = emptyList(), bodies = emptyList())
  private val default = ResponseSchema(headers = emptyList(), bodies = emptyList())

  @Test
  fun `responseFor returns explicit response when defined`() {
    val schemas = ResponseSchemas(byStatusCode = mapOf(200 to ok, 400 to badRequest))

    assert(schemas.responseFor(200) === ok)
    assert(schemas.responseFor(400) === badRequest)
  }

  @Test
  fun `responseFor falls back to class response when no exact match`() {
    val schemas = ResponseSchemas(byStatusCode = mapOf(200 to ok), byClass = mapOf(4 to class4xx))

    assert(schemas.responseFor(404) === class4xx)
    assert(schemas.responseFor(403) === class4xx)
  }

  @Test
  fun `responseFor returns exact response over class response`() {
    val schemas = ResponseSchemas(byStatusCode = mapOf(400 to badRequest), byClass = mapOf(4 to class4xx))

    assert(schemas.responseFor(400) === badRequest)
  }

  @Test
  fun `responseFor falls back to default when no exact and no class match`() {
    val schemas = ResponseSchemas(
      byStatusCode = mapOf(200 to ok),
      byClass = mapOf(4 to class4xx),
      defaultResponse = default
    )

    assert(schemas.responseFor(500) === default)
  }

  @Test
  fun `responseFor returns null when no match at all`() {
    val schemas = ResponseSchemas(byStatusCode = mapOf(200 to ok))

    assert(schemas.responseFor(404) == null)
  }

  @Test
  fun `badRequestResponse uses the same fallback chain`() {
    assert(ResponseSchemas(byStatusCode = mapOf(400 to badRequest)).badRequestResponse() === badRequest)
    assert(ResponseSchemas(byClass = mapOf(4 to class4xx)).badRequestResponse() === class4xx)
    assert(ResponseSchemas(defaultResponse = default).badRequestResponse() === default)
    assert(ResponseSchemas().badRequestResponse() == null)
  }

  @Test
  fun `successResponses returns only 2xx from byStatusCode`() {
    val schemas = ResponseSchemas(
      byStatusCode = mapOf(200 to ok, 400 to badRequest),
      byClass = mapOf(2 to class4xx),
      defaultResponse = default
    )

    assert(schemas.successResponses() == mapOf(200 to ok))
  }

  @Test
  fun `hasResponses covers exact status codes and class and default responses`() {
    assert(ResponseSchemas(byStatusCode = mapOf(200 to ok)).hasResponses())
    assert(ResponseSchemas(byClass = mapOf(4 to class4xx)).hasResponses())
    assert(ResponseSchemas(defaultResponse = default).hasResponses())
    assert(!ResponseSchemas().hasResponses())
  }
}
