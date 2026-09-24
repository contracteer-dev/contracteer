package dev.contracteer.core.operation

import dev.contracteer.core.operation.PrimaryResponse.Resolved
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.Ambiguous
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoPreferredResponse
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoResponsesDeclared
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoSelectableResponse
import org.junit.jupiter.api.Test

class PrimaryResponseTest {

  private val continueResponse = response()
  private val ok = response()
  private val created = response()
  private val movedPermanently = response()
  private val found = response()
  private val notModified = response()
  private val badRequest = response()
  private val forbidden = response()
  private val notFound = response()
  private val serverError = response()
  private val classResponse = response()
  private val default = response()

  @Test
  fun `resolves the only declared response whatever its status code`() {
    assert(ResponseSchemas(byStatusCode = mapOf(200 to ok)).primaryResponse() == Resolved(200, ok))
    assert(ResponseSchemas(byStatusCode = mapOf(302 to found)).primaryResponse() == Resolved(302, found))
    assert(ResponseSchemas(byStatusCode = mapOf(403 to forbidden)).primaryResponse() == Resolved(403, forbidden))
    assert(ResponseSchemas(byStatusCode = mapOf(500 to serverError)).primaryResponse() == Resolved(500, serverError))
  }

  @Test
  fun `resolves the schema the document declares for the primary status code`() {
    val primary = ResponseSchemas(byStatusCode = mapOf(200 to ok, 404 to notFound)).primaryResponse()

    assert(primary is Resolved && primary.schema === ok)
  }

  @Test
  fun `resolves the single exact 2xx when several responses are declared`() {
    assert(ResponseSchemas(byStatusCode = mapOf(200 to ok, 404 to notFound)).primaryResponse() == Resolved(200, ok))
    assert(ResponseSchemas(byStatusCode = mapOf(200 to ok, 304 to notModified)).primaryResponse() == Resolved(200, ok))
    assert(ResponseSchemas(byStatusCode = mapOf(200 to ok, 302 to found)).primaryResponse() == Resolved(200, ok))
    assert(ResponseSchemas(byStatusCode = mapOf(201 to created), defaultResponse = default).primaryResponse() == Resolved(201, created))
  }

  @Test
  fun `resolves a single exact 3xx only when no exact 2xx is declared`() {
    assert(ResponseSchemas(byStatusCode = mapOf(302 to found, 404 to notFound)).primaryResponse() == Resolved(302, found))
    assert(ResponseSchemas(byStatusCode = mapOf(302 to found), byClass = mapOf(2 to classResponse)).primaryResponse() == Resolved(302, found))
  }

  @Test
  fun `never resolves a class or default response`() {
    assert(ResponseSchemas(byClass = mapOf(2 to classResponse)).primaryResponse() == NoSelectableResponse(listOf("2XX")))
    assert(ResponseSchemas(defaultResponse = default).primaryResponse() == NoSelectableResponse(listOf("default")))
    assert(ResponseSchemas(byClass = mapOf(2 to classResponse), defaultResponse = default).primaryResponse()
           == NoSelectableResponse(listOf("2XX", "default")))
    assert(ResponseSchemas(byClass = mapOf(2 to classResponse, 4 to classResponse, 5 to classResponse)).primaryResponse()
           == NoSelectableResponse(listOf("2XX", "4XX", "5XX")))
  }

  @Test
  fun `never resolves a response no unconditional request can elicit`() {
    assert(ResponseSchemas(byStatusCode = mapOf(100 to continueResponse)).primaryResponse() == NoSelectableResponse(listOf("100")))
    assert(ResponseSchemas(byStatusCode = mapOf(304 to notModified)).primaryResponse() == NoSelectableResponse(listOf("304")))
    assert(ResponseSchemas(byClass = mapOf(1 to classResponse)).primaryResponse() == NoSelectableResponse(listOf("1XX")))
  }

  @Test
  fun `reports ambiguity and its candidates when several exact 2xx compete`() {
    assert(ResponseSchemas(byStatusCode = mapOf(200 to ok, 201 to created)).primaryResponse()
           == Ambiguous(declared = listOf("200", "201"), candidates = listOf(200, 201)))
    assert(ResponseSchemas(byStatusCode = mapOf(200 to ok, 201 to created, 404 to notFound)).primaryResponse()
           == Ambiguous(declared = listOf("200", "201", "404"), candidates = listOf(200, 201)))
  }

  @Test
  fun `reports ambiguity and its candidates when several exact 3xx compete and no exact 2xx is declared`() {
    assert(ResponseSchemas(byStatusCode = mapOf(301 to movedPermanently, 302 to found)).primaryResponse()
           == Ambiguous(declared = listOf("301", "302"), candidates = listOf(301, 302)))
    assert(ResponseSchemas(byStatusCode = mapOf(301 to movedPermanently, 302 to found, 304 to notModified)).primaryResponse()
           == Ambiguous(declared = listOf("301", "302", "304"), candidates = listOf(301, 302)))
  }

  @Test
  fun `reports no preferred response when several responses are declared and none is an exact 2xx or 3xx`() {
    assert(ResponseSchemas(byStatusCode = mapOf(400 to badRequest, 404 to notFound)).primaryResponse()
           == NoPreferredResponse(listOf("400", "404")))
    assert(ResponseSchemas(byStatusCode = mapOf(403 to forbidden), defaultResponse = default).primaryResponse()
           == NoPreferredResponse(listOf("403", "default")))
    assert(ResponseSchemas(byStatusCode = mapOf(404 to notFound), byClass = mapOf(2 to classResponse)).primaryResponse()
           == NoPreferredResponse(listOf("404", "2XX")))
  }

  @Test
  fun `reports no responses declared when the operation declares none`() {
    assert(ResponseSchemas().primaryResponse() == NoResponsesDeclared)
  }

  private fun response() = ResponseSchema(headers = emptyList(), bodies = emptyList())
}
