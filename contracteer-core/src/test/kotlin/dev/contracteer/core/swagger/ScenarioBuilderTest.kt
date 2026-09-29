package dev.contracteer.core.swagger

import dev.contracteer.core.dsl.integerType
import dev.contracteer.core.dsl.objectType
import dev.contracteer.core.dsl.stringType
import dev.contracteer.core.assertSuccess
import dev.contracteer.core.codec.SimpleParameterCodec
import dev.contracteer.core.normalize
import dev.contracteer.core.operation.*
import dev.contracteer.core.serde.JsonSerde
import kotlin.test.Test

class ScenarioBuilderTest {

  private val jsonSerde = JsonSerde

  @Test
  fun `builds scenario from matching request and response example keys`() {
    val request = ExtractedRequestSchema(
      parameters = listOf(
        ExtractedParameterSchema(
          schema = ParameterSchema(
            element = ParameterElement.PathParam("id"),
            dataType = integerType(),
            isRequired = true,
            codec = SimpleParameterCodec("id", false)
          ),
          examples = mapOf("GET_DETAILS" to 10, "NOT_FOUND" to 999)
        )
      ),
      bodies = emptyList()
    )
    val byStatusCode = mapOf(
      200 to ExtractedResponseSchema(
        headers = emptyList(),
        bodies = listOf(ExtractedBodySchema(
          schema = BodySchema(
            contentType = ContentType("application/json"),
            dataType = objectType { properties { "id" to integerType(); "name" to stringType() } },
            isRequired = false,
            serde = jsonSerde
          ),
          examples = mapOf("GET_DETAILS" to mapOf("id" to 10, "name" to "Duvel"))
        ))
      ),
      404 to ExtractedResponseSchema(
        headers = emptyList(),
        bodies = listOf(ExtractedBodySchema(
          schema = BodySchema(
            contentType = ContentType("application/json"),
            dataType = objectType { properties { "error" to stringType() } },
            isRequired = false,
            serde = jsonSerde
          ),
          examples = mapOf("NOT_FOUND" to mapOf("error" to "NOT FOUND"))
        ))
      )
    )

    val scenarios = ScenarioBuilder.buildScenarios("GET", "/products/{id}", request, byStatusCode, emptyMap(), null, LoadWarnings()).assertSuccess()

    assert(scenarios.size == 2)

    val details = scenarios.first { it.key == "GET_DETAILS" }
    assert(details.statusCode == 200)
    assert(details.request.parameterValues[ParameterElement.PathParam("id")] == 10.normalize())
    assert(details.response.body!!.value == mapOf("id" to 10, "name" to "Duvel").normalize())

    val notFound = scenarios.first { it.key == "NOT_FOUND" }
    assert(notFound.statusCode == 404)
    assert(notFound.request.parameterValues[ParameterElement.PathParam("id")] == 999.normalize())
    assert(notFound.response.body!!.value == mapOf("error" to "NOT FOUND").normalize())
  }

  @Test
  fun `returns empty list when no example keys exist`() {
    val request = ExtractedRequestSchema(parameters = emptyList(), bodies = emptyList())
    val byStatusCode = mapOf(200 to ExtractedResponseSchema(headers = emptyList(), bodies = emptyList()))

    val scenarios = ScenarioBuilder.buildScenarios("GET", "/test", request, byStatusCode, emptyMap(), null, LoadWarnings()).assertSuccess()

    assert(scenarios.isEmpty())
  }

  @Test
  fun `does not create scenario when keys exist only on request side`() {
    val request = ExtractedRequestSchema(
      parameters = listOf(
        ExtractedParameterSchema(
          schema = ParameterSchema(
            element = ParameterElement.PathParam("id"),
            dataType = integerType(),
            isRequired = true,
            codec = SimpleParameterCodec("id", false)
          ),
          examples = mapOf("MY_KEY" to 10)
        )
      ),
      bodies = emptyList()
    )
    val byStatusCode = mapOf(200 to ExtractedResponseSchema(headers = emptyList(), bodies = emptyList()))

    val scenarios = ScenarioBuilder.buildScenarios("GET", "/test/{id}", request, byStatusCode, emptyMap(), null, LoadWarnings()).assertSuccess()

    assert(scenarios.isEmpty())
  }

