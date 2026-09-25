package dev.contracteer.verifier

import dev.contracteer.core.dsl.apiOperation
import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.Ambiguous
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoPreferredResponse
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoResponsesDeclared
import dev.contracteer.core.operation.PrimaryResponse.Unresolved.NoSelectableResponse
import dev.contracteer.verifier.VerificationCase.TypeMismatch
import kotlin.test.Test

class VerificationPlanTest {

  @Test
  fun `does not report the primary response when it resolves`() {
    // Given
    val apiOperation = apiOperation("GET", "/products") {
      response(200) {}
      response(404) {}
    }

    // When
    val plan = VerificationCaseFactory.plan(apiOperation)

    // Then
    assert(plan.unverifiedPrimaryResponse == null)
  }

  @Test
  fun `reports the primary response as unverified when several exact 2xx compete`() {
    // Given
    val apiOperation = apiOperation("POST", "/orders") {
      response(200) {}
      response(201) {}
    }

    // When
    val plan = VerificationCaseFactory.plan(apiOperation)

    // Then
    val unverified = plan.unverifiedPrimaryResponse
    assert(unverified?.reason == Ambiguous(declared = listOf("200", "201"), candidates = listOf(200, 201)))
    assert(unverified?.method == "POST")
    assert(unverified?.path == "/orders")
  }

  @Test
  fun `reports an ambiguous primary response when only some candidates have a scenario`() {
    // Given
    val apiOperation = apiOperation("POST", "/orders") {
      response(200) {}
      response(201) {}
      response(404) {}
      scenario("ok", status = 200)
      scenario("notFound", status = 404)
    }

    // When
    val plan = VerificationCaseFactory.plan(apiOperation)

    // Then
    assert(plan.unverifiedPrimaryResponse?.reason is Ambiguous)
  }

  @Test
  fun `does not report an ambiguous primary response when every candidate has a scenario`() {
    // Given
    val apiOperation = apiOperation("POST", "/orders") {
      response(200) {}
      response(201) {}
      scenario("ok", status = 200)
      scenario("created", status = 201)
    }

    // When
    val plan = VerificationCaseFactory.plan(apiOperation)

    // Then
    assert(plan.unverifiedPrimaryResponse == null)
  }

  @Test
  fun `reports no preferred response even when every declared response has a scenario`() {
    // Given
    val apiOperation = apiOperation("GET", "/items") {
      response(400) {}
      response(404) {}
      scenario("badRequest", status = 400)
      scenario("notFound", status = 404)
    }

    // When
    val plan = VerificationCaseFactory.plan(apiOperation)

    // Then
    assert(plan.unverifiedPrimaryResponse?.reason == NoPreferredResponse(listOf("400", "404")))
  }

  @Test
  fun `reports the primary response as unverified alongside the cases the operation still produces`() {
    // Given
    val apiOperation = apiOperation("GET", "/reports") {
      request { queryParam("page", integerType(), isRequired = true) }
      classResponse(4) {}
    }

    // When
    val plan = VerificationCaseFactory.plan(apiOperation)

    // Then
    assert(plan.cases.single() is TypeMismatch)
    assert(plan.unverifiedPrimaryResponse?.reason == NoSelectableResponse(listOf("4XX")))
  }

  @Test
  fun `reports the primary response as unverified when no response is declared`() {
    // Given
    val apiOperation = apiOperation("GET", "/ping") {}

    // When
    val plan = VerificationCaseFactory.plan(apiOperation)

    // Then
    assert(plan.unverifiedPrimaryResponse?.reason == NoResponsesDeclared)
  }

  @Test
  fun `creates the cases of the plan`() {
    // Given
    val apiOperation = apiOperation("GET", "/reports") {
      request { queryParam("page", integerType(), isRequired = true) }
      response(200) {}
      response(400) {}
    }

    // When
    val cases = VerificationCaseFactory.create(apiOperation)

    // Then
    assert(cases == VerificationCaseFactory.plan(apiOperation).cases)
  }

  @Test
  fun `names the competing status codes and asks for a scenario on each`() {
    // Given
    val twoCandidates = apiOperation("post", "/orders") {
      response(200) {}
      response(201) {}
      response(404) {}
      scenario("notFound", status = 404)
    }
    val threeCandidates = apiOperation("post", "/orders") {
      response(200) {}
      response(201) {}
      response(202) {}
    }

    // When
    val twoCandidatesMessage = VerificationCaseFactory.plan(twoCandidates).unverifiedPrimaryResponse?.message
    val threeCandidatesMessage = VerificationCaseFactory.plan(threeCandidates).unverifiedPrimaryResponse?.message

    // Then
    assert(twoCandidatesMessage ==
           "POST /orders -> primary response not verified: 200 and 201 both qualify; declare a scenario for each of them")
    assert(threeCandidatesMessage ==
           "POST /orders -> primary response not verified: 200, 201 and 202 all qualify; declare a scenario for each of them")
  }

  @Test
  fun `explains why no declared response can be the primary response`() {
    // Given
    val noSelectableResponse = apiOperation("GET", "/reports") {
      classResponse(2) {}
      defaultResponse {}
    }
    val noPreferredResponse = apiOperation("GET", "/items") {
      response(404) {}
      classResponse(2) {}
    }
    val noResponsesDeclared = apiOperation("GET", "/ping") {}

    // When
    val noSelectableMessage = VerificationCaseFactory.plan(noSelectableResponse).unverifiedPrimaryResponse?.message
    val noPreferredMessage = VerificationCaseFactory.plan(noPreferredResponse).unverifiedPrimaryResponse?.message
    val noResponsesMessage = VerificationCaseFactory.plan(noResponsesDeclared).unverifiedPrimaryResponse?.message

    // Then
    assert(noSelectableMessage ==
           "GET /reports -> primary response not verified: declares only 2XX and default; no exact status code a request can target")
    assert(noPreferredMessage ==
           "GET /items -> primary response not verified: declares 404 and 2XX; no exact 2xx or 3xx a request can target")
    assert(noResponsesMessage ==
           "GET /ping -> primary response not verified: declares no response")
  }
}
