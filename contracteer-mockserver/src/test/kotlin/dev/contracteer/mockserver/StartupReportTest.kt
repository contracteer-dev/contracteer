package dev.contracteer.mockserver

import dev.contracteer.core.dsl.apiOperation
import kotlin.test.Test

class StartupReportTest {

  @Test
  fun `reports nothing when every operation answers a 2xx or 3xx primary response`() {
    // Given
    val operations = listOf(
      apiOperation("GET", "/products") {
        response(200) {}
        response(404) {}
      },
      apiOperation("GET", "/legacy") {
        response(302) {}
      }
    )

    // When
    val report = startupReport(operations)

    // Then
    assert(report == StartupReport(warnings = emptyList(), details = emptyList()))
  }

  @Test
  fun `names the competing status codes of an operation with no primary response`() {
    // Given
    val operations = listOf(
      apiOperation("post", "/orders") {
        response(200) {}
        response(201) {}
        response(404) {}
      },
      apiOperation("put", "/orders") {
        response(200) {}
        response(201) {}
        response(202) {}
      }
    )

    // When
    val report = startupReport(operations)

    // Then
    assert(report == StartupReport(
      warnings = listOf(
        "POST /orders -> answers 418 to any valid request that matches no scenario: 200 and 201 both qualify",
        "PUT /orders -> answers 418 to any valid request that matches no scenario: 200, 201 and 202 all qualify"
      ),
      details = emptyList()
    ))
  }

  @Test
  fun `explains why no declared response can be the primary response`() {
    // Given
    val operations = listOf(
      apiOperation("GET", "/reports") {
        classResponse(2) {}
        defaultResponse {}
      },
      apiOperation("GET", "/items") {
        response(404) {}
        classResponse(2) {}
      },
      apiOperation("GET", "/ping") {}
    )

    // When
    val report = startupReport(operations)

    // Then
    assert(report.warnings == listOf(
      "GET /reports -> answers 418 to any valid request that matches no scenario: " +
      "declares only 2XX and default; no exact status code a request can target",
      "GET /items -> answers 418 to any valid request that matches no scenario: " +
      "declares 404 and 2XX; no exact 2xx or 3xx a request can target",
      "GET /ping -> answers 418 to any valid request that matches no scenario: declares no response"
    ))
  }

  @Test
  fun `reports an operation with no primary response even when every candidate has a scenario`() {
    // Given
    val operation = apiOperation("POST", "/orders") {
      response(200) {}
      response(201) {}
      scenario("ok", status = 200)
      scenario("created", status = 201)
    }

    // When
    val report = startupReport(listOf(operation))

    // Then
    assert(report.warnings == listOf(
      "POST /orders -> answers 418 to any valid request that matches no scenario: 200 and 201 both qualify"
    ))
  }

  @Test
  fun `reports the error status an operation answers when it is the only response declared`() {
    // Given
    val operation = apiOperation("GET", "/health") {
      response(500) {}
    }

    // When
    val report = startupReport(listOf(operation))

    // Then
    assert(report.warnings == listOf(
      "GET /health -> answers 500 to any valid request that matches no scenario: 500 is the only response declared"
    ))
  }

  @Test
  fun `lists the first ten operations and points to debug logging for the full list when more are reported`() {
    // Given
    val operations = (1..12).map { index ->
      apiOperation("GET", "/health/$index") { response(503) {} }
    }
    val lines = (1..12).map { index ->
      "GET /health/$index -> answers 503 to any valid request that matches no scenario: 503 is the only response declared"
    }

    // When
    val report = startupReport(operations)

    // Then
    assert(report == StartupReport(
      warnings = lines.take(10) + "...and 2 more; enable DEBUG logging for 'dev.contracteer.mockserver' to list them",
      details = lines
    ))
  }
}