  @Test
  fun `creates cartesian product of request and response content types`() {
    val productType = objectType { properties { "id" to integerType() } }
    val request = ExtractedRequestSchema(
      parameters = emptyList(),
      bodies = listOf(
        ExtractedBodySchema(BodySchema(ContentType("application/json"), productType, false, jsonSerde), examples = mapOf("NEW" to mapOf("id" to 1))),
        ExtractedBodySchema(BodySchema(ContentType("application/xml"), productType, false, jsonSerde), examples = mapOf("NEW" to mapOf("id" to 1)))
      )
    )
    val byStatusCode = mapOf(
      201 to ExtractedResponseSchema(
        headers = emptyList(),
        bodies = listOf(
          ExtractedBodySchema(BodySchema(ContentType("application/json"), productType, false, jsonSerde), examples = mapOf("NEW" to mapOf("id" to 1))),
          ExtractedBodySchema(BodySchema(ContentType("application/xml"), productType, false, jsonSerde), examples = mapOf("NEW" to mapOf("id" to 1)))
        )
      )
    )

    val scenarios = ScenarioBuilder.buildScenarios("POST", "/products", request, byStatusCode, emptyMap(), null, LoadWarnings()).assertSuccess()

    assert(scenarios.size == 4)
    val pairs = scenarios.map { it.request.body!!.contentType.value to it.response.body!!.contentType.value }.toSet()
    assert(pairs == setOf(
      "application/json" to "application/json",
      "application/json" to "application/xml",
      "application/xml" to "application/json",
      "application/xml" to "application/xml"
    ))
  }

  @Test
  fun `does not validate 400 scenario examples against request schema`() {
    val request = ExtractedRequestSchema(
      parameters = listOf(
        ExtractedParameterSchema(
          schema = ParameterSchema(
            element = ParameterElement.PathParam("id"),
            dataType = integerType(),
            isRequired = true,
            codec = SimpleParameterCodec("id", false)
          ),
          examples = mapOf("INVALID_PATH" to "not-a-number")
        )
      ),
      bodies = emptyList()
    )
    val byStatusCode = mapOf(
      400 to ExtractedResponseSchema(
        headers = emptyList(),
        bodies = listOf(ExtractedBodySchema(
          schema = BodySchema(
            contentType = ContentType("application/json"),
            dataType = objectType { properties { "error" to stringType() } },
            isRequired = false,
            serde = jsonSerde
          ),
          examples = mapOf("INVALID_PATH" to mapOf("error" to "bad request"))
        ))
      )
    )

    val scenarios = ScenarioBuilder.buildScenarios("GET", "/test/{id}", request, byStatusCode, emptyMap(), null, LoadWarnings()).assertSuccess()

    assert(scenarios.size == 1)
    assert(scenarios.single().statusCode == 400)
    assert(scenarios.single().request.parameterValues[ParameterElement.PathParam("id")] == "not-a-number")
  }

  @Test
  fun `rejects non-400 scenario with invalid example value`() {
    val request = ExtractedRequestSchema(
      parameters = listOf(
        ExtractedParameterSchema(
          schema = ParameterSchema(
            element = ParameterElement.PathParam("id"),
            dataType = integerType(),
            isRequired = true,
            codec = SimpleParameterCodec("id", false)
          ),
          examples = mapOf("BAD" to "not-a-number")
        )
      ),
      bodies = emptyList()
    )
    val byStatusCode = mapOf(
      200 to ExtractedResponseSchema(
        headers = emptyList(),
        bodies = listOf(ExtractedBodySchema(
          schema = BodySchema(
            contentType = ContentType("application/json"),
            dataType = objectType { properties { "id" to integerType() } },
            isRequired = false,
            serde = jsonSerde
          ),
          examples = mapOf("BAD" to mapOf("id" to 1))
        ))
      )
    )

    val result = ScenarioBuilder.buildScenarios("GET", "/test/{id}", request, byStatusCode, emptyMap(), null, LoadWarnings())

    assert(result.isFailure())
  }

  @Test
  fun `creates scenario from status-code-prefixed key without response examples`() {
    val request = ExtractedRequestSchema(
      parameters = listOf(
        ExtractedParameterSchema(
          schema = ParameterSchema(
            element = ParameterElement.PathParam("id"),
            dataType = integerType(),
            isRequired = true,
            codec = SimpleParameterCodec("id", false)
          ),
          examples = mapOf("404_not_found" to 999)
        )
      ),
      bodies = emptyList()
    )
    val byStatusCode = mapOf(
      200 to ExtractedResponseSchema(headers = emptyList(), bodies = emptyList()),
      404 to ExtractedResponseSchema(
        headers = emptyList(),
        bodies = listOf(ExtractedBodySchema(
          schema = BodySchema(
            contentType = ContentType("application/json"),
            dataType = objectType { properties { "error" to stringType() } },
            isRequired = false,
            serde = jsonSerde
          ),
          examples = emptyMap()
        ))
      )
    )

    val scenarios = ScenarioBuilder.buildScenarios("GET", "/test/{id}", request, byStatusCode, emptyMap(), null, LoadWarnings()).assertSuccess()

    assert(scenarios.size == 1)
    assert(scenarios.single().key == "404_not_found")
    assert(scenarios.single().statusCode == 404)
    assert(scenarios.single().request.parameterValues[ParameterElement.PathParam("id")] == 999.normalize())
    assert(scenarios.single().response.body == null)
  }

  @Test
  fun `creates scenario from prefixed key with fallback to class response`() {
    val request = ExtractedRequestSchema(
      parameters = listOf(
        ExtractedParameterSchema(
          schema = ParameterSchema(
            element = ParameterElement.PathParam("id"),
            dataType = integerType(),
            isRequired = true,
            codec = SimpleParameterCodec("id", false)
          ),
          examples = mapOf("404_not_found" to 999)
        )
      ),
      bodies = emptyList()
    )
    val byStatusCode = mapOf(200 to ExtractedResponseSchema(headers = emptyList(), bodies = emptyList()))
    val byClass = mapOf(4 to ExtractedResponseSchema(headers = emptyList(), bodies = emptyList()))

    val scenarios = ScenarioBuilder.buildScenarios("GET", "/test/{id}", request, byStatusCode, byClass, null, LoadWarnings()).assertSuccess()

    assert(scenarios.size == 1)
    assert(scenarios.single().key == "404_not_found")
    assert(scenarios.single().statusCode == 404)
  }

  @Test
  fun `creates scenario from prefixed key with fallback to default response`() {
    val request = ExtractedRequestSchema(
      parameters = listOf(
        ExtractedParameterSchema(
          schema = ParameterSchema(
            element = ParameterElement.PathParam("id"),
            dataType = integerType(),
            isRequired = true,
            codec = SimpleParameterCodec("id", false)
          ),
          examples = mapOf("404_not_found" to 999)
        )
      ),
      bodies = emptyList()
    )
    val byStatusCode = mapOf(200 to ExtractedResponseSchema(headers = emptyList(), bodies = emptyList()))
    val default = ExtractedResponseSchema(headers = emptyList(), bodies = emptyList())

    val scenarios = ScenarioBuilder.buildScenarios("GET", "/test/{id}", request, byStatusCode, emptyMap(), default, LoadWarnings()).assertSuccess()

    assert(scenarios.size == 1)
    assert(scenarios.single().key == "404_not_found")
    assert(scenarios.single().statusCode == 404)
  }

  @Test
  fun `includes request body example in scenario`() {
    val productType = objectType { properties { "id" to integerType(); "name" to stringType() } }
    val request = ExtractedRequestSchema(
      parameters = emptyList(),
      bodies = listOf(ExtractedBodySchema(
        schema = BodySchema(
          contentType = ContentType("application/json"),
          dataType = productType,
          isRequired = true,
          serde = jsonSerde
        ),
        examples = mapOf("CREATE" to mapOf("id" to 1, "name" to "Duvel"))
      ))
    )
    val byStatusCode = mapOf(
      201 to ExtractedResponseSchema(
        headers = emptyList(),
        bodies = listOf(ExtractedBodySchema(
          schema = BodySchema(
            contentType = ContentType("application/json"),
            dataType = productType,
            isRequired = false,
            serde = jsonSerde
          ),
          examples = mapOf("CREATE" to mapOf("id" to 1, "name" to "Duvel"))
        ))
      )
    )

    val scenarios = ScenarioBuilder.buildScenarios("POST", "/products", request, byStatusCode, emptyMap(), null, LoadWarnings()).assertSuccess()

    assert(scenarios.size == 1)
    assert(scenarios.single().request.body!!.contentType.value == "application/json")
    assert(scenarios.single().request.body!!.value == mapOf("id" to 1, "name" to "Duvel").normalize())
  }
}
